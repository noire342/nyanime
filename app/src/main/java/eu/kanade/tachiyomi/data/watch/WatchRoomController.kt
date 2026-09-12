package eu.kanade.tachiyomi.data.watch

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * The creator orders commands; followers never publish player observations as commands.
 * Explicit user intent, group buffering and local safety holds have separate ownership.
 * A lost creator ends in a paused room, never in a competing leader/split-brain session.
 */
class WatchRoomController(
    private val scope: CoroutineScope,
    private val player: WatchPlayer,
    private val now: () -> Long,
    private val wallMillis: () -> Long = System::currentTimeMillis,
    private val transportFactory: (WatchInvite, WatchIdentity) -> WatchTransport = { invite, identity ->
        NostrWatchTransport(invite, identity)
    },
) {
    private val mutableState = MutableStateFlow(WatchRoomState())
    val state = mutableState.asStateFlow()
    val active: Boolean get() = state.value.active
    private var transport: WatchTransport? = null
    private var invite: WatchInvite? = null
    private var ticker: Job? = null
    private var generation = 0L
    private var sequence = 0L
    private var started = 0L
    private var lastHostMessage = 0L
    private var lastBroadcast = -10_000L
    private var lastStatus = -10_000L
    private var lastPing = -10_000L
    private var lastSeek = -10_000L
    private var lastRetry = 0L
    private var pendingSince = 0L
    private var pendingCommand: WatchMessage? = null
    private var lastStatusValue: WatchPeerStatus? = null
    private var clock = WatchClock()
    private var timeline: WatchMessage? = null
    private val pings = mutableSetOf<Long>()
    private val peers = linkedMapOf<String, Pair<WatchPeerStatus, Long>>()
    private val sequences = mutableMapOf<String, Long>()
    private val acknowledgements = mutableMapOf<String, Long>()
    private val departed = linkedMapOf<String, Long>()
    private var name = ""
    private var desiredPaused = true
    private var baseSpeed = 1.0
    private var originalSpeed = 1.0
    private var appliedSpeed: Double? = null
    private var seekRevision = 0L
    private var appliedSeekRevision = -1L
    private var pendingSeek: Double? = null
    private var pendingSeekSince = 0L
    private var acceptedMedia: String? = null
    private var closedMessage = ""
    var expectsPaused: Boolean = true
        private set

    fun create(displayName: String, relays: List<String> = WatchInvite.defaultRelays) = start(displayName) { identity ->
        WatchInvite.create(identity.publicKey, wallMillis(), relays)
    }

    fun join(code: String, displayName: String) = start(displayName) { WatchInvite.parse(code, wallMillis()) }

    private fun start(displayName: String, makeInvite: (WatchIdentity) -> WatchInvite) {
        if (active) return
        var identity: WatchIdentity? = null
        try {
            val sample = player.sample()
            identity = WatchIdentity()
            val room = makeInvite(identity)
            val network = transportFactory(room, identity)
            reset()
            name = displayName.trim().take(32).ifBlank { "Spettatore" }
            invite = room
            transport = network
            originalSpeed = sample.speed.takeIf { it.isFinite() }?.coerceIn(0.25, 3.0) ?: 1.0
            baseSpeed = originalSpeed
            started = now()
            lastHostMessage = started
            val host = room.owns(network.publicKey)
            mutableState.value = WatchRoomState(
                phase = WatchPhase.Connecting,
                active = true,
                host = host,
                invite = room.encode(),
                media = if (host) sample.media else null,
                message = "Connessione alla stanza…",
            )
            player.pause(true)
            val token = ++generation
            network.start(
                onMessage = { sender, message -> scope.launch { if (token == generation) receive(sender, message) } },
                onConnection = { count ->
                    scope.launch {
                        if (token == generation && active) {
                            mutableState.value = state.value.copy(relayCount = count)
                            if (count > 0) {
                                lastStatus = -10_000
                                lastBroadcast = -10_000
                                lastPing = -10_000
                            }
                        }
                    }
                },
            )
            ticker = scope.launch {
                while (active && token == generation) {
                    tick()
                    delay(250)
                }
            }
        } catch (e: Exception) {
            identity?.clear()
            transport?.close()
            transport = null
            mutableState.value = WatchRoomState(
                phase = WatchPhase.Failed,
                message = (e as? IllegalArgumentException)?.message ?: "Impossibile aprire la stanza. Riprova.",
            )
        }
    }

    private fun reset() {
        sequence = 0
        lastBroadcast = -10_000
        lastStatus = -10_000
        lastPing = -10_000
        lastSeek = -10_000
        clock = WatchClock()
        timeline = null
        pings.clear()
        peers.clear()
        sequences.clear()
        acknowledgements.clear()
        departed.clear()
        lastStatusValue = null
        pendingCommand = null
        pendingSeek = null
        acceptedMedia = null
        desiredPaused = true
        expectsPaused = true
        appliedSpeed = null
        seekRevision = 0
        appliedSeekRevision = -1
        closedMessage = ""
    }

    fun leave() {
        if (!active) {
            mutableState.value = WatchRoomState()
            return
        }
        send(message(if (state.value.host) WatchMessageType.Closed else WatchMessageType.Leave))
        stop(WatchPhase.Idle, "")
    }

    private fun stop(phase: WatchPhase, reason: String) {
        generation++
        ticker?.cancel()
        ticker = null
        transport?.close()
        transport = null
        invite = null
        pendingCommand = null
        player.pause(true)
        player.speed(originalSpeed)
        appliedSpeed = null
        mutableState.value = WatchRoomState(phase = phase, message = reason)
    }

    /** Timers, audio-focus loss and backgrounding can only be cleared by an explicit local Play. */
    fun hold() {
        if (!active) return
        expectsPaused = true
        mutableState.value = state.value.copy(localHold = true)
        player.pause(true)
        requestPause(true)
        lastStatus = -10_000
    }

    fun playerAttached() {
        // A newly loaded native player must not start ahead of the room readiness barrier.
        expectsPaused = true
        appliedSpeed = null
        appliedSeekRevision = -1
        lastStatus = -10_000
        lastBroadcast = -10_000
    }

    fun resumeByUser(): Boolean {
        if (!active) return false
        player.userResumed()
        mutableState.value = state.value.copy(localHold = false)
        lastStatus = -10_000
        return requestPause(false)
    }

    fun requestPause(paused: Boolean): Boolean = command(if (paused) "pause" else "play")
    fun requestSeek(seconds: Double): Boolean = command("seek", seconds)
    fun requestSpeed(speed: Double): Boolean = command("speed", speed)

    private fun command(action: String, value: Double = 0.0): Boolean {
        if (!active) return false
        if (!value.isFinite()) return true
        if (!state.value.host && !state.value.sharedControls) {
            mutableState.value = state.value.copy(message = "I comandi sono gestiti da chi ha creato la stanza.")
            return true
        }
        if (action == "play" && state.value.localHold) return true
        if (action != "pause" && state.value.phase == WatchPhase.DifferentVideo) return true
        val sample = player.sample()
        val request = message(WatchMessageType.Command).copy(
            command = action,
            media = sample.media,
            position = if (action ==
                "seek"
            ) {
                value.coerceIn(0.0, sample.media?.duration ?: 0.0)
            } else {
                sample.position.coerceAtLeast(0.0)
            },
            speed = if (action == "speed") value.coerceIn(0.25, 3.0) else baseSpeed,
        )
        if (state.value.host) {
            applyCommand(request)
        } else {
            pendingCommand = request
            pendingSince = now()
            lastRetry = now()
            send(request)
            // Pausing is safe immediately. Play/seek wait for the creator's authoritative state.
            if (action == "pause") player.pause(true)
        }
        return true
    }

    private fun applyCommand(request: WatchMessage) {
        when (request.command) {
            "pause" -> desiredPaused = true
            "play" -> if (!state.value.localHold) desiredPaused = false
            "seek" -> {
                val duration = player.sample().media?.duration ?: return
                pendingSeek = request.position.coerceIn(0.0, duration)
                pendingSeekSince = now()
                seekRevision++
                player.seek(pendingSeek!!)
            }
            "speed" -> baseSpeed = request.speed
            else -> return
        }
        lastBroadcast = -10_000
    }

    fun setSharedControls(value: Boolean) {
        if (active && state.value.host) {
            mutableState.value = state.value.copy(sharedControls = value)
            lastBroadcast = -10_000
        }
    }

    fun setWaitForEveryone(value: Boolean) {
        if (active && state.value.host) {
            mutableState.value = state.value.copy(waitForEveryone = value)
            lastBroadcast = -10_000
        }
    }

    fun confirmSameVideo() {
        val local = player.sample().media ?: return
        val remote = state.value.media ?: return
        if (local.compatibleDuration(remote)) {
            acceptedMedia = local.key + "|" + remote.key
            lastStatus = -10_000
            appliedSeekRevision = -1
        }
    }

    fun resync() {
        if (!active) return
        lastSeek = -10_000
        appliedSeekRevision = -1
        lastPing = -10_000
        lastStatus = -10_000
        lastBroadcast = -10_000
    }

    fun isManagedSpeed(value: Double): Boolean = active && appliedSpeed?.let { abs(value - it) < 0.0001 } == true
    fun preferredSpeed(): Double = if (active) baseSpeed else originalSpeed

    private fun receive(sender: String, incoming: WatchMessage) {
        if (!active || sender == transport?.publicKey || !incoming.valid()) return
        val isOwner = invite?.owns(sender) == true
        if (!state.value.host && !isOwner) return
        if (incoming.sequence <= (departed[sender] ?: 0L)) return
        if (state.value.host && sender !in peers && peers.size >= 7) return
        val lane = sender + "|" + incoming.type.name
        if (incoming.sequence <= (sequences[lane] ?: 0L)) {
            if (state.value.host && incoming.type == WatchMessageType.Command) lastBroadcast = -10_000
            return
        }
        sequences[lane] = incoming.sequence
        if (sequences.size >
            128
        ) {
            sequences.remove(sequences.keys.first { it != lane && it.substringBefore('|') !in peers })
        }
        if (isOwner && incoming.type == WatchMessageType.Timeline) lastHostMessage = now()
        when (incoming.type) {
            WatchMessageType.Hello, WatchMessageType.Status -> if (state.value.host) {
                val same = incoming.media?.key == player.sample().media?.key
                peers[sender] =
                    WatchPeerStatus(incoming.name, incoming.ready && same, incoming.buffering, incoming.media) to now()
                lastBroadcast = -10_000
            }
            WatchMessageType.Ping -> if (state.value.host) {
                send(message(WatchMessageType.Pong).copy(target = sender, ping = incoming.ping))
            }
            WatchMessageType.Pong -> if (isOwner &&
                incoming.target == transport?.publicKey &&
                pings.remove(incoming.ping)
            ) {
                clock.accept(incoming.ping, now(), incoming.at)
            }
            WatchMessageType.Timeline -> if (isOwner) {
                if (incoming.peers.size >= 8 && transport?.publicKey !in incoming.peers) {
                    stop(WatchPhase.Failed, "La stanza è piena: possono partecipare fino a 8 persone.")
                    return
                }
                timeline = incoming
                baseSpeed = incoming.speed
                val own = transport?.publicKey
                pendingCommand?.let { pending ->
                    if ((incoming.acknowledgements[own] ?: 0) >= pending.sequence) pendingCommand = null
                }
                mutableState.value = state.value.copy(
                    media = incoming.media,
                    sharedControls = incoming.sharedControls,
                    waitForEveryone = incoming.waitForEveryone,
                    playRequested = incoming.playRequested,
                    members = incoming.peers.map { (id, peer) ->
                        WatchMember(id, peer.name, peer.ready, peer.buffering)
                    },
                )
            }
            WatchMessageType.Command -> if (state.value.host && sender in peers) {
                val allowed = state.value.sharedControls &&
                    (
                        incoming.command == "pause" ||
                            incoming.media?.key == player.sample().media?.key
                        )
                if (allowed) applyCommand(incoming)
                acknowledgements[sender] = incoming.sequence
                lastBroadcast = -10_000
            }
            WatchMessageType.Leave -> if (state.value.host) {
                departed[sender] = incoming.sequence
                if (departed.size > 16) departed.remove(departed.keys.first())
                peers.remove(sender)
                acknowledgements.remove(sender)
                lastBroadcast = -10_000
            }
            WatchMessageType.Closed -> if (isOwner) {
                stop(WatchPhase.Closed, "Chi ha creato la stanza l'ha chiusa. Il video è in pausa.")
            }
        }
    }

    private fun tick() {
        if (wallMillis() >= (invite?.expires ?: 0)) {
            send(message(if (state.value.host) WatchMessageType.Closed else WatchMessageType.Leave))
            stop(WatchPhase.Closed, "La stanza è scaduta. Puoi crearne una nuova.")
            return
        }
        val time = now()
        val sample = player.sample()
        if (sample.media?.valid() == false) {
            stop(WatchPhase.Failed, "Questo contenuto non può essere condiviso nella stanza.")
            return
        }
        if (state.value.relayCount == 0) {
            expectsPaused = true
            player.pause(true)
            setSpeed(baseSpeed)
            mutableState.value = state.value.copy(
                phase = if (time - started < 12_000) WatchPhase.Connecting else WatchPhase.Reconnecting,
                message = if (time - started <
                    12_000
                ) {
                    "Connessione alla stanza…"
                } else {
                    "Connessione assente. Riprovo automaticamente…"
                },
            )
            return
        }
        if (state.value.host) tickHost(sample, time) else tickGuest(sample, time)
    }

    private fun tickHost(sample: WatchPlayback, time: Long) {
        peers.filterValues { time - it.second > 20_000 }.keys.toList().forEach {
            peers.remove(it)
            acknowledgements.remove(it)
        }
        val media = sample.media
        if (state.value.media?.key != media?.key) {
            seekRevision++
            pendingSeek = null
            // Old readiness must not release a new episode.
            peers.replaceAll { _, entry -> entry.first.copy(ready = false) to entry.second }
            lastBroadcast = -10_000
        }
        pendingSeek?.let {
            if (sample.ready &&
                !sample.buffering &&
                abs(sample.position - it) < 1.5 ||
                time - pendingSeekSince > 10_000
            ) {
                pendingSeek = null
            }
        }
        val position = (pendingSeek ?: sample.position).coerceIn(0.0, media?.duration ?: 0.0)
        val waiting = state.value.waitForEveryone && peers.values.any { !it.first.ready || it.first.buffering }
        val buffering = !sample.ready || sample.buffering || pendingSeek != null || waiting
        val paused = desiredPaused || buffering || state.value.localHold || sample.ended
        expectsPaused = paused
        if (sample.paused != paused) player.pause(paused)
        setSpeed(baseSpeed)
        val hostStatus = WatchPeerStatus(name, sample.ready && !state.value.localHold, sample.buffering, media)
        val all = linkedMapOf(transport!!.publicKey to hostStatus)
        peers.forEach { (id, peer) -> all[id] = peer.first }
        val phase = when {
            peers.isEmpty() && paused -> WatchPhase.Waiting
            buffering -> WatchPhase.Buffering
            paused -> WatchPhase.Paused
            else -> WatchPhase.Playing
        }
        mutableState.value = state.value.copy(
            phase = phase,
            media = media,
            playRequested = !desiredPaused,
            members = all.map { (id, peer) -> WatchMember(id, peer.name, peer.ready, peer.buffering) },
            message = when {
                state.value.localHold -> "In pausa su questo telefono. Tocca Riprendi quando vuoi tornare."
                sample.ended -> "Episodio terminato. Aspettiamo il prossimo."
                peers.isEmpty() -> "Condividi il codice con il tuo amico."
                waiting -> "Aspettiamo che tutti siano pronti."
                buffering -> "Caricamento del video…"
                paused -> "Tutti pronti. Puoi avviare la riproduzione."
                else -> "State guardando insieme."
            },
        )
        // A full snapshot repairs missed/lost commands and reconnections, without replaying old video actions.
        if (time - lastBroadcast >= 2000) {
            send(
                message(WatchMessageType.Timeline).copy(
                    media = media, position = position, paused = paused, playRequested = !desiredPaused,
                    speed = baseSpeed, seekRevision = seekRevision,
                    buffering = buffering, sharedControls = state.value.sharedControls,
                    waitForEveryone = state.value.waitForEveryone,
                    peers = all.mapValues { it.value.copy(media = null) }, acknowledgements = acknowledgements.toMap(),
                ),
            )
            lastBroadcast = time
        }
    }

    private fun tickGuest(sample: WatchPlayback, time: Long) {
        val remote = timeline
        val localMedia = sample.media
        val same = localMedia != null &&
            remote?.media?.let {
                localMedia.matches(it) ||
                    (acceptedMedia == localMedia.key + "|" + it.key && localMedia.compatibleDuration(it))
            } == true
        val localReady = sample.ready && same && !state.value.localHold
        val status = WatchPeerStatus(name, localReady, sample.buffering, localMedia)
        if (status != lastStatusValue || time - lastStatus >= 4000) {
            send(
                message(if (lastStatusValue == null) WatchMessageType.Hello else WatchMessageType.Status).copy(
                    media = localMedia,
                    ready = localReady,
                    buffering = sample.buffering,
                    name = name,
                ),
            )
            lastStatusValue = status
            lastStatus = time
        }
        if (time - lastPing >= if (clock.ready) 10_000 else 1500) {
            pings.add(time)
            if (pings.size > 8) pings.remove(pings.min())
            send(message(WatchMessageType.Ping).copy(ping = time))
            lastPing = time
        }
        pendingCommand?.let {
            if (time - pendingSince > 8000) {
                pendingCommand = null
                closedMessage = "Comando non confermato. Controlla la connessione e riprova."
            } else if (time - lastRetry >= 1500) {
                send(it)
                lastRetry = time
            }
        }
        val stale = time - lastHostMessage > 12_000
        if (remote == null || stale || !same || !clock.ready || !localReady) {
            expectsPaused = true
            player.pause(true)
            setSpeed(baseSpeed)
            mutableState.value = state.value.copy(
                phase = when {
                    stale -> WatchPhase.Reconnecting
                    remote == null || !clock.ready -> WatchPhase.Waiting
                    remote.media == null -> WatchPhase.Waiting
                    !same -> WatchPhase.DifferentVideo
                    else -> WatchPhase.Paused
                },
                latencyMs = clock.latency,
                message = when {
                    stale -> "Aspetto chi ha creato la stanza. Il video resta in pausa."
                    remote == null -> "Aspetto il tuo amico. La sua stanza deve essere aperta."
                    remote.media == null -> "Il tuo amico sta scegliendo cosa guardare."
                    !same && sample.ready && localMedia?.key == remote.media.key && localMedia.duration > 0 ->
                        "La durata dei video è diversa. Scegli la stessa versione nelle qualità del player."
                    !same -> "Preparazione dell'episodio scelto dal tuo amico…"
                    state.value.localHold -> "In pausa su questo telefono. Tocca Riprendi quando vuoi tornare."
                    else -> "Preparazione della sincronizzazione…"
                },
            )
            return
        }
        val elapsed = ((time + clock.offset - remote.at) / 1000.0).coerceIn(0.0, 12.0)
        val target = (remote.position + if (remote.paused) 0.0 else elapsed * remote.speed).coerceIn(
            0.0,
            localMedia!!.duration,
        )
        val pausePending = pendingCommand?.command == "pause"
        val shouldPause = remote.paused || sample.buffering || pausePending
        expectsPaused = shouldPause
        if (sample.paused != shouldPause) player.pause(shouldPause)
        if (!sample.buffering && sample.ready) {
            val forced = remote.seekRevision != appliedSeekRevision
            val correction = WatchSynchronizer.correct(
                sample.position,
                target,
                baseSpeed,
                shouldPause,
                forced || time - lastSeek > 5000,
            )
            if (forced || correction.seek != null) {
                if (abs(sample.position - target) > 0.18) {
                    player.seek(target)
                    lastSeek = time
                }
                appliedSeekRevision = remote.seekRevision
            }
            setSpeed(correction.speed)
        } else {
            setSpeed(baseSpeed)
        }
        mutableState.value = state.value.copy(
            phase = when {
                remote.buffering || sample.buffering -> WatchPhase.Buffering
                shouldPause -> WatchPhase.Paused
                else -> WatchPhase.Playing
            },
            latencyMs = clock.latency,
            driftMs = ((target - sample.position) * 1000).roundToLong(),
            message = when {
                closedMessage.isNotEmpty() -> closedMessage
                remote.buffering || sample.buffering -> "Aspettiamo che tutti siano pronti."
                shouldPause -> "La stanza è in pausa."
                else -> "State guardando insieme."
            },
        )
    }

    private fun setSpeed(speed: Double) {
        if (appliedSpeed == null || abs(appliedSpeed!! - speed) > 0.0001) {
            appliedSpeed = speed
            player.speed(speed)
        }
    }

    private fun message(type: WatchMessageType): WatchMessage = WatchMessage(
        type = type,
        sequence = ++sequence,
        at = now(),
    )
    private fun send(message: WatchMessage) = transport?.send(message)
}

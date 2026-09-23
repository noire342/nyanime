package eu.kanade.tachiyomi.data.watch

import eu.kanade.tachiyomi.data.reading.ReadingEnvelope
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
    private val onSessionStarted: (WatchInvite, WatchIdentity, String) -> Unit = { _, _, _ -> },
    private val onSessionEnded: () -> Unit = {},
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
    private val managedSpeeds = ArrayDeque<Double>()
    private var seekRevision = 0L
    private var appliedSeekRevision = -1L
    private var pendingSeek: Double? = null
    private var pendingSeekSince = 0L
    private var acceptedMedia: String? = null
    private var closedMessage = ""
    private var failedCommand: WatchMessage? = null
    private var waitingSince: Long? = null
    private var feedbackMediaKey: String? = null
    private var lastManualRetry = -10_000L
    private var activitySequence = 0L
    private var lastSeenActivity = 0L
    private var activityUntil = 0L
    private var publishedActivity: WatchActivity? = null
    private val startGate = WatchStartGate()
    private val driftCorrector = WatchDriftCorrector()
    private var pausedBy = ""
    private var cueSequence = 0L
    private var skipKey: String? = null
    private var skipCue: WatchSkip? = null
    private var skipWait: Long? = null
    private val suppressedSkips = linkedSetOf<String>()
    private var nextCue: WatchNext? = null
    private var suppressedNext: String? = null
    private var advancedFrom: String? = null
    var readingMode: Boolean = false
        private set
    var onReadingMessage: ((String, ReadingEnvelope, Boolean) -> Unit)? = null

    fun setReadingMode(enabled: Boolean) {
        if (readingMode == enabled) return
        if (enabled && active) {
            if (state.value.host) {
                hold()
            } else {
                player.pause(true)
                pendingCommand = null
                mutableState.value = state.value.copy(localHold = true, pendingPlaybackPaused = null)
            }
        }
        readingMode = enabled
        lastStatus = -10_000
        lastBroadcast = -10_000
    }

    fun sendReading(value: ReadingEnvelope, target: String = "") {
        if (active && (state.value.host || state.value.readingSupported) && value.valid()) {
            send(message(WatchMessageType.Status).copy(reading = value, target = target))
        }
    }
    var expectsPaused: Boolean = true
        private set

    fun create(displayName: String, relays: List<String> = WatchInvite.defaultRelays) = start(displayName) { identity ->
        WatchInvite.create(identity.publicKey, wallMillis(), relays)
    }

    fun join(code: String, displayName: String) = start(displayName) { WatchInvite.parse(code, wallMillis()) }

    fun resume(room: WatchInvite, identitySecret: ByteArray, displayName: String) =
        start(displayName, WatchIdentity(identitySecret)) { room.validate(wallMillis()) }

    private fun start(
        displayName: String,
        restoredIdentity: WatchIdentity? = null,
        makeInvite: (WatchIdentity) -> WatchInvite,
    ) {
        if (active) {
            restoredIdentity?.clear()
            return
        }
        var identity: WatchIdentity? = restoredIdentity
        try {
            val sample = if (readingMode) WatchPlayback(null, 0.0, true, false, false, 1.0) else player.sample()
            identity = identity ?: WatchIdentity()
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
                localMemberId = network.publicKey,
                readingSupported = host,
                readingVersion = if (host) 2 else 0,
            )
            onSessionStarted(room, identity, name)
            if (!readingMode) player.pause(true)
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
        startGate.reset()
        driftCorrector.reset()
        pausedBy = ""
        cueSequence = 0L
        skipKey = null
        skipCue = null
        skipWait = null
        suppressedSkips.clear()
        nextCue = null
        suppressedNext = null
        advancedFrom = null
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
        managedSpeeds.clear()
        seekRevision = 0
        appliedSeekRevision = -1
        closedMessage = ""
        failedCommand = null
        waitingSince = null
        feedbackMediaKey = null
        lastManualRetry = -10_000
        activitySequence = 0
        lastSeenActivity = 0
        activityUntil = 0
        publishedActivity = null
    }

    fun leave() {
        if (!active) {
            mutableState.value = WatchRoomState()
            return
        }
        send(message(if (state.value.host) WatchMessageType.Closed else WatchMessageType.Leave))
        onSessionEnded()
        stop(WatchPhase.Idle, "")
    }

    private fun stop(phase: WatchPhase, reason: String) {
        if (phase == WatchPhase.Closed) onSessionEnded()
        generation++
        ticker?.cancel()
        ticker = null
        transport?.close()
        transport = null
        invite = null
        pendingCommand = null
        if (!readingMode) {
            player.pause(true)
            player.speed(originalSpeed)
        }
        appliedSpeed = null
        mutableState.value = WatchRoomState(phase = phase, message = reason)
    }

    /** Timers, audio-focus loss and backgrounding can only be cleared by an explicit local Play. */
    fun hold() {
        if (!active || readingMode) return
        expectsPaused = true
        cancelNext()
        startGate.reset()
        mutableState.value = state.value.copy(localHold = true)
        player.pause(true)
        requestPause(true)
        lastStatus = -10_000
    }

    fun playerAttached() {
        // A newly loaded native player must not start ahead of the room readiness barrier.
        expectsPaused = true
        appliedSpeed = null
        driftCorrector.reset()
        startGate.reset()
        appliedSeekRevision = -1
        lastStatus = -10_000
        lastBroadcast = -10_000
    }

    fun resumeByUser(): Boolean {
        if (!active || readingMode) return false
        player.userResumed()
        mutableState.value = state.value.copy(localHold = false)
        lastStatus = -10_000
        return requestPause(false)
    }

    fun requestPause(paused: Boolean): Boolean = command(if (paused) "pause" else "play")
    fun requestSeek(seconds: Double): Boolean = command("seek", seconds)
    fun requestSpeed(speed: Double): Boolean = command("speed", speed)

    fun offerSkip(key: String?, label: String = "", target: Double = 0.0, autoSeconds: Int? = null) {
        if (!active || !state.value.host || readingMode) return
        if (player.sample().ended) {
            dismissSkip()
            return
        }
        if (key == null) {
            if (skipCue != null) lastBroadcast = -10_000
            skipCue = null
            skipKey = null
            return
        }
        if (key == skipKey || key in suppressedSkips || !target.isFinite() || target <= 0) return
        skipKey = key
        skipWait = autoSeconds?.coerceIn(3, 30)?.times(1000L)
        skipCue = WatchSkip(++cueSequence, label.take(100).ifBlank { "Salta sigla" }, target)
        lastBroadcast = -10_000
    }

    fun requestSkip() = command("skip", cue = state.value.skip?.id ?: 0)
    fun cancelSkip() = command("cancel_skip", cue = state.value.skip?.id ?: 0)
    fun playNextNow() = command("next", cue = state.value.next?.id ?: 0)
    fun cancelNext() = command("cancel_next", cue = state.value.next?.id ?: 0)

    private fun dismissSkip() {
        skipKey?.let {
            suppressedSkips.add(it)
            if (suppressedSkips.size > 16) suppressedSkips.remove(suppressedSkips.first())
        }
        skipCue = null
    }

    private fun command(action: String, value: Double = 0.0, cue: Long = 0): Boolean {
        if (!active || readingMode) return false
        if (!value.isFinite()) return true
        if (!state.value.host && !state.value.sharedControls) {
            mutableState.value = state.value.copy(message = "I comandi sono gestiti da chi ha creato la stanza.")
            return true
        }
        if (action == "play" && state.value.localHold) return true
        if (action !in listOf("pause", "cancel_skip", "cancel_next") &&
            state.value.phase == WatchPhase.DifferentVideo
        ) {
            return true
        }
        val sample = player.sample()
        failedCommand = null
        closedMessage = ""
        mutableState.value = state.value.copy(commandFailed = false)
        val request = message(WatchMessageType.Command).copy(
            command = action,
            cueId = cue,
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
            applyCommand(request, name)
        } else {
            pendingCommand = request
            pendingSince = now()
            lastRetry = now()
            mutableState.value = state.value.copy(
                pendingPlaybackPaused = when (action) {
                    "play" -> false
                    "pause" -> true
                    else -> null
                },
                resumeSeconds = if (action == "pause") null else state.value.resumeSeconds,
            )
            send(request)
            // Pausing is safe immediately. Play/seek wait for the creator's authoritative state.
            if (action == "pause") player.pause(true)
        }
        return true
    }

    private fun applyCommand(
        request: WatchMessage,
        actor: String,
        actorId: String = transport?.publicKey.orEmpty(),
        announce: Boolean = true,
    ) {
        when (request.command) {
            "pause" -> {
                desiredPaused = true
                mutableState.value = state.value.copy(playRequested = false, resumeSeconds = null)
                pausedBy = actor
                startGate.reset()
                suppressedNext = player.sample().media?.key
                nextCue = null
                skipCue = skipCue?.copy(deadline = null)
            }
            "play" -> if (!state.value.localHold) {
                desiredPaused = false
                mutableState.value =
                    state.value.copy(playRequested = true, message = "Preparazione della riproduzione…")
                pausedBy = ""
            }
            "skip" -> {
                val cue = skipCue?.takeIf { it.id == request.cueId } ?: return
                dismissSkip()
                applyCommand(request.copy(command = "seek", position = cue.target), actor, actorId, announce)
            }
            "cancel_skip" -> {
                if (skipCue?.id != request.cueId) return
                dismissSkip()
            }
            "next" -> {
                val cue = nextCue?.takeIf { it.id == request.cueId } ?: return
                // Preparation and safety checks still gate the actual episode change.
                nextCue = cue.copy(deadline = now())
            }
            "cancel_next" -> {
                if (nextCue?.id != request.cueId) return
                suppressedNext = player.sample().media?.key
                nextCue = null
            }
            "seek" -> {
                startGate.reset()
                driftCorrector.reset()
                nextCue = null
                suppressedNext = null
                advancedFrom = null
                val duration = player.sample().media?.duration ?: return
                pendingSeek = request.position.coerceIn(0.0, duration)
                pendingSeekSince = now()
                seekRevision++
                player.seek(pendingSeek!!)
            }
            "speed" -> baseSpeed = request.speed
            else -> return
        }
        if (announce && request.command in listOf("pause", "play", "seek", "speed")) {
            player.sample().media?.let { media ->
                val activity = WatchActivity(
                    ++activitySequence,
                    now(),
                    actorId,
                    actor.ifBlank { "Spettatore" }.take(32),
                    request.command,
                    media.key,
                    if (request.command == "speed") request.speed else request.position,
                )
                publishedActivity = activity
                activityUntil = now() + 3500
                mutableState.value = state.value.copy(activity = activity)
            }
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
        if (!active || readingMode) return
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
        driftCorrector.reset()
        appliedSeekRevision = -1
        lastPing = -10_000
        lastStatus = -10_000
        lastBroadcast = -10_000
    }

    /** Explicit retry keeps episode identity, command ordering and the existing local safety hold. */
    fun retryFailedCommand() {
        if (!active || readingMode) return
        val failed = failedCommand ?: return
        if (failed.media?.key != state.value.media?.key || failed.media?.key != player.sample().media?.key) {
            failedCommand = null
            mutableState.value = state.value.copy(commandFailed = false)
            return
        }
        if (now() - lastManualRetry < 2000) return
        lastManualRetry = now()
        transport?.retryUnavailable()
        resync()
        if (failed.command == "play" && state.value.localHold) return
        command(failed.command, if (failed.command == "speed") failed.speed else failed.position, failed.cueId)
    }

    fun retryConnection() {
        if (!active || now() - lastManualRetry < 2000) return
        lastManualRetry = now()
        waitingSince = now()
        mutableState.value = state.value.copy(waitingSeconds = 0)
        transport?.retryUnavailable()
        resync()
    }

    fun isManagedSpeed(value: Double): Boolean = active && managedSpeeds.any { abs(value - it) < 0.0001 }
    fun preferredSpeed(): Double = if (active) baseSpeed else originalSpeed

    private fun receive(sender: String, incoming: WatchMessage) {
        if (!active || sender == transport?.publicKey || !incoming.valid()) return
        val isOwner = invite?.owns(sender) == true
        if (!state.value.host && !isOwner) return
        incoming.reading?.let {
            if ((incoming.target.isEmpty() || incoming.target == transport?.publicKey) &&
                (isOwner || sender in peers)
            ) {
                onReadingMessage?.invoke(sender, it, isOwner)
            }
            return
        }
        if (incoming.coordinationVersion != 2) {
            stop(WatchPhase.Failed, "Per guardare insieme aggiornate Nyanime su entrambi i telefoni.")
            return
        }
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
                val same = !readingMode && incoming.media?.key == player.sample().media?.key
                peers[sender] =
                    WatchPeerStatus(
                        incoming.name,
                        incoming.ready && same,
                        incoming.buffering,
                        incoming.media,
                        incoming.problem,
                        incoming.canAdvance,
                        incoming.preparedNextKey,
                        incoming.nextProblem,
                        incoming.readingMode,
                        incoming.position.takeIf { incoming.media != null && !incoming.readingMode },
                    ) to now()
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
                val alreadyConnected = timeline != null
                timeline = incoming
                mutableState.value = state.value.copy(
                    readingSupported = incoming.readingVersion in 1..2,
                    readingVersion = incoming.readingVersion,
                )
                baseSpeed = incoming.speed
                val own = transport?.publicKey
                pendingCommand?.let { pending ->
                    if ((incoming.acknowledgements[own] ?: 0) >= pending.sequence) {
                        pendingCommand = null
                        failedCommand = null
                        closedMessage = ""
                        mutableState.value = state.value.copy(commandFailed = false)
                    }
                }
                failedCommand?.let { failed ->
                    if ((incoming.acknowledgements[own] ?: 0) >= failed.sequence) {
                        failedCommand = null
                        closedMessage = ""
                        mutableState.value = state.value.copy(commandFailed = false)
                    }
                }
                incoming.activity?.takeIf { it.id > lastSeenActivity }?.let { activity ->
                    lastSeenActivity = activity.id
                    val age = now() + clock.offset - activity.at
                    if (alreadyConnected &&
                        clock.ready &&
                        age in -500..3500 &&
                        activity.mediaKey == incoming.media?.key
                    ) {
                        activityUntil = now() + (3500 - age.coerceAtLeast(0))
                        mutableState.value = state.value.copy(activity = activity)
                    }
                }
                mutableState.value = state.value.copy(
                    media = incoming.media,
                    sharedControls = incoming.sharedControls,
                    waitForEveryone = incoming.waitForEveryone,
                    playRequested = incoming.playRequested,
                    pendingPlaybackPaused = pendingCommand?.command?.let {
                        when (it) {
                            "play" -> false
                            "pause" -> true
                            else -> null
                        }
                    },
                    skip = incoming.skip,
                    upcoming = incoming.upcoming,
                    next = incoming.next,
                    members = incoming.peers.map { (id, peer) ->
                        WatchMember(
                            id,
                            peer.name,
                            peer.ready,
                            peer.buffering,
                            peer.problem,
                            peer.nextProblem,
                            peer.positionSeconds,
                            peer.reading,
                        )
                    },
                )
            }
            WatchMessageType.Command -> if (state.value.host && sender in peers) {
                val allowed = !readingMode &&
                    state.value.sharedControls &&
                    (
                        incoming.command == "pause" ||
                            (incoming.command == "cancel_skip" && incoming.cueId == skipCue?.id) ||
                            (incoming.command == "cancel_next" && incoming.cueId == nextCue?.id) ||
                            incoming.media?.key == player.sample().media?.key
                        )
                if (allowed && !readingMode) applyCommand(incoming, peers.getValue(sender).first.name, sender)
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
                stop(
                    WatchPhase.Closed,
                    if (readingMode) {
                        "Chi ha creato la stanza l'ha chiusa. Puoi continuare a leggere."
                    } else {
                        "Chi ha creato la stanza l'ha chiusa. Il video è in pausa."
                    },
                )
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
        if (readingMode) {
            tickReading(time)
            return
        }
        val sample = player.sample()
        if (sample.media?.valid() == false) {
            stop(WatchPhase.Failed, "Questo contenuto non può essere condiviso nella stanza.")
            return
        }
        // Expiry also runs when no relay is connected; a spinner must not conceal a lost command.
        pendingCommand?.takeIf { time - pendingSince > 8000 }?.let {
            failedCommand = it
            pendingCommand = null
            closedMessage = "Comando non confermato. Controlla la connessione e riprova."
            mutableState.value = state.value.copy(pendingPlaybackPaused = null, commandFailed = true)
        }
        if (state.value.relayCount == 0) {
            startGate.reset()
            driftCorrector.reset()
            // Reconnection must receive a new host snapshot before reusing its playback intent.
            if (!state.value.host) timeline = null
            skipCue = skipCue?.copy(deadline = null)
            nextCue = nextCue?.copy(deadline = null)
            expectsPaused = true
            player.pause(true)
            setSpeed(baseSpeed)
            mutableState.value = state.value.copy(
                phase = if (time - started < 12_000) WatchPhase.Connecting else WatchPhase.Reconnecting,
                resumeSeconds = null,
                skipSeconds = null,
                nextSeconds = null,
                skip = state.value.skip?.copy(deadline = null),
                next = state.value.next?.copy(deadline = null),
                message = if (time - started <
                    12_000
                ) {
                    "Connessione alla stanza…"
                } else {
                    "Connessione assente. Riprovo automaticamente…"
                },
            )
            updateFeedback(time)
            return
        }
        if (state.value.host) tickHost(sample, time) else tickGuest(sample, time)
        updateFeedback(time)
    }

    private fun tickReading(time: Long) {
        expectsPaused = true
        peers.filterValues { time - it.second > 20_000 }.keys.toList().forEach {
            peers.remove(it)
            acknowledgements.remove(it)
        }
        if (state.value.host) {
            val all = linkedMapOf(transport!!.publicKey to WatchPeerStatus(name, false, false, reading = true))
            peers.forEach { (id, peer) -> all[id] = peer.first }
            mutableState.value = state.value.copy(
                media = null,
                playRequested = false,
                phase = WatchPhase.Waiting,
                members = all.map { (id, peer) ->
                    WatchMember(id, peer.name, peer.ready, peer.buffering, reading = peer.reading)
                },
                message = "Stanza aperta · lettura libera",
                upcoming = null,
                skipSeconds = null,
                nextSeconds = null,
                resumeSeconds = null,
                skip = null,
                next = null,
            )
            if (time - lastBroadcast >= 2000) {
                send(
                    message(WatchMessageType.Timeline).copy(
                        peers = all,
                        readingMode = true,
                        sharedControls = state.value.sharedControls,
                        waitForEveryone = state.value.waitForEveryone,
                    ),
                )
                lastBroadcast = time
            }
        } else if (time - lastStatus >= 2000) {
            send(message(WatchMessageType.Status).copy(name = name, readingMode = true))
            lastStatus = time
        }
    }

    private fun updateFeedback(time: Long) {
        val room = state.value
        if (room.media?.key != feedbackMediaKey) {
            feedbackMediaKey = room.media?.key
            waitingSince = null
            failedCommand = null
            closedMessage = ""
        }
        val waiting = room.preparingPlayback ||
            room.phase in listOf(WatchPhase.Connecting, WatchPhase.Reconnecting, WatchPhase.DifferentVideo)
        if (!waiting) {
            waitingSince = null
        } else if (waitingSince == null) {
            waitingSince = time
        }
        mutableState.value = room.copy(
            waitingSeconds = waitingSince?.let { ((time - it) / 1000).toInt().coerceAtLeast(0) } ?: 0,
            commandFailed = failedCommand != null,
            activity = room.activity?.takeIf { time < activityUntil && it.mediaKey == room.media?.key },
        )
    }

    private fun tickHost(sample: WatchPlayback, time: Long) {
        peers.filterValues { time - it.second > 20_000 }.keys.toList().forEach {
            peers.remove(it)
            acknowledgements.remove(it)
        }
        val media = sample.media
        if (sample.ended) dismissSkip()
        if (state.value.media?.key != media?.key) {
            seekRevision++
            pendingSeek = null
            startGate.reset()
            skipCue = null
            skipKey = null
            suppressedSkips.clear()
            nextCue = null
            suppressedNext = null
            advancedFrom = null
            peers.replaceAll { _, entry -> entry.first.copy(ready = false) to entry.second }
            lastBroadcast = -10_000
        }
        pendingSeek?.let {
            if ((sample.ready && !sample.buffering && abs(sample.position - it) < 1.5) ||
                time - pendingSeekSince > 10_000
            ) {
                pendingSeek = null
            }
        }
        val position = (pendingSeek ?: sample.position).coerceIn(0.0, media?.duration ?: 0.0)
        val waiting = state.value.waitForEveryone &&
            peers.values.any {
                !it.first.reading && (!it.first.ready || it.first.buffering || time - it.second > 6000)
            }
        val buffering = !sample.ready || sample.buffering || pendingSeek != null || waiting
        val previousDeadline = startGate.deadline
        val running = startGate.update(
            !buffering && !state.value.localHold && !sample.ended,
            !desiredPaused,
            time,
        )
        if (previousDeadline != startGate.deadline) lastBroadcast = -10_000
        val paused = !running
        expectsPaused = paused
        if (sample.paused != paused) player.pause(paused)
        setSpeed(baseSpeed)

        skipCue?.let { cue ->
            if (paused) {
                skipCue = cue.copy(deadline = null)
            } else if (cue.deadline == null && skipWait != null) {
                skipCue = cue.copy(deadline = time + skipWait!!)
                lastBroadcast = -10_000
            } else if (cue.deadline != null && time >= cue.deadline) {
                applyCommand(
                    message(WatchMessageType.Command).copy(command = "skip", cueId = cue.id),
                    name,
                    announce = false,
                )
                tickHost(player.sample(), time)
                return
            }
        }

        val upcoming = sample.upcoming
        if (!sample.ended) {
            nextCue = null
            suppressedNext = null
        } else if (upcoming != null && media != null && suppressedNext != media.key && advancedFrom != media.key) {
            if (nextCue?.media?.key != upcoming.key && sample.canAdvance) {
                nextCue = WatchNext(++cueSequence, upcoming)
                lastBroadcast = -10_000
            }
            val everyonePrepared = peers.values.all {
                it.first.reading ||
                    (
                        it.first.preparedNextKey == upcoming.key &&
                            it.first.canAdvance &&
                            it.first.nextProblem == WatchProblem.None &&
                            time - it.second <= 6000
                        )
            }
            nextCue?.let { cue ->
                if (!sample.canAdvance || state.value.localHold || !everyonePrepared) {
                    if (cue.deadline != null) lastBroadcast = -10_000
                    nextCue = cue.copy(deadline = null)
                } else if (cue.deadline == null) {
                    nextCue = cue.copy(deadline = time + 10_000)
                    lastBroadcast = -10_000
                } else if (time >= cue.deadline) {
                    advancedFrom = media.key
                    nextCue = null
                    desiredPaused = false
                    startGate.reset()
                    player.advance(upcoming)
                }
            }
        } else {
            nextCue = null
        }

        val problem = when {
            state.value.localHold -> WatchProblem.LocalPause
            sample.problem != WatchProblem.None -> sample.problem
            sample.buffering -> WatchProblem.Buffering
            !sample.ready -> WatchProblem.Opening
            else -> WatchProblem.None
        }
        val all = linkedMapOf(
            transport!!.publicKey to WatchPeerStatus(
                name,
                sample.ready && !state.value.localHold,
                sample.buffering,
                media,
                problem,
                sample.canAdvance,
                upcoming?.key,
                reading = readingMode,
                positionSeconds = sample.position.takeIf { media != null && !readingMode },
            ),
        )
        peers.forEach { (id, peer) ->
            all[id] = if (time - peer.second > 6000) {
                peer.first.copy(ready = false, problem = WatchProblem.Connection)
            } else {
                peer.first
            }
        }
        val blocking = all.entries.firstOrNull {
            it.key != transport!!.publicKey && !it.value.reading && (!it.value.ready || it.value.buffering)
        }?.value
        val nextBlocking = all.entries.firstOrNull {
            it.key != transport!!.publicKey &&
                !it.value.reading &&
                (
                    it.value.nextProblem != WatchProblem.None ||
                        it.value.preparedNextKey != upcoming?.key ||
                        !it.value.canAdvance ||
                        it.value.problem == WatchProblem.Connection
                    )
        }?.value
        val countdown = remainingSeconds(startGate.deadline, time)?.takeIf { it > 0 }
        val phase = when {
            peers.isEmpty() && desiredPaused -> WatchPhase.Waiting
            buffering -> WatchPhase.Buffering
            countdown != null -> WatchPhase.Starting
            paused -> WatchPhase.Paused
            else -> WatchPhase.Playing
        }
        val statusText = when {
            state.value.localHold -> "In pausa su questo telefono. Tocca Riprendi quando vuoi tornare."
            nextCue != null && nextBlocking != null ->
                nextBlocking.name +
                    ": " +
                    if (nextBlocking.problem == WatchProblem.Connection) {
                        nextBlocking.problem.description()
                    } else if (nextBlocking.nextProblem != WatchProblem.None) {
                        nextBlocking.nextProblem.description()
                    } else if (!nextBlocking.canAdvance) {
                        "non è pronto per il prossimo episodio"
                    } else {
                        "preparazione del prossimo episodio"
                    }
            nextCue != null -> "Il prossimo episodio è pronto per tutti."
            sample.ended -> "Episodio terminato."
            waiting && blocking != null ->
                blocking.name +
                    ": " +
                    if (blocking.problem != WatchProblem.None) blocking.problem.description() else "in attesa"
            buffering -> problem.description()
            countdown != null -> "Si riparte insieme tra " + countdown
            paused && !desiredPaused -> "Verifica che tutti siano pronti…"
            paused && pausedBy.isNotBlank() -> pausedBy + " ha messo in pausa."
            paused -> "Tutti pronti. Puoi avviare la riproduzione."
            else -> "State guardando insieme."
        }
        mutableState.value = state.value.copy(
            phase = phase, media = media, playRequested = !desiredPaused,
            members = all.map { (id, peer) ->
                WatchMember(
                    id,
                    peer.name,
                    peer.ready,
                    peer.buffering,
                    peer.problem,
                    peer.nextProblem,
                    peer.positionSeconds,
                    peer.reading,
                )
            },
            message = if (peers.isEmpty() && media == null) "Condividi il codice con il tuo amico." else statusText,
            resumeSeconds = countdown, skip = skipCue,
            skipSeconds = remainingSeconds(skipCue?.deadline, time),
            upcoming = upcoming, next = nextCue, nextSeconds = remainingSeconds(nextCue?.deadline, time),
        )
        val interval = if (countdown != null || skipCue?.deadline != null || nextCue != null) 500 else 2000
        if (time - lastBroadcast >= interval) {
            send(
                message(WatchMessageType.Timeline).copy(
                    media = media, position = position, paused = paused, playRequested = !desiredPaused,
                    speed = baseSpeed, seekRevision = seekRevision, resumeAt = startGate.deadline,
                    buffering = buffering, sharedControls = state.value.sharedControls,
                    waitForEveryone = state.value.waitForEveryone,
                    peers = all.mapValues { it.value.copy(media = null, preparedNextKey = null) },
                    acknowledgements = acknowledgements.toMap(), pausedBy = pausedBy,
                    skip = skipCue, upcoming = upcoming, next = nextCue,
                    activity = publishedActivity?.takeIf { time - it.at <= 3500 && it.mediaKey == media?.key },
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
        val problem = when {
            state.value.localHold -> WatchProblem.LocalPause
            sample.problem != WatchProblem.None -> sample.problem
            !same && sample.ready && localMedia?.key == remote?.media?.key -> WatchProblem.DifferentEdition
            !same -> WatchProblem.Opening
            sample.buffering -> WatchProblem.Buffering
            !sample.ready -> WatchProblem.Opening
            else -> WatchProblem.None
        }
        val status = WatchPeerStatus(
            name,
            localReady,
            sample.buffering,
            localMedia,
            problem,
            sample.canAdvance && !state.value.localHold,
            sample.preparedNextKey,
            sample.nextProblem,
            readingMode,
            sample.position.takeIf { localMedia != null && !readingMode }?.let { (it / 5).toInt() * 5.0 },
        )
        if (status != lastStatusValue || time - lastStatus >= 4000) {
            send(
                message(if (lastStatusValue == null) WatchMessageType.Hello else WatchMessageType.Status).copy(
                    media = localMedia,
                    ready = localReady,
                    buffering = sample.buffering,
                    name = name,
                    problem = problem,
                    canAdvance = status.canAdvance,
                    preparedNextKey = sample.preparedNextKey,
                    nextProblem = sample.nextProblem,
                    readingMode = readingMode,
                    position = status.positionSeconds ?: 0.0,
                ),
            )
            lastStatusValue = status
            lastStatus = time
        }
        if (readingMode) return
        if (time - lastPing >= if (clock.ready) 10_000 else 1500) {
            pings.add(time)
            if (pings.size > 8) pings.remove(pings.min())
            send(message(WatchMessageType.Ping).copy(ping = time))
            lastPing = time
        }
        pendingCommand?.let {
            if (time - lastRetry >= 1500) {
                send(it)
                lastRetry = time
            }
        }
        val hostTime = time + clock.offset
        val stale = time - lastHostMessage > 12_000
        mutableState.value = state.value.copy(
            skipSeconds = remainingSeconds(remote?.skip?.deadline, hostTime).takeUnless { stale || !clock.ready },
            nextSeconds = remainingSeconds(remote?.next?.deadline, hostTime).takeUnless { stale || !clock.ready },
            resumeSeconds = remainingSeconds(remote?.resumeAt, hostTime)?.takeIf {
                it > 0 && !stale && clock.ready && localReady && !sample.buffering && pendingCommand?.command != "pause"
            },
            skip = remote?.skip?.let { if (stale) it.copy(deadline = null) else it },
            next = remote?.next?.let { if (stale) it.copy(deadline = null) else it },
        )
        if (remote == null || stale || !same || !clock.ready || !localReady) {
            expectsPaused = true
            driftCorrector.reset()
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
        val scheduled = remote.resumeAt
        val scheduledPlay = scheduled != null && hostTime >= scheduled && remote.playRequested && !remote.buffering
        val moving = !remote.paused || scheduledPlay
        val elapsed = ((hostTime - maxOf(remote.at, scheduled ?: remote.at)) / 1000.0).coerceIn(0.0, 12.0)
        val target = (remote.position + if (moving) elapsed * remote.speed else 0.0).coerceIn(
            0.0,
            localMedia!!.duration,
        )
        val pausePending = pendingCommand?.command == "pause"
        val shouldPause = !moving || sample.buffering || pausePending
        expectsPaused = shouldPause
        if (sample.paused != shouldPause) player.pause(shouldPause)
        if (!sample.buffering && sample.ready) {
            val forced = remote.seekRevision != appliedSeekRevision
            val correction = driftCorrector.correct(
                sample.position,
                target,
                baseSpeed,
                shouldPause,
                forced || time - lastSeek > 5000,
                time,
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
                state.value.resumeSeconds != null -> WatchPhase.Starting
                shouldPause -> WatchPhase.Paused
                else -> WatchPhase.Playing
            },
            latencyMs = clock.latency,
            driftMs = ((target - sample.position) * 1000).roundToLong(),
            message = when {
                closedMessage.isNotEmpty() -> closedMessage
                remote.buffering || sample.buffering -> {
                    val blocked = remote.peers.values.firstOrNull { !it.ready || it.buffering }
                    blocked?.let { it.name + ": " + it.problem.description() } ?: "Preparazione del video"
                }
                state.value.resumeSeconds != null -> "Si riparte insieme tra " + state.value.resumeSeconds
                shouldPause && remote.pausedBy.isNotBlank() -> remote.pausedBy + " ha messo in pausa."
                shouldPause -> "La stanza è in pausa."
                else -> "State guardando insieme."
            },
        )
    }

    private fun setSpeed(speed: Double) {
        if (appliedSpeed == null || abs(appliedSpeed!! - speed) > 0.0001) {
            appliedSpeed = speed
            managedSpeeds.addLast(speed)
            if (managedSpeeds.size > 16) managedSpeeds.removeFirst()
            player.speed(speed)
        }
    }

    private fun message(type: WatchMessageType): WatchMessage = WatchMessage(
        type = type,
        coordinationVersion = 2,
        readingVersion = 2,
        sequence = ++sequence,
        at = now(),
    )
    private fun send(message: WatchMessage) = transport?.send(message)
}

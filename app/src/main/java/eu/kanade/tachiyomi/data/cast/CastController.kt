package eu.kanade.tachiyomi.data.cast

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.SystemClock
import android.widget.Toast
import androidx.core.content.ContextCompat
import eu.kanade.tachiyomi.animesource.model.HttpServer
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.ui.player.PlayerActivity
import eu.kanade.tachiyomi.ui.player.controls.components.sheets.HosterState
import eu.kanade.tachiyomi.ui.player.loader.EpisodeLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Headers
import okhttp3.OkHttpClient
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.model.asAnimeCover
import tachiyomi.domain.items.episode.interactor.GetEpisode
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.net.Inet4Address

class CastController private constructor(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(CastState())
    val state = mutableState.asStateFlow()
    private val google = GoogleCastTransport(context)
    private val dlna = DlnaTransport(context, ::localAddress)
    private val commands = Mutex()
    private val progress = CastProgressWriter(context)
    private var transport: CastTransport? = null
    private var relay: CastRelay? = null

    @Volatile private var preparingRelay: CastRelay? = null
    private var sourceServer: HttpServer? = null
    private var request: CastRequest? = null
    private var loading: Job? = null
    private var polling: Job? = null
    private var discovery: Job? = null
    private var queueLoading: Job? = null
    private var stopping: Job? = null
    private var generation = 0L
    private var phoneResume: Pair<Long, Long>? = null
    private val volumeQueue = CastVolumeQueue()
    private var volumePump: Job? = null

    init {
        combine(google.devices, dlna.devices) { cast, upnp -> cast + upnp }
            .onEach { devices -> mutableState.update { it.copy(devices = devices) } }.launchIn(scope)
    }

    fun discover() {
        val previousDiscovery = discovery
        previousDiscovery?.cancel()
        dlna.stopDiscovery()
        discovery = scope.launch {
            previousDiscovery?.join()
            mutableState.update { it.copy(discovering = true, error = null) }
            val googleJob = launch {
                try {
                    withTimeout(15_000) { google.discover() }
                    mutableState.update { it.copy(googleAvailable = true) }
                } catch (e: Exception) {
                    if (e is CancellationException && e !is TimeoutCancellationException) throw e
                    mutableState.update { it.copy(googleAvailable = false) }
                }
            }
            try {
                withTimeout(12_000) { dlna.discover() }
                googleJob.join()
                delay(2000)
            } catch (e: Exception) {
                if (e is CancellationException && e !is TimeoutCancellationException) throw e
                mutableState.update { it.copy(error = "Collega telefono e TV alla stessa rete Wi-Fi o Ethernet.") }
            } finally {
                mutableState.update { it.copy(discovering = false) }
            }
        }
    }

    fun stopDiscovery() {
        discovery?.cancel()
        dlna.stopDiscovery()
        google.stopDiscovery()
    }

    fun play(input: CastRequest, device: CastDevice? = state.value.device) {
        if (device == null) return
        val previousLoad = loading
        val previousQueue = queueLoading
        val previousStop = stopping
        previousLoad?.cancel()
        preparingRelay?.revoke()
        previousQueue?.cancel()
        val currentGeneration = ++generation
        mutableState.update { it.copy(connecting = true, error = null, needsReconnect = false) }
        // Start while the user's screen is still foreground, before any source/network suspension.
        try {
            ContextCompat.startForegroundService(context, Intent(context, CastSessionService::class.java))
        } catch (_: Exception) {
            mutableState.update { it.copy(connecting = false, error = "Apri UltraYomi per avviare il Cast") }
            return
        }
        loading = scope.launch {
            var nextRelay: CastRelay? = null
            var nextServer: HttpServer? = null
            var attempted: CastTransport? = null
            var committed = false
            try {
                previousLoad?.join()
                previousQueue?.join()
                previousStop?.join()
                currentCoroutineContext().ensureActive()
                val source = Injekt.get<AnimeSourceManager>().get(input.sourceId)
                check(source != null) { "L'estensione non è disponibile" }
                val http = source as? AnimeHttpSource
                val prepared = withTimeout(30_000) {
                    withContext(Dispatchers.IO) {
                        var video = input.video
                        if (video.usesHttpServer()) {
                            nextServer = http?.createHttpServer() ?: error("Il server della fonte non è disponibile")
                            nextServer!!.start()
                            check(nextServer!!.listeningPort > 0)
                            video = video.copyHttpServer(nextServer!!.listeningPort)
                        }
                        nextRelay = CastRelay(
                            localAddress(),
                            http?.client ?: OkHttpClient(),
                            video.headers ?: http?.headers ?: Headers.Builder().build(),
                            ::openLocal,
                        ).apply { start() }
                        preparingRelay = nextRelay
                        currentCoroutineContext().ensureActive()
                        val (url, mime) = nextRelay!!.prepare(video.videoUrl)
                        CastMedia(
                            input.animeId, input.episodeId, input.sourceId, input.title, input.episodeName,
                            url, mime, input.positionMs.coerceAtLeast(0), input.durationMs.coerceAtLeast(0),
                            video.subtitleTracks.filter { it.url.substringBefore('?').endsWith(".vtt", true) }
                                .map { CastSubtitle(it.lang, nextRelay!!.register(it.url)) },
                            cover = Injekt.get<GetAnime>().await(input.animeId)?.asAnimeCover(),
                            quality = video.videoTitle,
                        )
                    }
                }
                polling?.cancelAndJoin()
                commands.withLock {
                    flush()
                    val selected = if (device.protocol == CastProtocol.GOOGLE_CAST) google else dlna
                    attempted = selected
                    if (transport != null && transport !== selected) withTimeout(8000) { transport!!.stop() }
                    withTimeout(40_000) { selected.load(device, prepared) }
                    check(generation == currentGeneration)
                    withContext(Dispatchers.IO) { closeRelay() }
                    relay = nextRelay
                    preparingRelay = null
                    sourceServer = nextServer
                    nextRelay = null
                    nextServer = null
                    request = input
                    transport = selected
                    committed = true
                    val index = input.playlist.indexOf(input.episodeId)
                    mutableState.update {
                        it.copy(
                            connecting = false, device = device, media = prepared, subtitleIndex = -1,
                            playback = CastPlayback(
                                input.positionMs,
                                input.durationMs,
                                paused = false,
                                buffering = true,
                            ),
                            canNext = index >= 0 && index < input.playlist.lastIndex,
                            canPrevious = index > 0, error = null, needsReconnect = false,
                        )
                    }
                }
                startPolling(currentGeneration)
            } catch (e: Exception) {
                // Once load has touched a receiver the old session cannot safely be restored.
                if (attempted != null && !committed) {
                    withContext(NonCancellable) {
                        commands.withLock {
                            runCatching { withTimeout(8000) { attempted!!.stop() } }
                            if (transport !== attempted) runCatching { withTimeout(8000) { transport?.stop() } }
                            transport = null
                            request = null
                            withContext(Dispatchers.IO) { closeRelay() }
                            mutableState.update {
                                it.copy(
                                    device = null,
                                    media = null,
                                    canNext = false,
                                    canPrevious = false,
                                )
                            }
                        }
                    }
                }
                if (e is CancellationException && e !is TimeoutCancellationException) throw e
                if (generation == currentGeneration) {
                    val message = if (e is TimeoutCancellationException) {
                        "La TV o la fonte non risponde. Riprova."
                    } else {
                        "Impossibile avviare il Cast. Verifica la rete o scegli un'altra qualità."
                    }
                    mutableState.update { it.copy(connecting = false, error = message) }
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    if (transport != null) {
                        startPolling(currentGeneration)
                    } else {
                        context.stopService(Intent(context, CastSessionService::class.java))
                    }
                }
            } finally {
                if (preparingRelay === nextRelay) preparingRelay = null
                withContext(NonCancellable + Dispatchers.IO) {
                    runCatching { nextRelay?.stop() }
                    runCatching { nextServer?.stop() }
                }
                if (generation == currentGeneration) mutableState.update { it.copy(connecting = false) }
            }
        }
    }

    private fun startPolling(expectedGeneration: Long) {
        polling?.cancel()
        polling = scope.launch {
            var failures = 0
            var ticks = 0
            var failureSince = 0L
            var bufferingSince = 0L
            while (generation == expectedGeneration && state.value.active) {
                try {
                    commands.withLock {
                        val observed = withTimeout(20_000) { transport?.status() } ?: return@withLock
                        val current = CastHandoffPolicy.observation(state.value.playback, observed)
                        val now = SystemClock.elapsedRealtime()
                        bufferingSince = if (!current.buffering) 0L else bufferingSince.takeIf { it > 0 } ?: now
                        val stalled = current.buffering && now - bufferingSince >= 60_000
                        mutableState.update {
                            it.copy(
                                playback = current,
                                error = if (stalled) "Il video è fermo in caricamento. Ricollega la TV." else null,
                                needsReconnect = stalled,
                            )
                        }
                        if (++ticks % 5 == 0 || current.finished) flush()
                    }
                    failures = 0
                    failureSince = 0L
                    if (state.value.needsReconnect) break
                    if (state.value.playback.finished) {
                        if (request?.autoPlay == true && state.value.canNext) next()
                        break
                    }
                } catch (e: Exception) {
                    if (e is CancellationException && e !is TimeoutCancellationException) throw e
                    failures++
                    if (failureSince == 0L) failureSince = SystemClock.elapsedRealtime()
                    val exhausted = SystemClock.elapsedRealtime() - failureSince >= 45_000
                    mutableState.update {
                        it.copy(
                            error = if (
                                exhausted
                            ) {
                                "TV non raggiungibile. Ricollegati o continua sul telefono."
                            } else {
                                "Riconnessione alla TV…"
                            },
                            needsReconnect = exhausted,
                        )
                    }
                    if (exhausted) break
                }
                delay(if (failures == 0) 1000 else (failures * 1000L).coerceAtMost(5000))
            }
        }
    }

    private suspend fun flush() {
        val value = state.value
        try {
            value.media?.let { withContext(Dispatchers.IO) { progress.save(it, value.playback) } }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            mutableState.update {
                it.copy(error = "Salvataggio dei progressi non riuscito. Riproveremo alla prossima sincronizzazione.")
            }
        }
    }

    private fun command(block: suspend CastTransport.() -> Unit): Job? {
        if (state.value.connecting || state.value.needsReconnect) return null
        val expectedGeneration = generation
        return scope.launch {
            try {
                commands.withLock {
                    if (generation != expectedGeneration || state.value.connecting) return@withLock
                    withTimeout(10_000) { transport?.block() }
                    mutableState.update { it.copy(error = null) }
                }
            } catch (e: Exception) {
                if (e is CancellationException && e !is TimeoutCancellationException) throw e
                if (
                    generation == expectedGeneration
                ) {
                    mutableState.update { it.copy(error = "La TV non ha eseguito il comando. Riprova.") }
                }
            }
        }
    }

    fun togglePause() {
        command {
            val paused = !state.value.playback.paused
            pause(paused)
            mutableState.update { it.copy(playback = it.playback.copy(paused = paused)) }
        }
    }
    fun seek(positionMs: Long) {
        command {
            if (!state.value.playback.canSeek) return@command
            val target = positionMs.coerceIn(0, state.value.playback.durationMs.coerceAtLeast(0))
            seek(target)
            mutableState.update { it.copy(playback = it.playback.copy(positionMs = target, finished = false)) }
            flush()
            startPolling(generation)
        }
    }
    fun setVolume(value: Float) {
        if (!state.value.playback.canSetVolume || state.value.connecting) return
        volumeQueue.set(generation, value)
        pumpVolume()
    }
    fun adjustVolume(delta: Float) {
        if (!state.value.playback.canSetVolume || state.value.connecting) return
        volumeQueue.adjust(generation, state.value.playback.volume, delta)
        pumpVolume()
    }
    private fun pumpVolume() {
        if (volumePump?.isActive == true) return
        volumePump = scope.launch {
            while (volumeQueue.hasPending) {
                delay(80)
                val target = volumeQueue.take() ?: continue
                if (target.generation == generation) {
                    command {
                        if (!volumeQueue.isLatest(target)) return@command
                        volume(target.value)
                        mutableState.update { it.copy(playback = it.playback.copy(volume = target.value)) }
                    }?.join()
                }
                volumeQueue.complete(target)
            }
        }
    }
    fun setBrightness(value: Float) {
        command {
            if (!state.value.playback.canSetBrightness) return@command
            brightness(value.coerceIn(0f, 1f))
            mutableState.update { it.copy(playback = it.playback.copy(brightness = value.coerceIn(0f, 1f))) }
        }
    }
    fun reconnect() {
        val current = request ?: return
        play(current.copy(positionMs = state.value.playback.positionMs, durationMs = state.value.playback.durationMs))
    }
    fun setSubtitle(index: Int) {
        command {
            subtitle(index)
            mutableState.update { it.copy(subtitleIndex = index) }
        }
    }
    fun next() = move(1)
    fun previous() = move(-1)

    private fun move(offset: Int) {
        val current = request ?: return
        if (state.value.connecting) return
        val index = current.playlist.indexOf(current.episodeId)
        val episodeId = current.playlist.getOrNull(index + offset) ?: return
        val expectedGeneration = generation
        queueLoading = scope.launch {
            mutableState.update { it.copy(connecting = true, error = null) }
            try {
                val input = withTimeout(60_000) { withContext(Dispatchers.IO) { resolve(current, episodeId) } }
                if (generation == expectedGeneration) {
                    queueLoading = null
                    play(input)
                }
            } catch (e: Exception) {
                if (e is CancellationException && e !is TimeoutCancellationException) throw e
                if (
                    generation == expectedGeneration
                ) {
                    mutableState.update { it.copy(error = "Episodio non disponibile. Scegli un altro video dall’app.") }
                }
            } finally {
                if (generation == expectedGeneration) mutableState.update { it.copy(connecting = false) }
            }
        }
    }

    private suspend fun resolve(current: CastRequest, episodeId: Long): CastRequest {
        val anime = Injekt.get<GetAnime>().await(current.animeId) ?: error("Titolo non disponibile")
        val episode = Injekt.get<GetEpisode>().await(episodeId) ?: error("Episodio non disponibile")
        check(episode.animeId == anime.id)
        val source = Injekt.get<AnimeSourceManager>().get(anime.source) ?: error("Estensione non disponibile")
        for (hoster in EpisodeLoader.getHosters(episode, anime, source).take(8)) {
            val ready = EpisodeLoader.loadHosterVideos(source, hoster, force = true) as? HosterState.Ready ?: continue
            for (candidate in ready.videoList.take(6)) {
                try {
                    val video = if (source is AnimeHttpSource) source.resolveVideo(candidate) ?: continue else candidate
                    return current.copy(
                        episodeId = episodeId,
                        episodeName = episode.name,
                        video = video,
                        positionMs = if (episode.seen) 0 else episode.lastSecondSeen,
                        durationMs = episode.totalSeconds,
                    )
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                }
            }
        }
        error("Nessun video disponibile")
    }

    fun stop(returnToPhone: Boolean = false) {
        val current = request
        val previousStop = stopping
        val currentGeneration = ++generation
        val previousLoad = loading
        previousLoad?.cancel()
        preparingRelay?.revoke()
        queueLoading?.cancel()
        val previousPoll = polling
        previousPoll?.cancel()
        mutableState.update { it.copy(connecting = true) }
        stopping = scope.launch {
            previousStop?.join()
            previousLoad?.join()
            previousPoll?.join()
            commands.withLock {
                runCatching { flush() }
                if (returnToPhone && current != null) phoneResume = current.episodeId to state.value.playback.positionMs
                runCatching { withTimeout(8000) { transport?.stop() } }
                transport = null
                request = null
                withContext(Dispatchers.IO) { closeRelay() }
                if (generation == currentGeneration) {
                    mutableState.update {
                        it.copy(
                            connecting = false,
                            device = null,
                            media = null,
                            error = null,
                            canNext = false,
                            canPrevious = false,
                            needsReconnect = false,
                        )
                    }
                }
                if (
                    generation == currentGeneration
                ) {
                    context.stopService(Intent(context, CastSessionService::class.java))
                }
            }
            if (returnToPhone && current != null && generation == currentGeneration) {
                context.startActivity(
                    PlayerActivity.newIntent(context, current.animeId, current.episodeId)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    fun takePhoneResume(episodeId: Long): Long? = phoneResume?.takeIf { it.first == episodeId }?.second
        ?.also { phoneResume = null }

    private fun closeRelay() {
        val previousRelay = relay
        val previousServer = sourceServer
        relay = null
        sourceServer = null
        runCatching { previousRelay?.stop() }
        runCatching { previousServer?.stop() }
    }

    private fun openLocal(url: String): CastRelay.LocalResource? {
        val uri = Uri.parse(url)
        if (uri.scheme == "file") {
            val file = File(uri.path ?: return null)
            return CastRelay.LocalResource(file.inputStream(), file.length(), CastWire.mime(url))
        }
        if (uri.scheme != "content") return null
        val descriptor = context.contentResolver.openAssetFileDescriptor(uri, "r") ?: return null
        val length = descriptor.length
        if (length < 0) {
            descriptor.close()
            error("Il provider del file non comunica la dimensione necessaria per il Cast")
        }
        return CastRelay.LocalResource(
            descriptor.createInputStream(),
            length,
            CastWire.mime(url, context.contentResolver.getType(uri)),
        )
    }

    private fun localAddress(): String {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        return manager.allNetworks.asSequence().filter {
            val capabilities = manager.getNetworkCapabilities(it)
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ||
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
        }.flatMap { manager.getLinkProperties(it)?.linkAddresses.orEmpty().asSequence() }
            .map { it.address }.filterIsInstance<Inet4Address>().firstOrNull { !it.isLoopbackAddress }
            ?.hostAddress ?: error("Collega il telefono alla stessa rete Wi-Fi della TV")
    }

    companion object {
        @Volatile private var instance: CastController? = null
        fun get(context: Context): CastController = instance ?: synchronized(this) {
            instance ?: CastController(context.applicationContext).also { instance = it }
        }
    }
}

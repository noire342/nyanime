package eu.kanade.tachiyomi.data.watch

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import eu.kanade.domain.entries.anime.model.toDomainAnime
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.ui.player.PlayerActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import mihon.domain.source.interactor.UpdateAnimeFromRemote
import tachiyomi.domain.entries.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.lang.ref.WeakReference
import java.net.URI

data class WatchOpeningState(
    val loading: Boolean = false,
    val error: String? = null,
    val problem: WatchProblem = WatchProblem.None,
)

private class WatchResolveFailure(val problem: WatchProblem, message: String) : IllegalArgumentException(message)

/**
 * A room outlives individual player screens. The host selects catalog entries; every guest resolves
 * them using their own installed extension, existing database and normal player loader.
 */
class WatchTogetherManager private constructor(private val application: Application) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var foreground = WeakReference<Activity>(null)
    private var playerOwner = WeakReference<Activity>(null)
    private val playback = WatchPlayerAttachment()
    private val delegate: WatchPlayer? get() = playback.player
    private var openInPlayer: ((Long, Long) -> Unit)? = null
    private var pendingOpen: Triple<String, Long, Long>? = null
    private var selection: WatchSelection? = null
    private var resolutionGeneration = 0L
    private var prepared: WatchSelection? = null
    private var prepareJob: kotlinx.coroutines.Job? = null
    private val mutablePreparation = MutableStateFlow(WatchOpeningState())
    val preparation = mutablePreparation.asStateFlow()
    private val mutableOpening = MutableStateFlow(WatchOpeningState())
    val opening = mutableOpening.asStateFlow()
    private val preferences = application.getSharedPreferences("watch_together", Context.MODE_PRIVATE)
    var displayName: String
        get() = preferences.getString("name", "").orEmpty()
        set(value) {
            preferences.edit().putString("name", value.trim().take(32)).apply()
        }

    val controller = WatchRoomController(
        scope,
        object : WatchPlayer {
            override fun sample(): WatchPlayback = currentPlayback()
            override fun pause(paused: Boolean) {
                delegate?.pause(paused)
            }
            override fun seek(seconds: Double) {
                delegate?.seek(seconds)
            }
            override fun speed(value: Double) {
                delegate?.speed(value)
            }
            override fun advance(media: WatchMedia) {
                delegate?.advance(media)
            }
            override fun userResumed() {
                delegate?.userResumed()
            }
        },
        SystemClock::elapsedRealtime,
    )

    init {
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                present(activity)
            }
            override fun onActivityPaused(activity: Activity) {
                if (foreground.get() === activity) foreground.clear()
            }
            override fun onActivityStopped(activity: Activity) {
                if (!activity.isChangingConfigurations) {
                    scope.launch {
                        delay(1000)
                        if (foreground.get() == null &&
                            playerOwner.get()?.isInPictureInPictureMode != true
                        ) {
                            controller.hold()
                        }
                    }
                }
            }
            override fun onActivityDestroyed(activity: Activity) {
                detach(activity)
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        })
        scope.launch {
            controller.state
                .distinctUntilChanged { old, new ->
                    old.active == new.active &&
                        old.host == new.host &&
                        old.invite == new.invite &&
                        old.media?.key == new.media?.key
                }
                .collectLatest { room ->
                    pendingOpen = null
                    selection = null
                    mutableOpening.value = WatchOpeningState()
                    if (!room.active) {
                        resolutionGeneration++
                        playback.forgetDetached()
                        application.stopService(Intent(application, WatchSessionService::class.java))
                    } else {
                        if (foreground.get() != null) {
                            runCatching {
                                application.startForegroundService(Intent(application, WatchSessionService::class.java))
                            }
                        }
                        if (!room.host && room.media != null) resolve(room.media)
                    }
                }
        }
        scope.launch {
            controller.state.distinctUntilChanged { old, new ->
                old.active == new.active && old.host == new.host && old.upcoming?.key == new.upcoming?.key
            }.collectLatest { prepareUpcoming(it.upcoming.takeIf { _ -> it.active }) }
        }
    }

    private fun prepareUpcoming(media: WatchMedia?) {
        prepareJob?.cancel()
        if (media != null ||
            prepared?.remote?.key != controller.state.value.media?.key ||
            !controller.active
        ) {
            prepared = null
        }
        mutablePreparation.value = WatchOpeningState()
        if (media == null || controller.state.value.host || !controller.active) return
        prepareJob = scope.launch {
            mutablePreparation.value = WatchOpeningState(loading = true)
            try {
                val result = resolveCatalog(media)
                if (controller.active &&
                    !controller.state.value.host &&
                    controller.state.value.upcoming?.key == media.key
                ) {
                    prepared = result
                    mutablePreparation.value = WatchOpeningState()
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                mutablePreparation.value =
                    WatchOpeningState(error = "La fonte non ha risposto in tempo.", problem = WatchProblem.SourceError)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutablePreparation.value = WatchOpeningState(
                    error = e.message ?: "Impossibile preparare il prossimo episodio.",
                    problem = (e as? WatchResolveFailure)?.problem ?: WatchProblem.SourceError,
                )
            }
        }
    }

    fun retryPreparation() {
        if (!preparation.value.loading) prepareUpcoming(controller.state.value.upcoming)
    }

    fun present(activity: Activity) {
        foreground = WeakReference(activity)
        launchPending()
    }

    fun createRoom(name: String) {
        playback.forgetDetached()
        controller.create(name)
    }

    fun attach(activity: Activity, player: WatchPlayer, open: (Long, Long) -> Unit) {
        playerOwner = WeakReference(activity)
        playback.attach(player)
        openInPlayer = open
        controller.playerAttached()
        present(activity)
    }

    fun detach(activity: Activity) {
        if (playerOwner.get() !== activity) return
        playback.detach()
        if (!controller.active) playback.forgetDetached()
        openInPlayer = null
        playerOwner.clear()
    }

    fun retryOpening() {
        val media = controller.state.value.media ?: return
        if (controller.state.value.host || !controller.active || opening.value.loading) return
        scope.launch { resolve(media) }
    }

    fun openSelectedVideo() {
        val room = controller.state.value
        val media = room.media ?: return
        if (!room.active || opening.value.loading) return
        val activity = foreground.get() ?: return
        val owner = playerOwner.get()
        if (owner === activity && currentPlayback().media?.key == media.key) return
        scope.launch { resolve(media, openExisting = true) }
    }

    private fun currentPlayback(): WatchPlayback {
        val sample = playback.sample()
        val mapped = selection?.applyTo(sample) ?: sample
        return mapped.copy(
            problem = if (opening.value.loading) {
                WatchProblem.Opening
            } else {
                opening.value.problem.takeUnless {
                    it ==
                        WatchProblem.None
                }
                    ?: sample.problem
            },
            preparedNextKey = prepared?.remote?.key,
            nextProblem = if (preparation.value.loading) WatchProblem.Opening else preparation.value.problem,
        )
    }

    private suspend fun resolve(media: WatchMedia, openExisting: Boolean = false) {
        val generation = ++resolutionGeneration
        val wasHost = controller.state.value.host
        if (!openExisting && currentPlayback().media?.key == media.key) {
            mutableOpening.value = WatchOpeningState()
            return
        }
        mutableOpening.value = WatchOpeningState(loading = true)
        try {
            val cached = prepared?.takeIf {
                it.remote.key == media.key &&
                    Injekt.get<AnimeSourceManager>().get(media.sourceId) != null
            }
            val result = cached ?: resolveCatalog(media)
            if (generation == resolutionGeneration &&
                controller.active &&
                controller.state.value.host == wasHost &&
                controller.state.value.media?.key == media.key
            ) {
                selection = result.takeUnless { wasHost }
                pendingOpen = Triple(media.key, result.animeId, result.episodeId)
                mutableOpening.value = WatchOpeningState()
                launchPending()
            }
        } catch (e: CancellationException) {
            if (e is kotlinx.coroutines.TimeoutCancellationException) {
                if (generation ==
                    resolutionGeneration
                ) {
                    mutableOpening.value =
                        WatchOpeningState(
                            error = "La fonte non ha risposto in tempo. Riprova.",
                            problem = WatchProblem.SourceError,
                        )
                }
            } else {
                throw e
            }
        } catch (e: Exception) {
            if (generation == resolutionGeneration) {
                mutableOpening.value = WatchOpeningState(
                    error =
                    (e as? IllegalArgumentException)?.message
                        ?: "Non riesco a caricare l'episodio dalla tua fonte. Riprova.",
                    problem = (e as? WatchResolveFailure)?.problem ?: WatchProblem.SourceError,
                )
            }
        }
    }

    private suspend fun resolveCatalog(media: WatchMedia): WatchSelection = withTimeout(45_000) {
        withContext(Dispatchers.IO) {
            val sourceManager = Injekt.get<AnimeSourceManager>()
            sourceManager.isInitialized.first { it }
            val source = sourceManager.get(media.sourceId)
                ?: throw WatchResolveFailure(
                    WatchProblem.MissingSource,
                    "Serve la stessa estensione del tuo amico, installata e attendibile.",
                )
            require(media.animeUrl.isNotBlank() && media.episodeUrl.isNotBlank()) {
                "Questo contenuto non ha un riferimento condivisibile. L'host deve scegliere un titolo da un'estensione."
            }
            require(
                source is AnimeHttpSource && WatchCatalogReference.isAllowed(media.animeUrl, source.baseUrl),
            ) {
                "Il riferimento al titolo non è compatibile con la tua estensione. Aggiornala e riprova."
            }
            val entry = SAnime.create().apply {
                url = media.animeUrl
                title = media.title
            }
            val anime = Injekt.get<NetworkToLocalAnime>().await(entry.toDomainAnime(source.id))
            val getEpisodes = Injekt.get<GetEpisodesByAnimeId>()
            var episodes = getEpisodes.await(anime.id)
            var episode = episodes.singleOrNull { it.url == media.episodeUrl }
            if (episode == null) {
                Injekt.get<UpdateAnimeFromRemote>().awaitEpisodesUpdate(
                    source = source,
                    anime = anime,
                    fetchDetails = !anime.initialized,
                    fetchEpisodes = true,
                ).getOrThrow()
                episodes = getEpisodes.await(anime.id)
                episode = episodes.singleOrNull { it.url == media.episodeUrl }
                    ?: episodes.singleOrNull {
                        media.number > 0 &&
                            it.episodeNumber == media.number &&
                            WatchMedia.normalize(it.name) == WatchMedia.normalize(media.episode)
                    }
            }
            requireNotNull(episode) { "Episodio non disponibile nella tua estensione. Aggiornala e riprova." }
            val local = media.copy(sourceId = source.id, animeUrl = anime.url, episodeUrl = episode.url)
            WatchSelection(media, local.key, anime.id, episode.id)
        }
    }

    private fun launchPending() {
        val target = pendingOpen ?: return
        val activity = foreground.get() ?: return
        if (activity.isFinishing ||
            activity.isDestroyed ||
            !controller.active ||
            controller.state.value.media?.key != target.first
        ) {
            return
        }
        pendingOpen = null
        if (activity === playerOwner.get()) {
            openInPlayer?.invoke(target.second, target.third)
        } else {
            activity.startActivity(PlayerActivity.newIntent(activity, target.second, target.third))
        }
    }

    companion object {
        @Volatile private var instance: WatchTogetherManager? = null
        fun get(context: Context): WatchTogetherManager = instance ?: synchronized(this) {
            instance ?: WatchTogetherManager(context.applicationContext as Application).also { instance = it }
        }
    }
}

object WatchCatalogReference {
    fun isAllowed(reference: String, baseUrl: String): Boolean = runCatching {
        require(reference.isNotBlank() && !reference.startsWith("//") && !reference.contains('\\'))
        val base = URI(baseUrl)
        val uri = base.resolve(reference)
        uri.scheme in listOf("http", "https") &&
            uri.scheme.equals(base.scheme, ignoreCase = true) &&
            uri.host.equals(base.host, ignoreCase = true) &&
            uri.port == base.port &&
            uri.userInfo == null
    }.getOrDefault(false)
}

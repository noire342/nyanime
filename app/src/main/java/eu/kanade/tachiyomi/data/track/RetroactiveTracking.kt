package eu.kanade.tachiyomi.data.track

import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.track.service.TrackPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import logcat.LogPriority
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.interactor.GetLibraryAnime
import tachiyomi.domain.entries.manga.interactor.GetLibraryManga
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.history.anime.interactor.GetAnimeHistory
import tachiyomi.domain.history.manga.interactor.GetMangaHistory
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks
import tachiyomi.domain.track.manga.interactor.GetMangaTracks
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/** Recovers older watched/read titles without delaying player or reader startup. */
internal object RetroactiveTracking {
    data class State(
        val running: Boolean = false,
        val completed: Boolean = false,
        val processed: Int = 0,
        val total: Int = 0,
        val linked: Int = 0,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableState = MutableStateFlow(State())
    val state = mutableState.asStateFlow()

    @Volatile private var job: Job? = null

    @Volatile private var foreground = false

    private fun completedPreference() = Injekt.get<PreferenceStore>()
        .getBoolean(Preference.appStateKey("retroactive_tracking_completed_v1"))

    fun onForeground() {
        foreground = true
        val completed = completedPreference().get()
        if (completed) {
            mutableState.value = mutableState.value.copy(completed = true)
            logcat(LogPriority.INFO) { "Historical tracking recovery already completed" }
        } else {
            start(delayMs = 10_000)
        }
    }

    fun onBackground() {
        foreground = false
        job?.cancel()
    }

    fun start(delayMs: Long = 0) {
        if (!foreground || job?.isActive == true || completedPreference().get()) return
        job = scope.launch {
            try {
                if (delayMs > 0) delay(delayMs)
                run()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                logcat(LogPriority.WARN, error) { "Historical tracking recovery failed" }
            } finally {
                mutableState.value = mutableState.value.copy(running = false)
            }
        }
    }

    private suspend fun run() {
        if (!foreground ||
            Injekt.get<BasePreferences>().incognitoMode().get() ||
            !Injekt.get<TrackPreferences>().autoUpdateTrack().get()
        ) {
            return
        }
        val manager = Injekt.get<TrackerManager>()
        val animeServices = manager.loggedInTrackers().filterIsInstance<AnimeTracker>()
        val mangaServices = manager.loggedInTrackers().filterIsInstance<MangaTracker>()
        if (animeServices.isEmpty() && mangaServices.isEmpty()) return

        val animeHistory = Injekt.get<GetAnimeHistory>().subscribe("").first()
        val mangaHistory = Injekt.get<GetMangaHistory>().subscribe("").first()
        val animeIds = (
            animeHistory.sortedByDescending { it.seenAt?.time ?: 0L }.map { it.animeId } +
                Injekt.get<GetLibraryAnime>().await().filter { it.hasStarted }.map { it.id }
            ).distinct()
        val mangaIds = (
            mangaHistory.sortedByDescending { it.readAt?.time ?: 0L }.map { it.mangaId } +
                Injekt.get<GetLibraryManga>().await().filter { it.hasStarted }.map { it.id }
            ).distinct()
        val total = (if (animeServices.isEmpty()) 0 else animeIds.size) +
            (if (mangaServices.isEmpty()) 0 else mangaIds.size)
        mutableState.value = State(running = true, total = total)

        val attemptsPref = Injekt.get<PreferenceStore>().getStringSet(
            Preference.appStateKey("retroactive_tracking_scanned_v1"),
        )
        val attempted = attemptsPref.get().toMutableSet()
        val animeSourceManager = Injekt.get<AnimeSourceManager>()
        val mangaSourceManager = Injekt.get<MangaSourceManager>()
        if (animeServices.isNotEmpty() &&
            withTimeoutOrNull(15_000) { animeSourceManager.isInitialized.first { it } } != true
        ) {
            return
        }
        if (mangaServices.isNotEmpty() &&
            withTimeoutOrNull(15_000) { mangaSourceManager.isInitialized.first { it } } != true
        ) {
            return
        }
        val getAnimeTracks = Injekt.get<GetAnimeTracks>()
        val getMangaTracks = Injekt.get<GetMangaTracks>()
        val linkedPref = Injekt.get<PreferenceStore>().getInt(Preference.appStateKey("retroactive_tracking_linked_v1"))
        var processed = 0
        var linked = linkedPref.get()

        suspend fun attempt(key: String, action: suspend () -> Boolean) {
            if (key in attempted) return
            val result = try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                logcat(LogPriority.WARN, error) { "Could not recover historical tracking" }
                throw error
            }
            if (result) {
                linked++
                linkedPref.set(linked)
            }
            attempted += key
            attemptsPref.set(attempted)
            delay(750)
        }

        if (animeServices.isNotEmpty()) {
            for (id in animeIds) {
                currentCoroutineContext().ensureActive()
                try {
                    val anime = Injekt.get<GetAnime>().await(id) ?: continue
                    val source = animeSourceManager.get(anime.source) ?: continue
                    val existing = getAnimeTracks.await(id).map { it.trackerId }.toSet()
                    if (animeServices.all { (it as Tracker).id in existing }) continue
                    val episodes = Injekt.get<GetEpisodesByAnimeId>().await(id)
                    val highestSeen = episodes.filter { it.seen && it.episodeNumber > 0 && it.episodeNumber.isFinite() }
                        .maxOfOrNull { it.episodeNumber }
                        ?: animeHistory.filter {
                            it.animeId == id && it.episodeNumber > 0 && it.episodeNumber.isFinite()
                        }
                            .maxOfOrNull { it.episodeNumber }
                        ?: continue
                    attempt("a:$id") { AutoTrackOnStart.anime(anime, source, highestSeen) }
                } finally {
                    processed++
                    mutableState.value = State(running = true, processed = processed, total = total, linked = linked)
                }
            }
        }
        if (mangaServices.isNotEmpty()) {
            for (id in mangaIds) {
                currentCoroutineContext().ensureActive()
                try {
                    val manga = Injekt.get<GetManga>().await(id) ?: continue
                    val source = mangaSourceManager.get(manga.source) ?: continue
                    val existing = getMangaTracks.await(id).map { it.trackerId }.toSet()
                    if (mangaServices.all { (it as Tracker).id in existing }) continue
                    val hasRead = Injekt.get<tachiyomi.domain.items.chapter.interactor.GetChaptersByMangaId>()
                        .await(id).any { it.read } ||
                        mangaHistory.any { it.mangaId == id }
                    if (!hasRead) continue
                    attempt("m:$id") { AutoTrackOnStart.manga(manga, source) }
                } finally {
                    processed++
                    mutableState.value = State(running = true, processed = processed, total = total, linked = linked)
                }
            }
        }
        completedPreference().set(true)
        mutableState.value = State(completed = true, processed = processed, total = total, linked = linked)
        logcat(LogPriority.INFO) { "Historical tracking recovery completed: scanned=$total linked=$linked" }
    }
}

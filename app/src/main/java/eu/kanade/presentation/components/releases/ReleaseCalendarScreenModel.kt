package eu.kanade.presentation.components.releases

import android.app.Application
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.releases.AiringRepository
import eu.kanade.tachiyomi.data.releases.ChapterScheduleRepository
import eu.kanade.tachiyomi.data.releases.ReleaseEligibility
import eu.kanade.tachiyomi.data.releases.ReleaseMedium
import eu.kanade.tachiyomi.data.releases.ReleaseMonitor
import eu.kanade.tachiyomi.data.releases.ReleaseStore
import eu.kanade.tachiyomi.data.track.SourceTrackingHints
import eu.kanade.tachiyomi.data.track.TrackerManager
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.discovery.SourceHomePresentation
import tachiyomi.domain.discovery.homePresentation
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.asAnimeCover
import tachiyomi.domain.entries.anime.repository.AnimeRepository
import tachiyomi.domain.entries.manga.model.asMangaCover
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.domain.items.chapter.repository.ChapterRepository
import tachiyomi.domain.items.episode.repository.EpisodeRepository
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.track.anime.repository.AnimeTrackRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.LocalDate
import java.time.YearMonth

/** Reads local snapshots only. Source and catalog requests belong to the durable monitor. */
class ReleaseCalendarScreenModel(private val scope: ReleaseMedium? = null) :
    StateScreenModel<ReleaseCalendarScreenModel.State>(State(medium = scope)) {
    private val store = ReleaseStore()
    private val app: Application = Injekt.get()
    private var allItems = emptyList<ReleaseAgendaItem>()

    private data class Snapshot(val items: List<ReleaseAgendaItem>, val warning: String? = null)

    init {
        screenModelScope.launch(Dispatchers.IO) {
            val snapshots = if (scope == null) {
                combine(anime(), manga()) { a, m -> Snapshot(a.items + m.items, a.warning ?: m.warning) }
            } else if (scope == ReleaseMedium.ANIME) {
                anime()
            } else {
                manga()
            }
            combine(snapshots, Injekt.get<eu.kanade.domain.ui.UiPreferences>().dismissedLibraryUpdates().changes()) {
                    snapshot,
                    dismissed,
                ->
                snapshot.copy(
                    items = snapshot.items.filterNot {
                        it.dismissalKey in dismissed || it.choices.any { choice -> choice.dismissalKey in dismissed }
                    },
                )
            }.collectLatest {
                allItems = it.items.sortedBy { item -> item.at }
                mutableState.update { state -> state.copy(loading = false, warning = it.warning) }
                publish()
            }
        }
        refresh()
    }

    private fun anime(): Flow<Snapshot> {
        val repository = AiringRepository()
        return combine(
            repository.events(),
            store.monitoredFlow(ReleaseMedium.ANIME),
            store.noticeFlow(ReleaseMedium.ANIME),
            repository.caches(),
        ) { events, ids, notices, caches ->
            val items = mutableListOf<ReleaseAgendaItem>()
            val anime: AnimeRepository = Injekt.get()
            val episodes: EpisodeRepository = Injekt.get()
            var unresolved = false
            var unavailable = false
            val works = mutableListOf<Anime>()
            val presentByEntry = mutableMapOf<Long, Set<Double>>()
            val watchedByEntry = mutableMapOf<Long, Set<Double>>()
            val groupedEvents = events.groupBy { it.entryId }
            val groupedNotices = notices.groupBy { it.entryId }
            for (id in ids) {
                val cache = caches[id]
                unresolved = unresolved || cache == null || cache.status == "UNRESOLVED"
                unavailable = unavailable || cache?.status == "UNAVAILABLE"
                val entryEvents = groupedEvents[id].orEmpty()
                val entryNotices = groupedNotices[id].orEmpty()
                if (ReleaseEligibility.source(ReleaseMedium.ANIME, id) == null) continue
                val entry = anime.getAnimeById(id)
                val presentation = entry.homePresentation ?: SourceHomePresentation()
                val hints = SourceTrackingHints.from(entry)
                val tracks = Injekt.get<AnimeTrackRepository>().getTracksByAnimeId(id)
                val trackIds = buildMap {
                    tracks.forEach { track ->
                        when (track.trackerId) {
                            TrackerManager.ANILIST -> put("anilist", track.remoteId)
                            1L -> put("myanimelist", track.remoteId)
                        }
                    }
                }
                val hintIds = buildMap {
                    hints?.anilistId?.let { put("anilist", it) }
                    hints?.malId?.let { put("myanimelist", it) }
                }
                val eventIds = entryEvents.map { it.catalogId }.filter { it > 0 }.distinct()
                val ids = ReleaseAgendaMerge.verifiedIds(
                    presentation.catalogIds,
                    hintIds,
                    trackIds,
                    eventIds.singleOrNull()?.let { mapOf("anilist" to it) }.orEmpty(),
                )
                val identity = if (ids == null || eventIds.size > 1) {
                    SourceHomePresentation() // Contradictory IDs also disable the title/year fallback.
                } else {
                    presentation.copy(catalogIds = ids, choices = emptyList())
                }
                works += entry.copy(memo = identity.attachTo(entry.memo))
                val sourceLabel = Injekt.get<AnimeSourceManager>().getOrStub(entry.source).name
                val present = episodes.getEpisodeByAnimeId(id)
                val numbers = present.map { it.episodeNumber }.toSet()
                presentByEntry[id] = numbers
                watchedByEntry[id] = present.filter { it.seen }.map { it.episodeNumber }.toSet()
                val byId = present.associateBy { it.id }
                for (event in entryEvents) {
                    if (event.episode.toDouble() in numbers) continue
                    items += ReleaseAgendaItem(
                        "anime-planned-$id-${event.episode}",
                        id,
                        entry.title,
                        entry.asAnimeCover(),
                        event.airingAt,
                        app.getString(R.string.release_broadcast_label, event.episode.toString()),
                        number = event.episode.toDouble(),
                        sourceLabel = sourceLabel,
                    )
                }
                for (notice in entryNotices) {
                    val item = byId[notice.itemId]?.takeUnless { it.seen } ?: continue
                    if (notice.sourceAt <= 0) continue
                    items += ReleaseAgendaItem(
                        "anime-available-${item.id}",
                        id,
                        entry.title,
                        entry.asAnimeCover(),
                        notice.sourceAt,
                        app.getString(
                            R.string.release_content_available,
                            item.name,
                        ),
                        item.id,
                        dismissalKey = "${item.dateFetch}|anime|${entry.source}|${entry.title}|${item.name}",
                        number = item.episodeNumber,
                        sourceLabel = sourceLabel,
                    )
                }
            }
            Snapshot(
                ReleaseAgendaMerge.merge(items, works, presentByEntry, watchedByEntry),
                when {
                    unavailable -> app.getString(R.string.release_calendar_error)
                    unresolved -> app.getString(R.string.release_calendar_unresolved)
                    else -> null
                },
            )
        }
    }

    private fun manga(): Flow<Snapshot> = combine(
        ChapterScheduleRepository().events(),
        store.monitoredFlow(ReleaseMedium.MANGA),
        store.noticeFlow(ReleaseMedium.MANGA),
    ) { events, ids, notices ->
        val items = mutableListOf<ReleaseAgendaItem>()
        val manga: MangaRepository = Injekt.get()
        val chapters: ChapterRepository = Injekt.get()
        val groupedEvents = events.groupBy { it.entryId }
        val groupedNotices = notices.groupBy { it.entryId }
        for (id in ids) {
            if (ReleaseEligibility.source(ReleaseMedium.MANGA, id) == null) continue
            val entryEvents = groupedEvents[id].orEmpty()
            val entryNotices = groupedNotices[id].orEmpty()
            if (entryEvents.isEmpty() && entryNotices.isEmpty()) continue
            val entry = manga.getMangaById(id)
            val present = chapters.getChapterByMangaId(id)
            val numbers = present.map { it.chapterNumber }.toSet()
            val byId = present.associateBy { it.id }
            for (event in entryEvents) {
                if (event.release.number in numbers) continue
                items += ReleaseAgendaItem(
                    "manga-planned-$id-${event.release.number}",
                    id,
                    entry.title,
                    entry.asMangaCover(),
                    event.release.releaseAt,
                    app.getString(R.string.release_chapter_planned, event.release.number.toString()),
                    medium = ReleaseMedium.MANGA,
                )
            }
            for (notice in entryNotices) {
                val item = byId[notice.itemId]?.takeUnless { it.read } ?: continue
                if (notice.sourceAt <= 0) continue
                items += ReleaseAgendaItem(
                    "manga-available-${item.id}",
                    id,
                    entry.title,
                    entry.asMangaCover(),
                    notice.sourceAt,
                    app.getString(
                        R.string.release_content_available,
                        item.name,
                    ),
                    item.id,
                    ReleaseMedium.MANGA,
                    dismissalKey = "${item.dateFetch}|manga|${entry.source}|${entry.title}|${item.name}",
                )
            }
        }
        Snapshot(items)
    }

    fun refresh() = ReleaseMonitor.enqueue(app)

    fun setMonth(month: YearMonth) {
        mutableState.update { it.copy(month = month, date = null) }
        publish()
    }

    fun setDate(date: LocalDate?) {
        mutableState.update { it.copy(date = date) }
        publish()
    }

    fun setMedium(medium: ReleaseMedium?) {
        if (scope != null) return
        mutableState.update { it.copy(medium = medium) }
        publish()
    }

    private fun publish() {
        mutableState.update { state ->
            val from = if (state.month == YearMonth.now()) LocalDate.now() else state.month.atDay(1)
            val filtered = allItems.filter { state.medium == null || it.medium == state.medium }
            state.copy(
                items = filtered.filter { item ->
                    state.date?.let { item.date == it } ?: (item.date >= from && item.date < from.plusDays(7))
                },
                events = filtered.groupingBy { it.date }.eachCount().toImmutableMap(),
            )
        }
    }

    data class State(
        val month: YearMonth = YearMonth.now(),
        val date: LocalDate? = null,
        val medium: ReleaseMedium? = null,
        val items: List<ReleaseAgendaItem> = emptyList(),
        val events: ImmutableMap<LocalDate, Int> = persistentMapOf(),
        val loading: Boolean = true,
        val warning: String? = null,
    )
}

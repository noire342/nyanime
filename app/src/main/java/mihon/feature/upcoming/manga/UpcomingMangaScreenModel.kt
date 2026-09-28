package mihon.feature.upcoming.manga

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.presentation.components.releases.ReleaseAgendaItem
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.releases.ChapterScheduleRepository
import eu.kanade.tachiyomi.data.releases.ReleaseEligibility
import eu.kanade.tachiyomi.data.releases.ReleaseMedium
import eu.kanade.tachiyomi.data.releases.ReleaseMonitor
import eu.kanade.tachiyomi.data.releases.ReleaseStore
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.entries.manga.model.asMangaCover
import tachiyomi.domain.entries.manga.repository.MangaRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.LocalDate
import java.time.YearMonth

class UpcomingMangaScreenModel : StateScreenModel<UpcomingMangaScreenModel.State>(State()) {
    private val store = ReleaseStore()
    private val repository = ChapterScheduleRepository()
    private val entries: MangaRepository = Injekt.get()
    private var allItems = emptyList<ReleaseAgendaItem>()
    private var refreshing: Job? = null

    init {
        screenModelScope.launch(Dispatchers.IO) {
            combine(
                repository.events(),
                store.monitoredFlow(ReleaseMedium.MANGA),
                store.noticeFlow(ReleaseMedium.MANGA),
            ) {
                    events,
                    followed,
                    notices,
                ->
                Triple(events, followed.toSet(), notices)
            }.collectLatest { (events, followed, notices) ->
                val context = Injekt.get<android.app.Application>()
                val items = mutableListOf<ReleaseAgendaItem>()
                val existing = HashMap<Long, Set<Double>>()
                for (event in events.filter { it.entryId in followed }) {
                    if (ReleaseEligibility.source(ReleaseMedium.MANGA, event.entryId) == null) continue
                    val present = existing.getOrPut(event.entryId) {
                        Injekt.get<tachiyomi.domain.items.chapter.repository.ChapterRepository>()
                            .getChapterByMangaId(event.entryId).map { it.chapterNumber }.toSet()
                    }
                    if (event.release.number in present) continue
                    val entry = entries.getMangaById(event.entryId)

                    items += ReleaseAgendaItem(
                        key = "planned-${event.entryId}-${event.release.number}",
                        entryId = entry.id,
                        title = entry.title,
                        cover = entry.asMangaCover(),
                        at = event.release.releaseAt,
                        label = context.getString(R.string.release_chapter_planned, event.release.number.toString()),
                    )
                }
                for (notice in notices.filter { it.entryId in followed }) {
                    if (ReleaseEligibility.source(ReleaseMedium.MANGA, notice.entryId) == null) continue
                    val entry = entries.getMangaById(notice.entryId)

                    val item =
                        Injekt.get<tachiyomi.domain.items.chapter.repository.ChapterRepository>().getChapterById(
                            notice.itemId,
                        )
                            ?: continue
                    if (item.read) continue
                    items +=
                        ReleaseAgendaItem(
                            "available-${notice.itemId}",
                            entry.id,
                            entry.title,
                            entry.asMangaCover(),
                            notice.createdAt,
                            context.getString(R.string.release_content_available, item.name),
                            notice.itemId,
                        )
                }
                allItems = items.sortedBy { it.at }
                publish()
            }
        }
        refresh()
    }

    fun refresh() {
        if (refreshing?.isActive == true) return
        refreshing = screenModelScope.launch(Dispatchers.IO) {
            mutableState.update { it.copy(loading = true) }
            try {
                var errors = false
                var unresolved = false
                for (id in store.monitoredIds(ReleaseMedium.MANGA)) {
                    if (ReleaseEligibility.source(ReleaseMedium.MANGA, id) == null) continue
                    val entry = entries.getMangaById(id)
                    repository.refresh(entry)
                }
                val app = Injekt.get<android.app.Application>()
                mutableState.update {
                    it.copy(
                        warning = when {
                            errors -> app.getString(R.string.release_calendar_error)
                            unresolved -> app.getString(R.string.release_calendar_unresolved)
                            else -> null
                        },
                    )
                }
                ReleaseMonitor.enqueue(app)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                mutableState.update {
                    it.copy(warning = Injekt.get<android.app.Application>().getString(R.string.release_calendar_error))
                }
            } finally {
                mutableState.update { it.copy(loading = false) }
            }
        }
    }

    fun setSelectedYearMonth(month: YearMonth) {
        mutableState.update { it.copy(selectedYearMonth = month, selectedDate = null) }
        publish()
        refresh()
    }

    fun setSelectedDate(date: LocalDate?) {
        mutableState.update { it.copy(selectedDate = date) }
        publish()
    }

    private fun publish() {
        mutableState.update { state ->
            val from = if (state.selectedYearMonth ==
                YearMonth.now()
            ) {
                LocalDate.now()
            } else {
                state.selectedYearMonth.atDay(1)
            }
            val items = allItems.filter { item ->
                state.selectedDate?.let { item.date == it } ?: (item.date >= from && item.date < from.plusDays(7))
            }
            state.copy(items = items, events = allItems.groupingBy { it.date }.eachCount().toImmutableMap())
        }
    }

    data class State(
        val selectedYearMonth: YearMonth = YearMonth.now(),
        val selectedDate: LocalDate? = null,
        val items: List<ReleaseAgendaItem> = emptyList(),
        val events: ImmutableMap<LocalDate, Int> = persistentMapOf(),
        val loading: Boolean = false,
        val warning: String? = null,
    )
}

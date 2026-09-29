package eu.kanade.presentation.components.releases

import android.app.Application
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.data.releases.AiringIssue
import eu.kanade.tachiyomi.data.releases.ReleaseMedium
import eu.kanade.tachiyomi.data.releases.ReleaseMonitor
import eu.kanade.tachiyomi.data.releases.ReleaseStore
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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

    init {
        screenModelScope.launch(Dispatchers.IO) {
            ReleaseAgendaRepository().snapshots(scope).collectLatest {
                allItems = it.items.sortedBy { item -> item.at }
                val warnings = it.warnings
                publish { state -> state.copy(loading = false, warnings = warnings, issues = it.issues) }
            }
        }
        refresh()
    }

    fun refresh() = ReleaseMonitor.enqueue(app)

    fun setMonth(month: YearMonth) {
        publish { it.copy(month = month, date = null) }
    }

    fun setDate(date: LocalDate?) {
        publish { it.copy(date = date) }
    }

    fun setMedium(medium: ReleaseMedium?) {
        if (scope != null) return
        publish { it.copy(medium = medium) }
    }

    private fun publish(transform: (State) -> State) {
        mutableState.update { previous ->
            val state = transform(previous)
            val filtered = allItems.filter { state.medium == null || it.medium == state.medium }
            state.copy(
                items = filtered,
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
        val warnings: Map<ReleaseMedium, String> = emptyMap(),
        val issues: List<AiringIssue> = emptyList(),
    ) {
        val warning: String? get() = if (medium == null) warnings.values.firstOrNull() else warnings[medium]
    }
}

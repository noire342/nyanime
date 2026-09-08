package eu.kanade.tachiyomi.ui.discovery

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.data.discovery.SampleHomeFilters
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.discovery.SourceHomeAccess
import tachiyomi.domain.discovery.SourceHomeGateway
import tachiyomi.domain.discovery.SourceHomeRepository
import tachiyomi.domain.discovery.SourceHomeRequest
import tachiyomi.domain.entries.anime.model.Anime
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class CartoonsListScreenModel(
    private val sectionId: String,
    private val gateway: SourceHomeGateway = Injekt.get(),
    private val repository: SourceHomeRepository = Injekt.get(),
) : StateScreenModel<CartoonsListScreenModel.State>(State()) {
    private var job: Job? = null
    private var nextPage = 1

    init {
        screenModelScope.launch {
            gateway.observeAccess().collect { access ->
                job?.cancel()
                mutableState.value = State(access = access, query = state.value.query)
                nextPage = 1
                load(reset = true)
            }
        }
    }

    fun search(query: String) {
        if (query.trim() == state.value.query) return
        job?.cancel()
        mutableState.value = State(access = state.value.access, query = query.trim())
        nextPage = 1
        load(reset = true, debounce = true)
    }

    fun load(reset: Boolean = false, debounce: Boolean = false) {
        val current = state.value
        if (current.access.loading || current.access.source == null || current.access.offline) return
        if (sectionId == SampleHomeFilters.SEARCH && current.query.isBlank()) return
        if (!reset && (job?.isActive == true || !current.hasNext)) return
        job?.cancel()
        val page = if (reset) 1 else nextPage
        val previous = if (reset) emptyList() else current.items
        mutableState.update { it.copy(loading = true, error = null) }
        job = screenModelScope.launch {
            if (debounce) delay(400)
            repository.observe(
                current.access,
                SourceHomeRequest(sectionId, page, current.query),
                refresh = reset && current.items.isNotEmpty(),
            ).collect { value ->
                val result = value.data
                mutableState.update {
                    it.copy(
                        items = if (result != null) (previous + result.items).distinctBy(Anime::id) else it.items,
                        loading = value.loading,
                        error = value.error,
                        stale = value.stale,
                        hasNext = result?.hasNextPage ?: it.hasNext,
                    )
                }
                if (!value.loading && value.error == null && result != null) nextPage = page + 1
            }
        }
    }

    data class State(
        val access: SourceHomeAccess = SourceHomeAccess(loading = true),
        val query: String = "",
        val items: List<Anime> = emptyList(),
        val loading: Boolean = false,
        val stale: Boolean = false,
        val error: String? = null,
        val hasNext: Boolean = true,
    )
}

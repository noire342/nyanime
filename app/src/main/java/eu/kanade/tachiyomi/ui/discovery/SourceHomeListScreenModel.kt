package eu.kanade.tachiyomi.ui.discovery

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.data.discovery.ExtensionHomeServices
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.discovery.SourceHomeGroupAccess
import tachiyomi.domain.discovery.SourceHomeRequest
import tachiyomi.domain.discovery.homeItemKey
import tachiyomi.domain.entries.anime.model.Anime
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class SourceHomeListScreenModel(
    homeKey: String,
    sectionId: String,
    services: ExtensionHomeServices = Injekt.get(),
    private var date: String? = null,
) : StateScreenModel<SourceHomeListScreenModel.State>(State()) {
    private val repository = services.merged
    private val accessFlow = services.observeGroup(homeKey)
    private var job: Job? = null
    private var nextPage = 1
    private val initialCategory = sectionId.takeIf { it.startsWith("category:") }
    private val sectionId = if (initialCategory != null) SourceHomeRequest.SEARCH else sectionId
    private var initializedCategory = false

    init {
        screenModelScope.launch {
            accessFlow.collect { access ->
                job?.cancel()
                var selection = state.value.filters
                if (!initializedCategory && access.group != null) {
                    initializedCategory = true
                    selection = requireNotNull(access.group).providers.firstNotNullOfOrNull { provider ->
                        provider.categories.firstOrNull { it.id == initialCategory }?.browseValues
                    }.orEmpty()
                }
                val supported = access.group?.browseFilters.orEmpty()
                selection =
                    selection.filter { (name, values) -> supported.any { it.name == name && it.accepts(values) } }
                mutableState.value = State(access = access, query = state.value.query, filters = selection)
                nextPage = 1
                load(reset = true)
            }
        }
    }

    fun search(query: String) {
        if (query.trim() == state.value.query) return
        job?.cancel()
        mutableState.value = State(access = state.value.access, query = query.trim(), filters = state.value.filters)
        nextPage = 1
        load(reset = true, debounce = true)
    }

    fun applyFilters(values: Map<String, List<String>>) {
        val controls = state.value.access.group?.browseFilters.orEmpty()
        val next = values.filter { (name, value) ->
            controls.any {
                it.name == name && it.accepts(value) && value != it.defaults
            }
        }
        if (next == state.value.filters) return
        job?.cancel()
        mutableState.value = State(access = state.value.access, query = state.value.query, filters = next)
        nextPage = 1
        load(reset = true)
    }

    fun selectDate(value: String?) {
        if (date == value) return
        date = value
        job?.cancel()
        mutableState.value =
            State(access = state.value.access, query = state.value.query, filters = state.value.filters)
        nextPage = 1
        load(reset = true)
    }

    fun load(reset: Boolean = false, debounce: Boolean = false) {
        val current = state.value
        if (current.access.loading || current.access.group == null || current.access.offline) return
        if (!reset && (job?.isActive == true || !current.hasNext)) return
        job?.cancel()
        val page = if (reset) 1 else nextPage
        val previous = if (reset) emptyList() else current.items
        mutableState.update { it.copy(loading = true, error = null) }
        job = screenModelScope.launch {
            if (debounce) delay(400)
            repository.observe(
                current.access,
                SourceHomeRequest(sectionId, page, current.query, date, current.filters, browse = true),
                refresh = reset && current.items.isNotEmpty(),
            ).collect { value ->
                val result = value.data
                mutableState.update {
                    it.copy(
                        items = if (result != null) {
                            (previous + result.items).distinctBy { anime -> anime.homeItemKey }
                        } else {
                            it.items
                        },
                        title = result?.title ?: it.title,
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
        val access: SourceHomeGroupAccess = SourceHomeGroupAccess(loading = true),
        val query: String = "",
        val filters: Map<String, List<String>> = emptyMap(),
        val title: String? = null,
        val items: List<Anime> = emptyList(),
        val loading: Boolean = false,
        val stale: Boolean = false,
        val error: String? = null,
        val hasNext: Boolean = true,
    )
}

package eu.kanade.tachiyomi.ui.discovery

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.data.discovery.LocalHomeItem
import eu.kanade.tachiyomi.data.discovery.LocalHomeSections
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.discovery.SectionState
import tachiyomi.domain.discovery.SourceHomeAccess
import tachiyomi.domain.discovery.SourceHomeGateway
import tachiyomi.domain.discovery.SourceHomePage
import tachiyomi.domain.discovery.SourceHomeRepository
import tachiyomi.domain.discovery.SourceHomeRequest
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class CartoonsHomeScreenModel(
    private val gateway: SourceHomeGateway = Injekt.get(),
    private val repository: SourceHomeRepository = Injekt.get(),
    private val locals: LocalHomeSections = Injekt.get(),
) : StateScreenModel<CartoonsHomeScreenModel.State>(State()) {
    private val jobs = mutableMapOf<String, Job>()

    init {
        screenModelScope.launch {
            gateway.observeAccess().collectLatest { access ->
                jobs.values.forEach(Job::cancel)
                jobs.clear()
                mutableState.value = State(access = access)
                val source = access.source ?: return@collectLatest
                coroutineScope {
                    launch {
                        locals.resume(source.id).observe().collect { value ->
                            mutableState.update { it.copy(resume = value) }
                        }
                    }
                    launch {
                        locals.updates(source.id).observe().collect { value ->
                            mutableState.update { it.copy(updates = value) }
                        }
                    }
                }
            }
        }
    }

    fun load(sectionId: String, refresh: Boolean = false) {
        val access = state.value.access
        if (access.source == null || access.offline || access.loading) return
        if (!refresh && sectionId in state.value.sections) return
        jobs.remove(sectionId)?.cancel()
        jobs[sectionId] = screenModelScope.launch {
            repository.observe(access, SourceHomeRequest(sectionId), refresh).collect { value ->
                mutableState.update { it.copy(sections = it.sections + (sectionId to value)) }
            }
        }
    }

    fun refresh() {
        state.value.sections.keys.toList().forEach { load(it, true) }
    }

    data class State(
        val access: SourceHomeAccess = SourceHomeAccess(loading = true),
        val sections: Map<String, SectionState<SourceHomePage>> = emptyMap(),
        val resume: SectionState<List<LocalHomeItem>> = SectionState(),
        val updates: SectionState<List<LocalHomeItem>> = SectionState(),
    )
}

package eu.kanade.tachiyomi.ui.discovery

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.data.discovery.ExtensionHomeServices
import eu.kanade.tachiyomi.data.discovery.LocalHomeItem
import eu.kanade.tachiyomi.data.discovery.LocalHomeSections
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.discovery.SectionState
import tachiyomi.domain.discovery.SourceHomeGroupAccess
import tachiyomi.domain.discovery.SourceHomePage
import tachiyomi.domain.discovery.SourceHomeRequest
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class SourceHomeScreenModel(
    homeKey: String,
    private val services: ExtensionHomeServices = Injekt.get(),
    private val locals: LocalHomeSections = Injekt.get(),
) : StateScreenModel<SourceHomeScreenModel.State>(State()) {
    private val accessFlow = services.observeGroup(homeKey)
    private val visible = MutableStateFlow(false)
    private val feeds = SourceHomeFeedLoader(screenModelScope, services.merged) { state.value.access }

    init {
        screenModelScope.launch {
            feeds.sections.collect { sections -> mutableState.update { it.copy(sections = sections) } }
        }
        screenModelScope.launch {
            accessFlow.collectLatest { access ->
                feeds.reset()
                mutableState.value = State(access = access)
                val source = access.group ?: return@collectLatest
                visible.collectLatest { active ->
                    if (!active) return@collectLatest
                    coroutineScope {
                        launch {
                            locals.resume(source.sourceIds).observe().collect { value ->
                                mutableState.update { it.copy(resume = value) }
                            }
                        }
                        launch {
                            locals.updates.observe().collect { value ->
                                mutableState.update { it.copy(updates = value) }
                            }
                        }
                    }
                }
            }
        }
    }

    fun load(sectionId: String, refresh: Boolean = false, date: String? = null) {
        if (refresh && !state.value.access.offline) restartArtwork()
        feeds.load(SourceHomeRequest(sectionId, date = date), refresh)
    }

    fun refresh() {
        if (state.value.access.offline) return
        restartArtwork()
        feeds.refresh(force = true)
    }

    private fun restartArtwork() = mutableState.update { it.copy(artworkRefreshKey = it.artworkRefreshKey + 1) }

    fun onResume() {
        visible.value = true
        feeds.resume()
    }

    fun onPause() {
        visible.value = false
        feeds.pause()
    }

    data class State(
        val access: SourceHomeGroupAccess = SourceHomeGroupAccess(loading = true),
        val sections: Map<String, SectionState<SourceHomePage>> = emptyMap(),
        val resume: SectionState<List<LocalHomeItem>> = SectionState(),
        val updates: SectionState<List<LocalHomeItem>> = SectionState(),
        val artworkRefreshKey: Int = 0,
    )
}

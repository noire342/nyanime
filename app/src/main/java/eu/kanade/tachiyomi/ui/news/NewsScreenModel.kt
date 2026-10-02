package eu.kanade.tachiyomi.ui.news

import androidx.compose.runtime.compositionLocalOf
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.data.news.NewsPersonalLibrary
import eu.kanade.tachiyomi.data.news.NewsPersonalTitles
import eu.kanade.tachiyomi.data.news.NewsRepository
import eu.kanade.tachiyomi.data.news.NewsRules
import eu.kanade.tachiyomi.data.news.StoredNews
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nyanime.news.api.NewsMedium
import nyanime.news.api.NewsRequest
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

val LocalNewsModel = compositionLocalOf<NewsScreenModel?> { null }
val LocalNewsSearch = compositionLocalOf { false }

data class NewsViewState(
    val tab: Int = 0,
    val query: String = "",
    val medium: NewsMedium? = null,
    val source: String? = null,
    val category: String? = null,
    val items: List<StoredNews> = emptyList(),
    val personal: NewsPersonalLibrary = NewsPersonalLibrary(),
    val searching: Boolean = false,
    val busy: Boolean = false,
    val error: Boolean = false,
    val canLoadMore: Boolean = false,
    val generation: Int = 0,
)

class NewsScreenModel : ScreenModel {
    val repository = Injekt.get<NewsRepository>()
    private val mutable = MutableStateFlow(NewsViewState())
    val state = mutable.asStateFlow()
    private var hits = emptySet<String>()
    private var request: Job? = null
    private var refreshJob: Job? = null
    private var relationsJob: Job? = null
    private var generation = 0
    private val cursors = mutableMapOf<String, String?>()
    private var beforeSearch: NewsViewState? = null

    init {
        screenModelScope.launch {
            try {
                repository.initialize()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                updateView { it.copy(error = true) }
            }
            combine(repository.store.state, repository.registry.state) { _, _ -> Unit }.collect {
                val selected = state.value.source
                val available = repository.registry.state.value.any { it.packageName == selected && it.source != null }
                updateView {
                    if (selected != null && !available) {
                        it.copy(source = null, category = null, generation = it.generation + 1)
                    } else {
                        it
                    }
                }
            }
        }
    }

    // Selection and its rows are published together; a new filter never carries the old filter's rows.
    private fun updateView(transform: (NewsViewState) -> NewsViewState) {
        mutable.update { old ->
            val view = transform(old)
            val snapshot = repository.store.state.value
            val enabled = repository.registry.state.value.filter { it.source != null }.map { it.packageName }.toSet()
            val personal = NewsRules.personalIds(snapshot, view.personal.ids)
            val items = snapshot.articles.values.asSequence()
                .filter { (it.source in enabled || view.tab == 2 && it.saved) }
                .filter { view.source == null || it.source == view.source }
                .filter { view.medium == null || view.medium in it.article.media }
                .filter { view.category == null || view.category in it.article.categories }
                .filter {
                    when (view.tab) {
                        1 -> NewsRules.personal(it, snapshot, personal)
                        2 -> it.saved
                        else -> true
                    }
                }
                .filter {
                    !view.searching ||
                        view.query.isBlank() ||
                        it.key in hits ||
                        it.article.title.contains(view.query.trim(), ignoreCase = true)
                }
                .sortedWith(
                    compareByDescending<StoredNews> {
                        it.article.publishedAt ?: Long.MIN_VALUE
                    }.thenBy { it.key },
                )
                .distinctBy { it.article.url }.toList()
            view.copy(items = items)
        }
    }

    fun refresh(force: Boolean = false) {
        if (state.value.searching) {
            if (force) submit()
            return
        }
        if (refreshJob?.isActive == true || request?.isActive == true) return
        val token = generation
        refreshJob = screenModelScope.launch {
            updateView { it.copy(busy = true, error = false) }
            try {
                val personal = NewsPersonalTitles().load()
                updateView { it.copy(personal = personal) }
                repository.refresh(force)
                if (token == generation) {
                    cursors.putAll(repository.store.state.value.checks.mapValues { it.value.nextCursor })
                    updateView { it.copy(canLoadMore = cursors.values.any { cursor -> cursor != null }) }
                }
                if (relationsJob?.isActive != true) {
                    relationsJob = screenModelScope.launch {
                        try {
                            repository.refreshRelations(personal.ids)
                        } catch (cancel: CancellationException) {
                            throw cancel
                        } catch (_: Exception) {
                            // Relation enrichment must never block the feed or discard previously verified links.
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (token == generation) updateView { it.copy(error = true) }
            } finally {
                if (token == generation) updateView { it.copy(busy = false) }
            }
        }
    }

    fun tab(tab: Int) {
        updateView { it.copy(tab = tab, generation = it.generation + 1) }
    }

    fun filter(
        medium: NewsMedium? = state.value.medium,
        source: String? = state.value.source,
        category: String? = state.value.category,
    ) {
        hits = emptySet()
        updateView {
            it.copy(medium = medium, source = source, category = category, generation = it.generation + 1)
        }
        if (state.value.tab != 2) submit()
    }

    fun openSearch() {
        beforeSearch = state.value
        refreshJob?.cancel()
        request?.cancel()
        generation++
        hits = emptySet()
        updateView {
            it.copy(
                searching = true,
                query = "",
                medium = null,
                category = null,
                busy = false,
                generation =
                it.generation + 1,
            )
        }
    }

    fun closeSearch() {
        request?.cancel()
        generation++
        hits = emptySet()
        val previous = beforeSearch
        beforeSearch = null
        updateView {
            it.copy(
                searching = false,
                query = "",
                busy = false,
                medium = previous?.medium,
                source = previous?.source,
                category = previous?.category,
                generation = it.generation + 1,
            )
        }
    }

    fun edit(value: String) {
        hits = emptySet()
        updateView { it.copy(query = value.take(256), generation = it.generation + 1) }
        launchQuery(debounce = true)
    }

    fun submit() = launchQuery(debounce = false)
    fun more() = launchQuery(debounce = false, more = true)

    private fun launchQuery(debounce: Boolean, more: Boolean = false) {
        request?.cancel()
        refreshJob?.cancel()
        val token = ++generation
        if (!more) {
            hits = emptySet()
            cursors.clear()
        }
        updateView { it.copy(busy = true, error = false, canLoadMore = false) }
        request = screenModelScope.launch {
            try {
                if (debounce) delay(350)
                val view = state.value
                // Opening or clearing search only shows cached articles; it does not fan out empty searches.
                if (view.searching && view.query.isBlank() || view.tab == 2) return@launch
                repository.initialize()
                if (Injekt.get<BasePreferences>().downloadedOnly().get()) return@launch
                repository.registry.state.value.filter {
                    it.source != null && (view.source == null || it.packageName == view.source)
                }.map { extension ->
                    async {
                        val source = requireNotNull(extension.source)
                        if (view.query.isNotBlank() && !source.capabilities.search) return@async
                        if (view.medium != null && view.medium !in source.capabilities.media) return@async
                        val cursor = if (more) cursors[extension.packageName] ?: return@async else null
                        try {
                            val page = repository.page(
                                extension.packageName,
                                NewsRequest(cursor, view.medium, view.category),
                                if (view.searching) view.query.trim() else "",
                            )
                            if (generation != token) return@async
                            hits = hits + page.articles.map { NewsRules.key(extension.packageName, it.id) }
                            cursors[extension.packageName] = page.nextCursor?.takeUnless { it == cursor }
                            updateView { if (more) it.copy(generation = it.generation + 1) else it }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            if (generation == token) updateView { it.copy(error = true) }
                        }
                    }
                }.awaitAll()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (generation == token) updateView { it.copy(error = true) }
            } finally {
                if (generation == token) {
                    updateView {
                        it.copy(busy = false, canLoadMore = cursors.values.any { cursor -> cursor != null })
                    }
                }
            }
        }
    }
}

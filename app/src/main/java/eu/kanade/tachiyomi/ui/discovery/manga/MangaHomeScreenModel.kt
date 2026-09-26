package eu.kanade.tachiyomi.ui.discovery.manga

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.entries.manga.model.toSManga
import eu.kanade.domain.items.chapter.interactor.SyncChaptersWithSource
import eu.kanade.domain.source.manga.interactor.GetMangaIncognitoState
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.data.discovery.MangaHomeChapter
import eu.kanade.tachiyomi.data.discovery.MangaHomeItem
import eu.kanade.tachiyomi.data.discovery.MangaHomePage
import eu.kanade.tachiyomi.data.discovery.MangaHomeRegistry
import eu.kanade.tachiyomi.data.discovery.MangaHomeService
import eu.kanade.tachiyomi.ui.updates.dismissLibraryUpdate
import eu.kanade.tachiyomi.ui.updates.inboxKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import tachiyomi.domain.discovery.SourceHomeRequest
import tachiyomi.domain.discovery.SourceHomeSource
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.history.manga.interactor.GetMangaHistory
import tachiyomi.domain.history.manga.interactor.GetNextChapters
import tachiyomi.domain.history.manga.model.MangaHistoryWithRelations
import tachiyomi.domain.items.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.updates.manga.interactor.GetMangaUpdates
import tachiyomi.domain.updates.manga.model.MangaUpdatesWithRelations
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant
import java.util.concurrent.TimeUnit

data class MangaHomeRowState(
    val page: MangaHomePage? = null,
    val loadedPages: Int = 0,
    val loading: Boolean = true,
    val error: String? = null,
)

data class MangaHomeState(
    val initializing: Boolean = true,
    val homes: List<SourceHomeSource> = emptyList(),
    val selected: SourceHomeSource? = null,
    val offline: Boolean = false,
    val rows: Map<String, MangaHomeRowState> = emptyMap(),
    val history: List<MangaHistoryWithRelations> = emptyList(),
    val updates: List<MangaUpdatesWithRelations> = emptyList(),
    val opening: String? = null,
)

class MangaHomeScreenModel(
    private val registry: MangaHomeRegistry = Injekt.get(),
    private val service: MangaHomeService = Injekt.get(),
    private val base: BasePreferences = Injekt.get(),
    private val preferences: SourcePreferences = Injekt.get(),
    private val manager: MangaSourceManager = Injekt.get(),
    private val incognito: GetMangaIncognitoState = Injekt.get(),
    private val getUpdates: GetMangaUpdates = Injekt.get(),
    private val uiPreferences: UiPreferences = Injekt.get(),
) : StateScreenModel<MangaHomeState>(MangaHomeState()) {
    val events = MutableSharedFlow<Event>()
    private var loadJob: Job? = null
    private var openJob: Job? = null
    private var generation = 0
    private var lastHomeRefreshMs = Long.MIN_VALUE

    init {
        screenModelScope.launch {
            combine(
                registry.observe(),
                base.downloadedOnly().changes(),
                base.incognitoMode().changes(),
                preferences.incognitoMangaExtensions().changes(),
            ) { listing, offline, _, _ -> listing to offline }.collectLatest { (listing, offline) ->
                val previous = state.value.selected
                val selected = listing.homes.firstOrNull { it.key == previous?.key }
                    ?: listing.homes.firstOrNull { it.primary } ?: listing.homes.firstOrNull()
                loadJob?.cancel()
                openJob?.cancel()
                generation++
                mutableState.update {
                    it.copy(
                        initializing = listing.loading,
                        homes = listing.homes,
                        selected = selected,
                        offline = offline,
                        rows = emptyMap(),
                        opening = null,
                    )
                }
                if (selected != null && !offline) refresh()
            }
        }
        screenModelScope.launch {
            combine(
                Injekt.get<GetMangaHistory>().subscribe(""),
                preferences.disabledMangaSources().changes(),
                preferences.enabledLanguages().changes(),
                base.incognitoMode().changes(),
                preferences.incognitoMangaExtensions().changes(),
            ) { history, disabled, languages, private, _ ->
                if (private) {
                    emptyList()
                } else {
                    history.filter {
                        it.coverData.sourceId.toString() !in disabled &&
                            manager.get(it.coverData.sourceId)?.lang in languages &&
                            !incognito.await(it.coverData.sourceId)
                    }.distinctBy { it.mangaId }.take(20)
                }
            }.collect { history -> mutableState.update { it.copy(history = history) } }
        }
        screenModelScope.launch {
            combine(
                getUpdates.subscribe(Instant.now().minusSeconds(30L * 86_400)),
                uiPreferences.dismissedLibraryUpdates().changes(),
                base.incognitoMode().changes(),
            ) { updates, dismissed, private ->
                if (private) {
                    emptyList()
                } else {
                    updates
                        .distinctBy { it.mangaId }
                        .filterNot { it.read || it.inboxKey() in dismissed }
                        .take(30)
                }
            }.collect { updates -> mutableState.update { it.copy(updates = updates) } }
        }
    }

    fun dismissUpdate(update: MangaUpdatesWithRelations) {
        dismissLibraryUpdate(uiPreferences.dismissedLibraryUpdates(), update.inboxKey())
    }

    fun selectHome(key: String) {
        val selected = state.value.homes.firstOrNull { it.key == key } ?: return
        if (selected == state.value.selected) return
        loadJob?.cancel()
        openJob?.cancel()
        generation++
        mutableState.update { it.copy(selected = selected, rows = emptyMap(), opening = null) }
        refresh()
    }

    fun refresh() {
        val home = state.value.selected ?: return
        if (state.value.offline) return
        lastHomeRefreshMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime())
        loadJob?.cancel()
        val version = ++generation
        loadJob = screenModelScope.launch {
            coroutineScope {
                home.sections.map { section ->
                    async { fetch(home, SourceHomeRequest(section.id), version) }
                }.awaitAll()
            }
        }
    }

    fun refreshIfStale() {
        val now = TimeUnit.NANOSECONDS.toMillis(System.nanoTime())
        if (lastHomeRefreshMs == Long.MIN_VALUE ||
            now - lastHomeRefreshMs >= 10 * 60_000L ||
            (state.value.rows.values.any { it.error != null } && now - lastHomeRefreshMs >= 60_000L)
        ) {
            refresh()
        }
    }

    fun loadSection(sectionId: String, next: Boolean = false) {
        val home = state.value.selected ?: return
        val row = state.value.rows[sectionId]
        if (row?.loading == true || state.value.offline) return
        val page = if (next) (row?.loadedPages ?: 0) + 1 else 1
        val version = generation
        screenModelScope.launch { fetch(home, SourceHomeRequest(sectionId, page), version) }
    }

    private suspend fun fetch(home: SourceHomeSource, request: SourceHomeRequest, version: Int) {
        if (version != generation) return
        mutableState.update {
            it.copy(
                rows = it.rows +
                    (
                        request.sectionId to (it.rows[request.sectionId] ?: MangaHomeRowState())
                            .copy(loading = true, error = null)
                        ),
            )
        }
        try {
            val page = service.fetch(home.key, request)
            if (version != generation) return
            mutableState.update { current ->
                val previous = current.rows[request.sectionId]?.page?.items.orEmpty()
                val merged = if (request.page > 1) {
                    page.copy(items = (previous + page.items).distinctBy(MangaHomeItem::key))
                } else {
                    page
                }
                current.copy(
                    rows = current.rows + (request.sectionId to MangaHomeRowState(merged, request.page, false)),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (version == generation) {
                mutableState.update {
                    it.copy(
                        rows = it.rows +
                            (
                                request.sectionId to (it.rows[request.sectionId] ?: MangaHomeRowState())
                                    .copy(loading = false, error = e.message ?: "Caricamento non riuscito")
                                ),
                    )
                }
            }
        }
    }

    fun openChapter(item: MangaHomeItem, chapter: MangaHomeChapter) {
        if (openJob?.isActive == true) return
        val home = state.value.selected ?: return
        val access = registry.access(home.key)
        if (access.offline || access.source == null || item.manga.source != home.id) return
        openJob = screenModelScope.launch {
            mutableState.update { it.copy(opening = chapter.url) }
            try {
                val target = withContext(Dispatchers.IO) {
                    withTimeout(30_000) {
                        val chapters = Injekt.get<GetChaptersByMangaId>()
                        val local = chapters.await(item.manga.id).firstOrNull { it.url == chapter.url }
                        if (local != null) return@withTimeout local
                        check(access == registry.access(home.key)) { "Fonte non disponibile" }
                        val source = requireNotNull(manager.get(item.manga.source))
                        val remote = source.getChapterList(item.manga.toSManga())
                        check(access == registry.access(home.key)) { "Fonte non disponibile" }
                        Injekt.get<SyncChaptersWithSource>().await(remote, item.manga, source)
                        chapters.await(item.manga.id).firstOrNull { it.url == chapter.url }
                            ?: error("Questo capitolo non è più disponibile. Apri la scheda per aggiornare l’elenco.")
                    }
                }
                check(access == registry.access(home.key)) { "Fonte non disponibile" }
                events.emit(Event.Read(item.manga.id, target.id))
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                events.emit(Event.Error("Il capitolo non ha risposto in tempo. Riprova."))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                events.emit(Event.Error(e.message ?: "Impossibile aprire il capitolo"))
            } finally {
                mutableState.update { it.copy(opening = null) }
            }
        }
    }

    fun resume(history: MangaHistoryWithRelations) {
        if (openJob?.isActive == true) return
        openJob = screenModelScope.launch {
            val next = withContext(Dispatchers.IO) {
                Injekt.get<GetNextChapters>().await(
                    history.mangaId,
                    history.chapterId,
                    onlyUnread = false,
                ).firstOrNull { chapter ->
                    !base.downloadedOnly().get() ||
                        Injekt.get<eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager>()
                            .isChapterDownloaded(
                                chapter.name,
                                chapter.scanlator,
                                history.title,
                                history.coverData.sourceId,
                            )
                }
            }
            if (next == null) {
                events.emit(Event.Details(history.mangaId))
            } else {
                events.emit(Event.Read(history.mangaId, next.id))
            }
        }
    }

    fun cancelOpening() {
        openJob?.cancel()
        mutableState.update { it.copy(opening = null) }
    }

    sealed interface Event {
        data class Read(val mangaId: Long, val chapterId: Long) : Event
        data class Details(val mangaId: Long) : Event
        data class Error(val message: String) : Event
    }
}

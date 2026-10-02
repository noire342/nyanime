package eu.kanade.tachiyomi.ui.discovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.discovery.CatalogRow
import eu.kanade.presentation.discovery.ContinueWatchingRow
import eu.kanade.presentation.discovery.DiscoveryHomeHeader
import eu.kanade.presentation.discovery.FeaturedCarousel
import eu.kanade.presentation.discovery.HomeCategories
import eu.kanade.presentation.discovery.HomeCategoryTransition
import eu.kanade.presentation.discovery.HomeHeroSkeleton
import eu.kanade.presentation.discovery.HomeLoadingSkeleton
import eu.kanade.presentation.discovery.HomeLoadingTransition
import eu.kanade.presentation.discovery.HomePosterRowSkeleton
import eu.kanade.presentation.discovery.LoadNotice
import eu.kanade.presentation.discovery.LocalAnimeRow
import eu.kanade.presentation.discovery.PosterCard
import eu.kanade.presentation.discovery.SectionHeader
import eu.kanade.presentation.discovery.awaitingContent
import eu.kanade.presentation.discovery.displayTitle
import eu.kanade.presentation.motion.appMotionEnabled
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.data.discovery.ContinueWatchingRefresher
import eu.kanade.tachiyomi.ui.browse.anime.source.browse.BrowseAnimeSourceScreen
import eu.kanade.tachiyomi.ui.discovery.manga.MangaHomeTabContent
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.history.HistoriesTab
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.updates.AcknowledgeUpdateNoticeWhenVisible
import eu.kanade.tachiyomi.ui.updates.UpdatesTab
import eu.kanade.tachiyomi.ui.updates.dismissLibraryUpdate
import eu.kanade.tachiyomi.ui.updates.hasNewLibraryUpdateNotice
import eu.kanade.tachiyomi.ui.updates.markLibraryUpdateNoticesSeen
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import tachiyomi.domain.discovery.CatalogAnime
import tachiyomi.domain.discovery.CatalogFailureReason
import tachiyomi.domain.discovery.CatalogFeed
import tachiyomi.domain.discovery.SectionState
import tachiyomi.domain.discovery.SourceHomeGroup
import tachiyomi.domain.entries.anime.model.asAnimeCover
import tachiyomi.domain.source.anime.interactor.GetRemoteAnime
import tachiyomi.presentation.core.components.material.PullRefresh
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data object DiscoveryTab : Tab {
    const val MANGA_CATEGORY = "nyanime:manga"
    const val NEWS_CATEGORY = "nyanime:news"
    val newsSelected = kotlinx.coroutines.flow.MutableStateFlow(false)
    private val reselectRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val updateRequests = MutableSharedFlow<String>(extraBufferCapacity = 1)

    internal fun revealUpdates(pageKey: String) {
        updateRequests.tryEmit(pageKey)
    }

    override suspend fun onReselect(navigator: Navigator) {
        reselectRequests.emit(Unit)
    }

    override val options: TabOptions
        @Composable get() = TabOptions(5u, "Home", rememberVectorPainter(Icons.Outlined.Home))

    @Composable
    override fun Content() {
        val model = rememberScreenModel { DiscoveryHomeScreenModel() }
        val availability by model.state.collectAsState()
        var selected by rememberSaveable(
            stateSaver = Saver<String?, Any>(
                save = { it.orEmpty() },
                restore = DiscoveryHomeAvailability::restoreSelection,
            ),
        ) { mutableStateOf<String?>(null) }
        val isManga = selected == MANGA_CATEGORY
        val newsModel = requireNotNull(eu.kanade.tachiyomi.ui.news.LocalNewsModel.current)
        val newsExtensions by newsModel.repository.registry.state.collectAsState()
        val hasNews = newsExtensions.any { it.source != null }
        val isNews = selected == NEWS_CATEGORY && hasNews
        LaunchedEffect(isNews) { newsSelected.value = isNews }
        val homeKey = if (isManga || isNews) null else availability.selectedHome(selected)
        LaunchedEffect(availability) {
            if (selected != MANGA_CATEGORY &&
                selected != NEWS_CATEGORY
            ) {
                selected = availability.reconcileSelection(selected)
            }
        }
        val newsTitle = androidx.compose.ui.res.stringResource(eu.kanade.tachiyomi.R.string.news_title)
        val headerHomes = remember(availability.homes, hasNews, newsTitle) {
            availability.homes +
                SourceHomeGroup(MANGA_CATEGORY, "Manga", emptyList()) +
                if (hasNews) listOf(SourceHomeGroup(NEWS_CATEGORY, newsTitle, emptyList())) else emptyList()
        }
        val orderPreference = remember { Injekt.get<UiPreferences>().homeCategoryOrder() }
        val categoryOrder by orderPreference.changes().collectAsState(initial = orderPreference.get())
        val cycleCategory = {
            val current = if (selected == MANGA_CATEGORY || isNews) selected else availability.selectedHome(selected)
            selected = HomeCategories.next(current, HomeCategories.choices(headerHomes, categoryOrder))
        }
        val savedState = rememberSaveableStateHolder()
        val page = rememberHomePage(homeKey, isManga, availability.loading, isNews)
        val header = page.headerState(headerHomes)
        val navigator = LocalNavigator.currentOrThrow
        Scaffold(
            topBar = {
                DiscoveryHomeHeader(
                    selectedHome = if (isManga) {
                        MANGA_CATEGORY
                    } else if (isNews) {
                        NEWS_CATEGORY
                    } else {
                        homeKey
                    },
                    homes = headerHomes,
                    onSelect = { selected = it },
                    onBack = if (navigator.lastItem == DiscoveryTab) ({ navigator.pop() }) else null,
                    onSearch = header.onSearch,
                    onRefresh = header.onRefresh,
                    onUpdates = header.onUpdates,
                    hasUpdates = header.hasUpdates,
                    logo = header.logo,
                    artworkRefreshKey = header.artworkRefreshKey,
                    categoryOrder = categoryOrder,
                    onPrioritizeCategory = { orderPreference.set(HomeCategories.moveFirst(categoryOrder, it)) },
                )
            },
        ) { padding ->
            HomeCategoryTransition(
                page = page,
                modifier = Modifier.fillMaxSize().padding(padding),
            ) { displayed, active ->
                savedState.SaveableStateProvider(displayed.key) {
                    when (displayed) {
                        is DiscoveryHomePage.News -> eu.kanade.tachiyomi.ui.news.NewsContent(displayed.model, active)
                        DiscoveryHomePage.Initializing -> HomeLoadingSkeleton()
                        is DiscoveryHomePage.Manga -> MangaHomeTabContent(
                            page = displayed,
                            active = active,
                            onCycleCategory = cycleCategory,
                        )
                        is DiscoveryHomePage.Source -> SourceHomeContent(
                            page = displayed,
                            active = active,
                            onCycleCategory = cycleCategory,
                        )
                        is DiscoveryHomePage.Catalog -> AnimeContent(displayed, active, cycleCategory)
                    }
                }
            }
        }
    }

    @Composable
    private fun AnimeContent(
        page: DiscoveryHomePage.Catalog,
        active: Boolean,
        onCycleCategory: () -> Unit,
    ) {
        val model = page.model
        val state by model.state.collectAsState()
        val continueWatchingRefresher = remember { Injekt.get<ContinueWatchingRefresher>() }
        val scope = rememberCoroutineScope()
        LifecycleStartEffect(active, state.offline, state.sources.isNotEmpty()) {
            val refreshJob = if (active && !state.offline && state.sources.isNotEmpty()) {
                scope.launch { continueWatchingRefresher.refresh() }
            } else {
                null
            }
            onStopOrDispose { refreshJob?.cancel() }
        }
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val dismissedUpdates = remember { Injekt.get<UiPreferences>().dismissedLibraryUpdates() }
        val seenNotices = remember { Injekt.get<UiPreferences>().lastSeenAnimeUpdateNotice() }
        val lastSeenAt by seenNotices.changes().collectAsState(initial = seenNotices.get())
        val autoAcknowledge = remember { Injekt.get<UiPreferences>().autoAcknowledgeHomeUpdates() }
        val acknowledgeOnScroll by autoAcknowledge.changes().collectAsState(initial = autoAcknowledge.get())
        val updateKeys = state.updates.data.orEmpty().mapNotNull { it.updateKey }.toSet()
        val hasNewUpdates = hasNewLibraryUpdateNotice(updateKeys, lastSeenAt)
        val listState = rememberLazyListState()
        HandleHomeReselect(listState, onCycleCategory, active)
        HandleHomeUpdateRequest(listState, page.key, 2, active)
        AcknowledgeUpdateNoticeWhenVisible(
            listState,
            "updates",
            updateKeys,
            active && hasNewUpdates && acknowledgeOnScroll,
            seenNotices,
        )
        val openUpdatesScreen: () -> Unit = {
            markLibraryUpdateNoticesSeen(seenNotices, updateKeys)
            navigator.push(UpdatesTab)
        }
        LaunchedEffect(Unit) { (context as? MainActivity)?.ready = true }
        val openCatalog: (CatalogAnime) -> Unit = { navigator.push(CatalogDetailScreen(it.id.value, it.id.provider)) }
        var sourceMenu by rememberSaveable { mutableStateOf(false) }
        val selected = state.sources.firstOrNull { it.id == state.selectedSource }
        Box(Modifier.fillMaxSize()) {
            PullRefresh(
                refreshing = state.catalog.values.any { it.loading } ||
                    state.popular.loading ||
                    state.latest.loading,
                enabled = !state.offline,
                onRefresh = model::refresh,
                indicatorOnGestureOnly = true,
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(
                        bottom =
                        24.dp + eu.kanade.tachiyomi.ui.home.LocalFloatingNavigationInset.current,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(key = "hero") {
                        val featured = state.catalog[CatalogFeed.TRENDING] ?: SectionState()
                        HomeLoadingTransition(
                            loading = featured.awaitingContent,
                            placeholder = { HomeHeroSkeleton(withBrowseAction = false) },
                        ) {
                            FeaturedCarousel(
                                featured.data?.items.orEmpty(),
                                openCatalog,
                                eu.kanade.presentation.discovery.panoramaAutoplay(listState, "hero", active),
                            )
                        }
                    }
                    item(key = "resume") {
                        SectionHeader("Continua a guardare") { navigator.push(HistoriesTab) }
                        ContinueWatchingRow(state.resume, { navigator.push(AnimeScreen(it)) }) { item ->
                            scope.launch {
                                context.playDiscoveryEpisode(item.episode, item.forcedStartPositionMs)
                            }
                        }
                        if (state.resume.data?.isEmpty() == true) {
                            TextButton(onClick = {
                                navigator.push(CatalogListScreen(CatalogFeed.TRENDING))
                            }) { Text("Scopri cosa guardare") }
                        }
                    }
                    if (state.updates.data?.isNotEmpty() == true) {
                        item(key = "updates") {
                            SectionHeader("Le tue novità", openUpdatesScreen)
                            LocalAnimeRow(state.updates, { item ->
                                item.updateKey?.let { dismissLibraryUpdate(dismissedUpdates, it) }
                                navigator.push(AnimeScreen(item.anime.id))
                            }) { item ->
                                item.updateKey?.let { dismissLibraryUpdate(dismissedUpdates, it) }
                                scope.launch { context.playDiscoveryEpisode(item.episode) }
                            }
                        }
                    }
                    item(key = "shortcuts") {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            TextButton(onClick = { navigator.push(HistoriesTab) }) { Text("Cronologia") }
                        }
                        if (state.offline) {
                            Text("Solo download · catalogo salvato", Modifier.padding(horizontal = 16.dp))
                        }
                    }
                    state.catalogueOutage?.let { message ->
                        item(key = "catalogue-outage") {
                            LoadNotice(false, message, false) {
                                DiscoveryScreenModel.feeds.forEach { model.load(it, true) }
                            }
                        }
                    }
                    if (state.catalog.values.any { it.data?.provider == "kitsu" }) {
                        item(key = "catalogue-fallback") {
                            Text(
                                "Catalogo alternativo Kitsu attivo · nessuna selezione manuale necessaria",
                                Modifier.padding(horizontal = 16.dp),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    items(
                        DiscoveryScreenModel.feeds.filter { feed ->
                            state.catalogueOutage == null || state.catalog[feed]?.data != null
                        },
                        key = { it.name },
                    ) { feed ->
                        val section = state.catalog[feed] ?: SectionState()
                        Column {
                            SectionHeader(feed.displayTitle()) { navigator.push(CatalogListScreen(feed)) }
                            if (feed ==
                                CatalogFeed.WEEK
                            ) {
                                Text(
                                    "Messa in onda · la disponibilità nelle fonti può variare",
                                    Modifier.padding(horizontal = 16.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            val error = section.error.takeUnless {
                                section.failureReason == CatalogFailureReason.SERVICE_UNAVAILABLE
                            }
                            LoadNotice(section.loading, error, section.stale, showLoadingIndicator = false) {
                                model.load(feed, true)
                            }
                            HomeLoadingTransition(
                                loading = section.awaitingContent,
                                placeholder = { HomePosterRowSkeleton() },
                            ) {
                                CatalogRow(section.data?.items.orEmpty(), openCatalog, feed == CatalogFeed.WEEK)
                            }
                            if (feed == CatalogFeed.WEEK) {
                                section.data?.notice?.let { Text(it, Modifier.padding(horizontal = 16.dp)) }
                            }
                            if (section.data?.items?.isEmpty() == true &&
                                section.data?.notice == null &&
                                !section.loading
                            ) {
                                Text("Nessun anime disponibile", Modifier.padding(horizontal = 16.dp))
                            }
                        }
                    }
                    item(key = "source-picker") {
                        SectionHeader("Dalle tue fonti")
                        Column {
                            TextButton(onClick = { sourceMenu = true }, enabled = state.sources.isNotEmpty()) {
                                val sourceLabel = selected?.let { "${it.name} (${it.language.uppercase()}) ▾" }
                                Text(sourceLabel ?: "Nessuna fonte abilitata")
                            }
                            DropdownMenu(expanded = sourceMenu, onDismissRequest = { sourceMenu = false }) {
                                state.sources.forEach { source ->
                                    DropdownMenuItem(
                                        text = { Text("${source.name} (${source.language.uppercase()})") },
                                        onClick = {
                                            sourceMenu = false
                                            model.selectSource(source.id)
                                        },
                                    )
                                }
                            }
                        }
                        if (state.sources.isEmpty()) {
                            TextButton(onClick = {
                                scope.launch {
                                    HomeScreen.openTab(HomeScreen.Tab.Browse(toExtensions = true, anime = true))
                                }
                            }) { Text("Configura le estensioni") }
                        }
                    }
                    if (selected != null && !state.offline) {
                        items(if (selected.supportsLatest) listOf(false, true) else listOf(false), key = {
                            "source:$it"
                        }) { latest ->
                            val section = if (latest) state.latest else state.popular
                            Column {
                                val title = if (latest) "Ultimi aggiornamenti" else "Popolari"
                                SectionHeader("$title · ${selected.name}") {
                                    val query = if (latest) {
                                        GetRemoteAnime.QUERY_LATEST
                                    } else {
                                        GetRemoteAnime.QUERY_POPULAR
                                    }
                                    navigator.push(BrowseAnimeSourceScreen(selected.id, query))
                                }
                                LoadNotice(
                                    section.loading,
                                    section.error,
                                    section.stale,
                                    showLoadingIndicator = false,
                                ) {
                                    model.loadSource(latest)
                                }
                                HomeLoadingTransition(
                                    loading = section.awaitingContent,
                                    placeholder = { HomePosterRowSkeleton() },
                                ) {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        items(section.data.orEmpty(), key = { it.id }) { anime ->
                                            PosterCard(
                                                anime.title,
                                                anime.asAnimeCover(),
                                                selected.language.uppercase(),
                                                { navigator.push(AnimeScreen(anime.id, true)) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Text(
                            "Informazioni del catalogo: AniList · alternativa automatica Kitsu",
                            Modifier.padding(horizontal = 16.dp),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }

    @Composable
    internal fun HandleHomeReselect(listState: LazyListState, onAtTop: () -> Unit, active: Boolean = true) {
        val motion = appMotionEnabled()
        val currentOnAtTop by rememberUpdatedState(onAtTop)
        LaunchedEffect(listState, motion, active) {
            if (!active) return@LaunchedEffect
            var scrollJob: Job? = null
            reselectRequests.collect {
                // A second tap during the return must not cycle after the animation ends.
                if (scrollJob?.isActive == true) return@collect
                if (listState.canScrollBackward) {
                    scrollJob = launch {
                        if (motion) listState.animateScrollToItem(0) else listState.scrollToItem(0)
                    }
                } else {
                    currentOnAtTop()
                }
            }
        }
    }

    @Composable
    internal fun HandleHomeUpdateRequest(listState: LazyListState, pageKey: String, index: Int, active: Boolean) {
        val motion = appMotionEnabled()
        val currentIndex by rememberUpdatedState(index)
        LaunchedEffect(listState, pageKey, active, motion) {
            if (!active) return@LaunchedEffect
            updateRequests.collect { target ->
                if (target == pageKey) {
                    if (motion) listState.animateScrollToItem(currentIndex) else listState.scrollToItem(currentIndex)
                }
            }
        }
    }
}

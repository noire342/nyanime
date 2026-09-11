package eu.kanade.tachiyomi.ui.discovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.presentation.discovery.CatalogRow
import eu.kanade.presentation.discovery.DiscoveryHomeHeader
import eu.kanade.presentation.discovery.FeaturedCarousel
import eu.kanade.presentation.discovery.LoadNotice
import eu.kanade.presentation.discovery.LocalAnimeRow
import eu.kanade.presentation.discovery.PosterCard
import eu.kanade.presentation.discovery.SectionHeader
import eu.kanade.presentation.discovery.displayTitle
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.ui.browse.anime.source.browse.BrowseAnimeSourceScreen
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.history.HistoriesTab
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.updates.UpdatesTab
import kotlinx.coroutines.launch
import tachiyomi.domain.discovery.CatalogAnime
import tachiyomi.domain.discovery.CatalogFeed
import tachiyomi.domain.discovery.SectionState
import tachiyomi.domain.discovery.SourceHomeGroup
import tachiyomi.domain.entries.anime.model.asAnimeCover
import tachiyomi.domain.source.anime.interactor.GetRemoteAnime
import tachiyomi.presentation.core.screens.LoadingScreen

data object DiscoveryTab : Tab {
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
        val homeKey = availability.selectedHome(selected)
        LaunchedEffect(availability) { selected = availability.reconcileSelection(selected) }
        val savedState = rememberSaveableStateHolder()
        if (availability.loading) {
            LoadingScreen()
            return
        }
        savedState.SaveableStateProvider(homeKey?.let { "source:$it" } ?: "catalog") {
            if (homeKey != null) {
                SourceHomeContent(homeKey, availability.homes, onSelect = { selected = it })
            } else {
                AnimeContent(
                    homes = availability.homes,
                    onSelect = { selected = it },
                )
            }
        }
    }

    @Composable
    private fun AnimeContent(homes: List<SourceHomeGroup>, onSelect: (String?) -> Unit) {
        val model = rememberScreenModel { DiscoveryScreenModel() }
        val state by model.state.collectAsState()
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        LaunchedEffect(Unit) { (context as? MainActivity)?.ready = true }
        val openCatalog: (CatalogAnime) -> Unit = { navigator.push(CatalogDetailScreen(it.id.value, it.id.provider)) }
        var sourceMenu by rememberSaveable { mutableStateOf(false) }
        val selected = state.sources.firstOrNull { it.id == state.selectedSource }
        Scaffold(
            topBar = {
                DiscoveryHomeHeader(
                    selectedHome = null,
                    homes = homes,
                    onSelect = onSelect,
                    onBack = if (navigator.lastItem == DiscoveryTab) {
                        { navigator.pop() }
                    } else {
                        null
                    },
                    onSearch = { navigator.push(CatalogListScreen(CatalogFeed.SEARCH)) },
                    onRefresh = model::refresh,
                )
            },
        ) { padding ->
            LazyColumn(
                Modifier.padding(padding),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "shortcuts") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        TextButton(onClick = { navigator.push(UpdatesTab) }) { Text("Aggiornamenti") }
                        TextButton(onClick = { navigator.push(HistoriesTab) }) { Text("Cronologia") }
                    }
                    if (state.offline) Text("Solo download · catalogo salvato", Modifier.padding(horizontal = 16.dp))
                }
                item(key = "hero") {
                    val featured = state.catalog[CatalogFeed.TRENDING] ?: SectionState()
                    FeaturedCarousel(featured.data?.items.orEmpty(), openCatalog)
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
                item(key = "resume") {
                    SectionHeader("Continua a guardare") { navigator.push(HistoriesTab) }
                    LocalAnimeRow(state.resume, { navigator.push(AnimeScreen(it)) }) { item ->
                        scope.launch {
                            context.playDiscoveryEpisode(item.episode)
                        }
                    }
                    if (state.resume.data?.isEmpty() == true) {
                        TextButton(onClick = {
                            navigator.push(CatalogListScreen(CatalogFeed.TRENDING))
                        }) { Text("Scopri cosa guardare") }
                    }
                }
                item(key = "updates") {
                    SectionHeader("Nuovi episodi della tua libreria") { navigator.push(UpdatesTab) }
                    LocalAnimeRow(state.updates, { navigator.push(AnimeScreen(it)) }) { item ->
                        scope.launch {
                            context.playDiscoveryEpisode(item.episode)
                        }
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
                            section.failureReason == tachiyomi.domain.discovery.CatalogFailureReason.SERVICE_UNAVAILABLE
                        }
                        LoadNotice(section.loading, error, section.stale) { model.load(feed, true) }
                        CatalogRow(section.data?.items.orEmpty(), openCatalog, feed == CatalogFeed.WEEK)
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
                                val query = if (latest) GetRemoteAnime.QUERY_LATEST else GetRemoteAnime.QUERY_POPULAR
                                navigator.push(BrowseAnimeSourceScreen(selected.id, query))
                            }
                            LoadNotice(section.loading, section.error, section.stale) { model.loadSource(latest) }
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(section.data.orEmpty(), key = { it.id }) { anime ->
                                    PosterCard(anime.title, anime.asAnimeCover(), selected.language.uppercase(), {
                                        navigator.push(AnimeScreen(anime.id, true))
                                    })
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

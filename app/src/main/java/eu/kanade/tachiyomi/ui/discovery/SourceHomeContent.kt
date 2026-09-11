package eu.kanade.tachiyomi.ui.discovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.discovery.ContinueWatchingRow
import eu.kanade.presentation.discovery.DiscoveryHomeHeader
import eu.kanade.presentation.discovery.LoadNotice
import eu.kanade.presentation.discovery.LocalAnimeRow
import eu.kanade.presentation.discovery.SectionHeader
import eu.kanade.presentation.discovery.SourceFeaturedCarousel
import eu.kanade.presentation.discovery.SourceHomeDateSelector
import eu.kanade.presentation.discovery.SourceHomePosterCard
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.main.MainActivity
import kotlinx.coroutines.launch
import tachiyomi.domain.discovery.SectionState
import tachiyomi.domain.discovery.SourceHomeGroup
import tachiyomi.domain.discovery.SourceHomeRequest
import tachiyomi.domain.discovery.homeItemKey
import tachiyomi.presentation.core.components.material.PullRefresh

@Composable
fun DiscoveryTab.SourceHomeContent(homeKey: String, homes: List<SourceHomeGroup>, onSelect: (String?) -> Unit) {
    val model = rememberScreenModel(tag = homeKey) { SourceHomeScreenModel(homeKey) }
    val state by model.state.collectAsState()
    val navigator = LocalNavigator.currentOrThrow
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { (context as? MainActivity)?.ready = true }
    val access = state.access
    val source = access.group
    // Parent and child observe availability independently: never render a stale branded frame.
    if (access.loading || source == null) return
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { model.onResume() }
    LaunchedEffect(access) { model.onResume() }
    Scaffold(topBar = {
        DiscoveryHomeHeader(
            selectedHome = homeKey,
            homes = homes,
            onSelect = onSelect,
            onBack = if (navigator.lastItem == DiscoveryTab) {
                { navigator.pop() }
            } else {
                null
            },
            onSearch = if (!access.offline && source.searchable) {
                { navigator.push(SourceHomeListScreen(homeKey, SourceHomeRequest.SEARCH, "Cerca ${source.title}")) }
            } else {
                null
            },
            onRefresh = model::refresh,
        )
    }) { padding ->
        PullRefresh(
            refreshing = state.sections.values.any { it.loading },
            enabled = !access.offline,
            onRefresh = model::refresh,
            modifier = Modifier.padding(padding),
        ) {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Local playback is the first row, regardless of the number/state of remote sections.
                item(key = "resume:" + source.id) {
                    SectionHeader("Continua a guardare") { navigator.push(SourceHomeHistoryScreen(homeKey)) }
                    ContinueWatchingRow(
                        state.resume,
                        onOpen = { navigator.push(AnimeScreen(it)) },
                        emptyMessage = "I titoli che guardi in questa Home compariranno qui.",
                    ) { item -> scope.launch { context.playDiscoveryEpisode(item.episode) } }
                }
                if (access.offline) {
                    item(key = "offline") {
                        Text("Solo download · nessuna richiesta alle fonti", Modifier.padding(horizontal = 16.dp))
                    }
                }
                if (!access.offline) {
                    items(source.rows, key = { it.id }) { row ->
                        var selection by rememberSaveable(homeKey, row.id) { mutableStateOf<String?>(null) }
                        val variantStates = rememberSaveableStateHolder()
                        val section = row.selected(selection)
                        var date by rememberSaveable(homeKey, section.id) { mutableStateOf<String?>(null) }
                        LaunchedEffect(access, section.id, date) { model.load(section.id, date = date) }
                        val value = state.sections[section.id] ?: SectionState()
                        Column {
                            SectionHeader(value.data?.title?.takeIf { row.sections.size == 1 } ?: row.title) {
                                navigator.push(SourceHomeListScreen(homeKey, section.id, section.title, date))
                            }
                            if (row.sections.size > 1) {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    items(row.sections, key = { it.id }) { variant ->
                                        FilterChip(
                                            selected = variant.id == section.id,
                                            onClick = { selection = variant.id },
                                            label = { Text(variant.group?.tab ?: variant.title) },
                                        )
                                    }
                                }
                            }
                            if (section.supportsDate) SourceHomeDateSelector(date) { date = it }
                            LoadNotice(value.loading, value.error, value.stale) { model.load(section.id, true, date) }
                            if (value.data?.items?.isEmpty() == true && !value.loading && value.error == null) {
                                Text("Nessun titolo in questa sezione", Modifier.padding(horizontal = 16.dp))
                            }
                            // Each variant owns its scroll state; switching never reuses another tab's offset.
                            variantStates.SaveableStateProvider(section.id) {
                                if (section.layout == "featured") {
                                    SourceFeaturedCarousel(
                                        value.data?.items.orEmpty(),
                                        state.artworkRefreshKey,
                                    ) { anime ->
                                        navigator.push(AnimeScreen(anime.id, true))
                                    }
                                } else {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        items(value.data?.items.orEmpty(), key = { it.homeItemKey }) { anime ->
                                            SourceHomePosterCard(
                                                anime,
                                                source.sourceLabel(
                                                    anime.source,
                                                ),
                                                {
                                                    navigator.push(AnimeScreen(anime.id, true))
                                                },
                                                refreshKey = state.artworkRefreshKey,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (source.categories.isNotEmpty()) {
                        item(key = "categories") {
                            SectionHeader("Esplora le categorie")
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(source.categories, key = { it.id }) { category ->
                                    AssistChip(
                                        onClick = {
                                            navigator.push(SourceHomeListScreen(homeKey, category.id, category.title))
                                        },
                                        label = { Text(category.title) },
                                    )
                                }
                            }
                        }
                    }
                    if (source.sections.isEmpty()) {
                        item { Text("Aggiorna l’estensione per usare le sezioni della Home", Modifier.padding(16.dp)) }
                    }
                }
                item(key = "updates:" + source.id) {
                    SectionHeader("Nuovi episodi della tua libreria")
                    LocalAnimeRow(
                        state.updates,
                        onOpen = { navigator.push(AnimeScreen(it)) },
                        emptyMessage = "Aggiungi i titoli alla libreria per ritrovare qui i loro aggiornamenti.",
                    ) { item -> scope.launch { context.playDiscoveryEpisode(item.episode) } }
                }
            }
        }
    }
}

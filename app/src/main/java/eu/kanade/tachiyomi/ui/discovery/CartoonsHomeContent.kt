package eu.kanade.tachiyomi.ui.discovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.discovery.DiscoveryHomeHeader
import eu.kanade.presentation.discovery.LoadNotice
import eu.kanade.presentation.discovery.LocalAnimeRow
import eu.kanade.presentation.discovery.PosterCard
import eu.kanade.presentation.discovery.SectionHeader
import eu.kanade.tachiyomi.data.discovery.SampleHomeFilters
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.main.MainActivity
import kotlinx.coroutines.launch
import tachiyomi.domain.discovery.SectionState
import tachiyomi.domain.entries.anime.model.asAnimeCover

@Composable
fun DiscoveryTab.CartoonsHomeContent(onSelect: (Boolean) -> Unit) {
    val model = rememberScreenModel { CartoonsHomeScreenModel() }
    val state by model.state.collectAsState()
    val navigator = LocalNavigator.currentOrThrow
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { (context as? MainActivity)?.ready = true }
    val access = state.access
    val source = access.source
    // Parent and child observe availability independently: never render a stale branded frame.
    if (access.loading || source == null) return
    Scaffold(topBar = {
        DiscoveryHomeHeader(
            cartoons = true,
            cartoonsAvailable = true,
            onSelect = onSelect,
            onBack = if (navigator.lastItem == DiscoveryTab) {
                { navigator.pop() }
            } else {
                null
            },
            onSearch = if (!access.offline) {
                { navigator.push(CartoonsListScreen(SampleHomeFilters.SEARCH, "Cerca cartoni")) }
            } else {
                null
            },
            onRefresh = model::refresh,
        )
    }) { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "intro") {
                Text(
                    "Cartoni · TestSource",
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.headlineSmall,
                )
                LoadNotice(access.loading, access.error)
                if (access.offline) {
                    Text("Solo download · nessuna richiesta a TestSource", Modifier.padding(horizontal = 16.dp))
                }
            }
            item(key = "resume:" + source.id) {
                SectionHeader("Continua a guardare")
                LocalAnimeRow(
                    state.resume,
                    onOpen = { navigator.push(AnimeScreen(it)) },
                    emptyMessage = "I cartoni che guardi su TestSource compariranno qui.",
                ) { item -> scope.launch { context.playDiscoveryEpisode(item.episode) } }
            }
            item(key = "updates:" + source.id) {
                SectionHeader("Nuovi episodi della tua libreria")
                LocalAnimeRow(
                    state.updates,
                    onOpen = { navigator.push(AnimeScreen(it)) },
                    emptyMessage = "Aggiungi i cartoni alla libreria per ritrovare qui i loro aggiornamenti.",
                ) { item -> scope.launch { context.playDiscoveryEpisode(item.episode) } }
            }
            if (!access.offline) {
                if (source.categories.isNotEmpty()) {
                    item(key = "categories") {
                        SectionHeader("Esplora le categorie")
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(source.categories, key = { it.id }) { category ->
                                AssistChip(
                                    onClick = { navigator.push(CartoonsListScreen(category.id, category.title)) },
                                    label = { Text(category.title) },
                                )
                            }
                        }
                    }
                }
                items(source.sections, key = { it.id }) { section ->
                    LaunchedEffect(access, section.id) { model.load(section.id) }
                    val value = state.sections[section.id] ?: SectionState()
                    SectionHeader(section.title) {
                        navigator.push(CartoonsListScreen(section.id, section.title))
                    }
                    LoadNotice(value.loading, value.error, value.stale) { model.load(section.id, true) }
                    if (value.data?.items?.isEmpty() == true && !value.loading && value.error == null) {
                        Text("Nessun titolo in questa sezione", Modifier.padding(horizontal = 16.dp))
                    }
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(value.data?.items.orEmpty(), key = { it.id }) { anime ->
                            PosterCard(anime.title, anime.asAnimeCover(), "TestSource · IT", {
                                navigator.push(AnimeScreen(anime.id, true))
                            })
                        }
                    }
                }
                if (source.sections.isEmpty()) {
                    item { Text("Aggiorna TestSource per usare le sezioni della Home", Modifier.padding(16.dp)) }
                }
            }
        }
    }
}

package eu.kanade.tachiyomi.ui.discovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.discovery.LoadNotice
import eu.kanade.presentation.discovery.SourceHomeActiveFilters
import eu.kanade.presentation.discovery.SourceHomeFilterSheet
import eu.kanade.presentation.discovery.SourceHomePosterCard
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import tachiyomi.domain.discovery.SourceHomeRequest
import tachiyomi.domain.discovery.homeItemKey

class SourceHomeListScreen(
    private val homeKey: String,
    private val sectionId: String,
    private val title: String,
    private val date: String? = null,
) : Screen() {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        var selectedDate by rememberSaveable { mutableStateOf(date) }
        val model = rememberScreenModel { SourceHomeListScreenModel(homeKey, sectionId, date = date) }
        val state by model.state.collectAsState()
        val navigator = LocalNavigator.currentOrThrow
        var query by rememberSaveable { mutableStateOf("") }
        var showFilters by rememberSaveable { mutableStateOf(false) }
        val isCatalogue = sectionId == SourceHomeRequest.SEARCH || sectionId.startsWith("category:")
        val availability = DiscoveryHomeAvailability.from(state.access)
        val leaveSourcePage = availability.shouldLeaveSourcePage(navigator.lastItem == this)
        LaunchedEffect(leaveSourcePage) {
            if (leaveSourcePage && !navigator.pop()) navigator.replace(DiscoveryTab)
        }
        val source = state.access.group
        if (availability.loading || source == null) {
            // A restored route or an extension removal must not expose source-specific labels or cached cards.
            Scaffold(topBar = { TopAppBar(title = { Text("Home") }) }) { padding ->
                Box(Modifier.padding(padding)) { LoadNotice(availability.loading, null) }
            }
            return
        }
        LaunchedEffect(query) { if (isCatalogue) model.search(query) }
        LaunchedEffect(selectedDate) { model.selectDate(selectedDate) }
        if (showFilters) {
            SourceHomeFilterSheet(
                source.browseFilters,
                state.filters,
                onDismiss = { showFilters = false },
                onApply = model::applyFilters,
            )
        }
        Scaffold(topBar = {
            Column {
                TopAppBar(title = {
                    Text(if (isCatalogue) "Esplora ${source.title}" else state.title ?: title)
                }, navigationIcon = {
                    IconButton(onClick = { navigator.pop() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Indietro")
                    }
                })
                if (isCatalogue) {
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(
                            query,
                            { query = it },
                            Modifier.fillMaxWidth(),
                            placeholder = { Text("Titolo, parola chiave…") },
                            singleLine = true,
                            shape = RoundedCornerShape(24.dp),
                            leadingIcon = { Icon(Icons.Outlined.Search, null) },
                            trailingIcon = {
                                if (query.isNotEmpty()) {
                                    IconButton(onClick = { query = "" }) {
                                        Icon(Icons.Outlined.Close, "Cancella ricerca")
                                    }
                                }
                            },
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (state.filters.isEmpty()) {
                                    "Tutto il catalogo"
                                } else {
                                    "${state.filters.size} filtri attivi"
                                },
                                modifier = Modifier.weight(1f),
                            )
                            if (source.browseFilters.isNotEmpty()) {
                                FilledTonalButton(onClick = { showFilters = true }) {
                                    Icon(Icons.Outlined.Tune, null)
                                    Text("Filtri", Modifier.padding(start = 8.dp))
                                }
                            }
                        }
                        SourceHomeActiveFilters(state.filters) {
                            model.applyFilters(state.filters - it)
                        }
                    }
                }
            }
        }) { padding ->
            LazyVerticalGrid(
                GridCells.Adaptive(148.dp),
                Modifier.padding(padding),
                contentPadding = PaddingValues(12.dp),
            ) {
                if (source.sections.firstOrNull { it.id == sectionId }?.supportsDate == true) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        eu.kanade.presentation.discovery.SourceHomeDateSelector(selectedDate) {
                            selectedDate = it
                        }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    val error = when {
                        state.access.offline -> "Modalità solo download: il catalogo della fonte è disattivato"
                        else -> state.error
                    }
                    LoadNotice(state.loading || state.access.loading, error, state.stale) {
                        model.load(reset = state.items.isEmpty())
                    }
                }
                items(state.items, key = { it.homeItemKey }) { anime ->
                    SourceHomePosterCard(
                        anime,
                        source.sourceLabel(anime.source),
                        { navigator.push(AnimeScreen(anime.id, true)) },
                        Modifier.padding(6.dp),
                    )
                }
                if (!state.loading &&
                    state.items.isEmpty() &&
                    state.access.group != null &&
                    !state.access.offline &&
                    state.error == null
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            if (isCatalogue && query.isBlank() && state.filters.isEmpty()) {
                                "Nessun titolo disponibile"
                            } else {
                                "Nessun risultato"
                            },
                            Modifier.padding(16.dp),
                        )
                    }
                }
                if (state.hasNext && state.items.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        LaunchedEffect(state.items.size, state.error) {
                            if (!state.loading && state.error == null) model.load()
                        }
                        TextButton(onClick = { model.load() }, enabled = !state.loading) {
                            Text("Carica altri")
                        }
                    }
                }
            }
        }
    }
}

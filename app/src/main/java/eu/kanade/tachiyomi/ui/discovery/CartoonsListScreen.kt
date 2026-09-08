package eu.kanade.tachiyomi.ui.discovery

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.discovery.LoadNotice
import eu.kanade.presentation.discovery.PosterCard
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.discovery.SampleHomeFilters
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import tachiyomi.domain.entries.anime.model.asAnimeCover

class CartoonsListScreen(private val sectionId: String, private val title: String) : Screen() {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val model = rememberScreenModel { CartoonsListScreenModel(sectionId) }
        val state by model.state.collectAsState()
        val navigator = LocalNavigator.currentOrThrow
        var query by rememberSaveable { mutableStateOf("") }
        LaunchedEffect(query) { if (sectionId == SampleHomeFilters.SEARCH) model.search(query) }
        Scaffold(topBar = {
            TopAppBar(title = { Text(title) }, navigationIcon = {
                IconButton(onClick = { navigator.pop() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Indietro") }
            })
        }) { padding ->
            LazyVerticalGrid(
                GridCells.Adaptive(148.dp),
                Modifier.padding(padding),
                contentPadding = PaddingValues(12.dp),
            ) {
                if (sectionId == SampleHomeFilters.SEARCH) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        OutlinedTextField(
                            query,
                            { query = it },
                            Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            label = { Text("Cerca su TestSource") },
                            singleLine = true,
                        )
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    val error = when {
                        state.access.offline -> "Modalità solo download: il catalogo TestSource è disattivato"
                        !state.access.loading && state.access.source == null -> "TestSource non disponibile o disabilitata"
                        else -> state.error ?: state.access.error
                    }
                    LoadNotice(state.loading || state.access.loading, error, state.stale) {
                        model.load(reset = state.items.isEmpty())
                    }
                }
                items(state.items, key = { it.id }) { anime ->
                    PosterCard(
                        anime.title,
                        anime.asAnimeCover(),
                        "TestSource · IT",
                        { navigator.push(AnimeScreen(anime.id, true)) },
                        Modifier.padding(6.dp),
                    )
                }
                if (!state.loading &&
                    state.items.isEmpty() &&
                    state.access.source != null &&
                    !state.access.offline &&
                    state.error == null
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            if (sectionId == SampleHomeFilters.SEARCH && query.isBlank()) {
                                "Cerca un cartone per titolo"
                            } else {
                                "Nessun risultato"
                            },
                            Modifier.padding(16.dp),
                        )
                    }
                }
                if (state.hasNext && state.items.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        TextButton(onClick = { model.load() }, enabled = !state.loading) { Text("Carica altri") }
                    }
                }
            }
        }
    }
}

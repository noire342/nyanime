package eu.kanade.tachiyomi.ui.discovery.manga

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.discovery.MangaGenreLabels
import eu.kanade.tachiyomi.data.discovery.MangaHomeItem
import eu.kanade.tachiyomi.data.discovery.MangaHomeMerge
import eu.kanade.tachiyomi.data.discovery.MangaHomePage
import eu.kanade.tachiyomi.data.discovery.MangaHomeRegistry
import eu.kanade.tachiyomi.data.discovery.MangaHomeService
import eu.kanade.tachiyomi.ui.entries.manga.MangaScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tachiyomi.domain.discovery.SourceHomeListing
import tachiyomi.domain.discovery.SourceHomeRequest
import tachiyomi.domain.entries.manga.model.Manga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/** Search only among the installed manga Home providers currently allowed by the language toggle. */
class MangaHomeSearchScreen(private val initialGenre: String? = null) : Screen() {
    @Composable
    override fun Content() {
        val registry = remember { Injekt.get<MangaHomeRegistry>() }
        val service = remember { Injekt.get<MangaHomeService>() }
        val uiPreferences = remember { Injekt.get<UiPreferences>() }
        val listing by registry.observe().collectAsState(initial = SourceHomeListing())
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        var query by rememberSaveable { mutableStateOf("") }
        var selectedSource by rememberSaveable { mutableStateOf<String?>(null) }
        var selectedGenre by rememberSaveable { mutableStateOf(initialGenre) }
        var page by rememberSaveable { mutableIntStateOf(1) }
        var items by remember { mutableStateOf<List<MangaHomeItem>>(emptyList()) }
        var hasMore by remember { mutableStateOf(false) }
        var loading by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        val homes = listing.homes.filter { it.search != null }
        val genres = MangaGenreLabels.distinct(homes.flatMap { it.categories }.map { it.title })
        val selectedHomes = homes.filter { selectedSource == null || it.key == selectedSource }
        val eligible = selectedHomes.mapNotNull { home ->
            val section = selectedGenre?.let { genre ->
                home.categories.firstOrNull { MangaGenreLabels.key(it.title) == MangaGenreLabels.key(genre) }
            }
            if (selectedGenre != null && section == null) null else home to section
        }
        val revision = eligible.joinToString("|") { it.first.key + ":" + it.first.revision }

        LaunchedEffect(query, selectedSource, selectedGenre, page, revision) {
            loading = true
            error = null
            if (page == 1) {
                items = emptyList()
                hasMore = false
            }
            if (query.isNotBlank()) delay(320)
            val previousItems = if (page > 1) items else emptyList()
            val pagesBySource = mutableMapOf<String, MangaHomePage>()
            val failures = mutableListOf<Throwable>()
            val preferredSource = uiPreferences.preferredMangaHomeSource().get().takeIf { it != 0L }
            coroutineScope {
                val completed = Channel<Pair<String, Result<MangaHomePage>>>(eligible.size.coerceAtLeast(1))
                eligible.forEach { (home, section) ->
                    launch {
                        val result = try {
                            Result.success(
                                service.fetch(
                                    home.key,
                                    SourceHomeRequest(section?.id ?: SourceHomeRequest.SEARCH, page, query),
                                ),
                            )
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            Result.failure(failure)
                        }
                        completed.send(home.key to result)
                    }
                }
                repeat(eligible.size) {
                    val (sourceKey, result) = completed.receive()
                    result.onSuccess { pagesBySource[sourceKey] = it }
                        .onFailure { failures += it }
                    val available = eligible.mapNotNull { (home, _) -> pagesBySource[home.key] }
                    if (available.isNotEmpty()) {
                        val merged = MangaHomeMerge.merge(available, preferredSource)
                        items = (previousItems + merged.items).distinctBy(MangaHomeItem::key)
                        hasMore = merged.hasNextPage
                    }
                }
            }
            val pages = eligible.mapNotNull { (home, _) -> pagesBySource[home.key] }
            error = if (pages.isEmpty() && eligible.isNotEmpty()) {
                failures.firstNotNullOfOrNull { it.message } ?: "Ricerca non riuscita"
            } else {
                null
            }
            loading = false
            if (pages.size > 1) {
                val enriched = coroutineScope {
                    pages.map { sourcePage ->
                        async {
                            sourcePage.copy(
                                items = sourcePage.items.mapIndexed { index, item ->
                                    if (index >= 8 || item.presentation?.catalogIds?.isNotEmpty() == true) {
                                        item
                                    } else {
                                        try {
                                            service.enrichIdentity(item)
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (_: Exception) {
                                            item
                                        }
                                    }
                                },
                            )
                        }
                    }.awaitAll()
                }
                val identityMerged = MangaHomeMerge.merge(enriched, preferredSource)
                items = MangaHomeMerge.merge(
                    listOf(MangaHomePage(previousItems, false), identityMerged),
                    preferredSource,
                ).items.distinctBy(MangaHomeItem::key)
            }
        }

        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = { navigator.pop() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Indietro")
                    }
                    Text(
                        "Esplora manga",
                        Modifier.padding(top = 12.dp),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        page = 1
                    },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    placeholder = { Text("Titolo o parola chiave") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FilterChip(selected = selectedSource == null, onClick = {
                            selectedSource = null
                            page = 1
                        }, label = { Text("Tutte le fonti") })
                    }
                    items(homes, key = { it.key }) { home ->
                        FilterChip(
                            selected = selectedSource == home.key,
                            onClick = {
                                selectedSource = home.key
                                page = 1
                            },
                            label = { Text("${home.sourceName} · ${home.language.uppercase()}") },
                        )
                    }
                }
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FilterChip(selected = selectedGenre == null, onClick = {
                            selectedGenre = null
                            page = 1
                        }, label = { Text("Tutti i generi") })
                    }
                    items(genres) { genre ->
                        FilterChip(selected = selectedGenre == genre, onClick = {
                            selectedGenre = genre
                            page = 1
                        }, label = { Text(genre) })
                    }
                }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    if (homes.isEmpty() && !listing.loading) {
                        item { Text("Nessuna fonte manga disponibile con la lingua scelta.") }
                    } else if (eligible.isEmpty()) {
                        item { Text("Nessuna fonte supporta questo filtro. Scegli un altro genere.") }
                    } else if (items.isEmpty() && !loading) {
                        item { Text(error ?: "Nessun manga trovato.") }
                    }
                    items(items, key = { it.key }) { item ->
                        var menu by remember(item.key) { mutableStateOf(false) }
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                AsyncImage(
                                    model = ImageRequest.Builder(
                                        context,
                                    ).data(item.manga.copy(favorite = false)).crossfade(180).build(),
                                    contentDescription = item.manga.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.width(86.dp).height(129.dp).clickable {
                                        navigator.push(MangaScreen(item.manga.id, fromSource = true))
                                    },
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        item.manga.title,
                                        Modifier.clickable {
                                            navigator.push(MangaScreen(item.manga.id, fromSource = true))
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    val variants = listOf(item.manga) + item.alternateSources
                                    val sourceName = homes.firstOrNull {
                                        it.id == item.manga.source
                                    }?.sourceName.orEmpty()
                                    TextButton(onClick = { menu = true }, enabled = variants.size > 1) {
                                        Text(if (variants.size > 1) "${variants.size} fonti · scegli" else sourceName)
                                    }
                                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                        variants.forEach { manga: Manga ->
                                            val source = homes.firstOrNull {
                                                it.id == manga.source
                                            }?.sourceName.orEmpty()
                                            DropdownMenuItem(text = { Text(source) }, onClick = {
                                                menu = false
                                                navigator.push(MangaScreen(manga.id, fromSource = true))
                                            })
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (hasMore && items.isNotEmpty()) {
                        item { TextButton(onClick = { page++ }, enabled = !loading) { Text("Mostra altri") } }
                    }
                }
            }
        }
    }
}

package eu.kanade.presentation.discovery.manga

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.Extras
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import eu.kanade.tachiyomi.data.coil.MangaCoverFetcher
import eu.kanade.tachiyomi.data.discovery.MangaHomeChapter
import eu.kanade.tachiyomi.data.discovery.MangaHomeItem
import eu.kanade.tachiyomi.ui.discovery.manga.MangaHomeState
import tachiyomi.domain.discovery.SourceHomeSection
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.history.manga.model.MangaHistoryWithRelations

@Composable
fun MangaHomeContent(
    state: MangaHomeState,
    onRefresh: () -> Unit,
    onSelectHome: (String) -> Unit,
    onManga: (Manga) -> Unit,
    onChapter: (MangaHomeItem, MangaHomeChapter) -> Unit,
    onResume: (MangaHistoryWithRelations) -> Unit,
    onArchive: (Map<String, String>) -> Unit,
    onMore: (SourceHomeSection) -> Unit,
    onRetry: (String) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    val home = state.selected
    var displayedSourceKey by rememberSaveable { mutableStateOf(home?.key) }
    LaunchedEffect(home?.key) {
        if (displayedSourceKey != home?.key) {
            listState.scrollToItem(0)
            displayedSourceKey = home?.key
        }
    }
    var pulled by remember { mutableStateOf(false) }
    val refreshing = state.rows.values.any { it.loading }
    LaunchedEffect(refreshing) { if (!refreshing) pulled = false }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val columns = when {
            maxWidth >= 900.dp -> 3
            maxWidth >= 600.dp -> 2
            else -> 1
        }
        PullToRefreshBox(
            isRefreshing = pulled && refreshing,
            onRefresh = {
                pulled = true
                onRefresh()
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item("heading") {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                home?.title ?: "Manga",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            home?.let { Text(it.sourceName, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        if (!state.offline && home?.search != null) {
                            IconButton(onClick = { home.search?.let { onArchive(it.selections) } }) {
                                Icon(Icons.Outlined.Search, contentDescription = "Cerca nell’archivio")
                            }
                        }
                        if (!state.offline) {
                            IconButton(onClick = onRefresh, enabled = !refreshing) {
                                Icon(Icons.Outlined.Refresh, contentDescription = "Aggiorna Home")
                            }
                        }
                    }
                }
                if (state.homes.size > 1) {
                    item("sources") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(state.homes, key = { it.key }) {
                                FilterChip(
                                    selected = it.key == home?.key,
                                    onClick = { onSelectHome(it.key) },
                                    label = { Text(it.sourceName) },
                                )
                            }
                        }
                    }
                }
                if (state.history.isNotEmpty()) {
                    item("continue") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SectionTitle("Continua a leggere")
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(state.history, key = { it.mangaId }) { history ->
                                    Surface(
                                        onClick = { onResume(history) },
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainer,
                                        modifier = Modifier.width(260.dp),
                                    ) {
                                        Row(
                                            Modifier.padding(10.dp),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            Artwork(history.coverData, history.title, Modifier.width(60.dp))
                                            Column(Modifier.weight(1f).align(Alignment.CenterVertically)) {
                                                Text(
                                                    history.title,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    fontWeight = FontWeight.SemiBold,
                                                )
                                                Text(
                                                    "Riprendi la lettura",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.primary,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (state.offline) {
                    item("offline") {
                        Text(
                            "Modalità Solo scaricati: apri la biblioteca per leggere i capitoli sul dispositivo.",
                            Modifier.padding(20.dp),
                        )
                    }
                } else if (home != null) {
                    if (home.categories.isNotEmpty()) {
                        item("categories") {
                            var genresOpen by remember { mutableStateOf(false) }
                            Row(
                                Modifier.padding(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box {
                                    AssistChip(onClick = { genresOpen = true }, label = { Text("Generi") })
                                    DropdownMenu(
                                        expanded = genresOpen,
                                        onDismissRequest = { genresOpen = false },
                                        modifier = Modifier.heightIn(max = 400.dp),
                                    ) {
                                        home.categories.forEach { category ->
                                            DropdownMenuItem(text = { Text(category.title) }, onClick = {
                                                genresOpen = false
                                                onArchive(category.selections)
                                            })
                                        }
                                    }
                                }
                                home.search?.let { search ->
                                    AssistChip(onClick = {
                                        onArchive(search.selections)
                                    }, label = { Text(search.title) })
                                }
                            }
                        }
                    }
                    home.sections.forEach { section ->
                        val row = state.rows[section.id]
                        item("heading:" + section.id) {
                            Row(
                                Modifier.fillMaxWidth().padding(top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                SectionTitle(section.title, Modifier.weight(1f))
                                if (section.moreSelections != null || row?.page?.hasNextPage == true) {
                                    IconButton(onClick = { onMore(section) }, enabled = row?.loading != true) {
                                        Icon(
                                            Icons.AutoMirrored.Outlined.ArrowForward,
                                            contentDescription = "Mostra altri: " + section.title,
                                        )
                                    }
                                }
                            }
                        }
                        if (row?.page == null && (row == null || row.loading)) {
                            item("loading:" + section.id) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    repeat(3) {
                                        Box(
                                            Modifier.weight(1f).height(150.dp).clip(RoundedCornerShape(10.dp))
                                                .background(MaterialTheme.colorScheme.surfaceContainer),
                                        )
                                    }
                                }
                            }
                        }
                        if (row?.error != null) {
                            item("error:" + section.id) {
                                Column(Modifier.padding(horizontal = 20.dp)) {
                                    Text(row.error, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    OutlinedButton(onClick = { onRetry(section.id) }) { Text("Riprova") }
                                }
                            }
                        }
                        val entries = row?.page?.items.orEmpty()
                        if (entries.isEmpty() && row?.page != null && row.error == null) {
                            item("empty:" + section.id) {
                                Text("Nessun contenuto in questa sezione.", Modifier.padding(horizontal = 20.dp))
                            }
                        }
                        if (section.layout in listOf("chapters", "featured", "posters")) {
                            item("rail:" + section.id) {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                ) {
                                    items(entries, key = { it.key }) { entry ->
                                        Column(
                                            Modifier.width(152.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Artwork(
                                                entry.manga.copy(favorite = false),
                                                entry.manga.title,
                                                Modifier.fillMaxWidth().clickable { onManga(entry.manga) },
                                            )
                                            Text(
                                                entry.manga.title,
                                                Modifier.clickable { onManga(entry.manga) },
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                fontWeight = FontWeight.SemiBold,

                                            )
                                            entry.presentation?.chapters.orEmpty().forEach { chapter ->
                                                ChapterButton(
                                                    chapter,
                                                    state.opening == chapter.url,
                                                ) {
                                                    onChapter(
                                                        entry,
                                                        chapter,
                                                    )
                                                }
                                            }
                                            entry.presentation?.details.orEmpty().forEach {
                                                Text(
                                                    it,
                                                    style = MaterialTheme.typography.bodySmall,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            items(
                                entries.chunked(columns),
                                key = { section.id + ":" + it.first().key },
                                contentType = { section.layout },
                            ) { group ->
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    group.forEach { entry ->
                                        MangaUpdateCard(entry, state.opening, {
                                            onManga(entry.manga)
                                        }, Modifier.weight(1f)) {
                                            onChapter(entry, it)
                                        }
                                    }
                                    repeat(columns - group.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                        if (row?.page?.hasNextPage == true && entries.isNotEmpty()) {
                            item("next:" + section.id) {
                                TextButton(
                                    onClick = { onMore(section) },
                                    enabled = !row.loading,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(if (row.loading) "Caricamento…" else "Mostra altri")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        modifier.padding(horizontal = 20.dp),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun MangaUpdateCard(
    item: MangaHomeItem,
    opening: String?,
    onManga: () -> Unit,
    modifier: Modifier = Modifier,
    onChapter: (MangaHomeChapter) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.width(100.dp)) {
                Artwork(
                    item.manga.copy(favorite = false),
                    item.manga.title,
                    Modifier.fillMaxWidth().clickable(onClick = onManga),
                )
                item.presentation?.rank?.let { rank ->
                    Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(bottomEnd = 10.dp)) {
                        Text(
                            rank.toString(),
                            Modifier.padding(
                                horizontal = 10.dp,
                                vertical = 6.dp,
                            ),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    item.manga.title,
                    Modifier.clickable(onClick = onManga),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium,
                )
                item.presentation?.badges?.takeIf { it.isNotEmpty() }?.let {
                    Text(
                        it.joinToString(" · "),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                item.presentation?.details.orEmpty().forEach {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item.presentation?.chapters.orEmpty().forEach { chapter ->
                    ChapterButton(chapter, opening == chapter.url) { onChapter(chapter) }
                }
                if (item.presentation?.chapters.isNullOrEmpty()) {
                    TextButton(
                        onClick = onManga,
                        contentPadding = PaddingValues(horizontal = 0.dp),
                    ) { Text("Apri manga") }
                }
            }
        }
    }
}

@Composable
private fun ChapterButton(chapter: MangaHomeChapter, loading: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = !loading,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(7.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(
                horizontal = 10.dp,
                vertical = 8.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(if (loading) "Apertura…" else chapter.label, style = MaterialTheme.typography.labelLarge)
            if (chapter.isNew || !chapter.date.isNullOrBlank()) {
                Text(
                    if (chapter.isNew) "Nuovo" else chapter.date.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (chapter.isNew) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },

                )
            }
        }
    }
}

@Composable
private fun Artwork(data: Any, title: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var previous by remember { mutableStateOf<Painter?>(null) }
    var failed by remember(data) { mutableStateOf(false) }
    var retry by remember(data) { mutableIntStateOf(0) }
    val request = remember(data, retry, context) {
        ImageRequest.Builder(context).data(data).crossfade(180)
            .apply {
                extras[MangaCoverFetcher.USE_CUSTOM_COVER_KEY] =
                    data is tachiyomi.domain.entries.manga.model.MangaCover
                extras[ArtworkRetryKey] = retry
            }
            .build()
    }
    Box(
        modifier.aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Icon(
            Icons.AutoMirrored.Outlined.MenuBook,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .25f),
            modifier = Modifier.align(Alignment.Center).size(32.dp),
        )
        AsyncImage(
            model = request,
            contentDescription = title,
            placeholder = previous,
            error = previous,
            onSuccess = {
                previous = it.painter
                failed = false
            },
            onError = { failed = true },
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (failed) {
            IconButton(onClick = {
                failed = false
                retry++
            }, modifier = Modifier.align(Alignment.Center)) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Ricarica copertina")
            }
        }
    }
}

private val ArtworkRetryKey = Extras.Key(0)

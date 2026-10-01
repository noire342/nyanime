package eu.kanade.presentation.discovery.manga

import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import eu.kanade.presentation.discovery.HomeExploreAction
import eu.kanade.presentation.discovery.HomeGenreChip
import eu.kanade.presentation.discovery.HomeLoadingTransition
import eu.kanade.presentation.discovery.HomeMotion
import eu.kanade.presentation.discovery.HomePosterRowSkeleton
import eu.kanade.presentation.discovery.HomeSelectionChip
import eu.kanade.presentation.discovery.HomeSkeleton
import eu.kanade.presentation.motion.appMotionEnabled
import eu.kanade.presentation.privacy.nsfwPrivacy
import eu.kanade.presentation.privacy.privacyRegion
import eu.kanade.presentation.util.formatChapterNumber
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.coil.MangaCoverFetcher
import eu.kanade.tachiyomi.data.discovery.MangaGenreLabels
import eu.kanade.tachiyomi.data.discovery.MangaHomeChapter
import eu.kanade.tachiyomi.data.discovery.MangaHomeItem
import eu.kanade.tachiyomi.ui.discovery.manga.MangaHomeState
import eu.kanade.tachiyomi.ui.privacy.PrivacyArea
import tachiyomi.domain.discovery.SourceHomeSection
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.history.manga.model.MangaHistoryWithRelations
import tachiyomi.domain.updates.manga.model.MangaUpdatesWithRelations
import androidx.compose.ui.res.stringResource as androidStringResource

@Composable
fun MangaHomeContent(
    state: MangaHomeState,
    onRefresh: () -> Unit,
    onSelectHome: (String) -> Unit,
    onSelectAll: () -> Unit,
    onPreferredSource: (Long) -> Unit,
    onManga: (Manga) -> Unit,
    onChapter: (MangaHomeItem, MangaHomeChapter) -> Unit,
    onResume: (MangaHistoryWithRelations) -> Unit,
    onArchive: (Map<String, String>) -> Unit,
    onExplore: () -> Unit,
    onGenre: (String) -> Unit,
    onMore: (SourceHomeSection) -> Unit,
    onRetry: (String) -> Unit,
    onUpdates: () -> Unit,
    onUpdate: (MangaUpdatesWithRelations) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    val motionDuration = if (appMotionEnabled()) HomeMotion.CONTENT_MILLIS else 0
    val home = state.selected
    val sections = if (state.mixed) {
        state.homes.flatMap {
            it.sections
        }.distinctBy { it.id }
    } else {
        home?.sections.orEmpty()
    }
    val featuredRow = sections.firstOrNull()?.let { state.rows[it.id] }
    val featuredItem = featuredRow?.page?.items?.firstOrNull()
    val awaitingFeatured = featuredRow?.page == null && featuredRow?.error == null && featuredRow?.loading != false
    val personalUpdates = state.updates
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
                contentPadding = PaddingValues(
                    bottom =
                    24.dp + eu.kanade.tachiyomi.ui.home.LocalFloatingNavigationInset.current,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (home != null) {
                    item("hero") {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        ) {
                            HomeLoadingTransition(
                                loading = awaitingFeatured,
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = {
                                    HomeSkeleton(Modifier.fillMaxWidth().height(222.dp)) {
                                        Box(
                                            Modifier.fillMaxSize().background(
                                                MaterialTheme.colorScheme.surfaceContainerHigh,
                                            ),
                                        )
                                    }
                                },
                            ) {
                                if (featuredItem != null) {
                                    BoxWithConstraints(
                                        Modifier.fillMaxWidth().heightIn(min = 222.dp).nsfwPrivacy(featuredItem.manga),
                                    ) {
                                        val posterWidth = (maxWidth * 0.34f).coerceIn(76.dp, 132.dp)
                                        Row(
                                            Modifier.padding(12.dp),
                                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                                        ) {
                                            Artwork(
                                                featuredItem.manga.copy(favorite = false),
                                                featuredItem.manga.title,
                                                Modifier.width(posterWidth).clickable { onManga(featuredItem.manga) },
                                            )
                                            Column(
                                                Modifier.weight(1f),
                                                verticalArrangement = Arrangement.spacedBy(7.dp),
                                            ) {
                                                Text(
                                                    "IN PRIMO PIANO",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold,
                                                )
                                                Text(
                                                    featuredItem.manga.title,
                                                    style = MaterialTheme.typography.headlineSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 3,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                featuredItem.presentation?.chapters?.firstOrNull()?.let { chapter ->
                                                    Text(
                                                        chapter.label,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                }
                                                MangaSourceAction(featuredItem, onOpen = onManga)
                                                Button(
                                                    onClick = {
                                                        featuredItem.presentation?.chapters?.firstOrNull()?.let {
                                                            onChapter(featuredItem, it)
                                                        } ?: onManga(featuredItem.manga)
                                                    },
                                                    modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                                                    shape = RoundedCornerShape(12.dp),
                                                ) {
                                                    Text(
                                                        if (featuredItem.presentation?.chapters?.isNotEmpty() ==
                                                            true
                                                        ) {
                                                            androidStringResource(R.string.home_manga_read)
                                                        } else {
                                                            androidStringResource(R.string.home_open_manga)
                                                        },
                                                    )
                                                    Spacer(Modifier.width(6.dp))
                                                    Icon(
                                                        Icons.AutoMirrored.Outlined.ArrowForward,
                                                        null,
                                                        Modifier.size(18.dp),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (!state.initializing && state.homes.isEmpty()) {
                    item("missing-source") {
                        Text(
                            "Installa e abilita un’estensione manga per esplorare la Home. " +
                                "La tua biblioteca resta disponibile qui sopra.",
                            Modifier.padding(horizontal = 20.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (state.homes.size > 1) {
                    item("sources") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            item(key = "all") {
                                HomeSelectionChip("Per te", state.mixed, onSelectAll)
                            }
                            items(state.homes, key = { it.key }) {
                                HomeSelectionChip(it.sourceName, !state.mixed && it.key == home?.key) {
                                    onSelectHome(it.key)
                                }
                            }
                        }
                    }
                }
                if (state.history.isNotEmpty()) {
                    item("continue") {
                        Column(
                            modifier = Modifier.privacyRegion(PrivacyArea.RESUME),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            SectionTitle(androidStringResource(R.string.home_continue_reading))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(state.history, key = { it.mangaId }) { history ->
                                    Surface(
                                        onClick = { onResume(history) },
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainer,
                                        modifier = Modifier.width(282.dp).nsfwPrivacy(history.coverData),
                                    ) {
                                        Row(
                                            Modifier.padding(10.dp),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            Artwork(history.coverData, history.title, Modifier.width(68.dp))
                                            Column(Modifier.weight(1f).align(Alignment.CenterVertically)) {
                                                Text(
                                                    history.title,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    fontWeight = FontWeight.SemiBold,
                                                )
                                                if (history.chapterNumber > -1) {
                                                    Text(
                                                        "Cap. ${formatChapterNumber(history.chapterNumber)}",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = MaterialTheme.colorScheme.primary,
                                                    )
                                                }
                                                Text(
                                                    "Riprendi la lettura",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (personalUpdates.isNotEmpty()) {
                    item("personal-updates") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                Modifier.fillMaxWidth().padding(end = 20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                SectionTitle(androidStringResource(R.string.home_your_updates), Modifier.weight(1f))
                                TextButton(onClick = onUpdates) {
                                    Text(androidStringResource(R.string.home_see_all_updates))
                                }
                            }
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(personalUpdates, key = { it.chapterId }) { update ->
                                    Surface(
                                        onClick = { onUpdate(update) },
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainer,
                                        modifier = Modifier.width(282.dp).nsfwPrivacy(update.coverData),
                                    ) {
                                        Row(
                                            Modifier.padding(10.dp),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            Artwork(update.coverData, update.mangaTitle, Modifier.width(68.dp))
                                            Column(Modifier.weight(1f).align(Alignment.CenterVertically)) {
                                                Text(
                                                    update.mangaTitle,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    fontWeight = FontWeight.SemiBold,
                                                )
                                                Text(
                                                    "Nuovo · ${update.chapterName}",
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.primary,
                                                )
                                                Text(
                                                    androidStringResource(R.string.home_manga_read_arrow),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            androidStringResource(R.string.home_downloaded_manga_hint),
                            Modifier.padding(20.dp),
                        )
                    }
                } else if (home != null) {
                    sections.forEach { section ->
                        val row = state.rows[section.id]
                        val rail = section.layout in listOf("chapters", "featured", "posters")
                        val awaitingContent = row?.page == null && row?.error == null && row?.loading != false
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
                        if (!rail && awaitingContent) {
                            item("loading:" + section.id) {
                                HomePosterRowSkeleton(
                                    modifier = Modifier.animateItem(
                                        fadeInSpec = tween(motionDuration),
                                        fadeOutSpec = tween(motionDuration),
                                        placementSpec = tween(motionDuration),
                                    ),
                                )
                            }
                        }
                        if (row?.error != null) {
                            item("error:" + section.id) {
                                Column(Modifier.padding(horizontal = 20.dp)) {
                                    Text(row.error, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    OutlinedButton(onClick = {
                                        onRetry(section.id)
                                    }) { Text(androidStringResource(R.string.room_retry)) }
                                }
                            }
                        }
                        val entries = row?.page?.items.orEmpty()
                        if (entries.isEmpty() && row?.page != null && row.error == null) {
                            item("empty:" + section.id) {
                                Text(
                                    androidStringResource(R.string.home_section_empty),
                                    Modifier.padding(horizontal = 20.dp),
                                )
                            }
                        }
                        if (rail) {
                            item("rail:" + section.id) {
                                HomeLoadingTransition(
                                    loading = awaitingContent,
                                    placeholder = { HomePosterRowSkeleton() },
                                ) {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 20.dp),
                                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    ) {
                                        items(entries, key = { it.key }) { entry ->
                                            Column(
                                                Modifier.width(152.dp).nsfwPrivacy(entry.manga),
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
                                                MangaSourceSelector(entry, onManga)
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
                            }
                        } else {
                            items(
                                entries.chunked(columns),
                                key = { section.id + ":" + it.first().key },
                                contentType = { section.layout },
                            ) { group ->
                                Row(
                                    Modifier.animateItem(
                                        fadeInSpec = tween(motionDuration),
                                        fadeOutSpec = tween(motionDuration),
                                        placementSpec = tween(motionDuration),
                                    ).fillMaxWidth().padding(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    group.forEach { entry ->
                                        MangaUpdateCard(entry, state.opening, {
                                            onManga(entry.manga)
                                        }, state, onManga, onPreferredSource, Modifier.weight(1f)) {
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
                                    Text(
                                        if (row.loading) {
                                            androidStringResource(
                                                R.string.home_loading,
                                            )
                                        } else {
                                            androidStringResource(R.string.home_more)
                                        },
                                    )
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
        modifier.padding(horizontal = 16.dp),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun MangaUpdateCard(
    item: MangaHomeItem,
    opening: String?,
    onManga: () -> Unit,
    state: MangaHomeState,
    onSourceManga: (Manga) -> Unit,
    onPreferredSource: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onChapter: (MangaHomeChapter) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth().nsfwPrivacy(item.manga),
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
                MangaSourceSelector(item, onSourceManga)
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
                    ) { Text(androidStringResource(R.string.home_open_manga)) }
                }
            }
        }
    }
}

@Composable
private fun MangaSourceSelector(
    item: MangaHomeItem,
    onManga: (Manga) -> Unit,
) {
    MangaSourceAction(item, compact = true, onOpen = onManga)
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
            .clip(RoundedCornerShape(12.dp))
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
                Icon(Icons.Outlined.Refresh, contentDescription = androidStringResource(R.string.home_reload_cover))
            }
        }
    }
}

private val ArtworkRetryKey = Extras.Key(0)

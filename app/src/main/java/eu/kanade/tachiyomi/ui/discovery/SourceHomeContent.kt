package eu.kanade.tachiyomi.ui.discovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.discovery.ContinueWatchingRow
import eu.kanade.presentation.discovery.DiscoveryHomeHeader
import eu.kanade.presentation.discovery.HomeContentReveal
import eu.kanade.presentation.discovery.LoadNotice
import eu.kanade.presentation.discovery.LocalAnimeRow
import eu.kanade.presentation.discovery.SectionHeader
import eu.kanade.presentation.discovery.SourceFeaturedCarousel
import eu.kanade.presentation.discovery.SourceFeaturedSection
import eu.kanade.presentation.discovery.SourceHomeDateSelector
import eu.kanade.presentation.discovery.SourceHomeLogo
import eu.kanade.presentation.discovery.SourceHomePosterCard
import eu.kanade.presentation.motion.appMotionEnabled
import eu.kanade.presentation.theme.LocalNyanimeStyle
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.updates.AcknowledgeUpdateNoticeWhenVisible
import eu.kanade.tachiyomi.ui.updates.UpdatesTab
import eu.kanade.tachiyomi.ui.updates.dismissLibraryUpdate
import eu.kanade.tachiyomi.ui.updates.hasNewLibraryUpdateNotice
import eu.kanade.tachiyomi.ui.updates.markLibraryUpdateNoticesSeen
import kotlinx.coroutines.launch
import tachiyomi.domain.discovery.SectionState
import tachiyomi.domain.discovery.SourceHomeGroup
import tachiyomi.domain.discovery.SourceHomeRequest
import tachiyomi.domain.discovery.homeItemKey
import tachiyomi.domain.discovery.homePresentation
import tachiyomi.presentation.core.components.material.PullRefresh
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun DiscoveryTab.SourceHomeContent(homeKey: String, homes: List<SourceHomeGroup>, onSelect: (String?) -> Unit) {
    val model = rememberScreenModel(tag = homeKey) { SourceHomeScreenModel(homeKey) }
    val state by model.state.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.currentOrThrow
    val context = LocalContext.current
    val dismissedUpdates = remember { Injekt.get<UiPreferences>().dismissedLibraryUpdates() }
    val seenNotices = remember { Injekt.get<UiPreferences>().lastSeenAnimeUpdateNotice() }
    val lastSeenAt by seenNotices.changes().collectAsState(initial = seenNotices.get())
    val autoAcknowledge = remember { Injekt.get<UiPreferences>().autoAcknowledgeHomeUpdates() }
    val acknowledgeOnScroll by autoAcknowledge.changes().collectAsState(initial = autoAcknowledge.get())
    val updateKeys = state.updates.data.orEmpty().mapNotNull { it.updateKey }.toSet()
    val scope = rememberCoroutineScope()
    val listState = rememberSaveable(homeKey, saver = LazyListState.Saver) { LazyListState() }
    LaunchedEffect(Unit) { (context as? MainActivity)?.ready = true }
    val access = state.access
    val source = access.group
    // Parent and child observe availability independently: never render a stale branded frame.
    if (access.loading || source == null) return
    val modern = LocalNyanimeStyle.current
    val featuredRow = source.rows.firstOrNull { row ->
        row.sections.size == 1 && row.sections.first().layout == "featured" && !row.sections.first().supportsDate
    }.takeIf { modern }
    val heroSection = featuredRow?.sections?.firstOrNull()
        ?: source.rows.firstOrNull()?.sections?.firstOrNull()?.takeIf { modern }
    val hasNewUpdates = hasNewLibraryUpdateNotice(updateKeys, lastSeenAt)
    val motion = appMotionEnabled()
    val updatesSectionKey = "updates:" + source.id
    val updatesIndex = (if (!access.offline && source.searchable) 1 else 0) +
        (if (!access.offline && heroSection != null) 1 else 0) +
        1
    AcknowledgeUpdateNoticeWhenVisible(
        listState,
        updatesSectionKey,
        updateKeys,
        hasNewUpdates && acknowledgeOnScroll,
        seenNotices,
    )
    val openUpdatesScreen: () -> Unit = {
        markLibraryUpdateNoticesSeen(seenNotices, updateKeys)
        navigator.push(UpdatesTab)
    }
    val revealUpdates: () -> Unit = {
        markLibraryUpdateNoticesSeen(seenNotices, updateKeys)
        scope.launch {
            if (motion) listState.animateScrollToItem(updatesIndex) else listState.scrollToItem(updatesIndex)
        }
    }
    LifecycleStartEffect(access) {
        model.onResume()
        onStopOrDispose { model.onPause() }
    }
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
            onUpdates = revealUpdates,
            hasUpdates = hasNewUpdates,
            artworkRefreshKey = state.artworkRefreshKey,
            logo = source.providers.takeUnless { access.offline }
                ?.distinctBy { it.id }?.singleOrNull()?.let { provider ->
                    state.sections.values.asSequence().flatMap { it.data?.items.orEmpty().asSequence() }
                        .filter { it.source == provider.id }
                        .mapNotNull { it.homePresentation }
                        .firstOrNull { it.logoUrl != null }
                        ?.let {
                            SourceHomeLogo(
                                provider.id,
                                provider.sourceName,
                                requireNotNull(it.logoUrl),
                                it.logoName,
                                it.logoBackground,
                            )
                        }
                },
        )
    }) { padding ->
        HomeContentReveal(homeKey) {
            PullRefresh(
                indicatorOnGestureOnly = true,
                refreshing = state.sections.values.any { it.loading },
                enabled = !access.offline,
                onRefresh = model::refresh,
                modifier = Modifier.padding(padding),
            ) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (!access.offline && source.searchable) {
                        item(key = "explore:" + source.id) {
                            eu.kanade.presentation.discovery.SourceHomeExploreBar(
                                source.categories,
                                onBrowse = {
                                    navigator.push(
                                        SourceHomeListScreen(
                                            homeKey,
                                            SourceHomeRequest.SEARCH,
                                            "Esplora ${source.title}",
                                        ),
                                    )
                                },
                                onCategory = { navigator.push(SourceHomeListScreen(homeKey, it.id, it.title)) },
                            )
                        }
                    }
                    if (!access.offline && heroSection != null) {
                        item(key = "hero:" + source.id) {
                            LaunchedEffect(access, heroSection.id) { model.load(heroSection.id) }
                            val featured = state.sections[heroSection.id] ?: SectionState()
                            SourceFeaturedSection(
                                state = featured,
                                title = heroSection.title,
                                refreshKey = state.artworkRefreshKey,
                                limit = if (featuredRow == null) 8 else null,
                                onBrowse = {
                                    navigator.push(SourceHomeListScreen(homeKey, heroSection.id, heroSection.title))
                                },
                                onRetry = { model.load(heroSection.id, true) },
                                onOpen = { navigator.push(AnimeScreen(it.id, true)) },
                            )
                        }
                    }
                    // Local playback never depends on a successful remote feed or a featured row.
                    item(key = "resume:" + source.id) {
                        SectionHeader("Continua a guardare") { navigator.push(SourceHomeHistoryScreen(homeKey)) }
                        ContinueWatchingRow(
                            state.resume,
                            onOpen = { navigator.push(AnimeScreen(it)) },
                            emptyMessage = "I titoli che guardi in questa Home compariranno qui.",
                        ) { item -> scope.launch { context.playDiscoveryEpisode(item.episode) } }
                    }
                    if (state.updates.data?.isNotEmpty() == true) {
                        item(key = "updates:" + source.id) {
                            SectionHeader("Le tue novità", openUpdatesScreen)
                            LocalAnimeRow(
                                state.updates,
                                onOpen = { item ->
                                    item.updateKey?.let { dismissLibraryUpdate(dismissedUpdates, it) }
                                    navigator.push(AnimeScreen(item.anime.id))
                                },
                            ) { item ->
                                item.updateKey?.let { dismissLibraryUpdate(dismissedUpdates, it) }
                                scope.launch { context.playDiscoveryEpisode(item.episode) }
                            }
                        }
                    }
                    if (access.offline) {
                        item(key = "offline") {
                            Text("Solo download · nessuna richiesta alle fonti", Modifier.padding(horizontal = 16.dp))
                        }
                    }
                    if (!access.offline) {
                        items(source.rows.filter { it.id != featuredRow?.id }, key = { it.id }) { row ->
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
                                LoadNotice(value.loading, value.error, value.stale) {
                                    model.load(section.id, true, date)
                                }
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
                        if (source.sections.isEmpty()) {
                            item {
                                Text(
                                    "Aggiorna l’estensione per usare le sezioni della Home",
                                    Modifier.padding(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

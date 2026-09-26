package eu.kanade.tachiyomi.ui.discovery.manga

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleStartEffect
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.discovery.manga.MangaHomeContent
import eu.kanade.presentation.motion.appMotionEnabled
import eu.kanade.tachiyomi.ui.browse.manga.source.browse.BrowseMangaSourceScreen
import eu.kanade.tachiyomi.ui.entries.manga.MangaScreen
import eu.kanade.tachiyomi.ui.library.manga.MangaLibraryTab
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.updates.AcknowledgeUpdateNoticeWhenVisible
import eu.kanade.tachiyomi.ui.updates.MangaUpdatesScreen
import eu.kanade.tachiyomi.ui.updates.hasNewLibraryUpdateNotice
import eu.kanade.tachiyomi.ui.updates.inboxKey
import eu.kanade.tachiyomi.ui.updates.markLibraryUpdateNoticesSeen
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun MangaHomeTabContent(library: @Composable () -> Unit) {
    val model = MangaLibraryTab.rememberScreenModel { MangaHomeScreenModel() }
    val state by model.state.collectAsState()
    val navigator = LocalNavigator.currentOrThrow
    val homeKey = state.selected?.key.orEmpty()
    val seenNotices = remember { Injekt.get<UiPreferences>().lastSeenMangaUpdateNotice() }
    val lastSeenAt by seenNotices.changes().collectAsState(initial = seenNotices.get())
    val autoAcknowledge = remember { Injekt.get<UiPreferences>().autoAcknowledgeHomeUpdates() }
    val acknowledgeOnScroll by autoAcknowledge.changes().collectAsState(initial = autoAcknowledge.get())
    val scope = rememberCoroutineScope()
    val listState = rememberSaveable(homeKey, saver = LazyListState.Saver) { LazyListState() }
    val updateKeys = state.updates.map { it.inboxKey() }.toSet()
    val hasNewUpdates = hasNewLibraryUpdateNotice(updateKeys, lastSeenAt)
    val motion = appMotionEnabled()
    val updatesIndex = 1 +
        (if (state.homes.size > 1) 1 else 0) +
        (if (state.history.isNotEmpty()) 1 else 0)
    val showLibrary by MangaLibraryTab.libraryRequested.collectAsState()
    LifecycleStartEffect(showLibrary, homeKey) {
        val refreshJob = if (!showLibrary) {
            scope.launch {
                model.refreshIfStale()
                while (true) {
                    delay(10 * 60_000L)
                    model.refreshIfStale()
                }
            }
        } else {
            null
        }
        onStopOrDispose { refreshJob?.cancel() }
    }
    AcknowledgeUpdateNoticeWhenVisible(
        listState,
        "personal-updates",
        updateKeys,
        hasNewUpdates && acknowledgeOnScroll && !showLibrary,
        seenNotices,
    )
    val openUpdates: () -> Unit = {
        markLibraryUpdateNoticesSeen(seenNotices, updateKeys)
        navigator.push(MangaUpdatesScreen)
    }
    val revealUpdates: () -> Unit = {
        markLibraryUpdateNoticesSeen(seenNotices, updateKeys)
        scope.launch {
            if (motion) listState.animateScrollToItem(updatesIndex) else listState.scrollToItem(updatesIndex)
        }
    }
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val savedPages = rememberSaveableStateHolder()
    DisposableEffect(model) { onDispose { model.cancelOpening() } }
    LaunchedEffect(showLibrary) {
        if (!showLibrary) {
            eu.kanade.tachiyomi.ui.home.HomeScreen.showBottomNav(true)
        } else {
            model.cancelOpening()
        }
    }
    LaunchedEffect(model) {
        model.events.collectLatest { event ->
            when (event) {
                is MangaHomeScreenModel.Event.Read ->
                    context.startActivity(ReaderActivity.newIntent(context, event.mangaId, event.chapterId))
                is MangaHomeScreenModel.Event.Details -> navigator.push(MangaScreen(event.mangaId))
                is MangaHomeScreenModel.Event.Error -> snackbar.showSnackbar(event.message)
            }
        }
    }
    LaunchedEffect(state.initializing, state.homes.isNotEmpty(), showLibrary) {
        if (!state.initializing && state.homes.isNotEmpty() && !showLibrary) (context as? MainActivity)?.ready = true
    }
    if (state.homes.isEmpty()) {
        library()
        return
    }
    BackHandler(enabled = showLibrary) { MangaLibraryTab.libraryRequested.value = false }
    Surface {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
            PrimaryTabRow(selectedTabIndex = if (showLibrary) 1 else 0) {
                Tab(selected = !showLibrary, onClick = {
                    MangaLibraryTab.libraryRequested.value = false
                }, text = { Text("Home") })
                Tab(selected = showLibrary, onClick = {
                    MangaLibraryTab.libraryRequested.value = true
                }, text = { Text("Biblioteca") })
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Crossfade(
                    targetState = showLibrary,
                    animationSpec = tween(180),
                    label = "manga-home-library",
                ) { libraryPage ->
                    savedPages.SaveableStateProvider(if (libraryPage) "library" else "home") {
                        if (libraryPage) {
                            library()
                        } else {
                            MangaHomeContent(
                                state = state,
                                onRefresh = model::refresh,
                                onSelectHome = model::selectHome,
                                onManga = { navigator.push(MangaScreen(it.id, fromSource = true)) },
                                onChapter = model::openChapter,
                                onResume = model::resume,
                                onArchive = { filters ->
                                    state.selected?.let { navigator.push(BrowseMangaSourceScreen(it.id, "", filters)) }
                                },
                                onMore = { section ->
                                    val filters = section.moreSelections
                                    if (filters != null) {
                                        state.selected?.let {
                                            navigator.push(BrowseMangaSourceScreen(it.id, "", filters))
                                        }
                                    } else {
                                        model.loadSection(section.id, next = true)
                                    }
                                },
                                onRetry = { model.loadSection(it) },
                                onUpdates = openUpdates,
                                onNoticeClick = revealUpdates,
                                hasNewUpdates = hasNewUpdates,
                                onUpdate = { update ->
                                    model.dismissUpdate(update)
                                    context.startActivity(
                                        ReaderActivity.newIntent(context, update.mangaId, update.chapterId),
                                    )
                                },
                                listState = listState,
                            )
                        }
                    }
                }
                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

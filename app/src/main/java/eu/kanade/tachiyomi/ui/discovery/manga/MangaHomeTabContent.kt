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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.discovery.manga.MangaHomeContent
import eu.kanade.tachiyomi.ui.browse.manga.source.browse.BrowseMangaSourceScreen
import eu.kanade.tachiyomi.ui.entries.manga.MangaScreen
import eu.kanade.tachiyomi.ui.library.manga.MangaLibraryTab
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import kotlinx.coroutines.flow.collectLatest

@Composable
fun MangaHomeTabContent(library: @Composable () -> Unit) {
    val model = MangaLibraryTab.rememberScreenModel { MangaHomeScreenModel() }
    val state by model.state.collectAsState()
    val showLibrary by MangaLibraryTab.libraryRequested.collectAsState()
    val context = LocalContext.current
    val navigator = LocalNavigator.currentOrThrow
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
                            )
                        }
                    }
                }
                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

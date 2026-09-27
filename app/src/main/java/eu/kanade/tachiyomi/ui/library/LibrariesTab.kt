package eu.kanade.tachiyomi.ui.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.presentation.motion.appMotionEnabled
import eu.kanade.tachiyomi.ui.library.anime.AnimeLibraryTab
import eu.kanade.tachiyomi.ui.library.manga.MangaLibraryTab
import kotlinx.coroutines.flow.MutableStateFlow
import eu.kanade.presentation.util.Tab as AppTab

/** One personal collection destination; discovery belongs to Home. */
data object LibrariesTab : AppTab {
    private val mangaSelected = MutableStateFlow(false)

    fun showAnime() {
        mangaSelected.value = false
    }
    fun showManga() {
        mangaSelected.value = true
    }

    override val options: TabOptions
        @Composable get() = TabOptions(
            index = 1u,
            title = "Biblioteca & Libreria",
            icon = rememberVectorPainter(Icons.Outlined.CollectionsBookmark),
        )

    @Composable
    override fun Content() {
        val manga by mangaSelected.collectAsState()
        val motion = appMotionEnabled()
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Text(
                    "Biblioteca & Libreria",
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.headlineMedium,
                )
                PrimaryTabRow(selectedTabIndex = if (manga) 1 else 0) {
                    Tab(selected = !manga, onClick = ::showAnime, text = { Text("Anime") })
                    Tab(selected = manga, onClick = ::showManga, text = { Text("Manga") })
                }
                AnimatedContent(
                    targetState = manga,
                    transitionSpec = {
                        fadeIn(tween(if (motion) 180 else 0)) togetherWith
                            fadeOut(tween(if (motion) 120 else 0))
                    },
                    modifier = Modifier.weight(1f),
                    label = "personal-libraries",
                ) { showManga ->
                    if (showManga) MangaLibraryTab.LibraryContent() else AnimeLibraryTab.Content()
                }
            }
        }
    }
}

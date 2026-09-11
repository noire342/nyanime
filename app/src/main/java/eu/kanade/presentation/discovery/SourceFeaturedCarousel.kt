package eu.kanade.presentation.discovery

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import tachiyomi.domain.discovery.homeItemKey
import tachiyomi.domain.discovery.homePresentation
import tachiyomi.domain.entries.anime.model.Anime

/** The extension supplies every title, image and synopsis; there is no catalogue resolution here. */
@Composable
fun SourceFeaturedCarousel(items: List<Anime>, refreshKey: Int = 0, onClick: (Anime) -> Unit) {
    if (items.isEmpty()) return
    val pager = rememberPagerState { items.size }
    Column {
        HorizontalPager(state = pager, key = { items[it].homeItemKey }) { index ->
            val anime = items[index]
            val presentation = anime.homePresentation
            CinematicHero(
                title = anime.title,
                eyebrow = "In evidenza · ${index + 1} di ${items.size}",
                metadata = (presentation?.badges.orEmpty() + presentation?.details.orEmpty()).joinToString(" · "),
                description = anime.description,
                actionLabel = "Apri episodi",
                onOpen = { onClick(anime) },
            ) {
                SourceHomeArtwork(
                    data = anime,
                    background = !anime.backgroundUrl.isNullOrBlank(),
                    refreshKey = refreshKey,
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
        CarouselPosition(pager.currentPage, items.size)
    }
}

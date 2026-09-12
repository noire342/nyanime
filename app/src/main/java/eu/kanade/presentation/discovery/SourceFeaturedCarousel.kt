package eu.kanade.presentation.discovery

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import eu.kanade.presentation.motion.posterOpen
import eu.kanade.presentation.motion.posterSource
import eu.kanade.presentation.motion.posterSourcePlaceholder
import eu.kanade.presentation.motion.rememberPosterSource
import eu.kanade.presentation.theme.LocalNyanimeStyle
import tachiyomi.domain.discovery.homeItemKey
import tachiyomi.domain.discovery.homePresentation
import tachiyomi.domain.entries.anime.model.Anime
import kotlin.math.absoluteValue

/** The extension supplies every title, image and synopsis; there is no catalogue resolution here. */
@Composable
fun SourceFeaturedCarousel(
    items: List<Anime>,
    refreshKey: Int = 0,
    title: String = "In evidenza",
    onBrowse: (() -> Unit)? = null,
    onClick: (Anime) -> Unit,
) {
    if (!LocalNyanimeStyle.current) {
        return eu.kanade.presentation.discovery.legacy.SourceFeaturedCarousel(items, refreshKey, onClick)
    }
    if (items.isEmpty()) return
    val pager = rememberPagerState { items.size }
    Column {
        HorizontalPager(state = pager, key = { items[it].homeItemKey }) { index ->
            val anime = items[index]
            val poster = rememberPosterSource(anime)
            val openDetails = posterOpen(poster, anime.title) { onClick(anime) }
            val presentation = anime.homePresentation
            Box(
                Modifier.graphicsLayer {
                    val offset = ((pager.currentPage - index) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(
                        0f,
                        1f,
                    )
                    alpha = 1f - offset * 0.25f
                    scaleX = 1f - offset * 0.035f
                    scaleY = scaleX
                },
            ) {
                CinematicHero(
                    title = anime.title,
                    eyebrow = "$title · ${index + 1} di ${items.size}",
                    metadata = (presentation?.badges.orEmpty() + presentation?.details.orEmpty()).joinToString(" · "),
                    description = anime.description,
                    actionLabel = "Apri episodi",
                    onOpen = openDetails,
                ) {
                    SourceHomeArtwork(
                        data = anime,
                        background = !anime.backgroundUrl.isNullOrBlank(),
                        refreshKey = refreshKey,
                        contentDescription = null,
                        modifier = Modifier.matchParentSize().posterSource(poster),
                        initialPainter = posterSourcePlaceholder(poster),
                        onPainterReady = { poster.painter = it },
                    )
                }
            }
        }
        CarouselPosition(pager.currentPage, items.size, onBrowse)
    }
}

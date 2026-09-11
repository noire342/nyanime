package eu.kanade.presentation.discovery

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import tachiyomi.domain.discovery.homePresentation
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.asAnimeCover

@Composable
fun SourceHomePosterCard(
    anime: Anime,
    sourceLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    refreshKey: Int = 0,
) {
    val presentation = anime.homePresentation
    PosterCard(
        title = anime.title,
        cover = anime.asAnimeCover(),
        subtitle = (presentation?.details.orEmpty() + sourceLabel).filter { it.isNotBlank() }.joinToString("\n"),
        onClick = onClick,
        modifier = modifier,
        badges = presentation?.badges.orEmpty(),
        subtitleMaxLines = 12,
        artworkRefreshKey = refreshKey,
    )
}

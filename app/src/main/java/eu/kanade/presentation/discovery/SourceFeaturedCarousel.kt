package eu.kanade.presentation.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import tachiyomi.domain.entries.anime.model.Anime

/** The extension supplies every title, image and synopsis; there is no catalogue resolution here. */
@Composable
fun SourceFeaturedCarousel(items: List<Anime>, onClick: (Anime) -> Unit) {
    if (items.isEmpty()) return
    val pager = rememberPagerState { items.size }
    HorizontalPager(
        state = pager,
        key = { items[it].id },
        contentPadding = PaddingValues(horizontal = 16.dp),
        pageSpacing = 12.dp,
    ) { index ->
        val anime = items[index]
        Card(onClick = { onClick(anime) }, modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().heightIn(min = 340.dp), contentAlignment = Alignment.BottomStart) {
                AsyncImage(
                    model = anime.backgroundUrl ?: anime.thumbnailUrl,
                    contentDescription = anime.title,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                )
                Column(
                    Modifier.fillMaxWidth().background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.95f))),
                    ).padding(start = 20.dp, end = 20.dp, bottom = 20.dp, top = 80.dp),
                ) {
                    Text(
                        "${index + 1} / ${items.size}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        anime.title,
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    anime.description?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            it,
                            Modifier.padding(top = 8.dp),
                            color = Color.White,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Text(
                        "Apri episodi →",
                        Modifier.padding(top = 12.dp),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

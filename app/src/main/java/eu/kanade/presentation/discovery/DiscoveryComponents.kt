package eu.kanade.presentation.discovery

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import eu.kanade.presentation.motion.modernMotionEnabled
import eu.kanade.presentation.motion.posterForeground
import eu.kanade.presentation.motion.posterOpen
import eu.kanade.presentation.motion.posterSource
import eu.kanade.presentation.motion.posterSourcePlaceholder
import eu.kanade.presentation.motion.rememberPosterSource
import eu.kanade.presentation.theme.LocalNyanimeStyle
import eu.kanade.tachiyomi.data.discovery.LocalHomeItem
import tachiyomi.domain.discovery.CatalogAnime
import tachiyomi.domain.discovery.CatalogFeed
import tachiyomi.domain.discovery.SectionState
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.AnimeCover
import tachiyomi.domain.entries.anime.model.asAnimeCover
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

fun CatalogFeed.displayTitle(): String = when (this) {
    CatalogFeed.TRENDING -> "In tendenza"
    CatalogFeed.SEASON -> "Questa stagione"
    CatalogFeed.TOP -> "Più apprezzati"
    CatalogFeed.NEXT_SEASON -> "Prossima stagione"
    CatalogFeed.WEEK -> "Questa settimana"
    CatalogFeed.SEARCH -> "Cerca anime"
}

fun airingLabel(anime: CatalogAnime): String? = anime.airingAt?.let {
    val date = Instant.ofEpochSecond(it).atZone(ZoneId.systemDefault())
    "Ep. ${anime.airingEpisode ?: "?"} · ${DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).format(date)}"
}

fun metadataLabel(value: String?): String? = when (value) {
    "RELEASING" -> "In corso"
    "FINISHED" -> "Concluso"
    "NOT_YET_RELEASED" -> "In arrivo"
    "CANCELLED" -> "Annullato"
    "HIATUS" -> "In pausa"
    "WINTER" -> "Inverno"
    "SPRING" -> "Primavera"
    "SUMMER" -> "Estate"
    "FALL" -> "Autunno"
    "TV_SHORT" -> "Serie breve"
    "MOVIE" -> "Film"
    "SPECIAL" -> "Speciale"
    "PREQUEL" -> "Prequel"
    "SEQUEL" -> "Sequel"
    "SIDE_STORY" -> "Storia secondaria"
    "ALTERNATIVE" -> "Versione alternativa"
    "SPIN_OFF" -> "Spin-off"
    else -> value?.replace('_', ' ')
}

@Composable
fun SectionHeader(title: String, more: (() -> Unit)? = null) {
    if (!LocalNyanimeStyle.current) return eu.kanade.presentation.discovery.legacy.SectionHeader(title, more)
    Row(
        Modifier.fillMaxWidth().padding(
            horizontal = 16.dp,
            vertical = 8.dp,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            Modifier.weight(1f).semantics {
                heading()
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        if (more != null) {
            IconButton(onClick = more) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "Mostra tutti: $title") }
        }
    }
}

@Composable
fun LoadNotice(loading: Boolean, error: String?, stale: Boolean = false, retry: (() -> Unit)? = null) {
    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
    if (stale || error != null) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
            if (stale) Text("Dati salvati · aggiornamento non disponibile", style = MaterialTheme.typography.labelSmall)
            error?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (retry != null && !loading) TextButton(onClick = retry) { Text("Riprova") }
        }
    }
}

@Composable
fun PosterCard(
    title: String,
    cover: Any?,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badges: List<String> = emptyList(),
    subtitleMaxLines: Int = 3,
    artworkRefreshKey: Int = 0,
) {
    if (!LocalNyanimeStyle.current) {
        return eu.kanade.presentation.discovery.legacy.PosterCard(
            title,
            cover,
            subtitle,
            onClick,
            modifier,
            badges,
            subtitleMaxLines,
            artworkRefreshKey,
        )
    }
    var information by rememberSaveable(title) { mutableStateOf(false) }
    val poster = rememberPosterSource(cover)
    val openDetails = posterOpen(poster, title, onClick)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val motion = modernMotionEnabled()
    val pressAlpha = animateFloatAsState(if (motion && pressed) 0.9f else 1f, tween(100), label = "posterPress")
    if (information) {
        TitleInformationSheet(title, badges.joinToString(" · "), subtitle, { information = false }, onClick)
    }
    Column(
        modifier.width((132 * LocalDensity.current.fontScale.coerceIn(1f, 1.5f)).dp)
            .graphicsLayer {
                alpha = pressAlpha.value
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = "Apri $title",
                onClick = openDetails,
            ).padding(bottom = 8.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            if (cover is AnimeCover || cover is Anime) {
                SourceHomeArtwork(
                    cover,
                    Modifier.fillMaxSize().posterSource(poster),
                    refreshKey = artworkRefreshKey,
                    initialPainter = posterSourcePlaceholder(poster),
                    onPainterReady = { poster.painter = it },
                )
            } else {
                AsyncImage(
                    model = cover,
                    placeholder = posterSourcePlaceholder(poster),
                    error = posterSourcePlaceholder(poster),
                    onSuccess = { poster.painter = it.painter },
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().posterSource(poster),
                    contentScale = ContentScale.Crop,
                )
            }
            if (badges.isNotEmpty()) {
                Text(
                    badges.joinToString(" · "),
                    Modifier.align(Alignment.BottomStart).fillMaxWidth()
                        .posterForeground(poster)
                        .background(Color.Black.copy(alpha = 0.84f)).padding(horizontal = 6.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!subtitle.isNullOrBlank() || badges.isNotEmpty()) {
                IconButton(onClick = {
                    information = true
                }, modifier = Modifier.align(Alignment.TopEnd).posterForeground(poster)) {
                    Icon(
                        Icons.Outlined.Info,
                        "Informazioni su $title",
                        Modifier.background(Color.Black.copy(alpha = 0.72f), CircleShape).padding(3.dp),
                        tint = Color.White,
                    )
                }
            }
        }
        Text(
            title,
            Modifier.posterForeground(poster).padding(top = 8.dp),
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            subtitle.orEmpty(),
            modifier = Modifier.posterForeground(poster),
            maxLines = subtitleMaxLines.coerceIn(1, 2),
            minLines = subtitleMaxLines.coerceIn(1, 2),
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun CatalogRow(items: List<CatalogAnime>, onClick: (CatalogAnime) -> Unit, calendar: Boolean = false) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(items, key = { "${it.id.provider}:${it.id.value}:${it.airingAt}" }) { anime ->
            PosterCard(
                anime.title,
                anime.cover,
                if (calendar) {
                    airingLabel(anime)
                } else {
                    listOfNotNull(
                        anime.score?.let { "★ $it/100" },
                        metadataLabel(anime.format),
                    ).joinToString(" · ")
                },
                { onClick(anime) },
            )
        }
    }
}

@Composable
fun FeaturedCarousel(items: List<CatalogAnime>, onClick: (CatalogAnime) -> Unit) {
    if (!LocalNyanimeStyle.current) return eu.kanade.presentation.discovery.legacy.FeaturedCarousel(items, onClick)
    if (items.isEmpty()) return
    val pager = rememberPagerState { items.size.coerceAtMost(5) }
    Column {
        HorizontalPager(state = pager, key = { "${items[it].id.provider}:${items[it].id.value}" }) { index ->
            val anime = items[index]
            val poster = rememberPosterSource(anime.banner ?: anime.cover)
            val openDetails = posterOpen(poster, anime.title) { onClick(anime) }
            CinematicHero(
                title = anime.title,
                eyebrow = "In evidenza · ${index + 1} di ${pager.pageCount}",
                metadata = listOfNotNull(
                    anime.score?.let {
                        "★ $it/100"
                    },
                    anime.genres.joinToString(" · "),
                ).joinToString(" · "),
                description = anime.synopsis,
                actionLabel = "Scopri il titolo",
                onOpen = openDetails,
                poster = poster,
            ) {
                AsyncImage(
                    anime.banner ?: anime.cover,
                    null,
                    Modifier.matchParentSize().posterSource(poster),
                    placeholder = posterSourcePlaceholder(poster),
                    error = posterSourcePlaceholder(poster),
                    onSuccess = { poster.painter = it.painter },
                    contentScale = ContentScale.Crop,
                )
            }
        }
        CarouselPosition(pager.currentPage, pager.pageCount)
    }
}

@Composable
fun LocalAnimeRow(
    state: SectionState<List<LocalHomeItem>>,
    onOpen: (Long) -> Unit,
    emptyMessage: String = "Gli anime che segui compariranno qui.",
    onHide: ((LocalHomeItem) -> Unit)? = null,
    onPlay: (LocalHomeItem) -> Unit,
) {
    if (!LocalNyanimeStyle.current) {
        return eu.kanade.presentation.discovery.legacy.LocalAnimeRow(state, onOpen, emptyMessage, onHide, onPlay)
    }
    LoadNotice(state.loading, state.error)
    val items = state.data.orEmpty()
    if (!state.loading && items.isEmpty() && state.error == null) {
        Text(
            emptyMessage,
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cardWidth = (228 * LocalDensity.current.fontScale.coerceIn(1f, 1.4f)).dp
            .coerceAtMost((maxWidth - 32.dp).coerceAtLeast(160.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items, key = { it.anime.id }) { item ->
                val poster = rememberPosterSource(item.anime)
                val openDetails = posterOpen(poster, item.anime.title) { onOpen(item.anime.id) }
                Column(Modifier.width(cardWidth)) {
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    ) {
                        SourceHomeArtwork(
                            item.anime,
                            Modifier.fillMaxSize().posterSource(poster),
                            background = !item.anime.backgroundUrl.isNullOrBlank(),
                            initialPainter = posterSourcePlaceholder(poster),
                            onPainterReady = { poster.painter = it },
                        )
                        Box(
                            Modifier.fillMaxSize().posterForeground(poster, zIndex = 1f).background(
                                Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))),
                            ),
                        )
                        FilledIconButton(
                            onClick = { onPlay(item) },
                            modifier = Modifier.align(Alignment.Center).size(48.dp).posterForeground(poster),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color.Black.copy(alpha = 0.75f),
                                contentColor = Color.White,
                            ),
                        ) {
                            Icon(
                                Icons.Filled.PlayArrow,
                                (if (item.progress > 0) "Riprendi " else "Guarda ") + item.anime.title,
                                Modifier.size(32.dp),
                            )
                        }
                        LinearProgressIndicator(
                            progress = { item.progress.coerceIn(0f, 1f) },
                            modifier = Modifier.align(
                                Alignment.BottomCenter,
                            ).fillMaxWidth().height(3.dp).posterForeground(poster),
                            trackColor = Color.White.copy(alpha = 0.3f),
                            gapSize = 0.dp,
                            drawStopIndicator = {},
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().posterForeground(poster).padding(top = 6.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(
                            Modifier.weight(1f).clickable(role = Role.Button, onClickLabel = "Apri scheda", onClick = {
                                openDetails()
                            }),
                        ) {
                            Text(
                                item.anime.title,
                                maxLines = 2,
                                minLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                item.episode.name,
                                maxLines = 2,
                                minLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = openDetails, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Outlined.Info, "Scheda di ${item.anime.title}")
                        }
                        if (onHide != null) {
                            IconButton(onClick = { onHide(item) }, modifier = Modifier.size(48.dp)) {
                                Icon(
                                    Icons.Outlined.VisibilityOff,
                                    "Nascondi ${item.anime.title} da Continua a guardare",
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

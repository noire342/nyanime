package eu.kanade.presentation.discovery

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.R
import androidx.compose.ui.res.stringResource as androidStringResource

internal object PanoramaResumeLayout {
    // Leave a visible part of the next card, including on narrow screens. A single item uses
    // the available width; tablet cards stay comfortably reachable instead of growing forever.
    fun width(viewport: Dp, count: Int): Dp = (viewport - if (count > 1) 64.dp else 40.dp)
        .coerceIn(1.dp, 340.dp)
}

/** One large, direct resume target. The menu never intercepts the card's primary action. */
@Composable
internal fun PanoramaWatchResumeCard(
    title: String,
    episode: String,
    timing: String?,
    progress: Float?,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
    foregroundModifier: Modifier = Modifier,
    menu: @Composable (() -> Unit) -> Unit,
    artwork: @Composable (Modifier) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        onClick = onResume,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f)),
    ) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                artwork(Modifier.fillMaxSize())
                Box(
                    Modifier.matchParentSize().then(foregroundModifier).background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .8f))),
                    ),
                )
                Row(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(14.dp).then(foregroundModifier),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(44.dp).background(Color.White, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.PlayArrow, null, Modifier.size(28.dp), tint = Color.Black)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            androidStringResource(R.string.home_resume_action),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        timing?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = .9f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            // The seam stays in place even when duration is unknown; no fabricated progress.
            LinearProgressIndicator(
                progress = { progress?.coerceIn(0f, 1f) ?: 0f },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = panoramaAccent(manga = false),
                trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f),
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
            Column(
                Modifier.fillMaxWidth().heightIn(
                    min = watchResumeDetailsHeight(),
                ).padding(14.dp).then(foregroundModifier),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        minLines = 2,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Box {
                        IconButton(onClick = { expanded = true }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Outlined.MoreHoriz, androidStringResource(R.string.home_panorama_options, title))
                        }
                        DropdownMenu(expanded, onDismissRequest = { expanded = false }) { menu { expanded = false } }
                    }
                }
                Text(
                    episode,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun watchResumeDetailsHeight(): Dp = with(LocalDensity.current) {
    (MaterialTheme.typography.titleMedium.lineHeight.toDp() * 2).coerceAtLeast(48.dp) +
        MaterialTheme.typography.bodySmall.lineHeight.toDp() *
        2 +
        34.dp
}

@Composable
internal fun PanoramaWatchResumeSkeleton() {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width = PanoramaResumeLayout.width(maxWidth, 2)
        HomeSkeleton {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                userScrollEnabled = false,
            ) {
                items(2) {
                    Column(
                        Modifier.width(width).clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow),
                    ) {
                        SkeletonBlock(Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                        SkeletonBlock(Modifier.fillMaxWidth().height(3.dp))
                        Column(
                            Modifier.height(watchResumeDetailsHeight()).padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            SkeletonBlock(Modifier.fillMaxWidth(.85f).height(18.dp))
                            SkeletonBlock(Modifier.fillMaxWidth(.55f).height(18.dp))
                            SkeletonBlock(Modifier.fillMaxWidth(.65f).height(14.dp))
                        }
                    }
                }
            }
        }
    }
}

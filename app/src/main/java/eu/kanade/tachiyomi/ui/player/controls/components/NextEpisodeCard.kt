package eu.kanade.tachiyomi.ui.player.controls.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.discovery.ArtworkPlaceholder
import eu.kanade.tachiyomi.ui.player.PlayerPlaybackCompletion
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

/** Uses playlist metadata only; resolving the next stream waits for the user's decision or deadline. */
@Composable
fun NextEpisodeCard(
    seriesTitle: String,
    episodeTitle: String,
    secondsRemaining: Int,
    onPlayNow: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
    artwork: @Composable () -> Unit = { ArtworkPlaceholder(Modifier.size(64.dp, 88.dp)) },
) {
    val colors = MaterialTheme.colorScheme
    val progress by animateFloatAsState(
        targetValue = secondsRemaining.toFloat() / PlayerPlaybackCompletion.COUNTDOWN_SECONDS,
        animationSpec = tween(if (reduceMotion) 0 else 250),
        label = "next_episode_progress",
    )
    Surface(
        modifier = modifier.widthIn(max = 480.dp).fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = colors.surfaceContainer,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.55f)),
        shadowElevation = 8.dp,
    ) {
        Column(
            Modifier
                .background(Brush.linearGradient(listOf(colors.primary.copy(alpha = 0.09f), colors.surfaceContainer)))
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(AYMR.strings.player_up_next),
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    stringResource(AYMR.strings.player_up_next_countdown, secondsRemaining),
                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                    color = colors.primary,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(Modifier.size(64.dp, 88.dp).clip(RoundedCornerShape(10.dp))) { artwork() }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        seriesTitle,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        episodeTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                trackColor = colors.surfaceContainerHighest,
                drawStopIndicator = {},
            )
            val playButton: @Composable (Modifier) -> Unit = { buttonModifier ->
                Button(onClick = onPlayNow, modifier = buttonModifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.padding(end = 6.dp).size(20.dp))
                    Text(stringResource(AYMR.strings.player_up_next_play_now))
                }
            }
            val cancelButton: @Composable (Modifier) -> Unit = { buttonModifier ->
                TextButton(onClick = onCancel, modifier = buttonModifier.heightIn(min = 48.dp)) {
                    Text(stringResource(MR.strings.action_cancel))
                }
            }
            if (LocalDensity.current.fontScale > 1.3f) {
                playButton(Modifier.fillMaxWidth())
                cancelButton(Modifier.fillMaxWidth())
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    cancelButton(Modifier.weight(1f))
                    playButton(Modifier.weight(1.4f))
                }
            }
        }
    }
}

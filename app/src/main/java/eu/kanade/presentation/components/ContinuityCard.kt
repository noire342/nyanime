package eu.kanade.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.motion.ModernMotion
import eu.kanade.presentation.motion.appMotionEnabled

/** The body navigates; the separate corner control only changes the amount of detail. */
@Composable
internal fun ContinuityCard(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    preview: @Composable ColumnScope.() -> Unit = {},
    details: @Composable ColumnScope.() -> Unit,
) {
    val motion = appMotionEnabled()
    val duration = if (motion) ModernMotion.RESIZE_MILLIS else 0
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, tween(duration), label = "Dettagli collegamento")
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Box(
            Modifier.background(
                Brush.linearGradient(
                    listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), Color.Transparent),
                ),
            ).clickable(onClick = onOpen),
        ) {
            Column(Modifier.fillMaxWidth().heightIn(min = 76.dp)) {
                Column(Modifier.padding(start = 16.dp, top = 14.dp, end = 64.dp, bottom = 14.dp)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                    preview()
                }
                AnimatedVisibility(
                    visible = expanded,
                    enter = ModernMotion.enter(
                        duration,
                    ) +
                        expandVertically(tween(duration), expandFrom = Alignment.Top),
                    exit = fadeOut(tween(duration)) + shrinkVertically(tween(duration), shrinkTowards = Alignment.Top),
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 64.dp),
                        content = details,
                    )
                }
            }
            IconButton(
                onClick = { onExpandedChange(!expanded) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).size(48.dp)
                    .clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                Icon(
                    Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Riduci collegamento" else "Espandi collegamento",
                    modifier = Modifier.rotate(rotation),
                )
            }
        }
    }
}

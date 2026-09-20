package eu.kanade.presentation.discovery

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.motion.PosterSource
import eu.kanade.presentation.motion.modernMotionEnabled
import eu.kanade.presentation.motion.posterForeground

/** Artwork and actions stay owned by the existing catalogue/source integration. */
@Composable
internal fun CinematicHero(
    title: String,
    eyebrow: String,
    metadata: String?,
    description: String?,
    actionLabel: String,
    onOpen: () -> Unit,
    poster: PosterSource? = null,
    artwork: @Composable BoxScope.() -> Unit,
) {
    var showInformation by rememberSaveable(title) { mutableStateOf(false) }
    if (showInformation) {
        TitleInformationSheet(title, metadata, description, { showInformation = false }, onOpen)
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compact = maxWidth < 600.dp
        val artworkHeight = if (compact) (maxWidth * 1.25f).coerceIn(400.dp, 540.dp) else 380.dp
        Box(Modifier.fillMaxWidth().heightIn(min = artworkHeight)) {
            artwork()
            Box(
                Modifier.matchParentSize().posterForeground(poster, zIndex = 1f).background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.12f),
                        0.38f to Color.Black.copy(alpha = 0.05f),
                        0.7f to Color.Black.copy(alpha = 0.82f),
                        1f to MaterialTheme.colorScheme.background,
                    ),
                ),
            )
            Column(
                Modifier.fillMaxWidth().posterForeground(poster).padding(horizontal = 24.dp)
                    .padding(top = artworkHeight * 0.52f, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = if (compact) Alignment.CenterHorizontally else Alignment.Start,
            ) {
                Text(
                    eyebrow.uppercase(),
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(
                    Modifier.widthIn(max = 680.dp).fillMaxWidth()
                        .height(
                            with(LocalDensity.current) {
                                MaterialTheme.typography.headlineMedium.lineHeight.toDp() *
                                    3
                            },
                        ),
                ) {
                    Text(
                        title,
                        modifier = Modifier.align(if (compact) Alignment.BottomCenter else Alignment.BottomStart),
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = if (compact) TextAlign.Center else TextAlign.Start,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    metadata.orEmpty(),
                    color = Color.White.copy(alpha = 0.86f),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = if (compact) TextAlign.Center else TextAlign.Start,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!compact) {
                    Text(
                        description.orEmpty(),
                        modifier = Modifier.widthIn(max = 640.dp),
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        minLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(
                        16.dp,
                        if (compact) Alignment.CenterHorizontally else Alignment.Start,
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Button(
                        onClick = onOpen,
                        modifier = Modifier.widthIn(min = 168.dp).heightIn(min = 48.dp),
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    ) {
                        Text(actionLabel, style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.size(10.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(20.dp))
                    }
                    TextButton(
                        onClick = { showInformation = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                    ) {
                        Icon(Icons.Outlined.Info, null, Modifier.size(20.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("Info")
                    }
                }
            }
        }
    }
}

@Composable
internal fun CarouselPosition(page: Int, count: Int, onBrowse: (() -> Unit)? = null) {
    if (count <= 1 && onBrowse == null) return
    val duration = if (modernMotionEnabled()) 220 else 0
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (count > 1) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val start = (page - 3).coerceIn(0, (count - 7).coerceAtLeast(0))
                repeat(count.coerceAtMost(7)) { offset ->
                    val selected = start + offset == page
                    val targetWidth = if (selected) 20.dp else 5.dp
                    val width by animateDpAsState(targetWidth, tween(duration), label = "heroIndicatorWidth")
                    val color by animateColorAsState(
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        tween(duration),
                        label = "heroIndicatorColor",
                    )
                    Box(
                        Modifier.padding(horizontal = 3.dp).size(width, 3.dp)
                            .background(
                                color,
                                RoundedCornerShape(2.dp),
                            ),
                    )
                }
            }
        }
        if (onBrowse != null) {
            TextButton(onClick = onBrowse, modifier = Modifier.fillMaxWidth()) {
                Text("Tutti i titoli in evidenza", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.size(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(16.dp))
            }
        }
    }
}

package eu.kanade.tachiyomi.ui.tv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Semantic TV palette mirrored from the finalized webOS design tokens. */
object TvColors {
    val background = Color(0xFF080809)
    val surface = Color(0xFF242426)
    val text = Color(0xFFF5F5F5)
    val muted = Color(0xFFB9B9B9)
    val accent = Color(0xFFE50914)
    val red = Color(0xFFEC5363)
    val green = Color(0xFF45C179)
    val yellow = Color(0xFFEFC74A)
    val blue = Color(0xFF5797EF)
    val violet = Color(0xFFB983FF)
}

private val tvEase = CubicBezierEasing(.2f, .8f, .2f, 1f)

@Composable
fun TvAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cue: Color? = null,
    selected: Boolean = false,
    reduceMotion: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val active = focused || selected
    val scale by animateFloatAsState(
        if (focused && !reduceMotion) 1.035f else 1f,
        animationSpec = if (reduceMotion) snap() else tween(180, easing = tvEase),
        label = "TV action scale",
    )
    val background by animateColorAsState(
        if (active) {
            Color(0xFF343437)
        } else if (cue != null) {
            Color(0xFF242027)
        } else {
            TvColors.surface
        },
        animationSpec = if (reduceMotion) snap() else tween(230, easing = tvEase),
        label = "TV action surface",
    )
    val shape = RoundedCornerShape(11.dp)
    val glow = cue ?: TvColors.text
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                if (focused) {
                    13.dp
                } else if (cue != null) {
                    4.dp
                } else {
                    0.dp
                },
                shape,
                ambientColor = glow,
                spotColor = glow,
            )
            .border(
                BorderStroke(
                    if (active || cue != null) 2.dp else 1.dp,
                    if (active) {
                        Brush.linearGradient(listOf(glow.copy(alpha = .9f), glow.copy(alpha = .25f)))
                    } else if (cue != null) {
                        Brush.linearGradient(
                            listOf(
                                glow.copy(alpha = .54f),
                                Color.White.copy(alpha = .26f),
                                glow.copy(alpha = .45f),
                            ),
                        )
                    } else {
                        Brush.linearGradient(listOf(Color.White.copy(alpha = .14f), Color.White.copy(alpha = .07f)))
                    },
                ),
                shape,
            )
            .background(background, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 19.dp, vertical = 13.dp),
    ) {
        Text(
            label,
            color = TvColors.text,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Home categories use the same focus and action language as the rest of TV mode. */
@Composable
fun TvNavAction(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    reduceMotion: Boolean,
) {
    TvAction(
        label,
        onClick,
        selected = selected,
        cue = if (selected) TvColors.blue else null,
        reduceMotion = reduceMotion,
    )
}

@Composable
fun TvFocusFrame(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cue: Color = TvColors.text,
    selected: Boolean = false,
    reduceMotion: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (focused && !reduceMotion) 1.045f else 1f,
        animationSpec = if (reduceMotion) snap() else tween(180, easing = tvEase),
        label = "TV focus frame scale",
    )
    val shape = RoundedCornerShape(11.dp)
    Box(
        modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
            .shadow(
                if (focused) {
                    15.dp
                } else if (cue != TvColors.text) {
                    5.dp
                } else {
                    0.dp
                },
                shape,
                ambientColor = cue,
                spotColor = cue,
            )
            .border(
                if (focused || selected) {
                    3.dp
                } else if (cue != TvColors.text) {
                    2.dp
                } else {
                    1.dp
                },
                if (focused || selected) {
                    cue
                } else if (cue != TvColors.text) {
                    cue.copy(alpha = .65f)
                } else {
                    Color.White.copy(alpha = .12f)
                },
                shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        content = content,
    )
}

@Composable
fun TvLegend(
    items: List<Pair<Color, String>>,
    modifier: Modifier = Modifier,
    interactionToken: Int = 0,
    reduceMotion: Boolean = false,
) {
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(interactionToken, items) {
        visible = true
        delay(5_000)
        visible = false
    }
    AnimatedVisibility(
        visible = visible && items.isNotEmpty(),
        modifier = modifier,
        enter = if (reduceMotion) {
            fadeIn(animationSpec = snap())
        } else {
            fadeIn(tween(190)) + expandVertically(tween(220))
        },
        exit = if (reduceMotion) {
            fadeOut(animationSpec = snap())
        } else {
            fadeOut(tween(260)) + shrinkVertically(tween(270))
        },
    ) {
        Row(
            Modifier
                .background(Color(0xE51A1A1E), RoundedCornerShape(18.dp))
                .border(1.dp, Color.White.copy(alpha = .12f), RoundedCornerShape(18.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { (color, label) ->
                Row(
                    Modifier
                        .background(color.copy(alpha = .08f), RoundedCornerShape(12.dp))
                        .border(1.dp, color.copy(alpha = .24f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(10.dp)
                            .shadow(8.dp, RoundedCornerShape(50), ambientColor = color, spotColor = color)
                            .background(color, RoundedCornerShape(50)),
                    )
                    Text(
                        label,
                        color = TvColors.text.copy(alpha = .86f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

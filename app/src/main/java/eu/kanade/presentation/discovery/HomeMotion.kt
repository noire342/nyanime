package eu.kanade.presentation.discovery

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.theme.LocalNyanimeStyle

/** Only animates on navigation; no permanent animation loop or off-screen work. */
@Composable
fun HomeContentReveal(key: String, content: @Composable BoxScope.() -> Unit) {
    if (!LocalNyanimeStyle.current) {
        Box(content = content)
        return
    }
    val preview = LocalInspectionMode.current
    val progress = remember(key) { Animatable(if (preview) 1f else 0f) }
    LaunchedEffect(key) { progress.animateTo(1f, tween(260, easing = FastOutSlowInEasing)) }
    Box(
        Modifier.graphicsLayer {
            alpha = progress.value
            translationY = (1f - progress.value) * 12.dp.toPx()
        },
        content = content,
    )
}

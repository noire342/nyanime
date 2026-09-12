package eu.kanade.presentation.discovery

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import eu.kanade.presentation.motion.modernMotionEnabled
import eu.kanade.presentation.motion.posterNavigationRunning
import eu.kanade.presentation.theme.LocalNyanimeStyle

/** Reveal new sections once; returning navigation must not move a shared poster's destination. */
@Composable
fun HomeContentReveal(key: String, content: @Composable BoxScope.() -> Unit) {
    if (!LocalNyanimeStyle.current) {
        Box(content = content)
        return
    }
    val skip = LocalInspectionMode.current || !modernMotionEnabled() || posterNavigationRunning()
    var revealed by rememberSaveable(key) { mutableStateOf(false) }
    val progress = remember(key) { Animatable(if (skip || revealed) 1f else 0f) }
    LaunchedEffect(key, skip) {
        if (skip || revealed) {
            revealed = true
            progress.snapTo(1f)
        } else {
            revealed = true
            progress.animateTo(1f, tween(220, easing = FastOutSlowInEasing))
        }
    }
    Box(
        Modifier.graphicsLayer {
            alpha = progress.value
        },
        content = content,
    )
}

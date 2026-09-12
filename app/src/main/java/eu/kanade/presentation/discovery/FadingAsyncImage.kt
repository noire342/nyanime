package eu.kanade.presentation.discovery

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter

/** Fades decoded images from every cache tier, keeping the visible image while loading or failing. */
@Composable
internal fun FadingAsyncImage(
    model: Any,
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    previousPainter: Painter? = null,
    reduceMotion: Boolean = false,
    onSuccess: (AsyncImagePainter.State.Success) -> Unit = {},
    onError: (AsyncImagePainter.State.Error) -> Unit = {},
) {
    val immediate = reduceMotion || LocalInspectionMode.current
    val previous = remember(model, imageLoader) { previousPainter }
    val opacity = remember(model, imageLoader) { Animatable(if (immediate) 1f else 0f) }
    var ready by remember(model, imageLoader) { mutableStateOf<AsyncImagePainter.State.Success?>(null) }
    val success by rememberUpdatedState(onSuccess)
    LaunchedEffect(ready, immediate) {
        val result = ready ?: return@LaunchedEffect
        if (immediate) opacity.snapTo(1f) else opacity.animateTo(1f, tween(220))
        // A new navigation must retain the image the user actually sees, after the blend completes.
        success(result)
    }
    Box(modifier) {
        if (previous != null) {
            Image(previous, contentDescription, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        AsyncImage(
            model,
            contentDescription,
            imageLoader,
            Modifier.fillMaxSize().graphicsLayer { alpha = opacity.value },
            contentScale = ContentScale.Crop,
            onSuccess = { ready = it },
            onError = onError,
        )
    }
}

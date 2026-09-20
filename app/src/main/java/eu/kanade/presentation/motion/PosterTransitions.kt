package eu.kanade.presentation.motion

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import cafe.adriel.voyager.navigator.LocalNavigator
import eu.kanade.presentation.discovery.sourceHomeArtworkIdentity
import java.util.UUID

internal const val POSTER_TRANSITION_MILLIS = 360

@OptIn(ExperimentalSharedTransitionApi::class)
internal data class PosterScene(
    val state: PosterNavigationState<Painter>,
    val shared: SharedTransitionScope,
    val visibility: AnimatedVisibilityScope,
    val route: String,
    val active: PosterRoute<Painter>?,
    val running: Boolean,
    val enabled: Boolean,
)

@Suppress("CompositionLocalAllowlist")
internal val LocalPosterScene = staticCompositionLocalOf<PosterScene?> { null }

/** Uses the navigator's real transition so interruption, pop and screen disposal have one owner. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun <T> PosterNavigationTransition(
    navigation: Transition<T>,
    routeKey: (T) -> String,
    retainedRoutes: Set<String>,
    enabled: Boolean,
    transitionSpec: AnimatedContentTransitionScope<T>.() -> ContentTransform,
    modifier: Modifier = Modifier,
    state: PosterNavigationState<Painter> = remember { PosterNavigationState() },
    content: @Composable AnimatedVisibilityScope.(T) -> Unit,
) {
    val currentKey = routeKey(navigation.currentState)
    val targetKey = routeKey(navigation.targetState)
    val active = state.between(currentKey, targetKey).takeIf { enabled }
    val moving = currentKey != targetKey || navigation.isRunning
    LaunchedEffect(retainedRoutes, moving, enabled) {
        if (!enabled) {
            state.clear()
        } else if (!moving) {
            state.retain(retainedRoutes)
        }
    }
    SharedTransitionLayout(modifier) {
        val shared = this
        navigation.AnimatedContent(
            transitionSpec = {
                if (enabled && state.between(routeKey(initialState), routeKey(targetState)) != null) {
                    ModernMotion.transform(true, POSTER_TRANSITION_MILLIS).using(null)
                } else {
                    transitionSpec()
                }
            },
            contentKey = routeKey,
        ) { screen ->
            val key = routeKey(screen)
            CompositionLocalProvider(
                LocalPosterScene provides PosterScene(
                    state,
                    shared,
                    this,
                    key,
                    active,
                    running = moving,
                    enabled = enabled,
                ),
            ) {
                content(screen)
            }
        }
    }
}

internal class PosterSource(val element: String) {
    var painter by mutableStateOf<Painter?>(null)
}

/** The saved token identifies a particular card, including its position in a particular row. */
@Composable
internal fun rememberPosterSource(data: Any?): PosterSource {
    val identity = data?.let(::sourceHomeArtworkIdentity)
    val token = rememberSaveable(identity) { UUID.randomUUID().toString() }
    return remember(token) { PosterSource(token) }
}

@Composable
internal fun posterOpen(source: PosterSource, title: String, onClick: () -> Unit): () -> Unit {
    val scene = LocalPosterScene.current
    val navigator = LocalNavigator.current
    val retained = posterSourcePlaceholder(source)
    SideEffect {
        if (source.painter == null && retained != null) source.painter = retained
    }
    return {
        // A second tap on the outgoing page must not push the same destination twice.
        if (scene?.enabled != true || navigator == null || navigator.lastItem.key == scene.route) {
            val before = navigator?.lastItem
            onClick()
            val after = navigator?.lastItem
            val image = source.painter ?: retained
            if (scene?.enabled == true &&
                image != null &&
                after is PosterDetailsScreen &&
                before?.key == scene.route &&
                after.key != before.key
            ) {
                scene.state.connect(before.key, after.key, source.element, title, image)
            }
        }
    }
}

@Composable
internal fun posterSourcePlaceholder(source: PosterSource?): Painter? {
    if (source == null) return null
    source.painter?.let { return it }
    val scene = LocalPosterScene.current ?: return null
    return scene.active?.takeIf { it.origin == scene.route && it.element == source.element }?.artwork
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun Modifier.posterSource(source: PosterSource?): Modifier {
    if (source == null) return this
    val scene = LocalPosterScene.current ?: return this
    if (!scene.enabled) return this
    return with(scene.shared) {
        this@posterSource.sharedBounds(
            sharedContentState = rememberSharedContentState(source.element),
            animatedVisibilityScope = scene.visibility,
            enter = ModernMotion.enter(POSTER_TRANSITION_MILLIS),
            exit = fadeOut(tween(POSTER_TRANSITION_MILLIS, easing = LinearOutSlowInEasing)),
            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.Crop),
            boundsTransform = { _, _ -> tween(POSTER_TRANSITION_MILLIS, easing = FastOutSlowInEasing) },
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun Modifier.posterDestination(): Modifier {
    val scene = LocalPosterScene.current ?: return this
    val origin = scene.state.destination(scene.route) ?: return this
    if (!scene.enabled) return this
    return with(scene.shared) {
        this@posterDestination.sharedBounds(
            sharedContentState = rememberSharedContentState(origin.element),
            animatedVisibilityScope = scene.visibility,
            enter = ModernMotion.enter(POSTER_TRANSITION_MILLIS),
            exit = fadeOut(tween(POSTER_TRANSITION_MILLIS, easing = LinearOutSlowInEasing)),
            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.Crop),
            boundsTransform = { _, _ -> tween(POSTER_TRANSITION_MILLIS, easing = FastOutSlowInEasing) },
        )
    }
}

@Composable
internal fun posterDetailPreview(): PosterRoute<Painter>? {
    val scene = LocalPosterScene.current ?: return null
    return scene.state.destination(scene.route).takeIf { scene.enabled }
}

@Composable
internal fun posterNavigationRunning(): Boolean = LocalPosterScene.current?.running == true

@Composable
internal fun posterRequestsEnabled(): Boolean {
    val scene = LocalPosterScene.current ?: return true
    return !scene.enabled || !scene.running || scene.state.destination(scene.route) == null
}

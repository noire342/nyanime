package eu.kanade.presentation.motion

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import eu.kanade.presentation.theme.LocalNyanimeStyle

/**
 * Shared artwork escapes the page's alpha and is drawn over ordinary siblings. Decorations must
 * occupy the same overlay, with their own enter/exit animation, before returning to normal drawing.
 * Only the selected card is elevated; unrelated rows must never cover the travelling poster.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun Modifier.posterForeground(source: PosterSource? = null, zIndex: Float = 2f): Modifier {
    if (!LocalNyanimeStyle.current) return this
    val scene = LocalPosterScene.current ?: return this
    val active = scene.active ?: return this
    if (!scene.enabled || source != null && (active.origin != scene.route || active.element != source.element)) {
        return this
    }
    return with(scene.shared) {
        with(scene.visibility) {
            this@posterForeground
                .renderInSharedTransitionScopeOverlay(zIndexInOverlay = zIndex)
                .animateEnterExit(
                    enter = ModernMotion.enter(POSTER_TRANSITION_MILLIS),
                    exit = ModernMotion.exit(),
                    label = "poster_foreground",
                )
        }
    }
}

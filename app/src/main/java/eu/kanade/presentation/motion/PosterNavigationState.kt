package eu.kanade.presentation.motion

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape

/** One origin per destination, never one key per title: the same title can appear in several rows. */
internal data class PosterRoute<Artwork>(
    val origin: String,
    val destination: String,
    val element: String,
    val title: String,
    val artwork: Artwork,
    val shape: Shape = RectangleShape,
)

internal class PosterNavigationState<Artwork> {
    private val routes = mutableStateMapOf<String, PosterRoute<Artwork>>()

    fun connect(
        origin: String,
        destination: String,
        element: String,
        title: String,
        artwork: Artwork,
        shape: Shape = RectangleShape,
    ) {
        if (origin == destination) return
        routes[destination] = PosterRoute(origin, destination, element, title, artwork, shape)
    }

    fun destination(key: String): PosterRoute<Artwork>? = routes[key]

    fun between(first: String, second: String): PosterRoute<Artwork>? {
        if (first == second) return null
        return routes[second]?.takeIf { it.origin == first }
            ?: routes[first]?.takeIf { it.origin == second }
    }

    /** Keep a popped route until its exit has finished, then release its retained image. */
    fun retain(keys: Set<String>) {
        routes.entries.removeAll { it.value.origin !in keys || it.key !in keys }
    }

    fun clear() = routes.clear()
}

/** A navigation destination that can display the clicked poster while its real data loads. */
interface PosterDetailsScreen

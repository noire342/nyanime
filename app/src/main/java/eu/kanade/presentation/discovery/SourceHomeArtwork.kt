package eu.kanade.presentation.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import eu.kanade.tachiyomi.data.coil.ArtworkRequestPolicy
import eu.kanade.tachiyomi.data.coil.artworkTimeout
import eu.kanade.tachiyomi.data.coil.useBackground
import kotlinx.coroutines.delay

/** Retries only a failed image. Refresh and retry reuse healthy caches and never alter library data. */
@Composable
fun SourceHomeArtwork(
    data: Any,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    background: Boolean = false,
    refreshKey: Int = 0,
    imageLoader: ImageLoader = SingletonImageLoader.get(LocalContext.current),
) {
    key(data, background, refreshKey) {
        var attempt by remember { mutableIntStateOf(0) }
        var failure by remember { mutableStateOf<Throwable?>(null) }
        val context = LocalContext.current
        val request = remember(data, background, attempt, context) {
            ImageRequest.Builder(context).data(data).useBackground(background)
                .artworkTimeout(ArtworkRequestPolicy.HOME_TIMEOUT_MILLIS)
                // A failed decode must not keep rereading an unusable disk entry on retry.
                .diskCachePolicy(if (attempt == 0) CachePolicy.ENABLED else CachePolicy.WRITE_ONLY)
                .build()
        }
        LaunchedEffect(failure) {
            val error = failure ?: return@LaunchedEffect
            if (ArtworkRequestPolicy.shouldRetry(error, attempt)) {
                delay(ArtworkRequestPolicy.RETRY_DELAY_MILLIS)
                failure = null
                attempt++
            }
        }
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            key(attempt) {
                AsyncImage(
                    model = request,
                    imageLoader = imageLoader,
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    onSuccess = { failure = null },
                    onError = { failure = it.result.throwable },
                )
            }
            if (failure != null) {
                IconButton(onClick = {
                    failure = null
                    attempt++
                }) {
                    Icon(Icons.Outlined.Refresh, "Ricarica immagine")
                }
            }
        }
    }
}

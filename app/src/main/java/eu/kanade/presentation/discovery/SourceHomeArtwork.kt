package eu.kanade.presentation.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import eu.kanade.tachiyomi.data.coil.ArtworkHttpException
import eu.kanade.tachiyomi.data.coil.ArtworkRequestPolicy
import eu.kanade.tachiyomi.data.coil.artworkTimeout
import eu.kanade.tachiyomi.data.coil.useBackground
import eu.kanade.tachiyomi.data.discovery.SourceHomeArtworkResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import tachiyomi.domain.entries.anime.model.Anime
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

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
        var artwork by remember { mutableStateOf(data) }
        var useBackground by remember { mutableStateOf(background) }
        var detailsAttempted by remember { mutableStateOf(false) }
        var recovering by remember { mutableStateOf(false) }
        var loading by remember { mutableStateOf(true) }
        var showProgress by remember { mutableStateOf(false) }
        var manualRetry by remember { mutableStateOf(false) }
        val context = LocalContext.current
        val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
        val active = lifecycle.isAtLeast(Lifecycle.State.STARTED)
        val request = remember(artwork, useBackground, attempt, context) {
            ImageRequest.Builder(context).data(artwork).useBackground(useBackground)
                .artworkTimeout(ArtworkRequestPolicy.HOME_TIMEOUT_MILLIS)
                // A failed decode must not keep rereading an unusable disk entry on retry.
                .diskCachePolicy(if (attempt == 0) CachePolicy.ENABLED else CachePolicy.WRITE_ONLY)
                .build()
        }
        LaunchedEffect(loading, recovering, active) {
            showProgress = false
            if (active && (loading || recovering)) {
                delay(350)
                showProgress = true
            }
        }
        LaunchedEffect(failure, active) {
            if (!active) return@LaunchedEffect
            val error = failure ?: return@LaunchedEffect
            if (data is Anime && useBackground && !data.thumbnailUrl.isNullOrBlank()) {
                useBackground = false
                failure = null
                loading = true
                return@LaunchedEffect
            }
            if (data is Anime &&
                !detailsAttempted &&
                (error is IllegalStateException || error is ArtworkHttpException && error.code in listOf(404, 410))
            ) {
                recovering = true
                try {
                    val resolved = Injekt.get<SourceHomeArtworkResolver>().resolve(data, refresh = manualRetry)
                    detailsAttempted = true
                    val cover = resolved.cover ?: data.thumbnailUrl
                    val backdrop = resolved.background ?: data.backgroundUrl
                    check(!cover.isNullOrBlank() || useBackground && !backdrop.isNullOrBlank()) { "No cover available" }
                    artwork = data.copy(thumbnailUrl = cover, backgroundUrl = backdrop)
                    failure = null
                    loading = true
                    attempt++
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (cause: Exception) {
                    detailsAttempted = true
                    failure = cause
                } finally {
                    recovering = false
                }
                return@LaunchedEffect
            }
            if (ArtworkRequestPolicy.shouldRetry(error, attempt)) {
                delay(ArtworkRequestPolicy.RETRY_DELAY_MILLIS)
                failure = null
                loading = true
                attempt++
            }
        }
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            if (active) {
                key(attempt) {
                    AsyncImage(
                        model = request,
                        imageLoader = imageLoader,
                        contentDescription = contentDescription,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        onLoading = { loading = true },
                        onSuccess = {
                            failure = null
                            loading = false
                        },
                        onError = {
                            failure = it.result.throwable
                            loading = false
                        },
                    )
                }
            }
            if (showProgress) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            } else if (failure != null && !recovering) {
                IconButton(onClick = {
                    failure = null
                    detailsAttempted = false
                    manualRetry = true
                    loading = true
                    attempt++
                }) {
                    Icon(Icons.Outlined.Refresh, "Ricarica immagine")
                }
            }
        }
    }
}

package eu.kanade.presentation.motion

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.discovery.ArtworkPlaceholder
import eu.kanade.presentation.discovery.SourceHomeArtwork
import tachiyomi.presentation.core.components.TwoPanelBox
import tachiyomi.presentation.core.screens.LoadingScreen

internal fun posterDetailHeight(width: Dp, tablet: Boolean, catalog: Boolean = false): Dp = when {
    catalog -> (width * 0.75f).coerceIn(240.dp, 380.dp)
    tablet -> 280.dp
    else -> (width * 0.66f).coerceIn(220.dp, 360.dp)
}

/** Keep the exact decoded source image throughout the flight; load the detail backdrop afterwards. */
@Composable
internal fun PosterDetailArtwork(
    data: Any?,
    modifier: Modifier = Modifier,
    background: Boolean = false,
    sourceArtwork: Boolean = false,
) {
    val preview = posterDetailPreview()?.artwork
    val canRequest = posterRequestsEnabled()
    Box(modifier.posterDestination()) {
        when {
            (data == null || !canRequest) && preview != null -> Image(
                preview,
                null,
                Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            data == null -> ArtworkPlaceholder(Modifier.fillMaxSize())
            sourceArtwork -> SourceHomeArtwork(
                data,
                Modifier.fillMaxSize(),
                background = background,
                initialPainter = preview,
            )
            else -> {
                val context = LocalContext.current
                val request = remember(data, context) {
                    ImageRequest.Builder(context).data(data).crossfade(180).build()
                }
                AsyncImage(
                    request,
                    null,
                    Modifier.fillMaxSize(),
                    placeholder = preview,
                    error = preview,
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}

@Composable
internal fun PosterLoadingBody(
    catalog: Boolean = false,
    tablet: Boolean = false,
    appBarPadding: Dp = 0.dp,
) {
    val title = posterDetailPreview()?.title.orEmpty()
    Column {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(posterDetailHeight(maxWidth, tablet, catalog) + appBarPadding)) {
                PosterDetailArtwork(null, Modifier.matchParentSize())
                Box(
                    Modifier.matchParentSize().background(
                        Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background)),
                    ),
                )
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            listOf(0.7f, 1f, 0.9f).forEach { width ->
                Spacer(
                    Modifier.fillMaxWidth(width).height(18.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(6.dp)),
                )
            }
        }
    }
}

@Composable
internal fun PosterAnimeLoadingScreen(tablet: Boolean, navigateUp: () -> Unit) {
    if (posterDetailPreview() == null) {
        LoadingScreen()
        return
    }
    Scaffold(
        topBar = {
            AppBar(titleContent = {}, navigateUp = navigateUp, backgroundColor = Color.Transparent)
        },
    ) { padding ->
        if (tablet) {
            TwoPanelBox(
                startContent = { PosterLoadingBody(tablet = true, appBarPadding = padding.calculateTopPadding()) },
                endContent = {},
            )
        } else {
            PosterLoadingBody(appBarPadding = padding.calculateTopPadding())
        }
    }
}

package eu.kanade.presentation.discovery

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.decode.BitmapFactoryDecoder
import coil3.request.ImageRequest
import coil3.request.crossfade
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.motion.modernMotionEnabled
import eu.kanade.presentation.theme.NyanimeWordmark
import eu.kanade.tachiyomi.data.coil.artworkTimeout
import tachiyomi.domain.entries.anime.model.AnimeCover
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.presentation.core.util.collectAsState as collectPreferenceAsState

data class SourceHomeLogo(
    val sourceId: Long,
    val sourceName: String,
    val url: String,
    val name: String? = null,
    val background: String? = null,
)

@Composable
fun sourceHomeLogoEnabled(): Boolean {
    return if (LocalInspectionMode.current) {
        false
    } else {
        val preference = remember { Injekt.get<UiPreferences>().sourceHomeLogo() }
        val value by preference.collectPreferenceAsState()
        value
    }
}

@Composable
fun SourceHomeWordmark(
    logo: SourceHomeLogo?,
    modifier: Modifier = Modifier,
    enabled: Boolean = sourceHomeLogoEnabled(),
) {
    Box(modifier.height(48.dp), contentAlignment = Alignment.CenterStart) {
        if (enabled && logo != null) {
            val context = LocalContext.current
            val motion = modernMotionEnabled()
            val request = remember(logo, motion, context) {
                ImageRequest.Builder(context)
                    .data(AnimeCover(-1, logo.sourceId, false, logo.url, 0))
                    .memoryCacheKey("home-logo:${logo.sourceId}:${logo.url}")
                    .size(720, 144)
                    .crossfade(if (motion) 220 else 0)
                    .artworkTimeout(8_000)
                    .apply {
                        if (!motion && logo.url.substringBefore('?').endsWith(".gif", ignoreCase = true)) {
                            decoderFactory(BitmapFactoryDecoder.Factory())
                        }
                    }
                    .build()
            }
            val painter = rememberAsyncImagePainter(request)
            val state by painter.state.collectAsState()
            var previous by remember(logo.sourceId) { mutableStateOf<Painter?>(null) }
            LaunchedEffect(state) {
                (state as? AsyncImagePainter.State.Success)?.let { previous = it.painter }
            }
            val ready = state is AsyncImagePainter.State.Success
            if (ready || previous != null) {
                val displayed = if (ready) painter else requireNotNull(previous)
                val dimensions = displayed.intrinsicSize
                val compact = dimensions.width < dimensions.height * 1.6f && logo.name != null
                val background = when (logo.background) {
                    "light" -> Color.White
                    "dark" -> Color(0xFF101010)
                    else -> Color.Transparent
                }
                Row(
                    Modifier.widthIn(max = 240.dp).height(40.dp).clip(RoundedCornerShape(8.dp))
                        .background(background).padding(horizontal = if (logo.background == null) 0.dp else 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Image(
                        displayed,
                        logo.sourceName,
                        if (compact) Modifier.size(32.dp) else Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.CenterStart,
                    )
                    if (compact) {
                        Text(
                            requireNotNull(logo.name),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = when (logo.background) {
                                "light" -> Color.Black
                                "dark" -> Color.White
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }
            } else {
                NyanimeWordmark()
            }
        } else {
            NyanimeWordmark()
        }
    }
}

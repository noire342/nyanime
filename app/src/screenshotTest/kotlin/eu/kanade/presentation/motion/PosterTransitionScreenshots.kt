package eu.kanade.presentation.motion

import android.graphics.BitmapFactory
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import eu.kanade.domain.ui.model.AppTheme
import eu.kanade.presentation.theme.TachiyomiPreviewTheme

@PreviewTest
@Preview(name = "PosterOrigin", widthDp = 393, heightDp = 760, locale = "it")
@Preview(name = "PosterOriginLargeText", widthDp = 320, heightDp = 760, fontScale = 1.4f, locale = "it")
@Composable
fun PosterOriginScreenshot() = PosterMotionPreview("home")

@PreviewTest
@Preview(name = "WarmDetail", widthDp = 393, heightDp = 760, locale = "it")
@Preview(name = "WarmDetailLargeText", widthDp = 320, heightDp = 760, fontScale = 1.4f, locale = "it")
@Preview(name = "WarmDetailTablet", widthDp = 1000, heightDp = 700, locale = "it")
@Composable
fun PosterDetailScreenshot() = PosterMotionPreview("detail")

@PreviewTest
@Preview(name = "WarmCatalogDetail", widthDp = 393, heightDp = 760, locale = "it")
@Composable
fun PosterCatalogScreenshot() = PosterMotionPreview("detail", catalog = true)

/** Static transition endpoints; the host renderer does not advance the animation clock. */
@Composable
private fun PosterMotionPreview(initial: String, catalog: Boolean = false) {
    val artwork = remember {
        PosterSource::class.java.classLoader?.getResourceAsStream("nyanime-preview/poster-0.jpg")
            ?.use { BitmapFactory.decodeStream(it) }
            ?.let { BitmapPainter(it.asImageBitmap()) }
            ?: ColorPainter(Color(0xFF355B70))
    }
    val state = remember {
        PosterNavigationState<Painter>().apply {
            connect("home", "detail", "selected-poster", "Oltre la fine del viaggio", artwork)
        }
    }
    val transition = updateTransition(initial, label = "poster_preview")
    TachiyomiPreviewTheme(appTheme = AppTheme.NYANIME) {
        Surface {
            PosterNavigationTransition(
                navigation = transition,
                routeKey = { it },
                retainedRoutes = setOf("home", "detail"),
                enabled = true,
                transitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
                state = state,
                modifier = Modifier.fillMaxSize(),
            ) { screen ->
                if (screen == "home") {
                    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                        Text("NYANIME", color = Color(0xFFE50914), style = MaterialTheme.typography.headlineLarge)
                        Spacer(Modifier.height(40.dp))
                        Text("La tua lista", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(20.dp))
                        val source = remember { PosterSource("selected-poster") }
                        Image(
                            artwork,
                            null,
                            Modifier.size(152.dp, 228.dp).posterSource(source),
                            contentScale = ContentScale.Crop,
                        )
                    }
                } else if (catalog) {
                    PosterLoadingBody(catalog = true)
                } else {
                    PosterAnimeLoadingScreen(
                        tablet =
                        androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 800,
                    ) {}
                }
            }
        }
    }
}

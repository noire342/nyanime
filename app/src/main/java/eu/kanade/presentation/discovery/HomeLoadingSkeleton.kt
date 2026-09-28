package eu.kanade.presentation.discovery

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.motion.modernMotionEnabled
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

/** One opacity clock per visible skeleton region; frames only update its drawing layer. */
@Composable
fun HomeSkeleton(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val motion = modernMotionEnabled()
    val active = LocalHomeContentActive.current
    val description = stringResource(AYMR.strings.home_loading_content)
    val pulse = remember { Animatable(0.7f) }
    LaunchedEffect(motion, active) {
        if (!motion) {
            pulse.snapTo(0.7f)
        } else if (active) {
            val spec = tween<Float>(HomeMotion.PULSE_HALF_MILLIS, easing = FastOutSlowInEasing)
            while (true) {
                pulse.animateTo(0.9f, spec)
                pulse.animateTo(0.5f, spec)
            }
        }
    }
    Box(
        modifier.graphicsLayer { alpha = pulse.value }.clearAndSetSemantics {
            contentDescription = description
        },
    ) { content() }
}

@Composable
internal fun SkeletonBlock(modifier: Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(6.dp)))
}

@Composable
fun HomeHeroSkeleton(withBrowseAction: Boolean = true, withSourceAction: Boolean = false) {
    HomeSkeleton {
        Column {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val compact = maxWidth < 600.dp
                val artworkHeight = HomeLayout.heroHeight(maxWidth)
                val typography = MaterialTheme.typography
                val density = LocalDensity.current
                val titleHeight = with(density) { typography.headlineMedium.lineHeight.toDp() * 3 }
                val eyebrowHeight = with(density) { typography.labelMedium.lineHeight.toDp() }
                val metadataHeight = eyebrowHeight * 2
                Box(Modifier.fillMaxWidth().heightIn(min = artworkHeight)) {
                    Box(
                        Modifier.matchParentSize().background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.surfaceContainerLow,
                                    MaterialTheme.colorScheme.background,
                                ),
                            ),
                        ),
                    )
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                            .padding(top = artworkHeight * 0.52f, bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = if (compact) Alignment.CenterHorizontally else Alignment.Start,
                    ) {
                        SkeletonBlock(Modifier.width(140.dp).height(eyebrowHeight))
                        Box(
                            Modifier.widthIn(max = 680.dp).fillMaxWidth().height(titleHeight),
                            contentAlignment = if (compact) Alignment.BottomCenter else Alignment.BottomStart,
                        ) {
                            Column(
                                Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                horizontalAlignment = if (compact) Alignment.CenterHorizontally else Alignment.Start,
                            ) {
                                SkeletonBlock(Modifier.fillMaxWidth(0.85f).height(24.dp))
                                SkeletonBlock(Modifier.fillMaxWidth(0.65f).height(24.dp))
                            }
                        }
                        SkeletonBlock(Modifier.fillMaxWidth(0.6f).height(metadataHeight))
                        if (!compact) {
                            SkeletonBlock(Modifier.fillMaxWidth(0.8f).height(metadataHeight))
                        }
                        BoxWithConstraints(Modifier.widthIn(max = 420.dp).fillMaxWidth()) {
                            val stacked = HomeLayout.stackHeroActions(maxWidth, density.fontScale, withSourceAction)
                            val secondary: @Composable () -> Unit = {
                                if (withSourceAction) SkeletonBlock(Modifier.size(48.dp))
                                SkeletonBlock(Modifier.size(48.dp))
                            }
                            if (stacked) {
                                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    SkeletonBlock(Modifier.fillMaxWidth().height(48.dp))
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(
                                            8.dp,
                                            Alignment.CenterHorizontally,
                                        ),
                                    ) { secondary() }
                                }
                            } else {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    SkeletonBlock(Modifier.weight(1f).height(48.dp))
                                    secondary()
                                }
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().height(11.dp), horizontalArrangement = Arrangement.Center) {
                repeat(5) {
                    SkeletonBlock(Modifier.padding(horizontal = 3.dp).size(if (it == 0) 20.dp else 5.dp, 3.dp))
                }
            }
            if (withBrowseAction) {
                Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                    SkeletonBlock(Modifier.width(180.dp).height(16.dp))
                }
            }
        }
    }
}

@Composable
fun HomePosterRowSkeleton(wide: Boolean = false, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val fontScale = LocalDensity.current.fontScale
        val cardWidth = if (wide) HomeLayout.resumeWidth(fontScale, maxWidth) else HomeLayout.posterWidth(fontScale)
        HomeSkeleton {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                userScrollEnabled = false,
            ) {
                items(4) {
                    Column(Modifier.width(cardWidth), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SkeletonBlock(Modifier.fillMaxWidth().aspectRatio(if (wide) 16f / 9f else 2f / 3f))
                        SkeletonBlock(Modifier.fillMaxWidth(0.85f).height(16.dp))
                        SkeletonBlock(Modifier.fillMaxWidth(0.55f).height(14.dp))
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun HomeLoadingSkeleton(withSourceAction: Boolean = false) {
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp), userScrollEnabled = false) {
        item { HomeHeroSkeleton(withSourceAction = withSourceAction) }
        items(2) {
            HomeSkeleton(Modifier.padding(horizontal = 16.dp)) {
                SkeletonBlock(Modifier.width(160.dp).height(20.dp))
            }
            Spacer(Modifier.height(12.dp))
            HomePosterRowSkeleton()
        }
    }
}

@Composable
fun HomeMangaLoadingSkeleton() {
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp), userScrollEnabled = false) {
        item {
            HomeSkeleton(Modifier.padding(horizontal = 20.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SkeletonBlock(Modifier.width(120.dp).height(38.dp))
                    SkeletonBlock(Modifier.width(200.dp).height(48.dp))
                    SkeletonBlock(Modifier.fillMaxWidth().height(222.dp))
                    SkeletonBlock(Modifier.width(170.dp).height(24.dp))
                }
            }
        }
        item { HomePosterRowSkeleton() }
    }
}

@Preview(widthDp = 320)
@Composable
private fun HomeSkeletonPreview() {
    TachiyomiPreviewTheme { HomeLoadingSkeleton() }
}

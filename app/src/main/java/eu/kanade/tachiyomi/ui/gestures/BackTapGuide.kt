package eu.kanade.tachiyomi.ui.gestures

import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.motion.ModernMotion
import eu.kanade.presentation.motion.appMotionEnabled
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
internal fun BackTapGuide(coordinator: BackTapCoordinator) {
    val activity = LocalContext.current as? ComponentActivity ?: return
    val test by coordinator.test.collectAsState()
    val listening by coordinator.listening.collectAsState()
    val motion = appMotionEnabled()
    DisposableEffect(activity) { onDispose { coordinator.endTest(activity) } }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PhoneGuide(test.detections, motion, stringResource(AYMR.strings.back_tap_guide))
            Text(stringResource(AYMR.strings.back_tap_guide_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(
                    if (coordinator.supported) AYMR.strings.back_tap_guide else AYMR.strings.back_tap_not_supported,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            AnimatedContent(
                targetState = test.active,
                transitionSpec = { ModernMotion.transform(motion).using(null) },
                label = "rearTapTest",
            ) { active ->
                if (active) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (test.calibrating || test.completed == 3) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                repeat(3) { index ->
                                    val complete = index < test.completed
                                    Surface(
                                        shape = CircleShape,
                                        color = if (complete) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerHigh
                                        },
                                        modifier = Modifier.size(36.dp),
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            if (complete) {
                                                Icon(Icons.Default.Check, null, Modifier.size(20.dp))
                                            } else {
                                                Text("${index + 1}", style = MaterialTheme.typography.labelLarge)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        val text = when {
                            !listening -> stringResource(AYMR.strings.back_tap_sensor_unavailable)
                            test.calibrating -> stringResource(AYMR.strings.back_tap_calibration_wait)
                            test.completed == 3 -> stringResource(AYMR.strings.back_tap_calibration_done) +
                                "\n" +
                                stringResource(AYMR.strings.back_tap_detected, test.detections)
                            test.detections > 0 -> stringResource(AYMR.strings.back_tap_detected, test.detections)
                            else -> stringResource(AYMR.strings.back_tap_test_wait)
                        }
                        Text(
                            text,
                            Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = { coordinator.endTest(activity) }) {
                            Text(stringResource(AYMR.strings.back_tap_stop_test))
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { coordinator.startTest(activity, calibrate = true) },
                            enabled = coordinator.supported,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(AYMR.strings.back_tap_calibrate), textAlign = TextAlign.Center) }
                        OutlinedButton(
                            onClick = { coordinator.startTest(activity, calibrate = false) },
                            enabled = coordinator.supported,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(AYMR.strings.back_tap_test)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhoneGuide(detections: Int, motion: Boolean, description: String) {
    val progress = if (motion) {
        val transition = rememberInfiniteTransition(label = "rearTapGuide")
        transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2400), RepeatMode.Restart), label = "tapPair")
    } else {
        remember { androidx.compose.runtime.mutableFloatStateOf(0.16f) }
    }
    val glow = remember { Animatable(0f) }
    LaunchedEffect(detections) {
        if (detections > 0 && motion) {
            glow.snapTo(1f)
            glow.animateTo(0f, tween(600))
        }
    }
    val outline = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
    val accent = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(132.dp).semantics { contentDescription = description }) {
        val width = 66.dp.toPx()
        val height = 118.dp.toPx()
        val origin = Offset((size.width - width) / 2, (size.height - height) / 2)
        drawRoundRect(outline, origin, Size(width, height), CornerRadius(16.dp.toPx()), style = Stroke(2.dp.toPx()))
        drawRoundRect(
            outline,
            origin + Offset(9.dp.toPx(), 9.dp.toPx()),
            Size(21.dp.toPx(), 25.dp.toPx()),
            CornerRadius(7.dp.toPx()),
            style = Stroke(1.dp.toPx()),
        )
        val center = origin + Offset(width / 2, height * 0.58f)
        drawCircle(accent.copy(alpha = 0.2f + glow.value * 0.4f), 11.dp.toPx(), center)
        drawCircle(accent, 4.dp.toPx(), center)
        for (start in listOf(0f, 0.18f)) {
            val phase = (progress.value - start) / 0.24f
            if (phase in 0f..1f) {
                drawCircle(
                    accent.copy(alpha = (1 - phase) * 0.8f),
                    (10 + phase * 19).dp.toPx(),
                    center,
                    style = Stroke(2.dp.toPx()),
                )
            }
        }
    }
}

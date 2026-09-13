package eu.kanade.presentation.player

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.android.tools.screenshot.PreviewTest
import eu.kanade.domain.ui.model.AppTheme
import eu.kanade.presentation.discovery.PreviewImages
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import eu.kanade.tachiyomi.data.watch.WatchMedia
import eu.kanade.tachiyomi.data.watch.WatchMember
import eu.kanade.tachiyomi.data.watch.WatchOpeningState
import eu.kanade.tachiyomi.data.watch.WatchPhase
import eu.kanade.tachiyomi.data.watch.WatchRoomState
import eu.kanade.tachiyomi.ui.player.controls.MiddlePlayerControls
import eu.kanade.tachiyomi.ui.player.controls.components.TogetherLoadingArtwork
import eu.kanade.tachiyomi.ui.watch.WatchTogetherContent

@PreviewTest
@Preview(name = "TogetherCountdown", widthDp = 800, heightDp = 360, locale = "it")
@Preview(name = "TogetherCountdownLarge", widthDp = 320, heightDp = 320, fontScale = 1.5f, locale = "it")
@Composable
fun WatchStartScreenshot() = WatchPlayerPreview(
    WatchRoomState(active = true, phase = WatchPhase.Starting, playRequested = true, resumeSeconds = 2),
)

@PreviewTest
@Preview(name = "TogetherPreparing", widthDp = 800, heightDp = 360, locale = "it")
@Preview(name = "TogetherPreparingLarge", widthDp = 320, heightDp = 320, fontScale = 1.5f, locale = "it")
@Composable
fun WatchPreparingScreenshot() = WatchPlayerPreview(
    WatchRoomState(active = true, phase = WatchPhase.Paused, pendingPlaybackPaused = false),
)

@PreviewTest
@Preview(name = "TogetherReady", widthDp = 800, heightDp = 360, locale = "it")
@Composable
fun WatchReadyScreenshot() = WatchPlayerPreview(WatchRoomState(active = true, phase = WatchPhase.Paused))

@PreviewTest
@Preview(name = "TogetherLockedLoading", widthDp = 800, heightDp = 360, locale = "it")
@Composable
fun WatchLockedScreenshot() = WatchPlayerPreview(
    WatchRoomState(active = true, phase = WatchPhase.Buffering, playRequested = true),
    locked = true,
    loading = true,
    reduceMotion = true,
)

@PreviewTest
@Preview(name = "SoloPlayer", widthDp = 800, heightDp = 360, locale = "it")
@Composable
fun WatchSoloScreenshot() = WatchPlayerPreview(WatchRoomState())

@PreviewTest
@Preview(name = "TogetherMorphFrames", widthDp = 620, heightDp = 128, locale = "it")
@Composable
fun WatchMorphScreenshot() {
    Row(
        Modifier.fillMaxSize().background(Color(0xFF111015)),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(0f, 0.5f, 1f, 1.5f, 2f, 2.5f).forEach { phase ->
            TogetherLoadingArtwork(phase = { phase }, angle = { phase * 120f })
        }
    }
}

@Composable
private fun WatchPlayerPreview(
    room: WatchRoomState,
    locked: Boolean = false,
    loading: Boolean = false,
    reduceMotion: Boolean = false,
) {
    PreviewImages()
    TachiyomiPreviewTheme(appTheme = AppTheme.NYANIME, modernUi = true) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AsyncImage("preview://1", null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)))
            MiddlePlayerControls(
                hasPrevious = true,
                onSkipPrevious = {},
                hasNext = true,
                onSkipNext = {},
                isLoading = loading,
                isLoadingEpisode = false,
                controlsShown = !locked,
                areControlsLocked = locked,
                showLoadingCircle = true,
                paused = !room.wantsPlayback,
                gestureSeekAmount = null,
                onPlayPauseClick = {},
                enter = EnterTransition.None,
                exit = ExitTransition.None,
                watchRoom = room,
                reduceMotion = reduceMotion,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

@PreviewTest
@Preview(name = "TogetherSkip", widthDp = 393, heightDp = 240, locale = "it")
@Preview(name = "TogetherSkipLarge", widthDp = 320, heightDp = 400, fontScale = 1.5f, locale = "it")
@Composable
fun WatchSkipScreenshot() = WatchCuesPreview(
    WatchRoomState(
        active = true,
        skip = eu.kanade.tachiyomi.data.watch.WatchSkip(1, "Salta apertura", 90.0),
        skipSeconds = 3,
    ),
)

@PreviewTest
@Preview(name = "TogetherNext", widthDp = 393, heightDp = 440, locale = "it")
@Preview(name = "TogetherNextLarge", widthDp = 320, heightDp = 650, fontScale = 1.5f, locale = "it")
@Preview(name = "TogetherNextLandscape", widthDp = 800, heightDp = 360, locale = "it")
@Composable
fun WatchNextScreenshot() = WatchCuesPreview(
    WatchRoomState(
        active = true,
        next = eu.kanade.tachiyomi.data.watch.WatchNext(
            1,
            WatchMedia(
                "Una notte tra le stelle",
                "Episodio 4 · La promessa sotto la luna",
                4.0,
                0.0,
            ),
        ),
    ),
)

@Composable
private fun WatchCuesPreview(room: WatchRoomState) {
    TachiyomiPreviewTheme(appTheme = AppTheme.NYANIME, modernUi = true) {
        Surface {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
                eu.kanade.tachiyomi.ui.watch.WatchRoomCues(room, {}, {}, {}, {})
            }
        }
    }
}

@PreviewTest
@Preview(name = "WatchEntry", widthDp = 393, heightDp = 800, locale = "it")
@Preview(name = "WatchEntryLandscape", widthDp = 800, heightDp = 360, locale = "it")
@Preview(name = "WatchEntryLargeText", widthDp = 320, heightDp = 800, fontScale = 1.5f, locale = "it")
@Composable
fun WatchEntryScreenshot() = WatchPreview(WatchRoomState())

@PreviewTest
@Preview(name = "WatchRoom", widthDp = 393, heightDp = 950, locale = "it")
@Preview(name = "WatchRoomLandscape", widthDp = 800, heightDp = 360, locale = "it")
@Preview(name = "WatchRoomLargeText", widthDp = 320, heightDp = 1000, fontScale = 1.5f, locale = "it")
@Composable
fun WatchRoomScreenshot() = WatchPreview(
    WatchRoomState(
        phase = WatchPhase.Paused,
        active = true,
        host = true,
        invite = "NY1.ABCDabcd1234EFGHefgh5678IJKLijkl9012MNOPqr",
        media = WatchMedia("Una notte tra le stelle", "Episodio 3 · Un nuovo inizio", 3.0, 1440.0),
        members = listOf(WatchMember("host", "Lorenzo", true, false), WatchMember("guest", "Giulia", true, false)),
        relayCount = 2,
        message = "Tutti pronti. Puoi avviare la riproduzione.",
    ),
)

@PreviewTest
@Preview(name = "WatchGuestFailure", widthDp = 393, heightDp = 800, locale = "it")
@Composable
fun WatchFailureScreenshot() = WatchPreview(
    WatchRoomState(
        active = true,
        phase = WatchPhase.DifferentVideo,
        media = WatchMedia("Una notte tra le stelle", "Episodio 3", 3.0, 1440.0),
    ),
    WatchOpeningState(error = "Serve la stessa estensione del tuo amico, installata e attendibile."),
)

@Composable
private fun WatchPreview(room: WatchRoomState, opening: WatchOpeningState = WatchOpeningState()) {
    TachiyomiPreviewTheme(appTheme = AppTheme.NYANIME, modernUi = true) {
        Surface {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                WatchTogetherContent(room, opening, "", {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
            }
        }
    }
}

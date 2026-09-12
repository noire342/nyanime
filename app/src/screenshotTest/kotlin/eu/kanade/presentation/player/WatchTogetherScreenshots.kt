package eu.kanade.presentation.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import eu.kanade.domain.ui.model.AppTheme
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import eu.kanade.tachiyomi.data.watch.WatchMedia
import eu.kanade.tachiyomi.data.watch.WatchMember
import eu.kanade.tachiyomi.data.watch.WatchOpeningState
import eu.kanade.tachiyomi.data.watch.WatchPhase
import eu.kanade.tachiyomi.data.watch.WatchRoomState
import eu.kanade.tachiyomi.ui.watch.WatchTogetherContent

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

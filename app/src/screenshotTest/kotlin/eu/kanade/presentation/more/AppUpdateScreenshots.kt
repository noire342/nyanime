package eu.kanade.presentation.more

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import eu.kanade.tachiyomi.data.updater.UpdateScreenPhase

@PreviewTest
@Preview(name = "UpdateAvailableDark", widthDp = 393, heightDp = 852, locale = "it")
@Preview(name = "UpdateAvailableNarrow", widthDp = 320, heightDp = 720, fontScale = 1.5f, locale = "it")
@Composable
fun AppUpdateAvailableScreenshot() = UpdatePreview(UpdateScreenPhase.AVAILABLE, true)

@PreviewTest
@Preview(name = "UpdateDownloadLight", widthDp = 393, heightDp = 852, locale = "en-rUS")
@Preview(name = "UpdateDownloadNarrow", widthDp = 320, heightDp = 720, fontScale = 1.5f, locale = "en-rUS")
@Composable
fun AppUpdateDownloadScreenshot() = UpdatePreview(UpdateScreenPhase.DOWNLOADING, false)

@PreviewTest
@Preview(name = "UpdateReadyDark", widthDp = 393, heightDp = 852, locale = "it")
@Composable
fun AppUpdateReadyScreenshot() = UpdatePreview(UpdateScreenPhase.READY, true)

@PreviewTest
@Preview(name = "UpdateErrorLight", widthDp = 320, heightDp = 720, fontScale = 1.3f, locale = "en-rUS")
@Composable
fun AppUpdateErrorScreenshot() = UpdatePreview(UpdateScreenPhase.FAILED, false)

@Composable
private fun UpdatePreview(phase: UpdateScreenPhase, dark: Boolean) {
    TachiyomiPreviewTheme(darkTheme = dark) {
        NewUpdateScreen(
            versionName = "0.19.1.0",
            installedVersion = "0.19.0.2",
            changelogInfo = if (dark) {
                "### Interfaccia\n- Nuova schermata degli aggiornamenti, con novità più leggibili.\n" +
                    "- Comandi sempre raggiungibili durante il download.\n\n### Correzioni\n" +
                    "- Controlli migliorati per i file scaricati."
            } else {
                "### Interface\n- A new update page, with clearer release notes.\n" +
                    "- Actions stay within reach while downloading.\n\n### Fixes\n" +
                    "- Improved checks for downloaded files."
            },
            phase = phase,
            downloadProgress = 64,
            onCancelDownload = {},
            onOpenInBrowser = {},
            onRejectUpdate = {},
            onAcceptUpdate = {},
        )
    }
}

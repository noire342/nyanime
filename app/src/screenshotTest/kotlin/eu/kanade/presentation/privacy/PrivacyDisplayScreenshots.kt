package eu.kanade.presentation.privacy

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.more.settings.widget.InfoWidget
import eu.kanade.presentation.more.settings.widget.PreferenceGroupHeader
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import eu.kanade.tachiyomi.ui.privacy.PrivacyArea
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

@PreviewTest
@Preview(name = "PrivacyModernUnsupported", widthDp = 360, heightDp = 1000, locale = "it")
@Preview(name = "PrivacyNarrowLargeText", widthDp = 280, heightDp = 1300, fontScale = 1.5f, locale = "it")
@Composable
fun PrivacyUnsupportedPreview() = PrivacyPreview(false, true, true)

@PreviewTest
@Preview(name = "PrivacyLegacyUnsupported", widthDp = 320, heightDp = 1000, locale = "it")
@Composable
fun PrivacyLegacyPreview() = PrivacyPreview(false, false, false)

@PreviewTest
@Preview(name = "PrivacyValidationEnabled", widthDp = 393, heightDp = 1000, locale = "it")
@Preview(name = "PrivacyEnabledNarrowLargeText", widthDp = 280, heightDp = 1500, fontScale = 1.5f, locale = "it")
@Composable
fun PrivacyEnabledPreview() = PrivacyPreview(true, true, true)

@Composable
private fun PrivacyPreview(available: Boolean, modern: Boolean, dark: Boolean) {
    TachiyomiPreviewTheme(modernUi = modern, darkTheme = dark) {
        Scaffold(topBar = { AppBar(title = "Sicurezza", navigateUp = {}) }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
                PreferenceGroupHeader(stringResource(AYMR.strings.privacy_display_title))
                PrivacyToggleRow(
                    title = stringResource(AYMR.strings.privacy_display_enable),
                    subtitle = stringResource(
                        if (available) {
                            AYMR.strings.privacy_display_validation
                        } else {
                            AYMR.strings.privacy_display_no_hardware
                        },
                    ),
                    checked = available,
                    enabled = available,
                    onToggle = {},
                )
                if (available) {
                    PrivacyArea.entries.forEach { area ->
                        PrivacyToggleRow(
                            title = privacyAreaTitle(area),
                            checked = area != PrivacyArea.NSFW,
                            enabled = true,
                            onToggle = {},
                        )
                    }
                }
                InfoWidget(stringResource(AYMR.strings.privacy_display_summary))
            }
        }
    }
}

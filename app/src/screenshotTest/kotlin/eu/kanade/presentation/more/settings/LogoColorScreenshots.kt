package eu.kanade.presentation.more.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import eu.kanade.domain.ui.model.AppTheme
import eu.kanade.domain.ui.model.NyanimeLogoColor
import eu.kanade.presentation.more.LogoHeader
import eu.kanade.presentation.more.settings.widget.LogoColorPreferenceContent
import eu.kanade.presentation.theme.TachiyomiPreviewTheme

@PreviewTest
@Preview(name = "SunYellow", widthDp = 393, heightDp = 590, locale = "it")
@Preview(name = "NarrowLargeText", widthDp = 320, heightDp = 1030, fontScale = 1.6f, locale = "it")
@Composable
fun NyanimeYellowLogoSetting() = LogoSetting(NyanimeLogoColor.SUN_YELLOW)

@PreviewTest
@Preview(name = "Red", widthDp = 393, heightDp = 590, locale = "it")
@Composable
fun NyanimeRedLogoSetting() = LogoSetting(NyanimeLogoColor.RED)

@PreviewTest
@Preview(name = "Legacy", widthDp = 393, heightDp = 590, locale = "it")
@Composable
fun NyanimeLegacyLogoSetting() = LogoSetting(NyanimeLogoColor.SUN_YELLOW, AppTheme.DEFAULT)

@Composable
private fun LogoSetting(color: NyanimeLogoColor, theme: AppTheme = AppTheme.NYANIME) {
    TachiyomiPreviewTheme(appTheme = theme, logoColor = color) {
        Surface(Modifier.fillMaxSize()) {
            Column {
                LogoHeader()
                LogoColorPreferenceContent(color, {})
            }
        }
    }
}

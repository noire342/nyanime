package eu.kanade.presentation.privacy

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.util.system.copyToClipboard
import nyanime.privacy.display.PrivacyDisplayCapability
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

/** Local test APK only. Contains public device/firmware facts, never user or content identifiers. */
@Composable
internal fun privacyValidationDiagnostics(
    capability: PrivacyDisplayCapability,
): List<Preference.PreferenceItem.TextPreference> {
    if (!BuildConfig.PRIVACY_DISPLAY_VALIDATION) return emptyList()
    val context = LocalContext.current
    val title = stringResource(AYMR.strings.privacy_display_copy_validation)
    return listOf(
        Preference.PreferenceItem.TextPreference(
            title = title,
            subtitle = stringResource(AYMR.strings.privacy_display_copy_validation_summary),
            onClick = {
                val details = listOf(
                    "Nyanime r${BuildConfig.COMMIT_COUNT}",
                    "Model: ${Build.MODEL}",
                    "SDK: ${Build.VERSION.SDK_INT}",
                    "Firmware: ${Build.FINGERPRINT}",
                    "Availability: $capability",
                ).joinToString("\n")
                context.copyToClipboard(title, details)
            },
        ),
    )
}

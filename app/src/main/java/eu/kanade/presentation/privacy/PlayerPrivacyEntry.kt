package eu.kanade.presentation.privacy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.privacy.PrivacyArea
import eu.kanade.tachiyomi.ui.privacy.PrivacyDisplayRuntime
import nyanime.privacy.display.PrivacyDisplayCapability
import nyanime.privacy.display.PrivacyDisplayState
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun PlayerPrivacyEntry() {
    val controller = LocalContext.current.baseActivity()?.privacyDisplayController ?: return
    val state by controller.state.collectAsState()
    val requested by controller.videoEnabled.collectAsState()
    val runtimeCapability = PrivacyDisplayRuntime.capability(PrivacyArea.VIDEO)
    if (runtimeCapability != PrivacyDisplayCapability.Available && state !is PrivacyDisplayState.Failed) return
    val capability = when (val current = state) {
        is PrivacyDisplayState.Unavailable -> PrivacyDisplayCapability.Unavailable(current.reason)
        else -> runtimeCapability
    }
    val available = capability == PrivacyDisplayCapability.Available
    val subtitle = if (!available) {
        privacyCapabilityDescription(capability)
    } else {
        stringResource(
            if (state is PrivacyDisplayState.Applied) {
                AYMR.strings.privacy_display_applied
            } else {
                AYMR.strings.privacy_display_player_session
            },
        )
    }
    PrivacyToggleRow(
        title = stringResource(AYMR.strings.privacy_display_title),
        subtitle = subtitle,
        checked = available && (requested || state is PrivacyDisplayState.Applied),
        enabled = available,
        onToggle = controller::setVideoOverride,
        horizontalPadding = 0.dp,
    )
}

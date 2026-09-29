package eu.kanade.presentation.privacy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.privacy.PrivacyArea
import eu.kanade.tachiyomi.ui.privacy.PrivacyDisplayRuntime
import nyanime.privacy.display.PrivacyDisplayCapability
import nyanime.privacy.display.PrivacyDisplayState
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

/** Player and reader share the same window-owned, temporary control. */
@Composable
fun PrivacySessionEntry(area: PrivacyArea, sessionDescription: String, horizontalPadding: Dp = 16.dp) {
    val controller = LocalContext.current.baseActivity()?.privacyDisplayController ?: return
    val state by controller.state.collectAsState()
    val requestedAreas by controller.requestedAreas.collectAsState()
    val failure by PrivacyDisplayRuntime.failure.collectAsState()
    val capability = PrivacyDisplayRuntime.capability()
    val available = capability == PrivacyDisplayCapability.Available
    val subtitle = when {
        failure != null -> privacyCapabilityDescription(capability)
        !available -> privacyCapabilityDescription(capability)
        state is PrivacyDisplayState.Unavailable ->
            privacyCapabilityDescription(
                PrivacyDisplayCapability.Unavailable((state as PrivacyDisplayState.Unavailable).reason),
            )
        state is PrivacyDisplayState.Applied -> stringResource(AYMR.strings.privacy_display_applied)
        else -> sessionDescription
    }
    PrivacyToggleRow(
        title = stringResource(AYMR.strings.privacy_display_title),
        subtitle = subtitle,
        checked = area in requestedAreas,
        enabled = available,
        onToggle = { controller.setTemporaryOverride(area, it) },
        horizontalPadding = horizontalPadding,
    )
}

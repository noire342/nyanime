package eu.kanade.tachiyomi.ui.privacy

import android.os.Build
import eu.kanade.tachiyomi.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import nyanime.privacy.display.AndroidPrivacyDisplayBackends
import nyanime.privacy.display.PrivacyDisplayCapability
import nyanime.privacy.display.PrivacyDisplayState
import nyanime.privacy.display.PrivacyUnavailableReason

/** Only device/API facts live here; never a Window, Context, Activity or View. */
object PrivacyDisplayRuntime {
    private val mutableFailure = MutableStateFlow<PrivacyDisplayState.Failed?>(null)
    val failure: StateFlow<PrivacyDisplayState.Failed?> = mutableFailure

    fun recordFailure(state: PrivacyDisplayState.Failed) {
        mutableFailure.value = state
    }
    val backend by lazy { AndroidPrivacyDisplayBackends.create() }

    // An entry requires recorded physical validation for this exact firmware and area.
    private data class Verification(val model: String, val fingerprint: String, val areas: Set<PrivacyArea>)
    private val verifiedDevices: List<Verification> = emptyList()

    fun capability(area: PrivacyArea? = null): PrivacyDisplayCapability {
        if (mutableFailure.value != null) return PrivacyDisplayCapability.Unavailable(PrivacyUnavailableReason.FIRMWARE)
        if (backend.capability != PrivacyDisplayCapability.Available) return backend.capability
        val verified = verifiedDevices.firstOrNull { it.model == Build.MODEL && it.fingerprint == Build.FINGERPRINT }
        val permitted = BuildConfig.PRIVACY_DISPLAY_VALIDATION ||
            (verified != null && verified.areas.isNotEmpty() && (area == null || area in verified.areas))
        return if (permitted) {
            PrivacyDisplayCapability.Available
        } else {
            PrivacyDisplayCapability.Unavailable(PrivacyUnavailableReason.NOT_VALIDATED)
        }
    }
}

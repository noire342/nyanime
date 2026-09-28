package eu.kanade.tachiyomi.ui.privacy

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PrivacyDisplayVerifiedDevicesTest {
    private val model = "SM-S948B"
    private val fingerprint = "samsung/m3qxeea/m3q:16/BP4A.251205.006/S948BXXS4AZHL_OXM4AZHL:user/release-keys"

    @Test fun theConfirmedDeviceSupportsOrdinaryPrivacyAreas() {
        assertTrue(PrivacyDisplayVerifiedDevices.supports(model, fingerprint))
        PrivacyArea.entries.forEach {
            assertTrue(PrivacyDisplayVerifiedDevices.supports(model, fingerprint, it))
        }
    }

    @Test fun anotherHardwareModelCannotInheritTheApproval() {
        assertFalse(PrivacyDisplayVerifiedDevices.supports("SM-F741B", fingerprint))
        assertFalse(PrivacyDisplayVerifiedDevices.supports("SM-S948U", fingerprint))
        assertFalse(PrivacyDisplayVerifiedDevices.supports("", fingerprint))
    }

    @Test fun anotherFirmwareRequiresItsOwnVerification() {
        assertFalse(PrivacyDisplayVerifiedDevices.supports(model, fingerprint.replace("AZHL", "OTHER")))
        assertFalse(PrivacyDisplayVerifiedDevices.supports(model, "S948BXXS4AZHL"))
        assertFalse(PrivacyDisplayVerifiedDevices.supports(model, ""))
    }
}

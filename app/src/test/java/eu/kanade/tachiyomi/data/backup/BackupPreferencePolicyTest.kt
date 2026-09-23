package eu.kanade.tachiyomi.data.backup

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference

class BackupPreferencePolicyTest {
    @Test fun portableSettingsSurviveADeviceMove() {
        assertTrue(BackupPreferencePolicy.isPortable("pref_theme"))
        assertTrue(BackupPreferencePolicy.isPortable(Preference.privateKey("track_token_1")))
        assertTrue(BackupPreferencePolicy.isPortable(Preference.appStateKey("has_filters_toggle_state")))
    }

    @Test fun localStateAndDatabaseIdBasedProfilesStayOnTheOriginalDevice() {
        assertFalse(BackupPreferencePolicy.isPortable(Preference.appStateKey("storage_dir")))
        assertFalse(BackupPreferencePolicy.isPortable(Preference.appStateKey("discovery_hidden_resume")))
        assertFalse(BackupPreferencePolicy.isPortable(Preference.appStateKey("trusted_extensions")))
        assertFalse(BackupPreferencePolicy.isPortable(Preference.privateKey("anime4k_episode_profile_1_12")))
        assertFalse(BackupPreferencePolicy.isPortable(Preference.privateKey("anime4k_calibration_gpu")))
    }
}

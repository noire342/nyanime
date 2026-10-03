package eu.kanade.tachiyomi.data.backup

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

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

    @Test fun retiredGestureSettingsCannotReturnFromAnOlderBackup() {
        for (key in listOf(
            "back_tap_enabled",
            "back_tap_haptic",
            "back_tap_sensitivity",
            "back_tap_action_Reader",
            Preference.appStateKey("back_tap_calibration"),
        )) {
            assertTrue(BackupPreferencePolicy.isRetired(key))
            assertFalse(BackupPreferencePolicy.isPortable(key))
        }
        assertFalse(BackupPreferencePolicy.isRetired("reader_tap"))
        assertTrue(BackupPreferencePolicy.isPortable("player_longpress_action"))
    }

    @Test fun cleanupDeletesOnlyRetiredKeysWithoutReadingTheirDifferentValueTypes() {
        val store = mockk<PreferenceStore>()
        val calibrationKey = Preference.appStateKey("back_tap_calibration")
        every { store.getAll() } returns mapOf(
            "back_tap_enabled" to true,
            "back_tap_action_Player" to "PlayPause",
            calibrationKey to 1.2f,
            "reader_tap" to true,
            "pref_theme" to "Dark",
        )
        val removed = mutableSetOf<String>()
        val handle = mockk<Preference<String>>(relaxed = true)
        every { store.getString(any(), any()) } answers {
            removed.add(firstArg())
            handle
        }
        BackupPreferencePolicy.removeRetired(store)
        assertEquals(setOf("back_tap_enabled", "back_tap_action_Player", calibrationKey), removed)
        verify(exactly = 3) { handle.delete() }
        verify(exactly = 0) { handle.get() }
    }
}

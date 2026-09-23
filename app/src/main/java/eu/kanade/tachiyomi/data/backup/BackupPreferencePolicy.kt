package eu.kanade.tachiyomi.data.backup

import tachiyomi.core.common.preference.Preference

/** Settings that remain meaningful after the database IDs and hardware change. */
object BackupPreferencePolicy {
    private val portableAppStateKeys = setOf(
        Preference.appStateKey("has_filters_toggle_state"),
        Preference.appStateKey("last_anime_catalogue_source"),
        Preference.appStateKey("last_catalogue_source"),
    )

    private val deviceSpecificPrefixes = listOf(
        Preference.privateKey("anime4k_episode_profile_"),
        Preference.privateKey("anime4k_calibration_"),
    )

    fun isPortable(key: String): Boolean =
        (!Preference.isAppState(key) || key in portableAppStateKeys) &&
            deviceSpecificPrefixes.none(key::startsWith)
}

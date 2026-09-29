package eu.kanade.tachiyomi.data.releases

import tachiyomi.core.common.preference.PreferenceStore
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ReleasePreferences(store: PreferenceStore = Injekt.get()) {
    val enabled = store.getBoolean("release_monitor_enabled", true)
    val reminders = store.getBoolean("release_reminders_enabled", true)
    val advanceReminders = store.getBoolean("release_advance_reminders_enabled", true)
    val availability = store.getBoolean("release_availability_enabled", true)
    val unifiedAgenda = store.getBoolean("release_unified_agenda", true)
}

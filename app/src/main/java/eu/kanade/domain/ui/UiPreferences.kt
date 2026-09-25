package eu.kanade.domain.ui

import eu.kanade.domain.ui.model.AppTheme
import eu.kanade.domain.ui.model.NavStyle
import eu.kanade.domain.ui.model.StartScreen
import eu.kanade.domain.ui.model.TabletUiMode
import eu.kanade.domain.ui.model.ThemeMode
import eu.kanade.domain.ui.model.TvDisplayMode
import eu.kanade.domain.ui.model.TvUiMode
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.isDynamicColorAvailable
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

class UiPreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun themeMode() = preferenceStore.getEnum("pref_theme_mode_key", ThemeMode.SYSTEM)

    fun appTheme() = preferenceStore.getEnum(
        "pref_app_theme",
        AppTheme.NYANIME,
    )

    fun themeDarkAmoled() = preferenceStore.getBoolean("pref_theme_dark_amoled_key", false)

    fun sourceHomeLogo() = preferenceStore.getBoolean("source_home_logo", false)

    fun inAppUpdateInstallation() = preferenceStore.getBoolean("nyanime_in_app_update_installation", true)

    fun dismissedReadyUpdate() = preferenceStore.getString("nyanime_dismissed_ready_update")

    fun dismissedLibraryUpdates() = preferenceStore.getStringSet("nyanime_dismissed_library_updates")

    fun autoAcknowledgeHomeUpdates() = preferenceStore.getBoolean("nyanime_auto_acknowledge_home_updates", true)

    fun lastSeenAnimeUpdateNotice() = preferenceStore.getLong("nyanime_last_seen_anime_update_notice")

    fun lastSeenMangaUpdateNotice() = preferenceStore.getLong("nyanime_last_seen_manga_update_notice")

    fun modernUi() = preferenceStore.getBoolean("nyanime_modern_ui", true)

    fun tvUiMode() = preferenceStore.getEnum("nyanime_tv_ui_mode", TvUiMode.AUTOMATIC)

    fun tvDisplayMode() = preferenceStore.getEnum("nyanime_tv_display_mode", TvDisplayMode.MIRROR)

    fun tvReduceMotion() = preferenceStore.getBoolean("nyanime_tv_reduce_motion", false)

    fun legacyAppTheme() = preferenceStore.getEnum("nyanime_legacy_app_theme", legacyMangaTheme().get())

    fun activeAppTheme() = if (modernUi().get()) appTheme().get() else legacyAppTheme().get()

    private val legacyDefaultTheme get() = if (DeviceUtil.isDynamicColorAvailable) AppTheme.MONET else AppTheme.DEFAULT

    fun legacyMangaTheme() = preferenceStore.getEnum("nyanime_legacy_manga_theme", legacyDefaultTheme)

    fun installNyanimeThemeOnce() {
        val installed = preferenceStore.getBoolean("nyanime_visual_identity_v1", false)
        if (installed.get()) return
        legacyMangaTheme().set(appTheme().get().takeUnless { it == AppTheme.NYANIME } ?: legacyDefaultTheme)
        legacyAppTheme().set(legacyMangaTheme().get())
        appTheme().set(AppTheme.NYANIME)
        installed.set(true)
    }

    fun relativeTime() = preferenceStore.getBoolean("relative_time_v2", true)

    fun dateFormat() = preferenceStore.getString("app_date_format", "")

    fun tabletUiMode() = preferenceStore.getEnum("tablet_ui_mode", TabletUiMode.AUTOMATIC)

    fun startScreen() = preferenceStore.getEnum("start_screen", StartScreen.HOME)

    fun navStyle() = preferenceStore.getEnum("bottom_rail_nav_style", NavStyle.DISCOVERY)

    fun installDiscoveryNavigationOnce() {
        val migrated = preferenceStore.getBoolean("fork_discovery_navigation_v1", false)
        if (migrated.get()) return
        startScreen().set(StartScreen.HOME)
        navStyle().set(NavStyle.DISCOVERY)
        migrated.set(true)
    }

    companion object {
        fun dateFormat(format: String): DateTimeFormatter = when (format) {
            "" -> DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
            else -> DateTimeFormatter.ofPattern(format, Locale.getDefault())
        }
    }
}

package eu.kanade.tachiyomi.ui.gestures

import dev.icerock.moko.resources.StringResource
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum
import tachiyomi.i18n.aniyomi.AYMR

enum class BackTapContext(val title: StringResource, val default: BackTapAction, val actions: List<BackTapAction>) {
    Navigation(
        AYMR.strings.back_tap_navigation,
        BackTapAction.QuickMenu,
        listOf(
            BackTapAction.QuickMenu,
            BackTapAction.Search,
            BackTapAction.Library,
            BackTapAction.Releases,
            BackTapAction.Back,
        ),
    ),
    Player(
        AYMR.strings.back_tap_player,
        BackTapAction.PlayPause,
        listOf(
            BackTapAction.PlayPause,
            BackTapAction.Forward,
            BackTapAction.Rewind,
            BackTapAction.Controls,
            BackTapAction.QuickMenu,
        ),
    ),
    Reader(
        AYMR.strings.back_tap_reader,
        BackTapAction.NextPage,
        listOf(
            BackTapAction.NextPage,
            BackTapAction.PreviousPage,
            BackTapAction.Controls,
            BackTapAction.Bookmark,
            BackTapAction.QuickMenu,
        ),
    ),
    Remote(
        AYMR.strings.back_tap_remote,
        BackTapAction.PlayPause,
        listOf(
            BackTapAction.PlayPause,
            BackTapAction.Forward,
            BackTapAction.Rewind,
            BackTapAction.QuickMenu,
        ),
    ),
}

enum class BackTapAction(val title: StringResource) {
    QuickMenu(AYMR.strings.back_tap_quick_menu),
    Search(AYMR.strings.back_tap_search),
    Library(AYMR.strings.back_tap_library),
    Releases(AYMR.strings.back_tap_releases),
    Rooms(AYMR.strings.back_tap_rooms),
    Back(AYMR.strings.back_tap_back),
    PlayPause(AYMR.strings.back_tap_play_pause),
    Forward(AYMR.strings.back_tap_forward),
    Rewind(AYMR.strings.back_tap_rewind),
    Controls(AYMR.strings.back_tap_controls),
    NextPage(AYMR.strings.back_tap_next_page),
    PreviousPage(AYMR.strings.back_tap_previous_page),
    Bookmark(AYMR.strings.back_tap_bookmark),
}

enum class BackTapSensitivity(val title: StringResource, val multiplier: Double) {
    Low(AYMR.strings.back_tap_sensitivity_low, 1.35),
    Balanced(AYMR.strings.back_tap_sensitivity_balanced, 1.0),
    High(AYMR.strings.back_tap_sensitivity_high, 0.75),
}

class BackTapPreferences(private val store: PreferenceStore) {
    fun enabled() = store.getBoolean("back_tap_enabled", false)
    fun haptic() = store.getBoolean("back_tap_haptic", true)
    fun sensitivity() = store.getEnum("back_tap_sensitivity", BackTapSensitivity.Balanced)
    fun action(context: BackTapContext) = store.getEnum("back_tap_action_${context.name}", context.default)

    // Sensor response depends on this handset and case. Never restore it on another device.
    fun calibration() = store.getFloat(Preference.appStateKey("back_tap_calibration"), 0f)
    fun threshold() = (
        calibration().get().takeIf {
            it.isFinite() && it in BackTapDetector.CALIBRATION_THRESHOLD.toFloat()..8f
        } ?: 2.4f
        ) *
        sensitivity().get().multiplier
}

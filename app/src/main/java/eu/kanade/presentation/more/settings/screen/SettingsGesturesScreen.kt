package eu.kanade.presentation.more.settings.screen

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.ui.gestures.BackTapContext
import eu.kanade.tachiyomi.ui.gestures.BackTapCoordinator
import eu.kanade.tachiyomi.ui.gestures.BackTapGuide
import eu.kanade.tachiyomi.ui.gestures.BackTapPreferences
import eu.kanade.tachiyomi.ui.gestures.BackTapSensitivity
import eu.kanade.tachiyomi.ui.gestures.performBackTapNavigation
import eu.kanade.tachiyomi.ui.gestures.showBackTapQuickMenu
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsGesturesScreen : SearchableSettings {
    @Composable
    @ReadOnlyComposable
    override fun getTitleRes() = AYMR.strings.back_tap_settings_gestures

    @Composable
    override fun getPreferences(): List<Preference> {
        val preferences = remember { Injekt.get<BackTapPreferences>() }
        val coordinator = remember { Injekt.get<BackTapCoordinator>() }
        val activity = LocalContext.current as? ComponentActivity
        val enabled = preferences.enabled().collectAsState().value && coordinator.supported
        return listOf(
            Preference.PreferenceItem.CustomPreference(
                title = stringResource(AYMR.strings.back_tap_title),
                content = { BackTapGuide(coordinator) },
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = preferences.enabled(),
                title = stringResource(AYMR.strings.back_tap_enable),
                subtitle = stringResource(AYMR.strings.back_tap_foreground),
                enabled = coordinator.supported,
            ),
            Preference.PreferenceItem.ListPreference(
                preference = preferences.sensitivity(),
                title = stringResource(AYMR.strings.back_tap_sensitivity),
                entries = BackTapSensitivity.entries.associateWith { stringResource(it.title) }.toPersistentMap(),
                enabled = coordinator.supported,
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = preferences.haptic(),
                title = stringResource(AYMR.strings.back_tap_haptic),
                subtitle = stringResource(AYMR.strings.back_tap_haptic_summary),
                enabled = coordinator.supported,
            ),
            Preference.PreferenceGroup(
                title = stringResource(AYMR.strings.back_tap_actions),
                preferenceItems = BackTapContext.entries.map { context ->
                    Preference.PreferenceItem.ListPreference(
                        preference = preferences.action(context),
                        title = stringResource(context.title),
                        entries = context.actions.associateWith { stringResource(it.title) }.toPersistentMap(),
                        enabled = enabled,
                    )
                }.toPersistentList(),
            ),
            Preference.PreferenceGroup(
                title = stringResource(AYMR.strings.back_tap_title),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(AYMR.strings.back_tap_reset_calibration),
                        subtitle = stringResource(AYMR.strings.back_tap_reset_summary),
                        onClick = { preferences.calibration().delete() },
                        enabled = coordinator.supported,
                    ),
                    Preference.PreferenceItem.InfoPreference(stringResource(AYMR.strings.back_tap_local_calibration)),
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(AYMR.strings.back_tap_preview),
                        onClick = {
                            activity?.let { host ->
                                showBackTapQuickMenu(host) { performBackTapNavigation(host, it) }
                            }
                        },
                    ),
                ),
            ),
        )
    }
}

package eu.kanade.tachiyomi.util.system

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.NyanimeLogoColor
import tachiyomi.core.common.preference.Preference

/** Serializes startup reconciliation and user selections. Call off the main thread. */
object NyanimeLogoManager {
    @Synchronized
    fun select(context: Context, preferences: UiPreferences, color: NyanimeLogoColor) {
        LogoSelection(preferences.logoColor()) { applyLauncher(context, it) }.select(color)
    }

    @Synchronized
    fun reconcile(context: Context, preferences: UiPreferences) {
        applyLauncher(context, preferences.logoColor().get())
    }

    private fun applyLauncher(context: Context, color: NyanimeLogoColor) {
        val manager = context.packageManager
        fun component(choice: NyanimeLogoColor) = ComponentName(
            context.packageName,
            when (choice) {
                NyanimeLogoColor.RED -> "eu.kanade.tachiyomi.ui.main.NyanimeRedLauncher"
                NyanimeLogoColor.SUN_YELLOW -> "eu.kanade.tachiyomi.ui.main.NyanimeYellowLauncher"
            },
        )

        val changes = logoLauncherChanges(color) { choice ->
            when (manager.getComponentEnabledSetting(component(choice))) {
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> choice == NyanimeLogoColor.RED
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                else -> false
            }
        }
        if (changes.isEmpty()) return

        fun state(change: LogoLauncherChange) = if (change.enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            manager.setComponentEnabledSettings(
                changes.map {
                    PackageManager.ComponentEnabledSetting(component(it.color), state(it), PackageManager.DONT_KILL_APP)
                },
            )
        } else {
            // Enable the destination first. MainActivity itself is never disabled.
            changes.forEach {
                manager.setComponentEnabledSetting(component(it.color), state(it), PackageManager.DONT_KILL_APP)
            }
        }
    }
}

internal class LogoSelection(
    private val preference: Preference<NyanimeLogoColor>,
    private val applyLauncher: (NyanimeLogoColor) -> Unit,
) {
    fun select(color: NyanimeLogoColor) {
        val previous = preference.get()
        try {
            applyLauncher(color)
            if (previous != color) preference.set(color)
        } catch (error: Exception) {
            // A pre-33 device can reject the second of the two component updates.
            try {
                applyLauncher(previous)
            } catch (rollbackError: Exception) {
                error.addSuppressed(rollbackError)
            }
            throw error
        }
    }
}

internal data class LogoLauncherChange(val color: NyanimeLogoColor, val enabled: Boolean)

internal fun logoLauncherChanges(
    selected: NyanimeLogoColor,
    isEnabled: (NyanimeLogoColor) -> Boolean,
): List<LogoLauncherChange> = buildList {
    if (!isEnabled(selected)) add(LogoLauncherChange(selected, true))
    NyanimeLogoColor.entries.filter { it != selected && isEnabled(it) }.forEach {
        add(LogoLauncherChange(it, false))
    }
}

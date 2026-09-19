package eu.kanade.tachiyomi.ui

import eu.kanade.domain.ui.model.NyanimeLogoColor
import eu.kanade.domain.ui.model.NyanimeLogoColor.RED
import eu.kanade.domain.ui.model.NyanimeLogoColor.SUN_YELLOW
import eu.kanade.tachiyomi.util.system.LogoSelection
import eu.kanade.tachiyomi.util.system.logoLauncherChanges
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore.InMemoryPreference

class NyanimeLogoSelectionTest {
    @Test
    fun iconIsUpdatedBeforePreferenceAndCanReturnToRed() {
        val preference = InMemoryPreference("logo", null, RED)
        val calls = mutableListOf<NyanimeLogoColor>()
        val selection = LogoSelection(preference) { color ->
            assertEquals(if (color == SUN_YELLOW) RED else SUN_YELLOW, preference.get())
            calls += color
        }
        selection.select(SUN_YELLOW)
        assertEquals(SUN_YELLOW, preference.get())
        selection.select(RED)
        assertEquals(RED, preference.get())
        assertEquals(listOf(SUN_YELLOW, RED), calls)
    }

    @Test
    fun rejectionRestoresThePreviousIconAndDoesNotSaveTheFailedChoice() {
        val preference = InMemoryPreference("logo", null, RED)
        val calls = mutableListOf<NyanimeLogoColor>()
        val selection = LogoSelection(preference) {
            calls += it
            if (it == SUN_YELLOW) throw SecurityException("Launcher rejected the update")
        }
        assertThrows(SecurityException::class.java) { selection.select(SUN_YELLOW) }
        assertEquals(listOf(SUN_YELLOW, RED), calls)
        assertEquals(RED, preference.get())
        assertFalse(preference.isSet())
    }

    @Test
    fun failureDuringRollbackPreservesBothCauses() {
        val preference = InMemoryPreference("logo", SUN_YELLOW, RED)
        val selection = LogoSelection(preference) { throw IllegalStateException(it.name) }
        val error = assertThrows(IllegalStateException::class.java) { selection.select(RED) }
        assertEquals("RED", error.message)
        assertEquals("SUN_YELLOW", error.suppressed.single().message)
        assertEquals(SUN_YELLOW, preference.get())
    }

    @Test
    fun everyStartingStateConvergesWithoutRemovingTheLastEnabledIcon() {
        for (redEnabled in listOf(false, true)) {
            for (yellowEnabled in listOf(false, true)) {
                for (selected in NyanimeLogoColor.entries) {
                    val state = mutableMapOf(RED to redEnabled, SUN_YELLOW to yellowEnabled)
                    val changes = logoLauncherChanges(selected) { state.getValue(it) }
                    changes.forEach {
                        state[it.color] = it.enabled
                        assertTrue(state.values.any { enabled -> enabled })
                    }
                    assertTrue(state.getValue(selected))
                    assertEquals(1, state.values.count { it })
                    assertTrue(logoLauncherChanges(selected) { state.getValue(it) }.isEmpty())
                }
            }
        }
    }

    @Test
    fun partiallyAppliedSwitchCanBeRecoveredOnTheNextStart() {
        val state = mutableMapOf(RED to true, SUN_YELLOW to false)
        val preference = InMemoryPreference("logo", null, RED)
        val selection = LogoSelection(preference) { color ->
            logoLauncherChanges(color) { state.getValue(it) }.forEach {
                if (!it.enabled && it.color == RED) throw IllegalStateException("Second update failed")
                state[it.color] = it.enabled
            }
        }
        assertThrows(IllegalStateException::class.java) { selection.select(SUN_YELLOW) }
        assertEquals(mapOf(RED to true, SUN_YELLOW to false), state)
        assertEquals(RED, preference.get())
    }
}

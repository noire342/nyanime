package eu.kanade.tachiyomi.ui.player

import eu.kanade.tachiyomi.ui.player.settings.AdvancedPlayerPreferences
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore.InMemoryPreference
import tachiyomi.core.common.preference.PreferenceStore

class Anime4KStartupTest {
    @Test
    fun `automatic Smart defaults on without saving a manual episode choice`() {
        val preferences = AdvancedPlayerPreferences(store())
        assertTrue(preferences.anime4kSmartAutoStart().get())
        assertEquals(Anime4KProfile.Smart, preferences.loadAnime4kEpisodeProfile(1, 1).profile)
        assertNull(preferences.anime4kEpisodeProfile(1, 1))
    }

    @Test
    fun `disabling automatic Smart survives restart and covers previously saved Smart`() {
        val store = store()
        val preferences = AdvancedPlayerPreferences(store)
        preferences.saveAnime4kEpisodeProfile(1, 1, Anime4KEpisodeProfile(Anime4KProfile.Smart))
        preferences.anime4kSmartAutoStart().set(false)
        val recreated = AdvancedPlayerPreferences(store)
        assertFalse(recreated.anime4kSmartAutoStart().get())
        assertEquals(Anime4KProfile.Off, recreated.loadAnime4kEpisodeProfile(1, 1).profile)
        assertEquals(Anime4KProfile.Smart, recreated.anime4kEpisodeProfile(1, 1)?.profile)
        assertEquals(Anime4KProfile.Off, recreated.loadAnime4kEpisodeProfile(1, 2).profile)
        recreated.anime4kSmartAutoStart().set(true)
        recreated.beginAnime4kSession()
        assertEquals(Anime4KProfile.Smart, recreated.loadAnime4kEpisodeProfile(1, 2).profile)
    }

    @Test
    fun `manual Smart survives stream reload but does not override the next startup`() {
        val preferences = AdvancedPlayerPreferences(store())
        preferences.anime4kSmartAutoStart().set(false)
        preferences.loadAnime4kEpisodeProfile(1, 1)
        preferences.saveAnime4kEpisodeProfile(1, 1, Anime4KEpisodeProfile(Anime4KProfile.Smart))
        assertEquals(Anime4KProfile.Smart, preferences.loadAnime4kEpisodeProfile(1, 1).profile)
        preferences.beginAnime4kSession()
        assertEquals(Anime4KProfile.Off, preferences.loadAnime4kEpisodeProfile(1, 1).profile)
        assertEquals(Anime4KProfile.Off, preferences.loadAnime4kEpisodeProfile(1, 2).profile)
    }

    @Test
    fun `saved Off Maximum and Custom choices remain unchanged`() {
        val preferences = AdvancedPlayerPreferences(store())
        for (autoStart in listOf(true, false)) {
            preferences.anime4kSmartAutoStart().set(autoStart)
            for (profile in listOf(
                Anime4KEpisodeProfile(Anime4KProfile.Off),
                Anime4KEpisodeProfile(Anime4KProfile.Maximum),
                Anime4KEpisodeProfile(Anime4KProfile.Custom, Anime4KMode.ModeC),
            )) {
                preferences.beginAnime4kSession()
                preferences.saveAnime4kEpisodeProfile(1, 1, profile)
                assertEquals(profile, preferences.loadAnime4kEpisodeProfile(1, 1))
            }
        }
    }

    private fun store(): PreferenceStore {
        val strings = mutableMapOf<String, InMemoryPreference<String>>()
        val booleans = mutableMapOf<String, InMemoryPreference<Boolean>>()
        return mockk {
            every { getString(any(), any()) } answers {
                val key = firstArg<String>()
                strings.getOrPut(key) { InMemoryPreference(key, null, secondArg()) }
            }
            every { getBoolean(any(), any()) } answers {
                val key = firstArg<String>()
                booleans.getOrPut(key) { InMemoryPreference(key, null, secondArg()) }
            }
        }
    }
}

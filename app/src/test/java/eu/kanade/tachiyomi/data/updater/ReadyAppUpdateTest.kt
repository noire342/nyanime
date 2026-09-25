package eu.kanade.tachiyomi.data.updater

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReadyAppUpdateTest {
    @Test
    fun `preview update is newer even when version codes are equal`() {
        assertTrue(isNewerAppUpdate(133, "0.18.1.4-8270", 133, "0.18.1.4-8273"))
        assertFalse(isNewerAppUpdate(133, "0.18.1.4-8273", 133, "0.18.1.4-8273"))
        assertFalse(isNewerAppUpdate(133, "0.18.1.4-8273", 133, "0.18.1.4-8270"))
        assertFalse(isNewerAppUpdate(133, "0.18.1.4-8270", 133, "other-8273"))
        assertTrue(isNewerAppUpdate(132, "0.18.1.4-8270", 133, "0.18.1.4-8273"))
        assertFalse(isNewerAppUpdate(133, "0.18.1.4-8270", 132, "0.18.1.4-8273"))
    }
}

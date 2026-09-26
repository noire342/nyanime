package eu.kanade.tachiyomi.ui.entries.anime

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AnimeDetailRefreshGateTest {
    @Test
    fun `opening a cached title checks it once and keeps a successful list fresh briefly`() {
        val id = 901L
        assertTrue(AnimeDetailRefreshGate.tryBegin(id, 1_000L))
        assertFalse(AnimeDetailRefreshGate.tryBegin(id, 1_001L))
        AnimeDetailRefreshGate.finish(id, 2_000L, successful = true)
        assertFalse(AnimeDetailRefreshGate.tryBegin(id, 2_000L + 9 * 60_000L))
        assertTrue(AnimeDetailRefreshGate.tryBegin(id, 2_000L + 10 * 60_000L))
    }

    @Test
    fun `a failed source can be retried after a minute without waiting ten minutes`() {
        val id = 902L
        assertTrue(AnimeDetailRefreshGate.tryBegin(id, 1_000L))
        AnimeDetailRefreshGate.finish(id, 1_001L, successful = false)
        assertFalse(AnimeDetailRefreshGate.tryBegin(id, 60_999L))
        assertTrue(AnimeDetailRefreshGate.tryBegin(id, 61_000L))
        AnimeDetailRefreshGate.finish(id, 61_001L, successful = true)
        assertTrue(AnimeDetailRefreshGate.tryBegin(id, 661_001L))
        AnimeDetailRefreshGate.finish(id, 661_002L, successful = false)
        assertFalse(AnimeDetailRefreshGate.tryBegin(id, 721_000L))
        assertTrue(AnimeDetailRefreshGate.tryBegin(id, 721_001L))
    }

    @Test
    fun `leaving a title before a check finishes permits an immediate new check`() {
        val id = 903L
        assertTrue(AnimeDetailRefreshGate.tryBegin(id, 1_000L))
        AnimeDetailRefreshGate.finish(id, 1_001L, successful = false, cancelled = true)
        assertTrue(AnimeDetailRefreshGate.tryBegin(id, 1_002L))
    }
}

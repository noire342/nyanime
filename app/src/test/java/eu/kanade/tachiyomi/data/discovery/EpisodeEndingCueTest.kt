package eu.kanade.tachiyomi.data.discovery

import eu.kanade.tachiyomi.animesource.model.ChapterType
import eu.kanade.tachiyomi.animesource.model.TimeStamp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EpisodeEndingCueTest {
    private val duration = 1_200_000L
    private val ending = EpisodeEndingCue(duration, 840_000L, 940_000L)

    @Test
    fun `ending only advances near its end`() {
        assertFalse(shouldAdvanceResume(860_000L, duration, ending))
        assertTrue(shouldAdvanceResume(920_000L, duration, ending))
    }

    @Test
    fun `duration fallback uses the shorter of sixty seconds and five percent`() {
        assertFalse(shouldAdvanceResume(1_130_000L, duration, null))
        assertTrue(shouldAdvanceResume(1_145_000L, duration, null))
        assertFalse(shouldAdvanceResume(260_000L, 300_000L, null))
        assertTrue(shouldAdvanceResume(286_000L, 300_000L, null))
    }

    @Test
    fun `invalid duration and mismatched cue do not advance`() {
        assertFalse(shouldAdvanceResume(120_000L, 0L, ending))
        assertFalse(shouldAdvanceResume(920_000L, duration + 60_000L, ending))
        assertFalse(shouldAdvanceResume(duration + 20_000L, duration, ending))
    }

    @Test
    fun `only a validated late ending is retained`() {
        val stamps = listOf(
            TimeStamp(300.0, 360.0, "Earlier ending", ChapterType.Ending),
            TimeStamp(840.0, 940.0, "Ending", ChapterType.Ending),
            TimeStamp(1_000.0, 1_050.0, "Recap", ChapterType.Recap),
        )
        assertEquals(ending, EpisodeEndingCue.from(stamps, 1_200))
        assertNull(EpisodeEndingCue.from(stamps.take(1), 1_200))
        assertNull(EpisodeEndingCue.from(stamps, 30))
    }
}

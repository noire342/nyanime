package eu.kanade.tachiyomi.data.track

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AnimeMangaContinuityTest {
    @Test
    fun `uses only explicit chapter and episode checkpoints`() {
        val start = AnimeMangaContinuity.checkpoint("Vol 1, Chap 1 (Chap 1 adapted in EP 4)")
        val end = AnimeMangaContinuity.checkpoint("Vol 113, Chap 1150 (As of EP 1180)")
        assertEquals(1.0, start?.chapter)
        assertEquals(4, start?.episode)
        assertTrue(start?.exactEpisode == true)
        assertEquals(1150.0, end?.chapter)
        assertEquals(1180, end?.episode)
        assertFalse(end?.exactEpisode == true)
        assertNull(AnimeMangaContinuity.checkpoint("Anime covers the early arc"))
    }

    @Test
    fun `does not guess a chapter for an earlier episode`() {
        val choice = AnimeMangaContinuity.Choice(
            catalogId = 42,
            catalogMalId = null,
            title = "Example",
            coverUrl = null,
            format = "MANGA",
            beginning = AnimeMangaContinuity.Checkpoint(1.0, "Chap 1", null),
            latestAdapted = AnimeMangaContinuity.Checkpoint(30.0, "Chap 30 adapted in EP 12", 12, exactEpisode = true),
            matches = emptyList(),
        )
        assertNull(choice.continuationAfter(11.0))
        assertNull(choice.continuationAfter(null))
        assertEquals(30.0, choice.continuationAfter(12.0))
    }

    @Test
    fun `a conflicting catalog ID prevents automatic association`() {
        assertTrue(AnimeMangaContinuity.hasSameIdentity(10, 20, SourceTrackingHints(anilistId = 10)))
        assertFalse(AnimeMangaContinuity.hasSameIdentity(10, 20, SourceTrackingHints(malId = 20, anilistId = 11)))
        assertFalse(AnimeMangaContinuity.hasSameIdentity(10, 20, SourceTrackingHints(titles = listOf("Same title"))))
    }
}

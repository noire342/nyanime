package eu.kanade.tachiyomi.data.releases

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AiringRefreshQueueTest {
    private val now = 100 * ReleasePolicy.DAY

    @Test fun firstPassVisitsEveryTitleAcrossBatchesAndDoesNotRepeatFailedChecks() {
        var candidates = (1L..31L).map { AiringRefreshQueue.Candidate(it, AiringCache(), false) }
        val visited = mutableListOf<Long>()
        do {
            val batch = AiringRefreshQueue.batch(candidates.reversed(), now)
            assertTrue(batch.size <= AiringRefreshQueue.BATCH_SIZE)
            val selected = batch.map { it.entryId }.toSet()
            visited += selected
            candidates = candidates.map {
                if (it.entryId in selected) {
                    it.copy(cache = AiringCache(attemptedAt = now, status = "UNAVAILABLE"))
                } else {
                    it
                }
            }
            val more = AiringRefreshQueue.hasColdBacklog(candidates, selected)
            assertEquals(visited.size < candidates.size, more)
        } while (more)
        assertEquals((1L..31L).toList(), visited)
        assertTrue(AiringRefreshQueue.batch(candidates, now).isEmpty())
    }

    @Test fun anOldFailureDoesNotBlockNewEntriesAndCannotCreateAnEndlessContinuation() {
        val failed = AiringRefreshQueue.Candidate(
            100,
            AiringCache(attemptedAt = now - ReleasePolicy.HOUR, status = "UNAVAILABLE"),
            false,
        )
        val fresh = AiringRefreshQueue.Candidate(101, AiringCache(now, now, "AVAILABLE"), true)
        val pending = (1L..7L).map { AiringRefreshQueue.Candidate(it, AiringCache(), false) }
        val batch = AiringRefreshQueue.batch(listOf(failed, fresh) + pending, now)
        assertEquals((1L..6L).toList(), batch.map { it.entryId })
        assertTrue(AiringRefreshQueue.hasColdBacklog(pending + failed, batch.map { it.entryId }.toSet()))
        assertFalse(AiringRefreshQueue.hasColdBacklog(listOf(failed, fresh), emptySet()))
    }
}

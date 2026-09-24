package eu.kanade.tachiyomi.ui.tv

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.items.episode.model.Episode

class TvEpisodeRecommendationTest {
    private fun episode(id: Long, seen: Boolean = false) = Episode.create().copy(
        id = id,
        url = "/episode/$id",
        seen = seen,
        sourceOrder = id,
    )

    @Test
    fun `resume wins over later unwatched episode`() {
        val episodes = listOf(episode(1, seen = true), episode(2), episode(3))
        val states = mapOf("/episode/1" to TvEpisodeState(positionMs = 80_000, durationMs = 300_000))
        assertEquals(1L, recommendedTvEpisode(episodes, states, useMainState = false)?.id)
    }

    @Test
    fun `first unwatched episode is offered when no resume point exists`() {
        val episodes = listOf(episode(1, seen = true), episode(2), episode(3))
        assertEquals(2L, recommendedTvEpisode(episodes, emptyMap(), useMainState = true)?.id)
    }

    @Test
    fun `completed progress is not mistaken for a resume point`() {
        val episodes = listOf(episode(1), episode(2))
        val states = mapOf(
            "/episode/1" to TvEpisodeState(
                seen = true,
                positionMs = 300_000,
                durationMs = 300_000,
            ),
        )
        assertEquals(2L, recommendedTvEpisode(episodes, states, useMainState = false)?.id)
    }
}

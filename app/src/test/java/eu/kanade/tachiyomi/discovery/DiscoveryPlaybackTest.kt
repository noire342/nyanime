package eu.kanade.tachiyomi.discovery

import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.data.discovery.DiscoveryPlaybackService
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.asAnimeCover
import tachiyomi.domain.history.anime.interactor.GetAnimeHistory
import tachiyomi.domain.history.anime.interactor.GetNextEpisodes
import tachiyomi.domain.history.anime.model.AnimeHistoryWithRelations
import tachiyomi.domain.history.anime.repository.AnimeHistoryRepository
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.items.episode.model.Episode

class DiscoveryPlaybackTest {
    private val anime = Anime.create().copy(id = 1, title = "Example", source = 2)
    private val first = Episode.create().copy(
        id = 10,
        animeId = 1,
        name = "Episode 1",
        sourceOrder = 1,
        episodeNumber = 1.0,
        lastSecondSeen = 40,
        totalSeconds = 100,
    )
    private val second = Episode.create().copy(
        id = 11,
        animeId = 1,
        name = "Episode 2",
        sourceOrder = 0,
        episodeNumber = 2.0,
    )
    private val history = mockk<GetAnimeHistory>()
    private val getAnime = mockk<GetAnime>()
    private val episodes = mockk<GetEpisodesByAnimeId>()
    private val downloads = mockk<AnimeDownloadManager>()
    private val base = mockk<BasePreferences>()
    private val next = GetNextEpisodes(episodes, getAnime, mockk<AnimeHistoryRepository>())
    private val service = DiscoveryPlaybackService(history, next, downloads, base)

    private fun prepare(seen: Boolean = false) {
        coEvery { getAnime.await(1) } returns anime
        coEvery { episodes.await(1) } returns listOf(first.copy(seen = seen), second)
        every { history.subscribe("") } returns
            flowOf(listOf(AnimeHistoryWithRelations(1, 10, 1, anime.title, 1.0, null, anime.asAnimeCover())))
        every { base.downloadedOnly().get() } returns false
    }

    @Test
    fun `incomplete episode resumes same identity and progress`() = runBlocking {
        prepare()
        val episode = service.nextEpisode(anime)
        assertEquals(first.id, episode?.id)
        assertEquals(40L, episode?.lastSecondSeen)
    }

    @Test
    fun `finished episode advances through existing episode ordering`() = runBlocking {
        prepare(seen = true)
        assertEquals(second.id, service.nextEpisode(anime)?.id)
    }

    @Test
    fun `last episode has nothing to resume`() = runBlocking {
        prepare(seen = true)
        coEvery { episodes.await(1) } returns listOf(first.copy(seen = true))
        assertNull(service.nextEpisode(anime))
    }

    @Test
    fun `download only never opens a remote next episode`() = runBlocking {
        prepare()
        every { base.downloadedOnly().get() } returns true
        every { downloads.isEpisodeDownloaded(any(), any(), any(), any(), any()) } returns false
        assertNull(service.nextEpisode(anime))
        every { downloads.isEpisodeDownloaded(any(), any(), any(), any(), any()) } returns true
        assertEquals(first.id, service.nextEpisode(anime)?.id)
    }
}

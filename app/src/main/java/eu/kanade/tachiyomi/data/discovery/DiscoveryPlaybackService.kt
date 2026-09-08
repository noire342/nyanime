package eu.kanade.tachiyomi.data.discovery

import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.history.anime.interactor.GetAnimeHistory
import tachiyomi.domain.history.anime.interactor.GetNextEpisodes
import tachiyomi.domain.items.episode.model.Episode

class DiscoveryPlaybackService(
    private val history: GetAnimeHistory,
    private val next: GetNextEpisodes,
    private val downloads: AnimeDownloadManager,
    private val base: BasePreferences,
) {
    suspend fun nextEpisode(anime: Anime): Episode? = withContext(Dispatchers.IO) {
        val last = history.subscribe("").first().firstOrNull { it.animeId == anime.id }
        val episode = if (last == null) {
            next.await(anime.id).firstOrNull()
        } else {
            next.await(anime.id, last.episodeId, onlyUnseen = false).firstOrNull()
        }
        episode?.takeIf {
            !base.downloadedOnly().get() ||
                downloads.isEpisodeDownloaded(it.name, it.scanlator, anime.title, anime.source)
        }
    }
}

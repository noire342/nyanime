package eu.kanade.tachiyomi.data.track

import eu.kanade.domain.entries.anime.model.toDomainAnime
import eu.kanade.domain.entries.manga.model.toSManga
import eu.kanade.tachiyomi.animesource.AnimeCatalogIdResolver
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.FetchType
import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.source.MangaSourceUpdateGate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import tachiyomi.domain.entries.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.repository.AnimeRepository
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.track.anime.repository.AnimeTrackRepository

/** Catalog relationships are independent of library membership and tracker authentication. */
class MangaAnimeContinuity(
    private val animeRepository: AnimeRepository,
    private val animeTracks: AnimeTrackRepository,
    private val sourceManager: AnimeSourceManager,
    private val toLocal: NetworkToLocalAnime,
) {
    data class Choice(
        val catalogId: Long,
        val title: String,
        val format: String?,
        val episodes: Int?,
        val year: Int?,
        val coverUrl: String?,
        val viaOriginalNovel: Boolean,
        val matches: List<Anime>,
    )

    sealed interface Result {
        data class Found(val choices: List<Choice>) : Result
        data object NoCatalogId : Result
        data object NoAnimeRelation : Result
        data object Unavailable : Result
    }

    private val hintCache = LinkedHashMap<Pair<Long, String>, Pair<Long, SourceTrackingHints>>(64, 0.75f, true)

    suspend fun resolve(
        manga: Manga,
        source: MangaSource,
        trackedAniListId: Long?,
        trackedMalId: Long?,
    ): Result {
        val hints = try {
            mangaHints(manga, source)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (trackedAniListId == null && trackedMalId == null) throw error
            null
        }
        val mangaId = hints?.anilistId ?: trackedAniListId ?: (hints?.malId ?: trackedMalId)?.let {
            AniListMediaLookup.resolveId(it, AniListMediaLookup.Type.MANGA, malId = true)?.id
        } ?: return Result.NoCatalogId
        val relations = AniListMediaLookup.animeAdaptations(mangaId)
        if (relations.isEmpty()) return Result.NoAnimeRelation

        val known = (animeRepository.getAnimeFavorites() + animeRepository.getWatchedAnimeNotInLibrary())
            .distinctBy { it.id }
        val tracksById = animeTracks.getAnimeTracksAsFlow().first().groupBy { it.animeId }
        sourceManager.isInitialized.first { it }
        val sources = sourceManager.getAll().filterIsInstance<AnimeCatalogIdResolver>()
        val choices = coroutineScope {
            val permits = Semaphore(3)
            relations.map { relation ->
                async(Dispatchers.IO) {
                    permits.withPermit {
                        val matches = known.filter { anime ->
                            val tracks = tracksById[anime.id].orEmpty()
                            val trackedIds = SourceTrackingHints(
                                anilistId = tracks.firstOrNull { it.trackerId == TrackerManager.ANILIST }?.remoteId,
                                malId = tracks.firstOrNull { it.trackerId == 1L }?.remoteId,
                            )
                            sourceManager.get(anime.source) != null &&
                                knownIdentityMatches(
                                    relation.id,
                                    relation.malId,
                                    SourceTrackingHints.from(anime),
                                    trackedIds,
                                )
                        }.toMutableList()
                        if (matches.isEmpty()) {
                            for (resolver in sources) {
                                val animeSource = resolver as? AnimeSource ?: continue
                                val resolved = withTimeoutOrNull(6_000) {
                                    try {
                                        resolver.findAnimeByCatalogId("anilist", relation.id)
                                            ?: relation.malId?.let { resolver.findAnimeByCatalogId("myanimelist", it) }
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (_: Exception) {
                                        null
                                    }
                                } ?: continue
                                val resolvedIds = SourceTrackingHints.from(resolved)
                                val details = if (resolvedIds?.anilistId != null || resolvedIds?.malId != null) {
                                    resolved
                                } else {
                                    withTimeoutOrNull(6_000) {
                                        try {
                                            if (resolved.fetch_type == FetchType.Seasons) {
                                                animeSource.getAnimeSeasonUpdate(
                                                    resolved,
                                                    emptyList(),
                                                    fetchDetails = true,
                                                    fetchSeasons = false,
                                                ).anime
                                            } else {
                                                animeSource.getAnimeEpisodeUpdate(
                                                    resolved,
                                                    emptyList(),
                                                    fetchDetails = true,
                                                    fetchEpisodes = false,
                                                ).anime
                                            }
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (_: Exception) {
                                            null
                                        }
                                    } ?: continue
                                }
                                if (!AnimeMangaContinuity.hasSameIdentity(
                                        relation.id,
                                        relation.malId,
                                        SourceTrackingHints.from(details),
                                    )
                                ) {
                                    continue
                                }
                                details.url = resolved.url
                                matches += toLocal.await(details.toDomainAnime(animeSource.id))
                                break
                            }
                        }
                        Choice(
                            relation.id,
                            relation.title,
                            relation.format,
                            relation.episodes,
                            relation.year,
                            relation.coverUrl,
                            relation.viaOriginalNovel,
                            matches,
                        )
                    }
                }
            }.awaitAll()
        }
        return Result.Found(choices)
    }

    private suspend fun mangaHints(manga: Manga, source: MangaSource): SourceTrackingHints? {
        val key = manga.source to manga.url
        synchronized(hintCache) {
            hintCache[key]?.takeIf { it.first > System.currentTimeMillis() }?.second
        }?.let { return it }
        val hints = withContext(Dispatchers.IO) {
            SourceTrackingHints.from(
                MangaSourceUpdateGate.await(
                    source,
                    manga.toSManga(),
                    emptyList(),
                    fetchDetails = true,
                    fetchChapters = false,
                ).manga,
            )
        } ?: return null
        synchronized(hintCache) {
            hintCache[key] = (System.currentTimeMillis() + 60 * 60 * 1000L) to hints
            if (hintCache.size > 64) hintCache.remove(hintCache.keys.first())
        }
        return hints
    }

    companion object {
        internal fun knownIdentityMatches(
            id: Long,
            malId: Long?,
            sourceHints: SourceTrackingHints?,
            trackedHints: SourceTrackingHints?,
        ): Boolean {
            val hints = listOfNotNull(sourceHints, trackedHints)
            if (hints.any {
                    it.anilistId != null &&
                        it.anilistId != id ||
                        malId != null &&
                        it.malId != null &&
                        it.malId != malId
                }
            ) {
                return false
            }
            return hints.any { AnimeMangaContinuity.hasSameIdentity(id, malId, it) }
        }
    }
}

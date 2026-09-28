package eu.kanade.tachiyomi.data.track

import eu.kanade.domain.entries.manga.model.toDomainManga
import eu.kanade.domain.entries.manga.model.toSManga
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.RelatedMangaLinks
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.MangaCatalogIdResolver
import eu.kanade.tachiyomi.source.MangaCatalogLinkResolver
import eu.kanade.tachiyomi.source.MangaSourceUpdateGate
import eu.kanade.tachiyomi.source.model.SManga
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
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.track.manga.model.MangaTrack
import tachiyomi.domain.track.manga.repository.MangaTrackRepository

/** Verified catalog relationships and source-independent continuation checkpoints. */
class AnimeMangaContinuity(
    private val mangaRepository: MangaRepository,
    private val mangaTracks: MangaTrackRepository,
    private val sourceManager: MangaSourceManager,
    private val toLocal: NetworkToLocalManga,
) {
    data class Checkpoint(
        val chapter: Double,
        val note: String,
        val episode: Int?,
        val exactEpisode: Boolean = false,
        val season: Int? = null,
        val page: Int? = null,
    )

    data class Choice(
        val catalogId: Long,
        val catalogMalId: Long?,
        val title: String,
        val coverUrl: String?,
        val format: String?,
        val viaOriginalNovel: Boolean = false,
        val beginning: Checkpoint?,
        val latestAdapted: Checkpoint?,
        val matches: List<Manga>,
        val season: Int? = null,
    ) {
        /** Never interpolate episode numbers into chapter numbers. */
        fun continuationAfter(watchedEpisode: Double?): Double? {
            val end = latestAdapted ?: return null
            val episode = end.episode ?: return null
            if (!end.exactEpisode ||
                end.season != null ||
                watchedEpisode == null ||
                watchedEpisode != episode.toDouble()
            ) {
                return null
            }
            // Reopen the mapped chapter: adaptations can stop midway through it.
            return end.chapter
        }
    }

    sealed interface Result {
        data class Found(val choices: List<Choice>, val watchedEpisode: Double?) : Result
        data object NoCatalogId : Result
        data object NoMangaRelation : Result
    }

    private data class LinkedManga(val manga: SManga, val source: CatalogueSource, val hints: SourceTrackingHints)

    suspend fun resolve(
        anime: Anime,
        source: AnimeSource,
        trackedAniListId: Long?,
        trackedMalId: Long?,
        watchedEpisode: Double?,
    ): Result {
        val hints = AutoTrackOnStart.animeHints(anime, source)
        val animeId = hints?.anilistId ?: trackedAniListId ?: hints?.malId?.let {
            AniListMediaLookup.resolveId(it, AniListMediaLookup.Type.ANIME, malId = true)?.id
        } ?: trackedMalId?.let {
            AniListMediaLookup.resolveId(it, AniListMediaLookup.Type.ANIME, malId = true)?.id
        } ?: return Result.NoCatalogId

        val adaptation = AniListMediaLookup.mangaAdaptations(animeId)
        val allRelations = adaptation.relations
        val sourceSeason = anime.seasonNumber.takeIf { it in 1.0..99.0 && it % 1.0 == 0.0 }?.toInt()
        val season = when {
            sourceSeason != null &&
                adaptation.context.season != null &&
                sourceSeason != adaptation.context.season -> null
            else -> sourceSeason ?: adaptation.context.season
        }
        val allowUnscoped = adaptation.context.standaloneSeason && season == 1
        val relations = allRelations.filter { it.format == "MANGA" }
            .ifEmpty { allRelations.filter { it.format == "ONE_SHOT" } }
        if (relations.isEmpty()) return Result.NoMangaRelation

        val relatedLinks = (source as? RelatedMangaLinks)?.let { resolver ->
            withTimeoutOrNull(6_000) {
                runCatching { resolver.relatedMangaLinks(anime.url) }.getOrDefault(emptyList())
            }
        }.orEmpty()
        val linkResolvers = sourceManager.getCatalogueSources().filterIsInstance<MangaCatalogLinkResolver>()
        val linkedManga = relatedLinks.take(3).flatMap { link ->
            linkResolvers.mapNotNull { resolver ->
                val manga = withTimeoutOrNull(6_000) {
                    runCatching { resolver.findMangaByCatalogLink(link) }.getOrNull()
                } ?: return@mapNotNull null
                val hints = SourceTrackingHints.from(manga) ?: return@mapNotNull null
                LinkedManga(manga, resolver as CatalogueSource, hints)
            }
        }
        val prioritizedRelations = relations.sortedWith(
            compareByDescending<AniListMediaLookup.MangaRelation> { relation ->
                linkedManga.any { hasSameIdentity(relation.id, relation.malId, it.hints) }
            }.thenByDescending { relation ->
                relation.titles.any { TrackTitleMatcher.normalize(it) == TrackTitleMatcher.normalize(anime.title) }
            }.thenBy { it.id },
        ).take(12)

        val favorites = mangaRepository.getMangaFavorites()
        val tracksByMangaId = mangaTracks.getMangaTracksAsFlow().first().groupBy { it.mangaId }
        val metadata = coroutineScope {
            val permits = Semaphore(3)
            prioritizedRelations.map { relation ->
                async(Dispatchers.IO) {
                    permits.withPermit {
                        AdaptationChapterCatalog.mangaBaka(relation.id)
                    }
                }
            }.awaitAll()
        }
        val choices = prioritizedRelations.mapIndexed { index, relation ->
            val catalogMetadata = metadata[index]
            val matches = findVerifiedManga(relation, favorites, tracksByMangaId, linkedManga)
            val beginning = selectCheckpoint(catalogMetadata.beginning, season, allowUnscoped)
            val ending = selectCheckpoint(catalogMetadata.ending, season, allowUnscoped)
            val (directBeginning, directEnding) = if (beginning == null || ending == null) {
                matches.firstOrNull()?.let { readMangaUpdatesCheckpoint(it, tracksByMangaId[it.id].orEmpty()) }
                    ?: (emptyList<Checkpoint>() to emptyList())
            } else {
                emptyList<Checkpoint>() to emptyList()
            }
            Choice(
                catalogId = relation.id,
                catalogMalId = relation.malId,
                title = relation.titles.firstOrNull().orEmpty(),
                coverUrl = catalogMetadata.coverUrl,
                format = relation.format,
                viaOriginalNovel = relation.viaOriginalNovel,
                beginning = beginning ?: selectCheckpoint(directBeginning, season, allowUnscoped),
                latestAdapted = ending ?: selectCheckpoint(directEnding, season, allowUnscoped),
                matches = matches,
                season = season,
            )
        }
        // A documented reference for this season takes precedence over other novel arcs.
        return Result.Found(
            choices.sortedByDescending { choice ->
                listOfNotNull(choice.beginning, choice.latestAdapted).count { season != null && it.season == season }
            },
            watchedEpisode,
        )
    }

    private suspend fun findVerifiedManga(
        relation: AniListMediaLookup.MangaRelation,
        favorites: List<Manga>,
        tracksByMangaId: Map<Long, List<MangaTrack>>,
        linkedManga: List<LinkedManga>,
    ): List<Manga> = withContext(Dispatchers.IO) {
        val verified = mutableListOf<Manga>()
        for (manga in favorites) {
            val tracks = tracksByMangaId[manga.id].orEmpty()
            val conflicts = tracks.any {
                (it.trackerId == TrackerManager.ANILIST && it.remoteId != relation.id) ||
                    (it.trackerId == 1L && relation.malId != null && it.remoteId != relation.malId)
            }
            if (!conflicts &&
                tracks.any {
                    (it.trackerId == TrackerManager.ANILIST && it.remoteId == relation.id) ||
                        (it.trackerId == 1L && it.remoteId == relation.malId)
                }
            ) {
                verified += manga
            }
        }
        // A direct link still needs an independent catalog ID match before it can open automatically.
        for (linked in linkedManga) {
            if (!hasSameIdentity(relation.id, relation.malId, linked.hints)) continue
            val local = toLocal.await(linked.manga.toDomainManga(linked.source.id))
            if (verified.none { it.id == local.id }) verified += local
        }
        if (verified.isEmpty()) {
            // Titles only narrow which existing entries need an ID check; they never establish identity.
            val names = relation.titles.map(TrackTitleMatcher::normalize).toSet()
            val candidates = favorites.filter { TrackTitleMatcher.normalize(it.title) in names }.take(3)
            for (manga in candidates) {
                val source = sourceManager.get(manga.source) ?: continue
                val hints = withTimeoutOrNull(6_000) {
                    runCatching {
                        val details = MangaSourceUpdateGate.await(
                            source,
                            manga.toSManga(),
                            emptyList(),
                            fetchDetails = true,
                            fetchChapters = false,
                        ).manga
                        SourceTrackingHints.from(details)
                    }.getOrNull()
                }
                if (hasSameIdentity(relation.id, relation.malId, hints)) verified += manga
            }
        }
        // Sources with real ID lookup can resolve titles previously indexed in the extension.
        for (source in sourceManager.getCatalogueSources().filterIsInstance<MangaCatalogIdResolver>()) {
            if (verified.isNotEmpty()) break
            val mangaSource = source as CatalogueSource
            val remote = withTimeoutOrNull(6_000) {
                runCatching { source.findMangaByCatalogId("anilist", relation.id) }.getOrNull()
            } ?: continue
            val details = withTimeoutOrNull(6_000) {
                runCatching { mangaSource.getMangaDetails(remote) }.getOrNull()
            } ?: continue
            if (!hasSameIdentity(relation.id, relation.malId, SourceTrackingHints.from(details))) continue
            val local = toLocal.await(details.toDomainManga(mangaSource.id))
            if (verified.none { it.id == local.id }) verified += local
        }
        verified
    }

    private suspend fun readMangaUpdatesCheckpoint(
        manga: Manga,
        tracks: List<MangaTrack>,
    ): Pair<List<Checkpoint>, List<Checkpoint>> {
        val trackedId = tracks
            .firstOrNull { it.trackerId == 7L && it.remoteId > 0 }?.remoteId
        val id = trackedId ?: withTimeoutOrNull(6_000) {
            val source = sourceManager.get(manga.source) ?: return@withTimeoutOrNull null
            val details = MangaSourceUpdateGate.await(
                source,
                manga.toSManga(),
                emptyList(),
                fetchDetails = true,
                fetchChapters = false,
            ).manga
            SourceTrackingHints.from(details)?.mangaUpdatesId
        } ?: return emptyList<Checkpoint>() to emptyList()
        val metadata = AdaptationChapterCatalog.mangaUpdates(id)
        return metadata.beginning to metadata.ending
    }

    companion object {
        private val chapterPattern = Regex("(?i)\\b(?:chap(?:ter)?|ch\\.?)[ .:#]*(\\d+(?:\\.\\d+)?)\\b")
        private val episodePattern = Regex("(?i)\\b(?:ep(?:isode)?)[ .:#]*(\\d+)\\b")
        private val pagePattern = Regex("(?i)\\bpage[ .:#]*(\\d+)\\b")
        private val seasonEpisodePattern =
            Regex("(?i)\\bS(?:eason)?[ .:#]*(\\d+)[ .:-]*E(?:p(?:isode)?)?[ .:#]*(\\d+)\\b")
        private val seasonPattern = Regex("(?i)\\b(?:s(?:eason)?|stagione)[ .:#]*(\\d{1,2})\\b")
        private val exactEpisodePattern =
            Regex("(?i)\\b(?:adapted|covered|animated)\\s+in\\s+(?:ep(?:isode)?)[ .:#]*\\d+\\b")

        internal fun checkpoint(raw: String?): Checkpoint? = checkpoints(raw).singleOrNull()

        internal fun checkpoints(raw: String?): List<Checkpoint> {
            val text = raw?.trim()?.takeIf { it.isNotEmpty() && it.length <= 4096 } ?: return emptyList()
            return text.replace(Regex("(?i)<br\\s*/?>"), "\n").split(Regex("[\\r\\n;]+"))
                .flatMap { line ->
                    val references = chapterPattern.findAll(line).toList()
                    // Repeated chapter mentions in a note belong to the same reference.
                    val groups = references.fold(mutableListOf<MutableList<MatchResult>>()) { groups, reference ->
                        if (groups.lastOrNull()?.last()?.groupValues?.get(1) == reference.groupValues[1]) {
                            groups.last().add(reference)
                        } else {
                            groups.add(mutableListOf(reference))
                        }
                        groups
                    }
                    groups.mapIndexedNotNull { index, group ->
                        val chapter = group.first().groupValues[1].toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
                            ?: return@mapIndexedNotNull null
                        val start = if (index == 0) 0 else group.first().range.first
                        val end = groups.getOrNull(index + 1)?.first()?.range?.first ?: line.length
                        val note = line.substring(start, end).trim().takeIf { it.length <= 512 }
                            ?: return@mapIndexedNotNull null
                        val seasons = (seasonPattern.findAll(note) + seasonEpisodePattern.findAll(note)).mapNotNull {
                            it.groupValues[1].toIntOrNull()?.takeIf { number -> number > 0 }
                        }.distinct().toList()
                        // Ambiguous prose is not turned into a season/chapter assignment.
                        if (seasons.size > 1) return@mapIndexedNotNull null
                        val seasonEpisode = seasonEpisodePattern.find(note)
                        val episode = seasonEpisode?.groupValues?.get(2)?.toIntOrNull()
                            ?: episodePattern.find(note)?.groupValues?.get(1)?.toIntOrNull()
                        Checkpoint(
                            chapter,
                            note,
                            episode,
                            exactEpisodePattern.containsMatchIn(note),
                            seasons.singleOrNull(),
                            pagePattern.find(note)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it > 0 },
                        )
                    }
                }.distinctBy { listOf(it.chapter, it.season, it.episode, it.exactEpisode, it.page) }
        }

        internal fun selectCheckpoint(points: List<Checkpoint>, season: Int?, allowUnscoped: Boolean): Checkpoint? {
            if (season == null) return points.filter { it.season == null }.singleOrNull()
            val scoped = points.filter { it.season == season }
            if (scoped.isNotEmpty()) return scoped.singleOrNull()
            // A series-wide endpoint must not be labelled as the endpoint of a sequel.
            return if (allowUnscoped && points.none { it.season != null }) points.singleOrNull() else null
        }

        internal fun hasSameIdentity(anilistId: Long, malId: Long?, hints: SourceTrackingHints?): Boolean {
            if (hints == null) return false
            if (hints.anilistId != null && hints.anilistId != anilistId) return false
            if (malId != null && hints.malId != null && hints.malId != malId) return false
            return hints.anilistId == anilistId || (malId != null && hints.malId == malId)
        }
    }
}

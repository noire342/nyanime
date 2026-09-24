package eu.kanade.tachiyomi.ui.tv

import eu.kanade.domain.entries.anime.interactor.UpdateAnime
import eu.kanade.tachiyomi.data.discovery.ExtensionHomeRegistry
import eu.kanade.tachiyomi.data.discovery.ExtensionHomeServices
import eu.kanade.tachiyomi.data.discovery.LocalHomeItem
import eu.kanade.tachiyomi.data.discovery.LocalHomeSections
import eu.kanade.tachiyomi.ui.discovery.SourceHomeFeedLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mihon.domain.source.interactor.UpdateAnimeFromRemote
import tachiyomi.domain.discovery.SectionState
import tachiyomi.domain.discovery.SourceHomeGroup
import tachiyomi.domain.discovery.SourceHomeGroupAccess
import tachiyomi.domain.discovery.SourceHomePage
import tachiyomi.domain.discovery.SourceHomeRequest
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.repository.AnimeRepository
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.items.episode.repository.EpisodeRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data class TvCatalogState(
    val loading: Boolean = true,
    val homes: List<SourceHomeGroup> = emptyList(),
    val selectedHome: String? = null,
    val access: SourceHomeGroupAccess = SourceHomeGroupAccess(loading = true),
    val sections: Map<String, SectionState<SourceHomePage>> = emptyMap(),
    val resume: SectionState<List<LocalHomeItem>> = SectionState(),
    val personalResume: List<TvContinueItem> = emptyList(),
    val savedTitles: List<TvSavedAnime> = emptyList(),
    val detail: Anime? = null,
    val detailEpisodes: List<Episode> = emptyList(),
    val detailSeasons: List<Anime> = emptyList(),
    val detailLoading: Boolean = false,
    val detailError: String? = null,
    val detailProfileStates: Map<String, TvEpisodeState> = emptyMap(),
    val detailFavorite: Boolean = false,
    val detailCategory: String = "",
    val detailUsesMainState: Boolean = true,
)

data class TvSavedAnime(val anime: Anime, val category: String)

/** TV-specific orchestration over the same extension-neutral catalogue used by the phone. */
class TvCatalogController(
    private val scope: CoroutineScope,
    private val profiles: TvProfileRepository,
    private val registry: ExtensionHomeRegistry = Injekt.get(),
    private val services: ExtensionHomeServices = Injekt.get(),
    private val local: LocalHomeSections = Injekt.get(),
    private val animeRepository: AnimeRepository = Injekt.get(),
    private val episodes: EpisodeRepository = Injekt.get(),
    private val updater: UpdateAnimeFromRemote = Injekt.get(),
    private val updateAnime: UpdateAnime = Injekt.get(),
) {
    private val mutable = MutableStateFlow(TvCatalogState())
    val state: StateFlow<TvCatalogState> = mutable
    private val feeds = SourceHomeFeedLoader(scope, services.merged) { mutable.value.access }
    private var accessJob: Job? = null
    private var resumeJob: Job? = null
    private var savedJob: Job? = null
    private var detailJob: Job? = null
    private var profileId = TvProfile.MAIN_ID

    init {
        scope.launch {
            registry.observe().collect { listing ->
                val homes = listing.groups
                val previous = mutable.value.selectedHome
                val selected = previous?.takeIf { id -> homes.any { it.id == id } }
                    ?: homes.firstOrNull { it.primary }?.id
                    ?: homes.firstOrNull()?.id
                mutable.update { it.copy(loading = listing.loading, homes = homes, selectedHome = selected) }
                if (selected != previous) selectHome(selected)
            }
        }
        scope.launch { feeds.sections.collect { sections -> mutable.update { it.copy(sections = sections) } } }
    }

    fun selectProfile(id: String) {
        if (profileId == id) return
        profileId = id
        restartResume()
        restartSaved()
    }

    fun selectHome(id: String?) {
        if (id == null) return
        accessJob?.cancel()
        resumeJob?.cancel()
        savedJob?.cancel()
        feeds.reset()
        mutable.update {
            it.copy(
                selectedHome = id,
                access = SourceHomeGroupAccess(loading = true),
                sections = emptyMap(),
                resume = SectionState(),
                personalResume = emptyList(),
                savedTitles = emptyList(),
            )
        }
        accessJob = scope.launch {
            registry.observeGroup(id).collectLatest { access ->
                mutable.update { it.copy(access = access) }
                restartResume()
                restartSaved()
            }
        }
    }

    fun load(sectionId: String, date: String? = null, refresh: Boolean = false) {
        feeds.load(SourceHomeRequest(sectionId, date = date), refresh)
    }

    fun search(query: String, filters: Map<String, List<String>> = emptyMap()) {
        feeds.load(SourceHomeRequest(SourceHomeRequest.SEARCH, query = query, filters = filters, browse = true))
    }

    fun revalidate() {
        feeds.refresh(force = false)
        restartResume()
        restartSaved()
    }

    fun open(anime: Anime) {
        detailJob?.cancel()
        mutable.update {
            it.copy(
                detail = anime, detailEpisodes = emptyList(), detailSeasons = emptyList(),
                detailLoading = true, detailError = null, detailProfileStates = emptyMap(),
                detailFavorite = if (profileId == TvProfile.MAIN_ID) anime.favorite else false,
                detailCategory = "",
                detailUsesMainState = profileId == TvProfile.MAIN_ID,
            )
        }
        detailJob = scope.launch {
            try {
                val current = animeRepository.getAnimeById(anime.id)
                val existing = episodes.getEpisodeByAnimeId(current.id)
                val seasons = animeRepository.getAnimeSeasonsById(current.id).map { it.anime }
                val states = if (profileId != TvProfile.MAIN_ID && profileId != ANONYMOUS_ID) {
                    profiles.episodeStates(profileId, current.source, current.url)
                } else {
                    emptyMap()
                }
                val favorite = if (profileId != TvProfile.MAIN_ID && profileId != ANONYMOUS_ID) {
                    profiles.favorite(profileId, current.source, current.url)
                } else {
                    current.favorite
                }
                val category = if (profileId != TvProfile.MAIN_ID && profileId != ANONYMOUS_ID) {
                    profiles.category(profileId, current.source, current.url)
                } else {
                    ""
                }
                mutable.update {
                    it.copy(
                        detail = current,
                        detailEpisodes = existing.sortedBy { ep -> ep.sourceOrder },
                        detailSeasons = seasons,
                        detailLoading = existing.isEmpty() && seasons.isEmpty(),
                        detailProfileStates = states,
                        detailFavorite = favorite,
                        detailCategory = category,
                    )
                }
                if (existing.isEmpty() && seasons.isEmpty()) {
                    if (current.fetchType.name == "Seasons") {
                        updater.awaitSeasonsUpdate(current, fetchDetails = !current.initialized, fetchSeasons = true)
                            .getOrThrow()
                    } else {
                        updater.awaitEpisodesUpdate(current, fetchDetails = !current.initialized, fetchEpisodes = true)
                            .getOrThrow()
                    }
                }
                val fresh = animeRepository.getAnimeById(current.id)
                mutable.update {
                    it.copy(
                        detail = fresh,
                        detailEpisodes = episodes.getEpisodeByAnimeId(current.id).sortedBy { ep -> ep.sourceOrder },
                        detailSeasons = animeRepository.getAnimeSeasonsById(current.id).map { season -> season.anime },
                        detailLoading = false,
                        detailProfileStates = states,
                        detailFavorite = favorite,
                        detailCategory = category,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update {
                    it.copy(
                        detailLoading = false,
                        detailError =
                        e.message ?: "Impossibile caricare gli episodi",
                    )
                }
            }
        }
    }

    fun toggleFavorite() {
        val anime = mutable.value.detail ?: return
        val id = profileId
        if (id == ANONYMOUS_ID) return
        val next = !mutable.value.detailFavorite
        scope.launch {
            if (id == TvProfile.MAIN_ID) {
                updateAnime.awaitUpdateFavorite(anime.id, next)
            } else {
                profiles.setFavorite(id, anime.source, anime.url, anime.title, next)
            }
            mutable.update { it.copy(detailFavorite = next) }
            restartSaved()
        }
    }

    fun setDetailCategory(category: String) {
        val anime = mutable.value.detail ?: return
        val id = profileId
        if (id == TvProfile.MAIN_ID || id == ANONYMOUS_ID) return
        scope.launch {
            profiles.setCategory(id, anime.source, anime.url, anime.title, category)
            mutable.update { it.copy(detailFavorite = true, detailCategory = category) }
            restartSaved()
        }
    }

    fun closeDetail() {
        detailJob?.cancel()
        mutable.update { it.copy(detail = null, detailEpisodes = emptyList(), detailSeasons = emptyList()) }
    }

    private fun restartResume() {
        resumeJob?.cancel()
        val group = mutable.value.access.group ?: return
        val id = profileId
        resumeJob = scope.launch {
            if (id == TvProfile.MAIN_ID) {
                local.resume(group.sourceIds).observe().collect { value ->
                    mutable.update { it.copy(resume = value, personalResume = emptyList()) }
                }
            } else if (id != ANONYMOUS_ID) {
                mutable.update {
                    it.copy(
                        resume = SectionState(),
                        personalResume = profiles.continueWatching(id, group.sourceIds),
                    )
                }
            } else {
                mutable.update { it.copy(resume = SectionState(), personalResume = emptyList()) }
            }
        }
    }

    private fun restartSaved() {
        savedJob?.cancel()
        val group = mutable.value.access.group ?: return
        val id = profileId
        savedJob = scope.launch {
            try {
                val items = when (id) {
                    ANONYMOUS_ID -> emptyList()
                    TvProfile.MAIN_ID -> animeRepository.getAnimeFavorites()
                        .filter { it.source in group.sourceIds }.take(60)
                        .map { TvSavedAnime(it, "") }
                    else -> profiles.savedTitles(id, group.sourceIds).mapNotNull { item ->
                        animeRepository.getAnimeByUrlAndSourceId(item.titleUrl, item.source)
                            ?.let { TvSavedAnime(it, item.category) }
                    }
                }
                if (id == profileId && group.id == mutable.value.access.group?.id) {
                    mutable.update { it.copy(savedTitles = items) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                mutable.update { it.copy(savedTitles = emptyList()) }
            }
        }
    }

    companion object {
        const val ANONYMOUS_ID = "anonymous"
    }
}

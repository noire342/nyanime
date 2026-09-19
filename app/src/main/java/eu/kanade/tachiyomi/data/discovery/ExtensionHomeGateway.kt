package eu.kanade.tachiyomi.data.discovery

import eu.kanade.domain.entries.anime.model.toDomainAnime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import tachiyomi.domain.discovery.SourceHomeAccess
import tachiyomi.domain.discovery.SourceHomeGateway
import tachiyomi.domain.discovery.SourceHomePage
import tachiyomi.domain.discovery.SourceHomePresentation
import tachiyomi.domain.discovery.SourceHomeRequest
import tachiyomi.domain.discovery.homeItemKey
import tachiyomi.domain.entries.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import java.io.IOException

class ExtensionHomeGateway(
    private val homeKey: String,
    private val registry: ExtensionHomeRegistry,
    private val manager: AnimeSourceManager,
    private val toLocal: NetworkToLocalAnime,
    private val requests: Semaphore,
) : SourceHomeGateway {
    override fun observeAccess() = registry.observeAccess(homeKey)
    override fun currentAccess() = registry.access(homeKey)

    override suspend fun fetch(access: SourceHomeAccess, request: SourceHomeRequest) = withContext(Dispatchers.IO) {
        requests.withPermit {
            check(!access.offline && access == currentAccess()) { "Fonte non disponibile" }
            val definition = requireNotNull(access.source)
            val source = manager.get(definition.id) ?: error("Fonte non disponibile")
            val section = if (request.sectionId == SourceHomeRequest.SEARCH) {
                requireNotNull(definition.search) { "Ricerca non supportata" }
            } else {
                (definition.sections + definition.categories).firstOrNull { it.id == request.sectionId }
                    ?: error("La sezione non è più supportata: aggiorna la Home")
            }
            val requestedSection = if (request.browse && section.moreSelections != null) {
                section.copy(selections = requireNotNull(section.moreSelections))
            } else {
                section
            }
            val filters = ExtensionHomeFilters.apply(source.getFilterList(), requestedSection, request.date)
            require(request.filters.isEmpty() || request.sectionId == SourceHomeRequest.SEARCH)
            ExtensionHomeFilters.applyBrowse(filters, definition.browseFilters, section.browseValues + request.filters)
            try {
                withTimeout(30_000) {
                    val page = source.getSearchAnime(request.page, request.query.trim(), filters)
                    check(access == currentAccess()) { "La fonte è stata disabilitata" }
                    SourceHomePage(
                        page.animes.map { remote ->
                            val presentation = SourceHomePresentation.from(remote.memo)
                            val incoming = remote.toDomainAnime(source.id)
                            val local = toLocal.await(
                                incoming.copy(memo = SourceHomePresentation.without(incoming.memo)),
                            )
                            // Home artwork is presentation data: never rewrite library flags or progress.
                            local.copy(
                                thumbnailUrl = remote.thumbnail_url?.takeIf(String::isNotBlank) ?: local.thumbnailUrl,
                                backgroundUrl = remote.background_url?.takeIf(
                                    String::isNotBlank,
                                ) ?: local.backgroundUrl,
                                description = remote.description ?: local.description,
                                memo = presentation?.attachTo(local.memo) ?: SourceHomePresentation.without(local.memo),
                            )
                        }.distinctBy { it.homeItemKey },
                        page.hasNextPage,
                        page.animes.mapNotNull { SourceHomePresentation.from(it.memo)?.sectionTitle }
                            .distinct().singleOrNull(),
                    )
                }
            } catch (e: TimeoutCancellationException) {
                throw IOException("La fonte non ha risposto in tempo. Riprova.", e)
            }
        }
    }
}

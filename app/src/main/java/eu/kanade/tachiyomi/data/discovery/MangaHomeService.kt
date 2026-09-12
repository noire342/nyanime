package eu.kanade.tachiyomi.data.discovery

import eu.kanade.domain.entries.manga.model.toDomainManga
import eu.kanade.tachiyomi.source.CatalogueSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import tachiyomi.domain.discovery.SourceHomeRequest
import tachiyomi.domain.entries.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.source.manga.service.MangaSourceManager
import java.io.IOException

class MangaHomeService(
    private val registry: MangaHomeRegistry,
    private val manager: MangaSourceManager,
    private val toLocal: NetworkToLocalManga,
) {
    private val requests = Semaphore(2)

    suspend fun fetch(key: String, request: SourceHomeRequest): MangaHomePage = withContext(Dispatchers.IO) {
        requests.withPermit {
            val access = registry.access(key)
            check(!access.offline) { "La Home online non è disponibile in modalità Solo scaricati" }
            val definition = requireNotNull(access.source) { "Fonte non disponibile" }
            val source = requireNotNull(manager.get(definition.id) as? CatalogueSource)
            val section = if (request.sectionId == SourceHomeRequest.SEARCH) {
                requireNotNull(definition.search)
            } else {
                (definition.sections + definition.categories).firstOrNull { it.id == request.sectionId }
                    ?: error("Sezione non disponibile: aggiorna la Home")
            }
            val filters = MangaHomeFilters.apply(source.getFilterList(), section, request.date)
            try {
                withTimeout(30_000) {
                    val result = source.getSearchManga(request.page, request.query.trim(), filters)
                    check(access == registry.access(key)) { "La fonte è stata disabilitata" }
                    val items = result.mangas.take(100).map { remote ->
                        val presentation = MangaHomePresentation.from(remote)
                        val local = toLocal.await(remote.toDomainManga(source.id))
                        MangaHomeItem(
                            local.copy(
                                thumbnailUrl =
                                remote.thumbnail_url?.takeIf(String::isNotBlank) ?: local.thumbnailUrl,
                            ),
                            presentation,
                        )
                    }.distinctBy(MangaHomeItem::key)
                    check(access == registry.access(key)) { "La fonte è stata disabilitata" }
                    MangaHomePage(items, result.hasNextPage)
                }
            } catch (e: TimeoutCancellationException) {
                throw IOException("La fonte non ha risposto in tempo. Riprova.", e)
            }
        }
    }
}

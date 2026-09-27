package eu.kanade.tachiyomi.data.discovery

import eu.kanade.domain.entries.manga.model.toDomainManga
import eu.kanade.domain.entries.manga.model.toSManga
import eu.kanade.tachiyomi.data.track.SourceTrackingHints
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.MangaSourceUpdateGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import tachiyomi.domain.discovery.SourceHomeRequest
import tachiyomi.domain.entries.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.source.manga.service.MangaSourceManager
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

class MangaHomeService(
    private val registry: MangaHomeRegistry,
    private val manager: MangaSourceManager,
    private val toLocal: NetworkToLocalManga,
) {
    private val requests = Semaphore(2)
    private val interactiveRequests = Semaphore(2)
    private val identityRequests = Semaphore(2)
    private val identityCache = ConcurrentHashMap<String, Map<String, Long>>()

    suspend fun enrichIdentity(item: MangaHomeItem): MangaHomeItem {
        if (item.presentation?.catalogIds?.isNotEmpty() == true) return item
        val key = "${item.manga.source}:${item.manga.url}"
        val ids = identityCache[key] ?: identityRequests.withPermit {
            identityCache[key] ?: withTimeoutOrNull(6_000) {
                val source = manager.get(item.manga.source) ?: return@withTimeoutOrNull emptyMap()
                val details = MangaSourceUpdateGate.await(
                    source,
                    item.manga.toSManga(),
                    emptyList(),
                    fetchDetails = true,
                    fetchChapters = false,
                ).manga
                SourceTrackingHints.from(details).catalogIds()
            }.orEmpty().also { found ->
                if (identityCache.size > 512) identityCache.clear()
                if (found.isNotEmpty()) identityCache[key] = found
            }
        }
        return if (ids.isEmpty()) {
            item
        } else {
            item.copy(
                presentation = (item.presentation ?: MangaHomePresentation()).copy(catalogIds = ids),
            )
        }
    }

    private fun SourceTrackingHints?.catalogIds(): Map<String, Long> = buildMap {
        this@catalogIds?.anilistId?.let { put("anilist", it) }
        this@catalogIds?.malId?.let { put("myanimelist", it) }
        this@catalogIds?.mangaUpdatesId?.let { put("mangaupdates", it) }
    }

    suspend fun fetch(key: String, request: SourceHomeRequest): MangaHomePage = withContext(Dispatchers.IO) {
        val permits = if (request.sectionId == SourceHomeRequest.SEARCH || request.sectionId.startsWith("category:")) {
            interactiveRequests
        } else {
            requests
        }
        permits.withPermit {
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
                        val home = MangaHomePresentation.from(remote)
                        val catalogIds = SourceTrackingHints.from(remote).catalogIds()
                        val presentation = if (catalogIds.isEmpty()) {
                            home
                        } else {
                            (home ?: MangaHomePresentation()).copy(catalogIds = home?.catalogIds.orEmpty() + catalogIds)
                        }
                        val local = toLocal.await(remote.toDomainManga(source.id))
                        MangaHomeItem(
                            local.copy(
                                thumbnailUrl =
                                remote.thumbnail_url?.takeIf(String::isNotBlank) ?: local.thumbnailUrl,
                            ),
                            presentation,
                            remote.title,
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

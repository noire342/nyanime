package tachiyomi.data.discovery

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tachiyomi.domain.discovery.SectionState
import tachiyomi.domain.discovery.SourceHomeAccess
import tachiyomi.domain.discovery.SourceHomeGateway
import tachiyomi.domain.discovery.SourceHomePage
import tachiyomi.domain.discovery.SourceHomeRepository
import tachiyomi.domain.discovery.SourceHomeRequest
import java.time.Clock

/** Bounded, process-only cache: no browsing queries or extension data are written to disk. */
class CachedSourceHomeRepository(
    private val gateway: SourceHomeGateway,
    private val clock: Clock = Clock.systemUTC(),
    private val capacity: Int = 40,
) : SourceHomeRepository {
    init {
        require(capacity > 0)
    }

    private data class Key(val source: Long, val revision: String, val request: SourceHomeRequest)
    private data class Entry(val page: SourceHomePage, val at: Long)
    private val entries = LinkedHashMap<Key, Entry>()
    private val cacheLock = Mutex()
    private val requests = List(16) { Mutex() }

    override fun observe(access: SourceHomeAccess, request: SourceHomeRequest, refresh: Boolean) = flow {
        val source = access.source
        if (access.isPrivate) cacheLock.withLock { entries.clear() }
        if (source == null || access.offline || access != gateway.currentAccess()) {
            emit(SectionState<SourceHomePage>(loading = false, error = "Fonte non disponibile in questa modalità"))
            return@flow
        }
        val key = Key(source.id, source.revision, request.copy(query = request.query.trim()))
        val cached = cacheLock.withLock { entries[key].takeUnless { access.isPrivate } }
        val fresh = cached != null && clock.millis() - cached.at in 0 until TTL
        emit(SectionState(cached?.page, loading = refresh || !fresh, stale = cached != null && !fresh))
        if (fresh && !refresh) return@flow
        try {
            val page = requests[(key.hashCode() and Int.MAX_VALUE) % requests.size].withLock {
                check(access == gateway.currentAccess()) { "La fonte è stata disabilitata o aggiornata" }
                val updated = cacheLock.withLock { entries[key].takeUnless { access.isPrivate } }
                // Concurrent collectors share a refresh completed after their initial cache read.
                if (updated != null && updated !== cached && clock.millis() - updated.at in 0 until TTL) {
                    updated.page
                } else {
                    gateway.fetch(access, key.request).also { result ->
                        check(access == gateway.currentAccess()) { "La fonte non è più disponibile" }
                        if (!access.isPrivate) {
                            cacheLock.withLock {
                                entries.remove(key)
                                entries[key] = Entry(result, clock.millis())
                                while (entries.size > capacity) entries.remove(entries.keys.first())
                            }
                        }
                    }
                }
            }
            emit(SectionState(page, loading = false))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val stillAllowed = access == gateway.currentAccess()
            emit(
                SectionState(
                    cached?.page.takeIf { stillAllowed },
                    loading = false,
                    stale = cached != null && stillAllowed,
                    error = e.message ?: "Impossibile caricare TestSource",
                ),
            )
        }
    }.flowOn(Dispatchers.IO)

    companion object {
        const val TTL = 30 * 60_000L
    }
}

package tachiyomi.domain.search

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface SearchCandidateProvider {
    suspend fun candidates(query: String, medium: SearchMedium, online: Boolean): List<SearchTitle>
    suspend fun remember(items: List<SearchTitle>)
}

class SearchCandidateFailure(val local: List<SearchTitle>, cause: Exception) : java.io.IOException(cause)

data class SearchAssistance(
    val query: String = "",
    val suggestions: List<SearchTitle> = emptyList(),
    val correctedQuery: String? = null,
    val loading: Boolean = false,
    val unavailable: Boolean = false,
    val exact: Boolean = false,
    val enabled: Boolean = false,
)

data class SearchPage<T>(val items: List<T>, val hasNextPage: Boolean)

interface ExtensionSearchAdapter<T> {
    val key: String
    fun identity(item: T): String
    fun title(item: T): SearchTitle
    suspend fun fetch(page: Int, query: String): SearchPage<T>
}

interface TitleSearch {
    fun session(query: String, medium: SearchMedium, exact: Boolean = false, sourceId: Long? = null): SearchSession
}

/** One session per user query, shared by all sources. Each source/query owns its pagination. */
class SearchSession(
    val query: String,
    private val medium: SearchMedium,
    private val provider: SearchCandidateProvider,
    private val matcher: TitleMatcher,
    private val enabled: Boolean = true,
    private val online: Boolean = true,
    private val exact: Boolean = false,
    private val allowNetwork: Boolean = true,
) {
    private val mutableAssistance = MutableStateFlow(
        SearchAssistance(query = query, exact = exact, enabled = enabled, unavailable = !allowNetwork),
    )
    val assistance = mutableAssistance.asStateFlow()
    private val candidateLock = Mutex()
    private var candidateSnapshot: List<SearchTitle>? = null
    private val sourceLocks = mutableMapOf<String, Mutex>()
    private data class Cursor<T>(
        val query: String,
        var nextPage: Int,
        var more: Boolean,
        var pending: SearchPage<T>? = null,
    )
    private val cursors = mutableMapOf<String, MutableList<Cursor<*>>>()
    private val displayed = mutableMapOf<String, MutableSet<String>>()
    private val extraRequests = mutableMapOf<String, SearchRequestBudget>()

    suspend fun suggestions(): List<SearchTitle> = candidateLock.withLock {
        candidateSnapshot?.let { return@withLock it }
        if (!canAssist()) return@withLock emptyList()
        mutableAssistance.value = mutableAssistance.value.copy(loading = true)
        try {
            val found = matcher.rank(query, provider.candidates(query, medium, online)).take(5).map { it.item }
            candidateSnapshot = found
            mutableAssistance.value = mutableAssistance.value.copy(suggestions = found, loading = false)
            found
        } catch (failure: SearchCandidateFailure) {
            val found = matcher.rank(query, failure.local).take(5).map { it.item }
            candidateSnapshot = found
            mutableAssistance.value = mutableAssistance.value.copy(
                suggestions = found,
                loading = false,
                unavailable = true,
            )
            found
        } catch (cancelled: CancellationException) {
            mutableAssistance.value = mutableAssistance.value.copy(loading = false)
            throw cancelled
        } catch (_: Exception) {
            candidateSnapshot = emptyList()
            mutableAssistance.value = mutableAssistance.value.copy(loading = false, unavailable = true)
            emptyList()
        }
    }

    suspend fun <T> search(adapter: ExtensionSearchAdapter<T>, page: Int): SearchPage<T> {
        val lock = synchronized(sourceLocks) { sourceLocks.getOrPut(adapter.key) { Mutex() } }
        return lock.withLock {
            if (!allowNetwork) return@withLock SearchPage(emptyList(), false)
            if (page == 1) firstPage(adapter) else nextPage(adapter)
        }
    }

    private suspend fun <T> firstPage(adapter: ExtensionSearchAdapter<T>): SearchPage<T> {
        // A failed original request stays an error; it must not trigger spelling retries.
        val original = adapter.fetch(1, query)
        remember(original.items.map(adapter::title))
        val branches = mutableListOf(Cursor<T>(query, 2, original.hasNextPage))
        val merged = original.items.toMutableList()
        if (canAssist() && original.items.none { matcher.score(query, adapter.title(it).title) >= 92 }) {
            val candidates = suggestions()
            val ranked = matcher.rank(query, candidates)
            val first = ranked.firstOrNull()
            val runnerUp = ranked.firstOrNull { candidate ->
                candidate.item.names.none { name ->
                    first?.item?.names?.any { TitleNormalizer.compact(it) == TitleNormalizer.compact(name) } == true
                }
            }
            val confident =
                first != null && first.score >= 85 && (runnerUp == null || first.score - runnerUp.score >= 6)
            val corrected = if (confident) {
                requireNotNull(first).item.names
                    .filter { name ->
                        TitleNormalizer.numericParts(name) == TitleNormalizer.numericParts(query) &&
                            LexicalTitleMatcher.editDistance(
                                TitleNormalizer.compact(query),
                                TitleNormalizer.compact(name),
                                2,
                            ) <= 2
                    }.maxByOrNull { matcher.score(query, it) }
            } else {
                null
            }
            val attempts = buildList {
                corrected?.let(::add)
                if (corrected != null &&
                    first != null &&
                    TitleNormalizer.numericParts(first.item.title) == TitleNormalizer.numericParts(query)
                ) {
                    add(first.item.title)
                }
                val anchor = TitleNormalizer.words(corrected ?: query).split(' ')
                    .filter { it.length >= 4 && it.any(Char::isLetter) }.maxByOrNull { it.length }
                if (anchor != null) add(anchor)
            }.distinctBy { it.trim().lowercase() }.filter { it != query }.take(2)
            for (variant in attempts) {
                val budget = synchronized(extraRequests) {
                    extraRequests.getOrPut(adapter.key) { SearchRequestBudget(2) }
                }
                if (!budget.take()) break
                val recovered = try {
                    adapter.fetch(1, variant)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Preserve the successful original response and suggestions.
                    mutableAssistance.value = mutableAssistance.value.copy(unavailable = true)
                    break
                }
                remember(recovered.items.map(adapter::title))
                val namedVariant = corrected != null && variant in listOf(corrected, first?.item?.title)
                val pertinent = recovered.items.filter {
                    val item = adapter.title(it)
                    val expected = if (namedVariant) listOfNotNull(corrected, variant) else listOf(corrected ?: query)
                    item.names.any { name -> expected.any { matcher.score(it, name) >= 75 } }
                }
                merged += pertinent
                // An anchor is a single bounded probe, never a crawl of a broad catalog.
                branches += Cursor(variant, 2, recovered.hasNextPage && pertinent.isNotEmpty() && namedVariant)
                if (pertinent.isNotEmpty() && corrected != null) {
                    mutableAssistance.value = mutableAssistance.value.copy(correctedQuery = variant)
                    break
                }
            }
        }
        synchronized(cursors) { cursors[adapter.key] = branches.toMutableList<Cursor<*>>() }
        val unique = merged.distinctBy(adapter::identity)
        synchronized(displayed) { displayed[adapter.key] = unique.mapTo(mutableSetOf(), adapter::identity) }
        val ordered = if (canAssist()) {
            unique.sortedByDescending {
                matcher.score(query, adapter.title(it).title)
            }
        } else {
            unique
        }
        return SearchPage(ordered, branches.any { it.more })
    }

    private suspend fun <T> nextPage(adapter: ExtensionSearchAdapter<T>): SearchPage<T> {
        @Suppress("UNCHECKED_CAST")
        val branches = synchronized(cursors) { cursors[adapter.key]?.toList() } as? List<Cursor<T>>
            ?: return SearchPage(emptyList(), false)
        val items = mutableListOf<T>()
        val active = branches.filter { it.more }
        // Keep successful pages pending until every branch succeeds, so a retry cannot skip data.
        for (branch in active) {
            if (branch.pending == null) branch.pending = adapter.fetch(branch.nextPage, branch.query)
        }
        for (branch in active) {
            val result = requireNotNull(branch.pending)
            branch.pending = null
            branch.nextPage++
            branch.more = result.hasNextPage
            remember(result.items.map(adapter::title))
            val filtered = if (branch.query == query) {
                result.items
            } else {
                result.items.filter {
                    adapter.title(it).names.any { name ->
                        matcher.score(branch.query, name) >= 75
                    }
                }
            }
            synchronized(displayed) {
                val seen = displayed.getOrPut(adapter.key) { mutableSetOf() }
                items += filtered.filter { seen.add(adapter.identity(it)) }
            }
        }
        return SearchPage(items, branches.any { it.more })
    }

    private suspend fun remember(items: List<SearchTitle>) {
        try {
            provider.remember(items)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // A disposable index cannot turn a successful source response into an error.
        }
    }

    private fun canAssist() = enabled &&
        !exact &&
        query.length in 3..256 &&
        !query.contains("://") &&
        !query.startsWith("id:", true)
}

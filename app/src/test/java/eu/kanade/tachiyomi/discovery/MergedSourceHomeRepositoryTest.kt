package eu.kanade.tachiyomi.discovery

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.data.discovery.MergedSourceHomeRepository
import tachiyomi.domain.discovery.SectionState
import tachiyomi.domain.discovery.SourceHomeAccess
import tachiyomi.domain.discovery.SourceHomeGroup
import tachiyomi.domain.discovery.SourceHomeGroupAccess
import tachiyomi.domain.discovery.SourceHomePage
import tachiyomi.domain.discovery.SourceHomePresentation
import tachiyomi.domain.discovery.SourceHomeRepository
import tachiyomi.domain.discovery.SourceHomeRequest
import tachiyomi.domain.discovery.SourceHomeSection
import tachiyomi.domain.discovery.SourceHomeSource
import tachiyomi.domain.discovery.homePresentation
import tachiyomi.domain.entries.anime.model.Anime

class MergedSourceHomeRepositoryTest {
    @Test fun selectedDateOnlyQueriesCapableProvidersAndKeepsItsOwnPagination() = runBlocking {
        val dated = second.copy(sections = listOf(shelf.copy(dateFilter = "Date")))
        val current = access.copy(
            group = access.group!!.copy(providers = listOf(first, dated)),
            providers = listOf(SourceHomeAccess(first), SourceHomeAccess(dated)),
        )
        val calls = mutableListOf<Pair<Long, SourceHomeRequest>>()
        val merged = MergedSourceHomeRepository({ source ->
            provider { request ->
                calls += source.id to request
                flowOf(page())
            }
        }, { current })
        assertTrue(current.group!!.sections.single().supportsDate)
        val tomorrow = SourceHomeRequest("popular", date = "2026-09-12")
        merged.observe(current, tomorrow).last()
        merged.observe(current, tomorrow.copy(page = 2)).last()
        merged.observe(current, tomorrow.copy(page = 2, date = "2026-09-13")).last()
        assertEquals(listOf(2L to tomorrow, 2L to tomorrow.copy(page = 2, date = "2026-09-13")), calls)
    }

    @Test fun mergedFeedsPreserveDifferentEpisodesOfOneSeries() = runBlocking {
        val a = item(11, 1)
        val cards = listOf("ep9", "ep8", "ep9").map { id ->
            a.copy(memo = SourceHomePresentation(id = id).attachTo(a.memo))
        }
        val merged = MergedSourceHomeRepository({ source ->
            provider { flowOf(if (source.id == 1L) page(*cards.toTypedArray()) else page()) }
        }, { access })
        val result = merged.observe(access, SourceHomeRequest("popular")).last()
        assertEquals(listOf("ep9", "ep8"), result.data!!.items.map { it.homePresentation?.id })
        assertTrue(result.data!!.items.all { it.id == 11L })
    }

    private val shelf = SourceHomeSection("popular", "Popolari", emptyMap())
    private val first =
        SourceHomeSource(
            1,
            "v1",
            listOf(shelf),
            emptyList(),
            title = "Cartoni",
            sourceName = "Uno",
            homeId = "cartoons",
        )
    private val second = first.copy(id = 2, key = "two", sourceName = "Due")
    private val access = SourceHomeGroupAccess(
        SourceHomeGroup("cartoons", "Cartoni", listOf(first, second)),
        listOf(SourceHomeAccess(first), SourceHomeAccess(second)),
    )
    private fun item(
        id: Long,
        source: Long,
    ) = Anime.create().copy(id = id, source = source, url = "/$id", title = "Titolo uguale")
    private fun page(
        vararg items: Anime,
        next: Boolean = false,
    ) = SectionState(SourceHomePage(items.toList(), next), loading = false)
    private fun provider(
        load: (SourceHomeRequest) -> Flow<SectionState<SourceHomePage>>,
    ) = object : SourceHomeRepository {
        override fun observe(access: SourceHomeAccess, request: SourceHomeRequest, refresh: Boolean) = load(request)
    }

    @Test fun interleavesSourcesWithoutConfusingHomonymousTitles() = runBlocking {
        val a = item(11, 1)
        val b = item(12, 1)
        val c = item(21, 2)
        val merged = MergedSourceHomeRepository({ source ->
            provider { flowOf(if (source.id == 1L) page(a, a, b) else page(c)) }
        }, { access })
        val result = merged.observe(access, SourceHomeRequest("popular")).last()
        assertEquals(listOf(a, c, b), result.data!!.items)
        assertFalse(result.loading)
    }

    @Test fun failingProviderKeepsHealthyResultsAndNamesOnlyTheFailedSource() = runBlocking {
        val merged = MergedSourceHomeRepository({ source ->
            provider {
                flowOf(if (source.id == 1L) page(item(1, 1)) else SectionState(loading = false, error = "Errore"))
            }
        }, { access })
        val result = merged.observe(access, SourceHomeRequest("popular")).last()
        assertEquals(1, result.data!!.items.size)
        assertEquals("Due: Errore", result.error)
    }

    @Test fun slowProviderDoesNotDelayHealthyCards() = runBlocking {
        val never = CompletableDeferred<Unit>()
        val merged = MergedSourceHomeRepository({ source ->
            provider {
                if (source.id == 1L) {
                    flowOf(page(item(1, 1)))
                } else {
                    flow {
                        never.await()
                        emit(page())
                    }
                }
            }
        }, { access })
        val result = withTimeout(2_000) {
            merged.observe(access, SourceHomeRequest("popular")).first { it.data?.items?.isNotEmpty() == true }
        }
        assertTrue(result.loading)
        assertEquals(1, result.data!!.items.size)
    }

    @Test fun unavailableOrOfflineGroupNeverLoadsProviders() = runBlocking {
        val merged = MergedSourceHomeRepository({ error("Must not fetch") }, { access.copy(group = null) })
        assertNull(merged.observe(access, SourceHomeRequest("popular")).last().data)
        val offline = access.copy(offline = true)
        assertNull(merged.observe(offline, SourceHomeRequest("popular")).last().data)
    }

    @Test fun removedSourceCannotLeakLateResults() = runBlocking {
        var current = access
        val merged =
            MergedSourceHomeRepository({
                provider {
                    flow {
                        current = access.copy(group = null)
                        emit(page(item(1, 1)))
                    }
                }
            }, { current })
        assertNull(merged.observe(access, SourceHomeRequest("popular")).last().data)
    }

    @Test fun paginationStopsEachSourceIndependentlyAndRefreshRestartsIt() = runBlocking {
        val calls = mutableListOf<Pair<Long, Int>>()
        val merged = MergedSourceHomeRepository({ source ->
            provider { request ->
                calls += source.id to request.page
                flowOf(page(item(source.id * 10 + request.page, source.id), next = source.id == 2L))
            }
        }, { access })
        merged.observe(access, SourceHomeRequest("popular")).last()
        merged.observe(access, SourceHomeRequest("popular", 2)).last()
        assertFalse(1L to 2 in calls)
        assertTrue(2L to 2 in calls)
        merged.observe(access, SourceHomeRequest("popular", 2), refresh = true).last()
        assertTrue(1L to 2 in calls)
    }

    @Test fun mergedSectionsAreUniqueAndOnlySupportedSourcesAreQueried() = runBlocking {
        val extra = SourceHomeSection("recent", "Recenti", emptyMap())
        val changed = second.copy(sections = listOf(shelf, extra))
        val group = access.copy(
            group = access.group!!.copy(providers = listOf(first, changed)),
            providers = listOf(SourceHomeAccess(first), SourceHomeAccess(changed)),
        )
        assertEquals(listOf("popular", "recent"), group.group!!.sections.map { it.id })
        val merged = MergedSourceHomeRepository({ source ->
            assertEquals(2L, source.id)
            provider { flowOf(page(item(2, 2))) }
        }, { group })
        assertEquals(2L, merged.observe(group, SourceHomeRequest("recent")).last().data!!.items.single().source)
    }
}

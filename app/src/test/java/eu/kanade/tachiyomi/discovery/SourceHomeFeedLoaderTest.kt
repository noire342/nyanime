package eu.kanade.tachiyomi.discovery

import eu.kanade.tachiyomi.ui.discovery.SourceHomeFeedLoader
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.discovery.SectionState
import tachiyomi.domain.discovery.SourceHomeGroup
import tachiyomi.domain.discovery.SourceHomeGroupAccess
import tachiyomi.domain.discovery.SourceHomeGroupRepository
import tachiyomi.domain.discovery.SourceHomePage
import tachiyomi.domain.discovery.SourceHomeRequest

class SourceHomeFeedLoaderTest {
    @Test fun returnRevalidatesVisitedRequestsWhileManualRefreshForcesNetwork() = runBlocking {
        val calls = mutableListOf<Pair<SourceHomeRequest, Boolean>>()
        val access = SourceHomeGroupAccess(group = SourceHomeGroup("test", "Home", emptyList()))
        val repository = object : SourceHomeGroupRepository {
            override fun observe(access: SourceHomeGroupAccess, request: SourceHomeRequest, refresh: Boolean) = flow {
                calls += request to refresh
                emit(SectionState(SourceHomePage(emptyList(), false), loading = false))
            }
        }
        val loader =
            SourceHomeFeedLoader(CoroutineScope(coroutineContext + Dispatchers.Unconfined), repository) { access }
        val dated = SourceHomeRequest("schedule", date = "2026-09-12")
        loader.load(dated)
        loader.load(dated)
        assertEquals(1, calls.size)
        loader.refresh(false)
        loader.refresh(true)
        assertEquals(listOf(dated to false, dated to false, dated to true), calls)
        loader.reset()
        loader.refresh(true)
        assertTrue(loader.sections.value.isEmpty())
        assertEquals(3, calls.size)
    }

    @Test fun refreshKeepsCardsButChangingDateCannotReuseThemOnFailure() = runBlocking {
        var access = SourceHomeGroupAccess(group = SourceHomeGroup("test", "Home", emptyList()))
        var gate: CompletableDeferred<Unit>? = null
        var calls = 0
        val page = SourceHomePage(emptyList(), false, "First day")
        val repository = object : SourceHomeGroupRepository {
            override fun observe(access: SourceHomeGroupAccess, request: SourceHomeRequest, refresh: Boolean) = flow {
                calls++
                emit(SectionState())
                gate?.await()
                emit(
                    if (request.date ==
                        null
                    ) {
                        SectionState(page, loading = false)
                    } else {
                        SectionState(loading = false, error = "offline")
                    },
                )
            }
        }
        val loader =
            SourceHomeFeedLoader(CoroutineScope(coroutineContext + Dispatchers.Unconfined), repository) { access }
        loader.load(SourceHomeRequest("schedule"))
        gate = CompletableDeferred()
        loader.refresh(true)
        assertEquals(page, loader.sections.value["schedule"]!!.data)
        loader.refresh(false)
        assertEquals(2, calls)
        gate!!.complete(Unit)
        loader.load(SourceHomeRequest("schedule", date = "2026-09-12"))
        assertNull(loader.sections.value["schedule"]!!.data)
        assertEquals("offline", loader.sections.value["schedule"]!!.error)
        access = access.copy(offline = true)
        loader.refresh(true)
        assertEquals(3, calls)
        loader.reset()
    }
}

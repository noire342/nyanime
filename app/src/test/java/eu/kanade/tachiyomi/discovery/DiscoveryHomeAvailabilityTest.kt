package eu.kanade.tachiyomi.discovery

import eu.kanade.tachiyomi.ui.discovery.DiscoveryHomeAvailability
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.discovery.SourceHomeAccess
import tachiyomi.domain.discovery.SourceHomeListing
import tachiyomi.domain.discovery.SourceHomeSource

class DiscoveryHomeAvailabilityTest {
    @Test fun olderBooleanSelectionCannotCrashTheNewStringSelection() {
        assertNull(DiscoveryHomeAvailability.restoreSelection(true))
        assertNull(DiscoveryHomeAvailability.restoreSelection(false))
        assertNull(DiscoveryHomeAvailability.restoreSelection(null))
        assertNull(DiscoveryHomeAvailability.restoreSelection(""))
        assertEquals("films", DiscoveryHomeAvailability.restoreSelection("films"))
    }
    private val source = SourceHomeSource(42, "v1", emptyList(), emptyList(), homeId = "cartoons", title = "Cartoni")

    @Test fun absentExtensionNeverRestoresOptionalHome() {
        val state = DiscoveryHomeAvailability.from(SourceHomeAccess())
        assertTrue(state.homes.isEmpty())
        assertNull(state.selectedHome("cartoons"))
        assertNull(state.reconcileSelection("cartoons"))
        assertTrue(state.unavailable)
    }

    @Test fun initializationKeepsSavedSelectionWithoutFlashingOptionalContent() {
        val state = DiscoveryHomeAvailability.from(SourceHomeAccess(source, loading = true))
        assertTrue(state.homes.isEmpty())
        assertNull(state.selectedHome("cartoons"))
        assertEquals("cartoons", state.reconcileSelection("cartoons"))
        assertFalse(state.unavailable)
    }

    @Test fun initializedAvailableGroupCanRestoreSelection() {
        val state = DiscoveryHomeAvailability.from(SourceHomeAccess(source))
        assertEquals("cartoons", state.selectedHome("cartoons"))
        assertEquals("cartoons", state.reconcileSelection("cartoons"))
        assertNull(state.selectedHome(null))
    }

    @Test fun removalResetsToAnimeAndReinstallationDoesNotSwitchAutomatically() {
        val available = DiscoveryHomeAvailability.from(SourceHomeAccess(source))
        val missing = DiscoveryHomeAvailability.from(SourceHomeAccess())
        val selection = missing.reconcileSelection(available.reconcileSelection("cartoons"))
        assertNull(selection)
        assertNull(available.selectedHome(selection))
    }

    @Test fun incognitoAndDownloadOnlyDoNotPretendExtensionIsAbsent() {
        val state = DiscoveryHomeAvailability.from(SourceHomeAccess(source, offline = true, isPrivate = true))
        assertEquals("cartoons", state.selectedHome("cartoons"))
    }

    @Test fun failuresDoNotAdvertiseUnavailableExtensions() {
        val state = DiscoveryHomeAvailability.from(SourceHomeAccess(error = "Incompatible"))
        assertTrue(state.homes.isEmpty())
        assertTrue(state.unavailable)
    }

    @Test fun removedSourceClosesOnlyItsOwnActiveRoute() {
        val missing = DiscoveryHomeAvailability.from(SourceHomeAccess())
        assertTrue(missing.shouldLeaveSourcePage(true))
        assertFalse(missing.shouldLeaveSourcePage(false))
        assertFalse(DiscoveryHomeAvailability().shouldLeaveSourcePage(true))
    }

    @Test fun sharedHomeMergesProvidersWithoutCreatingDuplicateNavigation() {
        val other = source.copy(id = 99, key = "other", sourceName = "Ciao")
        val film = other.copy(key = "film", homeId = "films", title = "Film")
        val state = DiscoveryHomeAvailability.from(SourceHomeListing(false, listOf(source, other, film)))
        assertEquals(listOf("cartoons", "films"), state.homes.map { it.id })
        assertEquals(setOf(42L, 99L), state.homes.first().sourceIds)
        val removedOne = DiscoveryHomeAvailability.from(SourceHomeListing(false, listOf(other)))
        assertEquals("cartoons", removedOne.reconcileSelection("cartoons"))
    }
}

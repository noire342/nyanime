package eu.kanade.tachiyomi.discovery

import eu.kanade.tachiyomi.ui.discovery.DiscoveryHomeAvailability
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.discovery.SourceHomeAccess
import tachiyomi.domain.discovery.SourceHomeSource

class DiscoveryHomeAvailabilityTest {
    private val source = SourceHomeSource(42, "16.1", emptyList(), emptyList())

    @Test
    fun noExtensionMeansOnlyTheAnimeHomeEvenWithARestoredCartoonsSelection() {
        val state = DiscoveryHomeAvailability.from(SourceHomeAccess())
        assertFalse(state.cartoonsAvailable)
        assertFalse(state.showCartoons(requested = true))
        assertFalse(state.reconcileSelection(requested = true))
        assertTrue(state.unavailable)
    }

    @Test
    fun initializationNeverFlashesOptionalContentOrPrematurelyErasesTheSavedSelection() {
        val state = DiscoveryHomeAvailability.from(SourceHomeAccess(loading = true))
        assertFalse(state.cartoonsAvailable)
        assertFalse(state.showCartoons(requested = true))
        assertTrue(state.reconcileSelection(requested = true))
        assertFalse(state.unavailable)
    }

    @Test
    fun onlyAnInitializedAvailableSourceCanRestoreTheCartoonsHome() {
        val state = DiscoveryHomeAvailability.from(SourceHomeAccess(source))
        assertTrue(state.cartoonsAvailable)
        assertTrue(state.showCartoons(requested = true))
        assertTrue(state.reconcileSelection(requested = true))
        assertFalse(state.showCartoons(requested = false))
        assertFalse(state.unavailable)
    }

    @Test
    fun removalResetsToAnimeAndReinstallationDoesNotSwitchTheHomeByItself() {
        val available = DiscoveryHomeAvailability.from(SourceHomeAccess(source))
        val removed = DiscoveryHomeAvailability.from(SourceHomeAccess())
        var selection = available.reconcileSelection(true)
        assertTrue(available.showCartoons(selection))
        selection = removed.reconcileSelection(selection)
        assertFalse(removed.showCartoons(selection))
        assertFalse(available.showCartoons(selection))
        assertTrue(available.showCartoons(true))
    }

    @Test
    fun downloadOnlyAndIncognitoDoNotPretendTheInstalledExtensionIsMissing() {
        val state = DiscoveryHomeAvailability.from(SourceHomeAccess(source, offline = true, isPrivate = true))
        assertTrue(state.cartoonsAvailable)
        assertTrue(state.showCartoons(true))
    }

    @Test
    fun staleSourceDuringInitializationAndSourceErrorsStayHidden() {
        val initializing = DiscoveryHomeAvailability.from(SourceHomeAccess(source, loading = true))
        assertFalse(initializing.cartoonsAvailable)
        val broken = DiscoveryHomeAvailability.from(SourceHomeAccess(error = "Extension not compatible"))
        assertFalse(broken.cartoonsAvailable)
        assertTrue(broken.unavailable)
    }

    @Test
    fun unavailableSourceOnlyClosesItsOwnActiveRouteAfterInitialization() {
        val missing = DiscoveryHomeAvailability.from(SourceHomeAccess())
        assertTrue(missing.shouldLeaveSourcePage(current = true))
        assertFalse(missing.shouldLeaveSourcePage(current = false))
        assertFalse(DiscoveryHomeAvailability().shouldLeaveSourcePage(current = true))
        assertFalse(DiscoveryHomeAvailability.from(SourceHomeAccess(source)).shouldLeaveSourcePage(current = true))
    }
}

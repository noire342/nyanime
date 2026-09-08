package eu.kanade.tachiyomi.ui.discovery

import tachiyomi.domain.discovery.SourceHomeAccess

/** An optional extension may add a Home, but must never advertise itself before it is available. */
data class DiscoveryHomeAvailability(
    val loading: Boolean = true,
    val cartoonsAvailable: Boolean = false,
) {
    fun showCartoons(requested: Boolean): Boolean = requested && cartoonsAvailable && !loading

    // Keep a restored selection pending until extension initialization has actually finished.
    fun reconcileSelection(requested: Boolean): Boolean = requested && (loading || cartoonsAvailable)

    val unavailable: Boolean get() = !loading && !cartoonsAvailable

    fun shouldLeaveSourcePage(current: Boolean): Boolean = unavailable && current

    companion object {
        fun from(access: SourceHomeAccess) = DiscoveryHomeAvailability(
            loading = access.loading,
            cartoonsAvailable = !access.loading && access.source != null,
        )
    }
}

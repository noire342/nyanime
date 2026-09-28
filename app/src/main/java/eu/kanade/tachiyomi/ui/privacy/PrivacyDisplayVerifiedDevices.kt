package eu.kanade.tachiyomi.ui.privacy

/** Public device facts only. Approval never bypasses the backend's hardware/API checks. */
object PrivacyDisplayVerifiedDevices {
    private data class Device(val model: String, val fingerprint: String, val areas: Set<PrivacyArea>)

    private val devices = listOf(
        Device(
            model = "SM-S948B",
            fingerprint = "samsung/m3qxeea/m3q:16/BP4A.251205.006/S948BXXS4AZHL_OXM4AZHL:user/release-keys",
            areas = setOf(
                PrivacyArea.VIDEO,
                PrivacyArea.READER,
                PrivacyArea.LIBRARY,
                PrivacyArea.HISTORY,
                PrivacyArea.RESUME,
                PrivacyArea.SEARCH,
                PrivacyArea.NSFW,
            ),
        ),
    )

    fun supports(model: String, fingerprint: String, area: PrivacyArea? = null): Boolean = devices.any {
        it.model == model &&
            it.fingerprint == fingerprint &&
            it.areas.isNotEmpty() &&
            (area == null || area in it.areas)
    }
}

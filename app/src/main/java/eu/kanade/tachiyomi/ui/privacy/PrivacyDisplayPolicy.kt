package eu.kanade.tachiyomi.ui.privacy

/** User intent only: independent from windows, hardware and presentation. */
data class PrivacyDisplayPolicy(
    val enabled: Boolean,
    val selectedAreas: Set<PrivacyArea>,
    val temporaryOverrides: Map<PrivacyArea, Boolean> = emptyMap(),
) {
    fun permits(area: PrivacyArea, scope: PrivacyArea = area): Boolean =
        temporaryOverrides[scope] ?: (enabled && area in selectedAreas)

    fun withTemporaryOverride(scope: PrivacyArea, enabled: Boolean?): PrivacyDisplayPolicy = copy(
        temporaryOverrides = if (enabled ==
            null
        ) {
            temporaryOverrides - scope
        } else {
            temporaryOverrides + (scope to enabled)
        },
    )
}

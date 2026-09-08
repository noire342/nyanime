package eu.kanade.tachiyomi.data.discovery

import tachiyomi.domain.discovery.HomeSectionProvider

class LocalHomeSections(
    private val create: (Boolean, Long?) -> HomeSectionProvider<LocalHomeItem>,
) {
    val resume = create(true, null)
    val updates = create(false, null)
    fun resume(sourceId: Long) = create(true, sourceId)
    fun updates(sourceId: Long) = create(false, sourceId)
}

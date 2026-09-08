package eu.kanade.tachiyomi.data.discovery

import tachiyomi.domain.discovery.HomeSectionProvider

class LocalHomeSections(
    val resume: HomeSectionProvider<LocalHomeItem>,
    val updates: HomeSectionProvider<LocalHomeItem>,
)

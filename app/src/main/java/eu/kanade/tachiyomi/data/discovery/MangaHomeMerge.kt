package eu.kanade.tachiyomi.data.discovery

/** Merge only when independently supplied public catalogue IDs agree. */
object MangaHomeMerge {
    fun merge(pages: List<MangaHomePage>, preferredSource: Long? = null): MangaHomePage {
        val entries = (0 until (pages.maxOfOrNull { it.items.size } ?: 0)).flatMap { index ->
            pages.mapNotNull { it.items.getOrNull(index) }
        }
        val result = mutableListOf<MangaHomeItem>()
        entries.forEach { entry ->
            val index = result.indexOfFirst { existing ->
                existing.manga.source != entry.manga.source &&
                    existing.alternateSources.none { it.source == entry.manga.source } &&
                    samePublicWork(existing, entry)
            }
            if (index < 0) {
                result += entry
            } else {
                val current = result[index]
                val variants = current.alternateSources + entry.manga
                val ids = current.presentation?.catalogIds.orEmpty() + entry.presentation?.catalogIds.orEmpty()
                result[index] = if (preferredSource == entry.manga.source) {
                    entry.copy(
                        presentation = (entry.presentation ?: MangaHomePresentation()).copy(catalogIds = ids),
                        alternateSources = listOf(current.manga) + current.alternateSources,
                    )
                } else {
                    current.copy(
                        presentation = (current.presentation ?: MangaHomePresentation()).copy(catalogIds = ids),
                        alternateSources = variants,
                    )
                }
            }
        }
        return MangaHomePage(result, pages.any { it.hasNextPage })
    }

    private fun samePublicWork(left: MangaHomeItem, right: MangaHomeItem): Boolean {
        val a = left.presentation?.catalogIds.orEmpty()
        val b = right.presentation?.catalogIds.orEmpty()
        val shared = a.keys intersect b.keys
        if (shared.isEmpty() || shared.any { a[it] != b[it] }) return false
        return shared.any { a[it] == b[it] }
    }
}

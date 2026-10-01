package eu.kanade.tachiyomi.ui.search

import eu.kanade.tachiyomi.data.discovery.MangaGenreLabels
import eu.kanade.tachiyomi.data.discovery.MangaHomeItem
import eu.kanade.tachiyomi.data.track.SourceTrackingHints
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import tachiyomi.domain.discovery.SourceHomeRequest
import tachiyomi.domain.discovery.SourceHomeSection
import tachiyomi.domain.discovery.SourceHomeSource
import tachiyomi.domain.discovery.homePresentation
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.asAnimeCover
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.model.asMangaCover
import tachiyomi.domain.search.SearchMedium
import tachiyomi.domain.search.searchAliases

data class AtlasTarget(val id: Long, val source: Long, val medium: SearchMedium, val title: String)

data class AtlasEntry(
    val key: String,
    val medium: SearchMedium,
    val source: Long,
    val url: String,
    val title: String,
    val category: String,
    val categoryTitle: String,
    val anime: Anime? = null,
    val manga: Manga? = null,
    val catalogIds: Map<String, Long> = emptyMap(),
    val aliases: List<String> = emptyList(),
    val targets: List<AtlasTarget> = emptyList(),
) {
    val artwork: Any? get() = anime?.asAnimeCover() ?: manga?.asMangaCover()

    companion object {
        fun video(anime: Anime, category: String, label: String): AtlasEntry {
            val presentation = anime.homePresentation
            val hints = SourceTrackingHints.from(anime)
            val ids = buildMap {
                hints?.anilistId?.let { put("anilist", it) }
                hints?.malId?.let { put("myanimelist", it) }
                putAll(presentation?.catalogIds.orEmpty())
            }
            return AtlasEntry(
                "video:${anime.source}:${anime.url}", SearchMedium.VIDEO, anime.source, anime.url, anime.title,
                category, label, anime = anime, catalogIds = ids,
                aliases = (presentation?.aliases.orEmpty() + searchAliases(anime.memo)).distinct().take(15),
                targets =
                presentation?.choices?.map { AtlasTarget(it.animeId, it.sourceId, SearchMedium.VIDEO, it.title) }
                    ?.takeIf { it.isNotEmpty() }
                    ?: listOf(AtlasTarget(anime.id, anime.source, SearchMedium.VIDEO, anime.title)),
            )
        }

        fun manga(item: MangaHomeItem, category: String, label: String): AtlasEntry = AtlasEntry(
            "manga:${item.manga.source}:${item.manga.url}", SearchMedium.MANGA, item.manga.source,
            item.manga.url, item.sourceTitle, category, label, manga = item.manga,
            catalogIds = item.presentation?.catalogIds.orEmpty(),
            aliases = item.searchAliases.distinct().take(15),
            targets = (listOf(item.manga) + item.alternateSources).map {
                AtlasTarget(it.id, it.source, SearchMedium.MANGA, it.title)
            }.distinctBy { it.id },
        )
    }
}

data class AtlasCard(val key: String, val variants: List<AtlasEntry>) {
    val entry get() = variants.first()
    val targets get() = variants.flatMap { it.targets }.distinctBy { it.medium to it.id }
}

object AtlasCards {
    fun merge(entries: List<AtlasEntry>): List<AtlasCard> {
        val groups = mutableListOf<MutableList<AtlasEntry>>()
        entries.distinctBy { it.key }.forEach { incoming ->
            val group = groups.firstOrNull { members ->
                members.all { !conflicts(it, incoming) } && members.any { same(it, incoming) }
            }
            if (group == null) {
                groups.add(mutableListOf(incoming))
            } else {
                group.add(incoming)
                val compatible = groups.filter { other ->
                    other !== group &&
                        other.any { same(it, incoming) } &&
                        other.all { member -> group.all { !conflicts(member, it) } }
                }
                compatible.forEach { other ->
                    if (other.all { member -> group.all { !conflicts(member, it) } }) {
                        group.addAll(other)
                        groups.remove(other)
                    }
                }
            }
        }
        return groups.map { AtlasCard(it.first().key, it.toList()) }
    }

    private fun conflicts(a: AtlasEntry, b: AtlasEntry) = a.medium != b.medium ||
        (a.catalogIds.keys intersect b.catalogIds.keys).any { a.catalogIds[it] != b.catalogIds[it] }

    private fun same(a: AtlasEntry, b: AtlasEntry): Boolean {
        if (a.medium != b.medium) return false
        if (a.source == b.source && a.url == b.url) return true
        val common = a.catalogIds.keys intersect b.catalogIds.keys
        return common.isNotEmpty() && common.all { a.catalogIds[it] == b.catalogIds[it] }
    }
}

data class AtlasRoute(
    val key: String,
    val medium: SearchMedium,
    val source: Long,
    val name: String,
    val language: String,
    val category: String,
    val categoryTitle: String,
    val home: SourceHomeSource? = null,
)

data class AtlasGenre(val key: String, val label: String, val sections: Map<String, SourceHomeSection>)

object AtlasFilters {
    fun genres(routes: List<AtlasRoute>): List<AtlasGenre> = routes.flatMap { route ->
        route.home?.categories.orEmpty().map { Triple(MangaGenreLabels.key(it.title), route.key, it) }
    }.groupBy { it.first }.map { (key, members) ->
        AtlasGenre(key, members.first().third.title, members.associate { it.second to it.third })
    }

    /** Every selected control must be honored. A provider with no declaration is never guessed. */
    fun section(route: AtlasRoute, selected: List<AtlasGenre>): SourceHomeSection? {
        val request = request(route, selected) ?: return null
        val home = route.home ?: return null
        val section = if (request.sectionId == SourceHomeRequest.SEARCH) {
            home.search
        } else {
            home.categories.firstOrNull { it.id == request.sectionId }
        }
        return section?.copy(
            selections = section.moreSelections ?: section.selections,
            browseValues = section.browseValues + request.filters,
        )
    }

    fun request(route: AtlasRoute, selected: List<AtlasGenre>): SourceHomeRequest? {
        if (selected.isEmpty()) return SourceHomeRequest(SourceHomeRequest.SEARCH, browse = true)
        val sections = selected.map { it.sections[route.key] ?: return null }
        if (sections.size == 1) {
            val section = sections.single()
            return SourceHomeRequest(section.id, browse = true)
        }
        if (route.medium == SearchMedium.MANGA || sections.any { it.browseValues.isEmpty() }) return null
        val filters = sections.flatMap { it.browseValues.entries }.groupBy { it.key }
            .mapValues { (_, values) -> values.flatMap { it.value }.distinct() }
        if (!filters.all { (name, values) ->
                route.home?.browseFilters.orEmpty().any {
                    it.name == name &&
                        it.accepts(values)
                }
            }
        ) {
            return null
        }
        return SourceHomeRequest(SourceHomeRequest.SEARCH, filters = filters, browse = true)
    }
}

/** Disposable, bounded public Home snapshots. Never loads feeds or stores private browsing. */
object AtlasExploreCache {
    private val entries = LinkedHashMap<String, AtlasEntry>()
    private val mutableEntries = MutableStateFlow<List<AtlasEntry>>(emptyList())
    val snapshots = mutableEntries.asStateFlow()

    @Synchronized
    fun offer(items: List<AtlasEntry>) {
        items.take(60).forEach { entries[it.category + ":" + it.key] = it }
        while (entries.size > 160) entries.remove(entries.keys.first())
        mutableEntries.value = entries.values.toList()
    }
}

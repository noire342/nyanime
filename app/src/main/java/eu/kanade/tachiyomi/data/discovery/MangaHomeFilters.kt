package eu.kanade.tachiyomi.data.discovery

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import tachiyomi.domain.discovery.SourceHomeSection
import tachiyomi.domain.discovery.SourceHomeSectionGroup

/** Each request owns fresh filter instances; capabilities depend on labels, never their positions. */
object MangaHomeFilters {
    fun sections(manifest: ExtensionHomeManifest, filters: FilterList) = manifest.sections.map {
        SourceHomeSection(
            it.id,
            it.title,
            manifest.defaults + it.filters,
            it.layout.takeIf { it in setOf("chapters", "updates", "ranking", "featured") } ?: "posters",
            it.group?.let { group -> SourceHomeSectionGroup(group.id, group.title, group.tab) },
            it.dateFilter?.takeIf { label -> textFilter(filters, label) != null },
            it.moreFilters?.let { more -> manifest.defaults + more }?.takeIf { more ->
                supports(filters, SourceHomeSection(it.id, it.title, more))
            },
        )
    }.filter { supports(filters, it) }

    fun categories(manifest: ExtensionHomeManifest, filters: FilterList): List<SourceHomeSection> {
        val category = manifest.categories ?: return emptyList()
        return select(filters, category.filter)?.values.orEmpty().filterIsInstance<String>()
            .filter { it !in category.exclude && it.isNotBlank() && it.length <= 100 }.distinct().take(100).map {
                SourceHomeSection("category:$it", it, manifest.defaults + (category.filter to it))
            }.filter { supports(filters, it) }
    }

    fun search(manifest: ExtensionHomeManifest, filters: FilterList) = manifest.search?.let {
        SourceHomeSection(it.id, it.title, manifest.defaults + it.filters)
    }?.takeIf { supports(filters, it) }

    fun apply(filters: FilterList, section: SourceHomeSection, date: String? = null): FilterList {
        require(supports(filters, section)) { "Aggiorna l'estensione: filtri non compatibili" }
        section.selections.forEach { (label, value) ->
            val filter = requireNotNull(select(filters, label))
            filter.state = filter.values.indexOfFirst { it == value }
        }
        if (date != null) {
            // Validate even callers that do not construct a SourceHomeRequest themselves.
            tachiyomi.domain.discovery.SourceHomeRequest(section.id, date = date)
            requireNotNull(section.dateFilter?.let { textFilter(filters, it) }) {
                "La fonte non supporta la scelta della data"
            }.state = date
        } else {
            section.dateFilter?.let { textFilter(filters, it)?.state = "" }
        }
        return filters
    }

    fun supports(filters: FilterList, section: SourceHomeSection) = section.selections.all { (label, value) ->
        select(filters, label)?.values?.any { it == value } == true
    }

    private fun select(filters: FilterList, name: String) =
        filters.filterIsInstance<Filter.Select<*>>().singleOrNull { it.name == name }

    private fun textFilter(filters: FilterList, name: String) =
        filters.filterIsInstance<Filter.Text>().singleOrNull { it.name == name }
}

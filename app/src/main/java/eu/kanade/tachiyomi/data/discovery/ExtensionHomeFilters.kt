package eu.kanade.tachiyomi.data.discovery

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import tachiyomi.domain.discovery.SourceHomeSection

/** Each request owns fresh filter instances; capabilities depend on labels, never their positions. */
object ExtensionHomeFilters {
    fun sections(manifest: ExtensionHomeManifest, filters: AnimeFilterList) = manifest.sections.map {
        SourceHomeSection(it.id, it.title, manifest.defaults + it.filters)
    }.filter { supports(filters, it) }

    fun categories(manifest: ExtensionHomeManifest, filters: AnimeFilterList): List<SourceHomeSection> {
        val category = manifest.categories ?: return emptyList()
        return select(filters, category.filter)?.values.orEmpty().filterIsInstance<String>()
            .filter { it !in category.exclude && it.isNotBlank() && it.length <= 100 }.distinct().take(100).map {
                SourceHomeSection("category:$it", it, manifest.defaults + (category.filter to it))
            }.filter { supports(filters, it) }
    }

    fun search(manifest: ExtensionHomeManifest, filters: AnimeFilterList) = manifest.search?.let {
        SourceHomeSection(it.id, it.title, manifest.defaults + it.filters)
    }?.takeIf { supports(filters, it) }

    fun apply(filters: AnimeFilterList, section: SourceHomeSection): AnimeFilterList {
        require(supports(filters, section)) { "Aggiorna l’estensione: filtri non compatibili" }
        section.selections.forEach { (label, value) ->
            val filter = requireNotNull(select(filters, label))
            filter.state = filter.values.indexOfFirst { it == value }
        }
        return filters
    }

    fun supports(filters: AnimeFilterList, section: SourceHomeSection) = section.selections.all { (label, value) ->
        select(filters, label)?.values?.any { it == value } == true
    }

    private fun select(filters: AnimeFilterList, name: String) =
        filters.filterIsInstance<AnimeFilter.Select<*>>().singleOrNull { it.name == name }
}

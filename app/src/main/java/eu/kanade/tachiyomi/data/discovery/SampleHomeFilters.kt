package eu.kanade.tachiyomi.data.discovery

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import tachiyomi.domain.discovery.SourceHomeSection

/** The only adapter aware of TestSource's public filter labels. Never depends on private extension classes. */
object SampleHomeFilters {
    const val PACKAGE = "eu.kanade.tachiyomi.animeextension.test.sample"
    const val SEARCH = "search"
    private val defaults = mapOf("Ordina" to "Più visti", "Mostra" to "Tutti", "Categoria" to "Tutte")
    private val feeds = listOf(
        section("popular", "Più visti"),
        section("new-episodes", "Nuovi episodi", "Ordina" to "Più recenti", "Mostra" to "Nuovi episodi"),
        section("recent", "Aggiunti di recente", "Ordina" to "Più recenti"),
        section("recommended", "Consigliati", "Ordina" to "Consigliati"),
        section("films", "Film", "Mostra" to "Film"),
        section("rare", "Classici", "Mostra" to "Classici"),
    )

    fun sections(filters: AnimeFilterList): List<SourceHomeSection> = feeds.filter { supports(filters, it) }

    fun categories(filters: AnimeFilterList): List<SourceHomeSection> =
        select(filters, "Categoria")?.values.orEmpty().filterIsInstance<String>().filter { it != "Tutte" }.map {
            section("category:$it", it, "Categoria" to it)
        }.filter { supports(filters, it) }

    fun search() = section(SEARCH, "Cerca cartoni", "Ordina" to "A–Z")

    fun apply(filters: AnimeFilterList, section: SourceHomeSection): AnimeFilterList {
        require(supports(filters, section)) { "Aggiorna l’estensione TestSource: filtri non compatibili" }
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

    private fun section(id: String, title: String, vararg overrides: Pair<String, String>) =
        SourceHomeSection(id, title, defaults + overrides)
}

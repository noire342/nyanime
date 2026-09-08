package eu.kanade.tachiyomi.discovery

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.data.discovery.SampleHomeFilters
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SampleHomeFiltersTest {
    @Test
    fun allSixShelvesReflectTheExtensionFilters() {
        val filters = filters()
        assertEquals(
            listOf("popular", "new-episodes", "recent", "recommended", "films", "rare"),
            SampleHomeFilters.sections(filters).map { it.id },
        )
        assertEquals(listOf("Categoria A", "Categoria B"), SampleHomeFilters.categories(filters).map { it.title })
    }

    @Test
    fun selectionUsesValuesNotPositionsAndResetsDefaults() {
        val filters = filters(reverse = true)
        val section = SampleHomeFilters.sections(filters).first { it.id == "new-episodes" }
        SampleHomeFilters.apply(filters, section)
        assertEquals(
            mapOf("Ordina" to "Più recenti", "Mostra" to "Nuovi episodi", "Categoria" to "Tutte"),
            values(filters),
        )
        SampleHomeFilters.apply(filters, SampleHomeFilters.sections(filters).first())
        assertEquals(mapOf("Ordina" to "Più visti", "Mostra" to "Tutti", "Categoria" to "Tutte"), values(filters))
    }

    @Test
    fun missingChoicesHideUnsupportedShelvesRatherThanShowingAnUnfilteredCatalogue() {
        val filters = AnimeFilterList(listOf(select("Ordina", listOf("Più visti", "A–Z"))) + filters().drop(1))
        assertEquals(listOf("popular", "films", "rare"), SampleHomeFilters.sections(filters).map { it.id })
        assertFalse(SampleHomeFilters.sections(filters).any { it.id == "new-episodes" })
    }

    @Test
    fun unsupportedRestoredCategoryFailsExplicitly() {
        val category = SampleHomeFilters.categories(filters()).first()
        val changed = AnimeFilterList(filters().take(2))
        assertThrows(IllegalArgumentException::class.java) { SampleHomeFilters.apply(changed, category) }
    }

    @Test
    fun independentFilterListsCannotPolluteOtherRequests() {
        val first = filters()
        val second = filters()
        SampleHomeFilters.apply(first, SampleHomeFilters.sections(first).first { it.id == "films" })
        assertEquals("Film", values(first)["Mostra"])
        assertEquals("Tutti", values(second)["Mostra"])
        assertTrue(SampleHomeFilters.supports(second, SampleHomeFilters.search()))
    }

    companion object {
        fun filters(reverse: Boolean = false) = AnimeFilterList(
            select("Ordina", listOf("A–Z", "Più visti", "Più recenti", "Consigliati"), reverse),
            select("Mostra", listOf("Tutti", "Nuovi episodi", "Film", "Classici"), reverse),
            select("Categoria", listOf("Tutte", "Categoria A", "Categoria B"), reverse),
        )

        private fun select(name: String, values: List<String>, reverse: Boolean = false) =
            object : AnimeFilter.Select<String>(name, (if (reverse) values.reversed() else values).toTypedArray()) {}

        fun values(filters: AnimeFilterList) = filters.filterIsInstance<AnimeFilter.Select<*>>().associate {
            it.name to it.values[it.state]
        }
    }
}

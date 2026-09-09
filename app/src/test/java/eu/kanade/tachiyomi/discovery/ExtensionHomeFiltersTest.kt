package eu.kanade.tachiyomi.discovery

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.data.discovery.ExtensionHomeFilters
import eu.kanade.tachiyomi.data.discovery.ExtensionHomeManifest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExtensionHomeFiltersTest {
    @Test
    fun allSixShelvesReflectTheExtensionFilters() {
        val filters = filters()
        assertEquals(
            listOf("popular", "new-episodes", "recent", "recommended", "films", "rare"),
            ExtensionHomeFilters.sections(manifest(), filters).map { it.id },
        )
        assertEquals(
            listOf("Categoria A", "Categoria B"),
            ExtensionHomeFilters.categories(manifest(), filters).map {
                it.title
            },
        )
    }

    @Test
    fun selectionUsesValuesNotPositionsAndResetsDefaults() {
        val filters = filters(reverse = true)
        val section = ExtensionHomeFilters.sections(manifest(), filters).first { it.id == "new-episodes" }
        ExtensionHomeFilters.apply(filters, section)
        assertEquals(
            mapOf("Ordina" to "Più recenti", "Mostra" to "Nuovi episodi", "Categoria" to "Tutte"),
            values(filters),
        )
        ExtensionHomeFilters.apply(filters, ExtensionHomeFilters.sections(manifest(), filters).first())
        assertEquals(mapOf("Ordina" to "Più visti", "Mostra" to "Tutti", "Categoria" to "Tutte"), values(filters))
    }

    @Test
    fun missingChoicesHideUnsupportedShelvesRatherThanShowingAnUnfilteredCatalogue() {
        val filters = AnimeFilterList(listOf(select("Ordina", listOf("Più visti", "A–Z"))) + filters().drop(1))
        assertEquals(
            listOf("popular", "films", "rare"),
            ExtensionHomeFilters.sections(manifest(), filters).map {
                it.id
            },
        )
        assertFalse(ExtensionHomeFilters.sections(manifest(), filters).any { it.id == "new-episodes" })
    }

    @Test
    fun unsupportedRestoredCategoryFailsExplicitly() {
        val category = ExtensionHomeFilters.categories(manifest(), filters()).first()
        val changed = AnimeFilterList(filters().take(2))
        assertThrows(IllegalArgumentException::class.java) { ExtensionHomeFilters.apply(changed, category) }
    }

    @Test
    fun independentFilterListsCannotPolluteOtherRequests() {
        val first = filters()
        val second = filters()
        ExtensionHomeFilters.apply(first, ExtensionHomeFilters.sections(manifest(), first).first { it.id == "films" })
        assertEquals("Film", values(first)["Mostra"])
        assertEquals("Tutti", values(second)["Mostra"])
        assertTrue(
            ExtensionHomeFilters.supports(second, requireNotNull(ExtensionHomeFilters.search(manifest(), second))),
        )
    }

    companion object {
        fun manifest() = ExtensionHomeManifest.parse(
            requireNotNull(
                ExtensionHomeFiltersTest::class.java.getResource("/discovery/cartoons-home-v1.json"),
            ).readText(),
        ).single()
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

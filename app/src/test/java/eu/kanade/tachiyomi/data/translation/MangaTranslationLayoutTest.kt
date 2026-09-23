package eu.kanade.tachiyomi.data.translation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MangaTranslationLayoutTest {
    @Test
    fun adjacentLinesInOneBalloonAreJoinedWithoutCrossingPanels() {
        val lines = listOf(
            TranslationRegion(.10f, .50f, .25f, .52f, "IT'S QUIET"),
            TranslationRegion(.11f, .526f, .26f, .546f, "ON SNOWY DAYS"),
            TranslationRegion(.33f, .50f, .49f, .52f, "I'M JUST"),
            TranslationRegion(.34f, .526f, .50f, .546f, "SAYING"),
        )

        assertEquals(
            listOf("IT'S QUIET ON SNOWY DAYS", "I'M JUST SAYING"),
            groupTranslationLines(lines, vertical = false).map { it.original },
        )
    }
}

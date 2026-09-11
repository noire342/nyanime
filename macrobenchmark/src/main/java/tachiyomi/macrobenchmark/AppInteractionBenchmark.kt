package tachiyomi.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import org.junit.Rule
import org.junit.Test

class AppInteractionBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()

    @Test fun homeScroll() = measure("discovery")

    @Test fun animeLibraryScroll() = measure("library_anime")

    @Test fun mangaLibraryScroll() = measure("library_manga")

    private fun measure(tab: String) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = 3,
        startupMode = StartupMode.WARM,
        setupBlock = {
            if (iteration == 0) prepareFixtures()
            startActivityAndWait()
            openTab(tab)
        },
    ) { scrollContent() }

    @Test fun searchLargeAnimeLibrary() = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = 3,
        startupMode = StartupMode.WARM,
        setupBlock = {
            if (iteration == 0) prepareFixtures()
            startActivityAndWait()
            openTab("library_anime")
        },
    ) { librarySearch() }

    @Test fun openLocalMangaReader() = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = 3,
        startupMode = StartupMode.WARM,
        setupBlock = {
            if (iteration == 0) prepareFixtures()
            pressHome()
        },
    ) { openReader() }
}

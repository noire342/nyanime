package tachiyomi.macrobenchmark

import android.content.Intent
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

const val TARGET_PACKAGE = "xyz.jmir.tachiyomi.mi.anime4k.benchmark"
const val FIXTURE_ACTIVITY = "eu.kanade.tachiyomi.benchmark.BenchmarkSetupActivity"

fun MacrobenchmarkScope.prepareFixtures() = prepareFixtures(device)

fun prepareFixtures(device: UiDevice) {
    device.executeShellCommand("am start -W -n $TARGET_PACKAGE/$FIXTURE_ACTIVITY")
    check(device.wait(Until.hasObject(By.text("BENCHMARK_READY")), 120_000)) {
        "Synthetic fixture preparation failed; refusing to measure an empty library."
    }
    device.pressHome()
}

fun MacrobenchmarkScope.openTab(tag: String) {
    val tab = device.wait(Until.findObject(By.res(tag)), 10_000) ?: error("Missing navigation target: $tag")
    tab.click()
    device.waitForIdle()
}

fun MacrobenchmarkScope.scrollContent() {
    val list = device.wait(Until.findObject(By.scrollable(true)), 10_000)
        ?: error("No scrollable content in benchmark journey")
    list.setGestureMargin(device.displayWidth / 5)
    repeat(3) {
        list.swipe(Direction.UP, 0.65f)
        device.waitForIdle()
    }
}

fun MacrobenchmarkScope.openReader() {
    startActivityAndWait(Intent().setClassName(TARGET_PACKAGE, FIXTURE_ACTIVITY).putExtra("reader", true))
    check(device.wait(Until.hasObject(By.res(TARGET_PACKAGE, "reader_pager")), 15_000)) {
        "The local manga reader did not open"
    }
    device.waitForIdle()
}

fun MacrobenchmarkScope.librarySearch() {
    val search = device.wait(Until.findObject(By.desc("Search")), 10_000)
        ?: device.findObject(By.desc("Cerca")) ?: error("Library search button missing")
    search.click()
    val field = device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5_000)
        ?: error("Search field missing")
    field.text = "Benchmark 002"
    device.waitForIdle()
}

fun MacrobenchmarkScope.coreJourneys() {
    startActivityAndWait()
    scrollContent()
    openTab("library_anime")
    scrollContent()
    librarySearch()
    device.pressBack()
    device.pressBack()
    openTab("library_manga")
    scrollContent()
    openReader()
}

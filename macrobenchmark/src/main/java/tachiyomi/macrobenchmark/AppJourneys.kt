package tachiyomi.macrobenchmark

import android.annotation.SuppressLint
import android.content.Intent
import androidx.benchmark.Outputs
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

const val TARGET_PACKAGE = "xyz.jmir.tachiyomi.mi.anime4k.benchmark"
const val FIXTURE_ACTIVITY = "eu.kanade.tachiyomi.benchmark.BenchmarkSetupActivity"

fun MacrobenchmarkScope.prepareFixtures() = prepareFixtures(device)

fun prepareFixtures(device: UiDevice) {
    device.wakeUp()
    device.executeShellCommand("wm dismiss-keyguard")
    device.executeShellCommand("am start -W -n $TARGET_PACKAGE/$FIXTURE_ACTIVITY")
    if (!device.wait(Until.hasObject(By.text("BENCHMARK_READY")), 180_000)) {
        val failure = device.findObject(By.textStartsWith("BENCHMARK_FAILED"))?.text
        failJourney(device, "Synthetic fixture preparation failed: $failure")
    }
    device.pressHome()
}

fun MacrobenchmarkScope.openTab(tag: String) {
    val tab = device.wait(Until.findObject(By.res(tag)), 10_000)
        ?: failJourney(device, "Missing navigation target: $tag")
    tab.click()
    device.waitForIdle()
}

fun MacrobenchmarkScope.scrollContent() {
    repeat(3) {
        device.waitForIdle()
        // Compose can replace an accessibility node after any state or image update.
        // Resolve fresh bounds for each gesture, without retaining a UiObject2 across frames.
        var bounds: android.graphics.Rect? = null
        for (attempt in 0..2) {
            try {
                bounds = device.wait(Until.findObject(By.scrollable(true)), 10_000)?.visibleBounds
                if (bounds != null) break
            } catch (e: StaleObjectException) {
                if (attempt == 2) throw e
            }
        }
        val area = checkNotNull(bounds) { "No scrollable content in benchmark journey" }
        check(
            device.swipe(
                area.centerX(),
                area.top + area.height() * 4 / 5,
                area.centerX(),
                area.top + area.height() / 5,
                20,
            ),
        )
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

@SuppressLint("RestrictedApi")
fun failJourney(device: UiDevice, message: String): Nothing {
    // Preserve the actual screen when a journey fails, including R8-only failures.
    runCatching {
        val name = "journey-failure-${System.currentTimeMillis()}"
        Outputs.writeFile("$name.png") { check(device.takeScreenshot(it)) }
        Outputs.writeFile("$name.xml") { device.dumpWindowHierarchy(it) }
    }
    error(message)
}

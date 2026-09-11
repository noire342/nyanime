package tachiyomi.macrobenchmark

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Test
import java.io.File

class AccessibilitySmokeTest {
    @Test fun largeTextLandscapeAndTablet() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        prepareFixtures(device)
        val originalScale = device.executeShellCommand("settings get system font_scale").trim()
            .toFloatOrNull() ?: 1f
        try {
            device.executeShellCommand("settings put system font_scale 1.8")
            launchHome(device)
            capture(device, "home-large-text")
            clickTab(device, "library_anime")
            capture(device, "anime-library-large-text")
            clickTab(device, "library_manga")
            capture(device, "manga-library-large-text")
            device.setOrientationLeft()
            device.waitForIdle()
            clickTab(device, "discovery")
            capture(device, "home-landscape-large-text")
            device.setOrientationNatural()
            device.executeShellCommand("wm size 1600x2560")
            launchHome(device)
            capture(device, "home-tablet-large-text")
            check(device.hasObject(By.res("library_anime")))
            check(device.hasObject(By.res("library_manga")))
            check(device.hasObject(By.res("browse")))
            check(device.hasObject(By.res("more")))
            device.executeShellCommand("am start -W -n $TARGET_PACKAGE/$FIXTURE_ACTIVITY --ez reader true")
            check(device.wait(Until.hasObject(By.res(TARGET_PACKAGE, "reader_pager")), 15_000))
            device.waitForIdle()
            capture(device, "reader-tablet-large-text")
        } finally {
            device.executeShellCommand("wm size reset")
            device.executeShellCommand("settings put system font_scale $originalScale")
            device.setOrientationNatural()
            device.unfreezeRotation()
        }
    }

    private fun launchHome(device: UiDevice) {
        device.executeShellCommand("am force-stop $TARGET_PACKAGE")
        device.executeShellCommand("am start -W -n $TARGET_PACKAGE/eu.kanade.tachiyomi.ui.main.MainActivity")
        check(device.wait(Until.hasObject(By.res("library_anime")), 15_000))
        device.waitForIdle()
    }

    private fun clickTab(device: UiDevice, tag: String) {
        val target = device.wait(Until.findObject(By.res(tag)), 10_000) ?: error("Missing tab: $tag")
        check(target.visibleBounds.width() > 0 && target.visibleBounds.height() > 0)
        target.click()
        device.waitForIdle()
    }

    private fun capture(device: UiDevice, name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?.let(::File) ?: instrumentation.context.getExternalFilesDir(null)!!
        directory.mkdirs()
        check(device.takeScreenshot(File(directory, "$name.png")))
    }
}

package com.mathmotor.video

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Captures screenshots from the actual APK running on an Android emulator.
 * Files are written to the app's external-files directory so the CI job can pull them.
 */
@RunWith(AndroidJUnit4::class)
class EmulatorScreenshotTest {
    @Test
    fun captureHomeEditorAndTextPanel() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)

        // Launch the home activity directly. Force-stopping the package can also kill
        // the instrumentation process on this Android emulator configuration.
        device.executeShellCommand(
            "am start -n com.mathmotor.video.next/com.mathmotor.video.MainActivity"
        )

        assertTrue(
            "Home screen did not appear in the emulator",
            device.wait(Until.hasObject(By.text("Videoların")), 20_000)
        )
        device.waitForIdle()
        takeScreenshot(device, "home.png")

        val openEditor = device.findObject(
            By.clazz("android.widget.Button").textContains("Boş proje oluştur")
        ) ?: throw AssertionError("Could not find the Boş proje oluştur button")
        openEditor.click()

        assertTrue(
            "Editor screen did not appear in the emulator",
            device.wait(Until.hasObject(By.text("Düzenle")), 15_000)
        )
        device.waitForIdle()

        // Visual acceptance gate for the supplied compact-editor reference:
        // playback centered below the preview, Undo/Redo beside it, and all three
        // controls above the timeline instead of consuming the bottom tool dock.
        val preview = device.findObject(By.desc("Düzenlenmiş video önizlemesi"))
            ?: throw AssertionError("Could not find the editor preview bounds")
        val playback = device.findObject(By.desc("Önizlemeyi oynat veya duraklat"))
            ?: throw AssertionError("Playback control is missing below the preview")
        val undo = device.findObject(By.desc("Düzenleme geri al"))
            ?: throw AssertionError("Undo is missing from the preview control row")
        val redo = device.findObject(By.desc("Düzenleme yinele"))
            ?: throw AssertionError("Redo is missing from the preview control row")
        val timeRuler = device.findObject(By.desc("Zaman çizelgesi etiketi: SÜRE"))
            ?: throw AssertionError("Shared time ruler did not appear below preview controls")
        val timeDisplay = device.findObject(By.desc("Oynatma zamanı ve toplam video süresi"))
            ?: throw AssertionError("Single-line current/total time display is missing")
        val density = instrumentation.targetContext.resources.displayMetrics.density
        assertTrue(
            "Timeline time display should remain a single clean line: ${timeDisplay.text}",
            !timeDisplay.text.contains("Proje") && !timeDisplay.text.contains("Klip") &&
                '\n' !in timeDisplay.text
        )
        assertTrue(
            "Timeline left gutter should use a compact icon, bounds=${timeRuler.visibleBounds}",
            timeRuler.visibleBounds.width() <= (28f * density).toInt()
        )
        assertTrue(
            "Timeline time display should not wrap to multiple visual lines, bounds=${timeDisplay.visibleBounds}",
            timeDisplay.visibleBounds.height() <= (24f * density).toInt()
        )
        val clipTab = device.findObject(By.desc("Klip araçları sekmesi"))
            ?: throw AssertionError("Edit tool tab is missing from the bottom dock")
        assertTrue(
            "Playback control should be below the preview",
            playback.visibleBounds.top >= preview.visibleBounds.bottom
        )
        assertTrue(
            "Playback row should sit above the time ruler",
            playback.visibleBounds.bottom <= timeRuler.visibleBounds.top
        )
        assertTrue(
            "Playback control should be centered below the preview",
            kotlin.math.abs(playback.visibleBounds.centerX() - device.displayWidth / 2) <= device.displayWidth / 10
        )
        assertTrue(
            "Undo/Redo should be above, not inside, the bottom tool dock",
            undo.visibleBounds.bottom < clipTab.visibleBounds.top &&
                redo.visibleBounds.bottom < clipTab.visibleBounds.top
        )
        assertTrue(
            "Undo and Redo should be grouped at the right of the playback row",
            undo.visibleBounds.left > playback.visibleBounds.centerX() &&
                redo.visibleBounds.left > undo.visibleBounds.left
        )
        val audioTab = device.findObject(By.desc("Ses araçları sekmesi"))
            ?: throw AssertionError("Audio tool tab is missing from the bottom dock")
        val textTabForOrder = device.findObject(By.desc("Metin araçları sekmesi"))
            ?: throw AssertionError("Text tool tab is missing from the bottom dock")
        assertTrue(
            "Bottom tool order should be Edit, Audio, Text, Export",
            clipTab.visibleBounds.left < audioTab.visibleBounds.left &&
                audioTab.visibleBounds.left < textTabForOrder.visibleBounds.left
        )
        takeScreenshot(device, "editor-clip-panel.png")

        val textTab = device.findObject(By.text("T\nMetin"))
            ?: device.findObject(By.clazz("android.widget.Button").textContains("Metin"))
            ?: throw AssertionError("Could not find the Metin tab in the editor dock")
        textTab.click()
        device.waitForIdle()
        assertTrue(
            "Text layer panel did not appear in the emulator",
            device.wait(Until.hasObject(By.text("METİN KATMANLARI")), 8_000)
        )
        takeScreenshot(device, "editor-text-panel.png")

        // Exercise the same editor at a taller phone-sized viewport. This strengthens
        // responsive-layout coverage beyond the smallest 320x640 CI emulator; it is
        // not a substitute for a real-device touch/visual review.
        try {
            device.executeShellCommand("wm size 393x873")
            SystemClock.sleep(800)
            device.waitForIdle()
            val sizeOverride = device.executeShellCommand("wm size")
            assertTrue(
                "Could not apply the taller phone-sized viewport: $sizeOverride",
                sizeOverride.contains("393x873")
            )
            assertTrue(
                "UiDevice did not report the taller phone-sized viewport: " +
                    "${device.displayWidth}x${device.displayHeight}",
                device.displayWidth >= 390 && device.displayHeight >= 870
            )

            val largeClipTab = device.findObject(By.desc("Klip araçları sekmesi"))
                ?: throw AssertionError("Clip tab disappeared at the taller phone size")
            largeClipTab.click()
            device.waitForIdle()
            SystemClock.sleep(300)

            val largePreview = device.findObject(By.desc("Düzenlenmiş video önizlemesi"))
                ?: throw AssertionError("Preview disappeared at the taller phone size")
            val largePlayback = device.findObject(By.desc("Önizlemeyi oynat veya duraklat"))
                ?: throw AssertionError("Playback target disappeared at the taller phone size")
            val largeRuler = device.findObject(By.desc("Zaman çizelgesi etiketi: SÜRE"))
                ?: throw AssertionError("Shared ruler disappeared at the taller phone size")
            val largeTimeDisplay = device.findObject(By.desc("Oynatma zamanı ve toplam video süresi"))
                ?: throw AssertionError("Time display disappeared at the taller phone size")
            val largeDensity = instrumentation.targetContext.resources.displayMetrics.density
            assertTrue(
                "Taller-phone preview is too short: ${largePreview.visibleBounds}",
                largePreview.visibleBounds.height() >= (180f * largeDensity).toInt()
            )
            assertTrue(
                "Taller-phone playback target is too small: ${largePlayback.visibleBounds}",
                largePlayback.visibleBounds.height() >= (36f * largeDensity).toInt()
            )
            assertTrue(
                "Taller-phone clip tab is below the 44dp touch-target minimum: ${largeClipTab.visibleBounds}",
                largeClipTab.visibleBounds.height() >= (44f * largeDensity).toInt()
            )
            assertTrue(
                "Taller-phone timeline gutter is no longer compact: ${largeRuler.visibleBounds}",
                largeRuler.visibleBounds.width() <= (28f * largeDensity).toInt()
            )
            assertTrue(
                "Taller-phone time display wrapped: ${largeTimeDisplay.text}",
                '\n' !in largeTimeDisplay.text &&
                    largeTimeDisplay.visibleBounds.height() <= (24f * largeDensity).toInt()
            )
            takeScreenshot(device, "editor-phone-size.png")
        } finally {
            // The emulator is reused by the rest of the instrumentation suite.
            device.executeShellCommand("wm size reset")
            SystemClock.sleep(500)
            device.waitForIdle()
        }
    }

    private fun takeScreenshot(device: UiDevice, name: String) {
        // Write into the app-under-test's writable cache. The instrumentation APK's
        // external files directory may be unavailable on headless API 35 emulators.
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val pictures = File(targetContext.cacheDir, "emulator-screenshots")
        assertTrue(
            "Could not create screenshot directory at ${pictures.absolutePath}",
            pictures.exists() || pictures.mkdirs()
        )
        val saved = device.takeScreenshot(File(pictures, name))
        assertTrue("Screenshot was not saved: $name", saved)
    }
}

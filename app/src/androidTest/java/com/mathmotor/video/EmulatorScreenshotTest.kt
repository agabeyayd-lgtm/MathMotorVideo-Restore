package com.mathmotor.video

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
        val timelineHeader = device.findObject(By.text("ZAMAN ÇİZELGESİ"))
            ?: throw AssertionError("Timeline header did not appear below preview controls")
        val clipTab = device.findObject(By.desc("Klip araçları sekmesi"))
            ?: throw AssertionError("Edit tool tab is missing from the bottom dock")
        assertTrue(
            "Playback control should be below the preview",
            playback.visibleBounds.top >= preview.visibleBounds.bottom
        )
        assertTrue(
            "Playback row should sit above the timeline",
            playback.visibleBounds.bottom <= timelineHeader.visibleBounds.top
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

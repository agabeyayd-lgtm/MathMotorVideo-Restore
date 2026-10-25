package com.mathmotor.video

import android.os.Environment
import android.content.Intent
import android.net.Uri
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
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

        device.executeShellCommand("am force-stop com.mathmotor.video.next")
        device.executeShellCommand(
            "am start -n com.mathmotor.video.next/com.mathmotor.video.MainActivity"
        )

        assertTrue(
            "Home screen did not appear in the emulator",
            device.wait(Until.hasObject(By.text("MathMotor Video")), 20_000)
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


    @Test
    fun timelineGesturesTextTimingSizeAndUndoAreUsable() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val app = instrumentation.targetContext
        val fixture = File(app.filesDir, "editor-test-video.mp4")
        assertTrue("The emulator fixture must be staged before instrumentation", fixture.isFile && fixture.length() > 0L)

        device.executeShellCommand("am force-stop com.mathmotor.video.next")
        app.startActivity(Intent(app, EditorTestEntryActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra("video_uri", Uri.fromFile(fixture).toString())
        })

        assertTrue(
            "Editor did not import the fixture",
            device.wait(Until.hasObject(By.text("Düzenle")), 20_000)
        )
        assertTrue(
            "Video timeline accessibility node was not exposed",
            device.wait(Until.hasObject(By.descContains("VIDEO zaman çizelgesi")), 15_000)
        )
        assertTrue(
            "Timeline time readout is missing",
            device.wait(Until.hasObject(By.desc("Zaman çizelgesi zamanı")), 5_000)
        )
        device.waitForIdle()

        // Increase scale so the eight-second fixture extends beyond the viewport.
        repeat(5) {
            val zoomIn = device.findObject(By.desc("Zaman çizelgesini yakınlaştır"))
                ?: throw AssertionError("Timeline zoom-in action is missing")
            zoomIn.click()
            device.waitForIdle()
        }

        val readout = device.findObject(By.desc("Zaman çizelgesi zamanı"))
            ?: throw AssertionError("Timeline readout disappeared")
        val beforeScroll = readout.text
        val timeline = device.findObject(By.descContains("VIDEO zaman çizelgesi"))
            ?: throw AssertionError("Video timeline disappeared")
        val bounds = timeline.visibleBounds
        assertTrue("Timeline viewport is too small for gesture test", bounds.width() > 200 && bounds.height() > 8)
        assertTrue(
            "One-finger timeline swipe was not delivered",
            device.swipe(bounds.right - 14, bounds.centerY(), bounds.left + 14, bounds.centerY(), 32)
        )
        device.waitForIdle()
        Thread.sleep(250)
        val afterScroll = device.findObject(By.desc("Zaman çizelgesi zamanı"))?.text
        assertNotNull("Timeline time readout became unavailable", afterScroll)
        assertNotEquals("Horizontal timeline scrolling did not change the time beneath the playhead", beforeScroll, afterScroll)

        // Exercise an actual two-pointer pinch gesture, then verify the accessible
        // zoom label changed after the gesture completed.
        val beforePinchDescription = device.findObject(By.descContains("VIDEO zaman çizelgesi"))?.contentDescription
            ?: throw AssertionError("Timeline accessibility description is missing")
        val pinchBounds = device.findObject(By.descContains("VIDEO zaman çizelgesi"))!!.visibleBounds
        val y = pinchBounds.centerY()
        fun point(x: Int): MotionEvent.PointerCoords = MotionEvent.PointerCoords().apply {
            this.x = x.toFloat()
            this.y = y.toFloat()
            pressure = 1f
            size = 1f
        }
        val p1 = arrayOf(
            point(pinchBounds.left + pinchBounds.width() * 35 / 100),
            point(pinchBounds.left + pinchBounds.width() * 22 / 100),
            point(pinchBounds.left + pinchBounds.width() * 12 / 100)
        )
        val p2 = arrayOf(
            point(pinchBounds.left + pinchBounds.width() * 65 / 100),
            point(pinchBounds.left + pinchBounds.width() * 78 / 100),
            point(pinchBounds.left + pinchBounds.width() * 88 / 100)
        )
        assertTrue("Android did not deliver the two-finger pinch", device.performMultiPointerGesture(p1, p2))
        device.waitForIdle()
        val afterPinchDescription = device.findObject(By.descContains("VIDEO zaman çizelgesi"))?.contentDescription
        assertNotNull("Timeline was not rebuilt after pinch zoom", afterPinchDescription)
        assertNotEquals("Two-finger pinch did not change timeline zoom", beforePinchDescription, afterPinchDescription)

        val textTab = device.findObject(By.text("T\\nMetin"))
            ?: device.findObject(By.clazz("android.widget.Button").textContains("Metin"))
            ?: throw AssertionError("Could not find the Metin tab")
        textTab.click()
        assertTrue(
            "Text panel did not open",
            device.wait(Until.hasObject(By.text("METİN KATMANLARI")), 8_000)
        )
        val textInput = device.findObject(By.desc("Videodaki yazı metni"))
            ?: throw AssertionError("Text input accessibility node is missing")
        textInput.click()
        textInput.setText("CI yazısı")
        device.waitForIdle()
        Thread.sleep(800)
        device.pressBack()
        device.waitForIdle()

        val panelScroll = device.findObject(By.clazz("android.widget.ScrollView"))
        if (panelScroll != null) {
            repeat(2) { panelScroll.scroll(androidx.test.uiautomator.Direction.DOWN, 0.55f) }
        }
        device.waitForIdle()

        val startLabel = device.findObject(By.textContains("Yazı başlangıcı:"))
            ?: throw AssertionError("Text start-time label is missing")
        val endLabel = device.findObject(By.textContains("Yazı bitişi:"))
            ?: throw AssertionError("Text end-time label is missing")
        val sizeLabel = device.findObject(By.textContains("Yazı boyutu:"))
            ?: throw AssertionError("Text size label is missing")
        val startBefore = startLabel.text
        val endBefore = endLabel.text
        val sizeBefore = sizeLabel.text

        fun dragSeekBar(description: String, targetPercent: Int) {
            val seek = device.findObject(By.desc(description))
                ?: throw AssertionError("Missing seek bar: $description")
            assertTrue("Seek bar is disabled: $description", seek.isEnabled)
            val b = seek.visibleBounds
            assertTrue("Seek bar is off screen: $description, bounds=$b", b.width() > 100 && b.height() > 0)
            val yBar = b.centerY()
            val startX = b.left + 8
            val endX = b.left + (b.width() * targetPercent / 100).coerceIn(12, b.width() - 8)
            assertTrue("Could not drag seek bar: $description", device.swipe(startX, yBar, endX, yBar, 24))
            device.waitForIdle()
        }

        dragSeekBar("Yazı başlangıç zamanı", 35)
        val startAfter = device.findObject(By.textContains("Yazı başlangıcı:"))?.text
        assertNotEquals("Text start time did not respond to the slider", startBefore, startAfter)

        dragSeekBar("Yazı bitiş zamanı", 75)
        val endAfter = device.findObject(By.textContains("Yazı bitişi:"))?.text
        assertNotEquals("Text end time did not respond to the slider", endBefore, endAfter)

        dragSeekBar("Yazı boyutu", 72)
        val sizeAfter = device.findObject(By.textContains("Yazı boyutu:"))?.text
        assertNotEquals("Text size did not respond to the slider", sizeBefore, sizeAfter)

        val undo = device.findObject(By.desc("Geri al"))
            ?: throw AssertionError("Global Undo action is missing from the text panel")
        assertTrue("Undo did not become enabled after an edit", undo.isEnabled)
        undo.click()
        device.waitForIdle()
        val sizeAfterUndo = device.findObject(By.textContains("Yazı boyutu:"))?.text
        assertEquals("Undo did not restore the prior text size", sizeBefore, sizeAfterUndo)
    }

    private fun takeScreenshot(device: UiDevice, name: String) {
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val pictures = File(
            testContext.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
            "emulator-screenshots"
        )
        assertTrue("Could not create screenshot directory", pictures.exists() || pictures.mkdirs())
        val saved = device.takeScreenshot(File(pictures, name))
        assertTrue("Screenshot was not saved: $name", saved)
    }
}

package com.mathmotor.video

import android.os.Environment
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
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

        device.executeShellCommand("am force-stop com.mathmotor.video.next")
        device.executeShellCommand(
            "am start -n com.mathmotor.video.next/com.mathmotor.video.MainActivity"
        )

        assertTrue(
            "Home screen did not appear in the emulator",
            device.wait(Until.hasObject(By.text("VİDEO STÜDYOSU")), 20_000)
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


    /**
     * Injects the real CapCut-style long-press-then-drag gesture. A normal adb
     * drag starts moving immediately and therefore intentionally pans the
     * shared timeline instead of moving a clip. Keep one pointer down beyond
     * Android's long-press timeout before moving it so the production clip
     * reorder listener receives the same gesture a user performs.
     */
    @Test
    fun injectLongPressDragGesture() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        val startX = requireNotNull(args.getString("startX")).toFloat()
        val startY = requireNotNull(args.getString("startY")).toFloat()
        val endX = requireNotNull(args.getString("endX")).toFloat()
        val endY = requireNotNull(args.getString("endY")).toFloat()
        val downTime = SystemClock.uptimeMillis()

        fun inject(action: Int, x: Float, y: Float) {
            val event = MotionEvent.obtain(
                downTime,
                SystemClock.uptimeMillis(),
                action,
                x,
                y,
                0
            ).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
            try {
                assertTrue(
                    "Could not inject touchscreen action=$action at ($x,$y)",
                    instrumentation.uiAutomation.injectInputEvent(event, true)
                )
            } finally {
                event.recycle()
            }
        }

        inject(MotionEvent.ACTION_DOWN, startX, startY)
        // The view's production long-press Runnable uses Android's configured
        // timeout. Add a small margin so CI scheduling cannot race the timer.
        SystemClock.sleep(android.view.ViewConfiguration.getLongPressTimeout().toLong() + 180L)
        val midpointX = (startX + endX) / 2f
        val midpointY = (startY + endY) / 2f
        inject(MotionEvent.ACTION_MOVE, midpointX, midpointY)
        SystemClock.sleep(80L)
        inject(MotionEvent.ACTION_MOVE, endX, endY)
        SystemClock.sleep(80L)
        inject(MotionEvent.ACTION_UP, endX, endY)
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

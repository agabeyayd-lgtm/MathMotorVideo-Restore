package com.mathmotor.video

import android.content.Intent
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
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val args = InstrumentationRegistry.getArguments()
        val draftId = requireNotNull(args.getString("draftId"))

        // am instrument leaves the launcher focused. Re-open the already-saved
        // split project in the real editor from inside this test process so the
        // injected pointer stream is delivered to the editor window, not HOME.
        context.startActivity(Intent(context, EditorTestEntryActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra("draft_id", draftId)
            putExtra("timeline_sync_test", true)
        })

        assertTrue(
            "The restored split project did not return to the editor",
            device.wait(Until.hasObject(By.text("Düzenle")), 20_000)
        )
        assertTrue(
            "The first split clip was not restored",
            device.wait(Until.hasObject(By.descContains("Klip 1:")), 15_000)
        )
        assertTrue(
            "The second split clip was not restored",
            device.wait(Until.hasObject(By.descContains("Klip 2:")), 15_000)
        )
        device.waitForIdle()

        val firstClip = device.findObject(By.descContains("Klip 1:"))
            ?: throw AssertionError("Could not find the first restored clip surface")
        val secondClip = device.findObject(By.descContains("Klip 2:"))
            ?: throw AssertionError("Could not find the second restored clip surface")
        val firstBounds = firstClip.visibleBounds
        val secondBounds = secondClip.visibleBounds
        assertTrue(
            "The restored first clip is not visibly reachable: $firstBounds",
            firstBounds.width() > 20 && firstBounds.height() > 20
        )
        assertTrue(
            "The restored second clip is not visibly reachable: $secondBounds",
            secondBounds.width() > 20 && secondBounds.height() > 20
        )

        // Drop the second clip over the centre of the first. Because the two
        // pieces were split edge-to-edge, this moves the second piece to 00:00.
        val startX = secondBounds.centerX().toFloat()
        val startY = secondBounds.centerY().toFloat()
        val endX = firstBounds.centerX().toFloat()
        val endY = firstBounds.centerY().toFloat()
        assertTrue(
            "The clip drag target is not to the left of the moved segment: " +
                "first=$firstBounds second=$secondBounds",
            endX < startX
        )

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

        android.util.Log.i(
            "TimelineSyncTest",
            "VIDEO_CLIP_DRAG_INJECT start=($startX,$startY) end=($endX,$endY) " +
                "firstBounds=$firstBounds secondBounds=$secondBounds"
        )
        inject(MotionEvent.ACTION_DOWN, startX, startY)
        SystemClock.sleep(android.view.ViewConfiguration.getLongPressTimeout().toLong() + 220L)
        inject(MotionEvent.ACTION_MOVE, (startX + endX) / 2f, (startY + endY) / 2f)
        SystemClock.sleep(120L)
        inject(MotionEvent.ACTION_MOVE, endX, endY)
        SystemClock.sleep(120L)
        inject(MotionEvent.ACTION_UP, endX, endY)
        instrumentation.waitForIdleSync()

        fun readTimelineLog(): String =
            device.executeShellCommand("logcat -d -v threadtime -s TimelineSyncTest:I")

        var dropMarker = ""
        repeat(12) {
            dropMarker = readTimelineLog().lineSequence()
                .lastOrNull { line -> line.contains("VIDEO_CLIP_DROP") }
                .orEmpty()
            if (dropMarker.isNotBlank()) return@repeat
            SystemClock.sleep(500L)
        }
        assertTrue(
            "The real editor clip view did not deliver its long-press drag to the production drop handler. " +
                readTimelineLog().lineSequence().filter { it.contains("VIDEO_CLIP_TOUCH") || it.contains("VIDEO_CLIP_DRAG_INJECT") }
                    .joinToString("\n"),
            dropMarker.contains("ordered=1,0") &&
                Regex("starts=1:0,0:[1-9][0-9]*").containsMatchIn(dropMarker)
        )
        android.util.Log.i("TimelineSyncTest", "VIDEO_CLIP_DRAG_ASSERTION_PASS $dropMarker")

        val alignmentLog = device.executeShellCommand(
            "logcat -d -v threadtime -s TimelineAlignmentTest:I"
        )
        val snapshotPattern = Regex(
            "TIMELINE_ALIGNMENT_SNAPSHOT .*?clipBoundsByIndex=([0-9:,;]+)"
        )
        val latestBounds = alignmentLog.lineSequence().mapNotNull { line ->
            val raw = snapshotPattern.find(line)?.groupValues?.getOrNull(1) ?: return@mapNotNull null
            raw.split(";").mapNotNull { entry ->
                val parts = entry.split(":", limit = 2)
                if (parts.size != 2) return@mapNotNull null
                val rect = parts[1].split(",").mapNotNull(String::toIntOrNull)
                if (rect.size != 4) return@mapNotNull null
                parts[0].toIntOrNull()?.let { it to rect }
            }.toMap()
        }.lastOrNull { it.size >= 2 }
            ?: throw AssertionError("No post-drop main-video geometry snapshot was recorded")

        val orderedRects = latestBounds.toSortedMap().values.toList()
        val seamPx = orderedRects[0][2] - orderedRects[1][0]
        assertTrue(
            "Post-drop main-video clips must remain adjacent; measured seam=$seamPx px, " +
                "bounds=$latestBounds",
            kotlin.math.abs(seamPx) <= 1
        )
        android.util.Log.i(
            "TimelineSyncTest",
            "VIDEO_CLIP_DRAG_GEOMETRY_PASS seamPx=$seamPx bounds=$latestBounds"
        )

        takeScreenshot(device, "video-split-after-drag.png")

        // Navigate back to MainActivity so VideoEditorActivity.onStop performs
        // its normal autosave. The script then checks the exact restored order.
        device.executeShellCommand(
            "am start -n com.mathmotor.video.next/com.mathmotor.video.MainActivity -f 0x200000"
        )
        var savedMarker = ""
        repeat(10) {
            savedMarker = readTimelineLog()
            if (savedMarker.contains("LOCAL_DRAFT_SAVED id=$draftId")) return@repeat
            SystemClock.sleep(300L)
        }
        val draftLog = device.executeShellCommand("logcat -d -v threadtime -s MathMotorDraft:I")
        assertTrue(
            "The reordered split project was not autosaved before the instrumentation test ended",
            draftLog.contains("LOCAL_DRAFT_SAVED id=$draftId")
        )
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

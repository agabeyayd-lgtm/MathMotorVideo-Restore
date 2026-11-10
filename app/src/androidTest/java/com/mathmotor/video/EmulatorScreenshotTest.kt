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

        android.util.Log.i("TimelineSyncTest", "CLIP_REORDER_TEST start draftId=$draftId")
        // am instrument leaves the launcher focused. Re-open the already-saved
        // split project in the real editor from inside this test process so the
        // injected pointer stream is delivered to the editor window, not HOME.
        android.util.Log.i("TimelineSyncTest", "CLIP_REORDER_TEST launching_editor_entry")
        context.startActivity(Intent(context, EditorTestEntryActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra("draft_id", draftId)
            putExtra("timeline_sync_test", true)
        })
        android.util.Log.i("TimelineSyncTest", "CLIP_REORDER_TEST editor_entry_launch_returned")

        android.util.Log.i("TimelineSyncTest", "CLIP_REORDER_TEST waiting_for_first_clip")
        val firstClipVisible = device.wait(Until.hasObject(By.descContains("Klip 1:")), 20_000)
        android.util.Log.i("TimelineSyncTest", "CLIP_REORDER_TEST first_clip_visible=$firstClipVisible")
        assertTrue(
            "The restored split project did not return to the editor; no first video clip is exposed",
            firstClipVisible
        )
        android.util.Log.i("TimelineSyncTest", "CLIP_REORDER_TEST waiting_for_second_clip")
        val secondClipVisible = device.wait(Until.hasObject(By.descContains("Klip 2:")), 15_000)
        android.util.Log.i("TimelineSyncTest", "CLIP_REORDER_TEST second_clip_visible=$secondClipVisible")
        assertTrue(
            "The second split clip was not restored",
            secondClipVisible
        )
        android.util.Log.i("TimelineSyncTest", "CLIP_REORDER_TEST waiting_for_ui_idle")
        device.waitForIdle()
        android.util.Log.i("TimelineSyncTest", "CLIP_REORDER_TEST editor_ready")

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

        // Drop the second clip in the first quarter of the first segment.
        // The production insertion threshold is the target clip's midpoint:
        // dropping at its centre is ambiguous and may leave the piece after it.
        val startX = secondBounds.centerX().toFloat()
        val startY = secondBounds.centerY().toFloat()
        val endX = (firstBounds.left + firstBounds.width() / 4).toFloat()
        val endY = firstBounds.centerY().toFloat()
        assertTrue(
            "The drag must land in the first half of the first clip to insert at 00:00: " +
                "first=$firstBounds second=$secondBounds targetX=$endX",
            endX < firstBounds.centerX().toFloat() && endX < startX
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
        for (attempt in 0 until 12) {
            dropMarker = readTimelineLog().lineSequence()
                .lastOrNull { line -> line.contains("VIDEO_CLIP_DROP") }
                .orEmpty()
            if (dropMarker.isNotBlank()) break
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

        val snapshotPattern = Regex(
            "TIMELINE_ALIGNMENT_SNAPSHOT .*?clipBoundsByIndex=([0-9:,;]+)"
        )
        var latestBounds: Map<Int, List<Int>>? = null
        for (attempt in 0 until 12) {
            val alignmentLog = device.executeShellCommand(
                "logcat -d -v threadtime -s TimelineAlignmentTest:I"
            )
            latestBounds = alignmentLog.lineSequence().mapNotNull { line ->
                val raw = snapshotPattern.find(line)?.groupValues?.getOrNull(1)
                    ?: return@mapNotNull null
                raw.split(";").mapNotNull { entry ->
                    val parts = entry.split(":", limit = 2)
                    if (parts.size != 2) return@mapNotNull null
                    val rect = parts[1].split(",").mapNotNull(String::toIntOrNull)
                    if (rect.size != 4) return@mapNotNull null
                    parts[0].toIntOrNull()?.let { it to rect }
                }.toMap()
            }.lastOrNull { it.size >= 2 }
            if (latestBounds != null) break
            SystemClock.sleep(400L)
        }
        val measuredBounds = latestBounds
            ?: throw AssertionError("No post-drop main-video geometry snapshot was recorded")

        val orderedRects = measuredBounds.toSortedMap().values.toList()
        val seamPx = orderedRects[0][2] - orderedRects[1][0]
        assertTrue(
            "Post-drop main-video clips must remain adjacent; measured seam=$seamPx px, " +
                "bounds=$measuredBounds",
            kotlin.math.abs(seamPx) <= 1
        )
        android.util.Log.i(
            "TimelineSyncTest",
            "VIDEO_CLIP_DRAG_GEOMETRY_PASS seamPx=$seamPx bounds=$measuredBounds"
        )

        takeScreenshot(device, "video-split-after-drag.png")

        // Send the editor to Home so Android invokes VideoEditorActivity.onStop
        // and its real local-draft autosave. Launching MainActivity from shell
        // can be delivered to the current editor top instance on API 35.
        device.executeShellCommand("logcat -c")
        assertTrue("Could not background the editor after the clip drag", device.pressHome())
        var reorderedDraftSaved = false
        for (attempt in 0 until 12) {
            val draftLog = device.executeShellCommand("logcat -d -v threadtime -s MathMotorDraft:I")
            if (draftLog.contains("LOCAL_DRAFT_SAVED id=$draftId clips=2")) {
                reorderedDraftSaved = true
                break
            }
            SystemClock.sleep(400L)
        }
        assertTrue(
            "The reordered split project was not autosaved after returning Home",
            reorderedDraftSaved
        )

        // The production drop log identifies the segment inserted at project zero.
        // Its duration must survive a fresh Activity/process task launch, not merely
        // remain correct in the in-memory editor before it is closed.
        val movedDurationMs = Regex("starts=1:0,0:(\\d+)")
            .find(dropMarker)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()
            ?: throw AssertionError("Could not read the moved segment duration from VIDEO_CLIP_DROP: $dropMarker")
        assertTrue("The moved split segment must have positive duration", movedDurationMs > 0L)

        device.executeShellCommand("logcat -c")
        context.startActivity(Intent(context, EditorTestEntryActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra("draft_id", draftId)
            putExtra("timeline_sync_test", true)
        })
        assertTrue(
            "The reordered project did not reopen with its first video clip",
            device.wait(Until.hasObject(By.descContains("Klip 1:")), 20_000)
        )
        assertTrue(
            "The reordered project did not reopen with its second video clip",
            device.wait(Until.hasObject(By.descContains("Klip 2:")), 15_000)
        )
        device.waitForIdle()

        val restoredRangesPattern = Regex(
            "EDITOR_SMOKE_DRAFT_VIDEO_RESTORED id=${Regex.escape(draftId)} " +
                "clips=2 ranges=0:(\\d+),(\\d+):(\\d+)"
        )
        var restoredRanges: MatchResult? = null
        var restoreLog = ""
        for (attempt in 0 until 16) {
            restoreLog = device.executeShellCommand(
                "logcat -d -v threadtime -s EditorSmokeTest:I"
            )
            restoredRanges = restoredRangesPattern.find(restoreLog)
            if (restoredRanges != null) break
            SystemClock.sleep(350L)
        }
        val ranges = restoredRanges?.groupValues
            ?: throw AssertionError(
                "The reopened editor did not confirm the exact two-clip project ranges. Log: $restoreLog"
            )
        val firstEndMs = ranges[1].toLong()
        val secondStartMs = ranges[2].toLong()
        val secondEndMs = ranges[3].toLong()
        assertTrue(
            "The moved split segment did not remain first after saving/reopening: " +
                "expected first duration=$movedDurationMs, restored ranges=0:$firstEndMs,$secondStartMs:$secondEndMs",
            firstEndMs == movedDurationMs
        )
        assertTrue(
            "The saved/reopened split clips must remain exactly adjacent: " +
                "ranges=0:$firstEndMs,$secondStartMs:$secondEndMs",
            firstEndMs == secondStartMs && secondEndMs > secondStartMs
        )
        android.util.Log.i(
            "TimelineSyncTest",
            "VIDEO_CLIP_DRAG_PERSISTENCE_PASS movedDurationMs=$movedDurationMs " +
                "restoredRanges=0:$firstEndMs,$secondStartMs:$secondEndMs gapMs=${secondStartMs - firstEndMs}"
        )
        takeScreenshot(device, "video-split-restored-after-reopen.png")
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

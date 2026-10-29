package com.mathmotor.video

import android.content.ContentUris
import android.content.ContentValues
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.os.SystemClock
import android.os.Environment
import java.security.MessageDigest
import java.util.regex.Pattern
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * Runs against the deterministic 8-second fixture copied into the app's private files directory
 * by capture-emulator-screenshots.sh before connectedDebugAndroidTest starts.
 */
@RunWith(AndroidJUnit4::class)
class EditorTimelineInteractionTest {
    @Test
    fun fixedPlayheadScrollAndCenteredPinchStayInSync() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val density = instrumentation.targetContext.resources.displayMetrics.density
        val device = UiDevice.getInstance(instrumentation)
        val packageName = "com.mathmotor.video.next"
        val fixtureUri = "file:///data/user/0/$packageName/files/editor-test-video.mp4"

        // The instrumentation runner is hosted in the app-under-test process on this
        // configuration. Calling "am force-stop $packageName" here kills the test itself,
        // producing "Instrumentation run failed due to Process crashed" before assertions run.
        // Start the explicit test entry activity directly; it replaces the foreground screen
        // without killing the package that is hosting this instrumentation.
        device.executeShellCommand(
            "am start -n $packageName/com.mathmotor.video.EditorTestEntryActivity " +
                "--es video_uri $fixtureUri --es draft_id 22222222-2222-4222-8222-222222222222"
        )

        assertTrue(
            "Editor did not show the video lane",
            device.wait(Until.hasObject(By.desc("Zaman çizelgesi etiketi: VIDEO")), 20_000)
        )
        assertTrue(
            "Project time label did not appear",
            device.wait(Until.hasObject(By.desc("Oynatma zamanı ve toplam video süresi")), 20_000)
        )
        assertTrue("Sample clip was not loaded into the editor", awaitClipAdded(device))
        android.util.Log.i("TimelineInteractionTest", "stage=clip-loaded")
        device.waitForIdle()
        SystemClock.sleep(250)

        val videoLaneLabel = requireObject(device, By.desc("Zaman çizelgesi etiketi: VIDEO"), "VIDEO lane label")
        val laneLabelBounds = videoLaneLabel.visibleBounds
        val laneLeft = laneLabelBounds.right
        val laneRight = device.displayWidth - (4 * density).toInt()
        val laneWidth = (laneRight - laneLeft).coerceAtLeast(1)
        val laneY = laneLabelBounds.centerY()
        val centerX = laneLeft + laneWidth / 2
        val initialTime = requireProjectTime(device)
        val initialMs = projectPositionMs(initialTime)
        android.util.Log.i("TimelineInteractionTest", "stage=initial-time value=$initialTime laneY=$laneY centerX=$centerX")

        // Swipe the filmstrip body away from the trim handles. On the first frame the
        // fixed playhead and left trim handle share the center coordinate, so starting at
        // centerX would accidentally shorten the clip instead of scrolling the timeline.
        val dragPx = (70f * density).toInt()
        val dragStartX = laneLeft + laneWidth * 4 / 5
        val dragEndX = (dragStartX - dragPx).coerceAtLeast(laneLeft + 2)
        device.swipe(dragStartX, laneY, dragEndX, laneY, 35)
        device.waitForIdle()
        SystemClock.sleep(180)
        val afterPlayheadDrag = requireProjectTime(device)
        val afterPlayheadMs = projectPositionMs(afterPlayheadDrag)
        android.util.Log.i("TimelineInteractionTest", "stage=after-filmstrip-body-swipe value=$afterPlayheadDrag")
        assertTrue(
            "Swiping the filmstrip body did not change the project time: $initialTime -> $afterPlayheadDrag",
            initialMs != afterPlayheadMs
        )
        assertTrue(
            "A filmstrip-body swipe must not trim the source clip: $initialTime -> $afterPlayheadDrag",
            abs(projectDurationMs(initialTime) - projectDurationMs(afterPlayheadDrag)) <= 100L
        )

        // A centered pinch should change clip scale while keeping the center time stable.
        val laneScroll = device.findObjects(By.clazz("android.widget.HorizontalScrollView"))
            .firstOrNull {
                val bounds = it.visibleBounds
                bounds.top <= laneY && bounds.bottom >= laneY && bounds.left >= laneLeft - 2
            } ?: throw AssertionError("Could not find the VIDEO timeline scroll viewport")
        val zoomLabelBefore = requireObject(
            device, By.descContains("Zaman çizelgesi yakınlaştırma"), "timeline zoom accessibility label"
        ).contentDescription
        val zoomBefore = zoomPercent(zoomLabelBefore)
        val beforePinchTime = projectPositionMs(requireProjectTime(device))
        android.util.Log.i(
            "TimelineInteractionTest",
            "stage=before-pinch time=$beforePinchTime label=$zoomLabelBefore"
        )
        // Use a slow, wide pinch so both pointer streams produce multiple move events.
        laneScroll.pinchOpen(0.85f, 60)
        device.waitForIdle()
        SystemClock.sleep(350)
        val zoomLabelAfter = requireObject(
            device, By.descContains("Zaman çizelgesi yakınlaştırma"), "timeline zoom label after pinch"
        ).contentDescription
        val zoomAfter = zoomPercent(zoomLabelAfter)
        val afterPinchTime = projectPositionMs(requireProjectTime(device))
        android.util.Log.i(
            "TimelineInteractionTest",
            "stage=after-pinch time=$afterPinchTime label=$zoomLabelAfter"
        )
        assertTrue(
            "Pinch zoom did not change the timeline scale ($zoomLabelBefore -> $zoomLabelAfter)",
            zoomAfter > zoomBefore
        )
        assertTrue(
            "Centered pinch moved the time under the fixed playhead ($beforePinchTime -> $afterPinchTime ms)",
            abs(beforePinchTime - afterPinchTime) <= 150L
        )

        // Swiping the filmstrip itself must allow its HorizontalScrollView to take over,
        // rather than the clip view consuming the gesture as an in-clip scrub.
        val afterZoomLabel = requireObject(device, By.desc("Zaman çizelgesi etiketi: VIDEO"), "VIDEO lane label after zoom")
        val afterZoomBounds = afterZoomLabel.visibleBounds
        val scrollLeft = afterZoomBounds.right
        val scrollWidth = (laneRight - scrollLeft).coerceAtLeast(1)
        val swipeY = afterZoomBounds.centerY()
        var endTime = requireProjectTime(device)
        var endMs = projectPositionMs(endTime)
        for (attempt in 0 until 12) {
            if (endMs >= 7_800L) break
            device.swipe(
                scrollLeft + scrollWidth * 9 / 10,
                swipeY,
                scrollLeft + scrollWidth / 10,
                swipeY,
                40
            )
            device.waitForIdle()
            SystemClock.sleep(180)
            endTime = requireProjectTime(device)
            endMs = projectPositionMs(endTime)
            android.util.Log.i(
                "TimelineInteractionTest",
                "stage=end-swipe attempt=${attempt + 1} value=$endTime"
            )
        }
        assertTrue("A left swipe on the clip filmstrip did not move the timeline", endMs > afterPinchTime)
        android.util.Log.i("TimelineInteractionTest", "stage=after-end-swipe value=$endTime")
        assertTrue(
            "The timeline could not reach the final 8-second frame: $endTime ($endMs ms)",
            endMs >= 7_800L
        )

        // The symmetric leading inset must also let the start align exactly under the center line.
        var startTime = requireProjectTime(device)
        var startMs = projectPositionMs(startTime)
        for (attempt in 0 until 12) {
            if (startMs <= 200L) break
            device.swipe(
                scrollLeft + scrollWidth / 10,
                swipeY,
                scrollLeft + scrollWidth * 9 / 10,
                swipeY,
                40
            )
            device.waitForIdle()
            SystemClock.sleep(180)
            startTime = requireProjectTime(device)
            startMs = projectPositionMs(startTime)
            android.util.Log.i(
                "TimelineInteractionTest",
                "stage=start-swipe attempt=${attempt + 1} value=$startTime"
            )
        }
        android.util.Log.i("TimelineInteractionTest", "stage=after-start-swipe value=$startTime")
        assertTrue("The timeline could not return to its first frame: $startTime ($startMs ms)", startMs <= 200L)
    }

    @Test
    fun rightAndLeftTrimHandlesCanTrimAndRestoreSourceFootage() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val density = instrumentation.targetContext.resources.displayMetrics.density
        val device = UiDevice.getInstance(instrumentation)
        val packageName = "com.mathmotor.video.next"
        val fixtureUri = "file:///data/user/0/$packageName/files/editor-test-video.mp4"

        // Clear markers from the preceding smoke test so this assertion only starts
        // after this test's own sample clip has actually been inserted.
        device.executeShellCommand("logcat -c")
        device.executeShellCommand(
            "am start -n $packageName/com.mathmotor.video.EditorTestEntryActivity " +
                "--es video_uri $fixtureUri --es draft_id 33333333-3333-4333-8333-333333333333"
        )
        assertTrue(
            "Editor did not show the video lane for trim test",
            device.wait(Until.hasObject(By.desc("Zaman çizelgesi etiketi: VIDEO")), 20_000)
        )
        assertTrue(
            "Project time label did not appear for trim test",
            device.wait(Until.hasObject(By.desc("Oynatma zamanı ve toplam video süresi")), 20_000)
        )
        assertTrue("Trim test sample clip was not loaded", awaitClipAdded(device))
        device.waitForIdle()
        SystemClock.sleep(300)

        val projectLabel = requireObject(device, By.desc("Oynatma zamanı ve toplam video süresi"), "project-time label")
        val originalDurationMs = projectDurationMs(projectLabel.text)
        assertTrue("Fixture duration is unexpectedly short: $originalDurationMs ms", originalDurationMs >= 7_800L)

        val laneLabel = requireObject(device, By.desc("Zaman çizelgesi etiketi: VIDEO"), "VIDEO lane label")
        val laneBounds = laneLabel.visibleBounds
        val laneLeft = laneBounds.right
        val laneRight = device.displayWidth - (4 * density).toInt()
        val laneWidth = (laneRight - laneLeft).coerceAtLeast(1)
        val swipeY = laneBounds.centerY()

        // Bring the right edge under the viewport. At this zoom level the clip may
        // require several swipes; the strict end assertion prevents a false trim pass.
        var visibleTimeMs = projectPositionMs(requireProjectTime(device))
        var atEnd = false
        for (attempt in 0 until 12) {
            if (visibleTimeMs >= 7_800L) {
                atEnd = true
                break
            }
            device.swipe(
                laneLeft + laneWidth * 9 / 10,
                swipeY,
                laneLeft + laneWidth / 10,
                swipeY,
                40
            )
            device.waitForIdle()
            SystemClock.sleep(180)
            visibleTimeMs = projectPositionMs(requireProjectTime(device))
            android.util.Log.i(
                "TimelineTrimInteractionTest",
                "stage=scroll-to-end attempt=${attempt + 1} value=$visibleTimeMs"
            )
        }
        assertTrue("Could not scroll the trim test to the final frame: $visibleTimeMs ms", atEnd || visibleTimeMs >= 7_800L)

        val rightSelector = By.descContains("Sağ tutamaç")
        assertTrue("Right trim handle is not accessible at the end of the timeline", device.wait(Until.hasObject(rightSelector), 10_000))
        val rightHandle = requireObject(device, rightSelector, "right trim handle")
        val rightBounds = rightHandle.visibleBounds
        assertTrue("Right trim handle has no visible hit target: $rightBounds", rightBounds.width() >= 4 && rightBounds.height() >= 4)
        val dragPx = (20f * density).toInt().coerceAtLeast(8)
        device.swipe(
            rightBounds.centerX(),
            rightBounds.centerY(),
            (rightBounds.centerX() - dragPx).coerceAtLeast(laneLeft + 1),
            rightBounds.centerY(),
            30
        )
        device.waitForIdle()
        SystemClock.sleep(450)
        val durationAfterRightTrimMs = projectDurationMs(requireProjectTime(device))
        android.util.Log.i(
            "TimelineTrimInteractionTest",
            "stage=right-trim before=$originalDurationMs after=$durationAfterRightTrimMs"
        )
        assertTrue(
            "Dragging the right trim handle did not shorten the project: $originalDurationMs -> $durationAfterRightTrimMs ms",
            durationAfterRightTrimMs <= originalDurationMs - 150L
        )
        assertTrue("Right trim produced an invalid duration: $durationAfterRightTrimMs ms", durationAfterRightTrimMs >= 500L)

        // The shortened edge must also move outward again to recover the source footage.
        // finishTrimControlGesture resets the playhead to the selected clip start, so first
        // scroll the shortened clip's new right edge back under the center playhead.
        var restoreEndMs = projectPositionMs(requireProjectTime(device))
        for (attempt in 0 until 12) {
            if (restoreEndMs >= durationAfterRightTrimMs - 200L) break
            device.swipe(
                laneLeft + laneWidth * 9 / 10,
                swipeY,
                laneLeft + laneWidth / 10,
                swipeY,
                40
            )
            device.waitForIdle()
            SystemClock.sleep(180)
            restoreEndMs = projectPositionMs(requireProjectTime(device))
            android.util.Log.i(
                "TimelineTrimInteractionTest",
                "stage=scroll-to-right-restore-edge attempt=${attempt + 1} value=$restoreEndMs"
            )
        }
        assertTrue(
            "Could not reach the trimmed right edge to restore source footage: $restoreEndMs ms",
            restoreEndMs >= durationAfterRightTrimMs - 200L
        )
        val rightRestoreHandle = requireObject(device, rightSelector, "right trim handle for restoration")
        val rightRestoreBounds = rightRestoreHandle.visibleBounds
        device.swipe(
            rightRestoreBounds.centerX(),
            rightRestoreBounds.centerY(),
            (rightRestoreBounds.centerX() + dragPx).coerceAtMost(device.displayWidth - 2),
            rightRestoreBounds.centerY(),
            30
        )
        device.waitForIdle()
        SystemClock.sleep(450)
        val restoredDurationAfterRightMs = projectDurationMs(requireProjectTime(device))
        android.util.Log.i(
            "TimelineTrimInteractionTest",
            "stage=restore-right before=$durationAfterRightTrimMs after=$restoredDurationAfterRightMs original=$originalDurationMs"
        )
        assertTrue(
            "Dragging the right trim handle outward did not restore source footage: $durationAfterRightTrimMs -> $restoredDurationAfterRightMs ms",
            restoredDurationAfterRightMs >= durationAfterRightTrimMs + 150L
        )
        assertTrue(
            "Right-edge restoration did not recover the original source duration: $originalDurationMs -> $restoredDurationAfterRightMs ms",
            restoredDurationAfterRightMs >= originalDurationMs - 250L
        )

        // Return to the first frame and trim the left edge inward as well.
        var startMs = projectPositionMs(requireProjectTime(device))
        for (attempt in 0 until 12) {
            if (startMs <= 200L) break
            device.swipe(
                laneLeft + laneWidth / 10,
                swipeY,
                laneLeft + laneWidth * 9 / 10,
                swipeY,
                40
            )
            device.waitForIdle()
            SystemClock.sleep(180)
            startMs = projectPositionMs(requireProjectTime(device))
            android.util.Log.i(
                "TimelineTrimInteractionTest",
                "stage=scroll-to-start attempt=${attempt + 1} value=$startMs"
            )
        }
        assertTrue("Could not return to the first frame before left trim: $startMs ms", startMs <= 200L)
        val leftSelector = By.descContains("Sol tutamaç")
        assertTrue("Left trim handle is not accessible at the first frame", device.wait(Until.hasObject(leftSelector), 10_000))
        val leftHandle = requireObject(device, leftSelector, "left trim handle")
        val leftBounds = leftHandle.visibleBounds
        assertTrue("Left trim handle has no visible hit target: $leftBounds", leftBounds.width() >= 4 && leftBounds.height() >= 4)
        device.swipe(
            leftBounds.centerX(),
            leftBounds.centerY(),
            leftBounds.centerX() + dragPx,
            leftBounds.centerY(),
            30
        )
        device.waitForIdle()
        SystemClock.sleep(450)
        val durationAfterLeftTrimMs = projectDurationMs(requireProjectTime(device))
        android.util.Log.i(
            "TimelineTrimInteractionTest",
            "stage=left-trim before=$restoredDurationAfterRightMs after=$durationAfterLeftTrimMs"
        )
        assertTrue(
            "Dragging the left trim handle did not shorten the project: $restoredDurationAfterRightMs -> $durationAfterLeftTrimMs ms",
            durationAfterLeftTrimMs <= restoredDurationAfterRightMs - 150L
        )
        assertTrue("Left trim produced an invalid duration: $durationAfterLeftTrimMs ms", durationAfterLeftTrimMs >= 500L)

        // The left trim must be reversible too: drag the left edge back to its source start.
        val leftRestoreHandle = requireObject(device, leftSelector, "left trim handle for restoration")
        val leftRestoreBounds = leftRestoreHandle.visibleBounds
        device.swipe(
            leftRestoreBounds.centerX(),
            leftRestoreBounds.centerY(),
            (leftRestoreBounds.centerX() - dragPx).coerceAtLeast(laneLeft + 1),
            leftRestoreBounds.centerY(),
            30
        )
        device.waitForIdle()
        SystemClock.sleep(450)
        val restoredDurationAfterLeftMs = projectDurationMs(requireProjectTime(device))
        android.util.Log.i(
            "TimelineTrimInteractionTest",
            "stage=restore-left before=$durationAfterLeftTrimMs after=$restoredDurationAfterLeftMs original=$originalDurationMs"
        )
        assertTrue(
            "Dragging the left trim handle outward did not restore source footage: $durationAfterLeftTrimMs -> $restoredDurationAfterLeftMs ms",
            restoredDurationAfterLeftMs >= durationAfterLeftTrimMs + 150L
        )
        assertTrue(
            "Restoring both trim edges did not recover the prior untrimmed duration: $restoredDurationAfterRightMs -> $restoredDurationAfterLeftMs ms",
            restoredDurationAfterLeftMs >= restoredDurationAfterRightMs - 250L
        )
    }

    @Test
    fun filmstripScrollDoesNotTrimAndTrimHandleDoesNotBecomeScroll() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val density = context.resources.displayMetrics.density
        val device = UiDevice.getInstance(instrumentation)
        val packageName = context.packageName
        val fixture = java.io.File(context.filesDir, "editor-test-video.mp4")
        assertTrue("Trim-vs-scroll fixture is missing", fixture.isFile && fixture.length() > 0L)
        val originalHash = sha256(fixture)
        val fixtureUri = Uri.fromFile(fixture).toString()
        val draftId = java.util.UUID.randomUUID().toString()

        device.executeShellCommand("logcat -c")
        device.executeShellCommand(
            "am start --activity-clear-task -n " + packageName +
                "/com.mathmotor.video.EditorTestEntryActivity --es video_uri " +
                fixtureUri + " --es draft_id " + draftId
        )
        assertTrue(
            "Editor did not show the VIDEO lane for trim-vs-scroll test",
            device.wait(Until.hasObject(By.desc("Zaman çizelgesi etiketi: VIDEO")), 20_000)
        )
        assertTrue(
            "Project time label did not appear for trim-vs-scroll test",
            device.wait(Until.hasObject(By.desc("Oynatma zamanı ve toplam video süresi")), 20_000)
        )
        assertTrue("Trim-vs-scroll fixture clip was not loaded", awaitClipAdded(device))
        device.waitForIdle()
        SystemClock.sleep(250)

        val projectBefore = requireProjectTime(device)
        val originalDurationMs = projectDurationMs(projectBefore)
        assertTrue(
            "Trim-vs-scroll test requires the full 8-second source, got " + originalDurationMs + " ms",
            originalDurationMs in 7_800L..8_300L
        )
        val previewBounds = requireObject(
            device, By.desc("Düzenlenmiş video önizlemesi"), "compact video preview"
        ).visibleBounds
        assertTrue(
            "Compact preview should be larger than the old quarter-screen stage: bounds=$previewBounds density=$density",
            previewBounds.height() >= (180f * density).toInt()
        )
        val laneSelector = By.desc("Zaman çizelgesi satırı: VIDEO")
        val laneBeforeZoom = requireObject(device, laneSelector, "VIDEO timeline lane before pinch")
        val compactLaneBounds = laneBeforeZoom.visibleBounds
        assertTrue(
            "Compact VIDEO filmstrip should be a readable primary lane, bounds=$compactLaneBounds density=$density",
            compactLaneBounds.height() >= (38f * density).toInt()
        )
        assertTrue("Compact timeline is missing its shared time ruler", device.hasObject(By.desc("Zaman çizelgesi etiketi: SÜRE")))
        assertFalse(
            "Single-clip screen still shows a duplicate clip chooser row",
            device.hasObject(By.desc("Klip seçici satırı"))
        )
        val zoomBeforeLabel = requireObject(
            device, By.descContains("Zaman çizelgesi yakınlaştırma"), "timeline zoom before pinch"
        ).contentDescription
        val zoomBefore = zoomPercent(zoomBeforeLabel)
        val timeBeforeZoomMs = projectPositionMs(requireProjectTime(device))
        laneBeforeZoom.pinchOpen(0.85f, 60)
        device.waitForIdle()
        SystemClock.sleep(300)

        val zoomAfterLabel = requireObject(
            device, By.descContains("Zaman çizelgesi yakınlaştırma"), "timeline zoom after pinch"
        ).contentDescription
        val zoomAfter = zoomPercent(zoomAfterLabel)
        val timeAfterZoomMs = projectPositionMs(requireProjectTime(device))
        assertTrue(
            "Pinch did not change zoom before the trim-vs-scroll gestures: " +
                zoomBeforeLabel + " -> " + zoomAfterLabel,
            zoomAfter > zoomBefore
        )
        assertTrue(
            "Pinch moved the fixed-playhead time before the trim-vs-scroll gestures: " +
                timeBeforeZoomMs + " -> " + timeAfterZoomMs + " ms",
            kotlin.math.abs(timeBeforeZoomMs - timeAfterZoomMs) <= 150L
        )

        // A horizontal swipe through the filmstrip body must travel the timeline.
        // Keep both endpoints away from the visible trim handles.
        val filmstripSelector = By.descContains("Klip 1:")
        val filmstrip = requireObject(device, filmstripSelector, "first clip filmstrip")
        val filmstripBounds = filmstrip.visibleBounds
        val safeGapPx = (6f * density).toInt().coerceAtLeast(2)
        var safeBodyLeft = filmstripBounds.left + safeGapPx
        var safeBodyRight = filmstripBounds.right - safeGapPx

        // Exclude any trim handle that is actually visible inside the filmstrip's clipped
        // viewport. The previous 68%-to-46% swipe could be only ~15 dp after pinch zoom;
        // at high zoom, that correctly moved the timeline by ~120 ms and failed the
        // arbitrary 200 ms threshold without proving a scroll/trim conflict.
        device.findObject(By.descContains("Sol tutamaç"))?.visibleBounds?.let { bounds ->
            val overlapsFilmstrip = bounds.right > filmstripBounds.left &&
                bounds.left < filmstripBounds.right &&
                bounds.bottom > filmstripBounds.top && bounds.top < filmstripBounds.bottom
            if (overlapsFilmstrip) safeBodyLeft = maxOf(safeBodyLeft, bounds.right + safeGapPx)
        }
        device.findObject(By.descContains("Sağ tutamaç"))?.visibleBounds?.let { bounds ->
            val overlapsFilmstrip = bounds.right > filmstripBounds.left &&
                bounds.left < filmstripBounds.right &&
                bounds.bottom > filmstripBounds.top && bounds.top < filmstripBounds.bottom
            if (overlapsFilmstrip) safeBodyRight = minOf(safeBodyRight, bounds.left - safeGapPx)
        }
        val safeBodyWidth = safeBodyRight - safeBodyLeft
        assertTrue(
            "First filmstrip has too little trim-handle-free body for a decisive swipe: " +
                "filmstrip=$filmstripBounds safeBody=[$safeBodyLeft,$safeBodyRight] zoom=$zoomAfter%",
            safeBodyWidth >= (65f * density).toInt()
        )
        val swipeY = filmstripBounds.centerY()
        // Use most of the available body, not a tiny fixed percentage of a possibly clipped
        // post-zoom view. Both endpoints stay inside the safe area between trim handles.
        val swipeStartX = safeBodyLeft + (safeBodyWidth * 0.90f).toInt()
        val swipeEndX = safeBodyLeft + (safeBodyWidth * 0.10f).toInt()
        val positionBeforeBodySwipeMs = projectPositionMs(requireProjectTime(device))
        val durationBeforeBodySwipeMs = projectDurationMs(requireProjectTime(device))
        val estimatedSwipeMs = (((swipeStartX - swipeEndX) / density) /
            (22f * (zoomAfter.toFloat() / 100f)).coerceAtLeast(0.1f) * 1000f).toLong()
        android.util.Log.i(
            "TrimScrollConflictTest",
            "stage=filmstrip-swipe-setup filmstrip=$filmstripBounds safeBodyWidth=$safeBodyWidth " +
                "startX=$swipeStartX endX=$swipeEndX zoom=$zoomAfter estimatedDeltaMs=$estimatedSwipeMs"
        )
        device.swipe(swipeStartX, swipeY, swipeEndX, swipeY, 40)
        device.waitForIdle()
        SystemClock.sleep(350)
        val positionAfterBodySwipeMs = projectPositionMs(requireProjectTime(device))
        val durationAfterBodySwipeMs = projectDurationMs(requireProjectTime(device))
        android.util.Log.i(
            "TrimScrollConflictTest",
            "stage=filmstrip-swipe beforePosition=" + positionBeforeBodySwipeMs +
                " afterPosition=" + positionAfterBodySwipeMs +
                " beforeDuration=" + durationBeforeBodySwipeMs +
                " afterDuration=" + durationAfterBodySwipeMs +
                " zoom=" + zoomAfter
        )
        assertTrue(
            "A swipe on the filmstrip body did not scroll the playhead: " +
                positionBeforeBodySwipeMs + " -> " + positionAfterBodySwipeMs + " ms",
            kotlin.math.abs(positionAfterBodySwipeMs - positionBeforeBodySwipeMs) >= 200L
        )
        assertTrue(
            "A normal filmstrip swipe accidentally trimmed the clip: " +
                durationBeforeBodySwipeMs + " -> " + durationAfterBodySwipeMs + " ms",
            kotlin.math.abs(durationAfterBodySwipeMs - durationBeforeBodySwipeMs) <= 30L
        )

        // Move to the final source frames without touching either edge handle.
        var endPositionMs = positionAfterBodySwipeMs
        for (attempt in 0 until 12) {
            if (endPositionMs >= originalDurationMs - 200L) break
            // Scroll from the actual clip body, not the whole VIDEO lane. At high zoom the
            // lane's rightmost 10% overlaps the visible right trim handle; starting there
            // trims the source and resets the playhead instead of scrolling to the last frames.
            val filmstripForEndScroll = requireObject(
                device, filmstripSelector, "first clip filmstrip while scrolling to trim edge"
            )
            val bodyBounds = filmstripForEndScroll.visibleBounds
            val endSwipeGapPx = (6f * density).toInt().coerceAtLeast(2)
            var endSafeLeft = bodyBounds.left + endSwipeGapPx
            var endSafeRight = bodyBounds.right - endSwipeGapPx
            device.findObject(By.descContains("Sol tutamaç"))?.visibleBounds?.let { handle ->
                val overlaps = handle.right > bodyBounds.left && handle.left < bodyBounds.right &&
                    handle.bottom > bodyBounds.top && handle.top < bodyBounds.bottom
                if (overlaps) endSafeLeft = maxOf(endSafeLeft, handle.right + endSwipeGapPx)
            }
            device.findObject(By.descContains("Sağ tutamaç"))?.visibleBounds?.let { handle ->
                val overlaps = handle.right > bodyBounds.left && handle.left < bodyBounds.right &&
                    handle.bottom > bodyBounds.top && handle.top < bodyBounds.bottom
                if (overlaps) endSafeRight = minOf(endSafeRight, handle.left - endSwipeGapPx)
            }
            val endSafeWidth = endSafeRight - endSafeLeft
            assertTrue(
                "Filmstrip has too little handle-free body to scroll toward the source end: " +
                    "filmstrip=$bodyBounds safe=[$endSafeLeft,$endSafeRight] zoom=$zoomAfter%",
                endSafeWidth >= (40f * density).toInt()
            )
            val endSwipeY = bodyBounds.centerY()
            val endSwipeStartX = endSafeLeft + (endSafeWidth * 0.90f).toInt()
            val endSwipeEndX = endSafeLeft + (endSafeWidth * 0.10f).toInt()
            android.util.Log.i(
                "TrimScrollConflictTest",
                "stage=scroll-to-end-setup attempt=" + (attempt + 1) +
                    " filmstrip=$bodyBounds safeWidth=$endSafeWidth startX=$endSwipeStartX " +
                    "endX=$endSwipeEndX zoom=$zoomAfter"
            )
            device.swipe(endSwipeStartX, endSwipeY, endSwipeEndX, endSwipeY, 40)
            device.waitForIdle()
            SystemClock.sleep(180)
            endPositionMs = projectPositionMs(requireProjectTime(device))
            val endDurationMs = projectDurationMs(requireProjectTime(device))
            android.util.Log.i(
                "TrimScrollConflictTest",
                "stage=scroll-to-end attempt=" + (attempt + 1) + " position=" + endPositionMs +
                    " duration=" + endDurationMs
            )
            assertTrue(
                "Scrolling the clip body toward the source end changed its duration: " +
                    "$originalDurationMs -> $endDurationMs ms",
                kotlin.math.abs(endDurationMs - originalDurationMs) <= 30L
            )
        }
        assertTrue(
            "Could not scroll the final source frames under the playhead: " + endPositionMs + " ms",
            endPositionMs >= originalDurationMs - 200L
        )

        val rightSelector = By.descContains("Sağ tutamaç")
        assertTrue(
            "Right trim handle is not accessible at the source end",
            device.wait(Until.hasObject(rightSelector), 10_000)
        )
        val rightHandle = requireObject(device, rightSelector, "right trim handle at source end")
        val rightBounds = rightHandle.visibleBounds
        assertTrue("Right trim handle has no visible drag target: " + rightBounds,
            rightBounds.width() >= 4 && rightBounds.height() >= 4)
        val durationBeforeHandleDragMs = projectDurationMs(requireProjectTime(device))
        // 18 dp produced only a 130 ms trim at this zoom, missing the 150 ms assertion by 20 ms.
        // Use a clear 28 dp gesture so the test exercises the handle without weakening the threshold.
        val handleDragPx = (28f * density).toInt().coerceAtLeast(16)
        device.swipe(
            rightBounds.centerX(),
            rightBounds.centerY(),
            rightBounds.centerX() - handleDragPx,
            rightBounds.centerY(),
            30
        )
        device.waitForIdle()
        SystemClock.sleep(450)

        val durationAfterHandleDragMs = projectDurationMs(requireProjectTime(device))
        val positionAfterHandleDragMs = projectPositionMs(requireProjectTime(device))
        android.util.Log.i(
            "TrimScrollConflictTest",
            "stage=trim-handle beforeDuration=" + durationBeforeHandleDragMs +
                " afterDuration=" + durationAfterHandleDragMs +
                " playheadAfter=" + positionAfterHandleDragMs
        )
        assertTrue(
            "Right-handle drag did not shorten the clip by at least 150 ms: " +
                durationBeforeHandleDragMs + " -> " + durationAfterHandleDragMs + " ms",
            durationAfterHandleDragMs <= durationBeforeHandleDragMs - 150L
        )
        assertTrue(
            "Right-handle trim left an invalid duration: " + durationAfterHandleDragMs + " ms",
            durationAfterHandleDragMs >= 500L
        )
        assertTrue(
            "Right-handle trim should restore the selected clip start under the fixed playhead, got " +
                positionAfterHandleDragMs + " ms",
            positionAfterHandleDragMs <= 250L
        )
        assertTrue(
            "Trim-vs-scroll test modified its original source video",
            fixture.isFile && fixture.length() > 0L && sha256(fixture) == originalHash
        )
    }

    @Test
    fun multiClipReorderTrimUndoRedoAndSeekStayCorrectAcrossZoomLevels() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val packageName = context.packageName
        val firstFile = java.io.File(context.filesDir, "editor-test-video.mp4")
        val secondFile = java.io.File(context.filesDir, "editor-test-video-second.mp4")
        assertTrue("First multi-clip fixture is missing", firstFile.isFile && firstFile.length() > 0L)
        assertTrue("Second multi-clip fixture is missing", secondFile.isFile && secondFile.length() > 0L)
        val firstHash = sha256(firstFile)
        val secondHash = sha256(secondFile)

        fun mediaDurationMs(file: java.io.File): Long {
            val retriever = MediaMetadataRetriever()
            return try {
                retriever.setDataSource(file.absolutePath)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                    ?: throw AssertionError("Could not read fixture duration: ${file.name}")
            } finally {
                retriever.release()
            }
        }

        val firstDurationMs = mediaDurationMs(firstFile)
        val secondDurationMs = mediaDurationMs(secondFile)
        assertTrue("First fixture duration is invalid: $firstDurationMs ms", firstDurationMs >= 7_800L)
        assertTrue("Second fixture duration is invalid: $secondDurationMs ms", secondDurationMs >= 1_500L)

        fun clipJson(file: java.io.File, title: String, durationMs: Long) =
            org.json.JSONObject()
                .put("uri", Uri.fromFile(file).toString())
                .put("title", title)
                .put("durationMs", durationMs)
                .put("startMs", 0L)
                .put("endMs", durationMs)

        val draftId = java.util.UUID.randomUUID().toString()
        val project = org.json.JSONObject()
            .put("format", "MathMotorVideoProject")
            .put("version", 1)
            .put("clips", org.json.JSONArray()
                .put(clipJson(firstFile, "FIRST_RED", firstDurationMs))
                .put(clipJson(secondFile, "SECOND_GREEN", secondDurationMs)))
            .put("selectedClipIndex", 0)
            .put("timelinePlayheadMs", 0L)
            .put("timelineZoom", 1.0)
            .put("timelineHorizontalScrollMs", 0L)
            .put("textLayers", org.json.JSONArray())
            .put("sourceAudioVolume", 100)
            .put("musicVolume", 0)
            .put("soundVolume", 100)
            .put("keepSourceAudio", true)
        assertTrue(
            "Could not seed multi-clip reorder/trim project",
            VideoProjectDraftStore.save(context, draftId, "Multi-clip timeline interaction validation", project)
        )

        device.executeShellCommand("logcat -c")
        device.executeShellCommand(
            "am start --activity-clear-task -n " + packageName +
                "/com.mathmotor.video.EditorTestEntryActivity --es draft_id " + draftId
        )
        val restoreDeadline = SystemClock.uptimeMillis() + 20_000L
        var entryStarted = false
        var draftFound = false
        var draftRestored = false
        var restoreMarkers = ""
        while (SystemClock.uptimeMillis() < restoreDeadline) {
            val logs = device.executeShellCommand("logcat -d -s EditorSmokeTest:I")
            restoreMarkers = logs.takeLast(1_600)
            if (logs.contains("EDITOR_SMOKE_ENTRY_STARTED") && logs.contains("hasDraft=true")) entryStarted = true
            if (logs.contains("EDITOR_SMOKE_DRAFT_LOOKUP id=" + draftId + " found=true")) draftFound = true
            if (logs.contains("EDITOR_SMOKE_DRAFT_RESTORED id=" + draftId)) {
                draftRestored = true
                break
            }
            if (logs.contains("EDITOR_SMOKE_DRAFT_LOOKUP id=" + draftId + " found=false")) break
            SystemClock.sleep(200)
        }
        assertTrue("Multi-clip editor entry did not start; markers=$restoreMarkers", entryStarted)
        assertTrue("Multi-clip draft was not found; markers=$restoreMarkers", draftFound)
        assertTrue("Multi-clip draft did not restore; markers=$restoreMarkers", draftRestored)
        assertTrue(
            "Restored editor did not expose its VIDEO timeline lane",
            device.wait(Until.hasObject(By.desc("Zaman çizelgesi satırı: VIDEO")), 10_000)
        )
        requireObject(device, By.desc("Klip araçları sekmesi"), "clip tools tab").click()
        device.waitForIdle()

        fun assertClipRailOrder(leftTitle: String, rightTitle: String, stage: String) {
            val left = requireObject(device, By.text(leftTitle), "$stage clip tile $leftTitle").visibleBounds
            val right = requireObject(device, By.text(rightTitle), "$stage clip tile $rightTitle").visibleBounds
            assertTrue(
                "$stage clip rail order is wrong: $leftTitle at $left, $rightTitle at $right",
                left.centerX() < right.centerX()
            )
        }

        fun assertSelectedClip(expectedTitle: String, stage: String) {
            // In full multi-clip preview the playhead label correctly shows project
            // time/duration, not clip-local time. The clip rail has no parent
            // contentDescription, so pair its visible selected marker with the
            // expected title by their shared horizontal position.
            val titleBounds = requireObject(
                device, By.text(expectedTitle), "$stage clip-rail title $expectedTitle"
            ).visibleBounds
            val selectedMarkers = device.findObjects(By.text(Pattern.compile("^▶ KLİP [12]$")))
            assertEquals(
                "$stage should expose exactly one selected clip-rail marker",
                1, selectedMarkers.size
            )
            val markerBounds = selectedMarkers.single().visibleBounds
            val horizontalTolerancePx =
                (8f * context.resources.displayMetrics.density).toInt().coerceAtLeast(4)
            assertTrue(
                "$stage expected $expectedTitle to be selected, but selected marker bounds=$markerBounds " +
                    "and expected title bounds=$titleBounds",
                kotlin.math.abs(markerBounds.left - titleBounds.left) <= horizontalTolerancePx
            )
        }

        fun timelineScrollViewport(stage: String): UiObject2 {
            val laneBounds = requireObject(
                device, By.desc("Zaman çizelgesi satırı: VIDEO"), "$stage VIDEO lane"
            ).visibleBounds
            return device.findObjects(By.clazz("android.widget.HorizontalScrollView"))
                .firstOrNull {
                    val bounds = it.visibleBounds
                    bounds.top <= laneBounds.centerY() && bounds.bottom >= laneBounds.centerY() &&
                        bounds.left >= laneBounds.left - 2
                } ?: throw AssertionError("$stage could not find VIDEO timeline scroll viewport; lane=$laneBounds")
        }

        fun undoButton(stage: String): UiObject2 =
            requireObject(device, By.desc("Düzenleme geri al"), "$stage global Undo button")

        fun redoButton(stage: String): UiObject2 =
            requireObject(device, By.desc("Düzenleme yinele"), "$stage global Redo button")

        fun seekPlayheadTo(targetMs: Long, stage: String) {
            val totalMs = projectDurationMs(requireProjectTime(device))
            val expected = targetMs.coerceIn(0L, (totalMs - 1L).coerceAtLeast(0L))
            val seek = requireObject(
                device, By.desc("Zaman çizelgesinde oynatma noktası"), "$stage timeline playhead seek bar"
            )
            assertTrue("$stage playhead SeekBar is too narrow: ${seek.visibleBounds}", seek.visibleBounds.width() > 100)

            // UiObject2 has no setProgress method. Use the real accessibility ACTION_SET_PROGRESS
            // action exposed by Android's SeekBar, then verify the actual project-time label below.
            // Other existing tests continue to exercise physical trim/scroll gestures.
            val accessibilityRoot =
                InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow
            fun findPlayheadNode(
                node: android.view.accessibility.AccessibilityNodeInfo?
            ): android.view.accessibility.AccessibilityNodeInfo? {
                if (node == null) return null
                if (node.contentDescription?.toString() == "Zaman çizelgesinde oynatma noktası") {
                    return node
                }
                for (index in 0 until node.childCount) {
                    val match = findPlayheadNode(node.getChild(index))
                    if (match != null) return match
                }
                return null
            }
            val seekNode = findPlayheadNode(accessibilityRoot)
            assertTrue("$stage playhead accessibility node was not found", seekNode != null)
            val progressArgs = android.os.Bundle().apply {
                putFloat(
                    android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE,
                    expected.toFloat()
                )
            }
            val accepted = seekNode!!.performAction(
                android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id,
                progressArgs
            )
            assertTrue("$stage SeekBar rejected ACTION_SET_PROGRESS for $expected ms", accepted)
            device.waitForIdle()
            SystemClock.sleep(250)
            val actual = projectPositionMs(requireProjectTime(device))
            android.util.Log.i(
                "MultiClipTimelineTest",
                "stage=$stage method=accessibility-set-progress requested=$expected actual=$actual totalMs=$totalMs"
            )
            assertTrue("$stage seek did not reach $expected ms; actual=$actual ms", abs(actual - expected) <= 200L)
        }

        assertClipRailOrder("FIRST_RED", "SECOND_GREEN", "initial")
        seekPlayheadTo(1_000L, "initial-first-clip")
        assertSelectedClip("FIRST_RED", "initial seek into first clip")
        seekPlayheadTo(firstDurationMs + 1_000L, "initial-second-clip")
        assertSelectedClip("SECOND_GREEN", "initial seek into second clip")
        // Return to the first clip before pinching so its filmstrip is safely in the viewport.
        seekPlayheadTo(1_000L, "return-to-first-before-zoom")
        assertSelectedClip("FIRST_RED", "return to first clip")

        val zoomLabelBefore = requireObject(
            device, By.descContains("Zaman çizelgesi yakınlaştırma"), "initial timeline zoom"
        ).contentDescription
        val zoomBefore = zoomPercent(zoomLabelBefore)
        val timeBeforePinchMs = projectPositionMs(requireProjectTime(device))
        timelineScrollViewport("before first zoom").pinchOpen(0.85f, 60)
        device.waitForIdle()
        SystemClock.sleep(350)
        val zoomLabelAfter = requireObject(
            device, By.descContains("Zaman çizelgesi yakınlaştırma"), "timeline zoom after first pinch"
        ).contentDescription
        val zoomAfter = zoomPercent(zoomLabelAfter)
        val timeAfterPinchMs = projectPositionMs(requireProjectTime(device))
        assertTrue("First pinch did not increase zoom: $zoomLabelBefore -> $zoomLabelAfter", zoomAfter > zoomBefore)
        assertTrue(
            "First pinch changed the fixed-playhead time: $timeBeforePinchMs -> $timeAfterPinchMs ms",
            abs(timeBeforePinchMs - timeAfterPinchMs) <= 150L
        )

        // The filmstrip long-press menu is the real timeline reorder path.
        val firstFilmstrip = requireObject(device, By.descContains("Klip 1: FIRST_RED"), "first timeline filmstrip")
        val firstFilmstripBounds = firstFilmstrip.visibleBounds
        assertTrue("First filmstrip is not large enough for a long press: $firstFilmstripBounds",
            firstFilmstripBounds.width() > 20 && firstFilmstripBounds.height() > 12)
        // Inject a down event, keep the same finger down beyond Android's long-press
        // timeout, then inject up. This avoids both UiDevice.swipe step-count semantics and
        // shell input swipe implementations that may compress/collapse a tiny motion.
        // The fixed playhead is a 28dp-wide touch layer placed above the timeline lanes.
        // Touching the filmstrip's center can hit that overlay instead of the clip itself.
        val longPressX = firstFilmstripBounds.left + firstFilmstripBounds.width() / 4
        val playheadOverlayHalfWidthPx = (16f * context.resources.displayMetrics.density).toInt()
        assertTrue(
            "Long-press target is too close to the fixed playhead overlay: bounds=$firstFilmstripBounds x=$longPressX",
            kotlin.math.abs(longPressX - firstFilmstripBounds.centerX()) > playheadOverlayHalfWidthPx
        )
        val longPressY = firstFilmstripBounds.centerY()
        val longPressDurationMs = android.view.ViewConfiguration.getLongPressTimeout() + 500
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val downTime = SystemClock.uptimeMillis()
        android.util.Log.i(
            "MultiClipTimelineTest",
            "stage=filmstrip-long-press-inject x=$longPressX y=$longPressY centerX=${firstFilmstripBounds.centerX()} durationMs=$longPressDurationMs bounds=$firstFilmstripBounds"
        )
        val down = android.view.MotionEvent.obtain(
            downTime, downTime, android.view.MotionEvent.ACTION_DOWN,
            longPressX.toFloat(), longPressY.toFloat(), 0
        ).apply { source = android.view.InputDevice.SOURCE_TOUCHSCREEN }
        val downInjected = automation.injectInputEvent(down, true)
        down.recycle()
        assertTrue("Touchscreen ACTION_DOWN injection failed at ($longPressX,$longPressY)", downInjected)
        SystemClock.sleep(longPressDurationMs.toLong())
        val upTime = SystemClock.uptimeMillis()
        val up = android.view.MotionEvent.obtain(
            downTime, upTime, android.view.MotionEvent.ACTION_UP,
            longPressX.toFloat(), longPressY.toFloat(), 0
        ).apply { source = android.view.InputDevice.SOURCE_TOUCHSCREEN }
        val upInjected = automation.injectInputEvent(up, true)
        up.recycle()
        assertTrue("Touchscreen ACTION_UP injection failed at ($longPressX,$longPressY)", upInjected)
        device.waitForIdle()
        assertTrue(
            "Injected long-press did not open the first filmstrip clip menu; bounds=$firstFilmstripBounds durationMs=$longPressDurationMs",
            device.wait(Until.hasObject(By.text("Bir sonraya al")), 5_000)
        )
        requireObject(device, By.text("Bir sonraya al"), "move clip to next position").click()
        device.waitForIdle()
        SystemClock.sleep(250)
        assertClipRailOrder("SECOND_GREEN", "FIRST_RED", "after-reorder")

        assertTrue("Undo was not enabled after clip reorder", undoButton("after reorder").isEnabled)
        undoButton("after reorder").click()
        device.waitForIdle()
        SystemClock.sleep(250)
        assertClipRailOrder("FIRST_RED", "SECOND_GREEN", "after-undo-reorder")
        assertTrue("Redo was not enabled after undoing clip reorder", redoButton("after reorder undo").isEnabled)
        redoButton("after reorder undo").click()
        device.waitForIdle()
        SystemClock.sleep(250)
        assertClipRailOrder("SECOND_GREEN", "FIRST_RED", "after-redo-reorder")

        // Exercise a second zoom level with the new order, then trim the first output clip.
        val zoomLabelBeforeSecond = requireObject(
            device, By.descContains("Zaman çizelgesi yakınlaştırma"), "zoom before second pinch"
        ).contentDescription
        val zoomBeforeSecond = zoomPercent(zoomLabelBeforeSecond)
        val timeBeforeSecondPinchMs = projectPositionMs(requireProjectTime(device))
        timelineScrollViewport("before second zoom").pinchOpen(0.85f, 60)
        device.waitForIdle()
        SystemClock.sleep(350)
        val zoomLabelAfterSecond = requireObject(
            device, By.descContains("Zaman çizelgesi yakınlaştırma"), "zoom after second pinch"
        ).contentDescription
        val zoomAfterSecond = zoomPercent(zoomLabelAfterSecond)
        val timeAfterSecondPinchMs = projectPositionMs(requireProjectTime(device))
        assertTrue(
            "Second pinch did not change zoom: $zoomLabelBeforeSecond -> $zoomLabelAfterSecond",
            zoomAfterSecond > zoomBeforeSecond
        )
        assertTrue(
            "Second pinch changed the fixed-playhead time: $timeBeforeSecondPinchMs -> $timeAfterSecondPinchMs ms",
            abs(timeBeforeSecondPinchMs - timeAfterSecondPinchMs) <= 150L
        )

        // Reordered first clip is SECOND_GREEN. Trim its right edge while its end remains
        // inside the current viewport, and prove history restores the full multi-clip duration.
        seekPlayheadTo(secondDurationMs - 350L, "before-multi-clip-trim")
        assertSelectedClip("SECOND_GREEN", "before multi-clip trim")
        val totalBeforeTrimMs = projectDurationMs(requireProjectTime(device))
        val rightHandleSelector = By.descContains("Sağ tutamaç")
        assertTrue(
            "Right trim handle for the visible first output clip is not accessible",
            device.wait(Until.hasObject(rightHandleSelector), 8_000)
        )
        val rightHandle = requireObject(device, rightHandleSelector, "first output clip right trim handle")
        val rightBounds = rightHandle.visibleBounds
        assertTrue("Right trim handle has no visible hit target: $rightBounds",
            rightBounds.width() >= 4 && rightBounds.height() >= 4)
        val trimDragPx = (40f * context.resources.displayMetrics.density).toInt().coerceAtLeast(12)
        device.swipe(
            rightBounds.centerX(),
            rightBounds.centerY(),
            rightBounds.centerX() - trimDragPx,
            rightBounds.centerY(),
            30
        )
        device.waitForIdle()
        SystemClock.sleep(450)
        val totalAfterTrimMs = projectDurationMs(requireProjectTime(device))
        assertTrue(
            "Trimming the first clip in a multi-clip project did not shorten the combined sequence: " +
                "$totalBeforeTrimMs -> $totalAfterTrimMs ms",
            totalAfterTrimMs <= totalBeforeTrimMs - 150L
        )
        assertTrue("Multi-clip trim produced an invalid total duration: $totalAfterTrimMs ms", totalAfterTrimMs > firstDurationMs)

        assertTrue("Undo was not enabled after multi-clip trim", undoButton("after multi-clip trim").isEnabled)
        undoButton("after multi-clip trim").click()
        device.waitForIdle()
        SystemClock.sleep(350)
        val totalAfterUndoTrimMs = projectDurationMs(requireProjectTime(device))
        assertTrue(
            "Undo did not restore the original multi-clip duration after trim: " +
                "$totalBeforeTrimMs -> $totalAfterUndoTrimMs ms",
            abs(totalAfterUndoTrimMs - totalBeforeTrimMs) <= 100L
        )
        assertTrue("Redo was not enabled after undoing multi-clip trim", redoButton("after trim undo").isEnabled)
        redoButton("after trim undo").click()
        device.waitForIdle()
        SystemClock.sleep(350)
        val totalAfterRedoTrimMs = projectDurationMs(requireProjectTime(device))
        assertTrue(
            "Redo did not reapply the multi-clip trim: $totalAfterTrimMs -> $totalAfterRedoTrimMs ms",
            abs(totalAfterRedoTrimMs - totalAfterTrimMs) <= 100L
        )

        val trimmedFirstOutputClipMs = totalAfterRedoTrimMs - firstDurationMs
        assertTrue("Derived trimmed first clip duration is invalid: $trimmedFirstOutputClipMs ms",
            trimmedFirstOutputClipMs in 500L until secondDurationMs)
        seekPlayheadTo(1_000L, "reordered-trimmed-first")
        assertSelectedClip("SECOND_GREEN", "seek into reordered first clip after trim/redo")
        seekPlayheadTo(trimmedFirstOutputClipMs + 1_000L, "reordered-trimmed-second")
        assertSelectedClip("FIRST_RED", "seek into reordered second clip after trim/redo")

        assertTrue(
            "Multi-clip interactions modified the original first video fixture",
            firstFile.isFile && firstFile.length() > 0L && sha256(firstFile) == firstHash
        )
        assertTrue(
            "Multi-clip interactions modified the original second video fixture",
            secondFile.isFile && secondFile.length() > 0L && sha256(secondFile) == secondHash
        )
        android.util.Log.i(
            "MultiClipTimelineTest",
            "stage=verified originalOrder=FIRST_RED,SECOND_GREEN reorderedOrder=SECOND_GREEN,FIRST_RED " +
                "zoomLevels=$zoomBefore->$zoomAfter->$zoomAfterSecond durationBeforeTrim=$totalBeforeTrimMs " +
                "durationAfterTrim=$totalAfterTrimMs durationAfterUndo=$totalAfterUndoTrimMs " +
                "durationAfterRedo=$totalAfterRedoTrimMs sourceFilesUnchanged=true"
        )
    }

    @Test
    fun textPreviewAndSettingsRemainStableAfterTimelineSeekAndZoom() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val packageName = context.packageName
        val fixture = java.io.File(context.filesDir, "editor-test-video.mp4")
        assertTrue("Text seek/zoom fixture is missing", fixture.isFile && fixture.length() > 0L)

        val draftId = java.util.UUID.randomUUID().toString()
        val clip = org.json.JSONObject()
            .put("uri", Uri.fromFile(fixture).toString())
            .put("title", "TEXT_SEEK_ZOOM_FIXTURE")
            .put("durationMs", 8_020L)
            .put("startMs", 0L)
            .put("endMs", 8_020L)
        val textLayer = org.json.JSONObject()
            .put("id", 951L)
            .put("text", "SEEK ZOOM TEXT")
            .put("startMs", 1_000L)
            .put("endMs", 6_000L)
            .put("timingCustomized", true)
            .put("position", "TOP")
            .put("sizeSp", 32)
            .put("xFraction", 0.5)
            .put("yFraction", 0.16)
            .put("colorPreset", "CYAN")
            .put("isBold", false)
        val project = org.json.JSONObject()
            .put("format", "MathMotorVideoProject")
            .put("version", 1)
            .put("clips", org.json.JSONArray().put(clip))
            .put("selectedClipIndex", 0)
            .put("timelinePlayheadMs", 3_000L)
            .put("timelineZoom", 1.0)
            .put("timelineHorizontalScrollMs", 0L)
            .put("textLayers", org.json.JSONArray().put(textLayer))
            .put("selectedTextLayerId", 951L)
            .put("sourceAudioVolume", 100)
            .put("musicVolume", 45)
            .put("soundVolume", 100)
            .put("keepSourceAudio", true)
        assertTrue(
            "Could not seed text preview seek/zoom project",
            VideoProjectDraftStore.save(context, draftId, "Text preview seek/zoom", project)
        )

        device.executeShellCommand("logcat -c")
        device.executeShellCommand(
            "am start --activity-clear-task -n " + packageName +
                "/com.mathmotor.video.EditorTestEntryActivity --es draft_id " + draftId
        )
        val restoreDeadline = SystemClock.uptimeMillis() + 20_000L
        var entryStarted = false
        var draftFound = false
        var draftRestored = false
        var restoreMarkers = ""
        while (SystemClock.uptimeMillis() < restoreDeadline) {
            val logs = device.executeShellCommand("logcat -d -s EditorSmokeTest:I")
            restoreMarkers = logs.takeLast(1_600)
            if (logs.contains("EDITOR_SMOKE_ENTRY_STARTED") && logs.contains("hasDraft=true")) {
                entryStarted = true
            }
            if (logs.contains("EDITOR_SMOKE_DRAFT_LOOKUP id=" + draftId + " found=true")) {
                draftFound = true
            }
            if (logs.contains("EDITOR_SMOKE_DRAFT_RESTORED id=" + draftId)) {
                draftRestored = true
                break
            }
            if (logs.contains("EDITOR_SMOKE_DRAFT_LOOKUP id=" + draftId + " found=false")) break
            SystemClock.sleep(200)
        }
        assertTrue("Text seek/zoom entry activity did not start; markers=" + restoreMarkers, entryStarted)
        assertTrue("Text seek/zoom draft was not found; markers=" + restoreMarkers, draftFound)
        assertTrue("Text seek/zoom draft did not restore; markers=" + restoreMarkers, draftRestored)
        assertTrue(
            "Editor did not expose the VIDEO lane for text seek/zoom",
            device.wait(Until.hasObject(By.desc("Zaman çizelgesi etiketi: VIDEO")), 12_000)
        )
        assertTrue(
            "Seeded text was not active at the 3-second playhead",
            device.wait(Until.hasObject(By.clazz("android.widget.TextView").text("SEEK ZOOM TEXT")), 8_000)
        )

        val textTab = device.findObject(By.text("T\nMetin"))
            ?: device.findObject(By.clazz("android.widget.Button").textContains("Metin"))
            ?: throw AssertionError("Could not find the Metin tab for seek/zoom test")
        textTab.click()
        assertTrue(
            "Text panel did not open for seek/zoom test",
            device.wait(Until.hasObject(By.text("METİN KATMANLARI")), 8_000)
        )
        val panelScroll = UiScrollable(UiSelector().className("android.widget.ScrollView")).setAsVerticalList()
        assertTrue(
            "Could not reveal the text input before timeline interaction",
            panelScroll.scrollIntoView(UiSelector().description("Metin katmanı metin girişi"))
        )
        val textValueBefore = requireObject(
            device, By.desc("Metin katmanı metin girişi"), "seek/zoom text input"
        ).text
        assertTrue("Seeded text input was not restored: " + textValueBefore, textValueBefore == "SEEK ZOOM TEXT")

        assertTrue(
            "Could not reveal the start-time label before seek/zoom",
            panelScroll.scrollIntoView(UiSelector().textContains("Yazı başlangıcı:"))
        )
        val startLabelBefore = requireObject(
            device, By.textContains("Yazı başlangıcı:"), "text start label before seek/zoom"
        ).text
        assertTrue(
            "Could not reveal the end-time label before seek/zoom",
            panelScroll.scrollIntoView(UiSelector().textContains("Yazı bitişi:"))
        )
        val endLabelBefore = requireObject(
            device, By.textContains("Yazı bitişi:"), "text end label before seek/zoom"
        ).text
        assertTrue(
            "Could not reveal text-size controls before seek/zoom",
            panelScroll.scrollIntoView(UiSelector().text("A+"))
        )
        val sizeLabelBefore = requireObject(
            device, By.textContains("Yazı boyutu:"), "text size label before seek/zoom"
        ).text
        assertTrue("Seeded text size was not restored: " + sizeLabelBefore, sizeLabelBefore.contains("32 sp"))

        fun seekPlayheadTo(targetMs: Long) {
            val seek = requireObject(
                device, By.desc("Zaman çizelgesinde oynatma noktası"), "timeline playhead seek bar"
            )
            val bounds = seek.visibleBounds
            assertTrue("Timeline playhead seek bar is too narrow: $bounds", bounds.width() > 100)
            val expected = targetMs.coerceIn(0L, 8_020L)
            // SeekBar keeps its thumb padding inside the accessibility bounds. On the
            // compact emulator the default horizontal padding is about 15% per side;
            // dragging from the time currently displayed avoids tap-only interactions,
            // which did not move the thumb reliably after pinch zoom.
            val inset = (bounds.width() * 0.15f).toInt().coerceAtLeast(4)
            val usableWidth = (bounds.width() - 2 * inset).coerceAtLeast(2)
            fun xForTime(timeMs: Long): Int =
                (bounds.left + inset +
                    (timeMs.coerceIn(0L, 8_020L).toDouble() / 8_020.0 * usableWidth).toInt())
                    .coerceIn(bounds.left + 1, bounds.right - 2)

            var actual = projectPositionMs(requireProjectTime(device))
            for (attempt in 1..6) {
                if (abs(actual - expected) <= 150L) break
                val fromX = xForTime(actual)
                val correctionPx = ((expected - actual).toDouble() / 8_020.0 * usableWidth).toInt()
                    .let { delta -> if (delta == 0) if (expected > actual) 1 else -1 else delta }
                val toX = (fromX + correctionPx).coerceIn(bounds.left + 1, bounds.right - 2)
                device.swipe(fromX, bounds.centerY(), toX, bounds.centerY(), 30)
                device.waitForIdle()
                SystemClock.sleep(300)
                actual = projectPositionMs(requireProjectTime(device))
                android.util.Log.i(
                    "TextPreviewSeekZoomTest",
                    "stage=seek attempt=$attempt target=$expected actual=$actual fromX=$fromX toX=$toX bounds=$bounds"
                )
            }
            android.util.Log.i(
                "TextPreviewSeekZoomTest",
                "stage=seek-complete target=$expected actual=$actual zoom=" +
                    requireObject(device, By.descContains("Zaman çizelgesi yakınlaştırma"), "timeline zoom label").contentDescription
            )
            assertTrue(
                "Timeline seek did not reach requested time $expected ms; actual=$actual ms",
                abs(actual - expected) <= 150L
            )
            device.waitForIdle()
            SystemClock.sleep(250)
        }

        seekPlayheadTo(4_200L)
        val afterSeekMs = projectPositionMs(requireProjectTime(device))
        assertTrue(
            "Timeline did not seek into the text's active interval near 4.2 s: " + afterSeekMs,
            afterSeekMs in 3_500L..4_900L
        )
        val previewTextSelector = By.clazz("android.widget.TextView").text("SEEK ZOOM TEXT")
        assertTrue(
            "Preview text disappeared after seeking within its active interval",
            device.wait(Until.hasObject(previewTextSelector), 5_000)
        )

        val videoLaneLabel = requireObject(device, By.desc("Zaman çizelgesi etiketi: VIDEO"), "VIDEO lane label for text zoom")
        val laneLabelBounds = videoLaneLabel.visibleBounds
        val laneY = laneLabelBounds.centerY()
        val laneScroll = device.findObjects(By.clazz("android.widget.HorizontalScrollView"))
            .firstOrNull {
                val bounds = it.visibleBounds
                bounds.top <= laneY && bounds.bottom >= laneY && bounds.left >= laneLabelBounds.right - 2
            } ?: throw AssertionError("Could not find the VIDEO timeline scroll viewport for text zoom")
        val zoomLabelBefore = requireObject(
            device, By.descContains("Zaman çizelgesi yakınlaştırma"), "timeline zoom label before text zoom"
        ).contentDescription
        val zoomBefore = zoomPercent(zoomLabelBefore)
        val timeBeforePinch = projectPositionMs(requireProjectTime(device))
        laneScroll.pinchOpen(0.85f, 60)
        device.waitForIdle()
        SystemClock.sleep(350)
        val zoomLabelAfter = requireObject(
            device, By.descContains("Zaman çizelgesi yakınlaştırma"), "timeline zoom label after text zoom"
        ).contentDescription
        val zoomAfter = zoomPercent(zoomLabelAfter)
        val timeAfterPinch = projectPositionMs(requireProjectTime(device))
        assertTrue(
            "Pinch zoom did not change timeline scale: " + zoomLabelBefore + " -> " + zoomLabelAfter,
            zoomAfter > zoomBefore
        )
        assertTrue(
            "Pinch zoom changed the playhead time while checking text: " +
                timeBeforePinch + " -> " + timeAfterPinch,
            abs(timeBeforePinch - timeAfterPinch) <= 150L
        )
        assertTrue(
            "Preview text disappeared after timeline pinch-zoom",
            device.wait(Until.hasObject(previewTextSelector), 5_000)
        )

        seekPlayheadTo(7_100L)
        val afterOutsideSeekMs = projectPositionMs(requireProjectTime(device))
        assertTrue(
            "Timeline did not seek outside the text's active interval: " + afterOutsideSeekMs,
            afterOutsideSeekMs >= 6_500L
        )
        assertTrue(
            "Timed preview text stayed visible after seeking beyond 6 seconds",
            device.wait(Until.gone(previewTextSelector), 5_000)
        )
        seekPlayheadTo(4_200L)
        val afterReturnSeekMs = projectPositionMs(requireProjectTime(device))
        assertTrue(
            "Timeline did not return to the text's active interval: " + afterReturnSeekMs,
            afterReturnSeekMs in 3_500L..4_900L
        )
        assertTrue(
            "Timed preview text did not reappear after returning to its active interval",
            device.wait(Until.hasObject(previewTextSelector), 5_000)
        )

        assertTrue(
            "Could not revisit the text input after seek/zoom",
            panelScroll.scrollIntoView(UiSelector().description("Metin katmanı metin girişi"))
        )
        val textValueAfter = requireObject(
            device, By.desc("Metin katmanı metin girişi"), "text input after seek/zoom"
        ).text
        assertTrue(
            "Text content changed during timeline seek/zoom: " + textValueBefore + " -> " + textValueAfter,
            textValueAfter == textValueBefore
        )
        assertTrue(
            "Could not revisit the start-time label after seek/zoom",
            panelScroll.scrollIntoView(UiSelector().textContains("Yazı başlangıcı:"))
        )
        val startLabelAfter = requireObject(
            device, By.textContains("Yazı başlangıcı:"), "text start label after seek/zoom"
        ).text
        assertTrue(
            "Text start setting changed during seek/zoom: " + startLabelBefore + " -> " + startLabelAfter,
            startLabelAfter == startLabelBefore
        )
        assertTrue(
            "Could not revisit the end-time label after seek/zoom",
            panelScroll.scrollIntoView(UiSelector().textContains("Yazı bitişi:"))
        )
        val endLabelAfter = requireObject(
            device, By.textContains("Yazı bitişi:"), "text end label after seek/zoom"
        ).text
        assertTrue(
            "Text end setting changed during seek/zoom: " + endLabelBefore + " -> " + endLabelAfter,
            endLabelAfter == endLabelBefore
        )
        assertTrue(
            "Could not revisit text-size controls after seek/zoom",
            panelScroll.scrollIntoView(UiSelector().text("A+"))
        )
        val sizeLabelAfter = requireObject(
            device, By.textContains("Yazı boyutu:"), "text size label after seek/zoom"
        ).text
        assertTrue(
            "Text size changed during timeline seek/zoom: " + sizeLabelBefore + " -> " + sizeLabelAfter,
            sizeLabelAfter == sizeLabelBefore
        )
        val activePreviewBounds = requireObject(
            device, previewTextSelector, "active preview text after returning to its interval"
        ).visibleBounds
        assertTrue(
            "Top-positioned preview text moved out of the upper preview area after seek/zoom: " +
                activePreviewBounds,
            activePreviewBounds.top < device.displayHeight * 0.35f
        )
        android.util.Log.i(
            "TextPreviewSeekZoomTest",
            "stage=verified initial=3000 afterSeek=" + afterSeekMs +
                " zoom=" + zoomBefore + "->" + zoomAfter +
                " afterPinch=" + timeAfterPinch + " outside=" + afterOutsideSeekMs +
                " returned=" + afterReturnSeekMs + " text=" + textValueAfter +
                " size=" + sizeLabelAfter + " start=" + startLabelAfter + " end=" + endLabelAfter
        )
    }

    @Test
    fun textTimingAndSizeControlsUpdatePreview() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val packageName = "com.mathmotor.video.next"
        val fixtureUri = "file:///data/user/0/$packageName/files/editor-test-video.mp4"

        // Ignore markers from earlier smoke steps; this test must import its own fixture.
        device.executeShellCommand("logcat -c")
        device.executeShellCommand(
            "am start -n $packageName/com.mathmotor.video.EditorTestEntryActivity " +
                "--es video_uri $fixtureUri --es draft_id 44444444-4444-4444-8444-444444444444"
        )
        assertTrue(
            "Editor did not show the video lane for text test",
            device.wait(Until.hasObject(By.desc("Zaman çizelgesi etiketi: VIDEO")), 20_000)
        )
        assertTrue(
            "Project time label did not appear for text test",
            device.wait(Until.hasObject(By.desc("Oynatma zamanı ve toplam video süresi")), 20_000)
        )
        assertTrue("Text test sample clip was not loaded", awaitClipAdded(device))
        device.waitForIdle()
        SystemClock.sleep(250)

        val textTab = device.findObject(By.text("T\nMetin"))
            ?: device.findObject(By.clazz("android.widget.Button").textContains("Metin"))
            ?: throw AssertionError("Could not find the Metin tab for text test")
        textTab.click()
        assertTrue(
            "Text layer panel did not appear",
            device.wait(Until.hasObject(By.text("METİN KATMANLARI")), 8_000)
        )

        val panelScroll = UiScrollable(UiSelector().className("android.widget.ScrollView"))
        assertTrue(
            "Could not scroll the add-text control into the compact tool panel",
            panelScroll.scrollIntoView(UiSelector().description("Yeni metin katmanı ekle"))
        )
        device.waitForIdle()
        val addText = requireObject(device, By.desc("Yeni metin katmanı ekle"), "new-text-layer control")
        addText.click()
        val inputQuery = By.desc("Metin katmanı metin girişi")
        assertTrue(
            "Keyboard-aware editor layout did not activate",
            device.wait(Until.hasObject(By.text("Bitti ✓")), 8_000)
        )
        // The app itself must keep the newly focused field in the visible panel.
        // Do not call scrollIntoView here: that can race the editor's own post-IME
        // layout correction and accidentally scroll the field away again.
        SystemClock.sleep(700)
        assertTrue(
            "Text editor input did not remain accessible after keyboard resize",
            device.wait(Until.hasObject(inputQuery), 8_000)
        )
        device.waitForIdle()
        val input = requireObject(device, inputQuery, "text editor input")
        input.setText("TIMING TEST")
        device.pressBack()
        device.waitForIdle()
        SystemClock.sleep(300)

        val overlaySelector = By.clazz("android.widget.TextView").text("TIMING TEST")
        assertTrue(
            "New text did not appear in the video preview",
            device.wait(Until.hasObject(overlaySelector), 8_000)
        )
        val overlayBefore = requireObject(device, overlaySelector, "preview text overlay").visibleBounds
        assertTrue("Preview text has no visible bounds: $overlayBefore", overlayBefore.width() > 0 && overlayBefore.height() > 0)

        // Scroll only the editor's vertical panel; the actual preview overlay remains visible.
        assertTrue("Could not scroll to the text-size controls", panelScroll.scrollIntoView(UiSelector().text("A+")))
        device.waitForIdle()
        requireObject(device, By.text("A+"), "increase text size button").click()
        device.waitForIdle()
        SystemClock.sleep(250)

        val sizeLabel = requireObject(device, By.textContains("Yazı boyutu:"), "text-size value label")
        val sizeBefore = Regex("(\\d+)").find(sizeLabel.text)?.groupValues?.get(1)?.toInt()
            ?: throw AssertionError("Could not parse current text size: ${sizeLabel.text}")
        val previewAfterSize = requireObject(device, overlaySelector, "preview text after size change").visibleBounds
        assertTrue(
            "Increasing text size did not change the preview bounds: before=$overlayBefore after=$previewAfterSize",
            previewAfterSize.width() > overlayBefore.width() || previewAfterSize.height() > overlayBefore.height()
        )
        android.util.Log.i(
            "TextLayerInteractionTest",
            "stage=size-changed label=${sizeLabel.text} before=$overlayBefore after=$previewAfterSize"
        )

        assertTrue(
            "Could not scroll the text start-time slider into view",
            panelScroll.scrollIntoView(UiSelector().description("Yazı başlangıç zamanı"))
        )
        device.waitForIdle()
        val startSeek = requireObject(device, By.desc("Yazı başlangıç zamanı"), "text start-time seek bar")
        val startBounds = startSeek.visibleBounds
        assertTrue("Text start-time seek bar is not visible: $startBounds", startBounds.width() > 100 && startBounds.height() > 0)
        val startLabelBefore = requireObject(device, By.textContains("Yazı başlangıcı:"), "text start-time label").text
        device.swipe(
            startBounds.left + 4,
            startBounds.centerY(),
            startBounds.left + (startBounds.width() * 0.20f).toInt(),
            startBounds.centerY(),
            25
        )
        device.waitForIdle()
        SystemClock.sleep(250)
        val startLabelAfter = requireObject(device, By.textContains("Yazı başlangıcı:"), "updated text start-time label").text
        assertTrue(
            "Changing the text start-time slider did not update its label: $startLabelBefore -> $startLabelAfter",
            startLabelAfter != startLabelBefore
        )
        android.util.Log.i("TextLayerInteractionTest", "stage=start-time before=$startLabelBefore after=$startLabelAfter")

        assertTrue(
            "Could not scroll the text end-time slider into view",
            panelScroll.scrollIntoView(UiSelector().description("Yazı bitiş zamanı"))
        )
        device.waitForIdle()
        val endSeek = requireObject(device, By.desc("Yazı bitiş zamanı"), "text end-time seek bar")
        val endBounds = endSeek.visibleBounds
        assertTrue("Text end-time seek bar is not visible: $endBounds", endBounds.width() > 100 && endBounds.height() > 0)
        val endLabelBefore = requireObject(device, By.textContains("Yazı bitişi:"), "text end-time label").text
        device.swipe(
            endBounds.right - 4,
            endBounds.centerY(),
            endBounds.left + (endBounds.width() * 0.78f).toInt(),
            endBounds.centerY(),
            25
        )
        device.waitForIdle()
        SystemClock.sleep(250)
        val endLabelAfter = requireObject(device, By.textContains("Yazı bitişi:"), "updated text end-time label").text
        assertTrue(
            "Changing the text end-time slider did not update its label: $endLabelBefore -> $endLabelAfter",
            endLabelAfter != endLabelBefore
        )
        android.util.Log.i("TextLayerInteractionTest", "stage=end-time before=$endLabelBefore after=$endLabelAfter")

        // Slider checks above do not prove that the actual timeline strip handles work.
        // Drag the visible start/end handles and assert that the matching labels change.
        val startHandleSelector = By.desc("Yazı başlangıcını sürükle")
        assertTrue(
            "Could not find the text-strip start handle",
            device.wait(Until.hasObject(startHandleSelector), 5_000)
        )
        assertTrue(
            "Could not bring the text start-time label back into view before strip drag",
            panelScroll.scrollIntoView(UiSelector().textContains("Yazı başlangıcı:"))
        )
        device.waitForIdle()
        // Resolve the handle bounds only after the panel has finished scrolling/layout.
        val startHandle = requireObject(device, startHandleSelector, "text-strip start handle")
        val startHandleBounds = startHandle.visibleBounds
        assertTrue(
            "Text-strip start handle has no usable hit target after layout: $startHandleBounds",
            startHandleBounds.width() > 0 && startHandleBounds.height() > 0
        )
        val stripStartBefore = requireObject(
            device, By.textContains("Yazı başlangıcı:"), "start label before strip drag"
        ).text
        device.swipe(
            startHandleBounds.centerX(),
            startHandleBounds.centerY(),
            startHandleBounds.centerX() + 45,
            startHandleBounds.centerY(),
            20
        )
        device.waitForIdle()
        SystemClock.sleep(250)
        val stripStartAfter = requireObject(
            device, By.textContains("Yazı başlangıcı:"), "start label after strip drag"
        ).text
        assertTrue(
            "Dragging the text-strip start handle did not change the start time: $stripStartBefore -> $stripStartAfter",
            stripStartBefore != stripStartAfter
        )

        // The text-strip body deliberately consumes drags to move the strip itself and
        // disallows its parent from intercepting the gesture. Do not swipe on that nested
        // lane to reveal the other handle. The VIDEO lane is synchronized with every timeline
        // lane and its filmstrip has already been verified to scroll the timeline.
        // Move in bounded increments and stop as soon as the end handle becomes accessible;
        // jumping straight to the absolute end can move past the handle at high timeline zoom.
        val endHandleSelector = By.desc("Yazı bitişini sürükle")
        var endHandleVisible = device.wait(Until.hasObject(endHandleSelector), 500)
        val videoLane = requireObject(
            device,
            By.desc("Zaman çizelgesi satırı: VIDEO"),
            "VIDEO timeline lane for synchronized scroll"
        ).visibleBounds
        val swipeY = videoLane.centerY()
        val swipeFromX = videoLane.left + videoLane.width() * 3 / 4
        val swipeToX = videoLane.left + videoLane.width() / 2
        for (attempt in 0 until 8) {
            if (endHandleVisible) break
            device.swipe(swipeFromX, swipeY, swipeToX, swipeY, 25)
            device.waitForIdle()
            SystemClock.sleep(150)
            endHandleVisible = device.wait(Until.hasObject(endHandleSelector), 500)
            android.util.Log.i(
                "TextLayerInteractionTest",
                "stage=reveal-end-handle attempt=${attempt + 1} visible=$endHandleVisible"
            )
        }
        assertTrue(
            "Could not find the text-strip end handle after bounded VIDEO-lane scrolls",
            endHandleVisible
        )
        val endHandle = requireObject(device, endHandleSelector, "text-strip end handle")
        val endHandleBounds = endHandle.visibleBounds
        assertTrue(
            "Text-strip end handle has no usable hit target: $endHandleBounds",
            endHandleBounds.width() > 0 && endHandleBounds.height() > 0
        )
        // The timeline gestures can change the compact editor's visible viewport.
        // Explicitly bring the value label back into the text panel viewport instead of
        // assuming the previous slider scroll position still exposes it to UiAutomator.
        assertTrue(
            "Could not scroll the end-time label into view before strip drag",
            panelScroll.scrollIntoView(UiSelector().textContains("Yazı bitişi:"))
        )
        device.waitForIdle()
        val stripEndBefore = requireObject(
            device, By.textContains("Yazı bitişi:"), "end label before strip drag"
        ).text
        device.swipe(
            endHandleBounds.centerX(),
            endHandleBounds.centerY(),
            endHandleBounds.centerX() - 45,
            endHandleBounds.centerY(),
            20
        )
        device.waitForIdle()
        SystemClock.sleep(250)
        assertTrue(
            "Could not scroll the updated end-time label into view after strip drag",
            panelScroll.scrollIntoView(UiSelector().textContains("Yazı bitişi:"))
        )
        device.waitForIdle()
        val stripEndAfter = requireObject(
            device, By.textContains("Yazı bitişi:"), "end label after strip drag"
        ).text
        assertTrue(
            "Dragging the text-strip end handle did not change the end time: $stripEndBefore -> $stripEndAfter",
            stripEndBefore != stripEndAfter
        )
        android.util.Log.i(
            "TextLayerInteractionTest",
            "stage=strip-handles start=$stripStartBefore->$stripStartAfter end=$stripEndBefore->$stripEndAfter"
        )
    }

    @Test
    fun unifiedUndoRedoRestoresTextAndAudioEdits() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val packageName = "com.mathmotor.video.next"
        val fixtureUri = "file:///data/user/0/$packageName/files/editor-test-video.mp4"

        device.executeShellCommand("logcat -c")
        device.executeShellCommand(
            "am start -n $packageName/com.mathmotor.video.EditorTestEntryActivity " +
                "--es video_uri $fixtureUri --es draft_id 66666666-6666-4666-8666-666666666666"
        )
        assertTrue(
            "Editor did not show the video lane for unified undo test",
            device.wait(Until.hasObject(By.desc("Zaman çizelgesi etiketi: VIDEO")), 20_000)
        )
        assertTrue("Unified undo test clip was not loaded", awaitClipAdded(device))
        device.waitForIdle()

        // Create a text layer and edit its value. Undo should first restore the empty
        // layer, then remove that layer; Redo must reapply both changes in order.
        val textTab = requireObject(device, By.desc("Metin araçları sekmesi"), "Metin tab")
        textTab.click()
        val panelScroll = UiScrollable(UiSelector().className("android.widget.ScrollView")).setAsVerticalList()
        assertTrue(
            "Could not reveal new-text control",
            panelScroll.scrollIntoView(UiSelector().description("Yeni metin katmanı ekle"))
        )
        requireObject(device, By.desc("Yeni metin katmanı ekle"), "new text layer").click()
        assertTrue(
            "Text input did not appear",
            device.wait(Until.hasObject(By.desc("Metin katmanı metin girişi")), 8_000)
        )
        requireObject(device, By.desc("Metin katmanı metin girişi"), "text input").setText("UNDO REDO TEXT")
        device.pressBack()
        device.waitForIdle()

        val clipTab = requireObject(device, By.desc("Klip araçları sekmesi"), "Klip tab")
        clipTab.click()
        device.waitForIdle()
        assertTrue(
            "Global Undo button is not visible outside the tool panel",
            device.wait(Until.hasObject(By.desc("Düzenleme geri al")), 4_000)
        )
        device.waitForIdle()
        val undo = requireObject(device, By.desc("Düzenleme geri al"), "global Undo button")
        val redo = requireObject(device, By.desc("Düzenleme yinele"), "global Redo button")
        assertTrue("Global Undo button has no visible hit target: ${undo.visibleBounds}", undo.visibleBounds.height() > 0)
        assertTrue("Global Redo button has no visible hit target: ${redo.visibleBounds}", redo.visibleBounds.height() > 0)

        undo.click()
        device.waitForIdle()
        assertTrue(
            "Undo did not restore the blank text layer",
            device.wait(Until.hasObject(By.text("YAZI 1")), 4_000)
        )
        assertTrue(
            "Undo of text entry left the typed overlay visible",
            !device.hasObject(By.text("UNDO REDO TEXT"))
        )

        undo.click()
        device.waitForIdle()
        assertTrue(
            "Undo did not remove the newly added text layer",
            device.wait(Until.hasObject(By.text("+ Metin ekle")), 4_000)
        )

        redo.click()
        device.waitForIdle()
        assertTrue("Redo did not restore the text layer", device.wait(Until.hasObject(By.text("YAZI 1")), 4_000))
        redo.click()
        device.waitForIdle()
        assertTrue(
            "Redo did not restore edited text to the preview",
            device.wait(Until.hasObject(By.text("UNDO REDO TEXT")), 4_000)
        )

        // Exercise the same history with a real SeekBar touch, not a synthetic
        // direct state assignment, so start/stop tracking callbacks are included.
        val audioTab = requireObject(device, By.desc("Ses araçları sekmesi"), "Ses tab")
        audioTab.click()
        assertTrue("Audio panel did not appear", device.wait(Until.hasObject(By.text("SES KATMANLARI")), 4_000))
        assertTrue(
            "Could not reveal music volume slider",
            panelScroll.scrollIntoView(UiSelector().description("Müzik ses düzeyi"))
        )
        device.waitForIdle()
        val musicSeek = requireObject(device, By.desc("Müzik ses düzeyi"), "music volume seek bar")
        val bounds = musicSeek.visibleBounds
        assertTrue("Music volume slider is not visible: $bounds", bounds.width() > 100 && bounds.height() > 0)
        val currentX = bounds.left + (bounds.width() * 0.45f).toInt()
        val targetX = bounds.left + (bounds.width() * 0.25f).toInt()
        device.swipe(currentX, bounds.centerY(), targetX, bounds.centerY(), 25)
        device.waitForIdle()
        val changedLabel = requireObject(device, By.textContains("Müzik sesi:"), "changed music volume label").text
        val changedValue = Regex("(\\d+)").find(changedLabel)?.groupValues?.get(1)?.toInt()
            ?: throw AssertionError("Could not read adjusted music volume: $changedLabel")
        assertTrue("Touch did not change music volume: $changedLabel", changedValue != 45)

        clipTab.click()
        requireObject(device, By.desc("Düzenleme geri al"), "Undo audio volume").click()
        audioTab.click()
        assertTrue(
            "Could not reveal restored music volume label after Undo",
            panelScroll.scrollIntoView(UiSelector().text("Müzik sesi: 45%"))
        )
        device.waitForIdle()
        val restoredLabel = requireObject(device, By.text("Müzik sesi: 45%"), "restored music volume label")
        assertTrue("Undo did not restore music volume 45%", restoredLabel.text == "Müzik sesi: 45%")

        requireObject(device, By.desc("Düzenleme yinele"), "Redo audio volume").click()
        assertTrue(
            "Redo did not restore the adjusted music volume",
            device.wait(Until.hasObject(By.text("Müzik sesi: $changedValue%")), 4_000)
        )
        android.util.Log.i(
            "UnifiedUndoRedoTest",
            "stage=verified textUndoRedo=true musicVolume=45->$changedValue->45->$changedValue"
        )
    }

    @Test
    fun sourceAudioToggleAndEffectVolumeUseUndoRedoHistory() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val packageName = "com.mathmotor.video.next"
        val fixtureUri = "file:///data/user/0/$packageName/files/editor-test-video.mp4"

        device.executeShellCommand("logcat -c")
        device.executeShellCommand(
            "am start -n $packageName/com.mathmotor.video.EditorTestEntryActivity " +
                "--es video_uri $fixtureUri --es draft_id 88888888-8888-4888-8888-888888888888"
        )
        assertTrue(
            "Editor did not show the video lane for audio history test",
            device.wait(Until.hasObject(By.desc("Zaman çizelgesi etiketi: VIDEO")), 20_000)
        )
        assertTrue("Audio history test clip was not loaded", awaitClipAdded(device))
        device.waitForIdle()

        val panelScroll = UiScrollable(
            UiSelector().className("android.widget.ScrollView")
        ).setAsVerticalList()
        val audioTab = requireObject(device, By.desc("Ses araçları sekmesi"), "audio tools tab")
        val clipTab = requireObject(device, By.desc("Klip araçları sekmesi"), "clip tools tab")
        audioTab.click()
        assertTrue(
            "Audio panel did not appear for source-audio toggle",
            device.wait(Until.hasObject(By.text("SES KATMANLARI")), 4_000)
        )
        assertTrue(
            "Could not reveal source-audio checkbox",
            panelScroll.scrollIntoView(UiSelector().text("Videonun kendi sesini koru"))
        )
        val sourceAudio = requireObject(
            device, By.text("Videonun kendi sesini koru"), "source-audio checkbox"
        )
        assertTrue("Source audio should be enabled for a fresh project", sourceAudio.isChecked)
        sourceAudio.click()
        device.waitForIdle()
        assertTrue(
            "Source-audio checkbox did not switch off",
            !requireObject(device, By.text("Videonun kendi sesini koru"), "muted source-audio checkbox").isChecked
        )

        clipTab.click()
        requireObject(device, By.desc("Düzenleme geri al"), "Undo source-audio toggle").click()
        audioTab.click()
        assertTrue(
            "Undo did not restore source audio",
            panelScroll.scrollIntoView(UiSelector().text("Videonun kendi sesini koru"))
        )
        assertTrue(
            "Undo did not re-enable source audio",
            requireObject(device, By.text("Videonun kendi sesini koru"), "restored source-audio checkbox").isChecked
        )

        clipTab.click()
        requireObject(device, By.desc("Düzenleme yinele"), "Redo source-audio toggle").click()
        audioTab.click()
        assertTrue(
            "Could not reveal source audio after Redo",
            panelScroll.scrollIntoView(UiSelector().text("Videonun kendi sesini koru"))
        )
        assertTrue(
            "Redo did not disable source audio again",
            !requireObject(device, By.text("Videonun kendi sesini koru"), "redone source-audio checkbox").isChecked
        )

        // Exercise two distinct physical slider gestures. Intermediate move events
        // within each swipe must be grouped as one history entry, while the two swipes
        // must remain separate entries that Undo/Redo can traverse in order.
        assertTrue(
            "Could not reveal the effect-volume slider",
            panelScroll.scrollIntoView(UiSelector().description("Efekt ses düzeyi"))
        )
        device.waitForIdle()
        val effectSeek = requireObject(device, By.desc("Efekt ses düzeyi"), "effect volume seek bar")
        val bounds = effectSeek.visibleBounds
        assertTrue("Effect-volume slider has no usable hit target: $bounds", bounds.width() > 100 && bounds.height() > 0)

        device.swipe(
            bounds.left + (bounds.width() * 0.95f).toInt(),
            bounds.centerY(),
            bounds.left + (bounds.width() * 0.70f).toInt(),
            bounds.centerY(),
            30
        )
        device.waitForIdle()
        val firstLabel = requireObject(device, By.textContains("Efekt sesi:"), "first effect-volume label").text
        val firstGestureValue = Regex("(\\d+)").find(firstLabel)?.groupValues?.get(1)?.toInt()
            ?: throw AssertionError("Could not read first effect-volume gesture value: $firstLabel")
        assertTrue(
            "First effect-volume gesture did not reach an intermediate value: $firstLabel",
            firstGestureValue in 55..85
        )

        val secondStartX = bounds.left + (bounds.width() * (firstGestureValue / 100f)).toInt()
        device.swipe(
            secondStartX,
            bounds.centerY(),
            bounds.left + (bounds.width() * 0.30f).toInt(),
            bounds.centerY(),
            30
        )
        device.waitForIdle()
        val secondLabel = requireObject(device, By.textContains("Efekt sesi:"), "second effect-volume label").text
        val secondGestureValue = Regex("(\\d+)").find(secondLabel)?.groupValues?.get(1)?.toInt()
            ?: throw AssertionError("Could not read second effect-volume gesture value: $secondLabel")
        assertTrue(
            "Second separate effect-volume gesture did not move lower than the first: " +
                "$firstGestureValue -> $secondGestureValue ($secondLabel)",
            secondGestureValue <= firstGestureValue - 10
        )

        // One Undo should erase all intermediate values from gesture 2, returning to
        // gesture 1's committed value exactly. A second Undo returns to the baseline.
        clipTab.click()
        requireObject(device, By.desc("Düzenleme geri al"), "Undo second effect-volume gesture").click()
        audioTab.click()
        assertTrue(
            "Could not reveal first gesture value after Undo",
            panelScroll.scrollIntoView(UiSelector().text("Efekt sesi: $firstGestureValue%"))
        )
        assertTrue(
            "One Undo did not restore the first gesture's committed value",
            device.wait(Until.hasObject(By.text("Efekt sesi: $firstGestureValue%")), 4_000)
        )

        clipTab.click()
        requireObject(device, By.desc("Düzenleme geri al"), "Undo first effect-volume gesture").click()
        audioTab.click()
        assertTrue(
            "Could not reveal baseline effect volume after second Undo",
            panelScroll.scrollIntoView(UiSelector().text("Efekt sesi: 100%"))
        )
        assertTrue(
            "Second Undo did not restore baseline effect volume",
            device.wait(Until.hasObject(By.text("Efekt sesi: 100%")), 4_000)
        )

        // Redo must recover the two distinct committed values in the same order.
        clipTab.click()
        requireObject(device, By.desc("Düzenleme yinele"), "Redo first effect-volume gesture").click()
        audioTab.click()
        assertTrue(
            "Could not reveal first gesture value after Redo",
            panelScroll.scrollIntoView(UiSelector().text("Efekt sesi: $firstGestureValue%"))
        )
        assertTrue(
            "First Redo did not restore the first gesture's committed value",
            device.wait(Until.hasObject(By.text("Efekt sesi: $firstGestureValue%")), 4_000)
        )

        clipTab.click()
        requireObject(device, By.desc("Düzenleme yinele"), "Redo second effect-volume gesture").click()
        audioTab.click()
        assertTrue(
            "Could not reveal second gesture value after Redo",
            panelScroll.scrollIntoView(UiSelector().text("Efekt sesi: $secondGestureValue%"))
        )
        assertTrue(
            "Second Redo did not restore the second gesture's committed value",
            device.wait(Until.hasObject(By.text("Efekt sesi: $secondGestureValue%")), 4_000)
        )
        android.util.Log.i(
            "AudioHistoryTest",
            "stage=verified separateGestureGrouping baseline=100 first=$firstGestureValue second=$secondGestureValue " +
                "undo=[$firstGestureValue,100] redo=[$firstGestureValue,$secondGestureValue]"
        )
    }


    @Test
    fun oneContinuousTextTimingDragCreatesOneUndoEntryAndRedoRestoresIt() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val packageName = context.packageName
        val fixture = java.io.File(context.filesDir, "editor-test-video.mp4")
        assertTrue("Gesture-history fixture is missing", fixture.isFile && fixture.length() > 0L)

        val draftId = java.util.UUID.randomUUID().toString()
        val clip = org.json.JSONObject()
            .put("uri", Uri.fromFile(fixture).toString())
            .put("title", "GESTURE_HISTORY_FIXTURE")
            .put("durationMs", 8_020L)
            .put("startMs", 0L)
            .put("endMs", 8_020L)
        val textLayer = org.json.JSONObject()
            .put("id", 975L)
            .put("text", "GESTURE HISTORY")
            .put("startMs", 1_000L)
            .put("endMs", 6_000L)
            .put("timingCustomized", true)
            .put("position", "TOP")
            .put("sizeSp", 32)
            .put("xFraction", 0.5)
            .put("yFraction", 0.16)
            .put("colorPreset", "CYAN")
            .put("isBold", false)
        val project = org.json.JSONObject()
            .put("format", "MathMotorVideoProject")
            .put("version", 1)
            .put("clips", org.json.JSONArray().put(clip))
            .put("selectedClipIndex", 0)
            .put("timelinePlayheadMs", 3_000L)
            .put("timelineZoom", 1.0)
            .put("timelineHorizontalScrollMs", 0L)
            .put("textLayers", org.json.JSONArray().put(textLayer))
            .put("selectedTextLayerId", 975L)
            .put("sourceAudioVolume", 100)
            .put("musicVolume", 45)
            .put("soundVolume", 100)
            .put("keepSourceAudio", true)
        assertTrue(
            "Could not seed continuous-gesture history project",
            VideoProjectDraftStore.save(context, draftId, "Continuous gesture history", project)
        )

        device.executeShellCommand("logcat -c")
        device.executeShellCommand(
            "am start --activity-clear-task -n " + packageName +
                "/com.mathmotor.video.EditorTestEntryActivity --es draft_id " + draftId
        )
        val restoreDeadline = SystemClock.uptimeMillis() + 20_000L
        var restored = false
        var restoreLog = ""
        while (SystemClock.uptimeMillis() < restoreDeadline) {
            restoreLog = device.executeShellCommand("logcat -d -s EditorSmokeTest:I").takeLast(1_800)
            if (restoreLog.contains("EDITOR_SMOKE_DRAFT_RESTORED id=" + draftId)) {
                restored = true
                break
            }
            SystemClock.sleep(200)
        }
        assertTrue("Gesture-history draft was not restored: $restoreLog", restored)
        assertTrue("Gesture-history preview text did not appear",
            device.wait(Until.hasObject(By.text("GESTURE HISTORY")), 8_000))

        val textTab = device.findObject(By.text("T\nMetin"))
            ?: device.findObject(By.clazz("android.widget.Button").textContains("Metin"))
            ?: throw AssertionError("Could not open the Metin tab for gesture-history test")
        textTab.click()
        val panelScroll = UiScrollable(UiSelector().className("android.widget.ScrollView")).setAsVerticalList()
        assertTrue(
            "Could not reveal the text start-time slider for gesture-history test",
            panelScroll.scrollIntoView(UiSelector().description("Yazı başlangıç zamanı"))
        )
        device.waitForIdle()
        val startSeek = requireObject(device, By.desc("Yazı başlangıç zamanı"), "gesture-history text start slider")
        val startBounds = startSeek.visibleBounds
        assertTrue("Text start slider has no usable drag target: $startBounds", startBounds.width() > 100)
        val startLabel = requireObject(device, By.textContains("Yazı başlangıcı:"), "initial text start label")
        val before = startLabel.text
        assertTrue("Seeded text start time was not 1 second: $before", before.contains("00:01"))

        // One continuous drag intentionally crosses several SeekBar progress values.
        device.swipe(
            startBounds.left + 4,
            startBounds.centerY(),
            startBounds.left + (startBounds.width() * 0.42f).toInt(),
            startBounds.centerY(),
            35
        )
        device.waitForIdle()
        SystemClock.sleep(250)
        val after = requireObject(device, By.textContains("Yazı başlangıcı:"), "text start after continuous drag").text
        assertTrue("Continuous drag did not change the start time: $before -> $after", before != after)
        android.util.Log.i("GestureHistoryTest", "stage=drag before=$before after=$after")

        val undo = requireObject(device, By.desc("Düzenleme geri al"), "Undo after one continuous drag")
        assertTrue("Undo was not enabled after the continuous drag", undo.isEnabled)
        undo.click()
        device.waitForIdle()
        SystemClock.sleep(200)
        val restoredBefore = requireObject(device, By.textContains("Yazı başlangıcı:"), "text start after Undo").text
        assertEquals("One Undo should restore the value from before the drag", before, restoredBefore)
        assertFalse(
            "One continuous drag created multiple Undo entries",
            requireObject(device, By.desc("Düzenleme geri al"), "Undo after reverting the drag").isEnabled
        )

        val redo = requireObject(device, By.desc("Düzenleme yinele"), "Redo after one continuous drag")
        assertTrue("Redo was not enabled after Undo", redo.isEnabled)
        redo.click()
        device.waitForIdle()
        SystemClock.sleep(200)
        val restoredAfter = requireObject(device, By.textContains("Yazı başlangıcı:"), "text start after Redo").text
        assertEquals("Redo should restore the value from after the drag", after, restoredAfter)
        android.util.Log.i(
            "GestureHistoryTest",
            "stage=verified oneGestureOneUndo=true before=$before after=$after redo=$restoredAfter"
        )
    }

    @Test
    fun soundEffectAddStartTimeRemoveAndUndoRedoWork() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val packageName = "com.mathmotor.video.next"
        val videoFixture = java.io.File(context.filesDir, "editor-test-video.mp4")
        assertTrue("Sound-effect history video fixture is missing", videoFixture.isFile && videoFixture.length() > 0L)

        val fileName = "MM_EFFECT_HISTORY_${System.currentTimeMillis()}.wav"
        val wavFixture = java.io.File(context.cacheDir, fileName)
        writeToneWav(wavFixture, sampleRate = 48_000, durationMs = 3_000, frequencyHz = 880.0, amplitude = 12_000)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "audio/wav")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val effectDocument = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw AssertionError("Could not publish the deterministic audio fixture to Downloads")
        var documentPickerOpened = false
        try {
            val output = context.contentResolver.openOutputStream(effectDocument, "w")
                ?: throw AssertionError("Could not open the published audio fixture")
            output.use { stream -> wavFixture.inputStream().use { source -> source.copyTo(stream) } }
            val publishValues = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            assertTrue(
                "Could not make the deterministic audio fixture visible to the document picker",
                context.contentResolver.update(effectDocument, publishValues, null, null) > 0
            )

            device.executeShellCommand("logcat -c")
            device.executeShellCommand(
                "am start -n $packageName/com.mathmotor.video.EditorTestEntryActivity " +
                    "--es video_uri ${Uri.fromFile(videoFixture)} --es draft_id effect-history-${System.currentTimeMillis()}"
            )
            assertTrue(
                "Editor did not open for sound-effect lifecycle test",
                device.wait(Until.hasObject(By.desc("Zaman çizelgesi etiketi: VIDEO")), 20_000)
            )
            assertTrue("Sound-effect lifecycle test clip was not loaded", awaitClipAdded(device))
            device.waitForIdle()

            val audioTab = requireObject(device, By.desc("Ses araçları sekmesi"), "sound-effect audio tab")
            val clipTab = requireObject(device, By.desc("Klip araçları sekmesi"), "sound-effect history tab")
            val panelScroll = UiScrollable(UiSelector().className("android.widget.ScrollView")).setAsVerticalList()
            audioTab.click()
            assertTrue(
                "Audio panel did not appear for sound-effect lifecycle test",
                device.wait(Until.hasObject(By.text("SES KATMANLARI")), 4_000)
            )
            assertTrue(
                "Could not reveal the sound-effect add button",
                panelScroll.scrollIntoView(UiSelector().text("Efekt ekle"))
            )
            requireObject(device, By.text("Efekt ekle"), "add sound-effect button").click()
            documentPickerOpened = true

            // Recent intentionally omits files that have not been opened before.
            // Prefer the provider's accessible root button, but retain a coordinate
            // fallback for the API-35 compact DocumentsUI toolbar whose root button
            // has no exposed content description.
            val rootsButton = findObjectByAny(
                device,
                By.descContains("Show roots"),
                By.descContains("Show navigation drawer"),
                By.descContains("navigation drawer"),
                By.res("com.google.android.documentsui:id/roots")
            )
            if (rootsButton != null) {
                rootsButton.click()
            } else {
                device.click(28, 48)
            }
            device.waitForIdle()

            var downloads: UiObject2? = null
            val downloadsDeadline = SystemClock.uptimeMillis() + 10_000L
            while (downloads == null && SystemClock.uptimeMillis() < downloadsDeadline) {
                downloads = device.findObject(By.text("Downloads")) ?: device.findObject(By.text("Download"))
                if (downloads == null) SystemClock.sleep(200)
            }
            assertTrue(
                "DocumentsUI root navigation did not expose Downloads. Visible text=" +
                    device.findObjects(By.clazz("android.widget.TextView"))
                        .mapNotNull { runCatching { it.text?.toString() }.getOrNull() }
                        .filter { it.isNotBlank() }.distinct().take(80).joinToString(" | "),
                downloads != null
            )
            // JUnit evidence identifies the stale access at downloads!!.click(), not the
            // WAV row. Reacquire the folder through UiObject's selector-based click path.
            val downloadsName = if (device.hasObject(By.text("Downloads"))) "Downloads" else "Download"
            assertTrue(
                "DocumentsUI Downloads folder could not be clicked",
                device.findObject(UiSelector().text(downloadsName)).click()
            )
            device.waitForIdle()

            var fileRow: UiObject2? = null
            val fileDeadline = SystemClock.uptimeMillis() + 10_000L
            while (fileRow == null && SystemClock.uptimeMillis() < fileDeadline) {
                fileRow = device.findObject(By.text(fileName))
                if (fileRow == null) SystemClock.sleep(200)
            }
            if (fileRow == null) {
                val hierarchy = java.io.File(context.cacheDir, "effect-history-picker.xml")
                device.dumpWindowHierarchy(hierarchy)
                throw AssertionError(
                    "The audio fixture was not listed in DocumentsUI Downloads. " +
                        "Visible text=" + device.findObjects(By.clazz("android.widget.TextView"))
                            .mapNotNull { runCatching { it.text?.toString() }.getOrNull() }
                            .filter { it.isNotBlank() }.distinct().take(80).joinToString(" | ") +
                        " hierarchy=" + hierarchy.takeIf { it.isFile }?.readText()?.take(8_000)
                )
            }
            // Use the selector-backed UiObject API here: DocumentsUI replaces UiObject2
            // nodes while its row/selection state updates, even after a fresh query.
            val fileRowSelector = UiSelector().text(fileName)
            assertTrue("Audio fixture row has no visible hit target", device.wait(Until.hasObject(By.text(fileName)), 3_000))
            assertTrue("Audio fixture row could not be clicked", device.findObject(fileRowSelector).click())

            var effectAdded = device.wait(Until.hasObject(By.textContains(fileName)), 3_000)
            if (!effectAdded) {
                val openAction = findObjectByAny(
                    device,
                    By.res("com.google.android.documentsui:id/action_menu_item_open"),
                    By.res("com.android.documentsui:id/action_menu_item_open"),
                    By.res("com.google.android.documentsui:id/menu_open"),
                    By.res("com.android.documentsui:id/menu_open"),
                    By.text("Open"),
                    By.text("OPEN"),
                    By.text("Aç"),
                    By.text("AÇ")
                )
                openAction?.click()
                effectAdded = device.wait(Until.hasObject(By.textContains(fileName)), 15_000)
            }
            assertTrue("Choosing an audio document did not add a visible sound-effect layer", effectAdded)
            assertTrue(
                "The newly added effect did not expose its duration",
                device.wait(Until.hasObject(By.textContains("00:03")), 5_000)
            )

            // Adding an effect is one edit: Undo removes it; Redo restores it.
            clipTab.click()
            val undo = requireObject(device, By.desc("Düzenleme geri al"), "Undo sound-effect add")
            assertTrue("Undo was disabled after adding an effect", undo.isEnabled)
            undo.click()
            audioTab.click()
            assertTrue("Undo did not remove the newly added sound effect",
                device.wait(Until.gone(By.textContains(fileName)), 4_000))
            clipTab.click()
            val redo = requireObject(device, By.desc("Düzenleme yinele"), "Redo sound-effect add")
            assertTrue("Redo was disabled after undoing sound-effect add", redo.isEnabled)
            redo.click()
            audioTab.click()
            assertTrue("Redo did not restore the sound-effect layer",
                device.wait(Until.hasObject(By.textContains(fileName)), 4_000))

            assertTrue(
                "Could not reveal the sound-effect start-time slider",
                panelScroll.scrollIntoView(UiSelector().description("Efekt başlangıç zamanı"))
            )
            device.waitForIdle()
            val startSeek = requireObject(device, By.desc("Efekt başlangıç zamanı"), "sound-effect start-time seek bar")
            val seekBounds = startSeek.visibleBounds
            assertTrue(
                "Sound-effect start-time slider has no usable hit target: $seekBounds",
                seekBounds.width() > 100 && seekBounds.height() > 0 && startSeek.isEnabled
            )
            val startLabelSelector = By.textContains("Efekt başlangıcı:")
            val initialStartLabel = requireObject(device, startLabelSelector, "initial sound-effect start-time label").text
            device.swipe(
                seekBounds.left + (seekBounds.width() * 0.12f).toInt(),
                seekBounds.centerY(),
                seekBounds.left + (seekBounds.width() * 0.56f).toInt(),
                seekBounds.centerY(),
                30
            )
            device.waitForIdle()
            val changedStartLabel = requireObject(device, startLabelSelector, "changed sound-effect start-time label").text
            assertTrue(
                "Dragging the sound-effect start-time slider did not change the visible start time: " +
                    "$initialStartLabel -> $changedStartLabel",
                changedStartLabel != initialStartLabel
            )

            // One real slider gesture must be one history step; Undo/Redo restores its exact label.
            clipTab.click()
            requireObject(device, By.desc("Düzenleme geri al"), "Undo sound-effect start-time gesture").click()
            audioTab.click()
            assertTrue(
                "Undo did not restore the original sound-effect start time",
                panelScroll.scrollIntoView(UiSelector().text(initialStartLabel))
            )
            assertTrue(
                "Undo restored a different sound-effect start-time value",
                requireObject(device, By.text(initialStartLabel), "restored original effect start time").text == initialStartLabel
            )
            clipTab.click()
            requireObject(device, By.desc("Düzenleme yinele"), "Redo sound-effect start-time gesture").click()
            audioTab.click()
            assertTrue(
                "Redo did not restore the changed sound-effect start time",
                panelScroll.scrollIntoView(UiSelector().text(changedStartLabel))
            )
            assertTrue(
                "Redo restored a different sound-effect start-time value",
                requireObject(device, By.text(changedStartLabel), "redone effect start time").text == changedStartLabel
            )

            // Removing the layer is independently undoable and redoable, preserving its
            // selected file and adjusted start time in the restored snapshot.
            assertTrue(
                "Could not reveal the sound-effect remove button",
                panelScroll.scrollIntoView(UiSelector().text("Efekti kaldır"))
            )
            requireObject(device, By.text("Efekti kaldır"), "remove sound-effect button").click()
            assertTrue("Removing the effect left its file name visible",
                device.wait(Until.gone(By.textContains(fileName)), 4_000))
            clipTab.click()
            requireObject(device, By.desc("Düzenleme geri al"), "Undo sound-effect removal").click()
            audioTab.click()
            assertTrue("Undo did not restore the removed sound effect",
                device.wait(Until.hasObject(By.textContains(fileName)), 4_000))
            assertTrue(
                "Undo of removal did not restore the effect's adjusted start time",
                panelScroll.scrollIntoView(UiSelector().text(changedStartLabel))
            )
            clipTab.click()
            requireObject(device, By.desc("Düzenleme yinele"), "Redo sound-effect removal").click()
            audioTab.click()
            assertTrue("Redo did not remove the sound-effect layer again",
                device.wait(Until.gone(By.textContains(fileName)), 4_000))
            android.util.Log.i(
                "SoundEffectHistoryTest",
                "stage=verified addUndoRedo=true startTimeUndoRedo=true removeUndoRedo=true " +
                    "startBefore=$initialStartLabel startAfter=$changedStartLabel"
            )
        } finally {
            // Do not leave the system document picker on top if an assertion fails;
            // later instrumentation cases launch their own editor activity/task.
            if (documentPickerOpened) {
                runCatching { device.pressBack() }
                runCatching { device.waitForIdle() }
            }
            runCatching { context.contentResolver.delete(effectDocument, null, null) }
            runCatching { wavFixture.delete() }
        }
    }

    @Test
    fun savedDraftReopensTextLayerAndClipState() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val packageName = context.packageName
        val fixture = java.io.File(context.filesDir, "editor-test-video.mp4")
        assertTrue("Draft round-trip fixture is missing", fixture.isFile && fixture.length() > 0L)

        val draftId = java.util.UUID.randomUUID().toString()
        val clip = org.json.JSONObject()
            .put("uri", Uri.fromFile(fixture).toString())
            .put("title", fixture.name)
            .put("durationMs", 8020L)
            .put("startMs", 500L)
            .put("endMs", 7520L)
        val textLayer = org.json.JSONObject()
            .put("id", 931L)
            .put("text", "ROUND TRIP TEXT")
            .put("startMs", 0L)
            .put("endMs", 6500L)
            .put("timingCustomized", true)
            .put("position", "BOTTOM")
            .put("sizeSp", 32)
            .put("xFraction", 0.5)
            .put("yFraction", 0.82)
            .put("colorPreset", "YELLOW")
            .put("isBold", true)
        val project = org.json.JSONObject()
            .put("format", "MathMotorVideoProject")
            .put("version", 1)
            .put("clips", org.json.JSONArray().put(clip))
            .put("selectedClipIndex", 0)
            .put("timelinePlayheadMs", 1000L)
            .put("timelineZoom", 2.0)
            .put("timelineHorizontalScrollMs", 500L)
            .put("musicUri", org.json.JSONObject.NULL)
            .put("musicTitle", org.json.JSONObject.NULL)
            .put("soundUri", org.json.JSONObject.NULL)
            .put("soundTitle", org.json.JSONObject.NULL)
            .put("soundDurationMs", 0L)
            .put("soundStartMs", 0L)
            .put("textLayers", org.json.JSONArray().put(textLayer))
            .put("selectedTextLayerId", 931L)
            .put("sourceAudioVolume", 100)
            .put("musicVolume", 45)
            .put("soundVolume", 100)
            .put("keepSourceAudio", true)
            .put("exportName", "RoundTripTest")

        assertTrue(
            "Could not persist the test editor project to the local draft store",
            VideoProjectDraftStore.save(context, draftId, "Round-trip test", project)
        )
        val persisted = VideoProjectDraftStore.load(context, draftId)
            ?: throw AssertionError("Saved draft could not be read back")
        assertTrue(
            "Draft JSON did not preserve the text-layer payload",
            persisted.optJSONArray("textLayers")?.optJSONObject(0)?.optString("text") == "ROUND TRIP TEXT"
        )

        device.executeShellCommand(
            "am start -n $packageName/com.mathmotor.video.EditorTestEntryActivity " +
                "--es draft_id $draftId"
        )
        assertTrue(
            "Editor did not open from the saved draft",
            device.wait(Until.hasObject(By.textContains("Taslak açıldı")), 20_000)
        )
        assertTrue(
            "Saved draft did not restore the source video lane",
            device.wait(Until.hasObject(By.desc("Zaman çizelgesi etiketi: VIDEO")), 8_000)
        )
        assertTrue(
            "Saved clip trim bounds were not restored (expected 7.02 s)",
            device.wait(Until.hasObject(By.textContains("00:07.02")), 8_000)
        )

        val textTab = device.findObject(By.clazz("android.widget.Button").textContains("Metin"))
            ?: throw AssertionError("Could not open the text panel after draft restore")
        textTab.click()
        assertTrue(
            "Saved draft did not restore the text overlay in preview",
            device.wait(Until.hasObject(By.clazz("android.widget.TextView").text("ROUND TRIP TEXT")), 10_000)
        )
        // Check the editable field while it is still near the top of the panel.
        // Scrolling to the size controls first would move this EditText outside the
        // visible viewport, where UiAutomator intentionally cannot find it.
        val restoredInput = requireObject(
            device,
            By.desc("Metin katmanı metin girişi"),
            "restored text input before scrolling the panel"
        )
        assertTrue(
            "Saved text content was not restored to the editable control",
            restoredInput.text == "ROUND TRIP TEXT"
        )

        // The size controls continue below the compact editor viewport. Scroll the
        // actual vertical panel before looking for the size label in the accessibility tree.
        val textControlsScroll = UiScrollable(
            UiSelector().className("android.widget.ScrollView")
        ).setAsVerticalList()
        assertTrue(
            "Could not scroll the text panel to its size controls",
            textControlsScroll.scrollIntoView(UiSelector().textContains("Yazı boyutu:"))
        )
        val restoredSize = requireObject(
            device,
            By.textContains("Yazı boyutu:"),
            "restored text-size label after scrolling text panel"
        ).text
        assertTrue(
            "Saved text size was not restored (expected 32 sp): $restoredSize",
            Regex("\\b32\\b").containsMatchIn(restoredSize)
        )
        android.util.Log.i(
            "ProjectRoundTripTest",
            "stage=verified draftId=$draftId clips=1 textLayer=ROUND_TRIP_TEXT sizeSp=32"
        )
    }

    @Test
    fun manualJsonSaveAsThenCloseAndReopenPreservesProjectState() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val packageName = context.packageName
        val fixture = java.io.File(context.filesDir, "editor-test-video.mp4")
        val secondFixture = java.io.File(context.filesDir, "editor-test-video-second.mp4")
        assertTrue("Manual JSON round-trip fixture is missing", fixture.isFile && fixture.length() > 0L)
        assertTrue("Second manual JSON round-trip fixture is missing", secondFixture.isFile && secondFixture.length() > 0L)

        val fileName = "MM_JSON_RT_${System.currentTimeMillis()}.json"
        val draftId = java.util.UUID.randomUUID().toString()
        val fixtureUri = Uri.fromFile(fixture).toString()
        val secondFixtureUri = Uri.fromFile(secondFixture).toString()
        val firstClip = org.json.JSONObject()
            .put("uri", fixtureUri)
            .put("title", "FIRST_ORDERED_CLIP")
            .put("durationMs", 8020L)
            .put("startMs", 500L)
            .put("endMs", 7520L)
        val secondClip = org.json.JSONObject()
            .put("uri", secondFixtureUri)
            .put("title", "SECOND_ORDERED_CLIP")
            .put("durationMs", 3000L)
            .put("startMs", 200L)
            .put("endMs", 2700L)
        val textLayer = org.json.JSONObject()
            .put("id", 932L)
            .put("text", "MANUAL JSON TEXT")
            .put("startMs", 7000L)
            .put("endMs", 9000L)
            .put("timingCustomized", true)
            .put("position", "TOP")
            .put("sizeSp", 32)
            .put("xFraction", 0.23)
            .put("yFraction", 0.16)
            .put("colorPreset", "CYAN")
            .put("isBold", false)
        val project = org.json.JSONObject()
            .put("format", "MathMotorVideoProject")
            .put("version", 1)
            .put("clips", org.json.JSONArray().put(firstClip).put(secondClip))
            .put("selectedClipIndex", 1)
            .put("timelinePlayheadMs", 7700L)
            .put("timelineZoom", 2.0)
            // For the fixed-center playhead, saved horizontal scroll and playhead describe the same project time.
            .put("timelineHorizontalScrollMs", 7700L)
            .put("musicUri", fixtureUri)
            .put("musicTitle", "ROUND_TRIP_MUSIC")
            .put("soundUri", secondFixtureUri)
            .put("soundTitle", "ROUND_TRIP_EFFECT")
            .put("soundDurationMs", 3000L)
            .put("soundStartMs", 750L)
            .put("textLayers", org.json.JSONArray().put(textLayer))
            .put("selectedTextLayerId", 932L)
            .put("sourceAudioVolume", 72)
            .put("musicVolume", 37)
            .put("soundVolume", 66)
            .put("keepSourceAudio", true)
            .put("exportName", "ManualJsonRoundTrip")

        assertTrue(
            "Could not seed the manual-file-picker test project",
            VideoProjectDraftStore.save(context, draftId, "Manual JSON test seed", project)
        )

        // Ensure a real home activity exists underneath the test editor so Back genuinely
        // closes the editing session before a second, fresh editor opens the JSON file.
        device.executeShellCommand("am start -n $packageName/com.mathmotor.video.MainActivity")
        assertTrue(
            "The app home screen did not open before the manual JSON test",
            device.wait(Until.hasObject(By.textContains("Boş proje oluştur")), 12_000)
        )
        device.executeShellCommand(
            "am start -n $packageName/com.mathmotor.video.EditorTestEntryActivity --es draft_id $draftId"
        )
        assertTrue(
            "The seeded editor project did not open",
            device.wait(Until.hasObject(By.textContains("Taslak açıldı")), 20_000)
        )
        assertTrue(
            "Seeded combined trimmed duration was not present before Save As",
            device.wait(Until.hasObject(By.textContains("00:09.52")), 8_000)
        )

        val exportTab = device.findObject(By.text("Dışa aktar ›"))
            ?: throw AssertionError("Could not open export/project panel for manual Save As")
        exportTab.click()
        assertTrue(
            "The project/export panel did not open",
            device.wait(Until.hasObject(By.text("PROJE / DIŞA AKTARMA")), 8_000)
        )
        val panelScroller = UiScrollable(UiSelector().className("android.widget.ScrollView"))
            .setAsVerticalList()
        assertTrue(
            "Could not scroll to the actual project Save As button",
            panelScroller.scrollIntoView(UiSelector().text("Projeyi farklı kaydet (.JSON)"))
        )
        requireObject(
            device,
            By.text("Projeyi farklı kaydet (.JSON)"),
            "manual JSON Save As button"
        ).click()

        // Exercise the platform document picker rather than writing the JSON directly in the test.
        val filenameSelectors = arrayOf(
            By.res("com.google.android.documentsui:id/filename"),
            By.res("com.android.documentsui:id/filename"),
            By.clazz("android.widget.EditText")
        )
        val filenameDeadline = SystemClock.uptimeMillis() + 12_000L
        var filenameField: UiObject2? = null
        while (SystemClock.uptimeMillis() < filenameDeadline && filenameField == null) {
            filenameField = findObjectByAny(device, *filenameSelectors)
            if (filenameField == null) SystemClock.sleep(200)
        }
        if (filenameField == null) {
            val hierarchy = java.io.File(context.cacheDir, "manual-json-save-picker.xml")
            device.dumpWindowHierarchy(hierarchy)
            throw AssertionError(
                "Android Create Document picker did not expose a filename field. " +
                    "Hierarchy: ${hierarchy.takeIf { it.isFile }?.readText()?.take(4500)}"
            )
        }
        filenameField.setText(fileName)
        val saveButton = findObjectByAny(
            device,
            By.res("com.google.android.documentsui:id/save"),
            By.res("com.android.documentsui:id/save"),
            By.text("Save"),
            By.text("SAVE"),
            By.text("Kaydet"),
            By.text("KAYDET")
        ) ?: throw AssertionError("Could not find Save in Android's Create Document picker")
        saveButton.click()
        assertTrue(
            "The app did not confirm that manual JSON Save As completed",
            device.wait(Until.hasObject(By.textContains("PROJE KAYDEDİLDİ")), 15_000)
        )
        android.util.Log.i("ManualJsonRoundTripTest", "stage=json-saved filename=$fileName")

        // Close the original editor. Re-enter via the app's home button; no direct loadProject
        // bridge is used for the reopening step.
        device.pressBack()
        device.waitForIdle()
        if (!device.wait(Until.hasObject(By.textContains("Boş proje oluştur")), 6_000)) {
            device.executeShellCommand("am start -n $packageName/com.mathmotor.video.MainActivity")
        }
        assertTrue(
            "Back did not return to the home screen after saving JSON",
            device.wait(Until.hasObject(By.textContains("Boş proje oluştur")), 12_000)
        )
        requireObject(
            device,
            By.textContains("Boş proje oluştur"),
            "home screen empty-project action after closing editor"
        ).click()
        assertTrue(
            "A fresh editor did not open after closing the original session",
            device.wait(Until.hasObject(By.text("Dışa aktar ›")), 15_000)
        )

        val freshExportTab = requireObject(device, By.text("Dışa aktar ›"), "fresh editor export tab")
        freshExportTab.click()
        val freshPanelScroller = UiScrollable(UiSelector().className("android.widget.ScrollView"))
            .setAsVerticalList()
        assertTrue(
            "Could not scroll to the Open Project action in the fresh editor",
            freshPanelScroller.scrollIntoView(UiSelector().text("Kayıtlı projeyi aç"))
        )
        device.executeShellCommand("logcat -c")
        requireObject(device, By.text("Kayıtlı projeyi aç"), "open saved JSON button").click()

        // DocumentsUI can report the file from the background list while its navigation
        // drawer is still covering the screen. Never tap that obscured row: explicitly open
        // Downloads after opening the roots drawer, then reacquire the row from the active page.
        val openFileSelector = By.text(fileName)

        fun returnToHomeForFollowingTests() {
            // This is test-only cleanup. Closing DocumentsUI first prevents a failed picker
            // assertion from leaving an external activity above the home-screen screenshot test.
            runCatching { device.executeShellCommand("am force-stop com.google.android.documentsui") }
            repeat(3) {
                if (device.wait(Until.hasObject(By.text("Videoların")), 500)) return
                runCatching { device.pressBack() }
                device.waitForIdle()
            }
            runCatching {
                device.executeShellCommand("am start -n $packageName/com.mathmotor.video.MainActivity")
            }
            device.wait(Until.hasObject(By.text("Videoların")), 5_000)
        }

        fun throwWithPickerDiagnostics(message: String): Nothing {
            val hierarchy = java.io.File(context.cacheDir, "manual-json-open-result.xml")
            device.dumpWindowHierarchy(hierarchy)
            val visibleUiText = device.findObjects(By.clazz("android.widget.TextView"))
                .mapNotNull { runCatching { it.text?.toString() }.getOrNull() }
                .filter { it.isNotBlank() }
                .distinct()
                .take(100)
                .joinToString(" | ")
            val fullHierarchy = hierarchy.takeIf { it.isFile }?.readText().orEmpty()
            val actionableNodes = fullHierarchy.lineSequence()
                .filter { it.contains("clickable=\"true\"") || it.contains("resource-id=\"com.google.android.documentsui:id/action_menu_item_open\"") }
                .take(60)
                .joinToString("\n")
            returnToHomeForFollowingTests()
            throw AssertionError(
                "$message Visible UI: $visibleUiText. " +
                    "Picker actions: $actionableNodes. " +
                    "Hierarchy: ${fullHierarchy.take(16000)}"
            )
        }

        var savedFile = device.findObject(openFileSelector)
        if (savedFile == null) {
            val rootsButton = findObjectByAny(
                device,
                By.descContains("Show roots"),
                By.descContains("Show navigation drawer"),
                By.descContains("navigation drawer")
            )
            if (rootsButton != null) {
                try {
                    rootsButton.click()
                } catch (_: androidx.test.uiautomator.StaleObjectException) {
                    // DocumentsUI recreates its toolbar when the drawer is opened.
                }
                device.waitForIdle()

                // Choose Downloads even if the filename is still exposed by the obscured
                // Recent list. Otherwise the test may mistake a covered TextView for a
                // visible row and send its tap into the navigation drawer.
                var downloadsChosen = false
                val downloadsDeadline = SystemClock.uptimeMillis() + 6_000L
                while (!downloadsChosen && SystemClock.uptimeMillis() < downloadsDeadline) {
                    val downloads = device.findObject(By.text("Downloads"))
                        ?: device.findObject(By.text("Download"))
                    if (downloads != null) {
                        try {
                            downloads.click()
                            downloadsChosen = true
                        } catch (_: androidx.test.uiautomator.StaleObjectException) {
                            // Reacquire the row on the next iteration.
                        }
                    }
                    if (!downloadsChosen) SystemClock.sleep(200)
                }
                device.waitForIdle()
                SystemClock.sleep(250)
            }

            val fileDeadline = SystemClock.uptimeMillis() + 8_000L
            while (SystemClock.uptimeMillis() < fileDeadline && savedFile == null) {
                savedFile = device.findObject(openFileSelector)
                if (savedFile == null) SystemClock.sleep(200)
            }
        }

        if (savedFile == null) {
            throwWithPickerDiagnostics(
                "The JSON saved through Create Document was not visible in the active Open Document folder: $fileName."
            )
        }

        fun tapSavedDocumentRow() {
            val currentFile = device.findObject(openFileSelector)
                ?: throw AssertionError("Saved JSON disappeared from Open Document picker: $fileName")
            val bounds = currentFile.visibleBounds
            android.util.Log.i(
                "ManualJsonRoundTripTest",
                "stage=json-file-found filename=$fileName bounds=$bounds"
            )
            check(bounds.width() > 0 && bounds.height() > 0) {
                "Saved JSON filename has no visible hit target: $bounds"
            }
            device.click(bounds.centerX(), bounds.centerY())
        }

        try {
            tapSavedDocumentRow()
        } catch (_: androidx.test.uiautomator.StaleObjectException) {
            device.waitForIdle()
            tapSavedDocumentRow()
        }

        // Some DocumentsUI versions select a file on the first tap and expose a separate
        // Open action. Prefer that explicit action instead of repeatedly tapping the title.
        var restorationReported =
            device.wait(Until.hasObject(By.textContains("PROJE AÇILDI")), 2_500)
        if (!restorationReported) {
            val openAction = findObjectByAny(
                device,
                By.res("com.google.android.documentsui:id/action_menu_item_open"),
                By.res("com.android.documentsui:id/action_menu_item_open"),
                By.res("com.google.android.documentsui:id/menu_open"),
                By.res("com.android.documentsui:id/menu_open"),
                By.text("Open"),
                By.text("OPEN"),
                By.text("Aç"),
                By.text("AÇ")
            )
            if (openAction != null) {
                android.util.Log.i("ManualJsonRoundTripTest", "stage=click-explicit-open-action")
                try {
                    openAction.click()
                } catch (_: androidx.test.uiautomator.StaleObjectException) {
                    findObjectByAny(
                        device,
                        By.res("com.google.android.documentsui:id/action_menu_item_open"),
                        By.res("com.android.documentsui:id/action_menu_item_open"),
                        By.text("Open"),
                        By.text("OPEN"),
                        By.text("Aç"),
                        By.text("AÇ")
                    )?.click()
                }
            } else {
                // If the picker implements row selection without an exposed Open button, use
                // the selected document's Enter action as a last resort.
                android.util.Log.i("ManualJsonRoundTripTest", "stage=no-explicit-open-action-press-enter")
                device.pressEnter()
            }
            device.waitForIdle()
            restorationReported = device.wait(
                Until.hasObject(By.textContains("PROJE AÇILDI")),
                12_000
            )
        }

        if (!restorationReported) {
            throwWithPickerDiagnostics(
                "Opening saved JSON did not restore project (the picker may not have returned a URI)."
            )
        }

        // The fixture also seeds the playhead and zoom. Check user-visible timeline state,
        // not only the serialized fields or the restored clip duration.
        assertTrue(
            "Manual JSON did not restore timeline zoom to 200%",
            device.wait(Until.hasObject(By.descContains("Zaman çizelgesi yakınlaştırma 200%")), 8_000)
        )
        assertTrue(
            "Manual JSON did not restore the combined trimmed duration for both clips",
            device.wait(Until.hasObject(By.textContains("00:09.52")), 8_000)
        )
        val restoredPlayheadMs = projectPositionMs(requireProjectTime(device))
        assertTrue(
            "Manual JSON did not restore the 7.70-second playhead: $restoredPlayheadMs",
            abs(restoredPlayheadMs - 7700L) <= 250L
        )
        val restoredZoomDescription = requireObject(
            device,
            By.descContains("Zaman çizelgesi yakınlaştırma"),
            "restored timeline zoom indicator"
        ).contentDescription.toString()
        assertTrue(
            "Manual JSON did not restore 200% timeline zoom: $restoredZoomDescription",
            zoomPercent(restoredZoomDescription) == 200
        )
        val restoreLogOutput = device.executeShellCommand("logcat -d -s ProjectPersistenceTest:I")
        val restoreLog = restoreLogOutput.lineSequence()
            .lastOrNull { it.contains("PROJECT_JSON_RESTORED") }
            ?: throw AssertionError("Manual JSON open did not emit a project restoration marker: $restoreLogOutput")
        assertTrue(
            "Manual JSON did not preserve clip order and the two trim ranges: $restoreLog",
            restoreLog.contains("clips=FIRST_ORDERED_CLIP:500-7520,SECOND_ORDERED_CLIP:200-2700")
        )
        assertTrue(
            "Manual JSON did not preserve the playhead and zoom: $restoreLog",
            restoreLog.contains("playheadMs=7700 zoomPercent=200")
        )
        val restoredScrollMatch = Regex("scrollMs=(\\d+)").find(restoreLog)
            ?: throw AssertionError("Manual JSON restore did not report the saved timeline scroll: $restoreLog")
        val restoredScrollMs = restoredScrollMatch.groupValues[1].toLong()
        assertTrue(
            "Manual JSON did not preserve the 7.7-second playhead-aligned timeline scroll: $restoredScrollMs ms; $restoreLog",
            restoredScrollMs in 7_000L..8_100L
        )
        assertTrue(
            "Manual JSON did not preserve text timing, position, size, colour, bold state, and placement: $restoreLog",
            restoreLog.contains("textLayers=7000-9000:TOP:32:CYAN:false:0.23:0.16")
        )
        assertTrue(
            "Manual JSON did not restore the distinct music/effect source URIs and effect start: $restoreLog",
            restoreLog.contains("soundStartMs=750 musicFixtureMatches=true soundFixtureMatches=true")
        )
        assertTrue(
            "Manual JSON did not restore the preview text",
            device.wait(Until.hasObject(By.clazz("android.widget.TextView").text("MANUAL JSON TEXT")), 10_000)
        )

        val restoredTextTab = device.findObject(By.clazz("android.widget.Button").textContains("Metin"))
            ?: throw AssertionError("Could not inspect restored manual JSON text settings")
        restoredTextTab.click()
        val restoredTextInput = requireObject(
            device,
            By.desc("Metin katmanı metin girişi"),
            "editable text from manually reopened JSON"
        )
        assertTrue(
            "Manual JSON did not restore the editable text",
            restoredTextInput.text == "MANUAL JSON TEXT"
        )
        val textPanelScroller = UiScrollable(UiSelector().className("android.widget.ScrollView"))
            .setAsVerticalList()
        assertTrue(
            "Manual JSON did not restore the text start time",
            textPanelScroller.scrollIntoView(UiSelector().text("Yazı başlangıcı: 00:07.00"))
        )
        assertTrue(
            "Manual JSON did not expose the restored text end-time control",
            textPanelScroller.scrollIntoView(UiSelector().text("Yazı bitişi: 00:09.00"))
        )
        assertTrue(
            "Manual JSON did not restore the text end time",
            device.wait(Until.hasObject(By.text("Yazı bitişi: 00:09.00")), 5_000)
        )
        assertTrue(
            "Could not scroll to the saved top-position control",
            textPanelScroller.scrollIntoView(UiSelector().text("Üst"))
        )
        assertTrue(
            "Manual JSON did not restore the text layer’s top position",
            requireObject(device, By.text("Üst"), "top text-position radio").isChecked
        )
        assertTrue(
            "Could not scroll to the restored cyan text-colour control",
            textPanelScroller.scrollIntoView(UiSelector().text("Camgöbeği"))
        )
        assertTrue(
            "Manual JSON did not restore the cyan text colour",
            requireObject(device, By.text("Camgöbeği"), "cyan text-colour radio").isChecked
        )
        assertTrue(
            "Could not scroll to the bold-text control",
            textPanelScroller.scrollIntoView(UiSelector().text("Kalın yazı"))
        )
        assertTrue(
            "Manual JSON did not restore the text layer’s non-bold style",
            !requireObject(device, By.text("Kalın yazı"), "bold text checkbox").isChecked
        )
        assertTrue(
            "Could not scroll to text size after manual JSON restore",
            textPanelScroller.scrollIntoView(UiSelector().textContains("Yazı boyutu:"))
        )
        assertTrue(
            "Manual JSON did not restore text size 32 sp",
            device.wait(Until.hasObject(By.textContains("Yazı boyutu: 32")), 5_000)
        )

        val restoredAudioTab = device.findObject(By.clazz("android.widget.Button").textContains("Ses"))
            ?: throw AssertionError("Could not inspect restored manual JSON audio settings")
        restoredAudioTab.click()
        val audioPanelScroller = UiScrollable(UiSelector().className("android.widget.ScrollView"))
            .setAsVerticalList()
        // The two labels sit at different vertical positions in the audio panel. Querying
        // a ScrollView child before scrolling can miss it when the previous tab left the
        // shared editor panel at a nonzero scroll offset.
        assertTrue(
            "Could not scroll to the restored music reference",
            audioPanelScroller.scrollIntoView(UiSelector().textContains("ROUND_TRIP_MUSIC"))
        )
        assertTrue(
            "Manual JSON did not restore the music reference",
            device.wait(Until.hasObject(By.textContains("ROUND_TRIP_MUSIC")), 8_000)
        )
        assertTrue(
            "Could not scroll to the restored sound-effect reference",
            audioPanelScroller.scrollIntoView(UiSelector().textContains("ROUND_TRIP_EFFECT"))
        )
        assertTrue(
            "Manual JSON did not restore the sound-effect reference",
            device.wait(Until.hasObject(By.textContains("ROUND_TRIP_EFFECT")), 8_000)
        )
        assertTrue(
            "Manual JSON did not restore music volume 37%",
            audioPanelScroller.scrollIntoView(UiSelector().text("Müzik sesi: 37%"))
        )
        assertTrue(
            "Manual JSON did not restore effect volume 66%",
            audioPanelScroller.scrollIntoView(UiSelector().text("Efekt sesi: 66%"))
        )
        assertTrue(
            "Manual JSON did not restore source volume 72%",
            audioPanelScroller.scrollIntoView(UiSelector().text("Orijinal video sesi: 72%"))
        )
        android.util.Log.i(
            "ManualJsonRoundTripTest",
            "stage=verified filename=$fileName clips=FIRST_ORDERED_CLIP:7020,SECOND_ORDERED_CLIP:2500 " +
                "playheadMs=$restoredPlayheadMs zoom=200 scrollMs=$restoredScrollMs " +
                "text=MANUAL_JSON_TEXT interval=7000-9000 position=TOP sizeSp=32 color=CYAN bold=false " +
                "music=ROUND_TRIP_MUSIC/37 effect=ROUND_TRIP_EFFECT/66 sourceAudio=72"
        )
    }

    @Test
    fun twoClipsExportToOneMp4InOrder() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val resolver = context.contentResolver
        val device = UiDevice.getInstance(instrumentation)
        val packageName = context.packageName
        val firstFile = java.io.File(context.filesDir, "editor-test-video.mp4")
        val secondFile = java.io.File(context.filesDir, "editor-test-video-second.mp4")
        assertTrue("First test video is missing", firstFile.isFile && firstFile.length() > 0L)
        assertTrue("Second green test video is missing", secondFile.isFile && secondFile.length() > 0L)
        val firstSize = firstFile.length()
        val secondSize = secondFile.length()
        val firstHash = sha256(firstFile)
        val secondHash = sha256(secondFile)
        val firstUri = "file://${firstFile.absolutePath}"
        val secondUri = "file://${secondFile.absolutePath}"
        val exportName = "MathMotor_two_clip_test_${System.currentTimeMillis()}"
        val draftId = java.util.UUID.randomUUID().toString()

        device.executeShellCommand("logcat -c")
        device.executeShellCommand(
            "am start -n $packageName/com.mathmotor.video.EditorTestEntryActivity " +
                "--es video_uri_1 $firstUri --es video_uri_2 $secondUri --es draft_id $draftId"
        )

        val importDeadline = SystemClock.uptimeMillis() + 35_000L
        var clipsReady = false
        while (SystemClock.uptimeMillis() < importDeadline) {
            val log = device.executeShellCommand("logcat -d -s EditorSmokeTest:I")
            if (log.contains("EDITOR_SMOKE_CLIPS_ADDED count=2 skipped=0")) {
                clipsReady = true
                break
            }
            if (log.contains("EDITOR_SMOKE_MULTI_CLIP_SKIPPED") ||
                log.contains("EDITOR_SMOKE_MULTI_IMPORT_EMPTY") ||
                log.contains("EDITOR_SMOKE_MULTI_METADATA_ERROR")
            ) {
                throw AssertionError("The multi-clip import failed before export: $log")
            }
            SystemClock.sleep(350)
        }
        if (!clipsReady) {
            val log = device.executeShellCommand("logcat -d -s EditorSmokeTest:I EditorSmokeTest:E")
            throw AssertionError("The editor did not load both source clips in order within 35s. Import markers:\n$log")
        }
        device.waitForIdle()

        val exportTab = device.findObject(By.text("Dışa aktar ›"))
            ?: throw AssertionError("Could not find the editor's export tab")
        exportTab.click()
        assertTrue(
            "The export panel did not open",
            device.wait(Until.hasObject(By.text("PROJE / DIŞA AKTARMA")), 8_000)
        )
        val nameInput = requireObject(device, By.clazz("android.widget.EditText"), "export filename input")
        nameInput.setText(exportName)

        // The export controls extend below the compact emulator viewport. Scroll the
        // editor's single vertical panel to the real export button instead of assuming
        // it is already in the accessibility tree after the filename field is edited.
        val panelScroller = UiScrollable(UiSelector().className("android.widget.ScrollView"))
        panelScroller.setAsVerticalList()
        assertTrue(
            "Could not scroll the export panel to the MP4 export button",
            panelScroller.scrollIntoView(UiSelector().text("MP4 DIŞA AKTAR"))
        )
        device.waitForIdle()
        requireObject(device, By.text("MP4 DIŞA AKTAR"), "MP4 export button").click()

        val exportDeadline = SystemClock.uptimeMillis() + 120_000L
        var exportSaved = false
        while (SystemClock.uptimeMillis() < exportDeadline) {
            val log = device.executeShellCommand("logcat -d -s MultiClipExport:I")
            if (log.contains("MULTICLIP_EXPORT_SAVED name=$exportName")) {
                exportSaved = true
                break
            }
            if (log.contains("MULTICLIP_EXPORT_TRANSFORM_FAILED name=$exportName") ||
                log.contains("MULTICLIP_EXPORT_SAVE_FAILED name=$exportName")
            ) throw AssertionError("MP4 export failed: $log")
            SystemClock.sleep(500)
        }
        assertTrue("MP4 export did not save within 120 seconds", exportSaved)
        android.util.Log.i("MultiClipExportTest", "stage=export-success name=$exportName")

        var exportedUri: Uri? = null
        val queryDeadline = SystemClock.uptimeMillis() + 30_000L
        while (exportedUri == null && SystemClock.uptimeMillis() < queryDeadline) {
            resolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Video.Media._ID),
                "${MediaStore.Video.Media.DISPLAY_NAME} = ?",
                arrayOf("$exportName.mp4"),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    exportedUri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
                    )
                }
            }
            if (exportedUri == null) SystemClock.sleep(300)
        }
        val outputUri = exportedUri ?: throw AssertionError("Export success was shown but the new MP4 was not found in MediaStore")
        val metadata = MediaMetadataRetriever()
        val durationMs: Long
        val secondSegmentBitmap: android.graphics.Bitmap
        try {
            metadata.setDataSource(context, outputUri)
            durationMs = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: throw AssertionError("Could not read exported MP4 duration")
            secondSegmentBitmap = metadata.getFrameAtTime(
                9_500_000L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            ) ?: throw AssertionError("Could not decode a frame from the second clip's timeline position")
        } finally {
            metadata.release()
        }
        assertTrue("Combined export should be about 11 seconds, got $durationMs ms", durationMs in 10_500L..11_500L)
        val pixel = secondSegmentBitmap.getPixel(secondSegmentBitmap.width / 2, secondSegmentBitmap.height / 2)
        val red = android.graphics.Color.red(pixel)
        val green = android.graphics.Color.green(pixel)
        val blue = android.graphics.Color.blue(pixel)
        secondSegmentBitmap.recycle()
        assertTrue(
            "Frame at 9.5 seconds does not look like the green second clip (r=$red g=$green b=$blue)",
            green > red + 25 && green > blue + 25
        )
        assertTrue("First original fixture size changed during export", firstFile.length() == firstSize)
        assertTrue("Second original fixture size changed during export", secondFile.length() == secondSize)
        assertTrue("First original video content changed during export", sha256(firstFile) == firstHash)
        assertTrue("Second original video content changed during export", sha256(secondFile) == secondHash)
        android.util.Log.i(
            "MultiClipExportTest",
            "stage=verified durationMs=$durationMs secondFrameRGB=$red,$green,$blue originalsUnchanged=true"
        )
    }


    @Test
    fun timedStyledTextExportsOnlyInsideItsIntervalAtTopCenterInCyan() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val resolver = context.contentResolver
        val device = UiDevice.getInstance(instrumentation)
        val packageName = context.packageName
        val fixture = java.io.File(context.filesDir, "editor-test-video.mp4")
        assertTrue("Text parity fixture is missing", fixture.isFile && fixture.length() > 0L)
        val originalHash = sha256(fixture)
        val exportName = "MathMotor_text_parity_${System.currentTimeMillis()}"
        val draftId = java.util.UUID.randomUUID().toString()

        val clip = org.json.JSONObject()
            .put("uri", Uri.fromFile(fixture).toString())
            .put("title", "TEXT_PARITY_FIXTURE")
            .put("durationMs", 8020L)
            .put("startMs", 0L)
            .put("endMs", 8020L)
        val textLayer = org.json.JSONObject()
            .put("id", 943L)
            .put("text", "TEXT PARITY")
            .put("startMs", 2000L)
            .put("endMs", 5000L)
            .put("timingCustomized", true)
            .put("position", "TOP")
            .put("sizeSp", 32)
            .put("xFraction", 0.5)
            .put("yFraction", 0.16)
            .put("colorPreset", "CYAN")
            .put("isBold", true)
        val project = org.json.JSONObject()
            .put("format", "MathMotorVideoProject")
            .put("version", 1)
            .put("clips", org.json.JSONArray().put(clip))
            .put("selectedClipIndex", 0)
            .put("timelinePlayheadMs", 3000L)
            .put("timelineZoom", 1.0)
            .put("timelineHorizontalScrollMs", 3000L)
            .put("textLayers", org.json.JSONArray().put(textLayer))
            .put("selectedTextLayerId", 943L)
            .put("sourceAudioVolume", 100)
            .put("musicVolume", 45)
            .put("soundVolume", 100)
            .put("keepSourceAudio", true)
            .put("exportName", exportName)
        assertTrue(
            "Could not seed the timed-text export parity project",
            VideoProjectDraftStore.save(context, draftId, "Timed text export parity", project)
        )

        device.executeShellCommand("logcat -c")
        device.executeShellCommand("am start --activity-clear-task -n $packageName/com.mathmotor.video.MainActivity")
        assertTrue(
            "Home did not open before the timed-text export test",
            device.wait(Until.hasObject(By.textContains("Boş proje oluştur")), 12_000)
        )
        device.executeShellCommand(
            "am start --activity-clear-task -n $packageName/com.mathmotor.video.EditorTestEntryActivity --es draft_id $draftId"
        )
        assertTrue(
            "Seeded timed-text project did not open",
            device.wait(Until.hasObject(By.textContains("Taslak açıldı")), 20_000)
        )
        val previewTextSelector = By.clazz("android.widget.TextView").text("TEXT PARITY")
        assertTrue(
            "Timed text was not visible in the preview at 3 seconds",
            device.wait(Until.hasObject(previewTextSelector), 10_000)
        )
        device.waitForIdle()
        val previewTextBounds = requireObject(
            device, previewTextSelector, "styled text in video preview"
        ).visibleBounds
        assertTrue(
            "TOP-positioned text was not near the upper preview area: $previewTextBounds",
            previewTextBounds.top < device.displayHeight * 0.35f
        )

        val previewShot = instrumentation.uiAutomation.takeScreenshot()
            ?: throw AssertionError("Could not capture the preview text for colour verification")
        fun isCyan(pixel: Int): Boolean {
            val red = android.graphics.Color.red(pixel)
            val green = android.graphics.Color.green(pixel)
            val blue = android.graphics.Color.blue(pixel)
            return red < 150 && green > 125 && blue > 155 &&
                green - red > 35 && blue - red > 65
        }
        fun cyanPixelsInRect(
            bitmap: android.graphics.Bitmap,
            left: Int,
            top: Int,
            right: Int,
            bottom: Int
        ): Int {
            var count = 0
            val x0 = left.coerceIn(0, bitmap.width)
            val x1 = right.coerceIn(x0, bitmap.width)
            val y0 = top.coerceIn(0, bitmap.height)
            val y1 = bottom.coerceIn(y0, bitmap.height)
            for (y in y0 until y1) {
                for (x in x0 until x1) {
                    if (isCyan(bitmap.getPixel(x, y))) count++
                }
            }
            return count
        }
        val previewCyanPixels = cyanPixelsInRect(
            previewShot,
            previewTextBounds.left,
            previewTextBounds.top,
            previewTextBounds.right,
            previewTextBounds.bottom
        )
        previewShot.recycle()
        assertTrue(
            "Preview text does not render in the configured cyan colour (pixels=$previewCyanPixels)",
            previewCyanPixels >= 8
        )

        val exportTab = device.findObject(By.text("Dışa aktar ›"))
            ?: throw AssertionError("Could not open the export tab for timed text")
        exportTab.click()
        assertTrue(
            "The export panel did not open for timed text",
            device.wait(Until.hasObject(By.text("PROJE / DIŞA AKTARMA")), 8_000)
        )
        requireObject(device, By.clazz("android.widget.EditText"), "export filename input").setText(exportName)
        val panelScroller = UiScrollable(UiSelector().className("android.widget.ScrollView")).setAsVerticalList()
        assertTrue(
            "Could not scroll the export panel to the MP4 export button",
            panelScroller.scrollIntoView(UiSelector().text("MP4 DIŞA AKTAR"))
        )
        device.waitForIdle()
        requireObject(device, By.text("MP4 DIŞA AKTAR"), "MP4 export button").click()

        val exportDeadline = SystemClock.uptimeMillis() + 120_000L
        var exportSaved = false
        while (SystemClock.uptimeMillis() < exportDeadline) {
            val log = device.executeShellCommand("logcat -d -s MultiClipExport:I")
            if (log.contains("MULTICLIP_EXPORT_SAVED name=$exportName")) {
                exportSaved = true
                break
            }
            if (log.contains("MULTICLIP_EXPORT_TRANSFORM_FAILED name=$exportName") ||
                log.contains("MULTICLIP_EXPORT_SAVE_FAILED name=$exportName")
            ) throw AssertionError("Timed-text MP4 export failed: $log")
            SystemClock.sleep(500)
        }
        assertTrue("Timed-text MP4 was not saved within 120 seconds", exportSaved)

        var outputUri: Uri? = null
        val queryDeadline = SystemClock.uptimeMillis() + 30_000L
        while (outputUri == null && SystemClock.uptimeMillis() < queryDeadline) {
            resolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Video.Media._ID),
                "${MediaStore.Video.Media.DISPLAY_NAME} = ?",
                arrayOf("$exportName.mp4"),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    outputUri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
                    )
                }
            }
            if (outputUri == null) SystemClock.sleep(300)
        }
        val savedUri = outputUri ?: throw AssertionError(
            "Export reported success but the timed-text MP4 was not found in MediaStore"
        )

        val sourceRetriever = MediaMetadataRetriever()
        val exportRetriever = MediaMetadataRetriever()
        sourceRetriever.setDataSource(fixture.absolutePath)
        exportRetriever.setDataSource(context, savedUri)
        val frameWidth = exportRetriever.extractMetadata(
            MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
        )?.toIntOrNull() ?: throw AssertionError("Could not read exported video width")
        val frameHeight = exportRetriever.extractMetadata(
            MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
        )?.toIntOrNull() ?: throw AssertionError("Could not read exported video height")

        fun inspectTextAt(timeMs: Long): Triple<Int, Int, android.graphics.Rect?> {
            val sourceFrame = sourceRetriever.getFrameAtTime(
                timeMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST
            ) ?: throw AssertionError("Could not decode original fixture frame at ${timeMs}ms")
            val outputFrame = exportRetriever.getFrameAtTime(
                timeMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST
            ) ?: throw AssertionError("Could not decode exported frame at ${timeMs}ms")
            val alignedSource = if (sourceFrame.width != outputFrame.width ||
                sourceFrame.height != outputFrame.height
            ) {
                android.graphics.Bitmap.createScaledBitmap(
                    sourceFrame, outputFrame.width, outputFrame.height, false
                )
            } else sourceFrame
            var topCount = 0
            var lowerCount = 0
            var left = outputFrame.width
            var top = outputFrame.height
            var right = -1
            var bottom = -1
            val xStart = outputFrame.width / 10
            val xEnd = outputFrame.width * 9 / 10
            val topEnd = outputFrame.height * 45 / 100
            val lowerEnd = outputFrame.height * 90 / 100
            for (y in 0 until lowerEnd) {
                for (x in xStart until xEnd) {
                    val outputCyan = isCyan(outputFrame.getPixel(x, y))
                    val sourceCyan = isCyan(alignedSource.getPixel(x, y))
                    if (outputCyan && !sourceCyan) {
                        if (y < topEnd) {
                            topCount++
                            left = minOf(left, x)
                            right = maxOf(right, x)
                            top = minOf(top, y)
                            bottom = maxOf(bottom, y)
                        } else {
                            lowerCount++
                        }
                    }
                }
            }
            val textBounds = if (right >= left && bottom >= top) {
                android.graphics.Rect(left, top, right + 1, bottom + 1)
            } else null
            if (alignedSource !== sourceFrame) alignedSource.recycle()
            sourceFrame.recycle()
            outputFrame.recycle()
            return Triple(topCount, lowerCount, textBounds)
        }

        try {
            val before = inspectTextAt(1000L)
            val during = inspectTextAt(3000L)
            val after = inspectTextAt(6000L)
            assertTrue(
                "Timed text should not render before 2s; extra cyan pixels=${before.first}",
                before.first < 25
            )
            assertTrue(
                "Styled text did not render in exported frames during 2s-5s; extra cyan pixels=${during.first}",
                during.first >= 40
            )
            assertTrue(
                "Timed text should not render after 5s; extra cyan pixels=${after.first}",
                after.first < 25
            )
            assertTrue(
                "CYAN text should be concentrated near the top, not the lower frame " +
                    "(top=${during.first}, lower=${during.second})",
                during.first > during.second + 20
            )
            val bounds = during.third ?: throw AssertionError("Could not locate exported cyan text pixels")
            assertTrue(
                "Exported text was unexpectedly narrow for 32sp TEXT PARITY: $bounds width=$frameWidth",
                bounds.width() > frameWidth * 0.15f
            )
            assertTrue(
                "Exported text was unexpectedly short: $bounds height=$frameHeight",
                bounds.height() > frameHeight * 0.04f
            )
            assertTrue(
                "Exported text was not horizontally centered: $bounds width=$frameWidth",
                bounds.centerX() in (frameWidth * 0.25f).toInt()..(frameWidth * 0.75f).toInt()
            )
            assertTrue("Text export modified the original source video", sha256(fixture) == originalHash)
            android.util.Log.i(
                "TextExportParityTest",
                "stage=verified before=${before.first} during=${during.first} lower=${during.second} " +
                    "after=${after.first} bounds=$bounds frame=${frameWidth}x$frameHeight " +
                    "previewCyanPixels=$previewCyanPixels originalsUnchanged=true"
            )
        } finally {
            sourceRetriever.release()
            exportRetriever.release()
        }
    }


    @Test
    fun exportedMp4ContainsLoopedMusicAndTimedEffectAtConfiguredLevels() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val resolver = context.contentResolver
        val device = UiDevice.getInstance(instrumentation)
        val packageName = context.packageName
        val fixture = java.io.File(context.filesDir, "editor-test-video.mp4")
        assertTrue("Audio export video fixture is missing", fixture.isFile && fixture.length() > 0L)
        val musicFile = java.io.File(context.filesDir, "editor-audio-test-music.wav")
        val effectFile = java.io.File(context.filesDir, "editor-audio-test-effect.wav")
        writeToneWav(musicFile, 44_100, 1_000, 440.0, 12_000)
        writeToneWav(effectFile, 44_100, 1_200, 880.0, 8_000)
        val exportName = "MathMotor_audio_export_${System.currentTimeMillis()}"
        val draftId = java.util.UUID.randomUUID().toString()
        val clip = org.json.JSONObject()
            .put("uri", Uri.fromFile(fixture).toString())
            .put("title", "AUDIO_EXPORT_VIDEO")
            .put("durationMs", 8_020L)
            .put("startMs", 0L)
            .put("endMs", 8_020L)
        val project = org.json.JSONObject()
            .put("format", "MathMotorVideoProject")
            .put("version", 1)
            .put("clips", org.json.JSONArray().put(clip))
            .put("selectedClipIndex", 0)
            .put("timelinePlayheadMs", 3_000L)
            .put("timelineZoom", 1.0)
            .put("timelineHorizontalScrollMs", 3_000L)
            .put("textLayers", org.json.JSONArray())
            .put("sourceAudioVolume", 100)
            .put("musicUri", Uri.fromFile(musicFile).toString())
            .put("musicTitle", "AUDIO_TEST_MUSIC_440HZ")
            .put("musicVolume", 35)
            .put("soundUri", Uri.fromFile(effectFile).toString())
            .put("soundTitle", "AUDIO_TEST_EFFECT_880HZ")
            .put("soundDurationMs", 1_200L)
            .put("soundStartMs", 2_500L)
            .put("soundVolume", 60)
            .put("keepSourceAudio", false)
            .put("exportName", exportName)
        assertTrue("Could not seed audio export validation project",
            VideoProjectDraftStore.save(context, draftId, "Audio export validation", project))

        device.executeShellCommand("logcat -c")
        device.executeShellCommand("am start -n $packageName/com.mathmotor.video.MainActivity")
        assertTrue("Home did not open before audio export validation",
            device.wait(Until.hasObject(By.textContains("Boş proje oluştur")), 12_000))
        device.executeShellCommand(
            "am start -n $packageName/com.mathmotor.video.EditorTestEntryActivity --es draft_id $draftId")
        assertTrue("Seeded audio project did not open",
            device.wait(Until.hasObject(By.textContains("Taslak açıldı")), 20_000))

        (device.findObject(By.text("Dışa aktar ›"))
            ?: throw AssertionError("Could not open export tab for audio validation")).click()
        assertTrue("Audio export panel did not open",
            device.wait(Until.hasObject(By.text("PROJE / DIŞA AKTARMA")), 8_000))
        requireObject(device, By.clazz("android.widget.EditText"), "audio export filename").setText(exportName)
        val scroller = UiScrollable(UiSelector().className("android.widget.ScrollView")).setAsVerticalList()
        assertTrue("Could not scroll to MP4 export button",
            scroller.scrollIntoView(UiSelector().text("MP4 DIŞA AKTAR")))
        device.waitForIdle()
        requireObject(device, By.text("MP4 DIŞA AKTAR"), "audio validation MP4 button").click()

        val exportDeadline = SystemClock.uptimeMillis() + 120_000L
        var exported = false
        while (SystemClock.uptimeMillis() < exportDeadline) {
            val log = device.executeShellCommand("logcat -d -s MultiClipExport:I")
            if (log.contains("MULTICLIP_EXPORT_SAVED name=$exportName")) { exported = true; break }
            if (log.contains("MULTICLIP_EXPORT_TRANSFORM_FAILED name=$exportName") ||
                log.contains("MULTICLIP_EXPORT_SAVE_FAILED name=$exportName")) {
                throw AssertionError("Audio MP4 export failed: $log")
            }
            SystemClock.sleep(500)
        }
        assertTrue("Audio MP4 did not save within 120 seconds", exported)

        var outputUri: Uri? = null
        val queryDeadline = SystemClock.uptimeMillis() + 30_000L
        while (outputUri == null && SystemClock.uptimeMillis() < queryDeadline) {
            resolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Video.Media._ID),
                "${MediaStore.Video.Media.DISPLAY_NAME} = ?", arrayOf("$exportName.mp4"), null
            )?.use { cursor ->
                if (cursor.moveToFirst()) outputUri = ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)))
            }
            if (outputUri == null) SystemClock.sleep(300)
        }
        val savedUri = outputUri ?: throw AssertionError("Export succeeded but MP4 was not found in MediaStore")
        val decoded = decodeExportedAudioMono(context, savedUri)
        val sampleCount = decoded.chunks.sumOf { it.samples.size }
        assertTrue("Decoded AAC track is too short: rate=${decoded.sampleRate} samples=$sampleCount",
            sampleCount >= decoded.sampleRate * 7)

        val musicEarly = toneMagnitude(decoded, 440.0, 400L, 900L)
        val musicMiddle = toneMagnitude(decoded, 440.0, 4_000L, 4_500L)
        val musicLate = toneMagnitude(decoded, 440.0, 6_500L, 7_000L)
        val effectBefore = toneMagnitude(decoded, 880.0, 1_300L, 2_000L)
        val effectDuring = toneMagnitude(decoded, 880.0, 2_800L, 3_400L)
        val effectAfter = toneMagnitude(decoded, 880.0, 4_200L, 4_800L)
        android.util.Log.i("AudioExportTest",
            "stage=decoded rate=${decoded.sampleRate} music440=${musicEarly.toInt()},${musicMiddle.toInt()},${musicLate.toInt()} " +
                "effect880=${effectBefore.toInt()},${effectDuring.toInt()},${effectAfter.toInt()}")

        assertTrue("440 Hz music missing near start: $musicEarly", musicEarly in 2_000.0..6_500.0)
        assertTrue("Background music did not loop into middle: $musicMiddle", musicMiddle in 2_000.0..6_500.0)
        assertTrue("Background music did not loop into late video: $musicLate", musicLate in 2_000.0..6_500.0)
        assertTrue("880 Hz effect appeared before 2.5 s: $effectBefore", effectBefore < 650.0)
        assertTrue("Timed effect missing within 2.5–3.7 s: $effectDuring", effectDuring in 2_000.0..6_500.0)
        assertTrue("Timed effect remained after 3.7 s: $effectAfter", effectAfter < 650.0)

        // Quantitatively verify configured gain against each deterministic source tone.
        // Input WAV amplitudes are 12,000 (music) and 8,000 (effect), so expected
        // decoded tone amplitudes are approximately 4,200 and 4,800 respectively.
        // Tolerances allow AAC codec error but still catch a wrong volume multiplier.
        val musicGainRatio = musicMiddle / 12_000.0
        val effectGainRatio = effectDuring / 8_000.0
        android.util.Log.i(
            "AudioExportTest",
            "stage=quantitative-gain musicConfigured=0.35 musicMeasured=$musicGainRatio " +
                "effectConfigured=0.60 effectMeasured=$effectGainRatio"
        )
        assertTrue(
            "Background music gain did not match configured 35%: amplitude=$musicMiddle ratio=$musicGainRatio",
            musicGainRatio in 0.30..0.40
        )
        assertTrue(
            "Timed effect gain did not match configured 60%: amplitude=$effectDuring ratio=$effectGainRatio",
            effectGainRatio in 0.55..0.65
        )
        assertTrue("Original video fixture was changed", fixture.isFile && fixture.length() > 0L)
        android.util.Log.i("AudioExportTest",
            "stage=verified musicLooped=true effectStartMs=2500 effectDurationMs=1200 musicVolume=35 effectVolume=60 sourceAudioRemoved=true")
    }

    @Test
    fun exportedMp4RespectsSourceAudioVolumeAndMute() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val resolver = context.contentResolver
        val device = UiDevice.getInstance(instrumentation)
        val packageName = context.packageName
        val sourceFixture = java.io.File(context.filesDir, "editor-test-video-with-audio.mp4")
        assertTrue("Audio-bearing source fixture is missing", sourceFixture.isFile && sourceFixture.length() > 0L)
        val originalHash = sha256(sourceFixture)
        val musicFile = java.io.File(context.filesDir, "editor-audio-source-test-music.wav")
        val effectFile = java.io.File(context.filesDir, "editor-audio-source-test-effect.wav")
        writeToneWav(musicFile, 44_100, 1_000, 440.0, 12_000)
        writeToneWav(effectFile, 44_100, 1_200, 880.0, 8_000)

        val sourceDecoded = decodeExportedAudioMono(context, Uri.fromFile(sourceFixture))
        val sourceBaseline = toneMagnitude(sourceDecoded, 220.0, 500L, 900L)
        assertTrue("Source audio fixture has no measurable 220 Hz tone: $sourceBaseline", sourceBaseline > 5_000.0)

        fun exportAndDecode(keepSourceAudio: Boolean): DecodedMonoAudio {
            val exportName = "MathMotor_source_audio_${if (keepSourceAudio) "kept" else "muted"}_${System.currentTimeMillis()}"
            val draftId = java.util.UUID.randomUUID().toString()
            val clip = org.json.JSONObject()
                .put("uri", Uri.fromFile(sourceFixture).toString())
                .put("title", "AUDIO_SOURCE_VIDEO")
                .put("durationMs", 8_020L)
                .put("startMs", 0L)
                .put("endMs", 8_020L)
            val project = org.json.JSONObject()
                .put("format", "MathMotorVideoProject")
                .put("version", 1)
                .put("clips", org.json.JSONArray().put(clip))
                .put("selectedClipIndex", 0)
                .put("timelinePlayheadMs", 500L)
                .put("timelineZoom", 1.0)
                .put("timelineHorizontalScrollMs", 0L)
                .put("textLayers", org.json.JSONArray())
                .put("sourceAudioVolume", 40)
                .put("musicUri", Uri.fromFile(musicFile).toString())
                .put("musicTitle", "SOURCE_AUDIO_TEST_MUSIC_440HZ")
                .put("musicVolume", 35)
                .put("soundUri", Uri.fromFile(effectFile).toString())
                .put("soundTitle", "SOURCE_AUDIO_TEST_EFFECT_880HZ")
                .put("soundDurationMs", 1_200L)
                .put("soundStartMs", 2_500L)
                .put("soundVolume", 60)
                .put("keepSourceAudio", keepSourceAudio)
                .put("exportName", exportName)
            assertTrue("Could not seed source-audio validation project ($keepSourceAudio)",
                VideoProjectDraftStore.save(context, draftId, "Source audio validation", project))

            // Clear old restore events so the test waits for this draft, not a
            // previous editor launch, before exporting its audio state.
            device.executeShellCommand("logcat -c")
            // The first export leaves VideoEditorActivity at the top of an existing task.
            // Plain "am start" can then report START_DELIVERED_TO_TOP without creating a
            // new EditorTestEntryActivity, so the second (muted) draft is never loaded.
            // Clear that activity stack (not the process: force-stop would kill this
            // instrumentation runner) before launching the next uniquely seeded draft.
            device.executeShellCommand(
                "am start --activity-clear-task -n $packageName/com.mathmotor.video.EditorTestEntryActivity --es draft_id $draftId")

            // Verify the exact launch and project lookup markers before accepting any UI
            // already visible from the previous export as evidence of a new editor launch.
            val restoreDeadline = SystemClock.uptimeMillis() + 20_000L
            var entryStarted = false
            var draftLookupSeen = false
            var draftFound = false
            var draftRestored = false
            var lastEditorSmokeLog = ""
            while (SystemClock.uptimeMillis() < restoreDeadline) {
                val restoreLog = device.executeShellCommand("logcat -d -s EditorSmokeTest:I")
                lastEditorSmokeLog = restoreLog.takeLast(1_200)
                if (restoreLog.contains("EDITOR_SMOKE_ENTRY_STARTED") &&
                    restoreLog.contains("hasDraft=true")) {
                    entryStarted = true
                }
                if (restoreLog.contains("EDITOR_SMOKE_DRAFT_LOOKUP id=$draftId found=true")) {
                    draftLookupSeen = true
                    draftFound = true
                }
                if (restoreLog.contains("EDITOR_SMOKE_DRAFT_LOOKUP id=$draftId found=false")) {
                    draftLookupSeen = true
                    break
                }
                if (restoreLog.contains("EDITOR_SMOKE_DRAFT_RESTORED id=$draftId")) {
                    draftRestored = true
                    break
                }
                SystemClock.sleep(200)
            }
            assertTrue(
                "Editor test entry was not launched for source-audio draft " +
                    "(keepSourceAudio=$keepSourceAudio, markers=$lastEditorSmokeLog)",
                entryStarted
            )
            assertTrue(
                "Editor did not find the seeded source-audio draft " +
                    "(draftId=$draftId, lookupSeen=$draftLookupSeen, markers=$lastEditorSmokeLog)",
                draftFound
            )
            assertTrue(
                "Seeded source-audio project did not finish restoring " +
                    "(draftId=$draftId, markers=$lastEditorSmokeLog)",
                draftRestored
            )
            assertTrue("Editor did not open for seeded source-audio project ($keepSourceAudio)",
                device.wait(Until.hasObject(By.desc("Zaman çizelgesi etiketi: VIDEO")), 20_000))
            assertTrue("Editor export tab did not appear for source-audio validation",
                device.wait(Until.hasObject(By.textContains("Dışa aktar")), 8_000))

            // Audio controls live on their own tab and can be below the compact
            // scroll viewport. Open that tab and scroll to the checkbox before asserting it.
            val audioTab = requireObject(device, By.desc("Ses araçları sekmesi"), "source-audio tools tab")
            audioTab.click()
            assertTrue(
                "Audio panel did not appear for source-audio validation",
                device.wait(Until.hasObject(By.text("SES KATMANLARI")), 8_000)
            )
            val audioPanelScroller = UiScrollable(
                UiSelector().className("android.widget.ScrollView")
            ).setAsVerticalList()
            assertTrue(
                "Could not reveal source-audio checkbox in audio panel",
                audioPanelScroller.scrollIntoView(UiSelector().text("Videonun kendi sesini koru"))
            )
            device.waitForIdle()
            val sourceAudioCheckbox = requireObject(
                device, By.text("Videonun kendi sesini koru"), "source-audio checkbox"
            )
            assertTrue(
                "Restored source-audio state mismatch (expected=$keepSourceAudio, actual=${sourceAudioCheckbox.isChecked})",
                sourceAudioCheckbox.isChecked == keepSourceAudio
            )

            (device.findObject(By.text("Dışa aktar ›"))
                ?: throw AssertionError("Could not open export tab for source-audio validation")).click()
            assertTrue("Source-audio export panel did not open",
                device.wait(Until.hasObject(By.text("PROJE / DIŞA AKTARMA")), 8_000))
            requireObject(device, By.clazz("android.widget.EditText"), "source-audio export filename").setText(exportName)
            val scroller = UiScrollable(UiSelector().className("android.widget.ScrollView")).setAsVerticalList()
            assertTrue("Could not scroll to source-audio MP4 export button",
                scroller.scrollIntoView(UiSelector().text("MP4 DIŞA AKTAR")))
            device.waitForIdle()
            requireObject(device, By.text("MP4 DIŞA AKTAR"), "source-audio MP4 button").click()

            val exportDeadline = SystemClock.uptimeMillis() + 120_000L
            var exported = false
            while (SystemClock.uptimeMillis() < exportDeadline) {
                val log = device.executeShellCommand("logcat -d -s MultiClipExport:I")
                if (log.contains("MULTICLIP_EXPORT_SAVED name=$exportName")) { exported = true; break }
                if (log.contains("MULTICLIP_EXPORT_TRANSFORM_FAILED name=$exportName") ||
                    log.contains("MULTICLIP_EXPORT_SAVE_FAILED name=$exportName")) {
                    throw AssertionError("Source-audio MP4 export failed ($keepSourceAudio): $log")
                }
                SystemClock.sleep(500)
            }
            assertTrue("Source-audio MP4 did not save within 120 seconds ($keepSourceAudio)", exported)

            var outputUri: Uri? = null
            val queryDeadline = SystemClock.uptimeMillis() + 30_000L
            while (outputUri == null && SystemClock.uptimeMillis() < queryDeadline) {
                resolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Video.Media._ID),
                    "${MediaStore.Video.Media.DISPLAY_NAME} = ?",
                    arrayOf("$exportName.mp4"), null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) outputUri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)))
                }
                if (outputUri == null) SystemClock.sleep(300)
            }
            val savedUri = outputUri
                ?: throw AssertionError("Source-audio export succeeded but MP4 was not found ($keepSourceAudio)")
            return decodeExportedAudioMono(context, savedUri)
        }

        val kept = exportAndDecode(keepSourceAudio = true)
        val muted = exportAndDecode(keepSourceAudio = false)
        val keptEarly = toneMagnitude(kept, 220.0, 500L, 900L)
        val keptLate = toneMagnitude(kept, 220.0, 6_500L, 7_000L)
        val mutedEarly = toneMagnitude(muted, 220.0, 500L, 900L)
        val keptRatio = keptEarly / sourceBaseline
        val mutedRatio = mutedEarly / sourceBaseline
        android.util.Log.i("SourceAudioExportTest",
            "stage=measured source220=$sourceBaseline keptEarly=$keptEarly keptLate=$keptLate " +
                "mutedEarly=$mutedEarly keptRatio=$keptRatio mutedRatio=$mutedRatio")

        assertTrue("Source audio volume 40% was not reflected in exported PCM: ratio=$keptRatio " +
            "source=$sourceBaseline kept=$keptEarly", keptRatio in 0.28..0.52)
        assertTrue("Source video audio disappeared before the end of the clip: early=$keptEarly late=$keptLate",
            keptLate / sourceBaseline in 0.28..0.52)
        assertTrue("Muted export still contains the source 220 Hz tone: ratio=$mutedRatio magnitude=$mutedEarly",
            mutedRatio < 0.08)
        assertTrue("Export modified the original audio-bearing source video",
            sourceFixture.isFile && sha256(sourceFixture) == originalHash)
        android.util.Log.i("SourceAudioExportTest",
            "stage=verified sourceAudioVolume=40 mute=true musicVolume=35 effectVolume=60 originalsUnchanged=true")
    }

    @Test
    fun exportedMp4KeepsAudioOnsetAlignedWithVideoColorBoundary() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val resolver = context.contentResolver
        val device = UiDevice.getInstance(instrumentation)
        val packageName = context.packageName
        val fixture = java.io.File(context.filesDir, "editor-av-sync-video.mp4")
        assertTrue("Deterministic A/V sync fixture is missing", fixture.isFile && fixture.length() > 0L)
        val originalSize = fixture.length()
        val originalHash = sha256(fixture)
        val exportName = "MathMotor_av_sync_${System.currentTimeMillis()}"
        val draftId = java.util.UUID.randomUUID().toString()

        val clip = org.json.JSONObject()
            .put("uri", Uri.fromFile(fixture).toString())
            .put("title", "AV_SYNC_RED_TO_LIME")
            .put("durationMs", 4_000L)
            .put("startMs", 0L)
            .put("endMs", 4_000L)
        val project = org.json.JSONObject()
            .put("format", "MathMotorVideoProject")
            .put("version", 1)
            .put("clips", org.json.JSONArray().put(clip))
            .put("selectedClipIndex", 0)
            .put("timelinePlayheadMs", 1_800L)
            .put("timelineZoom", 1.0)
            .put("timelineHorizontalScrollMs", 0L)
            .put("textLayers", org.json.JSONArray())
            .put("sourceAudioVolume", 100)
            .put("musicVolume", 0)
            .put("soundVolume", 100)
            .put("keepSourceAudio", true)
            .put("exportName", exportName)
        assertTrue("Could not seed A/V synchronization project",
            VideoProjectDraftStore.save(context, draftId, "A/V synchronization validation", project))

        device.executeShellCommand("logcat -c")
        device.executeShellCommand(
            "am start --activity-clear-task -n " + packageName +
                "/com.mathmotor.video.EditorTestEntryActivity --es draft_id " + draftId
        )
        val restoreDeadline = SystemClock.uptimeMillis() + 20_000L
        var entryStarted = false
        var draftFound = false
        var draftRestored = false
        var restoreMarkers = ""
        while (SystemClock.uptimeMillis() < restoreDeadline) {
            val logs = device.executeShellCommand("logcat -d -s EditorSmokeTest:I")
            restoreMarkers = logs.takeLast(1_600)
            if (logs.contains("EDITOR_SMOKE_ENTRY_STARTED") && logs.contains("hasDraft=true")) {
                entryStarted = true
            }
            if (logs.contains("EDITOR_SMOKE_DRAFT_LOOKUP id=" + draftId + " found=true")) {
                draftFound = true
            }
            if (logs.contains("EDITOR_SMOKE_DRAFT_RESTORED id=" + draftId)) {
                draftRestored = true
                break
            }
            if (logs.contains("EDITOR_SMOKE_DRAFT_LOOKUP id=" + draftId + " found=false")) break
            SystemClock.sleep(200)
        }
        assertTrue("A/V test entry activity did not start; markers=" + restoreMarkers, entryStarted)
        assertTrue("A/V test draft was not found; markers=" + restoreMarkers, draftFound)
        assertTrue("A/V test draft did not restore; markers=" + restoreMarkers, draftRestored)
        assertTrue("A/V test project did not expose the VIDEO lane",
            device.wait(Until.hasObject(By.desc("Zaman çizelgesi etiketi: VIDEO")), 12_000))

        (device.findObject(By.text("Dışa aktar ›"))
            ?: throw AssertionError("Could not open export tab for A/V sync validation")).click()
        assertTrue("A/V sync export panel did not open",
            device.wait(Until.hasObject(By.text("PROJE / DIŞA AKTARMA")), 8_000))
        requireObject(device, By.clazz("android.widget.EditText"), "A/V sync export filename").setText(exportName)
        val scroller = UiScrollable(UiSelector().className("android.widget.ScrollView")).setAsVerticalList()
        assertTrue("Could not scroll to A/V sync MP4 export button",
            scroller.scrollIntoView(UiSelector().text("MP4 DIŞA AKTAR")))
        device.waitForIdle()
        requireObject(device, By.text("MP4 DIŞA AKTAR"), "A/V sync MP4 button").click()

        val exportDeadline = SystemClock.uptimeMillis() + 120_000L
        var exported = false
        while (SystemClock.uptimeMillis() < exportDeadline) {
            val logs = device.executeShellCommand("logcat -d -s MultiClipExport:I")
            if (logs.contains("MULTICLIP_EXPORT_SAVED name=$exportName")) {
                exported = true
                break
            }
            if (logs.contains("MULTICLIP_EXPORT_TRANSFORM_FAILED name=$exportName") ||
                logs.contains("MULTICLIP_EXPORT_SAVE_FAILED name=$exportName")) {
                throw AssertionError("A/V sync MP4 export failed: $logs")
            }
            SystemClock.sleep(500)
        }
        assertTrue("A/V sync MP4 did not save within 120 seconds", exported)

        var outputUri: Uri? = null
        val queryDeadline = SystemClock.uptimeMillis() + 30_000L
        while (outputUri == null && SystemClock.uptimeMillis() < queryDeadline) {
            resolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Video.Media._ID),
                "${MediaStore.Video.Media.DISPLAY_NAME} = ?",
                arrayOf("$exportName.mp4"),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    outputUri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
                    )
                }
            }
            if (outputUri == null) SystemClock.sleep(300)
        }
        val savedUri = outputUri
            ?: throw AssertionError("A/V sync export reported success but the new MP4 was not found")

        val retriever = MediaMetadataRetriever()
        val videoMeasurement = try {
            retriever.setDataSource(context, savedUri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: throw AssertionError("Could not read A/V sync export duration")
            fun classifyFrameAt(timeMs: Long): String {
                val frame = retriever.getFrameAtTime(
                    timeMs * 1_000L,
                    MediaMetadataRetriever.OPTION_CLOSEST
                ) ?: throw AssertionError("Could not decode A/V sync frame at $timeMs ms")
                try {
                    val pixel = frame.getPixel(frame.width / 2, frame.height / 2)
                    val red = android.graphics.Color.red(pixel)
                    val green = android.graphics.Color.green(pixel)
                    val blue = android.graphics.Color.blue(pixel)
                    val label = when {
                        green > red + 35 && green > blue + 35 -> "GREEN"
                        red > green + 35 && red > blue + 35 -> "RED"
                        else -> "UNKNOWN"
                    }
                    return "$label($red,$green,$blue)"
                } finally {
                    frame.recycle()
                }
            }
            durationMs to (1_800L..2_200L step 25L).map { timeMs ->
                timeMs to classifyFrameAt(timeMs)
            }
        } finally {
            retriever.release()
        }
        val durationMs = videoMeasurement.first
        val frameSamples = videoMeasurement.second
        assertTrue("A/V sync export duration is not approximately 4 seconds: $durationMs ms",
            durationMs in 3_900L..4_200L)
        val firstGreenIndex = frameSamples.indexOfFirst { it.second.startsWith("GREEN") }
        assertTrue("No green video boundary found around 2 seconds: $frameSamples",
            firstGreenIndex > 0)
        val lastRed = frameSamples[firstGreenIndex - 1]
        val firstGreen = frameSamples[firstGreenIndex]
        assertTrue("Video did not transition from red to lime at the 2 s boundary: $lastRed -> $firstGreen",
            lastRed.second.startsWith("RED") && firstGreen.second.startsWith("GREEN"))
        val videoBoundaryMs = firstGreen.first
        assertTrue("Video color boundary drifted from 2,000 ms: $videoBoundaryMs ms; samples=$frameSamples",
            videoBoundaryMs in 1_900L..2_100L)

        val decodedAudio = decodeExportedAudioMono(context, savedUri)
        val audioBefore = toneMagnitude(decodedAudio, 997.0, 1_650L, 1_900L)
        val audioAfter = toneMagnitude(decodedAudio, 997.0, 2_100L, 2_350L)
        assertTrue("997 Hz audio tone appeared too early before the video transition: magnitude=$audioBefore",
            audioBefore < 900.0)
        assertTrue("997 Hz audio tone missing after the 2 s video transition: magnitude=$audioAfter",
            audioAfter > 2_000.0)
        var audioToneOnsetMs: Long? = null
        for (windowStartMs in 1_500L..2_500L step 25L) {
            val magnitude = toneMagnitude(decodedAudio, 997.0, windowStartMs, windowStartMs + 100L)
            if (magnitude >= 1_500.0) {
                audioToneOnsetMs = windowStartMs
                break
            }
        }
        val detectedAudioOnsetMs = audioToneOnsetMs
            ?: throw AssertionError("Could not find a measurable 997 Hz audio onset around 2 s")
        val boundaryDeltaMs = kotlin.math.abs(detectedAudioOnsetMs - videoBoundaryMs)
        android.util.Log.i(
            "AVSyncExportTest",
            "stage=measured durationMs=$durationMs videoBoundaryMs=$videoBoundaryMs " +
                "lastRed=${lastRed.second} firstGreen=${firstGreen.second} " +
                "audioToneOnsetWindowMs=$detectedAudioOnsetMs audioBefore=$audioBefore " +
                "audioAfter=$audioAfter boundaryDeltaMs=$boundaryDeltaMs"
        )
        assertTrue(
            "Exported audio onset and video color transition differ by $boundaryDeltaMs ms " +
                "(audioWindow=$detectedAudioOnsetMs ms, videoBoundary=$videoBoundaryMs ms)",
            boundaryDeltaMs <= 150L
        )
        assertTrue("Export changed the A/V sync fixture size", fixture.isFile && fixture.length() == originalSize)
        assertTrue("Export modified the original A/V sync fixture", sha256(fixture) == originalHash)
        android.util.Log.i(
            "AVSyncExportTest",
            "stage=verified aligned=true toleranceMs=150 originalsUnchanged=true"
        )
    }

    @Test
    fun splitAtPlayheadExportsTwoOrderedSegmentsWithoutMovingAvSeam() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val resolver = context.contentResolver
        val device = UiDevice.getInstance(instrumentation)
        val packageName = context.packageName
        val fixture = java.io.File(context.filesDir, "editor-av-sync-video.mp4")
        assertTrue("Split/export A/V fixture is missing", fixture.isFile && fixture.length() > 0L)
        val sourceSize = fixture.length()
        val sourceHash = sha256(fixture)
        val exportName = "MathMotor_split_boundary_${System.currentTimeMillis()}"
        val draftId = java.util.UUID.randomUUID().toString()

        val clip = org.json.JSONObject()
            .put("uri", Uri.fromFile(fixture).toString())
            .put("title", "SPLIT_RED_TO_LIME")
            .put("durationMs", 4_000L)
            .put("startMs", 0L)
            .put("endMs", 4_000L)
        val project = org.json.JSONObject()
            .put("format", "MathMotorVideoProject")
            .put("version", 1)
            .put("clips", org.json.JSONArray().put(clip))
            .put("selectedClipIndex", 0)
            .put("timelinePlayheadMs", 2_000L)
            .put("timelineZoom", 1.0)
            .put("timelineHorizontalScrollMs", 0L)
            .put("textLayers", org.json.JSONArray())
            .put("sourceAudioVolume", 100)
            .put("musicVolume", 0)
            .put("soundVolume", 100)
            .put("keepSourceAudio", true)
            .put("exportName", exportName)
        assertTrue("Could not seed split-at-playhead project",
            VideoProjectDraftStore.save(context, draftId, "Split/export boundary validation", project))

        device.executeShellCommand("logcat -c")
        device.executeShellCommand(
            "am start --activity-clear-task -n " + packageName +
                "/com.mathmotor.video.EditorTestEntryActivity --es draft_id " + draftId
        )
        val restoreDeadline = SystemClock.uptimeMillis() + 20_000L
        var entryStarted = false
        var draftFound = false
        var draftRestored = false
        var restoreMarkers = ""
        while (SystemClock.uptimeMillis() < restoreDeadline) {
            val logs = device.executeShellCommand("logcat -d -s EditorSmokeTest:I")
            restoreMarkers = logs.takeLast(1_600)
            if (logs.contains("EDITOR_SMOKE_ENTRY_STARTED") && logs.contains("hasDraft=true")) {
                entryStarted = true
            }
            if (logs.contains("EDITOR_SMOKE_DRAFT_LOOKUP id=" + draftId + " found=true")) {
                draftFound = true
            }
            if (logs.contains("EDITOR_SMOKE_DRAFT_RESTORED id=" + draftId)) {
                draftRestored = true
                break
            }
            if (logs.contains("EDITOR_SMOKE_DRAFT_LOOKUP id=" + draftId + " found=false")) break
            SystemClock.sleep(200)
        }
        assertTrue("Split test entry activity did not start; markers=" + restoreMarkers, entryStarted)
        assertTrue("Split test draft was not found; markers=" + restoreMarkers, draftFound)
        assertTrue("Split test draft did not restore; markers=" + restoreMarkers, draftRestored)
        assertTrue("Split test project did not expose the VIDEO lane",
            device.wait(Until.hasObject(By.desc("Zaman çizelgesi etiketi: VIDEO")), 12_000))

        fun seekPlayheadTo(targetMs: Long) {
            val seek = requireObject(
                device, By.desc("Zaman çizelgesinde oynatma noktası"), "split test playhead seek bar"
            )
            val bounds = seek.visibleBounds
            assertTrue("Split test playhead SeekBar is too narrow: $bounds", bounds.width() > 100)
            val expected = targetMs.coerceIn(0L, 4_000L)
            val inset = (bounds.width() * 0.15f).toInt().coerceAtLeast(4)
            val usableWidth = (bounds.width() - 2 * inset).coerceAtLeast(2)
            var actual = projectPositionMs(requireProjectTime(device))
            for (attempt in 1..6) {
                if (kotlin.math.abs(actual - expected) <= 100L) break
                val fromX = (bounds.left + inset +
                    (actual.coerceIn(0L, 4_000L).toDouble() / 4_000.0 * usableWidth).toInt())
                    .coerceIn(bounds.left + 1, bounds.right - 2)
                val delta = ((expected - actual).toDouble() / 4_000.0 * usableWidth).toInt()
                    .let { if (it == 0) if (expected > actual) 1 else -1 else it }
                val toX = (fromX + delta).coerceIn(bounds.left + 1, bounds.right - 2)
                device.swipe(fromX, bounds.centerY(), toX, bounds.centerY(), 30)
                device.waitForIdle()
                SystemClock.sleep(250)
                actual = projectPositionMs(requireProjectTime(device))
                android.util.Log.i(
                    "SplitAtPlayheadTest",
                    "stage=seek attempt=$attempt target=$expected actual=$actual fromX=$fromX toX=$toX"
                )
            }
            assertTrue("Could not position split playhead at $expected ms; actual=$actual ms",
                kotlin.math.abs(actual - expected) <= 100L)
        }

        seekPlayheadTo(2_000L)
        val splitTimeBefore = projectPositionMs(requireProjectTime(device))
        assertTrue("Playhead was not at the 2 s split point: $splitTimeBefore", splitTimeBefore in 1_900L..2_100L)

        // Compact layout intentionally starts with tool panels closed; open the clip tools
        // before searching the scrollable panel for the split action.
        device.findObject(By.desc("Klip araçları sekmesi"))?.click()
        device.waitForIdle()
        var splitButton = device.findObject(By.text("Böl ✂"))
        if (splitButton == null) {
            val panelScroller = UiScrollable(UiSelector().className("android.widget.ScrollView")).setAsVerticalList()
            assertTrue("Could not reveal the Split at Playhead action",
                panelScroller.scrollIntoView(UiSelector().text("Böl ✂")))
            splitButton = device.findObject(By.text("Böl ✂"))
        }
        (splitButton ?: throw AssertionError("Split at Playhead action was not exposed")).click()
        assertTrue("Editor did not confirm that the clip was split at the playhead",
            device.wait(Until.hasObject(By.textContains("Klip iki parçaya bölündü")), 8_000))
        assertTrue("First split clip tile was not visible", device.wait(Until.hasObject(By.text("KLİP 1")), 5_000))
        assertTrue("Second split clip tile was not visible (selected tiles include the ▶ prefix)", device.wait(Until.hasObject(By.textContains("KLİP 2")), 5_000))
        assertTrue("Split clip A title was not restored in the clip list",
            device.wait(Until.hasObject(By.textContains("SPLIT_RED_TO_LIME • A")), 5_000))
        assertTrue("Split clip B title was not restored in the clip list",
            device.wait(Until.hasObject(By.textContains("SPLIT_RED_TO_LIME • B")), 5_000))
        val splitLengths = device.findObjects(By.text("00:02.00"))
        assertTrue("Expected two 2-second split segments, found ${splitLengths.size} matching clip lengths",
            splitLengths.size >= 2)
        val playheadAfterSplit = projectPositionMs(requireProjectTime(device))
        assertTrue("Playhead moved away from the new segment seam after split: $playheadAfterSplit",
            playheadAfterSplit in 1_850L..2_150L)
        android.util.Log.i(
            "SplitAtPlayheadTest",
            "stage=split-verified playheadBeforeMs=$splitTimeBefore playheadAfterMs=$playheadAfterSplit " +
                "clipTiles=2 splitLengths=00:02.00,00:02.00"
        )

        // Verify split is one undoable edit, and Redo restores the exact two-segment
        // state before continuing to the already-verified exported MP4 seam assertions.
        val splitUndo = requireObject(device, By.desc("Düzenleme geri al"), "Undo split operation")
        assertTrue("Undo was disabled immediately after splitting a clip", splitUndo.isEnabled)
        splitUndo.click()
        device.waitForIdle()
        // Clip tiles live in the horizontal clip carousel, not the vertical tool-panel ScrollView.
        // Verify the restored model through independent UI evidence first. A title can
        // be outside the compact carousel's accessibility viewport.
        device.waitForIdle()
        assertTrue(
            "Undo left split segment A in the clip list",
            !device.hasObject(By.textContains("SPLIT_RED_TO_LIME • A"))
        )
        assertTrue(
            "Undo left split segment B in the clip list",
            !device.hasObject(By.textContains("SPLIT_RED_TO_LIME • B"))
        )
        val restoredWholeClipLengths = device.findObjects(By.text("00:04.00"))
        assertTrue(
            "Undo did not restore a single 4-second clip; matching lengths=${restoredWholeClipLengths.size}",
            restoredWholeClipLengths.isNotEmpty()
        )
        // The redundant single-clip chooser is intentionally hidden on compact phones,
        // so the restored 4-second duration plus removal of A/B are the correct Undo checks.
        // Redo below restores two visible tiles and verifies their preserved split titles.
        android.util.Log.i(
            "SplitUndoRedoTest",
            "stage=undo-verified splitSegmentsRemoved=true singleClipLength=00:04.00"
        )

        val splitRedo = requireObject(device, By.desc("Düzenleme yinele"), "Redo split operation")
        assertTrue("Redo was disabled after undoing the split", splitRedo.isEnabled)
        splitRedo.click()
        device.waitForIdle()
        assertTrue(
            "Redo did not restore split segment A",
            device.wait(Until.hasObject(By.textContains("SPLIT_RED_TO_LIME • A")), 5_000)
        )
        assertTrue(
            "Redo did not restore split segment B",
            device.wait(Until.hasObject(By.textContains("SPLIT_RED_TO_LIME • B")), 5_000)
        )
        val redoneSplitLengths = device.findObjects(By.text("00:02.00"))
        assertTrue(
            "Redo did not restore two 2-second split segments; matching lengths=${redoneSplitLengths.size}",
            redoneSplitLengths.size >= 2
        )
        val playheadAfterSplitRedo = projectPositionMs(requireProjectTime(device))
        assertTrue(
            "Redo moved the split seam away from 2 seconds: $playheadAfterSplitRedo",
            playheadAfterSplitRedo in 1_850L..2_150L
        )
        android.util.Log.i(
            "SplitUndoRedoTest",
            "stage=redo-verified segmentA=true segmentB=true lengths=00:02.00,00:02.00 " +
                "playheadMs=$playheadAfterSplitRedo"
        )

        (device.findObject(By.text("Dışa aktar ›"))
            ?: throw AssertionError("Could not open export tab for split boundary validation")).click()
        assertTrue("Split export panel did not open",
            device.wait(Until.hasObject(By.text("PROJE / DIŞA AKTARMA")), 8_000))
        requireObject(device, By.clazz("android.widget.EditText"), "split export filename").setText(exportName)
        val exportScroller = UiScrollable(UiSelector().className("android.widget.ScrollView")).setAsVerticalList()
        assertTrue("Could not scroll to split MP4 export button",
            exportScroller.scrollIntoView(UiSelector().text("MP4 DIŞA AKTAR")))
        device.waitForIdle()
        requireObject(device, By.text("MP4 DIŞA AKTAR"), "split MP4 export button").click()

        val exportDeadline = SystemClock.uptimeMillis() + 120_000L
        var exported = false
        while (SystemClock.uptimeMillis() < exportDeadline) {
            val logs = device.executeShellCommand("logcat -d -s MultiClipExport:I")
            if (logs.contains("MULTICLIP_EXPORT_SAVED name=$exportName")) {
                exported = true
                break
            }
            if (logs.contains("MULTICLIP_EXPORT_TRANSFORM_FAILED name=$exportName") ||
                logs.contains("MULTICLIP_EXPORT_SAVE_FAILED name=$exportName")) {
                throw AssertionError("Split MP4 export failed: $logs")
            }
            SystemClock.sleep(500)
        }
        assertTrue("Split MP4 did not save within 120 seconds", exported)

        var outputUri: Uri? = null
        val queryDeadline = SystemClock.uptimeMillis() + 30_000L
        while (outputUri == null && SystemClock.uptimeMillis() < queryDeadline) {
            resolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Video.Media._ID),
                "${MediaStore.Video.Media.DISPLAY_NAME} = ?",
                arrayOf("$exportName.mp4"),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    outputUri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
                    )
                }
            }
            if (outputUri == null) SystemClock.sleep(300)
        }
        val savedUri = outputUri
            ?: throw AssertionError("Split export reported success but the MP4 was not found")

        val retriever = MediaMetadataRetriever()
        val videoMeasurement = try {
            retriever.setDataSource(context, savedUri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: throw AssertionError("Could not read split export duration")
            fun classifyFrameAt(timeMs: Long): String {
                val frame = retriever.getFrameAtTime(
                    timeMs * 1_000L,
                    MediaMetadataRetriever.OPTION_CLOSEST
                ) ?: throw AssertionError("Could not decode split export frame at $timeMs ms")
                try {
                    val pixel = frame.getPixel(frame.width / 2, frame.height / 2)
                    val red = android.graphics.Color.red(pixel)
                    val green = android.graphics.Color.green(pixel)
                    val blue = android.graphics.Color.blue(pixel)
                    val label = when {
                        green > red + 35 && green > blue + 35 -> "GREEN"
                        red > green + 35 && red > blue + 35 -> "RED"
                        else -> "UNKNOWN"
                    }
                    return "$label($red,$green,$blue)"
                } finally {
                    frame.recycle()
                }
            }
            durationMs to (1_800L..2_200L step 25L).map { timeMs ->
                timeMs to classifyFrameAt(timeMs)
            }
        } finally {
            retriever.release()
        }
        val durationMs = videoMeasurement.first
        val frameSamples = videoMeasurement.second
        assertTrue("Split export must remain about 4 seconds; duration=$durationMs ms",
            durationMs in 3_900L..4_200L)
        val firstGreenIndex = frameSamples.indexOfFirst { it.second.startsWith("GREEN") }
        assertTrue("Split export has no red→lime boundary near 2 s: $frameSamples", firstGreenIndex > 0)
        val lastRed = frameSamples[firstGreenIndex - 1]
        val firstGreen = frameSamples[firstGreenIndex]
        assertTrue("Split export boundary frames are not red→lime: $lastRed -> $firstGreen",
            lastRed.second.startsWith("RED") && firstGreen.second.startsWith("GREEN"))
        val videoBoundaryMs = firstGreen.first
        assertTrue("Split export video boundary drifted: $videoBoundaryMs ms; samples=$frameSamples",
            videoBoundaryMs in 1_900L..2_100L)

        val decodedAudio = decodeExportedAudioMono(context, savedUri)
        val audioBefore = toneMagnitude(decodedAudio, 997.0, 1_650L, 1_900L)
        val audioAfter = toneMagnitude(decodedAudio, 997.0, 2_100L, 2_350L)
        assertTrue("Split export has an early 997 Hz tone before the seam: $audioBefore",
            audioBefore < 900.0)
        assertTrue("Split export lost 997 Hz source tone after the seam: $audioAfter",
            audioAfter > 2_000.0)
        var audioToneOnsetMs: Long? = null
        for (windowStartMs in 1_500L..2_500L step 25L) {
            val magnitude = toneMagnitude(decodedAudio, 997.0, windowStartMs, windowStartMs + 100L)
            if (magnitude >= 1_500.0) {
                audioToneOnsetMs = windowStartMs
                break
            }
        }
        val audioOnsetWindowMs = audioToneOnsetMs
            ?: throw AssertionError("Could not detect 997 Hz tone onset in split export")
        val seamDeltaMs = kotlin.math.abs(audioOnsetWindowMs - videoBoundaryMs)
        android.util.Log.i(
            "SplitAtPlayheadTest",
            "stage=export-measured durationMs=$durationMs videoBoundaryMs=$videoBoundaryMs " +
                "audioToneOnsetWindowMs=$audioOnsetWindowMs audioBefore=$audioBefore " +
                "audioAfter=$audioAfter seamDeltaMs=$seamDeltaMs"
        )
        assertTrue(
            "Split export audio/video seam differs by $seamDeltaMs ms " +
                "(audioWindow=$audioOnsetWindowMs ms, videoBoundary=$videoBoundaryMs ms)",
            seamDeltaMs <= 150L
        )
        assertTrue("Splitting/export modified the source fixture size",
            fixture.isFile && fixture.length() == sourceSize)
        assertTrue("Splitting/export modified the source fixture contents",
            sha256(fixture) == sourceHash)
        android.util.Log.i(
            "SplitAtPlayheadTest",
            "stage=verified clips=2 totalDurationMs=$durationMs seamDeltaMs=$seamDeltaMs originalsUnchanged=true"
        )
    }

    @Test
    fun filmstripLoadsFramesFromDistinctTimestamps() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val packageName = "com.mathmotor.video.next"
        val fixtureUri = "file:///data/user/0/$packageName/files/editor-test-video.mp4"

        device.executeShellCommand("logcat -c")
        device.executeShellCommand(
            "am start -n $packageName/com.mathmotor.video.EditorTestEntryActivity " +
                "--es video_uri $fixtureUri --es draft_id 55555555-5555-4555-8555-555555555555"
        )
        assertTrue(
            "Editor did not show the video lane for thumbnail test",
            device.wait(Until.hasObject(By.desc("Zaman çizelgesi etiketi: VIDEO")), 20_000)
        )
        assertTrue(
            "Project time label did not appear for thumbnail test",
            device.wait(Until.hasObject(By.desc("Oynatma zamanı ve toplam video süresi")), 20_000)
        )
        assertTrue("Thumbnail test sample clip was not loaded", awaitClipAdded(device))
        device.waitForIdle()

        val readyPattern = Regex("TIMELINE_THUMBNAIL_READY timestampMs=(\\d+) fingerprint=(-?\\d+)")
        val deadline = SystemClock.uptimeMillis() + 30_000L
        var readyFrames = emptyList<Pair<Long, Long>>()
        while (SystemClock.uptimeMillis() < deadline) {
            val log = device.executeShellCommand("logcat -d -s TimelineThumbnail:I")
            readyFrames = readyPattern.findAll(log).map { match ->
                match.groupValues[1].toLong() to match.groupValues[2].toLong()
            }.toList()
            if (readyFrames.map { it.first }.distinct().size >= 5 &&
                readyFrames.map { it.second }.distinct().size >= 2
            ) break
            SystemClock.sleep(350)
        }

        val uniqueTimes = readyFrames.map { it.first }.distinct()
        val uniqueFrames = readyFrames.map { it.second }.distinct()
        android.util.Log.i(
            "TimelineThumbnailTest",
            "stage=decoded timestamps=${uniqueTimes.size} fingerprints=${uniqueFrames.size} " +
                "samples=${readyFrames.take(12)}"
        )
        assertTrue(
            "Filmstrip did not decode enough distinct requested timestamps: ${uniqueTimes.take(12)}",
            uniqueTimes.size >= 5
        )
        assertTrue(
            "Multiple timestamps appear to reuse the same frame fingerprint: ${uniqueFrames.size}",
            uniqueFrames.size >= 2
        )

        val loadedImages = device.findObjects(By.descContains("Klip karesi"))
            .filter { image ->
                image.contentDescription?.toString()?.contains("yüklendi") == true &&
                    image.visibleBounds.width() > 0 && image.visibleBounds.height() > 0
            }
        assertTrue(
            "Too few loaded thumbnails are exposed in the visible filmstrip: ${loadedImages.size}",
            loadedImages.size >= 3
        )
    }


    private data class DecodedAudioChunk(val firstSampleIndex: Long, val samples: ShortArray)
    private data class DecodedMonoAudio(val sampleRate: Int, val chunks: List<DecodedAudioChunk>)

    private fun writeToneWav(
        file: java.io.File, sampleRate: Int, durationMs: Int, frequencyHz: Double, amplitude: Int
    ) {
        val count = sampleRate * durationMs / 1_000
        val dataBytes = count * 2
        java.io.FileOutputStream(file).use { out ->
            fun le(value: Int, bytes: Int) { repeat(bytes) { n -> out.write((value ushr (n * 8)) and 0xff) } }
            out.write("RIFF".toByteArray(Charsets.US_ASCII)); le(36 + dataBytes, 4)
            out.write("WAVE".toByteArray(Charsets.US_ASCII)); out.write("fmt ".toByteArray(Charsets.US_ASCII))
            le(16, 4); le(1, 2); le(1, 2); le(sampleRate, 4); le(sampleRate * 2, 4); le(2, 2); le(16, 2)
            out.write("data".toByteArray(Charsets.US_ASCII)); le(dataBytes, 4)
            for (i in 0 until count) {
                val value = (amplitude * kotlin.math.sin(2.0 * Math.PI * frequencyHz * i / sampleRate)).toInt()
                le(value and 0xffff, 2)
            }
        }
        assertTrue("Could not write deterministic audio fixture: ${file.name}", file.isFile && file.length() > 44L)
    }

    private fun decodeExportedAudioMono(context: android.content.Context, uri: Uri): DecodedMonoAudio {
        val extractor = android.media.MediaExtractor()
        var decoder: android.media.MediaCodec? = null
        val chunks = mutableListOf<DecodedAudioChunk>()
        try {
            if (uri.scheme == "file") extractor.setDataSource(uri.path ?: throw AssertionError("File URI has no path"))
            else extractor.setDataSource(context, uri, null)
            var track = -1
            var inputFormat: android.media.MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                if (fmt.getString(android.media.MediaFormat.KEY_MIME).orEmpty().startsWith("audio/")) {
                    track = i; inputFormat = fmt; break
                }
            }
            assertTrue("Exported MP4 has no audio track", track >= 0 && inputFormat != null)
            val fmt = inputFormat!!
            val mime = fmt.getString(android.media.MediaFormat.KEY_MIME)
                ?: throw AssertionError("Audio track MIME missing")
            extractor.selectTrack(track)
            decoder = android.media.MediaCodec.createDecoderByType(mime)
            decoder.configure(fmt, null, null, 0)
            decoder.start()
            var rate = fmt.getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE)
            var channels = fmt.getInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT)
            var encoding = android.media.AudioFormat.ENCODING_PCM_16BIT
            var inputDone = false; var outputDone = false; var lastPts = 0L; var turns = 0
            val info = android.media.MediaCodec.BufferInfo()
            while (!outputDone && turns++ < 30_000) {
                if (!inputDone) {
                    val inputIndex = decoder.dequeueInputBuffer(10_000L)
                    if (inputIndex >= 0) {
                        val buf = decoder.getInputBuffer(inputIndex) ?: throw AssertionError("Null decoder input buffer")
                        buf.clear()
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(inputIndex, 0, 0, lastPts,
                                android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            val pts = extractor.sampleTime.coerceAtLeast(0L)
                            decoder.queueInputBuffer(inputIndex, 0, size, pts, 0)
                            lastPts = pts; extractor.advance()
                        }
                    }
                }
                when (val outIndex = decoder.dequeueOutputBuffer(info, 10_000L)) {
                    android.media.MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    android.media.MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outFmt = decoder.outputFormat
                        rate = outFmt.getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE)
                        channels = outFmt.getInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT)
                        if (outFmt.containsKey(android.media.MediaFormat.KEY_PCM_ENCODING))
                            encoding = outFmt.getInteger(android.media.MediaFormat.KEY_PCM_ENCODING)
                    }
                    else -> if (outIndex >= 0) {
                        if (info.size > 0) {
                            val buf = decoder.getOutputBuffer(outIndex) ?: throw AssertionError("Null decoder output buffer")
                            buf.order(java.nio.ByteOrder.LITTLE_ENDIAN)
                            buf.position(info.offset); buf.limit(info.offset + info.size)
                            val bytes = when (encoding) {
                                android.media.AudioFormat.ENCODING_PCM_FLOAT -> 4
                                android.media.AudioFormat.ENCODING_PCM_8BIT -> 1
                                else -> 2
                            }
                            val frameBytes = bytes * channels
                            val frameCount = info.size / frameBytes
                            val mono = ShortArray(frameCount)
                            for (frame in 0 until frameCount) {
                                var sum = 0.0
                                for (channel in 0 until channels) {
                                    val at = info.offset + frame * frameBytes + channel * bytes
                                    val value = when (encoding) {
                                        android.media.AudioFormat.ENCODING_PCM_FLOAT ->
                                            (buf.getFloat(at).coerceIn(-1f, 1f) * 32767f).toInt()
                                        android.media.AudioFormat.ENCODING_PCM_8BIT ->
                                            ((buf.get(at).toInt() and 0xff) - 128) shl 8
                                        else -> buf.getShort(at).toInt()
                                    }
                                    sum += value
                                }
                                mono[frame] = (sum / channels).toInt().coerceIn(-32768, 32767).toShort()
                            }
                            val first = info.presentationTimeUs.coerceAtLeast(0L) * rate / 1_000_000L
                            chunks.add(DecodedAudioChunk(first, mono))
                        }
                        if ((info.flags and android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true
                        decoder.releaseOutputBuffer(outIndex, false)
                    }
                }
            }
            assertTrue("Audio decoder timed out before EOS", outputDone)
            assertTrue("Audio decoder produced no PCM", chunks.isNotEmpty())
            return DecodedMonoAudio(rate, chunks)
        } finally {
            try { decoder?.stop() } catch (_: Exception) { }
            decoder?.release()
            extractor.release()
        }
    }

    private fun toneMagnitude(audio: DecodedMonoAudio, frequencyHz: Double, startMs: Long, endMs: Long): Double {
        val first = startMs * audio.sampleRate / 1_000L
        val last = endMs * audio.sampleRate / 1_000L
        var sinSum = 0.0; var cosSum = 0.0; var count = 0L
        for (chunk in audio.chunks) {
            val chunkEnd = chunk.firstSampleIndex + chunk.samples.size
            val from = maxOf(first, chunk.firstSampleIndex)
            val until = minOf(last, chunkEnd)
            for (index in from until until) {
                val sample = chunk.samples[(index - chunk.firstSampleIndex).toInt()].toDouble()
                val phase = 2.0 * Math.PI * frequencyHz * index / audio.sampleRate
                sinSum += sample * kotlin.math.sin(phase)
                cosSum += sample * kotlin.math.cos(phase)
                count++
            }
        }
        assertTrue("Insufficient decoded samples in $startMs-$endMs ms for ${frequencyHz}Hz: $count",
            count >= (last - first) * 3 / 4)
        return 2.0 * kotlin.math.sqrt(sinSum * sinSum + cosSum * cosSum) / count.coerceAtLeast(1L)
    }

    private fun projectDurationMs(label: String): Long {
        val match = Regex("(?:Proje\\s+)?\\d{2}:\\d{2}\\.\\d{2}\\s*/\\s*(\\d{2}):(\\d{2})\\.(\\d{2})")
            .find(label) ?: throw AssertionError("Could not parse project duration from: $label")
        val minutes = match.groupValues[1].toLong()
        val seconds = match.groupValues[2].toLong()
        val centiseconds = match.groupValues[3].toLong()
        return (minutes * 60 + seconds) * 1000 + centiseconds * 10
    }

    private fun awaitClipAdded(device: UiDevice): Boolean {
        val deadline = SystemClock.uptimeMillis() + 25_000L
        while (SystemClock.uptimeMillis() < deadline) {
            val log = device.executeShellCommand("logcat -d -s EditorSmokeTest:I")
            if (log.contains("EDITOR_SMOKE_CLIP_ADDED")) return true
            if (log.contains("EDITOR_SMOKE_CLIP_REJECTED")) return false
            SystemClock.sleep(400)
        }
        return false
    }

    private fun requireProjectTime(device: UiDevice): String {
        val label = requireObject(
            device, By.desc("Oynatma zamanı ve toplam video süresi"), "timeline time label"
        ).text
        assertTrue(
            "Timeline time display should be one clean line without duplicate project/clip labels: $label",
            !label.contains("Proje") && !label.contains("Klip") && '\n' !in label
        )
        return label
    }

    private fun zoomPercent(description: CharSequence): Int {
        val match = Regex("(\\d+)%").find(description.toString())
            ?: throw AssertionError("Could not parse timeline zoom label: $description")
        return match.groupValues[1].toInt()
    }

    private fun projectPositionMs(label: String): Long {
        val match = Regex("(?:Proje\\s+)?(\\d{2}):(\\d{2})\\.(\\d{2})").find(label)
            ?: throw AssertionError("Could not parse project time label: $label")
        val minutes = match.groupValues[1].toLong()
        val seconds = match.groupValues[2].toLong()
        val centiseconds = match.groupValues[3].toLong()
        return (minutes * 60 + seconds) * 1000 + centiseconds * 10
    }

    private fun sha256(file: java.io.File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    }

    private fun findObjectByAny(device: UiDevice, vararg selectors: BySelector): UiObject2? {
        for (selector in selectors) {
            val found = device.findObject(selector)
            if (found != null) return found
        }
        return null
    }

    private fun requireObject(device: UiDevice, selector: BySelector, description: String): UiObject2 =
        device.findObject(selector) ?: throw AssertionError("Could not find $description")
}

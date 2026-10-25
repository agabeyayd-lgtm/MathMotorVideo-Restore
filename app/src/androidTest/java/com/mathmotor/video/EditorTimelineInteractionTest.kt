package com.mathmotor.video

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
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

        device.executeShellCommand("logcat -c")
        device.executeShellCommand("am force-stop $packageName")
        device.executeShellCommand(
            "am start -n $packageName/com.mathmotor.video.EditorTestEntryActivity " +
                "--es video_uri $fixtureUri --es draft_id 22222222-2222-4222-8222-222222222222"
        )

        assertTrue(
            "Editor did not show the video lane",
            device.wait(Until.hasObject(By.text("VIDEO")), 20_000)
        )
        assertTrue(
            "Project time label did not appear",
            device.wait(Until.hasObject(By.textContains("Proje")), 20_000)
        )
        assertTrue("Sample clip was not loaded into the editor", awaitClipAdded(device))
        device.waitForIdle()
        SystemClock.sleep(250)

        val videoLaneLabel = requireObject(device, By.text("VIDEO"), "VIDEO lane label")
        val laneLabelBounds = videoLaneLabel.visibleBounds
        val laneLeft = laneLabelBounds.right
        val laneRight = device.displayWidth - (4 * density).toInt()
        val laneWidth = (laneRight - laneLeft).coerceAtLeast(1)
        val laneY = laneLabelBounds.centerY()
        val centerX = laneLeft + laneWidth / 2
        val initialTime = requireProjectTime(device)
        val initialMs = projectPositionMs(initialTime)

        // Dragging the playhead handle must scroll the timeline under the stationary line.
        val dragPx = (70f * density).toInt()
        device.swipe(centerX, laneY, (centerX + dragPx).coerceAtMost(laneRight - 2), laneY, 35)
        device.waitForIdle()
        SystemClock.sleep(180)
        val afterPlayheadDrag = requireProjectTime(device)
        val afterPlayheadMs = projectPositionMs(afterPlayheadDrag)
        assertTrue(
            "Dragging the playhead did not change the project time: $initialTime -> $afterPlayheadDrag",
            initialMs != afterPlayheadMs
        )

        // A centered pinch should change clip scale while keeping the center time stable.
        val laneScroll = device.findObjects(By.clazz("android.widget.HorizontalScrollView"))
            .firstOrNull {
                val bounds = it.visibleBounds
                bounds.top <= laneY && bounds.bottom >= laneY && bounds.left >= laneLeft - 2
            } ?: throw AssertionError("Could not find the VIDEO timeline scroll viewport")
        val clipBeforeZoom = requireObject(device, By.descContains("Klip 1:"), "first video clip")
        val clipWidthBeforeZoom = clipBeforeZoom.visibleBounds.width()
        val beforePinchTime = projectPositionMs(requireProjectTime(device))
        laneScroll.pinchOpen(0.5f)
        device.waitForIdle()
        SystemClock.sleep(250)
        val clipAfterZoom = requireObject(device, By.descContains("Klip 1:"), "first video clip after zoom")
        val clipWidthAfterZoom = clipAfterZoom.visibleBounds.width()
        val afterPinchTime = projectPositionMs(requireProjectTime(device))
        assertTrue(
            "Pinch zoom did not expand the clip filmstrip ($clipWidthBeforeZoom -> $clipWidthAfterZoom px)",
            clipWidthAfterZoom > clipWidthBeforeZoom
        )
        assertTrue(
            "Centered pinch moved the time under the fixed playhead ($beforePinchTime -> $afterPinchTime ms)",
            abs(beforePinchTime - afterPinchTime) <= 150L
        )

        // Swiping the filmstrip itself must allow its HorizontalScrollView to take over,
        // rather than the clip view consuming the gesture as an in-clip scrub.
        val afterZoomLabel = requireObject(device, By.text("VIDEO"), "VIDEO lane label after zoom")
        val afterZoomBounds = afterZoomLabel.visibleBounds
        val scrollLeft = afterZoomBounds.right
        val scrollWidth = (laneRight - scrollLeft).coerceAtLeast(1)
        val swipeY = afterZoomBounds.centerY()
        device.swipe(
            scrollLeft + scrollWidth * 9 / 10,
            swipeY,
            scrollLeft + scrollWidth / 10,
            swipeY,
            40
        )
        device.waitForIdle()
        SystemClock.sleep(250)
        val endTime = requireProjectTime(device)
        val endMs = projectPositionMs(endTime)
        assertTrue("A left swipe on the clip filmstrip did not move the timeline", endMs != afterPinchTime)
        assertTrue(
            "The timeline could not reach the final 8-second frame: $endTime",
            endTime.contains("/ 00:08.")
        )

        // The symmetric leading inset must also let the start align exactly under the center line.
        device.swipe(
            scrollLeft + scrollWidth / 10,
            swipeY,
            scrollLeft + scrollWidth * 9 / 10,
            swipeY,
            40
        )
        device.waitForIdle()
        SystemClock.sleep(200)
        val startTime = requireProjectTime(device)
        assertTrue("The timeline could not return to its first frame: $startTime", startTime.contains("Proje 00:00."))
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

    private fun requireProjectTime(device: UiDevice): String =
        requireObject(device, By.textContains("Proje"), "project time label").text

    private fun projectPositionMs(label: String): Long {
        val match = Regex("Proje\\s+(\\d{2}):(\\d{2})\\.(\\d{2})").find(label)
            ?: throw AssertionError("Could not parse project time label: $label")
        val minutes = match.groupValues[1].toLong()
        val seconds = match.groupValues[2].toLong()
        val centiseconds = match.groupValues[3].toLong()
        return (minutes * 60 + seconds) * 1000 + centiseconds * 10
    }

    private fun requireObject(device: UiDevice, selector: BySelector, description: String): UiObject2 =
        device.findObject(selector) ?: throw AssertionError("Could not find $description")
}

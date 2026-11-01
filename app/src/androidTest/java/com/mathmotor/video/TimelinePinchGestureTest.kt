package com.mathmotor.video

import android.graphics.Point
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the real timeline touch path against the deterministic eight-second
 * video staged by capture-emulator-screenshots.sh.
 *
 * The 00:05 ruler marker is used as a visible time anchor. After pinching, its
 * screen position must remain stable; a one-finger swipe on the VIDEO lane must
 * then move that ruler marker too, proving the shared horizontal scroll sync.
 */
@RunWith(AndroidJUnit4::class)
class TimelinePinchGestureTest {
    @Test
    fun pinchKeepsTimeAnchorAndVideoLaneSwipeMovesRulerWithIt() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)

        val fixtureReady = device.executeShellCommand(
            "run-as com.mathmotor.video.next test -s files/editor-test-video.mp4 && echo FIXTURE_READY"
        )
        assertTrue("The deterministic editor test video was not staged", fixtureReady.contains("FIXTURE_READY"))

        device.executeShellCommand("am force-stop com.mathmotor.video.next")
        device.executeShellCommand("logcat -c")
        device.executeShellCommand(
            "am start -n com.mathmotor.video.next/com.mathmotor.video.EditorTestEntryActivity " +
                "--es video_uri file:///data/user/0/com.mathmotor.video.next/files/editor-test-video.mp4 " +
                "--es draft_id 22222222-2222-4222-8222-222222222222"
        )

        var clipLoaded = false
        for (attempt in 0 until 30) {
            val log = device.executeShellCommand("logcat -d -t 1000 -s EditorSmokeTest:I")
            if (log.contains("EDITOR_SMOKE_CLIP_ADDED")) {
                clipLoaded = true
                break
            }
            Thread.sleep(500)
        }
        assertTrue("Editor did not confirm that the eight-second fixture was added", clipLoaded)
        assertTrue(
            "The video lane did not appear",
            device.wait(Until.hasObject(By.text("VIDEO")), 15_000)
        )
        assertTrue(
            "The five-second ruler marker did not appear",
            device.wait(Until.hasObject(By.textContains("00:05")), 10_000)
        )
        device.waitForIdle()

        val initialMarker = device.findObject(By.textContains("00:05"))
            ?: throw AssertionError("Could not locate the 00:05 ruler marker")
        val initialMarkerLeft = initialMarker.visibleBounds.left
        val anchorX = initialMarkerLeft.coerceIn(1, device.displayWidth - 1)
        val videoLane = device.findObject(By.text("VIDEO"))
            ?: throw AssertionError("Could not locate the VIDEO lane")
        val gestureY = videoLane.visibleBounds.centerY

        performPinch(device, anchorX, gestureY, device.displayWidth, startHalfSpan = 24, endHalfSpan = 92)
        assertTrue(
            "Pinch-out did not update the timeline zoom status",
            device.wait(Until.hasObject(By.textContains("Zaman çizelgesi:")), 5_000)
        )
        device.waitForIdle()
        val zoomedIn = readZoom(device)
        assertTrue("Pinch-out did not zoom in enough: $zoomedIn", zoomedIn >= 1.4f)
        val markerAfterZoomIn = findMarkerLeft(device)
        assertTrue(
            "Pinch-out moved the 00:05 time anchor by ${kotlin.math.abs(markerAfterZoomIn - initialMarkerLeft)} px",
            kotlin.math.abs(markerAfterZoomIn - initialMarkerLeft) <= 12
        )

        performPinch(device, markerAfterZoomIn, gestureY, device.displayWidth, startHalfSpan = 92, endHalfSpan = 66)
        device.waitForIdle()
        val zoomedOut = readZoom(device)
        assertTrue("Pinch-in did not reduce zoom ($zoomedIn -> $zoomedOut)", zoomedOut < zoomedIn)
        val markerBeforeLaneSwipe = findMarkerLeft(device)

        val swipeStartX = (device.displayWidth * 0.30f).toInt()
        val swipeEndX = (device.displayWidth * 0.55f).toInt()
        assertTrue(
            "One-finger swipe on the video lane was not dispatched",
            device.swipe(swipeStartX, gestureY, swipeEndX, gestureY, 30)
        )
        device.waitForIdle()
        val markerAfterLaneSwipe = findMarkerLeft(device)
        assertTrue(
            "Swiping the VIDEO lane did not move the ruler with it " +
                "(marker left: $markerBeforeLaneSwipe -> $markerAfterLaneSwipe)",
            markerAfterLaneSwipe > markerBeforeLaneSwipe + 12
        )
    }

    private fun performPinch(
        device: UiDevice,
        anchorX: Int,
        y: Int,
        screenWidth: Int,
        startHalfSpan: Int,
        endHalfSpan: Int
    ) {
        assertTrue("Pinch anchor is too close to the screen edge", anchorX > endHalfSpan + 8)
        assertTrue(
            "Pinch anchor is too close to the right screen edge",
            anchorX < screenWidth - endHalfSpan - 8
        )
        val performed = device.performTwoPointerGesture(
            Point(anchorX - startHalfSpan, y),
            Point(anchorX + startHalfSpan, y),
            Point(anchorX - endHalfSpan, y),
            Point(anchorX + endHalfSpan, y),
            24
        )
        assertTrue("Android did not dispatch the two-pointer pinch", performed)
    }

    private fun findMarkerLeft(device: UiDevice): Int {
        val marker = device.findObject(By.textContains("00:05"))
            ?: throw AssertionError("The 00:05 ruler marker disappeared")
        return marker.visibleBounds.left
    }

    private fun readZoom(device: UiDevice): Float {
        val status = device.findObject(By.textContains("Zaman çizelgesi:"))
            ?: throw AssertionError("The timeline zoom status was not exposed")
        val match = Regex("""([0-9]+(?:[.][0-9]+)?)×""").find(status.text.orEmpty())
            ?: throw AssertionError("Could not parse timeline zoom from: ${status.text}")
        return match.groupValues[1].toFloat()
    }
}

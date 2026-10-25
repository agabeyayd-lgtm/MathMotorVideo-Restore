package com.mathmotor.video

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
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
            device.wait(Until.hasObject(By.text("VIDEO")), 20_000)
        )
        assertTrue(
            "Project time label did not appear",
            device.wait(Until.hasObject(By.textContains("Proje")), 20_000)
        )
        assertTrue("Sample clip was not loaded into the editor", awaitClipAdded(device))
        android.util.Log.i("TimelineInteractionTest", "stage=clip-loaded")
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
        android.util.Log.i("TimelineInteractionTest", "stage=initial-time value=$initialTime laneY=$laneY centerX=$centerX")

        // Dragging the playhead handle must scroll the timeline under the stationary line.
        val dragPx = (70f * density).toInt()
        device.swipe(centerX, laneY, (centerX + dragPx).coerceAtMost(laneRight - 2), laneY, 35)
        device.waitForIdle()
        SystemClock.sleep(180)
        val afterPlayheadDrag = requireProjectTime(device)
        val afterPlayheadMs = projectPositionMs(afterPlayheadDrag)
        android.util.Log.i("TimelineInteractionTest", "stage=after-playhead-drag value=$afterPlayheadDrag")
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
        val afterZoomLabel = requireObject(device, By.text("VIDEO"), "VIDEO lane label after zoom")
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
    fun rightAndLeftTrimHandlesChangeProjectDuration() {
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
            device.wait(Until.hasObject(By.text("VIDEO")), 20_000)
        )
        assertTrue(
            "Project time label did not appear for trim test",
            device.wait(Until.hasObject(By.textContains("Proje")), 20_000)
        )
        assertTrue("Trim test sample clip was not loaded", awaitClipAdded(device))
        device.waitForIdle()
        SystemClock.sleep(300)

        val projectLabel = requireObject(device, By.textContains("Proje"), "project-time label")
        val originalDurationMs = projectDurationMs(projectLabel.text)
        assertTrue("Fixture duration is unexpectedly short: $originalDurationMs ms", originalDurationMs >= 7_800L)

        val laneLabel = requireObject(device, By.text("VIDEO"), "VIDEO lane label")
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
            "stage=left-trim before=$durationAfterRightTrimMs after=$durationAfterLeftTrimMs"
        )
        assertTrue(
            "Dragging the left trim handle did not shorten the project: $durationAfterRightTrimMs -> $durationAfterLeftTrimMs ms",
            durationAfterLeftTrimMs <= durationAfterRightTrimMs - 150L
        )
        assertTrue("Left trim produced an invalid duration: $durationAfterLeftTrimMs ms", durationAfterLeftTrimMs >= 500L)
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
            device.wait(Until.hasObject(By.text("VIDEO")), 20_000)
        )
        assertTrue(
            "Project time label did not appear for text test",
            device.wait(Until.hasObject(By.textContains("Proje")), 20_000)
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
        val input = requireObject(device, By.clazz("android.widget.EditText"), "text editor input")
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
            device.wait(Until.hasObject(By.text("VIDEO")), 20_000)
        )
        assertTrue(
            "Project time label did not appear for thumbnail test",
            device.wait(Until.hasObject(By.textContains("Proje")), 20_000)
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

    private fun projectDurationMs(label: String): Long {
        val match = Regex("Proje\\s+\\d{2}:\\d{2}\\.\\d{2}\\s*/\\s*(\\d{2}):(\\d{2})\\.(\\d{2})")
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

    private fun requireProjectTime(device: UiDevice): String =
        requireObject(device, By.textContains("Proje"), "project time label").text

    private fun zoomPercent(description: CharSequence): Int {
        val match = Regex("(\\d+)%").find(description.toString())
            ?: throw AssertionError("Could not parse timeline zoom label: $description")
        return match.groupValues[1].toInt()
    }

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

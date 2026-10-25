package com.mathmotor.video

import android.content.ContentUris
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.os.SystemClock
import java.security.MessageDigest
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
            device.wait(Until.hasObject(By.text("VIDEO")), 20_000)
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
            device.wait(Until.hasObject(By.text("VIDEO")), 8_000)
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
            .put("timelineHorizontalScrollMs", 1400L)
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
        val restoreLog = device.executeShellCommand("logcat -d -s ProjectPersistenceTest:I")
        assertTrue(
            "Manual JSON did not preserve clip order and the two trim ranges: $restoreLog",
            restoreLog.contains("clips=FIRST_ORDERED_CLIP:500-7520,SECOND_ORDERED_CLIP:200-2700")
        )
        assertTrue(
            "Manual JSON did not preserve the playhead and zoom: $restoreLog",
            restoreLog.contains("playheadMs=7700 zoomPercent=200")
        )
        val restoredScrollMatch = Regex("scrollMs=(\\\\d+)").find(restoreLog)
            ?: throw AssertionError("Manual JSON restore did not report the saved timeline scroll: $restoreLog")
        val restoredScrollMs = restoredScrollMatch.groupValues[1].toLong()
        assertTrue(
            "Manual JSON did not preserve the nonzero 1.4-second timeline scroll: $restoredScrollMs ms; $restoreLog",
            restoredScrollMs in 1_000L..1_800L
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
        assertTrue(
            "Manual JSON did not restore the music reference",
            device.wait(Until.hasObject(By.textContains("ROUND_TRIP_MUSIC")), 8_000)
        )
        assertTrue(
            "Manual JSON did not restore the sound-effect reference",
            device.wait(Until.hasObject(By.textContains("ROUND_TRIP_EFFECT")), 8_000)
        )
        val audioPanelScroller = UiScrollable(UiSelector().className("android.widget.ScrollView"))
            .setAsVerticalList()
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

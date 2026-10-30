package com.mathmotor.video

import android.content.ContentValues
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
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
        val timeRuler = device.findObject(By.desc("Zaman çizelgesi etiketi: SÜRE"))
            ?: throw AssertionError("Shared time ruler did not appear below preview controls")
        val timeDisplay = device.findObject(By.desc("Oynatma zamanı ve toplam video süresi"))
            ?: throw AssertionError("Single-line current/total time display is missing")
        assertTrue(
            "The duplicate timeline SeekBar must be removed; scrub directly on the playhead/timeline",
            device.findObject(By.desc("Zaman çizelgesinde oynatma noktası")) == null
        )
        val directPlayhead = device.findObject(By.desc("Oynatma çizgisi; sürükleyerek zamanı değiştir"))
            ?: throw AssertionError("Directly draggable timeline playhead is missing")
        val density = instrumentation.targetContext.resources.displayMetrics.density
        assertTrue(
            "Direct playhead touch target should be at least 36dp wide: ${directPlayhead.visibleBounds}",
            directPlayhead.visibleBounds.width() >= (36f * density).toInt()
        )
        assertTrue(
            "Timeline time display should remain a single clean line: ${timeDisplay.text}",
            !timeDisplay.text.contains("Proje") && !timeDisplay.text.contains("Klip") &&
                '\n' !in timeDisplay.text
        )
        assertTrue(
            "Timeline left gutter should use a compact icon, bounds=${timeRuler.visibleBounds}",
            timeRuler.visibleBounds.width() <= (28f * density).toInt()
        )
        assertTrue(
            "Timeline time display should not wrap to multiple visual lines, bounds=${timeDisplay.visibleBounds}",
            timeDisplay.visibleBounds.height() <= (24f * density).toInt()
        )
        val clipTab = device.findObject(By.desc("Klip araçları sekmesi"))
            ?: throw AssertionError("Edit tool tab is missing from the bottom dock")
        assertTrue(
            "Playback control should be below the preview",
            playback.visibleBounds.top >= preview.visibleBounds.bottom
        )
        assertTrue(
            "Playback row should sit above the time ruler",
            playback.visibleBounds.bottom <= timeRuler.visibleBounds.top
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
        val exportTab = device.findObject(By.desc("Dışa aktarma sekmesi"))
            ?: throw AssertionError("Export tool tab is missing from the bottom dock")
        assertTrue(
            "Bottom tool order should be Edit, Audio, Text, Export",
            clipTab.visibleBounds.left < audioTab.visibleBounds.left &&
                audioTab.visibleBounds.left < textTabForOrder.visibleBounds.left &&
                textTabForOrder.visibleBounds.left < exportTab.visibleBounds.left
        )
        listOf(clipTab, audioTab, textTabForOrder, exportTab).forEachIndexed { index, tab ->
            assertTrue(
                "Bottom dock tab $index is below the 44dp touch-target minimum: ${tab.visibleBounds}",
                tab.visibleBounds.height() >= (44f * density).toInt()
            )
        }
        clipTab.click()
        assertTrue(
            "Empty clip panel should offer a direct add-video action",
            device.wait(Until.hasObject(By.desc("Klip düzenlemek için video ekle")), 5_000)
        )
        val emptyAddVideo = device.findObject(By.desc("Klip düzenlemek için video ekle"))
            ?: throw AssertionError("Primary empty-editor add-video action disappeared")
        assertTrue(
            "Empty-editor primary action is too small: ${emptyAddVideo.visibleBounds}",
            emptyAddVideo.visibleBounds.width() >= (120f * density).toInt() &&
                emptyAddVideo.visibleBounds.height() >= (44f * density).toInt()
        )
        assertTrue(
            "Clip editing and trim controls should stay hidden until a video is added",
            device.findObject(By.text("Böl ✂")) == null &&
                device.findObject(By.text("Kopyala")) == null &&
                device.findObject(By.text("Klibi kaldır")) == null
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

        // Recreate the editor after changing the emulator size. The earlier editor
        // was initialized at 320x640 and intentionally caches its short-screen layout
        // classification; resizing that same Activity would not represent a real 393x873
        // first launch. A fresh editor instance must measure the larger viewport itself.
        try {
            device.pressBack()
            assertTrue(
                "Could not return to the home screen before the tall-phone layout check",
                device.wait(Until.hasObject(By.text("Videoların")), 8_000)
            )
            device.executeShellCommand("wm size 393x873")
            SystemClock.sleep(800)
            device.waitForIdle()
            val sizeOverride = device.executeShellCommand("wm size")
            assertTrue(
                "Could not apply the taller phone-sized viewport: $sizeOverride",
                sizeOverride.contains("393x873")
            )
            assertTrue(
                "UiDevice did not report the taller phone-sized viewport: " +
                    "${device.displayWidth}x${device.displayHeight}",
                device.displayWidth >= 390 && device.displayHeight >= 870
            )

            val tallOpenEditor = device.findObject(
                By.clazz("android.widget.Button").textContains("Boş proje oluştur")
            ) ?: throw AssertionError("Could not find the empty-editor action after viewport resize")
            tallOpenEditor.click()
            assertTrue(
                "A fresh editor instance did not open after the 393x873 viewport was applied",
                device.wait(Until.hasObject(By.desc("Düzenlenmiş video önizlemesi")), 15_000)
            )
            device.waitForIdle()

            val largeClipTab = device.findObject(By.desc("Klip araçları sekmesi"))
                ?: throw AssertionError("Clip tab disappeared at the taller phone size")
            val largeDensity = instrumentation.targetContext.resources.displayMetrics.density
            largeClipTab.click()
            device.waitForIdle()
            SystemClock.sleep(300)

            val tallEmptyAddVideo = device.findObject(By.desc("Klip düzenlemek için video ekle"))
                ?: throw AssertionError("Tall-phone empty editor lost its primary add-video action")
            assertTrue(
                "Tall-phone empty-editor action is clipped or too small: \${tallEmptyAddVideo.visibleBounds}",
                tallEmptyAddVideo.visibleBounds.height() >= (44f * largeDensity).toInt() &&
                    tallEmptyAddVideo.visibleBounds.width() >= (120f * largeDensity).toInt()
            )
            assertTrue(
                "Tall-phone empty-editor action must remain fully above the bottom dock: " +
                    "action=\${tallEmptyAddVideo.visibleBounds} dock=\${largeClipTab.visibleBounds}",
                tallEmptyAddVideo.visibleBounds.bottom <= largeClipTab.visibleBounds.top
            )

            val largePreviewStage = device.findObject(By.desc("Video önizleme alanı"))
                ?: throw AssertionError("Outer preview stage is not exposed for responsive-layout checks")
            assertTrue(
                "Taller-phone preview stage is too short: ${largePreviewStage.visibleBounds}",
                largePreviewStage.visibleBounds.height() >= (320f * largeDensity).toInt()
            )
            val largePreview = device.findObject(By.desc("Düzenlenmiş video önizlemesi"))
                ?: throw AssertionError("Preview canvas disappeared at the taller phone size")
            val largePlayback = device.findObject(By.desc("Önizlemeyi oynat veya duraklat"))
                ?: throw AssertionError("Playback target disappeared at the taller phone size")
            val largeRuler = device.findObject(By.desc("Zaman çizelgesi etiketi: SÜRE"))
                ?: throw AssertionError("Shared ruler disappeared at the taller phone size")
            val largeTimeDisplay = device.findObject(By.desc("Oynatma zamanı ve toplam video süresi"))
                ?: throw AssertionError("Time display disappeared at the taller phone size")

            assertTrue(
                "Taller-phone video canvas is not sufficiently visible: ${largePreview.visibleBounds}",
                // This accessibility node is PlayerView, whose bounds follow the source 9:16
                // video aspect ratio inside a taller preview stage; measure the visible canvas,
                // not the full surrounding stage container.
                largePreview.visibleBounds.height() >= (100f * largeDensity).toInt()
            )
            assertTrue(
                "Taller-phone playback target is too small: ${largePlayback.visibleBounds}",
                largePlayback.visibleBounds.height() >= (36f * largeDensity).toInt()
            )
            assertTrue(
                "Taller-phone clip tab is below the 44dp touch-target minimum: ${largeClipTab.visibleBounds}",
                largeClipTab.visibleBounds.height() >= (44f * largeDensity).toInt()
            )
            assertTrue(
                "Taller-phone timeline gutter is no longer compact: ${largeRuler.visibleBounds}",
                largeRuler.visibleBounds.width() <= (28f * largeDensity).toInt()
            )
            assertTrue(
                "Taller-phone time display wrapped: ${largeTimeDisplay.text}",
                '\n' !in largeTimeDisplay.text &&
                    largeTimeDisplay.visibleBounds.height() <= (24f * largeDensity).toInt()
            )
            takeScreenshot(device, "editor-phone-size.png")
            publishScreenshotToDownloads("editor-phone-size.png")

            // Repeat the tall-phone layout check with a real decoded test clip loaded.
            // Package the deterministic video into the instrumentation APK assets and
            // copy it into the target app's private files directory. A focused screenshot
            // run must not depend on the full-suite ADB fixture-push script.
            val loadedFixture = File(instrumentation.targetContext.filesDir, "editor-test-video.mp4")
            instrumentation.context.assets.open("editor-test-video.mp4").use { input ->
                loadedFixture.outputStream().use { output -> input.copyTo(output) }
            }
            assertTrue(
                "Focused screenshot test fixture was not copied into the target app",
                loadedFixture.isFile && loadedFixture.length() > 0L
            )

            // The earlier screenshot above is intentionally the empty-editor state;
            // this second capture closes the gap between empty and populated layouts.
            device.executeShellCommand("logcat -c")
            device.executeShellCommand(
                "am start -n com.mathmotor.video.next/com.mathmotor.video.EditorTestEntryActivity " +
                    "--es video_uri file:///data/user/0/com.mathmotor.video.next/files/editor-test-video.mp4"
            )
            assertTrue(
                "Loaded-video editor did not expose its VIDEO timeline lane at 393x873",
                device.wait(Until.hasObject(By.desc("Zaman çizelgesi etiketi: VIDEO")), 20_000)
            )
            var loadedClipConfirmed = false
            val clipDeadline = SystemClock.uptimeMillis() + 25_000L
            while (SystemClock.uptimeMillis() < clipDeadline) {
                val markerLog = device.executeShellCommand("logcat -d -s EditorSmokeTest:I")
                if (markerLog.contains("EDITOR_SMOKE_CLIP_ADDED")) {
                    loadedClipConfirmed = true
                    break
                }
                if (markerLog.contains("EDITOR_SMOKE_CLIP_REJECTED")) break
                SystemClock.sleep(400)
            }
            assertTrue(
                "The deterministic sample video was not confirmed as added to the timeline",
                loadedClipConfirmed
            )
            assertTrue(
                "A single clip should use the main VIDEO filmstrip, not a duplicate Klip 1 chooser row",
                !device.hasObject(By.desc("Klip seçici satırı"))
            )
            device.waitForIdle()
            SystemClock.sleep(500)

            val loadedPreviewStage = device.findObject(By.desc("Video önizleme alanı"))
                ?: throw AssertionError("Loaded-video preview stage is missing at 393x873")
            val loadedPreview = device.findObject(By.desc("Düzenlenmiş video önizlemesi"))
                ?: throw AssertionError("Loaded-video preview canvas is missing at 393x873")
            val loadedPlayback = device.findObject(By.desc("Önizlemeyi oynat veya duraklat"))
                ?: throw AssertionError("Playback control is missing with a video loaded")
            val loadedRuler = device.findObject(By.desc("Zaman çizelgesi etiketi: SÜRE"))
                ?: throw AssertionError("Shared ruler is missing with a video loaded")
            val loadedVideoLane = device.findObject(By.desc("Zaman çizelgesi etiketi: VIDEO"))
                ?: throw AssertionError("VIDEO lane is missing with a video loaded")
            val loadedTime = device.findObject(By.desc("Oynatma zamanı ve toplam video süresi"))
                ?: throw AssertionError("Current/total time is missing with a video loaded")

            assertTrue(
                "Loaded-video preview stage is too short: ${loadedPreviewStage.visibleBounds}",
                loadedPreviewStage.visibleBounds.height() >= (320f * largeDensity).toInt()
            )
            assertTrue(
                "Loaded-video canvas is too small: ${loadedPreview.visibleBounds}",
                loadedPreview.visibleBounds.height() >= (100f * largeDensity).toInt()
            )
            assertTrue(
                "Playback control is not below the loaded-video preview",
                loadedPlayback.visibleBounds.top >= loadedPreviewStage.visibleBounds.bottom
            )
            assertTrue(
                "Timeline ruler is not below playback controls",
                loadedRuler.visibleBounds.top >= loadedPlayback.visibleBounds.bottom
            )
            assertTrue(
                "VIDEO lane is clipped or hidden: ${loadedVideoLane.visibleBounds}",
                loadedVideoLane.visibleBounds.height() >= (18f * largeDensity).toInt()
            )
            assertTrue(
                "Loaded-video time readout wrapped or duplicated: ${loadedTime.text}",
                '\n' !in loadedTime.text && !loadedTime.text.contains("Proje") &&
                    !loadedTime.text.contains("Klip") &&
                    loadedTime.visibleBounds.height() <= (24f * largeDensity).toInt()
            )
            takeScreenshot(device, "editor-phone-size-loaded.png")
            publishScreenshotToDownloads("editor-phone-size-loaded.png")

            // Exercise the actual ruler labels at both zoom clamps. The ruler is built
            // from the same adaptive interval math as the timeline; verify that visible
            // labels remain present, spaced and carry the expected time interval.
            fun currentZoomPercent(): Int {
                val zoom = device.findObject(By.descContains("Zaman çizelgesi yakınlaştırma"))
                    ?: throw AssertionError("Timeline zoom indicator disappeared during ruler test")
                return Regex("(\\d+)%").find(zoom.contentDescription?.toString().orEmpty())
                    ?.groupValues?.get(1)?.toInt()
                    ?: throw AssertionError("Could not parse timeline zoom: ${zoom.contentDescription}")
            }

            fun zoomToClamp(targetPercent: Int) {
                var actual = currentZoomPercent()
                for (attempt in 0 until 12) {
                    if (actual == targetPercent) break
                    val videoViewport = device.findObject(By.desc("Zaman çizelgesi satırı: VIDEO"))
                        ?: throw AssertionError("VIDEO lane disappeared during zoom endpoint test")
                    if (actual < targetPercent) videoViewport.pinchOpen(0.80f, 50)
                    else videoViewport.pinchClose(0.80f, 50)
                    device.waitForIdle()
                    SystemClock.sleep(250)
                    actual = currentZoomPercent()
                }
                assertTrue(
                    "Timeline zoom did not reach requested clamp $targetPercent%; actual=$actual%",
                    actual == targetPercent
                )
            }

            fun assertVisibleRulerTicks(expectedIntervalMs: Long, minimumCount: Int, stage: String) {
                val visibleTicks = device.findObjects(By.descContains("Zaman çizelgesi işareti:"))
                    .filter { it.visibleBounds.width() > 0 && it.visibleBounds.height() > 0 }
                assertTrue(
                    "$stage ruler exposed too few visible labels: ${visibleTicks.map { it.contentDescription }}",
                    visibleTicks.size >= minimumCount
                )
                val intervals = visibleTicks.mapNotNull { tick ->
                    Regex("aralık (\\d+) ms").find(tick.contentDescription?.toString().orEmpty())
                        ?.groupValues?.get(1)?.toLong()
                }
                assertTrue(
                    "$stage ruler labels did not expose interval metadata: $intervals",
                    intervals.size >= minimumCount && intervals.all { it == expectedIntervalMs }
                )
                val minimumGapPx = (32f * largeDensity).toInt()
                val centers = visibleTicks.map { it.visibleBounds.centerX() }.sorted()
                for (index in 1 until centers.size) {
                    assertTrue(
                        "$stage ruler labels are too tightly packed: centers=$centers minimumGap=$minimumGapPx",
                        centers[index] - centers[index - 1] >= minimumGapPx
                    )
                }
            }

            zoomToClamp(50)
            assertVisibleRulerTicks(expectedIntervalMs = 5_000L, minimumCount = 2, stage = "minimum zoom")
            zoomToClamp(800)
            assertVisibleRulerTicks(expectedIntervalMs = 250L, minimumCount = 4, stage = "maximum zoom")
        } finally {
            // The emulator is reused by the rest of the instrumentation suite.
            device.executeShellCommand("wm size reset")
            SystemClock.sleep(500)
            device.waitForIdle()
        }
    }

    private fun publishScreenshotToDownloads(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // Screenshot files are written directly into the target app's cache root;
        // the emulator test APK's cache subdirectory is not reliably creatable on API 35.
        val appContext = instrumentation.targetContext
        val source = File(appContext.cacheDir, name)
        assertTrue("Cannot publish missing screenshot: ${source.absolutePath}", source.isFile)

        val resolver = appContext.contentResolver
        val displayName = "mathmotor-$name"
        val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/MathMotorTestScreenshots"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw AssertionError("Could not create shared MediaStore screenshot")
        try {
            resolver.openOutputStream(uri)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: throw AssertionError("Could not open shared screenshot output stream")
            val completed = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            resolver.update(uri, completed, null, null)
        } catch (failure: Throwable) {
            resolver.delete(uri, null, null)
            throw failure
        }
    }

    private fun takeScreenshot(device: UiDevice, name: String) {
        // Use the target app's existing cache root directly. On the hosted API 35
        // emulator, creating an extra subdirectory under the instrumentation APK cache
        // failed before the screenshot could be captured.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val destination = File(appContext.cacheDir, name)
        val saved = device.takeScreenshot(destination)
        assertTrue("Screenshot was not saved: ${destination.absolutePath}", saved)
    }
}

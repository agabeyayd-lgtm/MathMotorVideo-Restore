package com.mathmotor.video

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class VideoEditorEndToEndTest {
    @Test
    fun mp4LoadsSplitsAndExportsPlayableMp4() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = File(context.cacheDir, "editor-e2e-source.mp4")
        createTestVideo(source)

        val intent = Intent(context, VideoEditorActivity::class.java).apply {
            putExtra("video_uri", Uri.fromFile(source).toString())
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val scenario = ActivityScenario.launch<VideoEditorActivity>(intent)
        try {
            awaitUi(scenario, 15_000L, "the source video is added") { activity ->
                allText(activity).any { it.contains("eklendi", ignoreCase = true) }
            }

            awaitUi(scenario, 10_000L, "the source video reaches a decoded playback state") { activity ->
                val playerView = find(activity.findViewById(android.R.id.content), PlayerView::class.java)
                val player = playerView?.player ?: return@awaitUi false
                player.videoSize.width > 0 &&
                    (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_ENDED)
            }

            scenario.onActivity { activity ->
                val root = activity.findViewById<View>(android.R.id.content)
                val playerView = find(root, PlayerView::class.java)
                assertNotNull("Editor should contain a Media3 preview player", playerView)
                playerView?.player?.pause()
                playerView?.player?.seekTo(800L)
            }
            SystemClock.sleep(350L)

            scenario.onActivity { activity ->
                val root = activity.findViewById<View>(android.R.id.content)
                val splitButton = findText(root, "✂ BÖL • Oynatma noktasında")
                assertNotNull("The split-at-playhead control should exist", splitButton)
                splitButton?.performClick()
            }
            awaitUi(scenario, 5_000L, "the clip is split at the playhead") { activity ->
                allText(activity).any { it.contains("iki parçaya bölündü", ignoreCase = true) }
            }

            scenario.onActivity { activity ->
                val root = activity.findViewById<View>(android.R.id.content)
                val saveTab = findText(root, "Kaydet")
                assertNotNull("The export tab should exist", saveTab)
                saveTab?.performClick()
                val exportButton = findText(root, "MP4 OLARAK DIŞA AKTAR")
                assertNotNull("The MP4 export control should exist", exportButton)
                exportButton?.performClick()
            }

            awaitUi(scenario, 60_000L, "MP4 export to finish") { activity ->
                allText(activity).any {
                    it.contains("BAŞARILI • Düzenlenmiş MP4 kaydedildi", ignoreCase = true) ||
                        it.contains("DIŞA AKTARMA BAŞARISIZ", ignoreCase = true) ||
                        it.contains("Dışa aktarıcı başlatılamadı", ignoreCase = true) ||
                        it.contains("Video işlendi ancak kaydedilemedi", ignoreCase = true) ||
                        it.contains("Düzenleme hazırlanamadı", ignoreCase = true)
                }
            }

            var statusText = ""
            scenario.onActivity { activity ->
                statusText = allText(activity).firstOrNull {
                    it.contains("Düzenlenmiş MP4 kaydedildi", ignoreCase = true) ||
                        it.contains("DIŞA AKTARMA BAŞARISIZ", ignoreCase = true) ||
                        it.contains("Dışa aktarıcı başlatılamadı", ignoreCase = true) ||
                        it.contains("Video işlendi ancak kaydedilemedi", ignoreCase = true) ||
                        it.contains("Düzenleme hazırlanamadı", ignoreCase = true)
                }.orEmpty()
            }
            assertTrue("MP4 export did not succeed. Editor status: $statusText",
                statusText.contains("Düzenlenmiş MP4 kaydedildi", ignoreCase = true))

            val exportedUri = Regex("content://[^\\s]+").find(statusText)?.value
                ?: throw AssertionError("Successful export did not report a MediaStore URI: $statusText")
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, Uri.parse(exportedUri))
                val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
                val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    ?.toIntOrNull() ?: 0
                val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    ?.toIntOrNull() ?: 0
                assertTrue("Exported MP4 should have a readable, positive duration", duration > 0L)
                assertTrue("Exported MP4 should have valid video dimensions", width > 0 && height > 0)
            } finally {
                retriever.release()
            }
        } finally {
            scenario.close()
            source.delete()
        }
    }

    @Test
    fun videoPlayerRendersFirstFrameFromLocalMp4() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = File(context.cacheDir, "mathmotor-player-e2e-source.mp4")
        createTestVideo(source)

        val intent = Intent(context, VideoPlayerActivity::class.java).apply {
            putExtra("video_uri", Uri.fromFile(source).toString())
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val scenario = ActivityScenario.launch<VideoPlayerActivity>(intent)
        try {
            val deadline = SystemClock.elapsedRealtime() + 15_000L
            var rendered = false
            var uiState = ""
            while (!rendered && SystemClock.elapsedRealtime() < deadline) {
                scenario.onActivity { activity ->
                    val texts = allText(activity)
                    uiState = texts.joinToString(" | ")
                    rendered = texts.any { it.startsWith("Görüntü oynatılıyor:") }
                }
                if (uiState.contains("Video oynatılamadı:", ignoreCase = true)) break
                SystemClock.sleep(100L)
            }
            assertTrue(
                "Video player never reported a rendered first frame. UI: $uiState",
                rendered
            )
        } finally {
            scenario.close()
            source.delete()
        }
    }

    private fun createTestVideo(output: File) {
        val width = 96
        val height = 160
        val frameRate = 10
        val frameCount = 20
        val mime = MediaFormat.MIMETYPE_VIDEO_AVC
        val codecInfo = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.firstOrNull { info ->
            info.isEncoder && info.supportedTypes.any { it.equals(mime, ignoreCase = true) }
        } ?: throw AssertionError("No AVC encoder is available in the Android test emulator.")
        val supportedFormats = codecInfo.getCapabilitiesForType(mime).colorFormats
        val colorFormat = listOf(
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
        ).firstOrNull { it in supportedFormats }
            ?: throw AssertionError("The emulator AVC encoder has no supported YUV 4:2:0 input format.")

        output.parentFile?.mkdirs()
        output.delete()
        val encoder = MediaCodec.createByCodecName(codecInfo.name)
        val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var encoderStarted = false
        var muxerStarted = false
        val bufferInfo = MediaCodec.BufferInfo()
        try {
            val format = MediaFormat.createVideoFormat(mime, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)
                setInteger(MediaFormat.KEY_BIT_RATE, 160_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()
            encoderStarted = true

            var frameIndex = 0
            var inputEosQueued = false
            var outputEosReached = false
            var trackIndex = -1
            val deadline = SystemClock.elapsedRealtime() + 20_000L
            while (!outputEosReached && SystemClock.elapsedRealtime() < deadline) {
                if (frameIndex < frameCount) {
                    val inputIndex = encoder.dequeueInputBuffer(10_000L)
                    if (inputIndex >= 0) {
                        val inputSize = fillInputFrame(encoder, inputIndex, width, height, colorFormat)
                        val presentationTimeUs = frameIndex * 1_000_000L / frameRate
                        encoder.queueInputBuffer(inputIndex, 0, inputSize, presentationTimeUs, 0)
                        frameIndex++
                    }
                } else if (!inputEosQueued) {
                    val inputIndex = encoder.dequeueInputBuffer(10_000L)
                    if (inputIndex >= 0) {
                        val endTimeUs = frameCount * 1_000_000L / frameRate
                        encoder.queueInputBuffer(
                            inputIndex, 0, 0, endTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        inputEosQueued = true
                    }
                }

                val outputIndex = encoder.dequeueOutputBuffer(bufferInfo, 10_000L)
                when {
                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        trackIndex = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    outputIndex >= 0 -> {
                        val encoded = encoder.getOutputBuffer(outputIndex)
                        if (
                            bufferInfo.size > 0 && encoded != null && muxerStarted &&
                            bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0
                        ) {
                            encoded.position(bufferInfo.offset)
                            encoded.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(trackIndex, encoded, bufferInfo)
                        }
                        outputEosReached =
                            bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        encoder.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }
            check(outputEosReached) { "Timed out while creating the MP4 source video." }
            check(muxerStarted && output.length() > 0L) { "The source MP4 was not written." }
        } finally {
            if (encoderStarted) runCatching { encoder.stop() }
            runCatching { encoder.release() }
            if (muxerStarted) runCatching { muxer.stop() }
            runCatching { muxer.release() }
        }
    }

    private fun fillInputFrame(
        encoder: MediaCodec,
        inputIndex: Int,
        width: Int,
        height: Int,
        colorFormat: Int
    ): Int {
        val ySize = width * height
        val frameSize = ySize * 3 / 2
        if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible) {
            val image = encoder.getInputImage(inputIndex)
                ?: throw AssertionError("The AVC encoder did not expose a flexible YUV input image.")
            try {
                fillPlane(image.planes[0], width, height, 96.toByte())
                fillPlane(image.planes[1], width / 2, height / 2, 128.toByte())
                fillPlane(image.planes[2], width / 2, height / 2, 128.toByte())
            } finally {
                image.close()
            }
            return frameSize
        }

        val input = encoder.getInputBuffer(inputIndex)
            ?: throw AssertionError("The AVC encoder returned no input buffer.")
        input.clear()
        check(input.capacity() >= frameSize) { "The AVC encoder input buffer is too small." }
        for (index in 0 until ySize) input.put(index, 96.toByte())
        if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) {
            for (index in ySize until frameSize) input.put(index, 128.toByte())
        } else {
            for (index in ySize until frameSize) input.put(index, 128.toByte())
        }
        return frameSize
    }

    private fun fillPlane(
        plane: android.media.Image.Plane,
        width: Int,
        height: Int,
        value: Byte
    ) {
        val buffer = plane.buffer
        val start = buffer.position()
        for (row in 0 until height) {
            for (column in 0 until width) {
                buffer.put(start + row * plane.rowStride + column * plane.pixelStride, value)
            }
        }
    }

    private fun awaitUi(
        scenario: ActivityScenario<VideoEditorActivity>,
        timeoutMs: Long,
        description: String,
        predicate: (VideoEditorActivity) -> Boolean
    ) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        var matched = false
        while (SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity { activity -> matched = predicate(activity) }
            if (matched) return
            SystemClock.sleep(100L)
        }
        var state = ""
        scenario.onActivity { activity -> state = allText(activity).joinToString(" | ") }
        assertTrue("Timed out waiting for $description. Editor UI: $state", matched)
    }

    private fun allText(activity: Activity): List<String> =
        allTextFrom(activity.findViewById(android.R.id.content))

    private fun allTextFrom(view: View): List<String> {
        val values = mutableListOf<String>()
        if (view is TextView) values += view.text?.toString().orEmpty()
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) values += allTextFrom(view.getChildAt(index))
        }
        return values
    }

    private fun findText(view: View, expected: String): TextView? {
        if (view is TextView && view.text?.toString() == expected) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                val found = findText(view.getChildAt(index), expected)
                if (found != null) return found
            }
        }
        return null
    }

    private fun <T : View> find(view: View, type: Class<T>): T? {
        if (type.isInstance(view)) return type.cast(view)
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                val found = find(view.getChildAt(index), type)
                if (found != null) return found
            }
        }
        return null
    }

}

package com.mathmotor.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.SystemClock
import java.io.IOException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A decoder session retained for one source on one decoder thread.
 *
 * Filmstrip targets are submitted in timestamp order. Intermediate decoder
 * outputs are released immediately without allocating Bitmaps; only selected
 * frames are converted to thumbnails.
 */
internal class SequentialFilmstripFrameDecoder(
    context: Context,
    private val sourceUri: Uri,
    firstTargetTimeMs: Long
) : AutoCloseable {

    internal data class DecodedFrame(
        val presentationTimeMs: Long,
        val bitmap: Bitmap
    )

    private val appContext = context.applicationContext
    private var extractor: MediaExtractor? = null
    private var codec: MediaCodec? = null
    private var codecName: String = "unknown"
    private var rotationDegrees: Int = 0
    private var colorStandard: Int = MediaFormat.COLOR_STANDARD_BT601_NTSC
    private var colorRange: Int = MediaFormat.COLOR_RANGE_LIMITED
    private var inputEos = false
    private var outputEos = false
    private var lastOutputTimeUs = -1L

    @Volatile
    private var invalidated = false

    val isInvalidated: Boolean
        get() = invalidated

    val decoderName: String
        get() = codecName

    init {
        open(firstTargetTimeMs.coerceAtLeast(0L) * 1_000L)
    }

    @Synchronized
    @Throws(IOException::class, InterruptedException::class)
    fun decodeAtOrAfter(
        targetTimeMs: Long,
        maxWidthPx: Int,
        maxHeightPx: Int
    ): DecodedFrame? {
        check(!invalidated) { "Sequential decoder invalidated for " + sourceUri }
        check(!Thread.currentThread().isInterrupted) { "Sequential decode interrupted" }

        val targetTimeUs = targetTimeMs.coerceAtLeast(0L) * 1_000L
        if (lastOutputTimeUs >= 0L && targetTimeUs < lastOutputTimeUs) {
            seekAndFlush(targetTimeUs)
        }

        val activeCodec = codec ?: return null
        val activeExtractor = extractor ?: return null
        val info = MediaCodec.BufferInfo()
        val deadline = SystemClock.elapsedRealtime() + MAX_DECODE_WAIT_MS

        while (!outputEos && SystemClock.elapsedRealtime() < deadline) {
            if (Thread.currentThread().isInterrupted || invalidated) {
                throw InterruptedException("Sequential decode cancelled for " + sourceUri)
            }

            if (!inputEos) {
                val inputIndex = activeCodec.dequeueInputBuffer(CODEC_TIMEOUT_US)
                if (inputIndex >= 0) {
                    val inputBuffer = activeCodec.getInputBuffer(inputIndex)
                        ?: throw IOException("MediaCodec returned a null input buffer")
                    inputBuffer.clear()
                    val sampleSize = activeExtractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        activeCodec.queueInputBuffer(
                            inputIndex,
                            0,
                            0,
                            lastOutputTimeUs.coerceAtLeast(0L),
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        inputEos = true
                    } else {
                        val sampleTimeUs = activeExtractor.sampleTime
                        activeCodec.queueInputBuffer(
                            inputIndex,
                            0,
                            sampleSize,
                            sampleTimeUs.coerceAtLeast(0L),
                            0
                        )
                        activeExtractor.advance()
                    }
                }
            }

            val outputIndex = activeCodec.dequeueOutputBuffer(info, CODEC_TIMEOUT_US)
            when {
                outputIndex >= 0 -> {
                    val hasFrame = info.size > 0 &&
                        (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 &&
                        info.presentationTimeUs >= 0L
                    val frameTimeUs = info.presentationTimeUs

                    if (hasFrame && frameTimeUs >= targetTimeUs) {
                        var outputImage: Image? = null
                        val bitmap = try {
                            outputImage = activeCodec.getOutputImage(outputIndex)
                                ?: throw IOException(
                                    "Decoder " + codecName + " did not expose a YUV output image"
                                )
                            imageToThumbnail(
                                outputImage,
                                maxWidthPx.coerceAtLeast(1),
                                maxHeightPx.coerceAtLeast(1)
                            )
                        } finally {
                            outputImage?.close()
                            activeCodec.releaseOutputBuffer(outputIndex, false)
                        }
                        lastOutputTimeUs = frameTimeUs
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputEos = true
                        }
                        return DecodedFrame(frameTimeUs / 1_000L, bitmap)
                    } else {
                        // This is the key optimization: discarded inter-frames
                        // never become Android Bitmaps or enter the app caches.
                        activeCodec.releaseOutputBuffer(outputIndex, false)
                    }

                    if (hasFrame) lastOutputTimeUs = frameTimeUs
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputEos = true
                    }
                }

                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER ||
                    outputIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit
            }
        }

        if (outputEos) return null
        throw IOException("Timed out decoding " + sourceUri + " at " + targetTimeMs + "ms")
    }

    fun markInvalidated() {
        invalidated = true
    }

    @Synchronized
    private fun seekAndFlush(targetTimeUs: Long) {
        val activeExtractor = extractor ?: throw IOException("Missing MediaExtractor")
        val activeCodec = codec ?: throw IOException("Missing MediaCodec")
        activeExtractor.seekTo(targetTimeUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        activeCodec.flush()
        inputEos = false
        outputEos = false
        lastOutputTimeUs = -1L
    }

    private fun open(firstTargetTimeUs: Long) {
        val newExtractor = MediaExtractor()
        var newCodec: MediaCodec? = null
        try {
            newExtractor.setDataSource(appContext, sourceUri, null)
            var selectedTrack = -1
            var selectedFormat: MediaFormat? = null
            for (index in 0 until newExtractor.trackCount) {
                val format = newExtractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("video/")) {
                    selectedTrack = index
                    selectedFormat = format
                    break
                }
            }

            val format = selectedFormat ?: throw IOException("No video track in " + sourceUri)
            val mime = format.getString(MediaFormat.KEY_MIME)
                ?: throw IOException("Video track has no MIME type in " + sourceUri)
            val decoderInfo = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.firstOrNull { info ->
                !info.isEncoder &&
                    info.supportedTypes.any { it.equals(mime, ignoreCase = true) } &&
                    runCatching {
                        info.getCapabilitiesForType(mime).colorFormats.contains(
                            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
                        )
                    }.getOrDefault(false)
            } ?: throw IOException("No flexible-YUV decoder available for " + mime)

            newExtractor.selectTrack(selectedTrack)
            newExtractor.seekTo(firstTargetTimeUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            format.setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
            )

            rotationDegrees = runCatching {
                format.getInteger(MediaFormat.KEY_ROTATION)
            }.getOrDefault(0).let { ((it % 360) + 360) % 360 }
            colorStandard = runCatching {
                format.getInteger(MediaFormat.KEY_COLOR_STANDARD)
            }.getOrDefault(MediaFormat.COLOR_STANDARD_BT601_NTSC)
            colorRange = runCatching {
                format.getInteger(MediaFormat.KEY_COLOR_RANGE)
            }.getOrDefault(MediaFormat.COLOR_RANGE_LIMITED)

            newCodec = MediaCodec.createByCodecName(decoderInfo.name)
            newCodec.configure(format, null, null, 0)
            newCodec.start()

            extractor = newExtractor
            codec = newCodec
            codecName = decoderInfo.name
        } catch (error: Exception) {
            runCatching { newCodec?.stop() }
            runCatching { newCodec?.release() }
            runCatching { newExtractor.release() }
            throw error
        }
    }

    private fun imageToThumbnail(
        image: Image,
        maxWidthPx: Int,
        maxHeightPx: Int
    ): Bitmap {
        if (image.format != ImageFormat.YUV_420_888) {
            throw IOException("Unsupported decoder image format " + image.format)
        }

        val crop = image.cropRect
        val sourceWidth = crop.width().coerceAtLeast(1)
        val sourceHeight = crop.height().coerceAtLeast(1)
        val swapsDimensions = rotationDegrees == 90 || rotationDegrees == 270
        val rawMaxWidth = if (swapsDimensions) maxHeightPx else maxWidthPx
        val rawMaxHeight = if (swapsDimensions) maxWidthPx else maxHeightPx
        val scale = min(
            1.0,
            min(
                rawMaxWidth.toDouble() / sourceWidth.toDouble(),
                rawMaxHeight.toDouble() / sourceHeight.toDouble()
            )
        )
        val outputWidth = (sourceWidth * scale).roundToInt().coerceAtLeast(1)
        val outputHeight = (sourceHeight * scale).roundToInt().coerceAtLeast(1)

        val planes = image.planes
        if (planes.size < 3) throw IOException("YUV output has fewer than three planes")
        val yPlane = planes[0]
        val uPlane = planes[1]
        val vPlane = planes[2]
        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer
        val yBase = yBuffer.position()
        val uBase = uBuffer.position()
        val vBase = vBuffer.position()
        val pixels = IntArray(outputWidth * outputHeight)
        val fullRange = colorRange == MediaFormat.COLOR_RANGE_FULL
        val bt709 = colorStandard == MediaFormat.COLOR_STANDARD_BT709

        for (outY in 0 until outputHeight) {
            val sourceY = crop.top +
                ((outY.toDouble() * sourceHeight.toDouble()) / outputHeight.toDouble()).toInt()
                    .coerceIn(0, sourceHeight - 1)
            val yRow = yBase + sourceY * yPlane.rowStride
            val uvRowU = uBase + (sourceY / 2) * uPlane.rowStride
            val uvRowV = vBase + (sourceY / 2) * vPlane.rowStride
            for (outX in 0 until outputWidth) {
                val sourceX = crop.left +
                    ((outX.toDouble() * sourceWidth.toDouble()) / outputWidth.toDouble()).toInt()
                        .coerceIn(0, sourceWidth - 1)
                val yIndex = yRow + sourceX * yPlane.pixelStride
                val uvX = sourceX / 2
                if (yIndex >= yBuffer.limit() ||
                    uvRowU + uvX * uPlane.pixelStride >= uBuffer.limit() ||
                    uvRowV + uvX * vPlane.pixelStride >= vBuffer.limit()
                ) {
                    throw IOException("YUV plane stride exceeded its buffer limit")
                }

                val yValue = yBuffer.get(yIndex).toInt() and 0xff
                val uValue = (uBuffer.get(uvRowU + uvX * uPlane.pixelStride).toInt() and 0xff) - 128
                val vValue = (vBuffer.get(uvRowV + uvX * vPlane.pixelStride).toInt() and 0xff) - 128
                val luma = if (fullRange) yValue.toFloat() else max(0, yValue - 16) * 1.164383f

                val red: Float
                val green: Float
                val blue: Float
                if (fullRange) {
                    if (bt709) {
                        red = luma + 1.5748f * vValue
                        green = luma - 0.1873f * uValue - 0.4681f * vValue
                        blue = luma + 1.8556f * uValue
                    } else {
                        red = luma + 1.4020f * vValue
                        green = luma - 0.3441f * uValue - 0.7141f * vValue
                        blue = luma + 1.7720f * uValue
                    }
                } else {
                    if (bt709) {
                        red = luma + 1.7927f * vValue
                        green = luma - 0.2132f * uValue - 0.5329f * vValue
                        blue = luma + 2.1124f * uValue
                    } else {
                        red = luma + 1.5960f * vValue
                        green = luma - 0.3918f * uValue - 0.8130f * vValue
                        blue = luma + 2.0172f * uValue
                    }
                }

                val r = red.roundToInt().coerceIn(0, 255)
                val g = green.roundToInt().coerceIn(0, 255)
                val b = blue.roundToInt().coerceIn(0, 255)
                pixels[outY * outputWidth + outX] =
                    (0xff shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        var bitmap = Bitmap.createBitmap(
            pixels,
            outputWidth,
            outputHeight,
            Bitmap.Config.ARGB_8888
        )
        if (rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            val rotated = Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.width,
                bitmap.height,
                matrix,
                true
            )
            if (rotated !== bitmap) bitmap.recycle()
            bitmap = rotated
        }
        return bitmap
    }

    @Synchronized
    override fun close() {
        val activeCodec = codec
        codec = null
        extractor?.let { activeExtractor ->
            runCatching { activeExtractor.release() }
        }
        extractor = null
        if (activeCodec != null) {
            runCatching { activeCodec.stop() }
            runCatching { activeCodec.release() }
        }
        inputEos = false
        outputEos = true
        lastOutputTimeUs = -1L
    }

    private companion object {
        const val CODEC_TIMEOUT_US = 10_000L
        const val MAX_DECODE_WAIT_MS = 20_000L
    }
}

package com.mathmotor.video

import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.os.Environment
import android.provider.MediaStore
import android.view.Surface
import java.io.File
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/** Local 9:16 H.264 renderer. It does not need a cloud video API. */
object HumanVideoRenderer {
    private const val WIDTH = 540
    private const val HEIGHT = 960
    private const val FPS = 30
    private const val FRAME_COUNT = 300
    private const val BITRATE = 4_000_000

    fun render(context: Context, scenario: HumanScenario, onDone: (String?, Throwable?) -> Unit) {
        Thread {
            var codec: MediaCodec? = null
            var muxer: MediaMuxer? = null
            var inputSurface: Surface? = null
            val temp = File(context.cacheDir, "mm_${scenario.seed}_${System.currentTimeMillis()}.mp4")
            var outputUri: Uri? = null
            try {
                val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, WIDTH, HEIGHT).apply {
                    setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                    setInteger(MediaFormat.KEY_BIT_RATE, BITRATE)
                    setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                }
                codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
                codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                inputSurface = codec.createInputSurface()
                codec.start()
                muxer = MediaMuxer(temp.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                val state = DrainState()
                val info = MediaCodec.BufferInfo()

                for (i in 0 until FRAME_COUNT) {
                    drawFrame(inputSurface, scenario, i)
                    Thread.sleep(1000L / FPS)
                    drain(codec, muxer, info, state, waitForEnd = false)
                }
                inputSurface.release()
                inputSurface = null
                codec.signalEndOfInputStream()
                val eosDeadline = SystemClock.elapsedRealtime() + 15_000L
                while (!state.eos && SystemClock.elapsedRealtime() < eosDeadline) {
                    drain(codec, muxer, info, state, waitForEnd = true)
                }
                check(state.eos) { "H.264 encoder timed out while finalizing the MP4." }
                if (!state.started) error("Encoder did not produce a video track")
                muxer.stop()
                muxer.release(); muxer = null
                codec.stop(); codec.release(); codec = null
                outputUri = createMediaStoreOutput(context, temp, scenario.seed)
                temp.delete()
                onDone(outputUri.toString(), null)
            } catch (t: Throwable) {
                runCatching { inputSurface?.release() }
                runCatching { muxer?.release() }
                runCatching { codec?.stop() }
                runCatching { codec?.release() }
                runCatching { temp.delete() }
                runCatching { outputUri?.let { context.contentResolver.delete(it, null, null) } }
                onDone(null, t)
            }
        }.start()
    }

    private data class DrainState(var trackIndex: Int = -1, var started: Boolean = false, var eos: Boolean = false)

    private fun drain(codec: MediaCodec, muxer: MediaMuxer, info: MediaCodec.BufferInfo, state: DrainState, waitForEnd: Boolean) {
        val timeout = if (waitForEnd) 1_000L else 0L
        while (true) {
            when (val index = codec.dequeueOutputBuffer(info, timeout)) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> return
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    check(!state.started) { "Encoder format changed more than once" }
                    state.trackIndex = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    state.started = true
                }
                else -> if (index >= 0) {
                    val data = codec.getOutputBuffer(index)
                    if (data != null && info.size > 0 && state.started) {
                        data.position(info.offset)
                        data.limit(info.offset + info.size)
                        muxer.writeSampleData(state.trackIndex, data, info)
                    }
                    state.eos = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    codec.releaseOutputBuffer(index, false)
                    if (state.eos) return
                }
            }
            if (!waitForEnd) continue
        }
    }

    private fun createMediaStoreOutput(context: Context, source: File, seed: Long): Uri {
        val name = String.format(Locale.US, "MathMotor_%d_%d.mp4", seed, System.currentTimeMillis())
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/MathMotorVideo")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("Could not create output video in MediaStore")
            try {
                context.contentResolver.openOutputStream(uri)?.use { out -> source.inputStream().use { it.copyTo(out) } }
                    ?: error("Could not open output stream")
                val publishedRows = context.contentResolver.update(
                    uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null
                )
                check(publishedRows > 0) { "Could not publish the generated video in MediaStore." }
                return uri
            } catch (t: Throwable) {
                context.contentResolver.delete(uri, null, null)
                throw t
            }
        }
        val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "MathMotorVideo")
        if (!folder.exists() && !folder.mkdirs()) error("Could not create Movies/MathMotorVideo")
        val target = File(folder, name)
        source.copyTo(target, overwrite = false)
        return Uri.fromFile(target)
    }

    private fun drawFrame(surface: Surface, scenario: HumanScenario, index: Int) {
        val frame = scenario.world.frames[index.coerceIn(0, scenario.world.frames.lastIndex)]
        val progress = frame.t
        val canvas = surface.lockCanvas(null)
        try {
            canvas.drawColor(Color.rgb(239, 230, 213))
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = Color.rgb(250, 246, 237); canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT * 0.68f, p)
            p.color = Color.rgb(192, 160, 119); canvas.drawRect(0f, HEIGHT * 0.72f, WIDTH.toFloat(), HEIGHT.toFloat(), p)
            p.color = Color.rgb(150, 115, 83); p.strokeWidth = 5f; canvas.drawLine(0f, HEIGHT * 0.72f, WIDTH.toFloat(), HEIGHT * 0.72f, p)
            p.color = Color.rgb(130, 91, 58); canvas.drawRoundRect(RectF(55f, 575f, 485f, 620f), 8f, 8f, p)
            p.color = Color.rgb(95, 64, 43); canvas.drawRect(75f, 615f, 95f, 780f, p); canvas.drawRect(445f, 615f, 465f, 780f, p)

            val personX = 122f + frame.handX * 52f
            val personY = 510f
            p.color = Color.rgb(39, 61, 78)
            canvas.drawRoundRect(RectF(personX - 28f, personY - 3f, personX + 28f, personY + 125f), 22f, 22f, p)
            p.color = Color.rgb(207, 155, 111); canvas.drawCircle(personX, personY - 39f, 28f, p)
            p.color = Color.rgb(55, 55, 55); canvas.drawArc(RectF(personX - 29f, personY - 67f, personX + 29f, personY - 16f), 180f, 180f, false, p.apply { style = Paint.Style.STROKE; strokeWidth = 8f })
            p.style = Paint.Style.FILL
            p.color = Color.rgb(207, 155, 111); p.strokeWidth = 12f
            canvas.drawLine(personX + 14f, personY + 15f, personX + 72f, personY + 18f + 15f * sin(progress * 6.28f), p)
            canvas.drawLine(personX - 8f, personY + 110f, personX - 20f, personY + 180f, p)
            canvas.drawLine(personX + 8f, personY + 110f, personX + 34f, personY + 180f, p)

            val objX = (frame.objectX * WIDTH).coerceIn(95f, 455f)
            val objY = (frame.objectY * 540f + 150f).coerceIn(225f, 530f)
            canvas.save()
            canvas.rotate(frame.objectAngle, objX, objY)
            drawObject(canvas, p, scenario.kind, objX, objY, progress)
            canvas.restore()

            if (progress > 0.84f) {
                val settle = ((progress - 0.84f) / 0.16f).coerceIn(0f, 1f)
                p.color = Color.argb((18 + 40 * settle).toInt(), 255, 255, 255)
                canvas.drawCircle(objX, objY, 48f + 10f * cos(settle * 3.14159f), p)
                drawObject(canvas, p, scenario.kind, objX, objY, 1f)
            }
        } finally {
            surface.unlockCanvasAndPost(canvas)
        }
    }

    private fun drawObject(canvas: Canvas, p: Paint, kind: SceneKind, x: Float, y: Float, progress: Float) {
        p.style = Paint.Style.FILL
        when (kind) {
            SceneKind.TEA_TRAY -> {
                p.color = Color.rgb(80, 72, 65); canvas.drawRoundRect(RectF(x - 74f, y + 18f, x + 74f, y + 31f), 8f, 8f, p)
                for (i in -1..1) {
                    val gx = x + i * 44f
                    p.color = Color.rgb(224, 224, 209); canvas.drawRoundRect(RectF(gx - 15f, y - 13f, gx + 15f, y + 19f), 5f, 5f, p)
                    p.color = Color.rgb(166, 61, 40); canvas.drawRect(gx - 10f, y - 3f, gx + 10f, y + 13f, p)
                    p.color = Color.rgb(224, 224, 209); canvas.drawCircle(gx, y - 13f, 15f, p)
                }
            }
            SceneKind.BOX_STACK -> {
                for (i in 0..2) {
                    val lift = i * 41f
                    p.color = when (i) { 0 -> Color.rgb(185, 124, 66); 1 -> Color.rgb(202, 145, 83); else -> Color.rgb(169, 110, 57) }
                    canvas.drawRoundRect(RectF(x - 50f + lift * 0.12f, y - 42f - lift, x + 50f - lift * 0.12f, y + 2f - lift), 5f, 5f, p)
                    p.color = Color.rgb(128, 80, 43); canvas.drawLine(x, y - 41f - lift, x, y - lift, p)
                }
            }
            SceneKind.TOOLBOX -> {
                p.color = Color.rgb(57, 83, 105); canvas.drawRoundRect(RectF(x - 69f, y - 20f, x + 69f, y + 36f), 9f, 9f, p)
                p.color = Color.rgb(183, 192, 197); canvas.drawRoundRect(RectF(x - 28f, y - 28f, x + 28f, y - 18f), 4f, 4f, p)
                p.color = Color.rgb(215, 163, 67)
                canvas.drawRoundRect(RectF(x - 40f, y - 52f, x - 29f, y - 20f), 4f, 4f, p)
                canvas.drawRoundRect(RectF(x + 15f, y - 50f, x + 25f, y - 19f), 4f, 4f, p)
            }
            SceneKind.BROOM_BUCKET -> {
                p.color = Color.rgb(70, 126, 155); canvas.drawRoundRect(RectF(x - 43f, y - 6f, x + 43f, y + 48f), 7f, 7f, p)
                p.color = Color.rgb(155, 109, 67); p.strokeWidth = 10f
                canvas.drawLine(x + 8f, y - 8f, x - 14f, y - 112f, p)
                p.color = Color.rgb(220, 187, 123); canvas.drawRoundRect(RectF(x - 32f, y - 121f, x + 6f, y - 107f), 4f, 4f, p)
            }
            SceneKind.SHOP_CART -> {
                p.color = Color.rgb(120, 135, 143); p.strokeWidth = 8f; p.style = Paint.Style.STROKE
                canvas.drawLine(x - 64f, y - 36f, x + 51f, y - 36f, p)
                canvas.drawLine(x - 64f, y - 36f, x - 43f, y + 25f, p)
                canvas.drawLine(x - 43f, y + 25f, x + 42f, y + 25f, p)
                p.style = Paint.Style.FILL
                p.color = Color.rgb(69, 78, 82); canvas.drawCircle(x - 28f, y + 39f, 11f, p); canvas.drawCircle(x + 30f, y + 39f, 11f, p)
                p.color = Color.rgb(212, 164, 88); canvas.drawRoundRect(RectF(x - 40f, y - 28f, x + 30f, y - 4f), 4f, 4f, p)
            }
        }
    }
}

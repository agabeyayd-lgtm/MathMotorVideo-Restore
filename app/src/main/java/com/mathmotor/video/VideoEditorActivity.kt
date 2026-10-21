package com.mathmotor.video

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.graphics.Color
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.TextOverlay
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.ceil

@OptIn(UnstableApi::class)
class VideoEditorActivity : Activity() {
    private data class Clip(
        val uri: Uri,
        val title: String,
        val durationMs: Long,
        var startMs: Long = 0L,
        var endMs: Long = durationMs
    )

    private val clips = mutableListOf<Clip>()
    private var selectedClipIndex = -1
    private var musicUri: Uri? = null
    private var musicTitle: String? = null
    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var clipList: LinearLayout
    private lateinit var selectedClipLabel: TextView
    private lateinit var startLabel: TextView
    private lateinit var endLabel: TextView
    private lateinit var startSeek: SeekBar
    private lateinit var endSeek: SeekBar
    private lateinit var musicLabel: TextView
    private lateinit var textInput: EditText
    private lateinit var keepSourceAudio: CheckBox
    private lateinit var status: TextView
    private lateinit var exportButton: Button
    private var exporting = false

    companion object {
        private const val REQUEST_MAIN_VIDEO = 5101
        private const val REQUEST_ADD_VIDEO = 5102
        private const val REQUEST_AUDIO = 5103
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(15, 31, 45)
        window.navigationBarColor = Color.rgb(15, 31, 45)
        buildUi()
        player = ExoPlayer.Builder(this).build().also { playerView.player = it }
        val incoming = intent.getStringExtra("video_uri")
        if (!incoming.isNullOrBlank()) addClip(Uri.parse(incoming), false)
    }

    private fun buildUi() {
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.rgb(15, 31, 45)) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
            setBackgroundColor(Color.rgb(15, 31, 45))
        }
        scroll.addView(root)
        root.addView(label("VİDEO EDİTÖRÜ", 25f, Color.WHITE, true))
        root.addView(label("Telefonunda düzenle • yapay zekâ gerekmez", 13f, Color.rgb(173, 191, 204), false))
        root.addView(gap(10))

        playerView = PlayerView(this).apply {
            setBackgroundColor(Color.BLACK)
            useController = true
        }
        root.addView(playerView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(230)).apply {
            bottomMargin = dp(10)
        })
        root.addView(actionButton("ANA VİDEO SEÇ") { chooseVideo(false) })
        root.addView(actionButton("KLİP EKLE / SONUNA BİRLEŞTİR") { chooseVideo(true) })
        root.addView(label("ZAMAN ÇİZELGESİ • KLİPLER", 14f, Color.rgb(121, 202, 218), true))
        clipList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(clipList, matchWrap())

        selectedClipLabel = label("Önce bir video seç.", 13f, Color.WHITE, true)
        root.addView(selectedClipLabel)
        root.addView(actionButton("SEÇİLİ KLİBİ ÖNİZLE") { previewSelectedClip() })

        startLabel = label("Başlangıç: 00:00", 13f, Color.rgb(225, 233, 237), true)
        root.addView(startLabel)
        startSeek = SeekBar(this)
        root.addView(startSeek, matchWrap())
        endLabel = label("Bitiş: 00:00", 13f, Color.rgb(225, 233, 237), true)
        root.addView(endLabel)
        endSeek = SeekBar(this)
        root.addView(endSeek, matchWrap())
        installTrimListeners()

        root.addView(gap(6))
        root.addView(label("YAZI KATMANI", 14f, Color.rgb(121, 202, 218), true))
        textInput = EditText(this).apply {
            hint = "Videoda görünecek yazıyı buraya yaz"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(173, 191, 204))
            setSingleLine(false)
            minLines = 1
            maxLines = 3
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(Color.rgb(27, 48, 64))
        }
        root.addView(textInput, matchWrap())
        root.addView(label("Yazı dışa aktarılan videoya uygulanır.", 12f, Color.rgb(173, 191, 204), false))

        root.addView(gap(6))
        root.addView(label("MÜZİK / SES", 14f, Color.rgb(121, 202, 218), true))
        musicLabel = label("Ek ses seçilmedi.", 12f, Color.rgb(225, 233, 237), false)
        root.addView(musicLabel)
        root.addView(actionButton("TELEFONDAN MÜZİK / SES EKLE") { chooseAudio() })
        root.addView(actionButton("EKLENEN SESİ KALDIR") {
            musicUri = null
            musicTitle = null
            musicLabel.text = "Ek ses seçilmedi."
        })
        keepSourceAudio = CheckBox(this).apply {
            text = "Videonun kendi sesini koru"
            isChecked = true
            setTextColor(Color.WHITE)
        }
        root.addView(keepSourceAudio)

        exportButton = actionButton("DÜZENLENMİŞ MP4'Ü KAYDET") { exportEditedVideo() }
        root.addView(exportButton)
        status = label("Hazır. Video seç, başlangıç/bitişi ayarla; istersen klip, yazı ve ses ekle.", 13f, Color.rgb(225, 233, 237), false)
        status.setPadding(dp(12), dp(12), dp(12), dp(12))
        status.setBackgroundColor(Color.rgb(27, 48, 64))
        root.addView(status, matchWrap().apply { topMargin = dp(8) })
        root.addView(actionButton("ANA EKRANA DÖN") { finish() })
        setContentView(scroll)
    }

    private fun addClip(uri: Uri, replaceExisting: Boolean) {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) { }
        status.text = "Video bilgileri okunuyor…"
        Thread {
            val duration = runCatching {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(this, uri)
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                } finally {
                    retriever.release()
                }
            }.getOrDefault(0L)
            val title = queryDisplayName(uri) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Video klibi"
            runOnUiThread {
                if (duration <= 0L) {
                    status.text = "Bu videonun süresi okunamadı. Başka bir video dosyası seç."
                    return@runOnUiThread
                }
                if (replaceExisting) {
                    clips.clear()
                    selectedClipIndex = -1
                }
                clips.add(Clip(uri, title, duration, 0L, duration))
                selectedClipIndex = clips.lastIndex
                player?.setMediaItem(MediaItem.fromUri(uri))
                player?.prepare()
                player?.playWhenReady = true
                refreshClipList()
                loadTrimControls()
                status.text = "${title} eklendi. Kesme aralığını seçebilirsin."
            }
        }.start()
    }

    private fun chooseVideo(append: Boolean) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "video/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        try {
            startActivityForResult(intent, if (append) REQUEST_ADD_VIDEO else REQUEST_MAIN_VIDEO)
        } catch (e: Exception) {
            status.text = "Video seçici açılamadı: ${e.message}"
        }
    }

    private fun chooseAudio() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        try {
            startActivityForResult(intent, REQUEST_AUDIO)
        } catch (e: Exception) {
            status.text = "Ses seçici açılamadı: ${e.message}"
        }
    }

    @Deprecated("Use Activity Result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) { }

        when (requestCode) {
            REQUEST_MAIN_VIDEO, REQUEST_ADD_VIDEO -> {
                status.text = "Video bilgileri okunuyor…"
                Thread {
                    val details = runCatching {
                        val retriever = MediaMetadataRetriever()
                        try {
                            retriever.setDataSource(this, uri)
                            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                            duration
                        } finally {
                            retriever.release()
                        }
                    }.getOrElse { 0L }
                    val title = queryDisplayName(uri) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Video klibi"
                    runOnUiThread {
                        if (details <= 0L) {
                            status.text = "Bu videonun süresi okunamadı. Başka bir video dosyası seç."
                        } else {
                            if (requestCode == REQUEST_MAIN_VIDEO) {
                                clips.clear()
                                selectedClipIndex = -1
                            }
                            val clip = Clip(uri, title, details, 0L, details)
                            clips.add(clip)
                            selectedClipIndex = clips.lastIndex
                            player?.setMediaItem(MediaItem.fromUri(uri))
                            player?.prepare()
                            player?.playWhenReady = true
                            refreshClipList()
                            loadTrimControls()
                            status.text = "${title} eklendi. Kesme aralığını seçebilirsin."
                        }
                    }
                }.start()
            }
            REQUEST_AUDIO -> {
                musicUri = uri
                musicTitle = queryDisplayName(uri) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Ek ses"
                musicLabel.text = "Ek ses: ${musicTitle}"
                status.text = "Ses dosyası eklendi. Dışa aktarmada video sesiyle birlikte mikslenir."
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        var cursor: Cursor? = null
        return try {
            cursor = contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                cursor.getString(cursor.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME))
            } else null
        } catch (_: Exception) {
            null
        } finally {
            cursor?.close()
        }
    }

    private fun refreshClipList() {
        clipList.removeAllViews()
        if (clips.isEmpty()) {
            clipList.addView(label("Henüz klip eklenmedi.", 12f, Color.rgb(173, 191, 204), false))
        } else {
            clips.forEachIndexed { index, clip ->
                val prefix = if (index == selectedClipIndex) "▶ " else ""
                clipList.addView(actionButton("${prefix}${index + 1}. ${clip.title}  •  ${formatMs(clip.endMs - clip.startMs)}") {
                    selectedClipIndex = index
                    refreshClipList()
                    loadTrimControls()
                    player?.setMediaItem(MediaItem.fromUri(clip.uri))
                    player?.prepare()
                    player?.playWhenReady = true
                })
            }
        }
    }

    private fun installTrimListeners() {
        startSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val clip = selectedClip() ?: return
                val maxStart = (clip.durationMs - 250L).coerceAtLeast(0L)
                clip.startMs = (progress * 1000L).coerceAtMost(maxStart)
                if (clip.endMs - clip.startMs < 500L) {
                    clip.endMs = (clip.startMs + 1000L).coerceAtMost(clip.durationMs)
                    endSeek.progress = ceil(clip.endMs / 1000.0).toInt()
                }
                updateTrimLabels()
                refreshClipList()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        endSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val clip = selectedClip() ?: return
                clip.endMs = (progress * 1000L).coerceAtMost(clip.durationMs)
                if (clip.endMs - clip.startMs < 500L) {
                    clip.startMs = (clip.endMs - 1000L).coerceAtLeast(0L)
                    startSeek.progress = (clip.startMs / 1000L).toInt()
                }
                updateTrimLabels()
                refreshClipList()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun loadTrimControls() {
        val clip = selectedClip()
        if (clip == null) {
            selectedClipLabel.text = "Önce bir video seç."
            startSeek.max = 1
            endSeek.max = 1
            startSeek.progress = 0
            endSeek.progress = 1
            startSeek.isEnabled = false
            endSeek.isEnabled = false
            updateTrimLabels()
            return
        }
        startSeek.isEnabled = true
        endSeek.isEnabled = true
        val durationSeconds = ceil(clip.durationMs / 1000.0).toInt().coerceAtLeast(1)
        startSeek.max = durationSeconds
        endSeek.max = durationSeconds
        startSeek.progress = (clip.startMs / 1000L).toInt()
        endSeek.progress = ceil(clip.endMs / 1000.0).toInt().coerceAtMost(durationSeconds)
        selectedClipLabel.text = "Seçili klip: ${clip.title} • ${formatMs(clip.durationMs)}"
        updateTrimLabels()
    }

    private fun updateTrimLabels() {
        val clip = selectedClip()
        startLabel.text = "Başlangıç: ${formatMs(clip?.startMs ?: 0L)}"
        endLabel.text = "Bitiş: ${formatMs(clip?.endMs ?: 0L)}"
    }

    private fun previewSelectedClip() {
        val clip = selectedClip()
        if (clip == null) {
            status.text = "Önizleme için önce video seç."
            return
        }
        player?.setMediaItem(MediaItem.fromUri(clip.uri))
        player?.seekTo(clip.startMs)
        player?.prepare()
        player?.playWhenReady = true
        status.text = "Önizleme başladı. Kesme aralığı dışa aktarmada uygulanacak."
    }

    private fun exportEditedVideo() {
        if (exporting) return
        if (clips.isEmpty()) {
            status.text = "Önce bir video seç."
            return
        }
        val invalid = clips.firstOrNull { it.startMs < 0 || it.endMs <= it.startMs || it.endMs > it.durationMs }
        if (invalid != null) {
            status.text = "Bir klibin kesme aralığı geçersiz. Başlangıç ve bitişi kontrol et."
            return
        }

        val overlayText = textInput.text?.toString()?.trim().orEmpty()
        val videoItems = try {
            clips.map { clip ->
                val mediaItem = MediaItem.Builder()
                    .setUri(clip.uri)
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(clip.startMs)
                            .setEndPositionMs(clip.endMs)
                            .build()
                    )
                    .build()
                val videoEffects = if (overlayText.isBlank()) {
                    emptyList()
                } else {
                    val styled = SpannableString(overlayText).apply {
                        setSpan(ForegroundColorSpan(Color.WHITE), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        setSpan(BackgroundColorSpan(Color.argb(190, 0, 0, 0)), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        setSpan(StyleSpan(Typeface.BOLD), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    listOf(OverlayEffect(listOf(TextOverlay.createStaticTextOverlay(styled))))
                }
                EditedMediaItem.Builder(mediaItem)
                    .setEffects(Effects(emptyList(), videoEffects))
                    .apply { if (!keepSourceAudio.isChecked) setRemoveAudio(true) }
                    .build()
            }
        } catch (e: Exception) {
            status.text = "Düzenleme hazırlanamadı: ${e.message ?: e.javaClass.simpleName}"
            return
        }

        exporting = true
        exportButton.isEnabled = false
        status.text = "MP4 dışa aktarılıyor…\nKlip birleştirme, yazı ve ek ses uygulanıyor. Uygulamayı kapatma."
        val tempOutput = File(cacheDir, "mm_editor_${System.currentTimeMillis()}.mp4")
        val videoSequence = EditedMediaItemSequence.withAudioAndVideoFrom(videoItems)
        val audioSequence = musicUri?.let { uri ->
            val audioItem = EditedMediaItem.Builder(MediaItem.fromUri(uri))
                .setRemoveVideo(true)
                .build()
            EditedMediaItemSequence.withAudioFrom(listOf(audioItem)).buildUpon().setIsLooping(true).build()
        }
        val composition = if (audioSequence != null) {
            Composition.Builder(videoSequence, audioSequence).build()
        } else {
            Composition.Builder(videoSequence).build()
        }

        val transformer = try {
            Transformer.Builder(this)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        Thread {
                            val saved = runCatching { saveToMovies(tempOutput) }
                            tempOutput.delete()
                            runOnUiThread {
                                exporting = false
                                exportButton.isEnabled = true
                                if (saved.isSuccess) {
                                    val resultUri = saved.getOrThrow()
                                    val now = System.currentTimeMillis()
                                    VideoRecordStore.add(this@VideoEditorActivity, VideoRecord("Düzenlenmiş video", resultUri, now, now))
                                    status.text = "BAŞARILI • Düzenlenmiş MP4 kaydedildi.\n$resultUri"
                                    Toast.makeText(this@VideoEditorActivity, "Video kaydedildi.", Toast.LENGTH_LONG).show()
                                } else {
                                    status.text = "Video işlendi ancak kaydedilemedi: ${saved.exceptionOrNull()?.message}"
                                }
                            }
                        }.start()
                    }

                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        tempOutput.delete()
                        runOnUiThread {
                            exporting = false
                            exportButton.isEnabled = true
                            status.text = "DIŞA AKTARMA BAŞARISIZ\n${exportException.message ?: exportException.errorCodeName}"
                        }
                    }
                })
                .build()
        } catch (e: Exception) {
            exporting = false
            exportButton.isEnabled = true
            status.text = "Dışa aktarıcı başlatılamadı: ${e.message}"
            return
        }

        try {
            transformer.start(composition, tempOutput.absolutePath)
        } catch (e: Exception) {
            tempOutput.delete()
            exporting = false
            exportButton.isEnabled = true
            status.text = "Dışa aktarma başlatılamadı: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    private fun saveToMovies(source: File): String {
        val now = System.currentTimeMillis()
        val name = "MathMotor_Edit_${now}.mp4"
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/MathMotorVideo")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("Movies klasöründe video kaydı oluşturulamadı.")
            try {
                contentResolver.openOutputStream(uri)?.use { output ->
                    FileInputStream(source).use { input -> input.copyTo(output) }
                } ?: error("Kayıt dosyası açılamadı.")
                contentResolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
                return uri.toString()
            } catch (t: Throwable) {
                contentResolver.delete(uri, null, null)
                throw t
            }
        }
        @Suppress("DEPRECATION")
        val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "MathMotorVideo")
        if (!folder.exists() && !folder.mkdirs()) error("Movies/MathMotorVideo klasörü oluşturulamadı.")
        val target = File(folder, name)
        source.copyTo(target, overwrite = true)
        return Uri.fromFile(target).toString()
    }

    private fun selectedClip(): Clip? = clips.getOrNull(selectedClipIndex)

    private fun formatMs(ms: Long): String {
        val total = (ms.coerceAtLeast(0L) / 1000L).toInt()
        return String.format(Locale.US, "%02d:%02d", total / 60, total % 60)
    }

    private fun label(value: String, size: Float, color: Int, bold: Boolean): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(5), 0, dp(5))
        }

    private fun actionButton(value: String, action: () -> Unit): Button = Button(this).apply {
        text = value
        isAllCaps = false
        textSize = 13f
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        setPadding(dp(10), dp(8), dp(10), dp(8))
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(Color.rgb(35, 95, 117))
            cornerRadius = dp(10).toFloat()
        }
        minHeight = dp(44)
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(4); bottomMargin = dp(4) }
    }

    private fun gap(dpValue: Int): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(dpValue))
    }

    private fun matchWrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onStop() {
        player?.pause()
        super.onStop()
    }

    override fun onDestroy() {
        playerView.player = null
        player?.release()
        player = null
        super.onDestroy()
    }
}

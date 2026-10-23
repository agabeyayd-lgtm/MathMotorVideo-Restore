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
import android.os.Handler
import android.os.Looper
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
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
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
    private var soundUri: Uri? = null
    private var soundTitle: String? = null
    private var soundDurationMs: Long = 0L
    private var soundStartMs: Long = 0L
    private var textStartMs: Long = 0L
    private var textEndMs: Long = 0L
    private var textTimingCustomized = false
    private var sourceAudioVolume = 100
    private var musicVolume = 45
    private var soundVolume = 100
    private var textPosition = TextPosition.BOTTOM
    private enum class TextPosition { TOP, CENTER, BOTTOM }
    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var previewTextOverlay: TextView
    private lateinit var clipList: LinearLayout
    private lateinit var timelineTracks: LinearLayout
    private lateinit var timelineTracksFrame: FrameLayout
    private lateinit var timelinePlayheadLine: View
    private val timelineScrollViews = mutableListOf<android.widget.HorizontalScrollView>()
    private var syncingTimelineScroll = false
    private lateinit var timelinePlayheadSeek: SeekBar
    private lateinit var timelinePlayheadLabel: TextView
    private var timelinePlayheadMs = 0L
    private var timelinePreviewLoaded = false
    private val timelineHandler = Handler(Looper.getMainLooper())
    private val timelineSyncTask = object : Runnable {
        override fun run() {
            if (timelinePreviewLoaded) syncTimelinePlayheadFromPlayer()
            timelineHandler.postDelayed(this, 250L)
        }
    }
    private val editorPanels = linkedMapOf<String, LinearLayout>()
    private val editorTabButtons = linkedMapOf<String, Button>()
    private lateinit var selectedClipLabel: TextView
    private lateinit var startLabel: TextView
    private lateinit var endLabel: TextView
    private lateinit var startSeek: SeekBar
    private lateinit var endSeek: SeekBar
    private lateinit var musicLabel: TextView
    private lateinit var soundLabel: TextView
    private lateinit var soundStartLabel: TextView
    private lateinit var soundStartSeek: SeekBar
    private lateinit var soundVolumeLabel: TextView
    private lateinit var soundVolumeSeek: SeekBar
    private lateinit var musicVolumeLabel: TextView
    private lateinit var musicVolumeSeek: SeekBar
    private lateinit var sourceVolumeLabel: TextView
    private lateinit var sourceVolumeSeek: SeekBar
    private lateinit var textStartLabel: TextView
    private lateinit var textEndLabel: TextView
    private lateinit var textStartSeek: SeekBar
    private lateinit var textEndSeek: SeekBar
    private lateinit var textPositionGroup: android.widget.RadioGroup
    private lateinit var textInput: EditText
    private lateinit var keepSourceAudio: CheckBox
    private lateinit var status: TextView
    private lateinit var exportButton: Button
    private lateinit var exportNameInput: EditText
    private var exporting = false

    companion object {
        private const val REQUEST_MAIN_VIDEO = 5101
        private const val REQUEST_ADD_VIDEO = 5102
        private const val REQUEST_MUSIC = 5103
        private const val REQUEST_SOUND = 5104
        private const val REQUEST_SAVE_PROJECT = 5105
        private const val REQUEST_OPEN_PROJECT = 5106
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(15, 31, 45)
        window.navigationBarColor = Color.rgb(15, 31, 45)
        buildUi()
        player = ExoPlayer.Builder(this).build().also { playerView.player = it }
        timelineHandler.post(timelineSyncTask)
        val incoming = intent.getStringExtra("video_uri")
        if (!incoming.isNullOrBlank()) addClip(Uri.parse(incoming), false)
    }

    private fun buildUi() {
        editorPanels.clear()
        editorTabButtons.clear()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setBackgroundColor(Color.rgb(12, 18, 28))
        }

        fun miniButton(title: String, onClick: () -> Unit): Button = Button(this).apply {
            text = title
            isAllCaps = false
            textSize = 12f
            setTextColor(Color.WHITE)
            minHeight = dp(38)
            setPadding(dp(8), 0, dp(8), 0)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.rgb(35, 95, 117))
                cornerRadius = dp(9).toFloat()
            }
            setOnClickListener { onClick() }
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        topBar.addView(miniButton("‹ Geri") { finish() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42)))
        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, dp(4), 0)
            addView(label("Düzenle", 18f, Color.WHITE, true))
            addView(label("MOBILE VIDEO STUDIO", 10f, Color.rgb(154, 171, 190), false))
        }
        topBar.addView(heading, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        topBar.addView(miniButton("Dışa aktar ›") { showEditorPanel("export") },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42)))
        root.addView(topBar, matchWrap())
        root.addView(gap(6))

        playerView = PlayerView(this).apply {
            setBackgroundColor(Color.BLACK)
            useController = true
        }
        val previewFrame = FrameLayout(this)
        previewFrame.addView(
            playerView,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        previewTextOverlay = TextView(this).apply {
            textSize = 17f
            gravity = Gravity.CENTER
            maxLines = 2
            maxWidth = dp(290)
            setTextColor(Color.WHITE)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setShadowLayer(2f, 1f, 1f, Color.BLACK)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.argb(210, 0, 0, 0))
                cornerRadius = dp(8).toFloat()
            }
            visibility = View.GONE
        }
        previewFrame.addView(
            previewTextOverlay,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).apply {
                leftMargin = dp(8)
                rightMargin = dp(8)
                bottomMargin = dp(8)
            }
        )
        root.addView(previewFrame, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(205)
        ).apply { bottomMargin = dp(6) })

        val timelineCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(5), dp(8), dp(5))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.rgb(23, 32, 45))
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), Color.rgb(48, 66, 85))
            }
        }
        val timelineHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        timelineHeader.addView(label("ZAMAN ÇİZELGESİ", 12f, Color.rgb(121, 202, 218), true),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        timelineHeader.addView(miniButton("+ Klip") { chooseVideo(true) })
        timelineHeader.addView(miniButton("Değiştir") { chooseVideo(false) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)).apply {
                leftMargin = dp(5)
            })
        timelineCard.addView(timelineHeader, matchWrap())

        val clipScroll = android.widget.HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        clipList = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(5))
        }
        clipScroll.addView(clipList)
        timelineCard.addView(clipScroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(58)
        ))
        timelineTracksFrame = FrameLayout(this)
        timelineTracks = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        timelineTracksFrame.addView(
            timelineTracks,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        timelinePlayheadLine = View(this).apply {
            setBackgroundColor(Color.rgb(255, 78, 92))
            elevation = dp(4).toFloat()
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        timelineTracksFrame.addView(
            timelinePlayheadLine,
            FrameLayout.LayoutParams(dp(2), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.TOP or Gravity.START)
        )
        timelineCard.addView(timelineTracksFrame, matchWrap())

        val playheadRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(3), dp(3), dp(3), 0)
        }
        timelinePlayheadLabel = label("00:00 / 00:00", 10f, Color.rgb(225, 233, 237), true)
        playheadRow.addView(timelinePlayheadLabel, LinearLayout.LayoutParams(dp(82), ViewGroup.LayoutParams.WRAP_CONTENT))
        timelinePlayheadSeek = SeekBar(this).apply {
            max = 0
            progress = 0
            isEnabled = false
            contentDescription = "Zaman çizelgesinde oynatma noktası"
        }
        playheadRow.addView(timelinePlayheadSeek, LinearLayout.LayoutParams(0, dp(34), 1f))
        timelineCard.addView(playheadRow, matchWrap())
        installTimelinePlayheadListeners()
        root.addView(timelineCard, matchWrap())
        root.addView(gap(8))

        val clipPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        clipPanel.addView(label("KLİP DÜZENLEME", 13f, Color.rgb(121, 202, 218), true))
        selectedClipLabel = label("Önce bir video seç.", 12f, Color.WHITE, true)
        clipPanel.addView(selectedClipLabel)
        val clipActions1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        clipActions1.addView(miniButton("Önizle") { previewSelectedClip() },
            LinearLayout.LayoutParams(0, dp(42), 1f))
        clipActions1.addView(miniButton("Klibi kaldır") { removeSelectedClip() },
            LinearLayout.LayoutParams(0, dp(42), 1f).apply { leftMargin = dp(5) })
        clipPanel.addView(clipActions1)
        val clipActions2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        clipActions2.addView(miniButton("Böl ✂") { splitSelectedClipAtPlayhead() },
            LinearLayout.LayoutParams(0, dp(42), 1f))
        clipActions2.addView(miniButton("← Öne al") { moveSelectedClip(-1) },
            LinearLayout.LayoutParams(0, dp(42), 1f).apply { leftMargin = dp(4) })
        clipActions2.addView(miniButton("Sona al →") { moveSelectedClip(1) },
            LinearLayout.LayoutParams(0, dp(42), 1f).apply { leftMargin = dp(4) })
        clipPanel.addView(clipActions2)
        startLabel = label("Başlangıç: 00:00", 12f, Color.rgb(225, 233, 237), true)
        clipPanel.addView(startLabel)
        startSeek = SeekBar(this)
        clipPanel.addView(startSeek, matchWrap())
        endLabel = label("Bitiş: 00:00", 12f, Color.rgb(225, 233, 237), true)
        clipPanel.addView(endLabel)
        endSeek = SeekBar(this)
        clipPanel.addView(endSeek, matchWrap())
        installTrimListeners()

        val textPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        textPanel.addView(label("METİN KATMANI", 13f, Color.rgb(121, 202, 218), true))
        textInput = EditText(this).apply {
            hint = "Videoda görünecek yazı"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(154, 171, 190))
            setSingleLine(false)
            minLines = 1
            maxLines = 2
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(Color.rgb(27, 39, 54))
        }
        textPanel.addView(textInput, matchWrap())
        textPanel.addView(label("Yazının görünme aralığı", 11f, Color.rgb(154, 171, 190), false))
        textStartLabel = label("Yazı başlangıcı: 00:00", 12f, Color.WHITE, true)
        textPanel.addView(textStartLabel)
        textStartSeek = SeekBar(this).apply { max = 0; isEnabled = false }
        textPanel.addView(textStartSeek, matchWrap())
        textEndLabel = label("Yazı bitişi: 00:00", 12f, Color.WHITE, true)
        textPanel.addView(textEndLabel)
        textEndSeek = SeekBar(this).apply { max = 0; isEnabled = false }
        textPanel.addView(textEndSeek, matchWrap())
        installTextTimeListeners()
        textPanel.addView(label("Yazı konumu", 11f, Color.rgb(154, 171, 190), false))
        textPositionGroup = android.widget.RadioGroup(this).apply {
            orientation = android.widget.RadioGroup.HORIZONTAL
            addView(android.widget.RadioButton(this@VideoEditorActivity).apply { id = 1; text = "Üst"; setTextColor(Color.WHITE) })
            addView(android.widget.RadioButton(this@VideoEditorActivity).apply { id = 2; text = "Orta"; setTextColor(Color.WHITE) })
            addView(android.widget.RadioButton(this@VideoEditorActivity).apply { id = 3; text = "Alt"; setTextColor(Color.WHITE) })
            check(3)
            setOnCheckedChangeListener { _, checkedId ->
                textPosition = when (checkedId) {
                    1 -> TextPosition.TOP
                    2 -> TextPosition.CENTER
                    else -> TextPosition.BOTTOM
                }
                updatePreviewTextOverlay()
                refreshTimelineTracks()
            }
        }
        textPanel.addView(textPositionGroup, matchWrap())
        textInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updatePreviewTextOverlay()
                refreshTimelineTracks()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        val audioPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        audioPanel.addView(label("SES KATMANLARI", 13f, Color.rgb(121, 202, 218), true))
        musicLabel = label("Müzik seçilmedi.", 12f, Color.WHITE, false)
        audioPanel.addView(musicLabel)
        val musicActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        musicActions.addView(miniButton("Müzik ekle") { chooseAudio(true) },
            LinearLayout.LayoutParams(0, dp(42), 1f))
        musicActions.addView(miniButton("Müziği kaldır") {
            musicUri = null
            musicTitle = null
            musicLabel.text = "Müzik seçilmedi."
            refreshTimelineTracks()
        }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { leftMargin = dp(5) })
        audioPanel.addView(musicActions)
        musicVolumeLabel = label("Müzik sesi: 45%", 12f, Color.WHITE, true)
        audioPanel.addView(musicVolumeLabel)
        musicVolumeSeek = SeekBar(this).apply { max = 100; progress = musicVolume }
        musicVolumeSeek.setOnSeekBarChangeListener(volumeListener { value ->
            musicVolume = value
            musicVolumeLabel.text = "Müzik sesi: " + value + "%"
        })
        audioPanel.addView(musicVolumeSeek, matchWrap())

        audioPanel.addView(gap(4))
        soundLabel = label("Ses efekti seçilmedi.", 12f, Color.WHITE, false)
        audioPanel.addView(soundLabel)
        val soundActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        soundActions.addView(miniButton("Efekt ekle") { chooseAudio(false) },
            LinearLayout.LayoutParams(0, dp(42), 1f))
        soundActions.addView(miniButton("Efekti kaldır") {
            soundUri = null
            soundTitle = null
            soundDurationMs = 0L
            soundStartMs = 0L
            soundLabel.text = "Ses efekti seçilmedi."
            updateSoundControls()
            refreshTimelineTracks()
        }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { leftMargin = dp(5) })
        audioPanel.addView(soundActions)
        soundStartLabel = label("Efekt başlangıcı: 00:00", 12f, Color.WHITE, true)
        audioPanel.addView(soundStartLabel)
        soundStartSeek = SeekBar(this).apply { max = 0; isEnabled = false }
        soundStartSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val total = totalOutputDurationMs()
                soundStartMs = (progress * 1000L).coerceAtMost((total - 1L).coerceAtLeast(0L))
                updateSoundControls()
                refreshTimelineTracks()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        audioPanel.addView(soundStartSeek, matchWrap())
        soundVolumeLabel = label("Efekt sesi: 100%", 12f, Color.WHITE, true)
        audioPanel.addView(soundVolumeLabel)
        soundVolumeSeek = SeekBar(this).apply { max = 100; progress = soundVolume }
        soundVolumeSeek.setOnSeekBarChangeListener(volumeListener { value ->
            soundVolume = value
            soundVolumeLabel.text = "Efekt sesi: " + value + "%"
        })
        audioPanel.addView(soundVolumeSeek, matchWrap())
        keepSourceAudio = CheckBox(this).apply {
            text = "Videonun kendi sesini koru"
            isChecked = true
            setTextColor(Color.WHITE)
        }
        audioPanel.addView(keepSourceAudio)
        sourceVolumeLabel = label("Orijinal video sesi: 100%", 12f, Color.WHITE, true)
        audioPanel.addView(sourceVolumeLabel)
        sourceVolumeSeek = SeekBar(this).apply { max = 100; progress = sourceAudioVolume }
        sourceVolumeSeek.setOnSeekBarChangeListener(volumeListener { value ->
            sourceAudioVolume = value
            sourceVolumeLabel.text = "Orijinal video sesi: " + value + "%"
        })
        audioPanel.addView(sourceVolumeSeek, matchWrap())

        val exportPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        exportPanel.addView(label("PROJE / DIŞA AKTARMA", 13f, Color.rgb(121, 202, 218), true))
        exportPanel.addView(label("Dosya adı", 11f, Color.rgb(154, 171, 190), false))
        exportNameInput = EditText(this).apply {
            setText("MathMotor_Edit")
            hint = "Örn. Tatil_videosu"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(154, 171, 190))
            setSingleLine(true)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(Color.rgb(27, 39, 54))
        }
        exportPanel.addView(exportNameInput, matchWrap())
        exportPanel.addView(actionButton("Projeyi farklı kaydet (.JSON)") { requestSaveProject() })
        exportPanel.addView(actionButton("Kayıtlı projeyi aç") { requestOpenProject() })
        exportButton = actionButton("MP4 DIŞA AKTAR") { exportEditedVideo() }
        exportPanel.addView(exportButton)

        editorPanels["clip"] = clipPanel
        editorPanels["text"] = textPanel
        editorPanels["audio"] = audioPanel
        editorPanels["export"] = exportPanel

        val panelScroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        val panelHolder = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(2), dp(4), dp(4))
        }
        editorPanels.values.forEachIndexed { index, panel ->
            panel.visibility = if (index == 0) View.VISIBLE else View.GONE
            panelHolder.addView(panel, matchWrap())
        }
        panelScroll.addView(panelHolder)
        root.addView(panelScroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        status = TextView(this).apply {
            text = "Hazır. Zaman çizelgesine video ekleyebilirsin."
            setTextColor(Color.rgb(205, 218, 231))
            textSize = 11f
            maxLines = 2
            setPadding(dp(8), dp(5), dp(8), dp(5))
            setBackgroundColor(Color.rgb(23, 32, 45))
        }
        root.addView(status, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(4) })

        val dock = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(5), 0, 0)
        }
        listOf(
            Triple("clip", "Klip", "✂"),
            Triple("text", "Metin", "T"),
            Triple("audio", "Ses", "♫"),
            Triple("export", "Dışa aktar", "⇧")
        ).forEach { (key, title, symbol) ->
            val tab = miniButton("$symbol  $title") { showEditorPanel(key) }
            editorTabButtons[key] = tab
            dock.addView(tab, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                if (key != "clip") leftMargin = dp(4)
            })
        }
        root.addView(dock, matchWrap())

        setContentView(root)
        showEditorPanel("clip")
        refreshTimelineTracks()
    }

    private fun showEditorPanel(name: String) {
        editorPanels.forEach { (key, panel) ->
            panel.visibility = if (key == name) View.VISIBLE else View.GONE
        }
        editorTabButtons.forEach { (key, button) ->
            button.background = android.graphics.drawable.GradientDrawable().apply {
                setColor(if (key == name) Color.rgb(0, 145, 125) else Color.rgb(35, 95, 117))
                cornerRadius = dp(9).toFloat()
            }
        }
        if (name == "export") status.text = "Projeyi kaydedebilir veya MP4 olarak dışa aktarabilirsin."
    }

    private fun refreshTimelineTracks() {
        if (!::timelineTracks.isInitialized) return
        timelineTracks.removeAllViews()
        timelineScrollViews.clear()
        if (::timelinePlayheadSeek.isInitialized) updateTimelinePlayheadControls()

        val totalMs = totalOutputDurationMs()
        val pxPerSecond = dp(22).toFloat()

        fun exactWidth(durationMs: Long): Int =
            ((durationMs.coerceAtLeast(0L) / 1000f) * pxPerSecond).toInt().coerceAtLeast(0)

        fun makeLane(tag: String, heightDp: Int = 30): LinearLayout {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(3), dp(1), dp(3), dp(1))
            }
            row.addView(TextView(this).apply {
                text = tag
                textSize = 9f
                setTextColor(Color.rgb(166, 184, 201))
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER_VERTICAL
            }, LinearLayout.LayoutParams(dp(54), ViewGroup.LayoutParams.MATCH_PARENT))

            val scroll = android.widget.HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
            }
            timelineScrollViews.add(scroll)
            scroll.setOnScrollChangeListener { view, scrollX, _, _, _ ->
                if (!syncingTimelineScroll) {
                    syncingTimelineScroll = true
                    try {
                        timelineScrollViews.forEach { other ->
                            if (other !== view && other.scrollX != scrollX) other.scrollTo(scrollX, 0)
                        }
                    } finally {
                        syncingTimelineScroll = false
                    }
                    updateTimelinePlayheadLine()
                }
            }
            val track = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(2), dp(1), dp(2), dp(1))
            }
            scroll.addView(track)
            row.addView(scroll, LinearLayout.LayoutParams(0, dp(heightDp), 1f))
            timelineTracks.addView(row, matchWrap())
            return track
        }

        fun addBlock(
            track: LinearLayout,
            title: String,
            durationMs: Long,
            fill: Int,
            selected: Boolean = false,
            onClick: (() -> Unit)? = null,
            minimumWidthDp: Int = 36
        ) {
            val width = maxOf(dp(minimumWidthDp), exactWidth(durationMs))
            val block = TextView(this).apply {
                text = title
                textSize = 9f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER_VERTICAL
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(dp(6), 0, dp(6), 0)
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(fill)
                    cornerRadius = dp(5).toFloat()
                    setStroke(dp(if (selected) 2 else 1), if (selected) Color.rgb(117, 240, 221) else Color.rgb(61, 79, 99))
                }
                if (onClick != null) {
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { onClick() }
                }
            }
            track.addView(block, LinearLayout.LayoutParams(width, dp(24)).apply {
                rightMargin = dp(3)
            })
        }

        fun addSpacer(track: LinearLayout, durationMs: Long) {
            val width = exactWidth(durationMs)
            if (width <= 0) return
            track.addView(View(this), LinearLayout.LayoutParams(width, dp(24)))
        }

        val ruler = makeLane("SÜRE", 22)
        val rulerEndMs = (((totalMs + 4999L) / 5000L).coerceAtLeast(1L)) * 5000L
        var rulerTimeMs = 0L
        while (rulerTimeMs < rulerEndMs) {
            val spanMs = minOf(5000L, rulerEndMs - rulerTimeMs)
            val tick = TextView(this).apply {
                text = formatMs(rulerTimeMs) + " ┆"
                textSize = 9f
                setTextColor(Color.rgb(166, 184, 201))
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                setPadding(dp(2), 0, 0, 0)
            }
            ruler.addView(tick, LinearLayout.LayoutParams(maxOf(dp(26), exactWidth(spanMs)), dp(22)))
            rulerTimeMs += spanMs
        }

        val videoTrack = makeLane("VIDEO", 24)
        if (clips.isEmpty()) {
            addBlock(videoTrack, "+ Video ekle", 5000L, Color.rgb(38, 105, 123), onClick = { chooseVideo(true) }, minimumWidthDp = 115)
        } else {
            clips.forEachIndexed { index, clip ->
                val duration = (clip.endMs - clip.startMs).coerceAtLeast(0L)
                addBlock(
                    videoTrack,
                    "${index + 1}. ${clip.title}  ${formatMs(duration)}",
                    duration,
                    if (index == selectedClipIndex) Color.rgb(0, 120, 125) else Color.rgb(38, 105, 123),
                    selected = index == selectedClipIndex,
                    onClick = { selectTimelineClip(index) },
                    minimumWidthDp = 10
                )
            }
        }

        val textTrack = makeLane("METİN", 24)
        val overlayText = if (::textInput.isInitialized) textInput.text?.toString()?.trim().orEmpty() else ""
        if (overlayText.isNotBlank() && totalMs > 0L) {
            val startMs = textStartMs.coerceIn(0L, totalMs)
            val endMs = textEndMs.coerceIn(startMs, totalMs)
            addSpacer(textTrack, startMs)
            addBlock(textTrack, overlayText, (endMs - startMs).coerceAtLeast(0L), Color.rgb(110, 74, 154), minimumWidthDp = 42)
            addSpacer(textTrack, (totalMs - endMs).coerceAtLeast(0L))
        } else {
            addBlock(textTrack, "+ Metin ekle", totalMs.coerceAtLeast(5000L), Color.rgb(70, 54, 93),
                onClick = { showEditorPanel("text") }, minimumWidthDp = 115)
        }

        val musicTrack = makeLane("MÜZİK", 24)
        if (musicUri != null) {
            addBlock(musicTrack, "♫ ${musicTitle ?: "Müzik"}", totalMs, Color.rgb(46, 125, 85), minimumWidthDp = 56)
        } else {
            addBlock(musicTrack, "+ Müzik ekle", totalMs.coerceAtLeast(5000L), Color.rgb(42, 78, 63),
                onClick = { chooseAudio(true) }, minimumWidthDp = 115)
        }

        val effectTrack = makeLane("EFEKT", 24)
        if (soundUri != null && totalMs > 0L) {
            val startMs = soundStartMs.coerceIn(0L, totalMs)
            val duration = soundDurationMs.coerceAtMost((totalMs - startMs).coerceAtLeast(0L))
            addSpacer(effectTrack, startMs)
            addBlock(effectTrack, "♪ ${soundTitle ?: "Ses efekti"}", duration, Color.rgb(166, 102, 40), minimumWidthDp = 44)
            addSpacer(effectTrack, (totalMs - startMs - duration).coerceAtLeast(0L))
        } else {
            addBlock(effectTrack, "+ Ses efekti ekle", totalMs.coerceAtLeast(5000L), Color.rgb(96, 68, 41),
                onClick = { chooseAudio(false) }, minimumWidthDp = 115)
        }
        updateTimelinePlayheadLine()
    }

    private fun updateTimelinePlayheadLine() {
        if (!::timelinePlayheadLine.isInitialized || !::timelineTracksFrame.isInitialized) return
        val scrollX = timelineScrollViews.firstOrNull()?.scrollX ?: 0
        val pixelsPerSecond = dp(22).toFloat()
        val timelineX = dp(54 + 3) + (timelinePlayheadMs / 1000f * pixelsPerSecond).toInt() - scrollX
        val params = timelinePlayheadLine.layoutParams as? FrameLayout.LayoutParams ?: return
        params.width = dp(2)
        params.leftMargin = timelineX.coerceAtLeast(dp(0))
        timelinePlayheadLine.layoutParams = params
        timelinePlayheadLine.bringToFront()
    }

    private fun installTimelinePlayheadListeners() {
        timelinePlayheadSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                timelinePlayheadMs = progress.toLong()
                updateTimelinePlayheadLabel()
                seekTimelinePreview(timelinePlayheadMs)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                if (clips.isEmpty()) return
                ensureTimelinePreviewLoaded()
                player?.playWhenReady = false
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                updateTimelinePlayheadLabel()
            }
        })
    }

    private fun updateTimelinePlayheadControls() {
        if (!::timelinePlayheadSeek.isInitialized || !::timelinePlayheadLabel.isInitialized) return
        val totalMs = totalOutputDurationMs()
        val maxMs = totalMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt().coerceAtLeast(0)
        timelinePlayheadSeek.max = maxMs
        timelinePlayheadSeek.isEnabled = clips.isNotEmpty() && totalMs > 0L
        timelinePlayheadMs = timelinePlayheadMs.coerceIn(0L, totalMs)
        timelinePlayheadSeek.progress = timelinePlayheadMs.coerceAtMost(maxMs.toLong()).toInt()
        updateTimelinePlayheadLabel()
        updateTimelinePlayheadLine()
    }

    private fun updateTimelinePlayheadLabel() {
        if (!::timelinePlayheadLabel.isInitialized) return
        timelinePlayheadLabel.text = "${formatTimelineMs(timelinePlayheadMs)} / ${formatTimelineMs(totalOutputDurationMs())}"
    }

    private fun ensureTimelinePreviewLoaded() {
        if (timelinePreviewLoaded || clips.isEmpty()) return
        val items = clips.map { clip -> clippedMediaItem(clip) }
        player?.setMediaItems(items, 0, 0L)
        player?.prepare()
        player?.playWhenReady = false
        timelinePreviewLoaded = true
    }

    private fun seekTimelinePreview(requestedMs: Long) {
        if (clips.isEmpty()) return
        ensureTimelinePreviewLoaded()
        val totalMs = totalOutputDurationMs()
        if (totalMs <= 0L) return
        val targetMs = requestedMs.coerceIn(0L, (totalMs - 1L).coerceAtLeast(0L))
        var offsetMs = 0L
        clips.forEachIndexed { index, clip ->
            val durationMs = (clip.endMs - clip.startMs).coerceAtLeast(0L)
            if (targetMs < offsetMs + durationMs || index == clips.lastIndex) {
                val localMs = (targetMs - offsetMs).coerceIn(0L, (durationMs - 1L).coerceAtLeast(0L))
                player?.seekTo(index, localMs)
                timelinePlayheadMs = targetMs
                timelinePlayheadSeek.progress = targetMs.coerceAtMost(timelinePlayheadSeek.max.toLong()).toInt()
                updateTimelinePlayheadLabel()
                return
            }
            offsetMs += durationMs
        }
    }

    private fun syncTimelinePlayheadFromPlayer() {
        val currentPlayer = player ?: return
        if (!timelinePreviewLoaded || clips.isEmpty()) return
        val index = currentPlayer.currentMediaItemIndex
        if (index !in clips.indices) return
        val offsetMs = clips.take(index).sumOf { (it.endMs - it.startMs).coerceAtLeast(0L) }
        timelinePlayheadMs = (offsetMs + currentPlayer.currentPosition).coerceIn(0L, totalOutputDurationMs())
        val progress = timelinePlayheadMs.coerceAtMost(timelinePlayheadSeek.max.toLong()).toInt()
        if (timelinePlayheadSeek.progress != progress) timelinePlayheadSeek.progress = progress
        updateTimelinePlayheadLabel()
    }

    private fun selectTimelineClip(index: Int) {
        if (index !in clips.indices) return
        timelinePreviewLoaded = false
        selectedClipIndex = index
        val clip = clips[index]
        refreshClipList()
        loadTrimControls()
        player?.setMediaItem(clippedMediaItem(clip))
        player?.prepare()
        player?.playWhenReady = false
        status.text = "Seçili klip: ${clip.title}. Klipler dışa aktarılırken bu sırayla tek MP4'e birleştirilir."
    }

    private fun updatePreviewTextOverlay() {
        if (!::previewTextOverlay.isInitialized || !::textInput.isInitialized) return
        val value = textInput.text?.toString()?.trim().orEmpty()
        if (value.isBlank()) {
            previewTextOverlay.visibility = View.GONE
            return
        }
        previewTextOverlay.text = value
        previewTextOverlay.visibility = View.VISIBLE
        val params = previewTextOverlay.layoutParams as? FrameLayout.LayoutParams ?: return
        params.gravity = when (textPosition) {
            TextPosition.TOP -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
            TextPosition.CENTER -> Gravity.CENTER
            TextPosition.BOTTOM -> Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        }
        params.topMargin = if (textPosition == TextPosition.TOP) dp(8) else 0
        params.bottomMargin = if (textPosition == TextPosition.BOTTOM) dp(8) else 0
        params.leftMargin = dp(8)
        params.rightMargin = dp(8)
        previewTextOverlay.layoutParams = params
    }

    private fun volumeListener(onValue: (Int) -> Unit): SeekBar.OnSeekBarChangeListener =
        object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) onValue(progress.coerceIn(0, 100))
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }

    private fun installTextTimeListeners() {
        textStartSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                textTimingCustomized = true
                textStartMs = (progress * 1000L).coerceAtMost(totalOutputDurationMs())
                if (textEndMs <= textStartMs) textEndMs = (textStartMs + 1000L).coerceAtMost(totalOutputDurationMs())
                updateTextControls()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        textEndSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                textTimingCustomized = true
                textEndMs = (progress * 1000L).coerceAtMost(totalOutputDurationMs())
                if (textEndMs <= textStartMs) textStartMs = (textEndMs - 1000L).coerceAtLeast(0L)
                updateTextControls()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun updateTextControls() {
        if (!::textStartSeek.isInitialized || !::textEndSeek.isInitialized) return
        val total = totalOutputDurationMs()
        val maxSeconds = ceil(total / 1000.0).toInt().coerceAtLeast(0)
        if (!textTimingCustomized || textEndMs <= 0L || textEndMs > total) textEndMs = total
        textStartMs = textStartMs.coerceAtMost((total - 1L).coerceAtLeast(0L))
        if (textEndMs <= textStartMs && total > 0L) {
            textStartMs = 0L
            textEndMs = total
        }
        textStartSeek.max = maxSeconds
        textEndSeek.max = maxSeconds
        textStartSeek.progress = (textStartMs / 1000L).toInt().coerceAtMost(maxSeconds)
        textEndSeek.progress = ceil(textEndMs / 1000.0).toInt().coerceAtMost(maxSeconds)
        textStartSeek.isEnabled = total > 0L
        textEndSeek.isEnabled = total > 0L
        textStartLabel.text = "Yazı başlangıcı: " + formatMs(textStartMs)
        textEndLabel.text = "Yazı bitişi: " + formatMs(textEndMs)
        refreshTimelineTracks()
    }

    private fun audioEffects(volumePercent: Int): List<AudioProcessor> {
        if (volumePercent >= 100) return emptyList()
        val gain = volumePercent.coerceIn(0, 100) / 100f
        val processor = ChannelMixingAudioProcessor()
        for (channels in 1..8) {
            val coefficients = FloatArray(channels * channels)
            for (channel in 0 until channels) coefficients[channel * channels + channel] = gain
            processor.putChannelMixingMatrix(ChannelMixingMatrix(channels, channels, coefficients))
        }
        return listOf(processor)
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
                timelinePreviewLoaded = false
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

    private fun requestSaveProject() {
        if (clips.isEmpty()) {
            status.text = "Projeyi kaydetmeden önce en az bir video ekle."
            return
        }
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "MathMotor_Proje.json")
        }
        try {
            startActivityForResult(intent, REQUEST_SAVE_PROJECT)
        } catch (e: Exception) {
            status.text = "Proje kaydetme ekranı açılamadı: ${e.message}"
        }
    }

    private fun requestOpenProject() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        try {
            startActivityForResult(intent, REQUEST_OPEN_PROJECT)
        } catch (e: Exception) {
            status.text = "Proje açma ekranı açılamadı: ${e.message}"
        }
    }

    private fun createProjectJson(): JSONObject {
        val clipArray = JSONArray()
        clips.forEach { clip ->
            clipArray.put(JSONObject().apply {
                put("uri", clip.uri.toString())
                put("title", clip.title)
                put("durationMs", clip.durationMs)
                put("startMs", clip.startMs)
                put("endMs", clip.endMs)
            })
        }
        return JSONObject().apply {
            put("format", "MathMotorVideoProject")
            put("version", 1)
            put("clips", clipArray)
            put("selectedClipIndex", selectedClipIndex)
            put("musicUri", musicUri?.toString() ?: JSONObject.NULL)
            put("musicTitle", musicTitle ?: JSONObject.NULL)
            put("soundUri", soundUri?.toString() ?: JSONObject.NULL)
            put("soundTitle", soundTitle ?: JSONObject.NULL)
            put("soundDurationMs", soundDurationMs)
            put("soundStartMs", soundStartMs)
            put("text", textInput.text?.toString().orEmpty())
            put("textStartMs", textStartMs)
            put("textEndMs", textEndMs)
            put("textTimingCustomized", textTimingCustomized)
            put("textPosition", textPosition.name)
            put("sourceAudioVolume", sourceAudioVolume)
            put("musicVolume", musicVolume)
            put("soundVolume", soundVolume)
            put("keepSourceAudio", keepSourceAudio.isChecked)
            put("exportName", exportNameInput.text?.toString().orEmpty())
        }
    }

    private fun applyProjectJson(json: JSONObject) {
        require(json.optString("format") == "MathMotorVideoProject") { "Bu dosya MathMotorVideo proje dosyası değil." }
        require(json.optInt("version", 0) == 1) { "Bu proje sürümü desteklenmiyor." }
        val loadedClips = mutableListOf<Clip>()
        val clipArray = json.optJSONArray("clips") ?: JSONArray()
        for (i in 0 until clipArray.length()) {
            val item = clipArray.getJSONObject(i)
            val uri = Uri.parse(item.getString("uri"))
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) { }
            val duration = item.optLong("durationMs", 0L)
            val start = item.optLong("startMs", 0L).coerceIn(0L, duration)
            val end = item.optLong("endMs", duration).coerceIn(start, duration)
            require(duration > 0L && end > start) { "Projede geçersiz klip aralığı var." }
            loadedClips.add(Clip(uri, item.optString("title", "Video klibi"), duration, start, end))
        }
        require(loadedClips.isNotEmpty()) { "Projede video klibi bulunamadı." }

        clips.clear()
        clips.addAll(loadedClips)
        selectedClipIndex = json.optInt("selectedClipIndex", 0).coerceIn(0, clips.lastIndex)
        musicUri = json.optString("musicUri").takeIf { it.isNotBlank() && it != "null" }?.let(Uri::parse)
        musicTitle = json.optString("musicTitle").takeIf { it.isNotBlank() && it != "null" }
        soundUri = json.optString("soundUri").takeIf { it.isNotBlank() && it != "null" }?.let(Uri::parse)
        soundTitle = json.optString("soundTitle").takeIf { it.isNotBlank() && it != "null" }
        soundDurationMs = json.optLong("soundDurationMs", 0L).coerceAtLeast(0L)
        soundStartMs = json.optLong("soundStartMs", 0L).coerceAtLeast(0L)
        textStartMs = json.optLong("textStartMs", 0L).coerceAtLeast(0L)
        textEndMs = json.optLong("textEndMs", totalOutputDurationMs()).coerceAtLeast(0L)
        textTimingCustomized = json.optBoolean("textTimingCustomized", false)
        textPosition = runCatching { TextPosition.valueOf(json.optString("textPosition", "BOTTOM")) }.getOrDefault(TextPosition.BOTTOM)
        sourceAudioVolume = json.optInt("sourceAudioVolume", 100).coerceIn(0, 100)
        musicVolume = json.optInt("musicVolume", 45).coerceIn(0, 100)
        soundVolume = json.optInt("soundVolume", 100).coerceIn(0, 100)

        textInput.setText(json.optString("text", ""))
        textPositionGroup.check(when (textPosition) {
            TextPosition.TOP -> 1
            TextPosition.CENTER -> 2
            TextPosition.BOTTOM -> 3
        })
        keepSourceAudio.isChecked = json.optBoolean("keepSourceAudio", true)
        exportNameInput.setText(json.optString("exportName", "MathMotor_Edit"))
        musicLabel.text = musicTitle?.let { "Müzik: $it • video boyunca döngü" } ?: "Müzik seçilmedi."
        soundLabel.text = soundTitle?.let { "Ses/efekt: $it • ${formatMs(soundDurationMs)}" } ?: "Ses veya efekt seçilmedi."
        musicVolumeSeek.progress = musicVolume
        musicVolumeLabel.text = "Müzik ses seviyesi: $musicVolume%"
        soundVolumeSeek.progress = soundVolume
        soundVolumeLabel.text = "Ses/efekt seviyesi: $soundVolume%"
        sourceVolumeSeek.progress = sourceAudioVolume
        sourceVolumeLabel.text = "Orijinal video sesi: $sourceAudioVolume%"
        refreshClipList()
        loadTrimControls()
        selectedClip()?.let { clip ->
            player?.setMediaItem(clippedMediaItem(clip))
            player?.prepare()
            player?.playWhenReady = false
        }
        status.text = "PROJE AÇILDI. Klipler, kesmeler, yazı ve ses ayarları geri yüklendi."
    }

    private fun chooseVideo(append: Boolean) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "video/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, append)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        try {
            startActivityForResult(intent, if (append) REQUEST_ADD_VIDEO else REQUEST_MAIN_VIDEO)
        } catch (e: Exception) {
            status.text = "Video seçici açılamadı: ${e.message}"
        }
    }

    private fun chooseAudio(forMusic: Boolean) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        try {
            startActivityForResult(intent, if (forMusic) REQUEST_MUSIC else REQUEST_SOUND)
        } catch (e: Exception) {
            status.text = "Ses seçici açılamadı: ${e.message}"
        }
    }

    @Deprecated("Use Activity Result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        if (requestCode == REQUEST_MAIN_VIDEO || requestCode == REQUEST_ADD_VIDEO) {
            val selectedUris = mutableListOf<Uri>()
            val picked = data?.clipData
            if (picked != null) {
                for (i in 0 until picked.itemCount) selectedUris.add(picked.getItemAt(i).uri)
            } else {
                data?.data?.let { selectedUris.add(it) }
            }
            if (selectedUris.isEmpty()) {
                status.text = "Video seçilmedi."
                return
            }
            addClipsInOrder(selectedUris, requestCode == REQUEST_MAIN_VIDEO)
            return
        }
        val uri = data?.data ?: return
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) { }

        when (requestCode) {
            REQUEST_SAVE_PROJECT -> {
                runCatching {
                    contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(createProjectJson().toString(2)) }
                        ?: error("Proje dosyası açılamadı.")
                }.onSuccess {
                    status.text = "PROJE KAYDEDİLDİ. Daha sonra 'Kayıtlı projeyi aç' ile devam edebilirsin."
                    Toast.makeText(this, "Düzenleme projesi kaydedildi.", Toast.LENGTH_LONG).show()
                }.onFailure { status.text = "Proje kaydedilemedi: ${it.message}" }
            }
            REQUEST_OPEN_PROJECT -> {
                runCatching {
                    val json = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                        ?: error("Proje dosyası okunamadı.")
                    applyProjectJson(JSONObject(json))
                }.onFailure { status.text = "Proje açılamadı: ${it.message}" }
            }
            REQUEST_MUSIC -> {
                musicUri = uri
                musicTitle = queryDisplayName(uri) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Müzik"
                musicLabel.text = "Müzik: ${musicTitle} • video boyunca döngü"
                refreshTimelineTracks()
                status.text = "Müzik katmanı eklendi. Video süresi boyunca çalar."
            }
            REQUEST_SOUND -> {
                status.text = "Ses dosyasının süresi okunuyor…"
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
                    val title = queryDisplayName(uri) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Ses efekti"
                    runOnUiThread {
                        if (duration <= 0L) {
                            status.text = "Ses dosyasının süresi okunamadı. Başka bir ses dosyası seç."
                        } else {
                            soundUri = uri
                            soundTitle = title
                            soundDurationMs = duration
                            soundStartMs = 0L
                            soundLabel.text = "Ses/efekt: ${title} • ${formatMs(duration)}"
                            updateSoundControls()
                            refreshTimelineTracks()
                            status.text = "Ses katmanı eklendi. Başlama saniyesini aşağıdaki kaydırıcıdan ayarla."
                        }
                    }
                }.start()
            }
        }
    }

    private fun addClipsInOrder(uris: List<Uri>, replaceExisting: Boolean) {
        if (uris.isEmpty()) return
        status.text = "Seçilen ${uris.size} video sırayla hazırlanıyor…"
        Thread {
            val ready = uris.mapNotNull { uri ->
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) { }
                runCatching {
                    val retriever = MediaMetadataRetriever()
                    val duration = try {
                        retriever.setDataSource(this, uri)
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    } finally {
                        retriever.release()
                    }
                    if (duration <= 0L) null else Clip(
                        uri = uri,
                        title = queryDisplayName(uri) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Video klibi",
                        durationMs = duration,
                        startMs = 0L,
                        endMs = duration
                    )
                }.getOrNull()
            }
            runOnUiThread {
                if (replaceExisting) {
                    clips.clear()
                    selectedClipIndex = -1
                }
                clips.addAll(ready)
                if (ready.isEmpty()) {
                    status.text = "Videolar okunamadı. Başka bir MP4/video dosyası seç."
                    refreshClipList()
                    return@runOnUiThread
                }
                selectedClipIndex = clips.lastIndex
                timelinePreviewLoaded = false
                refreshClipList()
                loadTrimControls()
                val selected = selectedClip()
                if (selected != null) {
                    player?.setMediaItem(clippedMediaItem(selected))
                    player?.prepare()
                    player?.playWhenReady = false
                }
                val skipped = uris.size - ready.size
                status.text = buildString {
                    append("${ready.size} video zaman çizelgesine eklendi. Klibe dokun: seç; basılı tutup sürükle: sırala.")
                    if (skipped > 0) append(" $skipped dosya okunamadığı için atlandı.")
                    append(" İstediğin sırada tek MP4 olarak dışa aktarabilirsin.")
                }
            }
        }.start()
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
        timelinePreviewLoaded = false
        clipList.removeAllViews()
        if (clips.isEmpty()) {
            clipList.addView(TextView(this).apply {
                text = "＋ Video ekle"
                textSize = 12f
                setTextColor(Color.rgb(173, 191, 204))
                gravity = Gravity.CENTER
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.rgb(27, 39, 54))
                    cornerRadius = dp(8).toFloat()
                    setStroke(dp(1), Color.rgb(48, 66, 85))
                }
                setPadding(dp(16), 0, dp(16), 0)
            }, LinearLayout.LayoutParams(dp(135), dp(64)))
        } else {
            clips.forEachIndexed { index, clip ->
                val selected = index == selectedClipIndex
                val tile = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(9), dp(5), dp(7), dp(5))
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(if (selected) Color.rgb(24, 93, 104) else Color.rgb(27, 39, 54))
                        cornerRadius = dp(8).toFloat()
                        setStroke(dp(if (selected) 2 else 1),
                            if (selected) Color.rgb(91, 224, 210) else Color.rgb(48, 66, 85))
                    }
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        selectedClipIndex = index
                        refreshClipList()
                        loadTrimControls()
                        player?.setMediaItem(clippedMediaItem(clip))
                        player?.prepare()
                        player?.playWhenReady = true
                        status.text = "Klip ${index + 1} seçildi: ${clip.title}"
                    }
                }
                tile.addView(TextView(this).apply {
                    text = "${if (selected) "▶ " else ""}KLİP ${index + 1}"
                    textSize = 10f
                    setTextColor(if (selected) Color.rgb(117, 240, 221) else Color.rgb(154, 171, 190))
                    typeface = Typeface.DEFAULT_BOLD
                })
                tile.addView(TextView(this).apply {
                    text = clip.title
                    textSize = 11f
                    setTextColor(Color.WHITE)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                })
                tile.addView(TextView(this).apply {
                    text = formatMs(clip.endMs - clip.startMs)
                    textSize = 10f
                    setTextColor(Color.rgb(205, 218, 231))
                })
                tile.setOnLongClickListener {
                    val dragData = android.content.ClipData.newPlainText("video_clip_index", index.toString())
                    tile.startDragAndDrop(dragData, View.DragShadowBuilder(tile), index, 0)
                    status.text = "Klibi basılı tutup sürükleyerek sırasını değiştir."
                    true
                }
                tile.setOnDragListener { _, event ->
                    when (event.action) {
                        View.DragEvent.ACTION_DRAG_STARTED -> event.clipDescription?.hasMimeType("text/plain") == true
                        View.DragEvent.ACTION_DRAG_ENTERED,
                        View.DragEvent.ACTION_DRAG_LOCATION,
                        View.DragEvent.ACTION_DRAG_EXITED -> true
                        View.DragEvent.ACTION_DROP -> {
                            val from = event.localState as? Int
                            if (from != null && from in clips.indices) reorderClip(from, index)
                            true
                        }
                        View.DragEvent.ACTION_DRAG_ENDED -> true
                        else -> false
                    }
                }
                clipList.addView(tile, LinearLayout.LayoutParams(dp(145), dp(68)).apply {
                    rightMargin = dp(6)
                })
            }
        }
        refreshTimelineTracks()
    }

    private fun removeSelectedClip() {
        if (selectedClipIndex !in clips.indices) {
            status.text = "Önce kaldırılacak klibi seç."
            return
        }
        clips.removeAt(selectedClipIndex)
        timelinePreviewLoaded = false
        selectedClipIndex = if (clips.isEmpty()) -1 else selectedClipIndex.coerceAtMost(clips.lastIndex)
        refreshClipList()
        loadTrimControls()
        val selected = selectedClip()
        if (selected == null) {
            player?.clearMediaItems()
        } else {
            player?.setMediaItem(MediaItem.fromUri(selected.uri))
            player?.prepare()
        }
        status.text = if (clips.isEmpty()) "Zaman çizelgesi boş." else "Klip kaldırıldı. Kalan klipler yerinde duruyor."
    }

    private fun reorderClip(from: Int, to: Int) {
        if (from !in clips.indices || to !in clips.indices || from == to) return
        val previouslySelected = selectedClip()
        val moved = clips.removeAt(from)
        clips.add(to, moved)
        selectedClipIndex = clips.indexOfFirst { it === previouslySelected }.takeIf { it >= 0 } ?: to
        timelinePreviewLoaded = false
        refreshClipList()
        loadTrimControls()
        val selected = selectedClip()
        if (selected != null) {
            player?.setMediaItem(clippedMediaItem(selected))
            player?.prepare()
            player?.playWhenReady = false
        }
        status.text = "Klip sırası değişti. Dışa aktarma bu yeni sırayı kullanacak."
    }

    private fun splitSelectedClipAtPlayhead() {
        val clip = selectedClip()
        if (clip == null) {
            status.text = "Önce bölmek istediğin klibi seç."
            return
        }
        val duration = (clip.endMs - clip.startMs).coerceAtLeast(0L)
        val localPosition = if (timelinePreviewLoaded) {
            val clipTimelineStart = clips.take(selectedClipIndex).sumOf { (it.endMs - it.startMs).coerceAtLeast(0L) }
            (timelinePlayheadMs - clipTimelineStart).coerceIn(0L, duration)
        } else {
            (player?.currentPosition ?: 0L).coerceIn(0L, duration)
        }
        val splitAt = clip.startMs + localPosition
        if (localPosition < 350L || duration - localPosition < 350L) {
            status.text = "Bölmek için oynatma noktasını klibin ortalarına getir; iki tarafta da en az 0,35 saniye olmalı."
            return
        }
        clips[selectedClipIndex] = clip.copy(title = "${clip.title} • A", endMs = splitAt)
        clips.add(selectedClipIndex + 1, clip.copy(title = "${clip.title} • B", startMs = splitAt))
        selectedClipIndex += 1
        timelinePreviewLoaded = false
        refreshClipList()
        loadTrimControls()
        player?.setMediaItem(clippedMediaItem(clips[selectedClipIndex]))
        player?.prepare()
        player?.playWhenReady = false
        status.text = "Klip iki parçaya bölündü. Parçalar zaman çizelgesinde ayrı ayrı taşınabilir ve tek MP4 olarak dışa aktarılır."
    }

    private fun moveSelectedClip(delta: Int) {
        if (selectedClipIndex !in clips.indices) {
            status.text = "Önce taşınacak klibi seç."
            return
        }
        val target = selectedClipIndex + delta
        if (target !in clips.indices) {
            status.text = if (delta < 0) "Bu klip zaten ilk sırada." else "Bu klip zaten son sırada."
            return
        }
        val clip = clips.removeAt(selectedClipIndex)
        clips.add(target, clip)
        timelinePreviewLoaded = false
        selectedClipIndex = target
        refreshClipList()
        loadTrimControls()
        player?.setMediaItem(clippedMediaItem(clip))
        player?.prepare()
        player?.playWhenReady = true
        status.text = "Klip sırası değiştirildi. Dışa aktarma bu sırayı kullanacak."
    }

    private fun clippedMediaItem(clip: Clip): MediaItem =
        MediaItem.Builder()
            .setUri(clip.uri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(clip.startMs)
                    .setEndPositionMs(clip.endMs)
                    .build()
            )
            .build()

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
        updateSoundControls()
        updateTextControls()
    }

    private fun totalOutputDurationMs(): Long =
        clips.sumOf { (it.endMs - it.startMs).coerceAtLeast(0L) }

    private fun updateSoundControls() {
        if (!::soundStartSeek.isInitialized || !::soundStartLabel.isInitialized) return
        val total = totalOutputDurationMs()
        val maxSeconds = ceil(total / 1000.0).toInt().coerceAtLeast(0)
        soundStartSeek.max = maxSeconds
        soundStartMs = soundStartMs.coerceAtMost((total - 1L).coerceAtLeast(0L))
        soundStartSeek.progress = (soundStartMs / 1000L).toInt().coerceAtMost(maxSeconds)
        soundStartSeek.isEnabled = soundUri != null && total > 0L
        soundStartLabel.text = "Ses başlangıcı: ${formatMs(soundStartMs)}"
    }

    private fun previewSelectedClip() {
        timelinePreviewLoaded = false
        val clip = selectedClip()
        if (clip == null) {
            status.text = "Önizleme için önce video seç."
            return
        }
        player?.setMediaItem(clippedMediaItem(clip))
        player?.prepare()
        player?.playWhenReady = true
        status.text = "Seçili kesme aralığının önizlemesi başladı."
    }

    private fun buildClipSegment(
        clip: Clip,
        localStartMs: Long,
        localEndMs: Long
    ): EditedMediaItem {
        require(localEndMs > localStartMs) { "Boş video parçası oluşturulamaz." }
        val mediaItem = MediaItem.Builder()
            .setUri(clip.uri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(clip.startMs + localStartMs)
                    .setEndPositionMs(clip.startMs + localEndMs)
                    .build()
            )
            .build()

        val audio = if (keepSourceAudio.isChecked) audioEffects(sourceAudioVolume) else emptyList()
        return EditedMediaItem.Builder(mediaItem)
            .setEffects(Effects(audio, emptyList()))
            .apply { if (!keepSourceAudio.isChecked) setRemoveAudio(true) }
            .build()
    }

    private fun buildTimedTextOverlay(
        overlayText: String,
        startMs: Long,
        endMs: Long
    ): TextOverlay {
        val styled = SpannableString(overlayText).apply {
            setSpan(ForegroundColorSpan(Color.WHITE), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(BackgroundColorSpan(Color.argb(190, 0, 0, 0)), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(StyleSpan(Typeface.BOLD), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val y = when (textPosition) {
            TextPosition.TOP -> 0.78f
            TextPosition.CENTER -> 0f
            TextPosition.BOTTOM -> -0.78f
        }
        val settings = OverlaySettings.Builder()
            .setBackgroundFrameAnchor(0f, y)
            .setOverlayFrameAnchor(0f, 0f)
            .build()
        return object : TextOverlay() {
            override fun getText(presentationTimeUs: Long): SpannableString {
                val timeMs = presentationTimeUs / 1000L
                return if (timeMs >= startMs && timeMs < endMs) SpannableString(styled) else SpannableString("")
            }

            override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings = settings
        }
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
        val requestedExportName = exportNameInput.text?.toString().orEmpty()
        val videoItems = try {
            clips.map { clip ->
                val duration = clip.endMs - clip.startMs
                buildClipSegment(clip, 0L, duration)
            }
        } catch (e: Exception) {
            status.text = "Düzenleme hazırlanamadı: ${e.message ?: e.javaClass.simpleName}"
            return
        }

        exporting = true
        exportButton.isEnabled = false
        status.text = "MP4 dışa aktarılıyor…\nKlipler seçtiğin sırayla birleştiriliyor; yazı, müzik ve ses katmanları uygulanıyor. Uygulamayı kapatma."
        val tempOutput = File(cacheDir, "mm_editor_${System.currentTimeMillis()}.mp4")
        val videoSequence = EditedMediaItemSequence.Builder(videoItems).build()
        val compositionSequences = mutableListOf(videoSequence)
        musicUri?.let { uri ->
            val musicItem = EditedMediaItem.Builder(MediaItem.fromUri(uri))
                .setRemoveVideo(true)
                .setEffects(Effects(audioEffects(musicVolume), emptyList()))
                .build()
            compositionSequences.add(
                EditedMediaItemSequence.Builder(listOf(musicItem)).setIsLooping(true).build()
            )
        }
        soundUri?.let { uri ->
            val outputDurationMs = totalOutputDurationMs()
            val offsetMs = soundStartMs.coerceAtMost((outputDurationMs - 1L).coerceAtLeast(0L))
            val remainingMs = (outputDurationMs - offsetMs).coerceAtLeast(1L)
            val soundLengthMs = soundDurationMs.coerceAtLeast(1L).coerceAtMost(remainingMs)
            val soundMediaItem = MediaItem.Builder()
                .setUri(uri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(0L)
                        .setEndPositionMs(soundLengthMs)
                        .build()
                )
                .build()
            val soundItem = EditedMediaItem.Builder(soundMediaItem)
                .setRemoveVideo(true)
                .setEffects(Effects(audioEffects(soundVolume), emptyList()))
                .build()
            val soundSequenceBuilder = EditedMediaItemSequence.Builder()
            if (offsetMs > 0L) soundSequenceBuilder.addGap(offsetMs * 1000L)
            soundSequenceBuilder.addItem(soundItem)
            compositionSequences.add(soundSequenceBuilder.build())
        }
        val compositionBuilder = Composition.Builder(compositionSequences)
        if (overlayText.isNotBlank() && textEndMs > textStartMs) {
            compositionBuilder.setEffects(
                Effects(
                    emptyList(),
                    listOf(OverlayEffect(listOf(buildTimedTextOverlay(overlayText, textStartMs, textEndMs))))
                )
            )
        }
        val composition = compositionBuilder.build()

        val transformer = try {
            Transformer.Builder(this)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        Thread {
                            val saved = runCatching { saveToMovies(tempOutput, requestedExportName) }
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
                            status.text = "DIŞA AKTARMA BAŞARISIZ\n${exportException.message ?: "Kod ${exportException.errorCode}"}"
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

    private fun saveToMovies(source: File, requestedName: String): String {
        val cleaned = requestedName.trim().removeSuffix(".mp4").removeSuffix(".MP4")
            .replace(Regex("[^A-Za-z0-9_ -]"), "")
            .replace(Regex("\\s+"), "_")
            .trim('_', '-', ' ')
            .take(80)
        val baseName = cleaned.ifBlank { "MathMotor_Edit_${System.currentTimeMillis()}" }
        val name = if (baseName.lowercase(Locale.US).endsWith(".mp4")) baseName else "$baseName.mp4"
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

    private fun formatTimelineMs(ms: Long): String {
        val safeMs = ms.coerceAtLeast(0L)
        val totalSeconds = safeMs / 1000L
        val tenths = (safeMs % 1000L) / 100L
        return String.format(Locale.US, "%02d:%02d.%d", totalSeconds / 60L, totalSeconds % 60L, tenths)
    }

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
        timelineHandler.removeCallbacks(timelineSyncTask)
        playerView.player = null
        player?.release()
        player = null
        super.onDestroy()
    }
}

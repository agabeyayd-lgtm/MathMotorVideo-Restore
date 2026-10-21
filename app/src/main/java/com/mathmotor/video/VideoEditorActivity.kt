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
    private lateinit var clipList: LinearLayout
    private lateinit var timelineTracks: LinearLayout
    private lateinit var textTrackContent: LinearLayout
    private lateinit var musicTrackContent: LinearLayout
    private lateinit var soundTrackContent: LinearLayout
    private var onOpenTextTool: (() -> Unit)? = null
    private var onOpenAudioTool: (() -> Unit)? = null
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
        val incoming = intent.getStringExtra("video_uri")
        if (!incoming.isNullOrBlank()) addClip(Uri.parse(incoming), false)
    }

    private fun buildUi() {
        val bg = Color.rgb(14, 16, 22)
        val panelBg = Color.rgb(25, 29, 38)
        val muted = Color.rgb(165, 174, 190)
        val accent = Color.rgb(0, 202, 170)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(4), dp(10), dp(6))
            setBackgroundColor(bg)
        }

        // Compact editor header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }
        val titleBlock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleBlock.addView(label("MathMotor Studio", 20f, Color.WHITE, true))
        titleBlock.addView(label("VİDEO DÜZENLE", 10f, muted, true))
        header.addView(titleBlock, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val back = Button(this).apply {
            text = "Kapat"
            isAllCaps = false
            textSize = 12f
            setTextColor(Color.WHITE)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.rgb(44, 49, 61))
                cornerRadius = dp(12).toFloat()
            }
            setOnClickListener { finish() }
        }
        header.addView(back, LinearLayout.LayoutParams(dp(74), dp(40)))
        root.addView(header)

        // Video monitor
        playerView = PlayerView(this).apply {
            setBackgroundColor(Color.BLACK)
            useController = true
        }
        root.addView(playerView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(205)).apply {
            topMargin = dp(4)
            bottomMargin = dp(6)
        })

        // A clear, multi-track timeline with one-tap add actions.
        val timelineHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(3), dp(2), dp(3))
        }
        timelineHeader.addView(label("ZAMAN ÇİZELGESİ", 14f, accent, true),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        timelineHeader.addView(label("Eklediklerin aşağıda görünür", 10f, muted, false))
        root.addView(timelineHeader)

        val quickActionScroll = android.widget.HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        val quickActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        fun timelineAction(title: String, color: Int, action: () -> Unit) {
            val button = Button(this).apply {
                text = title
                isAllCaps = false
                textSize = 11f
                setTextColor(if (color == Color.rgb(0, 202, 170)) Color.BLACK else Color.WHITE)
                minHeight = dp(38)
                setPadding(dp(7), dp(2), dp(7), dp(2))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(color)
                    cornerRadius = dp(9).toFloat()
                }
                setOnClickListener { action() }
            }
            quickActions.addView(button, LinearLayout.LayoutParams(dp(88), dp(40)).apply {
                marginEnd = dp(5)
            })
        }
        timelineAction("+ Video", Color.rgb(0, 202, 170)) { chooseVideo(false) }
        timelineAction("+ Klip", Color.rgb(49, 112, 133)) { chooseVideo(true) }
        timelineAction("+ Metin", Color.rgb(89, 82, 156)) {
            onOpenTextTool?.invoke() ?: run { status.text = "Metin aracı açılmadı." }
        }
        timelineAction("+ Müzik", Color.rgb(49, 112, 133)) { chooseAudio(true) }
        timelineAction("+ Ses efekti", Color.rgb(49, 112, 133)) { chooseAudio(false) }
        quickActionScroll.addView(quickActions, android.widget.HorizontalScrollView.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        root.addView(quickActionScroll)

        timelineTracks = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(panelBg)
                cornerRadius = dp(11).toFloat()
                setStroke(dp(1), Color.rgb(46, 53, 66))
            }
        }
        clipList = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minHeight = dp(40)
        }
        textTrackContent = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minHeight = dp(40)
        }
        musicTrackContent = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minHeight = dp(40)
        }
        soundTrackContent = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minHeight = dp(40)
        }
        addTimelineLane(timelineTracks, "VIDEO", clipList, Color.rgb(0, 202, 170))
        addTimelineLane(timelineTracks, "METİN", textTrackContent, Color.rgb(177, 139, 255))
        addTimelineLane(timelineTracks, "MÜZİK", musicTrackContent, Color.rgb(90, 175, 255))
        addTimelineLane(timelineTracks, "SES", soundTrackContent, Color.rgb(255, 174, 84))
        root.addView(timelineTracks, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(3)
            bottomMargin = dp(4)
        })

        // Tool navigation
        val toolBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(6), 0, dp(6))
        }
        root.addView(toolBar)

        val panelScroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            setBackgroundColor(panelBg)
        }
        val panelStack = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(14))
        }
        panelScroll.addView(panelStack)
        root.addView(panelScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val panels = mutableListOf<LinearLayout>()
        fun newPanel(): LinearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            panels.add(this)
            panelStack.addView(this, matchWrap())
        }
        fun sectionHeading(text: String) {
            val heading = label(text, 15f, Color.WHITE, true)
            heading.setPadding(0, dp(2), 0, dp(8))
            heading.background = null
            val line = View(this).apply { setBackgroundColor(accent) }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(heading)
                addView(line, LinearLayout.LayoutParams(dp(42), dp(3)).apply { bottomMargin = dp(10) })
            }
            // Keep the accent underline visually tied to each tool section.
            activePanelForHeading?.addView(row)
        }
        activePanelForHeading = null

        // Clip / trim panel
        val clipPanel = newPanel()
        activePanelForHeading = clipPanel
        sectionHeading("KLİP DÜZENLE")
        selectedClipLabel = label("Önce + Video düğmesine dokun.", 13f, Color.WHITE, true)
        clipPanel.addView(selectedClipLabel)
        val clipActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        clipActions.addView(compactButton("▶ Önizle") { previewSelectedClip() })
        clipActions.addView(compactButton("Sil") { removeSelectedClip() })
        clipActions.addView(compactButton("← Öne al") { moveSelectedClip(-1) })
        clipActions.addView(compactButton("Sona →") { moveSelectedClip(1) })
        clipPanel.addView(clipActions)
        startLabel = label("Başlangıç: 00:00", 12f, Color.WHITE, true)
        clipPanel.addView(startLabel)
        startSeek = SeekBar(this)
        clipPanel.addView(startSeek, matchWrap())
        endLabel = label("Bitiş: 00:00", 12f, Color.WHITE, true)
        clipPanel.addView(endLabel)
        endSeek = SeekBar(this)
        clipPanel.addView(endSeek, matchWrap())
        installTrimListeners()

        // Text panel
        val textPanel = newPanel()
        activePanelForHeading = textPanel
        sectionHeading("METİN")
        textInput = EditText(this).apply {
            hint = "Videoda görünecek yazıyı yaz"
            setTextColor(Color.WHITE)
            setHintTextColor(muted)
            setSingleLine(false)
            minLines = 1
            maxLines = 3
            setPadding(dp(12), dp(9), dp(12), dp(9))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.rgb(37, 42, 53))
                cornerRadius = dp(10).toFloat()
                setStroke(dp(1), Color.rgb(68, 77, 94))
            }
        }
        textPanel.addView(textInput, matchWrap())
        textInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                refreshTimelineTracks()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        textPanel.addView(label("Metnin ekranda kalacağı süre", 12f, muted, false))
        textStartLabel = label("Başlangıç: 00:00", 12f, Color.WHITE, true)
        textPanel.addView(textStartLabel)
        textStartSeek = SeekBar(this).apply { max = 0; isEnabled = false }
        textPanel.addView(textStartSeek, matchWrap())
        textEndLabel = label("Bitiş: 00:00", 12f, Color.WHITE, true)
        textPanel.addView(textEndLabel)
        textEndSeek = SeekBar(this).apply { max = 0; isEnabled = false }
        textPanel.addView(textEndSeek, matchWrap())
        installTextTimeListeners()
        textPanel.addView(label("Metin konumu", 12f, muted, false))
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
            }
        }
        textPanel.addView(textPositionGroup, matchWrap())

        // Music / sound panel
        val audioPanel = newPanel()
        activePanelForHeading = audioPanel
        sectionHeading("MÜZİK VE SES")
        musicLabel = label("Müzik seçilmedi.", 12f, muted, false)
        audioPanel.addView(musicLabel)
        audioPanel.addView(actionButton("+ Müzik ekle (video boyunca)") { chooseAudio(true) })
        audioPanel.addView(actionButton("Müziği kaldır") {
            musicUri = null
            musicTitle = null
            musicLabel.text = "Müzik seçilmedi."
            refreshTimelineTracks()
        })
        musicVolumeLabel = label("Müzik ses seviyesi: 45%", 12f, Color.WHITE, true)
        audioPanel.addView(musicVolumeLabel)
        musicVolumeSeek = SeekBar(this).apply { max = 100; progress = musicVolume }
        musicVolumeSeek.setOnSeekBarChangeListener(volumeListener { value ->
            musicVolume = value
            musicVolumeLabel.text = "Müzik ses seviyesi: " + value + "%"
        })
        audioPanel.addView(musicVolumeSeek, matchWrap())
        audioPanel.addView(View(this).apply { setBackgroundColor(Color.rgb(55, 62, 76)) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
                topMargin = dp(10); bottomMargin = dp(8)
            })
        soundLabel = label("Ses efekti seçilmedi.", 12f, muted, false)
        audioPanel.addView(soundLabel)
        audioPanel.addView(actionButton("+ Ses efekti ekle") { chooseAudio(false) })
        soundStartLabel = label("Ses başlangıcı: 00:00", 12f, Color.WHITE, true)
        audioPanel.addView(soundStartLabel)
        soundStartSeek = SeekBar(this).apply { max = 0; isEnabled = false }
        soundStartSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val total = totalOutputDurationMs()
                soundStartMs = (progress * 1000L).coerceAtMost((total - 1L).coerceAtLeast(0L))
                updateSoundControls()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        audioPanel.addView(soundStartSeek, matchWrap())
        soundVolumeLabel = label("Ses efekti seviyesi: 100%", 12f, Color.WHITE, true)
        audioPanel.addView(soundVolumeLabel)
        soundVolumeSeek = SeekBar(this).apply { max = 100; progress = soundVolume }
        soundVolumeSeek.setOnSeekBarChangeListener(volumeListener { value ->
            soundVolume = value
            soundVolumeLabel.text = "Ses/efekt seviyesi: " + value + "%"
        })
        audioPanel.addView(soundVolumeSeek, matchWrap())
        audioPanel.addView(actionButton("Ses efektini kaldır") {
            soundUri = null
            soundTitle = null
            soundDurationMs = 0L
            soundStartMs = 0L
            soundLabel.text = "Ses efekti seçilmedi."
            updateSoundControls()
            refreshTimelineTracks()
        })
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

        // Project and export panel
        val projectPanel = newPanel()
        activePanelForHeading = projectPanel
        sectionHeading("PROJE VE DIŞA AKTAR")
        exportNameInput = EditText(this).apply {
            setText("MathMotor_Edit")
            hint = "Videonun dosya adı"
            setTextColor(Color.WHITE)
            setHintTextColor(muted)
            setSingleLine(true)
            setPadding(dp(12), dp(9), dp(12), dp(9))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.rgb(37, 42, 53))
                cornerRadius = dp(10).toFloat()
                setStroke(dp(1), Color.rgb(68, 77, 94))
            }
        }
        projectPanel.addView(label("Dışa aktarılacak videonun adı", 12f, muted, false))
        projectPanel.addView(exportNameInput, matchWrap())
        projectPanel.addView(actionButton("Projeyi farklı kaydet (.JSON)") { requestSaveProject() })
        projectPanel.addView(actionButton("Kayıtlı projeyi aç") { requestOpenProject() })
        exportButton = actionButton("MP4 OLARAK DIŞA AKTAR") { exportEditedVideo() }.apply {
            textSize = 15f
            minHeight = dp(52)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(accent)
                cornerRadius = dp(12).toFloat()
            }
        }
        projectPanel.addView(exportButton)

        // Tabs remain visible while each tool's controls can scroll independently.
        val tabs = mutableListOf<Button>()
        fun addTab(name: String, target: LinearLayout) {
            val button = Button(this).apply {
                text = name
                isAllCaps = false
                textSize = 11f
                setTextColor(Color.WHITE)
                minHeight = dp(46)
                setPadding(dp(2), dp(3), dp(2), dp(3))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.rgb(42, 47, 59))
                    cornerRadius = dp(9).toFloat()
                }
            }
            toolBar.addView(button, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                marginStart = dp(2); marginEnd = dp(2)
            })
            tabs.add(button)
            button.setOnClickListener {
                panels.forEach { it.visibility = if (it === target) View.VISIBLE else View.GONE }
                tabs.forEach { tab ->
                    tab.background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(if (tab === button) accent else Color.rgb(42, 47, 59))
                        cornerRadius = dp(9).toFloat()
                    }
                    tab.setTextColor(if (tab === button) Color.BLACK else Color.WHITE)
                }
                panelScroll.scrollTo(0, 0)
            }
        }
        addTab("Klip / Kes", clipPanel)
        addTab("Metin", textPanel)
        addTab("Ses", audioPanel)
        addTab("Kaydet", projectPanel)
        onOpenTextTool = { tabs[1].performClick() }
        onOpenAudioTool = { tabs[2].performClick() }
        panels.forEach { it.visibility = View.GONE }
        clipPanel.visibility = View.VISIBLE
        tabs.first().performClick()

        status = label("Hazır • Önce + Video ile başla.", 11f, Color.WHITE, false)
        status.maxLines = 2
        status.setPadding(dp(8), dp(5), dp(8), dp(5))
        status.setBackgroundColor(Color.rgb(31, 36, 46))
        root.addView(status, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(5)
        })

        setContentView(root)
    }

    // Reference used only while constructing the tool sections.
    private var activePanelForHeading: LinearLayout? = null

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
            REQUEST_MUSIC -> {
                musicUri = uri
                musicTitle = queryDisplayName(uri) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Müzik"
                musicLabel.text = "Müzik: ${musicTitle} • video boyunca döngü"
                refreshTimelineTracks()
                status.text = "Müzik zaman çizelgesine eklendi. Video süresi boyunca çalar."
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
                            status.text = "Ses katmanı eklendi. Başlama saniyesini aşağıdaki kaydırıcıdan ayarla."
                        }
                    }
                }.start()
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
            addTimelineChip(clipList, "+ Video ekle", Color.rgb(0, 202, 170), Color.BLACK) {
                chooseVideo(false)
            }
        } else {
            clips.forEachIndexed { index, clip ->
                val selected = index == selectedClipIndex
                val duration = formatMs(clip.endMs - clip.startMs)
                addTimelineChip(
                    clipList,
                    (if (selected) "▶ " else "") + "${index + 1}. ${clip.title}\n$duration",
                    if (selected) Color.rgb(0, 202, 170) else Color.rgb(45, 91, 111),
                    if (selected) Color.BLACK else Color.WHITE
                ) {
                    selectedClipIndex = index
                    refreshClipList()
                    loadTrimControls()
                    player?.setMediaItem(clippedMediaItem(clip))
                    player?.prepare()
                    player?.playWhenReady = true
                    status.text = "Seçili klip: ${clip.title}"
                }
            }
        }
        refreshTimelineTracks()
    }

    private fun addTimelineLane(
        host: LinearLayout,
        laneName: String,
        content: LinearLayout,
        labelColor: Int
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.rgb(30, 35, 45))
                cornerRadius = dp(7).toFloat()
            }
        }
        val name = TextView(this).apply {
            text = laneName
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(labelColor)
            setPadding(dp(3), dp(3), dp(3), dp(3))
        }
        row.addView(name, LinearLayout.LayoutParams(dp(57), dp(44)))
        val laneScroll = android.widget.HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            setPadding(dp(3), dp(2), dp(3), dp(2))
            clipToPadding = false
        }
        laneScroll.addView(content, android.widget.HorizontalScrollView.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
        row.addView(laneScroll, LinearLayout.LayoutParams(0, dp(44), 1f))
        host.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply {
            bottomMargin = dp(3)
        })
    }

    private fun addTimelineChip(
        parent: LinearLayout,
        title: String,
        fill: Int,
        textColor: Int = Color.WHITE,
        action: () -> Unit
    ) {
        val chip = Button(this).apply {
            text = title
            isAllCaps = false
            isSingleLine = false
            textSize = 10f
            gravity = Gravity.CENTER
            setTextColor(textColor)
            minHeight = dp(34)
            setPadding(dp(8), dp(1), dp(8), dp(1))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(fill)
                cornerRadius = dp(7).toFloat()
            }
            setOnClickListener { action() }
        }
        parent.addView(chip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)).apply {
            marginStart = dp(2)
            marginEnd = dp(4)
        })
    }

    private fun refreshTimelineTracks() {
        if (!::textTrackContent.isInitialized ||
            !::musicTrackContent.isInitialized ||
            !::soundTrackContent.isInitialized
        ) return

        textTrackContent.removeAllViews()
        val overlayText = if (::textInput.isInitialized) textInput.text?.toString()?.trim().orEmpty() else ""
        if (overlayText.isBlank()) {
            addTimelineChip(textTrackContent, "+ Metin ekle", Color.rgb(89, 82, 156)) {
                onOpenTextTool?.invoke()
            }
        } else {
            val shownText = if (overlayText.length > 24) overlayText.take(24) + "…" else overlayText
            addTimelineChip(
                textTrackContent,
                "T  $shownText  ·  ${formatMs(textStartMs)}–${formatMs(textEndMs)}",
                Color.rgb(116, 87, 178)
            ) {
                onOpenTextTool?.invoke()
            }
        }

        musicTrackContent.removeAllViews()
        if (musicUri == null) {
            addTimelineChip(musicTrackContent, "+ Müzik ekle", Color.rgb(42, 92, 132)) {
                chooseAudio(true)
            }
        } else {
            val title = musicTitle?.take(20) ?: "Müzik"
            addTimelineChip(
                musicTrackContent,
                "♪  $title  ·  00:00–${formatMs(totalOutputDurationMs())}",
                Color.rgb(37, 101, 149)
            ) {
                onOpenAudioTool?.invoke()
            }
        }

        soundTrackContent.removeAllViews()
        if (soundUri == null) {
            addTimelineChip(soundTrackContent, "+ Ses efekti ekle", Color.rgb(130, 82, 40)) {
                chooseAudio(false)
            }
        } else {
            val title = soundTitle?.take(20) ?: "Ses efekti"
            addTimelineChip(
                soundTrackContent,
                "♫  $title  ·  ${formatMs(soundStartMs)}",
                Color.rgb(154, 98, 46)
            ) {
                onOpenAudioTool?.invoke()
            }
        }
    }

    private fun removeSelectedClip() {
        if (selectedClipIndex !in clips.indices) {
            status.text = "Önce kaldırılacak klibi seç."
            return
        }
        clips.removeAt(selectedClipIndex)
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
        localEndMs: Long,
        overlayText: String?
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

        val videoEffects = if (overlayText.isNullOrBlank()) {
            emptyList()
        } else {
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
            listOf(OverlayEffect(listOf(TextOverlay.createStaticTextOverlay(styled, settings))))
        }
        val audio = if (keepSourceAudio.isChecked) audioEffects(sourceAudioVolume) else emptyList()
        return EditedMediaItem.Builder(mediaItem)
            .setEffects(Effects(audio, videoEffects))
            .apply { if (!keepSourceAudio.isChecked) setRemoveAudio(true) }
            .build()
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
            val builtItems = mutableListOf<EditedMediaItem>()
            var timelineOffsetMs = 0L
            clips.forEach { clip ->
                val duration = clip.endMs - clip.startMs
                val localTextStart = (textStartMs - timelineOffsetMs).coerceAtLeast(0L)
                val localTextEnd = (textEndMs - timelineOffsetMs).coerceAtMost(duration)
                val showTextHere = overlayText.isNotBlank() && localTextEnd > localTextStart
                if (!showTextHere) {
                    builtItems.add(buildClipSegment(clip, 0L, duration, null))
                } else {
                    if (localTextStart > 0L) builtItems.add(buildClipSegment(clip, 0L, localTextStart, null))
                    builtItems.add(buildClipSegment(clip, localTextStart, localTextEnd, overlayText))
                    if (localTextEnd < duration) builtItems.add(buildClipSegment(clip, localTextEnd, duration, null))
                }
                timelineOffsetMs += duration
            }
            builtItems
        } catch (e: Exception) {
            status.text = "Düzenleme hazırlanamadı: ${e.message ?: e.javaClass.simpleName}"
            return
        }

        exporting = true
        exportButton.isEnabled = false
        status.text = "MP4 dışa aktarılıyor…\nKlipler, yazı, müzik ve ses katmanları uygulanıyor. Uygulamayı kapatma."
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
        val composition = Composition.Builder(compositionSequences).build()

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

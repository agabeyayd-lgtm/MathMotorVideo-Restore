package com.mathmotor.video

import android.app.Activity
import android.content.ContentValues
import android.content.ClipData
import android.content.Intent
import android.database.Cursor
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Environment
import android.provider.MediaStore
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.DragEvent
import android.widget.FrameLayout
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.AdapterView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.DefaultGainProvider
import androidx.media3.common.audio.GainProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
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
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@OptIn(UnstableApi::class)
class VideoEditorActivity : Activity() {
    private data class Clip(
        val uri: Uri,
        val title: String,
        val durationMs: Long,
        var startMs: Long = 0L,
        var endMs: Long = durationMs,
        val frameRate: Float = 30f
    )
    private data class TextLayer(
        val id: Long,
        var text: String,
        var startMs: Long,
        var endMs: Long,
        var position: TextPosition
    )
    private data class AudioLayer(
        val id: Long,
        val uri: Uri,
        var title: String,
        var durationMs: Long,
        var startMs: Long,
        var volume: Int,
        var fadeInMs: Long = 0L,
        var fadeOutMs: Long = 0L
    )

    private val clips = mutableListOf<Clip>()
    private val textLayers = mutableListOf<TextLayer>()
    private val musicLayers = mutableListOf<AudioLayer>()
    private val soundLayers = mutableListOf<AudioLayer>()
    private var selectedTextLayerId: Long? = null
    private var selectedMusicLayerId: Long? = null
    private var selectedSoundLayerId: Long? = null
    private var nextLayerId = 1L
    private var suppressLayerControlCallbacks = false
    private val audioPreviewPlayers = mutableMapOf<Long, ExoPlayer>()
    private val audioPreviewSources = mutableMapOf<Long, Uri>()
    private val thumbnailCache = object : LruCache<String, Bitmap>(6 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
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
    private var previewTimelineOffsetMs = 0L
    private var previewingSequence = false
    private val previewHandler = Handler(Looper.getMainLooper())
    private val previewTicker = object : Runnable {
        override fun run() {
            refreshPreviewOverlay()
            updatePlayheadUi(currentTimelinePositionMs())
            syncAudioPreviews()
            previewHandler.postDelayed(this, 100L)
        }
    }
    private lateinit var playerView: PlayerView
    private lateinit var previewTextOverlay: TextView
    private lateinit var clipList: LinearLayout
    private lateinit var timelineTracks: LinearLayout
    private lateinit var timelineRulerContent: LinearLayout
    private val timelineScrollViews = mutableListOf<android.widget.HorizontalScrollView>()
    private var syncingTimelineScroll = false
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
    private lateinit var playheadSeek: SeekBar
    private lateinit var playheadLabel: TextView
    private lateinit var undoButton: Button
    private lateinit var redoButton: Button
    private lateinit var aspectSpinner: Spinner
    private lateinit var resolutionSpinner: Spinner
    private val undoStates = mutableListOf<String>()
    private val redoStates = mutableListOf<String>()
    private var restoringHistory = false
    private var updatingPlayhead = false
    private val aspectOptions = listOf("9:16 Dikey", "16:9 Yatay", "1:1 Kare")
    private val resolutionOptions = listOf("480p", "720p", "1080p")
    private var outputAspectIndex = 0
    private var outputResolutionIndex = 1
    private lateinit var musicLabel: TextView
    private lateinit var soundLabel: TextView
    private lateinit var soundStartLabel: TextView
    private lateinit var soundStartSeek: SeekBar
    private lateinit var soundVolumeLabel: TextView
    private lateinit var soundVolumeSeek: SeekBar
    private lateinit var musicFadeInSeek: SeekBar
    private lateinit var musicFadeOutSeek: SeekBar
    private lateinit var soundFadeInSeek: SeekBar
    private lateinit var soundFadeOutSeek: SeekBar
    private lateinit var musicFadeLabel: TextView
    private lateinit var soundFadeLabel: TextView
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
    private lateinit var textLayerList: LinearLayout
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
        previewHandler.post(previewTicker)
        val incoming = intent.getStringExtra("video_uri")
        if (!incoming.isNullOrBlank()) addClip(Uri.parse(incoming), false)
    }

    private fun buildUi() {
        val bg = Color.rgb(14, 16, 22)
        val panelBg = Color.rgb(25, 29, 38)
        val muted = Color.rgb(165, 174, 190)
        val accent = Color.rgb(0, 202, 170)

        fun compactButton(value: String, action: () -> Unit): Button = actionButton(value, action).apply {
            textSize = 12f
            minHeight = dp(40)
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                marginStart = dp(3)
                marginEnd = dp(3)
            }
        }

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
        undoButton = Button(this).apply {
            text = "↶"
            contentDescription = "Geri al"
            isAllCaps = false
            textSize = 21f
            setTextColor(Color.WHITE)
            minWidth = dp(42)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.rgb(44, 49, 61))
                cornerRadius = dp(10).toFloat()
            }
            setOnClickListener { undoLastChange() }
        }
        header.addView(undoButton, LinearLayout.LayoutParams(dp(42), dp(40)).apply { marginEnd = dp(4) })
        redoButton = Button(this).apply {
            text = "↷"
            contentDescription = "Yinele"
            isAllCaps = false
            textSize = 21f
            setTextColor(Color.WHITE)
            minWidth = dp(42)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.rgb(44, 49, 61))
                cornerRadius = dp(10).toFloat()
            }
            setOnClickListener { redoLastChange() }
        }
        header.addView(redoButton, LinearLayout.LayoutParams(dp(42), dp(40)).apply { marginEnd = dp(4) })
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
        previewTextOverlay = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 21f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            maxLines = 3
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.argb(205, 0, 0, 0))
                cornerRadius = dp(7).toFloat()
            }
            visibility = View.GONE
        }
        playerView.overlayFrameLayout?.addView(
            previewTextOverlay,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                leftMargin = dp(12)
                rightMargin = dp(12)
                bottomMargin = dp(14)
                topMargin = dp(14)
            }
        )
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
        quickActionScroll.addView(quickActions, ViewGroup.LayoutParams(
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
        timelineRulerContent = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(34)
        }
        clipList = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(34)
        }
        textTrackContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(34)
        }
        musicTrackContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(34)
        }
        soundTrackContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(34)
        }
        timelineScrollViews.clear()
        addTimelineLane(timelineTracks, "SÜRE", timelineRulerContent, Color.rgb(165, 174, 190))
        addTimelineLane(timelineTracks, "VIDEO", clipList, Color.rgb(0, 202, 170))
        addTimelineLane(timelineTracks, "METİN", textTrackContent, Color.rgb(177, 139, 255))
        addTimelineLane(timelineTracks, "MÜZİK", musicTrackContent, Color.rgb(90, 175, 255))
        addTimelineLane(timelineTracks, "SES", soundTrackContent, Color.rgb(255, 174, 84))
        root.addView(timelineTracks, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(3)
            bottomMargin = dp(4)
        })

        // One playhead controls the time shown in the preview and in every timeline lane.
        val playheadRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), 0, dp(2), dp(2))
        }
        playheadLabel = label("00:00.000 / 00:00.000", 11f, Color.WHITE, true)
        playheadRow.addView(playheadLabel, LinearLayout.LayoutParams(dp(115), ViewGroup.LayoutParams.WRAP_CONTENT))
        playheadSeek = SeekBar(this).apply {
            max = 10000
            progress = 0
            isEnabled = false
        }
        playheadRow.addView(playheadSeek, LinearLayout.LayoutParams(0, dp(36), 1f))
        root.addView(playheadRow)
        playheadSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser || updatingPlayhead) return
                val total = totalOutputDurationMs()
                val target = (total * progress.toLong() / 10000L).coerceIn(0L, total)
                playheadLabel.text = "${formatPreciseMs(target)} / ${formatPreciseMs(total)}"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                if (seekBar != null) {
                    val total = totalOutputDurationMs()
                    val target = total * seekBar.progress.toLong() / 10000L
                    seekToTimelineTime(target, preservePlayback = true)
                }
            }
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
        clipActions.addView(compactButton("▶ Tümünü izle") { previewSelectedClip() })
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
        clipPanel.addView(actionButton("✂ BÖL • Oynatma noktasında") { splitSelectedClipAtPlayhead() })
        clipPanel.addView(label("Kesme hassasiyeti: kare kare", 11f, muted, false))
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
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                if (!suppressLayerControlCallbacks) recordUndoState()
            }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!suppressLayerControlCallbacks && !restoringHistory) {
                    val active = textLayers.firstOrNull { it.id == selectedTextLayerId }
                    if (active != null) {
                        active.text = s?.toString().orEmpty()
                    } else if (!s.isNullOrBlank()) {
                        val layer = TextLayer(nextLayerId++, s.toString(), textStartMs, textEndMs.coerceAtLeast(totalOutputDurationMs()), textPosition)
                        textLayers.add(layer)
                        selectedTextLayerId = layer.id
                    }
                }
                refreshTextLayerList()
                refreshTimelineTracks()
                refreshPreviewOverlay()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        val textLayerHeader = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        textLayerHeader.addView(label("METİN KATMANLARI", 12f, muted, true),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        textLayerHeader.addView(actionButton("+ Yeni metin") { addTextLayer() }.apply {
            textSize = 11f
            minHeight = dp(38)
            layoutParams = LinearLayout.LayoutParams(dp(125), dp(38))
        })
        textPanel.addView(textLayerHeader)
        textLayerList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(3), dp(4), dp(3))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.rgb(31, 36, 46))
                cornerRadius = dp(8).toFloat()
            }
        }
        textPanel.addView(textLayerList, matchWrap())
        textPanel.addView(actionButton("Seçili metin katmanını sil") { removeSelectedTextLayer() })
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
                if (!suppressLayerControlCallbacks) {
                    recordUndoState()
                    textLayers.firstOrNull { it.id == selectedTextLayerId }?.position = textPosition
                    refreshTextLayerList()
                    refreshTimelineTracks()
                }
                refreshPreviewOverlay()
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
        audioPanel.addView(actionButton("Seçili müziği kaldır") {
            removeSelectedAudioLayer(isMusic = true)
        })
        musicVolumeLabel = label("Müzik ses seviyesi: 45%", 12f, Color.WHITE, true)
        audioPanel.addView(musicVolumeLabel)
        musicVolumeSeek = SeekBar(this).apply { max = 100; progress = musicVolume }
        musicVolumeSeek.setOnSeekBarChangeListener(volumeListener { value ->
            musicVolume = value
            musicVolumeLabel.text = "Müzik ses seviyesi: " + value + "%"
            musicLayers.firstOrNull { it.id == selectedMusicLayerId }?.volume = value
            audioPreviewPlayers.values.forEach { it.volume = value / 100f }
            refreshTimelineTracks()
        })
        audioPanel.addView(musicVolumeSeek, matchWrap())
        musicFadeLabel = label("Müziğin açılma / kısılma süresi: 0 sn / 0 sn", 11f, Color.WHITE, true)
        audioPanel.addView(musicFadeLabel)
        musicFadeInSeek = SeekBar(this).apply { max = 10; progress = 0 }
        musicFadeOutSeek = SeekBar(this).apply { max = 10; progress = 0 }
        musicFadeInSeek.setOnSeekBarChangeListener(audioFadeListener(true, true))
        musicFadeOutSeek.setOnSeekBarChangeListener(audioFadeListener(true, false))
        audioPanel.addView(label("Müzik açılma süresi (sn)", 11f, muted, false))
        audioPanel.addView(musicFadeInSeek, matchWrap())
        audioPanel.addView(label("Müzik kısılma süresi (sn)", 11f, muted, false))
        audioPanel.addView(musicFadeOutSeek, matchWrap())
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
                soundLayers.firstOrNull { it.id == selectedSoundLayerId }?.startMs = soundStartMs
                updateSoundControls()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) { recordUndoState() }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        audioPanel.addView(soundStartSeek, matchWrap())
        soundVolumeLabel = label("Ses efekti seviyesi: 100%", 12f, Color.WHITE, true)
        audioPanel.addView(soundVolumeLabel)
        soundVolumeSeek = SeekBar(this).apply { max = 100; progress = soundVolume }
        soundVolumeSeek.setOnSeekBarChangeListener(volumeListener { value ->
            soundVolume = value
            soundVolumeLabel.text = "Ses/efekt seviyesi: " + value + "%"
            soundLayers.firstOrNull { it.id == selectedSoundLayerId }?.volume = value
            audioPreviewPlayers.values.forEach { it.volume = value / 100f }
            refreshTimelineTracks()
        })
        audioPanel.addView(soundVolumeSeek, matchWrap())
        soundFadeLabel = label("Efektin açılma / kısılma süresi: 0 sn / 0 sn", 11f, Color.WHITE, true)
        audioPanel.addView(soundFadeLabel)
        soundFadeInSeek = SeekBar(this).apply { max = 10; progress = 0 }
        soundFadeOutSeek = SeekBar(this).apply { max = 10; progress = 0 }
        soundFadeInSeek.setOnSeekBarChangeListener(audioFadeListener(false, true))
        soundFadeOutSeek.setOnSeekBarChangeListener(audioFadeListener(false, false))
        audioPanel.addView(label("Efekt açılma süresi (sn)", 11f, muted, false))
        audioPanel.addView(soundFadeInSeek, matchWrap())
        audioPanel.addView(label("Efekt kısılma süresi (sn)", 11f, muted, false))
        audioPanel.addView(soundFadeOutSeek, matchWrap())
        audioPanel.addView(actionButton("Seçili ses efektini kaldır") {
            removeSelectedAudioLayer(isMusic = false)
        })
        keepSourceAudio = CheckBox(this).apply {
            text = "Videonun kendi sesini koru"
            isChecked = true
            setTextColor(Color.WHITE)
            setOnCheckedChangeListener { _, checked ->
                player?.volume = if (checked) sourceAudioVolume / 100f else 0f
            }
        }
        audioPanel.addView(keepSourceAudio)
        sourceVolumeLabel = label("Orijinal video sesi: 100%", 12f, Color.WHITE, true)
        audioPanel.addView(sourceVolumeLabel)
        sourceVolumeSeek = SeekBar(this).apply { max = 100; progress = sourceAudioVolume }
        sourceVolumeSeek.setOnSeekBarChangeListener(volumeListener { value ->
            sourceAudioVolume = value
            sourceVolumeLabel.text = "Orijinal video sesi: " + value + "%"
            player?.volume = if (keepSourceAudio.isChecked) value / 100f else 0f
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
        projectPanel.addView(label("Video biçimi", 12f, muted, false))
        aspectSpinner = Spinner(this)
        val aspectAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, aspectOptions).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        aspectSpinner.adapter = aspectAdapter
        aspectSpinner.setSelection(outputAspectIndex, false)
        projectPanel.addView(aspectSpinner, matchWrap())
        projectPanel.addView(label("Çözünürlük", 12f, muted, false))
        resolutionSpinner = Spinner(this)
        val resolutionAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, resolutionOptions).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        resolutionSpinner.adapter = resolutionAdapter
        resolutionSpinner.setSelection(outputResolutionIndex, false)
        projectPanel.addView(resolutionSpinner, matchWrap())
        aspectSpinner.onItemSelectedListener = simpleSelectionListener {
            if (!restoringHistory && outputAspectIndex != it) recordUndoState()
            outputAspectIndex = it.coerceIn(0, aspectOptions.lastIndex)
        }
        resolutionSpinner.onItemSelectedListener = simpleSelectionListener {
            if (!restoringHistory && outputResolutionIndex != it) recordUndoState()
            outputResolutionIndex = it.coerceIn(0, resolutionOptions.lastIndex)
        }
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
        updateUndoRedoButtons()
        updatePlayheadUi(0L)
        refreshTextLayerList()
        refreshTimelineTracks()
    }

    // Reference used only while constructing the tool sections.
    private var activePanelForHeading: LinearLayout? = null

    private fun addTextLayer() {
        if (clips.isEmpty()) {
            status.text = "Önce video ekle; sonra metin katmanı oluştur."
            return
        }
        recordUndoState()
        val total = totalOutputDurationMs().coerceAtLeast(1L)
        val layer = TextLayer(nextLayerId++, "", 0L, total, TextPosition.BOTTOM)
        textLayers.add(layer)
        selectedTextLayerId = layer.id
        loadTextLayerControls(layer)
        onOpenTextTool?.invoke()
        status.text = "Yeni metin katmanı eklendi. Yazını yukarıya yaz."
    }

    private fun removeSelectedTextLayer() {
        val layer = textLayers.firstOrNull { it.id == selectedTextLayerId }
        if (layer == null) {
            status.text = "Önce silinecek metin katmanını seç."
            return
        }
        recordUndoState()
        val index = textLayers.indexOf(layer)
        textLayers.remove(layer)
        selectedTextLayerId = textLayers.getOrNull((index - 1).coerceAtLeast(0))?.id
        loadTextLayerControls(textLayers.firstOrNull { it.id == selectedTextLayerId })
        refreshTextLayerList()
        refreshTimelineTracks()
        refreshPreviewOverlay()
        status.text = "Metin katmanı silindi."
    }

    private fun selectTextLayer(layer: TextLayer) {
        if (selectedTextLayerId == layer.id) {
            onOpenTextTool?.invoke()
            return
        }
        syncSelectedTextLayerFromControls()
        selectedTextLayerId = layer.id
        loadTextLayerControls(layer)
        onOpenTextTool?.invoke()
        status.text = "Düzenlenen metin: ${layer.text.ifBlank { "(boş metin)" }}"
    }

    private fun loadTextLayerControls(layer: TextLayer?) {
        suppressLayerControlCallbacks = true
        selectedTextLayerId = layer?.id
        textInput.setText(layer?.text.orEmpty())
        textStartMs = layer?.startMs ?: 0L
        textEndMs = layer?.endMs ?: totalOutputDurationMs()
        textPosition = layer?.position ?: TextPosition.BOTTOM
        textTimingCustomized = layer != null
        textPositionGroup.check(when (textPosition) {
            TextPosition.TOP -> 1
            TextPosition.CENTER -> 2
            TextPosition.BOTTOM -> 3
        })
        updateTextControls()
        suppressLayerControlCallbacks = false
        refreshTextLayerList()
        refreshTimelineTracks()
        refreshPreviewOverlay()
    }

    private fun syncSelectedTextLayerFromControls() {
        if (suppressLayerControlCallbacks || restoringHistory) return
        val text = textInput.text?.toString().orEmpty()
        var layer = textLayers.firstOrNull { it.id == selectedTextLayerId }
        if (layer == null && text.isNotBlank()) {
            layer = TextLayer(nextLayerId++, text, textStartMs, textEndMs.coerceAtLeast(1L), textPosition)
            textLayers.add(layer)
            selectedTextLayerId = layer.id
        }
        layer?.let {
            it.text = text
            it.startMs = textStartMs.coerceIn(0L, totalOutputDurationMs())
            it.endMs = textEndMs.coerceIn(it.startMs, totalOutputDurationMs())
            it.position = textPosition
        }
    }

    private fun refreshTextLayerList() {
        if (!::textLayerList.isInitialized) return
        textLayerList.removeAllViews()
        if (textLayers.isEmpty()) {
            textLayerList.addView(label("Henüz metin yok. + Yeni metin ile katman ekle.", 11f, Color.rgb(165, 174, 190), false))
            return
        }
        textLayers.forEachIndexed { index, layer ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val selected = layer.id == selectedTextLayerId
            val name = layer.text.trim().ifBlank { "Metin katmanı ${index + 1}" }.let {
                if (it.length > 20) it.take(20) + "…" else it
            }
            val selectButton = Button(this).apply {
                text = "T${index + 1}  $name  •  ${formatMs(layer.startMs)}–${formatMs(layer.endMs)}"
                isAllCaps = false
                isSingleLine = false
                textSize = 10f
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                setTextColor(if (selected) Color.BLACK else Color.WHITE)
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(if (selected) Color.rgb(177, 139, 255) else Color.rgb(48, 54, 67))
                    cornerRadius = dp(7).toFloat()
                }
                setOnClickListener { selectTextLayer(layer) }
            }
            row.addView(selectButton, LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginEnd = dp(5) })
            val delete = Button(this).apply {
                text = "×"
                contentDescription = "Metin katmanını sil"
                isAllCaps = false
                textSize = 18f
                setTextColor(Color.WHITE)
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.rgb(93, 48, 57))
                    cornerRadius = dp(7).toFloat()
                }
                setOnClickListener {
                    selectedTextLayerId = layer.id
                    removeSelectedTextLayer()
                }
            }
            row.addView(delete, LinearLayout.LayoutParams(dp(42), dp(38)))
            textLayerList.addView(row, matchWrap())
        }
    }

    private fun audioFadeListener(isMusic: Boolean, isFadeIn: Boolean): SeekBar.OnSeekBarChangeListener =
        object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser || suppressLayerControlCallbacks) return
                val layers = if (isMusic) musicLayers else soundLayers
                val selectedId = if (isMusic) selectedMusicLayerId else selectedSoundLayerId
                val layer = layers.firstOrNull { it.id == selectedId }
                if (layer != null) {
                    if (isFadeIn) layer.fadeInMs = progress * 1000L else layer.fadeOutMs = progress * 1000L
                }
                if (isMusic) {
                    musicFadeLabel.text = "Müziğin açılma / kısılma süresi: ${musicFadeInSeek.progress} sn / ${musicFadeOutSeek.progress} sn"
                } else {
                    soundFadeLabel.text = "Efektin açılma / kısılma süresi: ${soundFadeInSeek.progress} sn / ${soundFadeOutSeek.progress} sn"
                }
                refreshTimelineTracks()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) { recordUndoState() }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }

    private fun loadSelectedMusicLayer(layer: AudioLayer?) {
        suppressLayerControlCallbacks = true
        selectedMusicLayerId = layer?.id
        musicUri = layer?.uri
        musicTitle = layer?.title
        musicVolume = layer?.volume ?: 45
        musicLabel.text = layer?.let { "Müzik: ${it.title} • video boyunca" } ?: "Müzik seçilmedi."
        musicVolumeSeek.progress = musicVolume
        musicVolumeLabel.text = "Müzik ses seviyesi: $musicVolume%"
        musicFadeInSeek.progress = (((layer?.fadeInMs ?: 0L) / 1000L).toInt()).coerceIn(0, 10)
        musicFadeOutSeek.progress = (((layer?.fadeOutMs ?: 0L) / 1000L).toInt()).coerceIn(0, 10)
        musicFadeLabel.text = "Müziğin açılma / kısılma süresi: ${musicFadeInSeek.progress} sn / ${musicFadeOutSeek.progress} sn"
        suppressLayerControlCallbacks = false
        refreshTimelineTracks()
    }

    private fun loadSelectedSoundLayer(layer: AudioLayer?) {
        suppressLayerControlCallbacks = true
        selectedSoundLayerId = layer?.id
        soundUri = layer?.uri
        soundTitle = layer?.title
        soundDurationMs = layer?.durationMs ?: 0L
        soundStartMs = layer?.startMs ?: 0L
        soundVolume = layer?.volume ?: 100
        soundLabel.text = layer?.let { "Ses efekti: ${it.title} • ${formatMs(it.durationMs)}" } ?: "Ses efekti seçilmedi."
        soundVolumeSeek.progress = soundVolume
        soundVolumeLabel.text = "Ses/efekt seviyesi: $soundVolume%"
        soundFadeInSeek.progress = (((layer?.fadeInMs ?: 0L) / 1000L).toInt()).coerceIn(0, 10)
        soundFadeOutSeek.progress = (((layer?.fadeOutMs ?: 0L) / 1000L).toInt()).coerceIn(0, 10)
        soundFadeLabel.text = "Efektin açılma / kısılma süresi: ${soundFadeInSeek.progress} sn / ${soundFadeOutSeek.progress} sn"
        suppressLayerControlCallbacks = false
        updateSoundControls()
    }

    private fun removeSelectedAudioLayer(isMusic: Boolean) {
        val layers = if (isMusic) musicLayers else soundLayers
        val id = if (isMusic) selectedMusicLayerId else selectedSoundLayerId
        val layer = layers.firstOrNull { it.id == id }
        if (layer == null) {
            status.text = if (isMusic) "Önce silinecek müzik katmanını seç." else "Önce silinecek ses katmanını seç."
            return
        }
        recordUndoState()
        layers.remove(layer)
        audioPreviewPlayers.remove(layer.id)?.release()
        audioPreviewSources.remove(layer.id)
        if (isMusic) loadSelectedMusicLayer(layers.lastOrNull()) else loadSelectedSoundLayer(layers.lastOrNull())
        refreshTimelineTracks()
        status.text = if (isMusic) "Müzik katmanı silindi." else "Ses katmanı silindi."
    }

    private fun addAudioLayer(isMusic: Boolean, uri: Uri, title: String, durationMs: Long) {
        recordUndoState()
        val layer = AudioLayer(
            id = nextLayerId++,
            uri = uri,
            title = title,
            durationMs = durationMs.coerceAtLeast(1L),
            startMs = 0L,
            volume = if (isMusic) musicVolume else soundVolume
        )
        if (isMusic) {
            musicLayers.add(layer)
            loadSelectedMusicLayer(layer)
        } else {
            soundLayers.add(layer)
            loadSelectedSoundLayer(layer)
        }
        refreshTimelineTracks()
        status.text = if (isMusic) "Müzik katmanı zaman çizelgesine eklendi." else "Ses efekti zaman çizelgesine eklendi."
    }

    private fun selectAudioLayer(layer: AudioLayer, isMusic: Boolean) {
        if (isMusic) loadSelectedMusicLayer(layer) else loadSelectedSoundLayer(layer)
        onOpenAudioTool?.invoke()
    }

    private fun syncSelectedAudioLayerFromControls() {
        musicLayers.firstOrNull { it.id == selectedMusicLayerId }?.let { layer ->
            layer.volume = musicVolume.coerceIn(0, 100)
            layer.fadeInMs = musicFadeInSeek.progress * 1000L
            layer.fadeOutMs = musicFadeOutSeek.progress * 1000L
        }
        soundLayers.firstOrNull { it.id == selectedSoundLayerId }?.let { layer ->
            layer.startMs = soundStartMs.coerceAtLeast(0L)
            layer.volume = soundVolume.coerceIn(0, 100)
            layer.fadeInMs = soundFadeInSeek.progress * 1000L
            layer.fadeOutMs = soundFadeOutSeek.progress * 1000L
        }
    }

    private fun audioGain(volumePercent: Int, relativePositionMs: Long, trackDurationMs: Long, fadeInMs: Long, fadeOutMs: Long): Float {
        val base = volumePercent.coerceIn(0, 100) / 100f
        var fade = 1f
        val duration = trackDurationMs.coerceAtLeast(0L)
        if (fadeInMs > 0L && relativePositionMs < fadeInMs) {
            fade = minOf(fade, (relativePositionMs.toFloat() / fadeInMs).coerceIn(0f, 1f))
        }
        val fadeStart = (duration - fadeOutMs).coerceAtLeast(0L)
        if (fadeOutMs > 0L && relativePositionMs > fadeStart) {
            fade = minOf(fade, ((duration - relativePositionMs).toFloat() / fadeOutMs).coerceIn(0f, 1f))
        }
        return base * fade
    }

    private fun simpleSelectionListener(onSelected: (Int) -> Unit): AdapterView.OnItemSelectedListener =
        object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                onSelected(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

    private fun updateUndoRedoButtons() {
        if (::undoButton.isInitialized) {
            undoButton.isEnabled = undoStates.isNotEmpty() && !exporting
            undoButton.alpha = if (undoButton.isEnabled) 1f else 0.42f
        }
        if (::redoButton.isInitialized) {
            redoButton.isEnabled = redoStates.isNotEmpty() && !exporting
            redoButton.alpha = if (redoButton.isEnabled) 1f else 0.42f
        }
    }

    private fun recordUndoState() {
        if (restoringHistory || !::textInput.isInitialized || !::exportNameInput.isInitialized) return
        val snapshot = runCatching { createProjectJson().toString() }.getOrNull() ?: return
        if (undoStates.lastOrNull() != snapshot) {
            undoStates.add(snapshot)
            if (undoStates.size > 80) undoStates.removeAt(0)
        }
        redoStates.clear()
        updateUndoRedoButtons()
    }

    private fun undoLastChange() {
        if (undoStates.isEmpty() || restoringHistory) return
        val current = runCatching { createProjectJson().toString() }.getOrNull() ?: return
        val target = undoStates.removeAt(undoStates.lastIndex)
        redoStates.add(current)
        restoringHistory = true
        runCatching { applyProjectJson(JSONObject(target), allowEmptyClips = true) }
            .onFailure { status.text = "Geri alma yapılamadı: ${it.message}" }
        restoringHistory = false
        updateUndoRedoButtons()
        status.text = "Son işlem geri alındı."
    }

    private fun redoLastChange() {
        if (redoStates.isEmpty() || restoringHistory) return
        val current = runCatching { createProjectJson().toString() }.getOrNull() ?: return
        val target = redoStates.removeAt(redoStates.lastIndex)
        undoStates.add(current)
        restoringHistory = true
        runCatching { applyProjectJson(JSONObject(target), allowEmptyClips = true) }
            .onFailure { status.text = "Yineleme yapılamadı: ${it.message}" }
        restoringHistory = false
        updateUndoRedoButtons()
        status.text = "İşlem yine uygulandı."
    }

    private fun updatePlayheadUi(positionMs: Long) {
        if (!::playheadSeek.isInitialized || !::playheadLabel.isInitialized) return
        val total = totalOutputDurationMs().coerceAtLeast(0L)
        val bounded = positionMs.coerceIn(0L, total)
        playheadLabel.text = "${formatPreciseMs(bounded)} / ${formatPreciseMs(total)}"
        updatingPlayhead = true
        playheadSeek.isEnabled = total > 0L
        playheadSeek.progress = if (total <= 0L) 0 else (bounded * 10000L / total).toInt().coerceIn(0, 10000)
        updatingPlayhead = false
    }

    private fun seekToTimelineTime(targetMs: Long, preservePlayback: Boolean) {
        if (clips.isEmpty()) return
        val total = totalOutputDurationMs()
        val target = targetMs.coerceIn(0L, (total - 1L).coerceAtLeast(0L))
        var remaining = target
        var index = 0
        while (index < clips.lastIndex) {
            val duration = (clips[index].endMs - clips[index].startMs).coerceAtLeast(0L)
            if (remaining < duration) break
            remaining -= duration
            index++
        }
        val exo = player ?: return
        val wasPlaying = preservePlayback && (exo.isPlaying || exo.playWhenReady)
        selectedClipIndex = index
        previewingSequence = true
        previewTimelineOffsetMs = outputOffsetForClip(index)
        val duration = (clips[index].endMs - clips[index].startMs).coerceAtLeast(1L)
        val localPosition = remaining.coerceIn(0L, (duration - 1L).coerceAtLeast(0L))
        if (exo.mediaItemCount == clips.size) {
            exo.seekTo(index, localPosition)
        } else {
            exo.setMediaItems(clips.map { clippedMediaItem(it) }, index, localPosition)
            exo.prepare()
        }
        exo.volume = if (keepSourceAudio.isChecked) sourceAudioVolume / 100f else 0f
        exo.playWhenReady = wasPlaying
        refreshClipList()
        loadTrimControls()
        updatePlayheadUi(target)
        refreshPreviewOverlay()
    }

    private fun framesFor(clip: Clip): Int =
        ceil(clip.durationMs * clip.frameRate.coerceIn(1f, 120f) / 1000.0).toInt().coerceAtLeast(1)

    private fun frameForMs(clip: Clip, timeMs: Long): Int =
        (timeMs.coerceAtLeast(0L) * clip.frameRate.coerceIn(1f, 120f) / 1000f)
            .roundToInt().coerceIn(0, framesFor(clip))

    private fun msForFrame(clip: Clip, frame: Int): Long =
        (frame.coerceAtLeast(0) * 1000.0 / clip.frameRate.coerceIn(1f, 120f))
            .roundToLong().coerceIn(0L, clip.durationMs)

    private fun readVideoMetadata(uri: Uri): Pair<Long, Float> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(this, uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val fps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                ?.toFloatOrNull()?.takeIf { it in 1f..120f } ?: 30f
            duration to fps
        } finally {
            retriever.release()
        }
    }

    private fun splitSelectedClipAtPlayhead() {
        if (selectedClipIndex !in clips.indices) {
            status.text = "Önce bölünecek klibi seç."
            return
        }
        val index = if (previewingSequence) (player?.currentMediaItemIndex ?: selectedClipIndex).coerceIn(0, clips.lastIndex) else selectedClipIndex
        val clip = clips[index]
        val localOutputMs = (currentTimelinePositionMs() - outputOffsetForClip(index)).coerceAtLeast(0L)
        val rawBoundary = clip.startMs + localOutputMs
        val frame = frameForMs(clip, rawBoundary)
        val boundary = msForFrame(clip, frame).coerceIn(clip.startMs, clip.endMs)
        val minFrameMs = (1000f / clip.frameRate.coerceIn(1f, 120f)).roundToLong().coerceAtLeast(1L)
        if (boundary - clip.startMs < minFrameMs || clip.endMs - boundary < minFrameMs) {
            status.text = "Bölmek için oynatma noktasını klibin içine, iki uçtan en az bir kare uzağa getir."
            return
        }
        recordUndoState()
        val first = clip.copy(title = clip.title + " • A", endMs = boundary)
        val second = clip.copy(title = clip.title + " • B", startMs = boundary)
        clips[index] = first
        clips.add(index + 1, second)
        selectedClipIndex = index + 1
        previewingSequence = false
        refreshClipList()
        loadTrimControls()
        seekToTimelineTime(outputOffsetForClip(selectedClipIndex), preservePlayback = false)
        status.text = "Klip oynatma noktasından iki parçaya bölündü."
    }

    private fun outputDimensions(): Pair<Int, Int> {
        val shortSide = when (outputResolutionIndex.coerceIn(0, resolutionOptions.lastIndex)) {
            0 -> 480
            1 -> 720
            else -> 1080
        }
        val ratio = when (outputAspectIndex.coerceIn(0, aspectOptions.lastIndex)) {
            0 -> 9f / 16f
            1 -> 16f / 9f
            else -> 1f
        }
        val width: Int
        val height: Int
        if (ratio >= 1f) {
            height = shortSide
            width = (shortSide * ratio).roundToInt()
        } else {
            width = shortSide
            height = (shortSide / ratio).roundToInt()
        }
        return (width / 2 * 2).coerceAtLeast(2) to (height / 2 * 2).coerceAtLeast(2)
    }

    private fun volumeListener(onValue: (Int) -> Unit): SeekBar.OnSeekBarChangeListener =
        object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) onValue(progress.coerceIn(0, 100))
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) { recordUndoState() }
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
        syncSelectedTextLayerFromControls()
        refreshTextLayerList()
        refreshTimelineTracks()
        refreshPreviewOverlay()
    }

    private fun audioEffects(
        volumePercent: Int,
        fadeInMs: Long = 0L,
        fadeOutMs: Long = 0L,
        trackDurationMs: Long = 0L
    ): List<AudioProcessor> {
        val processors = mutableListOf<AudioProcessor>()
        if (volumePercent < 100) {
            val gain = volumePercent.coerceIn(0, 100) / 100f
            val processor = ChannelMixingAudioProcessor()
            for (channels in 1..8) {
                val coefficients = FloatArray(channels * channels)
                for (channel in 0 until channels) coefficients[channel * channels + channel] = gain
                processor.putChannelMixingMatrix(ChannelMixingMatrix(channels, channels, coefficients))
            }
            processors.add(processor)
        }
        if (trackDurationMs > 0L && (fadeInMs > 0L || fadeOutMs > 0L)) {
            val fadeIn = fadeInMs.coerceAtLeast(0L).coerceAtMost(trackDurationMs)
            val fadeOut = fadeOutMs.coerceAtLeast(0L).coerceAtMost(trackDurationMs)
            val builder = DefaultGainProvider.Builder(1f)
            if (fadeIn > 0L) {
                builder.addFadeAt(0L, fadeIn * 1000L, DefaultGainProvider.FADE_IN_LINEAR)
            }
            if (fadeOut > 0L) {
                builder.addFadeAt((trackDurationMs - fadeOut).coerceAtLeast(0L) * 1000L,
                    fadeOut * 1000L, DefaultGainProvider.FADE_OUT_LINEAR)
            }
            processors.add(GainProcessor(builder.build()))
        }
        return processors
    }

    private fun addClip(uri: Uri, replaceExisting: Boolean) {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) { }
        status.text = "Video bilgileri okunuyor…"
        Thread {
            val metadata = runCatching { readVideoMetadata(uri) }.getOrDefault(0L to 30f)
            val duration = metadata.first
            val frameRate = metadata.second
            val title = queryDisplayName(uri) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Video klibi"
            runOnUiThread {
                if (duration <= 0L) {
                    status.text = "Bu videonun süresi okunamadı. Başka bir video dosyası seç."
                    return@runOnUiThread
                }
                recordUndoState()
                if (replaceExisting) {
                    clips.clear()
                    selectedClipIndex = -1
                }
                clips.add(Clip(uri, title, duration, 0L, duration, frameRate))
                selectedClipIndex = clips.lastIndex
                previewingSequence = false
                previewTimelineOffsetMs = outputOffsetForClip(selectedClipIndex)
                player?.setMediaItem(clippedMediaItem(clips.last()))
                player?.volume = if (keepSourceAudio.isChecked) sourceAudioVolume / 100f else 0f
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
        syncSelectedTextLayerFromControls()
        val clipArray = JSONArray()
        clips.forEach { clip ->
            clipArray.put(JSONObject().apply {
                put("uri", clip.uri.toString())
                put("title", clip.title)
                put("durationMs", clip.durationMs)
                put("startMs", clip.startMs)
                put("endMs", clip.endMs)
                put("frameRate", clip.frameRate.toDouble())
            })
        }
        val textLayerArray = JSONArray()
        textLayers.forEach { layer ->
            textLayerArray.put(JSONObject().apply {
                put("id", layer.id)
                put("text", layer.text)
                put("startMs", layer.startMs)
                put("endMs", layer.endMs)
                put("position", layer.position.name)
            })
        }
        fun audioArray(layers: List<AudioLayer>) = JSONArray().apply {
            layers.forEach { layer ->
                put(JSONObject().apply {
                    put("id", layer.id)
                    put("uri", layer.uri.toString())
                    put("title", layer.title)
                    put("durationMs", layer.durationMs)
                    put("startMs", layer.startMs)
                    put("volume", layer.volume)
                    put("fadeInMs", layer.fadeInMs)
                    put("fadeOutMs", layer.fadeOutMs)
                })
            }
        }
        return JSONObject().apply {
            put("format", "MathMotorVideoProject")
            put("version", 3)
            put("clips", clipArray)
            put("selectedClipIndex", selectedClipIndex)
            put("textLayers", textLayerArray)
            put("musicLayers", audioArray(musicLayers))
            put("soundLayers", audioArray(soundLayers))
            put("selectedTextLayerId", selectedTextLayerId ?: JSONObject.NULL)
            put("selectedMusicLayerId", selectedMusicLayerId ?: JSONObject.NULL)
            put("selectedSoundLayerId", selectedSoundLayerId ?: JSONObject.NULL)
            put("nextLayerId", nextLayerId)
            // Keep legacy fields for compatibility with projects saved by earlier editor builds.
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
            put("outputAspectIndex", outputAspectIndex)
            put("outputResolutionIndex", outputResolutionIndex)
        }
    }

    private fun applyProjectJson(json: JSONObject, allowEmptyClips: Boolean = false) {
        require(json.optString("format") == "MathMotorVideoProject") { "Bu dosya MathMotorVideo proje dosyası değil." }
        require(json.optInt("version", 0) in 1..3) { "Bu proje sürümü desteklenmiyor." }
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
            val frameRate = item.optDouble("frameRate", 30.0).toFloat().takeIf { it in 1f..120f } ?: 30f
            loadedClips.add(Clip(uri, item.optString("title", "Video klibi"), duration, start, end, frameRate))
        }
        require(loadedClips.isNotEmpty() || allowEmptyClips) { "Projede video klibi bulunamadı." }

        val loadedTextLayers = mutableListOf<TextLayer>()
        val textArray = json.optJSONArray("textLayers")
        if (textArray != null) {
            for (i in 0 until textArray.length()) {
                val item = textArray.getJSONObject(i)
                val id = item.optLong("id", 0L).takeIf { it > 0L } ?: (i + 1L)
                val start = item.optLong("startMs", 0L).coerceAtLeast(0L)
                val end = item.optLong("endMs", totalOutputDurationMs()).coerceAtLeast(start)
                val position = runCatching { TextPosition.valueOf(item.optString("position", "BOTTOM")) }.getOrDefault(TextPosition.BOTTOM)
                loadedTextLayers.add(TextLayer(id, item.optString("text", ""), start, end, position))
            }
        } else {
            val oldText = json.optString("text", "")
            if (oldText.isNotBlank()) {
                val id = 1L
                loadedTextLayers.add(TextLayer(
                    id, oldText, json.optLong("textStartMs", 0L).coerceAtLeast(0L),
                    json.optLong("textEndMs", totalOutputDurationMs()).coerceAtLeast(0L),
                    runCatching { TextPosition.valueOf(json.optString("textPosition", "BOTTOM")) }.getOrDefault(TextPosition.BOTTOM)
                ))
            }
        }

        fun parseAudioLayers(array: JSONArray?, isMusic: Boolean): MutableList<AudioLayer> {
            val layers = mutableListOf<AudioLayer>()
            if (array != null) {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val rawUri = item.optString("uri")
                    if (rawUri.isBlank() || rawUri == "null") continue
                    val uri = Uri.parse(rawUri)
                    try {
                        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } catch (_: Exception) { }
                    layers.add(AudioLayer(
                        item.optLong("id", 0L).takeIf { it > 0L } ?: (1000L + i),
                        uri,
                        item.optString("title", if (isMusic) "Müzik" else "Ses efekti"),
                        item.optLong("durationMs", if (isMusic) totalOutputDurationMs() else 0L).coerceAtLeast(1L),
                        item.optLong("startMs", 0L).coerceAtLeast(0L),
                        item.optInt("volume", if (isMusic) 45 else 100).coerceIn(0, 100),
                        item.optLong("fadeInMs", 0L).coerceAtLeast(0L),
                        item.optLong("fadeOutMs", 0L).coerceAtLeast(0L)
                    ))
                }
            } else {
                val key = if (isMusic) "musicUri" else "soundUri"
                val rawUri = json.optString(key)
                if (rawUri.isNotBlank() && rawUri != "null") {
                    val uri = Uri.parse(rawUri)
                    try {
                        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } catch (_: Exception) { }
                    layers.add(AudioLayer(
                        if (isMusic) 1000L else 2000L,
                        uri,
                        json.optString(if (isMusic) "musicTitle" else "soundTitle", if (isMusic) "Müzik" else "Ses efekti"),
                        json.optLong(if (isMusic) "musicDurationMs" else "soundDurationMs", if (isMusic) totalOutputDurationMs() else 1L).coerceAtLeast(1L),
                        if (isMusic) 0L else json.optLong("soundStartMs", 0L).coerceAtLeast(0L),
                        json.optInt(if (isMusic) "musicVolume" else "soundVolume", if (isMusic) 45 else 100).coerceIn(0, 100)
                    ))
                }
            }
            return layers
        }

        clips.clear()
        clips.addAll(loadedClips)
        selectedClipIndex = if (clips.isEmpty()) -1 else json.optInt("selectedClipIndex", 0).coerceIn(0, clips.lastIndex)
        textLayers.clear()
        textLayers.addAll(loadedTextLayers)
        val savedTextLayerId = json.optString("selectedTextLayerId").toLongOrNull()
        selectedTextLayerId = loadedTextLayers.firstOrNull { it.id == savedTextLayerId }?.id
            ?: loadedTextLayers.lastOrNull()?.id
        musicLayers.clear()
        musicLayers.addAll(parseAudioLayers(json.optJSONArray("musicLayers"), true))
        soundLayers.clear()
        soundLayers.addAll(parseAudioLayers(json.optJSONArray("soundLayers"), false))
        val savedMusicLayerId = json.optString("selectedMusicLayerId").toLongOrNull()
        selectedMusicLayerId = musicLayers.firstOrNull { it.id == savedMusicLayerId }?.id
            ?: musicLayers.lastOrNull()?.id
        val savedSoundLayerId = json.optString("selectedSoundLayerId").toLongOrNull()
        selectedSoundLayerId = soundLayers.firstOrNull { it.id == savedSoundLayerId }?.id
            ?: soundLayers.lastOrNull()?.id
        nextLayerId = maxOf(
            json.optLong("nextLayerId", 1L),
            (textLayers.map { it.id } + musicLayers.map { it.id } + soundLayers.map { it.id }).maxOrNull()?.plus(1L) ?: 1L
        )

        textStartMs = json.optLong("textStartMs", 0L).coerceAtLeast(0L)
        textEndMs = json.optLong("textEndMs", totalOutputDurationMs()).coerceAtLeast(0L)
        textTimingCustomized = json.optBoolean("textTimingCustomized", false)
        textPosition = runCatching { TextPosition.valueOf(json.optString("textPosition", "BOTTOM")) }.getOrDefault(TextPosition.BOTTOM)
        sourceAudioVolume = json.optInt("sourceAudioVolume", 100).coerceIn(0, 100)
        musicVolume = json.optInt("musicVolume", 45).coerceIn(0, 100)
        soundVolume = json.optInt("soundVolume", 100).coerceIn(0, 100)
        outputAspectIndex = json.optInt("outputAspectIndex", 0).coerceIn(0, aspectOptions.lastIndex)
        outputResolutionIndex = json.optInt("outputResolutionIndex", 1).coerceIn(0, resolutionOptions.lastIndex)
        if (::aspectSpinner.isInitialized) aspectSpinner.setSelection(outputAspectIndex, false)
        if (::resolutionSpinner.isInitialized) resolutionSpinner.setSelection(outputResolutionIndex, false)

        val fallbackTextLayer = textLayers.firstOrNull { it.id == selectedTextLayerId }
            ?: textLayers.lastOrNull()
        val fallbackMusicLayer = musicLayers.firstOrNull { it.id == selectedMusicLayerId } ?: musicLayers.lastOrNull()
        val fallbackSoundLayer = soundLayers.firstOrNull { it.id == selectedSoundLayerId } ?: soundLayers.lastOrNull()
        suppressLayerControlCallbacks = true
        textInput.setText(fallbackTextLayer?.text ?: json.optString("text", ""))
        textPosition = fallbackTextLayer?.position
            ?: runCatching { TextPosition.valueOf(json.optString("textPosition", "BOTTOM")) }.getOrDefault(TextPosition.BOTTOM)
        textStartMs = fallbackTextLayer?.startMs ?: json.optLong("textStartMs", 0L).coerceAtLeast(0L)
        textEndMs = fallbackTextLayer?.endMs ?: json.optLong("textEndMs", totalOutputDurationMs()).coerceAtLeast(0L)
        textTimingCustomized = fallbackTextLayer != null || json.optBoolean("textTimingCustomized", false)
        textPositionGroup.check(when (textPosition) {
            TextPosition.TOP -> 1
            TextPosition.CENTER -> 2
            TextPosition.BOTTOM -> 3
        })
        keepSourceAudio.isChecked = json.optBoolean("keepSourceAudio", true)
        exportNameInput.setText(json.optString("exportName", "MathMotor_Edit"))
        suppressLayerControlCallbacks = false
        loadSelectedMusicLayer(fallbackMusicLayer)
        loadSelectedSoundLayer(fallbackSoundLayer)
        musicVolume = fallbackMusicLayer?.volume ?: json.optInt("musicVolume", 45).coerceIn(0, 100)
        soundVolume = fallbackSoundLayer?.volume ?: json.optInt("soundVolume", 100).coerceIn(0, 100)
        sourceVolumeSeek.progress = sourceAudioVolume
        sourceVolumeLabel.text = "Orijinal video sesi: $sourceAudioVolume%"
        player?.volume = if (keepSourceAudio.isChecked) sourceAudioVolume / 100f else 0f
        previewHandler.removeCallbacks(previewTicker)
        audioPreviewPlayers.values.forEach { it.release() }
        audioPreviewPlayers.clear()
        audioPreviewSources.clear()
        refreshClipList()
        loadTrimControls()
        selectedClip()?.let { clip ->
            previewingSequence = false
            previewTimelineOffsetMs = outputOffsetForClip(selectedClipIndex)
            player?.setMediaItem(clippedMediaItem(clip))
            player?.volume = if (keepSourceAudio.isChecked) sourceAudioVolume / 100f else 0f
            player?.prepare()
            player?.playWhenReady = false
        } ?: player?.clearMediaItems()
        refreshTextLayerList()
        refreshTimelineTracks()
        refreshPreviewOverlay()
        updatePlayheadUi(0L)
        updateUndoRedoButtons()
        previewHandler.post(previewTicker)
        status.text = if (clips.isEmpty()) "Boş proje geri yüklendi." else "PROJE AÇILDI. Klipler, katmanlar, kesmeler ve ses ayarları geri yüklendi."
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
                    val metadata = runCatching { readVideoMetadata(uri) }.getOrDefault(0L to 30f)
                    val details = metadata.first
                    val frameRate = metadata.second
                    val title = queryDisplayName(uri) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Video klibi"
                    runOnUiThread {
                        if (details <= 0L) {
                            status.text = "Bu videonun süresi okunamadı. Başka bir video dosyası seç."
                        } else {
                            recordUndoState()
                            if (requestCode == REQUEST_MAIN_VIDEO) {
                                clips.clear()
                                selectedClipIndex = -1
                            }
                            val clip = Clip(uri, title, details, 0L, details, frameRate)
                            clips.add(clip)
                            selectedClipIndex = clips.lastIndex
                            previewingSequence = false
                            previewTimelineOffsetMs = outputOffsetForClip(selectedClipIndex)
                            player?.setMediaItem(clippedMediaItem(clip))
                            player?.volume = if (keepSourceAudio.isChecked) sourceAudioVolume / 100f else 0f
                            player?.prepare()
                            player?.playWhenReady = true
                            refreshClipList()
                            loadTrimControls()
                            status.text = "${title} eklendi. Kesme aralığını seçebilirsin."
                        }
                    }
                }.start()
            }
            REQUEST_MUSIC, REQUEST_SOUND -> {
                val isMusic = requestCode == REQUEST_MUSIC
                status.text = if (isMusic) "Müzik bilgileri okunuyor…" else "Ses efektinin süresi okunuyor…"
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
                    val title = queryDisplayName(uri) ?: uri.lastPathSegment?.substringAfterLast('/')
                        ?: if (isMusic) "Müzik" else "Ses efekti"
                    runOnUiThread {
                        if (duration <= 0L) {
                            status.text = if (isMusic) "Müziğin süresi okunamadı." else "Ses dosyasının süresi okunamadı."
                        } else {
                            addAudioLayer(isMusic, uri, title, duration)
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
        val scale = timelineScaleDpPerSecond()
        if (clips.isEmpty()) {
            addTimelineChip(clipList, "+ Video ekle", Color.rgb(0, 202, 170), Color.BLACK) {
                chooseVideo(false)
            }
        } else {
            clips.forEachIndexed { index, clip ->
                val selected = index == selectedClipIndex
                val durationMs = (clip.endMs - clip.startMs).coerceAtLeast(1L)
                val widthDp = ((durationMs / 1000f) * scale).toInt().coerceIn(48, timelineContentWidthDp())
                val chip = addTimedTimelineChip(
                    clipList,
                    (if (selected) "▶ " else "") + "${index + 1}. ${clip.title}\n${formatMs(durationMs)}",
                    if (selected) Color.rgb(0, 202, 170) else Color.rgb(45, 91, 111),
                    if (selected) Color.BLACK else Color.WHITE,
                    widthDp,
                    0
                ) {
                    selectedClipIndex = index
                    previewingSequence = false
                    previewTimelineOffsetMs = outputOffsetForClip(index)
                    refreshClipList()
                    loadTrimControls()
                    player?.setMediaItem(clippedMediaItem(clip))
                    player?.volume = if (keepSourceAudio.isChecked) sourceAudioVolume / 100f else 0f
                    player?.prepare()
                    player?.playWhenReady = true
                    status.text = "Seçili klip: ${clip.title}"
                }
                chip.tag = index
                chip.setOnLongClickListener { view ->
                    val payload = ClipData.newPlainText("MathMotorVideoClipIndex", index.toString())
                    view.startDragAndDrop(payload, View.DragShadowBuilder(view), view, 0)
                }
                chip.setOnDragListener { view, event ->
                    when (event.action) {
                        DragEvent.ACTION_DRAG_STARTED -> true
                        DragEvent.ACTION_DRAG_ENTERED -> {
                            view.alpha = 0.68f
                            true
                        }
                        DragEvent.ACTION_DRAG_EXITED -> {
                            view.alpha = 1f
                            true
                        }
                        DragEvent.ACTION_DROP -> {
                            view.alpha = 1f
                            val draggedView = event.localState as? View
                            val from = draggedView?.tag as? Int
                            val to = index
                            if (from != null && from in clips.indices && to in clips.indices && from != to) {
                                recordUndoState()
                                val wasSelected = selectedClipIndex
                                val moved = clips.removeAt(from)
                                clips.add(to, moved)
                                selectedClipIndex = when {
                                    wasSelected == from -> to
                                    from < to && wasSelected in (from + 1)..to -> wasSelected - 1
                                    from > to && wasSelected in to until from -> wasSelected + 1
                                    else -> wasSelected
                                }.coerceIn(0, clips.lastIndex)
                                previewingSequence = false
                                previewTimelineOffsetMs = outputOffsetForClip(selectedClipIndex)
                                refreshClipList()
                                loadTrimControls()
                                player?.setMediaItem(clippedMediaItem(clips[selectedClipIndex]))
                                player?.prepare()
                                player?.playWhenReady = false
                                status.text = "Klipler zaman çizelgesinde yeniden sıralandı."
                            }
                            true
                        }
                        DragEvent.ACTION_DRAG_ENDED -> {
                            view.alpha = 1f
                            true
                        }
                        else -> true
                    }
                }
            }
        }
        refreshTimelineTracks()
        updatePlayheadUi(currentTimelinePositionMs())
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
            setPadding(dp(3), dp(2), dp(3), dp(2))
        }
        row.addView(name, LinearLayout.LayoutParams(dp(57), dp(38)))
        val laneScroll = android.widget.HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            setPadding(dp(2), dp(1), dp(2), dp(1))
            clipToPadding = false
        }
        laneScroll.addView(content, ViewGroup.LayoutParams(
            dp(timelineContentWidthDp()), ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        laneScroll.setOnScrollChangeListener { view: View, scrollX: Int, _: Int, _: Int, _: Int ->
            if (!syncingTimelineScroll) {
                syncingTimelineScroll = true
                timelineScrollViews.forEach { other ->
                    if (other !== view && other.scrollX != scrollX) other.scrollTo(scrollX, 0)
                }
                syncingTimelineScroll = false
            }
        }
        timelineScrollViews.add(laneScroll)
        row.addView(laneScroll, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        host.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            minHeight = dp(38)
            bottomMargin = dp(2)
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
            minimumHeight = dp(34)
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

    private fun addTimedTimelineChip(
        parent: LinearLayout,
        title: String,
        fill: Int,
        textColor: Int,
        widthDp: Int,
        marginStartDp: Int,
        action: () -> Unit
    ): Button {
        val chip = Button(this).apply {
            text = title
            isAllCaps = false
            isSingleLine = false
            textSize = 10f
            gravity = Gravity.CENTER
            setTextColor(textColor)
            minHeight = dp(29)
            setPadding(dp(4), 0, dp(4), 0)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(fill)
                cornerRadius = dp(6).toFloat()
            }
            setOnClickListener { action() }
        }
        parent.addView(chip, LinearLayout.LayoutParams(dp(widthDp.coerceAtLeast(38)), dp(31)).apply {
            marginStart = dp(marginStartDp.coerceAtLeast(0))
            marginEnd = dp(2)
        })
        return chip
    }

    private fun timelineContentWidthDp(): Int {
        val seconds = (totalOutputDurationMs() / 1000f).coerceAtLeast(0.1f)
        return (seconds * 28f).toInt().coerceIn(280, 2400)
    }

    private fun timelineScaleDpPerSecond(): Float {
        val seconds = (totalOutputDurationMs() / 1000f).coerceAtLeast(0.1f)
        return timelineContentWidthDp() / seconds
    }

    private fun setTimelineContentWidth(content: LinearLayout, widthDp: Int) {
        val params = content.layoutParams ?: ViewGroup.LayoutParams(
            dp(widthDp), ViewGroup.LayoutParams.MATCH_PARENT
        )
        params.width = dp(widthDp)
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT
        content.layoutParams = params
    }

    private fun outputOffsetForClip(index: Int): Long {
        if (index <= 0 || index !in clips.indices) return 0L
        return clips.take(index).sumOf { (it.endMs - it.startMs).coerceAtLeast(0L) }
    }

    private fun currentTimelinePositionMs(): Long {
        val main = player
        val playbackPosition = main?.currentPosition?.coerceAtLeast(0L) ?: 0L
        if (previewingSequence && main != null) {
            val activeIndex = main.currentMediaItemIndex
            if (activeIndex in clips.indices) return outputOffsetForClip(activeIndex) + playbackPosition
        }
        return previewTimelineOffsetMs + playbackPosition
    }

    private fun refreshPreviewOverlay() {
        if (!::previewTextOverlay.isInitialized) return
        if (previewingSequence) {
            val activeIndex = player?.currentMediaItemIndex ?: -1
            if (activeIndex in clips.indices && activeIndex != selectedClipIndex) {
                selectedClipIndex = activeIndex
                previewTimelineOffsetMs = outputOffsetForClip(activeIndex)
                refreshClipList()
                loadTrimControls()
            }
        }
        if (selectedClip() == null) {
            previewTextOverlay.visibility = View.GONE
            return
        }
        val timeMs = currentTimelinePositionMs()
        val activeLayers = textLayers.filter { it.text.isNotBlank() && timeMs >= it.startMs && timeMs < it.endMs }
        if (activeLayers.isEmpty()) {
            previewTextOverlay.visibility = View.GONE
            return
        }
        previewTextOverlay.text = activeLayers.joinToString("\n") { it.text }
        val selectedPosition = activeLayers.firstOrNull { it.id == selectedTextLayerId }?.position ?: activeLayers.first().position
        val layoutParams = (previewTextOverlay.layoutParams as? FrameLayout.LayoutParams)
            ?: FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        layoutParams.gravity = when (selectedPosition) {
            TextPosition.TOP -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
            TextPosition.CENTER -> Gravity.CENTER
            TextPosition.BOTTOM -> Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        }
        layoutParams.leftMargin = dp(12)
        layoutParams.rightMargin = dp(12)
        layoutParams.topMargin = dp(14)
        layoutParams.bottomMargin = dp(14)
        previewTextOverlay.layoutParams = layoutParams
        previewTextOverlay.visibility = View.VISIBLE
    }

    private fun syncAudioPreviews() {
        val main = player
        if (main == null || clips.isEmpty() || !main.isPlaying) {
            audioPreviewPlayers.values.forEach { it.pause() }
            return
        }

        val timelineMs = currentTimelinePositionMs()
        val outputLength = totalOutputDurationMs().coerceAtLeast(1L)
        val allLayers = musicLayers + soundLayers
        val liveIds = allLayers.map { it.id }.toSet()
        val staleIds = audioPreviewPlayers.keys.filter { it !in liveIds }
        staleIds.forEach { id ->
            audioPreviewPlayers.remove(id)?.release()
            audioPreviewSources.remove(id)
        }

        allLayers.forEach { layer ->
            val isMusic = musicLayers.any { it.id == layer.id }
            val audio = audioPreviewPlayers.getOrPut(layer.id) {
                ExoPlayer.Builder(this).build().also {
                    it.repeatMode = if (isMusic) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                }
            }
            if (audioPreviewSources[layer.id] != layer.uri) {
                audio.playWhenReady = false
                audio.stop()
                audio.clearMediaItems()
                audio.setMediaItem(MediaItem.fromUri(layer.uri))
                audioPreviewSources[layer.id] = layer.uri
                audio.prepare()
            }
            if (audio.playbackState == Player.STATE_IDLE) {
                audio.setMediaItem(MediaItem.fromUri(layer.uri))
                audioPreviewSources[layer.id] = layer.uri
                audio.prepare()
            }

            if (isMusic) {
                if (timelineMs >= outputLength || audio.playbackState == Player.STATE_IDLE) {
                    audio.pause()
                    return@forEach
                }
                val mediaDuration = audio.duration.takeIf { it > 0L } ?: layer.durationMs.coerceAtLeast(1L)
                val target = timelineMs % mediaDuration
                if (!audio.isPlaying || kotlin.math.abs(audio.currentPosition - target) > 700L) {
                    if (audio.playbackState == Player.STATE_READY) audio.seekTo(target)
                }
                audio.volume = audioGain(layer.volume, timelineMs, outputLength, layer.fadeInMs, layer.fadeOutMs)
                audio.playWhenReady = true
            } else {
                val visibleDuration = minOf(layer.durationMs.coerceAtLeast(1L), (outputLength - layer.startMs).coerceAtLeast(0L))
                val localTime = timelineMs - layer.startMs
                if (localTime < 0L || localTime >= visibleDuration || audio.playbackState == Player.STATE_IDLE) {
                    audio.playWhenReady = false
                    if (localTime < 0L && audio.currentPosition > 0L && audio.playbackState == Player.STATE_READY) audio.seekTo(0L)
                    return@forEach
                }
                if (audio.playbackState == Player.STATE_READY) {
                    if (!audio.isPlaying || kotlin.math.abs(audio.currentPosition - localTime) > 450L) audio.seekTo(localTime)
                    audio.volume = audioGain(layer.volume, localTime, visibleDuration, layer.fadeInMs, layer.fadeOutMs)
                    audio.playWhenReady = true
                } else if (audio.playbackState == Player.STATE_BUFFERING) {
                    audio.playWhenReady = true
                }
            }
        }
    }

    private fun refreshTimelineTracks() {
        if (!::timelineRulerContent.isInitialized ||
            !::textTrackContent.isInitialized ||
            !::musicTrackContent.isInitialized ||
            !::soundTrackContent.isInitialized
        ) return

        val duration = totalOutputDurationMs()
        val widthDp = timelineContentWidthDp()
        val scale = timelineScaleDpPerSecond()
        listOf(timelineRulerContent, clipList, textTrackContent, musicTrackContent, soundTrackContent)
            .forEach { setTimelineContentWidth(it, widthDp) }

        timelineRulerContent.removeAllViews()
        if (duration <= 0L) {
            addTimedTimelineChip(timelineRulerContent, "00:00.000", Color.rgb(38, 43, 53), Color.LTGRAY, 94, 0) {}
        } else {
            val totalSeconds = ceil(duration / 1000.0).toInt()
            val step = if (totalSeconds > 180) 30 else if (totalSeconds > 60) 10 else 5
            var tick = 0
            while (tick <= totalSeconds) {
                val mark = TextView(this).apply {
                    text = formatMs(tick * 1000L)
                    textSize = 9f
                    setTextColor(Color.rgb(165, 174, 190))
                    gravity = Gravity.CENTER_VERTICAL or Gravity.START
                    setPadding(dp(3), 0, 0, 0)
                }
                val tickWidth = (scale * step).toInt().coerceAtLeast(32)
                timelineRulerContent.addView(mark, LinearLayout.LayoutParams(dp(tickWidth), dp(31)))
                tick += step
            }
        }

        // Each text overlay is an independent row with its own start and end.
        textTrackContent.removeAllViews()
        if (textLayers.isEmpty()) {
            addTimelineLayerRow(textTrackContent, "+ Metin ekle", Color.rgb(89, 82, 156), Color.WHITE, 0, 92) {
                onOpenTextTool?.invoke()
            }
        } else {
            textLayers.forEachIndexed { index, layer ->
                val startDp = (layer.startMs.coerceAtLeast(0L) / 1000f * scale).toInt().coerceIn(0, widthDp - 38)
                val layerDuration = (layer.endMs - layer.startMs).coerceAtLeast(1L)
                val layerWidth = ((layerDuration / 1000f) * scale).toInt().coerceAtLeast(48)
                    .coerceAtMost((widthDp - startDp).coerceAtLeast(38))
                val name = layer.text.trim().ifBlank { "Metin ${index + 1}" }.let {
                    if (it.length > 16) it.take(16) + "…" else it
                }
                addTimelineLayerRow(
                    textTrackContent,
                    "T${index + 1}  $name",
                    if (layer.id == selectedTextLayerId) Color.rgb(177, 139, 255) else Color.rgb(116, 87, 178),
                    if (layer.id == selectedTextLayerId) Color.BLACK else Color.WHITE,
                    startDp,
                    layerWidth
                ) { selectTextLayer(layer) }
            }
        }

        // Every music track spans the output timeline, each on its own row.
        musicTrackContent.removeAllViews()
        if (musicLayers.isEmpty()) {
            addTimelineLayerRow(musicTrackContent, "+ Müzik ekle", Color.rgb(42, 92, 132), Color.WHITE, 0, 104) {
                chooseAudio(true)
            }
        } else {
            musicLayers.forEachIndexed { index, layer ->
                addTimelineLayerRow(
                    musicTrackContent,
                    "♪ M${index + 1}  ${layer.title.take(18)}",
                    if (layer.id == selectedMusicLayerId) Color.rgb(90, 175, 255) else Color.rgb(37, 101, 149),
                    if (layer.id == selectedMusicLayerId) Color.BLACK else Color.WHITE,
                    0,
                    (widthDp - 4).coerceAtLeast(38)
                ) { selectAudioLayer(layer, isMusic = true) }
            }
        }

        // Every sound effect has its own start position and duration.
        soundTrackContent.removeAllViews()
        if (soundLayers.isEmpty()) {
            addTimelineLayerRow(soundTrackContent, "+ Ses efekti ekle", Color.rgb(130, 82, 40), Color.WHITE, 0, 124) {
                chooseAudio(false)
            }
        } else {
            soundLayers.forEachIndexed { index, layer ->
                val startDp = (layer.startMs.coerceAtLeast(0L) / 1000f * scale).toInt().coerceIn(0, widthDp - 38)
                val remaining = (duration - layer.startMs).coerceAtLeast(0L)
                val displayedDuration = minOf(layer.durationMs, remaining)
                val layerWidth = ((displayedDuration / 1000f) * scale).toInt().coerceAtLeast(44)
                    .coerceAtMost((widthDp - startDp).coerceAtLeast(38))
                addTimelineLayerRow(
                    soundTrackContent,
                    "♫ S${index + 1}  ${layer.title.take(14)}",
                    if (layer.id == selectedSoundLayerId) Color.rgb(255, 174, 84) else Color.rgb(154, 98, 46),
                    if (layer.id == selectedSoundLayerId) Color.BLACK else Color.WHITE,
                    startDp,
                    layerWidth
                ) { selectAudioLayer(layer, isMusic = false) }
            }
        }
    }

    private fun addTimelineLayerRow(
        host: LinearLayout,
        title: String,
        fill: Int,
        textColor: Int,
        startDp: Int,
        widthDp: Int,
        action: () -> Unit
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(33)
        }
        if (startDp > 0) {
            row.addView(View(this), LinearLayout.LayoutParams(dp(startDp), dp(31)))
        }
        addTimedTimelineChip(row, title, fill, textColor, widthDp, 0, action)
        host.addView(row, LinearLayout.LayoutParams(dp(timelineContentWidthDp()), dp(34)).apply {
            bottomMargin = dp(1)
        })
    }

    private fun removeSelectedClip() {
        if (selectedClipIndex !in clips.indices) {
            status.text = "Önce kaldırılacak klibi seç."
            return
        }
        recordUndoState()
        clips.removeAt(selectedClipIndex)
        selectedClipIndex = if (clips.isEmpty()) -1 else selectedClipIndex.coerceAtMost(clips.lastIndex)
        previewingSequence = false
        previewTimelineOffsetMs = outputOffsetForClip(selectedClipIndex)
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
        recordUndoState()
        val clip = clips.removeAt(selectedClipIndex)
        clips.add(target, clip)
        selectedClipIndex = target
        previewingSequence = false
        previewTimelineOffsetMs = outputOffsetForClip(selectedClipIndex)
        refreshClipList()
        loadTrimControls()
        player?.setMediaItem(clippedMediaItem(clip))
        player?.volume = if (keepSourceAudio.isChecked) sourceAudioVolume / 100f else 0f
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
                val minGap = (1000f / clip.frameRate.coerceIn(1f, 120f)).roundToLong().coerceAtLeast(1L)
                clip.startMs = msForFrame(clip, progress).coerceAtMost((clip.endMs - minGap).coerceAtLeast(0L))
                updateTrimLabels()
                refreshClipList()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) { recordUndoState() }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        endSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val clip = selectedClip() ?: return
                val minGap = (1000f / clip.frameRate.coerceIn(1f, 120f)).roundToLong().coerceAtLeast(1L)
                clip.endMs = msForFrame(clip, progress).coerceAtLeast((clip.startMs + minGap).coerceAtMost(clip.durationMs))
                    .coerceAtMost(clip.durationMs)
                updateTrimLabels()
                refreshClipList()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) { recordUndoState() }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun loadTrimControls() {
        val clip = selectedClip()
        if (clip == null) {
            selectedClipLabel.text = "Önce + Video ile klip ekle."
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
        val frameCount = framesFor(clip)
        startSeek.max = frameCount
        endSeek.max = frameCount
        startSeek.progress = frameForMs(clip, clip.startMs)
        endSeek.progress = frameForMs(clip, clip.endMs).coerceAtMost(frameCount)
        selectedClipLabel.text = "Seçili klip: ${clip.title} • ${formatMs(clip.durationMs)} • ${clip.frameRate.toInt()} FPS"
        updateTrimLabels()
    }

    private fun updateTrimLabels() {
        val clip = selectedClip()
        startLabel.text = "Başlangıç: ${formatPreciseMs(clip?.startMs ?: 0L)}"
        endLabel.text = "Bitiş: ${formatPreciseMs(clip?.endMs ?: 0L)}"
        updateSoundControls()
        updateTextControls()
        updatePlayheadUi(currentTimelinePositionMs())
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
        refreshTimelineTracks()
    }

    private fun previewSelectedClip() {
        if (clips.isEmpty()) {
            status.text = "Önizleme için önce video ekle."
            return
        }
        selectedClipIndex = selectedClipIndex.coerceIn(0, clips.lastIndex)
        previewingSequence = true
        previewTimelineOffsetMs = outputOffsetForClip(selectedClipIndex)
        player?.volume = if (keepSourceAudio.isChecked) sourceAudioVolume / 100f else 0f
        player?.setMediaItems(clips.map { clippedMediaItem(it) }, selectedClipIndex, 0L)
        player?.prepare()
        player?.playWhenReady = true
        refreshClipList()
        loadTrimControls()
        refreshPreviewOverlay()
        status.text = "Tüm zaman çizelgesi oynatılıyor. Yazı, müzik ve ses efektleri zamanlarına göre önizlenir."
    }

    private fun buildClipSegment(
        clip: Clip,
        localStartMs: Long,
        localEndMs: Long,
        visibleTextLayers: List<TextLayer>
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

        val videoEffects = mutableListOf<androidx.media3.common.Effect>()
        val (outputWidth, outputHeight) = outputDimensions()
        videoEffects.add(Presentation.createForWidthAndHeight(
            outputWidth, outputHeight, Presentation.LAYOUT_SCALE_TO_FIT
        ))
        val textOverlays = visibleTextLayers.filter { it.text.isNotBlank() }.map { layer ->
            val styled = SpannableString(layer.text).apply {
                setSpan(ForegroundColorSpan(Color.WHITE), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(BackgroundColorSpan(Color.argb(190, 0, 0, 0)), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(StyleSpan(Typeface.BOLD), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            val y = when (layer.position) {
                TextPosition.TOP -> 0.78f
                TextPosition.CENTER -> 0f
                TextPosition.BOTTOM -> -0.78f
            }
            val settings = OverlaySettings.Builder()
                .setBackgroundFrameAnchor(0f, y)
                .setOverlayFrameAnchor(0f, 0f)
                .build()
            TextOverlay.createStaticTextOverlay(styled, settings)
        }
        if (textOverlays.isNotEmpty()) videoEffects.add(OverlayEffect(textOverlays))
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
        syncSelectedTextLayerFromControls()
        syncSelectedAudioLayerFromControls()
        val invalid = clips.firstOrNull { it.startMs < 0 || it.endMs <= it.startMs || it.endMs > it.durationMs }
        if (invalid != null) {
            status.text = "Bir klibin kesme aralığı geçersiz. Başlangıç ve bitişi kontrol et."
            return
        }

        val requestedExportName = exportNameInput.text?.toString().orEmpty()
        val videoItems = try {
            val builtItems = mutableListOf<EditedMediaItem>()
            var timelineOffsetMs = 0L
            clips.forEach { clip ->
                val duration = clip.endMs - clip.startMs
                val boundaries = sortedSetOf(0L, duration)
                textLayers.forEach { layer ->
                    if (layer.startMs > timelineOffsetMs && layer.startMs < timelineOffsetMs + duration) {
                        boundaries.add(layer.startMs - timelineOffsetMs)
                    }
                    if (layer.endMs > timelineOffsetMs && layer.endMs < timelineOffsetMs + duration) {
                        boundaries.add(layer.endMs - timelineOffsetMs)
                    }
                }
                val points = boundaries.toList()
                for (i in 0 until points.lastIndex) {
                    val localStart = points[i]
                    val localEnd = points[i + 1]
                    if (localEnd <= localStart) continue
                    val globalStart = timelineOffsetMs + localStart
                    val activeText = textLayers.filter { layer ->
                        layer.text.isNotBlank() && globalStart >= layer.startMs && globalStart < layer.endMs
                    }
                    builtItems.add(buildClipSegment(clip, localStart, localEnd, activeText))
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
        updateUndoRedoButtons()
        status.text = "MP4 dışa aktarılıyor…\nKlipler, metin katmanları ve ses katmanları uygulanıyor. Uygulamayı kapatma."
        val tempOutput = File(cacheDir, "mm_editor_${System.currentTimeMillis()}.mp4")
        val videoSequence = EditedMediaItemSequence.Builder(videoItems).build()
        val compositionSequences = mutableListOf(videoSequence)
        val outputDurationMs = totalOutputDurationMs().coerceAtLeast(1L)

        // Repeat music as a finite set of trimmed items: fade-in happens only at the start
        // and fade-out only at the end of the entire output, not on every loop.
        musicLayers.forEach { layer ->
            val musicItems = mutableListOf<EditedMediaItem>()
            var remainingMs = outputDurationMs
            var elapsedMs = 0L
            val sourceDurationMs = layer.durationMs.coerceAtLeast(1L)
            while (remainingMs > 0L) {
                val partDurationMs = minOf(sourceDurationMs, remainingMs)
                val mediaItem = MediaItem.Builder()
                    .setUri(layer.uri)
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(0L)
                            .setEndPositionMs(partDurationMs)
                            .build()
                    )
                    .build()
                val fadeIn = if (elapsedMs == 0L) layer.fadeInMs else 0L
                val fadeOut = if (remainingMs == partDurationMs) layer.fadeOutMs else 0L
                musicItems.add(
                    EditedMediaItem.Builder(mediaItem)
                        .setRemoveVideo(true)
                        .setEffects(Effects(audioEffects(layer.volume, fadeIn, fadeOut, partDurationMs), emptyList()))
                        .build()
                )
                elapsedMs += partDurationMs
                remainingMs -= partDurationMs
            }
            compositionSequences.add(EditedMediaItemSequence.Builder(musicItems).build())
        }

        soundLayers.forEach { layer ->
            val offsetMs = layer.startMs.coerceAtMost((outputDurationMs - 1L).coerceAtLeast(0L))
            val visibleDurationMs = minOf(layer.durationMs.coerceAtLeast(1L), (outputDurationMs - offsetMs).coerceAtLeast(1L))
            val soundMediaItem = MediaItem.Builder()
                .setUri(layer.uri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(0L)
                        .setEndPositionMs(visibleDurationMs)
                        .build()
                )
                .build()
            val soundItem = EditedMediaItem.Builder(soundMediaItem)
                .setRemoveVideo(true)
                .setEffects(Effects(audioEffects(layer.volume, layer.fadeInMs, layer.fadeOutMs, visibleDurationMs), emptyList()))
                .build()
            val sequence = EditedMediaItemSequence.Builder().apply {
                if (offsetMs > 0L) addGap(offsetMs * 1000L)
                addItem(soundItem)
            }.build()
            compositionSequences.add(sequence)
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
                                updateUndoRedoButtons()
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
                            updateUndoRedoButtons()
                            status.text = "DIŞA AKTARMA BAŞARISIZ\n${exportException.message ?: "Kod ${exportException.errorCode}"}"
                        }
                    }
                })
                .build()
        } catch (e: Exception) {
            exporting = false
            exportButton.isEnabled = true
            updateUndoRedoButtons()
            status.text = "Dışa aktarıcı başlatılamadı: ${e.message}"
            return
        }

        try {
            transformer.start(composition, tempOutput.absolutePath)
        } catch (e: Exception) {
            tempOutput.delete()
            exporting = false
            exportButton.isEnabled = true
            updateUndoRedoButtons()
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

    private fun formatPreciseMs(ms: Long): String {
        val value = ms.coerceAtLeast(0L)
        val totalSeconds = value / 1000L
        return String.format(Locale.US, "%02d:%02d.%03d", totalSeconds / 60L, totalSeconds % 60L, value % 1000L)
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

    override fun onStart() {
        super.onStart()
        previewHandler.removeCallbacks(previewTicker)
        previewHandler.post(previewTicker)
    }

    override fun onStop() {
        previewHandler.removeCallbacks(previewTicker)
        player?.pause()
        audioPreviewPlayers.values.forEach { it.pause() }
        super.onStop()
    }

    override fun onDestroy() {
        previewHandler.removeCallbacks(previewTicker)
        playerView.player = null
        player?.release()
        audioPreviewPlayers.values.forEach { it.release() }
        audioPreviewPlayers.clear()
        audioPreviewSources.clear()
        player = null
        super.onDestroy()
    }
}

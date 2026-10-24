package com.mathmotor.video

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.graphics.Bitmap
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
import android.text.style.AbsoluteSizeSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
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
import androidx.media3.effect.Presentation
import androidx.media3.common.OverlaySettings
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.effect.TextOverlay
import androidx.media3.inspector.frame.FrameExtractor
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
import java.lang.ref.WeakReference
import java.util.Locale
import java.util.concurrent.TimeUnit
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

    private data class ClipEditSnapshot(
        val clips: List<Clip>,
        val selectedClipIndex: Int,
        val timelinePlayheadMs: Long,
        val previewSingleClip: Boolean
    )

    private val clips = mutableListOf<Clip>()
    private var draftId: String? = null
    private val undoClipHistory = mutableListOf<ClipEditSnapshot>()
    private val redoClipHistory = mutableListOf<ClipEditSnapshot>()
    private var activeTrimHistorySnapshot: ClipEditSnapshot? = null
    private var undoClipButton: Button? = null
    private var redoClipButton: Button? = null
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
    private var textSizeSp = 24
    private var textColorPreset = TextColorPreset.WHITE
    private var textBold = true
    private lateinit var textColorGroup: android.widget.RadioGroup
    private lateinit var textBoldCheck: CheckBox
    private lateinit var textOverlayEditToggle: Button
    private enum class TextPosition { TOP, CENTER, BOTTOM }
    private fun defaultTextYFraction(position: TextPosition): Float = when (position) {
        TextPosition.TOP -> 0.16f
        TextPosition.CENTER -> 0.50f
        TextPosition.BOTTOM -> 0.84f
    }
    private enum class TextColorPreset(val colorInt: Int) {
        WHITE(Color.WHITE),
        YELLOW(Color.rgb(255, 221, 74)),
        CYAN(Color.rgb(65, 224, 255)),
        RED(Color.rgb(255, 90, 105)),
        GREEN(Color.rgb(103, 245, 154))
    }

    private data class TextLayer(
        val id: Long,
        var text: String,
        var startMs: Long,
        var endMs: Long,
        var timingCustomized: Boolean,
        var position: TextPosition,
        var sizeSp: Int,
        var colorPreset: TextColorPreset,
        var isBold: Boolean,
        var xFraction: Float = 0.5f,
        var yFraction: Float = 0.84f
    )

    private val textLayers = mutableListOf<TextLayer>()
    private var selectedTextLayerId: Long? = null
    private var nextTextLayerId = 1L
    private var loadingTextLayerControls = false
    private var activePreviewTextLayers: List<TextLayer> = emptyList()
    private val previewTextOverlays = mutableListOf<TextView>()
    private lateinit var textLayerList: LinearLayout
    private var player: ExoPlayer? = null
    private var musicPreviewPlayer: ExoPlayer? = null
    private var soundPreviewPlayer: ExoPlayer? = null
    private var musicPreviewUri: Uri? = null
    private var soundPreviewUri: Uri? = null
    private lateinit var playerView: PlayerView
    private lateinit var previewFrame: FrameLayout
    private lateinit var previewFocusButton: Button
    private var previewStageHeightDp = 340
    private var previewDefaultStageHeightDp = 340
    private var previewHeightAutoSized = false
    private var compactEditorLayout = false
    private var previewFocusMode = false
    private val previewFocusOriginalVisibility = linkedMapOf<View, Int>()
    private lateinit var previewTextOverlay: TextView
    private lateinit var previewEmptyState: LinearLayout
    private lateinit var clipList: LinearLayout
    private lateinit var timelineTracks: LinearLayout
    private lateinit var timelineTracksFrame: FrameLayout
    private lateinit var timelinePlayheadLine: View
    private val timelineScrollViews = mutableListOf<android.widget.HorizontalScrollView>()
    private var syncingTimelineScroll = false
    private var timelineHorizontalScrollX = 0
    private var pendingTimelineScrollX: Int? = null
    private var timelineZoom = 1.0f
    private var deferTimelineRefresh = false
    private val timelineThumbnailCache = object : android.util.LruCache<String, Bitmap>(256) {}
    private val pendingTimelineThumbnails = mutableMapOf<String, MutableList<WeakReference<ImageView>>>()
    private val timelineThumbnailExecutor = java.util.concurrent.Executors.newFixedThreadPool(2)
    private lateinit var timelinePlayheadSeek: SeekBar
    private lateinit var timelinePlayheadLabel: TextView
    private var timelinePlayheadMs = 0L
    private var timelineScrubGrabOffsetPx = 0f
    private var timelinePreviewLoaded = false
    private var previewSingleClip = false
    private val timelineHandler = Handler(Looper.getMainLooper())
    private val textTimelineRefreshTask = object : Runnable {
        override fun run() { refreshTimelineTracks() }
    }
    private val timelineSyncTask = object : Runnable {
        override fun run() {
            if (timelinePreviewLoaded) syncTimelinePlayheadFromPlayer()
            else if (previewSingleClip) syncSingleClipPlayheadFromPlayer()
            syncPreviewAudio()
            timelineHandler.postDelayed(this, 250L)
        }
    }
    private val editorPanels = linkedMapOf<String, LinearLayout>()
    private lateinit var editorPanelScroll: ScrollView
    private val editorTabButtons = linkedMapOf<String, Button>()
    private var activeEditorPanel: String? = null
    private var textOverlayEditingEnabled: Boolean = false
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
    private lateinit var textSizeLabel: TextView
    private lateinit var textSizeSeek: SeekBar
    private lateinit var textInput: EditText
    private lateinit var keepSourceAudio: CheckBox
    private lateinit var status: TextView
    private lateinit var exportButton: Button
    private lateinit var exportNameInput: EditText
    private lateinit var exportResolutionGroup: android.widget.RadioGroup
    private var exportResolutionShortSide = 0
    private var exporting = false
    private var keyboardLayoutActive = false
    private var maxEditorViewportHeightPx = 0
    private var applyingResponsiveLayout = false

    companion object {
        private const val REQUEST_MAIN_VIDEO = 5101
        private const val REQUEST_ADD_VIDEO = 5102
        private const val REQUEST_MUSIC = 5103
        private const val REQUEST_SOUND = 5104
        private const val REQUEST_SAVE_PROJECT = 5105
        private const val REQUEST_OPEN_PROJECT = 5106
        private const val REQUEST_LEGACY_STORAGE = 5107
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(15, 31, 45)
        window.navigationBarColor = Color.rgb(15, 31, 45)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val requestedDraftId = intent.getStringExtra("draft_id")
            ?.takeIf { VideoProjectDraftStore.isValidId(it) }
        draftId = requestedDraftId ?: VideoProjectDraftStore.newId()
        buildUi()
        player = ExoPlayer.Builder(this).build().also { exo ->
            exo.volume = if (keepSourceAudio.isChecked) sourceAudioVolume / 100f else 0f
            playerView.player = exo
            exo.addListener(object : androidx.media3.common.Player.Listener {
                override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                    playerView.post { updatePreviewCanvasSize() }
                }
            })
        }
        timelineHandler.post(timelineSyncTask)
        val incoming = intent.getStringExtra("video_uri")
        if ((applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            android.util.Log.i("EditorSmokeTest", "EDITOR_SMOKE_EDITOR_STARTED uri=$incoming")
        }
        val savedDraft = requestedDraftId?.let { VideoProjectDraftStore.load(this, it) }
        if (savedDraft != null) {
            runCatching { applyProjectJson(savedDraft) }
                .onSuccess { status.text = "Taslak açıldı. Düzenlemeye kaldığın yerden devam edebilirsin." }
                .onFailure {
                    status.text = "Taslak açılamadı: ${it.message ?: "proje verisi okunamadı"}"
                    if (!incoming.isNullOrBlank()) addClip(Uri.parse(incoming), false)
                }
        } else if (!incoming.isNullOrBlank()) {
            addClip(Uri.parse(incoming), false)
        } else if ((applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            android.util.Log.e("EditorSmokeTest", "EDITOR_SMOKE_MISSING_VIDEO_URI")
        }
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
            minHeight = dp(44)
            setPadding(dp(8), 0, dp(8), 0)
            val buttonShape = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.rgb(27, 43, 59))
                cornerRadius = dp(9).toFloat()
                setStroke(dp(1), Color.rgb(47, 66, 84))
            }
            background = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(Color.rgb(71, 111, 132)),
                buttonShape,
                null
            )
            isHapticFeedbackEnabled = true
            setOnClickListener {
                performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            }
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        topBar.addView(miniButton("‹ Geri") { finish() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)))
        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, dp(4), 0)
            addView(label("Düzenle", 18f, Color.WHITE, true))
            addView(label("YEREL VİDEO DÜZENLEME", 10f, Color.rgb(154, 171, 190), false))
        }
        topBar.addView(heading, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        topBar.addView(miniButton("Dışa aktar ›") { showEditorPanel("export") },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42)))
        root.addView(topBar, matchWrap())
        root.addView(gap(6))

        playerView = PlayerView(this).apply {
            setBackgroundColor(Color.BLACK)
            // Native controls show the source clip duration, not the combined project duration.
            // Keep playback aligned with the edited timeline controls below.
            useController = false
            contentDescription = "Düzenlenmiş video önizlemesi"
        }
        previewFrame = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(5, 9, 15))
        }
        previewFrame.addView(
            playerView,
            FrameLayout.LayoutParams(dp(180), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER)
        )
        previewTextOverlay = TextView(this).apply {
            textSize = textSizeSp.toFloat()
            gravity = Gravity.CENTER
            maxLines = 2
            maxWidth = dp(170)
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
        previewTextOverlays.add(previewTextOverlay)
        installPreviewTextTouch(previewTextOverlay)
        previewEmptyState = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.rgb(17, 28, 41))
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), Color.rgb(53, 87, 102))
            }
            isClickable = true
            isFocusable = true
            contentDescription = "Galeriden video ekle"
            setOnClickListener { chooseVideo(true) }
            addView(TextView(this@VideoEditorActivity).apply {
                text = "＋"
                textSize = 31f
                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.rgb(57, 220, 190))
            }, LinearLayout.LayoutParams(dp(62), dp(52)))
            addView(TextView(this@VideoEditorActivity).apply {
                text = "Videonu ekle"
                textSize = 15f
                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
            })
            addView(TextView(this@VideoEditorActivity).apply {
                text = "Başlamak için dokun"
                textSize = 11f
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(154, 174, 195))
                setPadding(0, dp(3), 0, 0)
            })
        }
        previewFrame.addView(
            previewEmptyState,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        )
        val previewSizeControls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            elevation = dp(12).toFloat()
            setPadding(dp(4), 0, dp(4), 0)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.argb(205, 8, 15, 22))
                cornerRadius = dp(9).toFloat()
            }
            addView(label("VİDEO", 9f, Color.WHITE, true), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = dp(4); rightMargin = dp(2) })
            addView(miniButton("−") { adjustPreviewStageHeight(-30) },
                LinearLayout.LayoutParams(dp(38), dp(40)))
            addView(miniButton("↺") { setPreviewStageHeight(previewDefaultStageHeightDp) },
                LinearLayout.LayoutParams(dp(42), dp(40)))
            addView(miniButton("+") { adjustPreviewStageHeight(30) },
                LinearLayout.LayoutParams(dp(38), dp(40)))
            previewFocusButton = miniButton("⛶") { togglePreviewFocusMode() }
            previewFocusButton.contentDescription = "Videoyu geniş ekranda aç"
            addView(previewFocusButton, LinearLayout.LayoutParams(dp(42), dp(40)))
            contentDescription = "Video önizlemesini küçült, varsayılana döndür veya büyüt"
        }
        previewFrame.addView(previewSizeControls, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, dp(44), Gravity.TOP or Gravity.END
        ).apply { topMargin = dp(6); rightMargin = dp(6) })
        root.addView(previewFrame, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(previewStageHeightDp)
        ).apply { bottomMargin = dp(8) })
        previewFrame.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            updatePreviewCanvasSize()
        }

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
        timelineHeader.addView(label("ZAMAN ÇİZELGESİ", 11f, Color.rgb(121, 202, 218), true).apply {
            setSingleLine(true)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = dp(4)
        })
        timelineHeader.addView(miniButton("▶Ⅱ") {
            if (clips.isEmpty()) {
                chooseVideo(true)
            } else {
                ensureTimelinePreviewLoaded()
                val currentPlayer = player
                if (currentPlayer != null && currentPlayer.isPlaying) {
                    currentPlayer.pause()
                    status.text = "Önizleme duraklatıldı."
                } else {
                    currentPlayer?.playWhenReady = true
                    status.text = "Proje önizlemesi oynatılıyor."
                }
            }
        }.apply {
            minWidth = dp(38)
            minimumWidth = dp(38)
            textSize = 13f
            setPadding(0, 0, 0, 0)
            contentDescription = "Önizlemeyi oynat veya duraklat"
        }, LinearLayout.LayoutParams(dp(42), dp(40)))
        timelineHeader.addView(miniButton("＋") { chooseVideo(true) }.apply {
            minWidth = dp(38)
            minimumWidth = dp(38)
            textSize = 18f
            setPadding(0, 0, 0, 0)
            contentDescription = "Zaman çizelgesine video ekle"
        }, LinearLayout.LayoutParams(dp(42), dp(40)).apply { leftMargin = dp(3) })
        timelineHeader.addView(miniButton("↻") { chooseVideo(false) }.apply {
            minWidth = dp(38)
            minimumWidth = dp(38)
            textSize = 16f
            setPadding(0, 0, 0, 0)
            contentDescription = "Seçili videoyu değiştir"
        }, LinearLayout.LayoutParams(dp(42), dp(40)).apply { leftMargin = dp(3) })
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
        timelinePlayheadLine = FrameLayout(this).apply {
            isClickable = true
            elevation = dp(4).toFloat()
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            addView(
                View(this@VideoEditorActivity).apply {
                    setBackgroundColor(Color.rgb(255, 78, 92))
                },
                FrameLayout.LayoutParams(dp(2), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
            )
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        if (clips.isEmpty()) return@setOnTouchListener false
                        ensureTimelinePreviewLoaded()
                        player?.playWhenReady = false
                        // Preserve where the finger grabbed the handle so touching an edge
                        // does not jump the playhead by half the handle width.
                        timelineScrubGrabOffsetPx = event.x - view.width / 2f
                        seekFromTimelineFrameX(view.x + event.x - timelineScrubGrabOffsetPx)
                        true
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        seekFromTimelineFrameX(view.x + event.x - timelineScrubGrabOffsetPx)
                        true
                    }
                    android.view.MotionEvent.ACTION_UP -> {
                        seekFromTimelineFrameX(view.x + event.x - timelineScrubGrabOffsetPx)
                        view.performClick()
                        true
                    }
                    android.view.MotionEvent.ACTION_CANCEL -> true
                    else -> true
                }
            }
        }
        timelinePlayheadLine.setOnClickListener { /* Touch position is handled by the scrub listener. */ }
        timelineTracksFrame.addView(
            timelinePlayheadLine,
            FrameLayout.LayoutParams(dp(28), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.TOP or Gravity.START)
        )
        timelineCard.addView(timelineTracksFrame, matchWrap())
        timelineCard.addView(label("Uzun bas: klibi taşı / sil / kopyala  •  Kenarlardan: kes", 9f, Color.rgb(180, 202, 216), false).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(3), dp(2), dp(3), 0)
            contentDescription = "Klibe uzun basarak taşı, sil veya kopyala; kesmek için kenarları sürükle"
        }, matchWrap())

        val playheadRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(3), dp(3), dp(3), 0)
        }
        timelinePlayheadLabel = label("00:00.0 / 00:00.0", 9f, Color.rgb(225, 233, 237), true).apply { maxLines = 2; setLineSpacing(-dp(2).toFloat(), 1f) }
        playheadRow.addView(timelinePlayheadLabel, LinearLayout.LayoutParams(dp(128), ViewGroup.LayoutParams.WRAP_CONTENT))
        timelinePlayheadSeek = SeekBar(this).apply {
            max = 0
            progress = 0
            isEnabled = false
            contentDescription = "Zaman çizelgesinde oynatma noktası"
        }
        playheadRow.addView(timelinePlayheadSeek, LinearLayout.LayoutParams(0, dp(34), 1f))
        playheadRow.addView(miniButton("−") { adjustTimelineZoom(0.75f) },
            LinearLayout.LayoutParams(dp(42), dp(42)).apply { leftMargin = dp(3) })
        playheadRow.addView(miniButton("+") { adjustTimelineZoom(1.35f) },
            LinearLayout.LayoutParams(dp(42), dp(42)).apply { leftMargin = dp(2) })
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
            LinearLayout.LayoutParams(0, dp(46), 1f))
        clipActions1.addView(miniButton("Kopyala") {
            if (selectedClipIndex in clips.indices) {
                duplicateClipAt(selectedClipIndex)
            } else {
                status.text = "Kopyalamak için önce zaman çizelgesinden bir klip seç."
            }
        }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { leftMargin = dp(4) })
        clipActions1.addView(miniButton("Klibi kaldır") { removeSelectedClip() },
            LinearLayout.LayoutParams(0, dp(46), 1f).apply { leftMargin = dp(4) })
        clipPanel.addView(clipActions1)
        val clipActions2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        clipActions2.addView(miniButton("Böl ✂") { splitSelectedClipAtPlayhead() },
            LinearLayout.LayoutParams(0, dp(42), 1f))
        clipActions2.addView(miniButton("← Öne al") { moveSelectedClip(-1) },
            LinearLayout.LayoutParams(0, dp(42), 1f).apply { leftMargin = dp(4) })
        clipActions2.addView(miniButton("Sona al →") { moveSelectedClip(1) },
            LinearLayout.LayoutParams(0, dp(42), 1f).apply { leftMargin = dp(4) })
        clipPanel.addView(clipActions2)
        val clipHistoryRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        undoClipButton = miniButton("↶ Klip geri al") { undoClipEdit() }.apply {
            isEnabled = false
        }
        redoClipButton = miniButton("↷ Klip yinele") { redoClipEdit() }.apply {
            isEnabled = false
        }
        clipHistoryRow.addView(undoClipButton, LinearLayout.LayoutParams(0, dp(40), 1f))
        clipHistoryRow.addView(redoClipButton, LinearLayout.LayoutParams(0, dp(40), 1f).apply {
            leftMargin = dp(5)
        })
        clipPanel.addView(clipHistoryRow)
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
        textPanel.addView(label("METİN KATMANLARI", 13f, Color.rgb(121, 202, 218), true))
        textPanel.addView(label("Yazıyı videonun üstünde sürükle; iki parmakla büyüt veya küçült.", 11f, Color.rgb(154, 171, 190), false))
        textOverlayEditToggle = miniButton("Önizlemede yazı düzenleme: KAPALI") {
            textOverlayEditingEnabled = !textOverlayEditingEnabled
            updateTextOverlayEditToggle()
            status.text = if (textOverlayEditingEnabled) {
                "Yazıyı videonun üzerinde sürükleyebilir ve iki parmakla boyutlandırabilirsin."
            } else {
                "Önizlemede yazı hareketi kapalı. Metin alanı ve boyut düğmeleri kullanılabilir."
            }
        }.apply {
            contentDescription = "Önizleme üzerindeki yazıyı sürükleme ve iki parmakla boyutlandırmayı aç veya kapat"
            minHeight = dp(42)
        }
        textPanel.addView(textOverlayEditToggle, matchWrap())
        updateTextOverlayEditToggle()
        val textLayerActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        textLayerActions.addView(miniButton("＋ Yeni") { addTextLayer() },
            LinearLayout.LayoutParams(0, dp(42), 1f))
        textLayerActions.addView(miniButton("Kopyala") { duplicateSelectedTextLayer() },
            LinearLayout.LayoutParams(0, dp(42), 1f).apply { leftMargin = dp(4) })
        textLayerActions.addView(miniButton("Sil") { removeSelectedTextLayer() },
            LinearLayout.LayoutParams(0, dp(42), 1f).apply { leftMargin = dp(4) })
        textPanel.addView(textLayerActions)
        val textLayerScroll = android.widget.HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        textLayerList = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(4))
        }
        textLayerScroll.addView(textLayerList)
        textPanel.addView(textLayerScroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(48)
        ))
        textInput = EditText(this).apply {
            hint = "Videoda görünecek yazı"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(154, 171, 190))
            setSingleLine(false)
            minLines = 1
            maxLines = 2
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE or
                android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI
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
                if (!loadingTextLayerControls) {
                    selectedTextLayer()?.let { it.yFraction = defaultTextYFraction(textPosition) }
                    syncSelectedTextLayerFromControls()
                }
                updatePreviewTextOverlay()
                refreshTimelineTracks()
            }
        }
        textPanel.addView(textPositionGroup, matchWrap())
        textPanel.addView(label("Yazı rengi", 11f, Color.rgb(154, 171, 190), false))
        textColorGroup = android.widget.RadioGroup(this).apply {
            orientation = android.widget.RadioGroup.HORIZONTAL
            TextColorPreset.values().forEachIndexed { index, preset ->
                addView(android.widget.RadioButton(this@VideoEditorActivity).apply {
                    id = 11 + index
                    text = when (preset) {
                        TextColorPreset.WHITE -> "Beyaz"
                        TextColorPreset.YELLOW -> "Sarı"
                        TextColorPreset.CYAN -> "Camgöbeği"
                        TextColorPreset.RED -> "Kırmızı"
                        TextColorPreset.GREEN -> "Yeşil"
                    }
                    textSize = 10f
                    setTextColor(preset.colorInt)
                    buttonTintList = android.content.res.ColorStateList.valueOf(preset.colorInt)
                    minHeight = dp(38)
                    setPadding(dp(2), 0, dp(4), 0)
                })
            }
            check(11)
            setOnCheckedChangeListener { _, checkedId ->
                if (loadingTextLayerControls) return@setOnCheckedChangeListener
                textColorPreset = TextColorPreset.values().getOrNull(checkedId - 11)
                    ?: TextColorPreset.WHITE
                syncSelectedTextLayerFromControls()
                updatePreviewTextOverlay()
            }
        }
        val textColorScroll = android.widget.HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        textColorScroll.addView(textColorGroup, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        textPanel.addView(textColorScroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(44)
        ))
        textBoldCheck = CheckBox(this).apply {
            text = "Kalın yazı"
            textSize = 12f
            setTextColor(Color.WHITE)
            buttonTintList = android.content.res.ColorStateList.valueOf(Color.rgb(57, 220, 190))
            isChecked = true
            setOnCheckedChangeListener { _, checked ->
                if (loadingTextLayerControls) return@setOnCheckedChangeListener
                textBold = checked
                syncSelectedTextLayerFromControls()
                updatePreviewTextOverlay()
            }
        }
        textPanel.addView(textBoldCheck, matchWrap())
        textSizeLabel = label("Yazı boyutu: $textSizeSp", 12f, Color.WHITE, true)
        val textSizeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(textSizeLabel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(miniButton("A−") { adjustSelectedTextSize(-4) }, LinearLayout.LayoutParams(dp(52), dp(42)))
            addView(miniButton("A+") { adjustSelectedTextSize(4) }, LinearLayout.LayoutParams(dp(52), dp(42)).apply { leftMargin = dp(4) })
        }
        textPanel.addView(textSizeRow, matchWrap())
        textSizeSeek = SeekBar(this).apply {
            max = 84
            progress = textSizeSp - 12
            contentDescription = "Yazı boyutu"
        }
        textSizeSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                textSizeSp = (progress + 12).coerceIn(12, 96)
                textSizeLabel.text = "Yazı boyutu: $textSizeSp sp"
                if (!loadingTextLayerControls) {
                    syncSelectedTextLayerFromControls()
                    selectedTextLayer()?.sizeSp = textSizeSp
                }
                updatePreviewTextOverlay()
                previewTextOverlays.forEach { it.requestLayout() }
                refreshTextLayerList()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        textPanel.addView(textSizeSeek, matchWrap())
        textInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (loadingTextLayerControls) return
                if (selectedTextLayer() == null && !s.isNullOrBlank()) {
                    val total = totalOutputDurationMs()
                    val layer = TextLayer(
                        id = nextTextLayerId++,
                        text = "",
                        startMs = 0L,
                        endMs = total,
                        timingCustomized = false,
                        position = textPosition,
                        sizeSp = textSizeSp,
                        colorPreset = textColorPreset,
                        isBold = textBold,
                        xFraction = 0.5f,
                        yFraction = defaultTextYFraction(textPosition)
                    )
                    textLayers.add(layer)
                    selectedTextLayerId = layer.id
                }
                syncSelectedTextLayerFromControls()
                refreshTextLayerList()
                updatePreviewTextOverlay()
                timelineHandler.removeCallbacks(textTimelineRefreshTask)
                timelineHandler.postDelayed(textTimelineRefreshTask, 120L)
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        refreshTextLayerList()

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
            musicPreviewPlayer?.volume = value / 100f
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
                soundStartMs = TimelineTrimMath.timeForProgress(progress, total)
                    .coerceAtMost((total - 1L).coerceAtLeast(0L))
                updateSoundControls()
                if (!deferTimelineRefresh) refreshTimelineTracks()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) { deferTimelineRefresh = true }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                deferTimelineRefresh = false
                refreshTimelineTracks()
            }
        })
        audioPanel.addView(soundStartSeek, matchWrap())
        soundVolumeLabel = label("Efekt sesi: 100%", 12f, Color.WHITE, true)
        audioPanel.addView(soundVolumeLabel)
        soundVolumeSeek = SeekBar(this).apply { max = 100; progress = soundVolume }
        soundVolumeSeek.setOnSeekBarChangeListener(volumeListener { value ->
            soundVolume = value
            soundVolumeLabel.text = "Efekt sesi: " + value + "%"
            soundPreviewPlayer?.volume = value / 100f
        })
        audioPanel.addView(soundVolumeSeek, matchWrap())
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
        exportPanel.addView(label("Çıkış çözünürlüğü", 11f, Color.rgb(154, 171, 190), false))
        exportResolutionGroup = android.widget.RadioGroup(this).apply {
            orientation = android.widget.RadioGroup.HORIZONTAL
            listOf(301 to "Kaynak", 302 to "720p", 303 to "1080p").forEach { (optionId, title) ->
                addView(android.widget.RadioButton(this@VideoEditorActivity).apply {
                    id = optionId
                    text = title
                    textSize = 12f
                    setTextColor(Color.WHITE)
                    buttonTintList = android.content.res.ColorStateList.valueOf(Color.rgb(57, 220, 190))
                    minHeight = dp(42)
                    setPadding(dp(4), 0, dp(9), 0)
                }, android.widget.RadioGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ))
            }
            check(301)
            setOnCheckedChangeListener { _, checkedId ->
                exportResolutionShortSide = when (checkedId) {
                    302 -> 720
                    303 -> 1080
                    else -> 0
                }
                status.text = when (exportResolutionShortSide) {
                    720 -> "Dışa aktarma çözünürlüğü 720p olarak seçildi."
                    1080 -> "Dışa aktarma çözünürlüğü 1080p olarak seçildi."
                    else -> "Kaynak video çözünürlüğü korunacak."
                }
            }
        }
        exportPanel.addView(exportResolutionGroup, matchWrap())
        exportPanel.addView(label("Kaynak: orijinal boyut • 720p / 1080p: ölçeklenmiş MP4", 10f, Color.rgb(132, 153, 173), false))
        exportPanel.addView(actionButton("Projeyi farklı kaydet (.JSON)") { requestSaveProject() })
        exportPanel.addView(actionButton("Kayıtlı projeyi aç") { requestOpenProject() })
        exportButton = actionButton("MP4 DIŞA AKTAR") { exportEditedVideo() }
        exportPanel.addView(exportButton)

        editorPanels["clip"] = clipPanel
        editorPanels["text"] = textPanel
        editorPanels["audio"] = audioPanel
        editorPanels["export"] = exportPanel

        editorPanelScroll = ScrollView(this).apply {
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
        editorPanelScroll.addView(panelHolder)
        textInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                textInput.post {
                    textInput.requestRectangleOnScreen(
                        android.graphics.Rect(0, 0, textInput.width, textInput.height), true
                    )
                }
            }
        }
        root.addView(editorPanelScroll, LinearLayout.LayoutParams(
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
            val tab = miniButton("$symbol\n$title") { toggleEditorPanel(key) }.apply {
                textSize = 10f
                minHeight = dp(52)
                setPadding(dp(1), dp(2), dp(1), dp(2))
                maxLines = 2
            }
            editorTabButtons[key] = tab
            dock.addView(tab, LinearLayout.LayoutParams(0, dp(52), 1f).apply {
                if (key != "clip") leftMargin = dp(4)
            })
        }
        root.addView(dock, matchWrap())

        setContentView(root)
        showEditorPanel("clip")
        refreshTextLayerList()
        refreshTimelineTracks()
        installResponsiveEditorLayout(
            root = root,
            previewFrame = previewFrame,
            timelineCard = timelineCard,
            clipScroll = clipScroll,
            panelScroll = editorPanelScroll,
            statusView = status,
            bottomDock = dock,
            topBar = topBar,
            rulerAwareTimelineRefresh = { refreshTimelineTracks() }
        )
    }

    private fun installResponsiveEditorLayout(
        root: LinearLayout,
        previewFrame: FrameLayout,
        timelineCard: LinearLayout,
        clipScroll: android.widget.HorizontalScrollView,
        panelScroll: ScrollView,
        statusView: TextView,
        bottomDock: LinearLayout,
        topBar: LinearLayout,
        rulerAwareTimelineRefresh: () -> Unit
    ) {
        var insetLeft = 0
        var insetTop = 0
        var insetRight = 0
        var insetBottom = 0
        var imeInsetBottom = 0

        fun applySafeRootPadding(imeVisible: Boolean, viewportHeight: Int) {
            // On some devices adjustResize already excludes the IME. On edge-to-edge
            // devices that keep the full viewport, reserve the keyboard inset ourselves.
            val resizedForIme = imeVisible &&
                maxEditorViewportHeightPx - viewportHeight > dp(150)
            val keyboardBottom = if (imeVisible && !resizedForIme) imeInsetBottom else 0
            root.setPadding(
                dp(if (imeVisible) 4 else 8) + insetLeft,
                dp(if (imeVisible) 2 else 4) + insetTop,
                dp(if (imeVisible) 4 else 8) + insetRight,
                dp(if (imeVisible) 2 else 4) + maxOf(insetBottom, keyboardBottom)
            )
        }

        fun applyKeyboardMode(imeVisible: Boolean, viewportHeight: Int) {
            if (imeVisible == keyboardLayoutActive) {
                applySafeRootPadding(imeVisible, viewportHeight)
                return
            }
            val availableHeight = viewportHeight.takeIf { it > 0 } ?: maxEditorViewportHeightPx
            keyboardLayoutActive = imeVisible
            applyingResponsiveLayout = true
            try {
                val previewParams = previewFrame.layoutParams as LinearLayout.LayoutParams
                if (previewFocusMode) {
                    previewParams.height = 0
                    previewParams.weight = 1f
                } else {
                    previewParams.height = if (imeVisible) {
                        (availableHeight * 0.21f).toInt().coerceIn(dp(82), dp(112))
                    } else {
                        dp(previewStageHeightDp).coerceIn(dp(150), dp(460))
                    }
                    previewParams.weight = 0f
                }
                previewFrame.layoutParams = previewParams

                // Hide decorative strips and fixed docks so the active tool gets
                // the visible space left by the keyboard.
                clipScroll.visibility = if (imeVisible) View.GONE else View.VISIBLE
                bottomDock.visibility = if (imeVisible) View.GONE else View.VISIBLE
                statusView.visibility = if (imeVisible) View.GONE else View.VISIBLE
                if (topBar.childCount > 2) {
                    val topAction = topBar.getChildAt(2) as? Button
                    if (topAction != null) {
                        topAction.visibility = View.VISIBLE
                        if (imeVisible) {
                            topAction.text = "Bitti ✓"
                            topAction.setOnClickListener {
                                val focusedView = currentFocus
                                val token = focusedView?.windowToken ?: root.windowToken
                                val keyboard = getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                                    as? android.view.inputmethod.InputMethodManager
                                keyboard?.hideSoftInputFromWindow(token, 0)
                                focusedView?.clearFocus()
                            }
                        } else {
                            topAction.text = "Dışa aktar ›"
                            topAction.setOnClickListener { showEditorPanel("export") }
                        }
                    }
                }

                applySafeRootPadding(imeVisible, availableHeight)
                if (imeVisible) {
                    timelineCard.setPadding(dp(5), dp(2), dp(5), dp(2))
                    timelinePlayheadSeek.layoutParams?.let {
                        it.height = dp(24)
                        timelinePlayheadSeek.layoutParams = it
                    }
                } else {
                    timelineCard.setPadding(dp(8), dp(5), dp(8), dp(5))
                    timelinePlayheadSeek.layoutParams?.let {
                        it.height = dp(34)
                        timelinePlayheadSeek.layoutParams = it
                    }
                }
                rulerAwareTimelineRefresh()
                panelScroll.requestLayout()
                root.requestLayout()
            } finally {
                applyingResponsiveLayout = false
            }
        }

        root.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            if (applyingResponsiveLayout) return@addOnLayoutChangeListener
            val viewportHeight = view.height
            if (viewportHeight <= 0) return@addOnLayoutChangeListener
            if (!previewHeightAutoSized && !keyboardLayoutActive) {
                val density = resources.displayMetrics.density.coerceAtLeast(1f)
                val viewportDp = (viewportHeight / density).toInt()
                compactEditorLayout = viewportDp < 820
                val previewRatio = if (viewportDp < 650) 0.23f else 0.30f
                previewDefaultStageHeightDp = (viewportDp * previewRatio).toInt().coerceIn(150, 340)
                previewStageHeightDp = previewDefaultStageHeightDp
                previewHeightAutoSized = true

                // Reserve room for real tools and the multi-track timeline on shorter phones.
                if (compactEditorLayout) {
                    view.setPadding(dp(4), dp(2), dp(4), dp(2))
                }
                val previewParams = previewFrame.layoutParams as? LinearLayout.LayoutParams
                if (previewParams != null && !previewFocusMode) {
                    previewParams.height = dp(previewStageHeightDp)
                    previewParams.weight = 0f
                    previewFrame.layoutParams = previewParams
                }
                val clipParams = clipScroll.layoutParams as? LinearLayout.LayoutParams
                if (clipParams != null) {
                    clipParams.height = dp(if (compactEditorLayout) 44 else 58)
                    clipScroll.layoutParams = clipParams
                }
                timelineCard.setPadding(
                    dp(if (compactEditorLayout) 5 else 8),
                    dp(if (compactEditorLayout) 2 else 5),
                    dp(if (compactEditorLayout) 5 else 8),
                    dp(if (compactEditorLayout) 2 else 5)
                )
                if (compactEditorLayout) {
                    val timeLabelParams = timelinePlayheadLabel.layoutParams as? LinearLayout.LayoutParams
                    if (timeLabelParams != null) {
                        timeLabelParams.width = dp(94)
                        timelinePlayheadLabel.layoutParams = timeLabelParams
                    }
                }
                rulerAwareTimelineRefresh()
            }
            if (!keyboardLayoutActive && viewportHeight > maxEditorViewportHeightPx) {
                maxEditorViewportHeightPx = viewportHeight
            }
            if (maxEditorViewportHeightPx == 0) maxEditorViewportHeightPx = viewportHeight

            val rootInsets = view.rootWindowInsets
            val imeVisible = if (Build.VERSION.SDK_INT >= 30 && rootInsets != null) {
                rootInsets.isVisible(WindowInsets.Type.ime())
            } else {
                maxEditorViewportHeightPx - viewportHeight > dp(150)
            }
            if (imeVisible != keyboardLayoutActive) {
                applyKeyboardMode(imeVisible, viewportHeight)
            } else if (!imeVisible && !previewFocusMode) {
                val normalHeight = dp(previewStageHeightDp).coerceIn(dp(150), dp(460))
                val params = previewFrame.layoutParams as? LinearLayout.LayoutParams
                if (params != null && (params.height != normalHeight || params.weight != 0f)) {
                    applyingResponsiveLayout = true
                    params.height = normalHeight
                    params.weight = 0f
                    previewFrame.layoutParams = params
                    applyingResponsiveLayout = false
                }
            }
        }

        if (Build.VERSION.SDK_INT >= 30) {
            root.setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                val ime = insets.getInsets(WindowInsets.Type.ime())
                insetLeft = bars.left
                insetTop = bars.top
                insetRight = bars.right
                insetBottom = bars.bottom
                imeInsetBottom = ime.bottom
                val imeVisible = insets.isVisible(WindowInsets.Type.ime())
                view.post {
                    if (imeVisible != keyboardLayoutActive) {
                        applyKeyboardMode(imeVisible, view.height)
                    }
                    applySafeRootPadding(imeVisible, view.height)
                }
                insets
            }
            root.requestApplyInsets()
        }
    }

    /**
     * Fits the preview to the source video's aspect ratio inside a dark stage.
     * Portrait clips get a true 9:16 canvas; landscape clips remain fully visible.
     */
    private fun updatePreviewCanvasSize() {
        if (!::previewFrame.isInitialized || !::playerView.isInitialized) return
        val stageWidth = previewFrame.width
        val stageHeight = previewFrame.height
        if (stageWidth <= 0 || stageHeight <= 0) return

        val videoSize = player?.videoSize
        val ratio = (if (videoSize != null && videoSize.width > 0 && videoSize.height > 0) {
            val rotated = videoSize.unappliedRotationDegrees % 180 != 0
            val width = if (rotated) videoSize.height else videoSize.width
            val height = if (rotated) videoSize.width else videoSize.height
            width.toFloat() / height.toFloat()
        } else {
            9f / 16f
        }).coerceIn(0.25f, 4f)

        val targetWidth = minOf(stageWidth.toFloat(), stageHeight * ratio).toInt().coerceAtLeast(1)
        val targetHeight = minOf(stageHeight.toFloat(), targetWidth / ratio).toInt().coerceAtLeast(1)
        val params = playerView.layoutParams as? FrameLayout.LayoutParams ?: return
        if (params.width != targetWidth || params.height != targetHeight || params.gravity != Gravity.CENTER) {
            params.width = targetWidth
            params.height = targetHeight
            params.gravity = Gravity.CENTER
            playerView.layoutParams = params
        }
        if (::previewTextOverlay.isInitialized) {
            previewTextOverlays.forEach { overlay ->
                overlay.maxWidth = (targetWidth - dp(16)).coerceAtLeast(dp(100))
            }
            updatePreviewTextOverlay()
        }
    }

    private fun setPreviewStageHeight(heightDp: Int) {
        previewStageHeightDp = heightDp.coerceIn(150, 460)
        if (!::previewFrame.isInitialized) return
        if (!keyboardLayoutActive && !previewFocusMode) {
            val params = previewFrame.layoutParams as? LinearLayout.LayoutParams
            if (params != null && (params.height != dp(previewStageHeightDp) || params.weight != 0f)) {
                params.height = dp(previewStageHeightDp)
                params.weight = 0f
                previewFrame.layoutParams = params
            }
        }
        updatePreviewCanvasSize()
        if (::status.isInitialized) status.text = "Video önizleme alanı: ${previewStageHeightDp} dp."
    }

    private fun adjustPreviewStageHeight(deltaDp: Int) {
        setPreviewStageHeight(previewStageHeightDp + deltaDp)
    }

    private fun togglePreviewFocusMode() {
        val root = previewFrame.parent as? LinearLayout ?: return
        previewFocusMode = !previewFocusMode
        if (previewFocusMode) {
            previewFocusOriginalVisibility.clear()
            for (index in 0 until root.childCount) {
                val child = root.getChildAt(index)
                if (child !== previewFrame) {
                    previewFocusOriginalVisibility[child] = child.visibility
                    child.visibility = View.GONE
                }
            }
            root.setPadding(dp(4), dp(4), dp(4), dp(4))
            val params = previewFrame.layoutParams as LinearLayout.LayoutParams
            params.height = 0
            params.weight = 1f
            previewFrame.layoutParams = params
            previewFocusButton.text = "↙"
            previewFocusButton.contentDescription = "Düzenleme ekranına dön"
        } else {
            previewFocusOriginalVisibility.forEach { (view, visibility) -> view.visibility = visibility }
            previewFocusOriginalVisibility.clear()
            root.setPadding(dp(8), dp(4), dp(8), dp(4))
            val params = previewFrame.layoutParams as LinearLayout.LayoutParams
            params.height = dp(previewStageHeightDp)
            params.weight = 0f
            previewFrame.layoutParams = params
            previewFocusButton.text = "⛶"
            previewFocusButton.contentDescription = "Videoyu geniş ekranda aç"
        }
        previewFrame.requestLayout()
        updatePreviewCanvasSize()
    }

    private fun showEditorPanel(name: String) {
        activeEditorPanel = name
        textOverlayEditingEnabled = name == "text"
        updateTextOverlayEditToggle()
        if (::editorPanelScroll.isInitialized) editorPanelScroll.scrollTo(0, 0)
        editorPanels.forEach { (key, panel) ->
            panel.visibility = if (key == name) View.VISIBLE else View.GONE
        }
        editorTabButtons.forEach { (key, button) ->
            val selected = key == name
            button.background = android.graphics.drawable.GradientDrawable().apply {
                setColor(if (selected) Color.rgb(34, 184, 158) else Color.rgb(20, 31, 45))
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), if (selected) Color.rgb(100, 245, 218) else Color.rgb(42, 59, 77))
            }
            button.setTextColor(if (selected) Color.rgb(5, 25, 29) else Color.rgb(220, 231, 240))
        }
        if (name == "export") status.text = "Projeyi kaydedebilir veya MP4 olarak dışa aktarabilirsin."
    }

    private fun updateTextOverlayEditToggle() {
        if (!::textOverlayEditToggle.isInitialized) return
        val enabled = textOverlayEditingEnabled && activeEditorPanel == "text"
        textOverlayEditToggle.text = if (enabled) {
            "Önizlemede yazı düzenleme: AÇIK  ·  kapat"
        } else {
            "Önizlemede yazı düzenleme: KAPALI  ·  aç"
        }
        textOverlayEditToggle.setTextColor(if (enabled) Color.rgb(5, 25, 29) else Color.WHITE)
        textOverlayEditToggle.background = android.graphics.drawable.GradientDrawable().apply {
            setColor(if (enabled) Color.rgb(57, 220, 190) else Color.rgb(27, 39, 54))
            cornerRadius = dp(10).toFloat()
            setStroke(dp(1), if (enabled) Color.rgb(100, 245, 218) else Color.rgb(42, 59, 77))
        }
        textOverlayEditToggle.contentDescription = if (enabled) {
            "Önizlemede yazı düzenleme açık. Kapatmak için dokun."
        } else {
            "Önizlemede yazı düzenleme kapalı. Açmak için dokun."
        }
    }

    /** Tapping the currently selected tool a second time closes its panel. */
    private fun toggleEditorPanel(name: String) {
        if (activeEditorPanel != name) {
            showEditorPanel(name)
            return
        }
        activeEditorPanel = null
        textOverlayEditingEnabled = false
        updateTextOverlayEditToggle()
        editorPanels.values.forEach { it.visibility = View.GONE }
        editorTabButtons.values.forEach { button ->
            button.background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.rgb(20, 31, 45))
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), Color.rgb(42, 59, 77))
            }
            button.setTextColor(Color.rgb(220, 231, 240))
        }
        val focused = currentFocus
        val keyboard = getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
            as? android.view.inputmethod.InputMethodManager
        focused?.windowToken?.let { keyboard?.hideSoftInputFromWindow(it, 0) }
        focused?.clearFocus()
        if (::editorPanelScroll.isInitialized) editorPanelScroll.scrollTo(0, 0)
    }

    private fun refreshTimelineTracks() {
        if (!::timelineTracks.isInitialized) return
        // The scroll listener keeps this value current. Reading a freshly rebuilt
        // child view here can return zero before its posted scroll restoration runs.
        val preservedScrollX = pendingTimelineScrollX ?: timelineHorizontalScrollX
        pendingTimelineScrollX = null
        timelineHorizontalScrollX = preservedScrollX
        timelineTracks.removeAllViews()
        timelineScrollViews.clear()
        if (::timelinePlayheadSeek.isInitialized) updateTimelinePlayheadControls()

        if (!loadingTextLayerControls) syncSelectedTextLayerFromControls()
        val totalMs = totalOutputDurationMs()
        textLayers.forEach { layer ->
            val requestedEnd = if (layer.timingCustomized) {
                layer.endMs.coerceAtMost(totalMs)
            } else {
                totalMs
            }
            val interval = TextTimingMath.normalize(layer.startMs, requestedEnd, totalMs)
            layer.startMs = interval.startMs
            layer.endMs = interval.endMs
        }
        // Rebind the selected layer's time controls after clip edits, project loads,
        // or duration changes. This avoids stale labels from the previous video.
        if (::textStartSeek.isInitialized && ::textEndSeek.isInitialized && !loadingTextLayerControls) {
            selectedTextLayer()?.let { layer ->
                textStartMs = layer.startMs
                textEndMs = layer.endMs
                textTimingCustomized = layer.timingCustomized
            }
            updateTextControls(refreshTimeline = false)
        }
        val pxPerSecond = timelinePixelsPerSecond()

        fun widthBetween(startMs: Long, endMs: Long): Int =
            TimelineScaleMath.widthBetween(startMs, endMs, pxPerSecond)

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
                        timelineHorizontalScrollX = scrollX
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
                // Keep the content origin identical to the ruler and playhead coordinate system.
                setPadding(0, dp(1), 0, dp(1))
            }
            scroll.addView(track)
            val responsiveLaneHeight = if (keyboardLayoutActive || compactEditorLayout) 18 else heightDp
            row.addView(scroll, LinearLayout.LayoutParams(0, dp(responsiveLaneHeight), 1f))
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
            minimumWidthDp: Int = 0,
            startMs: Long = 0L
        ) {
            val safeStartMs = startMs.coerceAtLeast(0L)
            val safeEndMs = safeStartMs + durationMs.coerceAtLeast(0L)
            val width = maxOf(dp(minimumWidthDp), widthBetween(safeStartMs, safeEndMs).coerceAtLeast(1))
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
            val blockHeight = dp(if (keyboardLayoutActive || compactEditorLayout) 18 else 24)
            track.addView(block, LinearLayout.LayoutParams(width, blockHeight))
        }

        fun addSpacer(track: LinearLayout, startMs: Long, endMs: Long) {
            val width = widthBetween(startMs, endMs)
            if (width <= 0) return
            track.addView(View(this), LinearLayout.LayoutParams(width, dp(if (keyboardLayoutActive || compactEditorLayout) 18 else 24)))
        }

        fun addTextLayerBlock(track: LinearLayout, layer: TextLayer, layerIndex: Int) {
            val startMs = layer.startMs.coerceIn(0L, totalMs)
            val endMs = layer.endMs.coerceIn(startMs, totalMs)
            if (startMs > 0L) addSpacer(track, 0L, startMs)

            val blockWidth = widthBetween(startMs, endMs).coerceAtLeast(1)
            val blockHeight = dp(if (keyboardLayoutActive || compactEditorLayout) 18 else 24)
            val frame = FrameLayout(this).apply {
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.rgb(110, 74, 154))
                    cornerRadius = dp(5).toFloat()
                    setStroke(
                        dp(if (layer.id == selectedTextLayerId) 2 else 1),
                        if (layer.id == selectedTextLayerId) Color.rgb(117, 240, 221)
                        else Color.rgb(61, 79, 99)
                    )
                }
                clipToOutline = true
                contentDescription = "Yazı katmanı ${layerIndex + 1}: ortasından sürükleyerek taşı, kenarlarından sürükleyerek süresini değiştir"
            }
            val body = TextView(this).apply {
                text = layer.text.trim().ifBlank { "Yazı ${layerIndex + 1}" }
                textSize = 9f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER_VERTICAL
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(dp(7), 0, dp(7), 0)
                isClickable = true
                isFocusable = true
                setOnClickListener { selectTextLayer(layer.id) }
            }
            frame.addView(body, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))

            // Dragging the middle of a text clip moves its whole time range.
            var bodyDownRawX = 0f
            var bodyInitialStartMs = startMs
            var bodyInitialEndMs = endMs
            var bodyMoved = false
            body.setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        bodyDownRawX = event.rawX
                        bodyInitialStartMs = layer.startMs
                        bodyInitialEndMs = layer.endMs
                        bodyMoved = false
                        player?.playWhenReady = false
                        // Own this gesture from the start so the horizontal timeline does not steal it.
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                        true
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        val deltaPx = event.rawX - bodyDownRawX
                        if (kotlin.math.abs(deltaPx) > android.view.ViewConfiguration.get(this).scaledTouchSlop) {
                            bodyMoved = true
                            view.parent?.requestDisallowInterceptTouchEvent(true)
                            val pps = timelinePixelsPerSecond()
                            val leftLimitPx = bodyInitialStartMs / 1000f * pps
                            val rightLimitPx = (totalMs - bodyInitialEndMs).coerceAtLeast(0L) / 1000f * pps
                            frame.translationX = deltaPx.coerceIn(-leftLimitPx, rightLimitPx)
                        }
                        true
                    }
                    android.view.MotionEvent.ACTION_UP -> {
                        val deltaPx = event.rawX - bodyDownRawX
                        frame.translationX = 0f
                        view.parent?.requestDisallowInterceptTouchEvent(false)
                        if (bodyMoved) {
                            val deltaMs = TimelineScaleMath.millisecondsForPixels(
                                deltaPx, timelinePixelsPerSecond()
                            )
                            val spanMs = (bodyInitialEndMs - bodyInitialStartMs).coerceAtLeast(0L)
                            val newStart = (bodyInitialStartMs + deltaMs).coerceIn(
                                0L, (totalMs - spanMs).coerceAtLeast(0L)
                            )
                            layer.startMs = newStart
                            layer.endMs = newStart + spanMs
                            layer.timingCustomized = true
                            selectedTextLayerId = layer.id
                            loadTextLayerIntoControls(layer)
                            status.text = "Yazı katmanı zaman çizelgesinde taşındı."
                            refreshTimelineTracks()
                        } else {
                            view.performClick()
                            refreshTimelineTracks()
                        }
                        true
                    }
                    android.view.MotionEvent.ACTION_CANCEL -> {
                        frame.translationX = 0f
                        view.parent?.requestDisallowInterceptTouchEvent(false)
                        true
                    }
                    else -> true
                }
            }

            fun addTextTrimHandle(isStart: Boolean) {
                if (totalMs <= 0L) return
                val handleWidth = minOf(dp(20), (blockWidth / 2).coerceAtLeast(1))
                val handle = TextView(this).apply {
                    text = if (isStart) "‹" else "›"
                    textSize = 13f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    setBackgroundColor(Color.rgb(0, 164, 146))
                    elevation = dp(3).toFloat()
                    contentDescription = if (isStart) "Yazı başlangıcını sürükle" else "Yazı bitişini sürükle"
                }
                var downRawX = 0f
                var initialStartMs = startMs
                var initialEndMs = endMs
                var moved = false
                handle.setOnTouchListener { view, event ->
                    when (event.actionMasked) {
                        android.view.MotionEvent.ACTION_DOWN -> {
                            downRawX = event.rawX
                            initialStartMs = layer.startMs
                            initialEndMs = layer.endMs
                            moved = false
                            player?.playWhenReady = false
                            view.parent?.requestDisallowInterceptTouchEvent(true)
                            true
                        }
                        android.view.MotionEvent.ACTION_MOVE -> {
                            val deltaPx = event.rawX - downRawX
                            if (kotlin.math.abs(deltaPx) > dp(2)) moved = true
                            val pps = timelinePixelsPerSecond()
                            val minimumSpanMs = minOf(500L, totalMs)
                            val maximumShiftPx = ((initialEndMs - initialStartMs - minimumSpanMs)
                                .coerceAtLeast(0L) / 1000f * pps)
                            val sourceBeforePx = initialStartMs / 1000f * pps
                            val sourceAfterPx = (totalMs - initialEndMs).coerceAtLeast(0L) / 1000f * pps
                            view.translationX = if (isStart) {
                                deltaPx.coerceIn(-sourceBeforePx, maximumShiftPx)
                            } else {
                                deltaPx.coerceIn(-maximumShiftPx, sourceAfterPx)
                            }
                            true
                        }
                        android.view.MotionEvent.ACTION_UP -> {
                            val deltaPx = event.rawX - downRawX
                            view.translationX = 0f
                            view.parent?.requestDisallowInterceptTouchEvent(false)
                            if (moved) {
                                val deltaMs = TimelineScaleMath.millisecondsForPixels(
                                    deltaPx, timelinePixelsPerSecond()
                                )
                                val minimumSpanMs = minOf(500L, totalMs)
                                if (isStart) {
                                    layer.startMs = (initialStartMs + deltaMs).coerceIn(
                                        0L, (initialEndMs - minimumSpanMs).coerceAtLeast(0L)
                                    )
                                } else {
                                    layer.endMs = (initialEndMs + deltaMs).coerceIn(
                                        (initialStartMs + minimumSpanMs).coerceAtMost(totalMs),
                                        totalMs
                                    )
                                }
                                layer.timingCustomized = true
                                selectedTextLayerId = layer.id
                                loadTextLayerIntoControls(layer)
                                status.text = if (isStart) {
                                    "Yazı başlangıcı: ${formatTimelineMs(layer.startMs)}"
                                } else {
                                    "Yazı bitişi: ${formatTimelineMs(layer.endMs)}"
                                }
                            } else {
                                selectTextLayer(layer.id)
                            }
                            refreshTimelineTracks()
                            view.performClick()
                            true
                        }
                        android.view.MotionEvent.ACTION_CANCEL -> {
                            view.translationX = 0f
                            view.parent?.requestDisallowInterceptTouchEvent(false)
                            true
                        }
                        else -> true
                    }
                }
                frame.addView(handle, FrameLayout.LayoutParams(
                    handleWidth,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    if (isStart) Gravity.START else Gravity.END
                ))
            }

            addTextTrimHandle(isStart = true)
            addTextTrimHandle(isStart = false)
            track.addView(frame, LinearLayout.LayoutParams(blockWidth, blockHeight))
            if (endMs < totalMs) addSpacer(track, endMs, totalMs)
        }

        if (!keyboardLayoutActive && !compactEditorLayout) {
            val ruler = makeLane("SÜRE", 22)
            val rulerEndMs = (((totalMs + 4999L) / 5000L).coerceAtLeast(1L)) * 5000L
            var rulerTimeMs = 0L
            var rulerPositionPx = 0
            while (rulerTimeMs < rulerEndMs) {
                val spanMs = minOf(5000L, rulerEndMs - rulerTimeMs)
                val nextTimeMs = rulerTimeMs + spanMs
                val nextPositionPx = TimelineScaleMath.pixelsForTime(nextTimeMs, pxPerSecond)
                val tick = TextView(this).apply {
                    text = formatMs(rulerTimeMs) + " ┆"
                    textSize = 9f
                    setTextColor(Color.rgb(166, 184, 201))
                    gravity = Gravity.CENTER_VERTICAL or Gravity.START
                    setPadding(dp(2), 0, 0, 0)
                }
                ruler.addView(tick, LinearLayout.LayoutParams(
                    maxOf(dp(26), (nextPositionPx - rulerPositionPx).coerceAtLeast(1)), dp(22)
                ))
                rulerTimeMs = nextTimeMs
                rulerPositionPx = nextPositionPx
            }
        }

        val videoTrack = makeLane("VIDEO", if (keyboardLayoutActive || compactEditorLayout) 18 else 44)
        if (clips.isEmpty()) {
            addBlock(videoTrack, "+ Video ekle", 5000L, Color.rgb(38, 105, 123), onClick = { chooseVideo(true) }, minimumWidthDp = 115)
        } else {
            var videoOffsetMs = 0L
            clips.forEachIndexed { index, clip ->
                val duration = (clip.endMs - clip.startMs).coerceAtLeast(0L)
                val nextVideoOffsetMs = videoOffsetMs + duration
                val width = widthBetween(videoOffsetMs, nextVideoOffsetMs).coerceAtLeast(1)
                val height = dp(if (keyboardLayoutActive || compactEditorLayout) 18 else 42)
                val block = buildVideoClipFilmstrip(clip, index, width, height, index == selectedClipIndex)
                // Widths use absolute boundaries, so fractional-pixel rounding cannot accumulate.
                videoTrack.addView(block, LinearLayout.LayoutParams(width, height))
                videoOffsetMs = nextVideoOffsetMs
            }
        }

        if (textLayers.isEmpty()) {
            val textTrack = makeLane("METİN", 24)
            addBlock(
                textTrack, "+ Metin ekle", totalMs.coerceAtLeast(5000L), Color.rgb(70, 54, 93),
                onClick = { showEditorPanel("text"); addTextLayer() }, minimumWidthDp = 115
            )
        } else {
            textLayers.forEachIndexed { index, layer ->
                val textTrack = makeLane("YAZI ${index + 1}", 24)
                addTextLayerBlock(textTrack, layer, index)
            }
        }

        val musicTrack = makeLane("MÜZİK", 24)
        if (musicUri != null) {
            addBlock(
                musicTrack, "♫ ${musicTitle ?: "Müzik"}", totalMs, Color.rgb(46, 125, 85),
                onClick = { showEditorPanel("audio") }
            )
        } else {
            addBlock(musicTrack, "+ Müzik ekle", totalMs.coerceAtLeast(5000L), Color.rgb(42, 78, 63),
                onClick = { chooseAudio(true) }, minimumWidthDp = 115)
        }

        val effectTrack = makeLane("EFEKT", 24)
        if (soundUri != null && totalMs > 0L) {
            val startMs = soundStartMs.coerceIn(0L, totalMs)
            val duration = soundDurationMs.coerceAtMost((totalMs - startMs).coerceAtLeast(0L))
            val endMs = (startMs + duration).coerceAtMost(totalMs)
            addSpacer(effectTrack, 0L, startMs)
            addBlock(
                effectTrack, "♪ ${soundTitle ?: "Ses efekti"}", duration, Color.rgb(166, 102, 40),
                onClick = { showEditorPanel("audio") }, startMs = startMs
            )
            addSpacer(effectTrack, endMs, totalMs)
        } else {
            addBlock(effectTrack, "+ Ses efekti ekle", totalMs.coerceAtLeast(5000L), Color.rgb(96, 68, 41),
                onClick = { chooseAudio(false) }, minimumWidthDp = 115)
        }
        // A MATCH_PARENT playhead inside a WRAP_CONTENT FrameLayout would let
        // the frame expand to the full available screen height and push the tool dock
        // off-screen. Size the frame from the actual timeline lanes instead.
        val laneContentHeight = (0 until timelineTracks.childCount).sumOf { index ->
            val row = timelineTracks.getChildAt(index) as? LinearLayout ?: return@sumOf 0
            val lane = row.getChildAt(1)
            val laneHeight = lane?.layoutParams?.height?.takeIf { it > 0 } ?: dp(24)
            laneHeight + row.paddingTop + row.paddingBottom
        }.coerceAtLeast(dp(24))
        (timelineTracksFrame.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
            if (params.height != laneContentHeight) {
                params.height = laneContentHeight
                timelineTracksFrame.layoutParams = params
            }
        }

        val restoreScrollX = timelineHorizontalScrollX
        timelineScrollViews.toList().forEach { laneScroll ->
            laneScroll.post { laneScroll.scrollTo(restoreScrollX, 0) }
        }
        updateTimelinePlayheadLine()
    }

    private fun updateTimelinePlayheadLine() {
        if (!::timelinePlayheadLine.isInitialized || !::timelineTracksFrame.isInitialized) return
        val scrollX = timelineScrollViews.firstOrNull()?.scrollX ?: 0
        val pixelsPerSecond = timelinePixelsPerSecond()
        val timelineX = dp(57) + TimelineScaleMath.pixelsForTime(timelinePlayheadMs, pixelsPerSecond) - scrollX
        val frameWidth = timelineTracksFrame.width
        if (frameWidth > 0 && (timelineX < 0 || timelineX > frameWidth)) {
            timelinePlayheadLine.visibility = View.INVISIBLE
            return
        }
        timelinePlayheadLine.visibility = View.VISIBLE
        val params = timelinePlayheadLine.layoutParams as? FrameLayout.LayoutParams ?: return
        params.width = dp(28)
        // Do not pin an out-of-range playhead to the left edge: preserve the exact x coordinate.
        params.leftMargin = timelineX - dp(14)
        timelinePlayheadLine.layoutParams = params
        timelinePlayheadLine.bringToFront()
    }

    private fun timelinePixelsPerSecond(): Float = dp(22).toFloat() * timelineZoom

    private fun ensureTrimHandlesReachable(index: Int): Boolean {
        val clip = clips.getOrNull(index) ?: return false
        val durationMs = (clip.endMs - clip.startMs).coerceAtLeast(0L)
        if (durationMs <= 0L) return false

        // Keep a touch target for both trim edges, even after a clip is shortened to 0.5 s.
        val basePixelsPerSecond = dp(22).toFloat().coerceAtLeast(1f)
        val requiredZoom = (dp(80).toDouble() * 1000.0 /
            (durationMs.toDouble() * basePixelsPerSecond.toDouble())).toFloat().coerceIn(0.5f, 8.0f)
        val targetZoom = maxOf(timelineZoom, requiredZoom).coerceIn(0.5f, 8.0f)
        val zoomChanged = targetZoom != timelineZoom
        timelineZoom = targetZoom

        // Always bring the selected clip into view, even when the current zoom already
        // provides sufficient handle width. This avoids selection on a far-right clip
        // leaving both edges outside the viewport.
        val projectOffsetMs = clips.take(index).sumOf {
            (it.endMs - it.startMs).coerceAtLeast(0L)
        }
        val clipStartPx = TimelineScaleMath.pixelsForTime(projectOffsetMs, timelinePixelsPerSecond())
        pendingTimelineScrollX = (clipStartPx - dp(8)).coerceAtLeast(0)
        timelineHorizontalScrollX = pendingTimelineScrollX ?: 0
        return zoomChanged || pendingTimelineScrollX != null
    }

    private fun adjustTimelineZoom(factor: Float) {
        val newZoom = (timelineZoom * factor).coerceIn(0.5f, 8.0f)
        if (newZoom == timelineZoom) return
        timelineZoom = newZoom
        refreshTimelineTracks()
        status.text = "Zaman çizelgesi yakınlaştırma: " + timelineZoom.toString() + "×. Kırmızı çizgiyi sürükleyebilir veya video karelerine dokunabilirsin."
    }

    private fun seekFromTimelineFrameX(frameX: Float) {
        if (clips.isEmpty()) return
        val scrollX = timelineScrollViews.firstOrNull()?.scrollX ?: 0
        val timelineContentX = frameX + scrollX
        val videoStartX = dp(57).toFloat()
        val offsetPixels = (timelineContentX - videoStartX).coerceAtLeast(0f)
        val requestedMs = TimelineScaleMath.millisecondsForPixels(offsetPixels, timelinePixelsPerSecond())
        seekTimelinePreview(requestedMs.coerceIn(0L, totalOutputDurationMs()))
    }

    private fun installTimelinePlayheadListeners() {
        timelinePlayheadSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                timelinePlayheadMs = TimelineTrimMath.timeForProgress(progress, totalOutputDurationMs())
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
        timelinePlayheadSeek.max = TimelineTrimMath.progressMax(totalMs)
        timelinePlayheadSeek.isEnabled = clips.isNotEmpty() && totalMs > 0L
        timelinePlayheadMs = timelinePlayheadMs.coerceIn(0L, totalMs)
        timelinePlayheadSeek.progress = TimelineTrimMath.progressForTime(timelinePlayheadMs, totalMs)
        updateTimelinePlayheadLabel()
        updateTimelinePlayheadLine()
    }

    private fun updateTimelinePlayheadLabel() {
        if (!::timelinePlayheadLabel.isInitialized) return
        val totalMs = totalOutputDurationMs()
        val projectPositionMs = timelinePlayheadMs.coerceIn(0L, totalMs)
        val projectText = "Proje ${formatTimelineMs(projectPositionMs)} / ${formatTimelineMs(totalMs)}"
        val clip = selectedClip()
        timelinePlayheadLabel.text = if (previewSingleClip && clip != null) {
            val projectOffsetMs = clips.take(selectedClipIndex).sumOf {
                (it.endMs - it.startMs).coerceAtLeast(0L)
            }
            val clipDurationMs = (clip.endMs - clip.startMs).coerceAtLeast(0L)
            val clipPositionMs = (projectPositionMs - projectOffsetMs).coerceIn(0L, clipDurationMs)
            // Lead with the combined project/output duration. The selected clip's
            // local time is useful, but must not look like the final video duration.
            "$projectText\nKlip ${formatTimelineMs(clipPositionMs)} / ${formatTimelineMs(clipDurationMs)}"
        } else {
            projectText
        }
        updateTimelinePlayheadLine()
        updatePreviewTextOverlay()
    }

    private fun ensureTimelinePreviewLoaded() {
        if (timelinePreviewLoaded || clips.isEmpty()) return
        previewSingleClip = false
        val items = clips.map { clip -> clippedMediaItem(clip) }
        player?.setMediaItems(items, 0, 0L)
        player?.setSeekParameters(androidx.media3.exoplayer.SeekParameters.EXACT)
        player?.prepare()
        player?.playWhenReady = false
        timelinePreviewLoaded = true
    }

    private fun seekTimelinePreview(requestedMs: Long) {
        if (clips.isEmpty()) return
        ensureTimelinePreviewLoaded()
        if (totalOutputDurationMs() <= 0L) return
        val position = TimelinePositionMath.locate(
            clips.map { (it.endMs - it.startMs).coerceAtLeast(0L) },
            requestedMs
        ) ?: return
        val targetMs = position.clipStartMs + position.localPositionMs
        player?.seekTo(position.clipIndex, position.localPositionMs)
        timelinePlayheadMs = targetMs
        if (selectedClipIndex != position.clipIndex) {
            selectedClipIndex = position.clipIndex
            refreshClipList()
            loadTrimControls()
        }
        timelinePlayheadSeek.progress = TimelineTrimMath.progressForTime(targetMs, totalOutputDurationMs())
        updateTimelinePlayheadLabel()
    }

    private fun syncTimelinePlayheadFromPlayer() {
        val currentPlayer = player ?: return
        if (!timelinePreviewLoaded || clips.isEmpty()) return
        val index = currentPlayer.currentMediaItemIndex
        if (index !in clips.indices) return
        val offsetMs = clips.take(index).sumOf { (it.endMs - it.startMs).coerceAtLeast(0L) }
        timelinePlayheadMs = (offsetMs + currentPlayer.currentPosition).coerceIn(0L, totalOutputDurationMs())
        if (selectedClipIndex != index) {
            selectedClipIndex = index
            refreshClipList()
            loadTrimControls()
        }
        val progress = TimelineTrimMath.progressForTime(timelinePlayheadMs, totalOutputDurationMs())
        if (timelinePlayheadSeek.progress != progress) timelinePlayheadSeek.progress = progress
        updateTimelinePlayheadLabel()
    }

    private fun syncSingleClipPlayheadFromPlayer() {
        val currentPlayer = player ?: return
        val index = selectedClipIndex
        val clip = clips.getOrNull(index) ?: return
        val beforeMs = clips.take(index).sumOf { (it.endMs - it.startMs).coerceAtLeast(0L) }
        val clipDuration = (clip.endMs - clip.startMs).coerceAtLeast(0L)
        timelinePlayheadMs = (beforeMs + currentPlayer.currentPosition.coerceIn(0L, clipDuration))
            .coerceIn(0L, totalOutputDurationMs())
        if (::timelinePlayheadSeek.isInitialized) {
            timelinePlayheadSeek.progress = TimelineTrimMath.progressForTime(timelinePlayheadMs, totalOutputDurationMs())
        }
        updateTimelinePlayheadLabel()
    }

    private fun selectTimelineClip(index: Int) {
        if (index !in clips.indices) return
        timelinePreviewLoaded = false
        previewSingleClip = true
        selectedClipIndex = index
        timelinePlayheadMs = clips.take(index).sumOf { (it.endMs - it.startMs).coerceAtLeast(0L) }
        ensureTrimHandlesReachable(index)
        updateTimelinePlayheadLabel()
        val clip = clips[index]
        refreshClipList()
        loadTrimControls()
        player?.setMediaItem(clippedMediaItem(clip))
        player?.prepare()
        player?.playWhenReady = false
        status.text = "Seçili klip: ${clip.title}. Klipler dışa aktarılırken bu sırayla tek MP4'e birleştirilir."
        // refreshClipList() above rebuilds the timeline too; a second immediate refresh
        // could discard the pending auto-zoom scroll before the posted scroll runs.
    }

    private fun captureClipEditSnapshot(): ClipEditSnapshot = ClipEditSnapshot(
        clips = clips.map { it.copy() },
        selectedClipIndex = selectedClipIndex,
        timelinePlayheadMs = timelinePlayheadMs,
        previewSingleClip = previewSingleClip
    )

    private fun recordClipEdit() {
        pushClipUndoSnapshot(captureClipEditSnapshot())
    }

    private fun pushClipUndoSnapshot(snapshot: ClipEditSnapshot) {
        undoClipHistory.add(snapshot)
        if (undoClipHistory.size > 50) undoClipHistory.removeAt(0)
        redoClipHistory.clear()
        updateClipHistoryButtons()
    }

    private fun finishTrimHistoryGesture() {
        val before = activeTrimHistorySnapshot ?: return
        activeTrimHistorySnapshot = null
        if (before.clips != clips) pushClipUndoSnapshot(before)
    }

    private fun clearClipHistory() {
        undoClipHistory.clear()
        redoClipHistory.clear()
        activeTrimHistorySnapshot = null
        updateClipHistoryButtons()
    }

    private fun updateClipHistoryButtons() {
        undoClipButton?.isEnabled = undoClipHistory.isNotEmpty()
        redoClipButton?.isEnabled = redoClipHistory.isNotEmpty()
    }

    private fun undoClipEdit() {
        if (undoClipHistory.isEmpty()) {
            status.text = "Geri alınacak klip işlemi yok."
            return
        }
        redoClipHistory.add(captureClipEditSnapshot())
        val snapshot = undoClipHistory.removeAt(undoClipHistory.lastIndex)
        restoreClipEditSnapshot(snapshot)
        status.text = "Son klip işlemi geri alındı."
        updateClipHistoryButtons()
    }

    private fun redoClipEdit() {
        if (redoClipHistory.isEmpty()) {
            status.text = "Yinelenecek klip işlemi yok."
            return
        }
        undoClipHistory.add(captureClipEditSnapshot())
        if (undoClipHistory.size > 50) undoClipHistory.removeAt(0)
        val snapshot = redoClipHistory.removeAt(redoClipHistory.lastIndex)
        restoreClipEditSnapshot(snapshot)
        status.text = "Klip işlemi yinelendi."
        updateClipHistoryButtons()
    }

    private fun restoreClipEditSnapshot(snapshot: ClipEditSnapshot) {
        clips.clear()
        clips.addAll(snapshot.clips.map { it.copy() })
        selectedClipIndex = snapshot.selectedClipIndex.takeIf { it in clips.indices } ?: -1
        timelinePreviewLoaded = false
        previewSingleClip = snapshot.previewSingleClip && selectedClipIndex in clips.indices
        timelinePlayheadMs = snapshot.timelinePlayheadMs.coerceIn(0L, totalOutputDurationMs())

        refreshClipList()
        loadTrimControls()
        updateTimelinePlayheadControls()

        if (clips.isEmpty()) {
            previewSingleClip = false
            player?.clearMediaItems()
        } else if (previewSingleClip) {
            selectedClip()?.let { clip ->
                player?.setMediaItem(clippedMediaItem(clip))
                player?.prepare()
                player?.seekTo(0L)
                player?.playWhenReady = false
            }
        } else {
            ensureTimelinePreviewLoaded()
            seekTimelinePreview(timelinePlayheadMs)
        }
        updateClipHistoryButtons()
    }

    private fun selectedTextLayer(): TextLayer? =
        selectedTextLayerId?.let { id -> textLayers.firstOrNull { it.id == id } }

    private fun syncSelectedTextLayerFromControls() {
        if (loadingTextLayerControls || !::textInput.isInitialized) return
        val layer = selectedTextLayer() ?: return
        // Preserve trailing spaces while typing; trim only when rendering/exporting.
        layer.text = textInput.text?.toString().orEmpty()
        layer.startMs = textStartMs.coerceAtLeast(0L)
        layer.endMs = textEndMs.coerceAtLeast(layer.startMs)
        layer.timingCustomized = textTimingCustomized
        layer.position = textPosition
        layer.sizeSp = textSizeSp.coerceIn(12, 96)
        layer.colorPreset = textColorPreset
        layer.isBold = textBold
    }

    private fun refreshTextLayerList() {
        if (!::textLayerList.isInitialized) return
        textLayerList.removeAllViews()
        if (textLayers.isEmpty()) {
            textLayerList.addView(label("Henüz yazı katmanı yok. Yeni yazı ile ekle.", 11f, Color.rgb(154, 171, 190), false))
            return
        }
        textLayers.forEachIndexed { index, layer ->
            val selected = layer.id == selectedTextLayerId
            val title = layer.text.trim().ifBlank { "Yazı ${index + 1}" }.take(14)
            val button = Button(this).apply {
                text = "${if (selected) "✓ " else ""}${index + 1}. $title"
                isAllCaps = false
                textSize = 11f
                minHeight = dp(38)
                setPadding(dp(8), 0, dp(8), 0)
                setTextColor(if (selected) Color.rgb(5, 25, 29) else Color.WHITE)
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(if (selected) Color.rgb(57, 220, 190) else Color.rgb(27, 43, 59))
                    cornerRadius = dp(9).toFloat()
                    setStroke(dp(1), if (selected) Color.rgb(100, 245, 218) else Color.rgb(47, 66, 84))
                }
                setOnClickListener { selectTextLayer(layer.id) }
            }
            textLayerList.addView(button, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)
            ).apply { rightMargin = dp(5) })
        }
    }

    private fun addTextLayer() {
        if (textLayers.size >= 12) {
            status.text = "En fazla 12 metin katmanı ekleyebilirsin."
            return
        }
        syncSelectedTextLayerFromControls()
        val total = totalOutputDurationMs()
        if (total > 0L && timelinePlayheadMs >= total) {
            player?.playWhenReady = false
            seekTimelinePreview(0L)
        }
        val layer = TextLayer(
            id = nextTextLayerId++,
            text = "",
            startMs = 0L,
            endMs = total,
            timingCustomized = false,
            position = TextPosition.BOTTOM,
            sizeSp = 24,
            colorPreset = textColorPreset,
            isBold = textBold,
            xFraction = 0.5f,
            yFraction = defaultTextYFraction(TextPosition.BOTTOM)
        )
        textLayers.add(layer)
        selectedTextLayerId = layer.id
        loadTextLayerIntoControls(layer)
        refreshTextLayerList()
        updatePreviewTextOverlay()
        refreshTimelineTracks()
        showEditorPanel("text")
        focusTextInputAndShowKeyboard()
        status.text = "Yeni yazı katmanı eklendi. Metnini yazıp süresini ve konumunu ayarla."
    }

    private fun focusTextInputAndShowKeyboard() {
        if (!::textInput.isInitialized) return
        textInput.post {
            textInput.requestFocus()
            val keyboard = getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                as? android.view.inputmethod.InputMethodManager
            keyboard?.showSoftInput(textInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun adjustSelectedTextSize(delta: Int) {
        val layer = selectedTextLayer()
        if (layer == null) {
            status.text = "Önce bir yazı ekle veya mevcut yazıyı seç."
            return
        }
        syncSelectedTextLayerFromControls()
        val next = (layer.sizeSp + delta).coerceIn(12, 96)
        layer.sizeSp = next
        textSizeSp = next
        textSizeLabel.text = "Yazı boyutu: " + next + " sp"
        textSizeSeek.progress = next - 12
        updatePreviewTextOverlay()
        status.text = "Yazı boyutu: " + next + " sp. Videonun üzerinde iki parmakla da boyutlandırabilirsin."
    }

    private fun duplicateSelectedTextLayer() {
        if (textLayers.size >= 12) {
            status.text = "En fazla 12 metin katmanı ekleyebilirsin."
            return
        }
        syncSelectedTextLayerFromControls()
        val source = selectedTextLayer()
        if (source == null) {
            status.text = "Kopyalamak için önce bir yazı katmanı seç."
            return
        }
        val copy = source.copy(id = nextTextLayerId++, text = source.text)
        textLayers.add(textLayers.indexOf(source) + 1, copy)
        selectedTextLayerId = copy.id
        loadTextLayerIntoControls(copy)
        refreshTextLayerList()
        updatePreviewTextOverlay()
        refreshTimelineTracks()
        focusTextInputAndShowKeyboard()
        status.text = "Yazı katmanı kopyalandı. Metni ve ayarları bağımsız değiştirebilirsin."
    }

    private fun removeSelectedTextLayer() {
        val index = textLayers.indexOfFirst { it.id == selectedTextLayerId }
        if (index < 0) {
            status.text = "Silmek için önce bir yazı katmanı seç."
            return
        }
        textLayers.removeAt(index)
        val next = textLayers.getOrNull(index.coerceAtMost(textLayers.lastIndex))
        selectedTextLayerId = next?.id
        loadTextLayerIntoControls(next)
        refreshTextLayerList()
        updatePreviewTextOverlay()
        refreshTimelineTracks()
        status.text = "Metin katmanı kaldırıldı."
    }

    private fun selectTextLayer(id: Long) {
        if (textLayers.none { it.id == id }) return
        syncSelectedTextLayerFromControls()
        selectedTextLayerId = id
        val layer = selectedTextLayer()
        loadTextLayerIntoControls(layer)
        val total = totalOutputDurationMs()
        if (layer != null && total > 0L &&
            (timelinePlayheadMs < layer.startMs || timelinePlayheadMs >= layer.endMs)) {
            player?.playWhenReady = false
            val targetMs = layer.startMs.coerceIn(0L, (total - 1L).coerceAtLeast(0L))
            seekTimelinePreview(targetMs)
        }
        refreshTextLayerList()
        updatePreviewTextOverlay()
        refreshTimelineTracks()
        status.text = "Yazı katmanı seçildi."
    }

    private fun loadTextLayerIntoControls(layer: TextLayer?) {
        loadingTextLayerControls = true
        try {
            textStartMs = layer?.startMs?.coerceAtLeast(0L) ?: 0L
            textEndMs = layer?.endMs?.coerceAtLeast(textStartMs) ?: totalOutputDurationMs()
            textTimingCustomized = layer?.timingCustomized ?: false
            textPosition = layer?.position ?: TextPosition.BOTTOM
            textSizeSp = layer?.sizeSp?.coerceIn(12, 96) ?: 24
            textColorPreset = layer?.colorPreset ?: TextColorPreset.WHITE
            textBold = layer?.isBold ?: true
            textInput.setText(layer?.text.orEmpty())
            textSizeLabel.text = "Yazı boyutu: $textSizeSp sp"
            textSizeSeek.progress = textSizeSp - 12
            textPositionGroup.check(when (textPosition) {
                TextPosition.TOP -> 1
                TextPosition.CENTER -> 2
                TextPosition.BOTTOM -> 3
            })
            textColorGroup.check(11 + TextColorPreset.values().indexOf(textColorPreset).coerceAtLeast(0))
            textBoldCheck.isChecked = textBold
            updateTextControls()
        } finally {
            loadingTextLayerControls = false
        }
        syncSelectedTextLayerFromControls()
        refreshTextLayerList()
        updatePreviewTextOverlay()
        refreshTimelineTracks()
    }

    private fun updatePreviewTextOverlay() {
        if (!::previewTextOverlay.isInitialized || !::textInput.isInitialized) return
        val totalMs = totalOutputDurationMs()
        val active = textLayers.filter { layer ->
            layer.text.isNotBlank() &&
                TextTimingMath.isVisibleAt(timelinePlayheadMs, layer.startMs, layer.endMs, totalMs)
        }
        activePreviewTextLayers = active
        while (previewTextOverlays.size < active.size) {
            val overlay = createPreviewTextOverlayView()
            previewTextOverlays.add(overlay)
            previewFrame.addView(overlay, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ))
        }
        val visiblePositionCounts = mutableMapOf<TextPosition, Int>()
        previewTextOverlays.forEachIndexed { index, overlay ->
            val layer = active.getOrNull(index)
            if (layer == null) {
                overlay.visibility = View.GONE
                return@forEachIndexed
            }
            val positionIndex = visiblePositionCounts[layer.position] ?: 0
            visiblePositionCounts[layer.position] = positionIndex + 1
            overlay.tag = layer.id
            overlay.text = layer.text.trim()
            overlay.setTextSize(layer.sizeSp.toFloat())
            overlay.setTextColor(layer.colorPreset.colorInt)
            overlay.setTypeface(Typeface.DEFAULT, if (layer.isBold) Typeface.BOLD else Typeface.NORMAL)
            overlay.visibility = View.VISIBLE
            val params = overlay.layoutParams as? FrameLayout.LayoutParams ?: return@forEachIndexed
            params.gravity = Gravity.CENTER
            params.topMargin = 0
            params.bottomMargin = 0
            params.leftMargin = 0
            params.rightMargin = 0
            overlay.layoutParams = params
            val videoWidth = playerView.width.coerceAtLeast(dp(100))
            val videoHeight = playerView.height.coerceAtLeast(dp(100))
            overlay.maxWidth = (videoWidth - dp(16)).coerceAtLeast(dp(80))
            overlay.measure(
                View.MeasureSpec.makeMeasureSpec(videoWidth, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(videoHeight, View.MeasureSpec.AT_MOST)
            )
            val maxX = ((videoWidth - overlay.measuredWidth).coerceAtLeast(0) / 2f)
            val maxY = ((videoHeight - overlay.measuredHeight).coerceAtLeast(0) / 2f)
            overlay.translationX = (layer.xFraction.coerceIn(0f, 1f) - 0.5f) * 2f * maxX
            overlay.translationY = (layer.yFraction.coerceIn(0f, 1f) - 0.5f) * 2f * maxY
            overlay.bringToFront()
        }
    }

    private fun createPreviewTextOverlayView(): TextView = TextView(this).apply {
        textSize = 24f
        gravity = Gravity.CENTER
        maxLines = 2
        maxWidth = dp(170)
        setTextColor(Color.WHITE)
        setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        setPadding(dp(10), dp(6), dp(10), dp(6))
        setShadowLayer(2f, 1f, 1f, Color.BLACK)
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(Color.argb(210, 0, 0, 0))
            cornerRadius = dp(8).toFloat()
        }
        visibility = View.GONE
        installPreviewTextTouch(this)
    }

    private fun installPreviewTextTouch(overlay: TextView) {
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0.5f
        var startY = 0.84f
        var startDistance = 0f
        var startSize = 24
        overlay.isClickable = true
        overlay.setOnTouchListener { view, event ->
            if (!textOverlayEditingEnabled) return@setOnTouchListener false
            val layerId = view.tag as? Long ?: return@setOnTouchListener false
            val layer = textLayers.firstOrNull { it.id == layerId } ?: return@setOnTouchListener false
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    // The parent editor ScrollView must not intercept vertical text drags.
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                    if (selectedTextLayerId != layer.id) {
                        syncSelectedTextLayerFromControls()
                        selectedTextLayerId = layer.id
                        loadTextLayerIntoControls(layer)
                    }
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = layer.xFraction
                    startY = layer.yFraction
                    startSize = layer.sizeSp
                    startDistance = 0f
                    player?.playWhenReady = false
                    true
                }
                android.view.MotionEvent.ACTION_POINTER_DOWN -> {
                    if (event.pointerCount >= 2) {
                        val dx = (event.getX(0) - event.getX(1)).toDouble()
                        val dy = (event.getY(0) - event.getY(1)).toDouble()
                        startDistance = kotlin.math.sqrt(dx * dx + dy * dy).toFloat().coerceAtLeast(1f)
                        startSize = layer.sizeSp
                    }
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    if (event.pointerCount >= 2 && startDistance > 0f) {
                        val dx = (event.getX(0) - event.getX(1)).toDouble()
                        val dy = (event.getY(0) - event.getY(1)).toDouble()
                        val distance = kotlin.math.sqrt(dx * dx + dy * dy).toFloat()
                        layer.sizeSp = (startSize * distance / startDistance).toInt().coerceIn(12, 96)
                        textSizeSp = layer.sizeSp
                        if (selectedTextLayerId == layer.id) {
                            loadingTextLayerControls = true
                            try {
                                textSizeLabel.text = "Yazı boyutu: " + layer.sizeSp + " sp"
                                textSizeSeek.progress = layer.sizeSp - 12
                            } finally {
                                loadingTextLayerControls = false
                            }
                        }
                    } else if (event.pointerCount == 1) {
                        val videoWidth = playerView.width.coerceAtLeast(1).toFloat()
                        val videoHeight = playerView.height.coerceAtLeast(1).toFloat()
                        layer.xFraction = (startX + (event.rawX - downRawX) / videoWidth).coerceIn(0.03f, 0.97f)
                        layer.yFraction = (startY + (event.rawY - downRawY) / videoHeight).coerceIn(0.04f, 0.96f)
                        layer.position = when {
                            layer.yFraction < 0.34f -> TextPosition.TOP
                            layer.yFraction > 0.66f -> TextPosition.BOTTOM
                            else -> TextPosition.CENTER
                        }
                        if (selectedTextLayerId == layer.id) {
                            textPosition = layer.position
                            loadingTextLayerControls = true
                            try {
                                textPositionGroup.check(when (layer.position) {
                                    TextPosition.TOP -> 1
                                    TextPosition.CENTER -> 2
                                    TextPosition.BOTTOM -> 3
                                })
                            } finally {
                                loadingTextLayerControls = false
                            }
                        }
                    }
                    updatePreviewTextOverlay()
                    true
                }
                android.view.MotionEvent.ACTION_POINTER_UP -> {
                    // Re-anchor the one-finger drag to the finger that remains down.
                    // Otherwise lifting pointer 0 can make the next move jump the overlay.
                    val remainingIndex = if (event.actionIndex == 0) 1 else 0
                    if (remainingIndex < event.pointerCount) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            downRawX = event.getRawX(remainingIndex)
                            downRawY = event.getRawY(remainingIndex)
                        } else {
                            val location = IntArray(2)
                            view.getLocationOnScreen(location)
                            downRawX = location[0] + event.getX(remainingIndex)
                            downRawY = location[1] + event.getY(remainingIndex)
                        }
                    }
                    startX = layer.xFraction
                    startY = layer.yFraction
                    startDistance = 0f
                    true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                    if (selectedTextLayerId == layer.id) syncSelectedTextLayerFromControls()
                    refreshTextLayerList()
                    refreshTimelineTracks()
                    status.text = "Yazı konumu güncellendi. Sürükle veya iki parmağınla boyutlandır."
                    view.performClick()
                    true
                }
                android.view.MotionEvent.ACTION_CANCEL -> {
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                    startDistance = 0f
                    true
                }
                else -> true
            }
        }
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
                val interval = TextTimingMath.moveStart(
                    requestedStartMs = progress * TextTimingMath.SEEK_STEP_MS,
                    currentEndMs = textEndMs,
                    totalMs = totalOutputDurationMs()
                )
                textStartMs = interval.startMs
                textEndMs = interval.endMs
                updateTextControls()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) { deferTimelineRefresh = true }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                deferTimelineRefresh = false
                refreshTimelineTracks()
            }
        })
        textEndSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                textTimingCustomized = true
                val interval = TextTimingMath.moveEnd(
                    requestedEndMs = progress * TextTimingMath.SEEK_STEP_MS,
                    currentStartMs = textStartMs,
                    totalMs = totalOutputDurationMs()
                )
                textStartMs = interval.startMs
                textEndMs = interval.endMs
                updateTextControls()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) { deferTimelineRefresh = true }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                deferTimelineRefresh = false
                refreshTimelineTracks()
            }
        })
    }

    private fun updateTextControls(refreshTimeline: Boolean = true) {
        if (!::textStartSeek.isInitialized || !::textEndSeek.isInitialized) return
        val total = totalOutputDurationMs()
        // Seek controls use tenths of a second, but the final end may be a
        // partial tenth; normalize the interval before reflecting it in the UI.
        val maxTenths = ceil(total / TextTimingMath.SEEK_STEP_MS.toDouble()).toInt().coerceAtLeast(0)
        if (total <= 0L) {
            textStartMs = 0L
            textEndMs = 0L
        } else {
            if (!textTimingCustomized) textEndMs = total
            val interval = TextTimingMath.normalize(textStartMs, textEndMs, total)
            textStartMs = interval.startMs
            textEndMs = interval.endMs
        }
        textStartSeek.max = maxTenths
        textEndSeek.max = maxTenths
        textStartSeek.progress = (textStartMs / TextTimingMath.SEEK_STEP_MS).toInt().coerceAtMost(maxTenths)
        textEndSeek.progress = ceil(textEndMs / TextTimingMath.SEEK_STEP_MS.toDouble()).toInt().coerceAtMost(maxTenths)
        textStartSeek.isEnabled = total > 0L
        textEndSeek.isEnabled = total > 0L
        textStartLabel.text = "Yazı başlangıcı: " + formatTimelineMs(textStartMs)
        textEndLabel.text = "Yazı bitişi: " + formatTimelineMs(textEndMs)
        syncSelectedTextLayerFromControls()
        updatePreviewTextOverlay()
        if (!deferTimelineRefresh && refreshTimeline) refreshTimelineTracks()
    }

    /**
     * Plays selected music and sound effects alongside the video preview. Both are
     * driven by the output timeline, so scrubbing and clip changes keep them aligned.
     */
    private fun syncPreviewAudio() {
        if (isFinishing || isDestroyed) return

        if (musicPreviewUri != musicUri) {
            musicPreviewPlayer?.release()
            musicPreviewPlayer = null
            musicPreviewUri = musicUri
            musicUri?.let { uri ->
                musicPreviewPlayer = ExoPlayer.Builder(this).build().apply {
                    setMediaItem(MediaItem.fromUri(uri))
                    repeatMode = androidx.media3.common.Player.REPEAT_MODE_ONE
                    volume = musicVolume / 100f
                    prepare()
                }
            }
        }
        if (soundPreviewUri != soundUri) {
            soundPreviewPlayer?.release()
            soundPreviewPlayer = null
            soundPreviewUri = soundUri
            soundUri?.let { uri ->
                soundPreviewPlayer = ExoPlayer.Builder(this).build().apply {
                    setMediaItem(MediaItem.fromUri(uri))
                    volume = soundVolume / 100f
                    prepare()
                }
            }
        }

        val videoIsPlaying = player?.isPlaying == true &&
            (timelinePreviewLoaded || previewSingleClip) && !exporting
        val outputTimeMs = timelinePlayheadMs.coerceAtLeast(0L)

        musicPreviewPlayer?.let { music ->
            music.volume = musicVolume / 100f
            if (!videoIsPlaying) {
                music.playWhenReady = false
            } else {
                val duration = music.duration
                if (duration > 0L && duration != androidx.media3.common.C.TIME_UNSET) {
                    val target = outputTimeMs % duration
                    if (kotlin.math.abs(music.currentPosition - target) > 400L) music.seekTo(target)
                }
                music.playWhenReady = true
            }
        }

        soundPreviewPlayer?.let { sound ->
            sound.volume = soundVolume / 100f
            val outputDurationMs = totalOutputDurationMs()
            val effectStart = soundStartMs.coerceIn(0L, outputDurationMs)
            val effectEnd = (effectStart + soundDurationMs.coerceAtMost((outputDurationMs - effectStart).coerceAtLeast(0L)))
                .coerceAtLeast(effectStart)
            val insideEffect = videoIsPlaying && outputTimeMs >= effectStart &&
                outputTimeMs < effectEnd && soundDurationMs > 0L
            if (insideEffect) {
                val target = (outputTimeMs - effectStart).coerceAtLeast(0L)
                if (kotlin.math.abs(sound.currentPosition - target) > 400L) sound.seekTo(target)
                sound.playWhenReady = true
            } else {
                sound.playWhenReady = false
            }
        }
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
        val smokeTest = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (smokeTest) android.util.Log.i("EditorSmokeTest", "EDITOR_SMOKE_ADDCLIP_STARTED uri=$uri")
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
            if (smokeTest) android.util.Log.i(
                "EditorSmokeTest",
                "EDITOR_SMOKE_METADATA_READ durationMs=$duration title=$title uri=$uri"
            )
            runOnUiThread {
                if (duration <= 0L) {
                    if ((applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) android.util.Log.e(
                        "EditorSmokeTest",
                        "EDITOR_SMOKE_CLIP_REJECTED durationMs=$duration uri=$uri"
                    )
                    status.text = "Bu videonun süresi okunamadı. Başka bir video dosyası seç."
                    return@runOnUiThread
                }
                recordClipEdit()
                if (replaceExisting) {
                    clips.clear()
                    selectedClipIndex = -1
                }
                timelinePreviewLoaded = false
                previewSingleClip = true
                clips.add(Clip(uri, title, duration, 0L, duration))
                selectedClipIndex = clips.lastIndex
                player?.setMediaItem(MediaItem.fromUri(uri))
                player?.prepare()
                player?.playWhenReady = true
                refreshClipList()
                loadTrimControls()
                refreshTimelineTracks()
                status.text = "${title} eklendi. Kesme aralığını seçebilirsin."
                // A debug-only signal lets CI verify clip import even when the
                // emulator accessibility tree cannot be captured reliably.
                if ((applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) android.util.Log.i(
                    "EditorSmokeTest",
                    "EDITOR_SMOKE_CLIP_ADDED title=$title durationMs=$duration clipCount=${clips.size}"
                )
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
        val textLayerArray = JSONArray()
        textLayers.forEach { layer ->
            textLayerArray.put(JSONObject().apply {
                put("id", layer.id)
                put("text", layer.text)
                put("startMs", layer.startMs)
                put("endMs", layer.endMs)
                put("timingCustomized", layer.timingCustomized)
                put("position", layer.position.name)
                put("sizeSp", layer.sizeSp)
                put("xFraction", layer.xFraction)
                put("yFraction", layer.yFraction)
                put("colorPreset", layer.colorPreset.name)
                put("isBold", layer.isBold)
            })
        }
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
            put("timelinePlayheadMs", timelinePlayheadMs.coerceIn(0L, totalOutputDurationMs()))
            put("timelineZoom", timelineZoom)
            val currentScrollX = timelineScrollViews.firstOrNull()?.scrollX ?: timelineHorizontalScrollX
            put("timelineHorizontalScrollX", currentScrollX)
            put("timelineHorizontalScrollMs", TimelineScaleMath.millisecondsForPixels(
                currentScrollX.toFloat(), timelinePixelsPerSecond()
            ).coerceAtLeast(0L))
            put("musicUri", musicUri?.toString() ?: JSONObject.NULL)
            put("musicTitle", musicTitle ?: JSONObject.NULL)
            put("soundUri", soundUri?.toString() ?: JSONObject.NULL)
            put("soundTitle", soundTitle ?: JSONObject.NULL)
            put("soundDurationMs", soundDurationMs)
            put("soundStartMs", soundStartMs)
            put("text", textInput.text?.toString().orEmpty())
            put("textColorPreset", textColorPreset.name)
            put("textBold", textBold)
            put("textLayers", textLayerArray)
            put("selectedTextLayerId", selectedTextLayerId ?: JSONObject.NULL)
            put("textStartMs", textStartMs)
            put("textEndMs", textEndMs)
            put("textTimingCustomized", textTimingCustomized)
            put("textPosition", textPosition.name)
            put("textSizeSp", textSizeSp)
            put("sourceAudioVolume", sourceAudioVolume)
            put("musicVolume", musicVolume)
            put("soundVolume", soundVolume)
            put("keepSourceAudio", keepSourceAudio.isChecked)
            put("exportName", exportNameInput.text?.toString().orEmpty())
            put("exportResolutionShortSide", exportResolutionShortSide)
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

        clearClipHistory()
        clips.clear()
        clips.addAll(loadedClips)
        selectedClipIndex = json.optInt("selectedClipIndex", 0).coerceIn(0, clips.lastIndex)
        timelinePreviewLoaded = false
        previewSingleClip = true
        timelineZoom = json.optDouble("timelineZoom", timelineZoom.toDouble())
            .toFloat().takeIf { it.isFinite() }?.coerceIn(0.5f, 8.0f) ?: 1.0f
        val savedTimelineScrollMs: Long? = when {
            json.has("timelineHorizontalScrollMs") ->
                json.optLong("timelineHorizontalScrollMs", 0L).coerceAtLeast(0L)
            json.has("timelineHorizontalScrollX") ->
                TimelineScaleMath.millisecondsForPixels(
                    json.optInt("timelineHorizontalScrollX", 0).coerceAtLeast(0).toFloat(),
                    timelinePixelsPerSecond()
                ).coerceAtLeast(0L)
            else -> null
        }
        timelineHorizontalScrollX = savedTimelineScrollMs?.let {
            TimelineScaleMath.pixelsForTime(it, timelinePixelsPerSecond())
        } ?: 0
        pendingTimelineScrollX = savedTimelineScrollMs?.let {
            TimelineScaleMath.pixelsForTime(it, timelinePixelsPerSecond())
        }

        val defaultPlayheadMs = clips.take(selectedClipIndex).sumOf {
            (it.endMs - it.startMs).coerceAtLeast(0L)
        }
        timelinePlayheadMs = if (json.has("timelinePlayheadMs")) {
            json.optLong("timelinePlayheadMs", defaultPlayheadMs)
                .coerceIn(0L, totalOutputDurationMs())
        } else {
            defaultPlayheadMs
        }
        val hasSavedTimelineScroll = savedTimelineScrollMs != null
        val restoredPosition = TimelinePositionMath.locate(
            clips.map { (it.endMs - it.startMs).coerceAtLeast(0L) },
            timelinePlayheadMs
        )
        if (restoredPosition != null) {
            selectedClipIndex = restoredPosition.clipIndex
            timelinePlayheadMs = restoredPosition.clipStartMs + restoredPosition.localPositionMs
        } else {
            selectedClipIndex = json.optInt("selectedClipIndex", 0).coerceIn(0, clips.lastIndex)
        }
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
        textSizeSp = json.optInt("textSizeSp", 24).coerceIn(12, 96)
        textLayers.clear()
        val savedTextLayers = json.optJSONArray("textLayers")
        if (savedTextLayers != null) {
            for (i in 0 until savedTextLayers.length()) {
                val item = savedTextLayers.optJSONObject(i) ?: continue
                val id = item.optLong("id", i + 1L).coerceAtLeast(1L)
                val start = item.optLong("startMs", 0L).coerceAtLeast(0L)
                val end = item.optLong("endMs", totalOutputDurationMs()).coerceAtLeast(start)
                val position = runCatching {
                    TextPosition.valueOf(item.optString("position", "BOTTOM"))
                }.getOrDefault(TextPosition.BOTTOM)
                textLayers.add(TextLayer(
                    id = id,
                    text = item.optString("text", ""),
                    startMs = start,
                    endMs = end,
                    timingCustomized = item.optBoolean("timingCustomized", false),
                    position = position,
                    sizeSp = item.optInt("sizeSp", 24).coerceIn(12, 96),
                    colorPreset = runCatching {
                        TextColorPreset.valueOf(item.optString("colorPreset", "WHITE"))
                    }.getOrDefault(TextColorPreset.WHITE),
                    isBold = item.optBoolean("isBold", true),
                    xFraction = item.optDouble("xFraction", 0.5).toFloat().coerceIn(0f, 1f),
                    yFraction = item.optDouble("yFraction", defaultTextYFraction(position).toDouble()).toFloat().coerceIn(0f, 1f)
                ))
            }
        } else {
            val legacyText = json.optString("text", "")
            if (legacyText.isNotBlank()) {
                textLayers.add(TextLayer(
                    id = 1L,
                    text = legacyText,
                    startMs = textStartMs,
                    endMs = textEndMs,
                    timingCustomized = textTimingCustomized,
                    position = textPosition,
                    sizeSp = textSizeSp,
                    colorPreset = runCatching {
                        TextColorPreset.valueOf(json.optString("textColorPreset", "WHITE"))
                    }.getOrDefault(TextColorPreset.WHITE),
                    isBold = json.optBoolean("textBold", true),
                    xFraction = 0.5f,
                    yFraction = defaultTextYFraction(textPosition)
                ))
            }
        }
        nextTextLayerId = (textLayers.maxOfOrNull { it.id } ?: 0L) + 1L
        val savedSelectedTextId = json.optLong("selectedTextLayerId", textLayers.firstOrNull()?.id ?: -1L)
        selectedTextLayerId = savedSelectedTextId.takeIf { id -> textLayers.any { it.id == id } }
        sourceAudioVolume = json.optInt("sourceAudioVolume", 100).coerceIn(0, 100)
        musicVolume = json.optInt("musicVolume", 45).coerceIn(0, 100)
        soundVolume = json.optInt("soundVolume", 100).coerceIn(0, 100)

        loadTextLayerIntoControls(selectedTextLayer())
        refreshTextLayerList()
        keepSourceAudio.isChecked = json.optBoolean("keepSourceAudio", true)
        exportNameInput.setText(json.optString("exportName", "MathMotor_Edit"))
        exportResolutionShortSide = when (json.optInt("exportResolutionShortSide", 0)) {
            720 -> 720
            1080 -> 1080
            else -> 0
        }
        exportResolutionGroup.check(when (exportResolutionShortSide) {
            720 -> 302
            1080 -> 303
            else -> 301
        })
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
            val clipOffsetMs = clips.take(selectedClipIndex).sumOf {
                (it.endMs - it.startMs).coerceAtLeast(0L)
            }
            val localPositionMs = (timelinePlayheadMs - clipOffsetMs)
                .coerceIn(0L, (clip.endMs - clip.startMs).coerceAtLeast(0L))
            player?.seekTo(localPositionMs)
        }
        ensureTrimHandlesReachable(selectedClipIndex)
        if (hasSavedTimelineScroll) {
            val restoredScrollX = TimelineScaleMath.pixelsForTime(
                savedTimelineScrollMs ?: 0L,
                timelinePixelsPerSecond()
            )
            pendingTimelineScrollX = restoredScrollX
            timelineHorizontalScrollX = restoredScrollX
        }
        updateTimelinePlayheadControls()
        refreshTimelineTracks()
        status.text = "PROJE AÇILDI. Klipler, kesmeler, oynatma noktası, yazı ve ses ayarları geri yüklendi."
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_LEGACY_STORAGE) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            exportEditedVideo()
        } else {
            status.text = "Depolama izni verilmedi. Video kaydedilemedi."
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
                if (ready.isEmpty()) {
                    // Keep the existing timeline untouched if every selected file failed to load.
                    status.text = "Videolar okunamadı. Mevcut kliplerin korunması için zaman çizelgesi değiştirilmedi. Başka bir MP4/video dosyası seç."
                    return@runOnUiThread
                }
                recordClipEdit()
                if (replaceExisting) {
                    clips.clear()
                    selectedClipIndex = -1
                }
                clips.addAll(ready)
                selectedClipIndex = clips.lastIndex
                timelinePreviewLoaded = false
                previewSingleClip = true
                timelinePlayheadMs = clips.take(selectedClipIndex).sumOf { (it.endMs - it.startMs).coerceAtLeast(0L) }
                refreshClipList()
                loadTrimControls()
                refreshTimelineTracks()
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
        // This renderer is called during normal timeline playback at clip boundaries.
        // It must not invalidate the active playlist or the playhead sync loop.
        clipList.removeAllViews()
        if (::previewEmptyState.isInitialized) {
            previewEmptyState.visibility = if (clips.isEmpty()) View.VISIBLE else View.GONE
        }
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
                        timelinePreviewLoaded = false
                        previewSingleClip = true
                        timelinePlayheadMs = clips.take(index).sumOf { (it.endMs - it.startMs).coerceAtLeast(0L) }
                        ensureTrimHandlesReachable(index)
                        refreshClipList()
                        loadTrimControls()
                        updateTimelinePlayheadLabel()
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
                    text = formatTimelineMs(clip.endMs - clip.startMs)
                    textSize = 10f
                    setTextColor(Color.rgb(205, 218, 231))
                })
                tile.setOnLongClickListener {
                    tile.parent?.requestDisallowInterceptTouchEvent(true)
                    val dragData = android.content.ClipData.newPlainText("video_clip_index", index.toString())
                    val started = tile.startDragAndDrop(dragData, View.DragShadowBuilder(tile), index, 0)
                    status.text = if (started) {
                        "Klibi sürükleyip istediğin sıraya bırak."
                    } else {
                        tile.parent?.requestDisallowInterceptTouchEvent(false)
                        "Klip sürükleme başlatılamadı. Tekrar basılı tut."
                    }
                    started
                }
                tile.setOnDragListener { _, event ->
                    when (event.action) {
                        android.view.DragEvent.ACTION_DRAG_STARTED -> event.clipDescription?.hasMimeType("text/plain") == true
                        android.view.DragEvent.ACTION_DRAG_ENTERED,
                        android.view.DragEvent.ACTION_DRAG_LOCATION,
                        android.view.DragEvent.ACTION_DRAG_EXITED -> true
                        android.view.DragEvent.ACTION_DROP -> {
                            val from = event.localState as? Int
                            if (from != null && from in clips.indices) reorderClip(from, index)
                            true
                        }
                        android.view.DragEvent.ACTION_DRAG_ENDED -> { tile.parent?.requestDisallowInterceptTouchEvent(false); true }
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

    private fun duplicateClipAt(index: Int) {
        val source = clips.getOrNull(index) ?: return
        recordClipEdit()
        clips.add(index + 1, source.copy(title = "${source.title} • Kopya"))
        selectedClipIndex = index + 1
        timelinePreviewLoaded = false
        previewSingleClip = true
        timelinePlayheadMs = clips.take(selectedClipIndex).sumOf { (it.endMs - it.startMs).coerceAtLeast(0L) }
        refreshClipList()
        loadTrimControls()
        updateTimelinePlayheadLabel()
        player?.setMediaItem(clippedMediaItem(clips[selectedClipIndex]))
        player?.prepare()
        player?.playWhenReady = false
        status.text = "Klip kopyalandı ve hemen arkasına eklendi."
    }

    private fun removeClipAt(index: Int) {
        if (index !in clips.indices) return
        recordClipEdit()
        clips.removeAt(index)
        timelinePreviewLoaded = false
        selectedClipIndex = when {
            clips.isEmpty() -> -1
            index < clips.size -> index
            else -> clips.lastIndex
        }
        refreshClipList()
        loadTrimControls()
        val selected = selectedClip()
        if (selected == null) {
            previewSingleClip = false
            player?.clearMediaItems()
        } else {
            previewSingleClip = true
            player?.setMediaItem(clippedMediaItem(selected))
            player?.prepare()
            player?.playWhenReady = false
        }
        timelinePlayheadMs = clips.take(selectedClipIndex.coerceAtLeast(0)).sumOf { (it.endMs - it.startMs).coerceAtLeast(0L) }
        updateTimelinePlayheadLabel()
        status.text = if (clips.isEmpty()) "Zaman çizelgesi boş." else "Klip silindi."
    }

    private fun removeSelectedClip() {
        if (selectedClipIndex !in clips.indices) {
            status.text = "Önce kaldırılacak klibi seç."
            return
        }
        // Keep the button and long-press menu on the same, fully-synchronised removal path.
        removeClipAt(selectedClipIndex)
    }

    private fun reorderClip(from: Int, to: Int) {
        if (from !in clips.indices || to !in clips.indices || from == to) return
        recordClipEdit()
        val previouslySelected = selectedClip()
        val moved = clips.removeAt(from)
        clips.add(to, moved)
        selectedClipIndex = clips.indexOfFirst { it === previouslySelected }.takeIf { it >= 0 } ?: to
        timelinePreviewLoaded = false
        previewSingleClip = selectedClipIndex in clips.indices
        timelinePlayheadMs = clips.take(selectedClipIndex.coerceAtLeast(0)).sumOf { (it.endMs - it.startMs).coerceAtLeast(0L) }
        refreshClipList()
        loadTrimControls()
        val selected = selectedClip()
        if (selected != null) {
            player?.setMediaItem(clippedMediaItem(selected))
            player?.prepare()
            player?.playWhenReady = false
        }
        updateTimelinePlayheadLabel()
        status.text = "Klip sırası değişti. Dışa aktarma bu yeni sırayı kullanacak."
    }

    private fun splitSelectedClipAtPlayhead(expectedClipIndex: Int? = null) {
        if (clips.isEmpty()) {
            status.text = "Önce bölmek istediğin klibi seç."
            return
        }
        val totalMs = totalOutputDurationMs()
        if (totalMs <= 0L) {
            status.text = "Bölünecek bir video süresi yok."
            return
        }
        val playheadMs = timelinePlayheadMs.coerceIn(0L, (totalMs - 1L).coerceAtLeast(0L))

        // Normalize single-clip preview into the full sequence so a split always uses
        // the same global timeline time regardless of which preview mode was active.
        if (!timelinePreviewLoaded) {
            ensureTimelinePreviewLoaded()
            if (timelinePreviewLoaded) seekTimelinePreview(playheadMs)
        }

        val position = TimelinePositionMath.locate(
            clips.map { (it.endMs - it.startMs).coerceAtLeast(0L) },
            playheadMs
        )
        if (position == null) {
            status.text = "Oynatma noktası bir kliple eşleştirilemedi."
            return
        }
        if (expectedClipIndex != null && expectedClipIndex != position.clipIndex) {
            status.text = "Bölmek istediğin klibi seçtikten sonra kırmızı çizgiyi o klibin içine getir."
            return
        }

        selectedClipIndex = position.clipIndex
        val clip = clips[position.clipIndex]
        val duration = (clip.endMs - clip.startMs).coerceAtLeast(0L)
        val localPosition = position.localPositionMs
        val splitAt = clip.startMs + localPosition
        if (localPosition < 350L || duration - localPosition < 350L) {
            status.text = "Bölmek için oynatma noktasını klibin ortalarına getir; iki tarafta da en az 0,35 saniye olmalı."
            return
        }
        recordClipEdit()
        clips[position.clipIndex] = clip.copy(title = "${clip.title} • A", endMs = splitAt)
        clips.add(position.clipIndex + 1, clip.copy(title = "${clip.title} • B", startMs = splitAt))
        selectedClipIndex = position.clipIndex + 1
        timelinePreviewLoaded = false
        previewSingleClip = true
        timelinePlayheadMs = clips.take(selectedClipIndex).sumOf { (it.endMs - it.startMs).coerceAtLeast(0L) }
        refreshClipList()
        loadTrimControls()
        updateTimelinePlayheadLabel()
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
        recordClipEdit()
        val clip = clips.removeAt(selectedClipIndex)
        clips.add(target, clip)
        timelinePreviewLoaded = false
        selectedClipIndex = target
        previewSingleClip = true
        timelinePlayheadMs = clips.take(selectedClipIndex).sumOf { (it.endMs - it.startMs).coerceAtLeast(0L) }
        refreshClipList()
        loadTrimControls()
        updateTimelinePlayheadLabel()
        player?.setMediaItem(clippedMediaItem(clip))
        player?.prepare()
        player?.playWhenReady = false
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

    private fun buildVideoClipFilmstrip(
        clip: Clip,
        index: Int,
        width: Int,
        height: Int,
        selected: Boolean
    ): FrameLayout {
        val durationMs = (clip.endMs - clip.startMs).coerceAtLeast(0L)
        val container = FrameLayout(this).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.rgb(27, 39, 54))
                cornerRadius = dp(4).toFloat()
            }
            foreground = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = dp(4).toFloat()
                setStroke(dp(if (selected) 2 else 1),
                    if (selected) Color.rgb(117, 240, 221) else Color.rgb(61, 79, 99))
            }
            clipToOutline = true
            isClickable = true
            isFocusable = true
            setOnClickListener { selectTimelineClip(index) }
            var filmstripTouchActive = false
            var filmstripMoved = false
            var filmstripLongPressed = false
            var filmstripDownX = 0f
            val filmstripLongPress = Runnable {
                if (filmstripTouchActive && !filmstripMoved) {
                    filmstripLongPressed = performLongClick()
                }
            }
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        filmstripTouchActive = true
                        filmstripMoved = false
                        filmstripLongPressed = false
                        filmstripDownX = event.x
                        player?.playWhenReady = false
                        // The strip itself implements scrubbing and press-and-hold actions.
                        // Prevent its horizontal parent from stealing the gesture mid-press.
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                        timelineHandler.postDelayed(
                            filmstripLongPress,
                            android.view.ViewConfiguration.getLongPressTimeout().toLong()
                        )
                        true
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        if (kotlin.math.abs(event.x - filmstripDownX) > android.view.ViewConfiguration.get(this@VideoEditorActivity).scaledTouchSlop) {
                            filmstripMoved = true
                            timelineHandler.removeCallbacks(filmstripLongPress)
                            if (!filmstripLongPressed) {
                                val safeWidth = view.width.coerceAtLeast(1)
                                val fraction = (event.x / safeWidth.toFloat()).coerceIn(0f, 1f)
                                val offsetBeforeClip = clips.take(index).sumOf { (it.endMs - it.startMs).coerceAtLeast(0L) }
                                val clipDuration = (clip.endMs - clip.startMs).coerceAtLeast(0L)
                                val targetMs = offsetBeforeClip + (clipDuration * fraction).toLong()
                                seekTimelinePreview(targetMs.coerceIn(0L, totalOutputDurationMs()))
                            }
                        }
                        true
                    }
                    android.view.MotionEvent.ACTION_UP -> {
                        timelineHandler.removeCallbacks(filmstripLongPress)
                        filmstripTouchActive = false
                        view.parent?.requestDisallowInterceptTouchEvent(false)
                        if (!filmstripLongPressed) {
                            val safeWidth = view.width.coerceAtLeast(1)
                            val fraction = (event.x / safeWidth.toFloat()).coerceIn(0f, 1f)
                            val offsetBeforeClip = clips.take(index).sumOf { (it.endMs - it.startMs).coerceAtLeast(0L) }
                            val clipDuration = (clip.endMs - clip.startMs).coerceAtLeast(0L)
                            val targetMs = (offsetBeforeClip + (clipDuration * fraction).toLong())
                                .coerceIn(0L, totalOutputDurationMs().coerceAtLeast(1L) - 1L)
                            if (!filmstripMoved) selectTimelineClip(index)
                            seekTimelinePreview(targetMs)
                        }
                        true
                    }
                    android.view.MotionEvent.ACTION_CANCEL -> {
                        timelineHandler.removeCallbacks(filmstripLongPress)
                        filmstripTouchActive = false
                        view.parent?.requestDisallowInterceptTouchEvent(false)
                        true
                    }
                    else -> true
                }
            }
            setOnLongClickListener {
                val menu = android.widget.PopupMenu(this@VideoEditorActivity, this)
                menu.menu.add(0, 1, 0, "Kopyala")
                menu.menu.add(0, 2, 1, "Sil")
                menu.menu.add(0, 3, 2, "Böl")
                menu.menu.add(0, 4, 3, "Bir önceye al")
                menu.menu.add(0, 5, 4, "Bir sonraya al")
                menu.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        1 -> duplicateClipAt(index)
                        2 -> removeClipAt(index)
                        3 -> splitSelectedClipAtPlayhead(expectedClipIndex = index)
                        4 -> { selectTimelineClip(index); moveSelectedClip(-1) }
                        5 -> { selectTimelineClip(index); moveSelectedClip(1) }
                    }
                    true
                }
                menu.show()
                true
            }
            setOnDragListener { _, event ->
                when (event.action) {
                    android.view.DragEvent.ACTION_DRAG_STARTED ->
                        event.clipDescription?.hasMimeType("text/plain") == true
                    android.view.DragEvent.ACTION_DRAG_ENTERED,
                    android.view.DragEvent.ACTION_DRAG_LOCATION,
                    android.view.DragEvent.ACTION_DRAG_EXITED -> true
                    android.view.DragEvent.ACTION_DROP -> {
                        val from = event.localState as? Int
                        if (from != null && from in clips.indices) reorderClip(from, index)
                        true
                    }
                    android.view.DragEvent.ACTION_DRAG_ENDED -> true
                    else -> false
                }
            }
            contentDescription = "Klip ${index + 1}: ${clip.title}, ${formatTimelineMs(durationMs)}"
        }
        val filmstrip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.rgb(26, 37, 51))
        }
        // Cap decode work per clip to keep long timelines responsive and avoid a stale queue.
        val thumbnailCount = (width / dp(18)).coerceIn(2, 24)
        repeat(thumbnailCount) { slot ->
            val image = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(Color.rgb(31, 46, 59))
                contentDescription = "Klip karesi"
            }
            filmstrip.addView(image, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                if (slot < thumbnailCount - 1) rightMargin = dp(1)
            })
            val frameTime = clip.startMs + (durationMs * (slot + 1L) / (thumbnailCount + 1L))
            requestTimelineThumbnail(clip.uri, frameTime, image)
        }
        container.addView(filmstrip, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))

        // Larger, high-contrast touch targets make the trim edges easy to find and drag.
        fun addTrimHandle(isStart: Boolean) {
            // Bound handle travel by source-time limits, not rendered clip width.
            // Otherwise short trimmed clips cannot be expanded back toward the source boundaries.
            val handleWidth = minOf(dp(40), (width / 2).coerceAtLeast(1))
            fun allowedDeltaPx(startMs: Long, endMs: Long): Pair<Float, Float> {
                val baseMs = if (isStart) startMs else endMs
                val minMs = if (isStart) 0L else TimelineTrimMath.minEndMs(startMs, clip.durationMs)
                val maxMs = if (isStart) TimelineTrimMath.maxStartMs(endMs, clip.durationMs) else clip.durationMs
                val basePx = TimelineScaleMath.pixelsForTime(baseMs, timelinePixelsPerSecond())
                val minPx = TimelineScaleMath.pixelsForTime(minMs, timelinePixelsPerSecond())
                val maxPx = TimelineScaleMath.pixelsForTime(maxMs, timelinePixelsPerSecond())
                return (minPx - basePx).toFloat() to (maxPx - basePx).toFloat()
            }
            val handle = FrameLayout(this).apply {
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.rgb(0, 205, 177))
                    cornerRadius = dp(4).toFloat()
                    setStroke(dp(2), Color.WHITE)
                }
                elevation = dp(8).toFloat()
                contentDescription = if (isStart) "Sol tutamaç: klibin başlangıcını sürükleyerek kısalt veya uzat" else "Sağ tutamaç: klibin bitişini sürükleyerek kısalt veya uzat"
                val grip = TextView(this@VideoEditorActivity).apply {
                    text = if (isStart) "❮" else "❯"
                    textSize = 13f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    isClickable = false
                }
                addView(grip, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                var downRawX = 0f
                var initialStartMs = clip.startMs
                var initialEndMs = clip.endMs
                var moved = false
                setOnTouchListener { view, event ->
                    when (event.actionMasked) {
                        android.view.MotionEvent.ACTION_DOWN -> {
                            downRawX = event.rawX
                            initialStartMs = clip.startMs
                            initialEndMs = clip.endMs
                            moved = false
                            activeTrimHistorySnapshot = captureClipEditSnapshot()
                            player?.playWhenReady = false
                            view.parent?.requestDisallowInterceptTouchEvent(true)
                            true
                        }
                        android.view.MotionEvent.ACTION_MOVE -> {
                            val delta = event.rawX - downRawX
                            if (kotlin.math.abs(delta) > dp(2)) moved = true
                            val (minimumDeltaPx, maximumDeltaPx) = allowedDeltaPx(initialStartMs, initialEndMs)
                            // Keep the handle visible while the gesture crosses beyond the
                            // current cut edge to restore trimmed-away source footage.
                            // The committed time still uses the full raw pointer delta on ACTION_UP.
                            val visibleMinDelta = if (isStart) -handleWidth * 0.25f
                                else -(width - handleWidth).coerceAtLeast(0).toFloat()
                            val visibleMaxDelta = if (isStart) (width - handleWidth).coerceAtLeast(0).toFloat()
                                else handleWidth * 0.25f
                            val visualMin = maxOf(minimumDeltaPx, visibleMinDelta)
                            val visualMax = minOf(maximumDeltaPx, visibleMaxDelta)
                            view.translationX = delta.coerceIn(visualMin, maxOf(visualMin, visualMax))
                            true
                        }
                        android.view.MotionEvent.ACTION_UP -> {
                            val (minimumDeltaPx, maximumDeltaPx) = allowedDeltaPx(initialStartMs, initialEndMs)
                            val deltaPx = (event.rawX - downRawX).coerceIn(minimumDeltaPx, maximumDeltaPx)
                            val deltaMs = TimelineScaleMath.millisecondsForPixels(deltaPx, timelinePixelsPerSecond())
                            view.translationX = 0f
                            view.parent?.requestDisallowInterceptTouchEvent(false)
                            if (moved) {
                                val newStart = if (isStart) {
                                    TimelineTrimMath.clampStart(initialStartMs + deltaMs, initialEndMs, clip.durationMs)
                                } else initialStartMs
                                val newEnd = if (isStart) initialEndMs else {
                                    TimelineTrimMath.clampEnd(initialEndMs + deltaMs, initialStartMs, clip.durationMs)
                                }
                                if (newStart != initialStartMs || newEnd != initialEndMs) {
                                    if (isStart) clip.startMs = newStart else clip.endMs = newEnd
                                    selectedClipIndex = index
                                    finishTrimControlGesture()
                                    status.text = "✓ Başlangıç: ${formatTimelineMs(clip.startMs)} / Bitiş: ${formatTimelineMs(clip.endMs)}"
                                } else {
                                    activeTrimHistorySnapshot = null
                                }
                            } else {
                                activeTrimHistorySnapshot = null
                            }
                            view.performClick()
                            true
                        }
                        android.view.MotionEvent.ACTION_CANCEL -> {
                            view.translationX = 0f
                            activeTrimHistorySnapshot = null
                            view.parent?.requestDisallowInterceptTouchEvent(false)
                            true
                        }
                        else -> true
                    }
                }
                setOnClickListener { /* The handle is primarily a drag control. */ }
            }
            container.addView(
                handle,
                FrameLayout.LayoutParams(
                    handleWidth,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    if (isStart) Gravity.START else Gravity.END
                )
            )
        }
        addTrimHandle(isStart = true)
        addTrimHandle(isStart = false)

        val durationBadge = TextView(this).apply {
            text = formatTimelineMs(durationMs)
            textSize = if (height <= dp(18)) 7f else 8f
            setTextColor(Color.WHITE)
            setPadding(dp(3), 0, dp(3), 0)
            maxLines = 1
            gravity = Gravity.CENTER_VERTICAL
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.argb(190, 8, 13, 20))
                cornerRadius = dp(3).toFloat()
            }
        }
        container.addView(durationBadge, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            dp(if (height <= dp(18)) 12 else 14),
            Gravity.BOTTOM or Gravity.END
        ).apply { rightMargin = dp(2); bottomMargin = dp(2) })
        return container
    }

    private fun decodeTimelineFrame(uri: Uri, timeMs: Long): Bitmap? {
        val frameTimeUs = timeMs.coerceAtLeast(0L) * 1000L
        val retriever = MediaMetadataRetriever()
        val platformFrame = try {
            retriever.setDataSource(this, uri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                runCatching {
                    retriever.getScaledFrameAtTime(
                        frameTimeUs,
                        MediaMetadataRetriever.OPTION_CLOSEST,
                        96,
                        54
                    )
                }.getOrNull()
            } else {
                null
            } ?: runCatching {
                retriever.getFrameAtTime(frameTimeUs, MediaMetadataRetriever.OPTION_CLOSEST)
            }.getOrNull()
                ?: runCatching {
                    retriever.getFrameAtTime(frameTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                }.getOrNull()
                // Do not fall back to an untimestamped frame here: Android may
                // return the video's first frame for every requested slot, creating
                // a filmstrip full of incorrect/repeated thumbnails. Media3 below
                // retries the exact requested timestamp instead.
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
        if (platformFrame != null) return platformFrame

        // Fallback to Media3's decoder—the same playback stack used by the preview.
        // It is only initialized when Android's platform decoder returned no frame.
        var extractor: FrameExtractor? = null
        var frameFuture: java.util.concurrent.Future<FrameExtractor.Frame>? = null
        try {
            val createdExtractor = FrameExtractor.Builder(this, MediaItem.fromUri(uri))
                .setEffects(listOf(Presentation.createForHeight(96)))
                .build()
            extractor = createdExtractor
            val future = createdExtractor.getFrame(timeMs.coerceAtLeast(0L))
            frameFuture = future
            return future.get(4L, TimeUnit.SECONDS).bitmap
        } catch (_: Exception) {
            frameFuture?.cancel(true)
            return null
        } finally {
            runCatching { extractor?.close() }
        }
    }

    private fun requestTimelineThumbnail(uri: Uri, timeMs: Long, target: ImageView) {
        val timestampMs = timeMs.coerceAtLeast(0L)
        val key = "${uri}#${timestampMs}"
        target.tag = key
        val cached = timelineThumbnailCache.get(key)
        if (cached != null) {
            target.setImageBitmap(cached)
            return
        }
        val waiting = pendingTimelineThumbnails[key]
        if (waiting != null) {
            waiting.add(WeakReference(target))
            return
        }
        pendingTimelineThumbnails[key] = mutableListOf(WeakReference(target))
        try {
            timelineThumbnailExecutor.execute {
                var result: Bitmap? = null
                try {
                    val original = decodeTimelineFrame(uri, timestampMs)
                    if (original != null && !original.isRecycled && original.width > 0 && original.height > 0) {
                        val scale = maxOf(96f / original.width, 54f / original.height)
                        val scaledWidth = (original.width * scale).toInt().coerceAtLeast(96)
                        val scaledHeight = (original.height * scale).toInt().coerceAtLeast(54)
                        val scaled = Bitmap.createScaledBitmap(original, scaledWidth, scaledHeight, true)
                        if (scaled !== original && !original.isRecycled) original.recycle()
                        val left = ((scaled.width - 96) / 2).coerceAtLeast(0)
                        val top = ((scaled.height - 54) / 2).coerceAtLeast(0)
                        result = Bitmap.createBitmap(scaled, left, top, 96, 54)
                        if (result !== scaled && !scaled.isRecycled) scaled.recycle()
                    } else {
                        original?.let { if (!it.isRecycled) it.recycle() }
                    }
                } catch (_: Exception) {
                    // Keep the neutral placeholder visible only if both decoders fail.
                }
                val decoded = result
                runOnUiThread {
                    val targets = pendingTimelineThumbnails.remove(key).orEmpty()
                    if (decoded != null && !isFinishing && !isDestroyed) {
                        timelineThumbnailCache.put(key, decoded)
                        targets.forEach { ref ->
                            val image = ref.get()
                            if (image != null && image.tag == key) image.setImageBitmap(decoded)
                        }
                    } else {
                        decoded?.recycle()
                    }
                }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            pendingTimelineThumbnails.remove(key)
        }
    }

    private fun finishTrimControlGesture() {
        deferTimelineRefresh = false
        finishTrimHistoryGesture()
        val clip = selectedClip()
        if (clip != null) {
            timelinePreviewLoaded = false
            previewSingleClip = true
            player?.playWhenReady = false
            timelinePlayheadMs = clips.take(selectedClipIndex).sumOf {
                (it.endMs - it.startMs).coerceAtLeast(0L)
            }
            ensureTrimHandlesReachable(selectedClipIndex)
            player?.setMediaItem(clippedMediaItem(clip))
            player?.prepare()
            player?.seekTo(0L)
        } else {
            timelinePreviewLoaded = false
            previewSingleClip = false
        }
        refreshClipList()
        loadTrimControls()
        updateTimelinePlayheadLabel()
        status.text = "Kesme aralığı güncellendi."
    }

    private fun installTrimListeners() {
        startSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val clip = selectedClip() ?: return
                clip.startMs = TimelineTrimMath.clampStart(
                    TimelineTrimMath.timeForProgress(progress, clip.durationMs),
                    clip.endMs,
                    clip.durationMs
                )
                val actualProgress = TimelineTrimMath.progressForTime(clip.startMs, clip.durationMs)
                if (seekBar?.progress != actualProgress) seekBar?.progress = actualProgress
                updateTrimLabels()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                player?.playWhenReady = false
                deferTimelineRefresh = true
                activeTrimHistorySnapshot = captureClipEditSnapshot()
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                finishTrimControlGesture()
            }
        })
        endSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val clip = selectedClip() ?: return
                clip.endMs = TimelineTrimMath.clampEnd(
                    TimelineTrimMath.timeForProgress(progress, clip.durationMs),
                    clip.startMs,
                    clip.durationMs
                )
                val actualProgress = TimelineTrimMath.progressForTime(clip.endMs, clip.durationMs)
                if (seekBar?.progress != actualProgress) seekBar?.progress = actualProgress
                updateTrimLabels()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                player?.playWhenReady = false
                deferTimelineRefresh = true
                activeTrimHistorySnapshot = captureClipEditSnapshot()
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                finishTrimControlGesture()
            }
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
        val durationProgressMax = TimelineTrimMath.progressMax(clip.durationMs)
        startSeek.max = durationProgressMax
        endSeek.max = durationProgressMax
        startSeek.progress = TimelineTrimMath.progressForTime(clip.startMs, clip.durationMs)
        endSeek.progress = TimelineTrimMath.progressForTime(clip.endMs, clip.durationMs)
        selectedClipLabel.text = "Seçili klip: ${clip.title} • Kesit ${formatTimelineMs((clip.endMs - clip.startMs).coerceAtLeast(0L))} / Kaynak ${formatTimelineMs(clip.durationMs)}"
        updateTrimLabels()
    }

    private fun updateTrimLabels() {
        val clip = selectedClip()
        startLabel.text = "Başlangıç: ${formatTimelineMs(clip?.startMs ?: 0L)}"
        endLabel.text = "Bitiş: ${formatTimelineMs(clip?.endMs ?: 0L)}"
        updateSoundControls()
        updateTextControls()
    }

    private fun totalOutputDurationMs(): Long =
        clips.sumOf { (it.endMs - it.startMs).coerceAtLeast(0L) }

    private fun updateSoundControls() {
        if (!::soundStartSeek.isInitialized || !::soundStartLabel.isInitialized) return
        val total = totalOutputDurationMs()
        soundStartSeek.max = TimelineTrimMath.progressMax(total)
        soundStartMs = soundStartMs.coerceAtMost((total - 1L).coerceAtLeast(0L))
        soundStartSeek.progress = TimelineTrimMath.progressForTime(soundStartMs, total)
        soundStartSeek.isEnabled = soundUri != null && total > 0L
        soundStartLabel.text = "Ses başlangıcı: ${formatTimelineMs(soundStartMs)}"
    }

    private fun previewSelectedClip() {
        timelinePreviewLoaded = false
        previewSingleClip = true
        val clip = selectedClip()
        if (clip == null) {
            status.text = "Önizleme için önce video seç."
            return
        }
        timelinePlayheadMs = clips.take(selectedClipIndex).sumOf { (it.endMs - it.startMs).coerceAtLeast(0L) }
        updateTimelinePlayheadLabel()
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

    private fun buildTimedTextOverlay(layer: TextLayer, samePositionIndex: Int): TextOverlay {
        val overlayText = layer.text
        val startMs = layer.startMs
        val endMs = layer.endMs
        val styled = SpannableString(overlayText).apply {
            setSpan(ForegroundColorSpan(layer.colorPreset.colorInt), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(BackgroundColorSpan(Color.argb(190, 0, 0, 0)), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (layer.isBold) {
                setSpan(StyleSpan(Typeface.BOLD), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            setSpan(
                AbsoluteSizeSpan((layer.sizeSp * resources.displayMetrics.scaledDensity).toInt(), false),
                0,
                length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        val x = (layer.xFraction.coerceIn(0f, 1f) * 2f - 1f).coerceIn(-0.92f, 0.92f)
        val y = ((0.5f - layer.yFraction.coerceIn(0f, 1f)) * 2f).coerceIn(-0.85f, 0.85f)
        val settings = StaticOverlaySettings.Builder()
            .setBackgroundFrameAnchor(x, y)
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
        if (Build.VERSION.SDK_INT in 26..28 &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), REQUEST_LEGACY_STORAGE)
            status.text = "Android'ın eski sürümlerinde MP4 kaydetmek için depolama izni gerekiyor."
            return
        }
        if (clips.isEmpty()) {
            status.text = "Önce bir video seç."
            return
        }
        val invalid = clips.firstOrNull { it.startMs < 0 || it.endMs <= it.startMs || it.endMs > it.durationMs }
        if (invalid != null) {
            status.text = "Bir klibin kesme aralığı geçersiz. Başlangıç ve bitişi kontrol et."
            return
        }

        syncSelectedTextLayerFromControls()
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
        player?.pause()
        musicPreviewPlayer?.pause()
        soundPreviewPlayer?.pause()
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
        val outputVideoEffects = mutableListOf<androidx.media3.common.Effect>()
        when (exportResolutionShortSide) {
            720 -> outputVideoEffects.add(Presentation.createForShortSide(720))
            1080 -> outputVideoEffects.add(Presentation.createForShortSide(1080))
        }
        val exportTextLayers = textLayers.filter { it.text.isNotBlank() && it.endMs > it.startMs }
        if (exportTextLayers.isNotEmpty()) {
            val positionCounts = mutableMapOf<TextPosition, Int>()
            val overlays = exportTextLayers.map { layer ->
                val samePositionIndex = positionCounts[layer.position] ?: 0
                positionCounts[layer.position] = samePositionIndex + 1
                buildTimedTextOverlay(layer, samePositionIndex)
            }
            outputVideoEffects.add(OverlayEffect(overlays))
        }
        if (outputVideoEffects.isNotEmpty()) {
            compositionBuilder.setEffects(Effects(emptyList(), outputVideoEffects))
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
                                    val savedTitle = requestedExportName.trim()
                                        .removeSuffix(".mp4").removeSuffix(".MP4")
                                        .ifBlank { "Düzenlenmiş video" }
                                    VideoRecordStore.add(this@VideoEditorActivity, VideoRecord(savedTitle, resultUri, now, now))
                                    status.text = "BAŞARILI • " + savedTitle + " kaydedildi.\n" + resultUri
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
        // Round to centiseconds rather than truncating the real player position to tenths.
        val centiseconds = (ms.coerceAtLeast(0L) + 5L) / 10L
        val totalSeconds = centiseconds / 100L
        val fraction = (centiseconds % 100L).toInt()
        return String.format(Locale.US, "%02d:%02d.%02d", totalSeconds / 60L, totalSeconds % 60L, fraction)
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
        saveLocalDraftIfNeeded()
        player?.pause()
        musicPreviewPlayer?.pause()
        soundPreviewPlayer?.pause()
        super.onStop()
    }

    private fun saveLocalDraftIfNeeded() {
        if (clips.isEmpty()) return
        val id = draftId ?: return
        runCatching {
            val typedTitle = if (::exportNameInput.isInitialized) exportNameInput.text?.toString()?.trim().orEmpty() else ""
            val title = typedTitle.takeIf { it.isNotBlank() && it != "MathMotor_Edit" }
                ?: clips.firstOrNull()?.title
                ?: "Video taslağı"
            check(VideoProjectDraftStore.save(this, id, title, createProjectJson())) {
                "Yerel taslak dosyası kaydedilemedi."
            }
            android.util.Log.i("MathMotorDraft", "LOCAL_DRAFT_SAVED id=$id clips=${clips.size}")
        }.onFailure {
            android.util.Log.w("MathMotorDraft", "Local draft autosave failed", it)
        }
    }

    override fun onDestroy() {
        timelineHandler.removeCallbacks(timelineSyncTask)
        timelineHandler.removeCallbacks(textTimelineRefreshTask)
        timelineThumbnailExecutor.shutdownNow()
        pendingTimelineThumbnails.clear()
        timelineThumbnailCache.evictAll()
        playerView.player = null
        player?.release()
        player = null
        musicPreviewPlayer?.release()
        musicPreviewPlayer = null
        soundPreviewPlayer?.release()
        soundPreviewPlayer = null
        super.onDestroy()
    }
}

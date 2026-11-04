package com.mathmotor.video

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
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
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.RgbFilter
import androidx.media3.effect.MultipleInputVideoGraph
import androidx.media3.effect.Presentation
import androidx.media3.common.OverlaySettings
import androidx.media3.common.VideoCompositorSettings
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.effect.TextOverlay
import androidx.media3.inspector.frame.FrameExtractor
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.transformer.Composition
import androidx.media3.transformer.CompositionPlayer
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
        var endMs: Long = durationMs,
        var timelineStartMs: Long = 0L,
        var transformScale: Float = 1f,
        var transformRotationDegrees: Float = 0f,
        var transformTranslateX: Float = 0f,
        var transformTranslateY: Float = 0f,
        var frameRate: Double? = null,
        var coverTimeMs: Long? = null,
        var speed: Float = 1f
    )

    private data class OverlayClip(
        val id: Long,
        val uri: Uri,
        val title: String,
        val isImage: Boolean,
        val sourceDurationMs: Long,
        var sourceStartMs: Long = 0L,
        var timelineStartMs: Long = 0L,
        var durationMs: Long = sourceDurationMs,
        var scale: Float = 0.35f,
        var xFraction: Float = 0.78f,
        var yFraction: Float = 0.22f,
        var rotationDegrees: Float = 0f,
        var opacity: Float = 1f,
        val keyframes: MutableList<OverlayTransformKeyframe> = mutableListOf()
    )

    private data class ClipEditSnapshot(
        val clips: List<Clip>,
        val selectedClipIndex: Int,
        val timelinePlayheadMs: Long,
        val previewSingleClip: Boolean
    )

    private val clips = mutableListOf<Clip>()
    private val overlayClips = mutableListOf<OverlayClip>()
    private var nextOverlayId = 1L
    private var selectedOverlayId: Long? = null
    private var draftId: String? = null
    private val undoClipHistory = mutableListOf<ClipEditSnapshot>()
    private val redoClipHistory = mutableListOf<ClipEditSnapshot>()
    private var activeTrimHistorySnapshot: ClipEditSnapshot? = null
    private var undoClipButton: Button? = null
    private var redoClipButton: Button? = null
    private var selectedClipIndex = -1
    // Timeline trim chrome is shown only after an explicit clip selection tap.
    private var clipHandlesVisible = false
    private var musicUri: Uri? = null
    private var musicTitle: String? = null
    private var musicSourceDurationMs: Long = 0L
    private var musicSourceStartMs: Long = 0L
    private var musicSourceEndMs: Long = 0L
    private lateinit var musicSourceRangeLabel: TextView
    private lateinit var musicSourceStartSeek: SeekBar
    private lateinit var musicSourceEndSeek: SeekBar
    private var musicStartMs: Long = 0L
    private var musicDurationMs: Long = 0L
    private var soundUri: Uri? = null
    private var soundTitle: String? = null
    private var soundSourceDurationMs: Long = 0L
    private var soundSourceStartMs: Long = 0L
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
    private var textAnimationPreset = TextAnimationPreset.NONE
    private var textAnimationDurationMs = 350L
    private lateinit var textAnimationGroup: android.widget.RadioGroup
    private lateinit var textAnimationDurationLabel: TextView
    private lateinit var textAnimationDurationSeek: SeekBar
    private lateinit var textColorGroup: android.widget.RadioGroup
    private lateinit var textBoldCheck: CheckBox
    private lateinit var textOverlayEditToggle: Button
    private enum class TextPosition { TOP, CENTER, BOTTOM }
    private enum class TextLayerKind { TEXT, STICKER }
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
        var yFraction: Float = 0.84f,
        var kind: TextLayerKind = TextLayerKind.TEXT,
        var animationPreset: TextAnimationPreset = TextAnimationPreset.NONE,
        var animationDurationMs: Long = 350L
    )

    private val textLayers = mutableListOf<TextLayer>()
    private var selectedTextLayerId: Long? = null
    private var nextTextLayerId = 1L
    private var loadingTextLayerControls = false
    private var activePreviewTextLayers: List<TextLayer> = emptyList()
    private val previewTextOverlays = mutableListOf<TextView>()
    private lateinit var textLayerList: LinearLayout
    private var player: ExoPlayer? = null
    private var timelineCompositionPlayer: CompositionPlayer? = null
    private var musicPreviewPlayer: ExoPlayer? = null
    private var soundPreviewPlayer: ExoPlayer? = null
    private var musicPreviewUri: Uri? = null
    private var soundPreviewUri: Uri? = null
    private lateinit var playerView: PlayerView
    private lateinit var previewFrame: FrameLayout
    private lateinit var previewFrameOverlay: ImageView
    private val previewFrameRequestGeneration = java.util.concurrent.atomic.AtomicLong()
    private var pendingPreviewFrameTask: TimelineThumbnailTask? = null
    private var previewFrameBitmap: Bitmap? = null
    private var previewFrameCacheKey: String? = null
    private lateinit var previewFocusButton: Button
    private lateinit var playbackLeft: LinearLayout
    private lateinit var timelineRulerHeader: LinearLayout
    private lateinit var centeredPlaybackRow: FrameLayout
    private lateinit var fullscreenPlaybackOverlay: FrameLayout
    private var previewStageHeightDp = 340
    private var previewDefaultStageHeightDp = 340
    private var previewHeightAutoSized = false
    private var compactEditorLayout = false
    private var previewFocusMode = false
    private val previewFocusOriginalVisibility = linkedMapOf<View, Int>()
    private lateinit var previewTextOverlay: TextView
    private lateinit var clipTransformTouchOverlay: View
    private lateinit var overlayTransformTouchOverlay: View
    private lateinit var previewEmptyState: LinearLayout
    private lateinit var timelineTracks: LinearLayout
    private lateinit var timelineTracksFrame: FrameLayout
    private lateinit var timelinePlayheadLine: View
    private val timelineScrollViews = mutableListOf<android.widget.HorizontalScrollView>()
    // Order of the independently movable timeline strips, persisted with the project.
    private val timelineLaneOrder = mutableListOf<String>()
    private var syncingTimelineScroll = false
    private var autoScrollingTimeline = false
    private var timelineHorizontalScrollX = 0
    private var pendingTimelineScrollX: Int? = null
    // Single authoritative timeline scale in physical pixels per second.
    private var timelinePxPerSec = 0f
    private var timelineScaleGestureActive = false
    // Once a second finger starts a pinch inside the timeline, cancel the
    // one-finger child gesture and keep the Activity as the sole touch target.
    private var timelinePinchGestureCaptured = false
    private var timelineScaleAnchorTimeMs = 0L
    private var timelineScaleAnchorViewportX = 0f
    private var timelinePinchStartViewportX = 0f
    private var timelinePinchStartContentX = 0f
    private var timelinePinchBasePxPerSecond = 0f
    private var timelinePinchPreviewRatio = 1f
    // Keep one Activity-level detector so a second finger can cancel an active
    // clip/trim child gesture before taking ownership of the complete pinch.
    private val timelineScaleGestureDetector by lazy {
        ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                val lane = timelineScrollViews.firstOrNull() ?: return false
                if (lane.width <= 0 || !isWindowPointInsideTimeline(detector.focusX, detector.focusY)) return false
                // Freeze playback before the lane is visually transformed; otherwise
                // periodic player sync could scroll against the active pinch.
                pauseActivePreview()
                val location = IntArray(2)
                lane.getLocationInWindow(location)
                timelineScaleAnchorViewportX = detector.focusX - location[0]
                timelineScaleAnchorTimeMs = TimelineScaleMath.timeAtViewportX(
                    lane.scrollX,
                    timelineScaleAnchorViewportX,
                    timelineOriginSpacerWidth(lane.width).toFloat(),
                    timelinePixelsPerSecond()
                ).coerceIn(0L, totalOutputDurationMs())
                timelinePinchStartViewportX = timelineScaleAnchorViewportX
                timelinePinchStartContentX = lane.scrollX + timelinePinchStartViewportX
                timelinePinchBasePxPerSecond = timelinePixelsPerSecond()
                timelinePinchPreviewRatio = 1f
                timelineScaleGestureActive = true
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (!timelineScaleGestureActive) return false
                val lane = timelineScrollViews.firstOrNull() ?: return false
                val location = IntArray(2)
                lane.getLocationInWindow(location)
                timelineScaleAnchorViewportX = detector.focusX - location[0]

                val nextPxPerSecond = (
                    timelinePinchBasePxPerSecond *
                        timelinePinchPreviewRatio *
                        detector.scaleFactor
                    ).coerceIn(dp(11).toFloat(), dp(720).toFloat())
                timelinePinchPreviewRatio =
                    (nextPxPerSecond / timelinePinchBasePxPerSecond.coerceAtLeast(1f))
                        .coerceIn(
                            dp(11).toFloat() / timelinePinchBasePxPerSecond.coerceAtLeast(1f),
                            dp(720).toFloat() / timelinePinchBasePxPerSecond.coerceAtLeast(1f)
                        )

                // Preview the zoom with a view transform instead of rebuilding every
                // lane on every MotionEvent. The focal timestamp stays under the
                // moving two-finger midpoint; the actual pixel scale is committed once.
                val translationX = timelineScaleAnchorViewportX - timelinePinchStartViewportX
                timelineScrollViews.toList().forEach { laneScroll ->
                    val content = laneScroll.getChildAt(0) ?: return@forEach
                    content.pivotX = timelinePinchStartContentX
                    content.pivotY = content.height / 2f
                    content.scaleX = timelinePinchPreviewRatio
                    content.translationX = translationX
                }
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                finishTimelinePinch()
            }
        })
    }
    private var deferTimelineRefresh = false
    private val timelineThumbnailCache = object : android.util.LruCache<String, Bitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount.coerceAtLeast(1)
    }
    // Provider-generated poster appears only in the first cell while its exact frame is decoding.
    private val timelinePosterCache = object : android.util.LruCache<String, Bitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount.coerceAtLeast(1)
    }
    private val pendingTimelinePosterTargets = mutableMapOf<String, MutableList<WeakReference<ImageView>>>()
    private val pendingTimelinePosterLoads = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val timelinePosterExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val pendingTimelineThumbnails = mutableMapOf<String, MutableList<WeakReference<ImageView>>>()
    // Learned from the actual decoded frame, so imported and restored clips share
    // the same aspect-ratio-aware filmstrip path without blocking the UI for metadata.
    private val timelineVideoAspectRatios = java.util.concurrent.ConcurrentHashMap<String, Float>()
    private fun timelineVideoAspectRatio(uri: Uri): Float =
        timelineVideoAspectRatios[uri.toString()] ?: TimelineThumbnailMath.DEFAULT_SOURCE_ASPECT_RATIO

    private class TimelineThumbnailTask(
        private val distanceFromPlayheadMs: Long,
        private val sequence: Long,
        private val work: () -> Unit
    ) : Runnable, Comparable<TimelineThumbnailTask> {
        override fun compareTo(other: TimelineThumbnailTask): Int {
            val priority = distanceFromPlayheadMs.compareTo(other.distanceFromPlayheadMs)
            return if (priority != 0) priority else sequence.compareTo(other.sequence)
        }
        override fun run() = work()
    }
    private val timelineThumbnailTaskSequence = java.util.concurrent.atomic.AtomicLong()
    // Two isolated decoder workers overlap independent frame extraction. Each worker
    // owns its own MediaMetadataRetriever; priority still favours frames nearest playhead.
    private val timelineThumbnailWorkerSequence = java.util.concurrent.atomic.AtomicLong()
    private val timelineThumbnailRetrievers =
        java.util.concurrent.ConcurrentHashMap<Long, Pair<Uri, MediaMetadataRetriever>>()
    private val timelineThumbnailExtractors =
        java.util.concurrent.ConcurrentHashMap<Long, Pair<Uri, FrameExtractor>>()
    private val timelineThumbnailExecutor = java.util.concurrent.ThreadPoolExecutor(
        2,
        2,
        0L,
        java.util.concurrent.TimeUnit.MILLISECONDS,
        java.util.concurrent.PriorityBlockingQueue<Runnable>(
            11,
            java.util.Comparator { first, second ->
                (first as TimelineThumbnailTask).compareTo(second as TimelineThumbnailTask)
            }
        ),
        java.util.concurrent.ThreadFactory { worker ->
            Thread({
                try {
                    worker.run()
                } finally {
                    releaseTimelineThumbnailRetriever()
                    releaseTimelineThumbnailExtractor()
                }
            }, "timeline-thumbnail-\u0024{timelineThumbnailWorkerSequence.incrementAndGet()}")
        }
    )
    private lateinit var timelinePlayheadLabel: TextView
    private lateinit var timelinePlayButton: Button
    private var timelinePlayheadMs = 0L
    // Enabled only by the debug emulator test bridge; no telemetry is emitted in normal app use.
    private var timelineSyncTestEnabled = false
    // Local diagnostics are enabled only in debuggable builds; nothing is uploaded.
    private var timelineLifecycleDiagnosticsEnabled = false
    private var timelineLifecycleState = "not-created"
    private var timelinePreviewLoaded = false
    private var previewSingleClip = false
    private enum class TimelinePlaybackState { PAUSED, PLAYING, ERROR }
    private var timelinePlaybackState = TimelinePlaybackState.PAUSED
    private var timelinePreviewError: String? = null
    private val timelineHandler = Handler(Looper.getMainLooper())
    private var pendingTimelineScrubPreviewTargetMs: Long? = null
    private var lastTimelineScrubPreviewSeekUptimeMs = 0L
    private val timelineScrubPreviewSeekTask = object : Runnable {
        override fun run() {
            val targetMs = pendingTimelineScrubPreviewTargetMs ?: return
            pendingTimelineScrubPreviewTargetMs = null
            lastTimelineScrubPreviewSeekUptimeMs = SystemClock.uptimeMillis()
            // A finger scrub can request frames faster than the decoder can return.
            // Keep the last valid frame visible until the newest requested frame is ready.
            seekTimelinePreview(targetMs, preservePreviewWhileScrubbing = true)
        }
    }
    private val textTimelineRefreshTask = object : Runnable {
        override fun run() { refreshTimelineTracks() }
    }
    private val timelineSyncTask = object : Runnable {
        override fun run() {
            // A paused preview must not overwrite the position currently being scrubbed.
            // While playing, the composition player remains the source of truth.
            val composition = timelineCompositionPlayer
            if (timelinePreviewLoaded &&
                (composition?.isPlaying == true || composition?.playWhenReady == true)
            ) {
                syncTimelinePlayheadFromPlayer()
            } else if (previewSingleClip && player?.isPlaying == true) {
                syncSingleClipPlayheadFromPlayer()
            }
            syncPreviewAudio()
            updateTimelinePlayButtonState()
            if (timelineSyncTestEnabled) logTimelineSyncTestSample()
            // Keep the visible playhead close to the decoder clock while playing.
            // The previous 250ms polling made the ruler visibly trail the preview.
            timelineHandler.postDelayed(this, 50L)
        }
    }
    private val editorPanels = linkedMapOf<String, LinearLayout>()
    private lateinit var editorPanelScroll: ScrollView
    private lateinit var editorBottomSheet: View
    private lateinit var editorTimelineCard: View
    private lateinit var editorDock: LinearLayout
    private lateinit var editorDockScroll: android.widget.HorizontalScrollView
    private lateinit var clipVolumeLabel: TextView
    private lateinit var clipVolumeSeek: SeekBar
    private val editorTabButtons = linkedMapOf<String, Button>()
    private var activeEditorPanel: String? = null
    private var clipToolsMode = false
    private var activeClipTool: String? = null
    private var textOverlayEditingEnabled: Boolean = false
    private var selectedVideoFilter = VideoFilterPreset.ORIGINAL
    private var filterBrightness = 0
    private var filterContrast = 0
    private var filterSaturation = 0
    private var filterHue = 0
    private var updatingVideoFilterControls = false
    private val videoFilterButtons = linkedMapOf<VideoFilterPreset, Button>()
    private lateinit var videoFilterSummary: TextView
    private lateinit var filterBrightnessLabel: TextView
    private lateinit var filterContrastLabel: TextView
    private lateinit var filterSaturationLabel: TextView
    private lateinit var filterHueLabel: TextView
    private lateinit var filterBrightnessSeek: SeekBar
    private lateinit var filterContrastSeek: SeekBar
    private lateinit var filterSaturationSeek: SeekBar
    private lateinit var filterHueSeek: SeekBar
    private lateinit var selectedClipLabel: TextView
    private lateinit var overlayTitleLabel: TextView
    private lateinit var overlayStartLabel: TextView
    private lateinit var overlayDurationLabel: TextView
    private lateinit var overlayScaleLabel: TextView
    private lateinit var overlayPositionLabel: TextView
    private lateinit var overlayRotationLabel: TextView
    private lateinit var overlayOpacityLabel: TextView
    private lateinit var overlayKeyframeLabel: TextView
    private lateinit var overlayStartSeek: SeekBar
    private lateinit var overlayDurationSeek: SeekBar
    private lateinit var overlayScaleSeek: SeekBar
    private lateinit var overlayXSeek: SeekBar
    private lateinit var overlayYSeek: SeekBar
    private lateinit var overlayRotationSeek: SeekBar
    private lateinit var overlayOpacitySeek: SeekBar
    private var updatingOverlayControls = false
    private var clipCoverButton: Button? = null
    private var pendingCoverSaveBitmap: Bitmap? = null
    private lateinit var startLabel: TextView
    private lateinit var endLabel: TextView
    private lateinit var clipSpeedLabel: TextView
    private lateinit var clipSpeedSeek: SeekBar
    private var clipSpeedHistorySnapshot: ClipEditSnapshot? = null
    private var updatingClipSpeedControl = false
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
    private var exportCanvasPreset = VideoCanvasPreset.SOURCE
    private lateinit var exportCanvasGroup: android.widget.RadioGroup
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
        private const val REQUEST_OVERLAY_ASSET = 5108
        private const val REQUEST_SAVE_COVER_IMAGE = 5109
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        timelineLifecycleDiagnosticsEnabled =
            (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        timelineSyncTestEnabled =
            (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0 &&
                intent.getBooleanExtra("timeline_sync_test", false)
        timelineLifecycleState = "created"
        logTimelineLifecycle(
            "onCreate",
            "savedStatePresent=${savedInstanceState != null} incomingVideo=${!intent.getStringExtra("video_uri").isNullOrBlank()}"
        )
        if (timelineSyncTestEnabled) {
            android.util.Log.i("TimelineSyncTest", "TIMELINE_SYNC_TEST_ENABLED")
        }
        window.statusBarColor = Color.rgb(15, 31, 45)
        window.navigationBarColor = Color.rgb(15, 31, 45)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val requestedDraftId = intent.getStringExtra("draft_id")
            ?.takeIf { VideoProjectDraftStore.isValidId(it) }
        draftId = requestedDraftId ?: VideoProjectDraftStore.newId()
        buildUi()
        if ((applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            val testScaleDp = intent.getFloatExtra("timeline_test_px_per_sec_dp", 0f)
            if (testScaleDp.isFinite() && testScaleDp > 0f) {
                timelinePxPerSec = dp(testScaleDp.toInt().coerceIn(11, 176)).toFloat()
            }
        }
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
                .onSuccess { /* Opening a saved draft is silent; no persistent status message. */ }
                .onFailure {
                    status.text = "Taslak açılamadı: ${it.message ?: "proje verisi okunamadı"}"
                    if (!incoming.isNullOrBlank()) addClip(Uri.parse(incoming), false)
                }
        } else if (!incoming.isNullOrBlank()) {
            addClip(Uri.parse(incoming), false)
        } else if ((applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            android.util.Log.e("EditorSmokeTest", "EDITOR_SMOKE_MISSING_VIDEO_URI")
        }
        logTimelineLifecycle("onCreateComplete", "draftId=$draftId clips=${clips.size}")
    }

    private fun logTimelineLifecycle(event: String, details: String = "") {
        if (!timelineLifecycleDiagnosticsEnabled) return
        val waitingTargets = pendingTimelineThumbnails.values.fold(0) { total, refs -> total + refs.size }
        val detailsSuffix = if (details.isBlank()) "" else " $details"
        android.util.Log.i(
            "TimelineLifecycle",
            "TIMELINE_LIFECYCLE event=$event pid=${android.os.Process.myPid()} " +
                "instance=${System.identityHashCode(this).toString(16)} state=$timelineLifecycleState " +
                "finishing=$isFinishing changingConfig=$isChangingConfigurations destroyed=$isDestroyed " +
                "thumbCacheBytes=${timelineThumbnailCache.size()} thumbCacheMaxBytes=${timelineThumbnailCache.maxSize()} " +
                "thumbCacheHits=${timelineThumbnailCache.hitCount()} thumbCacheMisses=${timelineThumbnailCache.missCount()} " +
                "thumbCacheEvictions=${timelineThumbnailCache.evictionCount()} posterCacheBytes=${timelinePosterCache.size()} " +
                "pendingKeys=${pendingTimelineThumbnails.size} pendingTargets=$waitingTargets " +
                "queuedTasks=${timelineThumbnailExecutor.queue.size} activeWorkers=${timelineThumbnailExecutor.activeCount}" +
                detailsSuffix
        )
    }

    override fun onRestart() {
        super.onRestart()
        timelineLifecycleState = "restarted"
        logTimelineLifecycle("onRestart")
    }

    override fun onStart() {
        super.onStart()
        timelineLifecycleState = "started"
        logTimelineLifecycle("onStart")
    }

    override fun onResume() {
        super.onResume()
        timelineLifecycleState = "resumed"
        logTimelineLifecycle("onResume")
    }

    override fun onPause() {
        timelineLifecycleState = "paused"
        logTimelineLifecycle("onPause")
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        logTimelineLifecycle("onSaveInstanceState")
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        logTimelineLifecycle("onTrimMemory", "level=$level")
    }

    // CapCut-reference workspace: preview, fixed centered playback, live centered time readout, scrolling shared timeline, pinned dock.
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
        topBar.addView(miniButton("×") { finish() }.apply {
            textSize = 27f
            contentDescription = "Düzenleyiciyi kapat"
            background = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
        }, LinearLayout.LayoutParams(dp(42), dp(44)))
        topBar.addView(miniButton("⌕") { chooseVideo(true) }.apply {
            textSize = 25f
            contentDescription = "Galeriden video ara"
            background = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
        }, LinearLayout.LayoutParams(dp(42), dp(44)))
        topBar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        topBar.addView(miniButton("YZ UHD ▾") { showEditorPanel("export") }.apply {
            contentDescription = "Çıkış çözünürlüğünü seç"
        }, LinearLayout.LayoutParams(dp(96), dp(42)).apply { rightMargin = dp(7) })
        topBar.addView(miniButton("Dışa Aktar") { showEditorPanel("export") }.apply {
            setTextColor(Color.rgb(4, 27, 31))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.rgb(42, 211, 218))
                cornerRadius = dp(9).toFloat()
            }
            contentDescription = "Dışa aktarma ayarlarını aç"
        }, LinearLayout.LayoutParams(dp(108), dp(42)))
        root.addView(topBar, matchWrap())
        root.addView(gap(6))

        // CompositionPlayer requires Surface output; the XML selects SurfaceView.
        // The paused playhead-frame layer remains a sibling overlay above the player.
        playerView = layoutInflater.inflate(R.layout.editor_preview_player, null) as PlayerView
        previewFrame = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(5, 9, 15))
        }
        // The preview must use the full editor width; a fixed 180dp player
        // makes the central canvas look like a narrow phone-within-a-phone.
        previewFrame.addView(
            playerView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        previewFrameOverlay = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.BLACK)
            visibility = View.GONE
            contentDescription = "Oynatma noktasındaki tam video karesi"
            isClickable = false
            isFocusable = false
        }
        previewFrame.addView(
            previewFrameOverlay,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        // Transparent gesture surface sits above the video but below editable text overlays.
        clipTransformTouchOverlay = View(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            visibility = View.GONE
            isClickable = true
            contentDescription = "Seçili video klibini sürükle, iki parmakla büyüt ve döndür"
        }
        previewFrame.addView(
            clipTransformTouchOverlay,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        installClipTransformTouch()

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
        overlayTransformTouchOverlay = View(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            visibility = View.GONE
            isClickable = true
            contentDescription = "Bindirmeyi önizlemede tek parmakla taşı, iki parmakla büyüt ve döndür"
        }
        previewFrame.addView(
            overlayTransformTouchOverlay,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        installOverlayTransformTouch()
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
        // Keep the preview clean: only the expand/focus affordance remains over the video.
        previewFocusButton = miniButton("⛶") { togglePreviewFocusMode() }.apply {
            contentDescription = "Videoyu geniş ekranda aç"
            elevation = dp(12).toFloat()
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.argb(205, 8, 15, 22))
                cornerRadius = dp(9).toFloat()
            }
        }
        // The preview flexes into the space left between contextual settings,
        // the content-sized timeline and the fixed bottom tool dock.
        root.addView(previewFrame, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ).apply { bottomMargin = dp(8) })
        previewFrame.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            updatePreviewCanvasSize()
        }

        val timelineCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(3), 0, dp(3))
            setBackgroundColor(Color.rgb(8, 12, 18))
        }
        editorTimelineCard = timelineCard
        // One playback control, fixed to the phone's horizontal center axis.
        centeredPlaybackRow = FrameLayout(this)
        timelinePlayButton = miniButton("▶") {
            if (clips.isEmpty()) {
                chooseVideo(true)
            } else {
                if (timelineSyncTestEnabled) android.util.Log.i(
                    "TimelineSyncTest",
                    "TIMELINE_PLAY_TAP playheadMs=$timelinePlayheadMs loaded=$timelinePreviewLoaded hasPlayer=${timelineCompositionPlayer != null}"
                )
                ensureTimelinePreviewLoaded()
                val currentPlayer = timelineCompositionPlayer
                if (currentPlayer == null) {
                    timelinePlaybackState = TimelinePlaybackState.ERROR
                    timelinePreviewError = "Önizleme oynatıcısı hazırlanamadı."
                    status.text = timelinePreviewError
                } else if (currentPlayer.isPlaying || currentPlayer.playWhenReady) {
                    // Freeze the decoder first, then copy its actual clock into the
                    // timeline. The periodic UI sync is intentionally not the source
                    // of truth for a pause tap (it can be up to one tick behind).
                    currentPlayer.pause()
                    currentPlayer.playWhenReady = false
                    timelineHandler.removeCallbacks(timelineScrubPreviewSeekTask)
                    pendingTimelineScrubPreviewTargetMs = null
                    val pausedAtMs = currentPlayer.currentPosition
                        .coerceIn(0L, totalOutputDurationMs())
                    timelinePlayheadMs = pausedAtMs
                    timelinePlaybackState = TimelinePlaybackState.PAUSED
                    scrollTimelineToPlayhead()
                    updateTimelinePlayheadLabel()
                    requestPreviewFrameAt(pausedAtMs, keepPreviousFrame = true)
                    status.text = "Önizleme duraklatıldı."
                } else {
                    timelinePreviewError = null
                    timelineHandler.removeCallbacks(timelineScrubPreviewSeekTask)
                    pendingTimelineScrubPreviewTargetMs = null
                    timelinePlaybackState = TimelinePlaybackState.PLAYING
                    // Resume from the exact frozen playhead, not the last 50ms UI sample.
                    currentPlayer.seekTo(timelinePlayheadMs.coerceIn(0L, totalOutputDurationMs()))
                    hidePreviewFrameOverlay()
                    currentPlayer.playWhenReady = true
                    status.text = "Proje önizlemesi oynatılıyor."
                }
                if (timelineSyncTestEnabled) android.util.Log.i(
                    "TimelineSyncTest",
                    "TIMELINE_PLAY_STATE isPlaying=${currentPlayer?.isPlaying} playWhenReady=${currentPlayer?.playWhenReady} state=${currentPlayer?.playbackState} previewLoaded=$timelinePreviewLoaded"
                )
                updateTimelinePlayButtonState()
            }
        }.apply {
            minWidth = dp(46)
            minimumWidth = dp(46)
            textSize = 17f
            setPadding(0, 0, 0, 0)
            background = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
            elevation = 0f
            contentDescription = "Önizlemeyi oynat veya duraklat"
        }
        centeredPlaybackRow.addView(
            timelinePlayButton,
            FrameLayout.LayoutParams(dp(46), dp(38), Gravity.CENTER)
        )
        centeredPlaybackRow.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateTimelinePlayheadLine()
        }
        root.addView(centeredPlaybackRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(38)
        ))

        // Compact playback time and fullscreen control share the left side of
        // the playback row; the play action stays on the phone's center axis.
        timelinePlayheadLabel = label("00:00.0 / 00:00.0", 8.5f, Color.rgb(225, 233, 237), true).apply {
            maxLines = 1
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            setSingleLine(true)
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(3), 0, dp(2), 0)
            contentDescription = "Oynatma zamanı ve toplam video süresi"
        }
        playbackLeft = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        previewFocusButton.apply {
            textSize = 22f
            minWidth = dp(38)
            minimumWidth = dp(38)
            contentDescription = "Önizlemeyi geniş ekranda aç"
        }
        playbackLeft.addView(previewFocusButton, LinearLayout.LayoutParams(dp(38), dp(36)))
        centeredPlaybackRow.addView(
            playbackLeft,
            FrameLayout.LayoutParams(dp(132), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START or Gravity.CENTER_VERTICAL).apply {
                leftMargin = dp(3)
            }
        )

        val undoPlaybackButton = miniButton("↶") { undoClipEdit() }.apply {
            isEnabled = false
            textSize = 18f
            minWidth = dp(30)
            minimumWidth = dp(30)
            contentDescription = "Klip işlemini geri al"
            setPadding(0, 0, 0, 0)
        }
        val redoPlaybackButton = miniButton("↷") { redoClipEdit() }.apply {
            isEnabled = false
            textSize = 18f
            minWidth = dp(30)
            minimumWidth = dp(30)
            contentDescription = "Klip işlemini yinele"
            setPadding(0, 0, 0, 0)
        }
        undoClipButton = undoPlaybackButton
        redoClipButton = redoPlaybackButton
        val fitTimelineButton = miniButton("Sığdır") { fitTimelineToProject() }.apply {
            textSize = 9f
            minWidth = dp(48)
            minimumWidth = dp(48)
            contentDescription = "Zaman çizelgesini projeye sığdır"
            setPadding(dp(2), 0, dp(2), 0)
        }
        val playbackRight = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(undoPlaybackButton, LinearLayout.LayoutParams(dp(30), dp(36)))
            addView(redoPlaybackButton, LinearLayout.LayoutParams(dp(30), dp(36)).apply { leftMargin = dp(2) })
            addView(fitTimelineButton, LinearLayout.LayoutParams(dp(48), dp(36)).apply { leftMargin = dp(3) })
        }
        centeredPlaybackRow.addView(
            playbackRight,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END or Gravity.CENTER_VERTICAL).apply {
                rightMargin = dp(3)
            }
        )
        timelineTracksFrame = FrameLayout(this)
        timelineTracks = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        timelineTracksFrame.addView(
            timelineTracks,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        timelinePlayheadLine = FrameLayout(this).apply {
            // Visual-only fixed centre guide. Touches pass through to the scrolling
            // timeline strip beneath it, so one-finger swipes scrub the shared timeline.
            isClickable = false
            isFocusable = false
            elevation = dp(4).toFloat()
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            addView(
                View(this@VideoEditorActivity).apply {
                    setBackgroundColor(Color.WHITE)
                },
                FrameLayout.LayoutParams(dp(2), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
            )
        }
        timelineTracksFrame.addView(
            timelinePlayheadLine,
            FrameLayout.LayoutParams(dp(28), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.TOP or Gravity.START)
        )
        timelineTracksFrame.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateTimelinePlayheadLine()
        }
        val timelineTracksScroll = ScrollView(this).apply {
            isFillViewport = false
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
        }
        timelineTracksScroll.addView(
            timelineTracksFrame,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        // The card owns a bounded track viewport. Extra layers scroll vertically
        // instead of pushing the preview or bottom tool dock off-screen.
        timelineCard.addView(timelineTracksScroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        // Keep the shared timeline compact and content-focused instead of stretching
        // its empty background to consume every remaining pixel on short phones.
        // The track rows scroll horizontally; the editor shell keeps its own height.
        root.addView(timelineCard, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(220)
        ))
        root.addView(gap(8))

        val clipPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        // The first-level tools live in the bottom dock. This panel is only the
        // second-level content that grows upward after the user chooses Kırp.
        clipPanel.addView(miniButton("‹ Araçlara dön") { returnToClipToolStrip() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)))
        selectedClipLabel = label("Önce bir video seç.", 12f, Color.WHITE, true)
        clipPanel.addView(selectedClipLabel)
        val clipActions1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val coverButton = miniButton("Kapak") { showClipCoverPicker() }.apply {
            contentDescription = "Seçili klibin kapak karesini seç"
        }
        clipCoverButton = coverButton
        clipActions1.addView(coverButton, LinearLayout.LayoutParams(0, dp(46), 1f))
        clipActions1.addView(miniButton("Önizle") { previewSelectedClip() },
            LinearLayout.LayoutParams(0, dp(46), 1f).apply { leftMargin = dp(3) })
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
        startLabel = label("Başlangıç: 00:00", 12f, Color.rgb(225, 233, 237), true)
        clipPanel.addView(startLabel)
        startSeek = SeekBar(this)
        clipPanel.addView(startSeek, matchWrap())
        endLabel = label("Bitiş: 00:00", 12f, Color.rgb(225, 233, 237), true)
        clipPanel.addView(endLabel)
        endSeek = SeekBar(this)
        clipPanel.addView(endSeek, matchWrap())
        installTrimListeners()
        clipSpeedLabel = label("Hız: 1.00×", 12f, Color.WHITE, true)
        clipPanel.addView(clipSpeedLabel)
        val clipSpeedPresets = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(0.1f, 0.25f, 0.5f, 1f, 2f, 4f, 10f, 100f).forEach { preset ->
            clipSpeedPresets.addView(miniButton("${preset}×") { setSelectedClipSpeed(preset) },
                LinearLayout.LayoutParams(dp(58), dp(40)).apply {
                    if (preset != 0.1f) leftMargin = dp(4)
                })
        }
        val clipSpeedPresetScroll = android.widget.HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(clipSpeedPresets)
        }
        clipPanel.addView(clipSpeedPresetScroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(42)
        ))
        clipSpeedSeek = SeekBar(this).apply {
            max = 100
            progress = 33
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser || updatingClipSpeedControl) return
                    selectedClip()?.let { clip ->
                        clip.speed = Math.pow(10.0, progress / 100.0 * 3.0 - 1.0).toFloat()
                        clipSpeedLabel.text = "Hız: ${"%.2f".format(Locale.US, clip.speed)}×"
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    pauseActivePreview()
                    clipSpeedHistorySnapshot = captureClipEditSnapshot()
                }
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    finishClipSpeedEdit()
                }
            })
        }
        clipPanel.addView(clipSpeedSeek, matchWrap())

        val clipVolumePanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        clipVolumePanel.addView(miniButton("‹ Araçlara dön") { returnToClipToolStrip() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)))
        clipVolumeLabel = label("Orijinal video sesi: 100%", 13f, Color.WHITE, true)
        clipVolumePanel.addView(clipVolumeLabel)
        clipVolumeSeek = SeekBar(this).apply {
            max = 100
            progress = sourceAudioVolume
            setOnSeekBarChangeListener(volumeListener { value ->
                sourceAudioVolume = value
                clipVolumeLabel.text = "Orijinal video sesi: " + value + "%"
                if (::sourceVolumeLabel.isInitialized) {
                    sourceVolumeLabel.text = "Orijinal video sesi: " + value + "%"
                }
                if (::sourceVolumeSeek.isInitialized) sourceVolumeSeek.progress = value
                player?.volume = if (::keepSourceAudio.isInitialized && keepSourceAudio.isChecked) {
                    value / 100f
                } else 0f
                if (::keepSourceAudio.isInitialized) refreshTimelineSourceAudio()
            })
        }
        clipVolumePanel.addView(clipVolumeSeek, matchWrap())
        clipVolumePanel.addView(label(
            "Ses ayarı seçili video klibine uygulanır.",
            11f, Color.rgb(154, 171, 190), false
        ))

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
        textPanel.addView(label("Giriş / çıkış animasyonu", 11f, Color.rgb(154, 171, 190), false))
        textAnimationGroup = android.widget.RadioGroup(this).apply {
            orientation = android.widget.RadioGroup.HORIZONTAL
            TextAnimationPreset.values().forEachIndexed { index, preset ->
                addView(android.widget.RadioButton(this@VideoEditorActivity).apply {
                    id = 31 + index
                    text = preset.title
                    textSize = 10f
                    setTextColor(Color.WHITE)
                    buttonTintList = android.content.res.ColorStateList.valueOf(Color.rgb(57, 220, 190))
                    minHeight = dp(38)
                    setPadding(dp(2), 0, dp(4), 0)
                })
            }
            check(31)
            setOnCheckedChangeListener { _, checkedId ->
                if (loadingTextLayerControls) return@setOnCheckedChangeListener
                textAnimationPreset = TextAnimationPreset.values().getOrNull(checkedId - 31)
                    ?: TextAnimationPreset.NONE
                selectedTextLayer()?.animationPreset = textAnimationPreset
                updatePreviewTextOverlay()
                refreshTimelineTracks()
            }
        }
        val textAnimationScroll = android.widget.HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(textAnimationGroup)
        }
        textPanel.addView(textAnimationScroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(44)
        ))
        textAnimationDurationLabel = label("Animasyon süresi: 350 ms", 11f, Color.rgb(205, 218, 231), false)
        textPanel.addView(textAnimationDurationLabel)
        textAnimationDurationSeek = SeekBar(this).apply {
            max = 1_400
            progress = 250
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser || loadingTextLayerControls) return
                    textAnimationDurationMs = (progress + 100L).coerceIn(100L, 1_500L)
                    textAnimationDurationLabel.text = "Animasyon süresi: $textAnimationDurationMs ms"
                    selectedTextLayer()?.animationDurationMs = textAnimationDurationMs
                    updatePreviewTextOverlay()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    refreshTimelineTracks()
                }
            })
        }
        textPanel.addView(textAnimationDurationSeek, matchWrap())
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

        val stickersPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        stickersPanel.addView(label("ÇIKARTMALAR", 13f, Color.rgb(121, 202, 218), true))
        stickersPanel.addView(label("Seçtiğin çıkartma, zaman çizelgesinde bağımsız katman olur; videonun üzerinde taşıyıp boyutlandırabilirsin.", 11f, Color.rgb(154, 171, 190), false))
        listOf("❤️", "🔥", "😂", "⭐", "✨", "🎉", "✅", "👑", "⚡", "💯", "🎵", "🎯", "🌈", "☀️", "🌙", "🚀")
            .chunked(4).forEach { group ->
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                group.forEach { glyph ->
                    row.addView(miniButton(glyph) { addSticker(glyph) },
                        LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                            if (group.first() != glyph) leftMargin = dp(4)
                        })
                }
                stickersPanel.addView(row, matchWrap())
                stickersPanel.addView(gap(4))
            }

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
            musicSourceDurationMs = 0L
            musicSourceStartMs = 0L
            musicSourceEndMs = 0L
            musicStartMs = 0L
            musicDurationMs = 0L
            if (::musicSourceRangeLabel.isInitialized) musicSourceRangeLabel.text = "Müzik seçilmedi."
            if (::musicSourceStartSeek.isInitialized) musicSourceStartSeek.progress = 0
            if (::musicSourceEndSeek.isInitialized) musicSourceEndSeek.progress = 1000
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

        musicSourceRangeLabel = label("Müzik kaynak aralığı: tüm dosya", 12f, Color.WHITE, true)
        audioPanel.addView(musicSourceRangeLabel)
        musicSourceStartSeek = SeekBar(this).apply { max = 1000; progress = 0; contentDescription = "Müzik dosyası başlangıç noktası" }
        musicSourceEndSeek = SeekBar(this).apply { max = 1000; progress = 1000; contentDescription = "Müzik dosyası bitiş noktası" }
        val sourceRangeListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser || musicSourceDurationMs <= 0L) return
                val requested = progress.toLong() * musicSourceDurationMs / 1000L
                val current = MusicSourceRange(musicSourceStartMs, musicSourceEndMs)
                val updated = if (seekBar === musicSourceStartSeek)
                    MusicSourceRangeMath.start(current, requested, musicSourceDurationMs)
                else MusicSourceRangeMath.end(current, requested, musicSourceDurationMs)
                musicSourceStartMs = updated.startMs
                musicSourceEndMs = updated.endMs
                musicSourceRangeLabel.text = "Dosya içi: ${formatTimelineMs(updated.startMs)} – ${formatTimelineMs(updated.endMs)}"
                musicPreviewPlayer?.pause()
                refreshTimelineTracks()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) { player?.playWhenReady = false }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }
        musicSourceStartSeek.setOnSeekBarChangeListener(sourceRangeListener)
        musicSourceEndSeek.setOnSeekBarChangeListener(sourceRangeListener)
        audioPanel.addView(label("Dosya içi başlangıç", 11f, Color.LTGRAY, false))
        audioPanel.addView(musicSourceStartSeek, matchWrap())
        audioPanel.addView(label("Dosya içi bitiş", 11f, Color.LTGRAY, false))
        audioPanel.addView(musicSourceEndSeek, matchWrap())

        audioPanel.addView(gap(4))
        soundLabel = label("Ses efekti seçilmedi.", 12f, Color.WHITE, false)
        audioPanel.addView(soundLabel)
        val soundActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        soundActions.addView(miniButton("Efekt ekle") { chooseAudio(false) },
            LinearLayout.LayoutParams(0, dp(42), 1f))
        soundActions.addView(miniButton("Efekti kaldır") {
            soundUri = null
            soundTitle = null
            soundSourceDurationMs = 0L
            soundSourceStartMs = 0L
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
                val latestStart = (total - soundDurationMs).coerceAtLeast(0L)
                soundStartMs = TimelineTrimMath.timeForProgress(progress, latestStart)
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
                refreshTimelineSourceAudio()
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
            refreshTimelineSourceAudio()
        })
        audioPanel.addView(sourceVolumeSeek, matchWrap())

        val effectsPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        effectsPanel.addView(label("FİLTRELER VE RENK AYARI", 13f, Color.rgb(121, 202, 218), true))
        effectsPanel.addView(label("Seçtiğin görünüm önizlemeye ve MP4 çıktısına birlikte uygulanır.", 11f, Color.rgb(154, 171, 190), false))
        videoFilterSummary = label("Görünüm: Orijinal", 12f, Color.WHITE, true)
        effectsPanel.addView(videoFilterSummary)

        val filterPresets = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        VideoFilterPreset.values().toList().chunked(3).forEach { group ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            group.forEach { preset ->
                val button = miniButton(preset.title) { applyVideoFilterPreset(preset) }
                videoFilterButtons[preset] = button
                row.addView(button, LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                    if (group.first() != preset) leftMargin = dp(4)
                })
            }
            filterPresets.addView(row, matchWrap())
            filterPresets.addView(gap(4))
        }
        effectsPanel.addView(filterPresets)

        filterBrightnessLabel = label("Parlaklık: 0", 11f, Color.rgb(205, 218, 231), false)
        filterBrightnessSeek = SeekBar(this).apply { max = 200; progress = 100 }
        effectsPanel.addView(filterBrightnessLabel)
        effectsPanel.addView(filterBrightnessSeek, matchWrap())

        filterContrastLabel = label("Kontrast: 0", 11f, Color.rgb(205, 218, 231), false)
        filterContrastSeek = SeekBar(this).apply { max = 200; progress = 100 }
        effectsPanel.addView(filterContrastLabel)
        effectsPanel.addView(filterContrastSeek, matchWrap())

        filterSaturationLabel = label("Doygunluk: 0", 11f, Color.rgb(205, 218, 231), false)
        filterSaturationSeek = SeekBar(this).apply { max = 200; progress = 100 }
        effectsPanel.addView(filterSaturationLabel)
        effectsPanel.addView(filterSaturationSeek, matchWrap())

        filterHueLabel = label("Renk tonu: 0°", 11f, Color.rgb(205, 218, 231), false)
        filterHueSeek = SeekBar(this).apply { max = 360; progress = 180 }
        effectsPanel.addView(filterHueLabel)
        effectsPanel.addView(filterHueSeek, matchWrap())
        effectsPanel.addView(actionButton("Renk ayarlarını sıfırla") {
            applyVideoFilterPreset(VideoFilterPreset.ORIGINAL)
        })

        val overlayPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        overlayPanel.addView(label("GÖRSEL / VİDEO BİNDİRME", 13f, Color.rgb(121, 202, 218), true))
        overlayPanel.addView(label("Logo seçtiğinde tüm video boyunca sağ üstte başlar. Önizlemede sürükleyebilir, iki parmakla boyutunu değiştirebilirsin.", 11f, Color.rgb(154, 171, 190), false))
        val overlayActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        overlayActions.addView(miniButton("＋ Logo / Fotoğraf") { chooseOverlayAsset(imagesOnly = true) },
            LinearLayout.LayoutParams(0, dp(42), 1f))
        overlayActions.addView(miniButton("＋ Video") { chooseOverlayAsset(imagesOnly = false) },
            LinearLayout.LayoutParams(0, dp(42), 1f).apply { leftMargin = dp(4) })
        overlayActions.addView(miniButton("Kaldır") { removeSelectedOverlay() },
            LinearLayout.LayoutParams(0, dp(42), 1f).apply { leftMargin = dp(4) })
        overlayPanel.addView(overlayActions)
        overlayTitleLabel = label("Henüz bindirme eklenmedi.", 12f, Color.WHITE, true)
        overlayPanel.addView(overlayTitleLabel)
        overlayStartLabel = label("Başlangıç", 11f, Color.rgb(205, 218, 231), false)
        overlayPanel.addView(overlayStartLabel)
        overlayStartSeek = SeekBar(this)
        overlayPanel.addView(overlayStartSeek, matchWrap())
        overlayDurationLabel = label("Katman süresi", 11f, Color.rgb(205, 218, 231), false)
        overlayPanel.addView(overlayDurationLabel)
        overlayDurationSeek = SeekBar(this)
        overlayPanel.addView(overlayDurationSeek, matchWrap())
        overlayScaleLabel = label("Boyut", 11f, Color.rgb(205, 218, 231), false)
        overlayPanel.addView(overlayScaleLabel)
        overlayScaleSeek = SeekBar(this).apply { max = 1000 }
        overlayPanel.addView(overlayScaleSeek, matchWrap())
        overlayPositionLabel = label("Konum • yatay / dikey", 11f, Color.rgb(205, 218, 231), false)
        overlayPanel.addView(overlayPositionLabel)
        val overlayPositionRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        overlayXSeek = SeekBar(this).apply { max = 1000 }
        overlayYSeek = SeekBar(this).apply { max = 1000 }
        overlayPositionRow.addView(overlayXSeek, LinearLayout.LayoutParams(0, dp(38), 1f))
        overlayPositionRow.addView(overlayYSeek, LinearLayout.LayoutParams(0, dp(38), 1f).apply { leftMargin = dp(5) })
        overlayPanel.addView(overlayPositionRow)
        overlayRotationLabel = label("Döndürme", 11f, Color.rgb(205, 218, 231), false)
        overlayPanel.addView(overlayRotationLabel)
        overlayRotationSeek = SeekBar(this).apply { max = 3600; progress = 1800 }
        overlayPanel.addView(overlayRotationSeek, matchWrap())
        overlayOpacityLabel = label("Saydamlık", 11f, Color.rgb(205, 218, 231), false)
        overlayPanel.addView(overlayOpacityLabel)
        overlayOpacitySeek = SeekBar(this).apply { max = 1000; progress = 1000 }
        overlayPanel.addView(overlayOpacitySeek, matchWrap())
        overlayKeyframeLabel = label("Anahtar kare: 0", 11f, Color.rgb(205, 218, 231), false)
        overlayPanel.addView(overlayKeyframeLabel)
        val overlayKeyframeActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        overlayKeyframeActions.addView(miniButton("◆ Kare ekle") { addOverlayKeyframeAtPlayhead() },
            LinearLayout.LayoutParams(0, dp(42), 1f))
        overlayKeyframeActions.addView(miniButton("Kareyi sil") { removeOverlayKeyframeAtPlayhead() },
            LinearLayout.LayoutParams(0, dp(42), 1f).apply { leftMargin = dp(4) })
        overlayPanel.addView(overlayKeyframeActions)
        installOverlayControlListeners()
        installVideoFilterListeners()
        loadVideoFilterControls()

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
        exportPanel.addView(label("Video oranı / kadraj", 11f, Color.rgb(154, 171, 190), false))
        exportCanvasGroup = android.widget.RadioGroup(this).apply {
            orientation = android.widget.RadioGroup.HORIZONTAL
            VideoCanvasPreset.values().forEachIndexed { index, preset ->
                val optionId = 340 + index
                addView(android.widget.RadioButton(this@VideoEditorActivity).apply {
                    id = optionId
                    text = preset.title
                    textSize = 11f
                    setTextColor(Color.WHITE)
                    buttonTintList = android.content.res.ColorStateList.valueOf(Color.rgb(57, 220, 190))
                    minHeight = dp(40)
                    setPadding(dp(3), 0, dp(7), 0)
                }, android.widget.RadioGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ))
            }
            check(340)
            setOnCheckedChangeListener { _, checkedId ->
                exportCanvasPreset = VideoCanvasPreset.values().getOrNull(checkedId - 340)
                    ?: VideoCanvasPreset.SOURCE
                refreshColorGradePreview()
                status.text = when (exportCanvasPreset) {
                    VideoCanvasPreset.SOURCE -> "Kaynak video oranı korunacak."
                    else -> "Kadraj ${exportCanvasPreset.title} olarak ayarlandı. Önizleme ve MP4 çıktısı aynı kadrajı kullanacak."
                }
            }
        }
        val canvasScroll = android.widget.HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(exportCanvasGroup)
        }
        exportPanel.addView(canvasScroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(46)
        ))
        exportPanel.addView(label("Kadraj değiştirirken kenarlardan kırpılır; kaynak: orijinal oran.", 10f, Color.rgb(132, 153, 173), false))
        exportPanel.addView(actionButton("Projeyi farklı kaydet (.JSON)") { requestSaveProject() })
        exportPanel.addView(actionButton("Kayıtlı projeyi aç") { requestOpenProject() })
        exportButton = actionButton("MP4 DIŞA AKTAR") { exportEditedVideo() }
        exportPanel.addView(exportButton)

        editorPanels["clip"] = clipPanel
        editorPanels["clipVolume"] = clipVolumePanel
        editorPanels["overlay"] = overlayPanel
        editorPanels["effects"] = effectsPanel
        editorPanels["stickers"] = stickersPanel
        editorPanels["text"] = textPanel
        editorPanels["audio"] = audioPanel
        editorPanels["export"] = exportPanel

        editorPanelScroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            clipToOutline = true
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.argb(220, 12, 19, 28))
                cornerRadii = floatArrayOf(
                    dp(14).toFloat(), dp(14).toFloat(),
                    dp(14).toFloat(), dp(14).toFloat(),
                    0f, 0f, 0f, 0f
                )
                setStroke(dp(1), Color.rgb(37, 53, 69))
            }
        }
        val panelHolder = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(2), dp(4), dp(4))
        }
        editorPanels.values.forEachIndexed { index, panel ->
            panel.visibility = View.GONE
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
        editorPanelScroll.visibility = View.GONE

        status = TextView(this).apply {
            text = "Hazır. Zaman çizelgesine video ekleyebilirsin."
            setTextColor(Color.rgb(205, 218, 231))
            textSize = 11f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(8), dp(3), dp(8), dp(3))
            setBackgroundColor(Color.rgb(23, 32, 45))
        }
        // Keep status as an internal diagnostic/message sink only. The old
        // persistent strip above the dock is intentionally not part of the editor UI.

        val dockScroll = android.widget.HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
        }
        val dock = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(3), dp(4), dp(3), dp(2))
            setBackgroundColor(Color.rgb(8, 12, 18))
            elevation = dp(8).toFloat()
        }
        editorDock = dock
        editorDockScroll = dockScroll
        listOf(
            Triple("clip", "Düzenle", "✂"),
            Triple("audio", "Ses", "♫"),
            Triple("text", "Metin", "T"),
            Triple("effects", "Efektler", "✦"),
            Triple("stickers", "Çıkartma", "☺"),
            Triple("overlay", "Bindirme", "▧"),
            Triple("captions", "Altyazı", "Aa"),
            Triple("export", "Proje", "⋯")
        ).forEach { (key, title, symbol) ->
            val tab = miniButton("$symbol\n$title") {
                if (key == "clip") {
                    if (clipToolsMode) exitClipTools() else enterClipTools()
                } else if (key == "captions") {
                    if (clipToolsMode) exitClipTools()
                    toggleEditorPanel("text")
                } else if (key in editorPanels) {
                    if (clipToolsMode) exitClipTools()
                    toggleEditorPanel(key)
                } else {
                    android.widget.Toast.makeText(this, "Bu araç bu sürümde etkin değil.", android.widget.Toast.LENGTH_SHORT).show()
                }
            }.apply {
                textSize = 10f
                gravity = Gravity.CENTER
                minWidth = dp(52)
                minimumWidth = dp(52)
                minHeight = dp(58)
                setPadding(dp(1), dp(2), dp(1), dp(2))
                maxLines = 2
                background = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
                setTextColor(Color.rgb(205, 216, 228))
            }
            editorTabButtons[key] = tab
            dock.addView(tab, LinearLayout.LayoutParams(dp(52), dp(58)).apply {
                if (key != "clip") leftMargin = dp(2)
            })
        }
        dockScroll.addView(dock, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
        // The dock and contextual tools share one bottom-anchored overlay. The
        // base editor reserves only the dock height; opening the detail panel
        // therefore overlays the lower timeline instead of shrinking the preview.
        val bottomSheet = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            elevation = dp(14).toFloat()
        }
        editorBottomSheet = bottomSheet
        bottomSheet.addView(editorPanelScroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(196)
        ))
        bottomSheet.addView(dockScroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(66)
        ))
        root.addView(View(this), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(66)
        ))

        val editorWorkspace = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
            setBackgroundColor(Color.rgb(12, 18, 28))
            addView(root, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
            addView(bottomSheet, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            ))
        }

        setContentView(editorWorkspace)
        refreshTextLayerList()
        refreshTimelineTracks()
        installResponsiveEditorLayout(
            root = root,
            previewFrame = previewFrame,
            timelineCard = timelineCard,
            panelScroll = editorPanelScroll,
            statusView = status,
            bottomDock = dockScroll,
            bottomSheet = bottomSheet,
            topBar = topBar,
            rulerAwareTimelineRefresh = { refreshTimelineTracks() }
        )
        previewFrame.post { logEditorPanelGeometry("closed-initial") }
    }

    private fun logEditorPanelGeometry(state: String) {
        if (!timelineSyncTestEnabled ||
            !::previewFrame.isInitialized ||
            !::editorTimelineCard.isInitialized
        ) return
        val previewLocation = IntArray(2)
        val timelineLocation = IntArray(2)
        previewFrame.getLocationOnScreen(previewLocation)
        editorTimelineCard.getLocationOnScreen(timelineLocation)
        android.util.Log.i(
            "EditorPanelLayoutTest",
            "EDITOR_PANEL_GEOMETRY state=" + state +
                " preview=" + previewFrame.width + "x" + previewFrame.height + "@" +
                previewLocation[0] + "," + previewLocation[1] +
                " timeline=" + editorTimelineCard.width + "x" + editorTimelineCard.height + "@" +
                timelineLocation[0] + "," + timelineLocation[1]
        )
    }

    private fun installResponsiveEditorLayout(
        root: LinearLayout,
        previewFrame: FrameLayout,
        timelineCard: LinearLayout,
        panelScroll: ScrollView,
        statusView: TextView,
        bottomDock: View,
        bottomSheet: View,
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
            // The overlay is outside the padded root, so mirror the safe bottom
            // inset explicitly. When adjustResize already moved the viewport
            // above the IME, no second keyboard offset is applied.
            val sheetParams = bottomSheet.layoutParams as? FrameLayout.LayoutParams
            if (sheetParams != null) {
                val safeBottom = when {
                    imeVisible && !resizedForIme -> dp(2) + maxOf(insetBottom, imeInsetBottom)
                    imeVisible -> dp(2) + insetBottom
                    else -> dp(4) + insetBottom
                }
                if (sheetParams.bottomMargin != safeBottom) {
                    sheetParams.bottomMargin = safeBottom
                    bottomSheet.layoutParams = sheetParams
                }
            }
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
                // Track rows determine the timeline card height. Keep the preview
                // flexible so the tool dock remains visible when settings or the IME open.
                val previewParams = previewFrame.layoutParams as LinearLayout.LayoutParams
                previewParams.height = 0
                previewParams.weight = 1f
                previewFrame.layoutParams = previewParams

                if (::editorPanelScroll.isInitialized &&
                    editorPanelScroll.visibility == View.VISIBLE &&
                    activeEditorPanel != null
                ) {
                    val panelParams = editorPanelScroll.layoutParams as? LinearLayout.LayoutParams
                    if (panelParams != null) {
                        panelParams.height = dp(if (imeVisible) 124 else 196)
                        editorPanelScroll.layoutParams = panelParams
                    }
                }

                // Hide decorative strips and fixed docks so the active tool gets
                // the visible space left by the keyboard.
                bottomDock.visibility = if (imeVisible) View.GONE else View.VISIBLE
                statusView.visibility = if (imeVisible) View.GONE else View.VISIBLE
                if (topBar.childCount > 4) {
                    val topAction = topBar.getChildAt(4) as? Button
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
                } else {
                    timelineCard.setPadding(dp(8), dp(5), dp(8), dp(5))
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
                // The preview receives whatever height remains after the real
                // timeline rows, status strip and pinned bottom dock are measured.
                previewDefaultStageHeightDp = (viewportDp * 0.42f).toInt().coerceIn(190, 390)
                previewStageHeightDp = previewDefaultStageHeightDp
                val previewParams = previewFrame.layoutParams as? LinearLayout.LayoutParams
                if (previewParams != null) {
                    previewParams.height = 0
                    previewParams.weight = 1f
                    previewFrame.layoutParams = previewParams
                }
                previewHeightAutoSized = true

                // Reserve room for real tools and the multi-track timeline on shorter phones.
                if (compactEditorLayout) {
                    view.setPadding(dp(4), dp(2), dp(4), dp(2))
                }
                timelineCard.setPadding(
                    dp(if (compactEditorLayout) 5 else 8),
                    dp(if (compactEditorLayout) 2 else 5),
                    dp(if (compactEditorLayout) 5 else 8),
                    dp(if (compactEditorLayout) 2 else 5)
                )
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
                val params = previewFrame.layoutParams as? LinearLayout.LayoutParams
                if (params != null && (params.height != 0 || params.weight != 1f)) {
                    applyingResponsiveLayout = true
                    params.height = 0
                    params.weight = 1f
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
        if (stageWidth <= 0 || previewFrame.height <= 0) return

        // Keep the PlayerView itself full-width. Media3's FIT mode preserves the
        // source aspect ratio inside that canvas; shrinking the view to the
        // calculated portrait-video width made the whole preview look undersized.
        val params = playerView.layoutParams as? FrameLayout.LayoutParams ?: return
        if (params.width != ViewGroup.LayoutParams.MATCH_PARENT ||
            params.height != ViewGroup.LayoutParams.MATCH_PARENT ||
            params.gravity != Gravity.CENTER
        ) {
            params.width = ViewGroup.LayoutParams.MATCH_PARENT
            params.height = ViewGroup.LayoutParams.MATCH_PARENT
            params.gravity = Gravity.CENTER
            playerView.layoutParams = params
        }
        if (::previewTextOverlay.isInitialized) {
            previewTextOverlays.forEach { overlay ->
                overlay.maxWidth = (stageWidth - dp(16)).coerceAtLeast(dp(100))
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
            // Keep the fullscreen exit affordance near the top safe area and move
            // the same play/time controls onto the preview, as in CapCut.
            (previewFocusButton.parent as? ViewGroup)?.removeView(previewFocusButton)
            (timelinePlayheadLabel.parent as? ViewGroup)?.removeView(timelinePlayheadLabel)
            (timelinePlayButton.parent as? ViewGroup)?.removeView(timelinePlayButton)
            fullscreenPlaybackOverlay = FrameLayout(this).apply {
                setBackgroundColor(Color.argb(150, 0, 0, 0))
                addView(
                    timelinePlayheadLabel,
                    FrameLayout.LayoutParams(dp(142), dp(40), Gravity.START or Gravity.CENTER_VERTICAL).apply {
                        leftMargin = dp(12)
                    }
                )
                addView(
                    timelinePlayButton,
                    FrameLayout.LayoutParams(dp(48), dp(44), Gravity.CENTER)
                )
            }
            previewFrame.addView(
                fullscreenPlaybackOverlay,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56), Gravity.BOTTOM)
            )
            previewFrame.addView(
                previewFocusButton,
                FrameLayout.LayoutParams(dp(44), dp(44), Gravity.TOP or Gravity.END).apply {
                    topMargin = dp(12)
                    rightMargin = dp(12)
                }
            )
            for (index in 0 until root.childCount) {
                val child = root.getChildAt(index)
                if (child !== previewFrame) {
                    previewFocusOriginalVisibility[child] = child.visibility
                    child.visibility = View.GONE
                }
            }
            // The dock/sheet is now a sibling overlay of the base root, so
            // explicitly include it in the same fullscreen hide/restore cycle.
            if (::editorBottomSheet.isInitialized) {
                previewFocusOriginalVisibility[editorBottomSheet] = editorBottomSheet.visibility
                editorBottomSheet.visibility = View.GONE
            }
            root.setPadding(dp(4), dp(4), dp(4), dp(4))
            val params = previewFrame.layoutParams as LinearLayout.LayoutParams
            params.height = 0
            params.weight = 1f
            previewFrame.layoutParams = params
            previewFocusButton.text = "↙"
            previewFocusButton.contentDescription = "Düzenleme ekranına dön"
        } else {
            // Return the same controls and click handlers to their normal rows.
            (previewFocusButton.parent as? ViewGroup)?.removeView(previewFocusButton)
            (fullscreenPlaybackOverlay.parent as? ViewGroup)?.removeView(fullscreenPlaybackOverlay)
            playbackLeft.addView(
                previewFocusButton,
                0,
                LinearLayout.LayoutParams(dp(38), dp(36))
            )
            if (::timelineRulerHeader.isInitialized) {
                (timelinePlayheadLabel.parent as? ViewGroup)?.removeView(timelinePlayheadLabel)
                timelineRulerHeader.addView(
                    timelinePlayheadLabel,
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                )
            }
            centeredPlaybackRow.addView(
                timelinePlayButton,
                FrameLayout.LayoutParams(dp(46), dp(38), Gravity.CENTER)
            )
            previewFocusOriginalVisibility.forEach { (view, visibility) -> view.visibility = visibility }
            previewFocusOriginalVisibility.clear()
            root.setPadding(dp(8), dp(4), dp(8), dp(4))
            val params = previewFrame.layoutParams as LinearLayout.LayoutParams
            params.height = 0
            params.weight = 1f
            previewFrame.layoutParams = params
            previewFocusButton.text = "⛶"
            previewFocusButton.contentDescription = "Videoyu geniş ekranda aç"
        }
        previewFrame.requestLayout()
        updatePreviewCanvasSize()
    }

    private fun makeClipDockTool(
        symbol: String,
        title: String,
        description: String = title,
        onClick: () -> Unit
    ): Button = Button(this).apply {
        text = "$symbol\n$title"
        contentDescription = description
        isAllCaps = false
        textSize = 10f
        gravity = Gravity.CENTER
        setTextColor(Color.rgb(205, 216, 228))
        minWidth = dp(58)
        minimumWidth = dp(58)
        minHeight = dp(58)
        setPadding(dp(2), dp(2), dp(2), dp(2))
        maxLines = 2
        background = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
        isHapticFeedbackEnabled = true
        setOnClickListener {
            performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            onClick()
        }
    }

    /** Stage one: replace the global dock with one compact horizontal clip-tool strip. */
    private fun enterClipTools() {
        if (!::editorDock.isInitialized || !::editorPanelScroll.isInitialized) return
        logEditorPanelGeometry("before-open:clip-tools")
        clipToolsMode = true
        activeClipTool = null
        activeEditorPanel = null
        updateClipTransformTouchAvailability()
        updateOverlayTransformTouchAvailability()
        textOverlayEditingEnabled = false
        updateTextOverlayEditToggle()
        editorPanels.values.forEach { it.visibility = View.GONE }
        editorPanelScroll.animate().cancel()
        editorPanelScroll.visibility = View.GONE
        editorPanelScroll.translationY = 0f
        editorPanelScroll.alpha = 1f

        editorDock.removeAllViews()
        val tools = listOf(
            Triple("‹", "Geri", "Ana araçlara dön"),
            Triple("⌁", "Kırp", "Kırpma ayarlarını aç"),
            Triple("♫", "Ses düzeyi", "Orijinal video sesini ayarla"),
            Triple("✂", "Böl", "Seçili klibi oynatma çizgisinde böl"),
            Triple("▧", "Kapak", "Seçili klibin kapak karesini seç"),
            Triple("▤", "Sil", "Seçili klibi sil")
        )
        tools.forEach { (symbol, title, description) ->
            val action: () -> Unit = when (title) {
                "Geri" -> ({ exitClipTools() })
                "Kırp" -> ({
                    activeClipTool = "trim"
                    selectedClipLabel.text = if (selectedClipIndex in clips.indices) {
                        clips[selectedClipIndex].title
                    } else "Önce zaman çizelgesinden bir video seç."
                    showEditorPanel("clip")
                })
                "Ses düzeyi" -> ({
                    activeClipTool = "volume"
                    clipVolumeSeek.progress = sourceAudioVolume
                    clipVolumeLabel.text = "Orijinal video sesi: $sourceAudioVolume%"
                    showEditorPanel("clipVolume")
                })
                "Böl" -> ({ splitSelectedClipAtPlayhead() })
                "Kapak" -> ({ showClipCoverPicker() })
                "Sil" -> ({ removeSelectedClip() })
                else -> ({})
            }
            editorDock.addView(
                makeClipDockTool(symbol, title, description, action),
                LinearLayout.LayoutParams(dp(60), dp(58))
            )
        }
        editorDockScroll.scrollTo(0, 0)
        editorDockScroll.translationY = dp(24).toFloat()
        editorDockScroll.animate()
            .translationY(0f)
            .setDuration(180L)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()
        logEditorPanelGeometry("clip-tools")
    }

    /** Stage two: return from the expanded tool content to the single tool strip. */
    private fun returnToClipToolStrip() {
        if (!clipToolsMode) return
        activeEditorPanel = null
        activeClipTool = null
        updateClipTransformTouchAvailability()
        updateOverlayTransformTouchAvailability()
        textOverlayEditingEnabled = false
        editorPanels.values.forEach { it.visibility = View.GONE }
        if (editorPanelScroll.visibility == View.VISIBLE) {
            editorPanelScroll.animate().cancel()
            val distance = editorPanelScroll.height.takeIf { it > 0 } ?: dp(160)
            editorPanelScroll.animate()
                .translationY(distance.toFloat())
                .alpha(0f)
                .setDuration(160L)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .withEndAction {
                    if (clipToolsMode && activeEditorPanel == null) {
                        editorPanelScroll.visibility = View.GONE
                        editorPanelScroll.translationY = 0f
                        editorPanelScroll.alpha = 1f
                        logEditorPanelGeometry("clip-tool-strip")
                    }
                }
                .start()
        } else {
            editorPanelScroll.visibility = View.GONE
            logEditorPanelGeometry("clip-tool-strip")
        }
        val focused = currentFocus
        val keyboard = getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
            as? android.view.inputmethod.InputMethodManager
        focused?.windowToken?.let { keyboard?.hideSoftInputFromWindow(it, 0) }
        focused?.clearFocus()
        editorPanelScroll.scrollTo(0, 0)
    }

    /** Leave the contextual clip tools and restore the global editor dock. */
    private fun exitClipTools() {
        if (!clipToolsMode) return
        clipToolsMode = false
        activeClipTool = null
        activeEditorPanel = null
        updateClipTransformTouchAvailability()
        updateOverlayTransformTouchAvailability()
        textOverlayEditingEnabled = false
        updateTextOverlayEditToggle()
        editorPanels.values.forEach { it.visibility = View.GONE }
        editorPanelScroll.animate().cancel()
        editorPanelScroll.visibility = View.GONE
        editorPanelScroll.translationY = 0f
        editorPanelScroll.alpha = 1f
        editorDock.removeAllViews()
        editorTabButtons.forEach { (key, button) ->
            editorDock.addView(button, LinearLayout.LayoutParams(dp(52), dp(58)).apply {
                if (key != "clip") leftMargin = dp(2)
            })
            button.background = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
            button.setTextColor(Color.rgb(205, 216, 228))
        }
        editorDockScroll.scrollTo(0, 0)
        logEditorPanelGeometry("closed")
    }

    private fun showEditorPanel(name: String) {
        // Keep the selected track's contextual dock alive while its detail sheet
        // is open. Closing the sheet returns to the same track tools; it must not
        // silently replace them with the global editor tabs.
        activeEditorPanel = name
        if (name == "overlay") loadOverlayControls()
        updateClipTransformTouchAvailability()
        updateOverlayTransformTouchAvailability()
        if (::editorPanelScroll.isInitialized) {
            val wasVisible = editorPanelScroll.visibility == View.VISIBLE
            if (!wasVisible) logEditorPanelGeometry("before-open:" + name)
            val params = editorPanelScroll.layoutParams as? LinearLayout.LayoutParams
            if (params != null) {
                val panelHeight = when (name) {
                    "clip" -> if (keyboardLayoutActive) 124 else 196
                    "clipVolume" -> if (keyboardLayoutActive) 112 else 150
                    else -> if (keyboardLayoutActive) 124 else 196
                }
                params.height = dp(panelHeight)
                editorPanelScroll.layoutParams = params
            }
            editorPanelScroll.visibility = View.VISIBLE
            editorPanelScroll.animate().cancel()
            if (wasVisible) {
                // Switching tools reuses the same sheet without replaying its entrance.
                editorPanelScroll.translationY = 0f
                editorPanelScroll.alpha = 1f
                editorPanelScroll.post { logEditorPanelGeometry("switch:" + name) }
            } else {
                // The details panel rises from the bottom dock like a native
                // editing drawer. It is an overlay, so the preview/timeline are
                // not remeasured when it appears.
                editorPanelScroll.alpha = 0f
                editorPanelScroll.translationY = editorPanelScroll.layoutParams.height.toFloat()
                editorPanelScroll.post {
                    if (activeEditorPanel != null &&
                        editorPanelScroll.visibility == View.VISIBLE
                    ) {
                        editorPanelScroll.animate()
                            .translationY(0f)
                            .alpha(1f)
                            .setDuration(220L)
                            .setInterpolator(android.view.animation.DecelerateInterpolator())
                            .withEndAction { logEditorPanelGeometry("open:" + name) }
                            .start()
                    }
                }
            }
        }
        textOverlayEditingEnabled = name == "text"
        updateTextOverlayEditToggle()
        if (::editorPanelScroll.isInitialized) editorPanelScroll.scrollTo(0, 0)
        editorPanels.forEach { (key, panel) ->
            panel.visibility = if (key == name) View.VISIBLE else View.GONE
        }
        editorTabButtons.forEach { (key, button) ->
            val selected = key == name
            button.background = android.graphics.drawable.GradientDrawable().apply {
                setColor(if (selected) Color.rgb(34, 48, 61) else Color.TRANSPARENT)
                cornerRadius = dp(8).toFloat()
                setStroke(dp(if (selected) 1 else 0),
                    if (selected) Color.rgb(57, 220, 190) else Color.TRANSPARENT)
            }
            button.setTextColor(if (selected) Color.rgb(86, 231, 200) else Color.rgb(205, 216, 228))
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
        updateClipTransformTouchAvailability()
        updateOverlayTransformTouchAvailability()
        textOverlayEditingEnabled = false
        if (::editorPanelScroll.isInitialized && editorPanelScroll.visibility == View.VISIBLE) {
            editorPanelScroll.animate().cancel()
            val exitDistance = editorPanelScroll.height
                .takeIf { it > 0 } ?: dp(if (keyboardLayoutActive) 124 else 196)
            editorPanelScroll.animate()
                .translationY(exitDistance.toFloat())
                .alpha(0f)
                .setDuration(180L)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .withEndAction {
                    if (activeEditorPanel == null) {
                        editorPanels.values.forEach { it.visibility = View.GONE }
                        editorPanelScroll.visibility = View.GONE
                        editorPanelScroll.translationY = 0f
                        editorPanelScroll.alpha = 1f
                        editorPanelScroll.post { logEditorPanelGeometry("closed") }
                    }
                }
                .start()
        } else {
            editorPanels.values.forEach { it.visibility = View.GONE }
        }
        updateTextOverlayEditToggle()
        editorTabButtons.values.forEach { button ->
            button.background = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
            button.setTextColor(Color.rgb(205, 216, 228))
        }
        val focused = currentFocus
        val keyboard = getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
            as? android.view.inputmethod.InputMethodManager
        focused?.windowToken?.let { keyboard?.hideSoftInputFromWindow(it, 0) }
        focused?.clearFocus()
        if (::editorPanelScroll.isInitialized) editorPanelScroll.scrollTo(0, 0)
    }

    override fun onBackPressed() {
        val panel = activeEditorPanel
        if (panel != null) {
            toggleEditorPanel(panel)
            return
        }
        if (clipToolsMode) {
            exitClipTools()
            return
        }
        super.onBackPressed()
    }

    private fun refreshTimelineTracks() {
        if (!::timelineTracks.isInitialized) return
        if (timelineLifecycleDiagnosticsEnabled) {
            val caller = Throwable().stackTrace.getOrNull(2)?.let {
                "${it.className.substringAfterLast('.')}.${it.methodName}"
            } ?: "unknown"
            logTimelineLifecycle(
                "timelineRefresh",
                "caller=$caller rowsBefore=${timelineTracks.childCount}"
            )
        }
        // The scroll listener keeps this value current. Reading a freshly rebuilt
        // child view here can return zero before its posted scroll restoration runs.
        val preservedScrollX = pendingTimelineScrollX ?: timelineHorizontalScrollX
        pendingTimelineScrollX = null
        timelineHorizontalScrollX = preservedScrollX
        if (::timelinePlayheadLabel.isInitialized && !previewFocusMode) {
            (timelinePlayheadLabel.parent as? ViewGroup)?.removeView(timelinePlayheadLabel)
        }
        timelineTracks.removeAllViews()
        timelineScrollViews.clear()
        if (::timelinePlayheadLabel.isInitialized) updateTimelinePlayheadDisplay()

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

        fun makeLane(
            tag: String,
            heightDp: Int = 30,
            laneKey: String = tag,
            reorderable: Boolean = true
        ): LinearLayout {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(3), dp(1), dp(3), dp(1))
                this.tag = if (reorderable) laneKey else "__ruler__"
                isClickable = true
                setOnClickListener { clearTimelineClipSelection() }
            }
            val grip = TextView(this).apply {
                // Keep the existing drag handle while giving the video/audio/text
                // lanes their compact CapCut-style left-side affordance.
                text = when {
                    tag == "Video" -> if (keepSourceAudio.isChecked) "🔊\nKlip\nsesini" else "🔇\nKlip\nsesini"
                    tag == "Müzik" || tag == "Ses" -> "♫"
                    tag.startsWith("Metin") -> "T"
                    reorderable -> "⠿"
                    else -> tag
                }
                textSize = when {
                    tag == "Video" -> 9f
                    tag == "Müzik" || tag == "Ses" || tag.startsWith("Metin") -> 17f
                    else -> if (reorderable) 18f else 9f
                }
                setTextColor(Color.rgb(166, 184, 201))
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                contentDescription = if (reorderable) {
                    "$tag şeridi. Tutup yukarı veya aşağı sürükleyerek yerini değiştir."
                } else tag
            }
            if (reorderable) {
                row.addView(grip, LinearLayout.LayoutParams(dp(44), ViewGroup.LayoutParams.MATCH_PARENT))
                val sideAction = TextView(this).apply {
                    text = when {
                        tag == "Video" -> "Kapak"
                        tag == "Müzik" || tag == "Ses" -> "Ses\nekle"
                        tag == "Metin" || tag == "Metin 1" -> "Metin\nekle"
                        else -> ""
                    }
                    textSize = 9f
                    gravity = Gravity.CENTER
                    setTextColor(Color.rgb(166, 184, 201))
                    isClickable = text.isNotBlank()
                    isFocusable = text.isNotBlank()
                    contentDescription = text.toString().replace("\n", " ")
                    setOnClickListener {
                        when (tag) {
                            "Video" -> if (selectedClip() != null) showClipCoverPicker() else chooseVideo(true)
                            "Müzik", "Ses" -> showEditorPanel("audio")
                            "Metin", "Metin 1" -> showEditorPanel("text")
                        }
                    }
                }
                row.addView(sideAction, LinearLayout.LayoutParams(dp(40), ViewGroup.LayoutParams.MATCH_PARENT))
            if (reorderable && tag == "Video") {
                grip.contentDescription = if (keepSourceAudio.isChecked) {
                    "Klip sesini kapat"
                } else "Klip sesini aç"
                grip.setOnClickListener {
                    keepSourceAudio.isChecked = !keepSourceAudio.isChecked
                    player?.volume = if (keepSourceAudio.isChecked) sourceAudioVolume / 100f else 0f
                    grip.text = if (keepSourceAudio.isChecked) "🔊\nKlip\nsesini" else "🔇\nKlip\nsesini"
                    grip.contentDescription = if (keepSourceAudio.isChecked) {
                        "Klip sesini kapat"
                    } else "Klip sesini aç"
                }
            }
            } else {
                // The ruler's left header is the CapCut-style home for the
                // current/total playback time. Keep its width identical to the
                // media-lane control gutter so every timeline zero edge stays aligned.
                timelineRulerHeader = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(2), 0, dp(2), 0)
                }
                if (::timelinePlayheadLabel.isInitialized && !previewFocusMode) {
                    timelineRulerHeader.addView(
                        timelinePlayheadLabel,
                        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    )
                }
                row.addView(timelineRulerHeader, LinearLayout.LayoutParams(dp(84), ViewGroup.LayoutParams.MATCH_PARENT))
            }

            if (reorderable) {
                var downRawY = 0f
                var moved = false
                grip.setOnTouchListener { view, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            downRawY = event.rawY
                            moved = false
                            view.parent?.requestDisallowInterceptTouchEvent(true)
                            true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val deltaY = event.rawY - downRawY
                            if (kotlin.math.abs(deltaY) > android.view.ViewConfiguration.get(this).scaledTouchSlop) {
                                moved = true
                                val location = IntArray(2)
                                timelineTracks.getLocationOnScreen(location)
                                val localY = (event.rawY - location[1]).toInt()
                                val currentIndex = timelineTracks.indexOfChild(row)
                                var targetIndex = timelineTracks.childCount
                                for (index in 0 until timelineTracks.childCount) {
                                    val candidate = timelineTracks.getChildAt(index)
                                    if (candidate === row || candidate.tag == "__ruler__") continue
                                    if (localY < candidate.top + candidate.height / 2) {
                                        targetIndex = index
                                        break
                                    }
                                }
                                if (currentIndex >= 0 && targetIndex != currentIndex) {
                                    timelineTracks.removeView(row)
                                    if (targetIndex > currentIndex) targetIndex--
                                    timelineTracks.addView(row, targetIndex.coerceIn(0, timelineTracks.childCount))
                                    timelineTracks.requestLayout()
                                }
                            }
                            true
                        }
                        MotionEvent.ACTION_UP -> {
                            view.parent?.requestDisallowInterceptTouchEvent(false)
                            if (moved) {
                                timelineLaneOrder.clear()
                                for (index in 0 until timelineTracks.childCount) {
                                    val key = timelineTracks.getChildAt(index).tag as? String
                                    if (!key.isNullOrBlank() && key != "__ruler__") timelineLaneOrder.add(key)
                                }
                                status.text = "Şeridin sırası değiştirildi."
                            } else {
                                view.performClick()
                            }
                            true
                        }
                        MotionEvent.ACTION_CANCEL -> {
                            view.parent?.requestDisallowInterceptTouchEvent(false)
                            true
                        }
                        else -> false
                    }
                }
            }

            val scroll = android.widget.HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                isClickable = true
                setOnClickListener { clearTimelineClipSelection() }
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
                    if (!autoScrollingTimeline && view.width > 0) {
                        // The fixed center playhead stays still while the filmstrip moves
                        // underneath it. Update the visible time immediately on every drag.
                        val originPx = timelineOriginSpacerWidth(view.width).toFloat()
                        val rawPlayheadMs = TimelineScaleMath.timeAtViewportX(
                            scrollX, originPx, originPx, timelinePixelsPerSecond()
                        ).coerceIn(0L, totalOutputDurationMs())
                        val underPlayheadMs = snapTimelineTimeToSourceFrame(rawPlayheadMs)
                        if (underPlayheadMs != timelinePlayheadMs) {
                            // A manual scrub takes control from playback so the periodic
                            // player sync cannot pull the ruler back to an older frame.
                            timelineCompositionPlayer?.let { composition ->
                                if (composition.isPlaying || composition.playWhenReady) {
                                    composition.pause()
                                    composition.playWhenReady = false
                                }
                            }
                            timelinePlayheadMs = underPlayheadMs
                            // Invalidate an older pending decode without flashing black;
                            // keep the last valid frame until this scrub position is ready.
                            showPreviewFramePending(keepPreviousFrame = true)
                            updateTimelinePlayheadLabel()
                            scheduleTimelineScrubPreviewSeek(underPlayheadMs)
                            // Emit a direct gesture-time sample even before Media3's
                            // composition player has finished preparing. The smoke test
                            // can then validate the shared ruler/playhead scroll mapping.
                            if (timelineSyncTestEnabled) logTimelineSyncTestSample()
                        }
                    }
                    updateTimelinePlayheadLine()
                }
            }
            val track = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(1), 0, dp(1))
                // Empty portions of a lane are valid tap targets for clearing
                // clip selection; non-clickable spacers naturally bubble here.
                isClickable = true
                setOnClickListener { clearTimelineClipSelection() }
            }
            scroll.addView(track)
            val responsiveLaneHeightPx = if (reorderable && tag == "Video") {
                dp(TimelineThumbnailMath.THUMBNAIL_STRIP_HEIGHT_DP)
            } else {
                val responsiveLaneHeightDp = when {
                    keyboardLayoutActive -> minOf(heightDp, 28)
                    !reorderable -> heightDp
                    tag == "Müzik" || tag == "Ses" || tag.startsWith("Metin") -> heightDp
                    compactEditorLayout -> maxOf(heightDp, 52)
                    else -> heightDp
                }
                dp(responsiveLaneHeightDp)
            }
            row.addView(scroll, LinearLayout.LayoutParams(0, responsiveLaneHeightPx, 1f))

            if (!reorderable) {
                timelineTracks.addView(row, 0)
            } else {
                val savedIndex = timelineLaneOrder.indexOf(laneKey)
                if (savedIndex < 0) {
                    timelineLaneOrder.add(laneKey)
                    timelineTracks.addView(row)
                } else {
                    val rulerOffset = if (timelineTracks.childCount > 0 &&
                        timelineTracks.getChildAt(0).tag == "__ruler__") 1 else 0
                    timelineTracks.addView(row, (savedIndex + rulerOffset).coerceIn(0, timelineTracks.childCount))
                }
            }
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
            startMs: Long = 0L,
            onMove: ((Long) -> Unit)? = null,
            onTrimStart: ((Long) -> Unit)? = null,
            onTrimEnd: ((Long) -> Unit)? = null,
            keyframeTimesMs: List<Long> = emptyList()
        ) {
            val safeStart = startMs.coerceAtLeast(0L)
            val safeDuration = durationMs.coerceAtLeast(1L)
            val width = maxOf(dp(minimumWidthDp), widthBetween(safeStart, safeStart + safeDuration).coerceAtLeast(1))
            val blockHeight = dp(if (keyboardLayoutActive) 26 else 32)
            val frame = FrameLayout(this)
            val body = TextView(this).apply {
                text = title
                textSize = 9f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER_VERTICAL
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(dp(7), 0, dp(7), 0)
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(fill)
                    cornerRadius = dp(5).toFloat()
                    setStroke(dp(if (selected) 2 else 1),
                        if (selected) Color.rgb(117, 240, 221) else Color.rgb(61, 79, 99))
                }
                isClickable = true
                isFocusable = true
            }
            frame.addView(body, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            ))
            keyframeTimesMs.forEach { keyframeTimeMs ->
                val marker = TextView(this).apply {
                    text = "◆"
                    textSize = 9f
                    setTextColor(Color.rgb(255, 226, 112))
                    gravity = Gravity.CENTER
                    isClickable = false
                    isFocusable = false
                    contentDescription = "Anahtar kare: ${formatTimelineMs(keyframeTimeMs)}"
                }
                val markerX = TimelineScaleMath.pixelsForTime(
                    keyframeTimeMs.coerceIn(0L, safeDuration), timelinePixelsPerSecond()
                ).toInt().coerceIn(0, (width - dp(10)).coerceAtLeast(0))
                frame.addView(marker, FrameLayout.LayoutParams(dp(10), dp(10), Gravity.TOP or Gravity.LEFT).apply {
                    leftMargin = markerX
                })
            }
            if (onMove != null) {
                var downX = 0f
                var active = false
                var longPressed = false
                var moved = false
                val hold = Runnable {
                    if (active && !moved) {
                        longPressed = true
                        body.parent?.requestDisallowInterceptTouchEvent(true)
                        frame.animate().scaleX(0.96f).scaleY(0.96f).setDuration(100L).start()
                    }
                }
                body.setOnTouchListener { view, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            downX = event.rawX
                            active = true
                            moved = false
                            longPressed = false
                            pauseActivePreview()
                            timelineHandler.postDelayed(hold, android.view.ViewConfiguration.getLongPressTimeout().toLong())
                            true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = event.rawX - downX
                            if (kotlin.math.abs(dx) > android.view.ViewConfiguration.get(this).scaledTouchSlop) {
                                timelineHandler.removeCallbacks(hold)
                                if (longPressed) {
                                    moved = true
                                    view.parent?.requestDisallowInterceptTouchEvent(true)
                                    val pps = timelinePixelsPerSecond()
                                    frame.translationX = dx.coerceIn(
                                        -safeStart / 1000f * pps,
                                        (totalMs - safeStart - safeDuration).coerceAtLeast(0L) / 1000f * pps
                                    )
                                }
                            }
                            true
                        }
                        MotionEvent.ACTION_UP -> {
                            timelineHandler.removeCallbacks(hold)
                            active = false
                            frame.translationX = 0f
                            frame.animate().scaleX(1f).scaleY(1f).setDuration(100L).start()
                            view.parent?.requestDisallowInterceptTouchEvent(false)
                            if (longPressed && moved) {
                                onMove(TimelineScaleMath.millisecondsForPixels(event.rawX - downX, timelinePixelsPerSecond()))
                                refreshTimelineTracks()
                            } else if (!moved) onClick?.invoke()
                            view.performClick()
                            true
                        }
                        MotionEvent.ACTION_CANCEL -> {
                            timelineHandler.removeCallbacks(hold)
                            active = false
                            frame.translationX = 0f
                            view.parent?.requestDisallowInterceptTouchEvent(false)
                            true
                        }
                        else -> true
                    }
                }
            } else if (onClick != null) body.setOnClickListener { onClick() }

            fun addEdge(isStart: Boolean, callback: ((Long) -> Unit)?) {
                if (callback == null) return
                val edgeWidth = minOf(dp(18), (width / 3).coerceAtLeast(dp(7)))
                val edge = TextView(this).apply {
                    text = if (isStart) "‹" else "›"
                    textSize = 12f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                    setBackgroundColor(Color.rgb(23, 72, 103))
                    contentDescription = if (isStart) "Katman başlangıcını kırp" else "Katman bitişini kırp"
                }
                var downX = 0f
                var moved = false
                edge.setOnTouchListener { view, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            downX = event.rawX
                            moved = false
                            pauseActivePreview()
                            view.parent?.requestDisallowInterceptTouchEvent(true)
                            true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = event.rawX - downX
                            if (kotlin.math.abs(dx) > android.view.ViewConfiguration.get(this).scaledTouchSlop) moved = true
                            val delta = TimelineScaleMath.millisecondsForPixels(dx, timelinePixelsPerSecond())
                            val bound = if (isStart) safeDuration - minOf(500L, safeDuration) else totalMs - safeStart - safeDuration
                            view.translationX = dx.coerceIn(
                                (if (isStart) -safeStart else -safeDuration + minOf(500L, safeDuration)) / 1000f * timelinePixelsPerSecond(),
                                bound.coerceAtLeast(0L) / 1000f * timelinePixelsPerSecond()
                            )
                            true
                        }
                        MotionEvent.ACTION_UP -> {
                            val delta = TimelineScaleMath.millisecondsForPixels(event.rawX - downX, timelinePixelsPerSecond())
                            view.translationX = 0f
                            view.parent?.requestDisallowInterceptTouchEvent(false)
                            callback(if (moved) delta else 0L)
                            refreshTimelineTracks()
                            true
                        }
                        MotionEvent.ACTION_CANCEL -> {
                            view.translationX = 0f
                            view.parent?.requestDisallowInterceptTouchEvent(false)
                            true
                        }
                        else -> true
                    }
                }
                frame.addView(edge, FrameLayout.LayoutParams(
                    edgeWidth, ViewGroup.LayoutParams.MATCH_PARENT,
                    if (isStart) Gravity.START else Gravity.END
                ))
            }
            addEdge(true, onTrimStart)
            addEdge(false, onTrimEnd)
            track.addView(frame, LinearLayout.LayoutParams(width, blockHeight))
        }

        fun addSpacer(track: LinearLayout, startMs: Long, endMs: Long) {
            val width = widthBetween(startMs, endMs)
            if (width <= 0) return
            track.addView(View(this), LinearLayout.LayoutParams(width, dp(if (keyboardLayoutActive || compactEditorLayout) 20 else 24)))
        }

        fun addTextLayerBlock(track: LinearLayout, layer: TextLayer, layerIndex: Int) {
            val startMs = layer.startMs.coerceIn(0L, totalMs)
            val endMs = layer.endMs.coerceIn(startMs, totalMs)
            if (startMs > 0L) addSpacer(track, 0L, startMs)

            val blockWidth = widthBetween(startMs, endMs).coerceAtLeast(1)
            val blockHeight = dp(when {
                keyboardLayoutActive -> 26
                compactEditorLayout -> 42
                else -> 32
            })
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
            var bodyTouchActive = false
            var bodyLongPressed = false
            val bodyLongPressAction = Runnable {
                if (bodyTouchActive && !bodyMoved) {
                    bodyLongPressed = true
                    body.parent?.requestDisallowInterceptTouchEvent(true)
                    frame.animate().scaleX(0.96f).scaleY(0.96f).setDuration(100L).start()
                }
            }
            body.setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        bodyDownRawX = event.rawX
                        bodyInitialStartMs = layer.startMs
                        bodyInitialEndMs = layer.endMs
                        bodyMoved = false
                        bodyTouchActive = true
                        bodyLongPressed = false
                        player?.playWhenReady = false
                        // A normal horizontal swipe belongs to the shared timeline.
                        // Only a hold-then-drag moves this text strip.
                        view.parent?.requestDisallowInterceptTouchEvent(false)
                        timelineHandler.postDelayed(
                            bodyLongPressAction,
                            android.view.ViewConfiguration.getLongPressTimeout().toLong()
                        )
                        true
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        val deltaPx = event.rawX - bodyDownRawX
                        if (kotlin.math.abs(deltaPx) > android.view.ViewConfiguration.get(this).scaledTouchSlop) {
                            timelineHandler.removeCallbacks(bodyLongPressAction)
                            if (bodyLongPressed) {
                                bodyMoved = true
                                view.parent?.requestDisallowInterceptTouchEvent(true)
                                val pps = timelinePixelsPerSecond()
                                val leftLimitPx = bodyInitialStartMs / 1000f * pps
                                val rightLimitPx = (totalMs - bodyInitialEndMs).coerceAtLeast(0L) / 1000f * pps
                                frame.translationX = deltaPx.coerceIn(-leftLimitPx, rightLimitPx)
                            } else {
                                view.parent?.requestDisallowInterceptTouchEvent(false)
                            }
                        }
                        true
                    }
                    android.view.MotionEvent.ACTION_UP -> {
                        timelineHandler.removeCallbacks(bodyLongPressAction)
                        bodyTouchActive = false
                        frame.translationX = 0f
                        frame.animate().scaleX(1f).scaleY(1f).setDuration(100L).start()
                        view.parent?.requestDisallowInterceptTouchEvent(false)
                        if (bodyLongPressed && bodyMoved) {
                            val deltaPx = event.rawX - bodyDownRawX
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
                            enterTextTools()
                        } else if (!bodyMoved) {
                            view.performClick()
                            refreshTimelineTracks()
                        }
                        true
                    }
                    android.view.MotionEvent.ACTION_CANCEL -> {
                        timelineHandler.removeCallbacks(bodyLongPressAction)
                        bodyTouchActive = false
                        frame.translationX = 0f
                        frame.animate().scaleX(1f).scaleY(1f).setDuration(100L).start()
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
                                enterTextTools()
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

        // Keep the ruler present even while the keyboard is open. makeLane()
        // already reduces row height for IME mode; hiding this row makes the time
        // axis disappear exactly when text controls are being edited.
        val ruler = makeLane("", 26, "__ruler__", reorderable = false)
        // One shared ruler progresses from seconds to subsecond divisions and,
        // at close zoom, exact frame units from the source clip's real frame rate.
        val rulerFrameRate = clips.firstOrNull { it.frameRate != null }?.frameRate
        val rulerGrid = TimelineScaleMath.rulerGrid(
            pxPerSecond, dp(48).toFloat(), rulerFrameRate
        )
        fun addRulerInterval(startMs: Long, endMs: Long, label: String, minorDivisions: Int) {
            val startPositionPx = TimelineScaleMath.pixelsForTime(startMs, pxPerSecond)
            val nextPositionPx = TimelineScaleMath.pixelsForTime(endMs, pxPerSecond)
            val intervalWidth = (nextPositionPx - startPositionPx).coerceAtLeast(1)
            val tickCell = FrameLayout(this)
            val tick = TextView(this).apply {
                // Use the label selected for this exact absolute timestamp.
                // The former hard-coded 5-second condition hid every other
                // adaptive ruler label, including the frame labels at close zoom.
                text = label
                textSize = 9f
                setTextColor(Color.rgb(166, 184, 201))
                gravity = Gravity.TOP or Gravity.START
                setPadding(dp(2), 0, 0, 0)
                maxLines = 1
            }
            tickCell.addView(tick, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(15), Gravity.TOP or Gravity.START
            ))
            (if (minorDivisions > 0) TimelineScaleMath.rulerMinorTickOffsetsPx(
                intervalWidth, minorDivisions.coerceAtLeast(2)
            ) else emptyList()).forEach { x ->
                tickCell.addView(View(this).apply {
                    setBackgroundColor(Color.rgb(91, 111, 130))
                }, FrameLayout.LayoutParams(dp(1), dp(6)).apply {
                    leftMargin = x
                    topMargin = dp(15)
                })
            }
            tickCell.addView(View(this).apply {
                setBackgroundColor(Color.rgb(133, 153, 171))
            }, FrameLayout.LayoutParams(dp(1), dp(8)).apply {
                leftMargin = 0
                topMargin = dp(14)
            })
            ruler.addView(tickCell, LinearLayout.LayoutParams(intervalWidth, dp(22)))
        }

        if (rulerGrid.frameStep != null && rulerGrid.frameRate != null) {
            val fps = rulerGrid.frameRate
            val frameStep = rulerGrid.frameStep
            val lastFrame = kotlin.math.ceil(totalMs.toDouble() * fps / 1_000.0)
                .toLong().coerceAtLeast(1L)
            var frameIndex = 0L
            while (frameIndex < lastFrame) {
                val nextFrameIndex = frameIndex + frameStep
                val startMs = TimelineScaleMath.frameTimeMs(frameIndex, fps)
                val endMs = TimelineScaleMath.frameTimeMs(nextFrameIndex, fps)
                addRulerInterval(
                    startMs,
                    endMs,
                    TimelineScaleMath.formatRulerTickLabel(startMs, frameIndex, fps),
                    if (frameStep <= 1L) 0 else minOf(frameStep, 5L).toInt()
                )
                frameIndex = nextFrameIndex
            }
        } else {
            val rulerStepMs = rulerGrid.intervalMs
            val rulerEndMs = (((totalMs + rulerStepMs - 1L) / rulerStepMs).coerceAtLeast(1L)) * rulerStepMs
            var rulerTimeMs = 0L
            while (rulerTimeMs < rulerEndMs) {
                val nextTimeMs = rulerTimeMs + rulerStepMs
                addRulerInterval(
                    rulerTimeMs,
                    nextTimeMs,
                    TimelineScaleMath.formatRulerTickLabel(rulerTimeMs, 0L, null),
                    5
                )
                rulerTimeMs = nextTimeMs
            }
        }

        if (clips.isEmpty()) {
            // Keep the empty-state add action in the video lane as a floating,
            // transparent plus. Do not create a fake 5-second timeline block.
            val lane = makeLane(
                "Video",
                dp(TimelineThumbnailMath.THUMBNAIL_STRIP_HEIGHT_DP),
                "video"
            )
            val addVideoButton = TextView(this).apply {
                text = "＋"
                textSize = 22f
                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.rgb(57, 220, 190))
                background = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
                minWidth = dp(28)
                minHeight = dp(28)
                contentDescription = "Videoya klip ekle"
                isClickable = true
                isFocusable = true
                setOnClickListener { chooseVideo(true) }
            }
            lane.addView(addVideoButton, LinearLayout.LayoutParams(dp(28), ViewGroup.LayoutParams.MATCH_PARENT))
        } else {
            // CapCut-style main video strip: one VIDEO lane, one shared time axis.
            // Clips retain their real project start/end positions; overlaps are
            // stacked inside this same strip instead of creating extra VIDEO rows.
            val placements = TimelineVideoMath.singleTrackPlacements(videoTimelineSegments())
            val stripHeight = dp(TimelineThumbnailMath.THUMBNAIL_STRIP_HEIGHT_DP)
            val furthestClipEndPx = placements.maxOfOrNull {
                widthBetween(0L, it.timelineEndMs)
            } ?: 0
            // Reserve room for the floating add button immediately after the
            // last video clip. The button is visual only: it has no timeline
            // timestamp, does not create a placeholder clip, and never shifts
            // the shared ruler/playhead coordinate mapping.
            val addButtonLeft = furthestClipEndPx + dp(8)
            val addButtonRight = addButtonLeft + dp(28)
            val stripWidth = maxOf(widthBetween(0L, totalMs), furthestClipEndPx, addButtonRight)
                .coerceAtLeast(dp(115))
            val strip = FrameLayout(this).apply {
                setBackgroundColor(Color.rgb(16, 25, 36))
                layoutDirection = View.LAYOUT_DIRECTION_LTR
                clipChildren = true
                clipToPadding = true
                isClickable = true
                setOnClickListener { clearTimelineClipSelection() }
            }

            // Placement order is layer order: later clips draw above earlier ones,
            // matching activeSegmentIndexAt() and the flattened preview/export.
            placements.forEach { placement ->
                val index = placement.clipIndex
                val clip = clips.getOrNull(index) ?: return@forEach
                val left = widthBetween(0L, placement.timelineStartMs)
                val width = widthBetween(placement.timelineStartMs, placement.timelineEndMs)
                    .coerceAtLeast(dp(1))
                val clipView = buildVideoClipFilmstrip(
                    clip, index, width, stripHeight, index == selectedClipIndex && clipHandlesVisible
                )
                strip.addView(clipView, FrameLayout.LayoutParams(width, stripHeight).apply {
                    leftMargin = left
                    gravity = Gravity.TOP or Gravity.LEFT
                })
            }
            val addVideoButton = TextView(this).apply {
                text = "＋"
                textSize = 22f
                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.rgb(57, 220, 190))
                background = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
                minWidth = dp(28)
                minHeight = dp(28)
                contentDescription = "Zaman çizelgesine video klibi ekle"
                isClickable = true
                isFocusable = true
                elevation = dp(2).toFloat()
                setOnClickListener { chooseVideo(true) }
            }
            strip.addView(addVideoButton, FrameLayout.LayoutParams(
                dp(28), ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                leftMargin = addButtonLeft
                gravity = Gravity.TOP or Gravity.LEFT
            })
            val videoTrack = makeLane(
                "Video",
                dp(TimelineThumbnailMath.THUMBNAIL_STRIP_HEIGHT_DP),
                "video"
            )
            videoTrack.addView(strip, LinearLayout.LayoutParams(stripWidth, stripHeight))
        }

        val overlayTrack = makeLane("Bindirme", 30, "overlay")
        overlayClips.forEach { overlay ->
            val endMs = overlay.timelineStartMs + overlay.durationMs
            addSpacer(overlayTrack, 0L, overlay.timelineStartMs)
            addBlock(
                track = overlayTrack,
                title = (if (overlay.isImage) "▧ " else "▣ ") + overlay.title,
                durationMs = overlay.durationMs,
                fill = Color.rgb(41, 113, 154),
                selected = overlay.id == selectedOverlayId,
                onClick = { selectOverlay(overlay.id) },
                minimumWidthDp = 48,
                startMs = overlay.timelineStartMs,
                keyframeTimesMs = overlay.keyframes.map { it.timeMs },
                onMove = { deltaMs ->
                    val movableDuration = overlay.durationMs.coerceAtLeast(1L)
                    overlay.timelineStartMs = (overlay.timelineStartMs + deltaMs).coerceIn(
                        0L, (totalMs - movableDuration).coerceAtLeast(0L)
                    )
                    selectedOverlayId = overlay.id
                    timelinePlayheadMs = overlay.timelineStartMs
                    enterOverlayTools()
                    rebuildTimelinePreviewForOverlayEdit()
                    status.text = "Bindirme zaman çizelgesinde taşındı."
                },
                onTrimStart = { deltaMs ->
                    val oldStart = overlay.timelineStartMs
                    val oldDuration = overlay.durationMs
                    val minimum = minOf(500L, oldDuration)
                    val lower = -minOf(oldStart, if (overlay.isImage) oldStart else overlay.sourceStartMs)
                    val shift = deltaMs.coerceIn(lower, (oldDuration - minimum).coerceAtLeast(0L))
                    val newStartState = overlayStateAtTime(overlay, oldStart + shift)
                    overlay.timelineStartMs = oldStart + shift
                    overlay.durationMs = oldDuration - shift
                    if (!overlay.isImage) overlay.sourceStartMs = (overlay.sourceStartMs + shift).coerceAtLeast(0L)
                    if (overlay.keyframes.isNotEmpty()) {
                        val rebased = OverlayKeyframeMath.rebaseAfterStartTrim(
                            overlay.keyframes, shift, overlay.durationMs, newStartState
                        )
                        overlay.keyframes.clear()
                        overlay.keyframes.addAll(rebased)
                    }
                    selectedOverlayId = overlay.id
                    timelinePlayheadMs = overlay.timelineStartMs
                    enterOverlayTools()
                    rebuildTimelinePreviewForOverlayEdit()
                    status.text = "Bindirme başlangıcı kırpıldı."
                },
                onTrimEnd = { deltaMs ->
                    val oldDuration = overlay.durationMs
                    val sourceRemaining = if (overlay.isImage) Long.MAX_VALUE else
                        (overlay.sourceDurationMs - overlay.sourceStartMs).coerceAtLeast(1L)
                    val timelineRemaining = (totalMs - overlay.timelineStartMs).coerceAtLeast(1L)
                    val maximum = minOf(sourceRemaining, timelineRemaining)
                    val minimum = minOf(500L, maximum)
                    overlay.durationMs = (oldDuration + deltaMs).coerceIn(minimum, maximum)
                    selectedOverlayId = overlay.id
                    timelinePlayheadMs = overlay.timelineStartMs
                    enterOverlayTools()
                    rebuildTimelinePreviewForOverlayEdit()
                    status.text = "Bindirme bitişi kırpıldı."
                }
            )
            addSpacer(overlayTrack, endMs, totalMs)
        }

        if (textLayers.isEmpty()) {
            makeLane("Metin", 30, "text")
        } else {
            textLayers.forEachIndexed { index, layer ->
                val laneTitle = if (layer.kind == TextLayerKind.STICKER) "Çıkartma ${index + 1}" else "Metin ${index + 1}"
                val laneKey = if (layer.kind == TextLayerKind.STICKER) "sticker:${layer.id}" else "text:${layer.id}"
                val textTrack = makeLane(laneTitle, 30, laneKey)
                addTextLayerBlock(textTrack, layer, index)
            }
        }

        val musicTrack = makeLane("Müzik", 28, "music")
        if (musicUri != null && totalMs > 0L) {
            val musicRange = TimelineRangeMath.normalize(
                TimelineRange(musicStartMs, musicDurationMs),
                totalMs
            )
            musicStartMs = musicRange.startMs
            musicDurationMs = musicRange.durationMs
            addSpacer(musicTrack, 0L, musicRange.startMs)
            addMusicTimelineBlock(musicTrack, totalMs)
            addSpacer(musicTrack, musicRange.startMs + musicRange.durationMs, totalMs)
        }

        if (soundUri != null && totalMs > 0L) {
            val effectTrack = makeLane("Ses", 28, "sound")
            val segment = TimelineAudioMath.normalize(
                TimelineAudioSegment(soundStartMs, soundSourceStartMs, soundDurationMs),
                soundSourceDurationMs,
                totalMs
            )
            soundStartMs = segment.timelineStartMs
            soundSourceStartMs = segment.sourceStartMs
            soundDurationMs = segment.durationMs
            val endMs = segment.timelineStartMs + segment.durationMs
            addSpacer(effectTrack, 0L, segment.timelineStartMs)
            addSoundEffectTimelineBlock(effectTrack, totalMs)
            addSpacer(effectTrack, endMs, totalMs)
        }
        // All lane origins are offset from the shared 84dp control gutter so time zero
        // lands on the full timeline/Play center, not the center of the smaller
        // scroll viewport to the right of that gutter. This keeps ruler, filmstrip
        // and the stationary playhead on one vertical axis.
        timelineScrollViews.forEach { laneScroll ->
            val laneTrack = laneScroll.getChildAt(0) as? LinearLayout ?: return@forEach
            // Empty persistent lanes still represent the same project time axis.
            // Give them an invisible duration span so their HorizontalScrollView
            // remains scrollable and follows the shared ruler/video playhead.
            // Without this, an empty Text/Music lane stays at scrollX=0 while the
            // populated ruler/video lanes move during playback.
            if (laneTrack.childCount == 0 && totalMs > 0L) {
                val durationWidth = widthBetween(0L, totalMs).coerceAtLeast(dp(1))
                laneTrack.addView(
                    View(this).apply {
                        tag = "timeline-empty-duration-spacer"
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    },
                    LinearLayout.LayoutParams(durationWidth, dp(24))
                )
            }
            fun updateLaneOriginSpacer(viewportWidth: Int) {
                if (viewportWidth <= 0 || laneTrack.childCount == 0) return
                // The scroll viewport can be measured after the timeline is first built,
                // and can change again when the keyboard or phone width changes. Keep
                // both spacers tied to the full track frame, not just the scroll
                // viewport (which starts to the right of the 57dp label/grip gutter).
                val originWidth = timelineOriginSpacerWidth(viewportWidth)
                val endWidth = timelineEndSpacerWidth(viewportWidth)
                val first = laneTrack.getChildAt(0)
                if (first?.tag == "timeline-origin-spacer") {
                    val params = first.layoutParams as? LinearLayout.LayoutParams
                    if (params == null || params.width != originWidth) {
                        first.layoutParams = LinearLayout.LayoutParams(originWidth, dp(24))
                    }
                }
                val last = laneTrack.getChildAt(laneTrack.childCount - 1)
                if (last?.tag == "timeline-end-spacer") {
                    val params = last.layoutParams as? LinearLayout.LayoutParams
                    if (params == null || params.width != endWidth) {
                        last.layoutParams = LinearLayout.LayoutParams(endWidth, dp(24))
                    }
                }
            }
            val originWidth = timelineOriginSpacerWidth(laneScroll.width)
            val endWidth = timelineEndSpacerWidth(laneScroll.width)
            if (laneTrack.getChildAt(0)?.tag != "timeline-origin-spacer") {
                val originSpacer = View(this).apply { tag = "timeline-origin-spacer" }
                laneTrack.addView(originSpacer, 0, LinearLayout.LayoutParams(originWidth, dp(24)))
            }
            if (laneTrack.getChildAt(laneTrack.childCount - 1)?.tag != "timeline-end-spacer") {
                val endSpacer = View(this).apply { tag = "timeline-end-spacer" }
                laneTrack.addView(endSpacer, LinearLayout.LayoutParams(endWidth, dp(24)))
            }
            // Recalculate after measurement and on responsive viewport changes.
            laneScroll.addOnLayoutChangeListener { view, left, top, right, bottom,
                                                   oldLeft, oldTop, oldRight, oldBottom ->
                if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                    updateLaneOriginSpacer(view.width)
                }
            }
            laneScroll.post { updateLaneOriginSpacer(laneScroll.width) }
        }
        // The matching end spacer lets the final frame reach the center playhead too.
        // A MATCH_PARENT playhead inside a WRAP_CONTENT FrameLayout would let
        // the frame expand to the full available screen height and push the tool dock
        // off-screen. Size the frame from the actual timeline lanes instead.
        val laneContentHeight = (0 until timelineTracks.childCount).sumOf { index ->
            val row = timelineTracks.getChildAt(index) as? LinearLayout ?: return@sumOf 0
            val lane = row.getChildAt(if (row.tag == "__ruler__") 1 else 2)
            val laneHeight = lane?.layoutParams?.height?.takeIf { it > 0 } ?: dp(24)
            laneHeight + row.paddingTop + row.paddingBottom
        }.coerceAtLeast(if (keyboardLayoutActive) dp(24) else dp(188))
        (timelineTracksFrame.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            if (params.height != laneContentHeight) {
                params.height = laneContentHeight
                timelineTracksFrame.layoutParams = params
            }
        }
        // Size the timeline card from its real track content instead of reserving
        // a large fixed empty panel on small phones. Each added layer earns its own row.
        (timelineTracksFrame.parent?.parent as? LinearLayout)?.let { card ->
            val desiredHeight = TimelineLayoutMath.contentSizedCardHeightPx(
                laneContentHeightPx = laneContentHeight,
                timeRowHeightPx = dp(26),
                verticalPaddingPx = dp(6),
                minimumHeightPx = if (keyboardLayoutActive) dp(88) else dp(220),
                maximumHeightPx = if (keyboardLayoutActive) dp(154) else dp(280)
            )
            val cardParams = card.layoutParams as? LinearLayout.LayoutParams
            if (cardParams != null && cardParams.height != desiredHeight) {
                cardParams.height = desiredHeight
                card.layoutParams = cardParams
            }
        }

        val restoreScrollX = timelineHorizontalScrollX
        timelineScrollViews.firstOrNull()?.post {
            scrollTimelineProgrammatically(restoreScrollX)
        }
        updateTimelinePlayheadLine()
    }



    private fun ensureMusicSourceDuration(uri: Uri) {
        if (musicSourceDurationMs > 0L) return
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
            runOnUiThread {
                if (musicUri == uri && duration > 0L) {
                    musicSourceDurationMs = duration
                    if (musicSourceEndMs <= 0L) musicSourceEndMs = duration
                    musicSourceStartMs = musicSourceStartMs.coerceIn(0L, duration)
                    musicSourceEndMs = musicSourceEndMs.coerceIn(musicSourceStartMs, duration)
                    updateMusicSourceRangeControls()
                    if (musicDurationMs <= 0L) musicDurationMs = totalOutputDurationMs()
                    updateMusicTimingLabel()
                    refreshTimelineTracks()
                }
            }
        }.start()
    }

    private fun updateMusicSourceRangeControls() {
        if (!::musicSourceRangeLabel.isInitialized) return
        val duration = musicSourceDurationMs
        val range = MusicSourceRangeMath.normalize(
            MusicSourceRange(musicSourceStartMs, musicSourceEndMs.takeIf { it > 0L } ?: duration),
            duration
        )
        musicSourceStartMs = range.startMs
        musicSourceEndMs = range.endMs
        musicSourceRangeLabel.text = if (duration > 0L) {
            "Dosya içi: ${formatTimelineMs(range.startMs)} – ${formatTimelineMs(range.endMs)}"
        } else "Müzik kaynak aralığı okunuyor…"
        if (duration > 0L) {
            musicSourceStartSeek.progress = (range.startMs * 1000L / duration).toInt().coerceIn(0, 1000)
            musicSourceEndSeek.progress = (range.endMs * 1000L / duration).toInt().coerceIn(0, 1000)
        }
    }

    private fun updateMusicTimingLabel() {
        if (!::musicLabel.isInitialized) return
        val title = musicTitle
        if (musicUri == null || title == null) {
            musicLabel.text = "Müzik seçilmedi."
            return
        }
        val endMs = musicStartMs + musicDurationMs
        musicLabel.text = "Müzik: " + title + " • " +
            formatTimelineMs(musicStartMs) + " – " + formatTimelineMs(endMs) + " • döngü"
    }

    private fun addMusicTimelineBlock(track: LinearLayout, totalMs: Long) {
        val normalized = TimelineRangeMath.normalize(
            TimelineRange(musicStartMs, musicDurationMs),
            totalMs
        )
        musicStartMs = normalized.startMs
        musicDurationMs = normalized.durationMs
        if (normalized.durationMs <= 0L) return

        val pixelsPerSecond = timelinePixelsPerSecond()
        val blockWidth = maxOf(
            dp(38),
            TimelineScaleMath.widthBetween(
                normalized.startMs,
                normalized.startMs + normalized.durationMs,
                pixelsPerSecond
            ).coerceAtLeast(1)
        )
        val frame = FrameLayout(this).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.rgb(46, 125, 85))
                cornerRadius = dp(5).toFloat()
                setStroke(dp(1), Color.rgb(93, 190, 127))
            }
            clipToOutline = true
            contentDescription = "Müzik: ortasından taşı, kenarlarından süreyi ayarla"
        }
        val body = TextView(this).apply {
            text = "♫ " + (musicTitle ?: "Müzik")
            textSize = 9f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(7), 0, dp(7), 0)
        }
        frame.addView(body, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        var bodyDownX = 0f
        var bodyMoved = false
        var bodyTouchActive = false
        var bodyLongPressed = false
        var bodyInitial = normalized
        val bodyLongPressAction = Runnable {
            if (bodyTouchActive && !bodyMoved) {
                bodyLongPressed = true
                body.parent?.requestDisallowInterceptTouchEvent(true)
                frame.animate().scaleX(0.96f).scaleY(0.96f).setDuration(100L).start()
            }
        }
        body.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    bodyDownX = event.rawX
                    bodyInitial = TimelineRange(musicStartMs, musicDurationMs)
                    bodyMoved = false
                    bodyTouchActive = true
                    bodyLongPressed = false
                    player?.playWhenReady = false
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                    timelineHandler.postDelayed(
                        bodyLongPressAction,
                        android.view.ViewConfiguration.getLongPressTimeout().toLong()
                    )
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val deltaPx = event.rawX - bodyDownX
                    if (kotlin.math.abs(deltaPx) > android.view.ViewConfiguration.get(this).scaledTouchSlop) {
                        timelineHandler.removeCallbacks(bodyLongPressAction)
                        if (bodyLongPressed) {
                            bodyMoved = true
                            view.parent?.requestDisallowInterceptTouchEvent(true)
                            val minPx = -bodyInitial.startMs / 1000f * pixelsPerSecond
                            val maxPx = (totalMs - bodyInitial.startMs - bodyInitial.durationMs)
                                .coerceAtLeast(0L) / 1000f * pixelsPerSecond
                            frame.translationX = deltaPx.coerceIn(minPx, maxPx)
                        } else {
                            view.parent?.requestDisallowInterceptTouchEvent(false)
                        }
                    }
                    true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    timelineHandler.removeCallbacks(bodyLongPressAction)
                    bodyTouchActive = false
                    frame.translationX = 0f
                    frame.animate().scaleX(1f).scaleY(1f).setDuration(100L).start()
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                    if (bodyLongPressed && bodyMoved) {
                        val deltaPx = event.rawX - bodyDownX
                        val deltaMs = TimelineScaleMath.millisecondsForPixels(deltaPx, pixelsPerSecond)
                        val moved = TimelineRangeMath.move(
                            bodyInitial,
                            bodyInitial.startMs + deltaMs,
                            totalMs
                        )
                        musicStartMs = moved.startMs
                        musicDurationMs = moved.durationMs
                        updateMusicTimingLabel()
                        status.text = "Müzik zaman çizelgesinde taşındı."
                        refreshTimelineTracks()
                        enterAudioTools()
                    } else if (!bodyMoved) {
                        enterAudioTools()
                        view.performClick()
                    }
                    true
                }
                android.view.MotionEvent.ACTION_CANCEL -> {
                    timelineHandler.removeCallbacks(bodyLongPressAction)
                    bodyTouchActive = false
                    frame.translationX = 0f
                    frame.animate().scaleX(1f).scaleY(1f).setDuration(100L).start()
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                    true
                }
                else -> true
            }
        }

        fun addMusicTrimHandle(isStart: Boolean) {
            val handleWidth = minOf(dp(16), (blockWidth / 3).coerceAtLeast(dp(7)))
            val handle = TextView(this).apply {
                text = if (isStart) "‹" else "›"
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setBackgroundColor(Color.rgb(27, 91, 57))
                elevation = dp(3).toFloat()
                contentDescription = if (isStart) "Müzik başlangıç tutamacı" else "Müzik bitiş tutamacı"
            }
            var downRawX = 0f
            var initial = normalized
            var moved = false
            handle.setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        downRawX = event.rawX
                        initial = TimelineRange(musicStartMs, musicDurationMs)
                        moved = false
                        player?.playWhenReady = false
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                        true
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        val deltaPx = event.rawX - downRawX
                        if (kotlin.math.abs(deltaPx) > android.view.ViewConfiguration.get(this).scaledTouchSlop) {
                            moved = true
                        }
                        val deltaMs = TimelineScaleMath.millisecondsForPixels(deltaPx, pixelsPerSecond)
                        val lowerDeltaMs: Long
                        val upperDeltaMs: Long
                        if (isStart) {
                            lowerDeltaMs = -initial.startMs
                            upperDeltaMs = (initial.durationMs - TimelineRangeMath.MIN_DURATION_MS).coerceAtLeast(0L)
                        } else {
                            lowerDeltaMs = TimelineRangeMath.MIN_DURATION_MS - initial.durationMs
                            upperDeltaMs = (totalMs - initial.startMs - initial.durationMs).coerceAtLeast(0L)
                        }
                        val minPx = lowerDeltaMs / 1000f * pixelsPerSecond
                        val maxPx = upperDeltaMs / 1000f * pixelsPerSecond
                        view.translationX = deltaPx.coerceIn(minPx, maxPx)
                        true
                    }
                    android.view.MotionEvent.ACTION_UP -> {
                        val deltaPx = event.rawX - downRawX
                        val deltaMs = TimelineScaleMath.millisecondsForPixels(deltaPx, pixelsPerSecond)
                        view.translationX = 0f
                        view.parent?.requestDisallowInterceptTouchEvent(false)
                        if (moved) {
                            val updated = if (isStart) {
                                TimelineRangeMath.trimStart(initial, deltaMs, totalMs)
                            } else {
                                TimelineRangeMath.trimEnd(initial, deltaMs, totalMs)
                            }
                            musicStartMs = updated.startMs
                            musicDurationMs = updated.durationMs
                            updateMusicTimingLabel()
                            status.text = if (isStart) "Müzik başlangıcı ayarlandı." else "Müzik bitişi ayarlandı."
                            refreshTimelineTracks()
                            enterAudioTools()
                        } else {
                            enterAudioTools()
                        }
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

        addMusicTrimHandle(isStart = true)
        addMusicTrimHandle(isStart = false)
        track.addView(frame, LinearLayout.LayoutParams(blockWidth, dp(24)))
    }

    private fun addSoundEffectTimelineBlock(track: LinearLayout, totalMs: Long) {
        val normalized = TimelineAudioMath.normalize(
            TimelineAudioSegment(soundStartMs, soundSourceStartMs, soundDurationMs),
            soundSourceDurationMs,
            totalMs
        )
        soundStartMs = normalized.timelineStartMs
        soundSourceStartMs = normalized.sourceStartMs
        soundDurationMs = normalized.durationMs
        if (normalized.durationMs <= 0L) return

        val pixelsPerSecond = timelinePixelsPerSecond()
        val blockWidth = maxOf(
            dp(38),
            TimelineScaleMath.widthBetween(
                normalized.timelineStartMs,
                normalized.timelineStartMs + normalized.durationMs,
                pixelsPerSecond
            ).coerceAtLeast(1)
        )
        val frame = FrameLayout(this).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.rgb(166, 102, 40))
                cornerRadius = dp(5).toFloat()
                setStroke(dp(1), Color.rgb(214, 157, 84))
            }
            clipToOutline = true
            contentDescription = "Ses efekti: ortasından taşı, kenarlarından kırp"
        }
        val body = TextView(this).apply {
            text = "♪ ${soundTitle ?: "Ses efekti"}"
            textSize = 9f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(7), 0, dp(7), 0)
        }
        frame.addView(body, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        var bodyDownX = 0f
        var bodyMoved = false
        var bodyTouchActive = false
        var bodyLongPressed = false
        var bodyInitial = normalized
        val bodyLongPressAction = Runnable {
            if (bodyTouchActive && !bodyMoved) {
                bodyLongPressed = true
                body.parent?.requestDisallowInterceptTouchEvent(true)
                frame.animate().scaleX(0.96f).scaleY(0.96f).setDuration(100L).start()
            }
        }
        body.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    bodyDownX = event.rawX
                    bodyInitial = TimelineAudioSegment(soundStartMs, soundSourceStartMs, soundDurationMs)
                    bodyMoved = false
                    bodyTouchActive = true
                    bodyLongPressed = false
                    player?.playWhenReady = false
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                    timelineHandler.postDelayed(
                        bodyLongPressAction,
                        android.view.ViewConfiguration.getLongPressTimeout().toLong()
                    )
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val deltaPx = event.rawX - bodyDownX
                    if (kotlin.math.abs(deltaPx) > android.view.ViewConfiguration.get(this).scaledTouchSlop) {
                        timelineHandler.removeCallbacks(bodyLongPressAction)
                        if (bodyLongPressed) {
                            bodyMoved = true
                            view.parent?.requestDisallowInterceptTouchEvent(true)
                            val minPx = -bodyInitial.timelineStartMs / 1000f * pixelsPerSecond
                            val maxPx = (totalMs - bodyInitial.timelineStartMs - bodyInitial.durationMs)
                                .coerceAtLeast(0L) / 1000f * pixelsPerSecond
                            frame.translationX = deltaPx.coerceIn(minPx, maxPx)
                        } else {
                            view.parent?.requestDisallowInterceptTouchEvent(false)
                        }
                    }
                    true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    timelineHandler.removeCallbacks(bodyLongPressAction)
                    bodyTouchActive = false
                    frame.translationX = 0f
                    frame.animate().scaleX(1f).scaleY(1f).setDuration(100L).start()
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                    if (bodyLongPressed && bodyMoved) {
                        val deltaPx = event.rawX - bodyDownX
                        val deltaMs = TimelineScaleMath.millisecondsForPixels(deltaPx, pixelsPerSecond)
                        val moved = TimelineAudioMath.move(
                            bodyInitial,
                            bodyInitial.timelineStartMs + deltaMs,
                            soundSourceDurationMs,
                            totalMs
                        )
                        soundStartMs = moved.timelineStartMs
                        soundSourceStartMs = moved.sourceStartMs
                        soundDurationMs = moved.durationMs
                        updateSoundControls()
                        status.text = "Ses efekti zaman çizelgesinde taşındı."
                        refreshTimelineTracks()
                        enterAudioTools()
                    } else if (!bodyMoved) {
                        enterAudioTools()
                        view.performClick()
                    }
                    true
                }
                android.view.MotionEvent.ACTION_CANCEL -> {
                    timelineHandler.removeCallbacks(bodyLongPressAction)
                    bodyTouchActive = false
                    frame.translationX = 0f
                    frame.animate().scaleX(1f).scaleY(1f).setDuration(100L).start()
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                    true
                }
                else -> true
            }
        }

        fun addTrimHandle(isStart: Boolean) {
            val handleWidth = minOf(dp(16), (blockWidth / 3).coerceAtLeast(dp(7)))
            val handle = TextView(this).apply {
                text = if (isStart) "‹" else "›"
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setBackgroundColor(Color.rgb(116, 65, 25))
                elevation = dp(3).toFloat()
                contentDescription = if (isStart) "Efekt başlangıç tutamacı" else "Efekt bitiş tutamacı"
            }
            var downRawX = 0f
            var initial = normalized
            var moved = false
            handle.setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        downRawX = event.rawX
                        initial = TimelineAudioSegment(soundStartMs, soundSourceStartMs, soundDurationMs)
                        moved = false
                        player?.playWhenReady = false
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                        true
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        val deltaPx = event.rawX - downRawX
                        if (kotlin.math.abs(deltaPx) > android.view.ViewConfiguration.get(this).scaledTouchSlop) {
                            moved = true
                        }
                        val deltaMs = TimelineScaleMath.millisecondsForPixels(deltaPx, pixelsPerSecond)
                        val lowerDeltaMs: Long
                        val upperDeltaMs: Long
                        if (isStart) {
                            lowerDeltaMs = -minOf(initial.timelineStartMs, initial.sourceStartMs)
                            upperDeltaMs = (initial.durationMs - 100L).coerceAtLeast(0L)
                        } else {
                            val maximumDuration = minOf(
                                (soundSourceDurationMs - initial.sourceStartMs).coerceAtLeast(0L),
                                (totalMs - initial.timelineStartMs).coerceAtLeast(0L)
                            )
                            lowerDeltaMs = 100L - initial.durationMs
                            upperDeltaMs = (maximumDuration - initial.durationMs).coerceAtLeast(0L)
                        }
                        val minPx = lowerDeltaMs / 1000f * pixelsPerSecond
                        val maxPx = upperDeltaMs / 1000f * pixelsPerSecond
                        view.translationX = deltaPx.coerceIn(minPx, maxPx)
                        true
                    }
                    android.view.MotionEvent.ACTION_UP -> {
                        val deltaPx = event.rawX - downRawX
                        val deltaMs = TimelineScaleMath.millisecondsForPixels(deltaPx, pixelsPerSecond)
                        view.translationX = 0f
                        view.parent?.requestDisallowInterceptTouchEvent(false)
                        if (moved) {
                            val updated = if (isStart) {
                                TimelineAudioMath.trimStart(
                                    initial, deltaMs, soundSourceDurationMs, totalMs
                                )
                            } else {
                                TimelineAudioMath.trimEnd(
                                    initial, deltaMs, soundSourceDurationMs, totalMs
                                )
                            }
                            soundStartMs = updated.timelineStartMs
                            soundSourceStartMs = updated.sourceStartMs
                            soundDurationMs = updated.durationMs
                            updateSoundControls()
                            status.text = if (isStart) "Ses efektinin başlangıcı kırpıldı." else "Ses efektinin bitişi kırpıldı."
                            refreshTimelineTracks()
                            enterAudioTools()
                        } else {
                            enterAudioTools()
                        }
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

        addTrimHandle(isStart = true)
        addTrimHandle(isStart = false)
        track.addView(frame, LinearLayout.LayoutParams(blockWidth, dp(24)))
    }

    private fun updateTimelinePlayheadLine() {
        if (!::timelinePlayheadLine.isInitialized || !::timelineTracksFrame.isInitialized) return
        // Match the Play control's center in the full editor content area. Do not
        // center relative to the narrower scroll viewport after its 57dp grip gutter.
        val centerX = timelineTracksFrame.width / 2
        timelinePlayheadLine.visibility = View.VISIBLE
        val params = timelinePlayheadLine.layoutParams as? FrameLayout.LayoutParams ?: return
        params.width = dp(28)
        // The white playhead is a stationary viewport guide; timeline content moves beneath it.
        params.leftMargin = centerX - dp(14)
        timelinePlayheadLine.layoutParams = params
        // Playback and the white playhead remain fixed while the time readout updates.
        timelinePlayheadLine.bringToFront()
    }

    private fun scrollTimelineToPlayhead() {
        val firstLane = timelineScrollViews.firstOrNull() ?: return
        if (firstLane.width <= 0 || timelineScrollViews.isEmpty()) return
        // With time zero at the viewport center, scroll distance equals elapsed time.
        val requestedScroll = TimelineScaleMath.pixelsForTime(
            timelinePlayheadMs, timelinePixelsPerSecond()
        ).coerceAtLeast(0)
        if (firstLane.scrollX == requestedScroll) return
        scrollTimelineProgrammatically(requestedScroll)
    }

    private fun scrollTimelineProgrammatically(requestedScrollX: Int) {
        val target = requestedScrollX.coerceAtLeast(0)
        val lanes = timelineScrollViews.toList()
        val wasAutoScrolling = autoScrollingTimeline
        val wasSyncing = syncingTimelineScroll
        autoScrollingTimeline = true
        syncingTimelineScroll = true
        try {
            lanes.forEach { lane ->
                if (lane.scrollX != target) lane.scrollTo(target, 0)
            }
            timelineHorizontalScrollX = lanes.firstOrNull()?.scrollX ?: target
        } finally {
            syncingTimelineScroll = wasSyncing
            autoScrollingTimeline = wasAutoScrolling
        }
        updateTimelinePlayheadLine()
    }

    private fun finishTimelinePinch() {
        if (!timelineScaleGestureActive) return

        val finalPxPerSecond = (
            timelinePinchBasePxPerSecond * timelinePinchPreviewRatio
        ).coerceIn(dp(11).toFloat(), dp(720).toFloat())

        // Remove the temporary transform before rebuilding real timeline geometry.
        timelineScrollViews.toList().forEach { laneScroll ->
            val content = laneScroll.getChildAt(0) ?: return@forEach
            content.scaleX = 1f
            content.translationX = 0f
            content.pivotX = content.width / 2f
            content.pivotY = content.height / 2f
        }

        timelineScaleGestureActive = false
        val lane = timelineScrollViews.firstOrNull() ?: return
        val scaleChanged = kotlin.math.abs(finalPxPerSecond - timelinePixelsPerSecond()) > 0.01f
        if (scaleChanged) timelinePxPerSec = finalPxPerSecond

        // A moving pinch midpoint also pans the timeline while keeping the original
        // timestamp under that midpoint; the same spacer geometry is used by every lane.
        val anchoredScrollX = TimelineScaleMath.scrollForAnchoredZoom(
            timelineScaleAnchorTimeMs,
            timelineScaleAnchorViewportX,
            timelineOriginSpacerWidth(lane.width).toFloat(),
            timelinePixelsPerSecond()
        ).coerceAtLeast(0)
        val scrollChanged = anchoredScrollX != lane.scrollX
        if (scaleChanged || scrollChanged) {
            // If the pinch midpoint is not at the fixed centre playhead, moving the
            // timeline changes the time now beneath that playhead. Keep the readout,
            // seek control and preview synchronized with the visible centre frame.
            val centreX = timelineOriginSpacerWidth(lane.width).toFloat()
            val playheadAfterPinch = TimelineScaleMath.timeAtViewportX(
                anchoredScrollX,
                centreX,
                centreX,
                timelinePixelsPerSecond()
            ).coerceIn(0L, totalOutputDurationMs())
            val playheadChanged = playheadAfterPinch != timelinePlayheadMs
            if (playheadChanged) timelinePlayheadMs = playheadAfterPinch

            pendingTimelineScrollX = anchoredScrollX
            timelineHorizontalScrollX = anchoredScrollX
            refreshTimelineTracks()
            if (playheadChanged) {
                updateTimelinePlayheadLabel()
                scheduleTimelineScrubPreviewSeek(playheadAfterPinch)
            }
        }
    }

    private fun isWindowPointInsideTimeline(screenX: Float, screenY: Float): Boolean {
        if (!::timelineTracksFrame.isInitialized) return false
        val location = IntArray(2)
        // Activity.dispatchTouchEvent / ScaleGestureDetector coordinates are window-local.
        // Compare them with getLocationInWindow(), not absolute screen coordinates.
        timelineTracksFrame.getLocationInWindow(location)
        return screenX >= location[0] && screenX <= location[0] + timelineTracksFrame.width &&
            screenY >= location[1] && screenY <= location[1] + timelineTracksFrame.height
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val wasPinchCaptured = timelinePinchGestureCaptured
        timelineScaleGestureDetector.onTouchEvent(event)

        if (!wasPinchCaptured &&
            event.pointerCount >= 2 &&
            timelineScaleGestureActive
        ) {
            // The first finger may already have started a clip drag/trim. Cancel
            // that child gesture before the pinch owns subsequent pointer events.
            val cancel = MotionEvent.obtain(event).apply {
                action = MotionEvent.ACTION_CANCEL
            }
            try {
                super.dispatchTouchEvent(cancel)
            } finally {
                cancel.recycle()
            }
            timelinePinchGestureCaptured = true
            return true
        }

        if (timelinePinchGestureCaptured) {
            if (event.actionMasked == MotionEvent.ACTION_UP ||
                event.actionMasked == MotionEvent.ACTION_CANCEL
            ) {
                timelinePinchGestureCaptured = false
                timelineScaleGestureActive = false
            }
            return true
        }

        if (event.actionMasked == MotionEvent.ACTION_CANCEL ||
            event.actionMasked == MotionEvent.ACTION_UP) {
            timelineScaleGestureActive = false
        }
        return super.dispatchTouchEvent(event)
    }

    private fun timelinePixelsPerSecond(): Float {
        if (!timelinePxPerSec.isFinite() || timelinePxPerSec <= 0f) {
            timelinePxPerSec = dp(22).toFloat().coerceAtLeast(1f)
        }
        return timelinePxPerSec
    }

    private fun fitTimelineToProject() {
        val totalMs = totalOutputDurationMs()
        if (totalMs <= 0L) return
        val frameWidth = timelineTracksFrame.width.takeIf { it > 0 }
            ?: resources.displayMetrics.widthPixels
        val availableWidth = (frameWidth - timelineHeaderGutterWidthPx() - dp(3)).coerceAtLeast(dp(80))
        timelinePxPerSec = TimelineScaleMath.fitPixelsPerSecond(
            durationMs = totalMs,
            availableWidthPx = availableWidth,
            minimumPxPerSecond = dp(11).toFloat(),
            maximumPxPerSecond = dp(176).toFloat()
        )
        // Preserve the current time under the fixed center playhead after fitting.
        val fitScrollX = TimelineScaleMath.pixelsForTime(
            timelinePlayheadMs.coerceIn(0L, totalMs), timelinePixelsPerSecond()
        )
        pendingTimelineScrollX = fitScrollX
        timelineHorizontalScrollX = fitScrollX
        refreshTimelineTracks()
        updateTimelinePlayheadLabel()
    }

    /**
     * Horizontal space consumed before a lane's scrolling content: the 84dp
     * ruler/media header plus the row's 3dp leading inset. Keep this in sync
     * with makeLane(), otherwise the fixed playhead maps to the wrong project time.
     */
    private fun timelineHeaderGutterWidthPx(): Int = dp(87)

    /** Width of the leading blank time-zero spacer inside each horizontally scrolling lane. */
    private fun timelineFrameWidth(viewportWidth: Int): Int {
        val gutterPx = timelineHeaderGutterWidthPx()
        return timelineTracksFrame.width.takeIf { it > 0 }
            ?: if (viewportWidth > 0) viewportWidth + gutterPx
            else resources.displayMetrics.widthPixels
    }

    private fun timelineOriginSpacerWidth(viewportWidth: Int = 0): Int =
        TimelineScaleMath.originSpacerWidthForCenteredPlayhead(
            timelineFrameWidth(viewportWidth),
            timelineHeaderGutterWidthPx()
        )

    private fun timelineEndSpacerWidth(viewportWidth: Int = 0): Int =
        TimelineScaleMath.endSpacerWidthForCenteredPlayhead(
            timelineFrameWidth(viewportWidth),
            timelineHeaderGutterWidthPx(),
            dp(3)
        )

    private fun ensureTrimHandlesReachable(index: Int): Boolean {
        val clip = clips.getOrNull(index) ?: return false
        val durationMs = ClipSpeedMath.timelineDurationMs(clip.endMs - clip.startMs, clip.speed)
        if (durationMs <= 0L) return false

        // Selection may scroll the clip into view, but must never change px/sec.
        // Always bring the selected clip into view, even when the current zoom already
        // provides sufficient handle width. This avoids selection on a far-right clip
        // leaving both edges outside the viewport.
        val projectOffsetMs = clip.timelineStartMs
        val clipStartPx = TimelineScaleMath.pixelsForTime(projectOffsetMs, timelinePixelsPerSecond())
        pendingTimelineScrollX = (clipStartPx - dp(8)).coerceAtLeast(0)
        timelineHorizontalScrollX = pendingTimelineScrollX ?: 0
        return pendingTimelineScrollX != null
    }

    private fun adjustTimelineZoom(factor: Float) {
        val newPxPerSec = (timelinePixelsPerSecond() * factor)
            .coerceIn(dp(11).toFloat(), dp(720).toFloat())
        if (newPxPerSec == timelinePixelsPerSecond()) return
        timelinePxPerSec = newPxPerSec
        refreshTimelineTracks()
        status.text = "Zaman çizelgesi yakınlaştırıldı."
    }

    private fun updateTimelinePlayheadDisplay() {
        if (!::timelinePlayheadLabel.isInitialized) return
        timelinePlayheadMs = timelinePlayheadMs.coerceIn(0L, totalOutputDurationMs())
        updateTimelinePlayheadLabel()
    }

    private fun updateTimelinePlayheadLabel() {
        if (!::timelinePlayheadLabel.isInitialized) return
        val totalMs = totalOutputDurationMs()
        val projectPositionMs = timelinePlayheadMs.coerceIn(0L, totalMs)
        timelinePlayheadLabel.text =
            "${formatMs(projectPositionMs)} / ${formatMs(totalMs)}"
        updateTimelinePlayheadLine()
        updatePreviewTextOverlay()
    }

    private fun updateTimelinePlayButtonState() {
        if (!::timelinePlayButton.isInitialized) return
        val current = timelineCompositionPlayer
        val playing = current?.isPlaying == true || current?.playWhenReady == true
        if (playing) timelinePlaybackState = TimelinePlaybackState.PLAYING
        else if (timelinePlaybackState != TimelinePlaybackState.ERROR) {
            timelinePlaybackState = TimelinePlaybackState.PAUSED
        }
        timelinePlayButton.text = when (timelinePlaybackState) {
            TimelinePlaybackState.PLAYING -> "Ⅱ"
            TimelinePlaybackState.PAUSED -> "▶"
            TimelinePlaybackState.ERROR -> "!"
        }
        timelinePlayButton.contentDescription = when (timelinePlaybackState) {
            TimelinePlaybackState.PLAYING -> "Önizlemeyi duraklat"
            TimelinePlaybackState.PAUSED -> "Önizlemeyi oynat"
            TimelinePlaybackState.ERROR -> "Oynatıcı hatası: " + timelinePreviewError.orEmpty()
        }
    }

    /** Emits real playback/timeline geometry only when the debug CI test opts in. */
    private fun logTimelineSyncTestSample() {
        val composition = timelineCompositionPlayer
        val firstLane = timelineScrollViews.firstOrNull() ?: return
        if (!::timelinePlayheadLine.isInitialized || firstLane.width <= 0) return

        val playingComposition = composition?.isPlaying == true
        val singleClip = clips.getOrNull(selectedClipIndex)
        val playingSingleClip = previewSingleClip && player?.isPlaying == true && singleClip != null
        val playerPositionMs = when {
            composition != null -> composition.currentPosition
            playingSingleClip -> {
                val clip = singleClip!!
                clip.timelineStartMs + player!!.currentPosition.coerceIn(0L, clip.durationMs)
            }
            else -> timelinePlayheadMs
        }
        val pixelsPerSecond = timelinePixelsPerSecond()
        val playheadLocation = IntArray(2)
        val playButtonLocation = IntArray(2)
        val frameLocation = IntArray(2)
        timelinePlayheadLine.getLocationOnScreen(playheadLocation)
        timelinePlayButton.getLocationOnScreen(playButtonLocation)
        timelineTracksFrame.getLocationOnScreen(frameLocation)
        val playheadCenterPx = playheadLocation[0] + timelinePlayheadLine.width / 2
        val playButtonCenterPx = playButtonLocation[0] + timelinePlayButton.width / 2
        val expectedCenterPx = frameLocation[0] + timelineTracksFrame.width / 2
        val expectedScrollPx = TimelineScaleMath.pixelsForTime(timelinePlayheadMs, pixelsPerSecond)
        val laneScrolls = timelineScrollViews.joinToString(",") { it.scrollX.toString() }
        // The time-zero point is the RIGHT edge of the leading spacer, not its left edge.
        val originRightScreenXs = timelineScrollViews.joinToString(",") { lane ->
            val track = lane.getChildAt(0) as? LinearLayout
            val origin = track?.getChildAt(0)
            if (origin == null) Int.MIN_VALUE.toString()
            else {
                val location = IntArray(2)
                origin.getLocationOnScreen(location)
                (location[0] + origin.width).toString()
            }
        }
        android.util.Log.i(
            "TimelineSyncTest",
            "TIMELINE_SYNC_SAMPLE playing=${playingComposition || playingSingleClip} " +
                "playerMs=$playerPositionMs playheadMs=$timelinePlayheadMs " +
                "scrollXs=$laneScrolls originRightXs=$originRightScreenXs pxPerSecond=$pixelsPerSecond " +
                "expectedScrollPx=$expectedScrollPx playheadCenterPx=$playheadCenterPx " +
                "playButtonCenterPx=$playButtonCenterPx expectedCenterPx=$expectedCenterPx"
        )
    }

    private fun buildFlattenedVideoSequence():EditedMediaItemSequence {
        val builder=EditedMediaItemSequence.Builder(setOf(androidx.media3.common.C.TRACK_TYPE_AUDIO,androidx.media3.common.C.TRACK_TYPE_VIDEO))
        val total=totalOutputDurationMs()
        val boundaries=(videoTimelineSegments().flatMap{listOf(it.timelineStartMs,it.timelineEndMs)}+listOf(0L,total)).distinct().sorted()
        for(i in 0 until (boundaries.size-1).coerceAtLeast(0)){
            val from=boundaries[i];val to=boundaries[i+1];val span=(to-from).coerceAtLeast(0L)
            if(span<=0L)continue
            val active=activeClipIndexAt(from)
            if(active==null)builder.addGap(span*1000L)
            else{
                val clip=clips[active]
                val localStart = ClipSpeedMath.sourceOffsetMs(
                    (from - clip.timelineStartMs).coerceAtLeast(0L), clip.speed
                )
                val localEnd = ClipSpeedMath.sourceOffsetMs(
                    (to - clip.timelineStartMs).coerceAtLeast(0L), clip.speed
                ).coerceAtMost((clip.endMs - clip.startMs).coerceAtLeast(0L))
                if (localEnd > localStart) builder.addItem(buildClipSegment(clip, localStart, localEnd))
            }
        }
        return builder.build()
    }
    private fun buildOverlayVideoSequence(
        overlay: OverlayClip,
        outputDurationMs: Long
    ): EditedMediaItemSequence {
        val safeStartMs = overlay.timelineStartMs.coerceIn(0L, outputDurationMs)
        val safeDurationMs = overlay.durationMs.coerceAtLeast(1L)
            .coerceAtMost(outputDurationMs - safeStartMs)
        require(safeDurationMs > 0L) { "Bindirme süresi zaman çizelgesinin dışında." }
        val sequenceBuilder = EditedMediaItemSequence.Builder(
            setOf(androidx.media3.common.C.TRACK_TYPE_VIDEO)
        )
        if (safeStartMs > 0L) sequenceBuilder.addGap(safeStartMs * 1000L)

        val mediaItem = if (overlay.isImage) {
            MediaItem.Builder()
                .setUri(overlay.uri)
                .setImageDurationMs(safeDurationMs)
                .build()
        } else {
            MediaItem.Builder()
                .setUri(overlay.uri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(overlay.sourceStartMs)
                        .setEndPositionMs(overlay.sourceStartMs + safeDurationMs)
                        .build()
                )
                .build()
        }
        val editedItem = EditedMediaItem.Builder(mediaItem)
            .apply { if (!overlay.isImage) setRemoveAudio(true) }
            .build()
        sequenceBuilder.addItem(editedItem)

        val trailingGapMs = (outputDurationMs - safeStartMs - safeDurationMs).coerceAtLeast(0L)
        if (trailingGapMs > 0L) sequenceBuilder.addGap(trailingGapMs * 1000L)
        return sequenceBuilder.build()
    }

    private inner class OverlayVideoCompositorSettings(
        private val layers: List<OverlayClip>,
        private val baseVideoInputId: Int
    ) : VideoCompositorSettings {
        override fun getOutputSize(
            inputSizes: MutableList<androidx.media3.common.util.Size>
        ): androidx.media3.common.util.Size {
            return inputSizes.getOrNull(baseVideoInputId)
                ?: inputSizes.lastOrNull()
                ?: androidx.media3.common.util.Size(1080, 1920)
        }

        override fun getOverlaySettings(inputId: Int, presentationTimeUs: Long): OverlaySettings {
            val layer = layers.getOrNull(inputId) ?: return StaticOverlaySettings.Builder().build()
            val timeMs = presentationTimeUs / 1_000L
            val visible = timeMs >= layer.timelineStartMs &&
                timeMs < layer.timelineStartMs + layer.durationMs
            val state = overlayStateAtTime(layer, timeMs)
            return StaticOverlaySettings.Builder()
                .setAlphaScale(if (visible) state.opacity else 0f)
                .setScale(state.scale, state.scale)
                .setBackgroundFrameAnchor(
                    state.xFraction * 2f - 1f,
                    1f - state.yFraction * 2f
                )
                .setRotationDegrees(state.rotationDegrees)
                .build()
        }
    }

    private fun buildVideoCompositionSequences(): List<EditedMediaItemSequence> {
        val outputDurationMs = totalOutputDurationMs().coerceAtLeast(1L)
        val orderedOverlays = overlayClips.sortedByDescending { it.id }
        val overlaySequences = orderedOverlays.map { overlay ->
            buildOverlayVideoSequence(overlay, outputDurationMs)
        }
        // Media3 uses the first sequence as a foreground input. Keep the main edited
        // video last so it remains the full-frame background under every bindirme.
        return overlaySequences + buildFlattenedVideoSequence()
    }

    private fun overlayBaseTransformState(overlay: OverlayClip) = OverlayTransformState(
        scale = overlay.scale,
        xFraction = overlay.xFraction,
        yFraction = overlay.yFraction,
        rotationDegrees = overlay.rotationDegrees,
        opacity = overlay.opacity
    )

    private fun overlayLocalTimeMs(overlay: OverlayClip): Long =
        (timelinePlayheadMs - overlay.timelineStartMs).coerceIn(0L, overlay.durationMs.coerceAtLeast(0L))

    private fun overlayStateAtTime(overlay: OverlayClip, projectTimeMs: Long): OverlayTransformState {
        val localTimeMs = (projectTimeMs - overlay.timelineStartMs)
            .coerceIn(0L, overlay.durationMs.coerceAtLeast(0L))
        return OverlayKeyframeMath.stateAt(
            overlayBaseTransformState(overlay),
            overlay.keyframes,
            localTimeMs
        )
    }

    private fun updateOverlayTransformAtPlayhead(
        overlay: OverlayClip,
        updatedState: OverlayTransformState
    ) {
        val normalized = OverlayTransformState.normalize(updatedState)
        if (overlay.keyframes.isEmpty()) {
            overlay.scale = normalized.scale
            overlay.xFraction = normalized.xFraction
            overlay.yFraction = normalized.yFraction
            overlay.rotationDegrees = normalized.rotationDegrees
            overlay.opacity = normalized.opacity
            return
        }
        val localTimeMs = overlayLocalTimeMs(overlay)
        val frames = OverlayKeyframeMath.upsert(
            overlay.keyframes,
            OverlayTransformKeyframe(localTimeMs, normalized)
        )
        overlay.keyframes.clear()
        overlay.keyframes.addAll(frames)
    }

    private fun addOverlayKeyframeAtPlayhead() {
        val overlay = selectedOverlay() ?: run {
            status.text = "Önce bir bindirme katmanı seç."
            return
        }
        val localTimeMs = overlayLocalTimeMs(overlay)
        if (overlay.keyframes.isEmpty() && localTimeMs > 0L) {
            overlay.keyframes.add(OverlayTransformKeyframe(0L, overlayBaseTransformState(overlay)))
        }
        val currentState = overlayStateAtTime(overlay, timelinePlayheadMs)
        val frames = OverlayKeyframeMath.upsert(
            overlay.keyframes,
            OverlayTransformKeyframe(localTimeMs, currentState)
        )
        overlay.keyframes.clear()
        overlay.keyframes.addAll(frames)
        loadOverlayControls()
        refreshTimelineTracks()
        rebuildTimelinePreviewForOverlayEdit()
        status.text = "Anahtar kare eklendi: ${formatTimelineMs(timelinePlayheadMs)}."
    }

    private fun removeOverlayKeyframeAtPlayhead() {
        val overlay = selectedOverlay() ?: run {
            status.text = "Önce bir bindirme katmanı seç."
            return
        }
        val index = OverlayKeyframeMath.nearestIndex(
            overlay.keyframes, overlayLocalTimeMs(overlay), toleranceMs = 250L
        )
        if (index == null) {
            status.text = "Oynatma noktasına yakın bir anahtar kare yok."
            return
        }
        overlay.keyframes.removeAt(index)
        loadOverlayControls()
        refreshTimelineTracks()
        rebuildTimelinePreviewForOverlayEdit()
        status.text = "Anahtar kare silindi."
    }

    private fun buildOverlayCompositorSettings(): VideoCompositorSettings =
        OverlayVideoCompositorSettings(overlayClips.sortedByDescending { it.id }, overlayClips.size)

    private fun installVideoFilterListeners() {
        fun attach(seekBar: SeekBar, onValue: (Int) -> Unit) {
            seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser || updatingVideoFilterControls) return
                    onValue(progress)
                    updateVideoFilterLabels()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    pauseActivePreview()
                }
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    refreshColorGradePreview()
                }
            })
        }
        attach(filterBrightnessSeek) { filterBrightness = it - 100 }
        attach(filterContrastSeek) { filterContrast = it - 100 }
        attach(filterSaturationSeek) { filterSaturation = it - 100 }
        attach(filterHueSeek) { filterHue = it - 180 }
    }

    private fun updateVideoFilterLabels() {
        if (!::videoFilterSummary.isInitialized) return
        videoFilterSummary.text = "Görünüm: ${selectedVideoFilter.title}"
        filterBrightnessLabel.text = "Parlaklık: $filterBrightness"
        filterContrastLabel.text = "Kontrast: $filterContrast"
        filterSaturationLabel.text = "Doygunluk: $filterSaturation"
        filterHueLabel.text = "Renk tonu: ${if (filterHue > 0) "+" else ""}$filterHue°"
    }

    private fun loadVideoFilterControls() {
        if (!::filterBrightnessSeek.isInitialized) return
        updatingVideoFilterControls = true
        try {
            filterBrightnessSeek.progress = (filterBrightness + 100).coerceIn(0, 200)
            filterContrastSeek.progress = (filterContrast + 100).coerceIn(0, 200)
            filterSaturationSeek.progress = (filterSaturation + 100).coerceIn(0, 200)
            filterHueSeek.progress = (filterHue + 180).coerceIn(0, 360)
            updateVideoFilterLabels()
            videoFilterButtons.forEach { (preset, button) ->
                val selected = preset == selectedVideoFilter
                button.background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(if (selected) Color.rgb(39, 107, 103) else Color.rgb(27, 43, 59))
                    cornerRadius = dp(9).toFloat()
                    setStroke(dp(if (selected) 2 else 1),
                        if (selected) Color.rgb(57, 220, 190) else Color.rgb(47, 66, 84))
                }
                button.setTextColor(if (selected) Color.WHITE else Color.rgb(205, 216, 228))
            }
        } finally {
            updatingVideoFilterControls = false
        }
    }

    private fun applyVideoFilterPreset(preset: VideoFilterPreset) {
        selectedVideoFilter = preset
        when (preset) {
            VideoFilterPreset.ORIGINAL, VideoFilterPreset.MONOCHROME, VideoFilterPreset.NEGATIVE -> {
                filterBrightness = 0
                filterContrast = 0
                filterSaturation = 0
                filterHue = 0
            }
            VideoFilterPreset.VIVID -> {
                filterBrightness = 2
                filterContrast = 12
                filterSaturation = 28
                filterHue = 0
            }
            VideoFilterPreset.CINEMATIC -> {
                filterBrightness = -3
                filterContrast = 18
                filterSaturation = -18
                filterHue = 0
            }
            VideoFilterPreset.WARM -> {
                filterBrightness = 2
                filterContrast = 4
                filterSaturation = 10
                filterHue = -8
            }
            VideoFilterPreset.COOL -> {
                filterBrightness = 0
                filterContrast = 4
                filterSaturation = 6
                filterHue = 12
            }
        }
        loadVideoFilterControls()
        refreshColorGradePreview()
        status.text = "Görünüm güncellendi: ${preset.title}."
    }

    private fun buildEditorVideoEffects(): List<androidx.media3.common.Effect> {
        val effects = mutableListOf<androidx.media3.common.Effect>()
        when (selectedVideoFilter) {
            VideoFilterPreset.MONOCHROME -> effects.add(RgbFilter.createGrayscaleFilter())
            VideoFilterPreset.NEGATIVE -> effects.add(RgbFilter.createInvertedFilter())
            else -> Unit
        }
        if (filterBrightness != 0) effects.add(Brightness(filterBrightness / 100f))
        if (filterContrast != 0) effects.add(Contrast(filterContrast / 100f))
        if (filterSaturation != 0 || filterHue != 0) {
            effects.add(
                HslAdjustment.Builder()
                    .adjustHue(filterHue.toFloat())
                    .adjustSaturation(filterSaturation.toFloat())
                    .build()
            )
        }
        return effects
    }

    private fun refreshColorGradePreview() {
        if (clips.isEmpty()) return
        val wasPlaying = timelineCompositionPlayer?.playWhenReady == true
        val position = timelinePlayheadMs.coerceIn(0L, totalOutputDurationMs())
        timelineCompositionPlayer?.release()
        timelineCompositionPlayer = null
        timelinePreviewLoaded = false
        ensureTimelinePreviewLoaded()
        timelineCompositionPlayer?.seekTo(position)
        timelineCompositionPlayer?.playWhenReady = wasPlaying
        updateTimelinePlayButtonState()
    }

    private fun buildVideoTimelineComposition():Composition {
        val sequences = buildVideoCompositionSequences()
        val builder = Composition.Builder(sequences)
        if (overlayClips.isNotEmpty()) {
            builder.setVideoCompositorSettings(buildOverlayCompositorSettings())
        }
        val colorEffects = buildEditorVideoEffects().toMutableList()
        exportCanvasPreset.aspectRatio?.let { ratio ->
            colorEffects.add(Presentation.createForAspectRatio(
                ratio, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
            ))
        }
        if (colorEffects.isNotEmpty()) {
            builder.setEffects(Effects(emptyList(), colorEffects))
        }
        return builder.build()
    }

    private fun pauseActivePreview(){
        player?.playWhenReady=false
        timelineCompositionPlayer?.playWhenReady=false
    }
    private fun showSingleClipPreview(){
        hidePreviewFrameOverlay()
        timelineCompositionPlayer?.release()
        timelineCompositionPlayer=null
        timelinePreviewLoaded=false
        if(::playerView.isInitialized)playerView.player=player
    }
    private fun refreshTimelineSourceAudio(){
        if(!timelinePreviewLoaded)return
        val wasPlaying=timelineCompositionPlayer?.playWhenReady==true
        val position=timelinePlayheadMs
        timelineCompositionPlayer?.release();timelineCompositionPlayer=null;timelinePreviewLoaded=false
        ensureTimelinePreviewLoaded()
        timelineCompositionPlayer?.seekTo(position)
        timelineCompositionPlayer?.playWhenReady=wasPlaying
    }
    private fun ensureTimelinePreviewLoaded(){
        if(timelinePreviewLoaded&&timelineCompositionPlayer!=null)return
        if(clips.isEmpty())return
        if (timelineSyncTestEnabled) android.util.Log.i("TimelineSyncTest",
            "TIMELINE_PREVIEW_INIT clips=${clips.size} playheadMs=$timelinePlayheadMs playerViewInitialized=${::playerView.isInitialized}")
        previewSingleClip=false
        pauseActivePreview()
        runCatching{timelineCompositionPlayer?.release()}
        timelineCompositionPlayer=null
        try{
            val compositionPlayerBuilder = CompositionPlayer.Builder(this)
            // CompositionPlayer defaults to SingleInputVideoGraph. Use the multi-input
            // graph only when overlays are present, otherwise preserve the simple path.
            if (overlayClips.isNotEmpty()) {
                compositionPlayerBuilder.setVideoGraphFactory(MultipleInputVideoGraph.Factory())
            }
            val comp = compositionPlayerBuilder.build()
            if (timelineSyncTestEnabled) android.util.Log.i("TimelineSyncTest", "TIMELINE_PREVIEW_BUILDER_OK")
            comp.addListener(object:androidx.media3.common.Player.Listener{
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) hidePreviewFrameOverlay()
                }
                override fun onVideoSizeChanged(videoSize:androidx.media3.common.VideoSize){
                    playerView.post{updatePreviewCanvasSize()}
                }
                override fun onPlayerError(error:androidx.media3.common.PlaybackException){
                    android.util.Log.e("MathMotorTimeline","Composition player failed",error)
                    timelinePreviewError = error.message ?: "Media3 oynatma hatası"
                    timelinePlaybackState = TimelinePlaybackState.ERROR
                    updateTimelinePlayButtonState()
                    status.text = "Oynatıcı hatası: " + timelinePreviewError
                }
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == androidx.media3.common.Player.STATE_ENDED) {
                        timelinePlayheadMs = totalOutputDurationMs()
                        timelinePlaybackState = TimelinePlaybackState.PAUSED
                        timelineCompositionPlayer?.playWhenReady = false
                        requestPreviewFrameAt((totalOutputDurationMs() - 1L).coerceAtLeast(0L))
                        scrollTimelineToPlayhead()
                        updateTimelinePlayheadLabel()
                        updateTimelinePlayButtonState()
                    }
                }
            })
            comp.setComposition(buildVideoTimelineComposition())
            if (timelineSyncTestEnabled) android.util.Log.i("TimelineSyncTest", "TIMELINE_PREVIEW_COMPOSITION_OK")
            comp.prepare()
            comp.playWhenReady=false
            timelineCompositionPlayer=comp
            if (timelineSyncTestEnabled) android.util.Log.i("TimelineSyncTest", "TIMELINE_PREVIEW_ASSIGNED")
            playerView.player=comp
            timelinePreviewLoaded=true
            if (timelineSyncTestEnabled) android.util.Log.i("TimelineSyncTest", "TIMELINE_PREVIEW_READY")
        }catch(error:Throwable){
            android.util.Log.e("MathMotorTimeline", "Timeline preview setup failed", error)
            if (timelineSyncTestEnabled) android.util.Log.e("TimelineSyncTest",
                "TIMELINE_PREVIEW_FAILED type=${error.javaClass.name} message=${error.message}", error)
            timelineCompositionPlayer?.release();timelineCompositionPlayer=null;timelinePreviewLoaded=false
            playerView.player=player
            status.text="Zaman çizelgesi önizlemesi kurulamadı: ${error.message ?: error.javaClass.simpleName}"
        }
    }
    /**
     * Coalesces rapid HorizontalScrollView updates before asking Media3 to decode.
     * The latest requested time is retained, so a stopped drag always lands exactly
     * on the frame represented by the fixed playhead.
     */
    private fun scheduleTimelineScrubPreviewSeek(targetMs: Long) {
        pendingTimelineScrubPreviewTargetMs = targetMs
        val delayMs = TimelineScaleMath.scrubSeekDelayMs(
            nowMs = SystemClock.uptimeMillis(),
            lastSeekUptimeMs = lastTimelineScrubPreviewSeekUptimeMs,
            // Target roughly 30fps while scrubbing; the newest target replaces stale work.
            minimumIntervalMs = 33L
        )
        timelineHandler.removeCallbacks(timelineScrubPreviewSeekTask)
        timelineHandler.postDelayed(timelineScrubPreviewSeekTask, delayMs)
    }

    private fun seekTimelinePreview(
        requestedMs: Long,
        preservePreviewWhileScrubbing: Boolean = false
    ) {
        timelineHandler.removeCallbacks(timelineScrubPreviewSeekTask)
        pendingTimelineScrubPreviewTargetMs = null
        if(clips.isEmpty())return
        ensureTimelinePreviewLoaded()
        val comp=timelineCompositionPlayer?:return
        val total=totalOutputDurationMs()
        if(total<=0L)return
        val target=requestedMs.coerceIn(0L,total)
        comp.seekTo(target);timelinePlayheadMs=target
        if (comp.isPlaying || comp.playWhenReady) hidePreviewFrameOverlay()
        else requestPreviewFrameAt(
            target,
            keepPreviousFrame = preservePreviewWhileScrubbing
        )
        val active=activeClipIndexAt(target)
        if(active!=null&&selectedClipIndex!=active){
            // Playback/scrubbing can follow another active clip, but it must not
            // transfer the user's explicit selection or reveal trim handles automatically.
            clipHandlesVisible = false
            if (activeEditorPanel == "clip") toggleEditorPanel("clip")
            selectedClipIndex=active;refreshTimelineAfterClipChange();loadTrimControls()
        }
        updateTimelinePlayheadLabel()
    }
    private fun syncTimelinePlayheadFromPlayer(){
        val comp=timelineCompositionPlayer?:return
        if(!timelinePreviewLoaded||clips.isEmpty())return
        timelinePlayheadMs=comp.currentPosition.coerceIn(0L,totalOutputDurationMs())
        val active=activeClipIndexAt(timelinePlayheadMs)
        if(active!=null&&selectedClipIndex!=active){
            // Playback/scrubbing can follow another active clip, but it must not
            // transfer the user's explicit selection or reveal trim handles automatically.
            clipHandlesVisible = false
            if (activeEditorPanel == "clip") toggleEditorPanel("clip")
            selectedClipIndex=active;refreshTimelineAfterClipChange();loadTrimControls()
        }
        scrollTimelineToPlayhead()
        updateTimelinePlayheadLabel()
    }
    private fun syncSingleClipPlayheadFromPlayer(){
        val current=player?:return
        val clip=clips.getOrNull(selectedClipIndex)?:return
        val duration=ClipSpeedMath.timelineDurationMs(clip.endMs-clip.startMs,clip.speed)
        timelinePlayheadMs=(clip.timelineStartMs+current.currentPosition.coerceIn(0L,duration)).coerceIn(0L,totalOutputDurationMs())
        scrollTimelineToPlayhead()
        updateTimelinePlayheadLabel()
    }

    private fun normalizeClipTransform(clip: Clip) {
        val normalized = ClipTransformMath.normalize(
            clip.transformScale,
            clip.transformRotationDegrees,
            clip.transformTranslateX,
            clip.transformTranslateY
        )
        clip.transformScale = normalized.scale
        clip.transformRotationDegrees = normalized.rotationDegrees
        clip.transformTranslateX = normalized.translateX
        clip.transformTranslateY = normalized.translateY
    }

    private fun updateClipTransformTouchAvailability() {
        if (!::clipTransformTouchOverlay.isInitialized) return
        val enabled = clipHandlesVisible &&
            selectedClipIndex in clips.indices &&
            activeEditorPanel == "clip"
        clipTransformTouchOverlay.visibility = if (enabled) View.VISIBLE else View.GONE
        clipTransformTouchOverlay.isClickable = enabled
        if (!enabled) resetPlayerViewTransform()
    }

    private fun resetPlayerViewTransform() {
        if (!::playerView.isInitialized) return
        playerView.pivotX = playerView.width / 2f
        playerView.pivotY = playerView.height / 2f
        playerView.scaleX = 1f
        playerView.scaleY = 1f
        playerView.rotation = 0f
        playerView.translationX = 0f
        playerView.translationY = 0f
        resetPreviewFrameTransform()
    }

    private fun applyTemporaryClipTransform(clip: Clip) {
        normalizeClipTransform(clip)
        if (!::playerView.isInitialized) return
        playerView.pivotX = playerView.width / 2f
        playerView.pivotY = playerView.height / 2f
        playerView.scaleX = clip.transformScale
        playerView.scaleY = clip.transformScale
        playerView.rotation = clip.transformRotationDegrees
        playerView.translationX = clip.transformTranslateX * playerView.width / 2f
        playerView.translationY = clip.transformTranslateY * playerView.height / 2f
        applyPreviewFrameTransform(clip)
    }

    private fun finishClipTransformGesture(
        clipIndex: Int,
        before: ClipEditSnapshot?,
        cancelled: Boolean
    ) {
        val clip = clips.getOrNull(clipIndex)
        val original = before?.clips?.getOrNull(clipIndex)
        if (cancelled && clip != null && original != null) {
            clip.transformScale = original.transformScale
            clip.transformRotationDegrees = original.transformRotationDegrees
            clip.transformTranslateX = original.transformTranslateX
            clip.transformTranslateY = original.transformTranslateY
        }
        clip?.let(::normalizeClipTransform)
        resetPlayerViewTransform()
        if (!cancelled && before != null && before.clips != clips) {
            pushClipUndoSnapshot(before)
        }
        if (clip != null) {
            timelinePreviewLoaded = false
            previewSingleClip = false
            ensureTimelinePreviewLoaded()
            seekTimelinePreview(timelinePlayheadMs)
            status.text = if (cancelled) {
                "Video dönüşümü geri alındı."
            } else {
                "Video konumu, boyutu ve açısı bu klibe kaydedildi."
            }
        }
        updateClipTransformTouchAvailability()
    }

    private fun installClipTransformTouch() {
        var activeIndex = -1
        var beforeSnapshot: ClipEditSnapshot? = null
        var downRawX = 0f
        var downRawY = 0f
        var baseScale = 1f
        var baseRotation = 0f
        var baseTranslateX = 0f
        var baseTranslateY = 0f
        var twoFingerStartDistance = 0f
        var twoFingerStartAngle = 0f
        var twoFingerStartCenterX = 0f
        var twoFingerStartCenterY = 0f
        var twoFingerBaseScale = 1f
        var twoFingerBaseRotation = 0f
        var twoFingerBaseTranslateX = 0f
        var twoFingerBaseTranslateY = 0f

        clipTransformTouchOverlay.setOnTouchListener { view, event ->
            val clip = clips.getOrNull(activeIndex)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (!clipHandlesVisible || activeEditorPanel != "clip" ||
                        selectedClipIndex !in clips.indices
                    ) return@setOnTouchListener false

                    activeIndex = selectedClipIndex
                    val selected = clips[activeIndex]
                    beforeSnapshot = captureClipEditSnapshot().copy(previewSingleClip = false)
                    if (!timelinePreviewLoaded || timelineCompositionPlayer == null ||
                        playerView.player !== timelineCompositionPlayer
                    ) {
                        timelinePreviewLoaded = false
                        previewSingleClip = false
                        ensureTimelinePreviewLoaded()
                        seekTimelinePreview(timelinePlayheadMs)
                    }
                    player?.playWhenReady = false
                    timelineCompositionPlayer?.playWhenReady = false
                    baseScale = selected.transformScale
                    baseRotation = selected.transformRotationDegrees
                    baseTranslateX = selected.transformTranslateX
                    baseTranslateY = selected.transformTranslateY
                    downRawX = event.rawX
                    downRawY = event.rawY
                    twoFingerStartDistance = 0f
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                    true
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (clip == null || event.pointerCount < 2) return@setOnTouchListener true
                    val dx = event.getX(0) - event.getX(1)
                    val dy = event.getY(0) - event.getY(1)
                    twoFingerStartDistance = kotlin.math.hypot(dx.toDouble(), dy.toDouble())
                        .toFloat().coerceAtLeast(1f)
                    twoFingerStartAngle = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
                    twoFingerStartCenterX = (event.getX(0) + event.getX(1)) / 2f
                    twoFingerStartCenterY = (event.getY(0) + event.getY(1)) / 2f
                    twoFingerBaseScale = clip.transformScale
                    twoFingerBaseRotation = clip.transformRotationDegrees
                    twoFingerBaseTranslateX = clip.transformTranslateX
                    twoFingerBaseTranslateY = clip.transformTranslateY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (clip == null) return@setOnTouchListener true
                    if (event.pointerCount >= 2 && twoFingerStartDistance > 0f) {
                        val dx = event.getX(0) - event.getX(1)
                        val dy = event.getY(0) - event.getY(1)
                        val distance = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
                        val angle = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
                        val centerX = (event.getX(0) + event.getX(1)) / 2f
                        val centerY = (event.getY(0) + event.getY(1)) / 2f
                        val angleDelta = ((angle - twoFingerStartAngle + 180f) % 360f + 360f) % 360f - 180f
                        clip.transformScale = (twoFingerBaseScale * distance / twoFingerStartDistance)
                            .coerceIn(0.25f, 4f)
                        clip.transformRotationDegrees = twoFingerBaseRotation + angleDelta
                        clip.transformTranslateX = (
                            twoFingerBaseTranslateX + (centerX - twoFingerStartCenterX) * 2f /
                                view.width.coerceAtLeast(1)
                            ).coerceIn(-2f, 2f)
                        clip.transformTranslateY = (
                            twoFingerBaseTranslateY + (centerY - twoFingerStartCenterY) * 2f /
                                view.height.coerceAtLeast(1)
                            ).coerceIn(-2f, 2f)
                    } else if (event.pointerCount == 1) {
                        clip.transformTranslateX = (
                            baseTranslateX + (event.rawX - downRawX) * 2f /
                                view.width.coerceAtLeast(1)
                            ).coerceIn(-2f, 2f)
                        clip.transformTranslateY = (
                            baseTranslateY + (event.rawY - downRawY) * 2f /
                                view.height.coerceAtLeast(1)
                            ).coerceIn(-2f, 2f)
                    }
                    applyTemporaryClipTransform(clip)
                    true
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    if (clip != null && event.pointerCount >= 2) {
                        val remainingIndex = if (event.actionIndex == 0) 1 else 0
                        val location = IntArray(2)
                        view.getLocationOnScreen(location)
                        downRawX = location[0] + event.getX(remainingIndex)
                        downRawY = location[1] + event.getY(remainingIndex)
                        baseTranslateX = clip.transformTranslateX
                        baseTranslateY = clip.transformTranslateY
                        baseScale = clip.transformScale
                        baseRotation = clip.transformRotationDegrees
                        twoFingerStartDistance = 0f
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                    finishClipTransformGesture(activeIndex, beforeSnapshot, cancelled = false)
                    activeIndex = -1
                    beforeSnapshot = null
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                    finishClipTransformGesture(activeIndex, beforeSnapshot, cancelled = true)
                    activeIndex = -1
                    beforeSnapshot = null
                    true
                }
                else -> true
            }
        }
    }

    // A track tap is selection only: switch the bottom contextual dock and keep
    // the timeline touch surface unobstructed until the user chooses a tool.
    private fun selectTimelineClip(index:Int){
        if(index !in clips.indices)return
        clipHandlesVisible = true
        timelinePreviewLoaded=false;previewSingleClip=false
        selectedClipIndex=index;timelinePlayheadMs=clips[index].timelineStartMs
        ensureTrimHandlesReachable(index)
        val clip=clips[index]
        refreshTimelineAfterClipChange();loadTrimControls()
        ensureTimelinePreviewLoaded();seekTimelinePreview(timelinePlayheadMs)
        // Selecting a filmstrip changes only the bottom contextual tool row.
        // The expanded trim/settings sheet is opened only after an explicit tool tap,
        // leaving the selected strip unobstructed for immediate long-press dragging.
        enterClipTools()
        updateClipTransformTouchAvailability()
        status.text="Seçili klip: ${clip.title}. Alt araç satırından bir işlem seçebilir veya şeridi basılı tutup sürükleyebilirsin."
        updateTimelinePlayheadLabel()
    }

    private fun clearTimelineClipSelection() {
        if (!clipHandlesVisible && selectedClipIndex < 0) return
        clipHandlesVisible = false
        selectedClipIndex = -1
        if (activeEditorPanel == "clip") toggleEditorPanel("clip")
        updateClipTransformTouchAvailability()
        refreshTimelineTracks()
        updateTimelinePlayheadLabel()
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

        refreshTimelineAfterClipChange()
        loadTrimControls()
        updateTimelinePlayheadDisplay()

        if (clips.isEmpty()) {
            previewSingleClip = false
            player?.clearMediaItems()
        } else {
            // Always restore through the shared composition so per-clip effects
            // remain visible after undo/redo as well as in exported output.
            previewSingleClip = false
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
        layer.animationPreset = textAnimationPreset
        layer.animationDurationMs = textAnimationDurationMs.coerceIn(100L, 1_500L)
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
            val title = layer.text.trim().ifBlank {
                if (layer.kind == TextLayerKind.STICKER) "Çıkartma ${index + 1}" else "Yazı ${index + 1}"
            }.take(14)
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

    private fun addSticker(glyph: String) {
        if (clips.isEmpty()) {
            status.text = "Çıkartma eklemeden önce bir video seç."
            return
        }
        if (textLayers.size >= 12) {
            status.text = "En fazla 12 metin ve çıkartma katmanı ekleyebilirsin."
            return
        }
        syncSelectedTextLayerFromControls()
        val total = totalOutputDurationMs()
        val start = timelinePlayheadMs.coerceIn(0L, (total - 1L).coerceAtLeast(0L))
        val layer = TextLayer(
            id = nextTextLayerId++,
            text = glyph,
            startMs = start,
            endMs = total,
            timingCustomized = true,
            position = TextPosition.CENTER,
            sizeSp = 48,
            colorPreset = TextColorPreset.WHITE,
            isBold = true,
            xFraction = 0.5f,
            yFraction = 0.5f,
            kind = TextLayerKind.STICKER
        )
        textLayers.add(layer)
        selectedTextLayerId = layer.id
        loadTextLayerIntoControls(layer)
        refreshTextLayerList()
        updatePreviewTextOverlay()
        refreshTimelineTracks()
        status.text = "Çıkartma eklendi. Zaman çizelgesinde bağımsız katman olarak taşıyabilir ve süresini değiştirebilirsin."
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

    /** Text-track selection mirrors video and overlay: selection changes only the dock. */
    private fun enterTextTools() {
        if (!::editorDock.isInitialized || !::editorPanelScroll.isInitialized) return
        clipToolsMode = true
        activeClipTool = null
        activeEditorPanel = null
        updateClipTransformTouchAvailability()
        updateOverlayTransformTouchAvailability()
        textOverlayEditingEnabled = false
        updateTextOverlayEditToggle()
        editorPanels.values.forEach { it.visibility = View.GONE }
        editorPanelScroll.animate().cancel()
        editorPanelScroll.visibility = View.GONE
        editorPanelScroll.translationY = 0f
        editorPanelScroll.alpha = 1f
        editorDock.removeAllViews()
        val tools = listOf(
            Triple("‹", "Geri", "Ana araçlara dön"),
            Triple("T", "Düzenle", "Seçili metin katmanını düzenle"),
            Triple("×", "Sil", "Seçili metin katmanını kaldır")
        )
        tools.forEach { (symbol, title, description) ->
            val action: () -> Unit = when (title) {
                "Geri" -> ({ exitClipTools() })
                "Düzenle" -> ({ showEditorPanel("text") })
                "Sil" -> ({ selectedTextLayerId?.let { removeTextLayer(it) } })
                else -> ({})
            }
            editorDock.addView(makeClipDockTool(symbol, title, description, action),
                LinearLayout.LayoutParams(dp(60), dp(58)))
        }
        editorDockScroll.scrollTo(0, 0)
        logEditorPanelGeometry("text-tools")
    }

    /** Audio-track selection mirrors every other timeline track. */
    private fun enterAudioTools() {
        if (!::editorDock.isInitialized || !::editorPanelScroll.isInitialized) return
        clipToolsMode = true
        activeClipTool = null
        activeEditorPanel = null
        updateClipTransformTouchAvailability()
        updateOverlayTransformTouchAvailability()
        textOverlayEditingEnabled = false
        updateTextOverlayEditToggle()
        editorPanels.values.forEach { it.visibility = View.GONE }
        editorPanelScroll.animate().cancel()
        editorPanelScroll.visibility = View.GONE
        editorPanelScroll.translationY = 0f
        editorPanelScroll.alpha = 1f
        editorDock.removeAllViews()
        val tools = listOf(
            Triple("‹", "Geri", "Ana araçlara dön"),
            Triple("♫", "Düzenle", "Seçili ses katmanını düzenle")
        )
        tools.forEach { (symbol, title, description) ->
            val action: () -> Unit = when (title) {
                "Geri" -> ({ exitClipTools() })
                "Düzenle" -> ({ showEditorPanel("audio") })
                else -> ({})
            }
            editorDock.addView(makeClipDockTool(symbol, title, description, action),
                LinearLayout.LayoutParams(dp(60), dp(58)))
        }
        editorDockScroll.scrollTo(0, 0)
        logEditorPanelGeometry("audio-tools")
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
        enterTextTools()
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
            textAnimationPreset = layer?.animationPreset ?: TextAnimationPreset.NONE
            textAnimationDurationMs = layer?.animationDurationMs?.coerceIn(100L, 1_500L) ?: 350L
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
            textAnimationGroup.check(
                31 + TextAnimationPreset.values().indexOf(textAnimationPreset).coerceAtLeast(0)
            )
            textAnimationDurationSeek.progress = (textAnimationDurationMs - 100L).toInt().coerceIn(0, 1_400)
            textAnimationDurationLabel.text = "Animasyon süresi: $textAnimationDurationMs ms"
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
            val animationState = TextAnimationMath.stateAt(
                layer.animationPreset,
                timelinePlayheadMs - layer.startMs,
                layer.endMs - timelinePlayheadMs,
                layer.animationDurationMs
            )
            overlay.alpha = animationState.alpha
            overlay.scaleX = animationState.scale
            overlay.scaleY = animationState.scale
            if (layer.kind == TextLayerKind.STICKER) {
                overlay.setPadding(0, 0, 0, 0)
                overlay.background = null
                overlay.setShadowLayer(4f, 0f, 2f, Color.BLACK)
            } else {
                overlay.setPadding(dp(10), dp(6), dp(10), dp(6))
                overlay.background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.argb(210, 0, 0, 0))
                    cornerRadius = dp(8).toFloat()
                }
                overlay.setShadowLayer(2f, 1f, 1f, Color.BLACK)
            }
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
            overlay.translationX = (layer.xFraction.coerceIn(0f, 1f) - 0.5f) * 2f * maxX +
                animationState.offsetXFraction * videoWidth
            overlay.translationY = (layer.yFraction.coerceIn(0f, 1f) - 0.5f) * 2f * maxY +
                animationState.offsetYFraction * videoHeight
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
            val musicEndMs = (musicStartMs + musicDurationMs).coerceAtMost(totalOutputDurationMs())
            val insideMusic = videoIsPlaying && musicDurationMs > 0L &&
                outputTimeMs >= musicStartMs && outputTimeMs < musicEndMs
            if (!insideMusic) {
                music.playWhenReady = false
            } else {
                val sourceDuration = music.duration
                if (sourceDuration > 0L && sourceDuration != androidx.media3.common.C.TIME_UNSET) {
                    val sourceRange = MusicSourceRangeMath.normalize(MusicSourceRange(musicSourceStartMs, musicSourceEndMs.takeIf { it > 0L } ?: sourceDuration), sourceDuration)
                    val target = MusicSourceRangeMath.sourcePosition(sourceRange, outputTimeMs - musicStartMs, sourceDuration)
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
                val target = soundSourceStartMs + (outputTimeMs - effectStart).coerceAtLeast(0L)
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
            var sourceFrameRate: Double? = null
            val duration = runCatching {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(this, uri)
                    sourceFrameRate = retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE
                    )?.toDoubleOrNull()?.takeIf { it.isFinite() && it in 1.0..240.0 }
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                } finally {
                    retriever.release()
                }
            }.getOrDefault(0L)
            if (sourceFrameRate == null) sourceFrameRate = readFrameRateFromMediaExtractor(uri)
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
                // Append after the furthest VIDEO clip, not after a longer music/text layer.
                // This prevents unrelated tracks from creating a gray gap before a new clip.
                val timelineStartMs = TimelineVideoMath.totalDuration(videoTimelineSegments())
                clips.add(Clip(uri,title,duration,0L,duration,timelineStartMs, frameRate = sourceFrameRate))
                selectedClipIndex = clips.lastIndex
                clipHandlesVisible = false
                showSingleClipPreview()
                player?.setMediaItem(MediaItem.fromUri(uri))
                player?.prepare()
                // Keep the imported clip paused so the user can align the initial frame, ruler and fixed playhead.
                player?.playWhenReady = false
                refreshTimelineAfterClipChange()
                loadTrimControls()
                refreshTimelineTracks()
                // Show the actual frame beneath the fixed playhead immediately,
                // even while the single-clip player is still preparing.
                requestPreviewFrameAt(timelinePlayheadMs)
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
        val overlayArray = JSONArray()
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
                put("kind", layer.kind.name)
                put("animationPreset", layer.animationPreset.key)
                put("animationDurationMs", layer.animationDurationMs)
            })
        }
        clips.forEach { clip ->
            clipArray.put(JSONObject().apply {
                put("uri", clip.uri.toString())
                put("title", clip.title)
                put("durationMs", clip.durationMs)
                put("startMs", clip.startMs)
                put("endMs", clip.endMs)
                put("timelineStartMs",clip.timelineStartMs)
                clip.frameRate?.let { put("frameRate", it) }
                put("transformScale", clip.transformScale)
                put("transformRotationDegrees", clip.transformRotationDegrees)
                put("transformTranslateX", clip.transformTranslateX)
                put("transformTranslateY", clip.transformTranslateY)
                put("coverTimeMs", clip.coverTimeMs ?: JSONObject.NULL)
                put("speed", clip.speed)
            })
        }
        overlayClips.forEach { overlay ->
            overlayArray.put(JSONObject().apply {
                put("id", overlay.id)
                put("uri", overlay.uri.toString())
                put("title", overlay.title)
                put("isImage", overlay.isImage)
                put("sourceDurationMs", overlay.sourceDurationMs)
                put("sourceStartMs", overlay.sourceStartMs)
                put("timelineStartMs", overlay.timelineStartMs)
                put("durationMs", overlay.durationMs)
                put("scale", overlay.scale)
                put("xFraction", overlay.xFraction)
                put("yFraction", overlay.yFraction)
                put("rotationDegrees", overlay.rotationDegrees)
                put("opacity", overlay.opacity)
                put("keyframes", JSONArray().apply {
                    overlay.keyframes.sortedBy { it.timeMs }.forEach { frame ->
                        put(JSONObject().apply {
                            put("timeMs", frame.timeMs)
                            put("scale", frame.state.scale)
                            put("xFraction", frame.state.xFraction)
                            put("yFraction", frame.state.yFraction)
                            put("rotationDegrees", frame.state.rotationDegrees)
                            put("opacity", frame.state.opacity)
                        })
                    }
                })
            })
        }
        return JSONObject().apply {
            put("format", "MathMotorVideoProject")
            put("version", 1)
            put("clips", clipArray)
            put("overlays", overlayArray)
            put("selectedOverlayId", selectedOverlayId ?: JSONObject.NULL)
            put("nextOverlayId", nextOverlayId)
            put("selectedClipIndex", selectedClipIndex)
            put("timelinePlayheadMs", timelinePlayheadMs.coerceIn(0L, totalOutputDurationMs()))
            put("timelinePxPerSec", timelinePixelsPerSecond())
            // Backward compatibility for older project files.
            put("timelineZoom", timelinePixelsPerSecond() / dp(22).toFloat().coerceAtLeast(1f))
            put("timelineLaneOrder", JSONArray().apply {
                timelineLaneOrder.forEach { put(it) }
            })
            // The cached value is authoritative during the short interval between
            // scheduling a scroll restore and the ScrollView applying it.
            val currentScrollX = pendingTimelineScrollX ?: timelineHorizontalScrollX
            put("timelineHorizontalScrollX", currentScrollX)
            put("timelineHorizontalScrollMs", TimelineScaleMath.millisecondsForPixels(
                currentScrollX.toFloat(), timelinePixelsPerSecond()
            ).coerceAtLeast(0L))
            put("musicUri", musicUri?.toString() ?: JSONObject.NULL)
            put("musicTitle", musicTitle ?: JSONObject.NULL)
            put("musicSourceDurationMs", musicSourceDurationMs)
            put("musicSourceStartMs", musicSourceStartMs)
            put("musicSourceEndMs", musicSourceEndMs)
            put("musicStartMs", musicStartMs)
            put("musicDurationMs", musicDurationMs)
            put("soundUri", soundUri?.toString() ?: JSONObject.NULL)
            put("soundTitle", soundTitle ?: JSONObject.NULL)
            put("soundDurationMs", soundDurationMs)
            put("soundSourceDurationMs", soundSourceDurationMs)
            put("soundSourceStartMs", soundSourceStartMs)
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
            put("videoFilterPreset", selectedVideoFilter.key)
            put("filterBrightness", filterBrightness)
            put("filterContrast", filterContrast)
            put("filterSaturation", filterSaturation)
            put("filterHue", filterHue)
            put("sourceAudioVolume", sourceAudioVolume)
            put("musicVolume", musicVolume)
            put("soundVolume", soundVolume)
            put("keepSourceAudio", keepSourceAudio.isChecked)
            put("exportName", exportNameInput.text?.toString().orEmpty())
            put("exportResolutionShortSide", exportResolutionShortSide)
            put("exportCanvasPreset", exportCanvasPreset.key)
        }
    }

    private fun applyProjectJson(json: JSONObject) {
        require(json.optString("format") == "MathMotorVideoProject") { "Bu dosya MathMotorVideo proje dosyası değil." }
        require(json.optInt("version", 0) == 1) { "Bu proje sürümü desteklenmiyor." }
        val loadedClips = mutableListOf<Clip>()
        val clipArray = json.optJSONArray("clips") ?: JSONArray()
        var legacyTimelineCursorMs=0L
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
            val timelineStartMs=if(item.has("timelineStartMs"))item.optLong("timelineStartMs",legacyTimelineCursorMs).coerceAtLeast(0L) else legacyTimelineCursorMs
            val loadedClip = Clip(
                uri = uri,
                title = item.optString("title", "Video klibi"),
                durationMs = duration,
                startMs = start,
                endMs = end,
                timelineStartMs = timelineStartMs,
                frameRate = item.optDouble("frameRate", Double.NaN)
                    .takeIf { it.isFinite() && it in 1.0..240.0 },
                transformScale = item.optDouble("transformScale", 1.0).toFloat(),
                transformRotationDegrees = item.optDouble("transformRotationDegrees", 0.0).toFloat(),
                transformTranslateX = item.optDouble("transformTranslateX", 0.0).toFloat(),
                transformTranslateY = item.optDouble("transformTranslateY", 0.0).toFloat(),
                coverTimeMs = if (item.has("coverTimeMs") && !item.isNull("coverTimeMs")) {
                    item.optLong("coverTimeMs", start).coerceIn(start, (end - 1L).coerceAtLeast(start))
                } else null,
                speed = ClipSpeedMath.normalize(item.optDouble("speed", 1.0).toFloat())
            )
            normalizeClipTransform(loadedClip)
            loadedClips.add(loadedClip)
            legacyTimelineCursorMs=maxOf(
                legacyTimelineCursorMs,
                timelineStartMs + ClipSpeedMath.timelineDurationMs((end - start).coerceAtLeast(0L), loadedClip.speed)
            )
        }
        val loadedOverlays = mutableListOf<OverlayClip>()
        val overlayArray = json.optJSONArray("overlays") ?: JSONArray()
        var maxOverlayId = 0L
        for (i in 0 until overlayArray.length()) {
            val item = overlayArray.getJSONObject(i)
            val uri = Uri.parse(item.getString("uri"))
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) { }
            val isImage = item.optBoolean("isImage", false)
            val sourceDurationMs = item.optLong("sourceDurationMs", if (isImage) 60_000L else 0L)
                .coerceAtLeast(1L)
            val sourceStartMs = item.optLong("sourceStartMs", 0L)
                .coerceIn(0L, sourceDurationMs - 1L)
            val durationMs = item.optLong("durationMs", sourceDurationMs - sourceStartMs)
                .coerceIn(1L, sourceDurationMs - sourceStartMs)
            val overlay = OverlayClip(
                id = item.optLong("id", i + 1L).coerceAtLeast(1L),
                uri = uri,
                title = item.optString("title", "Bindirme"),
                isImage = isImage,
                sourceDurationMs = sourceDurationMs,
                sourceStartMs = sourceStartMs,
                timelineStartMs = item.optLong("timelineStartMs", 0L).coerceAtLeast(0L),
                durationMs = durationMs,
                scale = item.optDouble("scale", 0.35).toFloat().coerceIn(0.10f, 1.50f),
                xFraction = item.optDouble("xFraction", 0.78).toFloat().coerceIn(0f, 1f),
                yFraction = item.optDouble("yFraction", 0.22).toFloat().coerceIn(0f, 1f),
                rotationDegrees = item.optDouble("rotationDegrees", 0.0).toFloat().coerceIn(-180f, 180f),
                opacity = item.optDouble("opacity", 1.0).toFloat().coerceIn(0f, 1f),
                keyframes = mutableListOf<OverlayTransformKeyframe>().apply {
                    val savedKeyframes = item.optJSONArray("keyframes") ?: JSONArray()
                    for (frameIndex in 0 until savedKeyframes.length()) {
                        val frame = savedKeyframes.optJSONObject(frameIndex) ?: continue
                        val state = OverlayTransformState.normalize(
                            OverlayTransformState(
                                scale = frame.optDouble("scale", item.optDouble("scale", 0.35)).toFloat(),
                                xFraction = frame.optDouble("xFraction", item.optDouble("xFraction", 0.78)).toFloat(),
                                yFraction = frame.optDouble("yFraction", item.optDouble("yFraction", 0.22)).toFloat(),
                                rotationDegrees = frame.optDouble("rotationDegrees", item.optDouble("rotationDegrees", 0.0)).toFloat(),
                                opacity = frame.optDouble("opacity", item.optDouble("opacity", 1.0)).toFloat()
                            )
                        )
                        add(
                            OverlayTransformKeyframe(
                                timeMs = frame.optLong("timeMs", 0L).coerceIn(0L, durationMs),
                                state = state
                            )
                        )
                    }
                    val ordered = sortedBy { it.timeMs }
                    clear()
                    addAll(ordered)
                }
            )
            loadedOverlays.add(overlay)
            maxOverlayId = maxOf(maxOverlayId, overlay.id)
        }

        require(loadedClips.isNotEmpty()) { "Projede video klibi bulunamadı." }

        clearClipHistory()
        clips.clear()
        clips.addAll(loadedClips)
        overlayClips.clear()
        overlayClips.addAll(loadedOverlays)
        selectedOverlayId = json.optLong("selectedOverlayId", maxOverlayId).takeIf { id ->
            overlayClips.any { it.id == id }
        } ?: overlayClips.lastOrNull()?.id
        nextOverlayId = maxOf(json.optLong("nextOverlayId", maxOverlayId + 1L), maxOverlayId + 1L)
        selectedClipIndex = json.optInt("selectedClipIndex", 0).coerceIn(0, clips.lastIndex)
        timelinePreviewLoaded = false
        previewSingleClip = true
        timelineLaneOrder.clear()
        json.optJSONArray("timelineLaneOrder")?.let { savedOrder ->
            for (i in 0 until savedOrder.length()) {
                val key = savedOrder.optString(i).takeIf { it.isNotBlank() }
                if (key != null && key !in timelineLaneOrder) timelineLaneOrder.add(key)
            }
        }
        val legacyZoom = json.optDouble("timelineZoom", 1.0).toFloat()
            .takeIf { it.isFinite() }?.coerceIn(0.5f, 8.0f) ?: 1.0f
        timelinePxPerSec = json.optDouble(
            "timelinePxPerSec", (dp(22).toFloat() * legacyZoom).toDouble()
        ).toFloat().takeIf { it.isFinite() }?.coerceIn(
            dp(11).toFloat(), dp(720).toFloat()
        ) ?: dp(22).toFloat()
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

        val defaultPlayheadMs=clips[selectedClipIndex].timelineStartMs
        timelinePlayheadMs = if (json.has("timelinePlayheadMs")) {
            json.optLong("timelinePlayheadMs", defaultPlayheadMs)
                .coerceIn(0L, totalOutputDurationMs())
        } else {
            defaultPlayheadMs
        }
        val hasSavedTimelineScroll = savedTimelineScrollMs != null
        val restoredActiveIndex=activeClipIndexAt(timelinePlayheadMs)
        selectedClipIndex=restoredActiveIndex ?: json.optInt("selectedClipIndex",0).coerceIn(0,clips.lastIndex)
        musicUri = json.optString("musicUri").takeIf { it.isNotBlank() && it != "null" }?.let(Uri::parse)
        musicTitle = json.optString("musicTitle").takeIf { it.isNotBlank() && it != "null" }
        musicSourceDurationMs = json.optLong("musicSourceDurationMs", 0L).coerceAtLeast(0L)
        musicSourceStartMs = json.optLong("musicSourceStartMs", 0L).coerceAtLeast(0L)
        musicSourceEndMs = json.optLong("musicSourceEndMs", musicSourceDurationMs).coerceAtLeast(musicSourceStartMs)
        musicStartMs = json.optLong("musicStartMs", 0L).coerceAtLeast(0L)
        musicDurationMs = json.optLong("musicDurationMs", totalOutputDurationMs()).coerceAtLeast(0L)
        musicSourceEndMs = MusicSourceRangeMath.normalize(MusicSourceRange(musicSourceStartMs, musicSourceEndMs), musicSourceDurationMs).endMs
        updateMusicSourceRangeControls()
        musicUri?.let { ensureMusicSourceDuration(it) }
        soundUri = json.optString("soundUri").takeIf { it.isNotBlank() && it != "null" }?.let(Uri::parse)
        soundTitle = json.optString("soundTitle").takeIf { it.isNotBlank() && it != "null" }
        val legacySoundDurationMs = json.optLong("soundDurationMs", 0L).coerceAtLeast(0L)
        soundSourceDurationMs = json.optLong("soundSourceDurationMs", legacySoundDurationMs)
            .coerceAtLeast(0L)
        soundSourceStartMs = json.optLong("soundSourceStartMs", 0L)
            .coerceIn(0L, soundSourceDurationMs)
        soundDurationMs = legacySoundDurationMs.coerceAtMost(
            (soundSourceDurationMs - soundSourceStartMs).coerceAtLeast(0L)
        )
        soundStartMs = json.optLong("soundStartMs", 0L).coerceAtLeast(0L)
        val restoredSound = TimelineAudioMath.normalize(
            TimelineAudioSegment(soundStartMs, soundSourceStartMs, soundDurationMs),
            soundSourceDurationMs,
            totalOutputDurationMs()
        )
        soundStartMs = restoredSound.timelineStartMs
        soundSourceStartMs = restoredSound.sourceStartMs
        soundDurationMs = restoredSound.durationMs
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
                    kind = runCatching {
                        TextLayerKind.valueOf(item.optString("kind", "TEXT"))
                    }.getOrDefault(TextLayerKind.TEXT),
                    animationPreset = TextAnimationPreset.fromKey(item.optString("animationPreset", "none")),
                    animationDurationMs = item.optLong("animationDurationMs", 350L).coerceIn(100L, 1_500L),
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
        selectedVideoFilter = VideoFilterPreset.fromKey(json.optString("videoFilterPreset", "original"))
        filterBrightness = json.optInt("filterBrightness", 0).coerceIn(-100, 100)
        filterContrast = json.optInt("filterContrast", 0).coerceIn(-100, 100)
        filterSaturation = json.optInt("filterSaturation", 0).coerceIn(-100, 100)
        filterHue = json.optInt("filterHue", 0).coerceIn(-180, 180)
        loadVideoFilterControls()
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
        exportCanvasPreset = VideoCanvasPreset.fromKey(json.optString("exportCanvasPreset", "source"))
        exportCanvasGroup.check(340 + VideoCanvasPreset.values().indexOf(exportCanvasPreset))
        updateMusicTimingLabel()
        soundLabel.text = soundTitle?.let { "Ses/efekt: $it • ${formatMs(soundDurationMs)}" } ?: "Ses veya efekt seçilmedi."
        updateMusicTimingLabel()
        musicVolumeSeek.progress = musicVolume
        musicVolumeLabel.text = "Müzik ses seviyesi: $musicVolume%"
        soundVolumeSeek.progress = soundVolume
        soundVolumeLabel.text = "Ses/efekt seviyesi: $soundVolume%"
        sourceVolumeSeek.progress = sourceAudioVolume
        sourceVolumeLabel.text = "Orijinal video sesi: $sourceAudioVolume%"
        refreshTimelineAfterClipChange()
        loadTrimControls()
        selectedClip()?.let {
            previewSingleClip = false
            timelinePreviewLoaded = false
            ensureTimelinePreviewLoaded()
            seekTimelinePreview(timelinePlayheadMs)
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
        updateTimelinePlayheadDisplay()
        loadOverlayControls()
        refreshTimelineTracks()
        status.text = "PROJE AÇILDI. Klipler, bindirmeler, kesmeler, oynatma noktası, yazı ve ses ayarları geri yüklendi."
        loadMissingClipFrameRates()
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

    private fun chooseOverlayAsset(imagesOnly: Boolean) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = if (imagesOnly) "image/*" else "video/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        try {
            startActivityForResult(intent, REQUEST_OVERLAY_ASSET)
        } catch (error: Exception) {
            status.text = "Fotoğraf/video seçici açılamadı: ${error.message}"
        }
    }

    private fun addOverlayAsset(uri: Uri) {
        val mimeType = contentResolver.getType(uri).orEmpty().lowercase(Locale.US)
        val extension = (uri.lastPathSegment ?: "").substringAfterLast('.', "").lowercase(Locale.US)
        val isImage = mimeType.startsWith("image/") ||
            (mimeType.isBlank() && extension in setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "heif"))
        val isVideo = mimeType.startsWith("video/") ||
            (mimeType.isBlank() && extension in setOf("mp4", "m4v", "mov", "webm", "mkv", "3gp"))
        if (!isImage && !isVideo) {
            status.text = "Bindirme için yalnızca fotoğraf veya video dosyası seçebilirsin."
            return
        }
        val title = queryDisplayName(uri) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Bindirme"
        status.text = "Bindirme dosyası hazırlanıyor…"
        Thread {
            val sourceDuration = if (isImage) 60_000L else runCatching {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(this, uri)
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                } finally {
                    retriever.release()
                }
            }.getOrDefault(0L)
            runOnUiThread {
                if (sourceDuration <= 0L) {
                    status.text = "Bindirme dosyasının süresi okunamadı. Başka bir görsel veya video seç."
                    return@runOnUiThread
                }
                val projectDuration = TimelineVideoMath.totalDuration(videoTimelineSegments()).coerceAtLeast(1_000L)
                val logoDefaults = if (isImage) OverlayAssetDefaultsMath.imageLogo(projectDuration) else null
                // A logo begins at project time zero and spans the entire main video,
                // regardless of the current playhead. Video overlays still start at the playhead.
                val startMs = if (logoDefaults != null) 0L
                    else timelinePlayheadMs.coerceIn(0L, projectDuration - 1L)
                val effectiveSourceDuration = logoDefaults?.sourceDurationMs ?: sourceDuration
                val defaultDuration = logoDefaults?.durationMs
                    ?: minOf(sourceDuration, (projectDuration - startMs).coerceAtLeast(1_000L))
                val overlay = OverlayClip(
                    id = nextOverlayId++,
                    uri = uri,
                    title = title,
                    isImage = isImage,
                    sourceDurationMs = effectiveSourceDuration,
                    sourceStartMs = 0L,
                    timelineStartMs = startMs,
                    durationMs = defaultDuration.coerceIn(1L, effectiveSourceDuration),
                    scale = logoDefaults?.scale ?: 0.35f,
                    xFraction = logoDefaults?.xFraction ?: 0.78f,
                    yFraction = logoDefaults?.yFraction ?: 0.22f,
                    opacity = logoDefaults?.opacity ?: 1f
                )
                overlayClips.add(overlay)
                selectedOverlayId = overlay.id
                showEditorPanel("overlay")
                loadOverlayControls()
                refreshTimelineTracks()
                rebuildTimelinePreviewForOverlayEdit()
                status.text = if (isImage) {
                    "Fotoğraf bindirme olarak eklendi. Süre, boyut ve konumu aşağıdan düzenle."
                } else {
                    "Video bindirme olarak eklendi. Süre, boyut ve konumu aşağıdan düzenle."
                }
            }
        }.start()
    }

    private fun selectedOverlay(): OverlayClip? =
        overlayClips.firstOrNull { it.id == selectedOverlayId }

    /** Select an overlay track without opening its settings sheet over the timeline. */
    private fun enterOverlayTools() {
        if (!::editorDock.isInitialized || !::editorPanelScroll.isInitialized) return
        clipToolsMode = true
        activeClipTool = null
        activeEditorPanel = null
        updateClipTransformTouchAvailability()
        updateOverlayTransformTouchAvailability()
        textOverlayEditingEnabled = false
        updateTextOverlayEditToggle()
        editorPanels.values.forEach { it.visibility = View.GONE }
        editorPanelScroll.animate().cancel()
        editorPanelScroll.visibility = View.GONE
        editorPanelScroll.translationY = 0f
        editorPanelScroll.alpha = 1f
        editorDock.removeAllViews()
        val tools = listOf(
            Triple("‹", "Geri", "Ana araçlara dön"),
            Triple("▧", "Düzenle", "Seçili bindirmenin ayarlarını aç"),
            Triple("×", "Sil", "Seçili bindirmeyi kaldır")
        )
        tools.forEach { (symbol, title, description) ->
            val action: () -> Unit = when (title) {
                "Geri" -> ({ exitClipTools() })
                "Düzenle" -> ({ showEditorPanel("overlay") })
                "Sil" -> ({ removeSelectedOverlay() })
                else -> ({})
            }
            editorDock.addView(
                makeClipDockTool(symbol, title, description, action),
                LinearLayout.LayoutParams(dp(60), dp(58))
            )
        }
        editorDockScroll.scrollTo(0, 0)
        logEditorPanelGeometry("overlay-tools")
    }

    private fun selectOverlay(id: Long) {
        val overlay = overlayClips.firstOrNull { it.id == id } ?: return
        selectedOverlayId = id
        timelinePlayheadMs = overlay.timelineStartMs
        loadOverlayControls()
        refreshTimelineTracks()
        updateTimelinePlayheadDisplay()
        if (timelinePreviewLoaded) seekTimelinePreview(timelinePlayheadMs)
        // Keep the timeline visible and draggable; settings rise only after "Düzenle".
        enterOverlayTools()
    }

    private fun updateOverlayTransformTouchAvailability() {
        if (!::overlayTransformTouchOverlay.isInitialized) return
        overlayTransformTouchOverlay.visibility =
            if (activeEditorPanel == "overlay" && selectedOverlay() != null) View.VISIBLE else View.GONE
    }

    private fun installOverlayTransformTouch() {
        var gestureOverlay: OverlayClip? = null
        var downX = 0f
        var downY = 0f
        var downCenterX = 0f
        var downCenterY = 0f
        var downXFraction = 0f
        var downYFraction = 0f
        var downScale = 1f
        var downRotation = 0f
        var downDistance = 0f
        var downAngle = 0f

        fun distance(event: android.view.MotionEvent): Float {
            val dx = event.getX(1) - event.getX(0)
            val dy = event.getY(1) - event.getY(0)
            return kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        }
        fun angle(event: android.view.MotionEvent): Float = Math.toDegrees(
            kotlin.math.atan2(
                (event.getY(1) - event.getY(0)).toDouble(),
                (event.getX(1) - event.getX(0)).toDouble()
            )
        ).toFloat()
        fun centroidX(event: android.view.MotionEvent): Float =
            if (event.pointerCount >= 2) (event.getX(0) + event.getX(1)) / 2f else event.x
        fun centroidY(event: android.view.MotionEvent): Float =
            if (event.pointerCount >= 2) (event.getY(0) + event.getY(1)) / 2f else event.y

        overlayTransformTouchOverlay.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    val overlay = selectedOverlay() ?: return@setOnTouchListener false
                    gestureOverlay = overlay
                    pauseActivePreview()
                    downX = event.x
                    downY = event.y
                    downCenterX = event.x
                    downCenterY = event.y
                    downXFraction = overlay.xFraction
                    downYFraction = overlay.yFraction
                    downScale = overlay.scale
                    downRotation = overlay.rotationDegrees
                    downDistance = 0f
                    downAngle = 0f
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                    true
                }
                android.view.MotionEvent.ACTION_POINTER_DOWN -> {
                    val overlay = gestureOverlay
                    if (overlay != null && event.pointerCount >= 2) {
                        downCenterX = centroidX(event)
                        downCenterY = centroidY(event)
                        downDistance = distance(event)
                        downAngle = angle(event)
                        val state = overlayStateAtTime(overlay, timelinePlayheadMs)
                        downXFraction = state.xFraction
                        downYFraction = state.yFraction
                        downScale = state.scale
                        downRotation = state.rotationDegrees
                    }
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val overlay = gestureOverlay ?: return@setOnTouchListener false
                    val width = view.width.toFloat().coerceAtLeast(1f)
                    val height = view.height.toFloat().coerceAtLeast(1f)
                    if (event.pointerCount >= 2 && downDistance > 0f) {
                        val centerX = centroidX(event)
                        val centerY = centroidY(event)
                        val rawRotationDelta = angle(event) - downAngle
                        val rotationDelta = ((rawRotationDelta + 540f) % 360f) - 180f
                        val updated = OverlayTransformState(
                            scale = (downScale * distance(event) / downDistance).coerceIn(0.10f, 1.50f),
                            xFraction = (downXFraction + (centerX - downCenterX) / width).coerceIn(0f, 1f),
                            yFraction = (downYFraction + (centerY - downCenterY) / height).coerceIn(0f, 1f),
                            rotationDegrees = (downRotation + rotationDelta).coerceIn(-180f, 180f),
                            opacity = overlayStateAtTime(overlay, timelinePlayheadMs).opacity
                        )
                        updateOverlayTransformAtPlayhead(overlay, updated)
                    } else if (event.pointerCount == 1) {
                        val state = overlayStateAtTime(overlay, timelinePlayheadMs)
                        updateOverlayTransformAtPlayhead(
                            overlay,
                            state.copy(
                                xFraction = (downXFraction + (event.x - downX) / width).coerceIn(0f, 1f),
                                yFraction = (downYFraction + (event.y - downY) / height).coerceIn(0f, 1f)
                            )
                        )
                    }
                    updateOverlayControlLabels(overlay)
                    true
                }
                android.view.MotionEvent.ACTION_POINTER_UP -> {
                    val overlay = gestureOverlay
                    if (overlay != null && event.pointerCount > 1) {
                        val remaining = if (event.actionIndex == 0) 1 else 0
                        downX = event.getX(remaining)
                        downY = event.getY(remaining)
                        downXFraction = overlay.xFraction
                        downYFraction = overlay.yFraction
                        downScale = overlay.scale
                        downRotation = overlay.rotationDegrees
                        downDistance = 0f
                    }
                    true
                }
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> {
                    val overlay = gestureOverlay
                    gestureOverlay = null
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                    if (overlay != null) {
                        loadOverlayControls()
                        refreshTimelineTracks()
                        rebuildTimelinePreviewForOverlayEdit()
                    }
                    true
                }
                else -> gestureOverlay != null
            }
        }
    }

    private fun removeSelectedOverlay() {
        val selected = selectedOverlay() ?: run {
            status.text = "Kaldırmak için önce zaman çizelgesinden bir bindirme seç."
            return
        }
        overlayClips.removeAll { it.id == selected.id }
        selectedOverlayId = overlayClips.lastOrNull()?.id
        loadOverlayControls()
        refreshTimelineTracks()
        rebuildTimelinePreviewForOverlayEdit()
        status.text = "Bindirme katmanı kaldırıldı."
    }

    private fun installOverlayControlListeners() {
        fun attach(seek: SeekBar, update: (OverlayClip, Int) -> Unit) {
            seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser || updatingOverlayControls) return
                    val overlay = selectedOverlay() ?: return
                    update(overlay, progress)
                    updateOverlayControlLabels(overlay)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    pauseActivePreview()
                }
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    if (!updatingOverlayControls) {
                        loadOverlayControls()
                        refreshTimelineTracks()
                        rebuildTimelinePreviewForOverlayEdit()
                    }
                }
            })
        }
        attach(overlayStartSeek) { overlay, progress ->
            val rangeMs = TimelineTrimMath.timeForProgress(progress, overlayStartSeek.max.toLong().coerceAtLeast(1L))
            overlay.timelineStartMs = rangeMs.coerceAtLeast(0L)
        }
        attach(overlayDurationSeek) { overlay, progress ->
            val requested = TimelineTrimMath.timeForProgress(progress, overlay.sourceDurationMs)
                .coerceAtLeast(1_000L)
            overlay.durationMs = requested.coerceAtMost(overlay.sourceDurationMs)
        }
        attach(overlayScaleSeek) { overlay, progress ->
            val state = overlayStateAtTime(overlay, timelinePlayheadMs)
            updateOverlayTransformAtPlayhead(
                overlay, state.copy(scale = (0.10f + progress / 1000f * 1.40f).coerceIn(0.10f, 1.50f))
            )
        }
        attach(overlayXSeek) { overlay, progress ->
            val state = overlayStateAtTime(overlay, timelinePlayheadMs)
            updateOverlayTransformAtPlayhead(overlay, state.copy(xFraction = progress / 1000f))
        }
        attach(overlayYSeek) { overlay, progress ->
            val state = overlayStateAtTime(overlay, timelinePlayheadMs)
            updateOverlayTransformAtPlayhead(overlay, state.copy(yFraction = progress / 1000f))
        }
        attach(overlayRotationSeek) { overlay, progress ->
            val state = overlayStateAtTime(overlay, timelinePlayheadMs)
            updateOverlayTransformAtPlayhead(overlay, state.copy(rotationDegrees = progress / 10f - 180f))
        }
        attach(overlayOpacitySeek) { overlay, progress ->
            val state = overlayStateAtTime(overlay, timelinePlayheadMs)
            updateOverlayTransformAtPlayhead(overlay, state.copy(opacity = (progress / 1000f).coerceIn(0f, 1f)))
        }
    }

    private fun loadOverlayControls() {
        if (!::overlayTitleLabel.isInitialized) return
        val overlay = selectedOverlay()
        updatingOverlayControls = true
        try {
            if (overlay == null) {
                overlayTitleLabel.text = "Henüz bindirme seçilmedi. Fotoğraf veya video ekle."
                listOf(overlayStartSeek, overlayDurationSeek, overlayScaleSeek,
                    overlayXSeek, overlayYSeek, overlayRotationSeek, overlayOpacitySeek).forEach { it.isEnabled = false }
                overlayStartLabel.text = "Başlangıç"
                overlayDurationLabel.text = "Katman süresi"
                overlayScaleLabel.text = "Boyut"
                overlayPositionLabel.text = "Konum • yatay / dikey"
                overlayRotationLabel.text = "Döndürme"
                overlayOpacityLabel.text = "Saydamlık"
                overlayKeyframeLabel.text = "Anahtar kare: 0"
                return
            }
            listOf(overlayStartSeek, overlayDurationSeek, overlayScaleSeek,
                overlayXSeek, overlayYSeek, overlayRotationSeek, overlayOpacitySeek).forEach { it.isEnabled = true }
            val projectDuration = maxOf(
                totalOutputDurationMs(),
                overlay.timelineStartMs + overlay.durationMs,
                1_000L
            )
            overlayStartSeek.max = TimelineTrimMath.progressMax(projectDuration)
            overlayStartSeek.progress = TimelineTrimMath.progressForTime(
                overlay.timelineStartMs, projectDuration
            )
            overlayDurationSeek.max = TimelineTrimMath.progressMax(overlay.sourceDurationMs)
            overlayDurationSeek.progress = TimelineTrimMath.progressForTime(
                overlay.durationMs, overlay.sourceDurationMs
            )
            val state = overlayStateAtTime(overlay, timelinePlayheadMs)
            overlayScaleSeek.progress = (((state.scale - 0.10f) / 1.40f) * 1000f).toInt().coerceIn(0, 1000)
            overlayXSeek.progress = (state.xFraction * 1000f).toInt().coerceIn(0, 1000)
            overlayYSeek.progress = (state.yFraction * 1000f).toInt().coerceIn(0, 1000)
            overlayRotationSeek.progress = ((state.rotationDegrees + 180f) * 10f).toInt().coerceIn(0, 3600)
            overlayOpacitySeek.progress = (state.opacity * 1000f).toInt().coerceIn(0, 1000)
            updateOverlayControlLabels(overlay)
        } finally {
            updatingOverlayControls = false
            updateOverlayTransformTouchAvailability()
        }
    }

    private fun updateOverlayControlLabels(overlay: OverlayClip) {
        overlayTitleLabel.text = (if (overlay.isImage) "Fotoğraf" else "Video") + " • " + overlay.title
        overlayStartLabel.text = "Başlangıç: " + formatTimelineMs(overlay.timelineStartMs)
        overlayDurationLabel.text = "Süre: " + formatTimelineMs(overlay.durationMs) + " / Kaynak: " + formatTimelineMs(overlay.sourceDurationMs)
        val state = overlayStateAtTime(overlay, timelinePlayheadMs)
        overlayScaleLabel.text = "Boyut: " + (state.scale * 100f).toInt() + "%"
        overlayPositionLabel.text = "Konum: X " + (state.xFraction * 100f).toInt() + "% • Y " + (state.yFraction * 100f).toInt() + "%"
        overlayRotationLabel.text = "Döndürme: " + state.rotationDegrees.toInt() + "°"
        overlayOpacityLabel.text = "Saydamlık: " + (state.opacity * 100f).toInt() + "%"
        val localTimeMs = overlayLocalTimeMs(overlay)
        val currentKeyframe = OverlayKeyframeMath.nearestIndex(overlay.keyframes, localTimeMs, 100L) != null
        overlayKeyframeLabel.text = "Anahtar kare: ${overlay.keyframes.size} • ${formatTimelineMs(localTimeMs)}" +
            if (currentKeyframe) " ◆" else ""
    }

    private fun rebuildTimelinePreviewForOverlayEdit() {
        val wasPlaying = timelineCompositionPlayer?.playWhenReady == true
        val position = timelinePlayheadMs
        pauseActivePreview()
        runCatching { timelineCompositionPlayer?.release() }
        timelineCompositionPlayer = null
        timelinePreviewLoaded = false
        if (clips.isNotEmpty()) {
            ensureTimelinePreviewLoaded()
            seekTimelinePreview(position.coerceAtMost(totalOutputDurationMs()))
            timelineCompositionPlayer?.playWhenReady = wasPlaying
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
        if (requestCode == REQUEST_SAVE_COVER_IMAGE) {
            savePendingCoverImage(if (resultCode == RESULT_OK) data?.data else null)
            return
        }
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
                status.text = "Müzik dosyasının süresi okunuyor…"
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
                    val title = queryDisplayName(uri) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Müzik"
                    runOnUiThread {
                        if (duration <= 0L) {
                            status.text = "Müzik dosyasının süresi okunamadı. Başka bir ses dosyası seç."
                        } else {
                            musicUri = uri
                            musicTitle = title
                            musicSourceDurationMs = duration
                            musicSourceStartMs = 0L
                            musicSourceEndMs = duration
                            updateMusicSourceRangeControls()
                            musicStartMs = 0L
                            if (::musicSourceStartSeek.isInitialized) musicSourceStartSeek.progress = 0
                            if (::musicSourceEndSeek.isInitialized) musicSourceEndSeek.progress = 1000
                            if (::musicSourceRangeLabel.isInitialized) musicSourceRangeLabel.text = "Dosya içi: 00:00 – ${formatTimelineMs(duration)}"
                            // Keep the full selected source range; if it extends beyond the
                            // video, the shared project timeline grows to include the music.
                            musicDurationMs = (musicSourceEndMs - musicSourceStartMs).coerceAtLeast(0L)
                            updateMusicTimingLabel()
                            refreshTimelineTracks()
                            status.text = "Müzik eklendi. Zaman çizelgesinde sürükleyip başlangıç ve bitişini ayarlayabilirsin."
                        }
                    }
                }.start()
            }
            REQUEST_OVERLAY_ASSET -> addOverlayAsset(uri)
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
                            soundSourceDurationMs = duration
                            soundSourceStartMs = 0L
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
                    var frameRate: Double? = null
                    val duration = try {
                        retriever.setDataSource(this, uri)
                        frameRate = retriever.extractMetadata(
                            MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE
                        )?.toDoubleOrNull()?.takeIf { it.isFinite() && it in 1.0..240.0 }
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    } finally {
                        retriever.release()
                    }
                    if (frameRate == null) frameRate = readFrameRateFromMediaExtractor(uri)
                    if (duration <= 0L) null else Clip(
                        uri = uri,
                        title = queryDisplayName(uri) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Video klibi",
                        durationMs = duration,
                        startMs = 0L,
                        endMs = duration,
                        frameRate = frameRate
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
                val startsFreshTimeline = replaceExisting || clips.isEmpty()
                if (replaceExisting) {
                    clips.clear()
                    selectedClipIndex = -1
                }
                if (startsFreshTimeline) {
                    // A newly opened video must begin at project time zero even if
                    // the previous editor session was scrolled to a later timestamp.
                    timelineHorizontalScrollX = 0
                    pendingTimelineScrollX = 0
                    timelinePlayheadMs = 0L
                }
                var appendAtMs=totalOutputDurationMs()
                val positioned=ready.map { item ->
                    item.copy(timelineStartMs=appendAtMs).also{appendAtMs+=(it.endMs-it.startMs).coerceAtLeast(0L)}
                }
                clips.addAll(positioned)
                selectedClipIndex=clips.lastIndex
                clipHandlesVisible = false
                timelinePreviewLoaded = false
                previewSingleClip = true
                timelinePlayheadMs = clips[selectedClipIndex].timelineStartMs
                refreshTimelineAfterClipChange()
                loadTrimControls()
                refreshTimelineTracks()
                if (selectedClip() != null) {
                    previewSingleClip = false
                    ensureTimelinePreviewLoaded()
                    seekTimelinePreview(timelinePlayheadMs)
                }
                val skipped = uris.size - ready.size
                status.text = buildString {
                    append("${ready.size} video zaman çizelgesine eklendi. Klibe dokun: seç; basılı tutup sürükle: zaman konumunu değiştir.")
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

    private fun refreshTimelineAfterClipChange() {
        // Preview empty state and the single visible timeline are refreshed together.
        if (::previewEmptyState.isInitialized) {
            previewEmptyState.visibility = if (clips.isEmpty()) View.VISIBLE else View.GONE
        }
        refreshTimelineTracks()
    }

    private fun duplicateClipAt(index:Int){
        val source=clips.getOrNull(index)?:return
        recordClipEdit()
        val span=ClipSpeedMath.timelineDurationMs(source.endMs-source.startMs,source.speed)
        clips.add(index+1,source.copy(title="${source.title} • Kopya",timelineStartMs=source.timelineStartMs+span))
        selectedClipIndex=index+1;timelinePreviewLoaded=false;previewSingleClip=false
        timelinePlayheadMs=clips[selectedClipIndex].timelineStartMs
        refreshTimelineAfterClipChange();loadTrimControls();ensureTimelinePreviewLoaded();seekTimelinePreview(timelinePlayheadMs)
        updateTimelinePlayheadLabel()
        status.text="Klip kopyası özgün klibin bitişine yerleştirildi."
    }

    private fun removeClipAt(index:Int){
        if(index !in clips.indices)return
        recordClipEdit()
        clips.removeAt(index)
        timelinePreviewLoaded=false
        selectedClipIndex=when{clips.isEmpty()->-1;index<clips.size->index;else->clips.lastIndex}
        // Update the playhead BEFORE rebuilding the lanes. Previously refreshTimelineAfterClipChange()
        // rebuilt the timeline with the deleted clip's old scroll position, then the
        // shorter content clamped that scroll and made both the zero ruler mark and the
        // trailing add button appear to jump sideways. Re-anchor scroll from the new
        // playhead timestamp so every lane keeps the same centre-time coordinate.
        val selected=selectedClip()
        timelinePlayheadMs=selected?.timelineStartMs?.coerceIn(0L,totalOutputDurationMs()) ?: 0L
        pendingTimelineScrollX=TimelineScaleMath.pixelsForTime(
            timelinePlayheadMs, timelinePixelsPerSecond()
        ).coerceAtLeast(0)
        timelineHorizontalScrollX=pendingTimelineScrollX ?: 0
        previewSingleClip=false
        refreshTimelineAfterClipChange()
        loadTrimControls()
        if(selected==null){
            pauseActivePreview();timelineCompositionPlayer?.release();timelineCompositionPlayer=null
            timelinePreviewLoaded=false;player?.clearMediaItems()
        }else{
            ensureTimelinePreviewLoaded()
            seekTimelinePreview(timelinePlayheadMs)
        }
        updateTimelinePlayheadLabel()
        status.text=if(clips.isEmpty())"Zaman çizelgesi boş." else "Klip silindi; zaman cetveli ve ekleme düğmesi yeni oynatma noktasına sabitlendi."
    }

    private fun removeSelectedClip() {
        if (selectedClipIndex !in clips.indices) {
            status.text = "Önce kaldırılacak klibi seç."
            return
        }
        // Keep the button and long-press menu on the same, fully-synchronised removal path.
        removeClipAt(selectedClipIndex)
    }

    

    private fun splitSelectedClipAtPlayhead(expectedClipIndex:Int?=null){
        if(clips.isEmpty()){status.text="Önce klip seç.";return}
        val total=totalOutputDurationMs()
        if(total<=0L){status.text="Bölünecek video süresi yok.";return}
        val playhead=timelinePlayheadMs.coerceIn(0L,(total-1L).coerceAtLeast(0L))
        val active=activeClipIndexAt(playhead)
        if(active==null){status.text="Oynatma noktası bir klibin üzerinde değil.";return}
        if(expectedClipIndex!=null&&expectedClipIndex!=active){status.text="Oynatma noktasını seçili klibin üzerine getir.";return}
        val clip=clips[active]
        val duration=ClipSpeedMath.timelineDurationMs(clip.endMs-clip.startMs,clip.speed)
        val local=(playhead-clip.timelineStartMs).coerceIn(0L,duration)
        val sourceSplit=clip.startMs+ClipSpeedMath.sourceOffsetMs(local,clip.speed)
        if(local<350L||duration-local<350L){status.text="İki tarafta da en az 0,35 saniye olmalı.";return}
        recordClipEdit()
        clips[active]=clip.copy(title="${clip.title} • A",endMs=sourceSplit)
        clips.add(active+1,clip.copy(title="${clip.title} • B",startMs=sourceSplit,timelineStartMs=playhead))
        selectedClipIndex=active+1;timelinePreviewLoaded=false;previewSingleClip=false;timelinePlayheadMs=playhead
        refreshTimelineAfterClipChange();loadTrimControls();ensureTimelinePreviewLoaded();seekTimelinePreview(playhead);updateTimelinePlayheadLabel()
        status.text="Klip bölündü; ikinci parça aynı oynatma noktasında başlıyor."
    }

    

    /**
     * Reorders a split/main-track clip by the horizontal location where it was dropped.
     * The pure timing plan rejects overlapping video layers rather than flattening their
     * intentional z-order. A successful move reflows the sequential video track while
     * leaving independently-timed text/audio lanes on their existing project timestamps.
     */
    private fun reorderTimelineClipFromDrop(index: Int, requestedStartMs: Long) {
        val plan = TimelineVideoMath.reorderForDrop(
            videoTimelineSegments(),
            movingIndex = index,
            requestedStartMs = requestedStartMs
        )
        if (plan == null) {
            status.text = "Video klipleri üst üste biniyor. Katman düzenini bozmamak için sıralama yapılmadı."
            return
        }

        val previous = clips.toList()
        val reordered = plan.orderedClipIndices.map { originalIndex ->
            previous[originalIndex].copy(
                timelineStartMs = plan.timelineStartByClipIndex[originalIndex]
                    ?: previous[originalIndex].timelineStartMs
            )
        }
        val changed = reordered.indices.any { position ->
            reordered[position] != previous.getOrNull(position)
        }
        if (!changed) return

        recordClipEdit()
        clips.clear()
        clips.addAll(reordered)
        selectedClipIndex = plan.orderedClipIndices.indexOf(index)
        clipHandlesVisible = true
        val selected = clips.getOrNull(selectedClipIndex) ?: return
        timelinePlayheadMs = selected.timelineStartMs
        pendingTimelineScrollX = TimelineScaleMath.pixelsForTime(
            timelinePlayheadMs, timelinePixelsPerSecond()
        ).coerceAtLeast(0)
        timelineHorizontalScrollX = pendingTimelineScrollX ?: 0
        timelinePreviewLoaded = false
        previewSingleClip = false

        refreshTimelineAfterClipChange()
        loadTrimControls()
        ensureTimelinePreviewLoaded()
        seekTimelinePreview(timelinePlayheadMs)
        updateTimelinePlayheadLabel()
        status.text = "Klip sırası değiştirildi; video parçaları art arda hizalandı."
    }

    private fun moveSelectedClip(delta:Int){
        if(selectedClipIndex !in clips.indices){status.text="Önce katman seç.";return}
        val target=selectedClipIndex+delta
        if(target !in clips.indices){status.text="Bu katman daha fazla taşınamıyor.";return}
        recordClipEdit();val moved=clips.removeAt(selectedClipIndex);clips.add(target,moved)
        selectedClipIndex=target;timelinePreviewLoaded=false;previewSingleClip=false;timelinePlayheadMs=moved.timelineStartMs
        refreshTimelineAfterClipChange();loadTrimControls();ensureTimelinePreviewLoaded();seekTimelinePreview(timelinePlayheadMs);updateTimelinePlayheadLabel()
        status.text="Katman sırası değişti; zaman konumları korundu."
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
        val sourceDurationMs = (clip.endMs - clip.startMs).coerceAtLeast(0L)
        val durationMs = ClipSpeedMath.timelineDurationMs(sourceDurationMs, clip.speed)
        val container = FrameLayout(this).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.rgb(27, 39, 54))
                cornerRadius = dp(4).toFloat()
            }
            foreground = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = dp(4).toFloat()
                setStroke(dp(if (selected) 2 else 1),
                    if (selected) Color.WHITE else Color.rgb(61, 79, 99))
            }
            clipToOutline = true
            // Keep the selected clip reachable even when it overlaps another clip.
            elevation = if (selected) dp(12).toFloat() else 0f
            isClickable = true
            isFocusable = true
            setOnClickListener { selectTimelineClip(index) }
            var filmstripTouchActive = false
            var filmstripMoved = false
            var filmstripLongPressed = false
            var downRawX = 0f
            var initialStartMs = clip.timelineStartMs
            val longPressAction = Runnable {
                if (filmstripTouchActive && !filmstripMoved) {
                    filmstripLongPressed = true
                    animate().scaleX(0.88f).scaleY(0.88f).setDuration(120L).start()
                }
            }
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        filmstripTouchActive = true
                        filmstripMoved = false
                        filmstripLongPressed = false
                        downRawX = event.rawX
                        initialStartMs = clip.timelineStartMs
                        pauseActivePreview()
                        // Do not steal the horizontal gesture from the parent lane:
                        // an ordinary drag scrubs; a hold-then-drag moves the clip.
                        view.parent?.requestDisallowInterceptTouchEvent(false)
                        timelineHandler.postDelayed(
                            longPressAction,
                            android.view.ViewConfiguration.getLongPressTimeout().toLong()
                        )
                        true
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - downRawX
                        if (kotlin.math.abs(dx) >
                            android.view.ViewConfiguration.get(this@VideoEditorActivity).scaledTouchSlop
                        ) {
                            filmstripMoved = true
                            timelineHandler.removeCallbacks(longPressAction)
                            if (filmstripLongPressed) {
                                view.parent?.requestDisallowInterceptTouchEvent(true)
                                val minimumPx = -TimelineScaleMath.pixelsForTime(
                                    initialStartMs, timelinePixelsPerSecond()
                                )
                                // Let the first clip travel right as well: the drop target
                                // determines its new sequence position, not a free timeline gap.
                                view.translationX = maxOf(minimumPx.toFloat(), dx)
                            } else {
                                // Let HorizontalScrollView take over this gesture and scrub.
                                view.parent?.requestDisallowInterceptTouchEvent(false)
                            }
                        }
                        true
                    }
                    android.view.MotionEvent.ACTION_UP -> {
                        timelineHandler.removeCallbacks(longPressAction)
                        filmstripTouchActive = false
                        view.parent?.requestDisallowInterceptTouchEvent(false)
                        view.translationX = 0f
                        view.animate().scaleX(1f).scaleY(1f).setDuration(100L).start()
                        if (filmstripLongPressed && filmstripMoved) {
                            val rawDeltaPx = event.rawX - downRawX
                            val minimumDeltaPx = -TimelineScaleMath.pixelsForTime(
                                initialStartMs, timelinePixelsPerSecond()
                            )
                            val boundedDeltaPx = maxOf(minimumDeltaPx.toFloat(), rawDeltaPx)
                            val deltaMs = TimelineScaleMath.millisecondsForPixels(
                                boundedDeltaPx, timelinePixelsPerSecond()
                            )
                            val requestedStartMs = (initialStartMs + deltaMs).coerceAtLeast(0L)
                            if (requestedStartMs != initialStartMs) {
                                reorderTimelineClipFromDrop(index, requestedStartMs)
                            }
                        } else if (filmstripLongPressed) {
                            // A stationary long press still opens the clip context menu.
                            view.performLongClick()
                        } else if (!filmstripMoved) {
                            view.performClick()
                        }
                        true
                    }
                    android.view.MotionEvent.ACTION_CANCEL -> {
                        timelineHandler.removeCallbacks(longPressAction)
                        filmstripTouchActive = false
                        view.translationX = 0f
                        view.animate().scaleX(1f).scaleY(1f).setDuration(100L).start()
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
                val currentSegment = videoTimelineSegments().getOrNull(index)
                val overlappingIndices = videoTimelineSegments().indices.filter { otherIndex ->
                    if (otherIndex == index || currentSegment == null) return@filter false
                    val other = TimelineVideoMath.normalize(videoTimelineSegments()[otherIndex])
                    val current = TimelineVideoMath.normalize(currentSegment)
                    current.timelineStartMs < other.timelineEndMs &&
                        other.timelineStartMs < current.timelineEndMs
                }
                if (overlappingIndices.isNotEmpty()) {
                    val overlapMenu = menu.menu.addSubMenu("Örtüşen klibi seç")
                    overlappingIndices.forEach { otherIndex ->
                        overlapMenu.add(0, 1000 + otherIndex, otherIndex, clips[otherIndex].title)
                    }
                }
                menu.setOnMenuItemClickListener { item ->
                    when {
                        item.itemId >= 1000 -> selectTimelineClip(item.itemId - 1000)
                        item.itemId == 1 -> duplicateClipAt(index)
                        item.itemId == 2 -> removeClipAt(index)
                        item.itemId == 3 -> splitSelectedClipAtPlayhead(expectedClipIndex = index)
                        item.itemId == 4 -> { selectTimelineClip(index); moveSelectedClip(-1) }
                        item.itemId == 5 -> { selectTimelineClip(index); moveSelectedClip(1) }
                    }
                    true
                }
                menu.show()
                true
            }
            contentDescription = "Klip ${index + 1}: ${clip.title}, ${formatTimelineMs(durationMs)}"
        }
        val filmstrip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            // The surrounding area matches the clip surface; it is not a black letterbox.
            setBackgroundColor(Color.rgb(27, 39, 54))
        }
        // Render one continuous filmstrip. Cell width follows the source aspect
        // ratio at a shared strip height; cells fill the clip without a second
        // independently moving strip. Bound the count to control decode/cache pressure.
        val targetThumbnailWidth = TimelineThumbnailMath.thumbnailCellWidthPx(
            height,
            timelineVideoAspectRatio(clip.uri)
        ).coerceAtLeast(1)
        // Cell count follows the real clip width (duration × px/sec): zooming in
        // creates more timestamp samples; zooming out reduces their density.
        val thumbnailCount = TimelineThumbnailMath.thumbnailCount(width, targetThumbnailWidth)
        val availableCellWidth = width.coerceAtLeast(thumbnailCount)
        val baseCellWidth = (availableCellWidth / thumbnailCount).coerceAtLeast(1)
        val extraPixels = availableCellWidth % thumbnailCount
        repeat(thumbnailCount) { slot ->
            val cellWidth = baseCellWidth + if (slot < extraPixels) 1 else 0
            val cellHeight = height.coerceAtLeast(1)
            val image = ImageView(this).apply {
                // Fit the complete source frame into its aspect-aware cell.
                // FIT_CENTER preserves every edge; no source pixels are cropped or stretched.
                scaleType = ImageView.ScaleType.FIT_CENTER
                setBackgroundColor(Color.TRANSPARENT)
                contentDescription = "Klip karesi"
            }
            filmstrip.addView(image, LinearLayout.LayoutParams(cellWidth, cellHeight))
            val cellStartMs = TimelineThumbnailMath.cellStartTime(
                clip.startMs, clip.endMs, thumbnailCount, slot
            )
            val cellEndMs = TimelineThumbnailMath.cellEndTime(
                clip.startMs, clip.endMs, thumbnailCount, slot
            )
            val frameTime = TimelineThumbnailMath.sampleTime(
                clip.startMs, clip.endMs, thumbnailCount, slot
            )
            // Expose the sample timestamp for diagnostics and accessibility. The
            // image now represents the center of this cell's shared timeline range.
            image.contentDescription = "Klip karesi @${frameTime}ms"
            val projectFrameTime = clip.timelineStartMs + (frameTime - clip.startMs).coerceAtLeast(0L)
            val priorityDistanceMs = kotlin.math.abs(projectFrameTime - timelinePlayheadMs)
            if (timelineSyncTestEnabled) android.util.Log.i(
                "TimelineFrameSyncTest",
                "TIMELINE_THUMBNAIL_SAMPLE slot=$slot count=$thumbnailCount sourceMs=$frameTime projectMs=$projectFrameTime"
            )
            requestTimelineThumbnail(
                clip.uri,
                frameTime,
                image,
                priorityDistanceMs,
                cellStartMs,
                cellEndMs,
                usePosterPlaceholder = slot == 0
            )
        }
        container.addView(filmstrip, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))

        // Larger, high-contrast touch targets make the trim edges easy to find and drag.
        fun addTrimHandle(isStart: Boolean) {
            if (!selected) return
            // Bound handle travel by source-time limits, not rendered clip width.
            // Otherwise short trimmed clips cannot be expanded back toward the source boundaries.
            val handleWidth = minOf(dp(40), (width / 2).coerceAtLeast(1))
            fun allowedDeltaPx(startMs:Long,endMs:Long):Pair<Float,Float>{
                // Handle travel is measured on the project timeline, while clip in/out
                // values remain source timestamps. Convert source bounds through speed
                // before translating them into pixels.
                val minSourceDelta=if(isStart) {
                    -(if(clip.timelineStartMs==0L) startMs else minOf(startMs,clip.timelineStartMs))
                } else {
                    TimelineTrimMath.minEndMs(startMs,clip.durationMs)-endMs
                }
                val maxSourceDelta=if(isStart) {
                    TimelineTrimMath.maxStartMs(endMs,clip.durationMs)-startMs
                } else {
                    clip.durationMs-endMs
                }
                val minTimelineDelta = -ClipSpeedMath.timelineOffsetMs(
                    kotlin.math.abs(minSourceDelta), clip.speed
                )
                val maxTimelineDelta = ClipSpeedMath.timelineOffsetMs(
                    maxSourceDelta.coerceAtLeast(0L), clip.speed
                )
                val scale=timelinePixelsPerSecond()/1000f
                return minTimelineDelta*scale to maxTimelineDelta*scale
            }
            val handle = FrameLayout(this).apply {
                // CapCut-style clean white trim edge: keep the wide transparent
                // touch target, but remove the oversized turquoise arrow blocks.
                setBackgroundColor(Color.TRANSPARENT)
                elevation = dp(8).toFloat()
                contentDescription = if (isStart) "Sol beyaz tutamaç: klibin başlangıcını sürükleyerek kısalt veya uzat" else "Sağ beyaz tutamaç: klibin bitişini sürükleyerek kısalt veya uzat"
                val grip = View(this@VideoEditorActivity).apply {
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(Color.WHITE)
                        cornerRadius = dp(3).toFloat()
                    }
                    isClickable = false
                }
                addView(grip, FrameLayout.LayoutParams(
                    dp(5), dp(30), Gravity.CENTER
                ))
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
                                val initial=TimelineVideoSegment(clip.timelineStartMs,initialStartMs,initialEndMs,clip.durationMs,clip.speed)
                                val sourceDeltaMs = ClipSpeedMath.sourceOffsetMs(kotlin.math.abs(deltaMs), clip.speed) *
                                    if (deltaMs < 0L) -1L else 1L
                                val updated=if(isStart && initial.timelineStartMs==0L)
                                    TimelineVideoMath.trimStartKeepingTimelinePosition(initial,sourceDeltaMs)
                                else if(isStart) TimelineVideoMath.trimStart(initial,sourceDeltaMs)
                                else TimelineVideoMath.trimEnd(initial,sourceDeltaMs)
                                if(updated!=initial){
                                    clip.startMs=updated.sourceStartMs;clip.endMs=updated.sourceEndMs
                                    // Trimming source footage must not move a clip already anchored at project time zero.
                                    clip.timelineStartMs=if(initial.timelineStartMs==0L) 0L else updated.timelineStartMs
                                    selectedClipIndex=index;finishTrimControlGesture()
                                    status.text="Klip kesimi güncellendi."
                                }else activeTrimHistorySnapshot=null
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

    private fun timelineThumbnailRetrieverFor(uri: Uri, tag: String): MediaMetadataRetriever? {
        val threadId = Thread.currentThread().id
        val current = timelineThumbnailRetrievers[threadId]
        if (current != null && current.first == uri) return current.second
        if (current != null) {
            runCatching { current.second.release() }
            timelineThumbnailRetrievers.remove(threadId, current)
        }
        return try {
            MediaMetadataRetriever().also { retriever ->
                retriever.setDataSource(this, uri)
                timelineThumbnailRetrievers[threadId] = uri to retriever
            }
        } catch (error: Exception) {
            android.util.Log.w(tag, "Could not open thumbnail source $uri", error)
            null
        }
    }

    private fun releaseTimelineThumbnailRetriever() {
        val threadId = Thread.currentThread().id
        timelineThumbnailRetrievers.remove(threadId)?.second?.let { retriever ->
            runCatching { retriever.release() }
        }
    }

    private fun timelineThumbnailExtractorFor(uri: Uri): FrameExtractor? {
        val threadId = Thread.currentThread().id
        val current = timelineThumbnailExtractors[threadId]
        if (current != null && current.first == uri) return current.second
        if (current != null) {
            runCatching { current.second.close() }
            timelineThumbnailExtractors.remove(threadId, current)
        }
        return try {
            FrameExtractor.Builder(this, MediaItem.fromUri(uri))
                .setEffects(listOf(Presentation.createForHeight(288)))
                .build()
                .also { timelineThumbnailExtractors[threadId] = uri to it }
        } catch (error: Exception) {
            android.util.Log.w("TimelineThumbnail", "Could not open Media3 thumbnail extractor for $uri", error)
            null
        }
    }

    private fun releaseTimelineThumbnailExtractor() {
        val threadId = Thread.currentThread().id
        timelineThumbnailExtractors.remove(threadId)?.second?.let { extractor ->
            runCatching { extractor.close() }
        }
    }

    private fun hidePreviewFrameOverlay() {
        if (!::previewFrameOverlay.isInitialized) return
        previewFrameRequestGeneration.incrementAndGet()
        pendingPreviewFrameTask?.let { timelineThumbnailExecutor.remove(it) }
        pendingPreviewFrameTask = null
        previewFrameOverlay.visibility = View.GONE
        previewFrameCacheKey = null
    }

    /** Cover the player immediately while a new playhead frame is being decoded. */
    private fun showPreviewFramePending(keepPreviousFrame: Boolean = false): Long {
        val requestId = previewFrameRequestGeneration.incrementAndGet()
        pendingPreviewFrameTask?.let { timelineThumbnailExecutor.remove(it) }
        pendingPreviewFrameTask = null
        if (!::previewFrameOverlay.isInitialized) return requestId
        // Pausing playback must not blank the last frame while a still is decoded.
        // The PlayerView already contains the last rendered video frame; if an overlay
        // is visible, keep that bitmap until the replacement is ready.
        if (keepPreviousFrame) return requestId
        previewFrameOverlay.setImageDrawable(null)
        previewFrameBitmap?.let { if (!it.isRecycled) it.recycle() }
        previewFrameBitmap = null
        previewFrameCacheKey = null
        previewFrameOverlay.setBackgroundColor(Color.BLACK)
        previewFrameOverlay.visibility = View.VISIBLE
        resetPreviewFrameTransform()
        return requestId
    }

    private fun resetPreviewFrameTransform() {
        if (!::previewFrameOverlay.isInitialized) return
        previewFrameOverlay.pivotX = previewFrameOverlay.width / 2f
        previewFrameOverlay.pivotY = previewFrameOverlay.height / 2f
        previewFrameOverlay.scaleX = 1f
        previewFrameOverlay.scaleY = 1f
        previewFrameOverlay.rotation = 0f
        previewFrameOverlay.translationX = 0f
        previewFrameOverlay.translationY = 0f
    }

    private fun applyPreviewFrameTransform(clip: Clip?) {
        if (!::previewFrameOverlay.isInitialized) return
        if (clip == null) {
            resetPreviewFrameTransform()
            return
        }
        normalizeClipTransform(clip)
        previewFrameOverlay.pivotX = previewFrameOverlay.width / 2f
        previewFrameOverlay.pivotY = previewFrameOverlay.height / 2f
        previewFrameOverlay.scaleX = clip.transformScale
        previewFrameOverlay.scaleY = clip.transformScale
        previewFrameOverlay.rotation = clip.transformRotationDegrees
        previewFrameOverlay.translationX = clip.transformTranslateX * previewFrameOverlay.width / 2f
        previewFrameOverlay.translationY = clip.transformTranslateY * previewFrameOverlay.height / 2f
    }

    /**
     * Decode a full-quality still for the exact project timestamp under the fixed
     * playhead. The cover stays black until that timestamp's own frame is ready;
     * no previous/nearby frame is shown as though it were current.
     */
    private fun requestPreviewFrameAt(
        projectTimeMs: Long,
        keepPreviousFrame: Boolean = false
    ) {
        if (!::previewFrameOverlay.isInitialized) return
        if (clips.isEmpty()) {
            hidePreviewFrameOverlay()
            return
        }
        val requestId = showPreviewFramePending(keepPreviousFrame)

        val lastFrameMs = (totalOutputDurationMs() - 1L).coerceAtLeast(0L)
        val safeProjectTimeMs = projectTimeMs.coerceIn(0L, lastFrameMs)
        val activeIndex = activeClipIndexAt(safeProjectTimeMs)
        val clip = clips.getOrNull(activeIndex ?: -1)
        if (clip == null) return // A timeline gap is intentionally rendered as black.

        val segment = TimelineVideoSegment(
            clip.timelineStartMs,
            clip.startMs,
            clip.endMs,
            clip.durationMs,
            clip.speed
        )
        val sourceTimeMs = TimelineVideoMath.sourceTimeAtProjectTime(segment, safeProjectTimeMs)
            ?: return
        // Show a nearby filmstrip frame immediately while the exact preview frame
        // is decoded. This avoids leaving the large canvas visibly frozen behind the
        // moving playhead. Copy cached pixels because the preview owns/recycles its bitmap.
        val sourcePrefix = "${clip.uri}#"
        val nearestCached = timelineThumbnailCache.snapshot().entries.mapNotNull { entry ->
            if (!entry.key.startsWith(sourcePrefix)) return@mapNotNull null
            val cachedTimeMs = entry.key.removePrefix(sourcePrefix).toLongOrNull()
                ?: return@mapNotNull null
            val distanceMs = kotlin.math.abs(cachedTimeMs - sourceTimeMs)
            if (distanceMs > 500L || entry.value.isRecycled) return@mapNotNull null
            Triple(distanceMs, cachedTimeMs, entry.value)
        }.minByOrNull { it.first }
        val nearestCacheKey = nearestCached?.let { clip.uri.toString() + "#" + it.second }
        if (nearestCached != null && nearestCacheKey != previewFrameCacheKey) {
            val cachedCopy = runCatching { nearestCached.third.copy(Bitmap.Config.ARGB_8888, false) }
                .getOrNull()
            if (cachedCopy != null && !cachedCopy.isRecycled) {
                val previous = previewFrameBitmap
                previewFrameBitmap = cachedCopy
                previewFrameCacheKey = nearestCacheKey
                previewFrameOverlay.setImageBitmap(cachedCopy)
                previewFrameOverlay.setBackgroundColor(Color.BLACK)
                previewFrameOverlay.visibility = View.VISIBLE
                if (previous != null && previous !== cachedCopy && !previous.isRecycled) previous.recycle()
                applyPreviewFrameTransform(clip)
                if (timelineSyncTestEnabled) android.util.Log.i(
                    "TimelineFrameSyncTest",
                    "TIMELINE_PREVIEW_CACHE_HIT requestedMs=$sourceTimeMs cachedMs=${nearestCached.second} distanceMs=${nearestCached.first}"
                )
            }
        }
        if (timelineSyncTestEnabled) android.util.Log.i(
            "TimelineFrameSyncTest",
            "TIMELINE_PREVIEW_FRAME_REQUEST projectMs=$safeProjectTimeMs sourceMs=$sourceTimeMs"
        )
        applyPreviewFrameTransform(clip)

        val task = TimelineThumbnailTask(
            -1L,
            timelineThumbnailTaskSequence.incrementAndGet()
        ) {
            if (requestId != previewFrameRequestGeneration.get()) return@TimelineThumbnailTask
            val decoded = decodeTimelinePreviewFrame(clip.uri, sourceTimeMs)
            runOnUiThread {
                if (requestId != previewFrameRequestGeneration.get() || isFinishing || isDestroyed) {
                    decoded?.let { if (!it.isRecycled) it.recycle() }
                    return@runOnUiThread
                }
                pendingPreviewFrameTask = null
                if (decoded != null && !decoded.isRecycled) {
                    val previous = previewFrameBitmap
                    previewFrameBitmap = decoded
                    previewFrameCacheKey = null
                    previewFrameOverlay.setImageBitmap(decoded)
                    if (previous !== decoded && previous != null && !previous.isRecycled) previous.recycle()
                    previewFrameOverlay.setBackgroundColor(Color.BLACK)
                    previewFrameOverlay.visibility = View.VISIBLE
                    applyPreviewFrameTransform(clip)
                    if (timelineSyncTestEnabled) {
                        android.util.Log.i(
                            "TimelineFrameSyncTest",
                            "TIMELINE_PREVIEW_FRAME_READY projectMs=$safeProjectTimeMs sourceMs=$sourceTimeMs width=${decoded.width} height=${decoded.height}"
                        )
                        if (keepPreviousFrame) android.util.Log.i(
                            "TimelineFrameSyncTest",
                            "TIMELINE_SCRUB_PREVIEW_FRAME_READY projectMs=$safeProjectTimeMs sourceMs=$sourceTimeMs"
                        )
                    }
                } else {
                    if (timelineSyncTestEnabled) android.util.Log.e(
                        "TimelineFrameSyncTest",
                        "TIMELINE_PREVIEW_FRAME_FAILED projectMs=$safeProjectTimeMs sourceMs=$sourceTimeMs"
                    )
                    // When pausing, preserve the last rendered frame if decoding
                    // the exact still fails; never replace a valid preview with black.
                    if (!keepPreviousFrame) {
                        previewFrameOverlay.setImageDrawable(null)
                        previewFrameOverlay.setBackgroundColor(Color.BLACK)
                        previewFrameOverlay.visibility = View.VISIBLE
                    }
                }
            }
        }
        pendingPreviewFrameTask = task
        try {
            timelineThumbnailExecutor.execute(task)
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            pendingPreviewFrameTask = null
        }
    }

    /** Uses the same source URI and project-to-source timestamp mapping as the filmstrip. */
    private fun decodeTimelinePreviewFrame(uri: Uri, timeMs: Long): Bitmap? {
        val frameTimeUs = timeMs.coerceAtLeast(0L) * 1000L
        val retriever = timelineThumbnailRetrieverFor(uri, "TimelinePreviewFrame")

        val frame = retriever?.let { active ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                runCatching {
                    active.getScaledFrameAtTime(
                        frameTimeUs,
                        MediaMetadataRetriever.OPTION_CLOSEST,
                        1440,
                        1440
                    )
                }.getOrNull()
            } else {
                runCatching {
                    active.getFrameAtTime(frameTimeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                }.getOrNull()
            }
        }
        if (frame != null) return frame

        var extractor: FrameExtractor? = null
        var decoded: Bitmap? = null
        var future: java.util.concurrent.Future<FrameExtractor.Frame>? = null
        try {
            val created = FrameExtractor.Builder(this, MediaItem.fromUri(uri)).build()
            extractor = created
            val frameFuture = created.getFrame(timeMs.coerceAtLeast(0L))
            future = frameFuture
            decoded = frameFuture.get(2500L, TimeUnit.MILLISECONDS).bitmap
        } catch (error: Exception) {
            future?.cancel(true)
            android.util.Log.w("TimelinePreviewFrame", "Frame decode failed at ${timeMs}ms", error)
        } finally {
            runCatching { extractor?.close() }
        }
        return decoded?.let { original ->
            val maxSide = 1440
            val largest = maxOf(original.width, original.height)
            if (largest <= maxSide) original
            else {
                val scale = maxSide.toFloat() / largest.toFloat()
                val scaled = Bitmap.createScaledBitmap(
                    original,
                    (original.width * scale).toInt().coerceAtLeast(1),
                    (original.height * scale).toInt().coerceAtLeast(1),
                    true
                )
                if (scaled !== original && !original.isRecycled) original.recycle()
                scaled
            }
        }
    }

    private fun decodeTimelineFrame(uri: Uri, timeMs: Long): Bitmap? {
        val frameTimeUs = timeMs.coerceAtLeast(0L) * 1000L
        val retriever = timelineThumbnailRetrieverFor(uri, "TimelineThumbnail")

        val platformFrame = retriever?.let { activeRetriever ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                runCatching {
                    activeRetriever.getScaledFrameAtTime(
                        frameTimeUs,
                        MediaMetadataRetriever.OPTION_CLOSEST,
                        162,
                        288
                    )
                }.getOrNull()
            } else {
                null
            } ?: runCatching {
                activeRetriever.getFrameAtTime(frameTimeUs, MediaMetadataRetriever.OPTION_CLOSEST)
            }.getOrNull()
        }
        if (platformFrame != null) return platformFrame

        // Reuse one Media3 extractor per decoder worker and source URI. Building
        // and closing an extractor for every cell made fallback decoding needlessly slow.
        val extractor = timelineThumbnailExtractorFor(uri) ?: return null
        var frameFuture: java.util.concurrent.Future<FrameExtractor.Frame>? = null
        return try {
            val future = extractor.getFrame(timeMs.coerceAtLeast(0L))
            frameFuture = future
            future.get(2500L, TimeUnit.MILLISECONDS).bitmap
        } catch (error: Exception) {
            frameFuture?.cancel(true)
            android.util.Log.w("TimelineThumbnail", "No frame decoded for $uri at ${timeMs}ms", error)
            null
        }
    }

    private fun requestTimelinePoster(uri: Uri) {
        val cacheKey = uri.toString()
        val cached = timelinePosterCache.get(cacheKey)
        if (cached != null && !cached.isRecycled) return
        if (!pendingTimelinePosterLoads.add(cacheKey)) return

        try {
            timelinePosterExecutor.execute {
                val poster = runCatching { decodeTimelinePoster(uri) }.getOrNull()
                runOnUiThread {
                    pendingTimelinePosterLoads.remove(cacheKey)
                    if (isFinishing || isDestroyed) {
                        poster?.let { if (!it.isRecycled) it.recycle() }
                        pendingTimelinePosterTargets.remove(cacheKey)
                        return@runOnUiThread
                    }
                    if (poster == null || poster.isRecycled) {
                        pendingTimelinePosterTargets.remove(cacheKey)
                        android.util.Log.w("TimelineThumbnail", "Temporary poster unavailable for $cacheKey")
                        return@runOnUiThread
                    }
                    timelinePosterCache.put(cacheKey, poster)
                    pendingTimelinePosterTargets.remove(cacheKey).orEmpty().forEach { reference ->
                        val target = reference.get() ?: return@forEach
                        val targetKey = target.tag?.toString() ?: return@forEach
                        if (targetKey.startsWith("$cacheKey#") && target.drawable == null) {
                            target.setImageBitmap(poster)
                            target.contentDescription = "Klip kapak karesi (geçici)"
                        }
                    }
                }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            pendingTimelinePosterLoads.remove(cacheKey)
        }
    }

    private fun decodeTimelinePoster(uri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val providerPoster = runCatching {
                contentResolver.loadThumbnail(
                    uri,
                    android.util.Size(dp(162), dp(96)),
                    CancellationSignal()
                )
            }.getOrNull()
            if (providerPoster != null) return providerPoster
        }

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(this, uri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(
                    0L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    dp(162).coerceAtLeast(1),
                    dp(288).coerceAtLeast(1)
                )
            } else {
                retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }
        } catch (error: Exception) {
            android.util.Log.w("TimelineThumbnail", "Temporary poster decode failed for $uri", error)
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun requestTimelineThumbnail(
        uri: Uri,
        timeMs: Long,
        target: ImageView,
        priorityDistanceMs: Long = kotlin.math.abs(timeMs - timelinePlayheadMs),
        cellStartMs: Long = timeMs,
        cellEndMs: Long = timeMs + 1L,
        usePosterPlaceholder: Boolean = false
    ) {
        val timestampMs = timeMs.coerceAtLeast(0L)
        val key = "${uri}#${timestampMs}"
        target.tag = key
        val cached = timelineThumbnailCache.get(key)
        if (cached != null && !cached.isRecycled) {
            target.setImageBitmap(cached)
            if (timelineLifecycleDiagnosticsEnabled) android.util.Log.i(
                "TimelineThumbnail",
                "TIMELINE_THUMBNAIL_CACHE_HIT sourceMs=$timestampMs cacheBytes=${timelineThumbnailCache.size()} " +
                    "attached=${target.isAttachedToWindow}"
            )
            if (timelineSyncTestEnabled) android.util.Log.i(
                "TimelineFrameSyncTest",
                "TIMELINE_THUMBNAIL_READY sourceMs=$timestampMs width=${cached.width} height=${cached.height} cached=true"
            )
            return
        }

        val showTemporaryPoster = usePosterPlaceholder && priorityDistanceMs <= 2_000L
        if (showTemporaryPoster) {
            requestTimelinePoster(uri)
            val poster = timelinePosterCache.get(uri.toString())
            if (poster != null && !poster.isRecycled) {
                target.setImageBitmap(poster)
                target.contentDescription = "Klip kapak karesi (geçici)"
            } else {
                pendingTimelinePosterTargets.getOrPut(uri.toString()) { mutableListOf() }
                    .add(WeakReference(target))
            }
        }

        // When zooming out, a newly widened cell often contains a frame that was
        // already decoded at the previous zoom level. Reuse that in-range bitmap
        // immediately instead of decoding the same section of the source again.
        // Keep the cell nearest the playhead exact so preview/filmstrip sync wins.
        if (priorityDistanceMs > 150L && cellEndMs > cellStartMs) {
            val prefix = "${uri}#"
            // A zoomed-in cell is usually narrower than the samples decoded at
            // the previous scale. Reuse the nearest cached frame even when its
            // timestamp is just outside this new cell, then decode the exact
            // sample in the background only when no sufficiently close frame exists.
            val maxReusableDistanceMs = maxOf(750L, (cellEndMs - cellStartMs) / 2L)
            val reusable = timelineThumbnailCache.snapshot().entries.mapNotNull { entry ->
                if (!entry.key.startsWith(prefix)) return@mapNotNull null
                val cachedTime = entry.key.removePrefix(prefix).toLongOrNull() ?: return@mapNotNull null
                val distanceMs = kotlin.math.abs(cachedTime - timestampMs)
                if (distanceMs > maxReusableDistanceMs) return@mapNotNull null
                val bitmap = entry.value
                if (bitmap.isRecycled) return@mapNotNull null
                Triple(distanceMs, cachedTime, bitmap)
            }.minByOrNull { it.first }
            if (reusable != null) {
                target.setImageBitmap(reusable.third)
                target.contentDescription = "Klip karesi @${reusable.second}ms (önbellek; kesin kare yükleniyor)"
                if (timelineSyncTestEnabled) android.util.Log.i(
                    "TimelineFrameSyncTest",
                    "TIMELINE_THUMBNAIL_REUSED sourceMs=${reusable.second} targetMs=$timestampMs"
                )
                // Do not return: keep the cached image visible immediately while
                // the exact sample is decoded and replaces it asynchronously.
            }
        }
        // Leave the cell neutral black until its own exact sample is decoded.
        // Reusing a nearby timestamp here made stale/partial frames look current.
        val waiting = pendingTimelineThumbnails[key]
        if (waiting != null) {
            waiting.add(WeakReference(target))
            if (timelineLifecycleDiagnosticsEnabled) android.util.Log.i(
                "TimelineThumbnail",
                "TIMELINE_THUMBNAIL_DEDUP sourceMs=$timestampMs waitingTargets=${waiting.size}"
            )
            return
        }
        pendingTimelineThumbnails[key] = mutableListOf(WeakReference(target))
        val enqueuedAtUptimeMs = SystemClock.uptimeMillis()
        if (timelineLifecycleDiagnosticsEnabled) android.util.Log.i(
            "TimelineThumbnail",
            "TIMELINE_THUMBNAIL_CACHE_MISS sourceMs=$timestampMs queueDepth=${timelineThumbnailExecutor.queue.size} " +
                "activeWorkers=${timelineThumbnailExecutor.activeCount} pendingKeys=${pendingTimelineThumbnails.size}"
        )
        val task = TimelineThumbnailTask(
            priorityDistanceMs.coerceAtLeast(0L),
            timelineThumbnailTaskSequence.incrementAndGet()
        ) {
            val taskStartedAtUptimeMs = SystemClock.uptimeMillis()
            val queueWaitMs = taskStartedAtUptimeMs - enqueuedAtUptimeMs
            var frameDecodeDurationMs = -1L
            var bitmapScaleDurationMs = 0L
            var decoded: Bitmap? = null
            try {
                val frameDecodeStartedAtUptimeMs = SystemClock.uptimeMillis()
                val original = decodeTimelineFrame(uri, timestampMs)
                val frameDecodedAtUptimeMs = SystemClock.uptimeMillis()
                frameDecodeDurationMs = frameDecodedAtUptimeMs - frameDecodeStartedAtUptimeMs
                if (original != null && !original.isRecycled &&
                    original.width > 0 && original.height > 0
                ) {
                    // Reduce the complete source frame to thumbnail bounds. Keep its
                    // original aspect ratio; never crop, pad with bars, or stretch it.
                    val scaleStartedAtUptimeMs = SystemClock.uptimeMillis()
                    val (thumbnailWidth, thumbnailHeight) = TimelineThumbnailMath.fitDimensions(
                        original.width,
                        original.height,
                        162,
                        288
                    )
                    val thumbnail = if (
                        original.width == thumbnailWidth && original.height == thumbnailHeight
                    ) {
                        original
                    } else {
                        Bitmap.createScaledBitmap(
                            original,
                            thumbnailWidth,
                            thumbnailHeight,
                            true
                        ).also { if (!original.isRecycled) original.recycle() }
                    }
                    bitmapScaleDurationMs = SystemClock.uptimeMillis() - scaleStartedAtUptimeMs
                    decoded = thumbnail
                } else {
                    original?.let { if (!it.isRecycled) it.recycle() }
                }
            } catch (error: Exception) {
                android.util.Log.w("TimelineThumbnail", "Thumbnail creation failed for $uri at ${timestampMs}ms", error)
            }

            val result = decoded
            val workerCompletedAtUptimeMs = SystemClock.uptimeMillis()
            runOnUiThread {
                val uiCallbackStartedAtUptimeMs = SystemClock.uptimeMillis()
                val uiDispatchWaitMs = uiCallbackStartedAtUptimeMs - workerCompletedAtUptimeMs
                val targets = pendingTimelineThumbnails.remove(key).orEmpty()
                var appliedTargets = 0
                if (result != null && !isFinishing && !isDestroyed) {
                    timelineThumbnailCache.put(key, result)
                    val decodedAspectRatio = result.width.toFloat() / result.height.coerceAtLeast(1).toFloat()
                    val previousAspectRatio = timelineVideoAspectRatios.putIfAbsent(uri.toString(), decodedAspectRatio)
                    if (previousAspectRatio == null &&
                        kotlin.math.abs(decodedAspectRatio - TimelineThumbnailMath.DEFAULT_SOURCE_ASPECT_RATIO) > 0.02f
                    ) {
                        // First decoded frame reveals the source's real orientation/ratio.
                        // Rebuild once with aspect-aware cell widths; all decoded frames remain cached.
                        refreshTimelineTracks()
                    }
                    if (timelineSyncTestEnabled) android.util.Log.i(
                        "TimelineFrameSyncTest",
                        "TIMELINE_THUMBNAIL_READY sourceMs=$timestampMs width=${result.width} height=${result.height} cached=false"
                    )
                    targets.forEach { ref ->
                        val image = ref.get()
                        if (image != null && image.tag == key) {
                            image.setImageBitmap(result)
                            // The first cell may temporarily show the project poster while its
                            // exact timestamped frame is decoding. Restore the authoritative
                            // sample label when that exact frame replaces the placeholder so
                            // accessibility and frame-alignment diagnostics identify the real
                            // image rather than continuing to report a temporary cover.
                            image.contentDescription = "Klip karesi @${timestampMs}ms"
                            appliedTargets++
                        }
                    }
                } else {
                    result?.recycle()
                    // Do not leave a gray-blue empty tile when a decoder rejects a frame.
                    targets.forEach { ref ->
                        val image = ref.get()
                        if (image != null && image.tag == key && image.drawable == null) {
                            image.setBackgroundColor(Color.TRANSPARENT)
                        }
                    }
                }
                if (timelineLifecycleDiagnosticsEnabled) {
                    android.util.Log.i(
                        "TimelineThumbnail",
                        "TIMELINE_THUMBNAIL_TIMING sourceMs=$timestampMs success=${result != null} " +
                            "queueWaitMs=$queueWaitMs frameDecodeMs=$frameDecodeDurationMs " +
                            "bitmapScaleMs=$bitmapScaleDurationMs uiDispatchWaitMs=$uiDispatchWaitMs " +
                            "uiApplyMs=${SystemClock.uptimeMillis() - uiCallbackStartedAtUptimeMs} " +
                            "targets=${targets.size} applied=$appliedTargets " +
                            "cacheBytes=${timelineThumbnailCache.size()} queueDepth=${timelineThumbnailExecutor.queue.size}"
                    )
                }
            }
        }
        try {
            timelineThumbnailExecutor.execute(task)
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            pendingTimelineThumbnails.remove(key)
            target.setBackgroundColor(Color.TRANSPARENT)
        }
    }

    private fun finishTrimControlGesture(){
        deferTimelineRefresh=false;finishTrimHistoryGesture()
        val clip=selectedClip()
        if(clip!=null){
            clip.coverTimeMs = clip.coverTimeMs?.coerceIn(
                clip.startMs, (clip.endMs - 1L).coerceAtLeast(clip.startMs)
            )
            timelinePreviewLoaded=false;previewSingleClip=false;pauseActivePreview();timelinePlayheadMs=clip.timelineStartMs
            ensureTrimHandlesReachable(selectedClipIndex)
            ensureTimelinePreviewLoaded();seekTimelinePreview(timelinePlayheadMs)
        }else{timelinePreviewLoaded=false;previewSingleClip=false}
        refreshTimelineAfterClipChange();loadTrimControls();updateTimelinePlayheadLabel();status.text="Kesme aralığı güncellendi."
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

    private fun updateClipSpeedControls() {
        if (!::clipSpeedSeek.isInitialized || !::clipSpeedLabel.isInitialized) return
        val clip = selectedClip()
        updatingClipSpeedControl = true
        try {
            if (clip == null) {
                clipSpeedSeek.isEnabled = false
                clipSpeedSeek.progress = 50
                clipSpeedLabel.text = "Hız: —"
            } else {
                clipSpeedSeek.isEnabled = true
                val speed = ClipSpeedMath.normalize(clip.speed)
                clipSpeedSeek.progress = (((Math.log10(speed.toDouble()) + 1.0) / 3.0) * 100.0)
                    .toInt().coerceIn(0, 100)
                clipSpeedLabel.text = "Hız: ${"%.2f".format(Locale.US, speed)}×"
            }
        } finally {
            updatingClipSpeedControl = false
        }
    }

    private fun setSelectedClipSpeed(speed: Float) {
        val clip = selectedClip() ?: run {
            status.text = "Hız ayarı için önce bir klip seç."
            return
        }
        val before = captureClipEditSnapshot()
        clip.speed = ClipSpeedMath.normalize(speed)
        finishClipSpeedEdit(before)
    }

    private fun finishClipSpeedEdit(snapshot: ClipEditSnapshot? = clipSpeedHistorySnapshot) {
        clipSpeedHistorySnapshot = null
        val before = snapshot ?: return
        if (before.clips == clips) {
            updateClipSpeedControls()
            return
        }
        val index = selectedClipIndex
        val oldClip = before.clips.getOrNull(index)
        val newClip = clips.getOrNull(index)
        if (oldClip != null && newClip != null) {
            val oldDuration = ClipSpeedMath.timelineDurationMs(oldClip.endMs - oldClip.startMs, oldClip.speed)
            val newDuration = ClipSpeedMath.timelineDurationMs(newClip.endMs - newClip.startMs, newClip.speed)
            val oldEnd = oldClip.timelineStartMs + oldDuration
            val delta = newDuration - oldDuration
            if (delta != 0L) {
                clips.forEachIndexed { otherIndex, other ->
                    if (otherIndex != index && other.timelineStartMs >= oldEnd) {
                        other.timelineStartMs = (other.timelineStartMs + delta).coerceAtLeast(0L)
                    }
                }
                val oldPlayhead = before.timelinePlayheadMs
                timelinePlayheadMs = when {
                    oldPlayhead in oldClip.timelineStartMs until oldEnd -> {
                        val sourceOffset = ClipSpeedMath.sourceOffsetMs(
                            oldPlayhead - oldClip.timelineStartMs, oldClip.speed
                        )
                        oldClip.timelineStartMs + ClipSpeedMath.timelineOffsetMs(sourceOffset, newClip.speed)
                    }
                    oldPlayhead >= oldEnd -> oldPlayhead + delta
                    else -> oldPlayhead
                }
            }
        }
        pushClipUndoSnapshot(before)
        timelinePlayheadMs = timelinePlayheadMs.coerceIn(0L, totalOutputDurationMs().coerceAtLeast(0L))
        timelinePreviewLoaded = false
        previewSingleClip = false
        refreshTimelineAfterClipChange()
        loadTrimControls()
        refreshColorGradePreview()
        updateTimelinePlayheadLabel()
        status.text = "Klip hızı güncellendi. Takip eden video klipleri zaman çizelgesindeki yeni bitişe göre kaydırıldı."
    }

    private fun loadTrimControls() {
        val clip = selectedClip()
        updateClipSpeedControls()
        if (clip == null) {
            clipCoverButton?.apply { isEnabled = false; text = "Kapak" }
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
        selectedClipLabel.text = ""
        clipCoverButton?.apply {
            isEnabled = true
            text = if (clip.coverTimeMs == null) "Kapak" else "Kapak ✓"
            contentDescription = if (clip.coverTimeMs == null) {
                "Seçili klibin kapak karesini seç"
            } else {
                "Kapak karesi seçildi: ${formatTimelineMs(clip.coverTimeMs!!)}"
            }
        }
        updateTrimLabels()
    }

    private fun updateTrimLabels() {
        val clip = selectedClip()
        startLabel.text = "Başlangıç: ${formatTimelineMs(clip?.startMs ?: 0L)}"
        endLabel.text = "Bitiş: ${formatTimelineMs(clip?.endMs ?: 0L)}"
        updateSoundControls()
        updateTextControls()
    }

    /**
     * Choose a representative source frame for this clip and optionally save it as JPG.
     * The selected frame is metadata; it never replaces timestamp-accurate filmstrip cells.
     */
    private fun showClipCoverPicker() {
        val clipIndex = selectedClipIndex
        val clip = selectedClip()
        if (clip == null) {
            status.text = "Kapak seçmek için önce zaman çizgisinden bir klip seç."
            return
        }

        pauseActivePreview()
        val firstAllowedMs = clip.startMs.coerceIn(0L, clip.durationMs)
        val lastAllowedMs = (clip.endMs - 1L).coerceIn(
            firstAllowedMs, (clip.durationMs - 1L).coerceAtLeast(firstAllowedMs)
        )
        val selectableDurationMs = (lastAllowedMs - firstAllowedMs).coerceAtLeast(1L)
        var selectedSourceTimeMs = (clip.coverTimeMs ?: firstAllowedMs).coerceIn(firstAllowedMs, lastAllowedMs)
        var previewBitmap: Bitmap? = null
        var previewRequestGeneration = 0L
        var dialogOpen = true

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(4), dp(12), dp(4))
        }
        val coverPreview = ImageView(this).apply {
            setBackgroundColor(Color.BLACK)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Seçilen kapak karesinin önizlemesi"
        }
        content.addView(coverPreview, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(210)
        ))
        val timestampLabel = label(
            "Kapak zamanı: ${formatTimelineMs(selectedSourceTimeMs)}", 12f, Color.WHITE, true
        )
        timestampLabel.setPadding(dp(2), dp(10), dp(2), dp(2))
        content.addView(timestampLabel)
        content.addView(label(
            "Kaydırarak videodan kullanmak istediğin kareyi seç.", 11f, Color.rgb(160, 177, 194), false
        ))
        val coverSeek = SeekBar(this).apply {
            max = TimelineTrimMath.progressMax(selectableDurationMs)
            progress = TimelineTrimMath.progressForTime(
                selectedSourceTimeMs - firstAllowedMs, selectableDurationMs
            )
            contentDescription = "Kapak karesi zaman seçici"
        }
        content.addView(coverSeek, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        fun requestCoverPreview() {
            val requestId = ++previewRequestGeneration
            val requestedTimeMs = selectedSourceTimeMs
            val task = TimelineThumbnailTask(
                -2L,
                timelineThumbnailTaskSequence.incrementAndGet()
            ) {
                val decoded = decodeTimelinePreviewFrame(clip.uri, requestedTimeMs)
                runOnUiThread {
                    if (!dialogOpen || requestId != previewRequestGeneration || isFinishing || isDestroyed) {
                        decoded?.let { if (!it.isRecycled) it.recycle() }
                        return@runOnUiThread
                    }
                    if (decoded != null && !decoded.isRecycled) {
                        val previous = previewBitmap
                        previewBitmap = decoded
                        coverPreview.setImageBitmap(decoded)
                        if (previous !== decoded && previous != null && !previous.isRecycled) previous.recycle()
                    } else {
                        timestampLabel.text = "Bu kare okunamadı: ${formatTimelineMs(requestedTimeMs)}"
                    }
                }
            }
            try {
                timelineThumbnailExecutor.execute(task)
            } catch (_: java.util.concurrent.RejectedExecutionException) {
                timestampLabel.text = "Kapak karesi çözücü şu anda meşgul."
            }
        }

        coverSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                selectedSourceTimeMs = (firstAllowedMs + TimelineTrimMath.timeForProgress(
                    progress, selectableDurationMs
                )).coerceIn(firstAllowedMs, lastAllowedMs)
                timestampLabel.text = "Kapak zamanı: ${formatTimelineMs(selectedSourceTimeMs)}"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) { requestCoverPreview() }
        })

        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("Kapak karesi")
            .setView(content)
            .setNegativeButton("Vazgeç", null)
            .setNeutralButton("JPG kaydet", null)
            .setPositiveButton("Kapak yap", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (clips.getOrNull(clipIndex) !== clip) {
                    status.text = "Seçili klip değişti. Kapak seçimini yeniden aç."
                    dialog.dismiss()
                    return@setOnClickListener
                }
                val bitmap = previewBitmap
                if (bitmap == null || bitmap.isRecycled) {
                    status.text = "Kapak karesi henüz hazır değil; görüntünün yüklenmesini bekle."
                    requestCoverPreview()
                    return@setOnClickListener
                }
                recordClipEdit()
                clip.coverTimeMs = selectedSourceTimeMs
                loadTrimControls()
                // “Kapak yap” now produces the visible JPG as well as persisting
                // the selected source timestamp in the project.
                saveClipCoverImage(bitmap, clip, selectedSourceTimeMs)
                dialog.dismiss()
            }
            dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                val bitmap = previewBitmap
                if (bitmap == null || bitmap.isRecycled) {
                    status.text = "Önce seçilen kapak karesinin önizlemesi yüklensin."
                    return@setOnClickListener
                }
                saveClipCoverImage(bitmap, clip, selectedSourceTimeMs)
            }
            requestCoverPreview()
        }
        dialog.setOnDismissListener {
            dialogOpen = false
            previewRequestGeneration++
            coverPreview.setImageDrawable(null)
            previewBitmap?.let { if (!it.isRecycled) it.recycle() }
            previewBitmap = null
        }
        dialog.show()
    }

    private fun saveClipCoverImage(bitmap: Bitmap, clip: Clip, sourceTimeMs: Long) {
        val safeTitle = clip.title.substringBeforeLast('.', clip.title)
            .replace(Regex("[^A-Za-z0-9_-]+"), "_")
            .trim('_')
            .take(36)
            .ifBlank { "MathMotorVideo" }
        val fileName = "${safeTitle}_Kapak_${sourceTimeMs}.jpg"

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val copy = runCatching { bitmap.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull()
            if (copy == null) {
                status.text = "Kapak görseli belleğe kopyalanamadı."
                return
            }
            pendingCoverSaveBitmap?.let { if (!it.isRecycled) it.recycle() }
            pendingCoverSaveBitmap = copy
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/jpeg"
                putExtra(Intent.EXTRA_TITLE, fileName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            try {
                startActivityForResult(intent, REQUEST_SAVE_COVER_IMAGE)
                status.text = "Kapak görselini kaydedeceğin klasörü seç."
            } catch (error: Exception) {
                pendingCoverSaveBitmap = null
                if (!copy.isRecycled) copy.recycle()
                status.text = "Kapak kaydetme penceresi açılamadı: ${error.message ?: error.javaClass.simpleName}"
            }
            return
        }

        var outputUri: Uri? = null
        try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/MathMotor/Kapaklar")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            outputUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Galeriye kayıt konumu oluşturulamadı.")
            val stream = contentResolver.openOutputStream(outputUri)
                ?: throw IllegalStateException("Kapak görseli dosyası açılamadı.")
            stream.use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 94, output)) {
                    throw IllegalStateException("JPEG kapak görseli oluşturulamadı.")
                }
            }
            contentResolver.update(outputUri, ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }, null, null)
            android.util.Log.i("ClipCover", "COVER_JPG_SAVED sourceMs=${sourceTimeMs} uri=${outputUri}")
            status.text = "Kapak görseli Galeri > Resimler > MathMotor > Kapaklar içine kaydedildi."
        } catch (error: Exception) {
            outputUri?.let { runCatching { contentResolver.delete(it, null, null) } }
            status.text = "Kapak görseli kaydedilemedi: ${error.message ?: error.javaClass.simpleName}"
        }
    }

    private fun savePendingCoverImage(uri: Uri?) {
        val bitmap = pendingCoverSaveBitmap
        pendingCoverSaveBitmap = null
        if (bitmap == null || bitmap.isRecycled) {
            status.text = "Kaydedilecek kapak görseli bulunamadı."
            return
        }
        try {
            if (uri == null) throw IllegalStateException("Kayıt konumu seçilmedi.")
            val output = contentResolver.openOutputStream(uri)
                ?: throw IllegalStateException("Seçilen dosya açılamadı.")
            output.use { stream ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 94, stream)) {
                    throw IllegalStateException("JPEG kapak görseli oluşturulamadı.")
                }
            }
            android.util.Log.i("ClipCover", "COVER_JPG_SAVED uri=$uri")
            status.text = "Kapak görseli seçtiğin klasöre kaydedildi."
        } catch (error: Exception) {
            status.text = "Kapak görseli kaydedilemedi: ${error.message ?: error.javaClass.simpleName}"
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }


    private fun videoTimelineSegments(): List<TimelineVideoSegment> =
        clips.map { TimelineVideoSegment(it.timelineStartMs,it.startMs,it.endMs,it.durationMs,it.speed) }

    private fun activeClipIndexAt(projectTimeMs:Long):Int? =
        TimelineVideoMath.activeSegmentIndexAt(videoTimelineSegments(),projectTimeMs)

    /** Snap only while the playhead is over an actual clip; timeline gaps remain exact gaps. */
    private fun snapTimelineTimeToSourceFrame(projectTimeMs: Long): Long {
        val index = activeClipIndexAt(projectTimeMs) ?: return projectTimeMs
        val clip = clips.getOrNull(index) ?: return projectTimeMs
        val fps = clip.frameRate?.takeIf { it.isFinite() && it in 1.0..240.0 } ?: return projectTimeMs
        val sourceTimeMs = clip.startMs + ClipSpeedMath.sourceOffsetMs(
            (projectTimeMs - clip.timelineStartMs).coerceAtLeast(0L), clip.speed
        )
        val snappedSourceMs = TimelineScaleMath.snapTimeToFrameMs(sourceTimeMs, fps)
            .coerceIn(clip.startMs, (clip.endMs - 1L).coerceAtLeast(clip.startMs))
        return (clip.timelineStartMs + ClipSpeedMath.timelineOffsetMs(snappedSourceMs - clip.startMs, clip.speed))
            .coerceIn(clip.timelineStartMs, clip.timelineStartMs + ClipSpeedMath.timelineDurationMs(clip.endMs - clip.startMs, clip.speed))
    }

    /** Older drafts did not persist FPS; read it off the original media without blocking the UI. */
    private fun loadMissingClipFrameRates() {
        val missingUris = clips.filter { it.frameRate == null }.map { it.uri }.distinct()
        if (missingUris.isEmpty()) return
        Thread {
            val loadedRates = missingUris.associateWith { uri ->
                readSourceFrameRate(uri)
            }
            runOnUiThread {
                var changed = false
                clips.filter { it.frameRate == null }.forEach { clip ->
                    val rate = loadedRates[clip.uri]
                    if (rate != null) {
                        clip.frameRate = rate
                        changed = true
                    }
                }
                if (changed && ::timelineTracks.isInitialized) refreshTimelineTracks()
            }
        }.start()
    }

    private fun readSourceFrameRate(uri: Uri): Double? {
        val capturedRate = runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(this, uri)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                    ?.toDoubleOrNull()?.takeIf { it.isFinite() && it in 1.0..240.0 }
            } finally {
                retriever.release()
            }
        }.getOrNull()
        return capturedRate ?: readFrameRateFromMediaExtractor(uri)
    }

    private fun readFrameRateFromMediaExtractor(uri: Uri): Double? {
        val extractor = android.media.MediaExtractor()
        return try {
            extractor.setDataSource(this, uri, null)
            (0 until extractor.trackCount).firstNotNullOfOrNull { index ->
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(android.media.MediaFormat.KEY_MIME).orEmpty()
                if (!mime.startsWith("video/") ||
                    !format.containsKey(android.media.MediaFormat.KEY_FRAME_RATE)
                ) {
                    null
                } else {
                    runCatching {
                        format.getInteger(android.media.MediaFormat.KEY_FRAME_RATE).toDouble()
                    }.getOrNull()?.takeIf { it.isFinite() && it in 1.0..240.0 }
                }
            }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun totalOutputDurationMs(): Long =
        maxOf(
            TimelineProjectDurationMath.totalDuration(
                videoEndMs = TimelineVideoMath.totalDuration(videoTimelineSegments()),
                textLayerEnds = textLayers.map { layer ->
                    TimelineLayerEnd(layer.endMs, followsProjectDuration = !layer.timingCustomized)
                },
                musicEndMs = if (musicUri != null) musicStartMs + musicDurationMs else 0L,
                soundEndMs = if (soundUri != null) soundStartMs + soundDurationMs else 0L
            ),
            overlayClips.maxOfOrNull { it.timelineStartMs + it.durationMs } ?: 0L
        )

    private fun updateSoundControls() {
        if (!::soundStartSeek.isInitialized || !::soundStartLabel.isInitialized) return
        val total = totalOutputDurationMs()
        if (soundUri != null) {
            val segment = TimelineAudioMath.normalize(
                TimelineAudioSegment(soundStartMs, soundSourceStartMs, soundDurationMs),
                soundSourceDurationMs,
                total
            )
            soundStartMs = segment.timelineStartMs
            soundSourceStartMs = segment.sourceStartMs
            soundDurationMs = segment.durationMs
        }
        val latestStart = (total - soundDurationMs).coerceAtLeast(0L)
        soundStartSeek.max = TimelineTrimMath.progressMax(latestStart)
        soundStartMs = soundStartMs.coerceIn(0L, latestStart)
        soundStartSeek.progress = TimelineTrimMath.progressForTime(soundStartMs, latestStart)
        soundStartSeek.isEnabled = soundUri != null && total > 0L
        val soundEndMs = soundStartMs + soundDurationMs
        soundStartLabel.text = "Ses: ${formatTimelineMs(soundStartMs)} – ${formatTimelineMs(soundEndMs)}"
    }

    private fun previewSelectedClip(){
        val clip=selectedClip()
        if(clip==null){status.text="Önizleme için önce video seç.";return}
        timelinePreviewLoaded=false;previewSingleClip=false;timelinePlayheadMs=clip.timelineStartMs
        updateTimelinePlayheadLabel();ensureTimelinePreviewLoaded();seekTimelinePreview(timelinePlayheadMs)
        hidePreviewFrameOverlay()
        timelineCompositionPlayer?.playWhenReady=true
        status.text="Seçili klibin dönüşüm ve kesme ayarlarıyla önizlemesi başladı."
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
        normalizeClipTransform(clip)
        // Capture immutable values for this composition. Gesture-time preview uses a
        // temporary view transform; rebuilding then installs the committed Media3 effect.
        val transformScale = clip.transformScale
        val transformRotationDegrees = clip.transformRotationDegrees
        val transformTranslateX = clip.transformTranslateX
        val transformTranslateY = clip.transformTranslateY
        val videoEffects = if (
            transformScale != 1f ||
            transformRotationDegrees != 0f ||
            transformTranslateX != 0f ||
            transformTranslateY != 0f
        ) {
            listOf<androidx.media3.common.Effect>(
                MatrixTransformation { _ ->
                    Matrix().apply {
                        // Media3 uses normalized device coordinates (-1..1); positive Y is up.
                        setValues(ClipTransformMath.matrixValues(
                            ClipTransformValues(
                                transformScale,
                                transformRotationDegrees,
                                transformTranslateX,
                                transformTranslateY
                            )
                        ))
                    }
                }
            )
        } else emptyList()
        val speed = ClipSpeedMath.normalize(clip.speed)
        val speedProvider = object : androidx.media3.common.audio.SpeedProvider {
            override fun getSpeed(timeUs: Long): Float = speed
            override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = androidx.media3.common.C.TIME_UNSET
        }
        return EditedMediaItem.Builder(mediaItem)
            .setDurationUs(clip.durationMs * 1000L)
            .setSpeed(speedProvider)
            .setEffects(Effects(audio, videoEffects))
            .apply { if (!keepSourceAudio.isChecked) setRemoveAudio(true) }
            .build()
    }

    private fun buildTimedTextOverlay(layer: TextLayer, samePositionIndex: Int): TextOverlay {
        val overlayText = layer.text
        val startMs = layer.startMs
        val endMs = layer.endMs
        val styled = SpannableString(overlayText).apply {
            setSpan(ForegroundColorSpan(layer.colorPreset.colorInt), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (layer.kind != TextLayerKind.STICKER) {
                setSpan(BackgroundColorSpan(Color.argb(190, 0, 0, 0)), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
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
        return object : TextOverlay() {
            override fun getText(presentationTimeUs: Long): SpannableString {
                val timeMs = presentationTimeUs / 1000L
                return if (timeMs >= startMs && timeMs < endMs) SpannableString(styled) else SpannableString("")
            }

            override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
                val timeMs = presentationTimeUs / 1_000L
                val animation = TextAnimationMath.stateAt(
                    layer.animationPreset,
                    timeMs - startMs,
                    endMs - timeMs,
                    layer.animationDurationMs
                )
                return StaticOverlaySettings.Builder()
                    .setAlphaScale(animation.alpha)
                    .setScale(animation.scale, animation.scale)
                    .setBackgroundFrameAnchor(
                        (x + animation.offsetXFraction * 2f).coerceIn(-0.92f, 0.92f),
                        (y - animation.offsetYFraction * 2f).coerceIn(-0.85f, 0.85f)
                    )
                    .setOverlayFrameAnchor(0f, 0f)
                    .build()
            }
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
        val videoSequences = try {
            buildVideoCompositionSequences()
        } catch (e: Exception) {
            status.text = "Düzenleme hazırlanamadı: ${e.message ?: e.javaClass.simpleName}"
            return
        }

        exporting = true
        pauseActivePreview()
        musicPreviewPlayer?.pause()
        soundPreviewPlayer?.pause()
        exportButton.isEnabled = false
        status.text = "MP4 dışa aktarılıyor…\nKlipler seçtiğin sırayla birleştiriliyor; yazı, müzik ve ses katmanları uygulanıyor. Uygulamayı kapatma."
        val tempOutput = File(cacheDir, "mm_editor_${System.currentTimeMillis()}.mp4")
        val compositionSequences = videoSequences.toMutableList()
        musicUri?.let { uri ->
            val outputDurationMs = totalOutputDurationMs()
            val musicRange = TimelineRangeMath.normalize(
                TimelineRange(musicStartMs, musicDurationMs),
                outputDurationMs
            )
            val sourceDurationMs = musicSourceDurationMs
            val sourceRange = MusicSourceRangeMath.normalize(MusicSourceRange(musicSourceStartMs, musicSourceEndMs.takeIf { it > 0L } ?: sourceDurationMs), sourceDurationMs)
            if (musicRange.durationMs > 0L && (sourceDurationMs <= 0L || sourceRange.durationMs <= 0L)) {
                exporting = false
                exportButton.isEnabled = true
                status.text = "Müzik dosyasının süresi okunamadı. Projeyi yeniden açıp tekrar dene."
                return
            }
            if (musicRange.durationMs > 0L) {
                val sequenceBuilder = EditedMediaItemSequence.Builder(
                    setOf(androidx.media3.common.C.TRACK_TYPE_AUDIO)
                ).experimentalSetForceAudioTrack(true)
                if (musicRange.startMs > 0L) sequenceBuilder.addGap(musicRange.startMs * 1000L)
                var remainingMs = musicRange.durationMs
                while (remainingMs > 0L) {
                    val chunkMs = minOf(sourceRange.durationMs, remainingMs)
                    val musicItem = MediaItem.Builder()
                        .setUri(uri)
                        .setClippingConfiguration(
                            MediaItem.ClippingConfiguration.Builder()
                                .setStartPositionMs(sourceRange.startMs)
                                .setEndPositionMs(sourceRange.startMs + chunkMs)
                                .build()
                        )
                        .build()
                    sequenceBuilder.addItem(
                        EditedMediaItem.Builder(musicItem)
                            .setRemoveVideo(true)
                            .setEffects(Effects(audioEffects(musicVolume), emptyList()))
                            .build()
                    )
                    remainingMs -= chunkMs
                }
                val trailingGapMs = (outputDurationMs - musicRange.startMs - musicRange.durationMs)
                    .coerceAtLeast(0L)
                if (trailingGapMs > 0L) sequenceBuilder.addGap(trailingGapMs * 1000L)
                compositionSequences.add(sequenceBuilder.build())
            }
        }
        soundUri?.let { uri ->
            val outputDurationMs = totalOutputDurationMs()
            val offsetMs = soundStartMs.coerceAtMost((outputDurationMs - 1L).coerceAtLeast(0L))
            val remainingMs = (outputDurationMs - offsetMs).coerceAtLeast(1L)
            val soundLengthMs = soundDurationMs.coerceAtLeast(1L).coerceAtMost(remainingMs)
            val safeSourceStartMs = soundSourceStartMs.coerceAtMost(
                (soundSourceDurationMs - soundLengthMs).coerceAtLeast(0L)
            )
            val soundMediaItem = MediaItem.Builder()
                .setUri(uri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(safeSourceStartMs)
                        .setEndPositionMs(safeSourceStartMs + soundLengthMs)
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
        if (overlayClips.isNotEmpty()) {
            compositionBuilder.setVideoCompositorSettings(buildOverlayCompositorSettings())
        }
        val outputVideoEffects = mutableListOf<androidx.media3.common.Effect>()
        outputVideoEffects.addAll(buildEditorVideoEffects())
        exportCanvasPreset.aspectRatio?.let { ratio ->
            outputVideoEffects.add(Presentation.createForAspectRatio(
                ratio, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
            ))
        }
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
        timelineLifecycleState = "stopped"
        logTimelineLifecycle("onStop")
        saveLocalDraftIfNeeded()
        pauseActivePreview()
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
        timelineLifecycleState = "destroyed"
        logTimelineLifecycle("onDestroy")
        timelineHandler.removeCallbacks(timelineSyncTask)
        timelineHandler.removeCallbacks(textTimelineRefreshTask)
        timelineHandler.removeCallbacks(timelineScrubPreviewSeekTask)
        pendingTimelineScrubPreviewTargetMs = null
        previewFrameRequestGeneration.incrementAndGet()
        pendingPreviewFrameTask?.let { timelineThumbnailExecutor.remove(it) }
        pendingPreviewFrameTask = null
        if (::previewFrameOverlay.isInitialized) previewFrameOverlay.setImageDrawable(null)
        previewFrameBitmap?.let { if (!it.isRecycled) it.recycle() }
        previewFrameBitmap = null
        // Drop queued thumbnail work. Each decoder worker releases its own retriever
        // in the worker-thread finally block when the executor shuts down.
        pendingCoverSaveBitmap?.let { if (!it.isRecycled) it.recycle() }
        pendingCoverSaveBitmap = null
        timelineThumbnailExecutor.queue.clear()
        timelineThumbnailExecutor.shutdownNow()
        timelinePosterExecutor.shutdownNow()
        pendingTimelineThumbnails.clear()
        pendingTimelinePosterTargets.clear()
        pendingTimelinePosterLoads.clear()
        timelinePosterCache.evictAll()
        timelineThumbnailCache.evictAll()
        playerView.player = null
        player?.release()
        player = null
        timelineCompositionPlayer?.release()
        timelineCompositionPlayer = null
        musicPreviewPlayer?.release()
        musicPreviewPlayer = null
        soundPreviewPlayer?.release()
        soundPreviewPlayer = null
        super.onDestroy()
    }
}

package com.mathmotor.video

import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Converts between the timeline's shared time axis and screen pixels.
 * All block boundaries are calculated from absolute times, avoiding accumulated
 * rounding differences when many clips or tracks are placed consecutively.
 */
internal object TimelineScaleMath {
    /** Leading spacer that places time zero beneath the full timeline/Play center. */
    fun originSpacerWidthForCenteredPlayhead(frameWidthPx: Int, gutterPx: Int): Int =
        (frameWidthPx.coerceAtLeast(0) / 2 - gutterPx.coerceAtLeast(0)).coerceAtLeast(1)

    /** Trailing spacer keeps the final project frame reachable beneath the same center line. */
    fun endSpacerWidthForCenteredPlayhead(
        frameWidthPx: Int,
        gutterPx: Int,
        trailingInsetPx: Int = 0
    ): Int {
        val viewportWidth = (
            frameWidthPx.coerceAtLeast(0) -
                gutterPx.coerceAtLeast(0) -
                trailingInsetPx.coerceAtLeast(0)
            ).coerceAtLeast(0)
        val originWidth = originSpacerWidthForCenteredPlayhead(frameWidthPx, gutterPx)
        return (viewportWidth - originWidth).coerceAtLeast(1)
    }

    fun pixelsForTime(timeMs: Long, pixelsPerSecond: Float): Int {
        if (timeMs <= 0L || !pixelsPerSecond.isFinite() || pixelsPerSecond <= 0f) return 0
        return ((timeMs.toDouble() / 1000.0) * pixelsPerSecond.toDouble())
            .roundToInt()
            .coerceAtLeast(0)
    }

    fun widthBetween(startMs: Long, endMs: Long, pixelsPerSecond: Float): Int {
        val start = startMs.coerceAtLeast(0L)
        val end = endMs.coerceAtLeast(start)
        return (pixelsForTime(end, pixelsPerSecond) -
            pixelsForTime(start, pixelsPerSecond)).coerceAtLeast(0)
    }

    fun millisecondsForPixels(offsetPixels: Float, pixelsPerSecond: Float): Long {
        if (!offsetPixels.isFinite() || !pixelsPerSecond.isFinite() || pixelsPerSecond <= 0f) return 0L
        return ((offsetPixels.toDouble() / pixelsPerSecond.toDouble()) * 1000.0)
            .roundToLong()
    }

    /** Deterministic fit scale for a project duration and measured timeline width. */
    fun fitPixelsPerSecond(
        durationMs: Long,
        availableWidthPx: Int,
        minimumPxPerSecond: Float,
        maximumPxPerSecond: Float
    ): Float {
        if (durationMs <= 0L || availableWidthPx <= 0) return minimumPxPerSecond.coerceAtLeast(0f)
        val raw = availableWidthPx.toDouble() * 1000.0 / durationMs.toDouble()
        val minimum = minimumPxPerSecond.coerceAtLeast(0f)
        val maximum = maximumPxPerSecond.coerceAtLeast(minimum)
        return raw.toFloat().coerceIn(minimum, maximum)
    }

    /** Select a readable seconds/subseconds ruler interval for the current zoom. */
    fun rulerStepMs(pixelsPerSecond: Float, minimumLabelSpacingPx: Float): Long {
        if (!pixelsPerSecond.isFinite() || pixelsPerSecond <= 0f) return 1_000L
        val targetMs = (
            minimumLabelSpacingPx.coerceAtLeast(1f).toDouble() /
                pixelsPerSecond.toDouble() * 1_000.0
            ).coerceAtLeast(1.0)
        val niceStepsMs = longArrayOf(
            10L, 20L, 50L, 100L, 200L, 250L, 500L,
            1_000L, 2_000L, 3_000L, 5_000L, 10_000L,
            15_000L, 30_000L, 60_000L, 120_000L, 300_000L, 600_000L
        )
        return niceStepsMs.firstOrNull { it.toDouble() >= targetMs }
            ?: (kotlin.math.ceil(targetMs / 600_000.0) * 600_000.0).toLong().coerceAtLeast(1L)
    }

    data class RulerGrid(
        val intervalMs: Long,
        val frameStep: Long? = null,
        val frameRate: Double? = null
    )

    /**
     * Seconds become readable subdivisions as zoom increases. At close zoom the
     * same ruler switches to source-frame units. Frame timestamps are calculated
     * from the absolute frame index, avoiding cumulative drift at 29.97 fps.
     */
    fun rulerGrid(
        pixelsPerSecond: Float,
        minimumLabelSpacingPx: Float,
        sourceFrameRate: Double?
    ): RulerGrid {
        val fps = sourceFrameRate?.takeIf { it.isFinite() && it in 1.0..240.0 }
        if (fps != null && pixelsPerSecond.isFinite() && pixelsPerSecond > 0f) {
            val targetFrames = (
                minimumLabelSpacingPx.coerceAtLeast(1f).toDouble() * fps /
                    pixelsPerSecond.toDouble()
                ).coerceAtLeast(1.0)
            val frameStep = niceFrameStep(kotlin.math.ceil(targetFrames).toLong())
            val intervalMs = frameTimeMs(frameStep, fps).coerceAtLeast(1L)
            if (intervalMs <= 500L) {
                return RulerGrid(intervalMs, frameStep, fps)
            }
        }
        return RulerGrid(rulerStepMs(pixelsPerSecond, minimumLabelSpacingPx))
    }

    private fun niceFrameStep(target: Long): Long {
        val safeTarget = target.coerceAtLeast(1L)
        val nice = longArrayOf(1L, 2L, 3L, 4L, 5L, 6L, 8L, 10L, 12L, 15L, 20L, 24L, 30L, 40L, 48L, 60L)
        return nice.firstOrNull { it >= safeTarget }
            ?: (kotlin.math.ceil(safeTarget / 60.0) * 60.0).toLong().coerceAtLeast(1L)
    }

    /** Exact frame-index to timestamp conversion; do not repeatedly add a rounded frame duration. */
    fun frameTimeMs(frameIndex: Long, framesPerSecond: Double): Long {
        if (!framesPerSecond.isFinite() || framesPerSecond <= 0.0) return 0L
        return (frameIndex.coerceAtLeast(0L).toDouble() * 1_000.0 / framesPerSecond).roundToLong()
    }

    fun frameIndexAtTimeMs(timeMs: Long, framesPerSecond: Double): Long {
        if (!framesPerSecond.isFinite() || framesPerSecond <= 0.0) return 0L
        return (timeMs.coerceAtLeast(0L).toDouble() * framesPerSecond / 1_000.0).roundToLong()
    }

    fun snapTimeToFrameMs(timeMs: Long, framesPerSecond: Double): Long =
        frameTimeMs(frameIndexAtTimeMs(timeMs, framesPerSecond), framesPerSecond)

    /** Pixel offsets for evenly spaced minor divisions between two major ruler ticks. */
    fun rulerMinorTickOffsetsPx(intervalWidthPx: Int, divisionCount: Int = 5): List<Int> {
        val width = intervalWidthPx.coerceAtLeast(0)
        val divisions = divisionCount.coerceAtLeast(2)
        return (1 until divisions).map { division -> width * division / divisions }
            .filter { it in 1 until width }
            .distinct()
    }

    /** CapCut-style ruler labels: clock time on whole seconds, frame index between them. */
    fun formatRulerTickLabel(timeMs: Long, frameIndex: Long, framesPerSecond: Double?): String {
        val safeMs = timeMs.coerceAtLeast(0L)
        val fps = framesPerSecond?.takeIf { it.isFinite() && it > 0.0 }
        if (fps != null && safeMs % 1_000L != 0L) {
            return "${frameIndex.coerceAtLeast(0L)}f"
        }
        val totalSeconds = safeMs / 1_000L
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return "%02d:%02d".format(java.util.Locale.US, minutes, seconds)
    }

    fun formatRulerTime(timeMs: Long): String {
        val safeMs = timeMs.coerceAtLeast(0L)
        val totalSeconds = safeMs / 1000L
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        val fraction = (safeMs % 1_000L).toInt()
        val decimal = if (fraction == 0) "" else {
            "." + "%03d".format(java.util.Locale.US, fraction).trimEnd('0')
        }
        return when {
            minutes > 0L -> "%d:%02d".format(java.util.Locale.US, minutes, seconds) + decimal
            else -> totalSeconds.toString() + decimal
        }
    }

    /** Minimum wait before the next decoder seek in a continuous timeline scrub. */
    fun scrubSeekDelayMs(
        nowMs: Long,
        lastSeekUptimeMs: Long,
        minimumIntervalMs: Long
    ): Long {
        val elapsedMs = (nowMs - lastSeekUptimeMs).coerceAtLeast(0L)
        return (minimumIntervalMs.coerceAtLeast(0L) - elapsedMs).coerceAtLeast(0L)
    }


    /** Time at a viewport x, measured from the shared time-zero content origin. */
    fun timeAtViewportX(
        scrollX: Int,
        viewportX: Float,
        originSpacerPx: Float,
        pixelsPerSecond: Float
    ): Long {
        val contentOffset = scrollX.toFloat() + viewportX - originSpacerPx
        return millisecondsForPixels(contentOffset.coerceAtLeast(0f), pixelsPerSecond)
    }

    /** Scroll offset that keeps [anchorTimeMs] beneath the same finger after zoom. */
    fun scrollForAnchoredZoom(
        anchorTimeMs: Long,
        viewportX: Float,
        originSpacerPx: Float,
        newPixelsPerSecond: Float
    ): Int {
        if (!newPixelsPerSecond.isFinite() ||
            newPixelsPerSecond <= 0f || anchorTimeMs <= 0L) return 0
        val contentX = pixelsForTime(anchorTimeMs, newPixelsPerSecond).toFloat()
        return (contentX - (viewportX - originSpacerPx)).roundToInt().coerceAtLeast(0)
    }
}


/**
 * A text layer with no user-defined end follows the project end. It must not
 * extend the project it is following; only explicitly timed text contributes
 * its own independent endpoint.
 */
internal data class TimelineLayerEnd(
    val endMs: Long,
    val followsProjectDuration: Boolean = false
)

/**
 * The ruler covers the furthest independent end among video, explicit text,
 * music and sound/effect items. Overlapping durations are never summed.
 */
internal object TimelineProjectDurationMath {
    fun totalDuration(
        videoEndMs: Long,
        textLayerEnds: Iterable<TimelineLayerEnd>,
        musicEndMs: Long,
        soundEndMs: Long
    ): Long {
        val independentTextEnd = textLayerEnds
            .filterNot { it.followsProjectDuration }
            .maxOfOrNull { it.endMs } ?: 0L
        return maxOf(videoEndMs, independentTextEnd, musicEndMs, soundEndMs, 0L)
    }
}

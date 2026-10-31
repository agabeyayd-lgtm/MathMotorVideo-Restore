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

    /** Select a readable ruler step from the current physical px/sec scale. */
    fun rulerStepMs(pixelsPerSecond: Float, minimumLabelSpacingPx: Float): Long {
        if (!pixelsPerSecond.isFinite() || pixelsPerSecond <= 0f) return 1_000L
        val targetSeconds = (minimumLabelSpacingPx.coerceAtLeast(1f) / pixelsPerSecond)
            .coerceAtLeast(0.001f)
        val niceSteps = floatArrayOf(0.1f, 0.2f, 0.5f, 1f, 2f, 5f, 10f, 15f, 30f, 60f, 120f, 300f, 600f)
        val seconds = niceSteps.firstOrNull { it >= targetSeconds }
            ?: (kotlin.math.ceil(targetSeconds / 600f) * 600f)
        return (seconds * 1000f).toLong().coerceAtLeast(1L)
    }

    fun formatRulerTime(timeMs: Long): String {
        val totalSeconds = timeMs.coerceAtLeast(0L) / 1000L
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return if (minutes > 0L) "%d:%02d".format(java.util.Locale.US, minutes, seconds)
        else seconds.toString()
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

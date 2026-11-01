package com.mathmotor.video

import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Converts between the timeline's shared time axis and screen pixels.
 * All block boundaries are calculated from absolute times, avoiding accumulated
 * rounding differences when many clips or tracks are placed consecutively.
 */
internal object TimelineScaleMath {
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

    /** Scroll offset that places [timeMs] directly under a stationary center playhead. */
    fun scrollForCenteredTime(timeMs: Long, pixelsPerSecond: Float): Int =
        pixelsForTime(timeMs, pixelsPerSecond)

    /** Project time shown beneath the stationary center playhead for a horizontal scroll offset. */
    fun timeForCenteredScroll(scrollX: Int, pixelsPerSecond: Float): Long =
        millisecondsForPixels(scrollX.coerceAtLeast(0).toFloat(), pixelsPerSecond).coerceAtLeast(0L)

    /**
     * Maps a horizontal position inside the timeline viewport to project time.
     * The content has a half-viewport leading inset, so the center position at scroll 0 is time 0.
     */
    fun timeAtViewportX(
        scrollX: Int,
        xWithinViewportPx: Float,
        viewportWidthPx: Int,
        pixelsPerSecond: Float
    ): Long {
        if (!xWithinViewportPx.isFinite()) return 0L
        val centeredOffset = scrollX.toDouble() + xWithinViewportPx.toDouble() -
            viewportWidthPx.coerceAtLeast(0).toDouble() / 2.0
        return millisecondsForPixels(centeredOffset.toFloat(), pixelsPerSecond).coerceAtLeast(0L)
    }

    /** New scroll offset that keeps [timeMs] under the same viewport focus after zooming. */
    fun scrollForAnchorTime(
        timeMs: Long,
        focusXWithinViewportPx: Float,
        viewportWidthPx: Int,
        pixelsPerSecond: Float
    ): Int {
        if (!focusXWithinViewportPx.isFinite()) return scrollForCenteredTime(timeMs, pixelsPerSecond)
        val target = pixelsForTime(timeMs, pixelsPerSecond).toFloat() +
            viewportWidthPx.coerceAtLeast(0) / 2f - focusXWithinViewportPx
        return target.roundToInt().coerceAtLeast(0)
    }

    /**
     * Chooses a readable ruler interval from the current time-to-pixel scale.
     * Longer projects use minute/large-interval marks only when zoomed out;
     * close zoom naturally moves through seconds into half-second marks.
     */
    fun rulerIntervalMs(
        pixelsPerSecond: Float,
        minimumLabelSpacingPx: Float,
        totalDurationMs: Long,
        coarseScaleThresholdPx: Float
    ): Long {
        if (!pixelsPerSecond.isFinite() || pixelsPerSecond <= 0f ||
            !minimumLabelSpacingPx.isFinite() || minimumLabelSpacingPx <= 0f
        ) return 60_000L
        val coarseIntervalMs = when {
            coarseScaleThresholdPx.isFinite() && coarseScaleThresholdPx > 0f &&
                pixelsPerSecond <= coarseScaleThresholdPx && totalDurationMs >= 600_000L -> 60_000L
            coarseScaleThresholdPx.isFinite() && coarseScaleThresholdPx > 0f &&
                pixelsPerSecond <= coarseScaleThresholdPx && totalDurationMs >= 120_000L -> 15_000L
            else -> 0L
        }
        val spacingIntervalMs = ceil(
            minimumLabelSpacingPx.toDouble() / pixelsPerSecond.toDouble() * 1000.0
        ).toLong().coerceAtLeast(250L)
        val requiredIntervalMs = maxOf(coarseIntervalMs, spacingIntervalMs)
        val readableIntervals = longArrayOf(
            250L, 500L, 1_000L, 2_000L, 5_000L, 10_000L, 15_000L,
            30_000L, 60_000L, 120_000L, 300_000L, 600_000L, 1_800_000L
        )
        return readableIntervals.firstOrNull { it >= requiredIntervalMs } ?: 3_600_000L
    }
}

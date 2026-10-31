package com.mathmotor.video

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

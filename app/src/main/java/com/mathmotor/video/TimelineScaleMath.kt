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

    fun scrollForTimeAtViewportOffset(
        timeMs: Long,
        pixelsPerSecond: Float,
        viewportOffsetPx: Float
    ): Int {
        if (!viewportOffsetPx.isFinite()) return 0
        return (pixelsForTime(timeMs, pixelsPerSecond) - viewportOffsetPx.roundToInt())
            .coerceAtLeast(0)
    }

    /** Accumulates pinch scale from successive pointer spans, regardless of MOVE event count. */
    fun zoomForPinchSpan(currentZoom: Float, previousSpanPx: Float, currentSpanPx: Float): Float {
        if (!currentZoom.isFinite() || currentZoom <= 0f) return 1f
        val safeZoom = currentZoom.coerceIn(0.5f, 8.0f)
        if (!previousSpanPx.isFinite() || !currentSpanPx.isFinite() || previousSpanPx <= 0f || currentSpanPx <= 0f) {
            return safeZoom
        }
        return (safeZoom * (currentSpanPx / previousSpanPx)).coerceIn(0.5f, 8.0f)
    }
    fun millisecondsForPixels(offsetPixels: Float, pixelsPerSecond: Float): Long {
        if (!offsetPixels.isFinite() || !pixelsPerSecond.isFinite() || pixelsPerSecond <= 0f) return 0L
        return ((offsetPixels.toDouble() / pixelsPerSecond.toDouble()) * 1000.0)
            .roundToLong()
    }
}

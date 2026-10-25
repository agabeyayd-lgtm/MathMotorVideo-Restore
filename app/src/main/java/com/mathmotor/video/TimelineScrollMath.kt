package com.mathmotor.video

/**
 * Shared conversions for a timeline whose playhead stays at a fixed viewport x
 * while the horizontal content moves underneath it.
 */
internal object TimelineScrollMath {
    fun timeAtFixedPlayhead(
        scrollX: Int,
        playheadViewportX: Int,
        contentOriginX: Int,
        leadingInsetPx: Int,
        pixelsPerSecond: Float,
        totalMs: Long
    ): Long {
        val localPlayheadX = (playheadViewportX - contentOriginX).coerceAtLeast(0)
        val timelineX = (scrollX.toLong() + localPlayheadX - leadingInsetPx).coerceAtLeast(0L)
        return TimelineScaleMath.millisecondsForPixels(timelineX.toFloat(), pixelsPerSecond)
            .coerceIn(0L, totalMs.coerceAtLeast(0L))
    }

    fun scrollForTimeAtFixedPlayhead(
        timeMs: Long,
        playheadViewportX: Int,
        contentOriginX: Int,
        leadingInsetPx: Int,
        pixelsPerSecond: Float
    ): Int {
        val contentX = TimelineScaleMath.pixelsForTime(timeMs.coerceAtLeast(0L), pixelsPerSecond)
        val playheadLocalX = (playheadViewportX - contentOriginX).coerceAtLeast(0)
        return (contentX.toLong() + leadingInsetPx - playheadLocalX)
            .coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    }

    /** Compute a scroll position that keeps a timeline time beneath a pinch focus point. */
    fun scrollForTimeAtViewportX(
        timeMs: Long,
        focusX: Float,
        leadingInsetPx: Int,
        pixelsPerSecond: Float
    ): Int {
        if (!focusX.isFinite()) return 0
        val contentX = TimelineScaleMath.pixelsForTime(timeMs.coerceAtLeast(0L), pixelsPerSecond)
        return (contentX.toFloat() + leadingInsetPx - focusX)
            .coerceIn(0f, Int.MAX_VALUE.toFloat()).toInt()
    }
}

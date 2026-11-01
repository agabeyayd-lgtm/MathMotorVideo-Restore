package com.mathmotor.video

/**
 * Pure sizing/sampling rules for the timeline filmstrip.
 *
 * The rendered clip width is duration × timeline px/sec, so the number of
 * thumbnail cells grows with pinch zoom and shrinks when the user zooms out.
 */
internal object TimelineThumbnailMath {
    fun thumbnailCount(widthPx: Int, cellWidthPx: Int, maxCount: Int = 256): Int {
        val safeWidth = widthPx.coerceAtLeast(1).toLong()
        val safeCellWidth = cellWidthPx.coerceAtLeast(1).toLong()
        val safeMaximum = maxCount.coerceAtLeast(1)
        val count = ((safeWidth + safeCellWidth - 1L) / safeCellWidth)
            .coerceAtLeast(1L)
            .coerceAtMost(safeMaximum.toLong())
        return count.toInt()
    }

    /**
     * Evenly samples [startMs, endMs), including the first frame and the final
     * representable millisecond before the clip end. A single cell samples startMs.
     */
    fun sampleTime(startMs: Long, endMs: Long, count: Int, index: Int): Long {
        if (endMs <= startMs || count <= 1) return startMs
        val slots = count - 1
        val slot = index.coerceIn(0, slots)
        val span = (endMs - startMs - 1L).coerceAtLeast(0L)
        val quotient = span / slots
        val remainder = span % slots
        val offset = quotient * slot + (remainder * slot) / slots
        return startMs + offset
    }
}

package com.mathmotor.video

/**
 * Pure sizing/sampling rules for the timeline filmstrip.
 *
 * The rendered clip width is duration × timeline px/sec, so the number of
 * thumbnail cells grows with pinch zoom and shrinks when the user zooms out.
 */
internal object TimelineThumbnailMath {
    const val SHORTS_THUMBNAIL_WIDTH_DP = 52
    const val SHORTS_ASPECT_WIDTH = 9
    const val SHORTS_ASPECT_HEIGHT = 16

    /** Exact pixel height for a full-frame 9:16 cell at the supplied pixel width. */
    fun portraitCellHeightPx(widthPx: Int): Int {
        val safeWidth = widthPx.coerceAtLeast(1).toLong()
        return ((safeWidth * SHORTS_ASPECT_HEIGHT + SHORTS_ASPECT_WIDTH / 2L) /
            SHORTS_ASPECT_WIDTH).toInt().coerceAtLeast(1)
    }

    /**
     * Shrinks a decoded frame inside the requested bounds without cropping,
     * padding, distortion, or upscaling. The returned bitmap can then be drawn
     * in a cell that has the same source aspect ratio.
     */
    fun fitDimensions(
        sourceWidth: Int,
        sourceHeight: Int,
        maxWidth: Int,
        maxHeight: Int
    ): Pair<Int, Int> {
        val width = sourceWidth.coerceAtLeast(1)
        val height = sourceHeight.coerceAtLeast(1)
        val boundWidth = maxWidth.coerceAtLeast(1)
        val boundHeight = maxHeight.coerceAtLeast(1)
        val scale = minOf(
            1.0,
            boundWidth.toDouble() / width.toDouble(),
            boundHeight.toDouble() / height.toDouble()
        )
        return (width * scale).toInt().coerceIn(1, boundWidth) to
            (height * scale).toInt().coerceIn(1, boundHeight)
    }
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
     * Samples the temporal center of the requested visual cell.
     *
     * Filmstrip cells divide the clip into [count] equal-width intervals. Sampling
     * endpoints across count - 1 gaps shifts every frame away from the cell it
     * represents, so a playhead centered on a cell can show a different moment
     * from the preview. This maps each bitmap to the midpoint of its own interval.
     */
    fun sampleTime(startMs: Long, endMs: Long, count: Int, index: Int): Long {
        if (endMs <= startMs) return startMs
        val safeCount = count.coerceAtLeast(1)
        val slot = index.coerceIn(0, safeCount - 1)
        val span = endMs - startMs

        // Calculate each interval boundary with quotient/remainder arithmetic to
        // avoid multiplying the full duration by a potentially large slot index.
        fun boundary(cell: Int): Long {
            val whole = span / safeCount
            val remainder = span % safeCount
            val cellIndex = cell.toLong()
            return whole * cellIndex + (remainder * cellIndex) / safeCount
        }

        val cellStart = boundary(slot)
        val cellEnd = boundary(slot + 1)
        val midpoint = cellStart + (cellEnd - cellStart) / 2L
        return startMs + midpoint.coerceIn(0L, (span - 1L).coerceAtLeast(0L))
    }
}

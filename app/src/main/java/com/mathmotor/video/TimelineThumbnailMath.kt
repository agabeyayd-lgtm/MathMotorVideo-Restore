package com.mathmotor.video

/**
 * Pure sizing/sampling rules for the timeline filmstrip.
 *
 * The rendered clip width is duration × timeline px/sec, so the number of
 * thumbnail cells grows with pinch zoom and shrinks when the user zooms out.
 */
internal object TimelineThumbnailMath {
    // Keep the video lane tall enough for the current portrait reference, but
    // calculate each thumbnail's width from the actual source aspect ratio.
    const val THUMBNAIL_STRIP_HEIGHT_DP = 92
    const val DEFAULT_SOURCE_ASPECT_RATIO = 9f / 16f

    /** Width of a full-frame thumbnail at the fixed strip height, without cropping. */
    fun thumbnailCellWidthPx(stripHeightPx: Int, sourceAspectRatio: Float): Int {
        val height = stripHeightPx.coerceAtLeast(1)
        val ratio = sourceAspectRatio.takeIf { it.isFinite() && it > 0f }
            ?.coerceIn(0.25f, 4f) ?: DEFAULT_SOURCE_ASPECT_RATIO
        return (height * ratio).toInt().coerceAtLeast(1)
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
    private fun boundary(span: Long, count: Int, cell: Int): Long {
        val safeCount = count.coerceAtLeast(1)
        val whole = span / safeCount
        val remainder = span % safeCount
        val cellIndex = cell.coerceIn(0, safeCount).toLong()
        return whole * cellIndex + (remainder * cellIndex) / safeCount
    }

    /** Inclusive source-time start of a filmstrip cell. */
    fun cellStartTime(startMs: Long, endMs: Long, count: Int, index: Int): Long {
        if (endMs <= startMs) return startMs
        val safeCount = count.coerceAtLeast(1)
        val slot = index.coerceIn(0, safeCount - 1)
        return startMs + boundary(endMs - startMs, safeCount, slot)
    }

    /** Exclusive source-time end of a filmstrip cell. */
    fun cellEndTime(startMs: Long, endMs: Long, count: Int, index: Int): Long {
        if (endMs <= startMs) return startMs
        val safeCount = count.coerceAtLeast(1)
        val slot = index.coerceIn(0, safeCount - 1)
        return startMs + boundary(endMs - startMs, safeCount, slot + 1)
    }

    fun sampleTime(startMs: Long, endMs: Long, count: Int, index: Int): Long {
        if (endMs <= startMs) return startMs
        val cellStart = cellStartTime(startMs, endMs, count, index)
        val cellEnd = cellEndTime(startMs, endMs, count, index)
        val midpoint = cellStart + (cellEnd - cellStart) / 2L
        return midpoint.coerceIn(startMs, (endMs - 1L).coerceAtLeast(startMs))
    }
}

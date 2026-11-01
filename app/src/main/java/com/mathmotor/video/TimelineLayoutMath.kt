package com.mathmotor.video

/**
 * Sizes the timeline shell from the rows it actually contains.
 *
 * The lane viewport may grow for a few rows, then becomes vertically scrollable
 * at [maximumHeightPx]. This prevents a single video lane from reserving a large
 * unused panel while keeping multi-layer projects inside the editor viewport.
 */
internal object TimelineLayoutMath {
    fun contentSizedCardHeightPx(
        laneContentHeightPx: Int,
        timeRowHeightPx: Int,
        verticalPaddingPx: Int,
        minimumHeightPx: Int,
        maximumHeightPx: Int
    ): Int {
        val minimum = minimumHeightPx.coerceAtLeast(0)
        val maximum = maximumHeightPx.coerceAtLeast(minimum)
        val desired = laneContentHeightPx.toLong().coerceAtLeast(0L) +
            timeRowHeightPx.toLong().coerceAtLeast(0L) +
            verticalPaddingPx.toLong().coerceAtLeast(0L)
        return desired.coerceIn(minimum.toLong(), maximum.toLong()).toInt()
    }
}

package com.mathmotor.video

import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineLayoutMathTest {
    @Test
    fun singleVideoLaneUsesOnlyItsContentAndHeaderSpace() {
        // Ruler + portrait video lane, followed by the time header and card padding.
        assertEquals(
            150,
            TimelineLayoutMath.contentSizedCardHeightPx(
                laneContentHeightPx = 118,
                timeRowHeightPx = 26,
                verticalPaddingPx = 6,
                minimumHeightPx = 96,
                maximumHeightPx = 280
            )
        )
    }

    @Test
    fun additionalLayersGrowTheCardUntilItsScrollLimit() {
        assertEquals(
            232,
            TimelineLayoutMath.contentSizedCardHeightPx(
                laneContentHeightPx = 200,
                timeRowHeightPx = 26,
                verticalPaddingPx = 6,
                minimumHeightPx = 96,
                maximumHeightPx = 280
            )
        )
        assertEquals(
            280,
            TimelineLayoutMath.contentSizedCardHeightPx(
                laneContentHeightPx = 500,
                timeRowHeightPx = 26,
                verticalPaddingPx = 6,
                minimumHeightPx = 96,
                maximumHeightPx = 280
            )
        )
    }

    @Test
    fun emptyAndKeyboardLayoutsRespectTheirMinimumAndMaximum() {
        assertEquals(
            96,
            TimelineLayoutMath.contentSizedCardHeightPx(
                laneContentHeightPx = 0,
                timeRowHeightPx = 0,
                verticalPaddingPx = 0,
                minimumHeightPx = 96,
                maximumHeightPx = 280
            )
        )
        assertEquals(
            154,
            TimelineLayoutMath.contentSizedCardHeightPx(
                laneContentHeightPx = 300,
                timeRowHeightPx = 26,
                verticalPaddingPx = 6,
                minimumHeightPx = 88,
                maximumHeightPx = 154
            )
        )
    }
}

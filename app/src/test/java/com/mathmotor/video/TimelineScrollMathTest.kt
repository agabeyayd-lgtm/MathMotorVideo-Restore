package com.mathmotor.video

import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineScrollMathTest {
    @Test
    fun fixedPlayheadTracksTheTimeUnderItsCenterAfterHorizontalScroll() {
        val pps = 22f
        val originX = 57
        val playheadX = 237
        val inset = playheadX - originX
        val scrollX = TimelineScaleMath.pixelsForTime(15_000L, pps)

        assertEquals(
            15_000L,
            TimelineScrollMath.timeAtFixedPlayhead(
                scrollX, playheadX, originX, inset, pps, 30_000L
            )
        )
    }

    @Test
    fun scrollForTimePlacesRequestedTimeUnderFixedPlayhead() {
        val pps = 29.7f
        val originX = 54
        val playheadX = 220
        val inset = playheadX - originX
        val targetMs = 12_345L
        val scrollX = TimelineScrollMath.scrollForTimeAtFixedPlayhead(
            targetMs, playheadX, originX, inset, pps
        )

        assertEquals(
            TimelineScaleMath.millisecondsForPixels(scrollX.toFloat(), pps),
            TimelineScrollMath.timeAtFixedPlayhead(
                scrollX, playheadX, originX, inset, pps, 60_000L
            )
        )
    }

    @Test
    fun playheadTimeIsClampedToTimelineBounds() {
        assertEquals(
            0L,
            TimelineScrollMath.timeAtFixedPlayhead(
                0, 200, 50, 150, 20f, 10_000L
            )
        )
        assertEquals(
            10_000L,
            TimelineScrollMath.timeAtFixedPlayhead(
                50_000, 200, 50, 150, 20f, 10_000L
            )
        )
    }

    @Test
    fun pinchFocusKeepsItsTimelineTimeUnderTheFinger() {
        val pps = 30f
        val timeMs = 8_000L
        val focusX = 85f
        val leadingInset = 170
        val scrollX = TimelineScrollMath.scrollForTimeAtViewportX(
            timeMs, focusX, leadingInset, pps
        )
        val contentX = scrollX + focusX.toInt() - leadingInset
        val actual = TimelineScaleMath.millisecondsForPixels(contentX.toFloat(), pps)
        assertEquals(7L, kotlin.math.abs(actual - timeMs))
    }
}

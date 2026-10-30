package com.mathmotor.video

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.abs

class TimelineScaleMathTest {
    @Test
    fun rulerAndPlayheadUseTheSameAbsolutePosition() {
        val pixelsPerSecond = 29.7f
        val duration = 20_000L
        assertEquals(
            TimelineScaleMath.pixelsForTime(duration, pixelsPerSecond),
            TimelineScaleMath.widthBetween(0L, duration, pixelsPerSecond)
        )
    }

    @Test
    fun adjacentClipWidthsDoNotAccumulateRoundingDrift() {
        val pixelsPerSecond = 29.7f
        val durations = listOf(1_150L, 2_350L, 875L, 4_625L, 3_000L)
        var offsetMs = 0L
        var offsetPx = 0
        durations.forEach { duration ->
            val nextMs = offsetMs + duration
            offsetPx += TimelineScaleMath.widthBetween(offsetMs, nextMs, pixelsPerSecond)
            offsetMs = nextMs
        }
        assertEquals(TimelineScaleMath.pixelsForTime(offsetMs, pixelsPerSecond), offsetPx)
    }

    @Test
    fun pixelToTimeRoundTripHasAtMostOneMillisecondRoundingError() {
        val pixelsPerSecond = 29.7f
        listOf(0L, 100L, 500L, 1_234L, 10_000L, 61_230L).forEach { timeMs ->
            val pixels = TimelineScaleMath.pixelsForTime(timeMs, pixelsPerSecond).toFloat()
            val converted = TimelineScaleMath.millisecondsForPixels(pixels, pixelsPerSecond)
            assertEquals(true, abs(converted - timeMs) <= 17L)
        }
    }

    @Test
    fun centeredPlayheadMapsScrollOffsetToProjectTime() {
        val pixelsPerSecond = 40f
        val viewportWidth = 300
        val targetTimeMs = 10_000L
        val scrollX = TimelineScaleMath.scrollForCenteredTime(targetTimeMs, pixelsPerSecond)

        assertEquals(400, scrollX)
        assertEquals(targetTimeMs, TimelineScaleMath.timeForCenteredScroll(scrollX, pixelsPerSecond))
        assertEquals(0L, TimelineScaleMath.timeAtViewportX(0, viewportWidth / 2f, viewportWidth, pixelsPerSecond))
        assertEquals(targetTimeMs, TimelineScaleMath.timeAtViewportX(
            scrollX, viewportWidth / 2f, viewportWidth, pixelsPerSecond
        ))
    }

    @Test
    fun zoomAnchorKeepsTheSameTimeUnderTheSameFingerPosition() {
        val viewportWidth = 360
        val focusX = 82f
        val beforeZoom = 27f
        val afterZoom = 54f
        val previousScroll = 125
        val anchorTime = TimelineScaleMath.timeAtViewportX(
            previousScroll, focusX, viewportWidth, beforeZoom
        )
        val newScroll = TimelineScaleMath.scrollForAnchorTime(
            anchorTime, focusX, viewportWidth, afterZoom
        )
        val timeAfterZoom = TimelineScaleMath.timeAtViewportX(
            newScroll, focusX, viewportWidth, afterZoom
        )

        assertEquals(anchorTime, timeAfterZoom)
    }

    @Test
    fun centeredTimelineCanReachTheEndWithSymmetricViewportPadding() {
        val viewportWidth = 320
        val pixelsPerSecond = 22f
        val durationMs = 10_000L
        val contentWidth = viewportWidth / 2 + TimelineScaleMath.pixelsForTime(durationMs, pixelsPerSecond) +
            viewportWidth / 2
        val maxScrollX = contentWidth - viewportWidth

        assertEquals(TimelineScaleMath.pixelsForTime(durationMs, pixelsPerSecond), maxScrollX)
        assertEquals(durationMs, TimelineScaleMath.timeAtViewportX(
            maxScrollX, viewportWidth / 2f, viewportWidth, pixelsPerSecond
        ))
    }

    @Test
    fun sharedTimeScaleMapsVideoTextAndAudioRangesAtEveryZoomLevel() {
        val ranges = listOf(
            0L to 8_020L,
            1_250L to 5_750L,
            0L to 8_020L,
            2_500L to 4_500L
        )
        listOf(11f, 22f, 44f, 88f, 176f).forEach { pps ->
            val mapped = ranges.map { (start, end) ->
                TimelineScaleMath.pixelsForTime(start, pps) to TimelineScaleMath.pixelsForTime(end, pps)
            }
            assertEquals(mapped[1].first, TimelineScaleMath.pixelsForTime(1_250L, pps))
            assertEquals(mapped[1].second, TimelineScaleMath.pixelsForTime(5_750L, pps))
            assertEquals(mapped[3].first, TimelineScaleMath.pixelsForTime(2_500L, pps))
            assertEquals(mapped[3].second, TimelineScaleMath.pixelsForTime(4_500L, pps))
        }
    }

    @Test
    fun rulerIntervalAdaptsToSecondsMinutesAndCloseZoom() {
        assertEquals(5_000L, TimelineScaleMath.rulerIntervalMs(11f, 36f, 8_020L, 16f))
        assertEquals(2_000L, TimelineScaleMath.rulerIntervalMs(22f, 36f, 8_020L, 16f))
        assertEquals(500L, TimelineScaleMath.rulerIntervalMs(88f, 36f, 8_020L, 16f))
        assertEquals(15_000L, TimelineScaleMath.rulerIntervalMs(11f, 36f, 300_000L, 16f))
        assertEquals(60_000L, TimelineScaleMath.rulerIntervalMs(11f, 36f, 720_000L, 16f))
    }

    @Test
    fun invalidScaleAndNegativeTimeAreSafe() {
        assertEquals(0, TimelineScaleMath.pixelsForTime(-100L, 22f))
        assertEquals(0, TimelineScaleMath.pixelsForTime(1_000L, 0f))
        assertEquals(0, TimelineScaleMath.widthBetween(2_000L, 1_000L, 22f))
        assertEquals(0L, TimelineScaleMath.millisecondsForPixels(100f, 0f))
        assertEquals(0, TimelineScaleMath.scrollForCenteredTime(-100L, 22f))
        assertEquals(0L, TimelineScaleMath.timeAtViewportX(0, 0f, 300, 22f))
    }
}

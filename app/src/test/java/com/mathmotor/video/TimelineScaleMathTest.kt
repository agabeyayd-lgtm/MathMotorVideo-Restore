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
    fun zoomAnchorKeepsTheSameTimestampUnderTheSameFingerMidpoint() {
        val oldScale = 22f
        val newScale = 44f
        val scrollX = 320
        val midpointX = 103f
        val originSpacer = 103f
        val anchorTime = TimelineScaleMath.timeAtViewportX(scrollX, midpointX, originSpacer, oldScale)
        val newScroll = TimelineScaleMath.scrollForAnchoredZoom(anchorTime, midpointX, originSpacer, newScale)
        val timeAfterZoom = TimelineScaleMath.timeAtViewportX(newScroll, midpointX, originSpacer, newScale)
        assertEquals(anchorTime, timeAfterZoom)
    }

    @Test
    fun timeZeroAndFirstVideoFrameShareTheFullPlayCenter() {
        val frameWidthPx = 320
        val gripAndLabelGutterPx = 57
        val originSpacerPx = TimelineScaleMath.originSpacerWidthForCenteredPlayhead(
            frameWidthPx,
            gripAndLabelGutterPx
        )

        val trailingRowInsetPx = 3
        val endSpacerPx = TimelineScaleMath.endSpacerWidthForCenteredPlayhead(
            frameWidthPx,
            gripAndLabelGutterPx,
            trailingRowInsetPx
        )
        val scrollViewportWidthPx = frameWidthPx - gripAndLabelGutterPx - trailingRowInsetPx

        assertEquals(103, originSpacerPx)
        assertEquals(157, endSpacerPx)
        assertEquals(scrollViewportWidthPx, originSpacerPx + endSpacerPx)
        assertEquals(frameWidthPx / 2, gripAndLabelGutterPx + originSpacerPx)
        assertEquals(
            0L,
            TimelineScaleMath.timeAtViewportX(
                scrollX = 0,
                viewportX = originSpacerPx.toFloat(),
                originSpacerPx = originSpacerPx.toFloat(),
                pixelsPerSecond = 22f
            )
        )
    }

    @Test
    fun playbackScrollMovesBothRulerAndVideoUnderTheSameFixedCenter() {
        val frameWidthPx = 320
        val gutterPx = 57
        val originSpacerPx = TimelineScaleMath.originSpacerWidthForCenteredPlayhead(frameWidthPx, gutterPx)
        val pixelsPerSecond = 22f
        val elapsedMs = 2_000L
        val scrollPx = TimelineScaleMath.pixelsForTime(elapsedMs, pixelsPerSecond)

        val timeUnderPlayhead = TimelineScaleMath.timeAtViewportX(
            scrollX = scrollPx,
            viewportX = originSpacerPx.toFloat(),
            originSpacerPx = originSpacerPx.toFloat(),
            pixelsPerSecond = pixelsPerSecond
        )
        assertEquals(elapsedMs, timeUnderPlayhead)
    }

    @Test
    fun zoomAnchorClampsAtTimelineStart() {
        assertEquals(
            0,
            TimelineScaleMath.scrollForAnchoredZoom(0L, 20f, 57f, 44f)
        )
    }

    @Test
    fun sharedProjectDurationExpandsToTheFurthestIndependentLayerEnd() {
        assertEquals(
            13_000L,
            TimelineProjectDurationMath.totalDuration(
                videoEndMs = 10_000L,
                textLayerEnds = listOf(TimelineLayerEnd(8_000L)),
                musicEndMs = 13_000L,
                soundEndMs = 2_500L
            )
        )
    }

    @Test
    fun sharedProjectDurationShrinksWhenTheFurthestLayerIsRemoved() {
        assertEquals(
            10_000L,
            TimelineProjectDurationMath.totalDuration(
                videoEndMs = 10_000L,
                textLayerEnds = listOf(TimelineLayerEnd(8_000L)),
                musicEndMs = 0L,
                soundEndMs = 2_500L
            )
        )
    }

    @Test
    fun projectFollowingTextDoesNotKeepAnOldLongerProjectDurationAlive() {
        assertEquals(
            10_000L,
            TimelineProjectDurationMath.totalDuration(
                videoEndMs = 10_000L,
                textLayerEnds = listOf(TimelineLayerEnd(13_000L, followsProjectDuration = true)),
                musicEndMs = 0L,
                soundEndMs = 0L
            )
        )
    }

    @Test
    fun explicitlyTimedTextCanExtendTheProjectDuration() {
        assertEquals(
            13_000L,
            TimelineProjectDurationMath.totalDuration(
                videoEndMs = 10_000L,
                textLayerEnds = listOf(TimelineLayerEnd(13_000L, followsProjectDuration = false)),
                musicEndMs = 0L,
                soundEndMs = 0L
            )
        )
    }

    @Test
    fun sharedProjectDurationNeverBecomesNegative() {
        assertEquals(
            0L,
            TimelineProjectDurationMath.totalDuration(
                videoEndMs = -1L,
                textLayerEnds = listOf(TimelineLayerEnd(-2L, followsProjectDuration = true)),
                musicEndMs = -3L,
                soundEndMs = -4L
            )
        )
    }

    @Test
    fun invalidScaleAndNegativeTimeAreSafe() {
        assertEquals(0, TimelineScaleMath.pixelsForTime(-100L, 22f))
        assertEquals(0, TimelineScaleMath.pixelsForTime(1_000L, 0f))
        assertEquals(0, TimelineScaleMath.widthBetween(2_000L, 1_000L, 22f))
        assertEquals(0L, TimelineScaleMath.millisecondsForPixels(100f, 0f))
    }
}

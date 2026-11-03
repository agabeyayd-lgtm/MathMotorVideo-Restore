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
        val rulerHeaderAndLeadingInsetPx = 87
        val originSpacerPx = TimelineScaleMath.originSpacerWidthForCenteredPlayhead(
            frameWidthPx,
            rulerHeaderAndLeadingInsetPx
        )

        val trailingRowInsetPx = 3
        val endSpacerPx = TimelineScaleMath.endSpacerWidthForCenteredPlayhead(
            frameWidthPx,
            rulerHeaderAndLeadingInsetPx,
            trailingRowInsetPx
        )
        val scrollViewportWidthPx = frameWidthPx - rulerHeaderAndLeadingInsetPx - trailingRowInsetPx

        assertEquals(73, originSpacerPx)
        assertEquals(157, endSpacerPx)
        assertEquals(scrollViewportWidthPx, originSpacerPx + endSpacerPx)
        assertEquals(frameWidthPx / 2, rulerHeaderAndLeadingInsetPx + originSpacerPx)
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
        val gutterPx = 87
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
            TimelineScaleMath.scrollForAnchoredZoom(0L, 20f, 99f, 44f)
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
    fun scrubDecoderSeekWaitsForRemainingThrottleWindow() {
        assertEquals(
            45L,
            TimelineScaleMath.scrubSeekDelayMs(
                nowMs = 1_075L,
                lastSeekUptimeMs = 1_000L,
                minimumIntervalMs = 120L
            )
        )
    }

    @Test
    fun scrubDecoderSeekRunsImmediatelyWhenThrottleWindowHasElapsed() {
        assertEquals(
            0L,
            TimelineScaleMath.scrubSeekDelayMs(
                nowMs = 1_200L,
                lastSeekUptimeMs = 1_000L,
                minimumIntervalMs = 120L
            )
        )
    }

    @Test
    fun scrubDecoderSeekCanRunAtInteractiveThirtyFramesPerSecondCadence() {
        assertEquals(
            23L,
            TimelineScaleMath.scrubSeekDelayMs(
                nowMs = 1_010L,
                lastSeekUptimeMs = 1_000L,
                minimumIntervalMs = 33L
            )
        )
        assertEquals(
            0L,
            TimelineScaleMath.scrubSeekDelayMs(
                nowMs = 1_040L,
                lastSeekUptimeMs = 1_000L,
                minimumIntervalMs = 33L
            )
        )
    }

    @Test
    fun fitScaleIsDeterministicForSameProjectAndViewport() {
        val first = TimelineScaleMath.fitPixelsPerSecond(10_000L, 357, 11f, 176f)
        val second = TimelineScaleMath.fitPixelsPerSecond(10_000L, 357, 11f, 176f)
        assertEquals(35.7f, first, 0.001f)
        assertEquals(first, second, 0f)
    }

    @Test
    fun fitScaleClampsToSupportedRange() {
        assertEquals(11f, TimelineScaleMath.fitPixelsPerSecond(60_000L, 100, 11f, 176f), 0f)
        assertEquals(176f, TimelineScaleMath.fitPixelsPerSecond(100L, 1_000, 11f, 176f), 0f)
    }

    @Test
    fun rulerTickSpacingMovesFromSecondsIntoReadableSubseconds() {
        assertEquals(3_000L, TimelineScaleMath.rulerStepMs(22f, 48f))
        assertEquals(1_000L, TimelineScaleMath.rulerStepMs(83f, 48f))
        assertEquals(5_000L, TimelineScaleMath.rulerStepMs(11f, 48f))
        assertEquals(500L, TimelineScaleMath.rulerStepMs(176f, 48f))
        assertEquals(100L, TimelineScaleMath.rulerStepMs(720f, 48f))
    }

    @Test
    fun frameRulerSwitchesToActualSourceFramesOnlyWhenZoomCanReadLabels() {
        val normal = TimelineScaleMath.rulerGrid(22f, 48f, 29.97)
        assertEquals(null, normal.frameStep)
        assertEquals(3_000L, normal.intervalMs)

        val medium = TimelineScaleMath.rulerGrid(83f, 48f, 29.97)
        assertEquals(null, medium.frameStep)
        assertEquals(1_000L, medium.intervalMs)

        val close = TimelineScaleMath.rulerGrid(176f, 48f, 29.97)
        assertEquals(10L, close.frameStep)
        assertEquals(334L, close.intervalMs)
        assertEquals(29.97, close.frameRate!!, 0.0001)
    }

    @Test
    fun maximumTimelineZoomCanDisplayIndividualFramesAtCommonSourceRates() {
        val fps23976 = TimelineScaleMath.rulerGrid(720f, 22f, 23.976)
        val fps30 = TimelineScaleMath.rulerGrid(720f, 22f, 30.0)
        val fps60 = TimelineScaleMath.rulerGrid(720f, 22f, 60.0)

        assertEquals(1L, fps23976.frameStep)
        assertEquals(1L, fps30.frameStep)
        assertEquals(2L, fps60.frameStep)
    }

    @Test
    fun fractionalFrameRateUsesAbsoluteFrameIndicesWithoutCumulativeDrift() {
        assertEquals(1_001L, TimelineScaleMath.frameTimeMs(30L, 29.97))
        assertEquals(2_002L, TimelineScaleMath.frameTimeMs(60L, 29.97))
        assertEquals(30L, TimelineScaleMath.frameIndexAtTimeMs(1_001L, 29.97))
        assertEquals(1_001L, TimelineScaleMath.snapTimeToFrameMs(1_000L, 29.97))
        assertEquals(1_000L, TimelineScaleMath.frameTimeMs(30L, 30.0))
    }

    @Test
    fun adaptiveRulerUsesClockLabelsOnWholeSecondsAndFrameLabelsBetween() {
        assertEquals("00:02", TimelineScaleMath.formatRulerTickLabel(2_002L, 60L, 29.97))
        assertEquals("15f", TimelineScaleMath.formatRulerTickLabel(2_502L, 75L, 29.97))
        assertEquals("00:03", TimelineScaleMath.formatRulerTickLabel(3_003L, 90L, 29.97))
        assertEquals("00:02", TimelineScaleMath.formatRulerTickLabel(2_000L, 0L, null))
    }

    @Test
    fun rulerTimeLabelsShowSubsecondPrecisionWithoutTrailingZeroes() {
        assertEquals("0.5", TimelineScaleMath.formatRulerTime(500L))
        assertEquals("0.25", TimelineScaleMath.formatRulerTime(250L))
        assertEquals("0.05", TimelineScaleMath.formatRulerTime(50L))
        assertEquals("1:05.25", TimelineScaleMath.formatRulerTime(65_250L))
    }

    @Test
    fun rulerMinorTicksScaleWithEachAdaptiveMajorInterval() {
        assertEquals(listOf(10, 20, 30, 40), TimelineScaleMath.rulerMinorTickOffsetsPx(50))
        assertEquals(listOf(16, 33, 49, 66), TimelineScaleMath.rulerMinorTickOffsetsPx(83))
        assertEquals(emptyList<Int>(), TimelineScaleMath.rulerMinorTickOffsetsPx(1))
        assertEquals(listOf(5), TimelineScaleMath.rulerMinorTickOffsetsPx(10, 2))
    }

    @Test
    fun rulerLabelsFormatWholeSecondsAndMinutes() {
        assertEquals("0", TimelineScaleMath.formatRulerTime(0L))
        assertEquals("3", TimelineScaleMath.formatRulerTime(3_000L))
        assertEquals("5", TimelineScaleMath.formatRulerTime(5_000L))
        assertEquals("1:05", TimelineScaleMath.formatRulerTime(65_000L))
    }

    @Test
    fun invalidScaleAndNegativeTimeAreSafe() {
        assertEquals(0, TimelineScaleMath.pixelsForTime(-100L, 22f))
        assertEquals(0, TimelineScaleMath.pixelsForTime(1_000L, 0f))
        assertEquals(0, TimelineScaleMath.widthBetween(2_000L, 1_000L, 22f))
        assertEquals(0L, TimelineScaleMath.millisecondsForPixels(100f, 0f))
    }

    @Test
    fun offCentrePinchKeepsMidpointTimestampAndUpdatesFixedCentreTime() {
        val oldScale = 22f
        val newScale = 44f
        val scrollBefore = 320
        val originSpacer = 103f
        val firstMidpointX = 75f
        val movedMidpointX = 130f

        val midpointTime = TimelineScaleMath.timeAtViewportX(
            scrollBefore,
            firstMidpointX,
            originSpacer,
            oldScale
        )
        val scrollAfter = TimelineScaleMath.scrollForAnchoredZoom(
            midpointTime,
            movedMidpointX,
            originSpacer,
            newScale
        )

        assertEquals(
            midpointTime,
            TimelineScaleMath.timeAtViewportX(
                scrollAfter,
                movedMidpointX,
                originSpacer,
                newScale
            )
        )

        val originalCentreTime = TimelineScaleMath.timeAtViewportX(
            scrollBefore,
            originSpacer,
            originSpacer,
            oldScale
        )
        val centreTimeAfterPinch = TimelineScaleMath.timeAtViewportX(
            scrollAfter,
            originSpacer,
            originSpacer,
            newScale
        )
        assertEquals(
            TimelineScaleMath.millisecondsForPixels(scrollAfter.toFloat(), newScale),
            centreTimeAfterPinch
        )
        assertEquals(false, originalCentreTime == centreTimeAfterPinch)
    }

    @Test
    fun filmstripThumbnailCountTracksSharedHorizontalZoomScale() {
        val normalCount = TimelineThumbnailMath.thumbnailCount(520, 52)
        val zoomedInCount = TimelineThumbnailMath.thumbnailCount(1_040, 52)
        val zoomedOutCount = TimelineThumbnailMath.thumbnailCount(260, 52)

        assertEquals(10, normalCount)
        assertEquals(20, zoomedInCount)
        assertEquals(5, zoomedOutCount)
    }

    @Test
    fun filmstripSamplesRemainSequentialAtDifferentZoomDensities() {
        listOf(5, 10, 20, 40).forEach { count ->
            val samples = (0 until count).map { index ->
                TimelineThumbnailMath.sampleTime(0L, 10_000L, count, index)
            }
            assertEquals(count, samples.distinct().size)
            assertEquals(true, samples.zipWithNext().all { (left, right) -> left < right })
            assertEquals(true, samples.first() >= 0L)
            assertEquals(true, samples.last() < 10_000L)
        }
    }

    @Test
    fun filmstripThumbnailCountIsBoundedForVeryWideClips() {
        assertEquals(256, TimelineThumbnailMath.thumbnailCount(1_000_000, 1))
    }


    @Test
    fun thumbnailCellWidthFollowsPortraitLandscapeAndSquareSourceRatios() {
        val stripHeightPx = 92

        assertEquals(
            52,
            TimelineThumbnailMath.thumbnailCellWidthPx(stripHeightPx, 9f / 16f)
        )
        assertEquals(
            164,
            TimelineThumbnailMath.thumbnailCellWidthPx(stripHeightPx, 16f / 9f)
        )
        assertEquals(
            92,
            TimelineThumbnailMath.thumbnailCellWidthPx(stripHeightPx, 1f)
        )
    }

    @Test
    fun invalidThumbnailAspectRatioFallsBackToPortraitWithoutCrashing() {
        assertEquals(
            52,
            TimelineThumbnailMath.thumbnailCellWidthPx(92, Float.NaN)
        )
        assertEquals(
            52,
            TimelineThumbnailMath.thumbnailCellWidthPx(92, 0f)
        )
    }

    @Test
    fun thumbnailDownsamplingKeepsEveryEdgeAndTheOriginalAspectRatio() {
        assertEquals(
            162 to 288,
            TimelineThumbnailMath.fitDimensions(1080, 1920, 162, 288)
        )
        assertEquals(
            162 to 91,
            TimelineThumbnailMath.fitDimensions(1920, 1080, 162, 288)
        )
    }

    @Test
    fun thumbnailDownsamplingNeverUpscalesSmallFrames() {
        assertEquals(
            90 to 160,
            TimelineThumbnailMath.fitDimensions(90, 160, 162, 288)
        )
    }

}

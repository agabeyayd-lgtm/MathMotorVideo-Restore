package com.mathmotor.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineThumbnailMathTest {
    @Test
    fun decodeBoundsMatchRenderedPortraitCellWithoutOversampling() {
        assertEquals(81 to 144, TimelineThumbnailMath.decodeBounds(81, 144, 9f / 16f))
    }

    @Test
    fun decodeBoundsFollowLandscapeAndFourThreeCellAspectRatios() {
        assertEquals(256 to 144, TimelineThumbnailMath.decodeBounds(256, 144, 16f / 9f))
        assertEquals(192 to 144, TimelineThumbnailMath.decodeBounds(192, 144, 4f / 3f))
    }

    @Test
    fun decodeBoundsClampInvalidAspectRatiosAndExplicitOversampling() {
        assertEquals(81 to 144, TimelineThumbnailMath.decodeBounds(81, 144, Float.NaN))
        assertEquals(122 to 216, TimelineThumbnailMath.decodeBounds(81, 144, 9f / 16f, 1.5f))
    }

    @Test
    fun previewDecodeBoundsUseTheMeasuredCanvasSizeWithoutExtraDecodePixels() {
        assertEquals(423 to 753, TimelineThumbnailMath.previewDecodeBounds(423, 753))
        assertEquals(753 to 423, TimelineThumbnailMath.previewDecodeBounds(753, 423))
    }

    @Test
    fun previewDecodeBoundsNeverUpscaleBeyondTheConfiguredLongEdge() {
        assertEquals(1080 to 1920, TimelineThumbnailMath.previewDecodeBounds(1080, 1920))
        assertEquals(1080 to 1920, TimelineThumbnailMath.previewDecodeBounds(1440, 2560))
    }

    @Test
    fun previewDecodeBoundsHandleInvalidDimensionsAndNonPositiveOversampling() {
        assertEquals(1 to 753, TimelineThumbnailMath.previewDecodeBounds(0, 753))
        assertEquals(423 to 753, TimelineThumbnailMath.previewDecodeBounds(423, 753, Float.NaN))
        assertEquals(423 to 753, TimelineThumbnailMath.previewDecodeBounds(423, 753, 0f))
    }

    @Test
    fun sourceAspectRatioUsesOriginalDimensionsWithoutThumbnailRounding() {
        assertEquals(9f / 16f, TimelineThumbnailMath.sourceAspectRatio(360, 640)!!, 0.000001f)
        assertEquals(16f / 9f, TimelineThumbnailMath.sourceAspectRatio(640, 360)!!, 0.000001f)
    }

    @Test
    fun sourceAspectRatioAccountsForQuarterTurnRotationMetadata() {
        assertEquals(9f / 16f, TimelineThumbnailMath.sourceAspectRatio(640, 360, 90)!!, 0.000001f)
        assertEquals(16f / 9f, TimelineThumbnailMath.sourceAspectRatio(360, 640, 270)!!, 0.000001f)
        assertEquals(16f / 9f, TimelineThumbnailMath.sourceAspectRatio(360, 640, 450)!!, 0.000001f)
    }

    @Test
    fun sourceAspectRatioRejectsMissingOrInvalidSourceDimensions() {
        assertNull(TimelineThumbnailMath.sourceAspectRatio(0, 640))
        assertNull(TimelineThumbnailMath.sourceAspectRatio(360, 0))
        assertNull(TimelineThumbnailMath.sourceAspectRatio(-1, 640))
    }

    @Test
    fun zoomingInCreatesMoreFilmstripFrames() {
        val cellWidthPx = 52
        val fitWidth = 260 // 10 seconds at a fit-to-screen scale
        val zoomedWidth = 520
        val closeZoomWidth = 1040

        val fitCount = TimelineThumbnailMath.thumbnailCount(fitWidth, cellWidthPx)
        val zoomedCount = TimelineThumbnailMath.thumbnailCount(zoomedWidth, cellWidthPx)
        val closeZoomCount = TimelineThumbnailMath.thumbnailCount(closeZoomWidth, cellWidthPx)

        assertEquals(5, fitCount)
        assertEquals(10, zoomedCount)
        assertEquals(20, closeZoomCount)
        assertTrue(zoomedCount > fitCount)
        assertTrue(closeZoomCount > zoomedCount)
    }

    @Test
    fun zoomingOutReducesFrameDensityAndCountIsBounded() {
        assertEquals(20, TimelineThumbnailMath.thumbnailCount(1040, 52))
        assertEquals(5, TimelineThumbnailMath.thumbnailCount(260, 52))
        assertEquals(256, TimelineThumbnailMath.thumbnailCount(100_000, 1))
        assertEquals(1, TimelineThumbnailMath.thumbnailCount(0, 52))
    }

    @Test
    fun samplesMatchTheTemporalCenterOfEachVisibleCell() {
        val start = 1_250L
        val end = 11_250L
        val count = 5

        val samples = (0 until count).map {
            TimelineThumbnailMath.sampleTime(start, end, count, it)
        }

        assertEquals(listOf(2_250L, 4_250L, 6_250L, 8_250L, 10_250L), samples)
        assertTrue(samples.zipWithNext().all { (left, right) -> right > left })
        assertTrue(samples.all { it in start until end })
    }

    @Test
    fun sampleTimesMapToTheCentersOfTheirTimelineCells() {
        val clipDurationMs = 10_000L
        val timelineWidthPx = 520
        val count = 5
        val cellWidthPx = timelineWidthPx / count

        val sampleCentersPx = (0 until count).map { slot ->
            val timeMs = TimelineThumbnailMath.sampleTime(0L, clipDurationMs, count, slot)
            (timeMs * timelineWidthPx / clipDurationMs).toInt()
        }

        assertEquals(listOf(52, 156, 260, 364, 468), sampleCentersPx)
        assertEquals((0 until count).map { it * cellWidthPx + cellWidthPx / 2 }, sampleCentersPx)
    }

    @Test
    fun cellRangesPartitionTheClipAndContainTheirSampleCenters() {
        val start = 1_250L
        val end = 11_250L
        val count = 5

        val ranges = (0 until count).map { slot ->
            TimelineThumbnailMath.cellStartTime(start, end, count, slot) to
                TimelineThumbnailMath.cellEndTime(start, end, count, slot)
        }
        val samples = (0 until count).map {
            TimelineThumbnailMath.sampleTime(start, end, count, it)
        }

        assertEquals(start, ranges.first().first)
        assertEquals(end, ranges.last().second)
        assertTrue(ranges.zipWithNext().all { (left, right) -> left.second == right.first })
        assertTrue(samples.indices.all { samples[it] in ranges[it].first until ranges[it].second })
    }

    @Test
    fun cellRangesStayOrderedWhenClipDurationIsNotDivisibleByCellCount() {
        val start = 17L
        val end = 1_024L
        val count = 7

        val ranges = (0 until count).map { slot ->
            TimelineThumbnailMath.cellStartTime(start, end, count, slot) to
                TimelineThumbnailMath.cellEndTime(start, end, count, slot)
        }

        assertEquals(start, ranges.first().first)
        assertEquals(end, ranges.last().second)
        assertTrue(ranges.all { it.second > it.first })
        assertTrue(ranges.zipWithNext().all { (left, right) -> left.second == right.first })
    }

    @Test
    fun oneCellSamplesItsCenterAndIndicesAreClamped() {
        assertEquals(4_750L, TimelineThumbnailMath.sampleTime(500L, 9_000L, 1, 0))
        assertEquals(8_416L, TimelineThumbnailMath.sampleTime(500L, 10_000L, 3, 99))
        assertEquals(2_083L, TimelineThumbnailMath.sampleTime(500L, 10_000L, 3, -2))
    }
}

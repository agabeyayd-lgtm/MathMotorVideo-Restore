package com.mathmotor.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineThumbnailMathTest {
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
    fun oneCellSamplesItsCenterAndIndicesAreClamped() {
        assertEquals(4_750L, TimelineThumbnailMath.sampleTime(500L, 9_000L, 1, 0))
        assertEquals(8_416L, TimelineThumbnailMath.sampleTime(500L, 10_000L, 3, 99))
        assertEquals(2_083L, TimelineThumbnailMath.sampleTime(500L, 10_000L, 3, -2))
    }
}

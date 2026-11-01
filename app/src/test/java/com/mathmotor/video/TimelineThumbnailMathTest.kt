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
    fun samplesCoverTheClipWithoutSamplingPastItsEnd() {
        val start = 1_250L
        val end = 11_250L
        val count = 5

        val samples = (0 until count).map {
            TimelineThumbnailMath.sampleTime(start, end, count, it)
        }

        assertEquals(listOf(1_250L, 3_749L, 6_249L, 8_749L, 11_249L), samples)
        assertTrue(samples.zipWithNext().all { (left, right) -> right > left })
        assertTrue(samples.all { it < end })
    }

    @Test
    fun oneCellUsesTheFirstFrameAndIndicesAreClamped() {
        assertEquals(500L, TimelineThumbnailMath.sampleTime(500L, 9_000L, 1, 0))
        assertEquals(9_999L, TimelineThumbnailMath.sampleTime(500L, 10_000L, 3, 99))
        assertEquals(500L, TimelineThumbnailMath.sampleTime(500L, 10_000L, 3, -2))
    }
}

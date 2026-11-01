package com.mathmotor.video

import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineFrameMathTest {
    @Test
    fun thirtyFpsUsesThirtyFramesBetweenSecondBoundaries() {
        assertEquals(0L, TimelineFrameMath.timeMsForFrame(0L))
        assertEquals(33L, TimelineFrameMath.timeMsForFrame(1L))
        assertEquals(67L, TimelineFrameMath.timeMsForFrame(2L))
        assertEquals(500L, TimelineFrameMath.timeMsForFrame(15L))
        assertEquals(967L, TimelineFrameMath.timeMsForFrame(29L))
        assertEquals(1_000L, TimelineFrameMath.timeMsForFrame(30L))
        assertEquals(30L, TimelineFrameMath.frameIndexForTimeMs(1_000L))
    }

    @Test
    fun arbitrarySeekTimesSnapToNearestProjectFrame() {
        assertEquals(500L, TimelineFrameMath.snapToFrameMs(487L))
        assertEquals(467L, TimelineFrameMath.snapToFrameMs(483L))
        assertEquals(0L, TimelineFrameMath.snapToFrameMs(-20L))
    }

    @Test
    fun frameConversionsRemainConsistentAcrossLongTimelines() {
        listOf(0L, 1L, 15L, 30L, 300L, 18_000L, 108_000L).forEach { frame ->
            val timeMs = TimelineFrameMath.timeMsForFrame(frame)
            assertEquals(frame, TimelineFrameMath.frameIndexForTimeMs(timeMs))
        }
    }

    @Test
    fun customFrameRatesAreSupported() {
        assertEquals(40L, TimelineFrameMath.timeMsForFrame(1L, 25))
        assertEquals(1_000L, TimelineFrameMath.timeMsForFrame(25L, 25))
        assertEquals(17L, TimelineFrameMath.timeMsForFrame(1L, 60))
        assertEquals(60L, TimelineFrameMath.frameIndexForTimeMs(1_000L, 60))
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroFpsIsRejected() {
        TimelineFrameMath.snapToFrameMs(100L, 0)
    }
}

package com.mathmotor.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimelinePositionMathTest {
    private val durations = listOf(1_500L, 2_000L, 750L)

    @Test
    fun mapsTimesWithinEachClipToLocalPosition() {
        assertEquals(TimelineClipPosition(0, 0L, 500L), TimelinePositionMath.locate(durations, 500L))
        assertEquals(TimelineClipPosition(1, 1_500L, 400L), TimelinePositionMath.locate(durations, 1_900L))
        assertEquals(TimelineClipPosition(2, 3_500L, 249L), TimelinePositionMath.locate(durations, 3_749L))
    }

    @Test
    fun exactClipBoundarySelectsFollowingClip() {
        assertEquals(TimelineClipPosition(1, 1_500L, 0L), TimelinePositionMath.locate(durations, 1_500L))
        assertEquals(TimelineClipPosition(2, 3_500L, 0L), TimelinePositionMath.locate(durations, 3_500L))
    }

    @Test
    fun negativeAndPastEndTimesAreClampedSafely() {
        assertEquals(TimelineClipPosition(0, 0L, 0L), TimelinePositionMath.locate(durations, -1L))
        assertEquals(TimelineClipPosition(2, 3_500L, 749L), TimelinePositionMath.locate(durations, 99_000L))
    }

    @Test
    fun emptyOrZeroLengthTimelinesHaveNoPosition() {
        assertNull(TimelinePositionMath.locate(emptyList(), 0L))
        assertNull(TimelinePositionMath.locate(listOf(0L, 0L), 0L))
    }
}

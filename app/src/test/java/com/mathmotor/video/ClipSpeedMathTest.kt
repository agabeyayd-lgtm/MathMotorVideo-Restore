package com.mathmotor.video

import org.junit.Assert.assertEquals
import org.junit.Test

class ClipSpeedMathTest {
    @Test
    fun durationChangesInverselyWithSpeed() {
        assertEquals(20_000L, ClipSpeedMath.timelineDurationMs(10_000L, 0.5f))
        assertEquals(10_000L, ClipSpeedMath.timelineDurationMs(10_000L, 1f))
        assertEquals(5_000L, ClipSpeedMath.timelineDurationMs(10_000L, 2f))
    }

    @Test
    fun sourceAndTimelineOffsetsRoundTrip() {
        val sourceOffset = 3_750L
        val timelineOffset = ClipSpeedMath.timelineOffsetMs(sourceOffset, 1.5f)
        assertEquals(sourceOffset, ClipSpeedMath.sourceOffsetMs(timelineOffset, 1.5f))
    }

    @Test
    fun speedIsBoundedAndInvalidValuesDefaultToNormal() {
        assertEquals(0.1f, ClipSpeedMath.normalize(0f))
        assertEquals(40f, ClipSpeedMath.normalize(40f))
        assertEquals(100f, ClipSpeedMath.normalize(120f))
        assertEquals(1f, ClipSpeedMath.normalize(Float.NaN))
    }
}

package com.mathmotor.video

import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineClipInsertionMathTest {
    @Test
    fun firstVideoStartsAtZeroWhenOnlyIndependentLanesRemain() {
        // Overlay/text/audio durations are deliberately not inputs to this rule.
        assertEquals(0L, TimelineClipInsertionMath.startTimeMs(0L))
    }

    @Test
    fun nextVideoStartsAtTheEndOfTheExistingVideoLane() {
        // A longer independent lane must not add a blank gap to the video row.
        assertEquals(8_000L, TimelineClipInsertionMath.startTimeMs(8_000L))
    }

    @Test
    fun negativeVideoDurationIsClampedToZero() {
        assertEquals(0L, TimelineClipInsertionMath.startTimeMs(-1L))
    }
}

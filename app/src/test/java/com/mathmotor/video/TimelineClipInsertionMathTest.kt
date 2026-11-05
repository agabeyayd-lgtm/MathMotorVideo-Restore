package com.mathmotor.video

import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineClipInsertionMathTest {
    @Test
    fun firstVideoStartsAtZeroEvenWhenIndependentLanesKeepProjectDuration() {
        assertEquals(
            0L,
            TimelineClipInsertionMath.startTimeMs(
                existingVideoClipCount = 0,
                projectDurationMs = 10_000L
            )
        )
    }

    @Test
    fun additionalVideoAppendsAfterExistingProjectContent() {
        assertEquals(
            10_000L,
            TimelineClipInsertionMath.startTimeMs(
                existingVideoClipCount = 1,
                projectDurationMs = 10_000L
            )
        )
    }

    @Test
    fun negativeProjectDurationIsClampedWhenAppending() {
        assertEquals(
            0L,
            TimelineClipInsertionMath.startTimeMs(
                existingVideoClipCount = 2,
                projectDurationMs = -1L
            )
        )
    }
}

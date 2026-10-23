package com.mathmotor.video

import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineTrimMathTest {
    @Test
    fun startCannotCrossEndOrLeaveLessThanHalfASecond() {
        assertEquals(4_500L, TimelineTrimMath.clampStart(9_000L, 5_000L, 10_000L))
        assertEquals(0L, TimelineTrimMath.clampStart(-100L, 5_000L, 10_000L))
        assertEquals(9_500L, TimelineTrimMath.clampStart(12_000L, 10_000L, 10_000L))
    }

    @Test
    fun endCannotCrossStartOrLeaveLessThanHalfASecond() {
        assertEquals(5_500L, TimelineTrimMath.clampEnd(1_000L, 5_000L, 10_000L))
        assertEquals(10_000L, TimelineTrimMath.clampEnd(20_000L, 9_500L, 10_000L))
        assertEquals(500L, TimelineTrimMath.clampEnd(0L, 0L, 10_000L))
    }

    @Test
    fun sourceShorterThanMinimumRemainsTrimmableWithoutInvalidBounds() {
        assertEquals(0L, TimelineTrimMath.clampStart(50L, 300L, 300L))
        assertEquals(300L, TimelineTrimMath.clampEnd(10L, 0L, 300L))
        assertEquals(0L, TimelineTrimMath.clampStart(100L, 0L, 0L))
        assertEquals(0L, TimelineTrimMath.clampEnd(100L, 0L, 0L))
    }

    @Test
    fun startAndEndStayWithinSourceDuration() {
        assertEquals(0L, TimelineTrimMath.clampStart(3_000L, 200L, 10_000L))
        assertEquals(10_000L, TimelineTrimMath.clampEnd(12_000L, 10_000L, 10_000L))
    }
}

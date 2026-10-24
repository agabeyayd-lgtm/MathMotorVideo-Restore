package com.mathmotor.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun trimEdgesCanBeMovedBackAfterReachingTheirLimits() {
        assertEquals(9_500L, TimelineTrimMath.clampStart(9_500L, 10_000L, 10_000L))
        assertEquals(0L, TimelineTrimMath.clampStart(0L, 10_000L, 10_000L))
        assertEquals(500L, TimelineTrimMath.clampEnd(0L, 0L, 10_000L))
        assertEquals(10_000L, TimelineTrimMath.clampEnd(10_000L, 0L, 10_000L))
        assertEquals(500L, TimelineTrimMath.clampEnd(500L, 0L, 10_000L))
    }

    @Test
    fun startBoundaryUsesCurrentEndAndEndBoundaryUsesCurrentStart() {
        assertEquals(3_500L, TimelineTrimMath.maxStartMs(4_000L, 10_000L))
        assertEquals(3_500L, TimelineTrimMath.clampStart(9_000L, 4_000L, 10_000L))
        assertEquals(8_500L, TimelineTrimMath.minEndMs(8_000L, 10_000L))
        assertEquals(8_500L, TimelineTrimMath.clampEnd(1_000L, 8_000L, 10_000L))
    }

    @Test
    fun millisecondSeekbarMappingRoundTripsRegularAndVeryLongMedia() {
        val ordinaryDuration = 123_456L
        listOf(0L, 1L, 500L, 12_345L, ordinaryDuration).forEach { time ->
            val progress = TimelineTrimMath.progressForTime(time, ordinaryDuration)
            assertEquals(time, TimelineTrimMath.timeForProgress(progress, ordinaryDuration))
        }
        val longDuration = Int.MAX_VALUE.toLong() + 987_654L
        val tolerance = kotlin.math.ceil(longDuration.toDouble() / Int.MAX_VALUE).toLong()
        listOf(0L, 1_000L, longDuration / 2, longDuration).forEach { time ->
            val progress = TimelineTrimMath.progressForTime(time, longDuration)
            val restored = TimelineTrimMath.timeForProgress(progress, longDuration)
            assertTrue(kotlin.math.abs(restored - time) <= tolerance)
        }
    }
}

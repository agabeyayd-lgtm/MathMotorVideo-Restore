package com.mathmotor.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextTimingMathTest {
    @Test
    fun intervalIncludesStartButExcludesEnd() {
        assertTrue(TextTimingMath.isVisibleAt(1_000L, 1_000L, 2_000L, 5_000L))
        assertTrue(TextTimingMath.isVisibleAt(1_999L, 1_000L, 2_000L, 5_000L))
        assertFalse(TextTimingMath.isVisibleAt(2_000L, 1_000L, 2_000L, 5_000L))
        assertFalse(TextTimingMath.isVisibleAt(0L, 1_000L, 2_000L, 5_000L))
    }

    @Test
    fun startCannotMovePastEnd() {
        assertEquals(
            TextTimingInterval(1_900L, 2_000L),
            TextTimingMath.moveStart(4_900L, 2_000L, 5_000L)
        )
    }

    @Test
    fun endCannotCollapseToZeroAndExpandToWholeProject() {
        assertEquals(
            TextTimingInterval(1_000L, 1_100L),
            TextTimingMath.moveEnd(0L, 1_000L, 5_000L)
        )
    }

    @Test
    fun veryShortProjectStillGetsAValidInterval() {
        assertEquals(
            TextTimingInterval(0L, 55L),
            TextTimingMath.normalize(500L, 0L, 55L)
        )
        assertFalse(TextTimingMath.isVisibleAt(55L, 0L, 55L, 55L))
    }

    @Test
    fun normalizeClampsIntervalToProjectBounds() {
        assertEquals(
            TextTimingInterval(4_900L, 5_000L),
            TextTimingMath.normalize(8_000L, 2_000L, 5_000L)
        )
        assertEquals(
            TextTimingInterval(0L, 0L),
            TextTimingMath.normalize(0L, 1_000L, 0L)
        )
    }
}

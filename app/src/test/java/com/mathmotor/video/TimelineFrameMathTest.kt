package com.mathmotor.video

import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineFrameMathTest {
    @Test
    fun thirtyFpsContainsThirtyFramesPerSecond() {
        assertEquals(30L, TimelineFrameMath.frameIndexAtTimeMs(1_000L))
        assertEquals(60L, TimelineFrameMath.frameIndexAtTimeMs(2_000L))
        assertEquals(300L, TimelineFrameMath.frameIndexAtTimeMs(10_000L))
    }

    @Test
    fun frameTimestampsUseTheThirtyFpsGrid() {
        assertEquals(0L, TimelineFrameMath.timeMsForFrame(0L))
        assertEquals(33L, TimelineFrameMath.timeMsForFrame(1L))
        assertEquals(67L, TimelineFrameMath.timeMsForFrame(2L))
        assertEquals(500L, TimelineFrameMath.timeMsForFrame(15L))
        assertEquals(1_000L, TimelineFrameMath.timeMsForFrame(30L))
        assertEquals(33_333L, TimelineFrameMath.timeUsForFrame(1L))
    }

    @Test
    fun scrubbingSnapsToNearestFrameWithoutAccumulatingMillisecondDrift() {
        assertEquals(500L, TimelineFrameMath.snapTimeMs(487L))
        assertEquals(500L, TimelineFrameMath.snapTimeMs(516L))
        assertEquals(533L, TimelineFrameMath.snapTimeMs(517L))
        assertEquals(1_000L, TimelineFrameMath.snapTimeMs(984L))
    }

    @Test
    fun frameRulerUsesReadableIntervalsAtDifferentZoomLevels() {
        assertEquals(5L, TimelineFrameMath.frameStepForPixelSpacing(176f, 30f))
        assertEquals(10L, TimelineFrameMath.frameStepForPixelSpacing(88f, 30f))
        assertEquals(60L, TimelineFrameMath.frameStepForPixelSpacing(22f, 30f))
    }

    @Test
    fun invalidInputsRemainSafe() {
        assertEquals(0L, TimelineFrameMath.frameIndexAtTimeMs(-1L))
        assertEquals(0L, TimelineFrameMath.timeUsForFrame(12L, 0))
        assertEquals(0L, TimelineFrameMath.snapTimeMs(300L, 0))
        assertEquals(1L, TimelineFrameMath.frameStepForPixelSpacing(0f, 30f))
    }
}

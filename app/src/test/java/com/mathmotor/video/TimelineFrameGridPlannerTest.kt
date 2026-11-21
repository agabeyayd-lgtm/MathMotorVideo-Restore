package com.mathmotor.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineFrameGridPlannerTest {
    @Test
    fun emitsCoarseToFineStagesWithoutDuplicateTimestamps() {
        val frames = TimelineFrameGridPlanner.orderedFrames(10_001L, 0L, 6_000L)
        assertEquals(4_000L, frames.first().stageMs)
        assertEquals(0L, frames.first().timestampMs)
        assertEquals(listOf(4_000L, 2_000L, 1_000L, 500L), frames.map { it.stageMs }.distinct())
        assertEquals(frames.size, frames.map { it.timestampMs }.distinct().size)
        assertTrue(frames.any { it.timestampMs == 10_000L })
    }

    @Test
    fun prioritizesVisibleRangeWithinEachCoarseToFineStage() {
        val frames = TimelineFrameGridPlanner.orderedFrames(20_000L, 8_000L, 12_000L)
        val fourSecond = frames.filter { it.stageMs == 4_000L }
        assertTrue(fourSecond.indexOfFirst { it.timestampMs == 8_000L } <
            fourSecond.indexOfFirst { it.timestampMs == 4_000L })
        assertTrue(fourSecond.indexOfFirst { it.timestampMs == 12_000L } <
            fourSecond.indexOfFirst { it.timestampMs == 16_000L })
    }

    @Test
    fun halfSecondStageCoversEveryHalfSecondTimestampAndFinalFrame() {
        val duration = 5_251L
        val frames = TimelineFrameGridPlanner.orderedFrames(duration, 0L, 1_000L)
        val timestamps = frames.map { it.timestampMs }.toSet()
        var timestamp = 0L
        while (timestamp < duration) {
            assertTrue("Missing grid timestamp $timestamp", timestamp in timestamps)
            timestamp += 500L
        }
        assertTrue(duration - 1L in timestamps)
    }

    @Test
    fun invalidDurationProducesNoWork() {
        assertTrue(TimelineFrameGridPlanner.orderedFrames(0L, 0L, 0L).isEmpty())
    }
}

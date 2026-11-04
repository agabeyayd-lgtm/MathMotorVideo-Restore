package com.mathmotor.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimelineVideoSpeedMathTest {
    @Test
    fun speedChangesTheProjectDurationWithoutChangingSourceInOut() {
        val source = TimelineVideoSegment(
            timelineStartMs = 2_000L,
            sourceStartMs = 1_000L,
            sourceEndMs = 9_000L,
            sourceDurationMs = 10_000L,
            speed = 2f
        )

        assertEquals(4_000L, source.durationMs)
        assertEquals(6_000L, source.timelineEndMs)
        assertEquals(1_000L, source.sourceStartMs)
        assertEquals(9_000L, source.sourceEndMs)
    }

    @Test
    fun projectTimestampMapsToSpeedAdjustedSourceTimestamp() {
        val source = TimelineVideoSegment(
            timelineStartMs = 1_500L,
            sourceStartMs = 2_000L,
            sourceEndMs = 8_000L,
            sourceDurationMs = 10_000L,
            speed = 2f
        )

        assertEquals(5_000L, TimelineVideoMath.sourceTimeAtProjectTime(source, 3_000L))
        assertNull(TimelineVideoMath.sourceTimeAtProjectTime(source, 4_500L))
    }

    @Test
    fun slowMotionExpandsTheVisibleProjectRange() {
        val source = TimelineVideoSegment(
            timelineStartMs = 0L,
            sourceStartMs = 0L,
            sourceEndMs = 5_000L,
            sourceDurationMs = 5_000L,
            speed = 0.5f
        )

        assertEquals(10_000L, TimelineVideoMath.totalDuration(listOf(source)))
        assertEquals(1, TimelineVideoMath.activeSegmentIndexAt(listOf(source), 9_999L))
        assertNull(TimelineVideoMath.activeSegmentIndexAt(listOf(source), 10_000L))
    }

    @Test
    fun movingAndTrimmingKeepSpeedAndSourceTimelineMappingConsistent() {
        val source = TimelineVideoSegment(
            timelineStartMs = 2_000L,
            sourceStartMs = 1_000L,
            sourceEndMs = 9_000L,
            sourceDurationMs = 10_000L,
            speed = 2f
        )
        val moved = TimelineVideoMath.move(source, 5_000L)
        assertEquals(5_000L, moved.timelineStartMs)
        assertEquals(2f, moved.speed)

        val leftTrimmed = TimelineVideoMath.trimStart(source, 1_000L)
        assertEquals(2_500L, leftTrimmed.timelineStartMs)
        assertEquals(2_000L, leftTrimmed.sourceStartMs)
        assertEquals(7_000L, leftTrimmed.durationMs)

        val rightTrimmed = TimelineVideoMath.trimEnd(source, -1_000L)
        assertEquals(2_000L, rightTrimmed.timelineStartMs)
        assertEquals(8_000L, rightTrimmed.sourceEndMs)
        assertEquals(3_500L, rightTrimmed.durationMs)
    }

    @Test
    fun speedDefaultsAndClampingKeepLegacyProjectsValid() {
        val legacy = TimelineVideoSegment(0L, 0L, 2_000L, 2_000L)
        assertEquals(1f, TimelineVideoMath.normalize(legacy).speed)
        assertEquals(2_000L, legacy.durationMs)

        val invalid = legacy.copy(speed = Float.NaN)
        assertEquals(1f, TimelineVideoMath.normalize(invalid).speed)
    }
}

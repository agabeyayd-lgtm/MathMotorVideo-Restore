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


class TimelineAudioMathTest {
    @Test
    fun movingAnAudioItemKeepsItsSourceTrimAndDuration() {
        val original = TimelineAudioSegment(
            timelineStartMs = 2_000L,
            sourceStartMs = 1_500L,
            durationMs = 3_000L
        )
        assertEquals(
            TimelineAudioSegment(5_000L, 1_500L, 3_000L),
            TimelineAudioMath.move(original, 5_000L, 10_000L, 10_000L)
        )
        assertEquals(
            TimelineAudioSegment(7_000L, 1_500L, 3_000L),
            TimelineAudioMath.move(original, 99_000L, 10_000L, 10_000L)
        )
    }

    @Test
    fun trimmingStartChangesTimelineAndSourceStartTogether() {
        val original = TimelineAudioSegment(2_000L, 1_000L, 5_000L)
        assertEquals(
            TimelineAudioSegment(3_000L, 2_000L, 4_000L),
            TimelineAudioMath.trimStart(original, 1_000L, 10_000L, 10_000L)
        )
        assertEquals(
            TimelineAudioSegment(1_000L, 0L, 6_000L),
            TimelineAudioMath.trimStart(original, -2_000L, 10_000L, 10_000L)
        )
    }

    @Test
    fun trimmingEndRespectsSourceAndProjectBoundaries() {
        val original = TimelineAudioSegment(2_000L, 1_000L, 4_000L)
        assertEquals(
            TimelineAudioSegment(2_000L, 1_000L, 6_000L),
            TimelineAudioMath.trimEnd(original, 2_000L, 10_000L, 10_000L)
        )
        assertEquals(
            TimelineAudioSegment(2_000L, 1_000L, 100L),
            TimelineAudioMath.trimEnd(original, -9_000L, 10_000L, 10_000L)
        )
    }

    @Test
    fun normalizationPreventsAudioItemsFromLeavingSourceOrTimeline() {
        assertEquals(
            TimelineAudioSegment(9_000L, 10_000L, 0L),
            TimelineAudioMath.normalize(
                TimelineAudioSegment(20_000L, 15_000L, 9_000L),
                sourceDurationMs = 10_000L,
                timelineDurationMs = 9_000L
            )
        )
    }
}


class TimelineRangeMathTest {
    @Test
    fun movingMusicChangesOnlyItsOwnTimelineStart() {
        val original = TimelineRange(2_000L, 5_000L)
        assertEquals(
            TimelineRange(4_000L, 5_000L),
            TimelineRangeMath.move(original, 4_000L, 10_000L)
        )
        assertEquals(
            TimelineRange(5_000L, 5_000L),
            TimelineRangeMath.move(original, 99_000L, 10_000L)
        )
    }

    @Test
    fun leftAndRightTrimAdjustTheMusicItemRange() {
        val original = TimelineRange(2_000L, 6_000L)
        assertEquals(
            TimelineRange(3_000L, 5_000L),
            TimelineRangeMath.trimStart(original, 1_000L, 10_000L)
        )
        assertEquals(
            TimelineRange(2_000L, 4_000L),
            TimelineRangeMath.trimEnd(original, -2_000L, 10_000L)
        )
    }

    @Test
    fun musicRangeCannotMoveOrExtendOutsideTheVideoTimeline() {
        assertEquals(
            TimelineRange(10_000L, 0L),
            TimelineRangeMath.normalize(TimelineRange(20_000L, 8_000L), 10_000L)
        )
        assertEquals(
            TimelineRange(0L, 8_000L),
            TimelineRangeMath.move(TimelineRange(2_000L, 8_000L), -5_000L, 10_000L)
        )
    }

    @Test
    fun trimmingCannotInvertOrEmptyAnExistingMusicRange() {
        assertEquals(
            TimelineRange(7_900L, 100L),
            TimelineRangeMath.trimStart(TimelineRange(2_000L, 6_000L), 99_000L, 10_000L)
        )
        assertEquals(
            TimelineRange(2_000L, 100L),
            TimelineRangeMath.trimEnd(TimelineRange(2_000L, 6_000L), -99_000L, 10_000L)
        )
    }
}


class TimelineVideoMathTest {
    @Test
    fun totalDurationUsesFurthestEndInsteadOfAddingOverlaps() {
        val segments = listOf(
            TimelineVideoSegment(0L, 0L, 5_000L, 5_000L),
            TimelineVideoSegment(2_000L, 0L, 4_000L, 4_000L),
            TimelineVideoSegment(9_000L, 500L, 2_000L, 2_000L)
        )
        assertEquals(10_500L, TimelineVideoMath.totalDuration(segments))
    }

    @Test
    fun overlapSelectsTopLayerAndGapSelectsNoClip() {
        val segments = listOf(
            TimelineVideoSegment(0L, 0L, 5_000L, 5_000L),
            TimelineVideoSegment(2_000L, 0L, 4_000L, 4_000L)
        )
        assertEquals(0, TimelineVideoMath.activeSegmentIndexAt(segments, 1_000L))
        assertEquals(1, TimelineVideoMath.activeSegmentIndexAt(segments, 2_500L))
        assertEquals(null, TimelineVideoMath.activeSegmentIndexAt(segments, 6_000L))
    }

    @Test
    fun overlappingClipsUseRowsThatMatchLayerPriority() {
        val segments = listOf(
            TimelineVideoSegment(0L, 0L, 5_000L, 5_000L),
            TimelineVideoSegment(2_000L, 0L, 4_000L, 4_000L)
        )
        // The later list item is the top layer, so it must appear in row zero.
        assertEquals(listOf(listOf(1), listOf(0)), TimelineVideoMath.assignLanes(segments))
    }

    @Test
    fun nonOverlappingClipsCanShareOneChronologicalRow() {
        val segments = listOf(
            TimelineVideoSegment(5_000L, 0L, 2_000L, 2_000L),
            TimelineVideoSegment(0L, 0L, 3_000L, 3_000L)
        )
        assertEquals(listOf(listOf(1, 0)), TimelineVideoMath.assignLanes(segments))
    }

    @Test
    fun movePreservesSourceRangeAndDoesNotAlterOtherSegment() {
        val source = TimelineVideoSegment(0L, 1_000L, 4_000L, 8_000L)
        val independent = TimelineVideoSegment(7_000L, 0L, 2_000L, 2_000L)
        assertEquals(
            TimelineVideoSegment(5_000L, 1_000L, 4_000L, 8_000L),
            TimelineVideoMath.move(source, 5_000L)
        )
        assertEquals(TimelineVideoSegment(7_000L, 0L, 2_000L, 2_000L), independent)
    }

    @Test
    fun leftTrimMovesProjectStartAndSourceInTogether() {
        val source = TimelineVideoSegment(3_000L, 2_000L, 8_000L, 10_000L)
        assertEquals(
            TimelineVideoSegment(4_000L, 3_000L, 8_000L, 10_000L),
            TimelineVideoMath.trimStart(source, 1_000L)
        )
        assertEquals(
            TimelineVideoSegment(1_000L, 0L, 8_000L, 10_000L),
            TimelineVideoMath.trimStart(source, -3_000L)
        )
    }

    @Test
    fun rightTrimChangesOnlySourceOutAndRespectsMinimumSpan() {
        val source = TimelineVideoSegment(3_000L, 2_000L, 8_000L, 10_000L)
        assertEquals(
            TimelineVideoSegment(3_000L, 2_000L, 9_000L, 10_000L),
            TimelineVideoMath.trimEnd(source, 1_000L)
        )
        assertEquals(
            TimelineVideoSegment(3_000L, 2_000L, 2_500L, 10_000L),
            TimelineVideoMath.trimEnd(source, -9_000L)
        )
    }

    @Test
    fun leftTrimCannotCrossProjectOrSourceZero() {
        val source = TimelineVideoSegment(500L, 2_000L, 5_000L, 8_000L)
        assertEquals(
            TimelineVideoSegment(0L, 1_500L, 5_000L, 8_000L),
            TimelineVideoMath.trimStart(source, -5_000L)
        )
    }
}

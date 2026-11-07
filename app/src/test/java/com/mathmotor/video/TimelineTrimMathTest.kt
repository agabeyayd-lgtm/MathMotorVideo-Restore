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


class MusicSourceRangeMathTest {
    @Test
    fun sourceRangeIsClampedAndRemainsOrdered() {
        assertEquals(MusicSourceRange(0L, 10_000L), MusicSourceRangeMath.normalize(MusicSourceRange(-5L, 20_000L), 10_000L))
        assertEquals(MusicSourceRange(2_000L, 8_000L), MusicSourceRangeMath.normalize(MusicSourceRange(2_000L, 8_000L), 10_000L))
        assertEquals(MusicSourceRange(9_900L, 10_000L), MusicSourceRangeMath.start(MusicSourceRange(0L, 10_000L), 9_999L, 10_000L))
        assertEquals(MusicSourceRange(2_000L, 2_100L), MusicSourceRangeMath.end(MusicSourceRange(2_000L, 8_000L), 0L, 10_000L))
    }

    @Test
    fun sourcePositionLoopsOnlyInsideSelectedRange() {
        val range = MusicSourceRange(3_000L, 7_000L)
        assertEquals(3_000L, MusicSourceRangeMath.sourcePosition(range, 0L, 10_000L))
        assertEquals(6_500L, MusicSourceRangeMath.sourcePosition(range, 3_500L, 10_000L))
        assertEquals(3_500L, MusicSourceRangeMath.sourcePosition(range, 4_500L, 10_000L))
    }

    @Test
    fun previewSeeksImmediatelyWhenPlaybackEscapesSelectedRange() {
        val range = MusicSourceRange(3_000L, 7_000L)
        assertTrue(MusicSourceRangeMath.needsPreviewSeek(7_025L, 3_025L, range))
        assertTrue(MusicSourceRangeMath.needsPreviewSeek(2_999L, 3_000L, range))
        assertTrue(MusicSourceRangeMath.needsPreviewSeek(4_000L, 4_500L, range))
        assertEquals(false, MusicSourceRangeMath.needsPreviewSeek(4_050L, 4_000L, range))
    }

    @Test
    fun emptyOrInvalidSourceRangeNeverProducesNegativeDuration() {
        assertEquals(0L, MusicSourceRangeMath.normalize(MusicSourceRange(5_000L, 2_000L), 10_000L).durationMs)
        assertEquals(0L, MusicSourceRangeMath.sourcePosition(MusicSourceRange(0L, 0L), 500L, 10_000L))
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
    fun playheadTimeMapsToCorrectSourceFrameAndReturnsNullForTimelineGaps() {
        val first = TimelineVideoSegment(0L, 1_000L, 5_000L, 8_000L)
        val second = TimelineVideoSegment(6_000L, 2_000L, 7_000L, 10_000L)
        assertEquals(3_500L, TimelineVideoMath.sourceTimeAtProjectTime(first, 2_500L))
        assertEquals(2_000L, TimelineVideoMath.sourceTimeAtProjectTime(second, 6_000L))
        assertEquals(null, TimelineVideoMath.sourceTimeAtProjectTime(first, 5_000L))
        assertEquals(null, TimelineVideoMath.sourceTimeAtProjectTime(second, 5_500L))
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
    fun everyClipUsesTheSameVisualStripAndKeepsItsExactTimelineRange() {
        val segments = listOf(
            TimelineVideoSegment(0L, 0L, 5_000L, 5_000L),
            TimelineVideoSegment(2_000L, 0L, 4_000L, 4_000L),
            TimelineVideoSegment(9_000L, 500L, 2_000L, 2_000L)
        )

        val placements = TimelineVideoMath.singleTrackPlacements(segments)

        assertEquals(listOf(0, 1, 2), placements.map { it.clipIndex })
        assertEquals(listOf(0L, 2_000L, 9_000L), placements.map { it.timelineStartMs })
        assertEquals(listOf(5_000L, 6_000L, 10_500L), placements.map { it.timelineEndMs })
        assertEquals(listOf(0, 1, 2), placements.map { it.layerOrder })
        // The first two clips still overlap in time; overlap does not create a row.
        assertEquals(1, TimelineVideoMath.activeSegmentIndexAt(segments, 2_500L))
    }

    @Test
    fun singleStripPlacementIncludesAllClipsEvenWhenOneIsFullyCovered() {
        val segments = listOf(
            TimelineVideoSegment(1_000L, 0L, 8_000L, 8_000L),
            TimelineVideoSegment(1_000L, 0L, 8_000L, 8_000L),
            TimelineVideoSegment(12_000L, 0L, 3_000L, 3_000L)
        )

        val placements = TimelineVideoMath.singleTrackPlacements(segments)

        assertEquals(3, placements.size)
        assertEquals(listOf(0, 1, 2), placements.map { it.clipIndex })
        assertEquals(placements[0].timelineStartMs, placements[1].timelineStartMs)
        assertEquals(placements[0].timelineEndMs, placements[1].timelineEndMs)
        // The higher layer remains the preview winner while either item is editable.
        assertEquals(1, TimelineVideoMath.activeSegmentIndexAt(segments, 4_000L))
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
    fun trimmingSourceStartAtTimelineZeroDoesNotCreateLeadingGap() {
        val first = TimelineVideoSegment(
            timelineStartMs = 0L,
            sourceStartMs = 0L,
            sourceEndMs = 6_320L,
            sourceDurationMs = 10_030L
        )
        val trimmed = TimelineVideoMath.trimStartKeepingTimelinePosition(first, 1_280L)
        assertEquals(0L, trimmed.timelineStartMs)
        assertEquals(1_280L, trimmed.sourceStartMs)
        assertEquals(6_320L, trimmed.sourceEndMs)
        assertEquals(5_040L, trimmed.durationMs)
        assertEquals(5_040L, trimmed.timelineEndMs)
    }

    @Test
    fun restoringSourceAtTimelineZeroKeepsProjectStartAtZero() {
        val first = TimelineVideoSegment(
            timelineStartMs = 0L,
            sourceStartMs = 1_280L,
            sourceEndMs = 6_320L,
            sourceDurationMs = 10_030L
        )
        val restored = TimelineVideoMath.trimStartKeepingTimelinePosition(first, -1_280L)
        assertEquals(0L, restored.timelineStartMs)
        assertEquals(0L, restored.sourceStartMs)
        assertEquals(6_320L, restored.sourceEndMs)
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
    fun droppingASplitSegmentOnAnOccupiedClipReordersTheMainVideoLane() {
        val segments = listOf(
            TimelineVideoSegment(0L, 0L, 3_000L, 3_000L),
            TimelineVideoSegment(3_000L, 0L, 5_000L, 5_000L),
            TimelineVideoSegment(8_000L, 1_000L, 3_000L, 5_000L)
        )

        val plan = TimelineVideoMath.reorderForDrop(
            segments = segments,
            movingIndex = 2,
            requestedStartMs = 3_000L
        )

        assertEquals(listOf(0, 2, 1), plan?.orderedClipIndices)
        assertEquals(0L, plan?.timelineStartByClipIndex?.get(0))
        assertEquals(3_000L, plan?.timelineStartByClipIndex?.get(2))
        assertEquals(5_000L, plan?.timelineStartByClipIndex?.get(1))
        // Source trim points and durations are never rewritten by the reorder plan.
        assertEquals(1_000L, segments[2].sourceStartMs)
        assertEquals(2_000L, segments[2].durationMs)
    }

    @Test
    fun movingAClipIntoAnEmptyGapPreservesEveryOtherClipPosition() {
        val segments = listOf(
            TimelineVideoSegment(0L, 0L, 2_000L, 2_000L),
            TimelineVideoSegment(4_000L, 0L, 2_000L, 2_000L),
            TimelineVideoSegment(7_000L, 0L, 1_000L, 1_000L)
        )

        val plan = TimelineVideoMath.reorderForDrop(
            segments = segments,
            movingIndex = 0,
            requestedStartMs = 9_000L
        )!!

        assertEquals(listOf(0, 1, 2), plan.orderedClipIndices)
        assertEquals(9_000L, plan.timelineStartByClipIndex[0])
        assertEquals(4_000L, plan.timelineStartByClipIndex[1])
        assertEquals(7_000L, plan.timelineStartByClipIndex[2])
    }

    @Test
    fun aSingleVideoClipCanMoveIntoAnEmptyTimelinePosition() {
        val plan = TimelineVideoMath.reorderForDrop(
            listOf(TimelineVideoSegment(0L, 0L, 4_000L, 4_000L)),
            movingIndex = 0,
            requestedStartMs = 6_500L
        )!!

        assertEquals(listOf(0), plan.orderedClipIndices)
        assertEquals(6_500L, plan.timelineStartByClipIndex[0])
    }

    @Test
    fun droppingAtTheFirstClipStartInsertsALongerMovedClipBeforeIt() {
        val segments = listOf(
            TimelineVideoSegment(0L, 0L, 1_500L, 1_500L),
            TimelineVideoSegment(1_500L, 0L, 6_000L, 6_000L)
        )

        val plan = TimelineVideoMath.reorderForDrop(
            segments = segments,
            movingIndex = 1,
            requestedStartMs = 0L
        )

        assertEquals(listOf(1, 0), plan?.orderedClipIndices)
        assertEquals(0L, plan?.timelineStartByClipIndex?.get(1))
        assertEquals(6_000L, plan?.timelineStartByClipIndex?.get(0))
    }

    @Test
    fun movingTheFirstClipIntoAnEmptyTailGapDoesNotShiftNeighbours() {
        val segments = listOf(
            TimelineVideoSegment(0L, 0L, 2_000L, 2_000L),
            TimelineVideoSegment(2_000L, 500L, 4_000L, 5_000L),
            TimelineVideoSegment(5_500L, 0L, 1_000L, 1_000L)
        )

        val plan = TimelineVideoMath.reorderForDrop(
            segments = segments,
            movingIndex = 0,
            requestedStartMs = 8_000L
        )!!

        assertEquals(listOf(0, 1, 2), plan.orderedClipIndices)
        assertEquals(8_000L, plan.timelineStartByClipIndex[0])
        assertEquals(2_000L, plan.timelineStartByClipIndex[1])
        assertEquals(5_500L, plan.timelineStartByClipIndex[2])
    }

    @Test
    fun reorderRepairsOverlappingMainVideoClipsAndNeverReturnsAnOverlap() {
        val overlapping = listOf(
            TimelineVideoSegment(0L, 0L, 5_000L, 5_000L),
            TimelineVideoSegment(2_000L, 500L, 4_000L, 4_000L),
            TimelineVideoSegment(9_000L, 0L, 1_000L, 1_000L)
        )

        val plan = TimelineVideoMath.reorderForDrop(overlapping, 1, 10_000L)!!

        assertEquals(listOf(0, 1, 2), plan.orderedClipIndices)
        assertEquals(0L, plan.timelineStartByClipIndex[0])
        assertEquals(10_000L, plan.timelineStartByClipIndex[1])
        assertEquals(9_000L, plan.timelineStartByClipIndex[2])
        // Source trim and speed remain untouched when the project start changes.
        assertEquals(
            TimelineVideoSegment(10_000L, 500L, 4_000L, 4_000L),
            overlapping[1].copy(timelineStartMs = plan.timelineStartByClipIndex[1]!!)
        )
    }

    @Test
    fun overlapRepairPreservesExistingGapsButPushesOnlyTheCollidingClip() {
        val segments = listOf(
            TimelineVideoSegment(0L, 0L, 2_000L, 2_000L),
            TimelineVideoSegment(1_500L, 300L, 3_000L, 3_000L),
            TimelineVideoSegment(7_000L, 0L, 1_000L, 1_000L)
        )

        val plan = TimelineVideoMath.resolveOverlaps(segments)!!

        assertEquals(listOf(0, 1, 2), plan.orderedClipIndices)
        assertEquals(0L, plan.timelineStartByClipIndex[0])
        assertEquals(2_000L, plan.timelineStartByClipIndex[1])
        assertEquals(7_000L, plan.timelineStartByClipIndex[2])
    }

    @Test
    fun splitUsesTheLeftSegmentEndAsTheRightSegmentStartForEveryPlaybackSpeed() {
        listOf(0.5f, 1f, 1.75f, 2f).forEach { speed ->
            val segment = TimelineVideoSegment(
                timelineStartMs = 1_250L,
                sourceStartMs = 1_000L,
                sourceEndMs = 9_000L,
                sourceDurationMs = 10_000L,
                speed = speed
            )
            val requested = segment.timelineStartMs + segment.durationMs / 2L + 7L
            val split = TimelineVideoMath.splitAtProjectTime(segment, requested)!!

            assertEquals(split.first.timelineEndMs, split.second.timelineStartMs)
            assertEquals(split.first.sourceEndMs, split.second.sourceStartMs)
            assertTrue(split.first.durationMs >= 350L)
            assertTrue(split.second.durationMs >= 350L)
            assertEquals(segment.timelineStartMs, split.first.timelineStartMs)
        }
    }

    @Test
    fun splitRejectsAPlayheadOutsideTheClipOrTooCloseToEitherEdge() {
        val segment = TimelineVideoSegment(1_000L, 0L, 2_000L, 2_000L)

        assertEquals(null, TimelineVideoMath.splitAtProjectTime(segment, 999L))
        assertEquals(null, TimelineVideoMath.splitAtProjectTime(segment, 1_100L))
        assertEquals(null, TimelineVideoMath.splitAtProjectTime(segment, 2_900L))
        assertEquals(null, TimelineVideoMath.splitAtProjectTime(segment, 3_000L))
    }

    @Test
    fun droppingInsideAnEmptyVideoLaneGapPreservesTheGap() {
        val segments = listOf(
            TimelineVideoSegment(0L, 0L, 2_000L, 2_000L),
            TimelineVideoSegment(5_000L, 0L, 2_000L, 2_000L)
        )

        val plan = TimelineVideoMath.reorderForDrop(
            segments = segments,
            movingIndex = 1,
            requestedStartMs = 5_100L
        )

        assertEquals(listOf(0, 1), plan?.orderedClipIndices)
        assertEquals(5_100L, plan?.timelineStartByClipIndex?.get(1))
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

package com.mathmotor.video

import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Shared source-time bounds and SeekBar mapping for clip trim controls. */
internal object TimelineTrimMath {
    const val MIN_CLIP_DURATION_MS = 500L

    fun progressMax(durationMs: Long): Int =
        durationMs.coerceAtLeast(1L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    fun timeForProgress(progress: Int, durationMs: Long): Long {
        val duration = durationMs.coerceAtLeast(0L)
        if (duration == 0L) return 0L
        val maxProgress = progressMax(duration)
        val safeProgress = progress.coerceIn(0, maxProgress)
        return if (duration <= Int.MAX_VALUE.toLong()) {
            safeProgress.toLong().coerceAtMost(duration)
        } else {
            (safeProgress.toDouble() * duration / maxProgress).roundToLong().coerceIn(0L, duration)
        }
    }

    fun progressForTime(timeMs: Long, durationMs: Long): Int {
        val duration = durationMs.coerceAtLeast(0L)
        if (duration == 0L) return 0
        val safeTime = timeMs.coerceIn(0L, duration)
        val maxProgress = progressMax(duration)
        return if (duration <= Int.MAX_VALUE.toLong()) {
            safeTime.toInt().coerceIn(0, maxProgress)
        } else {
            (safeTime.toDouble() / duration * maxProgress).roundToInt().coerceIn(0, maxProgress)
        }
    }

    fun maxStartMs(endMs: Long, sourceDurationMs: Long, minimumSpanMs: Long = MIN_CLIP_DURATION_MS): Long {
        val duration = sourceDurationMs.coerceAtLeast(0L)
        val minimum = minimumSpanMs.coerceAtLeast(1L).coerceAtMost(duration)
        val safeEnd = endMs.coerceIn(0L, duration)
        return (safeEnd - minimum).coerceAtLeast(0L)
    }

    fun minEndMs(startMs: Long, sourceDurationMs: Long, minimumSpanMs: Long = MIN_CLIP_DURATION_MS): Long {
        val duration = sourceDurationMs.coerceAtLeast(0L)
        val minimum = minimumSpanMs.coerceAtLeast(1L).coerceAtMost(duration)
        val safeStart = startMs.coerceIn(0L, duration)
        return (safeStart + minimum).coerceAtMost(duration)
    }

    fun clampStart(
        requestedStartMs: Long,
        endMs: Long,
        sourceDurationMs: Long,
        minimumSpanMs: Long = MIN_CLIP_DURATION_MS
    ): Long {
        val duration = sourceDurationMs.coerceAtLeast(0L)
        return requestedStartMs.coerceIn(0L, maxStartMs(endMs, duration, minimumSpanMs))
    }

    fun clampEnd(
        requestedEndMs: Long,
        startMs: Long,
        sourceDurationMs: Long,
        minimumSpanMs: Long = MIN_CLIP_DURATION_MS
    ): Long {
        val duration = sourceDurationMs.coerceAtLeast(0L)
        return requestedEndMs.coerceIn(minEndMs(startMs, duration, minimumSpanMs), duration)
    }
}


/** Source in/out points for a music file; the selected source range may loop on the project timeline. */
internal data class MusicSourceRange(val startMs: Long, val endMs: Long) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)
}

/** Keeps a music source selection ordered and inside the original file. */
internal object MusicSourceRangeMath {
    fun normalize(range: MusicSourceRange, sourceDurationMs: Long): MusicSourceRange {
        val duration = sourceDurationMs.coerceAtLeast(0L)
        val start = range.startMs.coerceIn(0L, duration)
        val end = range.endMs.coerceIn(start, duration)
        return MusicSourceRange(start, end)
    }

    fun start(range: MusicSourceRange, requestedStartMs: Long, sourceDurationMs: Long): MusicSourceRange {
        val safe = normalize(range, sourceDurationMs)
        val minimum = minOf(100L, safe.durationMs)
        return safe.copy(startMs = requestedStartMs.coerceIn(0L, (safe.endMs - minimum).coerceAtLeast(0L)))
    }

    fun end(range: MusicSourceRange, requestedEndMs: Long, sourceDurationMs: Long): MusicSourceRange {
        val safe = normalize(range, sourceDurationMs)
        val minimum = minOf(100L, (sourceDurationMs - safe.startMs).coerceAtLeast(0L))
        return safe.copy(endMs = requestedEndMs.coerceIn((safe.startMs + minimum).coerceAtMost(sourceDurationMs), sourceDurationMs.coerceAtLeast(0L)))
    }

    /** Source position at a project-time offset, repeating only the selected in/out range. */
    fun sourcePosition(range: MusicSourceRange, projectOffsetMs: Long, sourceDurationMs: Long): Long {
        val safe = normalize(range, sourceDurationMs)
        if (safe.durationMs <= 0L) return safe.startMs
        return safe.startMs + (projectOffsetMs.coerceAtLeast(0L) % safe.durationMs)
    }
}

/** A sound item with independent source and project-timeline boundaries. */
internal data class TimelineAudioSegment(
    val timelineStartMs: Long,
    val sourceStartMs: Long,
    val durationMs: Long
)

/** Pure timing operations for moving and trimming an audio item without touching other tracks. */
internal object TimelineAudioMath {
    fun normalize(
        segment: TimelineAudioSegment,
        sourceDurationMs: Long,
        timelineDurationMs: Long
    ): TimelineAudioSegment {
        val sourceDuration = sourceDurationMs.coerceAtLeast(0L)
        val timelineDuration = timelineDurationMs.coerceAtLeast(0L)
        val sourceStart = segment.sourceStartMs.coerceIn(0L, sourceDuration)
        val timelineStart = segment.timelineStartMs.coerceIn(0L, timelineDuration)
        val maximumDuration = minOf(
            (sourceDuration - sourceStart).coerceAtLeast(0L),
            (timelineDuration - timelineStart).coerceAtLeast(0L)
        )
        return TimelineAudioSegment(
            timelineStartMs = timelineStart,
            sourceStartMs = sourceStart,
            durationMs = segment.durationMs.coerceIn(0L, maximumDuration)
        )
    }

    /** Move the complete item; its source trim and duration remain unchanged. */
    fun move(
        segment: TimelineAudioSegment,
        requestedTimelineStartMs: Long,
        sourceDurationMs: Long,
        timelineDurationMs: Long
    ): TimelineAudioSegment {
        val safe = normalize(segment, sourceDurationMs, timelineDurationMs)
        if (safe.durationMs <= 0L) return safe
        val latestStart = (timelineDurationMs - safe.durationMs).coerceAtLeast(0L)
        return safe.copy(timelineStartMs = requestedTimelineStartMs.coerceIn(0L, latestStart))
    }

    /**
     * Trim the left edge. A positive delta removes source time from the beginning
     * and moves the project start right; a negative delta restores the earlier source.
     */
    fun trimStart(
        segment: TimelineAudioSegment,
        deltaMs: Long,
        sourceDurationMs: Long,
        timelineDurationMs: Long,
        minimumDurationMs: Long = 100L
    ): TimelineAudioSegment {
        val safe = normalize(segment, sourceDurationMs, timelineDurationMs)
        if (safe.durationMs <= 0L) return safe
        val minimum = minimumDurationMs.coerceAtLeast(1L).coerceAtMost(safe.durationMs)
        val lowerDelta = -minOf(safe.timelineStartMs, safe.sourceStartMs)
        val upperDelta = (safe.durationMs - minimum).coerceAtLeast(0L)
        val delta = deltaMs.coerceIn(lowerDelta, upperDelta)
        return normalize(
            TimelineAudioSegment(
                timelineStartMs = safe.timelineStartMs + delta,
                sourceStartMs = safe.sourceStartMs + delta,
                durationMs = safe.durationMs - delta
            ),
            sourceDurationMs,
            timelineDurationMs
        )
    }

    /** Trim the right edge without exceeding either the source file or project end. */
    fun trimEnd(
        segment: TimelineAudioSegment,
        deltaMs: Long,
        sourceDurationMs: Long,
        timelineDurationMs: Long,
        minimumDurationMs: Long = 100L
    ): TimelineAudioSegment {
        val safe = normalize(segment, sourceDurationMs, timelineDurationMs)
        if (safe.durationMs <= 0L) return safe
        val maximumDuration = minOf(
            (sourceDurationMs - safe.sourceStartMs).coerceAtLeast(0L),
            (timelineDurationMs - safe.timelineStartMs).coerceAtLeast(0L)
        )
        val minimum = minimumDurationMs.coerceAtLeast(1L).coerceAtMost(maximumDuration)
        val requested = safe.durationMs + deltaMs
        return safe.copy(durationMs = requested.coerceIn(minimum, maximumDuration))
    }
}


/** A movable and trimmable item positioned on the project timeline. */
internal data class TimelineRange(
    val startMs: Long,
    val durationMs: Long
)

/** Independent project-time positioning for music and other non-source-trimmed timeline items. */
internal object TimelineRangeMath {
    const val MIN_DURATION_MS = 100L

    fun normalize(range: TimelineRange, timelineDurationMs: Long): TimelineRange {
        val timelineDuration = timelineDurationMs.coerceAtLeast(0L)
        val start = range.startMs.coerceIn(0L, timelineDuration)
        val maximumDuration = (timelineDuration - start).coerceAtLeast(0L)
        return TimelineRange(
            startMs = start,
            durationMs = range.durationMs.coerceIn(0L, maximumDuration)
        )
    }

    fun move(
        range: TimelineRange,
        requestedStartMs: Long,
        timelineDurationMs: Long
    ): TimelineRange {
        val safe = normalize(range, timelineDurationMs)
        val latestStart = (timelineDurationMs - safe.durationMs).coerceAtLeast(0L)
        return safe.copy(startMs = requestedStartMs.coerceIn(0L, latestStart))
    }

    fun trimStart(
        range: TimelineRange,
        deltaMs: Long,
        timelineDurationMs: Long,
        minimumDurationMs: Long = MIN_DURATION_MS
    ): TimelineRange {
        val safe = normalize(range, timelineDurationMs)
        if (safe.durationMs <= 0L) return safe
        val minimum = minimumDurationMs.coerceAtLeast(1L).coerceAtMost(safe.durationMs)
        val delta = deltaMs.coerceIn(-safe.startMs, (safe.durationMs - minimum).coerceAtLeast(0L))
        return TimelineRange(
            startMs = safe.startMs + delta,
            durationMs = safe.durationMs - delta
        )
    }

    fun trimEnd(
        range: TimelineRange,
        deltaMs: Long,
        timelineDurationMs: Long,
        minimumDurationMs: Long = MIN_DURATION_MS
    ): TimelineRange {
        val safe = normalize(range, timelineDurationMs)
        if (safe.durationMs <= 0L) return safe
        val maximumDuration = (timelineDurationMs - safe.startMs).coerceAtLeast(0L)
        val minimum = minimumDurationMs.coerceAtLeast(1L).coerceAtMost(maximumDuration)
        return safe.copy(
            durationMs = (safe.durationMs + deltaMs).coerceIn(minimum, maximumDuration)
        )
    }
}


/** A source-trimmed video clip placed at an independent project-timeline position. */
internal data class TimelineVideoSegment(
    val timelineStartMs: Long,
    val sourceStartMs: Long,
    val sourceEndMs: Long,
    val sourceDurationMs: Long,
    val speed: Float = 1f
) {
    val sourceSpanMs: Long get() = (sourceEndMs - sourceStartMs).coerceAtLeast(0L)
    val durationMs: Long get() = ClipSpeedMath.timelineDurationMs(sourceSpanMs, speed)
    val timelineEndMs: Long get() = timelineStartMs.coerceAtLeast(0L) + durationMs
}

/** A clip's position inside the one shared visual VIDEO strip. */
internal data class TimelineVideoPlacement(
    val clipIndex: Int,
    val timelineStartMs: Long,
    val timelineEndMs: Long,
    val layerOrder: Int
)

/** A non-mutating result for moving one clip inside a sequential video lane. */
internal data class TimelineVideoReorderPlan(
    val orderedClipIndices: List<Int>,
    val timelineStartByClipIndex: Map<Int, Long>
)

/** Independent video placement, overlap selection and edge-trim math. */
internal object TimelineVideoMath {
    fun normalize(segment: TimelineVideoSegment): TimelineVideoSegment {
        val sourceDuration = segment.sourceDurationMs.coerceAtLeast(0L)
        val sourceStart = segment.sourceStartMs.coerceIn(0L, sourceDuration)
        val sourceEnd = segment.sourceEndMs.coerceIn(sourceStart, sourceDuration)
        return segment.copy(
            timelineStartMs = segment.timelineStartMs.coerceAtLeast(0L),
            sourceStartMs = sourceStart,
            sourceEndMs = sourceEnd,
            sourceDurationMs = sourceDuration,
            speed = ClipSpeedMath.normalize(segment.speed)
        )
    }

    fun totalDuration(segments: List<TimelineVideoSegment>): Long =
        segments.maxOfOrNull { normalize(it).timelineEndMs } ?: 0L

    /** Last clip in layer order is topmost when time ranges overlap. */
    fun activeSegmentIndexAt(segments: List<TimelineVideoSegment>, projectTimeMs: Long): Int? {
        if (projectTimeMs < 0L) return null
        return segments.indices.reversed().firstOrNull { index ->
            val safe = normalize(segments[index])
            safe.durationMs > 0L &&
                projectTimeMs >= safe.timelineStartMs && projectTimeMs < safe.timelineEndMs
        }
    }

    /** Maps a visible project timestamp back to the exact source timestamp for one clip. */
    fun sourceTimeAtProjectTime(segment: TimelineVideoSegment, projectTimeMs: Long): Long? {
        val safe = normalize(segment)
        if (safe.durationMs <= 0L ||
            projectTimeMs < safe.timelineStartMs ||
            projectTimeMs >= safe.timelineEndMs
        ) return null
        return safe.sourceStartMs + ClipSpeedMath.sourceOffsetMs(
            projectTimeMs - safe.timelineStartMs, safe.speed
        )
    }

    /**
     * Returns every clip for the single visual VIDEO strip.
     *
     * The input order is layer order (later entries are above earlier ones when
     * project ranges overlap), matching activeSegmentIndexAt() and preview/export.
     * Time ranges are preserved exactly; overlap never creates another visual row.
     */
    fun singleTrackPlacements(segments: List<TimelineVideoSegment>): List<TimelineVideoPlacement> =
        segments.mapIndexed { index, segment ->
            val safe = normalize(segment)
            TimelineVideoPlacement(
                clipIndex = index,
                timelineStartMs = safe.timelineStartMs,
                timelineEndMs = safe.timelineEndMs,
                layerOrder = index
            )
        }

    /**
     * Builds a CapCut-style insertion/reorder plan for a non-overlapping video sequence.
     * Drop position is interpreted by the moved clip's center against the other clip centers.
     * When order changes, all clips are packed sequentially from the original first timestamp.
     * Overlapping ranges return null so a multi-layer composition is never silently flattened.
     */
    fun reorderForDrop(
        segments: List<TimelineVideoSegment>,
        movingIndex: Int,
        requestedStartMs: Long
    ): TimelineVideoReorderPlan? {
        if (movingIndex !in segments.indices || segments.size < 2) return null
        val safe = segments.map(::normalize)
        if (safe.any { it.durationMs <= 0L }) return null

        val timeOrder = segments.indices.sortedWith(
            compareBy<Int> { safe[it].timelineStartMs }.thenBy { it }
        )
        if (timeOrder.zipWithNext().any { (first, second) ->
                safe[first].timelineEndMs > safe[second].timelineStartMs
            }
        ) return null

        val others = timeOrder.filterNot { it == movingIndex }
        val moving = safe[movingIndex]
        val requestedCenterMs = requestedStartMs.coerceAtLeast(0L) + moving.durationMs / 2L
        val insertionIndex = others.indexOfFirst { otherIndex ->
            val other = safe[otherIndex]
            requestedCenterMs < other.timelineStartMs + other.durationMs / 2L
        }.let { if (it < 0) others.size else it }
        val reorderedIndices = others.toMutableList().apply {
            add(insertionIndex, movingIndex)
        }

        // A drag that remains inside the clip's existing slot is not a move. Keep
        // intentional gaps intact instead of compacting the timeline on a no-op.
        if (reorderedIndices == timeOrder) {
            return TimelineVideoReorderPlan(
                orderedClipIndices = reorderedIndices,
                timelineStartByClipIndex = timeOrder.associateWith { safe[it].timelineStartMs }
            )
        }

        var cursorMs = safe[timeOrder.first()].timelineStartMs
        val starts = linkedMapOf<Int, Long>()
        reorderedIndices.forEach { clipIndex ->
            starts[clipIndex] = cursorMs
            cursorMs += safe[clipIndex].durationMs
        }
        return TimelineVideoReorderPlan(reorderedIndices, starts)
    }

    fun move(segment: TimelineVideoSegment, requestedStartMs: Long): TimelineVideoSegment =
        normalize(segment).copy(timelineStartMs = requestedStartMs.coerceAtLeast(0L))

    /** Left trim advances source in-point and project start together. */
    fun trimStart(
        segment: TimelineVideoSegment,
        deltaMs: Long,
        minimumDurationMs: Long = 500L
    ): TimelineVideoSegment {
        val safe = normalize(segment)
        if (safe.durationMs <= 0L) return safe
        val minimum = minimumDurationMs.coerceAtLeast(1L).coerceAtMost(safe.durationMs)
        val lowerSourceDelta = -minOf(
            safe.sourceStartMs,
            ClipSpeedMath.sourceOffsetMs(safe.timelineStartMs, safe.speed)
        )
        val maximumSourceDelta = ClipSpeedMath.sourceOffsetMs(
            (safe.durationMs - ClipSpeedMath.timelineDurationMs(minimum, safe.speed)).coerceAtLeast(0L),
            safe.speed
        )
        val delta = deltaMs.coerceIn(lowerSourceDelta, maximumSourceDelta)
        return safe.copy(
            timelineStartMs = safe.timelineStartMs + ClipSpeedMath.timelineOffsetMs(delta, safe.speed),
            sourceStartMs = safe.sourceStartMs + delta
        )
    }

    /**
     * Trim source footage from the left while preserving its project placement.
     * For a first clip anchored at timeline zero, advancing the source in-point
     * shortens the clip without creating an empty leading timeline gap.
     */
    fun trimStartKeepingTimelinePosition(
        segment: TimelineVideoSegment,
        deltaMs: Long,
        minimumDurationMs: Long = 500L
    ): TimelineVideoSegment {
        val safe = normalize(segment)
        if (safe.durationMs <= 0L) return safe
        val minimum = minimumDurationMs.coerceAtLeast(1L).coerceAtMost(safe.durationMs)
        val delta = deltaMs.coerceIn(
            -safe.sourceStartMs,
            (safe.durationMs - minimum).coerceAtLeast(0L)
        )
        return safe.copy(sourceStartMs = safe.sourceStartMs + delta)
    }

    /** Right trim changes source out-point only. */
    fun trimEnd(
        segment: TimelineVideoSegment,
        deltaMs: Long,
        minimumDurationMs: Long = 500L
    ): TimelineVideoSegment {
        val safe = normalize(segment)
        if (safe.durationMs <= 0L) return safe
        val minimum = minimumDurationMs.coerceAtLeast(1L).coerceAtMost(safe.durationMs)
        val minimumEnd = (safe.sourceStartMs + minimum).coerceAtMost(safe.sourceDurationMs)
        return safe.copy(
            sourceEndMs = (safe.sourceEndMs + deltaMs).coerceIn(minimumEnd, safe.sourceDurationMs)
        )
    }
}

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
    val sourceDurationMs: Long
) {
    val durationMs: Long get() = (sourceEndMs - sourceStartMs).coerceAtLeast(0L)
    val timelineEndMs: Long get() = timelineStartMs.coerceAtLeast(0L) + durationMs
}

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
            sourceDurationMs = sourceDuration
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
        val delta = deltaMs.coerceIn(
            -minOf(safe.timelineStartMs, safe.sourceStartMs),
            (safe.durationMs - minimum).coerceAtLeast(0L)
        )
        return safe.copy(
            timelineStartMs = safe.timelineStartMs + delta,
            sourceStartMs = safe.sourceStartMs + delta
        )
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

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

package com.mathmotor.video

/**
 * Bounds a clip's source in/out points without allowing an empty range.
 * Very short source files are allowed to use their full available duration.
 */
internal object TimelineTrimMath {
    const val MIN_CLIP_DURATION_MS = 500L

    fun clampStart(requestedStartMs: Long, endMs: Long, sourceDurationMs: Long): Long {
        val duration = sourceDurationMs.coerceAtLeast(0L)
        val minimum = minOf(MIN_CLIP_DURATION_MS, duration)
        val safeEnd = endMs.coerceIn(0L, duration)
        val latestStart = (safeEnd - minimum).coerceAtLeast(0L)
        return requestedStartMs.coerceIn(0L, latestStart)
    }

    fun clampEnd(requestedEndMs: Long, startMs: Long, sourceDurationMs: Long): Long {
        val duration = sourceDurationMs.coerceAtLeast(0L)
        val minimum = minOf(MIN_CLIP_DURATION_MS, duration)
        val safeStart = startMs.coerceIn(0L, duration)
        val earliestEnd = (safeStart + minimum).coerceAtMost(duration)
        return requestedEndMs.coerceIn(earliestEnd, duration)
    }
}

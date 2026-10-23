package com.mathmotor.video

internal data class TimelineClipPosition(
    val clipIndex: Int,
    val clipStartMs: Long,
    val localPositionMs: Long
)

/**
 * Maps an output-timeline time to the source clip and its position within that clip.
 * Clip boundaries belong to the following clip (except at the end of the timeline).
 */
internal object TimelinePositionMath {
    fun locate(durationsMs: List<Long>, requestedTimeMs: Long): TimelineClipPosition? {
        if (durationsMs.isEmpty()) return null
        val safeDurations = durationsMs.map { it.coerceAtLeast(0L) }
        val totalMs = safeDurations.sum()
        if (totalMs <= 0L) return null
        val targetMs = requestedTimeMs.coerceIn(0L, (totalMs - 1L).coerceAtLeast(0L))
        var clipStartMs = 0L
        for (index in safeDurations.indices) {
            val durationMs = safeDurations[index]
            if (targetMs < clipStartMs + durationMs || index == safeDurations.lastIndex) {
                val localPositionMs = (targetMs - clipStartMs)
                    .coerceIn(0L, (durationMs - 1L).coerceAtLeast(0L))
                return TimelineClipPosition(index, clipStartMs, localPositionMs)
            }
            clipStartMs += durationMs
        }
        return null
    }
}

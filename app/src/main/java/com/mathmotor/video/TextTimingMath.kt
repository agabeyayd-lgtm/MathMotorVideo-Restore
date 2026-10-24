package com.mathmotor.video

/**
 * Keeps a text overlay's visibility interval inside the exported project duration.
 * Intervals are half-open: the text is visible at startMs and hidden at endMs.
 */
internal data class TextTimingInterval(val startMs: Long, val endMs: Long)

internal object TextTimingMath {
    const val SEEK_STEP_MS = 100L

    fun normalize(
        startMs: Long,
        endMs: Long,
        totalMs: Long,
        minimumSpanMs: Long = SEEK_STEP_MS
    ): TextTimingInterval {
        val total = totalMs.coerceAtLeast(0L)
        if (total == 0L) return TextTimingInterval(0L, 0L)

        val minimumSpan = minimumSpanMs.coerceAtLeast(1L).coerceAtMost(total)
        val start = startMs.coerceIn(0L, (total - minimumSpan).coerceAtLeast(0L))
        val end = endMs.coerceIn(start + minimumSpan, total)
        return TextTimingInterval(start, end)
    }

    /** Move the start without allowing it to overtake the current end. */
    fun moveStart(
        requestedStartMs: Long,
        currentEndMs: Long,
        totalMs: Long,
        minimumSpanMs: Long = SEEK_STEP_MS
    ): TextTimingInterval {
        val total = totalMs.coerceAtLeast(0L)
        if (total == 0L) return TextTimingInterval(0L, 0L)
        val minimumSpan = minimumSpanMs.coerceAtLeast(1L).coerceAtMost(total)
        val end = currentEndMs.coerceIn(minimumSpan, total)
        val start = requestedStartMs.coerceIn(0L, (end - minimumSpan).coerceAtLeast(0L))
        return TextTimingInterval(start, end)
    }

    /** Move the end while keeping at least one valid seek step after the start. */
    fun moveEnd(
        requestedEndMs: Long,
        currentStartMs: Long,
        totalMs: Long,
        minimumSpanMs: Long = SEEK_STEP_MS
    ): TextTimingInterval {
        val total = totalMs.coerceAtLeast(0L)
        if (total == 0L) return TextTimingInterval(0L, 0L)
        val minimumSpan = minimumSpanMs.coerceAtLeast(1L).coerceAtMost(total)
        val start = currentStartMs.coerceIn(0L, (total - minimumSpan).coerceAtLeast(0L))
        val end = requestedEndMs.coerceIn(start + minimumSpan, total)
        return TextTimingInterval(start, end)
    }

    fun isVisibleAt(playheadMs: Long, startMs: Long, endMs: Long, totalMs: Long): Boolean {
        if (totalMs <= 0L) return false
        val interval = normalize(startMs, endMs, totalMs)
        return playheadMs >= interval.startMs && playheadMs < interval.endMs
    }
}

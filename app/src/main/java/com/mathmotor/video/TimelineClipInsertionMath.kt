package com.mathmotor.video

/**
 * Chooses the insertion point for newly imported main-video clips.
 *
 * Only the existing main-video lane defines where another main-video clip
 * belongs. Independent overlay, text and audio lanes may extend beyond it,
 * but must never create a blank gap before a newly appended video.
 */
internal object TimelineClipInsertionMath {
    fun startTimeMs(existingVideoDurationMs: Long): Long =
        existingVideoDurationMs.coerceAtLeast(0L)
}

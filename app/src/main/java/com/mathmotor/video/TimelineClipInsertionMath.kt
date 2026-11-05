package com.mathmotor.video

/**
 * Chooses the insertion point for newly imported main-video clips.
 *
 * Independent overlay, text and audio lanes may outlive the main-video lane.
 * When no main-video clip exists, a new main video starts at project time zero
 * and overlaps those independent lanes instead of inheriting their duration.
 */
internal object TimelineClipInsertionMath {
    fun startTimeMs(existingVideoClipCount: Int, projectDurationMs: Long): Long =
        if (existingVideoClipCount <= 0) 0L else projectDurationMs.coerceAtLeast(0L)
}

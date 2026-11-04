package com.mathmotor.video

import kotlin.math.roundToLong

/** Maps between source media time and project-timeline time for a constant clip speed. */
internal object ClipSpeedMath {
    const val MIN_SPEED = 0.1f
    const val MAX_SPEED = 10f

    fun normalize(speed: Float): Float =
        speed.takeIf { it.isFinite() }?.coerceIn(MIN_SPEED, MAX_SPEED) ?: 1f

    fun timelineDurationMs(sourceDurationMs: Long, speed: Float): Long =
        (sourceDurationMs.coerceAtLeast(0L).toDouble() / normalize(speed).toDouble())
            .roundToLong().coerceAtLeast(0L)

    fun sourceOffsetMs(timelineOffsetMs: Long, speed: Float): Long =
        (timelineOffsetMs.coerceAtLeast(0L).toDouble() * normalize(speed).toDouble())
            .roundToLong().coerceAtLeast(0L)

    fun timelineOffsetMs(sourceOffsetMs: Long, speed: Float): Long =
        (sourceOffsetMs.coerceAtLeast(0L).toDouble() / normalize(speed).toDouble())
            .roundToLong().coerceAtLeast(0L)
}

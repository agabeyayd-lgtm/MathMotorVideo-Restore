package com.mathmotor.video

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * Frame/time conversion for the editor's default 30 fps timeline grid.
 * Frame indices are authoritative; microseconds preserve sub-millisecond precision.
 */
internal object TimelineFrameMath {
    const val DEFAULT_FRAME_RATE = 30

    private val NICE_FRAME_STEPS = longArrayOf(
        1L, 2L, 3L, 5L, 10L, 15L, 20L, 30L, 60L, 90L, 150L, 300L, 600L
    )

    fun frameIndexAtTimeMs(timeMs: Long, frameRate: Int = DEFAULT_FRAME_RATE): Long {
        if (timeMs <= 0L || frameRate <= 0) return 0L
        return floor(timeMs.toDouble() * frameRate.toDouble() / 1000.0)
            .toLong().coerceAtLeast(0L)
    }

    fun nearestFrameIndexAtTimeMs(timeMs: Long, frameRate: Int = DEFAULT_FRAME_RATE): Long {
        if (timeMs <= 0L || frameRate <= 0) return 0L
        return (timeMs.toDouble() * frameRate.toDouble() / 1000.0)
            .roundToLong().coerceAtLeast(0L)
    }

    fun timeUsForFrame(frameIndex: Long, frameRate: Int = DEFAULT_FRAME_RATE): Long {
        if (frameIndex <= 0L || frameRate <= 0) return 0L
        return (frameIndex.toDouble() * 1_000_000.0 / frameRate.toDouble())
            .roundToLong().coerceAtLeast(0L)
    }

    fun timeMsForFrame(frameIndex: Long, frameRate: Int = DEFAULT_FRAME_RATE): Long =
        ((timeUsForFrame(frameIndex, frameRate) + 500L) / 1000L).coerceAtLeast(0L)

    fun snapTimeMs(timeMs: Long, frameRate: Int = DEFAULT_FRAME_RATE): Long =
        timeMsForFrame(nearestFrameIndexAtTimeMs(timeMs, frameRate), frameRate)

    /** Chooses a readable frame interval that keeps ruler labels separated at this zoom. */
    fun frameStepForPixelSpacing(
        pixelsPerSecond: Float,
        minimumSpacingPx: Float,
        frameRate: Int = DEFAULT_FRAME_RATE
    ): Long {
        if (!pixelsPerSecond.isFinite() || pixelsPerSecond <= 0f ||
            !minimumSpacingPx.isFinite() || minimumSpacingPx <= 0f || frameRate <= 0
        ) return 1L
        val requiredFrames = ceil(
            minimumSpacingPx.toDouble() * frameRate.toDouble() / pixelsPerSecond.toDouble()
        ).toLong().coerceAtLeast(1L)
        return NICE_FRAME_STEPS.firstOrNull { it >= requiredFrames } ?: requiredFrames
    }
}

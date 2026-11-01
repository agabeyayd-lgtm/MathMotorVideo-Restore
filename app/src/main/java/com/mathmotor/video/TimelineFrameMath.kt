package com.mathmotor.video

/**
 * Frame/time conversion for the editor's 30 FPS project timeline.
 *
 * Time remains stored in milliseconds for Media3 compatibility, but every
 * user seek is quantized to the nearest project-frame boundary. Integer
 * arithmetic avoids cumulative floating-point drift (30 FPS frame 1 is
 * 33.333... ms, frame 2 is 66.666... ms, etc.).
 */
internal object TimelineFrameMath {
    const val DEFAULT_PROJECT_FPS = 30

    fun timeMsForFrame(frameIndex: Long, fps: Int = DEFAULT_PROJECT_FPS): Long {
        require(fps > 0) { "FPS must be positive." }
        val safeFrame = frameIndex.coerceAtLeast(0L)
        return (safeFrame * 1000L + fps / 2L) / fps.toLong()
    }

    fun frameIndexForTimeMs(timeMs: Long, fps: Int = DEFAULT_PROJECT_FPS): Long {
        require(fps > 0) { "FPS must be positive." }
        val safeTimeMs = timeMs.coerceAtLeast(0L)
        return (safeTimeMs * fps.toLong() + 500L) / 1000L
    }

    fun snapToFrameMs(timeMs: Long, fps: Int = DEFAULT_PROJECT_FPS): Long =
        timeMsForFrame(frameIndexForTimeMs(timeMs, fps), fps)
}

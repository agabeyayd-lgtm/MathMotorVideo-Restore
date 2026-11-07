package com.mathmotor.video

/**
 * Timing and transform defaults for a still-image layer. Logo and full-frame
 * photo entry points intentionally use different placements.
 */
internal data class ImageOverlayDefaults(
    val sourceDurationMs: Long,
    val durationMs: Long,
    val scale: Float,
    val xFraction: Float,
    val yFraction: Float,
    val opacity: Float
)

internal object OverlayAssetDefaultsMath {
    fun imageLogo(
        projectDurationMs: Long,
        minimumSourceDurationMs: Long = 60_000L
    ): ImageOverlayDefaults {
        val duration = projectDurationMs.coerceAtLeast(1L)
        return ImageOverlayDefaults(
            sourceDurationMs = maxOf(minimumSourceDurationMs.coerceAtLeast(1L), duration),
            durationMs = duration,
            scale = 0.18f,
            xFraction = 0.88f,
            yFraction = 0.12f,
            opacity = 0.88f
        )
    }

    /** A photo opened from the home-screen editor action starts fitted to the project frame. */
    fun fullFramePhoto(
        projectDurationMs: Long,
        minimumSourceDurationMs: Long = 60_000L
    ): ImageOverlayDefaults {
        val duration = projectDurationMs.coerceAtLeast(1L)
        return ImageOverlayDefaults(
            sourceDurationMs = maxOf(minimumSourceDurationMs.coerceAtLeast(1L), duration),
            durationMs = duration,
            scale = 1f,
            xFraction = 0.5f,
            yFraction = 0.5f,
            opacity = 1f
        )
    }
}

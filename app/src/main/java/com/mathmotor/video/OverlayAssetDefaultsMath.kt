package com.mathmotor.video

/**
 * Presentation defaults for a still image added as a persistent project logo.
 * The image sequence is allowed to last as long as the edited video timeline.
 */
internal data class ImageLogoOverlayDefaults(
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
    ): ImageLogoOverlayDefaults {
        val duration = projectDurationMs.coerceAtLeast(1L)
        return ImageLogoOverlayDefaults(
            sourceDurationMs = maxOf(minimumSourceDurationMs.coerceAtLeast(1L), duration),
            durationMs = duration,
            scale = 0.18f,
            xFraction = 0.88f,
            yFraction = 0.12f,
            opacity = 0.88f
        )
    }
}

package com.mathmotor.video

/** Integer bounds for the project canvas fitted and centered inside the preview work stage. */
internal data class PreviewCanvasBounds(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int
)

/** Pure geometry shared by the editor preview and JVM unit tests. */
internal object PreviewCanvasMath {
    private const val DEFAULT_ASPECT_RATIO = 9f / 16f

    /**
     * Fits the requested project aspect into a stage without distortion.
     * Returns null until the stage has measurable positive dimensions.
     */
    fun fit(
        stageWidth: Int,
        stageHeight: Int,
        requestedAspectRatio: Float
    ): PreviewCanvasBounds? {
        if (stageWidth <= 0 || stageHeight <= 0) return null
        val aspectRatio = requestedAspectRatio
            .takeIf { it.isFinite() && it > 0f }
            ?: DEFAULT_ASPECT_RATIO

        val width = minOf(stageWidth.toFloat(), stageHeight * aspectRatio)
            .toInt().coerceIn(1, stageWidth)
        val height = minOf(stageHeight.toFloat(), stageWidth / aspectRatio)
            .toInt().coerceIn(1, stageHeight)

        return PreviewCanvasBounds(
            left = (stageWidth - width) / 2,
            top = (stageHeight - height) / 2,
            width = width,
            height = height
        )
    }

    /** Converts a normalized center position (0..1) into Android screen pixels. */
    fun screenTranslationForFraction(fraction: Float, extent: Float): Float {
        val normalized = fraction.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0.5f
        val safeExtent = extent.takeIf { it.isFinite() && it >= 0f } ?: 0f
        return (normalized - 0.5f) * safeExtent
    }

    /**
     * Maximum center translation that keeps a rotated layer inside the canvas,
     * leaving an inset for selection strokes and anti-aliased canvas edges.
     */
    fun maxCenterTranslation(
        canvasExtent: Float,
        rotatedContentExtent: Float,
        edgeInset: Float
    ): Float {
        val safeCanvas = canvasExtent.takeIf { it.isFinite() && it > 0f } ?: 0f
        val safeContent = rotatedContentExtent.takeIf { it.isFinite() && it >= 0f } ?: 0f
        val safeInset = edgeInset.takeIf { it.isFinite() && it >= 0f } ?: 0f
        return ((safeCanvas - safeContent) / 2f - safeInset).coerceAtLeast(0f)
    }

    /**
     * Converts the same center position into Media3's normalized frame anchor.
     * Media3's Y axis points up, opposite to Android screen coordinates.
     */
    fun media3AnchorForFraction(fraction: Float, invertY: Boolean = false): Float {
        val normalized = fraction.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0.5f
        return if (invertY) (0.5f - normalized) * 2f else (normalized - 0.5f) * 2f
    }

    /** Smallest signed rotation taking startDegrees to endDegrees, in [-180, 180]. */
    fun rotationDeltaDegrees(startDegrees: Float, endDegrees: Float): Float {
        if (!startDegrees.isFinite() || !endDegrees.isFinite()) return 0f
        return ((endDegrees - startDegrees + 540f) % 360f) - 180f
    }

    /** Keeps a stored layer rotation finite and inside the canonical angular range. */
    fun normalizeRotationDegrees(degrees: Float): Float {
        if (!degrees.isFinite()) return 0f
        return ((degrees + 180f) % 360f + 360f) % 360f - 180f
    }
}

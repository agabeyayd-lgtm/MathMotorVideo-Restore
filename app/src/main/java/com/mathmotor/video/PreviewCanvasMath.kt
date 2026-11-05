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
}

package com.mathmotor.video

import kotlin.math.cos
import kotlin.math.sin

/** Immutable normalized transform values for one video clip. */
internal data class ClipTransformValues(
    val scale: Float = 1f,
    val rotationDegrees: Float = 0f,
    val translateX: Float = 0f,
    val translateY: Float = 0f
)

/** Pure math shared by clip editing, Media3 effects and unit tests. */
internal object ClipTransformMath {
    fun normalize(
        scale: Float,
        rotationDegrees: Float,
        translateX: Float,
        translateY: Float
    ): ClipTransformValues {
        val safeScale = scale.takeIf { it.isFinite() }?.coerceIn(0.25f, 4f) ?: 1f
        val safeRotation = rotationDegrees.takeIf { it.isFinite() } ?: 0f
        val normalizedRotation = ((safeRotation + 180f) % 360f + 360f) % 360f - 180f
        val safeX = translateX.takeIf { it.isFinite() }?.coerceIn(-2f, 2f) ?: 0f
        val safeY = translateY.takeIf { it.isFinite() }?.coerceIn(-2f, 2f) ?: 0f
        return ClipTransformValues(safeScale, normalizedRotation, safeX, safeY)
    }

    /**
     * Returns an Android Matrix-compatible 3x3 value array for normalized
     * device coordinates. Positive rotation follows the preview's clockwise
     * screen-space gesture direction; Media3's normalized Y axis points up.
     */
    fun matrixValues(transform: ClipTransformValues): FloatArray {
        val value = normalize(
            transform.scale,
            transform.rotationDegrees,
            transform.translateX,
            transform.translateY
        )
        val radians = Math.toRadians(-value.rotationDegrees.toDouble())
        val cosine = cos(radians).toFloat() * value.scale
        val sine = sin(radians).toFloat() * value.scale
        return floatArrayOf(
            cosine, -sine, value.translateX,
            sine, cosine, -value.translateY,
            0f, 0f, 1f
        )
    }
}

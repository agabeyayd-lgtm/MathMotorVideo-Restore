package com.mathmotor.video

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.abs

class ClipTransformMathTest {
    @Test
    fun identityTransformProducesIdentityMatrix() {
        val values = ClipTransformMath.matrixValues(ClipTransformValues())
        val expected = floatArrayOf(
            1f, 0f, 0f,
            0f, 1f, 0f,
            0f, 0f, 1f
        )
        expected.indices.forEach { assertEquals(expected[it], values[it], 0.0001f) }
    }

    @Test
    fun scaleRotationAndTranslationAreCombinedInOneMatrix() {
        val values = ClipTransformMath.matrixValues(
            ClipTransformValues(
                scale = 2f,
                rotationDegrees = 90f,
                translateX = 0.25f,
                translateY = -0.5f
            )
        )
        val expected = floatArrayOf(
            0f, 2f, 0.25f,
            -2f, 0f, 0.5f,
            0f, 0f, 1f
        )
        expected.indices.forEach { assertEquals(expected[it], values[it], 0.0002f) }
    }

    @Test
    fun transformValuesAreClampedAndRotationIsNormalized() {
        val normalized = ClipTransformMath.normalize(
            scale = 99f,
            rotationDegrees = 540f,
            translateX = -9f,
            translateY = 9f
        )
        assertEquals(4f, normalized.scale, 0f)
        assertEquals(-180f, normalized.rotationDegrees, 0f)
        assertEquals(-2f, normalized.translateX, 0f)
        assertEquals(2f, normalized.translateY, 0f)
    }

    @Test
    fun nonFiniteTransformValuesFallBackToIdentityComponents() {
        val normalized = ClipTransformMath.normalize(
            scale = Float.NaN,
            rotationDegrees = Float.POSITIVE_INFINITY,
            translateX = Float.NaN,
            translateY = Float.NEGATIVE_INFINITY
        )
        assertEquals(1f, normalized.scale, 0f)
        assertEquals(0f, normalized.rotationDegrees, 0f)
        assertEquals(0f, normalized.translateX, 0f)
        assertEquals(0f, normalized.translateY, 0f)
    }

    @Test
    fun matrixRotationRetainsUnitScaleWithinFloatingPointTolerance() {
        val matrix = ClipTransformMath.matrixValues(ClipTransformValues(rotationDegrees = 37f))
        val firstColumnLength = kotlin.math.sqrt(matrix[0] * matrix[0] + matrix[3] * matrix[3])
        val secondColumnLength = kotlin.math.sqrt(matrix[1] * matrix[1] + matrix[4] * matrix[4])
        assertEquals(1f, firstColumnLength, 0.0001f)
        assertEquals(1f, secondColumnLength, 0.0001f)
        assertEquals(true, abs(matrix[0] * matrix[1] + matrix[3] * matrix[4]) < 0.0001f)
    }
}

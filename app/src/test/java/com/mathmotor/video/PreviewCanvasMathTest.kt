package com.mathmotor.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PreviewCanvasMathTest {
    @Test
    fun portraitCanvasIsCenteredInsideWideStage() {
        val bounds = PreviewCanvasMath.fit(1080, 1800, 9f / 16f)
        assertNotNull(bounds)
        bounds!!
        assertEquals(1012, bounds.width)
        assertEquals(1800, bounds.height)
        assertEquals(34, bounds.left)
        assertEquals(0, bounds.top)
    }

    @Test
    fun landscapeCanvasIsCenteredVerticallyInsidePortraitStage() {
        val bounds = PreviewCanvasMath.fit(1080, 1800, 16f / 9f)
        assertNotNull(bounds)
        bounds!!
        assertEquals(1080, bounds.width)
        assertEquals(607, bounds.height)
        assertEquals(0, bounds.left)
        assertEquals(596, bounds.top)
    }

    @Test
    fun squareCanvasIsCenteredWithoutDistortion() {
        val bounds = PreviewCanvasMath.fit(1080, 1800, 1f)
        assertNotNull(bounds)
        bounds!!
        assertEquals(1080, bounds.width)
        assertEquals(1080, bounds.height)
        assertEquals(0, bounds.left)
        assertEquals(360, bounds.top)
    }

    @Test
    fun invalidAspectUsesPortraitProjectDefault() {
        val nanBounds = PreviewCanvasMath.fit(1080, 1800, Float.NaN)
        val zeroBounds = PreviewCanvasMath.fit(1080, 1800, 0f)
        assertEquals(nanBounds, zeroBounds)
        assertEquals(1012, nanBounds!!.width)
        assertEquals(1800, nanBounds.height)
    }

    @Test
    fun emptyStageHasNoCanvasBounds() {
        assertNull(PreviewCanvasMath.fit(0, 1800, 9f / 16f))
        assertNull(PreviewCanvasMath.fit(1080, 0, 9f / 16f))
    }

    @Test
    fun normalizedCenterUsesSameScreenTranslationAndMedia3Anchor() {
        val extent = 400f
        assertEquals(-200f, PreviewCanvasMath.screenTranslationForFraction(0f, extent), 0f)
        assertEquals(0f, PreviewCanvasMath.screenTranslationForFraction(0.5f, extent), 0f)
        assertEquals(200f, PreviewCanvasMath.screenTranslationForFraction(1f, extent), 0f)

        assertEquals(-1f, PreviewCanvasMath.media3AnchorForFraction(0f), 0f)
        assertEquals(0f, PreviewCanvasMath.media3AnchorForFraction(0.5f), 0f)
        assertEquals(1f, PreviewCanvasMath.media3AnchorForFraction(1f), 0f)
        assertEquals(1f, PreviewCanvasMath.media3AnchorForFraction(0f, invertY = true), 0f)
        assertEquals(0f, PreviewCanvasMath.media3AnchorForFraction(0.5f, invertY = true), 0f)
        assertEquals(-1f, PreviewCanvasMath.media3AnchorForFraction(1f, invertY = true), 0f)
    }

    @Test
    fun selectionChromeKeepsAnInsetFromEveryCanvasEdge() {
        assertEquals(46f, PreviewCanvasMath.maxCenterTranslation(200f, 100f, 4f), 0f)
        assertEquals(0f, PreviewCanvasMath.maxCenterTranslation(200f, 200f, 2f), 0f)
        assertEquals(0f, PreviewCanvasMath.maxCenterTranslation(200f, 220f, 2f), 0f)
    }

    @Test
    fun invalidSelectionGeometryCannotProduceAnUnsafeTranslation() {
        assertEquals(0f, PreviewCanvasMath.maxCenterTranslation(Float.NaN, 100f, 2f), 0f)
        assertEquals(0f, PreviewCanvasMath.maxCenterTranslation(200f, Float.NaN, 2f), 0f)
        assertEquals(0f, PreviewCanvasMath.maxCenterTranslation(200f, 100f, Float.POSITIVE_INFINITY), 0f)
        assertEquals(0f, PreviewCanvasMath.maxCenterTranslation(200f, 100f, -1f), 0f)
    }

    @Test
    fun rotationDeltaUsesShortestDirectionAcrossZeroDegrees() {
        assertEquals(20f, PreviewCanvasMath.rotationDeltaDegrees(170f, -170f), 0f)
        assertEquals(-20f, PreviewCanvasMath.rotationDeltaDegrees(-170f, 170f), 0f)
        assertEquals(90f, PreviewCanvasMath.rotationDeltaDegrees(10f, 100f), 0f)
        assertEquals(-90f, PreviewCanvasMath.rotationDeltaDegrees(100f, 10f), 0f)
    }

    @Test
    fun rotationNormalizationKeepsFiniteCanonicalAngle() {
        assertEquals(0f, PreviewCanvasMath.normalizeRotationDegrees(360f), 0f)
        assertEquals(-90f, PreviewCanvasMath.normalizeRotationDegrees(270f), 0f)
        assertEquals(90f, PreviewCanvasMath.normalizeRotationDegrees(-270f), 0f)
        assertEquals(0f, PreviewCanvasMath.normalizeRotationDegrees(Float.NaN), 0f)
        assertEquals(0f, PreviewCanvasMath.normalizeRotationDegrees(Float.POSITIVE_INFINITY), 0f)
    }

    @Test
    fun screenPreviewAndMedia3ExportRepresentTheSameNormalizedPoint() {
        val fractions = listOf(0f, 0.03f, 0.25f, 0.5f, 0.75f, 0.97f, 1f)
        val width = 1012f
        val height = 1800f

        fractions.forEach { fraction ->
            val screenX = PreviewCanvasMath.screenTranslationForFraction(fraction, width)
            val screenY = PreviewCanvasMath.screenTranslationForFraction(fraction, height)
            val exportX = PreviewCanvasMath.media3AnchorForFraction(fraction)
            val exportY = PreviewCanvasMath.media3AnchorForFraction(fraction, invertY = true)

            assertEquals(exportX, screenX * 2f / width, 0.000001f)
            assertEquals(exportY, -screenY * 2f / height, 0.000001f)
        }
    }

    @Test
    fun nonFiniteCoordinateFractionsUseCanvasCenter() {
        assertEquals(0f, PreviewCanvasMath.screenTranslationForFraction(Float.NaN, 500f), 0f)
        assertEquals(0f, PreviewCanvasMath.screenTranslationForFraction(0.5f, Float.NaN), 0f)
        assertEquals(0f, PreviewCanvasMath.media3AnchorForFraction(Float.POSITIVE_INFINITY), 0f)
    }

    @Test
    fun boundsNeverExceedStageOrLeaveNegativeOffsets() {
        val ratios = listOf(9f / 16f, 4f / 5f, 1f, 16f / 9f, 4f)
        ratios.forEach { ratio ->
            val bounds = PreviewCanvasMath.fit(393, 611, ratio)
            assertNotNull(bounds)
            bounds!!
            assertEquals(true, bounds.width in 1..393)
            assertEquals(true, bounds.height in 1..611)
            assertEquals(true, bounds.left >= 0)
            assertEquals(true, bounds.top >= 0)
            assertEquals(true, bounds.left + bounds.width <= 393)
            assertEquals(true, bounds.top + bounds.height <= 611)
        }
    }
}

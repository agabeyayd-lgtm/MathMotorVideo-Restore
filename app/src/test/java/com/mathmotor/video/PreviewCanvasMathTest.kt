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

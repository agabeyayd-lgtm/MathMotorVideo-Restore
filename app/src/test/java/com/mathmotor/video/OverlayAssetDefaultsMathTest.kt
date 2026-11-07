package com.mathmotor.video

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayAssetDefaultsMathTest {
    @Test
    fun imageLogoCoversTheEntireProjectEvenWhenLongerThanOneMinute() {
        val defaults = OverlayAssetDefaultsMath.imageLogo(projectDurationMs = 185_000L)

        assertEquals(185_000L, defaults.sourceDurationMs)
        assertEquals(185_000L, defaults.durationMs)
    }

    @Test
    fun imageLogoUsesSmallUpperRightPlacementAndSlightTransparency() {
        val defaults = OverlayAssetDefaultsMath.imageLogo(projectDurationMs = 20_000L)

        assertEquals(0.18f, defaults.scale, 0f)
        assertEquals(0.88f, defaults.xFraction, 0f)
        assertEquals(0.12f, defaults.yFraction, 0f)
        assertEquals(0.88f, defaults.opacity, 0f)
    }

    @Test
    fun emptyProjectStillGetsAValidOneMillisecondImageSequence() {
        val defaults = OverlayAssetDefaultsMath.imageLogo(projectDurationMs = 0L)

        assertEquals(60_000L, defaults.sourceDurationMs)
        assertEquals(1L, defaults.durationMs)
    }

    @Test
    fun photoOpenedFromHomeStartsFullFrameCenteredAndOpaque() {
        val defaults = OverlayAssetDefaultsMath.fullFramePhoto(projectDurationMs = 20_000L)

        assertEquals(60_000L, defaults.sourceDurationMs)
        assertEquals(20_000L, defaults.durationMs)
        assertEquals(1f, defaults.scale, 0f)
        assertEquals(0.5f, defaults.xFraction, 0f)
        assertEquals(0.5f, defaults.yFraction, 0f)
        assertEquals(1f, defaults.opacity, 0f)
    }

    @Test
    fun fullFramePhotoKeepsAValidDurationForAnEmptyProject() {
        val defaults = OverlayAssetDefaultsMath.fullFramePhoto(projectDurationMs = 0L)

        assertEquals(60_000L, defaults.sourceDurationMs)
        assertEquals(1L, defaults.durationMs)
        assertEquals(1f, defaults.scale, 0f)
        assertEquals(0.5f, defaults.xFraction, 0f)
        assertEquals(0.5f, defaults.yFraction, 0f)
        assertEquals(1f, defaults.opacity, 0f)
    }
}

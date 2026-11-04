package com.mathmotor.video

import org.junit.Assert.assertEquals
import org.junit.Test

class TextAnimationMathTest {
    @Test
    fun noAnimationKeepsTextFullyVisible() {
        assertEquals(TextAnimationState(), TextAnimationMath.stateAt(
            TextAnimationPreset.NONE, 0L, 0L, 350L
        ))
    }

    @Test
    fun fadeAnimatesBothEntranceAndExit() {
        val entrance = TextAnimationMath.stateAt(TextAnimationPreset.FADE, 175L, 2_000L, 350L)
        assertEquals(0.5f, entrance.alpha, 0.0001f)
        val exit = TextAnimationMath.stateAt(TextAnimationPreset.FADE, 2_000L, 175L, 350L)
        assertEquals(0.5f, exit.alpha, 0.0001f)
        assertEquals(1f, TextAnimationMath.stateAt(
            TextAnimationPreset.FADE, 1_000L, 1_000L, 350L
        ).alpha, 0.0001f)
    }

    @Test
    fun slideAndPopHaveTheirOwnTransformCurves() {
        val slide = TextAnimationMath.stateAt(TextAnimationPreset.SLIDE_UP, 0L, 2_000L, 500L)
        assertEquals(0.14f, slide.offsetYFraction, 0.0001f)
        val pop = TextAnimationMath.stateAt(TextAnimationPreset.POP, 0L, 2_000L, 500L)
        assertEquals(0.65f, pop.scale, 0.0001f)
        assertEquals(0f, pop.alpha, 0.0001f)
    }

    @Test
    fun unknownAnimationRestoresToNone() {
        assertEquals(TextAnimationPreset.NONE, TextAnimationPreset.fromKey("not-real"))
    }
}

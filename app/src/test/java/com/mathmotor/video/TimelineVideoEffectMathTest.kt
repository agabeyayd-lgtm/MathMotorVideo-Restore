package com.mathmotor.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimelineVideoEffectMathTest {
    private fun effect(
        id: Long,
        start: Long,
        duration: Long,
        preset: TimelineVideoEffectPreset = TimelineVideoEffectPreset.BLUR
    ) = TimelineVideoEffectClip(id, preset, start, duration)

    @Test
    fun normalizationClampsEffectToProjectDurationAndIntensity() {
        assertEquals(
            TimelineVideoEffectClip(1L, TimelineVideoEffectPreset.BLUR, 7_000L, 3_000L, 100),
            TimelineVideoEffectMath.normalize(
                effect(1L, 7_000L, 8_000L).copy(intensityPercent = 140),
                10_000L
            )
        )
    }

    @Test
    fun tooShortProjectCannotContainAnEffectClip() {
        assertNull(TimelineVideoEffectMath.normalize(effect(1L, 0L, 1_000L), 249L))
    }

    @Test
    fun boundariesIncludeEffectStartsAndEndsWithoutDuplicates() {
        val boundaries = TimelineVideoEffectMath.boundaries(
            10_000L,
            listOf(
                effect(1L, 1_000L, 3_000L),
                effect(2L, 2_000L, 2_000L),
                effect(3L, 4_000L, 1_000L)
            )
        )

        assertEquals(listOf(0L, 1_000L, 2_000L, 4_000L, 5_000L, 10_000L), boundaries)
    }

    @Test
    fun activeEffectsUseHalfOpenRangesAndStableStackOrder() {
        val clips = listOf(
            effect(9L, 1_000L, 3_000L, TimelineVideoEffectPreset.NEGATIVE),
            effect(3L, 1_000L, 2_000L, TimelineVideoEffectPreset.MONOCHROME),
            effect(4L, 4_000L, 1_000L, TimelineVideoEffectPreset.WARM)
        )

        assertEquals(listOf(3L, 9L), TimelineVideoEffectMath.activeAt(1_000L, clips, 10_000L).map { it.id })
        assertEquals(listOf(9L), TimelineVideoEffectMath.activeAt(3_500L, clips, 10_000L).map { it.id })
        assertEquals(listOf(4L), TimelineVideoEffectMath.activeAt(4_000L, clips, 10_000L).map { it.id })
        assertEquals(emptyList<Long>(), TimelineVideoEffectMath.activeAt(5_000L, clips, 10_000L).map { it.id })
    }

    @Test
    fun movingEffectClampsToTimelineAndPreservesDuration() {
        val original = effect(1L, 2_000L, 3_000L)
        assertEquals(
            effect(1L, 7_000L, 3_000L),
            TimelineVideoEffectMath.move(original, 20_000L, 10_000L)
        )
        assertEquals(
            effect(1L, 0L, 3_000L),
            TimelineVideoEffectMath.move(original, -20_000L, 10_000L)
        )
    }

    @Test
    fun startAndEndTrimsPreserveMinimumDuration() {
        val original = effect(1L, 1_000L, 1_000L)
        assertEquals(
            effect(1L, 1_750L, 250L),
            TimelineVideoEffectMath.trimStart(original, 5_000L, 10_000L)
        )
        assertEquals(
            effect(1L, 1_000L, 250L),
            TimelineVideoEffectMath.trimEnd(original, -5_000L, 10_000L)
        )
    }

    @Test
    fun extremeDragDeltasClampWithoutOverflow() {
        val original = effect(1L, 2_000L, 3_000L)

        assertEquals(
            effect(1L, 7_000L, 3_000L),
            TimelineVideoEffectMath.move(original, Long.MAX_VALUE, 10_000L)
        )
        assertEquals(
            effect(1L, 0L, 3_000L),
            TimelineVideoEffectMath.move(original, Long.MIN_VALUE, 10_000L)
        )
        assertEquals(
            effect(1L, 4_750L, 250L),
            TimelineVideoEffectMath.trimStart(original, Long.MAX_VALUE, 10_000L)
        )
        assertEquals(
            effect(1L, 2_000L, 250L),
            TimelineVideoEffectMath.trimEnd(original, Long.MIN_VALUE, 10_000L)
        )
    }

    @Test
    fun endTrimClampsAtProjectBoundary() {
        assertEquals(
            effect(1L, 8_000L, 2_000L),
            TimelineVideoEffectMath.trimEnd(effect(1L, 8_000L, 1_000L), 9_000L, 10_000L)
        )
    }
}

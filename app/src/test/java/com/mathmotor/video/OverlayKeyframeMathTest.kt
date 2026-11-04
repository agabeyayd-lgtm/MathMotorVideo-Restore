package com.mathmotor.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OverlayKeyframeMathTest {
    private val start = OverlayTransformState(0.5f, 0.2f, 0.3f, 170f, 1f)
    private val end = OverlayTransformState(1f, 0.8f, 0.7f, -170f, 0f)

    @Test
    fun noKeyframesKeepTheBaseState() {
        assertEquals(start, OverlayKeyframeMath.stateAt(start, emptyList(), 2_000L))
    }

    @Test
    fun interpolationUsesTimelinePositionAndClampsAtTheEdges() {
        val frames = listOf(
            OverlayTransformKeyframe(0L, start),
            OverlayTransformKeyframe(2_000L, end)
        )
        assertEquals(start, OverlayKeyframeMath.stateAt(start, frames, -100L))
        val middle = OverlayKeyframeMath.stateAt(start, frames, 1_000L)
        assertEquals(0.75f, middle.scale, 0.0001f)
        assertEquals(0.5f, middle.xFraction, 0.0001f)
        assertEquals(0.5f, middle.yFraction, 0.0001f)
        assertEquals(180f, middle.rotationDegrees, 0.0001f)
        assertEquals(0.5f, middle.opacity, 0.0001f)
        assertEquals(end, OverlayKeyframeMath.stateAt(start, frames, 4_000L))
    }

    @Test
    fun upsertReplacesAnExistingTimestampWithoutDuplicating() {
        val frames = listOf(OverlayTransformKeyframe(1_000L, start))
        val updated = OverlayKeyframeMath.upsert(
            frames,
            OverlayTransformKeyframe(1_000L, end)
        )
        assertEquals(1, updated.size)
        assertEquals(end, updated.single().state)
    }

    @Test
    fun nearestIndexHonorsTheRequestedTolerance() {
        val frames = listOf(
            OverlayTransformKeyframe(0L, start),
            OverlayTransformKeyframe(1_000L, end)
        )
        assertEquals(1, OverlayKeyframeMath.nearestIndex(frames, 1_150L, 200L))
        assertNull(OverlayKeyframeMath.nearestIndex(frames, 1_500L, 200L))
    }
}

package com.mathmotor.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClipTransformKeyframeMathTest {
    private val first = ClipTransformValues(1f, 170f, -0.5f, 0.2f)
    private val last = ClipTransformValues(2f, -170f, 0.5f, -0.2f)

    @Test
    fun noKeyframesKeepTheStaticClipTransform() {
        assertEquals(first, ClipTransformKeyframeMath.stateAt(first, emptyList(), 500L))
    }

    @Test
    fun interpolationUsesTheShortestRotationAndInterpolatesPosition() {
        val frames = listOf(
            ClipTransformKeyframe(0L, first),
            ClipTransformKeyframe(1_000L, last)
        )
        val middle = ClipTransformKeyframeMath.stateAt(first, frames, 500L)
        assertEquals(1.5f, middle.scale, 0.0001f)
        assertEquals(0f, middle.translateX, 0.0001f)
        assertEquals(0f, middle.translateY, 0.0001f)
        assertEquals(-180f, middle.rotationDegrees, 0.0001f)
    }

    @Test
    fun duplicateStoredTimestampsUseTheLastSavedTransform() {
        val alternate = ClipTransformValues(1.75f, 45f, 0.25f, -0.1f)
        val frames = listOf(
            ClipTransformKeyframe(0L, first),
            ClipTransformKeyframe(1_000L, last),
            ClipTransformKeyframe(1_000L, alternate)
        )

        assertEquals(alternate, ClipTransformKeyframeMath.stateAt(first, frames, 1_000L))
    }

    @Test
    fun keyframeUpsertAndLookupAreStable() {
        val frames = listOf(ClipTransformKeyframe(500L, first))
        val updated = ClipTransformKeyframeMath.upsert(frames, ClipTransformKeyframe(500L, last))
        assertEquals(1, updated.size)
        assertEquals(last, updated.single().transform)
        assertEquals(0, ClipTransformKeyframeMath.nearestIndex(updated, 600L, 150L))
        assertNull(ClipTransformKeyframeMath.nearestIndex(updated, 800L, 100L))
    }
}

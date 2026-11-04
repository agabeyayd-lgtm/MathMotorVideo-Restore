package com.mathmotor.video

internal data class ClipTransformKeyframe(
    val timeMs: Long,
    val transform: ClipTransformValues
)

internal object ClipTransformKeyframeMath {
    fun stateAt(
        base: ClipTransformValues,
        keyframes: List<ClipTransformKeyframe>,
        requestedTimeMs: Long
    ): ClipTransformValues {
        if (keyframes.isEmpty()) {
            return ClipTransformMath.normalize(base.scale, base.rotationDegrees, base.translateX, base.translateY)
        }
        val sorted = keyframes.map {
            it.copy(
                timeMs = it.timeMs.coerceAtLeast(0L),
                transform = ClipTransformMath.normalize(
                    it.transform.scale, it.transform.rotationDegrees,
                    it.transform.translateX, it.transform.translateY
                )
            )
        }.sortedBy { it.timeMs }
        val time = requestedTimeMs.coerceAtLeast(0L)
        if (time <= sorted.first().timeMs) return sorted.first().transform
        if (time >= sorted.last().timeMs) return sorted.last().transform
        val rightIndex = sorted.indexOfFirst { it.timeMs >= time }.coerceAtLeast(1)
        val left = sorted[rightIndex - 1]
        val right = sorted[rightIndex]
        val fraction = ((time - left.timeMs).toFloat() /
            (right.timeMs - left.timeMs).coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
        fun lerp(a: Float, b: Float) = a + (b - a) * fraction
        var rotationDelta = (right.transform.rotationDegrees - left.transform.rotationDegrees) % 360f
        if (rotationDelta > 180f) rotationDelta -= 360f
        if (rotationDelta < -180f) rotationDelta += 360f
        return ClipTransformMath.normalize(
            lerp(left.transform.scale, right.transform.scale),
            left.transform.rotationDegrees + rotationDelta * fraction,
            lerp(left.transform.translateX, right.transform.translateX),
            lerp(left.transform.translateY, right.transform.translateY)
        )
    }

    fun upsert(
        keyframes: List<ClipTransformKeyframe>,
        keyframe: ClipTransformKeyframe
    ): List<ClipTransformKeyframe> {
        val time = keyframe.timeMs.coerceAtLeast(0L)
        return (keyframes.filterNot { it.timeMs == time } + keyframe.copy(
            timeMs = time,
            transform = ClipTransformMath.normalize(
                keyframe.transform.scale, keyframe.transform.rotationDegrees,
                keyframe.transform.translateX, keyframe.transform.translateY
            )
        )).sortedBy { it.timeMs }
    }

    fun nearestIndex(keyframes: List<ClipTransformKeyframe>, timeMs: Long, toleranceMs: Long): Int? {
        if (keyframes.isEmpty()) return null
        val target = timeMs.coerceAtLeast(0L)
        val closest = keyframes.indices.minByOrNull { kotlin.math.abs(keyframes[it].timeMs - target) }
            ?: return null
        return closest.takeIf {
            kotlin.math.abs(keyframes[it].timeMs - target) <= toleranceMs.coerceAtLeast(0L)
        }
    }
}

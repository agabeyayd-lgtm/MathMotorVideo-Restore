package com.mathmotor.video

internal data class OverlayTransformState(
    val scale: Float,
    val xFraction: Float,
    val yFraction: Float,
    val rotationDegrees: Float,
    val opacity: Float
) {
    companion object {
        fun normalize(value: OverlayTransformState): OverlayTransformState = OverlayTransformState(
            scale = value.scale.takeIf { it.isFinite() }?.coerceIn(0.10f, 1.50f) ?: 0.35f,
            xFraction = value.xFraction.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0.78f,
            yFraction = value.yFraction.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0.22f,
            rotationDegrees = value.rotationDegrees.takeIf { it.isFinite() }?.let {
                val wrapped = (((it + 180f) % 360f + 360f) % 360f) - 180f
                if (wrapped == -180f && it > 0f) 180f else wrapped
            } ?: 0f,
            opacity = value.opacity.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 1f
        )
    }
}

internal data class OverlayTransformKeyframe(
    val timeMs: Long,
    val state: OverlayTransformState
)

/** Keeps a video overlay's duration inside the source range remaining after its left trim. */
internal object OverlaySourceRangeMath {
    fun availableDurationMs(
        sourceDurationMs: Long,
        sourceStartMs: Long,
        isImage: Boolean
    ): Long {
        val sourceDuration = sourceDurationMs.coerceAtLeast(0L)
        val sourceStart = if (isImage) 0L else sourceStartMs.coerceIn(0L, sourceDuration)
        return (sourceDuration - sourceStart).coerceAtLeast(0L)
    }

    fun clampDurationMs(
        requestedDurationMs: Long,
        sourceDurationMs: Long,
        sourceStartMs: Long,
        isImage: Boolean,
        minimumDurationMs: Long = 1_000L
    ): Long {
        val available = availableDurationMs(sourceDurationMs, sourceStartMs, isImage)
        if (available <= 0L) return 0L
        val minimum = minimumDurationMs.coerceAtLeast(1L).coerceAtMost(available)
        return requestedDurationMs.coerceIn(minimum, available)
    }
}

/** Linear interpolation of independently editable overlay transform and opacity keyframes. */
internal object OverlayKeyframeMath {
    fun stateAt(
        baseState: OverlayTransformState,
        keyframes: List<OverlayTransformKeyframe>,
        requestedTimeMs: Long
    ): OverlayTransformState {
        if (keyframes.isEmpty()) return OverlayTransformState.normalize(baseState)
        val sorted = normalizedKeyframes(keyframes)
        val timeMs = requestedTimeMs.coerceAtLeast(0L)
        val first = sorted.first()
        val last = sorted.last()
        if (timeMs <= first.timeMs) return first.state
        if (timeMs >= last.timeMs) return last.state
        val rightIndex = sorted.indexOfFirst { it.timeMs >= timeMs }.coerceAtLeast(1)
        val left = sorted[rightIndex - 1]
        val right = sorted[rightIndex]
        val span = (right.timeMs - left.timeMs).coerceAtLeast(1L)
        val fraction = ((timeMs - left.timeMs).toFloat() / span.toFloat()).coerceIn(0f, 1f)
        fun lerp(start: Float, end: Float): Float = start + (end - start) * fraction
        val rotationDelta = shortestRotationDelta(left.state.rotationDegrees, right.state.rotationDegrees)
        return OverlayTransformState.normalize(
            OverlayTransformState(
                scale = lerp(left.state.scale, right.state.scale),
                xFraction = lerp(left.state.xFraction, right.state.xFraction),
                yFraction = lerp(left.state.yFraction, right.state.yFraction),
                rotationDegrees = left.state.rotationDegrees + rotationDelta * fraction,
                opacity = lerp(left.state.opacity, right.state.opacity)
            )
        )
    }

    fun upsert(
        keyframes: List<OverlayTransformKeyframe>,
        keyframe: OverlayTransformKeyframe
    ): List<OverlayTransformKeyframe> {
        val timeMs = keyframe.timeMs.coerceAtLeast(0L)
        return (normalizedKeyframes(keyframes).filterNot { it.timeMs == timeMs } + keyframe.copy(
            timeMs = timeMs,
            state = OverlayTransformState.normalize(keyframe.state)
        )).sortedBy { it.timeMs }
    }

    /** Rebase local keyframe times after moving an overlay's left trim edge. */
    fun rebaseAfterStartTrim(
        keyframes: List<OverlayTransformKeyframe>,
        removedStartMs: Long,
        newDurationMs: Long,
        stateAtNewStart: OverlayTransformState
    ): List<OverlayTransformKeyframe> {
        if (keyframes.isEmpty()) return emptyList()
        val shift = removedStartMs
        val duration = newDurationMs.coerceAtLeast(0L)
        val rebased = keyframes
            .filter { it.timeMs >= shift && it.timeMs <= shift + duration }
            .map { it.copy(timeMs = it.timeMs - shift) }
        return upsert(rebased, OverlayTransformKeyframe(0L, stateAtNewStart))
    }

    fun nearestIndex(
        keyframes: List<OverlayTransformKeyframe>,
        timeMs: Long,
        toleranceMs: Long
    ): Int? {
        if (keyframes.isEmpty()) return null
        val target = timeMs.coerceAtLeast(0L)
        val candidate = keyframes.indices.minByOrNull { index ->
            kotlin.math.abs(keyframes[index].timeMs - target)
        } ?: return null
        return candidate.takeIf {
            kotlin.math.abs(keyframes[it].timeMs - target) <= toleranceMs.coerceAtLeast(0L)
        }
    }

    /** Duplicate timestamps can occur in hand-edited/older project JSON; keep the last saved value. */
    private fun normalizedKeyframes(
        keyframes: List<OverlayTransformKeyframe>
    ): List<OverlayTransformKeyframe> = keyframes
        .map {
            it.copy(
                timeMs = it.timeMs.coerceAtLeast(0L),
                state = OverlayTransformState.normalize(it.state)
            )
        }
        .groupBy { it.timeMs }
        .values
        .map { it.last() }
        .sortedBy { it.timeMs }

    private fun shortestRotationDelta(from: Float, to: Float): Float {
        var delta = (to - from) % 360f
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        return delta
    }
}

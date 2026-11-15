package com.mathmotor.video

/**
 * A non-destructive video effect placed on the editor's shared project timeline.
 * The effect changes only the base video lane; independent overlay/text/audio
 * tracks keep their normal layer order.
 */
internal enum class TimelineVideoEffectPreset(val key: String, val title: String) {
    BLUR("blur", "Bulanık"),
    MONOCHROME("monochrome", "Siyah-beyaz"),
    NEGATIVE("negative", "Negatif"),
    WARM("warm", "Sıcak");

    companion object {
        fun fromKey(value: String?): TimelineVideoEffectPreset =
            values().firstOrNull { it.key == value } ?: BLUR
    }
}

internal data class TimelineVideoEffectClip(
    val id: Long,
    val preset: TimelineVideoEffectPreset,
    val timelineStartMs: Long,
    val durationMs: Long,
    val intensityPercent: Int = 100
) {
    val endMs: Long
        get() = timelineStartMs + durationMs
}

/** Pure time-range operations shared by the effect UI and composition builder. */
internal object TimelineVideoEffectMath {
    const val MIN_DURATION_MS = 250L
    const val DEFAULT_DURATION_MS = 3_000L

    fun normalize(
        clip: TimelineVideoEffectClip,
        totalDurationMs: Long
    ): TimelineVideoEffectClip? {
        if (totalDurationMs < MIN_DURATION_MS) return null
        val start = clip.timelineStartMs.coerceIn(0L, totalDurationMs - MIN_DURATION_MS)
        val requestedDuration = clip.durationMs.coerceAtLeast(MIN_DURATION_MS)
        val duration = requestedDuration.coerceAtMost(totalDurationMs - start)
        if (duration < MIN_DURATION_MS) return null
        return clip.copy(
            timelineStartMs = start,
            durationMs = duration,
            intensityPercent = clip.intensityPercent.coerceIn(0, 100)
        )
    }

    fun boundaries(
        totalDurationMs: Long,
        clips: List<TimelineVideoEffectClip>
    ): List<Long> {
        if (totalDurationMs <= 0L) return emptyList()
        return buildSet {
            add(0L)
            add(totalDurationMs)
            clips.forEach { raw ->
                val clip = normalize(raw, totalDurationMs) ?: return@forEach
                add(clip.timelineStartMs)
                add(clip.endMs)
            }
        }.sorted()
    }

    /** Effects active at the start of a segment whose edges already include effect boundaries. */
    fun activeAt(
        timeMs: Long,
        clips: List<TimelineVideoEffectClip>,
        totalDurationMs: Long
    ): List<TimelineVideoEffectClip> {
        if (timeMs < 0L || timeMs >= totalDurationMs) return emptyList()
        return clips.mapNotNull { normalize(it, totalDurationMs) }
            .filter { timeMs >= it.timelineStartMs && timeMs < it.endMs }
            .sortedWith(compareBy<TimelineVideoEffectClip> { it.timelineStartMs }.thenBy { it.id })
    }

    fun move(
        clip: TimelineVideoEffectClip,
        deltaMs: Long,
        totalDurationMs: Long
    ): TimelineVideoEffectClip? {
        val normalized = normalize(clip, totalDurationMs) ?: return null
        val maxStart = (totalDurationMs - normalized.durationMs).coerceAtLeast(0L)
        val baseStart = normalized.timelineStartMs.coerceAtMost(maxStart)
        val safeDelta = deltaMs.coerceIn(-baseStart, maxStart - baseStart)
        return normalized.copy(timelineStartMs = baseStart + safeDelta)
    }

    fun trimStart(
        clip: TimelineVideoEffectClip,
        deltaMs: Long,
        totalDurationMs: Long
    ): TimelineVideoEffectClip? {
        val normalized = normalize(clip, totalDurationMs) ?: return null
        val maximumStart = normalized.endMs - MIN_DURATION_MS
        val safeDelta = deltaMs.coerceIn(-normalized.timelineStartMs, maximumStart - normalized.timelineStartMs)
        val start = normalized.timelineStartMs + safeDelta
        return normalized.copy(
            timelineStartMs = start,
            durationMs = normalized.endMs - start
        )
    }

    fun trimEnd(
        clip: TimelineVideoEffectClip,
        deltaMs: Long,
        totalDurationMs: Long
    ): TimelineVideoEffectClip? {
        val normalized = normalize(clip, totalDurationMs) ?: return null
        val minimumEnd = normalized.timelineStartMs + MIN_DURATION_MS
        val minimumDelta = minimumEnd - normalized.endMs
        val maximumDelta = totalDurationMs - normalized.endMs
        val safeDelta = deltaMs.coerceIn(minimumDelta, maximumDelta)
        val end = normalized.endMs + safeDelta
        return normalized.copy(durationMs = end - normalized.timelineStartMs)
    }
}

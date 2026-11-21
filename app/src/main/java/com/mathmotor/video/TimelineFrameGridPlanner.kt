package com.mathmotor.video

/**
 * Builds one coarse-to-fine frame schedule for a source video.
 *
 * Every timestamp is generated only once. Each stage is sorted so timestamps
 * in the currently visible source interval are decoded before off-screen ones.
 */
internal object TimelineFrameGridPlanner {
    data class FrameRequest(
        val timestampMs: Long,
        val intervalMs: Long,
        val stageMs: Long
    )

    fun orderedFrames(
        durationMs: Long,
        visibleStartMs: Long,
        visibleEndMs: Long
    ): List<FrameRequest> {
        val duration = durationMs.coerceAtLeast(0L)
        if (duration <= 0L) return emptyList()

        val visibleStart = visibleStartMs.coerceIn(0L, duration - 1L)
        val visibleEnd = visibleEndMs.coerceIn(visibleStart, duration - 1L)
        val seen = HashSet<Long>()
        val result = ArrayList<FrameRequest>()
        val stages = longArrayOf(4_000L, 2_000L, 1_000L, 500L)

        stages.forEach { step ->
            val stageFrames = ArrayList<Long>()
            var timestamp = 0L
            while (timestamp < duration) {
                if (seen.add(timestamp)) stageFrames.add(timestamp)
                timestamp += step
            }
            if (step == 500L) {
                val finalTimestamp = duration - 1L
                if (seen.add(finalTimestamp)) stageFrames.add(finalTimestamp)
            }

            stageFrames.sortWith(
                compareBy<Long> { value ->
                    when {
                        value in visibleStart..visibleEnd -> 0
                        value < visibleStart -> 1
                        else -> 2
                    }
                }.thenBy { value ->
                    when {
                        value in visibleStart..visibleEnd -> value - visibleStart
                        value < visibleStart -> visibleStart - value
                        else -> value - visibleEnd
                    }
                }.thenBy { it }
            )
            stageFrames.forEach { value ->
                result.add(FrameRequest(value, step, step))
            }
        }
        return result
    }
}

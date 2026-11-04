package com.mathmotor.video

internal enum class TextAnimationPreset(val key: String, val title: String) {
    NONE("none", "Yok"),
    FADE("fade", "Solma"),
    SLIDE_UP("slide_up", "Yukarı"),
    SLIDE_LEFT("slide_left", "Soldan"),
    POP("pop", "Pop");

    companion object {
        fun fromKey(value: String?): TextAnimationPreset =
            values().firstOrNull { it.key == value } ?: NONE
    }
}

internal data class TextAnimationState(
    val alpha: Float = 1f,
    val scale: Float = 1f,
    val offsetXFraction: Float = 0f,
    val offsetYFraction: Float = 0f
)

internal object TextAnimationMath {
    fun stateAt(
        preset: TextAnimationPreset,
        elapsedMs: Long,
        remainingMs: Long,
        durationMs: Long
    ): TextAnimationState {
        if (preset == TextAnimationPreset.NONE) return TextAnimationState()
        val duration = durationMs.coerceIn(100L, 1_500L)
        val enter = (elapsedMs.coerceAtLeast(0L).toFloat() / duration).coerceIn(0f, 1f)
        val exit = (remainingMs.coerceAtLeast(0L).toFloat() / duration).coerceIn(0f, 1f)
        val progress = minOf(enter, exit)
        val alpha = progress
        return when (preset) {
            TextAnimationPreset.NONE -> TextAnimationState()
            TextAnimationPreset.FADE -> TextAnimationState(alpha = alpha)
            TextAnimationPreset.SLIDE_UP -> TextAnimationState(
                alpha = alpha,
                offsetYFraction = (1f - enter) * 0.14f - (1f - exit) * 0.14f
            )
            TextAnimationPreset.SLIDE_LEFT -> TextAnimationState(
                alpha = alpha,
                offsetXFraction = -(1f - enter) * 0.14f + (1f - exit) * 0.14f
            )
            TextAnimationPreset.POP -> TextAnimationState(
                alpha = alpha,
                scale = 0.65f + 0.35f * progress
            )
        }
    }
}

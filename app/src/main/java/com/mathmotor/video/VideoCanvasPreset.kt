package com.mathmotor.video

/** Manual output framing; no AI crop or subject detection is involved. */
internal enum class VideoCanvasPreset(
    val key: String,
    val title: String,
    val aspectRatio: Float?
) {
    SOURCE("source", "Kaynak", null),
    PORTRAIT_9_16("9:16", "9:16", 9f / 16f),
    PORTRAIT_4_5("4:5", "4:5", 4f / 5f),
    SQUARE("1:1", "1:1", 1f),
    LANDSCAPE_16_9("16:9", "16:9", 16f / 9f);

    companion object {
        fun fromKey(value: String?): VideoCanvasPreset =
            values().firstOrNull { it.key == value } ?: SOURCE
    }
}

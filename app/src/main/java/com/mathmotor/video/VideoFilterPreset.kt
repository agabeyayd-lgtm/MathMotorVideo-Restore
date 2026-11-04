package com.mathmotor.video

/** Non-AI color looks that can be rendered identically in preview and MP4 export. */
internal enum class VideoFilterPreset(val key: String, val title: String) {
    ORIGINAL("original", "Orijinal"),
    MONOCHROME("monochrome", "Siyah-beyaz"),
    VIVID("vivid", "Canlı"),
    CINEMATIC("cinematic", "Sinematik"),
    WARM("warm", "Sıcak"),
    COOL("cool", "Soğuk"),
    NEGATIVE("negative", "Negatif");

    companion object {
        fun fromKey(value: String?): VideoFilterPreset =
            values().firstOrNull { it.key == value } ?: ORIGINAL
    }
}

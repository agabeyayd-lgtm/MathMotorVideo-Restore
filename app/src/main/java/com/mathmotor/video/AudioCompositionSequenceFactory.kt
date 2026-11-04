package com.mathmotor.video

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence

/** Builds an audio-only sequence with explicit silence before/after its source items. */
@OptIn(UnstableApi::class)
internal object AudioCompositionSequenceFactory {
    fun build(
        items: List<EditedMediaItem>,
        leadingGapMs: Long = 0L,
        trailingGapMs: Long = 0L
    ): EditedMediaItemSequence {
        require(items.isNotEmpty()) { "Ses dizisi en az bir medya öğesi içermeli." }
        require(leadingGapMs >= 0L && trailingGapMs >= 0L) {
            "Ses dizisi boşluk süreleri negatif olamaz."
        }

        val builder = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO))
        if (leadingGapMs > 0L) builder.addGap(leadingGapMs * 1_000L)
        items.forEach { item -> builder.addItem(item) }
        if (trailingGapMs > 0L) builder.addGap(trailingGapMs * 1_000L)
        return builder.build()
    }
}

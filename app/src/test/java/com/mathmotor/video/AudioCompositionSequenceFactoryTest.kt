package com.mathmotor.video

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.EditedMediaItem
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(UnstableApi::class)
class AudioCompositionSequenceFactoryTest {
    @Test
    fun delayedAudioSequenceDeclaresAudioTrackAndKeepsSilenceGaps() {
        val source = MediaItem.Builder().setMediaId("test-audio").build()
        val item = EditedMediaItem.Builder(source).setRemoveVideo(true).build()

        val sequence = AudioCompositionSequenceFactory.build(
            items = listOf(item),
            leadingGapMs = 1_250L,
            trailingGapMs = 750L
        )

        assertEquals(setOf(C.TRACK_TYPE_AUDIO), sequence.trackTypes)
        assertEquals(3, sequence.editedMediaItems.size)
    }

    @Test
    fun immediateAudioSequenceHasNoUnnecessaryGapItems() {
        val source = MediaItem.Builder().setMediaId("test-audio").build()
        val item = EditedMediaItem.Builder(source).setRemoveVideo(true).build()

        val sequence = AudioCompositionSequenceFactory.build(listOf(item))

        assertEquals(setOf(C.TRACK_TYPE_AUDIO), sequence.trackTypes)
        assertEquals(1, sequence.editedMediaItems.size)
    }
}

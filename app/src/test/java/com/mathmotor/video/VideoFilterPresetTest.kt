package com.mathmotor.video

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoFilterPresetTest {
    @Test
    fun legacyProjectsDefaultToOriginalLook() {
        assertEquals(VideoFilterPreset.ORIGINAL, VideoFilterPreset.fromKey(null))
        assertEquals(VideoFilterPreset.ORIGINAL, VideoFilterPreset.fromKey(""))
    }

    @Test
    fun unknownFuturePresetFallsBackSafely() {
        assertEquals(VideoFilterPreset.ORIGINAL, VideoFilterPreset.fromKey("not-a-filter"))
    }

    @Test
    fun everyPresetHasAStableNonBlankStorageKeyAndTitle() {
        VideoFilterPreset.values().forEach { preset ->
            assertEquals(false, preset.key.isBlank())
            assertEquals(false, preset.title.isBlank())
        }
    }
}

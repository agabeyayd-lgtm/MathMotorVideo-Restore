package com.mathmotor.video

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoCanvasPresetTest {
    @Test
    fun legacyProjectsKeepTheirOriginalCanvas() {
        assertEquals(VideoCanvasPreset.SOURCE, VideoCanvasPreset.fromKey(null))
    }

    @Test
    fun allManualCanvasRatiosAreCorrect() {
        assertEquals(9f / 16f, VideoCanvasPreset.fromKey("9:16").aspectRatio!!)
        assertEquals(4f / 5f, VideoCanvasPreset.fromKey("4:5").aspectRatio!!)
        assertEquals(1f, VideoCanvasPreset.fromKey("1:1").aspectRatio!!)
        assertEquals(16f / 9f, VideoCanvasPreset.fromKey("16:9").aspectRatio!!)
    }

    @Test
    fun unknownCanvasFallsBackToSource() {
        assertEquals(VideoCanvasPreset.SOURCE, VideoCanvasPreset.fromKey("unknown"))
    }
}

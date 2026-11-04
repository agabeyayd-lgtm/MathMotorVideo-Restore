package com.mathmotor.video

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioChannelMixingMathTest {
    @Test
    fun everySupportedInputLayoutProducesStereo() {
        for (channels in 1..8) {
            val matrix = AudioChannelMixingMath.stereoMatrix(channels, 1f)
            assertEquals(channels, matrix.inputChannelCount)
            assertEquals(AudioChannelMixingMath.OUTPUT_CHANNEL_COUNT, matrix.outputChannelCount)
        }
    }

    @Test
    fun gainIsAppliedToEveryOutputCoefficient() {
        val matrix = AudioChannelMixingMath.stereoMatrix(2, 0.4f)
        assertEquals(0.4f, matrix.getMixingCoefficient(0, 0), 0.0001f)
        assertEquals(0f, matrix.getMixingCoefficient(0, 1), 0.0001f)
        assertEquals(0f, matrix.getMixingCoefficient(1, 0), 0.0001f)
        assertEquals(0.4f, matrix.getMixingCoefficient(1, 1), 0.0001f)
    }

    @Test
    fun extraSurroundChannelsAreIncludedInStereoDownmix() {
        val sixPointOne = AudioChannelMixingMath.stereoMatrix(7, 1f)
        assertEquals(0.5f, sixPointOne.getMixingCoefficient(6, 0), 0.0001f)
        assertEquals(0.5f, sixPointOne.getMixingCoefficient(6, 1), 0.0001f)

        val sevenPointOne = AudioChannelMixingMath.stereoMatrix(8, 1f)
        assertEquals(0.70710677f, sevenPointOne.getMixingCoefficient(6, 0), 0.0001f)
        assertEquals(0f, sevenPointOne.getMixingCoefficient(6, 1), 0.0001f)
        assertEquals(0f, sevenPointOne.getMixingCoefficient(7, 0), 0.0001f)
        assertEquals(0.70710677f, sevenPointOne.getMixingCoefficient(7, 1), 0.0001f)
    }
}

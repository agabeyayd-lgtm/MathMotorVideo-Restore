package com.mathmotor.video

import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.util.UnstableApi

/**
 * Normalizes every editor audio source to stereo so parallel Composition sequences
 * satisfy Media3's requirement that mixed audio items use the same channel count.
 */
@OptIn(UnstableApi::class)
internal object AudioChannelMixingMath {
    const val OUTPUT_CHANNEL_COUNT = 2
    private const val EXTRA_SURROUND_GAIN = 0.5f
    private const val SIDE_SURROUND_GAIN = 0.70710677f

    fun stereoMatrix(inputChannelCount: Int, gain: Float): ChannelMixingMatrix {
        require(inputChannelCount in 1..8) {
            "Desteklenen ses kanal sayısı 1–8 aralığında olmalı."
        }
        val safeGain = gain.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 1f
        val baseChannelCount = minOf(inputChannelCount, 6)
        val base = ChannelMixingMatrix.createForConstantPower(
            baseChannelCount,
            OUTPUT_CHANNEL_COUNT
        )
        val coefficients = FloatArray(inputChannelCount * OUTPUT_CHANNEL_COUNT)

        for (input in 0 until inputChannelCount) {
            for (output in 0 until OUTPUT_CHANNEL_COUNT) {
                val baseCoefficient = when {
                    input < baseChannelCount -> base.getMixingCoefficient(input, output)
                    inputChannelCount == 7 -> EXTRA_SURROUND_GAIN
                    input == 6 -> if (output == 0) SIDE_SURROUND_GAIN else 0f
                    else -> if (output == 1) SIDE_SURROUND_GAIN else 0f
                }
                coefficients[input * OUTPUT_CHANNEL_COUNT + output] =
                    baseCoefficient * safeGain
            }
        }
        return ChannelMixingMatrix(
            inputChannelCount,
            OUTPUT_CHANNEL_COUNT,
            coefficients
        )
    }
}

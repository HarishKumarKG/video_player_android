package com.harish.videoplayer.playback

import androidx.media3.common.audio.AudioMixingUtil
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import java.nio.ByteBuffer

/**
 * "Clear voices" for 5.1 / 7.1 movies on a phone.
 *
 * The problem: in surround audio the dialogue lives almost only in the CENTRE channel.
 * A phone has two speakers (or you wear stereo earphones), so the 6 channels must be squeezed
 * into 2. Android's built-in squeeze turns the centre down (often to ~70%, on some phones much
 * lower) while music and effects in the other channels stay loud, so voices sound low or vanish.
 *
 * The fix: we do the squeeze ourselves, before Android sees the audio, and give the centre
 * (voice) channel full weight in BOTH speakers:
 *
 *   left  = front-left + CENTRE + 0.6 × surround-left  + 0.3 × bass (LFE)
 *   right = front-right + CENTRE + 0.6 × surround-right + 0.3 × bass (LFE)
 *   (then everything scaled down a little so loud scenes don't distort)
 *
 * Stereo and mono audio pass straight through untouched.
 */
class VoiceDownmixAudioProcessor(private val isEnabled: () -> Boolean) : BaseAudioProcessor() {

    private var matrix: ChannelMixingMatrix? = null

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val channels = inputAudioFormat.channelCount
        val downmix = if (isEnabled() && AudioMixingUtil.canMix(inputAudioFormat)) stereoDownmixFor(channels) else null
        matrix = downmix
        // NOT_SET = "I'm not needed for this audio", so it flows past unchanged
        return if (downmix == null) AudioProcessor.AudioFormat.NOT_SET
        else AudioProcessor.AudioFormat(inputAudioFormat.sampleRate, 2, inputAudioFormat.encoding)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val mix = matrix ?: return
        val frames = inputBuffer.remaining() / inputAudioFormat.bytesPerFrame
        val output = replaceOutputBuffer(frames * outputAudioFormat.bytesPerFrame)
        AudioMixingUtil.mix(
            inputBuffer, inputAudioFormat, output, outputAudioFormat, mix, frames,
            /* accumulate = */ false, /* clipFloatOutput = */ true
        )
        output.flip()
    }

    override fun onReset() {
        matrix = null
    }

    companion object {
        private const val FRONT = 1.0f
        private const val CENTRE = 1.0f   // the voice: full strength in both speakers
        private const val SURROUND = 0.6f
        private const val LFE = 0.3f
        private const val SCALE = 0.6f    // headroom so explosions don't crackle

        /**
         * Rows = input channels in Android's order, columns = (left, right).
         * Returns null for mono / stereo (nothing to do) and layouts we don't know.
         */
        internal fun stereoDownmixFor(channelCount: Int): ChannelMixingMatrix? {
            val rows: List<FloatArray> = when (channelCount) {
                3 -> listOf(L, R, C)                         // L R C
                4 -> listOf(L, R, SL, SR)                    // quad: FL FR BL BR
                5 -> listOf(L, R, C, SL, SR)                 // 5.0
                6 -> listOf(L, R, C, BASS, SL, SR)           // 5.1
                7 -> listOf(L, R, C, BASS, BC, SL, SR)       // 6.1
                8 -> listOf(L, R, C, BASS, SL, SR, SL, SR)   // 7.1: FL FR FC LFE BL BR SL SR
                else -> return null
            }
            val coefficients = FloatArray(channelCount * 2)
            rows.forEachIndexed { i, (left, right) ->
                coefficients[i * 2] = left * SCALE
                coefficients[i * 2 + 1] = right * SCALE
            }
            return ChannelMixingMatrix(channelCount, 2, coefficients)
        }

        // How much of each input channel goes to (left, right)
        private val L = floatArrayOf(FRONT, 0f)
        private val R = floatArrayOf(0f, FRONT)
        private val C = floatArrayOf(CENTRE, CENTRE)
        private val BASS = floatArrayOf(LFE, LFE)
        private val SL = floatArrayOf(SURROUND, 0f)
        private val SR = floatArrayOf(0f, SURROUND)
        private val BC = floatArrayOf(SURROUND * 0.7f, SURROUND * 0.7f)
    }
}

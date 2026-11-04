package com.harish.videoplayer.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.tanh

/**
 * Volume above 100%, done inside the player itself, for every kind of audio:
 * stereo, 5.1, 7.1, with or without "Clear voices".
 *
 * Why not Android's LoudnessEnhancer (what we used before)? It's an effect Android attaches to
 * the app's sound output, and on most phones it only works on 2-channel sound. A 5.1 movie
 * reaches Android as 6 channels, so the boost silently did nothing (or very little).
 *
 * Here every sample is simply multiplied by the boost before Android sees the sound:
 *   boost 1.5 -> each sample × 1.5 (150%)
 * Loud peaks would then go past the maximum and crackle, so a soft limiter rounds off only the
 * part above 85% of full scale. Quiet and normal parts are untouched, explosions stay clean.
 *
 * Changing the boost glides to the new level over one buffer (a few ms), so there's no click.
 */
class BoostAudioProcessor : BaseAudioProcessor() {

    /** 1f = normal .. 2f = 200%. Set from any thread, read on the audio thread. */
    @Volatile var gain: Float = 1f

    private var appliedGain = 1f

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat =
        when (inputAudioFormat.encoding) {
            // Always active for normal sound, so the boost can change at any moment
            C.ENCODING_PCM_16BIT, C.ENCODING_PCM_FLOAT -> inputAudioFormat
            else -> AudioProcessor.AudioFormat.NOT_SET // other encodings flow past unchanged
        }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val output = replaceOutputBuffer(size)
        val target = gain
        val start = appliedGain
        if (start == 1f && target == 1f) {
            output.put(inputBuffer) // no boost: plain copy
            output.flip()
            return
        }
        val channels = inputAudioFormat.channelCount
        val float = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        val frames = size / (channels * if (float) 4 else 2)
        for (frame in 0 until frames) {
            // glide from the old to the new boost across this buffer
            val g = start + (target - start) * (frame + 1) / frames
            repeat(channels) {
                if (float) {
                    output.putFloat(boosted(inputBuffer.getFloat(), g))
                } else {
                    val sample = boosted(inputBuffer.getShort() / 32768f, g)
                    output.putShort((sample * 32767f).toInt().toShort())
                }
            }
        }
        inputBuffer.position(inputBuffer.limit())
        appliedGain = target
        output.flip()
    }

    override fun onReset() {
        appliedGain = gain
    }

    companion object {
        private const val KNEE = 0.85f

        internal fun boosted(sample: Float, gain: Float): Float {
            val x = sample * gain
            return if (gain > 1f) softLimit(x) else x
        }

        /** Unchanged up to 85%, then bends smoothly so it never passes 100%. */
        internal fun softLimit(x: Float): Float {
            val a = abs(x)
            if (a <= KNEE) return x
            val y = KNEE + (1f - KNEE) * tanh((a - KNEE) / (1f - KNEE))
            return if (x < 0) -y else y
        }
    }
}

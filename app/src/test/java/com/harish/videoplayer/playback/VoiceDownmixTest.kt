package com.harish.videoplayer.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceDownmixTest {

    private val left = 0
    private val right = 1

    @Test fun `stereo and mono are left alone`() {
        assertNull(VoiceDownmixAudioProcessor.stereoDownmixFor(1))
        assertNull(VoiceDownmixAudioProcessor.stereoDownmixFor(2))
    }

    @Test fun `5_1 keeps the voice channel as loud as the front speakers, in both ears`() {
        val m = checkNotNull(VoiceDownmixAudioProcessor.stereoDownmixFor(6)) // L R C LFE SL SR
        assertEquals(6, m.inputChannelCount)
        assertEquals(2, m.outputChannelCount)
        val front = m.getMixingCoefficient(0, left)
        assertEquals(front, m.getMixingCoefficient(2, left), 0.0001f)   // centre -> left
        assertEquals(front, m.getMixingCoefficient(2, right), 0.0001f)  // centre -> right
        assertEquals(0f, m.getMixingCoefficient(0, right), 0.0001f)     // front-left stays left
        assertTrue(m.getMixingCoefficient(4, left) < front)            // surround quieter than voice
        assertEquals(0f, m.getMixingCoefficient(4, right), 0.0001f)
    }

    @Test fun `7_1 is supported too`() {
        val m = checkNotNull(VoiceDownmixAudioProcessor.stereoDownmixFor(8))
        assertEquals(8, m.inputChannelCount)
    }
}

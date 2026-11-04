package com.harish.videoplayer.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoostAudioProcessorTest {

    @Test fun `no boost leaves every sample exactly as it was`() {
        listOf(-1f, -0.9f, 0f, 0.3f, 0.95f, 1f).forEach {
            assertEquals(it, BoostAudioProcessor.boosted(it, 1f), 0f)
        }
    }

    @Test fun `quiet sound is simply made louder`() {
        assertEquals(0.4f, BoostAudioProcessor.boosted(0.2f, 2f), 1e-6f)   // 200%
        assertEquals(-0.6f, BoostAudioProcessor.boosted(-0.4f, 1.5f), 1e-6f)
    }

    @Test fun `loud peaks are rounded off and never pass full scale`() {
        val peak = BoostAudioProcessor.boosted(0.9f, 2f)   // 1.8 before limiting
        assertTrue(peak < 1f && peak > 0.85f)
        val negative = BoostAudioProcessor.boosted(-1f, 2f)
        assertTrue(negative > -1f && negative < -0.85f)
        // louder in -> still louder out (no harsh flattening)
        assertTrue(BoostAudioProcessor.boosted(0.6f, 2f) < BoostAudioProcessor.boosted(0.9f, 2f))
    }
}

package com.harish.videoplayer.domain

import com.harish.videoplayer.domain.model.audioCodecLabel
import com.harish.videoplayer.domain.model.channelLayoutLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioFormatTest {

    @Test fun `codec names`() {
        assertEquals("AAC", audioCodecLabel("audio/mp4a-latm"))
        assertEquals("Dolby Digital", audioCodecLabel("audio/ac3"))
        assertEquals("Dolby Digital Plus", audioCodecLabel("audio/eac3"))
        assertEquals("DTS", audioCodecLabel("audio/vnd.dts"))
        assertEquals("XYZ", audioCodecLabel("audio/xyz"))
        assertNull(audioCodecLabel(null))
    }

    @Test fun `channel layouts`() {
        assertEquals("Stereo", channelLayoutLabel(2))
        assertEquals("2.1", channelLayoutLabel(3))
        assertEquals("5.1", channelLayoutLabel(6))
        assertNull(channelLayoutLabel(-1))
    }
}

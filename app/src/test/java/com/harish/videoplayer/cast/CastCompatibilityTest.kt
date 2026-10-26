package com.harish.videoplayer.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CastCompatibilityTest {

    private fun check(video: List<String>, audio: List<String>, kind: CastKind = CastKind.GOOGLE_CAST) =
        CastCompatibility.check(kind, MediaFormats(video, audio), "Living Room TV")

    @Test fun `normal 4K files cast without a warning`() {
        assertNull(check(listOf("video/hevc"), listOf("audio/eac3-joc")))     // 4K HDR + Atmos (DD+)
        assertNull(check(listOf("video/avc"), listOf("audio/mp4a-latm")))     // phone recording
        assertNull(check(listOf("video/dolby-vision"), listOf("audio/ac3")))
        assertNull(check(listOf("video/x-vnd.on2.vp9"), listOf("audio/opus"))) // YouTube-style WebM
    }

    @Test fun `TrueHD sound - no sound on the TV`() {
        val warning = check(listOf("video/hevc"), listOf("audio/true-hd"))!!
        assertEquals("No sound on Living Room TV", warning.headline)
        assertTrue(warning.reasons.first().contains("Dolby TrueHD"))
    }

    @Test fun `DTS first with an AC3 track after it - explains the first track is used`() {
        val warning = check(listOf("video/avc"), listOf("audio/vnd.dts", "audio/ac3"))!!
        assertTrue(warning.reasons[0].contains("DTS"))
        assertTrue(warning.reasons[1].contains("Dolby Digital (AC3)"))
    }

    @Test fun `old DivX video won't play at all`() {
        val warning = check(listOf("video/mp4v-es"), listOf("audio/mpeg"))!!
        assertEquals("This video won't play on Living Room TV", warning.headline)
        assertTrue(warning.reasons.first().contains("DivX"))
    }

    @Test fun `unknown formats and DLNA TVs never get a false alarm`() {
        assertNull(check(listOf("video/x-something-new"), listOf("audio/x-unknown")))
        assertNull(check(listOf("video/mp4v-es"), listOf("audio/vnd.dts"), CastKind.DLNA))
        assertNull(check(emptyList(), emptyList()))
    }

    @Test fun `explains why the TV refused`() {
        assertEquals(
            "The sound is DTS-HD, which Chromecast can't play.",
            CastCompatibility.explainFailure(MediaFormats(listOf("video/hevc"), listOf("audio/vnd.dts.hd")))
        )
        assertNull(CastCompatibility.explainFailure(MediaFormats(listOf("video/avc"), listOf("audio/mp4a-latm"))))
    }
}

package com.harish.videoplayer.domain

import com.harish.videoplayer.domain.model.formatDuration
import com.harish.videoplayer.domain.model.formatSize
import com.harish.videoplayer.domain.model.resolutionLabel
import com.harish.videoplayer.domain.model.speedLabel
import com.harish.videoplayer.domain.model.videoCount
import com.harish.videoplayer.domain.subtitle.SubtitleFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatTest {

    @Test fun durations() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("1:15", formatDuration(75_000))
        assertEquals("1:02:05", formatDuration(3_725_000))
    }

    @Test fun sizes() {
        assertEquals("900 B", formatSize(900))
        assertEquals("245 MB", formatSize(245L * 1024 * 1024))
        assertEquals("1.5 GB", formatSize(1536L * 1024 * 1024))
    }

    @Test fun `resolution uses the short side`() {
        assertEquals("1080p", resolutionLabel(1920, 1080))
        assertEquals("1080p", resolutionLabel(1080, 1920)) // portrait phone video
        assertEquals("4K", resolutionLabel(3840, 2160))
        assertEquals("360p", resolutionLabel(640, 360))
        assertNull(resolutionLabel(0, 0))
    }

    @Test fun speeds() {
        assertEquals("1x", speedLabel(1f))
        assertEquals("1.25x", speedLabel(1.25f))
        assertEquals("0.5x", speedLabel(0.5f))
        assertEquals("2x", speedLabel(2f))
    }

    @Test fun counts() {
        assertEquals("1 video", videoCount(1))
        assertEquals("3 videos", videoCount(3))
    }

    @Test fun `subtitle formats from file names`() {
        assertEquals(SubtitleFormat.SRT, SubtitleFormat.mimeTypeFor("Movie.en.SRT"))
        assertEquals(SubtitleFormat.VTT, SubtitleFormat.mimeTypeFor("talk.vtt"))
        assertEquals(SubtitleFormat.SSA, SubtitleFormat.mimeTypeFor("anime.ass"))
        assertNull(SubtitleFormat.mimeTypeFor("notes.txt"))
        assertEquals("en", SubtitleFormat.languageFor("Movie.en.srt"))
        assertEquals("tamil", SubtitleFormat.languageFor("Movie.tamil.srt"))
        assertNull(SubtitleFormat.languageFor("Movie.srt"))
    }
}

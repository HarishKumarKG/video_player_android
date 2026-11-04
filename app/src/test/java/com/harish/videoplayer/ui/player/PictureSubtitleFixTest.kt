package com.harish.videoplayer.ui.player

import com.harish.videoplayer.ui.player.PictureSubtitleFix.Box
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PictureSubtitleFixTest {

    private val cropped = 1920f / 800f // cinema-cropped 1080p rip
    private val full = 1920f / 1080f

    @Test fun `subtitle line on a cropped movie keeps its shape and stays at the bottom`() {
        // PGS line 800 x 80 px at y = 950 on a 1920 x 1080 frame
        val original = Box(left = 560f / 1920, width = 800f / 1920, top = 950f / 1080, height = 80f / 1080)
        val box = PictureSubtitleFix.fit(original, pictureAspect = 80f / 800, viewAspect = cropped)
        assertEquals(80f / 800, box.height, 1e-4f)            // 80 px of 800: true shape
        assertTrue(box.top + box.height <= 0.98f + 1e-4f)      // fully on screen
        assertEquals(original.width, box.width, 1e-5f)        // same width as the file says
    }

    @Test fun `whole-frame DVD picture is shrunk evenly instead of falling off the screen`() {
        // VobSub: 720 x 480 transparent picture of the whole frame, words at its bottom
        val box = PictureSubtitleFix.fit(Box(0f, 1f, 0f, 1f), pictureAspect = 480f / 720, viewAspect = cropped)
        assertEquals(0.96f, box.height, 1e-4f)                 // fits between the margins
        assertTrue(box.top >= 0.02f - 1e-4f && box.top + box.height <= 0.98f + 1e-4f)
        assertEquals(0.5f, box.left + box.width / 2, 1e-4f)    // still centred
        assertTrue(box.width < 1f)
    }

    @Test fun `full-frame video is left exactly as the file says`() {
        val original = Box(0.3f, 0.4f, 0.85f, 0.4f * full * 0.1f)
        val box = PictureSubtitleFix.fit(original, pictureAspect = 0.1f, viewAspect = full)
        assertEquals(original.top, box.top, 1e-4f)
        assertEquals(original.height, box.height, 1e-4f)
        assertEquals(original.left, box.left, 1e-4f)
    }

    @Test fun `top subtitle (signs, songs) stays at the top`() {
        val box = PictureSubtitleFix.fit(Box(0.3f, 0.4f, 0.05f, 0.05f), pictureAspect = 0.1f, viewAspect = cropped)
        assertEquals(0.05f, box.top, 1e-4f)
    }
}

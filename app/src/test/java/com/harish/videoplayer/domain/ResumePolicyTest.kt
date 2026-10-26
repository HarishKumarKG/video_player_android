package com.harish.videoplayer.domain

import com.harish.videoplayer.domain.resume.ResumeEntry
import com.harish.videoplayer.domain.resume.ResumePolicy
import com.harish.videoplayer.domain.resume.recentlyPlayed
import com.harish.videoplayer.video
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumePolicyTest {

    private val twentyMinutes = 20 * 60_000L

    @Test fun `barely started is not remembered`() = assertFalse(ResumePolicy.shouldRemember(3_000, twentyMinutes))

    @Test fun `middle of the video is remembered`() = assertTrue(ResumePolicy.shouldRemember(7 * 60_000L, twentyMinutes))

    @Test fun `last seconds count as finished`() = assertFalse(ResumePolicy.shouldRemember(twentyMinutes - 5_000, twentyMinutes))

    @Test fun `credits of a long film count as finished`() {
        val film = 2 * 60 * 60_000L // 2 hours: 97% is about 3.6 minutes before the end
        assertFalse(ResumePolicy.shouldRemember(film - 3 * 60_000L, film))
        assertTrue(ResumePolicy.shouldRemember(film - 10 * 60_000L, film))
    }

    @Test fun `unknown length is still remembered`() = assertTrue(ResumePolicy.shouldRemember(60_000, 0))

    @Test fun `progress is a fraction`() {
        assertEquals(0.25f, ResumePolicy.progress(5 * 60_000L, twentyMinutes), 0.0001f)
        assertEquals(0f, ResumePolicy.progress(5_000, 0), 0.0001f)
        assertEquals(1f, ResumePolicy.progress(twentyMinutes * 2, twentyMinutes), 0.0001f)
    }

    @Test fun `recently played is newest first and skips finished or deleted videos`() {
        val videos = listOf(video(1), video(2), video(3))
        val entries = mapOf(
            1L to ResumeEntry(positionMs = 30_000, durationMs = 600_000, updatedAtMs = 100),
            2L to ResumeEntry(positionMs = 595_000, durationMs = 600_000, updatedAtMs = 300), // finished
            3L to ResumeEntry(positionMs = 60_000, durationMs = 600_000, updatedAtMs = 200),
            9L to ResumeEntry(positionMs = 60_000, durationMs = 600_000, updatedAtMs = 400)  // file deleted
        )
        assertEquals(listOf(3L, 1L), recentlyPlayed(videos, entries).map { it.id })
    }

    @Test fun `only the latest 10 are listed`() {
        val videos = (1L..15L).map { video(it) }
        val entries = videos.associate { it.id to ResumeEntry(positionMs = 60_000, durationMs = 600_000, updatedAtMs = it.id) }
        val recent = recentlyPlayed(videos, entries)
        assertEquals(10, recent.size)
        assertEquals((15L downTo 6L).toList(), recent.map { it.id })
    }

    @Test fun `nothing saved means nothing recently played`() =
        assertTrue(recentlyPlayed(listOf(video(1)), emptyMap()).isEmpty())
}

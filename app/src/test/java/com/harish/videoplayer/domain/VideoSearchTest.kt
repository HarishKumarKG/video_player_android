package com.harish.videoplayer.domain

import com.harish.videoplayer.domain.library.VideoSearchIndex
import com.harish.videoplayer.domain.library.search
import com.harish.videoplayer.video
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoSearchTest {

    private val videos = listOf(
        video(1, name = "Trip to Ooty.mp4", path = "/storage/emulated/0/DCIM/Camera/Trip to Ooty.mp4"),
        video(2, name = "Café_Night.mkv", path = "/storage/emulated/0/Movies/Café_Night.mkv"),
        video(3, name = "birthday-party.mp4", path = "/storage/emulated/0/WhatsApp/Birthday/birthday-party.mp4")
    )

    @Test fun `matches part of the name, any case`() = assertEquals(listOf(1L), videos.search("ooty").map { it.id })

    @Test fun `accents are ignored`() = assertEquals(listOf(2L), videos.search("cafe").map { it.id })

    @Test fun `words can come from the folder name`() = assertEquals(listOf(1L), videos.search("camera trip").map { it.id })

    @Test fun `underscores and dashes split words`() = assertEquals(listOf(3L), videos.search("party_birthday").map { it.id })

    @Test fun `every word must match`() = assertTrue(videos.search("ooty night").isEmpty())

    @Test fun `blank query finds nothing`() = assertTrue(VideoSearchIndex(videos).search("   ").isEmpty())
}

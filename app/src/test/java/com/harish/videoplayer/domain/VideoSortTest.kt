package com.harish.videoplayer.domain

import com.harish.videoplayer.domain.model.VideoSortOrder
import com.harish.videoplayer.domain.model.sortedByOrder
import com.harish.videoplayer.video
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoSortTest {

    private val videos = listOf(
        video(1, name = "banana.mp4", durationMs = 10_000, sizeBytes = 300, dateModifiedSec = 200),
        video(2, name = "Apple.mkv", durationMs = 90_000, sizeBytes = 100, dateModifiedSec = 100),
        video(3, name = "cherry.mp4", durationMs = 50_000, sizeBytes = 200, dateModifiedSec = 300)
    )

    private fun ids(order: VideoSortOrder) = videos.sortedByOrder(order).map { it.id }

    @Test fun `date modified puts the newest first`() = assertEquals(listOf(3L, 1L, 2L), ids(VideoSortOrder.DATE_MODIFIED))

    @Test fun `name sort ignores upper and lower case`() = assertEquals(listOf(2L, 1L, 3L), ids(VideoSortOrder.NAME_ASC))

    @Test fun `name descending is the reverse`() = assertEquals(listOf(3L, 1L, 2L), ids(VideoSortOrder.NAME_DESC))

    @Test fun `longest first`() = assertEquals(listOf(2L, 3L, 1L), ids(VideoSortOrder.LONGEST))

    @Test fun `largest first`() = assertEquals(listOf(1L, 3L, 2L), ids(VideoSortOrder.LARGEST))

    @Test fun `title drops the extension`() = assertEquals("Apple", videos[1].title)

    @Test fun `folder name is the parent directory`() = assertEquals("Movies", videos[0].folderName)
}

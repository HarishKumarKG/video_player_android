package com.harish.videoplayer.domain

import com.harish.videoplayer.domain.library.breadcrumbs
import com.harish.videoplayer.domain.library.browse
import com.harish.videoplayer.domain.library.browseParent
import com.harish.videoplayer.domain.library.sortedFor
import com.harish.videoplayer.domain.library.storageLabel
import com.harish.videoplayer.domain.library.storageRootOf
import com.harish.videoplayer.domain.library.storageRoots
import com.harish.videoplayer.domain.model.VideoSortOrder
import com.harish.videoplayer.video
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoBrowserTest {

    private val internal = "/storage/emulated/0"
    private val sd = "/storage/1A2B-3C4D"

    private val videos = listOf(
        video(1, path = "$internal/DCIM/Camera/a.mp4", dateModifiedSec = 10),
        video(2, path = "$internal/DCIM/Camera/b.mp4", dateModifiedSec = 20),
        video(3, path = "$internal/DCIM/clip.mp4", dateModifiedSec = 5),
        video(4, path = "$internal/Movies/c.mkv", dateModifiedSec = 99),
        video(5, path = "$sd/Films/d.mp4", dateModifiedSec = 1)
    )

    @Test
    fun `top level lists each storage`() {
        val roots = videos.storageRoots()
        assertEquals(listOf("Internal storage", "SD card (1A2B-3C4D)"), roots.map { it.label })
        assertEquals(4, roots.first().videoCount)
    }

    @Test
    fun `a storage shows its folders and no loose videos`() {
        val listing = videos.browse(internal)
        assertEquals(listOf("DCIM", "Movies"), listing.dirs.map { it.label })
        assertEquals(3, listing.dirs.first { it.label == "DCIM" }.videoCount) // includes Camera inside
        assertTrue(listing.videos.isEmpty())
    }

    @Test
    fun `a folder shows sub-folders and its own videos`() {
        val listing = videos.browse("$internal/DCIM")
        assertEquals(listOf("Camera"), listing.dirs.map { it.label })
        assertEquals(listOf(3L), listing.videos.map { it.id })
    }

    @Test
    fun `trailing slash does not matter`() {
        assertEquals(videos.browse("$internal/DCIM"), videos.browse("$internal/DCIM/"))
    }

    @Test
    fun `going up stops at the storage list`() {
        assertEquals("$internal/DCIM", browseParent("$internal/DCIM/Camera"))
        assertEquals(internal, browseParent("$internal/DCIM"))
        assertNull(browseParent(internal))
        assertNull(browseParent(sd))
    }

    @Test
    fun `breadcrumbs start at the storage`() {
        val steps = breadcrumbs("$internal/DCIM/Camera")
        assertEquals(listOf("Internal storage", "DCIM", "Camera"), steps.map { it.second })
        assertEquals(listOf(internal, "$internal/DCIM", "$internal/DCIM/Camera"), steps.map { it.first })
        assertEquals(listOf("Internal storage"), breadcrumbs(internal).map { it.second })
    }

    @Test
    fun `folders follow the sort`() {
        val dirs = videos.browse(internal).dirs
        assertEquals(listOf("Movies", "DCIM"), dirs.sortedFor(VideoSortOrder.DATE_MODIFIED).map { it.label })
        assertEquals(listOf("Movies", "DCIM"), dirs.sortedFor(VideoSortOrder.NAME_DESC).map { it.label })
        assertEquals(listOf("DCIM", "Movies"), dirs.sortedFor(VideoSortOrder.LONGEST).map { it.label })
    }

    @Test
    fun `storage roots and labels`() {
        assertEquals(internal, storageRootOf("$internal/DCIM/a.mp4"))
        assertEquals(sd, storageRootOf("$sd/Films/d.mp4"))
        assertEquals("Internal storage", storageLabel(internal))
        assertEquals("SD card (1A2B-3C4D)", storageLabel(sd))
    }
}

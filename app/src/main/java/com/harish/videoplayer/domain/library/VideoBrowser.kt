package com.harish.videoplayer.domain.library

import com.harish.videoplayer.domain.model.Video
import com.harish.videoplayer.domain.model.VideoSortOrder

/**
 * A folder shown in the browser.
 * Example: "DCIM" with videoCount = 42 (all videos anywhere inside it, sub-folders included).
 */
data class BrowseDir(
    val path: String,
    val label: String,
    val videoCount: Int,
    val latestModifiedSec: Long, // newest video inside, for "Date modified" sorting
    val isStorageRoot: Boolean
)

/** What one folder contains: sub-folders that lead to videos, plus the videos right here. */
data class BrowseListing(val dirs: List<BrowseDir>, val videos: List<Video>)

private val byNameIgnoringCase = String.CASE_INSENSITIVE_ORDER

/**
 * "/storage/emulated/0/DCIM/a.mp4" -> "/storage/emulated/0"
 * "/storage/1A2B-3C4D/Movies/b.mkv" -> "/storage/1A2B-3C4D"
 */
fun storageRootOf(path: String): String {
    val parts = path.split('/').filter { it.isNotEmpty() }
    return when {
        parts.size >= 3 && parts[0] == "storage" && parts[1] == "emulated" -> "/storage/emulated/${parts[2]}"
        parts.size >= 2 -> "/${parts[0]}/${parts[1]}"
        else -> "/"
    }
}

/** Friendly name for a storage root. */
fun storageLabel(root: String): String = when {
    root == "/storage/emulated/0" -> "Internal storage"
    root.startsWith("/storage/emulated/") -> "Internal storage (user ${root.substringAfterLast('/')})"
    root.startsWith("/storage/") -> "SD card (${root.substringAfterLast('/')})"
    else -> root
}

/** Every storage (internal, SD card...) that has videos. */
fun List<Video>.storageRoots(): List<BrowseDir> =
    groupBy { storageRootOf(it.path) }
        .map { (root, videos) ->
            BrowseDir(root, storageLabel(root), videos.size, videos.maxOf { it.dateModifiedSec }, isStorageRoot = true)
        }
        .sortedBy { it.label }

/**
 * What's inside [dir]. Only folders that (somewhere below) contain videos are shown,
 * so you never open an empty folder.
 *
 * Example: browsing "/storage/emulated/0" with videos in DCIM/Camera and Movies
 * gives dirs = [DCIM (has Camera inside), Movies] and no videos (none sit directly in the root).
 */
fun List<Video>.browse(dir: String): BrowseListing {
    val prefix = dir.trimEnd('/') + "/"
    val (here, deeper) = filter { it.path.startsWith(prefix) }
        .partition { '/' !in it.path.removePrefix(prefix) }
    val dirs = deeper
        .groupBy { prefix + it.path.removePrefix(prefix).substringBefore('/') }
        .map { (path, videos) ->
            BrowseDir(
                path = path,
                label = path.substringAfterLast('/'),
                videoCount = videos.size,
                latestModifiedSec = videos.maxOf { it.dateModifiedSec },
                isStorageRoot = false
            )
        }
        .sortedWith(compareBy(byNameIgnoringCase) { it.label })
    return BrowseListing(dirs, here)
}

/** One level up. A storage root goes back to the storage list (null). */
fun browseParent(dir: String): String? =
    if (dir == storageRootOf("$dir/x")) null else dir.substringBeforeLast('/')

/**
 * The path as tappable steps, starting at the storage.
 * "/storage/emulated/0/DCIM/Camera" -> [Internal storage, DCIM, Camera] with their full paths.
 */
fun breadcrumbs(dir: String): List<Pair<String, String>> {
    val root = storageRootOf("$dir/x")
    val steps = mutableListOf(root to storageLabel(root))
    var path = root
    dir.removePrefix(root).split('/').filter { it.isNotEmpty() }.forEach { name ->
        path = "$path/$name"
        steps += path to name
    }
    return steps
}

/** Folders follow the chosen sort where it makes sense; otherwise they stay A–Z. */
fun List<BrowseDir>.sortedFor(order: VideoSortOrder): List<BrowseDir> = when (order) {
    VideoSortOrder.DATE_MODIFIED -> sortedByDescending { it.latestModifiedSec }
    VideoSortOrder.NAME_DESC -> sortedWith(compareByDescending(byNameIgnoringCase) { it.label })
    else -> sortedWith(compareBy(byNameIgnoringCase) { it.label })
}

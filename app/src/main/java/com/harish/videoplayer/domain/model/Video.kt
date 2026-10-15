package com.harish.videoplayer.domain.model

/** One video file read from MediaStore. */
data class Video(
    val id: Long,
    val name: String,          // file name with extension, e.g. "Trip to Ooty.mp4"
    val durationMs: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val mimeType: String,
    val dateModifiedSec: Long, // seconds since epoch (MediaStore.DATE_MODIFIED)
    val path: String
) {
    /** "Trip to Ooty.mp4" -> "Trip to Ooty" */
    val title: String get() = name.substringBeforeLast('.').ifBlank { name }

    /** Name of the folder the file is in, e.g. "Camera". */
    val folderName: String get() = path.substringBeforeLast('/', "").substringAfterLast('/')
}

/** Names of the other videos in the same folder as [video], so a rename can't clash with one. */
fun List<Video>.otherNamesInFolderOf(video: Video): List<String> {
    val folder = video.path.substringBeforeLast('/')
    return filter { it.id != video.id && it.path.substringBeforeLast('/') == folder }.map { it.name }
}

/** Sort options shown in the top-right menu. */
enum class VideoSortOrder(val label: String) {
    DATE_MODIFIED("Date modified"),
    NAME_ASC("Name (A–Z)"),
    NAME_DESC("Name (Z–A)"),
    LONGEST("Longest first"),
    LARGEST("Largest first")
}

fun List<Video>.sortedByOrder(order: VideoSortOrder): List<Video> = when (order) {
    VideoSortOrder.DATE_MODIFIED -> sortedByDescending { it.dateModifiedSec } // newest first
    VideoSortOrder.NAME_ASC -> sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    VideoSortOrder.NAME_DESC -> sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.title })
    VideoSortOrder.LONGEST -> sortedByDescending { it.durationMs }
    VideoSortOrder.LARGEST -> sortedByDescending { it.sizeBytes }
}

package com.harish.videoplayer.domain.model

import java.util.Locale

/** 75_000 -> "1:15", 3_725_000 -> "1:02:05" */
fun formatDuration(ms: Long): String {
    val totalSeconds = ms.coerceAtLeast(0) / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    else String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
}

/** 900 -> "900 B", 250_000 -> "244 KB", 256_901_120 -> "245 MB", 1_288_490_189 -> "1.2 GB" */
fun formatSize(bytes: Long): String {
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    return when {
        bytes >= gb -> String.format(Locale.ROOT, "%.1f GB", bytes / gb)
        bytes >= mb -> String.format(Locale.ROOT, "%.0f MB", bytes / mb)
        bytes >= kb -> String.format(Locale.ROOT, "%.0f KB", bytes / kb)
        else -> "$bytes B"
    }
}

/**
 * Quality label from the frame size, using the SHORT side so portrait phone videos
 * (1080 x 1920) are still "1080p". Returns null when the size is unknown.
 */
fun resolutionLabel(width: Int, height: Int): String? {
    val short = minOf(width, height)
    return when {
        short <= 0 -> null
        short >= 2160 -> "4K"
        short >= 1440 -> "1440p"
        short >= 1080 -> "1080p"
        short >= 720 -> "720p"
        short >= 480 -> "480p"
        else -> "${short}p"
    }
}

/** 1 -> "1 video", 5 -> "5 videos" */
fun videoCount(n: Int) = if (n == 1) "1 video" else "$n videos"

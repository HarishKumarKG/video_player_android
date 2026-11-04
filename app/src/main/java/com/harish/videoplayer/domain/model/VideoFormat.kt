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
 * Quality label from the frame size, the way people name it: 480p, 720p, 1080p, 2K, 4K, 8K.
 * Returns null when the size is unknown.
 *
 * Both sides count, because movies are often cropped to cinema shape: a 1080p Blu-ray rip
 * is usually 1920 x 800 (the black bars cut off). Looking only at the 800 called it "720p";
 * its 1920 width says 1080p. Portrait phone videos (1080 x 1920) work the same way.
 * A little slack (3%) covers odd sizes like 1916 x 1076.
 */
fun resolutionLabel(width: Int, height: Int): String? {
    val long = maxOf(width, height)
    val short = minOf(width, height)
    if (short <= 0) return null
    fun atLeast(longSide: Int, shortSide: Int) = long >= longSide * 0.97 || short >= shortSide * 0.97
    return when {
        atLeast(7680, 4320) -> "8K"
        atLeast(3840, 2160) -> "4K"
        atLeast(2560, 1440) -> "2K"
        atLeast(1920, 1080) -> "1080p"
        atLeast(1280, 720) -> "720p"
        atLeast(854, 480) -> "480p"
        else -> "${short}p"
    }
}

/** 1 -> "1 video", 5 -> "5 videos" */
fun videoCount(n: Int) = if (n == 1) "1 video" else "$n videos"

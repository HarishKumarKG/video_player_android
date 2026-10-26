package com.harish.videoplayer.domain.resume

import com.harish.videoplayer.domain.model.Video

/** Where you stopped watching a video. */
data class ResumeEntry(
    val positionMs: Long,
    val durationMs: Long,
    val updatedAtMs: Long // wall clock time it was saved, for "Recently played" order
) {
    /** 0f..1f, how much has been watched (for the red bar under thumbnails). */
    val progress: Float get() = ResumePolicy.progress(positionMs, durationMs)
}

/**
 * Rules for "continue from where I stopped".
 *
 * Example with a 20-minute video:
 *  - stopped at 0:03  -> not saved (you barely started)
 *  - stopped at 7:30  -> saved, next time it starts at 7:30
 *  - stopped at 19:55 -> not saved (you basically finished it, so it starts fresh)
 */
object ResumePolicy {
    /** Positions before this are treated as "not really started". */
    const val MIN_POSITION_MS = 5_000L

    /** Stopping within the last few seconds counts as finished. */
    const val END_MARGIN_MS = 10_000L

    /** ...or after 97% of the video (for long films where credits run for minutes). */
    const val FINISHED_FRACTION = 0.97f

    fun shouldRemember(positionMs: Long, durationMs: Long): Boolean {
        if (positionMs < MIN_POSITION_MS) return false
        if (durationMs <= 0) return true // unknown length: keep it
        return positionMs < durationMs - END_MARGIN_MS && positionMs < durationMs * FINISHED_FRACTION
    }

    fun progress(positionMs: Long, durationMs: Long): Float =
        if (durationMs <= 0) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}

/**
 * "Recently played": videos you stopped halfway, the one you watched most recently first.
 * Finished videos are never saved (see [ResumePolicy]), so they never appear here.
 * Saved positions for videos that no longer exist on the phone are skipped.
 *
 * Only the latest [limit] (10) are listed, so the list stays short and useful. Older videos
 * still remember their spot — they just aren't shown here.
 */
const val RECENTLY_PLAYED_LIMIT = 10

fun recentlyPlayed(
    videos: List<Video>,
    entries: Map<Long, ResumeEntry>,
    limit: Int = RECENTLY_PLAYED_LIMIT
): List<Video> {
    if (entries.isEmpty()) return emptyList()
    val byId = videos.associateBy { it.id }
    return entries.entries
        .filter { ResumePolicy.shouldRemember(it.value.positionMs, it.value.durationMs) }
        .sortedByDescending { it.value.updatedAtMs }
        .mapNotNull { byId[it.key] }
        .take(limit)
}

package com.harish.videoplayer.domain.library

import com.harish.videoplayer.domain.model.Video
import java.text.Normalizer
import java.util.Locale

/**
 * Pre-built search data for a video list.
 *
 * Building it normalizes every video's text ONCE (lower case, accents removed); after that each
 * search is just fast `contains` checks. Build it off the main thread for big libraries.
 *
 * Matching rules:
 * - looks in the file name and the folder name
 * - ignores upper/lower case and accents ("cafe" finds "Café")
 * - every word must match somewhere, in any order: "ooty camera" finds "Trip to Ooty.mp4"
 *   inside the "Camera" folder
 */
class VideoSearchIndex(videos: List<Video>) {

    private val entries: List<Pair<Video, String>> = videos.map { video ->
        video to normalize("${video.name} ${video.folderName}")
    }

    /** Videos matching [query], in the original list order. */
    fun search(query: String): List<Video> {
        val words = normalize(query).split(' ', '_', '-', '.').filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        return entries.mapNotNull { (video, text) -> video.takeIf { words.all { it in text } } }
    }

    private companion object {
        private val accents = Regex("\\p{Mn}+")

        fun normalize(text: String): String =
            Normalizer.normalize(text, Normalizer.Form.NFD)
                .replace(accents, "") // strip accents
                .lowercase(Locale.ROOT)
    }
}

/** One-off search (builds a throw-away index). Prefer keeping a [VideoSearchIndex] for repeated searches. */
fun List<Video>.search(query: String): List<Video> = VideoSearchIndex(this).search(query)

package com.harish.videoplayer.domain.subtitle

import java.util.Locale

/**
 * Picks the subtitle format from the file name, so the player knows how to read it.
 * The strings are the MIME types Media3 understands (MimeTypes.APPLICATION_SUBRIP etc.).
 */
object SubtitleFormat {
    const val SRT = "application/x-subrip"
    const val VTT = "text/vtt"
    const val SSA = "text/x-ssa"
    const val TTML = "application/ttml+xml"

    /** "Movie.en.srt" -> SRT; "notes.txt" -> null (not a subtitle file we know). */
    fun mimeTypeFor(fileName: String): String? =
        when (fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
            "srt" -> SRT
            "vtt" -> VTT
            "ass", "ssa" -> SSA
            "ttml", "dfxp", "xml" -> TTML
            else -> null
        }

    /**
     * Language hidden in the file name, if any: "Movie.en.srt" -> "en", "Movie.tamil.srt" -> "tamil",
     * "Movie.srt" -> null.
     */
    fun languageFor(fileName: String): String? {
        val parts = fileName.split('.')
        if (parts.size < 3) return null
        return parts[parts.size - 2].takeIf { it.length in 2..10 && it.all(Char::isLetter) }
    }
}

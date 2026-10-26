package com.harish.videoplayer.cast

/** The formats inside a video file, as MIME types in the file's track order (first = default). */
data class MediaFormats(
    val video: List<String>,
    val audio: List<String>
)

/** Why a video might not play on the TV, shown before casting so you can decide. */
data class CastWarning(
    /** e.g. "No sound on the TV" */
    val headline: String,
    /** One plain sentence per problem found. */
    val reasons: List<String>
)

/**
 * Checks a video's formats against what Google Cast (Chromecast / Google TV) can play,
 * BEFORE sending it, so you get a clear reason instead of a failed or silent video.
 *
 * Example: a Blu-ray rip "Movie.mkv" with HEVC video + TrueHD Atmos sound
 *   -> video is fine, sound isn't -> "No sound on the TV: the sound is Dolby TrueHD, which
 *      Chromecast can't play".
 *
 * Only formats we KNOW are a problem produce a warning; anything unknown is let through, so
 * a good file never gets a false alarm. DLNA TVs decode with their own chip and differ a lot
 * between brands, so they aren't checked.
 */
object CastCompatibility {

    private val playableVideo = setOf(
        "video/avc", "video/hevc", "video/dolby-vision",
        "video/x-vnd.on2.vp8", "video/x-vnd.on2.vp9", "video/av01"
    )
    private val playableAudio = setOf(
        "audio/mp4a-latm", "audio/mpeg", "audio/opus", "audio/vorbis", "audio/flac",
        "audio/ac3", "audio/eac3", "audio/eac3-joc", "audio/raw"
    )

    /** Formats Chromecast can't play, with the name people know them by. */
    private val knownBad = mapOf(
        "video/mp4v-es" to "MPEG-4 Part 2 (DivX / Xvid)",
        "video/mpeg2" to "MPEG-2",
        "video/3gpp" to "H.263",
        "video/wvc1" to "VC-1",
        "video/x-ms-wmv" to "Windows Media Video",
        "audio/true-hd" to "Dolby TrueHD",
        "audio/vnd.dts" to "DTS",
        "audio/vnd.dts.hd" to "DTS-HD",
        "audio/vnd.dts.uhd;profile=p2" to "DTS:X",
        "audio/ac4" to "Dolby AC-4",
        "audio/x-ms-wma" to "Windows Media Audio",
        "audio/alac" to "Apple Lossless (ALAC)",
        "audio/mpeg-l2" to "MP2",
        "audio/3gpp" to "AMR",
        "audio/amr-wb" to "AMR-WB",
        "audio/mha1" to "MPEG-H",
        "audio/mhm1" to "MPEG-H"
    )

    /** Friendly names for the playable ones (used when suggesting another sound track). */
    private val names = mapOf(
        "audio/mp4a-latm" to "AAC", "audio/mpeg" to "MP3", "audio/opus" to "Opus",
        "audio/vorbis" to "Vorbis", "audio/flac" to "FLAC", "audio/ac3" to "Dolby Digital (AC3)",
        "audio/eac3" to "Dolby Digital Plus (EAC3)", "audio/eac3-joc" to "Dolby Atmos (EAC3)",
        "audio/raw" to "PCM"
    )

    private fun String.base() = lowercase().trim()
    private fun isBad(mime: String) = mime.base() in knownBad
    private fun nameOf(mime: String) = knownBad[mime.base()] ?: names[mime.base()] ?: mime

    /** null = nothing wrong that we know of: cast straight away. */
    fun check(kind: CastKind, formats: MediaFormats, tvName: String = "the TV"): CastWarning? {
        if (kind != CastKind.GOOGLE_CAST) return null

        val videoProblem = formats.video.firstOrNull()?.takeIf(::isBad)
        val firstAudio = formats.audio.firstOrNull()
        val audioProblem = firstAudio?.takeIf(::isBad)
        if (videoProblem == null && audioProblem == null) return null

        val reasons = mutableListOf<String>()
        if (videoProblem != null) {
            reasons += "The picture is ${nameOf(videoProblem)}, which Chromecast can't play."
        }
        if (audioProblem != null) {
            reasons += "The sound is ${nameOf(audioProblem)}, which Chromecast can't play."
            val other = formats.audio.drop(1).firstOrNull { it.base() in playableAudio }
            if (other != null) {
                reasons += "The file also has a ${nameOf(other)} sound track, but Chromecast always " +
                    "uses the first one."
            }
        }
        val headline = when {
            videoProblem != null -> "This video won't play on $tvName"
            else -> "No sound on $tvName"
        }
        return CastWarning(headline, reasons)
    }

    /** A short reason for a video the TV just refused, or null if its formats look fine. */
    fun explainFailure(formats: MediaFormats): String? =
        check(CastKind.GOOGLE_CAST, formats)?.reasons?.firstOrNull()

    /** Shown under every warning: what always works. */
    const val SAFE_FORMATS = "Always works: MP4 with H.264 or HEVC picture and AAC, AC3 or EAC3 sound."
}

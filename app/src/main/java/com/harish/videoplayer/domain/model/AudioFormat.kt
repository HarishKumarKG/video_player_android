package com.harish.videoplayer.domain.model

/**
 * Friendly names for audio formats, so the audio track menu can say
 * "Tamil · Dolby Digital · 5.1" instead of "audio/ac3, 6 channels".
 */
fun audioCodecLabel(mimeType: String?): String? = when (mimeType) {
    null -> null
    "audio/mp4a-latm" -> "AAC"
    "audio/ac3" -> "Dolby Digital"
    "audio/eac3", "audio/eac3-joc" -> "Dolby Digital Plus"
    "audio/ac4" -> "Dolby AC-4"
    "audio/true-hd" -> "Dolby TrueHD"
    "audio/vnd.dts", "audio/vnd.dts.hd", "audio/vnd.dts.hd;profile=lbr", "audio/vnd.dts.uhd;profile=p2" -> "DTS"
    "audio/mpeg", "audio/mpeg-L1", "audio/mpeg-L2" -> "MP3"
    "audio/opus" -> "Opus"
    "audio/vorbis" -> "Vorbis"
    "audio/flac" -> "FLAC"
    "audio/alac" -> "ALAC"
    "audio/raw" -> "PCM"
    "audio/3gpp", "audio/amr-wb" -> "AMR"
    else -> mimeType.removePrefix("audio/").uppercase()
}

/** 1 -> "Mono", 2 -> "Stereo", 3 -> "2.1", 6 -> "5.1", 8 -> "7.1"; unknown -> null */
fun channelLayoutLabel(channelCount: Int): String? = when (channelCount) {
    1 -> "Mono"
    2 -> "Stereo"
    3 -> "2.1"
    4 -> "4.0"
    5 -> "5.0"
    6 -> "5.1"
    7 -> "6.1"
    8 -> "7.1"
    else -> null
}

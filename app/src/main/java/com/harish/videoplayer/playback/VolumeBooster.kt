package com.harish.videoplayer.playback

/**
 * Volume above 100% (up to 200%, like VLC) for quiet videos.
 *
 * The phone's volume stops at its maximum, so on top of that the player amplifies its own
 * sound ([BoostAudioProcessor]). Works the same for stereo and 5.1 / 7.1 movies.
 *
 * Example: boost 1.5 = 150%; boost 2.0 = 200% (twice as loud, peaks softly limited).
 */
class VolumeBooster(private val processor: BoostAudioProcessor) {
    /** 1f = normal (100%) .. 2f = 200% */
    var boost: Float = 1f
        private set

    fun set(level: Float) {
        boost = level.coerceIn(1f, MAX_BOOST)
        processor.gain = boost
    }

    fun reset() = set(1f)

    companion object {
        const val MAX_BOOST = 2f
    }
}

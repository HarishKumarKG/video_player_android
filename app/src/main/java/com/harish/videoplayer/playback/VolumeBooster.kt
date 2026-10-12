package com.harish.videoplayer.playback

import android.media.audiofx.LoudnessEnhancer
import android.util.Log
import kotlin.math.log10

/**
 * Volume above 100% (up to 200%, like VLC) for quiet videos.
 *
 * The phone's volume stops at its maximum, so on top of that we amplify the player's own sound
 * with Android's LoudnessEnhancer. It only affects this app's audio session.
 *
 * Example: boost 1.5 = 150% = about +3.5 dB; boost 2.0 = 200% = about +6 dB (twice as loud).
 * Very loud boosts can distort, the same as in VLC.
 */
class VolumeBooster {
    /** 1f = normal (100%) .. 2f = 200% */
    var boost: Float = 1f
        private set

    private var enhancer: LoudnessEnhancer? = null
    private var sessionId: Int = 0

    fun set(level: Float, audioSessionId: Int) {
        boost = level.coerceIn(1f, MAX_BOOST)
        apply(audioSessionId)
    }

    /** The player started a new audio session (new video): move the boost over to it. */
    fun onSessionChanged(audioSessionId: Int) {
        release()
        apply(audioSessionId)
    }

    fun reset() {
        boost = 1f
        release()
    }

    private fun apply(audioSessionId: Int) {
        if (boost <= 1f) {
            enhancer?.enabled = false
            return
        }
        if (audioSessionId <= 0) return
        if (enhancer == null || sessionId != audioSessionId) {
            release()
            enhancer = try {
                LoudnessEnhancer(audioSessionId)
            } catch (e: Exception) {
                Log.w("VolumeBooster", "Volume boost not available on this phone", e)
                null
            }
            sessionId = audioSessionId
        }
        enhancer?.apply {
            setTargetGain((2000 * log10(boost)).toInt()) // millibels: 20·log10(gain) dB × 100
            enabled = true
        }
    }

    private fun release() {
        enhancer?.release()
        enhancer = null
    }

    companion object {
        const val MAX_BOOST = 2f
    }
}

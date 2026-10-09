package com.harish.videoplayer.playback

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The ONE ExoPlayer of the app.
 *
 * The player screen draws its picture, and PlaybackService wraps it in a MediaSession for the
 * notification. Sharing a single instance means leaving the screen (Home button, PiP, screen off)
 * never restarts the video: the same player simply keeps going.
 *
 * Use it from the main thread only (ExoPlayer's rule).
 */
@Singleton
class PlayerHolder @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var instance: ExoPlayer? = null

    val player: ExoPlayer
        get() = instance ?: create().also { instance = it }

    private fun create(): ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            /* handleAudioFocus = */ true // pause for calls / other apps, resume after
        )
        .setHandleAudioBecomingNoisy(true) // headphones unplugged -> pause
        .setWakeMode(C.WAKE_MODE_LOCAL)    // keep the CPU awake for background audio
        .setSeekBackIncrementMs(SKIP_MS)
        .setSeekForwardIncrementMs(SKIP_MS)
        .build()

    companion object {
        /** The ⏪ / ⏩ buttons and double-tap jump by this much. */
        const val SKIP_MS = 10_000L
    }
}

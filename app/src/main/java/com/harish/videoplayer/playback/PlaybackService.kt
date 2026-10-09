package com.harish.videoplayer.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.harish.videoplayer.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Media3 session service. Media3 does the heavy lifting for us:
 *  - shows the media notification (title, thumbnail, ⏮ ⏯ ⏭ and a draggable seek bar)
 *  - turns the service into a foreground service while playing, so Android doesn't kill
 *    background audio
 *  - handles headset buttons, Bluetooth controls and the lock screen
 */
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject lateinit var playerHolder: PlayerHolder

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate() // Hilt injects here
        // Tapping the notification brings the app (and the player screen) back
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        session = MediaSession.Builder(this, playerHolder.player)
            .setSessionActivity(openApp)
            .setBitmapLoader(VideoThumbnailBitmapLoader(this))
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** App swiped away from Recents: stop the video (it is a video player, not a radio). */
    override fun onTaskRemoved(rootIntent: Intent?) {
        playerHolder.player.pause()
        stopSelf()
    }

    override fun onDestroy() {
        // The player itself is shared (PlayerHolder) and stays alive for the next video
        session?.release()
        session = null
        super.onDestroy()
    }
}

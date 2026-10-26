package com.harish.videoplayer.cast

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.IBinder
import android.os.PowerManager
import com.harish.videoplayer.MainActivity
import com.harish.videoplayer.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Keeps casting alive while the phone is locked or you're in another app.
 *
 * The TV keeps downloading the video from the phone, so Android must not freeze the app:
 * this foreground service shows "Playing on Living Room TV" with a Stop button, holds a
 * high-performance Wi-Fi lock (4K/8K need a steady 50-100+ Mbit/s) and keeps the CPU awake.
 */
@AndroidEntryPoint
class CastService : Service() {

    @Inject lateinit var castManager: CastManager

    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            castManager.stopCasting()
            return START_NOT_STICKY
        }
        val tvName = intent?.getStringExtra(EXTRA_TV) ?: "TV"
        startForeground(NOTIFICATION_ID, notification(tvName), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        acquireLocks()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        wifiLock?.takeIf { it.isHeld }?.release()
        wakeLock?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }

    private fun acquireLocks() {
        if (wifiLock == null) {
            wifiLock = getSystemService(WifiManager::class.java)
                ?.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "video-player:cast")
                ?.apply { setReferenceCounted(false); acquire() }
        }
        if (wakeLock == null) {
            wakeLock = getSystemService(PowerManager::class.java)
                ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "video-player:cast")
                ?.apply { setReferenceCounted(false); acquire(6 * 60 * 60 * 1000L) } // a long movie, at most
        }
    }

    private fun notification(tvName: String): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Casting to TV", NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, CastService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.baseline_cast_connected_24)
            .setContentTitle("Playing on $tvName")
            .setContentText("The video streams from this phone. Keep Wi-Fi on.")
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Stop casting", stop).build())
            .build()
    }

    companion object {
        private const val CHANNEL = "casting"
        private const val NOTIFICATION_ID = 42
        private const val ACTION_STOP = "com.harish.videoplayer.cast.STOP"
        private const val EXTRA_TV = "tv"

        fun start(context: Context, tvName: String) {
            context.startForegroundService(Intent(context, CastService::class.java).putExtra(EXTRA_TV, tvName))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CastService::class.java))
        }
    }
}

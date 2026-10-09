package com.harish.videoplayer.ui.player

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import com.harish.videoplayer.R

/** True while the app is shrunk into the floating picture-in-picture window. */
@Composable
fun rememberIsInPipMode(): Boolean {
    val activity = LocalActivity.current as? ComponentActivity ?: return false
    var inPip by remember { mutableStateOf(activity.isInPictureInPictureMode) }
    DisposableEffect(activity) {
        val listener = Consumer<PictureInPictureModeChangedInfo> { inPip = it.isInPictureInPictureMode }
        activity.addOnPictureInPictureModeChangedListener(listener)
        onDispose { activity.removeOnPictureInPictureModeChangedListener(listener) }
    }
    return inPip
}

/** Buttons shown inside the floating window. */
private enum class PipControl(@DrawableRes val icon: Int, val title: String) {
    REWIND(R.drawable.baseline_replay_10_24, "Back 10 seconds"),
    PLAY(R.drawable.baseline_play_arrow_24, "Play"),
    PAUSE(R.drawable.baseline_pause_24, "Pause"),
    FORWARD(R.drawable.baseline_forward_10_24, "Forward 10 seconds")
}

private const val ACTION_PIP_CONTROL = "com.harish.videoplayer.action.PIP_CONTROL"
private const val EXTRA_CONTROL = "control"

/**
 * Wires the player screen to Android's picture-in-picture:
 *  - keeps the window's shape matched to the video (16:9, 9:16, ...)
 *  - [autoEnter]: pressing Home while playing shrinks the video automatically
 *    (Android 12+ does it natively; on Android 11 we do it from the "user is leaving" hint)
 *  - adds ⏪ ⏯ ⏩ buttons to the floating window
 *
 * Returns a function that enters picture-in-picture right now (for the PiP button),
 * or null when this phone has no picture-in-picture (the button is then hidden).
 */
@Composable
fun PictureInPictureEffect(
    autoEnter: Boolean,
    isPlaying: Boolean,
    videoWidth: Int,
    videoHeight: Int,
    onPlayPause: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit
): (() -> Boolean)? {
    val activity = LocalActivity.current as? ComponentActivity ?: return null
    val supported = remember(activity) {
        activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }
    if (!supported) return null

    val currentPlayPause by rememberUpdatedState(onPlayPause)
    val currentRewind by rememberUpdatedState(onRewind)
    val currentForward by rememberUpdatedState(onForward)

    // Taps on the floating window's buttons arrive here as broadcasts from our own PendingIntents
    DisposableEffect(activity) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (PipControl.entries.getOrNull(intent.getIntExtra(EXTRA_CONTROL, -1))) {
                    PipControl.REWIND -> currentRewind()
                    PipControl.PLAY, PipControl.PAUSE -> currentPlayPause()
                    PipControl.FORWARD -> currentForward()
                    null -> Unit
                }
            }
        }
        ContextCompat.registerReceiver(
            activity, receiver, IntentFilter(ACTION_PIP_CONTROL), ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose { activity.unregisterReceiver(receiver) }
    }

    val params = remember(autoEnter, isPlaying, videoWidth, videoHeight) {
        buildParams(activity, autoEnter && isPlaying, isPlaying, videoWidth, videoHeight)
    }
    val currentParams by rememberUpdatedState(params)
    val currentAutoEnter by rememberUpdatedState(autoEnter && isPlaying)

    DisposableEffect(activity, params) {
        activity.setPictureInPictureParams(params)
        onDispose { }
    }

    // Android 11 has no auto-enter: do it ourselves when the user presses Home
    DisposableEffect(activity) {
        val onLeave = Runnable {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && currentAutoEnter) {
                activity.enterPictureInPictureMode(currentParams)
            }
        }
        activity.addOnUserLeaveHintListener(onLeave)
        onDispose {
            activity.removeOnUserLeaveHintListener(onLeave)
            // Leaving the player: Home must no longer shrink the (now closed) video
            activity.setPictureInPictureParams(buildParams(activity, false, false, 0, 0))
        }
    }

    return remember(activity) {
        { runCatching { activity.enterPictureInPictureMode(currentParams) }.getOrDefault(false) }
    }
}

private fun buildParams(
    activity: ComponentActivity,
    autoEnter: Boolean,
    isPlaying: Boolean,
    videoWidth: Int,
    videoHeight: Int
): PictureInPictureParams {
    val builder = PictureInPictureParams.Builder()
    if (videoWidth > 0 && videoHeight > 0) builder.setAspectRatio(clampedRatio(videoWidth, videoHeight))
    builder.setActions(
        listOf(
            remoteAction(activity, PipControl.REWIND),
            remoteAction(activity, if (isPlaying) PipControl.PAUSE else PipControl.PLAY),
            remoteAction(activity, PipControl.FORWARD)
        )
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        builder.setAutoEnterEnabled(autoEnter)
        builder.setSeamlessResizeEnabled(true)
    }
    return builder.build()
}

/** Android only accepts ratios between 1:2.39 and 2.39:1 (a very wide film would crash otherwise). */
private fun clampedRatio(width: Int, height: Int): Rational {
    val ratio = width.toFloat() / height
    return when {
        ratio > 2.39f -> Rational(239, 100)
        ratio < 1 / 2.39f -> Rational(100, 239)
        else -> Rational(width, height)
    }
}

private fun remoteAction(context: Context, control: PipControl): RemoteAction {
    val intent = Intent(ACTION_PIP_CONTROL)
        .setPackage(context.packageName)
        .putExtra(EXTRA_CONTROL, control.ordinal)
    val pending = PendingIntent.getBroadcast(
        context,
        control.ordinal, // different request code per button, or Android would reuse one intent
        intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    return RemoteAction(Icon.createWithResource(context, control.icon), control.title, control.title, pending)
}

package com.harish.videoplayer.ui.player

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Full-screen video: hides the status and navigation bars while the player is open.
 * A swipe from the edge shows them for a moment; they hide again by themselves.
 */
@Composable
fun ImmersiveModeEffect(enabled: Boolean) {
    val activity = LocalActivity.current ?: return
    DisposableEffect(activity, enabled) {
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        if (enabled) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

/** Stops the screen from going dark while the video plays (and lets it sleep when paused). */
@Composable
fun KeepScreenOnEffect(keepOn: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, keepOn) {
        view.keepScreenOn = keepOn
        onDispose { view.keepScreenOn = false }
    }
}

/**
 * Swipe-up/down brightness on the left side. Only THIS window gets brighter; the phone's own
 * brightness setting is left alone and comes back as soon as you close the player.
 */
class BrightnessController(private val activity: Activity) {
    /** 0f..1f */
    var level: Float = currentLevel()
        private set

    private fun currentLevel(): Float {
        val own = activity.window.attributes.screenBrightness
        if (own >= 0f) return own
        // Not overridden yet: start from the system brightness (0..255)
        val system = Settings.System.getInt(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
        return (system / 255f).coerceIn(0f, 1f)
    }

    fun change(by: Float) {
        level = (level + by).coerceIn(0.01f, 1f)
        val params = activity.window.attributes
        params.screenBrightness = level
        activity.window.attributes = params
    }

    fun reset() {
        val params = activity.window.attributes
        params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        activity.window.attributes = params
    }
}

@Composable
fun rememberBrightnessController(): BrightnessController? {
    val activity = LocalActivity.current ?: return null
    val controller = remember(activity) { BrightnessController(activity) }
    DisposableEffect(controller) { onDispose { controller.reset() } }
    return controller
}

/**
 * Swipe-up/down volume on the right side. Works in steps of the phone's media volume,
 * but remembers the fraction in between so slow swipes still move it.
 */
class VolumeController(context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    private var exact = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()

    /** 0f..1f */
    val level: Float get() = exact / max

    fun change(by: Float) {
        exact = (exact + by * max).coerceIn(0f, max.toFloat())
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, exact.toInt(), 0)
    }

    /** Re-reads the volume (it may have changed with the phone's buttons). */
    fun sync() {
        exact = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
    }
}

@Composable
fun rememberVolumeController(): VolumeController {
    val context = LocalContext.current
    return remember(context) { VolumeController(context.applicationContext) }
}

/** Rotate button: landscape <-> portrait. Closing the player gives control back to the phone. */
@Composable
fun OrientationResetEffect() {
    val activity = LocalActivity.current ?: return
    DisposableEffect(activity) {
        onDispose { activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
}

fun Activity.setLandscape(landscape: Boolean) {
    requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                           else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
}

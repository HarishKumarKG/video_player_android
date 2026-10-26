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
class VolumeController(
    context: Context,
    private val getBoost: () -> Float,
    private val setBoost: (Float) -> Unit
) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    private var exact = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()

    /**
     * 0f..2f, like VLC: 0..1 is the phone's own volume (0-100%), 1..2 is the extra boost
     * (100-200%) that only kicks in once the phone is already at maximum.
     */
    val level: Float get() = if (getBoost() > 1f) getBoost() else exact / max

    /**
     * Swiping up past 100% boosts; swiping down first removes the boost, then lowers the
     * phone's volume. [by] is a fraction of the full 0-100% range.
     */
    fun change(by: Float) {
        var delta = by
        val boost = getBoost()
        if (delta < 0 && boost > 1f) {
            val newBoost = boost + delta
            if (newBoost >= 1f) { setBoost(newBoost); return }
            setBoost(1f)
            delta = newBoost - 1f // what's left lowers the phone volume
        }
        val wanted = exact + delta * max
        exact = wanted.coerceIn(0f, max.toFloat())
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, exact.toInt(), 0)
        if (wanted > max) setBoost(getBoost() + (wanted - max) / max) // over the top -> boost
    }

    /** Re-reads the volume (it may have changed with the phone's buttons). */
    fun sync() {
        exact = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
    }
}

@Composable
fun rememberVolumeController(getBoost: () -> Float, setBoost: (Float) -> Unit): VolumeController {
    val context = LocalContext.current
    return remember(context) { VolumeController(context.applicationContext, getBoost, setBoost) }
}

/** Closing the player gives orientation control back to the phone. */
@Composable
fun OrientationResetEffect() {
    val activity = LocalActivity.current ?: return
    DisposableEffect(activity) {
        onDispose { activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
}

/**
 * How the player screen turns.
 *  AUTO      -> follows the phone: hold it sideways for landscape, upright for portrait.
 *               Like VLC / MX Player this uses the motion sensor directly, so it works even
 *               when the phone's own auto-rotate is switched off (that switch keeps the rest
 *               of the phone still; while watching you usually want the video to turn).
 *  LANDSCAPE / PORTRAIT -> locked by the rotate button, turning the phone does nothing
 *  LOCKED    -> the lock-rotation button: stays exactly the way it is right now
 *               (e.g. on a bus or lying in bed, bumps and tilts never turn the video)
 */
enum class OrientationMode { AUTO, LANDSCAPE, PORTRAIT, LOCKED }

/**
 * Remembers the lock-rotation button while the app is open, so the next video
 * you open stays locked too (it starts in whatever way you're holding the phone).
 */
object OrientationMemory {
    var locked = false
}

fun Activity.applyOrientation(mode: OrientationMode) {
    requestedOrientation = when (mode) {
        OrientationMode.AUTO -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        OrientationMode.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        OrientationMode.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        // Holds the current way up, even ignoring an upside-down flip
        OrientationMode.LOCKED -> ActivityInfo.SCREEN_ORIENTATION_LOCKED
    }
}

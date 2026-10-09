package com.harish.videoplayer.domain.model

/**
 * Switches the user can change in Settings (saved on the phone).
 * Defaults: everything ON except background audio.
 */
data class PlayerSettings(
    /** Every video continues from where you stopped it last time. */
    val resumePlayback: Boolean = true,
    /** Show the half-watched videos strip on the first screen. */
    val showRecentlyPlayed: Boolean = true,
    /** Keep the sound playing when you leave the app (like a music player). */
    val backgroundAudio: Boolean = false,
    /** Pressing Home while a video plays shrinks it into a floating window. */
    val autoPictureInPicture: Boolean = true
)

/** How the picture fits the screen. */
enum class AspectMode(val label: String) {
    FIT("Fit"),   // whole picture visible, black bars if needed
    FILL("Stretch"), // fills the screen, picture may look squashed
    ZOOM("Zoom");  // fills the screen, edges are cropped

    fun next(): AspectMode = entries[(ordinal + 1) % entries.size]
}

/** Speeds offered in the speed menu. */
val PlaybackSpeeds = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

/** 1f -> "1x", 1.25f -> "1.25x", 0.5f -> "0.5x" */
fun speedLabel(speed: Float): String =
    (if (speed % 1f == 0f) speed.toInt().toString() else speed.toString().trimEnd('0')) + "x"

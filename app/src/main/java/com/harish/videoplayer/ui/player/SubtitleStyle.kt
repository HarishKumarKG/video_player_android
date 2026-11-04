package com.harish.videoplayer.ui.player

import android.graphics.Color
import android.util.TypedValue
import android.view.accessibility.CaptioningManager
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import com.harish.videoplayer.domain.model.SubtitleLook

/** The app's own subtitle colour: soft off-white, easier on the eyes than pure white. */
private const val TEXT_COLOR = 0xFFF0EFED.toInt() // #F0EFED

/**
 * One standard size, in sp, the same for every video and both orientations.
 *
 * Why not Android's default? Its size is a share (5.3%) of the video area's HEIGHT. In landscape
 * that's ~20sp, fine; but when the player is tall (phone held upright, or a vertical video) the
 * same 5.3% becomes ~45sp and the lines stretch across the screen. A fixed sp size looks the same
 * everywhere, and still follows the phone's font-size setting.
 */
private const val STANDARD_SIZE_SP = 20f

private val appStyle = CaptionStyleCompat(
    /* foregroundColor = */ TEXT_COLOR,
    /* backgroundColor = */ Color.TRANSPARENT, // no box behind the lines
    /* windowColor = */ Color.TRANSPARENT,
    // Without a box, light text would vanish on a bright sky or a white wall, so every letter
    // gets a soft dark shadow (like Netflix / Prime Video)
    /* edgeType = */ CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
    /* edgeColor = */ Color.BLACK,
    /* typeface = */ null
)

/**
 * Gives the subtitles the look picked in Settings. In every look the words, timing and position
 * come from the video; only how they're drawn changes.
 *
 * Example, a styled anime .ass file (yellow text, black box, big font):
 *  - APP:   off-white, no box, standard size
 *  - VIDEO: yellow on black, the file's own size
 *  - PHONE: whatever you chose in Android's caption settings
 *
 * In the small picture-in-picture window, text is sized to the window instead, so it fits.
 */
fun SubtitleView.applySubtitleLook(look: SubtitleLook, inPip: Boolean) {
    when (look) {
        SubtitleLook.APP -> {
            setApplyEmbeddedStyles(false)    // ignore the file's colours / boxes
            setApplyEmbeddedFontSizes(false) // and its sizes: always the standard size
            setStyle(appStyle)
            standardSize(inPip, scale = 1f)
        }
        SubtitleLook.VIDEO -> {
            setApplyEmbeddedStyles(true)
            setApplyEmbeddedFontSizes(true)  // a file that sets its own size keeps it
            setStyle(CaptionStyleCompat.DEFAULT)
            standardSize(inPip, scale = 1f)  // lines without a size of their own
        }
        SubtitleLook.PHONE -> {
            setApplyEmbeddedStyles(false)
            setApplyEmbeddedFontSizes(false)
            setUserDefaultStyle()            // Settings > Accessibility > Caption preferences
            val captions = context.getSystemService(CaptioningManager::class.java)
            standardSize(inPip, scale = captions?.fontScale ?: 1f) // "Text size" there
        }
    }
}

private fun SubtitleView.standardSize(inPip: Boolean, scale: Float) {
    if (inPip) setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * scale)
    else setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, STANDARD_SIZE_SP * scale)
}

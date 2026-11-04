package com.harish.videoplayer.ui.player

import androidx.media3.common.Player
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.ui.PlayerView

/**
 * Keeps PICTURE subtitles (Blu-ray PGS, DVD VobSub, DVB — common in 1080p MKV rips) in their
 * real shape, fully on screen.
 *
 * The problem: those subtitles are pictures made for the full 1920 x 1080 frame, placed as
 * "x% of the width, y% of the height". Most movie rips are cropped to cinema shape
 * (1920 x 800), so the width still matches but the height doesn't: letters came out stretched.
 *
 * Some files (most DVD VobSub, some PGS) don't send just the subtitle line but a picture of the
 * WHOLE frame, transparent except for the words at the bottom. Drawn in its true shape on a
 * cropped movie, that picture is taller than the video, and the words at its bottom ended up
 * below the screen — invisible.
 *
 * So each subtitle picture is drawn in its true shape, and:
 *  - pinned by its bottom edge if it's in the lower half (top edge otherwise), and
 *  - if it's still too tall to fit, shrunk evenly (never squashed) until it fits.
 *
 * Text subtitles (.srt, .ass, most MKV) are not pictures and pass through untouched.
 */
class PictureSubtitleFix(private val view: PlayerView) : Player.Listener {
    // Runs right after PlayerView's own listener (added earlier), so our version is what shows
    override fun onCues(cueGroup: CueGroup) {
        if (cueGroup.cues.none { it.bitmap != null }) return
        val subtitles = view.subtitleView ?: return
        if (subtitles.width <= 0 || subtitles.height <= 0) return
        val viewAspect = subtitles.width.toFloat() / subtitles.height
        subtitles.setCues(cueGroup.cues.map { keepPictureShape(it, viewAspect) })
    }

    /** Where and how big to draw one subtitle picture, as fractions of the video area. */
    internal data class Box(val left: Float, val width: Float, val top: Float, val height: Float)

    companion object {
        private const val MARGIN = 0.02f

        fun keepPictureShape(cue: Cue, viewAspect: Float): Cue {
            val bitmap = cue.bitmap ?: return cue
            if (cue.size == Cue.DIMEN_UNSET || cue.bitmapHeight == Cue.DIMEN_UNSET ||
                cue.position == Cue.DIMEN_UNSET || cue.line == Cue.DIMEN_UNSET ||
                cue.lineType != Cue.LINE_TYPE_FRACTION || bitmap.width <= 0
            ) return cue
            val left = edge(cue.position, cue.positionAnchor, cue.size)
            val top = edge(cue.line, cue.lineAnchor, cue.bitmapHeight)
            val box = fit(
                Box(left, cue.size, top, cue.bitmapHeight),
                pictureAspect = bitmap.height.toFloat() / bitmap.width,
                viewAspect = viewAspect
            )
            return cue.buildUpon()
                .setPosition(box.left).setPositionAnchor(Cue.ANCHOR_TYPE_START)
                .setSize(box.width)
                .setLine(box.top, Cue.LINE_TYPE_FRACTION).setLineAnchor(Cue.ANCHOR_TYPE_START)
                .setBitmapHeight(box.height)
                .build()
        }

        /** The start (left / top) edge of something [length] long, anchored at [at]. */
        private fun edge(at: Float, anchor: Int, length: Float) = when (anchor) {
            Cue.ANCHOR_TYPE_MIDDLE -> at - length / 2
            Cue.ANCHOR_TYPE_END -> at - length
            else -> at
        }

        /**
         * [original] is where the file wanted the picture (it may be squashed). Returns a box with
         * the picture's true shape ([pictureAspect] = height / width) on a video area of
         * [viewAspect] (= width / height), kept on screen.
         */
        internal fun fit(original: Box, pictureAspect: Float, viewAspect: Float): Box {
            var width = original.width
            var height = width * viewAspect * pictureAspect // true shape, as a fraction of the height
            val lowerHalf = original.top + original.height / 2 > 0.5f
            val room = 1f - 2 * MARGIN
            if (height > room) { // taller than the video: shrink evenly until it fits
                val k = room / height
                width *= k
                height = room
            }
            val left = original.left + (original.width - width) / 2 // same centre as before
            val top = if (lowerHalf) {
                (minOf(original.top + original.height, 1f - MARGIN) - height).coerceAtLeast(MARGIN)
            } else {
                original.top.coerceIn(MARGIN, 1f - MARGIN - height)
            }
            return Box(left, width, top, height)
        }
    }
}

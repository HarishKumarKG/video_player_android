package com.harish.videoplayer.ui.player

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

/** Which half of the screen a gesture is on. */
enum class Side { LEFT, RIGHT }

/** Callbacks for the touch gestures on the video. Fractions are relative to the screen size. */
class PlayerGestureHandlers(
    val onTap: () -> Unit,
    val onDoubleTap: (Side) -> Unit,
    /** Up/down swipe. [delta] > 0 means the finger moved UP by that fraction of the height. */
    val onVerticalDrag: (side: Side, delta: Float) -> Unit,
    /** Left/right swipe. [delta] > 0 means the finger moved RIGHT by that fraction of the width. */
    val onHorizontalDrag: (delta: Float) -> Unit,
    val onDragEnd: () -> Unit
)

private enum class Axis { HORIZONTAL, VERTICAL }

/**
 * The gestures of a typical video app:
 *  - tap            -> show / hide the controls
 *  - double tap     -> left side: back 10s, right side: forward 10s
 *  - swipe up/down  -> left side: brightness, right side: volume
 *  - swipe sideways -> scrub through the video
 *
 * The first few pixels of a swipe decide its direction, so a slightly diagonal swipe
 * doesn't change brightness AND seek at the same time.
 */
@Composable
fun Modifier.playerGestures(enabled: Boolean, handlers: PlayerGestureHandlers): Modifier {
    val current by rememberUpdatedState(handlers)
    if (!enabled) {
        // Locked: a tap only reveals the unlock button
        return this.pointerInput(Unit) { detectTapGestures(onTap = { current.onTap() }) }
    }
    return this
        .pointerInput(Unit) {
            detectTapGestures(
                onTap = { current.onTap() },
                onDoubleTap = { offset ->
                    current.onDoubleTap(if (offset.x < size.width / 2f) Side.LEFT else Side.RIGHT)
                }
            )
        }
        .pointerInput(Unit) {
            var axis: Axis? = null
            var side = Side.LEFT
            detectDragGestures(
                onDragStart = { start ->
                    axis = null
                    side = if (start.x < size.width / 2f) Side.LEFT else Side.RIGHT
                },
                onDragEnd = { if (axis != null) current.onDragEnd() },
                onDragCancel = { if (axis != null) current.onDragEnd() },
                onDrag = { change, drag ->
                    change.consume()
                    val direction = axis ?: (if (abs(drag.x) > abs(drag.y)) Axis.HORIZONTAL else Axis.VERTICAL)
                        .also { axis = it }
                    when (direction) {
                        Axis.HORIZONTAL -> current.onHorizontalDrag(drag.x / size.width)
                        Axis.VERTICAL -> current.onVerticalDrag(side, -drag.y / size.height)
                    }
                }
            )
        }
}

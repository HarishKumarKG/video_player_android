package com.harish.videoplayer.ui.brand

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The white "H + play button" logo, drawn in the launcher icon's 108x108 coordinates.
 * It animates in every time it enters the screen:
 *   ring draws -> H legs spring up -> crossbar slides out -> red play button pops in.
 *
 * @param loop true (splash): the play button keeps "breathing" and a glow pulses forever.
 *             false (header): plays the intro once, then stays still.
 */
@Composable
fun HarishLogo(modifier: Modifier = Modifier, loop: Boolean) {
    val ring = remember { Animatable(0f) }
    val legs = remember { Animatable(0f) }
    val crossbar = remember { Animatable(0f) }
    val play = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch { ring.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
        launch {
            delay(150)
            legs.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow))
        }
        launch { delay(450); crossbar.animateTo(1f, tween(350, easing = FastOutSlowInEasing)) }
        launch {
            delay(700)
            // springy "pop" with a little overshoot
            play.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMediumLow))
        }
    }

    // `loop` never changes for a given call site, so this branch is safe in Compose
    val breathe: State<Float>
    val glow: State<Float>
    if (loop) {
        val infinite = rememberInfiniteTransition(label = "logo")
        breathe = infinite.animateFloat(
            initialValue = 1f,
            targetValue = 1.12f,
            animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
            label = "breathe"
        )
        glow = infinite.animateFloat(
            initialValue = 0.20f,
            targetValue = 0.45f,
            animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
            label = "glow"
        )
    } else {
        breathe = remember { mutableFloatStateOf(1f) }
        glow = remember { mutableFloatStateOf(0f) }
    }

    Canvas(modifier = modifier) {
        val s = size.minDimension / 108f // 1 icon unit in pixels

        if (glow.value > 0f) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = glow.value * ring.value), Color.Transparent),
                    center = center,
                    radius = size.minDimension / 2
                ),
                radius = size.minDimension / 2
            )
        }

        // Ring that draws itself clockwise from the top
        drawArc(
            color = BrandFrosted.copy(alpha = 0.6f),
            startAngle = -90f,
            sweepAngle = 360f * ring.value,
            useCenter = false,
            topLeft = Offset(25 * s, 25 * s),
            size = Size(58 * s, 58 * s),
            style = Stroke(width = 1.6f * s, cap = StrokeCap.Round)
        )

        // Legs of the H grow from the middle outwards (with a small spring overshoot)
        val legHeight = 44 * s * legs.value
        if (legHeight > 0f) {
            for (x in floatArrayOf(35f, 65f)) {
                drawRoundRect(
                    color = BrandHoneydew,
                    topLeft = Offset(x * s, 54 * s - legHeight / 2),
                    size = Size(8 * s, legHeight),
                    cornerRadius = CornerRadius(4 * s)
                )
            }
        }

        // Crossbar slides out from the centre
        val barWidth = 24 * s * crossbar.value
        drawRect(
            color = BrandHoneydew,
            topLeft = Offset(54 * s - barWidth / 2, 52.4f * s),
            size = Size(barWidth, 3.2f * s)
        )

        // Red play button pops out of the crossbar
        val playScale = play.value * breathe.value
        if (playScale > 0f) {
            val triangle = Path().apply {
                moveTo(49.5f * s, 46.5f * s)
                lineTo(61.5f * s, 54f * s)
                lineTo(49.5f * s, 61.5f * s)
                close()
            }
            scale(playScale, pivot = Offset(54.5f * s, 54f * s)) {
                drawPath(triangle, BrandRed, style = Fill)
                // a stroke with round joins softens the corners, like the launcher icon
                drawPath(triangle, BrandRed, style = Stroke(width = 2.4f * s, join = StrokeJoin.Round))
            }
        }
    }
}

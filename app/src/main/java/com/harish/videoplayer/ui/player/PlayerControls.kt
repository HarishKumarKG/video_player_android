package com.harish.videoplayer.ui.player

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.harish.videoplayer.R
import com.harish.videoplayer.domain.model.AspectMode
import com.harish.videoplayer.domain.model.formatDuration
import com.harish.videoplayer.domain.model.speedLabel

/** Everything the controls overlay can ask for. */
class PlayerControlActions(
    val onBack: () -> Unit,
    val onPlayPause: () -> Unit,
    val onRewind: () -> Unit,
    val onForward: () -> Unit,
    val onNext: () -> Unit,
    val onPrevious: () -> Unit,
    val onSeek: (Long) -> Unit,
    val onLock: () -> Unit,
    val onAspect: () -> Unit,
    val onRotate: () -> Unit,
    val onSubtitles: () -> Unit,
    val onAudio: () -> Unit,
    val onSpeed: () -> Unit,
    val onPictureInPicture: (() -> Unit)?, // null = not supported on this phone
    val onSettings: () -> Unit,
    /** Any touch on the controls: restarts the auto-hide timer. */
    val onInteraction: () -> Unit
)

private val White = Color.White

/**
 * Controls drawn over the video:
 *   top    -> ← title  [subtitles] [audio] [1x] [PiP] [⋮]
 *   middle -> ⏮  ⏪10  ⏯  ⏩10  ⏭
 *   bottom -> 12:34 ━━━●──── 45:00, then [lock] [Fit] ... [rotate]
 * A dark gradient at the top and bottom keeps white icons readable on bright videos.
 */
@Composable
fun PlayerControls(
    ui: PlayerUiState,
    aspect: AspectMode,
    showAudioButton: Boolean,
    actions: PlayerControlActions
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.65f),
                    0.25f to Color.Transparent,
                    0.7f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.75f)
                )
            )
            .windowInsetsPadding(WindowInsets.displayCutout) // keep buttons out of the camera notch
    ) {
        TopBar(ui, showAudioButton, actions, Modifier.align(Alignment.TopCenter))
        CenterButtons(ui, actions, Modifier.align(Alignment.Center))
        BottomBar(ui, aspect, actions, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun TopBar(ui: PlayerUiState, showAudioButton: Boolean, actions: PlayerControlActions, modifier: Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlayerIcon(R.drawable.baseline_arrow_back_24, "Close player", actions.onBack)
        Text(
            ui.title,
            color = White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp)
        )
        PlayerIcon(R.drawable.baseline_subtitles_24, "Subtitles", actions.onSubtitles)
        if (showAudioButton) PlayerIcon(R.drawable.baseline_audiotrack_24, "Audio track", actions.onAudio)
        TextButton(onClick = { actions.onInteraction(); actions.onSpeed() }) {
            Text(speedLabel(ui.speed), color = White, fontWeight = FontWeight.Bold)
        }
        actions.onPictureInPicture?.let {
            PlayerIcon(R.drawable.baseline_picture_in_picture_24, "Picture-in-picture", it)
        }
        PlayerIcon(R.drawable.baseline_more_vert_24, "Player settings", actions.onSettings)
    }
}

@Composable
private fun CenterButtons(ui: PlayerUiState, actions: PlayerControlActions, modifier: Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PlayerIcon(R.drawable.baseline_skip_previous_24, "Previous video", actions.onPrevious, size = 40.dp)
        PlayerIcon(R.drawable.baseline_replay_10_24, "Back 10 seconds", actions.onRewind, size = 40.dp)
        // Big round play button in the brand red
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.tertiary),
            contentAlignment = Alignment.Center
        ) {
            if (ui.isBuffering && !ui.isPlaying) {
                CircularProgressIndicator(color = White, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
            } else {
                IconButton(onClick = { actions.onInteraction(); actions.onPlayPause() }, modifier = Modifier.size(72.dp)) {
                    Icon(
                        painterResource(if (ui.isPlaying) R.drawable.baseline_pause_24 else R.drawable.baseline_play_arrow_24),
                        contentDescription = if (ui.isPlaying) "Pause" else "Play",
                        tint = White,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
        }
        PlayerIcon(R.drawable.baseline_forward_10_24, "Forward 10 seconds", actions.onForward, size = 40.dp)
        PlayerIcon(
            R.drawable.baseline_skip_next_24, "Next video", actions.onNext,
            size = 40.dp, enabled = ui.hasNext
        )
    }
}

@Composable
private fun BottomBar(ui: PlayerUiState, aspect: AspectMode, actions: PlayerControlActions, modifier: Modifier) {
    // While dragging, the slider shows the finger's spot; the seek happens when you let go
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val duration = ui.durationMs.coerceAtLeast(1)
    val fraction = dragFraction ?: (ui.positionMs.toFloat() / duration).coerceIn(0f, 1f)
    val shownPosition = dragFraction?.let { (it * duration).toLong() } ?: ui.positionMs

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TimeText(formatDuration(shownPosition))
            Slider(
                value = fraction,
                onValueChange = {
                    dragFraction = it
                    actions.onInteraction()
                },
                onValueChangeFinished = {
                    dragFraction?.let { actions.onSeek((it * duration).toLong()) }
                    dragFraction = null
                },
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.tertiary,
                    activeTrackColor = MaterialTheme.colorScheme.tertiary,
                    inactiveTrackColor = White.copy(alpha = 0.3f)
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            )
            TimeText(formatDuration(ui.durationMs))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlayerIcon(R.drawable.baseline_lock_open_24, "Lock controls", actions.onLock)
            TextButton(onClick = { actions.onInteraction(); actions.onAspect() }) {
                Icon(
                    painterResource(R.drawable.baseline_aspect_ratio_24),
                    contentDescription = null,
                    tint = White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(aspect.label, color = White)
            }
            Spacer(Modifier.weight(1f))
            PlayerIcon(R.drawable.baseline_screen_rotation_24, "Rotate screen", actions.onRotate)
        }
    }
}

@Composable
private fun TimeText(text: String) {
    Text(text, color = White, style = MaterialTheme.typography.labelMedium)
}

@Composable
private fun PlayerIcon(
    @DrawableRes icon: Int,
    description: String,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp = 24.dp,
    enabled: Boolean = true
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(size + 24.dp)) {
        Icon(
            painterResource(icon),
            contentDescription = description,
            tint = if (enabled) White else White.copy(alpha = 0.35f),
            modifier = Modifier.size(size)
        )
    }
}

/** When locked, only this small button shows (on a tap), so pocket touches can't do anything. */
@Composable
fun UnlockButton(onUnlock: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.displayCutout)
            .padding(16.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onUnlock) {
            Icon(painterResource(R.drawable.baseline_lock_24), contentDescription = "Unlock", tint = White)
        }
        Text("Tap to unlock", color = White, style = MaterialTheme.typography.labelLarge)
    }
}

/** Big round "feedback" bubble in the middle: brightness / volume level, or seek target. */
sealed interface GestureHud {
    data class Brightness(val level: Float) : GestureHud
    data class Volume(val level: Float) : GestureHud
    data class Seek(val targetMs: Long, val deltaMs: Long) : GestureHud
    data class Skip(val side: Side, val seconds: Int) : GestureHud
}

@Composable
fun GestureHudView(hud: GestureHud, modifier: Modifier = Modifier) {
    val (icon, text) = when (hud) {
        is GestureHud.Brightness -> R.drawable.baseline_brightness_24 to "${(hud.level * 100).toInt()}%"
        is GestureHud.Volume -> R.drawable.baseline_volume_up_24 to "${(hud.level * 100).toInt()}%"
        is GestureHud.Seek -> {
            val sign = if (hud.deltaMs >= 0) "+" else "−"
            (if (hud.deltaMs >= 0) R.drawable.baseline_forward_10_24 else R.drawable.baseline_replay_10_24) to "${formatDuration(hud.targetMs)}\n$sign${formatDuration(kotlin.math.abs(hud.deltaMs))}"
        }
        is GestureHud.Skip -> (if (hud.side == Side.LEFT) R.drawable.baseline_replay_10_24 else R.drawable.baseline_forward_10_24) to
            "${if (hud.side == Side.LEFT) "−" else "+"}${hud.seconds}s"
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = White,
            modifier = Modifier.size(32.dp)
        )
        Text(
            text,
            color = White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

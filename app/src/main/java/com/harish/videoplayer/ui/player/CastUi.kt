package com.harish.videoplayer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.harish.videoplayer.R
import com.harish.videoplayer.cast.CastDevice
import com.harish.videoplayer.cast.CastKind
import com.harish.videoplayer.cast.CastState
import com.harish.videoplayer.cast.RemoteState
import com.harish.videoplayer.domain.model.Video
import com.harish.videoplayer.domain.model.formatDuration
import com.harish.videoplayer.ui.common.VideoThumbnail

/**
 * "Cast to a TV" sheet: lists every TV found on the Wi-Fi while it's open
 * (Chromecasts / Google TVs and DLNA smart TVs together).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastDevicesSheet(
    devices: List<CastDevice>,
    searching: Boolean,
    castState: CastState,
    onStartSearch: () -> Unit,
    onStopSearch: () -> Unit,
    onPick: (CastDevice) -> Unit,
    onStopCasting: () -> Unit,
    onDismiss: () -> Unit
) {
    // Search only while the sheet is open (it uses the Wi-Fi and a little battery)
    DisposableEffect(Unit) {
        onStartSearch()
        onDispose { onStopSearch() }
    }
    val current = when (castState) {
        is CastState.Casting -> castState.device
        is CastState.Connecting -> castState.device
        CastState.Idle -> null
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
        ) {
            Text("Cast to a TV", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "The TV plays the original file, so 4K / 8K / HDR stay exactly as they are.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            if (searching) LinearProgressIndicator(Modifier.fillMaxWidth()) else Spacer(Modifier.height(4.dp))
            Spacer(Modifier.height(8.dp))

            devices.forEach { device ->
                DeviceRow(device, isCurrent = device.id == current?.id) { onPick(device) }
            }
            if (devices.isEmpty()) {
                Text(
                    if (searching) "Looking for TVs on your Wi-Fi…"
                    else "No TVs found. Turn the TV on and make sure the phone and TV are on the same Wi-Fi.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Tip: on Samsung / LG TVs, allow the phone the first time (a popup may appear on the TV).",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (current != null) {
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = onStopCasting, modifier = Modifier.fillMaxWidth()) {
                    Icon(painterResource(R.drawable.baseline_stop_24), null)
                    Spacer(Modifier.width(8.dp))
                    Text("Stop casting to ${current.name}")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DeviceRow(device: CastDevice, isCurrent: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (isCurrent) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painterResource(if (device.kind == CastKind.GOOGLE_CAST) R.drawable.baseline_cast_24 else R.drawable.baseline_tv_24),
                contentDescription = null,
                tint = if (isCurrent) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(device.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(device.kind.label, device.description).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isCurrent) Text("Playing", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelLarge)
    }
}

/** What the TV is doing, as a remote control. */
class CastPanelActions(
    val onClose: () -> Unit,
    val onPlayPause: () -> Unit,
    val onSkip: (Long) -> Unit,
    val onSeek: (Long) -> Unit,
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
    val onVolume: (Int) -> Unit,
    val onPickTv: () -> Unit,
    val onStopCasting: () -> Unit
)

/**
 * Shown on the phone while the video plays on the TV: the phone becomes the remote.
 * Leaving the player keeps the TV playing (stop it here or from the notification).
 */
@Composable
fun CastingPanel(state: CastState, actions: CastPanelActions) {
    val device: CastDevice
    val video: Video
    val casting = state as? CastState.Casting
    when (state) {
        is CastState.Casting -> { device = state.device; video = state.video }
        is CastState.Connecting -> { device = state.device; video = state.video }
        CastState.Idle -> return
    }
    val status = casting?.status
    val white = Color.White

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // The video's picture, dimmed, as the background
        VideoThumbnail(video, Modifier.fillMaxSize())
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Black.copy(alpha = 0.92f))))
        )
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = actions.onClose) {
                    Icon(painterResource(R.drawable.baseline_arrow_back_24), "Close (the TV keeps playing)", tint = white)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = actions.onPickTv) {
                    Icon(painterResource(R.drawable.baseline_cast_connected_24), "Choose TV", tint = MaterialTheme.colorScheme.tertiary)
                }
            }
            Spacer(Modifier.height(24.dp))
            Icon(painterResource(R.drawable.baseline_tv_24), null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(12.dp))
            Text(
                when {
                    casting == null -> "Connecting to ${device.name}…"
                    status?.state == RemoteState.BUFFERING -> "Loading on ${device.name}…"
                    status?.state == RemoteState.PAUSED -> "Paused on ${device.name}"
                    else -> "Playing on ${device.name}"
                },
                color = white.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                video.title,
                color = white,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(32.dp))

            if (casting == null || status == null) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.tertiary)
            } else {
                SeekRow(status.positionMs, status.durationMs, actions.onSeek)
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RemoteButton(R.drawable.baseline_skip_previous_24, "Previous video", casting.hasPrevious, actions.onPrevious)
                    RemoteButton(R.drawable.baseline_replay_10_24, "Back 10 seconds", true) { actions.onSkip(-10_000) }
                    FilledIconButton(
                        onClick = actions.onPlayPause,
                        modifier = Modifier.size(72.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                    ) {
                        Icon(
                            painterResource(if (status.state == RemoteState.PLAYING) R.drawable.baseline_pause_24 else R.drawable.baseline_play_arrow_24),
                            contentDescription = if (status.state == RemoteState.PLAYING) "Pause" else "Play",
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    RemoteButton(R.drawable.baseline_forward_10_24, "Forward 10 seconds", true) { actions.onSkip(10_000) }
                    RemoteButton(R.drawable.baseline_skip_next_24, "Next video", casting.hasNext, actions.onNext)
                }
                status.volume?.let { volume -> VolumeRow(volume, actions.onVolume) }
            }

            Spacer(Modifier.height(32.dp))
            OutlinedButton(onClick = actions.onStopCasting) {
                Icon(painterResource(R.drawable.baseline_stop_24), null, tint = white)
                Spacer(Modifier.width(8.dp))
                Text("Stop casting · continue on phone", color = white)
            }
        }
    }
}

@Composable
private fun SeekRow(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
    // While dragging, show the finger's spot; the TV jumps there when you let go
    var dragging by remember { mutableStateOf<Float?>(null) }
    val duration = durationMs.coerceAtLeast(1)
    val fraction = dragging ?: (positionMs.toFloat() / duration).coerceIn(0f, 1f)
    Column(Modifier.widthIn(max = 560.dp)) {
        Slider(
            value = fraction,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { onSeek((it * duration).toLong()) }
                dragging = null
            },
            colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.tertiary, activeTrackColor = MaterialTheme.colorScheme.tertiary)
        )
        Row(Modifier.fillMaxWidth()) {
            Text(formatDuration((fraction * duration).toLong()), color = Color.White, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.weight(1f))
            Text(if (durationMs > 0) formatDuration(durationMs) else "--:--", color = Color.White, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun VolumeRow(volume: Int, onVolume: (Int) -> Unit) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    Row(Modifier.widthIn(max = 420.dp).padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onVolume((volume - 5).coerceAtLeast(0)) }) {
            Icon(painterResource(R.drawable.baseline_volume_down_24), "TV volume down", tint = Color.White)
        }
        Slider(
            value = dragging ?: (volume / 100f),
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { onVolume((it * 100).toInt()) }
                dragging = null
            },
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = { onVolume((volume + 5).coerceAtMost(100)) }) {
            Icon(painterResource(R.drawable.baseline_volume_up_24), "TV volume up", tint = Color.White)
        }
    }
}

@Composable
private fun RemoteButton(icon: Int, description: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(56.dp)) {
        Icon(
            painterResource(icon),
            contentDescription = description,
            tint = if (enabled) Color.White else Color.White.copy(alpha = 0.3f),
            modifier = Modifier.size(32.dp)
        )
    }
}

package com.harish.videoplayer.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Compose-friendly mirror of the player. ExoPlayer tells us about changes through a listener;
 * we copy what the screen needs into Compose state so only the parts that changed redraw.
 * The position isn't an event, so it's polled (4x a second while playing).
 */
@Stable
class PlayerUiState(private val player: Player) {
    var isPlaying by mutableStateOf(false)
        private set
    var isBuffering by mutableStateOf(false)
        private set
    var hasEnded by mutableStateOf(false)
        private set
    var title by mutableStateOf("")
        private set
    var durationMs by mutableLongStateOf(0L)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set
    var bufferedMs by mutableLongStateOf(0L)
        private set
    var videoWidth by mutableIntStateOf(0)
        private set
    var videoHeight by mutableIntStateOf(0)
        private set
    var speed by mutableFloatStateOf(1f)
        private set
    var hasNext by mutableStateOf(false)
        private set
    var hasPrevious by mutableStateOf(false)
        private set
    var tracks by mutableStateOf(Tracks.EMPTY)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    internal val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh()
    }

    internal fun refresh() {
        isPlaying = player.isPlaying
        isBuffering = player.playbackState == Player.STATE_BUFFERING
        hasEnded = player.playbackState == Player.STATE_ENDED
        title = player.mediaMetadata.title?.toString().orEmpty()
        durationMs = player.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0) ?: 0L
        // Anamorphic videos store a pixel ratio; apply it so PiP gets the real shape
        val size = player.videoSize
        videoWidth = (size.width * size.pixelWidthHeightRatio).toInt()
        videoHeight = size.height
        speed = player.playbackParameters.speed
        hasNext = player.hasNextMediaItem()
        hasPrevious = player.hasPreviousMediaItem()
        tracks = player.currentTracks
        error = player.playerError?.let { "Can't play this video (${it.errorCodeName})" }
        updatePosition()
    }

    internal fun updatePosition() {
        positionMs = player.currentPosition.coerceAtLeast(0)
        bufferedMs = player.bufferedPosition.coerceAtLeast(0)
    }
}

@Composable
fun rememberPlayerUiState(player: Player): PlayerUiState {
    val state = remember(player) { PlayerUiState(player) }
    DisposableEffect(player) {
        player.addListener(state.listener)
        state.refresh()
        onDispose { player.removeListener(state.listener) }
    }
    LaunchedEffect(player, state.isPlaying) {
        while (isActive) {
            state.updatePosition()
            delay(if (state.isPlaying) 250 else 1000)
        }
    }
    return state
}

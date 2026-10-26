package com.harish.videoplayer.ui.player

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import com.harish.videoplayer.cast.CastDevice
import com.harish.videoplayer.cast.CastHandBack
import com.harish.videoplayer.cast.CastManager
import com.harish.videoplayer.cast.CastState
import com.harish.videoplayer.data.settings.PlayerSettingsRepository
import com.harish.videoplayer.domain.model.PlayerSettings
import com.harish.videoplayer.playback.PlaybackController
import com.harish.videoplayer.playback.TrackOption
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val controller: PlaybackController,
    private val settingsRepository: PlayerSettingsRepository,
    private val castManager: CastManager
) : ViewModel() {

    val player: ExoPlayer get() = controller.player
    val settings: StateFlow<PlayerSettings> = settingsRepository.settings
    val resumeNotice: StateFlow<Long?> = controller.resumeNotice
    fun consumeResumeNotice() = controller.consumeResumeNotice()

    fun togglePlayPause() {
        val p = player
        when {
            p.playbackState == Player.STATE_ENDED -> { p.seekTo(0); p.play() } // replay
            p.isPlaying -> p.pause()
            else -> p.play()
        }
    }

    fun rewind() = player.seekBack()
    fun forward() = player.seekForward()
    fun next() = player.seekToNextMediaItem()
    fun previous() = player.seekToPrevious() // restarts the video if past the first seconds
    fun seekTo(positionMs: Long) = player.seekTo(positionMs.coerceAtLeast(0))
    fun startOver() = controller.startOver()
    fun setSpeed(speed: Float) = controller.setSpeed(speed)
    fun volumeBoost(): Float = controller.volumeBoost
    fun setVolumeBoost(level: Float) = controller.setVolumeBoost(level)

    fun audioTracks(tracks: Tracks): List<TrackOption> = controller.tracksOf(tracks, C.TRACK_TYPE_AUDIO)
    fun subtitleTracks(tracks: Tracks): List<TrackOption> = controller.tracksOf(tracks, C.TRACK_TYPE_TEXT)
    fun selectAudio(option: TrackOption) = controller.chooseAudio(option) // also remembered for next time
    fun selectSubtitle(option: TrackOption?) = controller.selectTrack(C.TRACK_TYPE_TEXT, option)
    fun addSubtitle(uri: Uri, fileName: String) = controller.addSubtitle(uri, fileName)

    fun setBackgroundAudio(enabled: Boolean) = settingsRepository.setBackgroundAudio(enabled)
    fun setAutoPictureInPicture(enabled: Boolean) = settingsRepository.setAutoPictureInPicture(enabled)

    /**
     * The app went to the background (Home, another app, screen off) and is NOT showing the
     * floating window. With "Background audio" on, the sound continues without the picture;
     * otherwise the video pauses.
     * [closedFloatingWindow]: the user just closed the PiP window with ✕, which means "stop".
     */
    fun onAppHidden(closedFloatingWindow: Boolean) {
        // playWhenReady (not isPlaying): a video that is buffering for a moment still counts as playing
        if (settings.value.backgroundAudio && !closedFloatingWindow && player.playWhenReady) {
            controller.setVideoEnabled(false)
        } else {
            player.pause()
        }
    }

    fun onAppVisible() = controller.setVideoEnabled(true)

    /** Leaving the player screen (a video playing on the TV keeps playing). */
    fun close() = controller.stop()

    // ---------- Casting to a TV ----------
    val castState: StateFlow<CastState> = castManager.state
    val castDevices: StateFlow<List<CastDevice>> = castManager.devices
    val castSearching: StateFlow<Boolean> = castManager.searching
    val castMessages: SharedFlow<String> = castManager.messages
    val castHandBack: SharedFlow<CastHandBack> = castManager.handBack

    fun startCastSearch() = castManager.startSearch()
    fun stopCastSearch() = castManager.stopSearch()

    /** The video showing on the phone right now. */
    fun currentVideoId(): Long? = controller.queue.value.getOrNull(player.currentMediaItemIndex)?.id

    /** Sends the current video (and the rest of the list) to [device], from where the phone is now. */
    fun castTo(device: CastDevice) {
        val queue = controller.queue.value
        if (queue.isEmpty()) return
        val index = player.currentMediaItemIndex.coerceIn(queue.indices)
        val position = player.currentPosition
        player.pause() // the phone goes quiet; the TV takes over
        castManager.cast(device, queue, index, position)
    }

    fun castPlayPause() = castManager.togglePlayPause()
    fun castSkip(deltaMs: Long) = castManager.skipBy(deltaMs)
    fun castSeek(positionMs: Long) = castManager.seekTo(positionMs)
    fun castNext() = castManager.next()
    fun castPrevious() = castManager.previous()
    fun castVolume(volume: Int) = castManager.setVolume(volume)
    fun stopCasting() = castManager.stopCasting()

    /** Casting stopped: carry on on the phone from where the TV was (paused, ready to play). */
    fun continueOnPhone(handBack: CastHandBack) {
        val index = controller.queue.value.indexOfFirst { it.id == handBack.videoId }
        if (index < 0) return
        player.seekTo(index, handBack.positionMs)
        player.pause()
    }
}

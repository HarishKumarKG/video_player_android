package com.harish.videoplayer.ui.player

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import com.harish.videoplayer.data.settings.PlayerSettingsRepository
import com.harish.videoplayer.domain.model.PlayerSettings
import com.harish.videoplayer.playback.PlaybackController
import com.harish.videoplayer.playback.TrackOption
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val controller: PlaybackController,
    private val settingsRepository: PlayerSettingsRepository
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

    fun audioTracks(tracks: Tracks): List<TrackOption> = controller.tracksOf(tracks, C.TRACK_TYPE_AUDIO)
    fun subtitleTracks(tracks: Tracks): List<TrackOption> = controller.tracksOf(tracks, C.TRACK_TYPE_TEXT)
    fun selectAudio(option: TrackOption) = controller.selectTrack(C.TRACK_TYPE_AUDIO, option)
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
        if (settings.value.backgroundAudio && !closedFloatingWindow && player.isPlaying) {
            controller.setVideoEnabled(false)
        } else {
            player.pause()
        }
    }

    fun onAppVisible() = controller.setVideoEnabled(true)

    /** Leaving the player screen. */
    fun close() = controller.stop()
}

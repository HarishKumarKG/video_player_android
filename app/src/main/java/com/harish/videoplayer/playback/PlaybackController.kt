package com.harish.videoplayer.playback

import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.harish.videoplayer.data.resume.ResumeRepository
import com.harish.videoplayer.data.settings.PlayerSettingsRepository
import com.harish.videoplayer.domain.model.Video
import com.harish.videoplayer.domain.subtitle.SubtitleFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** One choosable audio or subtitle track, e.g. "Tamil · AAC 5.1". */
data class TrackOption(
    val type: Int,          // C.TRACK_TYPE_AUDIO or C.TRACK_TYPE_TEXT
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val isSelected: Boolean
)

/**
 * Everything the app can ask the player to do, in one place:
 * start a list of videos, stop, remember where you stopped, subtitles, audio tracks,
 * background mode.
 *
 * Example: tapping the 3rd video of a folder calls `play(folderVideos, 2)`. Next/previous then
 * move through that folder, and each video continues from where you left it last time.
 */
@Singleton
class PlaybackController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val holder: PlayerHolder,
    private val resumeRepository: ResumeRepository,
    private val settingsRepository: PlayerSettingsRepository
) {
    val player: ExoPlayer get() = holder.player

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var tracker: Job? = null
    private var listenerAdded = false

    // Last known spot of the current video (kept fresh every second while playing)
    private var currentId: Long? = null
    private var lastPositionMs = 0L
    private var lastDurationMs = 0L

    private val _queue = MutableStateFlow<List<Video>>(emptyList())
    val queue: StateFlow<List<Video>> = _queue.asStateFlow()

    /**
     * Position a video was just continued from, so the player can say "Continued from 12:34".
     * It's a StateFlow (not a one-shot event) because play() runs a moment BEFORE the player
     * screen exists; the screen shows it and then calls [consumeResumeNotice].
     */
    private val _resumeNotice = MutableStateFlow<Long?>(null)
    val resumeNotice: StateFlow<Long?> = _resumeNotice.asStateFlow()

    fun consumeResumeNotice() {
        _resumeNotice.value = null
    }

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val newId = mediaItem?.mediaId?.toLongOrNull()
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                // New list (or the same video re-loaded with a subtitle): nothing finished here
                currentId = newId
                return
            }
            // Moving on: remember where the previous video stopped (or forget it if it finished)
            currentId?.takeIf { it != newId }?.let { previous ->
                val finished = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
                if (finished) resumeRepository.clear(previous)
                else resumeRepository.save(previous, lastPositionMs, lastDurationMs)
            }
            currentId = newId
            lastPositionMs = 0L
            lastDurationMs = 0L
            newId?.let(::continueFromSavedPosition)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                startTracking()
            } else {
                tracker?.cancel()
                recordPosition()
                saveCurrent()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) currentId?.let(resumeRepository::clear)
        }
    }

    /** Plays [videos] starting at [startIndex]; continues from the saved spot if there is one. */
    fun play(videos: List<Video>, startIndex: Int) {
        if (videos.isEmpty()) return
        val p = player
        if (!listenerAdded) {
            p.addListener(listener)
            listenerAdded = true
        }
        // Save the video we're leaving (if any) before replacing the list
        recordPosition()
        saveCurrent()

        val index = startIndex.coerceIn(videos.indices)
        val resumeAt = savedPositionFor(videos[index].id)
        _queue.value = videos
        currentId = videos[index].id
        lastPositionMs = resumeAt ?: 0L
        lastDurationMs = videos[index].durationMs

        p.setMediaItems(videos.map { it.toMediaItem() }, index, resumeAt ?: 0L)
        setVideoEnabled(true)
        p.prepare()
        p.play()
        _resumeNotice.value = resumeAt
        connectSession()
    }

    /** "Start over" button on the resume message. */
    fun startOver() {
        player.seekTo(0)
        currentId?.let(resumeRepository::clear)
    }

    /** Leaving the player screen: remember the spot, stop, and let the service go away. */
    fun stop() {
        tracker?.cancel()
        recordPosition()
        saveCurrent()
        player.stop()
        player.clearMediaItems()
        currentId = null
        _queue.value = emptyList()
        _resumeNotice.value = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
    }

    /**
     * Background audio: turning the video track off while the app is hidden saves a lot of
     * battery (nothing is decoded for a screen nobody sees). Sound keeps playing.
     */
    fun setVideoEnabled(enabled: Boolean) {
        val p = player
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, !enabled)
            .build()
    }

    fun setSpeed(speed: Float) = player.setPlaybackSpeed(speed)

    // ---------- Audio & subtitle tracks ----------

    /** Audio or subtitle tracks found in the current video (e.g. the languages of an MKV). */
    fun tracksOf(tracks: Tracks, type: Int): List<TrackOption> {
        val options = mutableListOf<TrackOption>()
        tracks.groups.forEachIndexed { groupIndex, group ->
            if (group.type != type) return@forEachIndexed
            for (i in 0 until group.length) {
                if (!group.isTrackSupported(i)) continue
                val format = group.getTrackFormat(i)
                val language = format.language
                    ?.takeIf { it.isNotBlank() && it != "und" }
                    ?.let { Locale.forLanguageTag(it).displayLanguage.ifBlank { it } }
                val channels = when (format.channelCount) {
                    1 -> "Mono"
                    2 -> "Stereo"
                    6 -> "5.1"
                    8 -> "7.1"
                    else -> null
                }
                val label = listOfNotNull(format.label, language, channels)
                    .distinct()
                    .joinToString(" · ")
                    .ifBlank { "Track ${options.size + 1}" }
                options += TrackOption(type, groupIndex, i, label, group.isTrackSelected(i))
            }
        }
        return options
    }

    /** Picks one track; for subtitles, null means "Off". */
    fun selectTrack(type: Int, option: TrackOption?) {
        val p = player
        val builder = p.trackSelectionParameters.buildUpon()
        if (option == null) {
            builder.clearOverridesOfType(type).setTrackTypeDisabled(type, true)
        } else {
            val group = p.currentTracks.groups.getOrNull(option.groupIndex) ?: return
            builder.setTrackTypeDisabled(type, false)
                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex))
        }
        p.trackSelectionParameters = builder.build()
    }

    /**
     * Adds a subtitle file the user picked (.srt, .vtt, .ass...) to the current video.
     * Subtitles are part of the MediaItem, so the item is swapped for one that includes the file
     * and playback continues from the same spot.
     */
    fun addSubtitle(uri: Uri, fileName: String) {
        val p = player
        val item = p.currentMediaItem ?: return
        val index = p.currentMediaItemIndex
        val position = p.currentPosition
        val subtitle = MediaItem.SubtitleConfiguration.Builder(uri)
            .setMimeType(SubtitleFormat.mimeTypeFor(fileName) ?: SubtitleFormat.SRT)
            .setLanguage(SubtitleFormat.languageFor(fileName))
            .setLabel(fileName.substringBeforeLast('.'))
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT or C.SELECTION_FLAG_FORCED)
            .build()
        p.replaceMediaItem(index, item.buildUpon().setSubtitleConfigurations(listOf(subtitle)).build())
        p.seekTo(index, position)
        // Make sure subtitles are switched on and the new file wins over any embedded track
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .build()
    }

    // ---------- Internals ----------

    private fun continueFromSavedPosition(videoId: Long) {
        val saved = savedPositionFor(videoId) ?: return
        player.seekTo(saved)
        lastPositionMs = saved
        _resumeNotice.value = saved
    }

    /** Where to continue this video from, or null to start at 0 (also when "Resume" is off). */
    private fun savedPositionFor(videoId: Long): Long? =
        if (settingsRepository.settings.value.resumePlayback) resumeRepository.positionFor(videoId) else null

    private fun startTracking() {
        tracker?.cancel()
        tracker = scope.launch {
            var seconds = 0
            while (isActive) {
                recordPosition()
                if (++seconds % SAVE_EVERY_SECONDS == 0) saveCurrent() // survives a crash / force stop
                delay(1_000)
            }
        }
    }

    private fun recordPosition() {
        val p = player
        if (p.currentMediaItem == null) return
        lastPositionMs = p.currentPosition
        lastDurationMs = p.duration.takeIf { it != C.TIME_UNSET } ?: lastDurationMs
    }

    private fun saveCurrent() {
        currentId?.let { resumeRepository.save(it, lastPositionMs, lastDurationMs) }
    }

    /**
     * Connecting a MediaController starts/binds PlaybackService, which shows the media
     * notification and keeps playback alive in the background.
     */
    private fun connectSession() {
        val existing = controllerFuture
        if (existing != null && !existing.isCancelled) {
            val connected = existing.isDone && runCatching { existing.get().isConnected }.getOrDefault(false)
            if (connected || !existing.isDone) return
            MediaController.releaseFuture(existing) // dead connection: let it go before reconnecting
        }
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({ /* connected: nothing else to do */ }, ContextCompat.getMainExecutor(context))
        controllerFuture = future
    }

    private companion object {
        const val SAVE_EVERY_SECONDS = 5
    }
}

/** content://media/external/video/media/<id> */
fun Video.contentUri(): Uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

private fun Video.toMediaItem(): MediaItem {
    val uri = contentUri()
    return MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setDisplayTitle(title)
                .setArtist(folderName)  // shown as the second line in the notification
                .setArtworkUri(uri)     // VideoThumbnailBitmapLoader turns this into a frame
                .build()
        )
        .build()
}

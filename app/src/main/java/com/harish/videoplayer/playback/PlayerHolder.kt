package com.harish.videoplayer.playback

import android.content.Context
import androidx.media3.common.AudioAttributes
import android.os.Handler
import androidx.media3.common.C
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.harish.videoplayer.data.settings.PlayerSettingsRepository
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The ONE ExoPlayer of the app.
 *
 * The player screen draws its picture, and PlaybackService wraps it in a MediaSession for the
 * notification. Sharing a single instance means leaving the screen (Home button, PiP, screen off)
 * never restarts the video: the same player simply keeps going.
 *
 * Use it from the main thread only (ExoPlayer's rule).
 */
@Singleton
class PlayerHolder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: PlayerSettingsRepository
) {
    /** 5.1 / 7.1 -> stereo with clear dialogue (Settings > Clear voices). Checked per video. */
    private val voiceDownmix = VoiceDownmixAudioProcessor { settingsRepository.settings.value.clearVoice }

    private var instance: ExoPlayer? = null

    val player: ExoPlayer
        get() = instance ?: create().also { instance = it }

    private fun create(): ExoPlayer = ExoPlayer.Builder(context, AudioRenderersFactory(context, voiceDownmix))
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            /* handleAudioFocus = */ true // pause for calls / other apps, resume after
        )
        .setHandleAudioBecomingNoisy(true) // headphones unplugged -> pause
        .setWakeMode(C.WAKE_MODE_LOCAL)    // keep the CPU awake for background audio
        .setSeekBackIncrementMs(SKIP_MS)
        .setSeekForwardIncrementMs(SKIP_MS)
        .build()

    companion object {
        /** The ⏪ / ⏩ buttons and double-tap jump by this much. */
        const val SKIP_MS = 10_000L
    }
}

/**
 * Two audio decoders, in this order:
 *  1. The phone's own decoder - best for everyday formats (AAC, MP3...): fast and reliable.
 *  2. FFmpeg (software) - for formats the phone can't play at all: Dolby Digital (AC-3 / E-AC-3),
 *     DTS, TrueHD... Without it those tracks are silent.
 *
 * If the chosen decoder still fails in the middle of a file (some MKV rips have slightly broken
 * audio that only one of them tolerates), PlaybackController switches to the other one and
 * continues from the same spot - see `tryOtherAudioDecoder`.
 *
 * Video always uses the phone's hardware decoder (fast, battery friendly).
 */
private class AudioRenderersFactory(
    context: Context,
    private val voiceDownmix: VoiceDownmixAudioProcessor
) : DefaultRenderersFactory(context) {

    init {
        setEnableDecoderFallback(true)
    }

    /** Same audio output as Media3's default, plus our surround -> stereo "clear voices" step. */
    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioOutputPlaybackParams: Boolean
    ): AudioSink = DefaultAudioSink.Builder(context)
        .setEnableFloatOutput(enableFloatOutput)
        .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
        .setAudioProcessors(arrayOf(voiceDownmix))
        .build()

    override fun buildAudioRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        audioSink: AudioSink,
        eventHandler: Handler,
        eventListener: AudioRendererEventListener,
        out: ArrayList<Renderer>
    ) {
        super.buildAudioRenderers(
            context,
            DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON, // phone decoder first, FFmpeg as backup
            mediaCodecSelector,
            enableDecoderFallback,
            audioSink,
            eventHandler,
            eventListener,
            out
        )
    }
}

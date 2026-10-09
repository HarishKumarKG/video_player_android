package com.harish.videoplayer.data.settings

import android.content.Context
import com.harish.videoplayer.domain.model.PlayerSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Settings switches (resume, recently played, background audio, auto PiP) saved in SharedPreferences. */
@Singleton
class PlayerSettingsRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("player_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(
        PlayerSettings(
            resumePlayback = prefs.getBoolean(KEY_RESUME, true),
            showRecentlyPlayed = prefs.getBoolean(KEY_SHOW_RECENT, true),
            backgroundAudio = prefs.getBoolean(KEY_BACKGROUND_AUDIO, false),
            autoPictureInPicture = prefs.getBoolean(KEY_AUTO_PIP, true)
        )
    )
    val settings: StateFlow<PlayerSettings> = _settings.asStateFlow()

    fun setResumePlayback(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_RESUME, enabled).apply()
        _settings.update { it.copy(resumePlayback = enabled) }
    }

    fun setShowRecentlyPlayed(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_RECENT, enabled).apply()
        _settings.update { it.copy(showRecentlyPlayed = enabled) }
    }

    fun setBackgroundAudio(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BACKGROUND_AUDIO, enabled).apply()
        _settings.update { it.copy(backgroundAudio = enabled) }
    }

    fun setAutoPictureInPicture(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_PIP, enabled).apply()
        _settings.update { it.copy(autoPictureInPicture = enabled) }
    }

    private companion object {
        const val KEY_RESUME = "resume_playback"
        const val KEY_SHOW_RECENT = "show_recently_played"
        const val KEY_BACKGROUND_AUDIO = "background_audio"
        const val KEY_AUTO_PIP = "auto_pip"
    }
}

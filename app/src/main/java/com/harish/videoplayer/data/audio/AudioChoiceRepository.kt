package com.harish.videoplayer.data.audio

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The audio track picked for one video, e.g. language "ta", label "Tamil 5.1", 2nd audio track. */
data class AudioChoice(val language: String?, val label: String?, val audioIndex: Int)

/**
 * Remembers audio choices so you only switch once:
 *  - per video: the exact track you picked (Tamil instead of Telugu in that movie)
 *  - overall: the language of your last pick, used as the default for every other video
 *
 * Example: you switch "Leo.mkv" to Tamil. Next time Leo opens straight in Tamil, and a new
 * movie that also has a Tamil track starts in Tamil too.
 */
@Singleton
class AudioChoiceRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("audio_choices", Context.MODE_PRIVATE)

    private val _preferredLanguage = MutableStateFlow(prefs.getString(KEY_PREFERRED_LANGUAGE, null))
    val preferredLanguage: StateFlow<String?> = _preferredLanguage.asStateFlow()

    fun choiceFor(videoId: Long): AudioChoice? {
        val saved = prefs.getString(videoKey(videoId), null) ?: return null
        val parts = saved.split(SEPARATOR)
        if (parts.size != 3) return null
        return AudioChoice(
            language = parts[0].ifEmpty { null },
            label = parts[1].ifEmpty { null },
            audioIndex = parts[2].toIntOrNull() ?: return null
        )
    }

    fun save(videoId: Long, choice: AudioChoice) {
        val editor = prefs.edit()
            .putString(videoKey(videoId), listOf(choice.language.orEmpty(), choice.label.orEmpty().replace(SEPARATOR, " "), choice.audioIndex).joinToString(SEPARATOR))
        if (!choice.language.isNullOrBlank() && choice.language != "und") {
            editor.putString(KEY_PREFERRED_LANGUAGE, choice.language)
            _preferredLanguage.value = choice.language
        }
        editor.apply()
    }

    /** Settings > "Reset": back to each video's own default track. */
    fun clearPreferredLanguage() {
        prefs.edit().remove(KEY_PREFERRED_LANGUAGE).apply()
        _preferredLanguage.value = null
    }

    private fun videoKey(videoId: Long) = "video_$videoId"

    private companion object {
        const val KEY_PREFERRED_LANGUAGE = "preferred_language"
        const val SEPARATOR = "|"
    }
}

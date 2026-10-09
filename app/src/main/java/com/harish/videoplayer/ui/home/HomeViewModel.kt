package com.harish.videoplayer.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harish.videoplayer.data.resume.ResumeRepository
import com.harish.videoplayer.data.settings.PlayerSettingsRepository
import com.harish.videoplayer.data.settings.ThemeRepository
import com.harish.videoplayer.domain.model.PlayerSettings
import com.harish.videoplayer.data.video.VideoRepository
import com.harish.videoplayer.domain.library.VideoSearchIndex
import com.harish.videoplayer.domain.model.ThemeMode
import com.harish.videoplayer.domain.model.Video
import com.harish.videoplayer.domain.model.VideoSortOrder
import com.harish.videoplayer.domain.resume.ResumeEntry
import com.harish.videoplayer.playback.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the videos screen shows. */
data class HomeUiState(
    val videos: List<Video> = emptyList(),
    val isLoading: Boolean = true,      // first load, nothing cached yet
    val isRefreshing: Boolean = false,  // re-scanning MediaStore
    val sortOrder: VideoSortOrder = VideoSortOrder.DATE_MODIFIED
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val videoRepository: VideoRepository,
    private val playbackController: PlaybackController,
    private val themeRepository: ThemeRepository,
    resumeRepository: ResumeRepository,
    settingsRepository: PlayerSettingsRepository
) : ViewModel() {

    /** Resume / recently played switches (Settings screen). */
    val settings: StateFlow<PlayerSettings> = settingsRepository.settings

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    /** Where you stopped each video: drives the red progress bars and "Recently played". */
    val resume: StateFlow<Map<Long, ResumeEntry>> = resumeRepository.entries

    val themeMode: StateFlow<ThemeMode> = themeRepository.themeMode

    /**
     * Search data, prepared in the background as soon as videos load (and again only when the
     * list changes), so the very first letter you type already searches instantly.
     */
    val searchIndex: StateFlow<VideoSearchIndex?> = _state
        .map { it.videos }
        .distinctUntilChanged()
        .map { videos -> VideoSearchIndex(videos) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // One-time UI messages (shown as a Toast)
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            // 1. Show the cached list straight away
            val cached = videoRepository.loadCached()
            if (cached.isNotEmpty()) _state.update { it.copy(videos = cached, isLoading = false) }
            // 2. Then quietly re-scan MediaStore and swap in the fresh list
            loadVideos(userTriggered = false)
        }
    }

    /** Refresh button: re-scan MediaStore and report what changed. */
    fun refresh() {
        viewModelScope.launch { loadVideos(userTriggered = true) }
    }

    fun setSortOrder(order: VideoSortOrder) = _state.update { it.copy(sortOrder = order) }

    /** Sun / moon button in the header. */
    fun toggleDarkMode(isDarkNow: Boolean) =
        themeRepository.setThemeMode(if (isDarkNow) ThemeMode.LIGHT else ThemeMode.DARK)

    /** Starts [videos] at [index]; the screen then opens the player. */
    fun play(videos: List<Video>, index: Int) = playbackController.play(videos, index)

    private suspend fun loadVideos(userTriggered: Boolean) {
        if (_state.value.isRefreshing) return
        _state.update { it.copy(isRefreshing = true) }

        val before = _state.value.videos
        val fresh = try {
            videoRepository.refresh()
        } catch (e: Exception) {
            _state.update { it.copy(isLoading = false, isRefreshing = false) }
            if (userTriggered) _messages.tryEmit("Couldn't load videos")
            return
        }

        _state.update { it.copy(videos = fresh, isLoading = false, isRefreshing = false) }

        if (userTriggered) {
            val oldIds = before.mapTo(HashSet()) { it.id }
            val added = fresh.count { it.id !in oldIds }
            _messages.tryEmit(
                when {
                    added == 1 -> "1 new video found"
                    added > 1 -> "$added new videos found"
                    else -> "Video list is up to date"
                }
            )
        }
    }
}

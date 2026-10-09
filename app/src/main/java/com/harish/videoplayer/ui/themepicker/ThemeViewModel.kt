package com.harish.videoplayer.ui.themepicker

import androidx.lifecycle.ViewModel
import com.harish.videoplayer.data.settings.ThemeRepository
import com.harish.videoplayer.domain.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val themeRepository: ThemeRepository
) : ViewModel() {
    val themeMode: StateFlow<ThemeMode> = themeRepository.themeMode
    fun onThemeSelected(mode: ThemeMode) = themeRepository.setThemeMode(mode)
}

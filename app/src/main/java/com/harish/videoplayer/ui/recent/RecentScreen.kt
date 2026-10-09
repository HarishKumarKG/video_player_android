package com.harish.videoplayer.ui.recent

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.harish.videoplayer.R
import com.harish.videoplayer.data.resume.ResumeRepository
import com.harish.videoplayer.data.video.VideoRepository
import com.harish.videoplayer.domain.model.Video
import com.harish.videoplayer.domain.model.videoCount
import com.harish.videoplayer.domain.resume.ResumeEntry
import com.harish.videoplayer.domain.resume.recentlyPlayed
import com.harish.videoplayer.playback.PlaybackController
import com.harish.videoplayer.ui.brand.GradientTitle
import com.harish.videoplayer.ui.home.VideoInfoDialog
import com.harish.videoplayer.ui.home.VideoRow
import com.harish.videoplayer.ui.theme.BrandBackground
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class RecentViewModel @Inject constructor(
    videoRepository: VideoRepository,
    private val resumeRepository: ResumeRepository,
    private val playbackController: PlaybackController
) : ViewModel() {

    private val videos = MutableStateFlow<List<Video>>(emptyList())

    init {
        // The videos screen keeps this cache fresh, so reading it is instant
        viewModelScope.launch { videos.value = videoRepository.loadCached() }
    }

    val resume: StateFlow<Map<Long, ResumeEntry>> = resumeRepository.entries

    /** Half-watched videos, most recent first. Finished videos are never in here. */
    val recent: StateFlow<List<Video>> = combine(videos, resumeRepository.entries) { list, entries -> recentlyPlayed(list, entries) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun play(list: List<Video>, index: Int) = playbackController.play(list, index)
    fun remove(videoId: Long) = resumeRepository.clear(videoId)
    fun clearAll() = resumeRepository.clearAll()
}

/**
 * "Recently played": every video you stopped halfway, newest first, each with how much is left.
 * Tap one to continue from that exact spot. A video drops off this list by itself once you
 * watch it to the end.
 */
@Composable
fun RecentScreen(
    onMenuClick: () -> Unit,
    onOpenPlayer: () -> Unit,
    viewModel: RecentViewModel = hiltViewModel()
) {
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val resume by viewModel.resume.collectAsStateWithLifecycle()
    val openPlayer by rememberUpdatedState(onOpenPlayer)
    var infoFor by remember { mutableStateOf<Video?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    BrandBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onMenuClick) {
                    Icon(painterResource(R.drawable.baseline_menu_24), contentDescription = "Open menu")
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    GradientTitle(text = "Recently played", fontSize = 26.sp)
                    Text(
                        "PICK UP WHERE YOU LEFT OFF",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 3.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (recent.isNotEmpty()) {
                    TextButton(onClick = { confirmClear = true }) {
                        Text("Clear all", color = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }

            if (recent.isEmpty()) {
                Text(
                    "Nothing here yet.\n\nVideos you stop halfway show up here, so you can continue " +
                        "them later. Finished videos disappear from this list by themselves.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp)
                )
            } else {
                Text(
                    videoCount(recent.size) + " to continue",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = WindowInsets.navigationBars.asPaddingValues()
                ) {
                    itemsIndexed(recent, key = { _, v -> v.id }, contentType = { _, _ -> "video" }) { i, video ->
                        VideoRow(
                            video = video,
                            resume = resume[video.id],
                            onClick = {
                                viewModel.play(recent, i)
                                openPlayer()
                            },
                            onInfo = { infoFor = video },
                            onRemove = { viewModel.remove(video.id) }
                        )
                    }
                }
            }
        }
    }

    infoFor?.let { VideoInfoDialog(video = it, onDismiss = { infoFor = null }) }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear recently played?") },
            text = { Text("All saved positions are forgotten, so these videos will start from the beginning next time.") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearAll(); confirmClear = false }) {
                    Text("Clear", color = MaterialTheme.colorScheme.tertiary)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }
}

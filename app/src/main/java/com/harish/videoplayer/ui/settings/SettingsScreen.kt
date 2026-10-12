package com.harish.videoplayer.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harish.videoplayer.R
import com.harish.videoplayer.data.audio.AudioChoiceRepository
import com.harish.videoplayer.data.resume.ResumeRepository
import com.harish.videoplayer.data.settings.PlayerSettingsRepository
import com.harish.videoplayer.domain.model.PlayerSettings
import com.harish.videoplayer.ui.brand.GradientTitle
import com.harish.videoplayer.ui.player.SettingSwitchRow
import com.harish.videoplayer.ui.theme.BrandBackground
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: PlayerSettingsRepository,
    private val resumeRepository: ResumeRepository,
    private val audioChoiceRepository: AudioChoiceRepository
) : ViewModel() {
    /** Language code of the audio you picked last ("ta"), or null. */
    val preferredAudioLanguage: StateFlow<String?> = audioChoiceRepository.preferredLanguage
    fun resetPreferredAudioLanguage() = audioChoiceRepository.clearPreferredLanguage()
    val settings: StateFlow<PlayerSettings> = settingsRepository.settings
    fun setResumePlayback(on: Boolean) = settingsRepository.setResumePlayback(on)
    fun setShowRecentlyPlayed(on: Boolean) = settingsRepository.setShowRecentlyPlayed(on)
    fun setBackgroundAudio(on: Boolean) = settingsRepository.setBackgroundAudio(on)
    fun setAutoPictureInPicture(on: Boolean) = settingsRepository.setAutoPictureInPicture(on)
    fun clearRecentlyPlayed() = resumeRepository.clearAll()
}

/** All the switches in one place. Everything starts ON. */
@Composable
fun SettingsScreen(onMenuClick: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val audioLanguage by viewModel.preferredAudioLanguage.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }

    BrandBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onMenuClick) {
                    Icon(painterResource(R.drawable.baseline_menu_24), contentDescription = "Open menu")
                }
                Spacer(Modifier.width(8.dp))
                Column {
                    GradientTitle(text = "Settings")
                    Text(
                        "YOUR PLAYER, YOUR RULES",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 3.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            SettingsCard(title = "Continue watching") {
                SettingSwitchRow(
                    icon = R.drawable.baseline_history_24,
                    title = "Resume where I left off",
                    subtitle = "Every video starts from the spot you stopped at last time",
                    checked = settings.resumePlayback,
                    onCheckedChange = viewModel::setResumePlayback
                )
                SettingSwitchRow(
                    icon = R.drawable.baseline_video_library_24,
                    title = "Show recently played",
                    subtitle = "Half-watched videos on the first screen. Finished ones are left out",
                    checked = settings.showRecentlyPlayed,
                    onCheckedChange = viewModel::setShowRecentlyPlayed
                )
                TextButton(
                    onClick = { confirmClear = true },
                    modifier = Modifier.padding(start = 12.dp, bottom = 4.dp)
                ) {
                    Icon(
                        painterResource(R.drawable.baseline_delete_24),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Clear recently played", color = MaterialTheme.colorScheme.tertiary)
                }
            }

            SettingsCard(title = "Audio") {
                Row(
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painterResource(R.drawable.baseline_audiotrack_24),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Preferred audio language", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            audioLanguage?.let { "${languageName(it)} — videos start in it when they have it" }
                                ?: "Not set — pick a track in the player's 🎵 menu and it's remembered",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (audioLanguage != null) {
                        TextButton(onClick = viewModel::resetPreferredAudioLanguage) {
                            Text("Reset", color = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                }
            }

            SettingsCard(title = "Player") {
                SettingSwitchRow(
                    icon = R.drawable.baseline_picture_in_picture_24,
                    title = "Auto picture-in-picture",
                    subtitle = "Pressing Home while a video plays keeps it in a small floating window",
                    checked = settings.autoPictureInPicture,
                    onCheckedChange = viewModel::setAutoPictureInPicture
                )
                SettingSwitchRow(
                    icon = R.drawable.baseline_headphones_24,
                    title = "Background audio",
                    subtitle = "Keep listening when you lock the phone or leave the app, with controls on the lock screen",
                    checked = settings.backgroundAudio,
                    onCheckedChange = viewModel::setBackgroundAudio
                )
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear recently played?") },
            text = { Text("All saved positions are forgotten, so every video starts from the beginning next time.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearRecentlyPlayed()
                    confirmClear = false
                    Toast.makeText(context, "Recently played cleared", Toast.LENGTH_SHORT).show()
                }) { Text("Clear", color = MaterialTheme.colorScheme.tertiary) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.tertiary,
        modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 8.dp)
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
        tonalElevation = 2.dp
    ) {
        Column(Modifier.padding(vertical = 8.dp)) { content() }
    }
}

/** "ta" -> "Tamil", "te" -> "Telugu"; unknown codes are shown as they are. */
private fun languageName(code: String): String =
    java.util.Locale.forLanguageTag(code).displayLanguage.ifBlank { code }

package com.harish.videoplayer.ui.player

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.harish.videoplayer.R
import com.harish.videoplayer.domain.model.PlaybackSpeeds
import com.harish.videoplayer.domain.model.PlayerSettings
import com.harish.videoplayer.domain.model.speedLabel
import com.harish.videoplayer.playback.TrackOption

/** Which bottom sheet is open over the player. */
enum class PlayerSheet { SPEED, SUBTITLES, AUDIO, SETTINGS }

/** Shared bottom-sheet frame: title + scrollable content (works in landscape too). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerBottomSheet(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            content()
        }
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.tertiary)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun ActionRow(@DrawableRes icon: Int, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun SpeedSheet(current: Float, onSelect: (Float) -> Unit, onDismiss: () -> Unit) {
    PlayerBottomSheet("Playback speed", onDismiss) {
        PlaybackSpeeds.forEach { speed ->
            ChoiceRow(
                label = if (speed == 1f) "${speedLabel(speed)}  (normal)" else speedLabel(speed),
                selected = speed == current,
                onClick = { onSelect(speed); onDismiss() }
            )
        }
    }
}

/**
 * Subtitles: Off, the tracks inside the video (MKV files often have several languages) and
 * any file you loaded, plus "Load subtitle file…" to pick an .srt / .vtt / .ass file.
 */
@Composable
fun SubtitleSheet(
    tracks: List<TrackOption>,
    onSelect: (TrackOption?) -> Unit,
    onLoadFile: () -> Unit,
    onDismiss: () -> Unit
) {
    PlayerBottomSheet("Subtitles", onDismiss) {
        ChoiceRow("Off", selected = tracks.none { it.isSelected }, onClick = { onSelect(null); onDismiss() })
        tracks.forEach { track ->
            ChoiceRow(track.label, selected = track.isSelected, onClick = { onSelect(track); onDismiss() })
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        ActionRow(R.drawable.baseline_folder_24, "Load subtitle file…") { onLoadFile(); onDismiss() }
        Text(
            "Tip: .srt, .vtt and .ass files work. Keep them next to your videos to find them quickly.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}

@Composable
fun AudioTrackSheet(tracks: List<TrackOption>, onSelect: (TrackOption) -> Unit, onDismiss: () -> Unit) {
    PlayerBottomSheet("Audio track", onDismiss) {
        tracks.forEach { track ->
            if (track.isSupported) {
                ChoiceRow(track.label, selected = track.isSelected, onClick = { onSelect(track); onDismiss() })
            } else {
                Text(
                    "${track.label}  ·  can't play on this phone",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp)
                )
            }
        }
    }
}

@Composable
fun PlayerSettingsSheet(
    settings: PlayerSettings,
    onBackgroundAudioChange: (Boolean) -> Unit,
    onAutoPipChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    PlayerBottomSheet("Player settings", onDismiss) {
        SettingSwitchRow(
            icon = R.drawable.baseline_headphones_24,
            title = "Background audio",
            subtitle = "Keep listening when you lock the phone or leave the app, with controls on the lock screen",
            checked = settings.backgroundAudio,
            onCheckedChange = onBackgroundAudioChange
        )
        SettingSwitchRow(
            icon = R.drawable.baseline_picture_in_picture_24,
            title = "Auto picture-in-picture",
            subtitle = "Pressing Home while a video plays keeps it in a small floating window",
            checked = settings.autoPictureInPicture,
            onCheckedChange = onAutoPipChange
        )
    }
}

/** Icon, title, explanation and a switch. Also used by the Settings screen. */
@Composable
internal fun SettingSwitchRow(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.tertiary)
        )
    }
}

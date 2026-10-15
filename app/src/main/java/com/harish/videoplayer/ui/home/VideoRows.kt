package com.harish.videoplayer.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.harish.videoplayer.R
import com.harish.videoplayer.domain.library.BrowseDir
import com.harish.videoplayer.domain.model.Video
import com.harish.videoplayer.domain.model.formatDuration
import com.harish.videoplayer.domain.model.formatSize
import com.harish.videoplayer.domain.model.resolutionLabel
import com.harish.videoplayer.domain.model.videoCount
import com.harish.videoplayer.domain.resume.ResumeEntry
import com.harish.videoplayer.ui.common.VideoThumbnail
import java.text.DateFormat
import java.util.Date

/** One row: wide thumbnail (duration badge + watched bar), title, details and a ⋮ menu. */
@Composable
internal fun VideoRow(
    video: Video,
    resume: ResumeEntry?,
    onClick: () -> Unit,
    onInfo: () -> Unit,
    onRename: (() -> Unit)? = null, // shown as "Rename"
    onRemove: (() -> Unit)? = null  // shown as "Remove from recently played"
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ThumbnailWithBadges(video, resume, width = 128.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                video.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                listOfNotNull(resolutionLabel(video.width, video.height), formatSize(video.sizeBytes))
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (resume != null) {
                Text(
                    "${formatDuration((resume.durationMs - resume.positionMs).coerceAtLeast(0))} left",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
        VideoMenu(onInfo = onInfo, onRename = onRename, onRemove = onRemove)
    }
}

/** Thumbnail with the duration in the corner and a red bar for how much you've watched. */
@Composable
internal fun ThumbnailWithBadges(video: Video, resume: ResumeEntry?, width: Dp) {
    Box(
        modifier = Modifier
            .size(width, width * 9 / 16)
            .clip(RoundedCornerShape(10.dp))
    ) {
        VideoThumbnail(video, Modifier.fillMaxWidth().fillMaxHeight())
        Text(
            formatDuration(video.durationMs),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Black.copy(alpha = 0.65f))
                .padding(horizontal = 4.dp, vertical = 1.dp)
        )
        if (resume != null) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color.White.copy(alpha = 0.35f))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(resume.progress)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.tertiary)
                )
            }
        }
    }
}

@Composable
private fun VideoMenu(onInfo: () -> Unit, onRename: (() -> Unit)?, onRemove: (() -> Unit)?) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(painterResource(R.drawable.baseline_more_vert_24), contentDescription = "More options")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (onRename != null) {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    leadingIcon = { Icon(painterResource(R.drawable.baseline_edit_24), contentDescription = null) },
                    onClick = {
                        expanded = false
                        onRename()
                    }
                )
            }
            DropdownMenuItem(
                text = { Text("Info") },
                leadingIcon = { Icon(painterResource(R.drawable.baseline_info_24), contentDescription = null) },
                onClick = {
                    expanded = false
                    onInfo()
                }
            )
            if (onRemove != null) {
                DropdownMenuItem(
                    text = { Text("Remove from recently played") },
                    leadingIcon = { Icon(painterResource(R.drawable.baseline_delete_24), contentDescription = null) },
                    onClick = {
                        expanded = false
                        onRemove()
                    }
                )
            }
        }
    }
}

/** A folder (or a storage) in the browser: icon tile, name, and how many videos are inside. */
@Composable
internal fun FolderRow(dir: BrowseDir, onClick: () -> Unit) {
    val icon = when {
        !dir.isStorageRoot -> R.drawable.baseline_folder_24
        dir.label.startsWith("SD card") -> R.drawable.baseline_sd_card_24
        else -> R.drawable.baseline_smartphone_24
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.tertiaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(dir.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                videoCount(dir.videoCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            painterResource(R.drawable.baseline_chevron_right_24),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** "Recently played": a sideways strip of videos you didn't finish, most recent first. */
@Composable
internal fun ContinueWatchingRow(
    videos: List<Video>,
    resume: Map<Long, ResumeEntry>,
    onClick: (index: Int) -> Unit,
    onSeeAll: () -> Unit
) {
    Column(Modifier.padding(top = 8.dp, bottom = 4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painterResource(R.drawable.baseline_history_24),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "Recently played",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onSeeAll) {
                Text("See all", color = MaterialTheme.colorScheme.tertiary)
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(videos.size, key = { videos[it].id }) { index ->
                val video = videos[index]
                Column(
                    modifier = Modifier
                        .width(168.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onClick(index) }
                ) {
                    Box {
                        ThumbnailWithBadges(video, resume[video.id], width = 168.dp)
                        // soft play hint in the middle
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(top = 0.dp)
                                .size(36.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painterResource(R.drawable.baseline_play_arrow_24),
                                contentDescription = null,
                                tint = Color.White
                            )
                        }
                    }
                    Text(
                        video.title,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp)
                    )
                }
            }
        }
    }
}

/** Everything about one file. */
@Composable
internal fun VideoInfoDialog(video: Video, onDismiss: () -> Unit) {
    val rows = listOf(
        "Name" to video.name,
        "Duration" to formatDuration(video.durationMs),
        "Resolution" to (if (video.width > 0) "${video.width} × ${video.height}" else "Unknown") +
            (resolutionLabel(video.width, video.height)?.let { "  ($it)" } ?: ""),
        "Size" to formatSize(video.sizeBytes),
        "Type" to video.mimeType,
        "Modified" to DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(video.dateModifiedSec * 1000)),
        "Location" to video.path
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Video info") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                rows.forEach { (label, value) ->
                    Column {
                        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                        Text(value, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    )
}

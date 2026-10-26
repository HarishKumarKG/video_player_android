package com.harish.videoplayer.ui.home

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harish.videoplayer.R
import com.harish.videoplayer.domain.library.BrowseDir
import com.harish.videoplayer.domain.library.breadcrumbs
import com.harish.videoplayer.domain.library.browse
import com.harish.videoplayer.domain.library.browseParent
import com.harish.videoplayer.domain.library.sortedFor
import com.harish.videoplayer.domain.library.storageRoots
import com.harish.videoplayer.domain.model.Video
import com.harish.videoplayer.domain.model.otherNamesInFolderOf
import com.harish.videoplayer.domain.rename.namesInside
import com.harish.videoplayer.domain.model.VideoSortOrder
import com.harish.videoplayer.domain.model.sortedByOrder
import com.harish.videoplayer.domain.model.videoCount
import com.harish.videoplayer.domain.resume.ResumeEntry
import com.harish.videoplayer.domain.resume.recentlyPlayed
import com.harish.videoplayer.ui.common.RenameFileDialog
import com.harish.videoplayer.ui.theme.BrandBackground
import com.harish.videoplayer.ui.theme.LocalIsDarkTheme

/**
 * The browser: start at your storage (Internal / SD card), tap folders to go deeper,
 * tap a video to play it. Back (or the path bar at the top) goes up again.
 *
 * Example: Internal storage › DCIM › Camera shows the Camera clips; playing one makes the
 * other videos of that folder the "next / previous" list.
 */
@Composable
fun HomeRoute(
    onMenuClick: () -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenRecent: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val resume by viewModel.resume.collectAsStateWithLifecycle()
    val searchIndex by viewModel.searchIndex.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val isDark = LocalIsDarkTheme.current
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    // One "play" action for every list: start the videos, then open the player screen
    val openPlayer by rememberUpdatedState(onOpenPlayer)
    val play: (List<Video>, Int) -> Unit = remember(viewModel) {
        { list, index ->
            viewModel.play(list, index)
            openPlayer()
        }
    }

    var infoFor by remember { mutableStateOf<Video?>(null) }
    var renameFor by remember { mutableStateOf<Video?>(null) }
    var renameFolder by remember { mutableStateOf<BrowseDir?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf<String?>(null) } // null = not searching
    var currentDir by rememberSaveable { mutableStateOf<String?>(null) }  // null = top level

    // With only internal storage there's no point in a one-item "choose storage" list:
    // the top level is the storage itself.
    val roots = remember(state.videos) { state.videos.storageRoots() }
    val singleRoot = roots.singleOrNull()?.path
    val shownDir = currentDir ?: singleRoot

    BackHandler(enabled = searchQuery != null) { searchQuery = null }
    BackHandler(enabled = searchQuery == null && currentDir != null) {
        currentDir = browseParent(currentDir!!)?.takeIf { it != singleRoot }
    }

    val bottomPadding = WindowInsets.navigationBars.asPaddingValues()

    BrandBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
        ) {
            val query = searchQuery
            if (query != null) {
                SearchTopBar(
                    initialQuery = query,
                    onQueryChange = { searchQuery = it },
                    onClose = { searchQuery = null }
                )
                SearchResults(
                    query = query,
                    index = searchIndex,
                    sortOrder = state.sortOrder,
                    resume = resume,
                    onPlay = play,
                    onInfo = { infoFor = it },
                    onRename = { renameFor = it },
                    contentPadding = bottomPadding
                )
            } else {
                HomeHeader(
                    onMenuClick = onMenuClick,
                    isDark = isDark,
                    onToggleTheme = { viewModel.toggleDarkMode(isDark) },
                    isRefreshing = state.isRefreshing,
                    onRefresh = viewModel::refresh,
                    sort = enumHeaderSort(state.sortOrder, { it.label }, viewModel::setSortOrder)
                )
                SearchEntry(onClick = { searchQuery = "" })

                when {
                    state.isLoading && state.videos.isEmpty() -> Centered {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.tertiary)
                    }
                    state.videos.isEmpty() -> Centered {
                        Text(
                            "No videos found on this device.\nTap refresh after adding some.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    else -> {
                        if (shownDir != null) {
                            PathBar(
                                dir = shownDir,
                                onStepClick = { path -> currentDir = path.takeIf { it != singleRoot } },
                                onStorageListClick = if (singleRoot == null) ({ currentDir = null }) else null
                            )
                        }
                        // Slide right when going deeper, left when going back up
                        AnimatedContent(
                            targetState = shownDir,
                            transitionSpec = {
                                val deeper = (targetState?.length ?: 0) > (initialState?.length ?: 0)
                                val dir = if (deeper) 1 else -1
                                (slideInHorizontally(tween(250)) { it / 4 * dir } + fadeIn(tween(250))) togetherWith
                                    (slideOutHorizontally(tween(200)) { -it / 4 * dir } + fadeOut(tween(200)))
                            },
                            label = "browse"
                        ) { dir ->
                            FolderListing(
                                dir = dir,
                                videos = state.videos,
                                roots = roots,
                                sortOrder = state.sortOrder,
                                resume = resume,
                                showRecent = settings.showRecentlyPlayed,
                                onOpenRecent = onOpenRecent,
                                onOpenDir = { currentDir = it.path },
                                onPlay = play,
                                onInfo = { infoFor = it },
                                onRename = { renameFor = it },
                                onRenameFolder = { renameFolder = it },
                                contentPadding = bottomPadding
                            )
                        }
                    }
                }
            }
        }
    }

    infoFor?.let { VideoInfoDialog(video = it, onDismiss = { infoFor = null }) }

    renameFor?.let { video ->
        RenameFileDialog(
            fileName = video.name,
            otherNamesInFolder = remember(video, state.videos) { state.videos.otherNamesInFolderOf(video) },
            rename = { newName -> viewModel.rename(video, newName) },
            onRenamed = { newName ->
                renameFor = null
                viewModel.onRenamed()
                Toast.makeText(context, "Renamed to $newName", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { renameFor = null }
        )
    }

    renameFolder?.let { folder ->
        val parent = folder.path.substringBeforeLast('/')
        RenameFileDialog(
            fileName = folder.path.substringAfterLast('/'),
            isFolder = true,
            otherNamesInFolder = remember(folder, state.videos) { namesInside(parent, state.videos.map { it.path }) },
            rename = { newName -> viewModel.renameFolder(folder.path, newName) },
            onRenamed = { newName ->
                renameFolder = null
                viewModel.onRenamed()
                Toast.makeText(context, "Folder renamed to $newName", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { renameFolder = null }
        )
    }
}

/**
 * One folder's content: "Recently played" (first screen only), then sub-folders, then videos.
 * [dir] == null is the storage list (internal storage, SD card...).
 */
@Composable
private fun FolderListing(
    dir: String?,
    videos: List<Video>,
    roots: List<BrowseDir>,
    sortOrder: VideoSortOrder,
    resume: Map<Long, ResumeEntry>,
    showRecent: Boolean,
    onOpenRecent: () -> Unit,
    onOpenDir: (BrowseDir) -> Unit,
    onPlay: (List<Video>, Int) -> Unit,
    onInfo: (Video) -> Unit,
    onRename: (Video) -> Unit,
    onRenameFolder: (BrowseDir) -> Unit,
    contentPadding: PaddingValues
) {
    val listing = remember(videos, dir, sortOrder) {
        if (dir == null) roots to emptyList()
        else videos.browse(dir).let { it.dirs.sortedFor(sortOrder) to it.videos.sortedByOrder(sortOrder) }
    }
    val (dirs, here) = listing
    val isTopLevel = dir == null || roots.singleOrNull()?.path == dir
    // "Recently played" strip: first screen only, half-watched videos only
    val continueWatching = remember(videos, resume, isTopLevel, showRecent) {
        if (!isTopLevel || !showRecent) emptyList() else recentlyPlayed(videos, resume)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding
    ) {
        if (continueWatching.isNotEmpty()) {
            item(key = "continue", contentType = "continue") {
                ContinueWatchingRow(
                    videos = continueWatching,
                    resume = resume,
                    onClick = { onPlay(continueWatching, it) },
                    onSeeAll = onOpenRecent
                )
            }
        }
        if (dirs.isNotEmpty()) {
            item(key = "dirs-label", contentType = "label") {
                SectionLabel(if (dir == null) "Storage" else if (dirs.size == 1) "1 folder" else "${dirs.size} folders")
            }
            items(dirs, key = { "dir:" + it.path }, contentType = { "dir" }) { browseDir ->
                FolderRow(
                    dir = browseDir,
                    onClick = { onOpenDir(browseDir) },
                    // storages (Internal storage, SD card) can't be renamed
                    onRename = if (browseDir.isStorageRoot) null else ({ onRenameFolder(browseDir) })
                )
            }
        }
        if (dirs.isEmpty() && here.isEmpty()) {
            item(key = "empty", contentType = "label") {
                SectionLabel("No videos in this folder any more. Go back or tap refresh.")
            }
        }
        if (here.isNotEmpty()) {
            item(key = "videos-label", contentType = "label") {
                SectionLabel("${videoCount(here.size)} · ${sortOrder.label}")
            }
            itemsIndexed(here, key = { _, v -> v.id }, contentType = { _, _ -> "video" }) { i, video ->
                VideoRow(
                    video = video,
                    resume = resume[video.id],
                    onClick = { onPlay(here, i) },
                    onInfo = { onInfo(video) },
                    onRename = { onRename(video) }
                )
            }
        }
    }
}

/**
 * "Internal storage › DCIM › Camera" — scrolls sideways for deep paths and always shows the
 * current folder. Tap any step to jump back to it.
 */
@Composable
private fun PathBar(dir: String, onStepClick: (String) -> Unit, onStorageListClick: (() -> Unit)?) {
    val steps = remember(dir) { breadcrumbs(dir) }
    val listState = rememberLazyListState()
    // Keep the current folder (the last step) in view
    val lastItem = steps.lastIndex + if (onStorageListClick != null) 1 else 0
    LaunchedEffect(lastItem) { listState.animateScrollToItem(lastItem) }

    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onStorageListClick != null) {
            item(key = "storage") {
                Icon(
                    painterResource(R.drawable.baseline_sd_card_24),
                    contentDescription = "All storage",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onStorageListClick)
                        .padding(6.dp)
                        .size(18.dp)
                )
            }
        }
        itemsIndexed(steps, key = { _, step -> step.first }) { index, (path, label) ->
            val isCurrent = index == steps.lastIndex
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (index > 0 || onStorageListClick != null) {
                    Icon(
                        painterResource(R.drawable.baseline_chevron_right_24),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    color = if (isCurrent) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = !isCurrent) { onStepClick(path) }
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
    )
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) { content() }
}

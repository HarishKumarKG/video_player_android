package com.harish.videoplayer.ui.player

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import com.harish.videoplayer.cast.CastState
import com.harish.videoplayer.domain.model.AspectMode
import com.harish.videoplayer.domain.model.formatDuration
import com.harish.videoplayer.domain.subtitle.SubtitleFormat
import com.harish.videoplayer.ui.common.blockTouchesBehind
import kotlinx.coroutines.delay

/** How long the controls stay up after the last touch while the video plays. */
private const val CONTROLS_TIMEOUT_MS = 3_500L

/** A full-width sideways swipe moves this far through the video (shorter videos: their length). */
private const val SWIPE_SEEK_WINDOW_MS = 120_000L

@Composable
fun PlayerRoute(onClose: () -> Unit, viewModel: PlayerViewModel = hiltViewModel()) {
    // Nothing loaded (e.g. Android closed the app in the background and restored this screen).
    // Checked once on entry: while the screen animates away after closing, the list is empty
    // on purpose and must not trigger a second "close".
    val hasVideo = remember { viewModel.player.mediaItemCount > 0 }
    if (!hasVideo) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    PlayerScreen(viewModel = viewModel, onClose = {
        viewModel.close()
        onClose()
    })
}

@Composable
private fun PlayerScreen(viewModel: PlayerViewModel, onClose: () -> Unit) {
    val player = viewModel.player
    val ui = rememberPlayerUiState(player)
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val resumeNotice by viewModel.resumeNotice.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val isInPip = rememberIsInPipMode()
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    var locked by rememberSaveable { mutableStateOf(false) }
    var aspect by rememberSaveable { mutableStateOf(AspectMode.FIT) }
    var sheet by rememberSaveable { mutableStateOf<PlayerSheet?>(null) }
    var hud by remember { mutableStateOf<GestureHud?>(null) }
    var interactions by remember { mutableIntStateOf(0) } // bumps restart the auto-hide timer
    fun poke() { interactions++ }

    // ---------- Phone-level effects ----------
    ImmersiveModeEffect(enabled = !isInPip)
    KeepScreenOnEffect(keepOn = ui.isPlaying)
    OrientationResetEffect()
    val brightness = rememberBrightnessController()
    val volume = rememberVolumeController(getBoost = viewModel::volumeBoost, setBoost = viewModel::setVolumeBoost)
    BackHandler {
        if (locked) { controlsVisible = true; poke() } else onClose()
    }

    // ---------- Casting to a TV ----------
    val castState by viewModel.castState.collectAsStateWithLifecycle()
    val castDevices by viewModel.castDevices.collectAsStateWithLifecycle()
    val castSearching by viewModel.castSearching.collectAsStateWithLifecycle()
    val castWarning by viewModel.castWarning.collectAsStateWithLifecycle()
    val casting = castState !is CastState.Idle
    var showCastSheet by rememberSaveable { mutableStateOf(false) }

    // Android 17+: finding TVs on the Wi-Fi needs the "local network" permission
    val askLocalNetwork = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) showCastSheet = true
        else Toast.makeText(context, "Allow access to devices on your network to find TVs", Toast.LENGTH_LONG).show()
    }
    val openCastSheet = {
        if (Build.VERSION.SDK_INT >= 37 &&
            context.checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED
        ) {
            askLocalNetwork.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
        } else {
            showCastSheet = true
        }
    }
    // While the TV plays, the phone stays quiet
    LaunchedEffect(casting) { if (casting) player.pause() }
    LaunchedEffect(viewModel) {
        viewModel.castMessages.collect { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }
    LaunchedEffect(viewModel) {
        viewModel.castHandBack.collect {
            viewModel.continueOnPhone(it)
            controlsVisible = true
        }
    }
    // Opened another video while already casting: send that one to the TV too
    LaunchedEffect(Unit) {
        val device = when (val s = castState) {
            is CastState.Casting -> s.device.takeIf { s.video.id != viewModel.currentVideoId() }
            is CastState.Connecting -> s.device.takeIf { s.video.id != viewModel.currentVideoId() }
            CastState.Idle -> null
        }
        if (device != null) viewModel.castTo(device)
    }

    // Orientation follows the phone (AUTO) until the rotate button locks it
    var orientationMode by rememberSaveable {
        mutableStateOf(if (OrientationMemory.locked) OrientationMode.LOCKED else OrientationMode.AUTO)
    }
    LaunchedEffect(orientationMode) {
        activity?.applyOrientation(orientationMode)
        OrientationMemory.locked = orientationMode != OrientationMode.AUTO
    }

    // Video finished: bring the controls back so the replay button is right there
    LaunchedEffect(ui.hasEnded) {
        if (ui.hasEnded) controlsVisible = true
    }

    // Controls hide themselves a few seconds after the last touch (only while playing)
    LaunchedEffect(controlsVisible, ui.isPlaying, interactions, sheet) {
        if (controlsVisible && ui.isPlaying && sheet == null) {
            delay(CONTROLS_TIMEOUT_MS)
            controlsVisible = false
        }
    }

    // Picture-in-picture (floating window)
    val enterPip = PictureInPictureEffect(
        autoEnter = settings.autoPictureInPicture && !casting, // no floating window while the TV plays
        isPlaying = ui.isPlaying,
        videoWidth = ui.videoWidth,
        videoHeight = ui.videoHeight,
        onPlayPause = viewModel::togglePlayPause,
        onRewind = viewModel::rewind,
        onForward = viewModel::forward
    )

    // Background: keep sound (setting) or pause. Closing the floating window (✕) always pauses.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> if (activity != null && !activity.isChangingConfigurations) {
                    // Android stops the activity while it is still "in PiP" when the user taps ✕
                    viewModel.onAppHidden(closedFloatingWindow = activity.isInPictureInPictureMode)
                }
                Lifecycle.Event.ON_START -> viewModel.onAppVisible()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // "Continued from 12:34 · Start over" message
    var resumeMessage by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(resumeNotice) {
        resumeNotice?.let {
            resumeMessage = it
            viewModel.consumeResumeNotice()
        }
    }
    LaunchedEffect(resumeMessage) {
        if (resumeMessage != null) {
            delay(6_000)
            resumeMessage = null
        }
    }

    // Subtitle file picker (system file browser)
    val pickSubtitle = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = displayNameOf(context, uri)
        if (SubtitleFormat.mimeTypeFor(name) == null) {
            Toast.makeText(context, "That's not a subtitle file (.srt, .vtt, .ass)", Toast.LENGTH_SHORT).show()
        } else {
            viewModel.addSubtitle(uri, name)
            Toast.makeText(context, "Subtitles added", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------- Gestures ----------
    var skipSide by remember { mutableStateOf(Side.LEFT) }
    var skipSeconds by remember { mutableIntStateOf(0) }
    var lastSkipAt by remember { mutableLongStateOf(0L) }
    var scrubStartMs by remember { mutableLongStateOf(-1L) }
    var scrubTargetMs by remember { mutableLongStateOf(0L) }

    val gestures = remember(viewModel, brightness, volume) {
        PlayerGestureHandlers(
            onTap = {
                controlsVisible = !controlsVisible
                poke()
            },
            onDoubleTap = { side ->
                val now = SystemClock.elapsedRealtime()
                // Quick double-taps on the same side add up: -10s, -20s, -30s...
                skipSeconds = if (side == skipSide && now - lastSkipAt < 1_000) skipSeconds + 10 else 10
                skipSide = side
                lastSkipAt = now
                if (side == Side.LEFT) viewModel.rewind() else viewModel.forward()
                hud = GestureHud.Skip(side, skipSeconds)
            },
            onVerticalDrag = { side, delta ->
                hud = if (side == Side.LEFT && brightness != null) {
                    brightness.change(delta * 1.2f)
                    GestureHud.Brightness(brightness.level)
                } else {
                    if (hud !is GestureHud.Volume) volume.sync() // phone buttons may have changed it
                    volume.change(delta * 1.2f)
                    GestureHud.Volume(volume.level)
                }
            },
            onHorizontalDrag = { delta ->
                val duration = player.duration
                if (duration != C.TIME_UNSET && duration > 0) { // live / unknown length: can't scrub
                    if (scrubStartMs < 0) {
                        scrubStartMs = player.currentPosition
                        scrubTargetMs = scrubStartMs
                    }
                    val window = minOf(duration, SWIPE_SEEK_WINDOW_MS)
                    scrubTargetMs = (scrubTargetMs + (delta * window).toLong()).coerceIn(0L, duration)
                    hud = GestureHud.Seek(scrubTargetMs, scrubTargetMs - scrubStartMs)
                }
            },
            onDragEnd = {
                if (scrubStartMs >= 0) {
                    viewModel.seekTo(scrubTargetMs)
                    scrubStartMs = -1L
                }
                hud = null
            }
        )
    }
    // Skip bubble fades a moment after the last double tap
    LaunchedEffect(hud) {
        if (hud is GestureHud.Skip) {
            delay(700)
            hud = null
        }
    }

    val audioTracks = remember(ui.tracks) { viewModel.audioTracks(ui.tracks) }

    // ---------- Layout ----------
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .blockTouchesBehind()
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false            // our own Compose controls are drawn on top
                    setKeepContentOnPlayerReset(true) // no black flash when switching videos
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    setBackgroundColor(AndroidColor.BLACK)
                    this.player = player
                }
            },
            update = { view ->
                view.player = player
                view.resizeMode = when (aspect) {
                    AspectMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    AspectMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    AspectMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                }
            },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize()
        )

        if (!isInPip) {
            // Invisible layer that catches the swipes and taps
            Box(
                Modifier
                    .fillMaxSize()
                    .playerGestures(enabled = !locked && !casting, handlers = gestures)
            )

            AnimatedVisibility(visible = controlsVisible && !locked && !casting, enter = fadeIn(), exit = fadeOut()) {
                PlayerControls(
                    ui = ui,
                    aspect = aspect,
                    showAudioButton = audioTracks.isNotEmpty(),
                    orientationLocked = orientationMode != OrientationMode.AUTO,
                    actions = PlayerControlActions(
                        onBack = onClose,
                        onPlayPause = { viewModel.togglePlayPause(); poke() },
                        onRewind = { viewModel.rewind(); poke() },
                        onForward = { viewModel.forward(); poke() },
                        onNext = { viewModel.next(); poke() },
                        onPrevious = { viewModel.previous(); poke() },
                        onSeek = { viewModel.seekTo(it); poke() },
                        onLock = { locked = true; controlsVisible = false },
                        onAspect = {
                            aspect = aspect.next()
                            Toast.makeText(context, aspect.label, Toast.LENGTH_SHORT).show()
                            poke()
                        },
                        onRotate = {
                            // Auto -> lock to the OTHER side; locked -> back to following the phone
                            orientationMode = when (orientationMode) {
                                OrientationMode.AUTO -> if (isLandscape) OrientationMode.PORTRAIT else OrientationMode.LANDSCAPE
                                // Rotation is locked: turn to the other side and stay locked there
                                OrientationMode.LOCKED -> if (isLandscape) OrientationMode.PORTRAIT else OrientationMode.LANDSCAPE
                                else -> OrientationMode.AUTO
                            }
                            Toast.makeText(
                                context,
                                when (orientationMode) {
                                    OrientationMode.AUTO -> "Auto-rotate"
                                    OrientationMode.LANDSCAPE -> "Landscape (locked)"
                                    OrientationMode.PORTRAIT -> "Portrait (locked)"
                                    OrientationMode.LOCKED -> "Rotation locked"
                                },
                                Toast.LENGTH_SHORT
                            ).show()
                            poke()
                        },
                        onOrientationLock = {
                            orientationMode = if (orientationMode == OrientationMode.AUTO) OrientationMode.LOCKED else OrientationMode.AUTO
                            Toast.makeText(
                                context,
                                if (orientationMode == OrientationMode.AUTO) "Auto-rotate on" else "Rotation locked",
                                Toast.LENGTH_SHORT
                            ).show()
                            poke()
                        },
                        onSubtitles = { sheet = PlayerSheet.SUBTITLES },
                        onAudio = { sheet = PlayerSheet.AUDIO },
                        onSpeed = { sheet = PlayerSheet.SPEED },
                        onPictureInPicture = enterPip?.let { enter -> { enter() } },
                        onSettings = { sheet = PlayerSheet.SETTINGS },
                        onCast = { openCastSheet() },
                        onInteraction = ::poke
                    )
                )
            }

            if (locked && controlsVisible) {
                UnlockButton(
                    onUnlock = { locked = false; poke() },
                    modifier = Modifier.align(Alignment.CenterStart)
                )
            }

            hud?.let { current ->
                GestureHudView(
                    hud = current,
                    modifier = when {
                        current is GestureHud.Skip && current.side == Side.LEFT -> Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 48.dp)
                        current is GestureHud.Skip -> Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 48.dp)
                        else -> Modifier.align(Alignment.Center)
                    }
                )
            }

            resumeMessage?.let { position ->
                ResumeBanner(
                    positionMs = position,
                    onStartOver = {
                        viewModel.startOver()
                        resumeMessage = null
                    },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .windowInsetsPadding(WindowInsets.displayCutout)
                        .padding(top = 72.dp)
                )
            }

            // The video has sound tracks, but none of them can play: say so instead of staying silent
            if (ui.error == null && audioTracks.isNotEmpty() && audioTracks.none { it.isSupported }) {
                ErrorBanner(
                    message = "No sound: this phone can't play the audio (${audioTracks.first().label})",
                    hasNext = false,
                    onNext = {},
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp)
                )
            }

            ui.error?.let { message ->
                ErrorBanner(
                    message = message,
                    hasNext = ui.hasNext,
                    onNext = viewModel::next,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // Playing on a TV: the phone becomes the remote control
            if (casting) {
                CastingPanel(
                    state = castState,
                    actions = CastPanelActions(
                        onClose = onClose,
                        onPlayPause = viewModel::castPlayPause,
                        onSkip = viewModel::castSkip,
                        onSeek = viewModel::castSeek,
                        onPrevious = viewModel::castPrevious,
                        onNext = viewModel::castNext,
                        onVolume = viewModel::castVolume,
                        onPickTv = { openCastSheet() },
                        onStopCasting = viewModel::stopCasting
                    )
                )
            }
        }
    }

    if (showCastSheet) {
        CastDevicesSheet(
            devices = castDevices,
            searching = castSearching,
            castState = castState,
            onStartSearch = viewModel::startCastSearch,
            onStopSearch = viewModel::stopCastSearch,
            onPick = { device ->
                showCastSheet = false
                viewModel.castTo(device)
            },
            onStopCasting = {
                showCastSheet = false
                viewModel.stopCasting()
            },
            onDismiss = { showCastSheet = false }
        )
    }

    // The picked TV can't play this file's picture or sound: ask first
    castWarning?.let { pending ->
        CastWarningDialog(
            warning = pending.warning,
            onCastAnyway = viewModel::confirmCast,
            onCancel = viewModel::cancelCast
        )
    }

    // Auto-hide pauses while the lock bubble is up
    LaunchedEffect(locked, controlsVisible) {
        if (locked && controlsVisible) {
            delay(2_500)
            controlsVisible = false
        }
    }

    // ---------- Sheets ----------
    when (sheet) {
        PlayerSheet.SPEED -> SpeedSheet(
            current = ui.speed,
            onSelect = viewModel::setSpeed,
            onDismiss = { sheet = null }
        )
        PlayerSheet.SUBTITLES -> SubtitleSheet(
            tracks = viewModel.subtitleTracks(ui.tracks),
            onSelect = viewModel::selectSubtitle,
            onLoadFile = { pickSubtitle.launch(arrayOf("*/*")) },
            onDismiss = { sheet = null }
        )
        PlayerSheet.AUDIO -> AudioTrackSheet(
            tracks = viewModel.audioTracks(ui.tracks),
            onSelect = viewModel::selectAudio,
            onDismiss = { sheet = null }
        )
        PlayerSheet.SETTINGS -> PlayerSettingsSheet(
            settings = settings,
            onBackgroundAudioChange = viewModel::setBackgroundAudio,
            onAutoPipChange = viewModel::setAutoPictureInPicture,
            onDismiss = { sheet = null }
        )
        null -> Unit
    }
}

@Composable
private fun ResumeBanner(positionMs: Long, onStartOver: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.7f))
            .padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Continued from ${formatDuration(positionMs)}", color = Color.White, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(4.dp))
        TextButton(onClick = onStartOver) {
            Text("Start over", color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ErrorBanner(message: String, hasNext: Boolean, onNext: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .padding(24.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.85f), Color(0xFF3A1416).copy(alpha = 0.9f)))
            )
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(message, color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f, fill = false))
        if (hasNext) {
            TextButton(onClick = onNext) {
                Text("Next video", color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** "Movie.en.srt" for a file picked in the system file browser. */
private fun displayNameOf(context: Context, uri: Uri): String =
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment.orEmpty()

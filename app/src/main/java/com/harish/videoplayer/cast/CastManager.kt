package com.harish.videoplayer.cast

import android.content.Context
import android.util.Log
import com.harish.videoplayer.cast.dlna.DlnaRenderer
import com.harish.videoplayer.cast.dlna.DlnaTv
import com.harish.videoplayer.cast.dlna.SsdpDiscovery
import com.harish.videoplayer.cast.googlecast.GoogleCastDiscovery
import com.harish.videoplayer.data.resume.ResumeRepository
import com.harish.videoplayer.domain.model.Video
import com.harish.videoplayer.playback.contentUri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What casting is doing right now. */
sealed interface CastState {
    data object Idle : CastState
    data class Connecting(val device: CastDevice, val video: Video) : CastState
    data class Casting(
        val device: CastDevice,
        val video: Video,
        val status: RemoteStatus,
        val hasPrevious: Boolean,
        val hasNext: Boolean
    ) : CastState
}

/** Where the TV had got to when you stopped casting, so the phone can carry on from there. */
data class CastHandBack(val videoId: Long, val positionMs: Long)

/**
 * Casting, all in one place.
 *
 * 1. Search: Google Cast devices (via Play services) and DLNA TVs (via SSDP) at the same time.
 * 2. Cast:   share the file through [CastServer] -> tell the TV to open that link.
 * 3. Remote: play / pause / seek / volume / next, and once a second ask the TV where it is,
 *            so the seek bar moves and "Recently played" remembers the spot.
 *
 * It lives as long as the app (not the screen), so casting keeps going when you leave the player.
 */
@Singleton
class CastManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val server: CastServer,
    private val resumeRepository: ResumeRepository,
    private val formatReader: MediaFormatReader
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ssdp = SsdpDiscovery(context)
    private val googleCast by lazy { GoogleCastDiscovery(context) }

    private val dlnaTvs = LinkedHashMap<String, DlnaTv>()
    private var castDevices: List<CastDevice> = emptyList()

    private val _devices = MutableStateFlow<List<CastDevice>>(emptyList())
    val devices: StateFlow<List<CastDevice>> = _devices.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private val _state = MutableStateFlow<CastState>(CastState.Idle)
    val state: StateFlow<CastState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _handBack = MutableSharedFlow<CastHandBack>(extraBufferCapacity = 1)
    val handBack: SharedFlow<CastHandBack> = _handBack.asSharedFlow()

    private var searchJob: Job? = null
    private var castJob: Job? = null
    private var renderer: CastRenderer? = null
    private var queue: List<Video> = emptyList()
    private var index = 0
    private var lastStatus: RemoteStatus? = null

    val isCasting: Boolean get() = _state.value !is CastState.Idle

    // ------------------------------------------------------------------ search

    /** Looks for TVs until [stopSearch] (DLNA asks again every few seconds, Cast updates live). */
    fun startSearch() {
        if (searchJob?.isActive == true) return
        googleCast.start { found ->
            castDevices = found
            publishDevices()
        }
        searchJob = scope.launch {
            _searching.value = true
            try {
                while (isActive) {
                    ssdp.search(timeoutMs = 4_000) { tv ->
                        scope.launch {
                            dlnaTvs[tv.device.id] = tv
                            publishDevices()
                        }
                    }
                    delay(6_000)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "TV search failed", e)
            } finally {
                _searching.value = false
            }
        }
    }

    fun stopSearch() {
        searchJob?.cancel()
        searchJob = null
        if (renderer == null) googleCast.stop()
    }

    private fun publishDevices() {
        _devices.value = (castDevices + dlnaTvs.values.map { it.device }).sortedBy { it.name.lowercase() }
    }

    // ------------------------------------------------------------------ cast

    /** Plays [queue] from [index] on [device], starting at [startPositionMs]. */
    fun cast(device: CastDevice, queue: List<Video>, index: Int, startPositionMs: Long) {
        if (queue.isEmpty()) return
        val previousJob = castJob
        castJob = scope.launch {
            previousJob?.cancelAndJoin()
            val current = renderer
            if (current != null && current.device.id != device.id) {
                saveProgress()
                runCatching { current.disconnect() }
                renderer = null
            }
            this@CastManager.queue = queue
            this@CastManager.index = index.coerceIn(queue.indices)
            _state.value = CastState.Connecting(device, queue[this@CastManager.index])
            try {
                val r = renderer ?: createRenderer(device).also {
                    it.connect()
                    renderer = it
                }
                CastService.start(context, device.name)
                loadCurrent(r, startPositionMs)
                followTv()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Cast failed", e)
                val reason = queue.getOrNull(this@CastManager.index)?.let { reasonTvCantPlay(device, it) }
                _messages.tryEmit(reason ?: e.message ?: "Couldn't cast to ${device.name}")
                finish(stopTv = false, handBack = false)
            }
        }
    }

    fun togglePlayPause() = command {
        if (lastStatus?.state == RemoteState.PLAYING) it.pause() else it.play()
        lastStatus = lastStatus?.copy(state = if (lastStatus?.state == RemoteState.PLAYING) RemoteState.PAUSED else RemoteState.PLAYING)
        publishCasting()
    }

    fun seekTo(positionMs: Long) = command {
        it.seekTo(positionMs.coerceAtLeast(0))
        lastStatus = lastStatus?.copy(positionMs = positionMs)
        publishCasting()
    }

    fun skipBy(deltaMs: Long) = seekTo(((lastStatus?.positionMs ?: 0) + deltaMs).coerceAtLeast(0))

    fun setVolume(volume: Int) = command {
        it.setVolume(volume)
        lastStatus = lastStatus?.copy(volume = volume.coerceIn(0, 100))
        publishCasting()
    }

    fun next() = jump(+1)
    fun previous() = jump(-1)

    private fun jump(step: Int) = command { r ->
        val target = index + step
        if (target !in queue.indices) return@command
        saveProgress()
        index = target
        loadCurrent(r, resumeRepository.positionFor(queue[index].id) ?: 0)
    }

    /** Stops casting; the phone gets the TV's position back (see [handBack]). */
    fun stopCasting() {
        val job = castJob
        castJob = null
        scope.launch {
            job?.cancelAndJoin()
            finish(stopTv = true, handBack = true)
        }
    }

    // ------------------------------------------------------------------ internals

    private fun createRenderer(device: CastDevice): CastRenderer = when (device.kind) {
        CastKind.GOOGLE_CAST -> googleCast.renderer(device)
        CastKind.DLNA -> DlnaRenderer(dlnaTvs[device.id] ?: throw CastException("${device.name} is no longer available"))
    }

    private suspend fun loadCurrent(r: CastRenderer, startPositionMs: Long) {
        val video = queue[index]
        val mime = videoMimeType(video.name, video.mimeType)
        val url = withContext(Dispatchers.IO) { server.share(video.contentUri(), mime, video.name) }
        lastStatus = RemoteStatus(RemoteState.BUFFERING, startPositionMs, video.durationMs)
        publishCasting()
        r.load(CastMedia(url, video.title, mime, video.sizeBytes, video.durationMs), startPositionMs)
    }

    /** Asks the TV once a second what it's doing, until casting stops. */
    private suspend fun followTv() {
        var misses = 0
        var ticks = 0
        while (scope.isActive) {
            delay(1_000)
            val r = renderer ?: return
            val status = r.status()
            if (status == null) {
                if (++misses >= 15) {
                    _messages.tryEmit("Lost the connection to ${r.device.name}")
                    finish(stopTv = false, handBack = true)
                    return
                }
                continue
            }
            misses = 0
            val duration = status.durationMs.takeIf { it > 0 } ?: queue[index].durationMs
            lastStatus = status.copy(durationMs = duration, volume = status.volume ?: lastStatus?.volume)
            publishCasting()
            if (++ticks % 5 == 0) saveProgress()

            when (status.state) {
                RemoteState.FINISHED -> {
                    resumeRepository.clear(queue[index].id)
                    if (index < queue.lastIndex) {
                        index++
                        loadCurrent(r, 0)
                    } else {
                        _messages.tryEmit("Finished playing on ${r.device.name}")
                        finish(stopTv = false, handBack = false)
                        return
                    }
                }
                RemoteState.ERROR -> {
                    val reason = reasonTvCantPlay(r.device, queue[index])
                    _messages.tryEmit(
                        reason?.let { "${r.device.name} couldn't play this video. $it" }
                            ?: "${r.device.name} couldn't play this video. Its format may not be supported by this TV"
                    )
                    finish(stopTv = false, handBack = true)
                    return
                }
                else -> Unit
            }
        }
    }

    /** e.g. "The sound is DTS, which Chromecast can't play." — null when we don't know why. */
    private suspend fun reasonTvCantPlay(device: CastDevice, video: Video): String? {
        if (device.kind != CastKind.GOOGLE_CAST) return null
        return formatReader.read(video.contentUri())?.let(CastCompatibility::explainFailure)
    }

    private fun command(action: suspend (CastRenderer) -> Unit) {
        val r = renderer ?: return
        scope.launch {
            try {
                action(r)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.tryEmit(e.message ?: "The TV didn't respond")
            }
        }
    }

    private fun publishCasting() {
        val r = renderer ?: return
        val status = lastStatus ?: return
        _state.value = CastState.Casting(
            device = r.device,
            video = queue[index],
            status = status,
            hasPrevious = index > 0,
            hasNext = index < queue.lastIndex
        )
    }

    private fun saveProgress() {
        val status = lastStatus ?: return
        val video = queue.getOrNull(index) ?: return
        if (status.positionMs > 0) resumeRepository.save(video.id, status.positionMs, status.durationMs.takeIf { it > 0 } ?: video.durationMs)
    }

    private suspend fun finish(stopTv: Boolean, handBack: Boolean) {
        saveProgress()
        val video = queue.getOrNull(index)
        val position = lastStatus?.positionMs ?: 0
        if (stopTv) runCatching { renderer?.disconnect() }
        renderer = null
        lastStatus = null
        withContext(Dispatchers.IO) { server.stop() }
        CastService.stop(context)
        if (searchJob == null) googleCast.stop()
        _state.value = CastState.Idle
        if (handBack && video != null) _handBack.tryEmit(CastHandBack(video.id, position))
    }

    private companion object {
        const val TAG = "CastManager"
    }
}

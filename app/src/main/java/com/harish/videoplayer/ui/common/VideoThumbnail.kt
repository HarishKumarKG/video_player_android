package com.harish.videoplayer.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.harish.videoplayer.R
import com.harish.videoplayer.domain.model.Video
import com.harish.videoplayer.playback.contentUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * In-memory cache so a frame isn't decoded again every time a row scrolls back into view.
 * Keyed by video id + pixel width, so small list thumbnails and bigger cards don't overwrite
 * each other.
 */
private object ThumbnailCache {
    /**
     * At most 4 decodes at once. A fast fling no longer starts dozens of decodes that fight the
     * UI thread for CPU; rows that scroll away cancel their pending load anyway.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val dispatcher = Dispatchers.IO.limitedParallelism(4)

    private val bitmaps = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount // budget in bytes
    }
    private val missing = java.util.Collections.synchronizedSet(mutableSetOf<Long>())

    private fun key(id: Long, widthPx: Int) = "$id:$widthPx"

    fun get(id: Long, widthPx: Int): Bitmap? = bitmaps.get(key(id, widthPx))

    fun load(context: Context, video: Video, widthPx: Int): Bitmap? {
        get(video.id, widthPx)?.let { return it }
        if (video.id in missing) return null
        return try {
            // API 29+: MediaStore grabs a frame from the video (and caches it system-wide)
            context.contentResolver.loadThumbnail(video.contentUri(), Size(widthPx, widthPx * 9 / 16), null)
                .also { bitmaps.put(key(video.id, widthPx), it) }
        } catch (e: Exception) {
            missing.add(video.id) // broken / unsupported file
            null
        }
    }
}

/**
 * A frame of the video that fills [modifier] (crop), or a film icon while loading / when
 * there is no frame. Give the modifier a size, e.g. `Modifier.size(128.dp, 72.dp)`.
 */
@Composable
fun VideoThumbnail(video: Video, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var sizePx by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .onSizeChanged { sizePx = it },
        contentAlignment = Alignment.Center
    ) {
        // Round the width up to a multiple of 64 px so similar sizes share one cached bitmap
        val widthPx = ((sizePx.width + 63) / 64 * 64).coerceAtLeast(64)
        // key(): when the video changes, throw the old state away so its frame can't stick around
        key(video.id, widthPx) {
            val bitmap by produceState(initialValue = ThumbnailCache.get(video.id, widthPx), sizePx.width) {
                if (value == null && sizePx.width > 0) {
                    value = withContext(ThumbnailCache.dispatcher) { ThumbnailCache.load(context, video, widthPx) }
                }
            }
            val frame = bitmap
            if (frame != null) {
                Image(
                    bitmap = remember(frame) { frame.asImageBitmap() }, // wrap once, not on every redraw
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.baseline_movie_24),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}


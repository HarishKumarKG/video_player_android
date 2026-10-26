package com.harish.videoplayer.cast

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.MetadataRetriever
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads which picture and sound formats a video file contains (H.264, HEVC, AAC, DTS, TrueHD…)
 * without playing it. Uses Media3's own file readers — the same ones the player uses — so MKV
 * files with DTS / TrueHD sound are recognised too.
 */
@Singleton
class MediaFormatReader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /** null if the file couldn't be read (then we simply don't warn). */
    suspend fun read(uri: Uri): MediaFormats? = withContext(Dispatchers.IO) {
        try {
            MetadataRetriever.Builder(context, MediaItem.fromUri(uri)).build().use { retriever ->
                val groups = retriever.retrieveTrackGroups().get(10, TimeUnit.SECONDS)
                val video = mutableListOf<String>()
                val audio = mutableListOf<String>()
                for (i in 0 until groups.length) {
                    val group = groups.get(i)
                    val format = group.getFormat(0)
                    val mime = format.sampleMimeType ?: continue
                    when (group.type) {
                        C.TRACK_TYPE_VIDEO -> video += mime
                        C.TRACK_TYPE_AUDIO -> audio += mime
                    }
                }
                MediaFormats(video, audio)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Couldn't read formats of $uri: ${e.message}")
            null
        }
    }

    private companion object {
        const val TAG = "MediaFormatReader"
    }
}

package com.harish.videoplayer.data.video

import android.content.Context
import android.provider.MediaStore
import com.harish.videoplayer.domain.model.Video
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Videos come from two places:
 *  1. A small JSON cache in the app's private storage -> instant list on app start.
 *  2. MediaStore (the real source)                    -> used to refresh the cache.
 */
@Singleton
class VideoRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val cacheFile = File(context.filesDir, "videos_cache.json")

    /** Last saved list, or empty if the app has never loaded videos before. */
    suspend fun loadCached(): List<Video> = withContext(Dispatchers.IO) {
        if (!cacheFile.exists()) return@withContext emptyList()
        try {
            val array = JSONArray(cacheFile.readText())
            List(array.length()) { i -> array.getJSONObject(i).toVideo() }
        } catch (e: Exception) {
            emptyList() // corrupt/old cache: ignore it, a refresh will rebuild it
        }
    }

    /** Reads MediaStore and saves the result as the new cache. */
    suspend fun refresh(): List<Video> = withContext(Dispatchers.IO) {
        val videos = queryMediaStore()
        saveCache(videos)
        videos
    }

    private fun saveCache(videos: List<Video>) {
        val array = JSONArray().also { array -> videos.forEach { array.put(it.toJson()) } }
        // Write to a temp file then rename, so a crash mid-write can't leave a broken cache
        val tmp = File(cacheFile.parentFile, cacheFile.name + ".tmp")
        tmp.writeText(array.toString())
        tmp.renameTo(cacheFile)
    }

    private fun queryMediaStore(): List<Video> {
        val videos = mutableListOf<Video>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.DATA // file path: used for folders and search
        )
        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val durationCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val widthCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val heightCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
            val mimeCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
            val dataCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)

            while (c.moveToNext()) {
                val path = c.getString(dataCol) ?: ""
                videos.add(
                    Video(
                        id = c.getLong(idCol),
                        name = c.getString(nameCol) ?: path.substringAfterLast('/').ifEmpty { "Video" },
                        durationMs = c.getLong(durationCol),
                        sizeBytes = c.getLong(sizeCol),
                        width = c.getInt(widthCol),
                        height = c.getInt(heightCol),
                        mimeType = c.getString(mimeCol) ?: "video/*",
                        dateModifiedSec = c.getLong(dateCol),
                        path = path
                    )
                )
            }
        }
        return videos
    }
}

private fun Video.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("name", name)
    .put("durationMs", durationMs)
    .put("sizeBytes", sizeBytes)
    .put("width", width)
    .put("height", height)
    .put("mimeType", mimeType)
    .put("dateModifiedSec", dateModifiedSec)
    .put("path", path)

private fun JSONObject.toVideo(): Video = Video(
    id = getLong("id"),
    name = getString("name"),
    durationMs = getLong("durationMs"),
    sizeBytes = getLong("sizeBytes"),
    width = getInt("width"),
    height = getInt("height"),
    mimeType = getString("mimeType"),
    dateModifiedSec = getLong("dateModifiedSec"),
    path = getString("path")
)

package com.harish.videoplayer.playback

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Size
import androidx.media3.common.util.BitmapLoader
import androidx.media3.datasource.DataSourceBitmapLoader
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import java.util.concurrent.Executors

/**
 * Gives the notification / lock screen a picture for the video.
 *
 * Each video's "artwork" is its own content:// URI. A normal image loader can't read a video,
 * so for those we ask MediaStore for a thumbnail frame instead. Anything else (a real image)
 * goes to Media3's default loader.
 */
class VideoThumbnailBitmapLoader(private val context: Context) : BitmapLoader {

    private val fallback = DataSourceBitmapLoader(context)
    private val executor: ListeningExecutorService =
        MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())

    override fun supportsMimeType(mimeType: String): Boolean = fallback.supportsMimeType(mimeType)

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = fallback.decodeBitmap(data)

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> =
        if (uri.isVideoInMediaStore()) {
            executor.submit<Bitmap> { context.contentResolver.loadThumbnail(uri, Size(512, 288), null) }
        } else {
            fallback.loadBitmap(uri)
        }

    private fun Uri.isVideoInMediaStore(): Boolean =
        scheme == "content" && authority == "media" && pathSegments.contains("video")
}

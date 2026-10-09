package com.harish.videoplayer

import com.harish.videoplayer.domain.model.Video

/** Small helper so tests only spell out the fields they care about. */
fun video(
    id: Long,
    name: String = "Video $id.mp4",
    path: String = "/storage/emulated/0/Movies/$name",
    durationMs: Long = 60_000,
    sizeBytes: Long = 1_000_000,
    width: Int = 1920,
    height: Int = 1080,
    dateModifiedSec: Long = id
) = Video(
    id = id,
    name = name,
    durationMs = durationMs,
    sizeBytes = sizeBytes,
    width = width,
    height = height,
    mimeType = "video/mp4",
    dateModifiedSec = dateModifiedSec,
    path = path
)

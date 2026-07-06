package com.example.gifkeyboard.data

/**
 * Represents one searchable media item: a short looping clip.
 * `videoUrl` points at an MP4/WebM (the actual "GIF with sound" payload);
 * `previewUrl` is a static or silent-looping thumbnail shown in the grid before tap;
 * `hasAudio` lets the UI show a speaker badge so users know which clips are silent vs not.
 */
data class MediaItem(
    val id: String,
    val title: String,
    val previewUrl: String,
    val videoUrl: String,
    val width: Int,
    val height: Int,
    val hasAudio: Boolean,
    val sourceProvider: String = "tenor"
)

/** A named, curated category shown as a chip above the results grid. */
data class MediaCategory(
    val key: String,
    val displayName: String,
    val searchQuery: String
)

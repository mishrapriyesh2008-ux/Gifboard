package com.example.gifkeyboard.media

import com.example.gifkeyboard.data.MediaItem

/**
 * Royalty-free / Creative-Commons sample clips used ONLY when no provider API key
 * is configured, so the project builds and runs end-to-end without any account setup.
 * Replace urls with your own licensed/CC0 hosted clips if you want offline samples
 * baked into the APK under /res/raw instead of fetched at runtime.
 */
object SampleClips {

    private val items = listOf(
        MediaItem(
            id = "sample_clap",
            title = "applause",
            previewUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/ForBiggerBlazes.jpg",
            videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
            width = 480, height = 270, hasAudio = true
        ),
        MediaItem(
            id = "sample_laugh",
            title = "laughing reaction",
            previewUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/ForBiggerEscapes.jpg",
            videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
            width = 480, height = 270, hasAudio = true
        ),
        MediaItem(
            id = "sample_wow",
            title = "wow reaction",
            previewUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/ForBiggerFun.jpg",
            videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4",
            width = 480, height = 270, hasAudio = true
        ),
        MediaItem(
            id = "sample_celebrate",
            title = "celebration",
            previewUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/ForBiggerJoyrides.jpg",
            videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyrides.mp4",
            width = 480, height = 270, hasAudio = true
        ),
        MediaItem(
            id = "sample_facepalm",
            title = "facepalm",
            previewUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/ForBiggerMeltdowns.jpg",
            videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerMeltdowns.mp4",
            width = 480, height = 270, hasAudio = true
        ),
        MediaItem(
            id = "sample_silentgif",
            title = "silent loop",
            previewUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/Sintel.jpg",
            videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
            width = 480, height = 270, hasAudio = false
        )
    )

    fun all(): List<MediaItem> = items

    fun filterByQuery(query: String): List<MediaItem> {
        if (query.isBlank()) return items
        return items.filter { it.title.contains(query, ignoreCase = true) }
            .ifEmpty { items } // never show a dead "no results" state in the sample/demo mode
    }
}

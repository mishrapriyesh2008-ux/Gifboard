package com.example.gifkeyboard.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "media_history")
data class MediaHistoryEntity(
    @PrimaryKey val id: String,
    val title: String,
    val previewUrl: String,
    val videoUrl: String,
    val width: Int,
    val height: Int,
    val hasAudio: Boolean,
    val isFavorite: Boolean,
    val lastUsedAt: Long
)

fun MediaHistoryEntity.toMediaItem() = MediaItem(
    id = id, title = title, previewUrl = previewUrl, videoUrl = videoUrl,
    width = width, height = height, hasAudio = hasAudio
)

fun MediaItem.toHistoryEntity(isFavorite: Boolean, usedAt: Long) = MediaHistoryEntity(
    id = id, title = title, previewUrl = previewUrl, videoUrl = videoUrl,
    width = width, height = height, hasAudio = hasAudio,
    isFavorite = isFavorite, lastUsedAt = usedAt
)

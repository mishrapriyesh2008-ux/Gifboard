package com.example.gifkeyboard.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MediaHistoryEntity)

    @Query("SELECT * FROM media_history ORDER BY lastUsedAt DESC LIMIT 60")
    fun observeRecent(): Flow<List<MediaHistoryEntity>>

    @Query("SELECT * FROM media_history WHERE isFavorite = 1 ORDER BY lastUsedAt DESC")
    fun observeFavorites(): Flow<List<MediaHistoryEntity>>

    @Query("UPDATE media_history SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: String, isFavorite: Boolean)

    @Query("SELECT isFavorite FROM media_history WHERE id = :id LIMIT 1")
    suspend fun isFavorite(id: String): Boolean?

    @Query("DELETE FROM media_history WHERE id = :id")
    suspend fun delete(id: String)
}

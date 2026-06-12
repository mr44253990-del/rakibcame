package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CameraDao {

    // Custom Commands DAOs
    @Query("SELECT * FROM custom_commands ORDER BY id DESC")
    fun getAllCustomCommands(): Flow<List<CustomCommand>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomCommand(command: CustomCommand)

    @Query("DELETE FROM custom_commands WHERE id = :id")
    suspend fun deleteCustomCommandById(id: Int)

    // Captured Media DAOs
    @Query("SELECT * FROM captured_media WHERE isSecure = 0 ORDER BY timestamp DESC")
    fun getPublicMedia(): Flow<List<CapturedMedia>>

    @Query("SELECT * FROM captured_media WHERE isSecure = 1 ORDER BY timestamp DESC")
    fun getSecureMedia(): Flow<List<CapturedMedia>>

    @Query("SELECT * FROM captured_media ORDER BY timestamp DESC")
    fun getAllMedia(): Flow<List<CapturedMedia>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(media: CapturedMedia)

    @Update
    suspend fun updateMedia(media: CapturedMedia)

    @Query("DELETE FROM captured_media WHERE id = :id")
    suspend fun deleteMediaById(id: Int)

    @Query("UPDATE captured_media SET isFavorite = :isFav WHERE id = :id")
    suspend fun setFavorite(id: Int, isFav: Boolean)

    @Query("UPDATE captured_media SET isSecure = :isSec WHERE id = :id")
    suspend fun setSecure(id: Int, isSec: Boolean)

    @Query("SELECT * FROM captured_media WHERE isSecure = 0 AND (detectedObjects LIKE '%' || :query || '%' OR name LIKE '%' || :query || '%' OR detectedScene LIKE '%' || :query || '%') ORDER BY timestamp DESC")
    fun searchMedia(query: String): Flow<List<CapturedMedia>>
}

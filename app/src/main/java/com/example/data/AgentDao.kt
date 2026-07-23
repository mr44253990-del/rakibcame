package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AgentDao {
    @Query("SELECT * FROM controller_profiles ORDER BY updatedAt DESC")
    fun observeProfiles(): Flow<List<ControllerProfile>>

    @Query("SELECT * FROM button_mappings WHERE profileId = :profileId ORDER BY targetButton ASC, sourceLabel ASC")
    fun observeMappings(profileId: Long): Flow<List<ButtonMapping>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: ControllerProfile): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMapping(mapping: ButtonMapping): Long

    @Query("DELETE FROM button_mappings WHERE profileId = :profileId")
    suspend fun clearMappings(profileId: Long)

    @Query("DELETE FROM button_mappings WHERE id = :mappingId")
    suspend fun deleteMapping(mappingId: Long)

    @Query("SELECT COUNT(*) FROM controller_profiles")
    suspend fun getProfileCount(): Int

    @Query("SELECT id FROM controller_profiles ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getMostRecentProfileId(): Long?
}

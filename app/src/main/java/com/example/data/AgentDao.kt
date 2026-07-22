package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AgentDao {
    @Query("SELECT * FROM agent_memories ORDER BY updatedAt DESC")
    fun observeMemories(): Flow<List<AgentMemory>>

    @Query("SELECT * FROM action_logs ORDER BY createdAt DESC LIMIT 150")
    fun observeLogs(): Flow<List<ActionLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: AgentMemory): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ActionLog): Long

    @Query("DELETE FROM action_logs")
    suspend fun clearLogs()
}

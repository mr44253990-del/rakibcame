package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AgentDao {
    @Query("SELECT * FROM chat_sessions ORDER BY updatedAt DESC")
    fun observeSessions(): Flow<List<ChatSession>>

    @Query("SELECT * FROM chat_records WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    fun observeChatRecords(sessionId: String): Flow<List<ChatRecord>>

    @Query("SELECT * FROM agent_memories WHERE sessionId = :sessionId ORDER BY updatedAt DESC")
    fun observeMemories(sessionId: String): Flow<List<AgentMemory>>

    @Query("SELECT * FROM action_logs WHERE sessionId = :sessionId ORDER BY createdAt DESC LIMIT 200")
    fun observeLogs(sessionId: String): Flow<List<ActionLog>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSessionIfAbsent(session: ChatSession): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSession)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatRecord(record: ChatRecord): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: AgentMemory): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ActionLog): Long

    @Query("DELETE FROM action_logs WHERE sessionId = :sessionId")
    suspend fun clearLogs(sessionId: String)
}

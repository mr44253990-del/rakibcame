package com.example.data

import kotlinx.coroutines.flow.Flow

class AgentRepository(private val dao: AgentDao) {
    fun observeSessions(): Flow<List<ChatSession>> = dao.observeSessions()
    fun observeChatRecords(sessionId: String): Flow<List<ChatRecord>> = dao.observeChatRecords(sessionId)
    fun observeMemories(sessionId: String): Flow<List<AgentMemory>> = dao.observeMemories(sessionId)
    fun observeLogs(sessionId: String): Flow<List<ActionLog>> = dao.observeLogs(sessionId)

    suspend fun ensureSession(sessionId: String, title: String) {
        dao.insertSessionIfAbsent(ChatSession(id = sessionId, title = title, updatedAt = System.currentTimeMillis()))
    }

    suspend fun touchSession(sessionId: String, title: String? = null) {
        dao.insertSession(
            ChatSession(
                id = sessionId,
                title = title ?: "Chat",
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun addChatRecord(sessionId: String, role: String, text: String) {
        dao.insertChatRecord(
            ChatRecord(
                sessionId = sessionId,
                role = role,
                text = text.trim()
            )
        )
    }

    suspend fun addMemory(sessionId: String, title: String, content: String) {
        dao.insertMemory(
            AgentMemory(
                sessionId = sessionId,
                title = title,
                content = content.trim()
            )
        )
    }

    suspend fun addLog(sessionId: String, tabId: String, actionType: String, summary: String) {
        dao.insertLog(
            ActionLog(
                sessionId = sessionId,
                tabId = tabId,
                actionType = actionType,
                summary = summary.trim()
            )
        )
    }

    suspend fun clearLogs(sessionId: String) = dao.clearLogs(sessionId)
}

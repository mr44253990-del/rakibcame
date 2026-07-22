package com.example.data

class AgentRepository(private val dao: AgentDao) {
    val memories = dao.observeMemories()
    val logs = dao.observeLogs()

    suspend fun addMemory(title: String, content: String) {
        dao.insertMemory(
            AgentMemory(
                title = title,
                content = content.trim()
            )
        )
    }

    suspend fun addLog(tabId: String, actionType: String, summary: String) {
        dao.insertLog(
            ActionLog(
                tabId = tabId,
                actionType = actionType,
                summary = summary.trim()
            )
        )
    }

    suspend fun clearLogs() = dao.clearLogs()
}

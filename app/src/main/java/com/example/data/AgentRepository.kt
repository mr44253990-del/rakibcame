package com.example.data

import kotlinx.coroutines.flow.Flow

class AgentRepository(private val dao: AgentDao) {
    fun observeProfiles(): Flow<List<ControllerProfile>> = dao.observeProfiles()
    fun observeMappings(profileId: Long): Flow<List<ButtonMapping>> = dao.observeMappings(profileId)

    suspend fun createProfile(name: String): Long {
        return dao.insertProfile(ControllerProfile(name = name.trim()))
    }

    suspend fun upsertMapping(profileId: Long, sourceCode: String, sourceLabel: String, targetButton: String) {
        dao.upsertMapping(
            ButtonMapping(
                profileId = profileId,
                sourceCode = sourceCode,
                sourceLabel = sourceLabel,
                targetButton = targetButton
            )
        )
    }

    suspend fun clearMappings(profileId: Long) = dao.clearMappings(profileId)
    suspend fun deleteMapping(mappingId: Long) = dao.deleteMapping(mappingId)
    suspend fun getProfileCount(): Int = dao.getProfileCount()
    suspend fun getMostRecentProfileId(): Long? = dao.getMostRecentProfileId()
}

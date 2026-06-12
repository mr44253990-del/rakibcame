package com.example.data

import kotlinx.coroutines.flow.Flow

class CameraRepository(private val cameraDao: CameraDao) {

    val publicMedia: Flow<List<CapturedMedia>> = cameraDao.getPublicMedia()
    val secureMedia: Flow<List<CapturedMedia>> = cameraDao.getSecureMedia()
    val customCommands: Flow<List<CustomCommand>> = cameraDao.getAllCustomCommands()

    suspend fun insertCustomCommand(command: CustomCommand) {
        cameraDao.insertCustomCommand(command)
    }

    suspend fun deleteCustomCommand(id: Int) {
        cameraDao.deleteCustomCommandById(id)
    }

    suspend fun insertMedia(media: CapturedMedia) {
        cameraDao.insertMedia(media)
    }

    suspend fun updateMedia(media: CapturedMedia) {
        cameraDao.updateMedia(media)
    }

    suspend fun deleteMedia(id: Int) {
        cameraDao.deleteMediaById(id)
    }

    suspend fun setFavorite(id: Int, isFav: Boolean) {
        cameraDao.setFavorite(id, isFav)
    }

    suspend fun setSecure(id: Int, isSec: Boolean) {
        cameraDao.setSecure(id, isSec)
    }

    fun searchMedia(query: String): Flow<List<CapturedMedia>> {
        return cameraDao.searchMedia(query)
    }
}

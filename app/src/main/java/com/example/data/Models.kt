package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_commands")
data class CustomCommand(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val phrase: String, // e.g., "rakib selfie"
    val cameraSelection: String = "FRONT", // FRONT or BACK
    val timerSeconds: Int = 0, // 0, 3, 5, 10
    val filterType: String = "BEAUTY", // BEAUTY, CINEMATIC, NONE, HDR
    val resolution: String = "1080P", // 4K, 1080P, 720P
    val frameRate: Int = 30, // 30, 60
    val stabilization: Boolean = false,
    val isSystem: Boolean = false,
    val zoomLevel: Float = 1.0f,
    val actionType: String = "PHOTO" // PHOTO or VIDEO
)

@Entity(tableName = "captured_media")
data class CapturedMedia(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String, // e.g. "IMG_20260612_102030.jpg"
    val uriPath: String, // Simulated or actual file URI/Path
    val isVideo: Boolean = false,
    val isPanorama: Boolean = false, // 360/VR Panorama
    val timestamp: Long = System.currentTimeMillis(),
    val detectedObjects: String = "", // Comma-separated list for Offline AI Search, e.g., "Person, Dog, Coffee"
    val detectedScene: String = "Auto", // Scene: Sunset, Food, Landscape, Night, Sky, etc.
    val isFavorite: Boolean = false,
    val isSecure: Boolean = false, // Secure Folder/App Lock
    val fileSizeBytes: Long = 1024 * 1024 * 2 // simulated size, say 2MB
)

package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "controller_profiles")
data class ControllerProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "button_mappings",
    indices = [Index(value = ["profileId", "sourceCode"], unique = true)]
)
data class ButtonMapping(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val sourceCode: String,
    val sourceLabel: String,
    val targetButton: String,
    val updatedAt: Long = System.currentTimeMillis()
)

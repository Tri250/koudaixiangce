package com.alcedo.studio.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.alcedo.studio.data.model.Adjustments
import com.alcedo.studio.data.model.EditHistoryEntry

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sourceUri: String = "",
    val displayName: String = "",
    val adjustments: Adjustments = Adjustments.Default,
    val editHistory: List<EditHistoryEntry> = emptyList(),
    val version: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastEditedAt: Long = System.currentTimeMillis(),
    val rating: Int = 0,
    val colorLabel: String? = null,
    val isDeleted: Boolean = false,
)

@Entity(tableName = "presets")
data class PresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String = "",
    val description: String = "",
    val category: String = "custom",
    val adjustments: Adjustments = Adjustments.Default,
    val thumbnail: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isBuiltIn: Boolean = false,
    val isFavorite: Boolean = false,
    val usageCount: Int = 0,
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val mediaStoreId: Long = 0L,
    val uri: String = "",
    val displayName: String = "",
    val addedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "export_jobs")
data class ExportJobEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sourceUri: String = "",
    val outputUri: String = "",
    val settingsJson: String = "",
    val status: Int = 0,
    val progress: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long = 0L,
    val errorMessage: String? = null,
)

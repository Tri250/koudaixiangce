package com.rapidraw.data.db

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

@Immutable
@Entity(
    tableName = "projects",
    indices = [Index(value = ["modifiedAt"])],
)
@TypeConverters(RecipeConverters::class)
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis(),
    val imagePaths: List<String> = emptyList(),
    val thumbnailPath: String? = null,
    val settingsJson: String? = null,
)

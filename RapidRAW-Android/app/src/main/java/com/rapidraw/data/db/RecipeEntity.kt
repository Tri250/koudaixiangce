package com.rapidraw.data.db

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

@Immutable
@Entity(
    tableName = "recipes",
    indices = [Index(value = ["createdAt"]), Index(value = ["isLocal"])],
)
@TypeConverters(RecipeConverters::class)
data class RecipeEntity(
    @PrimaryKey val shareCode: String,
    val name: String,
    val author: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val adjustmentsJson: String,
    val filmId: String? = null,
    val filmIntensity: Float = 1.0f,
    val isLocal: Boolean = true,
)

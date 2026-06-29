package com.rapidraw.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

@Entity(tableName = "favorites")
@TypeConverters(FavoriteConverters::class)
data class FavoriteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val imagePath: String,
    val rating: Int = 0,
    val colorLabel: String = "",
    val isFavorite: Boolean = false,
    val tags: List<String> = emptyList(),
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

package com.rapidraw.data.db

import androidx.room.TypeConverter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class RecipeConverters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromFloatList(list: List<Float>): String = list.joinToString(",")

    @TypeConverter
    fun toFloatList(value: String): List<Float> {
        if (value.isEmpty()) return emptyList()
        return value.split(",").map { it.toFloatOrNull() ?: 0f }
    }

    @TypeConverter
    fun fromStringList(list: List<String>): String = json.encodeToString(list)

    @TypeConverter
    fun toStringList(value: String): List<String> {
        if (value.isEmpty()) return emptyList()
        return try {
            json.decodeFromString(value)
        } catch (_: Exception) {
            emptyList()
        }
    }
}

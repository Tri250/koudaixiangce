package com.rapidraw.data.db

import androidx.room.TypeConverter

class RecipeConverters {
    @TypeConverter
    fun fromFloatList(list: List<Float>): String = list.joinToString(",")

    @TypeConverter
    fun toFloatList(value: String): List<Float> {
        if (value.isEmpty()) return emptyList()
        return value.split(",").map { it.toFloatOrNull() ?: 0f }
    }
}

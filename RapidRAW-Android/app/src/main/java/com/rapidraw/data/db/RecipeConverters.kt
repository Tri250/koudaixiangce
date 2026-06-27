package com.rapidraw.data.db

import androidx.room.TypeConverter

class RecipeConverters {
    @TypeConverter
    fun fromFloatList(value: List<Float>): String = value.joinToString(",")

    @TypeConverter
    fun toFloatList(value: String): List<Float> =
        if (value.isEmpty()) emptyList() else value.split(",").map { it.toFloat() }
}

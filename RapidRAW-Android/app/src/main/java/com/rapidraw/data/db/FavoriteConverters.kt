package com.rapidraw.data.db

import androidx.room.TypeConverter

class FavoriteConverters {
    @TypeConverter
    fun fromStringList(list: List<String>): String = list.joinToString(",")

    @TypeConverter
    fun toStringList(value: String): List<String> {
        if (value.isEmpty()) return emptyList()
        return value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }
}

package com.alcedo.studio.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
data class Preset(
    val id: Long = 0L,
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
) : Parcelable {
    companion object {
        val Empty = Preset()
    }
}

@Serializable
@Parcelize
data class Project(
    val id: Long = 0L,
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
) : Parcelable

@Serializable
@Parcelize
data class EditHistoryEntry(
    val id: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val description: String = "",
    val adjustments: Adjustments = Adjustments.Default,
    val thumbnail: String? = null,
) : Parcelable

@Serializable
@Parcelize
data class SmartAlbum(
    val id: Long = 0L,
    val name: String = "",
    val query: String = "",
    val filterType: Int = FILTER_RATING,
    val filterValue: String = "",
    val isBuiltIn: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
) : Parcelable {
    companion object {
        const val FILTER_RATING = 0
        const val FILTER_COLOR_LABEL = 1
        const val FILTER_FAVORITE = 2
        const val FILTER_DATE = 3
        const val FILTER_FORMAT = 4
        const val FILTER_CAMERA = 5
        const val FILTER_KEYWORD = 6
    }
}

@Serializable
@Parcelize
data class FilmSimulation(
    val id: String = "",
    val name: String = "",
    val brand: String = "",
    val description: String = "",
    val adjustments: Adjustments = Adjustments.Default,
    val isBuiltIn: Boolean = true,
) : Parcelable {
    companion object {
        val BuiltInList = listOf(
            FilmSimulation(
                id = "kodak_portra_400",
                name = "Kodak Portra 400",
                brand = "Kodak",
                description = "经典人像负片，肤色表现自然",
                adjustments = Adjustments(
                    filmId = "kodak_portra_400",
                    filmIntensity = 1f,
                    saturation = -8f,
                    contrast = 5f,
                )
            ),
            FilmSimulation(
                id = "kodak_ektar_100",
                name = "Kodak Ektar 100",
                brand = "Kodak",
                description = "高饱和度负片，色彩鲜艳",
                adjustments = Adjustments(
                    filmId = "kodak_ektar_100",
                    filmIntensity = 1f,
                    saturation = 15f,
                    contrast = 10f,
                )
            ),
            FilmSimulation(
                id = "fuji_superia_400",
                name = "Fuji Superia 400",
                brand = "Fuji",
                description = "富士日用负片，清新色调",
                adjustments = Adjustments(
                    filmId = "fuji_superia_400",
                    filmIntensity = 1f,
                    saturation = 5f,
                    greenMagenta = -3f,
                )
            ),
            FilmSimulation(
                id = "fuji_velvia_50",
                name = "Fuji Velvia 50",
                brand = "Fuji",
                description = "富士反转片，高饱和度风景胶片",
                adjustments = Adjustments(
                    filmId = "fuji_velvia_50",
                    filmIntensity = 1f,
                    saturation = 25f,
                    contrast = 15f,
                )
            ),
            FilmSimulation(
                id = "agfa_vista_400",
                name = "Agfa Vista 400",
                brand = "Agfa",
                description = "爱克发复古色调，浓郁青色",
                adjustments = Adjustments(
                    filmId = "agfa_vista_400",
                    filmIntensity = 1f,
                    saturation = 10f,
                    temperature = -5f,
                )
            ),
            FilmSimulation(
                id = "bw_ilford_hp5",
                name = "Ilford HP5 Plus",
                brand = "Ilford",
                description = "伊尔福经典黑白胶卷",
                adjustments = Adjustments(
                    filmId = "bw_ilford_hp5",
                    filmIntensity = 1f,
                    contrast = 8f,
                    grainAmount = 20f,
                )
            ),
        )
    }
}

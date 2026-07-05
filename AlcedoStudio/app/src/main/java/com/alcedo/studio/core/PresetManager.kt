package com.alcedo.studio.core

import android.content.Context
import android.net.Uri
import com.alcedo.studio.data.model.Adjustments
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class PresetManager(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val presetDir: File
        get() = File(context.filesDir, "presets")

    private val categoryDir: File
        get() = File(context.filesDir, "preset_categories")

    init {
        if (!presetDir.exists()) presetDir.mkdirs()
        if (!categoryDir.exists()) categoryDir.mkdirs()
    }

    fun getPresets(category: String? = null): List<Preset> {
        val presets = presetDir.listFiles { file -> file.extension == "json" }
            ?.mapNotNull { file ->
                try {
                    json.decodeFromString<Preset>(file.readText())
                } catch (e: Exception) {
                    null
                }
            } ?: getBuiltInPresets()

        return if (category != null) {
            presets.filter { it.category == category }
        } else {
            presets
        }.sortedByDescending { it.usageCount }
    }

    private fun getBuiltInPresets(): List<Preset> {
        return listOf(
            Preset(
                id = "builtin_portrait",
                name = "人像",
                category = "人像",
                adjustments = Adjustments(
                    exposure = 0.3f,
                    contrast = 10f,
                    highlights = -10f,
                    shadows = 15f,
                    whites = 5f,
                    blacks = -5f,
                    temperature = 3f,
                    tint = 2f,
                    vibrance = 10f,
                    saturation = 5f,
                    texture = 5f,
                    clarity = 3f,
                    dehaze = 0f,
                    sharpening = 15f
                ),
                isBuiltIn = true,
                thumbnailColor = -0x333334
            ),
            Preset(
                id = "builtin_landscape",
                name = "风景",
                category = "风景",
                adjustments = Adjustments(
                    exposure = 0.2f,
                    contrast = 15f,
                    highlights = -20f,
                    shadows = 20f,
                    whites = 10f,
                    blacks = -10f,
                    temperature = -5f,
                    vibrance = 15f,
                    saturation = 10f,
                    texture = 10f,
                    clarity = 8f,
                    dehaze = 5f,
                    sharpening = 20f
                ),
                isBuiltIn = true,
                thumbnailColor = -0x663301
            ),
            Preset(
                id = "builtin_street",
                name = "街拍",
                category = "街头",
                adjustments = Adjustments(
                    exposure = -0.2f,
                    contrast = 20f,
                    highlights = -15f,
                    shadows = 10f,
                    whites = 5f,
                    blacks = -15f,
                    saturation = -10f,
                    clarity = 10f,
                    dehaze = 5f,
                    grain = 15f
                ),
                isBuiltIn = true,
                thumbnailColor = -0x404040
            ),
            Preset(
                id = "builtin_film",
                name = "复古胶片",
                category = "胶片",
                adjustments = Adjustments(
                    exposure = 0.1f,
                    contrast = 8f,
                    highlights = -5f,
                    shadows = 8f,
                    temperature = 8f,
                    tint = 3f,
                    saturation = -5f,
                    filmId = "kodak_portra_400",
                    grain = 20f,
                    vignette = 15f
                ),
                isBuiltIn = true,
                thumbnailColor = -0x332201
            ),
            Preset(
                id = "builtin_bw",
                name = "黑白",
                category = "黑白",
                adjustments = Adjustments(
                    blackAndWhite = true,
                    bwMixRed = 30f,
                    bwMixOrange = 40f,
                    bwMixYellow = 50f,
                    bwMixGreen = 20f,
                    bwMixAqua = -10f,
                    bwMixBlue = -20f,
                    bwMixPurple = -10f,
                    bwMixMagenta = 0f,
                    contrast = 15f,
                    texture = 10f,
                    clarity = 5f,
                    grain = 10f
                ),
                isBuiltIn = true,
                thumbnailColor = -0x808080
            ),
            Preset(
                id = "builtin_night",
                name = "夜景",
                category = "夜景",
                adjustments = Adjustments(
                    exposure = -0.5f,
                    contrast = 25f,
                    highlights = -30f,
                    shadows = 30f,
                    blacks = -20f,
                    temperature = -10f,
                    tint = -5f,
                    vibrance = 20f,
                    saturation = 10f,
                    dehaze = 10f,
                    sharpening = 25f,
                    noiseReduction = 20f,
                    luminanceNoiseReduction = 25f
                ),
                isBuiltIn = true,
                thumbnailColor = -0x6666cc
            ),
            Preset(
                id = "builtin_food",
                name = "美食",
                category = "美食",
                adjustments = Adjustments(
                    exposure = 0.3f,
                    contrast = 12f,
                    highlights = -8f,
                    shadows = 12f,
                    whites = 8f,
                    temperature = 5f,
                    vibrance = 20f,
                    saturation = 10f,
                    texture = 8f,
                    clarity = 5f,
                    sharpening = 15f
                ),
                isBuiltIn = true,
                thumbnailColor = -0x6600
            ),
            Preset(
                id = "builtin_minimal",
                name = "极简",
                category = "极简",
                adjustments = Adjustments(
                    exposure = 0.5f,
                    contrast = -5f,
                    highlights = -10f,
                    shadows = 20f,
                    whites = 20f,
                    saturation = -15f,
                    dehaze = -10f,
                    grain = 5f
                ),
                isBuiltIn = true,
                thumbnailColor = -0xcccccd
            )
        )
    }

    fun savePreset(
        name: String,
        adjustments: Adjustments,
        category: String = "自定义",
        thumbnailColor: Int = -0x1
    ): Preset {
        val preset = Preset(
            id = UUID.randomUUID().toString(),
            name = name,
            category = category,
            adjustments = adjustments,
            isBuiltIn = false,
            thumbnailColor = thumbnailColor
        )

        val file = File(presetDir, "${preset.id}.json")
        file.writeText(json.encodeToString(preset))

        return preset
    }

    fun updatePreset(preset: Preset) {
        val file = File(presetDir, "${preset.id}.json")
        file.writeText(json.encodeToString(preset))
    }

    fun deletePreset(presetId: String): Boolean {
        val file = File(presetDir, "$presetId.json")
        return if (file.exists()) {
            file.delete()
            true
        } else false
    }

    fun incrementUsage(presetId: String) {
        val presets = getPresets().toMutableList()
        val index = presets.indexOfFirst { it.id == presetId }
        if (index >= 0) {
            val preset = presets[index]
            presets[index] = preset.copy(usageCount = preset.usageCount + 1)
            updatePreset(presets[index])
        }
    }

    fun searchPresets(query: String): List<Preset> {
        val lowerQuery = query.lowercase()
        return getPresets().filter {
            it.name.lowercase().contains(lowerQuery) ||
                it.category.lowercase().contains(lowerQuery)
        }
    }

    fun getCategories(): List<String> {
        return getPresets().map { it.category }.distinct().sorted()
    }

    fun getFavorites(): List<Preset> {
        return getPresets().filter { it.isFavorite }
    }

    fun toggleFavorite(presetId: String) {
        val presets = getPresets().toMutableList()
        val index = presets.indexOfFirst { it.id == presetId }
        if (index >= 0) {
            val preset = presets[index]
            presets[index] = preset.copy(isFavorite = !preset.isFavorite)
            updatePreset(presets[index])
        }
    }

    fun importPreset(uri: Uri): Preset? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val preset = json.decodeFromString<Preset>(jsonString)

            val newPreset = preset.copy(
                id = UUID.randomUUID().toString(),
                isBuiltIn = false
            )

            val file = File(presetDir, "${newPreset.id}.json")
            file.writeText(json.encodeToString(newPreset))

            newPreset
        } catch (e: Exception) {
            null
        }
    }

    fun exportPreset(presetId: String): String? {
        val preset = getPresets().find { it.id == presetId } ?: return null
        return json.encodeToString(preset)
    }
}

@Serializable
data class Preset(
    val id: String,
    val name: String,
    val category: String = "自定义",
    val adjustments: Adjustments,
    val isBuiltIn: Boolean = false,
    val usageCount: Int = 0,
    val isFavorite: Boolean = false,
    val thumbnailColor: Int = -0x1,
    val description: String = "",
    val author: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

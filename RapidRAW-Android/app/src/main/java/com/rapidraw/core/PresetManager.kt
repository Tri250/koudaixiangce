package com.rapidraw.core

import android.content.Context
import android.content.SharedPreferences
import com.rapidraw.data.model.Preset
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Lightweight local persistence for user-created presets.
 *
 * Stores presets as a JSON array in SharedPreferences so they survive process
 * death without requiring a database migration. Built-in presets are never
 * written here; they are loaded from code.
 */
class PresetManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Serializable
    private data class PresetContainer(val presets: List<Preset> = emptyList())

    fun getAll(): List<Preset> {
        return try {
            val stored = prefs.getString(KEY_PRESETS, null) ?: return emptyList()
            json.decodeFromString(PresetContainer.serializer(), stored).presets
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(preset: Preset) {
        val current = getAll().toMutableList()
        val index = current.indexOfFirst { it.id == preset.id }
        if (index >= 0) {
            current[index] = preset
        } else {
            current.add(preset)
        }
        store(current)
    }

    fun saveFromAdjustments(name: String, adjustments: com.rapidraw.data.model.Adjustments, filmId: String?): Preset {
        val preset = Preset(
            id = UUID.randomUUID().toString(),
            name = name,
            filmId = filmId ?: adjustments.filmId.takeIf { it.isNotBlank() },
            adjustments = adjustments,
            createdAt = System.currentTimeMillis(),
        )
        save(preset)
        return preset
    }

    fun delete(id: String) {
        val current = getAll().filterNot { it.id == id }
        store(current)
    }

    fun rename(id: String, name: String) {
        val current = getAll().map { preset ->
            if (preset.id == id) preset.copy(name = name) else preset
        }
        store(current)
    }

    private fun store(presets: List<Preset>) {
        try {
            prefs.edit().putString(KEY_PRESETS, json.encodeToString(PresetContainer.serializer(), PresetContainer(presets))).apply()
        } catch (_: Exception) {
            // Ignore persistence failures; presets are a nice-to-have feature.
        }
    }

    companion object {
        private const val PREFS_NAME = "rapidraw_user_presets"
        private const val KEY_PRESETS = "presets"
    }
}

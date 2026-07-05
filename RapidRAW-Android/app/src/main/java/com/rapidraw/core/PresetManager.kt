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

    private val prefs: SharedPreferences = SafePreferences.get(context, PREFS_NAME)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Serializable
    private data class PresetContainer(val presets: List<Preset> = emptyList())

    /**
     * 获取所有预设（内置预设 + 用户自定义预设）。
     * 内置预设始终可用，且不会被用户删除。
     */
    fun getAll(): List<Preset> {
        val userPresets = try {
            val stored = prefs.getString(KEY_PRESETS, null) ?: return getBuiltInPresets()
            json.decodeFromString(PresetContainer.serializer(), stored).presets
        } catch (_: Exception) {
            emptyList()
        }
        return getBuiltInPresets() + userPresets
    }

    /**
     * 获取内置预设列表。这些预设始终可用，不受用户删除影响。
     * 包含 6 个精心设计的预设，覆盖常见拍摄场景。
     */
    fun getBuiltInPresets(): List<Preset> = BUILT_IN_PRESETS

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
            SafePreferences.putString(prefs, KEY_PRESETS, json.encodeToString(PresetContainer.serializer(), PresetContainer(presets)))
        } catch (_: Exception) {
            // Ignore persistence failures; presets are a nice-to-have feature.
        }
    }

    companion object {
        private const val PREFS_NAME = "rapidraw_user_presets"
        private const val KEY_PRESETS = "presets"

        /**
         * 内置预设列表 — 6 个精心设计的预设，覆盖常见拍摄场景。
         * 每个预设包含具体的 exposure、contrast、saturation、temperature 等调整值。
         */
        val BUILT_IN_PRESETS: List<Preset> = listOf(
            Preset(
                id = "builtin_qingxin",
                name = "清新",
                description = "通透蓝天绿色，适合风景与自然",
                category = "风景",
                isBuiltIn = true,
                previewGradient = listOf(0xFF87CEEB, 0xFF98FB98),
                adjustments = com.rapidraw.data.model.Adjustments(
                    exposure = 0.15f,
                    contrast = -10f,
                    saturation = 5f,
                    temperature = -8f,
                    vibrance = 12f,
                    highlights = -20f,
                    shadows = 15f,
                    clarity = 8f,
                    dehaze = 10f,
                ),
            ),
            Preset(
                id = "builtin_renxiang",
                name = "人像",
                description = "柔和肤色，自然虚化，适合人像摄影",
                category = "人像",
                isBuiltIn = true,
                previewGradient = listOf(0xFFF5E6D3, 0xFFE8D5C4),
                adjustments = com.rapidraw.data.model.Adjustments(
                    exposure = 0.2f,
                    contrast = -15f,
                    saturation = -5f,
                    temperature = 5f,
                    vibrance = 5f,
                    highlights = -10f,
                    shadows = 10f,
                    clarity = -10f,
                    softGlow = 0.15f,
                ),
            ),
            Preset(
                id = "builtin_fengjing",
                name = "风景",
                description = "高饱和高对比，增强风光层次感",
                category = "风景",
                isBuiltIn = true,
                previewGradient = listOf(0xFF2E8B57, 0xFF4682B4),
                adjustments = com.rapidraw.data.model.Adjustments(
                    exposure = 0.1f,
                    contrast = 15f,
                    saturation = 15f,
                    temperature = -3f,
                    vibrance = 20f,
                    highlights = -25f,
                    shadows = 10f,
                    clarity = 15f,
                    dehaze = 15f,
                ),
            ),
            Preset(
                id = "builtin_heibai",
                name = "黑白",
                description = "经典黑白影调，强调光影与质感",
                category = "黑白",
                isBuiltIn = true,
                previewGradient = listOf(0xFF333333, 0xFFCCCCCC),
                adjustments = com.rapidraw.data.model.Adjustments(
                    saturation = -100f,
                    contrast = 20f,
                    exposure = 0f,
                    highlights = -15f,
                    shadows = 15f,
                    whites = 10f,
                    blacks = -10f,
                    clarity = 25f,
                    grainAmount = 15f,
                    vignetteAmount = 10f,
                ),
            ),
            Preset(
                id = "builtin_dianyinggan",
                name = "电影感",
                description = "宽银幕色调，青橙对比，电影质感",
                category = "电影感",
                isBuiltIn = true,
                previewGradient = listOf(0xFF1A3A4A, 0xFFD4A04A),
                adjustments = com.rapidraw.data.model.Adjustments(
                    exposure = -0.1f,
                    contrast = 10f,
                    saturation = -10f,
                    temperature = -5f,
                    tint = 8f,
                    vibrance = -5f,
                    highlights = -20f,
                    shadows = 15f,
                    clarity = 10f,
                    vignetteAmount = 20f,
                    grainAmount = 10f,
                ),
            ),
            Preset(
                id = "builtin_fugu",
                name = "复古",
                description = "怀旧暖调，褪色质感，胶片味道",
                category = "复古",
                isBuiltIn = true,
                previewGradient = listOf(0xFF8B7355, 0xFFD2B48C),
                adjustments = com.rapidraw.data.model.Adjustments(
                    temperature = 10f,
                    saturation = -15f,
                    vibrance = -5f,
                    contrast = -5f,
                    exposure = 0.05f,
                    highlights = -25f,
                    shadows = 20f,
                    whites = -10f,
                    blacks = 5f,
                    vignetteAmount = 25f,
                    grainAmount = 25f,
                    dehaze = -5f,
                ),
            ),
        )
    }
}

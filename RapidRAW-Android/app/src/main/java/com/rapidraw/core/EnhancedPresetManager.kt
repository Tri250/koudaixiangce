package com.rapidraw.core

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.rapidraw.data.model.Adjustments
import java.io.File
import java.util.UUID

/**
 * 增强预设管理器 — 支持 Style/Tool 两种预设类型。
 *
 * Style: 覆盖全部调整参数（一键风格）
 * Tool:  叠加到当前编辑之上（增量调整），仅叠加预设中非默认值的参数
 *
 * 支持将遮罩和裁剪数据保存到预设中。
 * 对标 CyberTimon/RapidRAW v1.5.4 的 Style/Tool Presets 功能。
 */
class EnhancedPresetManager(context: Context) {

    companion object {
        private const val TAG = "EnhancedPresetManager"
        private const val PRESETS_DIR = "enhanced_presets"
    }

    @Serializable
    enum class PresetType {
        STYLE,
        TOOL,
    }

    @Serializable
    data class EnhancedPreset(
        val id: String = UUID.randomUUID().toString(),
        val name: String,
        val type: PresetType = PresetType.STYLE,
        val adjustmentsJson: String,
        val thumbnailPath: String? = null,
        val includeMasks: Boolean = false,
        val masksJson: String? = null,
        val includeCrop: Boolean = false,
        val cropJson: String? = null,
        val category: String = "user",
        val tags: List<String> = emptyList(),
        val createdAt: Long = System.currentTimeMillis(),
        val updatedAt: Long = System.currentTimeMillis(),
    )

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val presetsDir = File(context.filesDir, PRESETS_DIR).also { it.mkdirs() }
    private val presetCache = mutableListOf<EnhancedPreset>()

    private val _presets = MutableStateFlow<List<EnhancedPreset>>(emptyList())
    val presets: StateFlow<List<EnhancedPreset>> = _presets

    init {
        loadAllPresets()
    }

    /**
     * 保存预设
     */
    suspend fun savePreset(preset: EnhancedPreset): Result<EnhancedPreset> =
        withContext(Dispatchers.IO) {
            runCatching {
                val file = File(presetsDir, "${preset.id}.json")
                file.writeText(json.encodeToString(preset))

                val idx = presetCache.indexOfFirst { it.id == preset.id }
                if (idx >= 0) {
                    presetCache[idx] = preset
                } else {
                    presetCache.add(preset)
                }
                _presets.value = presetCache.toList()

                Log.d(TAG, "Preset saved: ${preset.name} (${preset.type})")
                preset
            }
        }

    /**
     * 应用预设到调整参数
     * @param currentAdjustments 当前编辑参数
     * @param preset 要应用的预设
     * @return 应用后的调整参数
     */
    fun applyPreset(
        currentAdjustments: Adjustments,
        preset: EnhancedPreset,
    ): Adjustments {
        val presetAdj = try {
            json.decodeFromString<Adjustments>(preset.adjustmentsJson)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode preset adjustments", e)
            return currentAdjustments
        }

        return when (preset.type) {
            PresetType.STYLE -> presetAdj
            PresetType.TOOL -> mergeAdjustments(currentAdjustments, presetAdj)
        }
    }

    /**
     * 增量合并调整参数 — Tool 预设模式
     * 仅叠加预设中实际修改过的参数（非默认值 0）
     */
    private fun mergeAdjustments(base: Adjustments, overlay: Adjustments): Adjustments {
        return base.copy(
            exposure = if (overlay.exposure != 0f) base.exposure + overlay.exposure else base.exposure,
            brightness = if (overlay.brightness != 0f) base.brightness + overlay.brightness else base.brightness,
            contrast = if (overlay.contrast != 0f) base.contrast + overlay.contrast else base.contrast,
            saturation = if (overlay.saturation != 0f) base.saturation + overlay.saturation else base.saturation,
            vibrance = if (overlay.vibrance != 0f) base.vibrance + overlay.vibrance else base.vibrance,
            clarity = if (overlay.clarity != 0f) base.clarity + overlay.clarity else base.clarity,
            temperature = if (overlay.temperature != 0f) base.temperature + overlay.temperature else base.temperature,
            tint = if (overlay.tint != 0f) base.tint + overlay.tint else base.tint,
            highlights = if (overlay.highlights != 0f) base.highlights + overlay.highlights else base.highlights,
            shadows = if (overlay.shadows != 0f) base.shadows + overlay.shadows else base.shadows,
            // 非增量参数直接覆盖
            filmId = if (overlay.filmId.isNotBlank()) overlay.filmId else base.filmId,
        )
    }

    fun deletePreset(presetId: String): Boolean {
        val file = File(presetsDir, "$presetId.json")
        val deleted = file.delete()
        if (deleted) {
            presetCache.removeAll { it.id == presetId }
            _presets.value = presetCache.toList()
        }
        return deleted
    }

    fun exportPreset(preset: EnhancedPreset): String {
        return json.encodeToString(preset)
    }

    suspend fun importPreset(jsonString: String): Result<EnhancedPreset> =
        withContext(Dispatchers.IO) {
            runCatching {
                val preset = json.decodeFromString<EnhancedPreset>(jsonString)
                val newPreset = preset.copy(
                    id = UUID.randomUUID().toString(),
                    category = "imported",
                )
                savePreset(newPreset).getOrThrow()
            }
        }

    private fun loadAllPresets() {
        presetCache.clear()
        presetsDir.listFiles()?.filter { it.extension == "json" }?.forEach { file ->
            try {
                val preset = json.decodeFromString<EnhancedPreset>(file.readText())
                presetCache.add(preset)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load preset ${file.name}: ${e.message}")
            }
        }
        _presets.value = presetCache.toList()
    }
}

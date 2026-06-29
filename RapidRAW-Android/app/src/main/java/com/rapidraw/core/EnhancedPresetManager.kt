package com.rapidraw.core

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * 增强预设管理器 — 支持 Style/Tool 两种预设类型。
 * Style: 覆盖全部调整参数（一键风格）
 * Tool: 叠加到当前编辑之上（增量调整）
 * 支持将遮罩和裁剪数据保存到预设中。
 */
class EnhancedPresetManager(context: Context) {

    companion object {
        private const val TAG = "EnhancedPresetManager"
        private const val PRESETS_DIR = "enhanced_presets"
    }

    @Serializable
    enum class PresetType {
        STYLE,  // 覆盖全部参数
        TOOL,   // 叠加到当前编辑
    }

    @Serializable
    data class EnhancedPreset(
        val id: String = UUID.randomUUID().toString(),
        val name: String,
        val type: PresetType = PresetType.STYLE,
        val adjustmentsJson: String,         // 序列化的 Adjustments
        val thumbnailPath: String? = null,   // 缩略图路径
        val includeMasks: Boolean = false,   // 是否包含遮罩
        val masksJson: String? = null,       // 序列化的遮罩数据
        val includeCrop: Boolean = false,    // 是否包含裁剪
        val cropJson: String? = null,        // 序列化的裁剪数据
        val category: String = "user",       // user/community/built-in
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

    private val _communityPresets = MutableStateFlow<List<EnhancedPreset>>(emptyList())
    val communityPresets: StateFlow<List<EnhancedPreset>> = _communityPresets

    init {
        loadAllPresets()
    }

    /**
     * 保存预设
     */
    suspend fun savePreset(preset: EnhancedPreset): Result<EnhancedPreset> = withContext(Dispatchers.IO) {
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
        return when (preset.type) {
            PresetType.STYLE -> {
                // Style: 完全覆盖
                val presetAdj = json.decodeFromString<Adjustments>(preset.adjustmentsJson)
                presetAdj
            }
            PresetType.TOOL -> {
                // Tool: 增量叠加 — 仅叠加预设中非零的参数
                val presetAdj = json.decodeFromString<Adjustments>(preset.adjustmentsJson)
                mergeAdjustments(currentAdjustments, presetAdj)
            }
        }
    }

    /**
     * 增量合并调整参数 — Tool 预设模式
     * 仅叠加预设中实际修改过的参数（非默认值）
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
            filmSimulation = overlay.filmSimulation.ifBlank { base.filmSimulation },
            toneCurvePoints = if (overlay.toneCurvePoints.isNotEmpty()) overlay.toneCurvePoints else base.toneCurvePoints,
        )
    }

    /**
     * 删除预设
     */
    fun deletePreset(presetId: String): Boolean {
        val file = File(presetsDir, "$presetId.json")
        val deleted = file.delete()
        if (deleted) {
            presetCache.removeAll { it.id == presetId }
            _presets.value = presetCache.toList()
        }
        return deleted
    }

    /**
     * 导出预设为 JSON 字符串（用于分享）
     */
    fun exportPreset(preset: EnhancedPreset): String {
        return json.encodeToString(preset)
    }

    /**
     * 导入预设
     */
    suspend fun importPreset(jsonString: String): Result<EnhancedPreset> = withContext(Dispatchers.IO) {
        runCatching {
            val preset = json.decodeFromString<EnhancedPreset>(jsonString)
            val newPreset = preset.copy(
                id = UUID.randomUUID().toString(),  // 防止 ID 冲突
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

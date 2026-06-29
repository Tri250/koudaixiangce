package com.rapidraw.core

import com.rapidraw.data.model.Adjustments
import com.rapidraw.data.model.ColorGrading
import com.rapidraw.data.model.ColorGradingRegion
import com.rapidraw.data.model.FilmCategory
import com.rapidraw.data.model.FilmSimulation
import com.rapidraw.data.model.HslChannel
import com.rapidraw.data.model.Coord
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 滤镜预设管理器 (Film Presets Manager)
 *
 * 整合 PixelFruit 专业滤镜预设系统：
 * - 人像滤镜系列（清新人像、温暖人像、冷调人像）
 * - 风景滤镜系列（黄金时刻、蓝调时刻、戏剧天空）
 * - 色彩风格滤镜（复古、电影、黑白）
 *
 * 支持自定义预设的保存和加载，支持预设强度调节。
 * 所有预设包含完整的色彩分级参数和调整设置。
 */
object FilmPresetsManager {

    // ── 预设数据结构 ─────────────────────────────────────────────────

    /**
     * 滤镜预设类别
     */
    enum class PresetCategory(val displayName: String, val displayNameEn: String) {
        PORTRAIT("人像", "Portrait"),
        LANDSCAPE("风景", "Landscape"),
        COLOR_STYLE("色彩风格", "Color Style"),
        CUSTOM("自定义", "Custom"),
        ALL("全部", "All"),
    }

    /**
     * 滤镜预设数据
     *
     * 每个预设包含完整的调整参数和色彩分级设置，
     * 可直接应用到图像。
     */
    @Serializable
    data class FilmPreset(
        val id: String,                              // 预设唯一 ID
        val displayName: String,                      // 中文名称
        val displayNameEn: String,                    // 英文名称
        val category: PresetCategory,                 // 所属类别
        val description: String,                      // 描述
        val thumbnailColor: String,                   // 预览色（十六进制 RGB）
        val adjustments: Adjustments,                 // 调整参数
        val defaultIntensity: Float = 1f,             // 默认强度 [0,1]
        val tags: List<String> = emptyList(),         // 标签
        val isCustom: Boolean = false,                // 是否自定义预设
        val createdAt: Long = 0L,                     // 创建时间（自定义预设）
    )

    // ── 内置预设数据 ────────────────────────────────────────────────

    /**
     * 所有内置滤镜预设
     *
     * 基于 PixelFruit 色彩科学的专业滤镜，
     * 经精心调校的色彩参数，实现电影级色彩效果。
     */
    val BUILTIN_PRESETS: List<FilmPreset> = listOf(
        // === 人像滤镜系列 ===

        // 清新人像
        FilmPreset(
            id = "portrait_fresh",
            displayName = "清新人像",
            displayNameEn = "Fresh Portrait",
            category = PresetCategory.PORTRAIT,
            description = "清新自然肤色，柔和过渡，适合日常人像",
            thumbnailColor = "#F5E6D3",
            adjustments = Adjustments(
                exposure = 0.1f,
                contrast = -5f,
                highlights = -10f,
                shadows = 5f,
                saturation = -8f,
                vibrance = 10f,
                temperature = 5f,
                clarity = -5f,
                colorGrading = ColorGrading(
                    shadows = ColorGradingRegion(hue = 30f, saturation = 15f, luminance = 5f),
                    midtones = ColorGradingRegion(hue = 0f, saturation = 0f, luminance = 0f),
                    highlights = ColorGradingRegion(hue = 0f, saturation = 0f, luminance = 0f),
                    blending = 20f,
                    balance = 0f,
                ),
                hslOranges = HslChannel(hue = 5f, saturation = -10f, luminance = 10f),
                hslReds = HslChannel(hue = 0f, saturation = -5f, luminance = 5f),
                hslYellows = HslChannel(hue = 0f, saturation = -10f, luminance = 5f),
            ),
            tags = listOf("人像", "清新", "肤色", "自然"),
        ),

        // 温暖人像
        FilmPreset(
            id = "portrait_warm",
            displayName = "温暖人像",
            displayNameEn = "Warm Portrait",
            category = PresetCategory.PORTRAIT,
            description = "温暖阳光感，肤色健康光泽，适合户外人像",
            thumbnailColor = "#F8D7B5",
            adjustments = Adjustments(
                exposure = 0.15f,
                contrast = 8f,
                highlights = -15f,
                shadows = 10f,
                saturation = 15f,
                vibrance = 5f,
                temperature = 12f,
                clarity = 3f,
                colorGrading = ColorGrading(
                    shadows = ColorGradingRegion(hue = 45f, saturation = 20f, luminance = 10f),
                    midtones = ColorGradingRegion(hue = 20f, saturation = 10f, luminance = 5f),
                    highlights = ColorGradingRegion(hue = 15f, saturation = 5f, luminance = 0f),
                    blending = 30f,
                    balance = 10f,
                ),
                hslOranges = HslChannel(hue = 10f, saturation = 15f, luminance = 5f),
                hslReds = HslChannel(hue = 5f, saturation = 10f, luminance = 8f),
                hslYellows = HslChannel(hue = -5f, saturation = 0f, luminance = 10f),
            ),
            tags = listOf("人像", "温暖", "阳光", "户外"),
        ),

        // 冷调人像
        FilmPreset(
            id = "portrait_cool",
            displayName = "冷调人像",
            displayNameEn = "Cool Portrait",
            category = PresetCategory.PORTRAIT,
            description = "冷调时尚感，肤色通透白皙，适合室内人像",
            thumbnailColor = "#E0E8F0",
            adjustments = Adjustments(
                exposure = 0.05f,
                contrast = 5f,
                highlights = -8f,
                shadows = 8f,
                saturation = -15f,
                vibrance = 15f,
                temperature = -15f,
                clarity = 0f,
                colorGrading = ColorGrading(
                    shadows = ColorGradingRegion(hue = 200f, saturation = 15f, luminance = 8f),
                    midtones = ColorGradingRegion(hue = 220f, saturation = 10f, luminance = 0f),
                    highlights = ColorGradingRegion(hue = 240f, saturation = 5f, luminance = -5f),
                    blending = 25f,
                    balance = -5f,
                ),
                hslOranges = HslChannel(hue = -10f, saturation = -15f, luminance = 10f),
                hslReds = HslChannel(hue = 0f, saturation = -10f, luminance = 5f),
                hslBlues = HslChannel(hue = 5f, saturation = 10f, luminance = 0f),
            ),
            tags = listOf("人像", "冷调", "时尚", "室内"),
        ),

        // 柔光人像
        FilmPreset(
            id = "portrait_soft",
            displayName = "柔光人像",
            displayNameEn = "Soft Portrait",
            category = PresetCategory.PORTRAIT,
            description = "梦幻柔光效果，适合逆光和唯美风格",
            thumbnailColor = "#FFEEF5",
            adjustments = Adjustments(
                exposure = 0.2f,
                contrast = -15f,
                highlights = -20f,
                shadows = 15f,
                saturation = -10f,
                vibrance = 8f,
                temperature = 8f,
                clarity = -15f,
                softGlow = 0.3f,
                colorGrading = ColorGrading(
                    shadows = ColorGradingRegion(hue = 0f, saturation = 0f, luminance = 15f),
                    midtones = ColorGradingRegion(hue = 0f, saturation = 0f, luminance = 0f),
                    highlights = ColorGradingRegion(hue = 340f, saturation = 10f, luminance = -5f),
                    blending = 40f,
                    balance = 5f,
                ),
            ),
            tags = listOf("人像", "柔光", "梦幻", "逆光"),
        ),

        // === 风景滤镜系列 ===

        // 黄金时刻
        FilmPreset(
            id = "landscape_golden_hour",
            displayName = "黄金时刻",
            displayNameEn = "Golden Hour",
            category = PresetCategory.LANDSCAPE,
            description = "日落前的温暖金色调，梦幻光影效果",
            thumbnailColor = "#FFB347",
            adjustments = Adjustments(
                exposure = -0.1f,
                contrast = 15f,
                highlights = -25f,
                shadows = 20f,
                saturation = 25f,
                vibrance = 10f,
                temperature = 25f,
                clarity = 10f,
                dehaze = 5f,
                colorGrading = ColorGrading(
                    shadows = ColorGradingRegion(hue = 30f, saturation = 30f, luminance = 15f),
                    midtones = ColorGradingRegion(hue = 35f, saturation = 20f, luminance = 5f),
                    highlights = ColorGradingRegion(hue = 45f, saturation = 15f, luminance = 0f),
                    blending = 50f,
                    balance = 15f,
                ),
                hslOranges = HslChannel(hue = 0f, saturation = 30f, luminance = 15f),
                hslYellows = HslChannel(hue = -5f, saturation = 20f, luminance = 10f),
                hslReds = HslChannel(hue = 10f, saturation = 15f, luminance = 10f),
            ),
            tags = listOf("风景", "日落", "温暖", "光影"),
        ),

        // 蓝调时刻
        FilmPreset(
            id = "landscape_blue_hour",
            displayName = "蓝调时刻",
            displayNameEn = "Blue Hour",
            category = PresetCategory.LANDSCAPE,
            description = "日出前的宁静蓝色调，神秘氛围",
            thumbnailColor = "#4A6FA5",
            adjustments = Adjustments(
                exposure = -0.15f,
                contrast = 20f,
                highlights = -10f,
                shadows = 15f,
                saturation = 10f,
                vibrance = 15f,
                temperature = -20f,
                clarity = 5f,
                dehaze = 10f,
                colorGrading = ColorGrading(
                    shadows = ColorGradingRegion(hue = 220f, saturation = 25f, luminance = 10f),
                    midtones = ColorGradingRegion(hue = 210f, saturation = 15f, luminance = 0f),
                    highlights = ColorGradingRegion(hue = 195f, saturation = 10f, luminance = -5f),
                    blending = 45f,
                    balance = -10f,
                ),
                hslBlues = HslChannel(hue = -5f, saturation = 25f, luminance = -5f),
                hslAquas = HslChannel(hue = 10f, saturation = 15f, luminance = 0f),
            ),
            tags = listOf("风景", "日出", "冷调", "神秘"),
        ),

        // 戏剧天空
        FilmPreset(
            id = "landscape_dramatic_sky",
            displayName = "戏剧天空",
            displayNameEn = "Dramatic Sky",
            category = PresetCategory.LANDSCAPE,
            description = "高对比度戏剧感，天空层次丰富",
            thumbnailColor = "#1E3A5F",
            adjustments = Adjustments(
                exposure = -0.2f,
                contrast = 30f,
                highlights = -35f,
                shadows = 25f,
                saturation = 20f,
                vibrance = 5f,
                temperature = -5f,
                clarity = 20f,
                dehaze = 15f,
                vignetteAmount = 15f,
                colorGrading = ColorGrading(
                    shadows = ColorGradingRegion(hue = 230f, saturation = 15f, luminance = 5f),
                    midtones = ColorGradingRegion(hue = 0f, saturation = 0f, luminance = 0f),
                    highlights = ColorGradingRegion(hue = 210f, saturation = 20f, luminance = -10f),
                    blending = 35f,
                    balance = 0f,
                ),
                hslBlues = HslChannel(hue = -10f, saturation = 30f, luminance = -10f),
                hslAquas = HslChannel(hue = 5f, saturation = 20f, luminance = -5f),
            ),
            tags = listOf("风景", "天空", "戏剧", "对比"),
        ),

        // 森林绿意
        FilmPreset(
            id = "landscape_forest",
            displayName = "森林绿意",
            displayNameEn = "Forest",
            category = PresetCategory.LANDSCAPE,
            description = "清新森林感，增强绿色层次",
            thumbnailColor = "#2D5A27",
            adjustments = Adjustments(
                exposure = 0f,
                contrast = 10f,
                highlights = -15f,
                shadows = 10f,
                saturation = 15f,
                vibrance = 20f,
                temperature = -3f,
                clarity = 8f,
                colorGrading = ColorGrading(
                    shadows = ColorGradingRegion(hue = 120f, saturation = 15f, luminance = 5f),
                    midtones = ColorGradingRegion(hue = 115f, saturation = 10f, luminance = 0f),
                    highlights = ColorGradingRegion(hue = 0f, saturation = 0f, luminance = 0f),
                    blending = 30f,
                    balance = 5f,
                ),
                hslGreens = HslChannel(hue = -5f, saturation = 25f, luminance = 10f),
                hslYellows = HslChannel(hue = 10f, saturation = 10f, luminance = 5f),
            ),
            tags = listOf("风景", "森林", "自然", "绿色"),
        ),

        // === 色彩风格滤镜 ===

        // 复古胶片
        FilmPreset(
            id = "style_vintage_film",
            displayName = "复古胶片",
            displayNameEn = "Vintage Film",
            category = PresetCategory.COLOR_STYLE,
            description = "经典复古胶片感，暖色调褪色效果",
            thumbnailColor = "#C4A35A",
            adjustments = Adjustments(
                exposure = -0.1f,
                contrast = 10f,
                highlights = -20f,
                shadows = 15f,
                saturation = -20f,
                vibrance = -5f,
                temperature = 15f,
                clarity = -10f,
                grainAmount = 20f,
                grainSize = 30f,
                vignetteAmount = 20f,
                colorGrading = ColorGrading(
                    shadows = ColorGradingRegion(hue = 35f, saturation = 20f, luminance = 10f),
                    midtones = ColorGradingRegion(hue = 25f, saturation = 10f, luminance = 0f),
                    highlights = ColorGradingRegion(hue = 45f, saturation = 5f, luminance = -10f),
                    blending = 40f,
                    balance = 10f,
                ),
                hslReds = HslChannel(hue = 5f, saturation = -10f, luminance = -5f),
                hslYellows = HslChannel(hue = 0f, saturation = -15f, luminance = 0f),
            ),
            tags = listOf("复古", "胶片", "怀旧", "暖调"),
        ),

        // 电影色彩
        FilmPreset(
            id = "style_cinematic",
            displayName = "电影色彩",
            displayNameEn = "Cinematic",
            category = PresetCategory.COLOR_STYLE,
            description = "电影级色彩，青橙色调对比",
            thumbnailColor = "#2C5F7C",
            adjustments = Adjustments(
                exposure = -0.05f,
                contrast = 25f,
                highlights = -30f,
                shadows = 20f,
                saturation = -5f,
                vibrance = 10f,
                temperature = -8f,
                clarity = 15f,
                dehaze = 10f,
                colorGrading = ColorGrading(
                    shadows = ColorGradingRegion(hue = 210f, saturation = 25f, luminance = 5f),
                    midtones = ColorGradingRegion(hue = 0f, saturation = -5f, luminance = 0f),
                    highlights = ColorGradingRegion(hue = 35f, saturation = 20f, luminance = -5f),
                    blending = 50f,
                    balance = 0f,
                ),
                hslOranges = HslChannel(hue = 0f, saturation = 15f, luminance = 5f),
                hslAquas = HslChannel(hue = 5f, saturation = 20f, luminance = 0f),
                hslBlues = HslChannel(hue = -10f, saturation = 25f, luminance = -5f),
            ),
            tags = listOf("电影", "青橙", "对比", "专业"),
        ),

        // 黑白经典
        FilmPreset(
            id = "style_bw_classic",
            displayName = "黑白经典",
            displayNameEn = "Classic B&W",
            category = PresetCategory.COLOR_STYLE,
            description = "经典黑白影调，高对比度",
            thumbnailColor = "#404040",
            adjustments = Adjustments(
                exposure = 0f,
                contrast = 30f,
                highlights = -15f,
                shadows = 10f,
                saturation = -100f,
                vibrance = 0f,
                clarity = 15f,
                grainAmount = 15f,
                vignetteAmount = 15f,
                colorGrading = ColorGrading(
                    shadows = ColorGradingRegion(hue = 0f, saturation = 0f, luminance = 10f),
                    midtones = ColorGradingRegion(hue = 0f, saturation = 0f, luminance = 0f),
                    highlights = ColorGradingRegion(hue = 0f, saturation = 0f, luminance = -5f),
                    blending = 0f,
                    balance = 0f,
                ),
            ),
            tags = listOf("黑白", "经典", "对比", "简约"),
        ),

        // 黑白柔和
        FilmPreset(
            id = "style_bw_soft",
            displayName = "黑白柔和",
            displayNameEn = "Soft B&W",
            category = PresetCategory.COLOR_STYLE,
            description = "柔和黑白，低对比度，复古质感",
            thumbnailColor = "#808080",
            adjustments = Adjustments(
                exposure = 0.1f,
                contrast = -10f,
                highlights = -10f,
                shadows = 20f,
                saturation = -100f,
                vibrance = 0f,
                clarity = -10f,
                grainAmount = 25f,
                vignetteAmount = 10f,
                colorGrading = ColorGrading(
                    shadows = ColorGradingRegion(hue = 0f, saturation = 0f, luminance = 15f),
                    midtones = ColorGradingRegion(hue = 0f, saturation = 0f, luminance = 5f),
                    highlights = ColorGradingRegion(hue = 0f, saturation = 0f, luminance = 0f),
                    blending = 20f,
                    balance = 5f,
                ),
            ),
            tags = listOf("黑白", "柔和", "复古", "低对比"),
        ),

        // 日系清新
        FilmPreset(
            id = "style_japanese_fresh",
            displayName = "日系清新",
            displayNameEn = "Japanese Fresh",
            category = PresetCategory.COLOR_STYLE,
            description = "日系低饱和，空气感，通透轻盈",
            thumbnailColor = "#E8F0F5",
            adjustments = Adjustments(
                exposure = 0.15f,
                contrast = -20f,
                highlights = -25f,
                shadows = 25f,
                blacks = 15f,
                saturation = -30f,
                vibrance = 10f,
                temperature = -5f,
                clarity = -15f,
                colorGrading = ColorGrading(
                    shadows = ColorGradingRegion(hue = 0f, saturation = 0f, luminance = 20f),
                    midtones = ColorGradingRegion(hue = 0f, saturation = 0f, luminance = 5f),
                    highlights = ColorGradingRegion(hue = 0f, saturation = 0f, luminance = -5f),
                    blending = 30f,
                    balance = 10f,
                ),
            ),
            tags = listOf("日系", "清新", "低饱和", "空气感"),
        ),

        // 赛博朋克
        FilmPreset(
            id = "style_cyberpunk",
            displayName = "赛博朋克",
            displayNameEn = "Cyberpunk",
            category = PresetCategory.COLOR_STYLE,
            description = "霓虹色彩，科幻未来感",
            thumbnailColor = "#FF00FF",
            adjustments = Adjustments(
                exposure = -0.1f,
                contrast = 25f,
                highlights = -10f,
                shadows = 15f,
                saturation = 30f,
                vibrance = 20f,
                temperature = -10f,
                clarity = 20f,
                colorGrading = ColorGrading(
                    shadows = ColorGradingRegion(hue = 280f, saturation = 30f, luminance = 10f),
                    midtones = ColorGradingRegion(hue = 200f, saturation = 15f, luminance = 0f),
                    highlights = ColorGradingRegion(hue = 330f, saturation = 25f, luminance = -10f),
                    blending = 60f,
                    balance = -5f,
                ),
                hslPurples = HslChannel(hue = 5f, saturation = 40f, luminance = 10f),
                hslMagentas = HslChannel(hue = -5f, saturation = 35f, luminance = 5f),
                hslBlues = HslChannel(hue = -15f, saturation = 30f, luminance = -5f),
            ),
            tags = listOf("赛博朋克", "霓虹", "科幻", "未来"),
        ),
    )

    // ── 预设管理功能 ────────────────────────────────────────────────

    /**
     * 自定义预设存储
     * 使用内存缓存 + JSON 序列化支持持久化
     */
    private val customPresets = mutableListOf<FilmPreset>()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * 获取所有预设（内置 + 自定义）
     */
    fun getAllPresets(): List<FilmPreset> {
        return BUILTIN_PRESETS + customPresets
    }

    /**
     * 按类别获取预设
     */
    fun getPresetsByCategory(category: PresetCategory): List<FilmPreset> {
        return if (category == PresetCategory.ALL) {
            getAllPresets()
        } else {
            getAllPresets().filter { it.category == category }
        }
    }

    /**
     * 搜索预设
     */
    fun searchPresets(query: String): List<FilmPreset> {
        val lowerQuery = query.lowercase()
        return getAllPresets().filter { preset ->
            preset.displayName.lowercase().contains(lowerQuery) ||
            preset.displayNameEn.lowercase().contains(lowerQuery) ||
            preset.description.lowercase().contains(lowerQuery) ||
            preset.tags.any { it.lowercase().contains(lowerQuery) }
        }
    }

    /**
     * 根据 ID 获取预设
     */
    fun getPresetById(id: String): FilmPreset? {
        return getAllPresets().find { it.id == id }
    }

    /**
     * 添加自定义预设
     */
    fun addCustomPreset(preset: FilmPreset): Boolean {
        // 检查 ID 是否已存在
        if (getAllPresets().any { it.id == preset.id }) {
            return false
        }
        val newPreset = preset.copy(
            isCustom = true,
            category = PresetCategory.CUSTOM,
            createdAt = System.currentTimeMillis()
        )
        customPresets.add(newPreset)
        return true
    }

    /**
     * 更新自定义预设
     */
    fun updateCustomPreset(preset: FilmPreset): Boolean {
        val index = customPresets.indexOfFirst { it.id == preset.id }
        if (index < 0) return false
        customPresets[index] = preset.copy(
            isCustom = true,
            category = PresetCategory.CUSTOM
        )
        return true
    }

    /**
     * 删除自定义预设
     */
    fun deleteCustomPreset(id: String): Boolean {
        return customPresets.removeIf { it.id == id }
    }

    /**
     * 从当前调整创建预设
     */
    fun createPresetFromAdjustments(
        id: String,
        displayName: String,
        displayNameEn: String,
        description: String,
        adjustments: Adjustments,
        tags: List<String> = emptyList()
    ): FilmPreset {
        return FilmPreset(
            id = id,
            displayName = displayName,
            displayNameEn = displayNameEn,
            category = PresetCategory.CUSTOM,
            description = description,
            thumbnailColor = generateThumbnailColor(adjustments),
            adjustments = adjustments,
            tags = tags,
            isCustom = true,
            createdAt = System.currentTimeMillis()
        )
    }

    /**
     * 根据调整参数生成预览色
     */
    private fun generateThumbnailColor(adjustments: Adjustments): String {
        // 基于温度和色彩分级生成代表色
        val tempOffset = adjustments.temperature / 100f
        val baseHue = when {
            tempOffset > 0.5f -> 30f  // 暖色（橙黄）
            tempOffset < -0.5f -> 220f  // 冷色（蓝）
            else -> 0f  // 中性
        }

        // 结合色彩分级的阴影色调
        val shadowHue = adjustments.colorGrading.shadows.hue
        val finalHue = if (shadowHue > 1f) shadowHue else baseHue

        // 转换为 RGB
        val rgb = ColorMath.hsvToRgb(finalHue, 0.3f, 0.85f)
        val r = (rgb[0] * 255).toInt().coerceIn(0, 255)
        val g = (rgb[1] * 255).toInt().coerceIn(0, 255)
        val b = (rgb[2] * 255).toInt().coerceIn(0, 255)

        return "#${r.toString(16).padStart(2, '0')}${g.toString(16).padStart(2, '0')}${b.toString(16).padStart(2, '0')}"
    }

    // ── 预设应用 ─────────────────────────────────────────────────────

    /**
     * 应用预设到调整参数
     *
     * @param baseAdjustments 基础调整参数（用户当前设置）
     * @param preset 要应用的预设
     * @param intensity 预设强度 [0,1]
     * @return 混合后的调整参数
     */
    fun applyPreset(
        baseAdjustments: Adjustments,
        preset: FilmPreset,
        intensity: Float = 1f
    ): Adjustments {
        if (intensity < 1e-6f) return baseAdjustments

        val presetAdj = preset.adjustments

        // 混合调整参数：使用线性插值
        return blendAdjustments(baseAdjustments, presetAdj, intensity)
    }

    /**
     * 混合两个调整参数
     *
     * 对每个参数进行线性插值，保持用户手动设置的参数不变
     * （仅覆盖预设中定义的参数）。
     */
    private fun blendAdjustments(
        base: Adjustments,
        preset: Adjustments,
        intensity: Float
    ): Adjustments {
        // 基础曝光调整（用户可能已手动设置）
        val blendedExposure = if (base.exposure != 0f) {
            base.exposure + preset.exposure * intensity * 0.5f
        } else {
            preset.exposure * intensity
        }

        // 对比度和色调
        val blendedContrast = blendValue(base.contrast, preset.contrast, intensity)
        val blendedHighlights = blendValue(base.highlights, preset.highlights, intensity)
        val blendedShadows = blendValue(base.shadows, preset.shadows, intensity)

        // 颜色参数
        val blendedSaturation = blendValue(base.saturation, preset.saturation, intensity)
        val blendedVibrance = blendValue(base.vibrance, preset.vibrance, intensity)
        val blendedTemperature = blendValue(base.temperature, preset.temperature, intensity)

        // 色彩分级（直接使用预设值，因为这通常是预设的核心）
        val blendedColorGrading = if (intensity >= 0.5f) {
            blendColorGrading(base.colorGrading, preset.colorGrading, intensity)
        } else {
            base.colorGrading
        }

        // HSL 调整
        val blendedHslReds = blendHslChannel(base.hslReds, preset.hslReds, intensity)
        val blendedHslOranges = blendHslChannel(base.hslOranges, preset.hslOranges, intensity)
        val blendedHslYellows = blendHslChannel(base.hslYellows, preset.hslYellows, intensity)
        val blendedHslGreens = blendHslChannel(base.hslGreens, preset.hslGreens, intensity)
        val blendedHslAquas = blendHslChannel(base.hslAquas, preset.hslAquas, intensity)
        val blendedHslBlues = blendHslChannel(base.hslBlues, preset.hslBlues, intensity)
        val blendedHslPurples = blendHslChannel(base.hslPurples, preset.hslPurples, intensity)
        val blendedHslMagentas = blendHslChannel(base.hslMagentas, preset.hslMagentas, intensity)

        // 效果参数
        val blendedClarity = blendValue(base.clarity, preset.clarity, intensity)
        val blendedDehaze = blendValue(base.dehaze, preset.dehaze, intensity)
        val blendedVignetteAmount = blendValue(base.vignetteAmount, preset.vignetteAmount, intensity)
        val blendedGrainAmount = blendValue(base.grainAmount, preset.grainAmount, intensity)

        return base.copy(
            exposure = blendedExposure,
            contrast = blendedContrast,
            highlights = blendedHighlights,
            shadows = blendedShadows,
            saturation = blendedSaturation,
            vibrance = blendedVibrance,
            temperature = blendedTemperature,
            colorGrading = blendedColorGrading,
            hslReds = blendedHslReds,
            hslOranges = blendedHslOranges,
            hslYellows = blendedHslYellows,
            hslGreens = blendedHslGreens,
            hslAquas = blendedHslAquas,
            hslBlues = blendedHslBlues,
            hslPurples = blendedHslPurples,
            hslMagentas = blendedHslMagentas,
            clarity = blendedClarity,
            dehaze = blendedDehaze,
            vignetteAmount = blendedVignetteAmount,
            grainAmount = blendedGrainAmount,
            filmIntensity = intensity,  // 记录预设强度
            filmId = preset.id,         // 记录预设 ID
        )
    }

    /**
     * 混合单个数值参数
     */
    private fun blendValue(base: Float, preset: Float, intensity: Float): Float {
        return base + (preset - base) * intensity
    }

    /**
     * 混合色彩分级参数
     */
    private fun blendColorGrading(base: ColorGrading, preset: ColorGrading, intensity: Float): ColorGrading {
        return ColorGrading(
            shadows = blendColorGradingRegion(base.shadows, preset.shadows, intensity),
            midtones = blendColorGradingRegion(base.midtones, preset.midtones, intensity),
            highlights = blendColorGradingRegion(base.highlights, preset.highlights, intensity),
            blending = blendValue(base.blending, preset.blending, intensity),
            balance = blendValue(base.balance, preset.balance, intensity),
        )
    }

    /**
     * 混合色彩分级区域参数
     */
    private fun blendColorGradingRegion(base: ColorGradingRegion, preset: ColorGradingRegion, intensity: Float): ColorGradingRegion {
        return ColorGradingRegion(
            hue = blendValue(base.hue, preset.hue, intensity),
            saturation = blendValue(base.saturation, preset.saturation, intensity),
            luminance = blendValue(base.luminance, preset.luminance, intensity),
        )
    }

    /**
     * 混合 HSL 通道参数
     */
    private fun blendHslChannel(base: HslChannel, preset: HslChannel, intensity: Float): HslChannel {
        return HslChannel(
            hue = blendValue(base.hue, preset.hue, intensity),
            saturation = blendValue(base.saturation, preset.saturation, intensity),
            luminance = blendValue(base.luminance, preset.luminance, intensity),
        )
    }

    // ── 序列化支持 ───────────────────────────────────────────────────

    /**
     * 序列化预设为 JSON
     */
    fun serializePreset(preset: FilmPreset): String {
        return json.encodeToString(preset)
    }

    /**
     * 从 JSON 反序列化预设
     */
    fun deserializePreset(jsonString: String): FilmPreset? {
        return try {
            json.decodeFromString<FilmPreset>(jsonString)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 序列化所有自定义预设
     */
    fun serializeCustomPresets(): String {
        return json.encodeToString(customPresets.toList())
    }

    /**
     * 反序列化自定义预设列表
     */
    fun deserializeCustomPresets(jsonString: String): Boolean {
        return try {
            val presets = json.decodeFromString<List<FilmPreset>>(jsonString)
            customPresets.clear()
            customPresets.addAll(presets)
            true
        } catch (e: Exception) {
            false
        }
    }

    // ── 与 FilmSimulation 集成 ──────────────────────────────────────

    /**
     * 将 FilmSimulation 转换为 FilmPreset
     *
     * 用于与现有的 FilmSimulation 系统兼容。
     */
    fun convertFilmSimulationToPreset(film: FilmSimulation): FilmPreset {
        // 根据 FilmSimulation 的类别映射到预设类别
        val category = when (film.category) {
            FilmCategory.CLASSIC -> PresetCategory.COLOR_STYLE
            FilmCategory.EMOTIONAL -> PresetCategory.PORTRAIT
            FilmCategory.STRUCTURAL -> PresetCategory.LANDSCAPE
        }

        return FilmPreset(
            id = film.id,
            displayName = film.displayName,
            displayNameEn = film.displayNameEn,
            category = category,
            description = film.description,
            thumbnailColor = "#808080",  // 默认颜色
            adjustments = film.baseAdjustments,
            tags = listOf(film.referenceFilm),
        )
    }

    /**
     * 获取推荐预设
     *
     * 基于图像特征智能推荐适合的预设。
     */
    fun getRecommendedPresets(
        isPortrait: Boolean,
        isLandscape: Boolean,
        hasWarmTone: Boolean,
        hasCoolTone: Boolean
    ): List<FilmPreset> {
        val recommendations = mutableListOf<FilmPreset>()

        if (isPortrait) {
            recommendations.addAll(getPresetsByCategory(PresetCategory.PORTRAIT))
        }

        if (isLandscape) {
            recommendations.addAll(getPresetsByCategory(PresetCategory.LANDSCAPE))
        }

        if (hasWarmTone) {
            recommendations.add(getPresetById("landscape_golden_hour")!!)
            recommendations.add(getPresetById("portrait_warm")!!)
        }

        if (hasCoolTone) {
            recommendations.add(getPresetById("landscape_blue_hour")!!)
            recommendations.add(getPresetById("portrait_cool")!!)
        }

        return recommendations.distinctBy { it.id }
    }
}
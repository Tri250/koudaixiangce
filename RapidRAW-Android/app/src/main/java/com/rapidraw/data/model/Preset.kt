package com.rapidraw.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Preset(
    val id: String,
    val name: String,
    val description: String? = null,
    val category: String? = null,
    val filmId: String? = null,
    val adjustments: Adjustments = Adjustments(),
    val isBuiltIn: Boolean = false,
    val thumbnailPath: String? = null,
    val previewGradient: List<Long> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
)

enum class HasselbladMasterFilter(
    val id: String,
    val displayName: String,
    val description: String,
    val category: String,
    val filmId: String,
    val previewGradient: List<Long>,
    val adjustments: Adjustments,
) {
    HEWA(
        id = "hewa_portrait",
        displayName = "和光",
        description = "柔和自然，适合人像摄影",
        category = "人像",
        filmId = "hasselblad_hewa",
        previewGradient = listOf(0xFFF5E6D3, 0xFFE8D5C4),
        adjustments = Adjustments(
            exposure = 0.1f,
            highlights = -15f,
            shadows = 10f,
            contrast = -8f,
            vibrance = 8f,
            temperature = 3f,
            clarity = -5f,
        ),
    ),
    NONGYU(
        id = "nongyu_street",
        displayName = "浓郁",
        description = "高饱和色彩，街头故事感",
        category = "街拍",
        filmId = "hasselblad_nongyu",
        previewGradient = listOf(0xFF4A3728, 0xFF8B6914),
        adjustments = Adjustments(
            saturation = 12f,
            vibrance = 15f,
            contrast = 10f,
            temperature = -3f,
            clarity = 8f,
        ),
    ),
    FUGU(
        id = "fugu_film",
        displayName = "复古",
        description = "怀旧复古色调",
        category = "人像",
        filmId = "hasselblad_fugu",
        previewGradient = listOf(0xFF8B7355, 0xFFA0826D),
        adjustments = Adjustments(
            temperature = 8f,
            saturation = -8f,
            vignetteAmount = 12f,
            grainAmount = 20f,
        ),
    ),
    QINGXIN(
        id = "qingxin_landscape",
        displayName = "清新",
        description = "通透蓝天绿色",
        category = "风景",
        filmId = "hasselblad_qingxin",
        previewGradient = listOf(0xFF87CEEB, 0xFF98FB98),
        adjustments = Adjustments(
            exposure = 0.15f,
            brightness = 0.1f,
            contrast = -15f,
            saturation = -20f,
            shadows = 15f,
            blacks = 10f,
            temperature = -2f,
        ),
    ),
    TONGTOU(
        id = "tongtou_arch",
        displayName = "通透",
        description = "清晰锐利的建筑质感",
        category = "风景",
        filmId = "hasselblad_tongtou",
        previewGradient = listOf(0xFFD3D3D3, 0xFFA9A9A9),
        adjustments = Adjustments(
            vibrance = 5f,
            clarity = 5f,
        ),
    ),
    NIHONG(
        id = "nihong_night",
        displayName = "霓虹",
        description = "赛博朋克城市夜景",
        category = "夜景",
        filmId = "hasselblad_nihong",
        previewGradient = listOf(0xFFFF1493, 0xFF00CED1),
        adjustments = Adjustments(
            contrast = 20f,
            saturation = 10f,
            highlights = -10f,
            shadows = -5f,
            temperature = 5f,
            clarity = 10f,
            dehaze = 5f,
        ),
    ),
    LENGDIAO(
        id = "lengdiao_flash",
        displayName = "冷调闪光",
        description = "冷色温闪光灯效果",
        category = "人像",
        filmId = "hasselblad_lengdiao",
        previewGradient = listOf(0xFFB0C4DE, 0xFF4682B4),
        adjustments = Adjustments(
            temperature = -12f,
            contrast = 8f,
            saturation = -5f,
            highlights = -5f,
        ),
    ),
    NUANDIAO(
        id = "nuandiao_flash",
        displayName = "暖调闪光",
        description = "暖色温闪光灯效果",
        category = "人像",
        filmId = "hasselblad_nuandiao",
        previewGradient = listOf(0xFFF4A460, 0xFFD2691E),
        adjustments = Adjustments(
            temperature = 10f,
            contrast = 5f,
            saturation = 3f,
            shadows = 5f,
        ),
    ),
    HEIBAI(
        id = "heibai_noir",
        displayName = "反差黑白",
        description = "高反差黑白胶片",
        category = "街拍",
        filmId = "hasselblad_heibai",
        previewGradient = listOf(0xFF333333, 0xFF888888),
        adjustments = Adjustments(
            saturation = -100f,
            contrast = 25f,
            clarity = 15f,
            grainAmount = 25f,
            vignetteAmount = 10f,
            highlights = -8f,
            shadows = 5f,
        ),
    ),
    ;

    fun toAdjustments(): Adjustments = adjustments

    fun toFilmSimulation(): FilmSimulation = when (this) {
        HEWA -> FilmSimulation.getById("hasselblad_hewa")!!
        NONGYU -> FilmSimulation.getById("hasselblad_nongyu")!!
        FUGU -> FilmSimulation.getById("hasselblad_fugu")!!
        QINGXIN -> FilmSimulation.getById("hasselblad_qingxin")!!
        TONGTOU -> FilmSimulation.getById("hasselblad_tongtou")!!
        NIHONG -> FilmSimulation.getById("hasselblad_nihong")!!
        LENGDIAO -> FilmSimulation.getById("hasselblad_lengdiao")!!
        NUANDIAO -> FilmSimulation.getById("hasselblad_nuandiao")!!
        HEIBAI -> FilmSimulation.getById("hasselblad_heibai")!!
    }
}

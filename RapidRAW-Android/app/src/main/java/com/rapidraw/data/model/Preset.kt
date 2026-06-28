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

enum class HasselbladMasterFilter(val displayName: String, val adjustments: Adjustments) {
    HEWA(
        displayName = "和光",
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
        displayName = "浓郁",
        adjustments = Adjustments(
            saturation = 12f,
            vibrance = 15f,
            contrast = 10f,
            temperature = -3f,
            clarity = 8f,
        ),
    ),
    FUGU(
        displayName = "复古",
        adjustments = Adjustments(
            temperature = 8f,
            saturation = -8f,
            vignetteAmount = 12f,
            grainAmount = 20f,
        ),
    ),
    QINGXIN(
        displayName = "清新",
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
        displayName = "通透",
        adjustments = Adjustments(
            vibrance = 5f,
            clarity = 5f,
        ),
    ),
    NIHONG(
        displayName = "霓虹",
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
        displayName = "冷调闪光",
        adjustments = Adjustments(
            temperature = -12f,
            contrast = 8f,
            saturation = -5f,
            highlights = -5f,
        ),
    ),
    NUANDIAO(
        displayName = "暖调闪光",
        adjustments = Adjustments(
            temperature = 10f,
            contrast = 5f,
            saturation = 3f,
            shadows = 5f,
        ),
    ),
    HEIBAI(
        displayName = "反差黑白",
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

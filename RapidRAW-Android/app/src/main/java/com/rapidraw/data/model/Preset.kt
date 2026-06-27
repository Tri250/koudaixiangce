package com.rapidraw.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Preset(
    val id: Long,
    val name: String,
    val folder: String? = null,
    val adjustments: Adjustments,
    val isBuiltIn: Boolean = false,
    val thumbnailPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

enum class HasselbladMasterFilter(val displayName: String, val adjustments: Adjustments) {
    NATURAL(
        displayName = "Natural",
        adjustments = Adjustments(
            clarity = 5f,
            temperature = 0f,
            vibrance = 8f,
        )
    ),
    VIBRANT(
        displayName = "Vibrant",
        adjustments = Adjustments(
            saturation = 20f,
            vibrance = 25f,
            contrast = 15f,
            clarity = 10f,
            dehaze = 5f,
        )
    ),
    CLASSIC(
        displayName = "Classic",
        adjustments = Adjustments(
            contrast = 10f,
            saturation = -10f,
            grainAmount = 15f,
            vignetteAmount = 8f,
            temperature = 5f,
        )
    ),
    PORTRAIT(
        displayName = "Portrait",
        adjustments = Adjustments(
            temperature = 8f,
            tint = 5f,
            clarity = -5f,
            vibrance = 10f,
            vignetteAmount = 5f,
            shadows = 10f,
        )
    ),
    LANDSCAPE(
        displayName = "Landscape",
        adjustments = Adjustments(
            clarity = 15f,
            vibrance = 15f,
            dehaze = 8f,
            saturation = 10f,
            structure = 10f,
        )
    ),
    MONOCHROME(
        displayName = "Monochrome",
        adjustments = Adjustments(
            saturation = -100f,
            contrast = 20f,
            clarity = 10f,
            grainAmount = 25f,
            vignetteAmount = 10f,
        )
    ),
}

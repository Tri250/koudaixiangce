package com.rapidraw.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Coord(val x: Float, val y: Float)

@Serializable
data class HslChannel(
    val hue: Float = 0f,
    val saturation: Float = 0f,
    val luminance: Float = 0f,
)

@Serializable
data class ColorGradingRegion(
    val hue: Float = 0f,
    val saturation: Float = 0f,
    val luminance: Float = 0f,
)

@Serializable
data class ColorGrading(
    val shadows: ColorGradingRegion = ColorGradingRegion(),
    val midtones: ColorGradingRegion = ColorGradingRegion(),
    val highlights: ColorGradingRegion = ColorGradingRegion(),
    val blending: Float = 0f,
    val balance: Float = 0f,
)

@Serializable
data class ColorCalibration(
    val shadowsTint: Float = 0f,
    val redHue: Float = 0f,
    val redSaturation: Float = 0f,
    val greenHue: Float = 0f,
    val greenSaturation: Float = 0f,
    val blueHue: Float = 0f,
    val blueSaturation: Float = 0f,
)

@Serializable
data class CropData(
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 1f,
    val height: Float = 1f,
    val aspectRatio: Float? = null,
    val rotation: Float = 0f,
)

@Serializable
data class SubMaskData(
    val type: String = "brush",
    val points: List<Coord> = emptyList(),
    val radius: Float = 50f,
    val feather: Float = 0f,
)

@Serializable
data class MaskContainer(
    val id: String = "",
    val name: String = "",
    val visible: Boolean = true,
    val invert: Boolean = false,
    val opacity: Float = 1f,
    val adjustments: Adjustments? = null,
    val subMasks: List<SubMaskData> = emptyList(),
)

@Serializable
data class Adjustments(
    // ── Basic ───────────────────────────────────────────────────
    val exposure: Float = 0f,
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val whites: Float = 0f,
    val blacks: Float = 0f,

    // ── Color ──────────────────────────────────────────────────
    val temperature: Float = 0f,
    val tint: Float = 0f,
    val saturation: Float = 0f,
    val vibrance: Float = 0f,

    // ── HSL (8-color mixer) ───────────────────────────────────
    val hslReds: HslChannel = HslChannel(),
    val hslOranges: HslChannel = HslChannel(),
    val hslYellows: HslChannel = HslChannel(),
    val hslGreens: HslChannel = HslChannel(),
    val hslAquas: HslChannel = HslChannel(),
    val hslBlues: HslChannel = HslChannel(),
    val hslPurples: HslChannel = HslChannel(),
    val hslMagentas: HslChannel = HslChannel(),

    // ── Curves ─────────────────────────────────────────────────
    val lumaCurve: List<Coord> = listOf(Coord(0f, 0f), Coord(255f, 255f)),
    val redCurve: List<Coord> = listOf(Coord(0f, 0f), Coord(255f, 255f)),
    val greenCurve: List<Coord> = listOf(Coord(0f, 0f), Coord(255f, 255f)),
    val blueCurve: List<Coord> = listOf(Coord(0f, 0f), Coord(255f, 255f)),

    // ── Color Grading ─────────────────────────────────────────
    val colorGrading: ColorGrading = ColorGrading(),

    // ── Color Calibration ──────────────────────────────────────
    val colorCalibration: ColorCalibration = ColorCalibration(),

    // ── Details ───────────────────────────────────────────────
    val sharpness: Float = 0f,
    val lumaNoiseReduction: Float = 0f,
    val colorNoiseReduction: Float = 0f,
    val clarity: Float = 0f,
    val dehaze: Float = 0f,
    val structure: Float = 0f,
    val centre: Float = 0f,
    val chromaticAberrationRedCyan: Float = 0f,
    val chromaticAberrationBlueYellow: Float = 0f,

    // ── Effects ───────────────────────────────────────────────
    val vignetteAmount: Float = 0f,
    val vignetteMidpoint: Float = 50f,
    val vignetteRoundness: Float = 0f,
    val vignetteFeather: Float = 50f,
    val grainAmount: Float = 0f,
    val grainSize: Float = 25f,
    val grainRoughness: Float = 50f,
    val lutIntensity: Float = 100f,
    val glowAmount: Float = 0f,
    val halationAmount: Float = 0f,
    val flareAmount: Float = 0f,

    // ── Transform ─────────────────────────────────────────────
    val rotation: Float = 0f,
    val orientationSteps: Int = 0,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val crop: CropData? = null,
    val transformDistortion: Float = 0f,
    val transformVertical: Float = 0f,
    val transformHorizontal: Float = 0f,
    val transformRotate: Float = 0f,
    val transformAspect: Float = 0f,
    val transformScale: Float = 100f,
    val transformXOffset: Float = 0f,
    val transformYOffset: Float = 0f,

    // ── Tone Mapper ───────────────────────────────────────────
    val toneMapper: String = "agx",

    // ── Clipping ──────────────────────────────────────────────
    val showClipping: Boolean = false,

    // ── Masks ─────────────────────────────────────────────────
    val masks: List<MaskContainer> = emptyList(),

    // ── AI Patches ────────────────────────────────────────────
    val aiPatches: List<Nothing> = emptyList(),
) {
    fun copyByField(key: String, value: Float): Adjustments = when (key) {
        // Basic
        "exposure" -> copy(exposure = value.coerceIn(-5f, 5f))
        "brightness" -> copy(brightness = value.coerceIn(-5f, 5f))
        "contrast" -> copy(contrast = value.coerceIn(-100f, 100f))
        "highlights" -> copy(highlights = value.coerceIn(-150f, 150f))
        "shadows" -> copy(shadows = value.coerceIn(-100f, 100f))
        "whites" -> copy(whites = value.coerceIn(-30f, 30f))
        "blacks" -> copy(blacks = value.coerceIn(-60f, 60f))
        // Color
        "temperature" -> copy(temperature = value.coerceIn(-100f, 100f))
        "tint" -> copy(tint = value.coerceIn(-100f, 100f))
        "saturation" -> copy(saturation = value.coerceIn(-100f, 100f))
        "vibrance" -> copy(vibrance = value.coerceIn(-100f, 100f))
        // HSL - Reds
        "hslReds.hue" -> copy(hslReds = hslReds.copy(hue = value.coerceIn(-100f, 100f)))
        "hslReds.saturation" -> copy(hslReds = hslReds.copy(saturation = value.coerceIn(-100f, 100f)))
        "hslReds.luminance" -> copy(hslReds = hslReds.copy(luminance = value.coerceIn(-100f, 100f)))
        // HSL - Oranges
        "hslOranges.hue" -> copy(hslOranges = hslOranges.copy(hue = value.coerceIn(-100f, 100f)))
        "hslOranges.saturation" -> copy(hslOranges = hslOranges.copy(saturation = value.coerceIn(-100f, 100f)))
        "hslOranges.luminance" -> copy(hslOranges = hslOranges.copy(luminance = value.coerceIn(-100f, 100f)))
        // HSL - Yellows
        "hslYellows.hue" -> copy(hslYellows = hslYellows.copy(hue = value.coerceIn(-100f, 100f)))
        "hslYellows.saturation" -> copy(hslYellows = hslYellows.copy(saturation = value.coerceIn(-100f, 100f)))
        "hslYellows.luminance" -> copy(hslYellows = hslYellows.copy(luminance = value.coerceIn(-100f, 100f)))
        // HSL - Greens
        "hslGreens.hue" -> copy(hslGreens = hslGreens.copy(hue = value.coerceIn(-100f, 100f)))
        "hslGreens.saturation" -> copy(hslGreens = hslGreens.copy(saturation = value.coerceIn(-100f, 100f)))
        "hslGreens.luminance" -> copy(hslGreens = hslGreens.copy(luminance = value.coerceIn(-100f, 100f)))
        // HSL - Aquas
        "hslAquas.hue" -> copy(hslAquas = hslAquas.copy(hue = value.coerceIn(-100f, 100f)))
        "hslAquas.saturation" -> copy(hslAquas = hslAquas.copy(saturation = value.coerceIn(-100f, 100f)))
        "hslAquas.luminance" -> copy(hslAquas = hslAquas.copy(luminance = value.coerceIn(-100f, 100f)))
        // HSL - Blues
        "hslBlues.hue" -> copy(hslBlues = hslBlues.copy(hue = value.coerceIn(-100f, 100f)))
        "hslBlues.saturation" -> copy(hslBlues = hslBlues.copy(saturation = value.coerceIn(-100f, 100f)))
        "hslBlues.luminance" -> copy(hslBlues = hslBlues.copy(luminance = value.coerceIn(-100f, 100f)))
        // HSL - Purples
        "hslPurples.hue" -> copy(hslPurples = hslPurples.copy(hue = value.coerceIn(-100f, 100f)))
        "hslPurples.saturation" -> copy(hslPurples = hslPurples.copy(saturation = value.coerceIn(-100f, 100f)))
        "hslPurples.luminance" -> copy(hslPurples = hslPurples.copy(luminance = value.coerceIn(-100f, 100f)))
        // HSL - Magentas
        "hslMagentas.hue" -> copy(hslMagentas = hslMagentas.copy(hue = value.coerceIn(-100f, 100f)))
        "hslMagentas.saturation" -> copy(hslMagentas = hslMagentas.copy(saturation = value.coerceIn(-100f, 100f)))
        "hslMagentas.luminance" -> copy(hslMagentas = hslMagentas.copy(luminance = value.coerceIn(-100f, 100f)))
        // Color Grading
        "colorGrading.shadows.hue" -> copy(colorGrading = colorGrading.copy(shadows = colorGrading.shadows.copy(hue = value.coerceIn(0f, 360f))))
        "colorGrading.shadows.saturation" -> copy(colorGrading = colorGrading.copy(shadows = colorGrading.shadows.copy(saturation = value.coerceIn(0f, 100f))))
        "colorGrading.shadows.luminance" -> copy(colorGrading = colorGrading.copy(shadows = colorGrading.shadows.copy(luminance = value.coerceIn(-100f, 100f))))
        "colorGrading.midtones.hue" -> copy(colorGrading = colorGrading.copy(midtones = colorGrading.midtones.copy(hue = value.coerceIn(0f, 360f))))
        "colorGrading.midtones.saturation" -> copy(colorGrading = colorGrading.copy(midtones = colorGrading.midtones.copy(saturation = value.coerceIn(0f, 100f))))
        "colorGrading.midtones.luminance" -> copy(colorGrading = colorGrading.copy(midtones = colorGrading.midtones.copy(luminance = value.coerceIn(-100f, 100f))))
        "colorGrading.highlights.hue" -> copy(colorGrading = colorGrading.copy(highlights = colorGrading.highlights.copy(hue = value.coerceIn(0f, 360f))))
        "colorGrading.highlights.saturation" -> copy(colorGrading = colorGrading.copy(highlights = colorGrading.highlights.copy(saturation = value.coerceIn(0f, 100f))))
        "colorGrading.highlights.luminance" -> copy(colorGrading = colorGrading.copy(highlights = colorGrading.highlights.copy(luminance = value.coerceIn(-100f, 100f))))
        "colorGrading.blending" -> copy(colorGrading = colorGrading.copy(blending = value.coerceIn(0f, 100f)))
        "colorGrading.balance" -> copy(colorGrading = colorGrading.copy(balance = value.coerceIn(-100f, 100f)))
        // Color Calibration
        "colorCalibration.shadowsTint" -> copy(colorCalibration = colorCalibration.copy(shadowsTint = value.coerceIn(-100f, 100f)))
        "colorCalibration.redHue" -> copy(colorCalibration = colorCalibration.copy(redHue = value.coerceIn(-100f, 100f)))
        "colorCalibration.redSaturation" -> copy(colorCalibration = colorCalibration.copy(redSaturation = value.coerceIn(-100f, 100f)))
        "colorCalibration.greenHue" -> copy(colorCalibration = colorCalibration.copy(greenHue = value.coerceIn(-100f, 100f)))
        "colorCalibration.greenSaturation" -> copy(colorCalibration = colorCalibration.copy(greenSaturation = value.coerceIn(-100f, 100f)))
        "colorCalibration.blueHue" -> copy(colorCalibration = colorCalibration.copy(blueHue = value.coerceIn(-100f, 100f)))
        "colorCalibration.blueSaturation" -> copy(colorCalibration = colorCalibration.copy(blueSaturation = value.coerceIn(-100f, 100f)))
        // Details
        "sharpness" -> copy(sharpness = value.coerceIn(0f, 150f))
        "lumaNoiseReduction" -> copy(lumaNoiseReduction = value.coerceIn(0f, 100f))
        "colorNoiseReduction" -> copy(colorNoiseReduction = value.coerceIn(0f, 100f))
        "clarity" -> copy(clarity = value.coerceIn(-100f, 100f))
        "dehaze" -> copy(dehaze = value.coerceIn(-100f, 100f))
        "structure" -> copy(structure = value.coerceIn(-100f, 100f))
        "centre" -> copy(centre = value.coerceIn(-100f, 100f))
        "chromaticAberrationRedCyan" -> copy(chromaticAberrationRedCyan = value.coerceIn(-100f, 100f))
        "chromaticAberrationBlueYellow" -> copy(chromaticAberrationBlueYellow = value.coerceIn(-100f, 100f))
        // Effects
        "vignetteAmount" -> copy(vignetteAmount = value.coerceIn(-100f, 100f))
        "vignetteMidpoint" -> copy(vignetteMidpoint = value.coerceIn(0f, 100f))
        "vignetteRoundness" -> copy(vignetteRoundness = value.coerceIn(-100f, 100f))
        "vignetteFeather" -> copy(vignetteFeather = value.coerceIn(0f, 100f))
        "grainAmount" -> copy(grainAmount = value.coerceIn(0f, 100f))
        "grainSize" -> copy(grainSize = value.coerceIn(0f, 100f))
        "grainRoughness" -> copy(grainRoughness = value.coerceIn(0f, 100f))
        "lutIntensity" -> copy(lutIntensity = value.coerceIn(0f, 100f))
        "glowAmount" -> copy(glowAmount = value.coerceIn(0f, 100f))
        "halationAmount" -> copy(halationAmount = value.coerceIn(0f, 100f))
        "flareAmount" -> copy(flareAmount = value.coerceIn(0f, 100f))
        // Transform
        "rotation" -> copy(rotation = value.coerceIn(-180f, 180f))
        "transformDistortion" -> copy(transformDistortion = value.coerceIn(-100f, 100f))
        "transformVertical" -> copy(transformVertical = value.coerceIn(-100f, 100f))
        "transformHorizontal" -> copy(transformHorizontal = value.coerceIn(-100f, 100f))
        "transformRotate" -> copy(transformRotate = value.coerceIn(-45f, 45f))
        "transformAspect" -> copy(transformAspect = value.coerceIn(-100f, 100f))
        "transformScale" -> copy(transformScale = value.coerceIn(10f, 200f))
        "transformXOffset" -> copy(transformXOffset = value.coerceIn(-100f, 100f))
        "transformYOffset" -> copy(transformYOffset = value.coerceIn(-100f, 100f))
        else -> this
    }
}

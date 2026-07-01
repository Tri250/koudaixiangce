package com.rapidraw.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class Coord(val x: Float, val y: Float)

/**
 * Represents an AI inpaint patch applied to a region of the image.
 */
@Immutable
@Serializable
data class AiPatch(
    val id: String = "",
    val maskPath: List<Coord> = emptyList(),
    val boundingBox: Coord = Coord(0f, 0f),
    val boundingBoxSize: Coord = Coord(0f, 0f),
    val applied: Boolean = false,
)

@Immutable
@Serializable
data class HslChannel(
    val hue: Float = 0f,
    val saturation: Float = 0f,
    val luminance: Float = 0f,
)

@Immutable
@Serializable
data class ColorGradingRegion(
    val hue: Float = 0f,
    val saturation: Float = 0f,
    val luminance: Float = 0f,
)

@Immutable
@Serializable
data class ColorGrading(
    val shadows: ColorGradingRegion = ColorGradingRegion(),
    val midtones: ColorGradingRegion = ColorGradingRegion(),
    val highlights: ColorGradingRegion = ColorGradingRegion(),
    val blending: Float = 0f,
    val balance: Float = 0f,
)

@Immutable
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

@Immutable
@Serializable
data class CropData(
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 1f,
    val height: Float = 1f,
    val aspectRatio: Float? = null,
    val rotation: Float = 0f,
)

@Immutable
@Serializable
data class SubMaskData(
    val type: String = "brush",
    val points: List<Coord> = emptyList(),
    val radius: Float = 50f,
    val feather: Float = 0f,
)

@Immutable
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

@Immutable
@Serializable
data class Adjustments(
    // ── Basic ───────────────────────────────────────────────────
    val exposure: Float = 0f,
    val toneLevel: Float = 0f,              // 影调 -1..1, combined brightness control
    val filmIntensity: Float = 1f,          // 滤镜强度 0..1
    val greenMagenta: Float = 0f,           // 青品 -1..1, green-magenta axis
    val softGlow: Float = 0f,               // 柔光 0..1, soft glow/bloom

    // Film simulation parameters (set when a film is selected)
    val filmId: String = "",                // Selected film simulation ID
    val filmHighlightRollOff: Float = 0f,   // 0..1
    val filmShadowLift: Float = 0f,         // 0..1
    val filmDrCompression: Float = 0f,      // 0..1
    val filmRedShift: Float = 0f,           // -1..1
    val filmGreenShift: Float = 0f,         // -1..1
    val filmBlueShift: Float = 0f,          // -1..1
    val filmSaturation: Float = 0f,         // -1..1
    val filmContrast: Float = 0f,           // -1..1
    val filmGrainAmount: Float = 0f,        // 0..1
    val filmGrainSize: Float = 0f,          // 0..1
    val filmGrainRoughness: Float = 0f,     // 0..1
    val filmCurvePoints: List<Pair<Float, Float>> = listOf(0f to 0f, 51f to 51f, 102f to 102f, 153f to 153f, 204f to 204f, 255f to 255f),

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

    // ── Color Grading ─────────────────────────────────────────────
    val colorGrading: ColorGrading = ColorGrading(),

    // ── CDL Color Grading (Lift/Gamma/Gain per-channel offsets) ──
    val colorGradingShadowsR: Float = 0f,
    val colorGradingShadowsG: Float = 0f,
    val colorGradingShadowsB: Float = 0f,
    val colorGradingMidtonesR: Float = 0f,
    val colorGradingMidtonesG: Float = 0f,
    val colorGradingMidtonesB: Float = 0f,
    val colorGradingHighlightsR: Float = 0f,
    val colorGradingHighlightsG: Float = 0f,
    val colorGradingHighlightsB: Float = 0f,

    // ── Color Calibration ──────────────────────────────────────
    val colorCalibration: ColorCalibration = ColorCalibration(),

    // ── Channel Mixer ──────────────────────────────────────────
    val channelMixerRedOutRed: Float = 100f,
    val channelMixerRedOutGreen: Float = 0f,
    val channelMixerRedOutBlue: Float = 0f,
    val channelMixerGreenOutRed: Float = 0f,
    val channelMixerGreenOutGreen: Float = 100f,
    val channelMixerGreenOutBlue: Float = 0f,
    val channelMixerBlueOutRed: Float = 0f,
    val channelMixerBlueOutGreen: Float = 0f,
    val channelMixerBlueOutBlue: Float = 100f,
    val channelMixerMonochrome: Boolean = false,

    // ── Split Toning ───────────────────────────────────────────
    val splitToningHighlightHue: Float = 0f,     // 0..360
    val splitToningHighlightSaturation: Float = 0f, // 0..100
    val splitToningShadowHue: Float = 0f,         // 0..360
    val splitToningShadowSaturation: Float = 0f,   // 0..100
    val splitToningBalance: Float = 0f,            // -100..100

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
    val agxContrast: Float = 0f,                 // 0..1
    val agxPedestal: Float = 0f,                 // 0..0.5

    // ── 2026 Color Science (AlcedoStudio integration) ──────────
    // 0=AgX, 1=ACES 2.0, 2=OpenDRT, 3=Standard
    val colorScienceMode: Int = 0,
    // 0=sRGB, 1=Display P3, 2=Rec.2020
    val displayColorSpace: Int = 0,
    // 0=SDR, 1=HDR10/PQ, 2=HLG
    val eotf: Int = 0,
    val peakLuminanceNits: Float = 1000f,        // HDR peak luminance

    // ── Oklab Perceptual Color Space (ZenFilters pipeline) ──────
    // ZenFilters/AlcedoStudio: perceptually uniform color operations in oklab space
    // Hue rotation: -1.0=CCW 180°, 0=no change, 1.0=CW 180°
    val oklabHueShift: Float = 0f,
    // Saturation: -1.0=desaturate to gray, 0=no change, 1.0=double chroma
    val oklabSaturation: Float = 0f,
    // Chroma: -1.0=reduce to 25%, 0=no change, 1.0=increase to 4x
    val oklabChroma: Float = 0f,
    // Lightness: -1.0=darken, 0=no change, 1.0=brighten (S-curve)
    val oklabLightness: Float = 0f,
    // Contrast: -1.0=flatten, 0=no change, 1.0=maximum contrast (S-curve around L=0.5)
    val oklabContrast: Float = 0f,
    // Texture preserve: 0=full oklab blend, 1.0=preserve edges and texture
    val oklabTextureAmount: Float = 0f,

    // ── LUT Library (AlcedoStudio integration) ────────────────
    val activeLutId: String = "",                // 当前应用的 LUT ID

    // ── HDR Export (2026 Android 14+ Ultra HDR) ───────────────
    // 0=SDR JPEG, 1=Ultra HDR JPEG, 2=HEIF 10-bit
    val hdrExportFormat: Int = 0,
    val hdrPeakLuminance: Float = 1000f,         // nits
    val hdrMaxBoostStop: Float = 4f,             // stops

    // ── Lens Correction ───────────────────────────────────────
    val lensDistortion: Float = 0f,              // -100..100
    val lensVignette: Float = 0f,                // -100..100
    val lensTca: Float = 0f,                     // -100..100
    val lensFocalLength: Float = 50f,            // mm

    // ── Clipping ──────────────────────────────────────────────
    val showClipping: Boolean = false,

    // ── Masks ─────────────────────────────────────────────────
    val masks: List<MaskContainer> = emptyList(),
    val flowMaskIntensity: Float = 100f,

    // ── Perspective Correction ────────────────────────────────
    val perspectiveVertical: Float = 0f,
    val perspectiveHorizontal: Float = 0f,
    val perspectiveRotate: Float = 0f,
    val perspectiveScale: Float = 100f,
    val perspectiveAspect: Float = 0f,

    // ── Local Tint ───────────────────────────────────────────
    val shadowsTintHue: Float = 0f,
    val shadowsTintSaturation: Float = 0f,
    val highlightsTintHue: Float = 0f,
    val highlightsTintSaturation: Float = 0f,

    // ── Edge Light / Rim Light ───────────────────────────────
    val edgeLightAmount: Float = 0f,
    val edgeLightHue: Float = 30f,
    val edgeLightSaturation: Float = 0.5f,

    // ── Color Range Selector ─────────────────────────────────
    val colorRangeHue: Float = 0f,
    val colorRangeWidth: Float = 30f,
    val colorRangeSatAdjust: Float = 0f,
    val colorRangeLumAdjust: Float = 0f,

    // ── AI Patches ────────────────────────────────────────────
    val aiPatches: List<AiPatch> = emptyList(),
) {
    fun copyByField(key: String, value: Float): Adjustments = when (key) {
        // Basic
        "exposure" -> copy(exposure = value.coerceIn(-5f, 5f))
        "toneLevel" -> copy(toneLevel = value.coerceIn(-1f, 1f))
        "filmIntensity" -> copy(filmIntensity = value.coerceIn(0f, 1f))
        "greenMagenta" -> copy(greenMagenta = value.coerceIn(-1f, 1f))
        "softGlow" -> copy(softGlow = value.coerceIn(0f, 1f))
        "filmHighlightRollOff" -> copy(filmHighlightRollOff = value.coerceIn(0f, 1f))
        "filmShadowLift" -> copy(filmShadowLift = value.coerceIn(0f, 1f))
        "filmDrCompression" -> copy(filmDrCompression = value.coerceIn(0f, 1f))
        "filmRedShift" -> copy(filmRedShift = value.coerceIn(-1f, 1f))
        "filmGreenShift" -> copy(filmGreenShift = value.coerceIn(-1f, 1f))
        "filmBlueShift" -> copy(filmBlueShift = value.coerceIn(-1f, 1f))
        "filmSaturation" -> copy(filmSaturation = value.coerceIn(-1f, 1f))
        "filmContrast" -> copy(filmContrast = value.coerceIn(-1f, 1f))
        "filmGrainAmount" -> copy(filmGrainAmount = value.coerceIn(0f, 1f))
        "filmGrainSize" -> copy(filmGrainSize = value.coerceIn(0f, 1f))
        "filmGrainRoughness" -> copy(filmGrainRoughness = value.coerceIn(0f, 1f))
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
        // CDL Color Grading
        "colorGradingShadowsR" -> copy(colorGradingShadowsR = value.coerceIn(-100f, 100f))
        "colorGradingShadowsG" -> copy(colorGradingShadowsG = value.coerceIn(-100f, 100f))
        "colorGradingShadowsB" -> copy(colorGradingShadowsB = value.coerceIn(-100f, 100f))
        "colorGradingMidtonesR" -> copy(colorGradingMidtonesR = value.coerceIn(-100f, 100f))
        "colorGradingMidtonesG" -> copy(colorGradingMidtonesG = value.coerceIn(-100f, 100f))
        "colorGradingMidtonesB" -> copy(colorGradingMidtonesB = value.coerceIn(-100f, 100f))
        "colorGradingHighlightsR" -> copy(colorGradingHighlightsR = value.coerceIn(-100f, 100f))
        "colorGradingHighlightsG" -> copy(colorGradingHighlightsG = value.coerceIn(-100f, 100f))
        "colorGradingHighlightsB" -> copy(colorGradingHighlightsB = value.coerceIn(-100f, 100f))
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
        "cropAspectRatio" -> copy(crop = if (value != 0f) CropData(aspectRatio = value) else null)
        "agxContrast" -> copy(agxContrast = value.coerceIn(0f, 1f))
        "agxPedestal" -> copy(agxPedestal = value.coerceIn(0f, 0.5f))
        "lensDistortion" -> copy(lensDistortion = value.coerceIn(-100f, 100f))
        "lensVignette" -> copy(lensVignette = value.coerceIn(-100f, 100f))
        "lensTca" -> copy(lensTca = value.coerceIn(-100f, 100f))
        "lensFocalLength" -> copy(lensFocalLength = value.coerceIn(1f, 1000f))
        "orientationSteps" -> copy(orientationSteps = Math.floorMod(value.toInt(), 4))
        "flipHorizontal" -> copy(flipHorizontal = value != 0f)
        "flipVertical" -> copy(flipVertical = value != 0f)
        // 2026 Color Science (AlcedoStudio integration)
        "colorScienceMode" -> copy(colorScienceMode = value.toInt().coerceIn(0, 3))
        "displayColorSpace" -> copy(displayColorSpace = value.toInt().coerceIn(0, 2))
        "eotf" -> copy(eotf = value.toInt().coerceIn(0, 2))
        "peakLuminanceNits" -> copy(peakLuminanceNits = value.coerceIn(100f, 10000f))
        // HDR export
        "hdrExportFormat" -> copy(hdrExportFormat = value.toInt().coerceIn(0, 2))
        "hdrPeakLuminance" -> copy(hdrPeakLuminance = value.coerceIn(100f, 10000f))
        "hdrMaxBoostStop" -> copy(hdrMaxBoostStop = value.coerceIn(1f, 8f))
        // Channel Mixer
        "channelMixerRedOutRed" -> copy(channelMixerRedOutRed = value.coerceIn(-200f, 200f))
        "channelMixerRedOutGreen" -> copy(channelMixerRedOutGreen = value.coerceIn(-200f, 200f))
        "channelMixerRedOutBlue" -> copy(channelMixerRedOutBlue = value.coerceIn(-200f, 200f))
        "channelMixerGreenOutRed" -> copy(channelMixerGreenOutRed = value.coerceIn(-200f, 200f))
        "channelMixerGreenOutGreen" -> copy(channelMixerGreenOutGreen = value.coerceIn(-200f, 200f))
        "channelMixerGreenOutBlue" -> copy(channelMixerGreenOutBlue = value.coerceIn(-200f, 200f))
        "channelMixerBlueOutRed" -> copy(channelMixerBlueOutRed = value.coerceIn(-200f, 200f))
        "channelMixerBlueOutGreen" -> copy(channelMixerBlueOutGreen = value.coerceIn(-200f, 200f))
        "channelMixerBlueOutBlue" -> copy(channelMixerBlueOutBlue = value.coerceIn(-200f, 200f))
        "channelMixerMonochrome" -> copy(channelMixerMonochrome = value != 0f)
        // Split Toning
        "splitToningHighlightHue" -> copy(splitToningHighlightHue = value.coerceIn(0f, 360f))
        "splitToningHighlightSaturation" -> copy(splitToningHighlightSaturation = value.coerceIn(0f, 100f))
        "splitToningShadowHue" -> copy(splitToningShadowHue = value.coerceIn(0f, 360f))
        "splitToningShadowSaturation" -> copy(splitToningShadowSaturation = value.coerceIn(0f, 100f))
        "splitToningBalance" -> copy(splitToningBalance = value.coerceIn(-100f, 100f))
        // Perspective Correction
        "perspectiveVertical" -> copy(perspectiveVertical = value.coerceIn(-100f, 100f))
        "perspectiveHorizontal" -> copy(perspectiveHorizontal = value.coerceIn(-100f, 100f))
        "perspectiveRotate" -> copy(perspectiveRotate = value.coerceIn(-45f, 45f))
        "perspectiveScale" -> copy(perspectiveScale = value.coerceIn(10f, 200f))
        "perspectiveAspect" -> copy(perspectiveAspect = value.coerceIn(-100f, 100f))
        // Local Tint
        "shadowsTintHue" -> copy(shadowsTintHue = value.coerceIn(0f, 360f))
        "shadowsTintSaturation" -> copy(shadowsTintSaturation = value.coerceIn(0f, 100f))
        "highlightsTintHue" -> copy(highlightsTintHue = value.coerceIn(0f, 360f))
        "highlightsTintSaturation" -> copy(highlightsTintSaturation = value.coerceIn(0f, 100f))
        // Edge Light
        "edgeLightAmount" -> copy(edgeLightAmount = value.coerceIn(0f, 100f))
        "edgeLightHue" -> copy(edgeLightHue = value.coerceIn(0f, 360f))
        "edgeLightSaturation" -> copy(edgeLightSaturation = value.coerceIn(0f, 1f))
        // Color Range Selector
        "colorRangeHue" -> copy(colorRangeHue = value.coerceIn(0f, 360f))
        "colorRangeWidth" -> copy(colorRangeWidth = value.coerceIn(1f, 180f))
        "colorRangeSatAdjust" -> copy(colorRangeSatAdjust = value.coerceIn(-100f, 100f))
        "colorRangeLumAdjust" -> copy(colorRangeLumAdjust = value.coerceIn(-100f, 100f))
        // Oklab Perceptual Color Space (ZenFilters pipeline)
        "oklabHueShift" -> copy(oklabHueShift = value.coerceIn(-1f, 1f))
        "oklabSaturation" -> copy(oklabSaturation = value.coerceIn(-1f, 1f))
        "oklabChroma" -> copy(oklabChroma = value.coerceIn(-1f, 1f))
        "oklabLightness" -> copy(oklabLightness = value.coerceIn(-1f, 1f))
        "oklabContrast" -> copy(oklabContrast = value.coerceIn(-1f, 1f))
        "oklabTextureAmount" -> copy(oklabTextureAmount = value.coerceIn(0f, 1f))
        else -> this
    }

    /**
     * Apply film simulation parameters.
     * Film base adjustments are only applied where user hasn't overridden (value == 0).
     * This is intentional: user edits always take priority over film defaults.
     * The model uses -100..100 scale for contrast etc., which is normalized to -1..1
     * in the processing pipeline via NormAdj.from().
     */
    fun withFilmSimulation(film: FilmSimulation): Adjustments {
        return this.copy(
            filmId = film.id,
            filmHighlightRollOff = film.highlightRollOff,
            filmShadowLift = film.shadowLift,
            filmDrCompression = film.drCompression,
            filmRedShift = film.redShift,
            filmGreenShift = film.greenShift,
            filmBlueShift = film.blueShift,
            filmSaturation = film.saturationModifier,
            filmContrast = film.contrastModifier,
            filmGrainAmount = film.grainAmount,
            filmGrainSize = film.grainSize,
            filmGrainRoughness = film.grainRoughness,
            filmCurvePoints = film.toneCurvePoints,
            // Merge film's base adjustments
            exposure = if (exposure == 0f) film.baseAdjustments.exposure else exposure,
            contrast = if (contrast == 0f) film.baseAdjustments.contrast else contrast,
            highlights = if (highlights == 0f) film.baseAdjustments.highlights else highlights,
            shadows = if (shadows == 0f) film.baseAdjustments.shadows else shadows,
            saturation = if (saturation == 0f) film.baseAdjustments.saturation else saturation,
            vibrance = if (vibrance == 0f) film.baseAdjustments.vibrance else vibrance,
            temperature = if (temperature == 0f) film.baseAdjustments.temperature else temperature,
            clarity = if (clarity == 0f) film.baseAdjustments.clarity else clarity,
            dehaze = if (dehaze == 0f) film.baseAdjustments.dehaze else dehaze,
            grainAmount = if (grainAmount == 0f) film.baseAdjustments.grainAmount else grainAmount,
            vignetteAmount = if (vignetteAmount == 0f) film.baseAdjustments.vignetteAmount else vignetteAmount,
            sharpness = if (sharpness == 0f) film.baseAdjustments.sharpness else sharpness,
        )
    }
}

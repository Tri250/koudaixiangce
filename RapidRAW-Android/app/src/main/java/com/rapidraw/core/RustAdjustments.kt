package com.rapidraw.core

import com.rapidraw.data.model.Adjustments
import com.rapidraw.data.model.Coord
import com.rapidraw.data.model.CropData
import com.rapidraw.data.model.HslChannel
import com.rapidraw.data.model.MaskContainer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Kotlin DTO that matches the Rust `Adjustments` struct exactly.
 *
 * The canonical UI model is [com.rapidraw.data.model.Adjustments]; this DTO is
 * serialized to JSON and passed through JNI to the Rust core.
 */
@Serializable
data class RustAdjustments(
    val exposure: Float = 0f,
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val whites: Float = 0f,
    val blacks: Float = 0f,

    val temperature: Float = 0f,
    val tint: Float = 0f,
    val saturation: Float = 0f,
    val vibrance: Float = 0f,
    val hsl: RustHslAdjustments = RustHslAdjustments(),
    val colorGrading: RustColorGrading = RustColorGrading(),
    val colorCalibration: RustColorCalibration = RustColorCalibration(),

    val sharpness: Float = 0f,
    val lumaNoiseReduction: Float = 0f,
    val colorNoiseReduction: Float = 0f,
    val clarity: Float = 0f,
    val dehaze: Float = 0f,
    val structure: Float = 0f,
    val centre: Float = 0f,
    val chromaticAberrationRedCyan: Float = 0f,
    val chromaticAberrationBlueYellow: Float = 0f,

    val vignetteAmount: Float = 0f,
    val vignetteMidpoint: Float = 0f,
    val vignetteRoundness: Float = 0f,
    val vignetteFeather: Float = 0f,
    val grainAmount: Float = 0f,
    val grainSize: Float = 0f,
    val grainRoughness: Float = 0f,
    val lutIntensity: Float = 0f,
    val glowAmount: Float = 0f,
    val halationAmount: Float = 0f,
    val flareAmount: Float = 0f,

    val rotation: Float = 0f,
    val orientationSteps: Int = 0,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val crop: RustCropData? = null,
    val transformDistortion: Float = 0f,
    val transformVertical: Float = 0f,
    val transformHorizontal: Float = 0f,
    val transformRotate: Float = 0f,
    val transformAspect: Float = 0f,
    val transformScale: Float = 0f,
    val transformXOffset: Float = 0f,
    val transformYOffset: Float = 0f,

    val lensMaker: String? = null,
    val lensModel: String? = null,
    val lensDistortionAmount: Float = 0f,
    val lensVignetteAmount: Float = 0f,
    val lensTcaAmount: Float = 0f,
    val lensDistortionEnabled: Boolean = false,
    val lensTcaEnabled: Boolean = false,
    val lensVignetteEnabled: Boolean = false,
    val lensDistortionParams: RustLensDistortionParams? = null,

    val curves: RustCurvesData = RustCurvesData(),
    val masks: List<RustMaskContainer> = emptyList(),
    val aiPatches: List<RustAiPatch> = emptyList(),

    val toneMapper: String = "agx",
    val showClipping: Boolean = false,
    val rating: Int = 0,
    val aspectRatio: Float? = null,
) {
    fun toJson(): String = Json.encodeToString(this)
}

@Serializable
data class RustCoord(val x: Float = 0f, val y: Float = 0f)

@Serializable
data class RustHueSatLum(
    val hue: Float = 0f,
    val saturation: Float = 0f,
    val luminance: Float = 0f,
)

@Serializable
data class RustHslAdjustments(
    val reds: RustHueSatLum = RustHueSatLum(),
    val oranges: RustHueSatLum = RustHueSatLum(),
    val yellows: RustHueSatLum = RustHueSatLum(),
    val greens: RustHueSatLum = RustHueSatLum(),
    val aquas: RustHueSatLum = RustHueSatLum(),
    val blues: RustHueSatLum = RustHueSatLum(),
    val purples: RustHueSatLum = RustHueSatLum(),
    val magentas: RustHueSatLum = RustHueSatLum(),
)

@Serializable
data class RustColorGrading(
    val shadows: RustHueSatLum = RustHueSatLum(),
    val midtones: RustHueSatLum = RustHueSatLum(),
    val highlights: RustHueSatLum = RustHueSatLum(),
    val blending: Float = 0f,
    val balance: Float = 0f,
)

@Serializable
data class RustColorCalibration(
    val shadowsTint: Float = 0f,
    val redHue: Float = 0f,
    val redSaturation: Float = 0f,
    val greenHue: Float = 0f,
    val greenSaturation: Float = 0f,
    val blueHue: Float = 0f,
    val blueSaturation: Float = 0f,
)

@Serializable
data class RustCurvesData(
    val luma: List<RustCoord> = listOf(RustCoord(0f, 0f), RustCoord(255f, 255f)),
    val red: List<RustCoord> = listOf(RustCoord(0f, 0f), RustCoord(255f, 255f)),
    val green: List<RustCoord> = listOf(RustCoord(0f, 0f), RustCoord(255f, 255f)),
    val blue: List<RustCoord> = listOf(RustCoord(0f, 0f), RustCoord(255f, 255f)),
)

@Serializable
data class RustCropData(
    val x: Double = 0.0,
    val y: Double = 0.0,
    val width: Double = 1.0,
    val height: Double = 1.0,
)

@Serializable
data class RustLensDistortionParams(
    val k1: Float = 0f,
    val k2: Float = 0f,
    val k3: Float = 0f,
    val model: Int = 0,
    val tcaVr: Float = 0f,
    val tcaVb: Float = 0f,
    val vigK1: Float = 0f,
    val vigK2: Float = 0f,
    val vigK3: Float = 0f,
)

@Serializable
data class RustSubMaskData(
    val id: String = "",
    val type: String = "brush",
    val visible: Boolean = true,
    val invert: Boolean = false,
    val opacity: Float = 1f,
    val mode: String = "normal",
    val parameters: String = "",
)

@Serializable
data class RustMaskContainer(
    val id: String = "",
    val name: String = "",
    val visible: Boolean = true,
    val invert: Boolean = false,
    val opacity: Float = 1f,
    val adjustments: RustAdjustments? = null,
    val subMasks: List<RustSubMaskData> = emptyList(),
)

@Serializable
data class RustAiPatch(
    val id: String = "",
    val maskBase64: String = "",
    val prompt: String = "",
    val strength: Float = 0f,
)

// ---------------------------------------------------------------------------
// Conversion from the canonical UI model
// ---------------------------------------------------------------------------

fun Adjustments.toRustAdjustments(): RustAdjustments = RustAdjustments(
    exposure = exposure,
    brightness = brightness,
    contrast = contrast,
    highlights = highlights,
    shadows = shadows,
    whites = whites,
    blacks = blacks,
    temperature = temperature,
    tint = tint,
    saturation = saturation,
    vibrance = vibrance,
    hsl = RustHslAdjustments(
        reds = hslReds.toRust(),
        oranges = hslOranges.toRust(),
        yellows = hslYellows.toRust(),
        greens = hslGreens.toRust(),
        aquas = hslAquas.toRust(),
        blues = hslBlues.toRust(),
        purples = hslPurples.toRust(),
        magentas = hslMagentas.toRust(),
    ),
    colorGrading = RustColorGrading(
        shadows = colorGrading.shadows.toRust(),
        midtones = colorGrading.midtones.toRust(),
        highlights = colorGrading.highlights.toRust(),
        blending = colorGrading.blending,
        balance = colorGrading.balance,
    ),
    colorCalibration = RustColorCalibration(
        shadowsTint = colorCalibration.shadowsTint,
        redHue = colorCalibration.redHue,
        redSaturation = colorCalibration.redSaturation,
        greenHue = colorCalibration.greenHue,
        greenSaturation = colorCalibration.greenSaturation,
        blueHue = colorCalibration.blueHue,
        blueSaturation = colorCalibration.blueSaturation,
    ),
    sharpness = sharpness,
    lumaNoiseReduction = lumaNoiseReduction,
    colorNoiseReduction = colorNoiseReduction,
    clarity = clarity,
    dehaze = dehaze,
    structure = structure,
    centre = centre,
    chromaticAberrationRedCyan = chromaticAberrationRedCyan,
    chromaticAberrationBlueYellow = chromaticAberrationBlueYellow,
    vignetteAmount = vignetteAmount,
    vignetteMidpoint = vignetteMidpoint,
    vignetteRoundness = vignetteRoundness,
    vignetteFeather = vignetteFeather,
    grainAmount = grainAmount,
    grainSize = grainSize,
    grainRoughness = grainRoughness,
    lutIntensity = lutIntensity,
    glowAmount = glowAmount,
    halationAmount = halationAmount,
    flareAmount = flareAmount,
    rotation = rotation,
    orientationSteps = orientationSteps,
    flipHorizontal = flipHorizontal,
    flipVertical = flipVertical,
    crop = crop?.toRust(),
    transformDistortion = transformDistortion,
    transformVertical = transformVertical,
    transformHorizontal = transformHorizontal,
    transformRotate = transformRotate,
    transformAspect = transformAspect,
    transformScale = transformScale,
    transformXOffset = transformXOffset,
    transformYOffset = transformYOffset,
    lensDistortionAmount = lensDistortion,
    lensVignetteAmount = lensVignette,
    lensTcaAmount = lensTca,
    toneMapper = toneMapper,
    showClipping = showClipping,
    curves = RustCurvesData(
        luma = lumaCurve.map { it.toRust() },
        red = redCurve.map { it.toRust() },
        green = greenCurve.map { it.toRust() },
        blue = blueCurve.map { it.toRust() },
    ),
    masks = masks.map { it.toRust() },
    aiPatches = aiPatches.map { it.toRust() },
)

private fun HslChannel.toRust(): RustHueSatLum = RustHueSatLum(hue, saturation, luminance)
private fun com.rapidraw.data.model.ColorGradingRegion.toRust(): RustHueSatLum =
    RustHueSatLum(hue, saturation, luminance)
private fun Coord.toRust(): RustCoord = RustCoord(x, y)
private fun CropData.toRust(): RustCropData = RustCropData(
    x = x.toDouble(),
    y = y.toDouble(),
    width = width.toDouble(),
    height = height.toDouble(),
)

private fun com.rapidraw.data.model.MaskContainer.toRust(): RustMaskContainer = RustMaskContainer(
    id = id,
    name = name,
    visible = visible,
    invert = invert,
    opacity = opacity,
    adjustments = adjustments?.toRustAdjustments(),
    subMasks = subMasks.map { sub ->
        RustSubMaskData(
            id = "",
            type = sub.type,
            visible = true,
            invert = false,
            opacity = 1f,
            mode = "normal",
            parameters = Json.encodeToString(sub),
        )
    },
)

private fun com.rapidraw.data.model.AiPatch.toRust(): RustAiPatch = RustAiPatch(
    id = id,
    maskBase64 = "",
    prompt = "",
    strength = 0f,
)

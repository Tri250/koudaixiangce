package com.alcedo.studio.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
data class Coord(val x: Float, val y: Float) : Parcelable

@Serializable
@Parcelize
data class HslChannel(
    val hue: Float = 0f,
    val saturation: Float = 0f,
    val luminance: Float = 0f,
) : Parcelable

@Serializable
@Parcelize
data class ColorGradingRegion(
    val hue: Float = 0f,
    val saturation: Float = 0f,
    val luminance: Float = 0f,
) : Parcelable

@Serializable
@Parcelize
data class ColorGrading(
    val shadows: ColorGradingRegion = ColorGradingRegion(),
    val midtones: ColorGradingRegion = ColorGradingRegion(),
    val highlights: ColorGradingRegion = ColorGradingRegion(),
    val blending: Float = 0f,
    val balance: Float = 0f,
) : Parcelable

@Serializable
@Parcelize
data class ColorCalibration(
    val shadowsTint: Float = 0f,
    val redHue: Float = 0f,
    val redSaturation: Float = 0f,
    val greenHue: Float = 0f,
    val greenSaturation: Float = 0f,
    val blueHue: Float = 0f,
    val blueSaturation: Float = 0f,
) : Parcelable

@Serializable
@Parcelize
data class CropData(
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 1f,
    val height: Float = 1f,
    val aspectRatio: Float? = null,
    val rotation: Float = 0f,
) : Parcelable

@Serializable
@Parcelize
data class SubMaskData(
    val type: String = "brush",
    val points: List<Coord> = emptyList(),
    val radius: Float = 50f,
    val feather: Float = 0f,
) : Parcelable

@Serializable
@Parcelize
data class MaskContainer(
    val id: String = "",
    val name: String = "",
    val visible: Boolean = true,
    val invert: Boolean = false,
    val opacity: Float = 1f,
    val adjustments: Adjustments? = null,
    val subMasks: List<SubMaskData> = emptyList(),
) : Parcelable

@Serializable
@Parcelize
data class AiPatch(
    val id: String = "",
    val maskPath: List<Coord> = emptyList(),
    val boundingBox: Coord = Coord(0f, 0f),
    val boundingBoxSize: Coord = Coord(0f, 0f),
    val applied: Boolean = false,
) : Parcelable

@Serializable
@Parcelize
data class Adjustments(
    val exposure: Float = 0f,
    val toneLevel: Float = 0f,
    val filmIntensity: Float = 1f,
    val greenMagenta: Float = 0f,
    val softGlow: Float = 0f,

    val filmId: String = "",
    val filmHighlightRollOff: Float = 0f,
    val filmShadowLift: Float = 0f,
    val filmDrCompression: Float = 0f,
    val filmRedShift: Float = 0f,
    val filmGreenShift: Float = 0f,
    val filmBlueShift: Float = 0f,
    val filmSaturation: Float = 0f,
    val filmContrast: Float = 0f,
    val filmGrainAmount: Float = 0f,
    val filmGrainSize: Float = 0f,
    val filmGrainRoughness: Float = 0f,
    val filmCurvePoints: List<Pair<Float, Float>> = listOf(
        0f to 0f, 51f to 51f, 102f to 102f,
        153f to 153f, 204f to 204f, 255f to 255f
    ),

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
    val lightness: Float = 0f,

    val hslReds: HslChannel = HslChannel(),
    val hslOranges: HslChannel = HslChannel(),
    val hslYellows: HslChannel = HslChannel(),
    val hslGreens: HslChannel = HslChannel(),
    val hslAquas: HslChannel = HslChannel(),
    val hslBlues: HslChannel = HslChannel(),
    val hslPurples: HslChannel = HslChannel(),
    val hslMagentas: HslChannel = HslChannel(),

    val lumaCurve: List<Coord> = listOf(Coord(0f, 0f), Coord(255f, 255f)),
    val redCurve: List<Coord> = listOf(Coord(0f, 0f), Coord(255f, 255f)),
    val greenCurve: List<Coord> = listOf(Coord(0f, 0f), Coord(255f, 255f)),
    val blueCurve: List<Coord> = listOf(Coord(0f, 0f), Coord(255f, 255f)),

    val colorGrading: ColorGrading = ColorGrading(),
    val colorCalibration: ColorCalibration = ColorCalibration(),

    val sharpness: Float = 0f,
    val clarity: Float = 0f,
    val structure: Float = 0f,
    val centre: Float = 0f,

    val dehaze: Float = 0f,
    val vignetteAmount: Float = 0f,
    val vignetteMidpoint: Float = 50f,
    val vignetteRoundness: Float = 0f,
    val vignetteFeather: Float = 50f,
    val grainAmount: Float = 0f,
    val grainSize: Float = 0f,
    val grainRoughness: Float = 0f,
    val chromaticAberrationRedCyan: Float = 0f,
    val chromaticAberrationBlueYellow: Float = 0f,

    val glowAmount: Float = 0f,
    val halationAmount: Float = 0f,
    val flareAmount: Float = 0f,

    val lumaNoiseReduction: Float = 0f,
    val colorNoiseReduction: Float = 0f,

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

    val lensDistortion: Float = 0f,
    val lensVignette: Float = 0f,
    val lensTca: Float = 0f,
    val lensFocalLength: Float = 50f,

    val lutIntensity: Float = 100f,

    val colorScienceMode: Int = 0,
    val toneMapper: String = "agx",
    val agxContrast: Float = 0f,
    val agxPedestal: Float = 0f,
    val peakLuminanceNits: Float = 1000f,

    val masks: List<MaskContainer> = emptyList(),
    val aiPatches: List<AiPatch> = emptyList(),

    val showClipping: Boolean = false,
    val rating: Int = 0,
    val colorLabel: String? = null,
) : Parcelable {
    companion object {
        val Default = Adjustments()
    }

    fun isDefault(): Boolean = this == Default
}

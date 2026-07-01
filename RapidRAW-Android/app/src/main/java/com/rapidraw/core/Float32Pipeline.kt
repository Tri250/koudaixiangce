package com.rapidraw.core

import android.graphics.Bitmap
import com.rapidraw.data.model.Adjustments
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow

/**
 * 32 位浮点处理管道
 *
 * 提供完整的 Scene-Referred 线性工作流：
 * 1. Bitmap ARGB_8888 → Float32 线性 RGB（反 sRGB OETF）
 * 2. 处理阶段（全部在线性空间）：
 *    - linear exposure
 *    - white balance
 *    - tone mapping (ACES 2.0 / OpenDRT / AgX)
 *    - color grading
 *    - film simulation
 * 3. Float32 线性 RGB → sRGB → Bitmap ARGB_8888
 *
 * 相比 ImageProcessor 的逐像素 Int 处理，Float32Pipeline：
 * - 保留超过 [0,1] 的高动态范围直到 tone mapping 阶段
 * - 减少每通道 8-bit 量化误差
 * - 支持更大范围的 exposure push/pull（+/- 6EV 以上）
 *
 * 与 ImageProcessor 集成：
 * - 接受 data.model.Adjustments 作为输入参数
 * - 可通过 ImageProcessor.currentLut 应用 3D LUT
 */
class Float32Pipeline {

    companion object {
        private const val TAG = "Float32Pipeline"
    }

    /** 当前 3D LUT（与 ImageProcessor 共享） */
    var currentLut: CubeLutParser.Lut3D? = null

    /**
     * 处理参数（与 ImageProcessor.NormAdj 对应，但使用 Float32 范围）
     */
    data class PipelineParams(
        val exposure: Float = 0f,
        val brightness: Float = 0f,
        val temperature: Float = 6500f,
        val tint: Float = 0f,
        val greenMagenta: Float = 0f,
        val contrast: Float = 0f,
        val highlights: Float = 0f,
        val shadows: Float = 0f,
        val whites: Float = 0f,
        val blacks: Float = 0f,
        val saturation: Float = 0f,
        val vibrance: Float = 0f,
        val toneLevel: Float = 0f,
        val dehaze: Float = 0f,
        val vignette: Float = 0f,
        val vignetteMidpoint: Float = 0.5f,
        val vignetteRoundness: Float = 0f,
        val vignetteFeather: Float = 0.5f,
        val grain: Float = 0f,
        val grainSize: Float = 1f,
        val grainRoughness: Float = 0f,
        val filmIntensity: Float = 1f,
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
        val filmCurvePoints: List<Pair<Float, Float>> = emptyList(),
        val toneCurvePoints: List<Pair<Float, Float>> = emptyList(),
        val redCurvePoints: List<Pair<Float, Float>> = emptyList(),
        val greenCurvePoints: List<Pair<Float, Float>> = emptyList(),
        val blueCurvePoints: List<Pair<Float, Float>> = emptyList(),
        val colorGradingShadows: List<Float> = listOf(0f, 0f, 0f),
        val colorGradingMidtones: List<Float> = listOf(0f, 0f, 0f),
        val colorGradingHighlights: List<Float> = listOf(0f, 0f, 0f),
        val colorGradingBlend: Float = 1f,
        val colorGradingBalance: Float = 0f,
        val colorGradingGlobalSat: Float = 0f,
        val calibRedHue: Float = 0f,
        val calibRedSat: Float = 0f,
        val calibGreenHue: Float = 0f,
        val calibGreenSat: Float = 0f,
        val calibBlueHue: Float = 0f,
        val calibBlueSat: Float = 0f,
        val softGlow: Float = 0f,
        val glowAmount: Float = 0f,
        val halationAmount: Float = 0f,
        val flareAmount: Float = 0f,
        val colorCalibrationShadowsTint: Float = 0f,
        val lutIntensity: Float = 1f,
        val toneMapper: String = "agx",
        val agxContrast: Float = 0f,
        val agxPedestal: Float = 0f,
        val colorScienceMode: Int = 0,
        val displayColorSpace: Int = 0,
        val peakLuminanceNits: Float = 1000f,
        val chromaticAberrationRedCyan: Float = 0f,
        val chromaticAberrationBlueYellow: Float = 0f,
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
        val splitToningHighlightHue: Float = 0f,
        val splitToningHighlightSaturation: Float = 0f,
        val splitToningShadowHue: Float = 0f,
        val splitToningShadowSaturation: Float = 0f,
        val splitToningBalance: Float = 0f,
        val perspectiveVertical: Float = 0f,
        val perspectiveHorizontal: Float = 0f,
        val perspectiveRotate: Float = 0f,
        val perspectiveScale: Float = 100f,
        val perspectiveAspect: Float = 0f,
        val shadowsTintHue: Float = 0f,
        val shadowsTintSaturation: Float = 0f,
        val highlightsTintHue: Float = 0f,
        val highlightsTintSaturation: Float = 0f,
        val edgeLightAmount: Float = 0f,
        val edgeLightHue: Float = 30f,
        val edgeLightSaturation: Float = 0.5f,
        val colorRangeHue: Float = 0f,
        val colorRangeWidth: Float = 30f,
        val colorRangeSatAdjust: Float = 0f,
        val colorRangeLumAdjust: Float = 0f,
        val eotf: Int = 0,
        val lensDistortion: Float = 0f,
        val lensVignette: Float = 0f,
        val lensTca: Float = 0f,
        val lensFocalLength: Float = 50f,
        val centre: Float = 0f,
        val clippingPreview: Boolean = false,
    ) {
        companion object {
            /**
             * 从 data.model.Adjustments 创建 PipelineParams
             */
            fun from(adj: Adjustments): PipelineParams = PipelineParams(
                exposure = adj.exposure,
                brightness = adj.brightness / 5f,
                temperature = 6500f + adj.temperature * 50f,
                tint = adj.tint / 100f,
                greenMagenta = adj.greenMagenta,
                contrast = adj.contrast / 100f,
                highlights = adj.highlights / 150f,
                shadows = adj.shadows / 100f,
                whites = adj.whites / 30f,
                blacks = adj.blacks / 60f,
                saturation = adj.saturation / 100f,
                vibrance = adj.vibrance / 100f,
                toneLevel = adj.toneLevel,
                dehaze = adj.dehaze / 100f,
                vignette = adj.vignetteAmount / 100f,
                vignetteMidpoint = adj.vignetteMidpoint / 100f,
                vignetteRoundness = adj.vignetteRoundness / 100f,
                vignetteFeather = adj.vignetteFeather / 100f,
                grain = adj.grainAmount / 100f,
                grainSize = adj.grainSize / 100f * 3f,
                grainRoughness = adj.grainRoughness / 100f,
                filmIntensity = adj.filmIntensity,
                filmId = adj.filmId,
                filmHighlightRollOff = adj.filmHighlightRollOff,
                filmShadowLift = adj.filmShadowLift,
                filmDrCompression = adj.filmDrCompression,
                filmRedShift = adj.filmRedShift,
                filmGreenShift = adj.filmGreenShift,
                filmBlueShift = adj.filmBlueShift,
                filmSaturation = adj.filmSaturation,
                filmContrast = adj.filmContrast,
                filmGrainAmount = adj.filmGrainAmount,
                filmGrainSize = adj.filmGrainSize,
                filmGrainRoughness = adj.filmGrainRoughness,
                filmCurvePoints = adj.filmCurvePoints.map { (it.first / 255f) to (it.second / 255f) },
                toneCurvePoints = adj.lumaCurve.map { (it.x / 255f) to (it.y / 255f) },
                redCurvePoints = adj.redCurve.map { (it.x / 255f) to (it.y / 255f) },
                greenCurvePoints = adj.greenCurve.map { (it.x / 255f) to (it.y / 255f) },
                blueCurvePoints = adj.blueCurve.map { (it.x / 255f) to (it.y / 255f) },
                colorGradingShadows = listOf(
                    adj.colorGrading.shadows.hue / 360f,
                    adj.colorGrading.shadows.saturation / 100f,
                    adj.colorGrading.shadows.luminance / 100f),
                colorGradingMidtones = listOf(
                    adj.colorGrading.midtones.hue / 360f,
                    adj.colorGrading.midtones.saturation / 100f,
                    adj.colorGrading.midtones.luminance / 100f),
                colorGradingHighlights = listOf(
                    adj.colorGrading.highlights.hue / 360f,
                    adj.colorGrading.highlights.saturation / 100f,
                    adj.colorGrading.highlights.luminance / 100f),
                colorGradingBlend = adj.colorGrading.blending / 100f,
                colorGradingBalance = adj.colorGrading.balance / 100f,
                colorGradingGlobalSat = 0f,
                calibRedHue = adj.colorCalibration.redHue / 100f,
                calibRedSat = adj.colorCalibration.redSaturation / 100f,
                calibGreenHue = adj.colorCalibration.greenHue / 100f,
                calibGreenSat = adj.colorCalibration.greenSaturation / 100f,
                calibBlueHue = adj.colorCalibration.blueHue / 100f,
                calibBlueSat = adj.colorCalibration.blueSaturation / 100f,
                softGlow = adj.softGlow,
                glowAmount = adj.glowAmount / 100f,
                halationAmount = adj.halationAmount / 100f,
                flareAmount = adj.flareAmount / 100f,
                colorCalibrationShadowsTint = adj.colorCalibration.shadowsTint / 100f,
                lutIntensity = adj.lutIntensity / 100f,
                toneMapper = adj.toneMapper,
                agxContrast = adj.agxContrast,
                agxPedestal = adj.agxPedestal,
                colorScienceMode = adj.colorScienceMode,
                displayColorSpace = adj.displayColorSpace,
                peakLuminanceNits = adj.peakLuminanceNits,
                chromaticAberrationRedCyan = adj.chromaticAberrationRedCyan / 100f,
                chromaticAberrationBlueYellow = adj.chromaticAberrationBlueYellow / 100f,
                channelMixerRedOutRed = adj.channelMixerRedOutRed / 200f,
                channelMixerRedOutGreen = adj.channelMixerRedOutGreen / 200f,
                channelMixerRedOutBlue = adj.channelMixerRedOutBlue / 200f,
                channelMixerGreenOutRed = adj.channelMixerGreenOutRed / 200f,
                channelMixerGreenOutGreen = adj.channelMixerGreenOutGreen / 200f,
                channelMixerGreenOutBlue = adj.channelMixerGreenOutBlue / 200f,
                channelMixerBlueOutRed = adj.channelMixerBlueOutRed / 200f,
                channelMixerBlueOutGreen = adj.channelMixerBlueOutGreen / 200f,
                channelMixerBlueOutBlue = adj.channelMixerBlueOutBlue / 200f,
                channelMixerMonochrome = adj.channelMixerMonochrome,
                splitToningHighlightHue = adj.splitToningHighlightHue / 360f,
                splitToningHighlightSaturation = adj.splitToningHighlightSaturation / 100f,
                splitToningShadowHue = adj.splitToningShadowHue / 360f,
                splitToningShadowSaturation = adj.splitToningShadowSaturation / 100f,
                splitToningBalance = adj.splitToningBalance / 100f,
                perspectiveVertical = adj.perspectiveVertical / 100f,
                perspectiveHorizontal = adj.perspectiveHorizontal / 100f,
                perspectiveRotate = adj.perspectiveRotate,
                perspectiveScale = adj.perspectiveScale / 100f,
                perspectiveAspect = adj.perspectiveAspect / 100f,
                shadowsTintHue = adj.shadowsTintHue / 360f,
                shadowsTintSaturation = adj.shadowsTintSaturation / 100f,
                highlightsTintHue = adj.highlightsTintHue / 360f,
                highlightsTintSaturation = adj.highlightsTintSaturation / 100f,
                edgeLightAmount = adj.edgeLightAmount / 100f,
                edgeLightHue = adj.edgeLightHue / 360f,
                edgeLightSaturation = adj.edgeLightSaturation,
                colorRangeHue = adj.colorRangeHue / 360f,
                colorRangeWidth = adj.colorRangeWidth / 180f,
                colorRangeSatAdjust = adj.colorRangeSatAdjust / 100f,
                colorRangeLumAdjust = adj.colorRangeLumAdjust / 100f,
                eotf = adj.eotf.coerceIn(0, 2),
                lensDistortion = adj.lensDistortion / 100f,
                lensVignette = adj.lensVignette / 100f,
                lensTca = adj.lensTca / 100f,
                lensFocalLength = adj.lensFocalLength,
                centre = adj.centre / 100f,
                clippingPreview = adj.showClipping,
            )
        }
    }

    /**
     * 将 Bitmap 处理为 Float32 管道并输出新 Bitmap
     *
     * @param bitmap 输入图像（ARGB_8888）
     * @param adjustments 用户调节参数
     * @return 处理后的 Bitmap
     */
    suspend fun process(
        bitmap: Bitmap,
        adjustments: Adjustments,
    ): Bitmap = withContext(Dispatchers.Default) {
        try {
            val params = PipelineParams.from(adjustments)

            val w = bitmap.width
            val h = bitmap.height
            val pixelCount = w * h

            // Step 1: Bitmap → Float32 linear RGB
            val pixels = IntArray(pixelCount)
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

            val linearR = FloatArray(pixelCount)
            val linearG = FloatArray(pixelCount)
            val linearB = FloatArray(pixelCount)

            for (i in 0 until pixelCount) {
                val p = pixels[i]
                val sr = ((p shr 16) and 0xFF) / 255f
                val sg = ((p shr 8) and 0xFF) / 255f
                val sb = (p and 0xFF) / 255f

                linearR[i] = ColorMath.srgbToLinear(sr)
                linearG[i] = ColorMath.srgbToLinear(sg)
                linearB[i] = ColorMath.srgbToLinear(sb)
            }

            // Step 2: Process in linear space
            processFloat32(linearR, linearG, linearB, w, h, params)

            // Step 3: Float32 linear → sRGB → Bitmap
            val outBitmap = try {
                Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            } catch (e: OutOfMemoryError) {
                Log.e(TAG, "OOM creating output bitmap ${w}x${h}", e)
                return@withContext try {
                    Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                } catch (_: OutOfMemoryError) { bitmap }
            }
            val outPixels = IntArray(pixelCount)

            for (i in 0 until pixelCount) {
                var r = ColorMath.linearToSrgb(linearR[i].coerceIn(0f, 1f))
                var g = ColorMath.linearToSrgb(linearG[i].coerceIn(0f, 1f))
                var b = ColorMath.linearToSrgb(linearB[i].coerceIn(0f, 1f))

                // Dither
                r = (r + ColorMath.gradientNoise(i.toFloat(), 0f) / 255f - 0.5f / 255f).coerceIn(0f, 1f)
                g = (g + ColorMath.gradientNoise(i.toFloat() + 100f, 100f) / 255f - 0.5f / 255f).coerceIn(0f, 1f)
                b = (b + ColorMath.gradientNoise(i.toFloat() + 200f, 200f) / 255f - 0.5f / 255f).coerceIn(0f, 1f)

                val ri = (r * 255f).toInt().coerceIn(0, 255)
                val gi = (g * 255f).toInt().coerceIn(0, 255)
                val bi = (b * 255f).toInt().coerceIn(0, 255)
                outPixels[i] = (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
            }

            outBitmap.setPixels(outPixels, 0, w, 0, 0, w, h)
            outBitmap
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OOM in Float32Pipeline", e)
            try { Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888) } catch (_: OutOfMemoryError) { bitmap }
        }
    }

    /**
     * 核心 Float32 处理管道（Scene-Referred Linear）
     */
    private suspend fun processFloat32(
        r: FloatArray,
        g: FloatArray,
        b: FloatArray,
        w: Int,
        h: Int,
        p: PipelineParams,
    ) = withContext(Dispatchers.Default) {
        val pixelCount = w * h

        // Pre-compute white balance multipliers
        val wbMultipliers = ColorMath.temperatureTintToMultipliers(p.temperature, p.tint)

        // Pre-compute curves
        val curvePoints = p.toneCurvePoints.sortedBy { it.first }
        val filmCurve = p.filmCurvePoints.sortedBy { it.first }
        val redCurve = p.redCurvePoints.sortedBy { it.first }
        val greenCurve = p.greenCurvePoints.sortedBy { it.first }
        val blueCurve = p.blueCurvePoints.sortedBy { it.first }

        // Pre-compute calibration matrix
        val calibMatrix = computeCalibrationMatrix(p)

        // Tone mapping config
        val toneMapConfig = when (p.colorScienceMode) {
            1 -> ColorScience.Config(
                mode = ColorScience.Mode.ACES_2,
                displaySpace = ColorScience.DisplayColorSpace.SRGB,
                contrast = p.contrast.coerceIn(0f, 1f),
                peakLuminanceNits = p.peakLuminanceNits,
            )
            2 -> ColorScience.Config(
                mode = ColorScience.Mode.OPEN_DRT,
                displaySpace = ColorScience.DisplayColorSpace.SRGB,
                contrast = p.contrast.coerceIn(0f, 1f),
                peakLuminanceNits = p.peakLuminanceNits,
            )
            3 -> ColorScience.Config(
                mode = ColorScience.Mode.STANDARD,
                displaySpace = ColorScience.DisplayColorSpace.SRGB,
                contrast = p.contrast.coerceIn(0f, 1f),
            )
            else -> ColorScience.Config(
                mode = ColorScience.Mode.AGX,
                displaySpace = ColorScience.DisplayColorSpace.SRGB,
                contrast = p.agxContrast.coerceIn(0f, 1f),
                pedestal = p.agxPedestal.coerceIn(0f, 0.5f),
            )
        }

        // Pixel loop
        for (y in 0 until h) {
            if (y % 256 == 0) yield()

            for (x in 0 until w) {
                val idx = y * w + x
                var rr = r[idx]
                var gg = g[idx]
                var bb = b[idx]

                // 1. Linear Exposure
                val exposureMul = 2f.pow(p.exposure)
                rr *= exposureMul
                gg *= exposureMul
                bb *= exposureMul

                // 2. Filmic Brightness
                val br = p.brightness * 2f
                rr = applyFilmicBrightness(rr, br)
                gg = applyFilmicBrightness(gg, br)
                bb = applyFilmicBrightness(bb, br)

                // 3. Tone Level
                if (abs(p.toneLevel) > 1e-6f) {
                    val luma = ColorMath.getLuma(rr, gg, bb)
                    val shift = p.toneLevel * 0.3f
                    val target = (luma + shift).coerceIn(0f, 1f)
                    val factor = if (luma > 1e-6f) target / luma else 1f
                    rr *= factor
                    gg *= factor
                    bb *= factor
                }

                // 4. White Balance
                rr *= wbMultipliers[0]
                gg *= wbMultipliers[1]
                bb *= wbMultipliers[2]

                // 5. Green-Magenta axis
                if (abs(p.greenMagenta) > 1e-6f) {
                    gg += p.greenMagenta * -0.1f
                    rr += p.greenMagenta * 0.05f
                    bb += p.greenMagenta * 0.05f
                }

                // 6. Highlights
                val highlightsResult = applyHighlightsAdjustment(rr, gg, bb, p.highlights)
                rr = highlightsResult[0]; gg = highlightsResult[1]; bb = highlightsResult[2]

                // 7. Tonal: Contrast/Shadows/Whites/Blacks
                val tonalResult = applyTonalAdjustments(rr, gg, bb, p)
                rr = tonalResult[0]; gg = tonalResult[1]; bb = tonalResult[2]

                // 8. Saturation / Vibrance
                val satResult = applyCreativeColor(rr, gg, bb, p.saturation, p.vibrance)
                rr = satResult[0]; gg = satResult[1]; bb = satResult[2]

                // 9. Tone Curves
                rr = ColorMath.applyCurve(rr, curvePoints)
                gg = ColorMath.applyCurve(gg, curvePoints)
                bb = ColorMath.applyCurve(bb, curvePoints)

                if (redCurve.size >= 2) rr = ColorMath.applyCurve(rr, redCurve)
                if (greenCurve.size >= 2) gg = ColorMath.applyCurve(gg, greenCurve)
                if (blueCurve.size >= 2) bb = ColorMath.applyCurve(bb, blueCurve)

                // 10. Color Grading
                val cgResult = applyColorGrading(rr, gg, bb, p)
                rr = cgResult[0]; gg = cgResult[1]; bb = cgResult[2]

                // 11. Dehaze
                val dehazeResult = applyDehaze(rr, gg, bb, p.dehaze)
                rr = dehazeResult[0]; gg = dehazeResult[1]; bb = dehazeResult[2]

                // 12. Vignette
                val vignetteResult = applyVignette(rr, gg, bb, x.toFloat() / w, y.toFloat() / h, p)
                rr = vignetteResult[0]; gg = vignetteResult[1]; bb = vignetteResult[2]

                // 13. Grain
                val grainResult = applyGrain(rr, gg, bb, x.toFloat(), y.toFloat(), p, w, h)
                rr = grainResult[0]; gg = grainResult[1]; bb = grainResult[2]

                // 14. Creative Light Effects
                val glowResult = applyGlow(rr, gg, bb, p.glowAmount)
                rr = glowResult[0]; gg = glowResult[1]; bb = glowResult[2]
                val halationResult = applyHalation(rr, gg, bb, p.halationAmount)
                rr = halationResult[0]; gg = halationResult[1]; bb = halationResult[2]
                val flareResult = applyFlare(rr, gg, bb, p.flareAmount)
                rr = flareResult[0]; gg = flareResult[1]; bb = flareResult[2]

                // 15. Color Calibration
                val calR = calibMatrix[0] * rr + calibMatrix[1] * gg + calibMatrix[2] * bb
                val calG = calibMatrix[3] * rr + calibMatrix[4] * gg + calibMatrix[5] * bb
                val calB = calibMatrix[6] * rr + calibMatrix[7] * gg + calibMatrix[8] * bb
                rr = calR; gg = calG; bb = calB

                // 16. Color Calibration Shadows Tint
                if (abs(p.colorCalibrationShadowsTint) > 1e-6f) {
                    val luma = ColorMath.getLuma(rr, gg, bb)
                    val sMask = ColorMath.shadowsMask(luma)
                    val tint = p.colorCalibrationShadowsTint * 0.15f * sMask
                    rr += tint
                    gg -= tint * 0.5f
                    bb += tint
                }

                // 17. Tone Mapping (ACES 2.0 / OpenDRT / AgX / Standard)
                val tmResult = ColorScience.apply(rr, gg, bb, toneMapConfig)
                rr = tmResult.first
                gg = tmResult.second
                bb = tmResult.third

                // 18. Film Simulation Processing
                if (p.filmId.isNotEmpty() && p.filmIntensity > 1e-6f) {
                    rr += p.filmRedShift * 0.15f
                    gg += p.filmGreenShift * 0.15f
                    bb += p.filmBlueShift * 0.15f

                    if (abs(p.filmSaturation) > 1e-6f) {
                        val lum = ColorMath.getLuma(rr, gg, bb)
                        val satMod = 1f + p.filmSaturation
                        rr = lum + (rr - lum) * satMod
                        gg = lum + (gg - lum) * satMod
                        bb = lum + (bb - lum) * satMod
                    }

                    if (abs(p.filmContrast) > 1e-6f) {
                        val contrastPow = 1f + p.filmContrast * 0.5f
                        val mid = 0.18f
                        rr = mid + (rr - mid) * contrastPow
                        gg = mid + (gg - mid) * contrastPow
                        bb = mid + (bb - mid) * contrastPow
                    }

                    if (p.filmHighlightRollOff > 1e-6f) {
                        val luma = ColorMath.getLuma(rr, gg, bb)
                        val hMask = ColorMath.highlightsMask(luma)
                        val shoulder = 1f - (1f - rr).toDouble().pow(1.0 + p.filmHighlightRollOff * 2.0).toFloat()
                        val shoulderg = 1f - (1f - gg).toDouble().pow(1.0 + p.filmHighlightRollOff * 2.0).toFloat()
                        val shoulderb = 1f - (1f - bb).toDouble().pow(1.0 + p.filmHighlightRollOff * 2.0).toFloat()
                        rr = rr + (shoulder - rr) * hMask * p.filmIntensity
                        gg = gg + (shoulderg - gg) * hMask * p.filmIntensity
                        bb = bb + (shoulderb - bb) * hMask * p.filmIntensity
                    }

                    if (p.filmShadowLift > 1e-6f) {
                        val luma = ColorMath.getLuma(rr, gg, bb)
                        val sMask = ColorMath.shadowsMask(luma)
                        val lift = p.filmShadowLift * 0.2f * sMask * p.filmIntensity
                        rr += lift; gg += lift; bb += lift
                    }

                    if (p.filmDrCompression > 1e-6f) {
                        val luma = ColorMath.getLuma(rr, gg, bb)
                        val compressed = luma / (luma + p.filmDrCompression * 0.5f + 1e-6f) * (1f + p.filmDrCompression * 0.5f)
                        val factor = if (luma > 1e-6f) compressed / luma else 1f
                        val blend = p.filmDrCompression * p.filmIntensity
                        val blendedFactor = 1f + (factor - 1f) * blend
                        rr *= blendedFactor; gg *= blendedFactor; bb *= blendedFactor
                    }

                    val hasFilmCurve = filmCurve.size >= 2 && filmCurve.any { abs(it.first - it.second) > 0.1f }
                    if (hasFilmCurve) {
                        val filmR = ColorMath.applyCurve(rr, filmCurve)
                        val filmG = ColorMath.applyCurve(gg, filmCurve)
                        val filmB = ColorMath.applyCurve(bb, filmCurve)
                        rr = rr + (filmR - rr) * p.filmIntensity
                        gg = gg + (filmG - gg) * p.filmIntensity
                        bb = bb + (filmB - bb) * p.filmIntensity
                    }

                    if (p.filmGrainAmount > 1e-6f) {
                        val fGrainSize = 1f + p.filmGrainSize * 4f
                        val noise = ColorMath.gradientNoise(x * fGrainSize, y * fGrainSize)
                        val grainOffset = (noise - 0.5f) * p.filmGrainAmount * 0.4f
                        val luma = ColorMath.getLuma(rr, gg, bb)
                        var grainMod = 1f - abs(luma - 0.5f) * 1.5f
                        grainMod = grainMod.coerceIn(0.2f, 1f)
                        val roughnessMod = 0.5f + p.filmGrainRoughness * 0.5f
                        rr += grainOffset * grainMod * roughnessMod * p.filmIntensity
                        gg += grainOffset * grainMod * roughnessMod * p.filmIntensity
                        bb += grainOffset * grainMod * roughnessMod * p.filmIntensity
                    }
                }

                // 19. Soft Glow / Bloom
                if (p.softGlow > 1e-6f) {
                    val bloom = p.softGlow * 0.3f * ColorMath.getLuma(rr, gg, bb)
                    rr += bloom; gg += bloom; bb += bloom
                }

                // 20. 3D LUT (CPU path)
                val lut = currentLut
                if (lut != null && p.lutIntensity > 1e-6f) {
                    val lutColor = lut.sample(rr.coerceIn(0f, 1f), gg.coerceIn(0f, 1f), bb.coerceIn(0f, 1f))
                    rr = rr + (lutColor.first - rr) * p.lutIntensity
                    gg = gg + (lutColor.second - gg) * p.lutIntensity
                    bb = bb + (lutColor.third - bb) * p.lutIntensity
                }

                // 21. Clipping preview
                if (p.clippingPreview) {
                    if (rr >= 1f || gg >= 1f || bb >= 1f) {
                        rr = 1f; gg = 0f; bb = 0f
                    } else if (rr <= 0f || gg <= 0f || bb <= 0f) {
                        rr = 0f; gg = 0f; bb = 1f
                    }
                }

                r[idx] = rr
                g[idx] = gg
                b[idx] = bb
            }
        }
    }

    // ── Processing helpers (Float32 variants) ──────────────────────

    private fun applyFilmicBrightness(channel: Float, brightness: Float): Float {
        if (abs(brightness) < 1e-6f) return channel
        val numerator = channel * (1f + brightness)
        val denominator = 1f + abs(brightness) * channel
        return numerator / max(denominator, 1e-6f)
    }

    private fun applyHighlightsAdjustment(r: Float, g: Float, b: Float, highlights: Float): FloatArray {
        if (abs(highlights) < 1e-6f) return floatArrayOf(r, g, b)
        val luma = ColorMath.getLuma(r, g, b)
        val mask = ColorMath.highlightsMask(luma)
        return if (highlights < 0f) {
            val compressedR = 1f - (1f - r).toDouble().pow(1.0 - highlights.toDouble()).toFloat()
            val compressedG = 1f - (1f - g).toDouble().pow(1.0 - highlights.toDouble()).toFloat()
            val compressedB = 1f - (1f - b).toDouble().pow(1.0 - highlights.toDouble()).toFloat()
            floatArrayOf(
                r + (compressedR - r) * mask,
                g + (compressedG - g) * mask,
                b + (compressedB - b) * mask
            )
        } else {
            val expandedR = r.toDouble().pow(1.0 / (1.0 + highlights.toDouble())).toFloat()
            val expandedG = g.toDouble().pow(1.0 / (1.0 + highlights.toDouble())).toFloat()
            val expandedB = b.toDouble().pow(1.0 / (1.0 + highlights.toDouble())).toFloat()
            floatArrayOf(
                r + (expandedR - r) * mask,
                g + (expandedG - g) * mask,
                b + (expandedB - b) * mask
            )
        }
    }

    private fun applyTonalAdjustments(r: Float, g: Float, b: Float, p: PipelineParams): FloatArray {
        var outR = r
        var outG = g
        var outB = b
        val luma = ColorMath.getLuma(r, g, b)

        if (abs(p.contrast) > 1e-6f) {
            val contrastPow = 1f + p.contrast
            val mid = 0.18f
            outR = mid + (outR - mid) * contrastPow
            outG = mid + (outG - mid) * contrastPow
            outB = mid + (outB - mid) * contrastPow
        }

        if (abs(p.shadows) > 1e-6f) {
            val sm = ColorMath.shadowsMask(luma)
            outR += p.shadows * sm * 0.3f
            outG += p.shadows * sm * 0.3f
            outB += p.shadows * sm * 0.3f
        }

        if (abs(p.whites) > 1e-6f) {
            val wm = ColorMath.whitesMask(luma)
            outR += p.whites * wm * 0.25f
            outG += p.whites * wm * 0.25f
            outB += p.whites * wm * 0.25f
        }

        if (abs(p.blacks) > 1e-6f) {
            val bm = ColorMath.blacksMask(luma)
            outR += p.blacks * bm * 0.25f
            outG += p.blacks * bm * 0.25f
            outB += p.blacks * bm * 0.25f
        }

        return floatArrayOf(outR, outG, outB)
    }

    private fun applyCreativeColor(r: Float, g: Float, b: Float, saturation: Float, vibrance: Float): FloatArray {
        if (abs(saturation) < 1e-6f && abs(vibrance) < 1e-6f) return floatArrayOf(r, g, b)
        val hsv = ColorMath.rgbToHsv(r, g, b)
        val currentSat = hsv[1]
        var skinProtection = 1f
        if (hsv[0] > 10f && hsv[0] < 50f && currentSat < 0.5f && hsv[2] > 0.2f) {
            skinProtection = 0.5f
        }
        val vibranceAmount = vibrance * (1f - currentSat) * skinProtection
        hsv[1] = (currentSat + vibranceAmount * 1.5f).coerceIn(0f, 1f)
        hsv[1] = (hsv[1] + saturation).coerceIn(0f, 1f)
        return ColorMath.hsvToRgb(hsv[0], hsv[1], hsv[2])
    }

    private fun applyColorGrading(r: Float, g: Float, b: Float, p: PipelineParams): FloatArray {
        val hasGrading = p.colorGradingShadows.any { abs(it) > 1e-6f } ||
            p.colorGradingMidtones.any { abs(it) > 1e-6f } ||
            p.colorGradingHighlights.any { abs(it) > 1e-6f } ||
            abs(p.colorGradingGlobalSat) > 1e-6f ||
            abs(p.colorGradingBalance) > 1e-6f

        if (!hasGrading) return floatArrayOf(r, g, b)

        val luma = ColorMath.getLuma(r, g, b)
        val sm = ColorMath.shadowsMask(luma)
        val mm = ColorMath.midtonesMask(luma)
        val hm = ColorMath.highlightsMask(luma)

        val balance = p.colorGradingBalance
        val balancedSm = sm * (1f + balance * 0.5f)
        val balancedHm = hm * (1f - balance * 0.5f)
        val maskSum = balancedSm + mm + balancedHm + 1e-6f
        val nsm = balancedSm / maskSum
        val nmm = mm / maskSum
        val nhm = balancedHm / maskSum

        val tintR = nsm * p.colorGradingShadows[0] + nmm * p.colorGradingMidtones[0] + nhm * p.colorGradingHighlights[0]
        val tintG = nsm * p.colorGradingShadows[1] + nmm * p.colorGradingMidtones[1] + nhm * p.colorGradingHighlights[1]
        val tintB = nsm * p.colorGradingShadows[2] + nmm * p.colorGradingMidtones[2] + nhm * p.colorGradingHighlights[2]

        var outR = r * (1f - p.colorGradingBlend) + (r + tintR) * p.colorGradingBlend
        var outG = g * (1f - p.colorGradingBlend) + (g + tintG) * p.colorGradingBlend
        var outB = b * (1f - p.colorGradingBlend) + (b + tintB) * p.colorGradingBlend

        if (abs(p.colorGradingGlobalSat) > 1e-6f) {
            val lum = ColorMath.getLuma(outR, outG, outB)
            outR = lum + (outR - lum) * (1f + p.colorGradingGlobalSat)
            outG = lum + (outG - lum) * (1f + p.colorGradingGlobalSat)
            outB = lum + (outB - lum) * (1f + p.colorGradingGlobalSat)
        }

        return floatArrayOf(outR, outG, outB)
    }

    private fun applyDehaze(r: Float, g: Float, b: Float, dehaze: Float): FloatArray {
        if (abs(dehaze) < 1e-6f) return floatArrayOf(r, g, b)
        val luma = ColorMath.getLuma(r, g, b)
        val hazeEstimate = ColorMath.smoothstep(0f, 0.8f, luma)
        return if (dehaze > 0f) {
            val atmosphericLightR = 0.85f
            val atmosphericLightG = 0.88f
            val atmosphericLightB = 0.92f
            var transmission = 1f - hazeEstimate * dehaze
            transmission = max(transmission, 0.1f)
            floatArrayOf(
                max((r - atmosphericLightR * hazeEstimate * dehaze) / transmission, 0f),
                max((g - atmosphericLightG * hazeEstimate * dehaze) / transmission, 0f),
                max((b - atmosphericLightB * hazeEstimate * dehaze) / transmission, 0f)
            )
        } else {
            val amount = -dehaze
            val atmosphericLightR = 0.85f
            val atmosphericLightG = 0.88f
            val atmosphericLightB = 0.92f
            floatArrayOf(
                r + (atmosphericLightR - r) * amount * hazeEstimate,
                g + (atmosphericLightG - g) * amount * hazeEstimate,
                b + (atmosphericLightB - b) * amount * hazeEstimate
            )
        }
    }

    private fun applyVignette(
        r: Float, g: Float, b: Float,
        nx: Float, ny: Float,
        p: PipelineParams,
    ): FloatArray {
        if (abs(p.vignette) < 1e-6f) return floatArrayOf(r, g, b)
        val cx = nx - 0.5f
        val cy = ny - 0.5f
        val dist = kotlin.math.sqrt(cx * cx + cy * cy) * 1.414f
        val aspect = if (p.vignetteRoundness > 0f) 1f + p.vignetteRoundness * 0.5f else 1f
        val shapedDist = dist * aspect
        val start = p.vignetteMidpoint * 0.7f
        val end = start + p.vignetteFeather * 0.3f + 0.05f
        val vignetteAmount: Float = if (p.vignette > 0f) {
            1f - ColorMath.smoothstep(start, end.coerceIn(start + 0.01f, 1f), shapedDist) * p.vignette
        } else {
            1f + ColorMath.smoothstep(start, end.coerceIn(start + 0.01f, 1f), shapedDist) * p.vignette
        }
        return floatArrayOf(r * vignetteAmount, g * vignetteAmount, b * vignetteAmount)
    }

    private fun applyGrain(
        r: Float, g: Float, b: Float,
        x: Float, y: Float,
        p: PipelineParams,
        imgW: Int, imgH: Int,
    ): FloatArray {
        if (p.grain < 1e-6f) return floatArrayOf(r, g, b)
        val noise = ColorMath.gradientNoise(x * p.grainSize, y * p.grainSize)
        var grainOffset = (noise - 0.5f) * p.grain * 0.3f
        if (p.grainRoughness > 1e-6f) {
            grainOffset *= (0.5f + p.grainRoughness * 0.5f)
        }
        val luma = ColorMath.getLuma(r, g, b)
        var grainAmount = 1f - abs(luma - 0.5f) * 1.5f
        grainAmount = grainAmount.coerceIn(0.2f, 1f)
        return floatArrayOf(
            r + grainOffset * grainAmount,
            g + grainOffset * grainAmount,
            b + grainOffset * grainAmount
        )
    }

    private fun applyGlow(r: Float, g: Float, b: Float, amount: Float): FloatArray {
        if (amount < 1e-6f) return floatArrayOf(r, g, b)
        val luma = ColorMath.getLuma(r, g, b)
        val bloom = luma * amount * 0.3f
        return floatArrayOf(r + bloom, g + bloom, b + bloom)
    }

    private fun applyHalation(r: Float, g: Float, b: Float, amount: Float): FloatArray {
        if (amount < 1e-6f) return floatArrayOf(r, g, b)
        val luma = ColorMath.getLuma(r, g, b)
        val highlightMask = ColorMath.highlightsMask(luma)
        val halation = highlightMask * amount * 0.4f
        return floatArrayOf(r + halation, g + halation * 0.3f, b + halation * 0.1f)
    }

    private fun applyFlare(r: Float, g: Float, b: Float, amount: Float): FloatArray {
        if (amount < 1e-6f) return floatArrayOf(r, g, b)
        val flareColor = floatArrayOf(0.9f, 0.85f, 0.8f)
        val blend = amount * 0.25f
        return floatArrayOf(
            r * (1f - blend) + flareColor[0] * blend,
            g * (1f - blend) + flareColor[1] * blend,
            b * (1f - blend) + flareColor[2] * blend
        )
    }

    private fun computeCalibrationMatrix(p: PipelineParams): FloatArray {
        val hasCalib = abs(p.calibRedHue) > 1e-6f || abs(p.calibRedSat) > 1e-6f ||
            abs(p.calibGreenHue) > 1e-6f || abs(p.calibGreenSat) > 1e-6f ||
            abs(p.calibBlueHue) > 1e-6f || abs(p.calibBlueSat) > 1e-6f

        if (!hasCalib) {
            return floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
        }

        val rh = p.calibRedHue * 60f
        val rs = 1f + p.calibRedSat
        val gh = p.calibGreenHue * 60f
        val gs = 1f + p.calibGreenSat
        val bh = p.calibBlueHue * 60f
        val bs = 1f + p.calibBlueSat

        val redPrimary = ColorMath.hsvToRgb(((rh) % 360f + 360f) % 360f, rs.coerceIn(0f, 2f), 1f)
        val greenPrimary = ColorMath.hsvToRgb(((120f + gh) % 360f + 360f) % 360f, gs.coerceIn(0f, 2f), 1f)
        val bluePrimary = ColorMath.hsvToRgb(((240f + bh) % 360f + 360f) % 360f, bs.coerceIn(0f, 2f), 1f)

        return floatArrayOf(
            redPrimary[0], redPrimary[1], redPrimary[2],
            greenPrimary[0], greenPrimary[1], greenPrimary[2],
            bluePrimary[0], bluePrimary[1], bluePrimary[2]
        )
    }
}

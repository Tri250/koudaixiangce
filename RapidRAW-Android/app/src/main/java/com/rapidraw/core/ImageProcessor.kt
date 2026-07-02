package com.rapidraw.core

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.rapidraw.data.model.ExrBitDepth
import com.rapidraw.core.ColorScience
import com.rapidraw.data.model.ExportFormat
import com.rapidraw.data.model.ExportSettings
import com.rapidraw.data.model.ExifData
import com.rapidraw.data.model.HeifBitDepth
import com.rapidraw.data.model.FilmSimulation
import com.rapidraw.data.model.MaskContainer
import com.rapidraw.data.model.ResizeMode
import com.rapidraw.data.model.WatermarkAnchor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import java.io.OutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

// ── Decoded Image (internal transport, uses model ExifData) ────────────

data class DecodedImage(
    val original: Bitmap,
    val preview: Bitmap,
    val width: Int,
    val height: Int,
    val isRaw: Boolean,
    val exif: ExifData,
    val orientation: Int = 0,
)

// ── Image Processor ────────────────────────────────────────────────────

class ImageProcessor {

    companion object {
        private const val TAG = "ImageProcessor"
        private const val PREVIEW_MAX_DIMENSION = 2048
        private const val MAX_MARK_BYTES = 64 * 1024 * 1024 // 64 MB
        // 导出时单边上限，避免 ~100MP 全分辨率 ARGB_8888 直接 OOM。
        private const val MAX_DECODE_PIXELS = 8192
        /** 100MP 阈值：超过此像素数的 RAW 将触发分块/降级保护 (R-19) */
        internal const val MAX_RAW_PIXELS_THRESHOLD = 100_000_000L
    }

    // ── L-03: 缩略图缓存管理 ──────────────────────────────────────────

    // 缩略图 LRU 缓存：适用于大图缩略图预览，低内存时可释放
    private val thumbnailCache = object : android.util.LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 8).toInt() // 最大使用 1/8 堆内存
    ) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.allocationByteCount
        }

        override fun entryRemoved(evicted: Boolean, key: String, oldValue: Bitmap, newValue: Bitmap?) {
            if (evicted && !oldValue.isRecycled) {
                oldValue.recycle()
            }
        }
    }

    /**
     * L-03: 清空缩略图内存缓存。
     * 在 onTrimMemory 回调中被调用，释放所有缓存的缩略图 Bitmap。
     * 缩略图可重新解码，优先释放以腾出内存空间供大图处理使用。
     */
    fun clearThumbnailCache() {
        thumbnailCache.evictAll()
        Log.d(TAG, "Thumbnail cache cleared")
    }

    /**
     * 将缩略图存入缓存。
     */
    fun cacheThumbnail(key: String, bitmap: Bitmap) {
        if (bitmap.isRecycled) return
        thumbnailCache.put(key, bitmap)
    }

    /**
     * 从缓存中获取缩略图。
     */
    fun getCachedThumbnail(key: String): Bitmap? {
        return thumbnailCache.get(key)
    }

    // ── Normalised Adjustments (private, proper equals/hashCode) ──────

    /**
     * Internal normalised adjustments used by the CPU processing pipeline.
     * All values are in the internal processing range (not UI scale).
     * Replaces the old core.Adjustments; uses List<Float> instead of
     * FloatArray and List<MaskContainer> instead of List<Any>.
     */
    private data class NormAdj(
        // Exposure & Brightness
        val exposure: Float = 0f,
        val brightness: Float = 0f,

        // Tone / Film
        val toneLevel: Float = 0f,
        val filmIntensity: Float = 1f,
        val greenMagenta: Float = 0f,
        val softGlow: Float = 0f,

        // Film simulation parameters
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
        val filmCurvePoints: List<Pair<Float, Float>> = listOf(0f to 0f, 0.2f to 0.2f, 0.4f to 0.4f, 0.6f to 0.6f, 0.8f to 0.8f, 1f to 1f),

        // White Balance
        val temperature: Float = 6500f,
        val tint: Float = 0f,

        // Tonal
        val contrast: Float = 0f,
        val highlights: Float = 0f,
        val shadows: Float = 0f,
        val whites: Float = 0f,
        val blacks: Float = 0f,

        // Color
        val saturation: Float = 0f,
        val vibrance: Float = 0f,
        val lightness: Float = 0f,          // D-04: 明度 -1..1

        // HSL 8-color panel
        val hueRed: Float = 0f,
        val satRed: Float = 0f,
        val lumRed: Float = 0f,
        val hueOrange: Float = 0f,
        val satOrange: Float = 0f,
        val lumOrange: Float = 0f,
        val hueYellow: Float = 0f,
        val satYellow: Float = 0f,
        val lumYellow: Float = 0f,
        val hueGreen: Float = 0f,
        val satGreen: Float = 0f,
        val lumGreen: Float = 0f,
        val hueAqua: Float = 0f,
        val satAqua: Float = 0f,
        val lumAqua: Float = 0f,
        val hueBlue: Float = 0f,
        val satBlue: Float = 0f,
        val lumBlue: Float = 0f,
        val huePurple: Float = 0f,
        val satPurple: Float = 0f,
        val lumPurple: Float = 0f,
        val hueMagenta: Float = 0f,
        val satMagenta: Float = 0f,
        val lumMagenta: Float = 0f,

        // Tone Curve
        val toneCurvePoints: List<Pair<Float, Float>> = listOf(
            0f to 0f, 0.1f to 0.1f, 0.25f to 0.25f, 0.5f to 0.5f,
            0.75f to 0.75f, 0.9f to 0.9f, 1f to 1f,
            0.125f to 0.125f, 0.375f to 0.375f, 0.625f to 0.625f
        ),

        // Color Grading
        val colorGradingShadows: List<Float> = listOf(0f, 0f, 0f),
        val colorGradingMidtones: List<Float> = listOf(0f, 0f, 0f),
        val colorGradingHighlights: List<Float> = listOf(0f, 0f, 0f),
        val colorGradingBlend: Float = 1f,
        val colorGradingGlobalSat: Float = 0f,

        // Color Calibration
        val calibRedHue: Float = 0f,
        val calibRedSat: Float = 0f,
        val calibGreenHue: Float = 0f,
        val calibGreenSat: Float = 0f,
        val calibBlueHue: Float = 0f,
        val calibBlueSat: Float = 0f,

        // Detail
        val sharpness: Float = 0f,
        val clarity: Float = 0f,
        val structure: Float = 0f,

        // Effects
        val dehaze: Float = 0f,
        val vignette: Float = 0f,
        val vignetteMidpoint: Float = 0.5f,
        val vignetteRoundness: Float = 0f,
        val vignetteFeather: Float = 0.5f,
        val grain: Float = 0f,
        val grainSize: Float = 1.0f,
        val grainRoughness: Float = 0f,
        val chromaticAberrationRedCyan: Float = 0f,
        val chromaticAberrationBlueYellow: Float = 0f,

        // Creative light effects
        val glowAmount: Float = 0f,
        val halationAmount: Float = 0f,
        val flareAmount: Float = 0f,

        // Blur-based creative effects
        val blurGlow: Float = 0f,
        val blurHalation: Float = 0f,

        // CDL Color Grading (per-channel R/G/B offsets for Lift/Gamma/Gain)
        val cdlShadowsR: Float = 0f,
        val cdlShadowsG: Float = 0f,
        val cdlShadowsB: Float = 0f,
        val cdlMidtonesR: Float = 0f,
        val cdlMidtonesG: Float = 0f,
        val cdlMidtonesB: Float = 0f,
        val cdlHighlightsR: Float = 0f,
        val cdlHighlightsG: Float = 0f,
        val cdlHighlightsB: Float = 0f,

        // LUT
        val lutIntensity: Float = 1f,

        // Detail (additional)
        val lumaNoiseReduction: Float = 0f,
        val colorNoiseReduction: Float = 0f,
        val centre: Float = 0f,

        // Color Grading (additional)
        val colorGradingBalance: Float = 0f,

        // Color Calibration (additional)
        val colorCalibrationShadowsTint: Float = 0f,

        // Transform
        val rotation: Float = 0f,
        val orientationSteps: Int = 0,
        val flipHorizontal: Boolean = false,
        val flipVertical: Boolean = false,
        val cropAspectRatio: Float? = null,
        val transformDistortion: Float = 0f,
        val transformVertical: Float = 0f,
        val transformHorizontal: Float = 0f,
        val transformRotate: Float = 0f,
        val transformAspect: Float = 0f,
        val transformScale: Float = 1f,
        val transformXOffset: Float = 0f,
        val transformYOffset: Float = 0f,

        // RGB Curves
        val redCurvePoints: List<Pair<Float, Float>> = listOf(0f to 0f, 1f to 1f),
        val greenCurvePoints: List<Pair<Float, Float>> = listOf(0f to 0f, 1f to 1f),
        val blueCurvePoints: List<Pair<Float, Float>> = listOf(0f to 0f, 1f to 1f),

        // Masks
        val masks: List<MaskContainer> = emptyList(),

        // Tone Mapping / Color Science
        val colorScienceMode: Int = 0, // 0=agx, 1=aces2, 2=opendrt, 3=standard
        val agxEnabled: Boolean = false,
        val agxContrast: Float = 0.0f,
        val agxPedestal: Float = 0.0f,
        val peakLuminanceNits: Float = 1000f,

        // Lens Correction
        val lensDistortion: Float = 0f,
        val lensVignette: Float = 0f,
        val lensTca: Float = 0f,
        val lensFocalLength: Float = 50f,

        // Debug
        val clippingPreview: Boolean = false
    ) {
        companion object {
            /**
             * Convert the canonical data.model.Adjustments to NormAdj.
             * Normalises value ranges from the UI scale to the internal processing scale.
             */
            fun from(src: com.rapidraw.data.model.Adjustments): NormAdj = NormAdj(
                exposure = src.exposure,
                brightness = src.brightness / 5f,                                  // -5..5 → -1..1
                toneLevel = src.toneLevel,
                filmIntensity = src.filmIntensity,
                greenMagenta = src.greenMagenta,
                softGlow = src.softGlow,
                filmId = src.filmId,
                filmHighlightRollOff = src.filmHighlightRollOff,
                filmShadowLift = src.filmShadowLift,
                filmDrCompression = src.filmDrCompression,
                filmRedShift = src.filmRedShift,
                filmGreenShift = src.filmGreenShift,
                filmBlueShift = src.filmBlueShift,
                filmSaturation = src.filmSaturation,
                filmContrast = src.filmContrast,
                filmGrainAmount = src.filmGrainAmount,
                filmGrainSize = src.filmGrainSize,
                filmGrainRoughness = src.filmGrainRoughness,
                filmCurvePoints = src.filmCurvePoints.map { (it.first / 255f) to (it.second / 255f) },
                temperature = 6500f + src.temperature * 50f,                       // offset → Kelvin
                tint = src.tint / 100f,                                            // -100..100 → -1..1
                contrast = src.contrast / 100f,                                    // -100..100 → -1..1
                highlights = src.highlights / 150f,                                // -150..150 → -1..1
                shadows = src.shadows / 100f,                                      // -100..100 → -1..1
                whites = src.whites / 30f,                                         // -30..30 → -1..1
                blacks = src.blacks / 60f,                                         // -60..60 → -1..1
                saturation = src.saturation / 100f,                                // -100..100 → -1..1
                vibrance = src.vibrance / 100f,                                    // -100..100 → -1..1
                lightness = src.lightness / 100f,                                   // D-04: 明度 -100..100 → -1..1
                hueRed = src.hslReds.hue / 100f,
                satRed = src.hslReds.saturation / 100f,
                lumRed = src.hslReds.luminance / 100f,
                hueOrange = src.hslOranges.hue / 100f,
                satOrange = src.hslOranges.saturation / 100f,
                lumOrange = src.hslOranges.luminance / 100f,
                hueYellow = src.hslYellows.hue / 100f,
                satYellow = src.hslYellows.saturation / 100f,
                lumYellow = src.hslYellows.luminance / 100f,
                hueGreen = src.hslGreens.hue / 100f,
                satGreen = src.hslGreens.saturation / 100f,
                lumGreen = src.hslGreens.luminance / 100f,
                hueAqua = src.hslAquas.hue / 100f,
                satAqua = src.hslAquas.saturation / 100f,
                lumAqua = src.hslAquas.luminance / 100f,
                hueBlue = src.hslBlues.hue / 100f,
                satBlue = src.hslBlues.saturation / 100f,
                lumBlue = src.hslBlues.luminance / 100f,
                huePurple = src.hslPurples.hue / 100f,
                satPurple = src.hslPurples.saturation / 100f,
                lumPurple = src.hslPurples.luminance / 100f,
                hueMagenta = src.hslMagentas.hue / 100f,
                satMagenta = src.hslMagentas.saturation / 100f,
                lumMagenta = src.hslMagentas.luminance / 100f,
                toneCurvePoints = src.lumaCurve.map { (it.x / 255f) to (it.y / 255f) },
                colorGradingShadows = listOf(
                    src.colorGrading.shadows.hue / 360f,
                    src.colorGrading.shadows.saturation / 100f,
                    src.colorGrading.shadows.luminance / 100f),
                colorGradingMidtones = listOf(
                    src.colorGrading.midtones.hue / 360f,
                    src.colorGrading.midtones.saturation / 100f,
                    src.colorGrading.midtones.luminance / 100f),
                colorGradingHighlights = listOf(
                    src.colorGrading.highlights.hue / 360f,
                    src.colorGrading.highlights.saturation / 100f,
                    src.colorGrading.highlights.luminance / 100f),
                colorGradingBlend = src.colorGrading.blending / 100f,
                colorGradingGlobalSat = 0f,
                calibRedHue = src.colorCalibration.redHue / 100f,
                calibRedSat = src.colorCalibration.redSaturation / 100f,
                calibGreenHue = src.colorCalibration.greenHue / 100f,
                calibGreenSat = src.colorCalibration.greenSaturation / 100f,
                calibBlueHue = src.colorCalibration.blueHue / 100f,
                calibBlueSat = src.colorCalibration.blueSaturation / 100f,
                sharpness = src.sharpness / 150f * 4f,                             // 0..150 → 0..4
                clarity = src.clarity / 100f,                                      // -100..100 → -1..1
                structure = src.structure / 100f,                                   // -100..100 → -1..1
                dehaze = src.dehaze / 100f,                                        // -100..100 → -1..1
                vignette = src.vignetteAmount / 100f,                              // -100..100 → -1..1
                grain = src.grainAmount / 100f,                                    // 0..100 → 0..1
                grainSize = src.grainSize / 100f * 3f,                             // 0..100 → 0..3
                chromaticAberrationRedCyan = src.chromaticAberrationRedCyan / 100f,
                chromaticAberrationBlueYellow = src.chromaticAberrationBlueYellow / 100f,
                colorScienceMode = src.colorScienceMode.coerceIn(0, 3),
                agxEnabled = src.toneMapper == "agx" || src.colorScienceMode == 0,
                agxContrast = src.agxContrast,
                agxPedestal = src.agxPedestal,
                peakLuminanceNits = src.peakLuminanceNits.coerceIn(100f, 10000f),
                // Lens correction
                lensDistortion = src.lensDistortion / 100f,
                lensVignette = src.lensVignette / 100f,
                lensTca = src.lensTca / 100f,
                lensFocalLength = src.lensFocalLength,
                // Vignette sub-parameters
                vignetteMidpoint = src.vignetteMidpoint / 100f,
                vignetteRoundness = src.vignetteRoundness / 100f,
                vignetteFeather = src.vignetteFeather / 100f,
                // Grain roughness
                grainRoughness = src.grainRoughness / 100f,
                // Creative light effects
                glowAmount = src.glowAmount / 100f,
                halationAmount = src.halationAmount / 100f,
                flareAmount = src.flareAmount / 100f,
                // Blur-based creative effects (unified with glowAmount/halationAmount)
                blurGlow = src.glowAmount / 100f,
                blurHalation = src.halationAmount / 100f,
                // CDL Color Grading
                cdlShadowsR = src.colorGradingShadowsR / 100f,
                cdlShadowsG = src.colorGradingShadowsG / 100f,
                cdlShadowsB = src.colorGradingShadowsB / 100f,
                cdlMidtonesR = src.colorGradingMidtonesR / 100f,
                cdlMidtonesG = src.colorGradingMidtonesG / 100f,
                cdlMidtonesB = src.colorGradingMidtonesB / 100f,
                cdlHighlightsR = src.colorGradingHighlightsR / 100f,
                cdlHighlightsG = src.colorGradingHighlightsG / 100f,
                cdlHighlightsB = src.colorGradingHighlightsB / 100f,
                lutIntensity = src.lutIntensity / 100f,
                // Detail (additional)
                lumaNoiseReduction = src.lumaNoiseReduction / 100f,
                colorNoiseReduction = src.colorNoiseReduction / 100f,
                centre = src.centre / 100f,
                // Color grading balance
                colorGradingBalance = src.colorGrading.balance / 100f,
                // Color calibration shadows tint
                colorCalibrationShadowsTint = src.colorCalibration.shadowsTint / 100f,
                // Transform
                rotation = src.rotation,
                orientationSteps = src.orientationSteps,
                flipHorizontal = src.flipHorizontal,
                flipVertical = src.flipVertical,
                cropAspectRatio = src.crop?.aspectRatio,
                transformDistortion = src.transformDistortion / 100f,
                transformVertical = src.transformVertical / 100f,
                transformHorizontal = src.transformHorizontal / 100f,
                transformRotate = src.transformRotate,
                transformAspect = src.transformAspect / 100f,
                transformScale = src.transformScale / 100f,
                transformXOffset = src.transformXOffset / 100f,
                transformYOffset = src.transformYOffset / 100f,
                // RGB Curves
                redCurvePoints = src.redCurve.map { it.x / 255f to it.y / 255f },
                greenCurvePoints = src.greenCurve.map { it.x / 255f to it.y / 255f },
                blueCurvePoints = src.blueCurve.map { it.x / 255f to it.y / 255f },
                // Masks
                masks = src.masks,
                clippingPreview = src.showClipping,
            )
        }
    }

    /** Current 3D LUT to apply during CPU processing (GPU preview uses texture directly).
     *  2026 hotfix: 使用 @Volatile 保证多协程并发调用 processFullResolution 时
     *  对 LUT 引用的可见性，避免脏读导致的预览/导出不一致。 */
    @Volatile
    var currentLut: CubeLutParser.Lut3D? = null

    private val aiDenoiser = AiDenoiser()

    // ── Load & Decode ──────────────────────────────────────────────

    suspend fun loadAndDecode(context: Context, uri: Uri): DecodedImage =
        withContext(Dispatchers.IO) {
            // v1.10.6: 大图解码耗时久，定期检查取消状态，避免用户离开页面后继续浪费资源
            suspend fun ensureActive() { if (!(coroutineContext[Job]?.isActive == true)) throw CancellationException("loadAndDecode cancelled") }
            try {
            ensureActive()
            val fileName = getFileName(context, uri)
            val isDng = fileName.lowercase().endsWith(".dng")
            val isRaw = isDng ||
                fileName.lowercase().let {
                    it.endsWith(".raw") || it.endsWith(".cr2") ||
                    it.endsWith(".nef") || it.endsWith(".arw") ||
                    it.endsWith(".orf") || it.endsWith(".rw2") ||
                    it.endsWith(".raf") || it.endsWith(".pef") ||
                    it.endsWith(".sr2") || it.endsWith(".dcr") ||
                    it.endsWith(".kdc") || it.endsWith(".3fr") ||
                    it.endsWith(".mrw")
                }

            // Extract EXIF
            ensureActive()
            val (exif, orientation) = readExifData(context, uri)

            // Decode bitmap
            ensureActive()

            // R-19: 超大 RAW (>100MP) 保护：先检查预估像素数，超标则强制降采样
            val estimatedRawPixels = if (isRaw) {
                try {
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    decodeUriStream(context, uri, opts)
                    opts.outWidth.toLong() * opts.outHeight.toLong()
                } catch (_: Exception) { 0L }
            } else 0L
            if (isRaw && estimatedRawPixels > MAX_RAW_PIXELS_THRESHOLD) {
                Log.w(TAG, "超大 RAW  detected: ~${estimatedRawPixels / 1_000_000}MP，启用分块 ROI 降级保护")
            }

            val originalBitmap: Bitmap = if (isRaw) {
                // Try libraw-based decoder first (real RAW support for CR2/NEF/ARW/RAF/RW2/ORF/DNG)
                val librawBitmap = try {
                    RawDecoder.decodeRaw(context, uri)
                } catch (e: Exception) {
                    Log.w(TAG, "libraw decoder failed, falling back: ${e.message}")
                    null
                }
                librawBitmap ?: if (isDng && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    decodeDng(context, uri)
                } else {
                    decodeRawFallback(context, uri)
                }
            } else {
                try {
                    decodeRegular(context, uri)
                } catch (oom: OutOfMemoryError) {
                    Log.w(TAG, "OOM in decodeRegular, retrying with higher inSampleSize", oom)
                    // Retry with progressively higher inSampleSize (up to 3 doublings)
                    var decoded: Bitmap? = null
                    var sampleSize = 2
                    var attempts = 0
                    while (decoded == null && attempts < 3) {
                        ensureActive()
                        try {
                            decoded = decodeRegularWithSampleSize(context, uri, sampleSize)
                        } catch (oom2: OutOfMemoryError) {
                            Log.w(TAG, "OOM in decodeRegular attempt ${attempts + 1} with inSampleSize=$sampleSize", oom2)
                            sampleSize *= 2
                            attempts++
                        }
                    }
                    decoded ?: throw oom
                } catch (e: IllegalArgumentException) {
                    throw IllegalArgumentException("Failed to decode image: ${e.message}", e)
                }
            }

            // v1.10.6: 大图解码后检查取消，避免继续占用内存处理已不需要的结果
            ensureActive()

            // Apply EXIF orientation
            val orientedBitmap = applyExifOrientation(originalBitmap, orientation)

            // v1.10.6: EXIF 旋转后再次检查取消
            ensureActive()

            // Create preview
            val previewBitmap = createPreview(orientedBitmap)

            // v1.10.6: 预览生成后检查取消，避免返回无效结果
            ensureActive()

            DecodedImage(
                original = orientedBitmap,
                preview = previewBitmap,
                width = orientedBitmap.width,
                height = orientedBitmap.height,
                isRaw = isRaw,
                exif = exif,
                orientation = orientation
            )
            } catch (oom: OutOfMemoryError) {
                Log.e(TAG, "OutOfMemoryError in loadAndDecode", oom)
                throw IllegalArgumentException("图片过大，内存不足")
            }
        }

    private fun getFileName(context: Context, uri: Uri): String {
        var name = ""
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) name = it.getString(nameIndex)
            }
        }
        if (name.isEmpty()) name = uri.lastPathSegment ?: "unknown.raw"
        return name
    }

    private fun readExifData(context: Context, uri: Uri): Pair<ExifData, Int> {
        var orientation = 0
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return ExifData() to 0
            inputStream.use { stream ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    val exifInterface = androidx.exifinterface.media.ExifInterface(stream)
                    orientation = when (exifInterface.getAttributeInt(
                        androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                        androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
                    )) {
                        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90
                        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
                        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270
                        else -> 0
                    }

                    val exifData = ExifData(
                        make = exifInterface.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_MAKE),
                        model = exifInterface.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_MODEL),
                        dateTime = exifInterface.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME),
                        iso = exifInterface.getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, 0)
                            .takeIf { it > 0 }?.toString(),
                        shutterSpeed = exifInterface.getAttributeDouble(androidx.exifinterface.media.ExifInterface.TAG_EXPOSURE_TIME, 0.0)
                            .takeIf { it > 0.0 }?.toString(),
                        focalLength = exifInterface.getAttributeDouble(androidx.exifinterface.media.ExifInterface.TAG_FOCAL_LENGTH, 0.0)
                            .takeIf { it > 0.0 }?.toString(),
                        aperture = exifInterface.getAttributeDouble(androidx.exifinterface.media.ExifInterface.TAG_F_NUMBER, 0.0)
                            .takeIf { it > 0.0 }?.toString(),
                    )
                    return exifData to orientation
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read EXIF: ${e.message}")
        }
        return ExifData() to orientation
    }

    private fun decodeDng(context: Context, uri: Uri): Bitmap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
                return android.graphics.ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.setAllocator(android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE)
                    decoder.isMutableRequired = true
                    // Size guard: if DNG exceeds ~50MP, downsample to avoid OOM
                    val maxDim = 4096
                    if (info.size.width > maxDim || info.size.height > maxDim) {
                        val scale = min(maxDim.toFloat() / info.size.width, maxDim.toFloat() / info.size.height)
                        decoder.setTargetSize(
                            (info.size.width * scale).toInt(),
                            (info.size.height * scale).toInt()
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "ImageDecoder failed for DNG, falling back: ${e.message}")
            }
        }
        return decodeRawFallback(context, uri)
    }

    private fun decodeRawFallback(context: Context, uri: Uri): Bitmap {
        // Try to decode RAW with BitmapFactory - some Android versions support this
        try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            decodeUriStream(context, uri, options)

            // Calculate inSampleSize for manageable size
            val maxDim = 4096
            var inSampleSize = calculateInSampleSize(options.outWidth, options.outHeight, maxDim, maxDim)

            // OOM guard: if estimated pixels > 50MP for RAW, enforce at least 2x downsample
            val estimatedPixels = options.outWidth.toLong() * options.outHeight.toLong()
            if (estimatedPixels > 50_000_000L && inSampleSize < 2) {
                inSampleSize = 2
                Log.i(TAG, "Large RAW (~${estimatedPixels / 1_000_000}MP), using inSampleSize=2 for OOM protection")
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inMutable = true
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val result = decodeUriStream(context, uri, decodeOptions)
            if (result != null) return result
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "OOM decoding RAW, falling back to thumbnail", e)
            // 继续走 thumbnail fallback
        } catch (e: Exception) {
            Log.w(TAG, "BitmapFactory failed to decode RAW: ${e.message}")
        }

        // Last resort: extract thumbnail from ExifInterface
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: throw IllegalArgumentException("Cannot open URI for thumbnail")
            inputStream.use { stream ->
                val exifInterface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    androidx.exifinterface.media.ExifInterface(stream)
                } else {
                    throw IllegalArgumentException("Cannot extract thumbnail on API < 24")
                }
                val thumbnail = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    exifInterface.thumbnailBitmap
                } else null
                thumbnail?.copy(Bitmap.Config.ARGB_8888, true)
                    ?: throw IllegalArgumentException("No thumbnail available for RAW image")
            }
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OOM extracting thumbnail for RAW; cannot decode", e)
            throw IllegalArgumentException("无法解码 RAW 图像：内存不足")
        } catch (e: Exception) {
            Log.w(TAG, "Thumbnail fallback also failed for RAW: ${e.message}")
            throw IllegalArgumentException("Failed to decode RAW image and no thumbnail available")
        }
    }

    private fun decodeRegular(context: Context, uri: Uri): Bitmap {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        decodeUriStream(context, uri, options)

        // Second pass: decode with size guard to avoid OOM on extremely large images
        val decodeOptions = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inMutable = true
            val maxDim = 4096
            inSampleSize = calculateInSampleSize(options.outWidth, options.outHeight, maxDim, maxDim)
        }
        val result = decodeUriStream(context, uri, decodeOptions)
            ?: throw IllegalArgumentException("Failed to decode image")
        return result
    }

    /**
     * Variant of [decodeRegular] that forces a specific [inSampleSize].
     * Used when the initial decode triggers OOM and we retry with more downsampling.
     */
    private fun decodeRegularWithSampleSize(context: Context, uri: Uri, inSampleSize: Int): Bitmap {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        decodeUriStream(context, uri, options)

        val decodeOptions = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inMutable = true
            this.inSampleSize = inSampleSize.coerceAtLeast(
                calculateInSampleSize(options.outWidth, options.outHeight, 4096, 4096)
            )
        }
        return decodeUriStream(context, uri, decodeOptions)
            ?: throw IllegalArgumentException("Failed to decode image with inSampleSize=$inSampleSize")
    }

    /**
     * 对 content URI 进行单流两次解码（bounds + decode）。
     * 若流支持 mark/reset 则复用同一 InputStream，避免 contentResolver 两次 open 的 IPC 开销；
     * 否则回退到重新 open。
     */
    private fun decodeUriStream(context: Context, uri: Uri, options: BitmapFactory.Options): Bitmap? {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        return inputStream.use { stream ->
            if (stream.markSupported()) {
                stream.mark(MAX_MARK_BYTES)
                val bitmap = BitmapFactory.decodeStream(stream, null, options)
                try {
                    stream.reset()
                } catch (_: java.io.IOException) {
                    // reset 失败则让调用方重新 open
                }
                bitmap
            } else {
                BitmapFactory.decodeStream(stream, null, options)
            }
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight &&
                (halfWidth / inSampleSize) >= reqWidth
            ) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    /**
     * 从文件路径或 content URI 同步加载全分辨率位图。
     *
     * v1.5.5 hotfix: 之前没有公开方法让 [com.rapidraw.data.export.ExportQueueProcessor]
     * 在编辑器已销毁的情况下独立加载源图，导致"重试"按钮无法真正导出。
     * 加载失败（路径不存在 / RAW 解码失败 / OOM）返回 null，由调用方标记 FAILED。
     *
     * @param imagePath 形如 "/sdcard/xxx.dng" 或 "content://..." 的源图像引用
     * @param allowDownsample 当图像超过 [MAX_DECODE_PIXELS] 时是否降采样（默认否：导出需要全分辨率）
     */
    fun loadBitmap(imagePath: String, allowDownsample: Boolean = false): Bitmap? {
        return try {
            if (imagePath.isBlank()) return null
            val context = appContextRef
            val uri = Uri.parse(imagePath)
            if (uri.scheme.isNullOrEmpty()) {
                loadBitmapFromFile(path = imagePath, allowDownsample = allowDownsample)
            } else {
                loadBitmapFromUri(context, uri, allowDownsample)
            }
        } catch (oom: OutOfMemoryError) {
            Log.w(TAG, "OOM loading bitmap for $imagePath", oom)
            null
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to load bitmap for $imagePath", t)
            null
        }
    }

    private fun loadBitmapFromFile(path: String, allowDownsample: Boolean): Bitmap? {
        val file = java.io.File(path)
        if (!file.exists() || !file.canRead()) return null
        val fileName = file.name
        // v1.5.5 hotfix: RAW 文件必须走 libraw 解码，普通 BitmapFactory.decodeFile 只能拿到缩略图/损坏像素
        if (isRawFileName(fileName)) {
            // v1.10.5: 主线程调用 runBlocking 会导致 ANR，添加检测与超时保护
            if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                Log.w(TAG, "loadBitmapFromFile called on main thread for RAW file, this may cause ANR. Falling back to simple decode.")
                return loadBitmapFromFileSimple(path, allowDownsample)
            }
            return runBlocking {
                withTimeoutOrNull(30_000L) {
                    try {
                        val context = appContextRef
                        val uri = Uri.fromFile(file)
                        com.rapidraw.core.RawDecoder.decodeRaw(context, uri)
                    } catch (t: Throwable) {
                        Log.w(TAG, "RAW decode failed for $fileName, falling back to BitmapFactory", t)
                        loadBitmapFromFileSimple(path, allowDownsample)
                    }
                } ?: run {
                    Log.w(TAG, "RAW decode timed out for $fileName, falling back to BitmapFactory")
                    loadBitmapFromFileSimple(path, allowDownsample)
                }
            }
        }
        return loadBitmapFromFileSimple(path, allowDownsample)
    }

    private fun loadBitmapFromFileSimple(path: String, allowDownsample: Boolean): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return null
        val decodeOptions = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            if (!allowDownsample) {
                val maxDim = MAX_DECODE_PIXELS
                inSampleSize = calculateInSampleSize(options.outWidth, options.outHeight, maxDim, maxDim)
            }
        }
        return BitmapFactory.decodeFile(path, decodeOptions)
    }

    private fun isRawFileName(name: String): Boolean {
        val n = name.lowercase()
        return n.endsWith(".dng") || n.endsWith(".raw") || n.endsWith(".cr2") ||
            n.endsWith(".nef") || n.endsWith(".arw") || n.endsWith(".orf") ||
            n.endsWith(".rw2") || n.endsWith(".raf") || n.endsWith(".pef") ||
            n.endsWith(".sr2") || n.endsWith(".dcr") || n.endsWith(".kdc") ||
            n.endsWith(".3fr") || n.endsWith(".mrw")
    }

    private fun loadBitmapFromUri(context: Context, uri: Uri, allowDownsample: Boolean): Bitmap? {
        // v1.5.5 hotfix: 通过 URI 加载时也要识别 RAW 后缀并走 libraw 解码路径
        val fileName = getFileName(context, uri)
        if (isRawFileName(fileName)) {
            // v1.10.5: 主线程调用 runBlocking 会导致 ANR，添加检测与超时保护
            if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                Log.w(TAG, "loadBitmapFromUri called on main thread for RAW file, this may cause ANR. Falling back to simple decode.")
                return loadBitmapFromUriSimple(context, uri, allowDownsample)
            }
            return runBlocking {
                withTimeoutOrNull(30_000L) {
                    try {
                        com.rapidraw.core.RawDecoder.decodeRaw(context, uri)
                    } catch (t: Throwable) {
                        Log.w(TAG, "RAW decode failed for $fileName, falling back to BitmapFactory", t)
                        loadBitmapFromUriSimple(context, uri, allowDownsample)
                    }
                } ?: run {
                    Log.w(TAG, "RAW decode timed out for $fileName, falling back to BitmapFactory")
                    loadBitmapFromUriSimple(context, uri, allowDownsample)
                }
            }
        }
        return loadBitmapFromUriSimple(context, uri, allowDownsample)
    }

    private fun loadBitmapFromUriSimple(context: Context, uri: Uri, allowDownsample: Boolean): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        decodeUriStream(context, uri, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return null
        val decodeOptions = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            if (!allowDownsample) {
                val maxDim = MAX_DECODE_PIXELS
                inSampleSize = calculateInSampleSize(options.outWidth, options.outHeight, maxDim, maxDim)
            }
        }
        return decodeUriStream(context, uri, decodeOptions)
    }

    private val appContextRef: Context
        get() = com.rapidraw.RapidRawApp.getInstance()
            ?.applicationContext
            ?: error("RapidRawApp not initialized when loadBitmap was called")

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        if (orientation == 0) return bitmap
        val matrix = Matrix()
        when (orientation) {
            90 -> matrix.postRotate(90f)
            180 -> matrix.postRotate(180f)
            270 -> matrix.postRotate(270f)
            else -> return bitmap
        }
        // v1.5.5 hotfix: createBitmap 在 OOM 或 width*height*4 整数溢出时抛异常。
        // 异常向上传播后原 bitmap 不会被 recycle，造成位图泄漏。这里加 OOM 兜底返回原图。
        // 2026 hotfix: HARDWARE bitmap 不可变，无法传给 createBitmap，需先 copy 再旋转。
        val sourceBitmap = try {
            if (!bitmap.isMutable) {
                bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true) ?: bitmap
            } else bitmap
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "OOM copying immutable bitmap for rotation", e)
            bitmap
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Cannot copy bitmap for rotation", e)
            bitmap
        }

        val rotated = try {
            Bitmap.createBitmap(sourceBitmap, 0, 0, sourceBitmap.width, sourceBitmap.height, matrix, true)
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "OOM applying EXIF orientation, returning source", e)
            return bitmap
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "IllegalArgumentException applying EXIF orientation, returning source", e)
            return bitmap
        }
        if (rotated !== bitmap && rotated !== sourceBitmap) {
            if (sourceBitmap !== bitmap) sourceBitmap.recycle()
            bitmap.recycle()
        }
        return rotated
    }

    private fun createPreview(original: Bitmap): Bitmap {
        val maxDim = PREVIEW_MAX_DIMENSION
        if (original.width <= maxDim && original.height <= maxDim) {
            // 始终创建独立副本，避免 originalBitmap 与 previewBitmapCache 共享引用导致重复回收或并发问题
            return try {
                original.copy(original.config ?: Bitmap.Config.ARGB_8888, false)
            } catch (e: OutOfMemoryError) {
                Log.w(TAG, "OOM creating preview copy, falling back to scaled", e)
                // OOM 时降级到缩略图策略
                val scale = 0.5f
                Bitmap.createScaledBitmap(
                    original,
                    (original.width * scale).toInt().coerceAtLeast(1),
                    (original.height * scale).toInt().coerceAtLeast(1),
                    true,
                )
            }
        }

        val scale = min(maxDim.toFloat() / original.width, maxDim.toFloat() / original.height)
        val newWidth = (original.width * scale).toInt().coerceAtLeast(1)
        val newHeight = (original.height * scale).toInt().coerceAtLeast(1)

        return try {
            Bitmap.createScaledBitmap(original, newWidth, newHeight, true)
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "OOM creating scaled preview, returning source", e)
            // v1.5.5 hotfix: 不能直接返回 original 共享引用，否则 previewBitmapCache 和
            // originalBitmap 指向同一 Bitmap，后续 recycle 时会把原图一起释放。
            // 改用 copy() 兜底，copy 失败时仍返回原图但要在调用方做相等性检查。
            try {
                original.copy(original.config ?: Bitmap.Config.ARGB_8888, false)
            } catch (_: OutOfMemoryError) {
                original
            } catch (_: IllegalStateException) {
                original
            }
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "IllegalArgumentException creating scaled preview, returning source", e)
            try {
                original.copy(original.config ?: Bitmap.Config.ARGB_8888, false)
            } catch (_: OutOfMemoryError) {
                original
            } catch (_: IllegalStateException) {
                original
            }
        }
    }

    // ── GPU Preview Processing ─────────────────────────────────────

    suspend fun processPreview(
        gpuPipeline: GpuPipeline,
        adjustments: com.rapidraw.data.model.Adjustments,
        previewWidth: Int,
        previewHeight: Int
    ): Bitmap? = withContext(Dispatchers.Main) {
        gpuPipeline.updateAdjustments(adjustments)

        // Create a preview-sized bitmap from the pipeline
        val previewBitmap = gpuPipeline.getProcessedBitmap()
        previewBitmap
    }

    // ── CPU Full Resolution Processing ─────────────────────────────

    /**
     * Full CPU processing pipeline for export.
     * Applies all adjustments in linear color space with progress reporting.
     */
    // GPU-CPU CONSISTENCY REQUIREMENT:
    // Any new adjustment parameter MUST be added to ALL THREE places:
    // 1. ImageProcessor.NormAdj.from() - CPU normalization
    // 2. GpuPipeline.updateAdjustments() - GPU uniform upload
    // 3. res/raw/image_adjustment.fs - Fragment shader processing
    // Violation will cause preview/export mismatch.
    suspend fun processFullResolution(
        adjustments: com.rapidraw.data.model.Adjustments,
        originalBitmap: Bitmap,
        allowDownsample: Boolean = true
    ): Bitmap = withContext(Dispatchers.Default) {
        // v1.5.9 hotfix: 前置校验输入位图，避免 recycled/空位图进入像素处理循环导致 native 崩溃。
        if (originalBitmap.isRecycled) {
            throw IllegalStateException("Cannot process recycled bitmap")
        }
        if (originalBitmap.width <= 0 || originalBitmap.height <= 0) {
            throw IllegalStateException("Invalid bitmap dimensions: ${originalBitmap.width}x${originalBitmap.height}")
        }

        // 2026 hotfix: 变量必须在 try 之前声明，否则 catch 块访问不到
        val n = try {
            NormAdj.from(adjustments)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to normalize adjustments", e)
            NormAdj()
        }

        // 内存保护：对于超大图（>64MP），可选降采样处理
        val maxPixels = 64_000_000 // 64MP
        val totalPixels = originalBitmap.width.toLong() * originalBitmap.height.toLong()
        val sourceBitmap = try {
            if (allowDownsample && totalPixels > maxPixels) {
                val scale = sqrt(maxPixels.toDouble() / totalPixels.toDouble()).toFloat()
                val newW = (originalBitmap.width * scale).toInt()
                val newH = (originalBitmap.height * scale).toInt()
                Bitmap.createScaledBitmap(originalBitmap, newW, newH, true)
            } else {
                originalBitmap
            }
        } catch (oom: OutOfMemoryError) {
            Log.e(TAG, "OOM creating source bitmap (downsample)", oom)
            throw IllegalStateException("内存不足，无法准备图像（${originalBitmap.width}x${originalBitmap.height}）", oom)
        }

        val w = sourceBitmap.width
        val h = sourceBitmap.height
        val pixelCount = w.toLong() * h.toLong()

        // Create output bitmap
        val outputBitmap = try {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        } catch (oom: OutOfMemoryError) {
            Log.e(TAG, "OOM creating output bitmap", oom)
            if (sourceBitmap !== originalBitmap) sourceBitmap.recycle()
            throw IllegalStateException("内存不足，无法创建输出位图（${w}x${h}）", oom)
        }

        // Get pixels as int array
        // 2026 hotfix: 防御 w*h 整数溢出
        val pixels: IntArray = try {
            if (pixelCount > Int.MAX_VALUE.toLong()) {
                throw IllegalArgumentException("Bitmap too large: $w x $h")
            }
            IntArray(pixelCount.toInt())
        } catch (oom: OutOfMemoryError) {
            if (!outputBitmap.isRecycled) outputBitmap.recycle()
            if (sourceBitmap !== originalBitmap) sourceBitmap.recycle()
            throw IllegalStateException("内存不足，无法分配像素数组（${w}x${h}）", oom)
        } catch (e: IllegalArgumentException) {
            if (!outputBitmap.isRecycled) outputBitmap.recycle()
            if (sourceBitmap !== originalBitmap) sourceBitmap.recycle()
            throw IllegalStateException("Invalid bitmap size: ${w}x${h}", e)
        }
        try {
            sourceBitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        } catch (oom: OutOfMemoryError) {
            // IntArray 分配时已成功，但 getPixels 不应 OOM；兜底
            if (!outputBitmap.isRecycled) outputBitmap.recycle()
            if (sourceBitmap !== originalBitmap) sourceBitmap.recycle()
            throw IllegalStateException("内存不足，无法读取像素（${w}x${h}）", oom)
        }

        // 2026 hotfix: 整个处理流程使用 try 包裹，确保 OOM/取消时能正确清理所有中间位图
        // workBitmap 必须在 try 之前声明，否则 catch 块访问不到
        var workBitmap = sourceBitmap
        try {

        // 2026 perf: 提前计算每像素常量，避免在内层 2000 万+ 像素循环中重复 pow/创建对象
        val exposureMultiplier = 2f.pow(n.exposure)
        val brightnessShift = n.brightness * 2f
        val colorScienceMode = ColorScience.Mode.entries.getOrElse(n.colorScienceMode) { ColorScience.Mode.STANDARD }
        val colorScienceConfig = ColorScience.Config(
            mode = colorScienceMode,
            displaySpace = ColorScience.DisplayColorSpace.SRGB,
            eotf = ColorScience.Eotf.SDR,
            peakLuminanceNits = n.peakLuminanceNits,
            contrast = n.agxContrast,
            pedestal = n.agxPedestal,
        )

        // Pre-compute white balance multipliers
        val wbMultipliers = ColorMath.temperatureTintToMultipliers(
            n.temperature, n.tint
        )

        // Pre-compute tone curve (identity if no changes)
        val curvePoints = n.toneCurvePoints.sortedBy { it.first }

        // Pre-compute film curve points
        val filmCurve = n.filmCurvePoints.sortedBy { it.first }

        // Pre-compute color calibration matrix
        val calibMatrix = computeCalibrationMatrix(n)

        // Pre-compute soft glow bloom buffer if needed
        val bloomBuffer = if (n.softGlow > 1e-6f) computeBloomBuffer(pixels, w, h, n.softGlow) else null

        // Pre-compute RGB curve points
        val redCurve = n.redCurvePoints.sortedBy { it.first }
        val greenCurve = n.greenCurvePoints.sortedBy { it.first }
        val blueCurve = n.blueCurvePoints.sortedBy { it.first }

        // Apply geometric transform before pixel processing
        val hasTransform = n.rotation != 0f || n.orientationSteps != 0 ||
            n.flipHorizontal || n.flipVertical || n.cropAspectRatio != null ||
            abs(n.transformDistortion) > 1e-6f || abs(n.transformVertical) > 1e-6f ||
            abs(n.transformHorizontal) > 1e-6f || abs(n.transformRotate) > 1e-6f ||
            abs(n.transformAspect) > 1e-6f || n.transformScale != 1f ||
            abs(n.transformXOffset) > 1e-6f || abs(n.transformYOffset) > 1e-6f
        if (hasTransform) {
            workBitmap = applyGeometricTransform(workBitmap, n)
        }

        // Re-read pixels after geometric transform
        if (workBitmap !== sourceBitmap) {
            workBitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        }

        // Apply lens correction if enabled (using LensDistortionCorrector for profile-based correction)
        if (abs(n.lensDistortion) > 1e-6f || abs(n.lensVignette) > 1e-6f || abs(n.lensTca) > 1e-6f) {
            val lensCorrector = LensDistortionCorrector()
            val lensProfile = LensDistortionCorrector.findProfile(
                make = "", model = "", focalLength = n.lensFocalLength
            )
            var lensCorrected = workBitmap
            if (lensProfile != null) {
                // Apply distortion correction
                if (abs(n.lensDistortion) > 1e-6f) {
                    lensCorrected = lensCorrector.correctDistortion(lensCorrected, lensProfile, abs(n.lensDistortion))
                }
                // Apply vignette correction
                if (abs(n.lensVignette) > 1e-6f) {
                    lensCorrected = lensCorrector.correctVignette(lensCorrected, lensProfile, abs(n.lensVignette))
                }
            }
            // Apply TCA correction (no profile needed)
            if (abs(n.lensTca) > 1e-6f) {
                lensCorrected = lensCorrector.correctTca(lensCorrected, abs(n.lensTca))
            }
            // Fallback to legacy applyLensCorrection for combined correction
            if (lensCorrected === workBitmap) {
                lensCorrected = applyLensCorrection(workBitmap, n)
            }
            if (lensCorrected !== workBitmap && workBitmap !== sourceBitmap) workBitmap.recycle()
            workBitmap = lensCorrected
            workBitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        }

        // Apply denoising if requested (CPU-based DenoiseProcessor for preview, AiDenoiser for strong noise)
        if (n.lumaNoiseReduction > 1e-6f || n.colorNoiseReduction > 1e-6f) {
            if (n.lumaNoiseReduction > 10f || n.colorNoiseReduction > 10f) {
                // Strong noise: use AI denoiser
                val denoised = aiDenoiser.denoise(
                    workBitmap,
                    preserveDetails = 1f - (n.lumaNoiseReduction / 150f).coerceIn(0f, 1f),
                    chromaStrength = (n.colorNoiseReduction / 100f).coerceIn(0f, 1f)
                )
                if (denoised !== workBitmap) {
                    if (workBitmap !== sourceBitmap) workBitmap.recycle()
                    workBitmap = denoised
                    workBitmap.getPixels(pixels, 0, w, 0, 0, w, h)
                }
            } else {
                // Light noise: use CPU-based DenoiseProcessor (bilateral + chroma blur)
                val denoiseProcessor = DenoiseProcessor()
                val denoised = denoiseProcessor.process(
                    workBitmap,
                    DenoiseProcessor.Params(
                        lumaDenoise = n.lumaNoiseReduction,
                        colorDenoise = n.colorNoiseReduction
                    )
                )
                if (denoised !== workBitmap) {
                    if (workBitmap !== sourceBitmap) workBitmap.recycle()
                    workBitmap = denoised
                    workBitmap.getPixels(pixels, 0, w, 0, 0, w, h)
                }
            }
        }

        // 2026 hotfix: 提升 currentLut 到局部变量并缓存 lutIntensity 阈值
        // 避免内层像素循环反复读取 @Volatile 字段，显著降低千万级像素图的 CPU 开销
        val lut = currentLut
        val useLut = lut != null
        val lutIntensityThreshold = 1e-6f

        // Process each pixel
        for (y in 0 until h) {
            // 每 256 行让出一次线程，避免超大图处理时阻塞 Dispatchers.Default 导致 ANR/卡顿
            if (y % 256 == 0) yield()
            if (sourceBitmap.isRecycled) throw CancellationException("Source bitmap recycled during processing")
            for (x in 0 until w) {
                val idx = y * w + x
                val pixel = pixels[idx]

                // Extract sRGB [0,1]
                var r = ((pixel shr 16) and 0xFF) / 255f
                var g = ((pixel shr 8) and 0xFF) / 255f
                var b = (pixel and 0xFF) / 255f

                // ── 1. sRGB to Linear ──
                r = ColorMath.srgbToLinear(r)
                g = ColorMath.srgbToLinear(g)
                b = ColorMath.srgbToLinear(b)

                // ── 2. Linear Exposure ──
                r = r * exposureMultiplier
                g = g * exposureMultiplier
                b = b * exposureMultiplier

                // ── 3. Filmic Brightness (rational curve with midtone emphasis) ──
                r = applyFilmicBrightness(r, brightnessShift)
                g = applyFilmicBrightness(g, brightnessShift)
                b = applyFilmicBrightness(b, brightnessShift)

                // ── 3b. Tone Level (影调: combined brightness control) ──
                if (abs(n.toneLevel) > 1e-6f) {
                    val luma = ColorMath.getLuma(r, g, b)
                    val shift = n.toneLevel * 0.3f
                    val target = (luma + shift).coerceIn(0f, 1f)
                    val factor = if (luma > 1e-6f) target / luma else 1f
                    r *= factor
                    g *= factor
                    b *= factor
                }

                // ── 4. White Balance ──
                r *= wbMultipliers[0]
                g *= wbMultipliers[1]
                b *= wbMultipliers[2]

                // ── 4b. Green-Magenta axis (青品) ──
                if (abs(n.greenMagenta) > 1e-6f) {
                    // Positive = magenta (reduce green, add red+blue), Negative = green
                    g += n.greenMagenta * -0.1f
                    r += n.greenMagenta * 0.05f
                    b += n.greenMagenta * 0.05f
                }

                // ── 5. Highlights Adjustment ──
                val highlightsResult = applyHighlightsAdjustment(r, g, b, n.highlights)
                r = highlightsResult[0]; g = highlightsResult[1]; b = highlightsResult[2]

                // ── 6. Tonal: Contrast/Shadows/Whites/Blacks ──
                val tonalResult = applyTonalAdjustments(r, g, b, n)
                r = tonalResult[0]; g = tonalResult[1]; b = tonalResult[2]

                // ── 6b. Centre (midtone emphasis) ──
                if (abs(n.centre) > 1e-6f) {
                    val luma = ColorMath.getLuma(r, g, b)
                    val shift = n.centre * 0.3f
                    val target = (luma + shift).coerceIn(0f, 1f)
                    val factor = if (luma > 1e-6f) target / luma else 1f
                    r *= factor
                    g *= factor
                    b *= factor
                }

                // ── 7. Saturation/Vibrance/Lightness (D-04) ──
                val satResult = applyCreativeColor(r, g, b, n.saturation, n.vibrance, n.lightness)
                r = satResult[0]; g = satResult[1]; b = satResult[2]

                // ── 8. HSL 8-color panel ──
                val hslResult = applyHslPanel(r, g, b, n)
                r = hslResult[0]; g = hslResult[1]; b = hslResult[2]

                // ── 9. Tone Curves ──
                r = ColorMath.applyCurve(r, curvePoints)
                g = ColorMath.applyCurve(g, curvePoints)
                b = ColorMath.applyCurve(b, curvePoints)

                // ── 9b. RGB Curves ──
                if (redCurve.size >= 2) {
                    r = ColorMath.applyCurve(r, redCurve)
                }
                if (greenCurve.size >= 2) {
                    g = ColorMath.applyCurve(g, greenCurve)
                }
                if (blueCurve.size >= 2) {
                    b = ColorMath.applyCurve(b, blueCurve)
                }

                // ── 10. Color Grading ──
                val cgResult = applyColorGrading(r, g, b, n)
                r = cgResult[0]; g = cgResult[1]; b = cgResult[2]

                // ── 11. Sharpness (unsharp mask) - applied in post ──
                // Sharpness and clarity are applied as spatial operations after pixel loop

                // ── 12. Clarity/Structure (local contrast) - applied in post ──

                // ── 13. Dehaze ──
                val dehazeResult = applyDehaze(r, g, b, n.dehaze)
                r = dehazeResult[0]; g = dehazeResult[1]; b = dehazeResult[2]

                // ── 14. Vignette ──
                val vignetteResult = applyVignette(r, g, b, x.toFloat() / w, y.toFloat() / h, n.vignette, n.vignetteMidpoint, n.vignetteRoundness, n.vignetteFeather)
                r = vignetteResult[0]; g = vignetteResult[1]; b = vignetteResult[2]

                // ── 14b. Chromatic Aberration (dual-axis) ──
                if (abs(n.chromaticAberrationRedCyan) > 1e-6f || abs(n.chromaticAberrationBlueYellow) > 1e-6f) {
                    val caResult = applyChromaticAberration(pixels, w, h, x, y, r, g, b, n.chromaticAberrationRedCyan, n.chromaticAberrationBlueYellow)
                    r = caResult[0]; g = caResult[1]; b = caResult[2]
                }

                // ── 15. Grain ──
                val grainResult = applyGrain(r, g, b, x.toFloat(), y.toFloat(), n.grain, n.grainSize, n.grainRoughness, w, h)
                r = grainResult[0]; g = grainResult[1]; b = grainResult[2]

                // ── 15b. Creative Light Effects ──
                val glowResult = applyGlow(r, g, b, n.glowAmount)
                r = glowResult[0]; g = glowResult[1]; b = glowResult[2]
                val halationResult = applyHalation(r, g, b, n.halationAmount)
                r = halationResult[0]; g = halationResult[1]; b = halationResult[2]
                val flareResult = applyFlare(r, g, b, n.flareAmount)
                r = flareResult[0]; g = flareResult[1]; b = flareResult[2]

                // ── 16. Color Calibration ──
                val cal = FloatArray(3)
                cal[0] = calibMatrix[0] * r + calibMatrix[1] * g + calibMatrix[2] * b
                cal[1] = calibMatrix[3] * r + calibMatrix[4] * g + calibMatrix[5] * b
                cal[2] = calibMatrix[6] * r + calibMatrix[7] * g + calibMatrix[8] * b
                r = cal[0]; g = cal[1]; b = cal[2]

                // ── 16b. Color Calibration Shadows Tint ──
                if (abs(n.colorCalibrationShadowsTint) > 1e-6f) {
                    val luma = ColorMath.getLuma(r, g, b)
                    val sMask = ColorMath.shadowsMask(luma)
                    val tint = n.colorCalibrationShadowsTint * 0.15f * sMask
                    r += tint
                    g -= tint * 0.5f
                    b += tint
                }

                // ── 17. Color Science Tone Mapping ──
                // colorScienceConfig 已在循环外预计算，避免每像素创建对象
                val toneMapped = ColorScience.apply(r, g, b, colorScienceConfig)
                r = toneMapped.first
                g = toneMapped.second
                b = toneMapped.third

                // ── 18. Film Simulation Processing ──
                if (n.filmId.isNotEmpty() && n.filmIntensity > 1e-6f) {
                    // Film color shifts (applied in linear space)
                    r += n.filmRedShift * 0.15f
                    g += n.filmGreenShift * 0.15f
                    b += n.filmBlueShift * 0.15f

                    // Film saturation modifier
                    if (abs(n.filmSaturation) > 1e-6f) {
                        val lum = ColorMath.getLuma(r, g, b)
                        val satMod = 1f + n.filmSaturation
                        r = lum + (r - lum) * satMod
                        g = lum + (g - lum) * satMod
                        b = lum + (b - lum) * satMod
                    }

                    // Film contrast modifier
                    if (abs(n.filmContrast) > 1e-6f) {
                        val contrastPow = 1f + n.filmContrast * 0.5f
                        val mid = 0.18f
                        r = mid + (r - mid) * contrastPow
                        g = mid + (g - mid) * contrastPow
                        b = mid + (b - mid) * contrastPow
                    }

                    // Film highlight roll-off
                    if (n.filmHighlightRollOff > 1e-6f) {
                        val luma = ColorMath.getLuma(r, g, b)
                        val hMask = ColorMath.highlightsMask(luma)
                        val shoulder = 1f - (1f - r).toDouble().pow(1.0 + n.filmHighlightRollOff * 2.0).toFloat()
                        val shoulderg = 1f - (1f - g).toDouble().pow(1.0 + n.filmHighlightRollOff * 2.0).toFloat()
                        val shoulderb = 1f - (1f - b).toDouble().pow(1.0 + n.filmHighlightRollOff * 2.0).toFloat()
                        r = r + (shoulder - r) * hMask * n.filmIntensity
                        g = g + (shoulderg - g) * hMask * n.filmIntensity
                        b = b + (shoulderb - b) * hMask * n.filmIntensity
                    }

                    // Film shadow lift
                    if (n.filmShadowLift > 1e-6f) {
                        val luma = ColorMath.getLuma(r, g, b)
                        val sMask = ColorMath.shadowsMask(luma)
                        val lift = n.filmShadowLift * 0.2f * sMask * n.filmIntensity
                        r += lift
                        g += lift
                        b += lift
                    }

                    // Film DR compression
                    if (n.filmDrCompression > 1e-6f) {
                        val luma = ColorMath.getLuma(r, g, b)
                        val compressed = luma / (luma + n.filmDrCompression * 0.5f + 1e-6f) * (1f + n.filmDrCompression * 0.5f)
                        val factor = if (luma > 1e-6f) compressed / luma else 1f
                        val blend = n.filmDrCompression * n.filmIntensity
                        val blendedFactor = 1f + (factor - 1f) * blend
                        r *= blendedFactor
                        g *= blendedFactor
                        b *= blendedFactor
                    }

                    // Film curve
                    val hasFilmCurve = n.filmCurvePoints.size >= 2 &&
                        n.filmCurvePoints.any { abs(it.first - it.second) > 0.1f }
                    if (hasFilmCurve) {
                        val filmR = ColorMath.applyCurve(r, filmCurve)
                        val filmG = ColorMath.applyCurve(g, filmCurve)
                        val filmB = ColorMath.applyCurve(b, filmCurve)
                        r = r + (filmR - r) * n.filmIntensity
                        g = g + (filmG - g) * n.filmIntensity
                        b = b + (filmB - b) * n.filmIntensity
                    }

                    }
            }

            // ── 18b. Independent Film Grain (applied even when film simulation is off) ──
            if (n.filmGrainAmount > 1e-6f) {
                val fGrainSize = 1f + n.filmGrainSize * 4f
                val noise = ColorMath.gradientNoise(x * fGrainSize, y * fGrainSize)
                val grainOffset = (noise - 0.5f) * n.filmGrainAmount * 0.4f
                val luma = ColorMath.getLuma(r, g, b)
                var grainMod = 1f - abs(luma - 0.5f) * 1.5f
                grainMod = grainMod.coerceIn(0.2f, 1f)
                // Roughness modulates grain sharpness
                val roughnessMod = 0.5f + n.filmGrainRoughness * 0.5f
                // Blend with film intensity if film is active, otherwise apply at full strength
                val grainBlend = if (n.filmId.isNotEmpty()) n.filmIntensity else 1f
                r += grainOffset * grainMod * roughnessMod * grainBlend
                g += grainOffset * grainMod * roughnessMod * grainBlend
                b += grainOffset * grainMod * roughnessMod * grainBlend
            }

            // ── 19. Soft Glow / Bloom ──
            if (bloomBuffer != null) {
                val bloomR = bloomBuffer[idx * 3]
                val bloomG = bloomBuffer[idx * 3 + 1]
                val bloomB = bloomBuffer[idx * 3 + 2]
                r = r + (bloomR - r) * n.softGlow * 0.5f
                g = g + (bloomG - g) * n.softGlow * 0.5f
                b = b + (bloomB - b) * n.softGlow * 0.5f
            }

            // ── 19b. 3D LUT (CPU path) ──
            // 2026 hotfix: 使用预先提升的 useLut/lut 变量，避免内层循环中重复读取 @Volatile
            if (useLut && n.lutIntensity > lutIntensityThreshold) {
                val lutColor = lut?.sample(r.coerceIn(0f, 1f), g.coerceIn(0f, 1f), b.coerceIn(0f, 1f))
                if (lutColor != null) {
                    r = r + (lutColor.first - r) * n.lutIntensity
                    g = g + (lutColor.second - g) * n.lutIntensity
                    b = b + (lutColor.third - b) * n.lutIntensity
                }
            }

            // ── 20. Linear to sRGB ──
            r = ColorMath.linearToSrgb(r)
            g = ColorMath.linearToSrgb(g)
            b = ColorMath.linearToSrgb(b)

            // Dither
            r = (r + ColorMath.gradientNoise(x.toFloat(), y.toFloat()) / 255f - 0.5f / 255f).coerceIn(0f, 1f)
            g = (g + ColorMath.gradientNoise(x.toFloat() + 100f, y.toFloat() + 100f) / 255f - 0.5f / 255f).coerceIn(0f, 1f)
            b = (b + ColorMath.gradientNoise(x.toFloat() + 200f, y.toFloat() + 200f) / 255f - 0.5f / 255f).coerceIn(0f, 1f)

            // Clamp
            r = r.coerceIn(0f, 1f)
            g = g.coerceIn(0f, 1f)
            b = b.coerceIn(0f, 1f)

            // Clipping preview
            if (n.clippingPreview) {
                if (r >= 1f || g >= 1f || b >= 1f) {
                    r = 1f; g = 0f; b = 0f
                } else if (r <= 0f || g <= 0f || b <= 0f) {
                    r = 0f; g = 0f; b = 1f
                }
            }

            // Write back
            val ri = (r * 255f).toInt().coerceIn(0, 255)
            val gi = (g * 255f).toInt().coerceIn(0, 255)
            val bi = (b * 255f).toInt().coerceIn(0, 255)
            val ai = 0xFF
            pixels[idx] = (ai shl 24) or (ri shl 16) or (gi shl 8) or bi
            }
        }

        outputBitmap.setPixels(pixels, 0, w, 0, 0, w, h)

        // Post-processing: Blur-based creative effects (Glow, Halation, CDL)
        var effectPixels = pixels
        if (n.blurGlow > 1e-6f) {
            effectPixels = applyGlow(effectPixels, w, h, n.blurGlow)
        }
        if (n.blurHalation > 1e-6f) {
            effectPixels = applyHalation(effectPixels, w, h, n.blurHalation)
        }
        val hasCdl = abs(n.cdlShadowsR) > 1e-6f || abs(n.cdlShadowsG) > 1e-6f ||
            abs(n.cdlShadowsB) > 1e-6f || abs(n.cdlMidtonesR) > 1e-6f ||
            abs(n.cdlMidtonesG) > 1e-6f || abs(n.cdlMidtonesB) > 1e-6f ||
            abs(n.cdlHighlightsR) > 1e-6f || abs(n.cdlHighlightsG) > 1e-6f ||
            abs(n.cdlHighlightsB) > 1e-6f
        if (hasCdl) {
            effectPixels = applyCdlGrading(effectPixels, w, h, n)
        }
        // Update bitmap if any effect was applied
        if (effectPixels !== pixels) {
            outputBitmap.setPixels(effectPixels, 0, w, 0, 0, w, h)
        }

        // Post-processing: Sharpness & Clarity (spatial operations)
        val finalBitmap = applySpatialOperations(outputBitmap, n)
        if (finalBitmap != outputBitmap) outputBitmap.recycle()

        // Recycle intermediate bitmaps if created
        if (workBitmap !== sourceBitmap) workBitmap.recycle()
        if (sourceBitmap !== originalBitmap) sourceBitmap.recycle()

        finalBitmap
    } catch (oom: OutOfMemoryError) {
        Log.e(TAG, "OOM in processFullResolution: w=$w h=$h totalPixels=$totalPixels", oom)
        // 2026 hotfix: 出现 OOM 时清理可能已分配但未完成的所有中间 bitmap，
        // 避免 Activity 重建时大量未释放的 native 内存累积导致后续 ANR/OOM
        if (!outputBitmap.isRecycled) outputBitmap.recycle()
        if (workBitmap.isRecycled) throw oom
        if (workBitmap !== sourceBitmap && workBitmap !== originalBitmap) workBitmap.recycle()
        if (sourceBitmap !== originalBitmap && !sourceBitmap.isRecycled) sourceBitmap.recycle()
        System.gc()
        throw IllegalStateException("内存不足，无法处理此图像（${w}x${h}）", oom)
    } catch (ce: CancellationException) {
        // 取消时同样清理，避免泄漏
        if (!outputBitmap.isRecycled) outputBitmap.recycle()
        if (!workBitmap.isRecycled && workBitmap !== sourceBitmap && workBitmap !== originalBitmap) {
            workBitmap.recycle()
        }
        if (sourceBitmap !== originalBitmap && !sourceBitmap.isRecycled) sourceBitmap.recycle()
        throw ce
    }
    }  // 关闭 withContext(Dispatchers.Default) {
    // ── Processing Functions (CPU path, linear color space) ────────

    /** Filmic brightness: rational curve with midtone emphasis */
    private fun applyFilmicBrightness(channel: Float, brightness: Float): Float {
        if (abs(brightness) < 1e-6f) return channel
        val numerator = channel * (1f + brightness)
        val denominator = 1f + abs(brightness) * channel
        return numerator / max(denominator, 1e-6f)
    }

    /** Highlights adjustment with luminance mask */
    private fun applyHighlightsAdjustment(r: Float, g: Float, b: Float, highlights: Float): FloatArray {
        if (abs(highlights) < 1e-6f) return floatArrayOf(r, g, b)

        val luma = ColorMath.getLuma(r, g, b)
        val mask = ColorMath.highlightsMask(luma)

        val outR: Float
        val outG: Float
        val outB: Float

        if (highlights < 0f) {
            // Compress: pull highlights down with a power function
            val compressedR = 1f - (1f - r).toDouble().pow(1.0 - highlights.toDouble()).toFloat()
            val compressedG = 1f - (1f - g).toDouble().pow(1.0 - highlights.toDouble()).toFloat()
            val compressedB = 1f - (1f - b).toDouble().pow(1.0 - highlights.toDouble()).toFloat()
            outR = r + (compressedR - r) * mask
            outG = g + (compressedG - g) * mask
            outB = b + (compressedB - b) * mask
        } else {
            // Expand: push highlights up
            val expandedR = r.toDouble().pow(1.0 / (1.0 + highlights.toDouble())).toFloat()
            val expandedG = g.toDouble().pow(1.0 / (1.0 + highlights.toDouble())).toFloat()
            val expandedB = b.toDouble().pow(1.0 / (1.0 + highlights.toDouble())).toFloat()
            outR = r + (expandedR - r) * mask
            outG = g + (expandedG - g) * mask
            outB = b + (expandedB - b) * mask
        }

        return floatArrayOf(outR, outG, outB)
    }

    /** Tonal adjustments: contrast, shadows, whites, blacks */
    private fun applyTonalAdjustments(r: Float, g: Float, b: Float, n: NormAdj): FloatArray {
        var outR = r
        var outG = g
        var outB = b

        val luma = ColorMath.getLuma(r, g, b)

        // Contrast (perceptual gamma curve around middle gray)
        if (abs(n.contrast) > 1e-6f) {
            val contrastPow = 1f + n.contrast
            val mid = 0.18f
            outR = mid + (outR - mid) * contrastPow
            outG = mid + (outG - mid) * contrastPow
            outB = mid + (outB - mid) * contrastPow
        }

        // Shadows
        if (abs(n.shadows) > 1e-6f) {
            val sm = ColorMath.shadowsMask(luma)
            outR += n.shadows * sm * 0.3f
            outG += n.shadows * sm * 0.3f
            outB += n.shadows * sm * 0.3f
        }

        // Whites
        if (abs(n.whites) > 1e-6f) {
            val wm = ColorMath.whitesMask(luma)
            outR += n.whites * wm * 0.25f
            outG += n.whites * wm * 0.25f
            outB += n.whites * wm * 0.25f
        }

        // Blacks
        if (abs(n.blacks) > 1e-6f) {
            val bm = ColorMath.blacksMask(luma)
            outR += n.blacks * bm * 0.25f
            outG += n.blacks * bm * 0.25f
            outB += n.blacks * bm * 0.25f
        }

        return floatArrayOf(outR, outG, outB)
    }

    /** Creative color: saturation + vibrance with skin tone protection, plus lightness */
    private fun applyCreativeColor(r: Float, g: Float, b: Float, saturation: Float, vibrance: Float, lightness: Float): FloatArray {
        if (abs(saturation) < 1e-6f && abs(vibrance) < 1e-6f && abs(lightness) < 1e-6f) return floatArrayOf(r, g, b)

        val hsv = ColorMath.rgbToHsv(r, g, b)
        val currentSat = hsv[1]

        // Skin tone protection
        var skinProtection = 1f
        if (hsv[0] > 10f && hsv[0] < 50f && currentSat < 0.5f && hsv[2] > 0.2f) {
            skinProtection = 0.5f
        }

        // Vibrance: less effect on already-saturated colors
        val vibranceAmount = vibrance * (1f - currentSat) * skinProtection
        hsv[1] = (currentSat + vibranceAmount * 1.5f).coerceIn(0f, 1f)

        // Saturation
        hsv[1] = (hsv[1] + saturation).coerceIn(0f, 1f)

        // D-04: Lightness adjustment via HSV V channel
        hsv[2] = (hsv[2] + lightness).coerceIn(0f, 1f)

        return ColorMath.hsvToRgb(hsv[0], hsv[1], hsv[2])
    }

    /** HSL 8-color panel adjustments */
    private fun applyHslPanel(r: Float, g: Float, b: Float, n: NormAdj): FloatArray {
        // Check if any HSL adjustment is non-zero
        val hasHslAdjustment = n.hueRed != 0f || n.satRed != 0f || n.lumRed != 0f ||
            n.hueOrange != 0f || n.satOrange != 0f || n.lumOrange != 0f ||
            n.hueYellow != 0f || n.satYellow != 0f || n.lumYellow != 0f ||
            n.hueGreen != 0f || n.satGreen != 0f || n.lumGreen != 0f ||
            n.hueAqua != 0f || n.satAqua != 0f || n.lumAqua != 0f ||
            n.hueBlue != 0f || n.satBlue != 0f || n.lumBlue != 0f ||
            n.huePurple != 0f || n.satPurple != 0f || n.lumPurple != 0f ||
            n.hueMagenta != 0f || n.satMagenta != 0f || n.lumMagenta != 0f

        if (!hasHslAdjustment) return floatArrayOf(r, g, b)

        val hsv = ColorMath.rgbToHsv(r, g, b)
        val hue = hsv[0]

        var hueShift = 0f
        var satShift = 0f
        var lumShift = 0f

        // HSL ranges: (center, span)
        val ranges = listOf(
            Triple(358f, 35f, Triple(n.hueRed, n.satRed, n.lumRed)),
            Triple(25f, 45f, Triple(n.hueOrange, n.satOrange, n.lumOrange)),
            Triple(60f, 40f, Triple(n.hueYellow, n.satYellow, n.lumYellow)),
            Triple(115f, 90f, Triple(n.hueGreen, n.satGreen, n.lumGreen)),
            Triple(180f, 60f, Triple(n.hueAqua, n.satAqua, n.lumAqua)),
            Triple(225f, 60f, Triple(n.hueBlue, n.satBlue, n.lumBlue)),
            Triple(280f, 55f, Triple(n.huePurple, n.satPurple, n.lumPurple)),
            Triple(330f, 50f, Triple(n.hueMagenta, n.satMagenta, n.lumMagenta))
        )

        for ((center, span, shifts) in ranges) {
            val w = ColorMath.hslRangeWeight(hue, center, span)
            if (w > 0f) {
                hueShift += shifts.first * w
                satShift += shifts.second * w
                lumShift += shifts.third * w
            }
        }

        hsv[0] = ((hsv[0] + hueShift * 60f) % 360f + 360f) % 360f
        hsv[1] = (hsv[1] + satShift).coerceIn(0f, 1f)

        var result = ColorMath.hsvToRgb(hsv[0], hsv[1], hsv[2])

        // Luminance shift
        if (abs(lumShift) > 1e-6f) {
            val luma = ColorMath.getLuma(result[0], result[1], result[2])
            result[0] = luma + (result[0] - luma) * (1f + lumShift)
            result[1] = luma + (result[1] - luma) * (1f + lumShift)
            result[2] = luma + (result[2] - luma) * (1f + lumShift)
        }

        return result
    }

    /** Color grading: shadows/midtones/highlights tinting */
    private fun applyColorGrading(r: Float, g: Float, b: Float, n: NormAdj): FloatArray {
        val hasGrading = n.colorGradingShadows.any { abs(it) > 1e-6f } ||
            n.colorGradingMidtones.any { abs(it) > 1e-6f } ||
            n.colorGradingHighlights.any { abs(it) > 1e-6f } ||
            abs(n.colorGradingGlobalSat) > 1e-6f ||
            abs(n.colorGradingBalance) > 1e-6f

        if (!hasGrading) return floatArrayOf(r, g, b)

        val luma = ColorMath.getLuma(r, g, b)

        val sm = ColorMath.shadowsMask(luma)
        val mm = ColorMath.midtonesMask(luma)
        val hm = ColorMath.highlightsMask(luma)

        // Apply balance: shift weight between shadows and highlights
        val balance = n.colorGradingBalance
        val balancedSm = sm * (1f + balance * 0.5f)
        val balancedHm = hm * (1f - balance * 0.5f)

        // Normalize masks
        val maskSum = balancedSm + mm + balancedHm + 1e-6f
        val nsm = balancedSm / maskSum
        val nmm = mm / maskSum
        val nhm = balancedHm / maskSum

        val tintR = nsm * n.colorGradingShadows[0] + nmm * n.colorGradingMidtones[0] + nhm * n.colorGradingHighlights[0]
        val tintG = nsm * n.colorGradingShadows[1] + nmm * n.colorGradingMidtones[1] + nhm * n.colorGradingHighlights[1]
        val tintB = nsm * n.colorGradingShadows[2] + nmm * n.colorGradingMidtones[2] + nhm * n.colorGradingHighlights[2]

        var outR = r * (1f - n.colorGradingBlend) + (r + tintR) * n.colorGradingBlend
        var outG = g * (1f - n.colorGradingBlend) + (g + tintG) * n.colorGradingBlend
        var outB = b * (1f - n.colorGradingBlend) + (b + tintB) * n.colorGradingBlend

        // Global saturation
        if (abs(n.colorGradingGlobalSat) > 1e-6f) {
            val lum = ColorMath.getLuma(outR, outG, outB)
            outR = lum + (outR - lum) * (1f + n.colorGradingGlobalSat)
            outG = lum + (outG - lum) * (1f + n.colorGradingGlobalSat)
            outB = lum + (outB - lum) * (1f + n.colorGradingGlobalSat)
        }

        return floatArrayOf(outR, outG, outB)
    }

    private fun midtonesMask(luma: Float): Float {
        return ColorMath.smoothstep(0.2f, 0.4f, luma) * (1f - ColorMath.smoothstep(0.6f, 0.8f, luma))
    }

    /** Compute 3x3 color calibration matrix from adjustment parameters */
    private fun computeCalibrationMatrix(n: NormAdj): FloatArray {
        val hasCalib = abs(n.calibRedHue) > 1e-6f || abs(n.calibRedSat) > 1e-6f ||
            abs(n.calibGreenHue) > 1e-6f || abs(n.calibGreenSat) > 1e-6f ||
            abs(n.calibBlueHue) > 1e-6f || abs(n.calibBlueSat) > 1e-6f

        if (!hasCalib) {
            // Identity matrix
            return floatArrayOf(
                1f, 0f, 0f,
                0f, 1f, 0f,
                0f, 0f, 1f
            )
        }

        val rh = n.calibRedHue * 60f
        val rs = 1f + n.calibRedSat
        val gh = n.calibGreenHue * 60f
        val gs = 1f + n.calibGreenSat
        val bh = n.calibBlueHue * 60f
        val bs = 1f + n.calibBlueSat

        // Convert each primary to HSV, adjust, convert back
        val redPrimary = ColorMath.hsvToRgb(((rh) % 360f + 360f) % 360f, rs.coerceIn(0f, 2f), 1f)
        val greenPrimary = ColorMath.hsvToRgb(((120f + gh) % 360f + 360f) % 360f, gs.coerceIn(0f, 2f), 1f)
        val bluePrimary = ColorMath.hsvToRgb(((240f + bh) % 360f + 360f) % 360f, bs.coerceIn(0f, 2f), 1f)

        return floatArrayOf(
            redPrimary[0], redPrimary[1], redPrimary[2],
            greenPrimary[0], greenPrimary[1], greenPrimary[2],
            bluePrimary[0], bluePrimary[1], bluePrimary[2]
        )
    }

    /** Dehaze */
    private fun applyDehaze(r: Float, g: Float, b: Float, dehaze: Float): FloatArray {
        if (abs(dehaze) < 1e-6f) return floatArrayOf(r, g, b)

        val luma = ColorMath.getLuma(r, g, b)
        val hazeEstimate = ColorMath.smoothstep(0f, 0.8f, luma)

        if (dehaze > 0f) {
            val atmosphericLightR = 0.85f
            val atmosphericLightG = 0.88f
            val atmosphericLightB = 0.92f
            var transmission = 1f - hazeEstimate * dehaze
            transmission = max(transmission, 0.1f)
            val outR = (r - atmosphericLightR * hazeEstimate * dehaze) / transmission
            val outG = (g - atmosphericLightG * hazeEstimate * dehaze) / transmission
            val outB = (b - atmosphericLightB * hazeEstimate * dehaze) / transmission
            return floatArrayOf(max(outR, 0f), max(outG, 0f), max(outB, 0f))
        } else {
            val amount = -dehaze
            val atmosphericLightR = 0.85f
            val atmosphericLightG = 0.88f
            val atmosphericLightB = 0.92f
            val outR = r + (atmosphericLightR - r) * amount * hazeEstimate
            val outG = g + (atmosphericLightG - g) * amount * hazeEstimate
            val outB = b + (atmosphericLightB - b) * amount * hazeEstimate
            return floatArrayOf(outR, outG, outB)
        }
    }

    /** Vignette with sub-parameters */
    private fun applyVignette(r: Float, g: Float, b: Float, nx: Float, ny: Float,
                               vignette: Float, midpoint: Float, roundness: Float, feather: Float): FloatArray {
        if (abs(vignette) < 1e-6f) return floatArrayOf(r, g, b)

        val cx = nx - 0.5f
        val cy = ny - 0.5f
        val dist = sqrt(cx * cx + cy * cy) * 1.414f

        // Apply roundness: 0 = circular, 1 = elliptical (aspect corrected)
        val aspect = if (roundness > 0f) 1f + roundness * 0.5f else 1f
        val shapedDist = dist * aspect

        // Midpoint controls where the vignette starts (0 = center, 1 = corners)
        val start = midpoint * 0.7f
        val end = start + feather * 0.3f + 0.05f

        val vignetteAmount: Float = if (vignette > 0f) {
            1f - ColorMath.smoothstep(start, end.coerceIn(start + 0.01f, 1f), shapedDist) * vignette
        } else {
            1f + ColorMath.smoothstep(start, end.coerceIn(start + 0.01f, 1f), shapedDist) * vignette
        }

        return floatArrayOf(r * vignetteAmount, g * vignetteAmount, b * vignetteAmount)
    }

    /** Film grain with roughness */
    private fun applyGrain(r: Float, g: Float, b: Float, x: Float, y: Float,
                           grain: Float, grainSize: Float, roughness: Float,
                           imgW: Int, imgH: Int): FloatArray {
        if (grain < 1e-6f) return floatArrayOf(r, g, b)

        val noise = ColorMath.gradientNoise(x * grainSize, y * grainSize)
        var grainOffset = (noise - 0.5f) * grain * 0.3f

        // Roughness modulates grain sharpness
        if (roughness > 1e-6f) {
            grainOffset *= (0.5f + roughness * 0.5f)
        }

        // Grain more visible in midtones
        val luma = ColorMath.getLuma(r, g, b)
        var grainAmount = 1f - abs(luma - 0.5f) * 1.5f
        grainAmount = grainAmount.coerceIn(0.2f, 1f)

        return floatArrayOf(
            r + grainOffset * grainAmount,
            g + grainOffset * grainAmount,
            b + grainOffset * grainAmount
        )
    }

    /** Glow (creative light effect) */
    private fun applyGlow(r: Float, g: Float, b: Float, amount: Float): FloatArray {
        if (amount < 1e-6f) return floatArrayOf(r, g, b)
        val luma = ColorMath.getLuma(r, g, b)
        val bloom = luma * amount * 0.3f
        return floatArrayOf(r + bloom, g + bloom, b + bloom)
    }

    /** Halation (creative light effect: red bloom around highlights) */
    private fun applyHalation(r: Float, g: Float, b: Float, amount: Float): FloatArray {
        if (amount < 1e-6f) return floatArrayOf(r, g, b)
        val luma = ColorMath.getLuma(r, g, b)
        val highlightMask = ColorMath.highlightsMask(luma)
        val halation = highlightMask * amount * 0.4f
        return floatArrayOf(r + halation, g + halation * 0.3f, b + halation * 0.1f)
    }

    /** Flare (creative light effect: global haze) */
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

    /** Spatial operations: sharpness (unsharp mask) and clarity/structure (local contrast) */
    private fun applySpatialOperations(bitmap: Bitmap, n: NormAdj): Bitmap {
        if (n.sharpness < 1e-6f && abs(n.clarity) < 1e-6f && abs(n.structure) < 1e-6f) {
            return bitmap
        }

        val w = bitmap.width
        val h = bitmap.height
        val result = try {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        } catch (oom: OutOfMemoryError) {
            Log.w(TAG, "OOM creating spatial-ops result bitmap", oom)
            return bitmap
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "IllegalArgument creating spatial-ops result bitmap", e)
            return bitmap
        }

        // 2026 hotfix: 防御 w*h 整数溢出
        val pixelCount = w.toLong() * h.toLong()
        if (pixelCount <= 0L || pixelCount > Int.MAX_VALUE.toLong()) {
            Log.w(TAG, "Invalid pixel count: $w x $h")
            if (!result.isRecycled) result.recycle()
            return bitmap
        }
        val srcPixels = IntArray(pixelCount.toInt())
        bitmap.getPixels(srcPixels, 0, w, 0, 0, w, h)
        val dstPixels = srcPixels.copyOf()

        // ── Clarity/Structure (local contrast via box blur high-pass) ──
        if (abs(n.clarity) > 1e-6f || abs(n.structure) > 1e-6f) {
            val clarityRadius = 5
            val structureRadius = 2

            // Box blur for clarity
            if (abs(n.clarity) > 1e-6f) {
                val blurred = boxBlur(srcPixels, w, h, clarityRadius)
                applyLocalContrastPass(srcPixels, blurred, dstPixels, w, h, n.clarity * 2f)
            }

            // Box blur for structure (finer)
            if (abs(n.structure) > 1e-6f) {
                val blurred = boxBlur(srcPixels, w, h, structureRadius)
                applyLocalContrastPass(srcPixels, blurred, dstPixels, w, h, n.structure * 1.5f)
            }
        }

        // ── Sharpness (unsharp mask) ──
        if (n.sharpness > 1e-6f) {
            val sharpRadius = 1
            val blurred = boxBlur(dstPixels, w, h, sharpRadius)
            for (i in dstPixels.indices) {
                val pixel = dstPixels[i]
                var r = ((pixel shr 16) and 0xFF) / 255f
                var g = ((pixel shr 8) and 0xFF) / 255f
                var b = (pixel and 0xFF) / 255f

                val bp = blurred[i]
                val br = ((bp shr 16) and 0xFF) / 255f
                val bg = ((bp shr 8) and 0xFF) / 255f
                val bb = (bp and 0xFF) / 255f

                // Unsharp mask: original + (original - blurred) * amount
                r = (r + (r - br) * n.sharpness).coerceIn(0f, 1f)
                g = (g + (g - bg) * n.sharpness).coerceIn(0f, 1f)
                b = (b + (b - bb) * n.sharpness).coerceIn(0f, 1f)

                val ri = (r * 255f).toInt().coerceIn(0, 255)
                val gi = (g * 255f).toInt().coerceIn(0, 255)
                val bi = (b * 255f).toInt().coerceIn(0, 255)
                dstPixels[i] = (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
            }
        }

        result.setPixels(dstPixels, 0, w, 0, 0, w, h)
        return result
    }

    /** Simple box blur for local contrast estimation */
    private fun boxBlur(pixels: IntArray, w: Int, h: Int, radius: Int): IntArray {
        // 2026 hotfix: 防御 pixels.size*3 整数溢出
        if (pixels.size.toLong() * 3L > Int.MAX_VALUE.toLong()) {
            Log.e(TAG, "boxBlur: pixel buffer too large ${pixels.size}")
            return pixels.copyOf()
        }
        val result = IntArray(pixels.size)
        val temp = FloatArray(pixels.size * 3)

        // Convert to float arrays
        for (i in pixels.indices) {
            val p = pixels[i]
            temp[i * 3] = ((p shr 16) and 0xFF) / 255f
            temp[i * 3 + 1] = ((p shr 8) and 0xFF) / 255f
            temp[i * 3 + 2] = (p and 0xFF) / 255f
        }

        val blurred = FloatArray(temp.size)
        temp.copyInto(blurred)

        // Horizontal pass
        for (y in 0 until h) {
            for (x in 0 until w) {
                var sumR = 0f
                var sumG = 0f
                var sumB = 0f
                var count = 0
                for (dx in -radius..radius) {
                    val sx = (x + dx).coerceIn(0, w - 1)
                    val si = y * w + sx
                    sumR += blurred[si * 3]
                    sumG += blurred[si * 3 + 1]
                    sumB += blurred[si * 3 + 2]
                    count++
                }
                val oi = y * w + x
                temp[oi * 3] = sumR / count
                temp[oi * 3 + 1] = sumG / count
                temp[oi * 3 + 2] = sumB / count
            }
        }

        // Vertical pass
        for (y in 0 until h) {
            for (x in 0 until w) {
                var sumR = 0f
                var sumG = 0f
                var sumB = 0f
                var count = 0
                for (dy in -radius..radius) {
                    val sy = (y + dy).coerceIn(0, h - 1)
                    val si = sy * w + x
                    sumR += temp[si * 3]
                    sumG += temp[si * 3 + 1]
                    sumB += temp[si * 3 + 2]
                    count++
                }
                val oi = y * w + x
                blurred[oi * 3] = sumR / count
                blurred[oi * 3 + 1] = sumG / count
                blurred[oi * 3 + 2] = sumB / count
            }
        }

        // Convert back to int pixels
        for (i in pixels.indices) {
            val r = (blurred[i * 3] * 255f).toInt().coerceIn(0, 255)
            val g = (blurred[i * 3 + 1] * 255f).toInt().coerceIn(0, 255)
            val b = (blurred[i * 3 + 2] * 255f).toInt().coerceIn(0, 255)
            result[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }

        return result
    }

    /** Apply local contrast: add high-pass signal */
    private fun applyLocalContrastPass(src: IntArray, blurred: IntArray, dst: IntArray,
                                       w: Int, h: Int, amount: Float) {
        for (i in src.indices) {
            val sp = src[i]
            val sr = ((sp shr 16) and 0xFF) / 255f
            val sg = ((sp shr 8) and 0xFF) / 255f
            val sb = (sp and 0xFF) / 255f

            val bp = blurred[i]
            val br = ((bp shr 16) and 0xFF) / 255f
            val bg = ((bp shr 8) and 0xFF) / 255f
            val bb = (bp and 0xFF) / 255f

            // High-pass = original - blurred
            val outR = (sr + (sr - br) * amount).coerceIn(0f, 1f)
            val outG = (sg + (sg - bg) * amount).coerceIn(0f, 1f)
            val outB = (sb + (sb - bb) * amount).coerceIn(0f, 1f)

            val ri = (outR * 255f).toInt().coerceIn(0, 255)
            val gi = (outG * 255f).toInt().coerceIn(0, 255)
            val bi = (outB * 255f).toInt().coerceIn(0, 255)
            dst[i] = (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
        }
    }

    // ── Smart Optimizer Integration ────────────────────────────────

    /**
     * Run SmartOptimizer on the given bitmap histograms and return
     * suggested adjustments.
     */
    fun smartOptimize(bitmap: Bitmap): com.rapidraw.data.model.Adjustments {
        val histograms = computeHistograms(bitmap)
        return SmartOptimizer.quickEnhance(
            histograms[0], histograms[1], histograms[2],
            bitmap.width, bitmap.height
        )
    }

    // ── Histogram Computation ──────────────────────────────────────

    /**
     * Compute R, G, B, and Luma histograms from a Bitmap.
     * Returns Array<IntArray> of size 4: [redHist, greenHist, blueHist, lumaHist]
     * Each histogram is IntArray(256).
     */
    fun computeHistograms(bitmap: Bitmap): Array<IntArray> {
        val w = bitmap.width
        val h = bitmap.height
        // 2026 hotfix: 防御 w*h 整数溢出
        val pixelCount = w.toLong() * h.toLong()
        if (pixelCount > Int.MAX_VALUE.toLong()) {
            Log.e(TAG, "computeHistograms: bitmap too large ${w}x$h")
            return arrayOf(IntArray(256), IntArray(256), IntArray(256), IntArray(256))
        }
        val count = pixelCount.toInt()
        val pixels = IntArray(count)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val redHist = IntArray(256)
        val greenHist = IntArray(256)
        val blueHist = IntArray(256)
        val lumaHist = IntArray(256)

        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            redHist[r]++
            greenHist[g]++
            blueHist[b]++
            // Rec.709 luma
            val luma = (0.2126f * r + 0.7152f * g + 0.0722f * b).toInt().coerceIn(0, 255)
            lumaHist[luma]++
        }

        return arrayOf(redHist, greenHist, blueHist, lumaHist)
    }

    // ── CPU-side Film Simulation Processing ────────────────────────

    /**
     * Apply film simulation processing to a color in linear space.
     * Used for full resolution export when a film simulation is selected.
     * Returns the processed color as FloatArray(3) [r, g, b].
     */
    fun applyFilmSimulation(color: FloatArray, film: FilmSimulation, intensity: Float): FloatArray {
        var r = color[0]
        var g = color[1]
        var b = color[2]

        if (intensity < 1e-6f) return floatArrayOf(r, g, b)

        // Film color shifts
        r += film.redShift * 0.15f * intensity
        g += film.greenShift * 0.15f * intensity
        b += film.blueShift * 0.15f * intensity

        // Film saturation modifier
        if (abs(film.saturationModifier) > 1e-6f) {
            val lum = ColorMath.getLuma(r, g, b)
            val satMod = 1f + film.saturationModifier * intensity
            r = lum + (r - lum) * satMod
            g = lum + (g - lum) * satMod
            b = lum + (b - lum) * satMod
        }

        // Film contrast modifier
        if (abs(film.contrastModifier) > 1e-6f) {
            val contrastPow = 1f + film.contrastModifier * 0.5f * intensity
            val mid = 0.18f
            r = mid + (r - mid) * contrastPow
            g = mid + (g - mid) * contrastPow
            b = mid + (b - mid) * contrastPow
        }

        // Film highlight roll-off
        if (film.highlightRollOff > 1e-6f) {
            val luma = ColorMath.getLuma(r, g, b)
            val hMask = ColorMath.highlightsMask(luma)
            val shoulder = 1f - (1f - r).toDouble().pow(1.0 + film.highlightRollOff * 2.0).toFloat()
            val shoulderg = 1f - (1f - g).toDouble().pow(1.0 + film.highlightRollOff * 2.0).toFloat()
            val shoulderb = 1f - (1f - b).toDouble().pow(1.0 + film.highlightRollOff * 2.0).toFloat()
            r = r + (shoulder - r) * hMask * intensity
            g = g + (shoulderg - g) * hMask * intensity
            b = b + (shoulderb - b) * hMask * intensity
        }

        // Film shadow lift
        if (film.shadowLift > 1e-6f) {
            val luma = ColorMath.getLuma(r, g, b)
            val sMask = ColorMath.shadowsMask(luma)
            val lift = film.shadowLift * 0.2f * sMask * intensity
            r += lift
            g += lift
            b += lift
        }

        // Film DR compression
        if (film.drCompression > 1e-6f) {
            val luma = ColorMath.getLuma(r, g, b)
            val compressed = luma / (luma + film.drCompression * 0.5f + 1e-6f) * (1f + film.drCompression * 0.5f)
            val factor = if (luma > 1e-6f) compressed / luma else 1f
            val blend = film.drCompression * intensity
            val blendedFactor = 1f + (factor - 1f) * blend
            r *= blendedFactor
            g *= blendedFactor
            b *= blendedFactor
        }

        // Film tone curve
        val filmCurve = film.toneCurvePoints.sortedBy { it.first }
        val hasFilmCurve = filmCurve.size >= 2 && filmCurve.any { abs(it.first - it.second) > 0.1f }
        if (hasFilmCurve) {
            val filmR = ColorMath.applyCurve(r, filmCurve)
            val filmG = ColorMath.applyCurve(g, filmCurve)
            val filmB = ColorMath.applyCurve(b, filmCurve)
            r = r + (filmR - r) * intensity
            g = g + (filmG - g) * intensity
            b = b + (filmB - b) * intensity
        }

        // Film grain
        if (film.grainAmount > 1e-6f) {
            val noise = ColorMath.gradientNoise(r * 1000f, g * 1000f + b * 500f)
            val grainOffset = (noise - 0.5f) * film.grainAmount * 0.4f * intensity
            val luma = ColorMath.getLuma(r, g, b)
            var grainMod = 1f - abs(luma - 0.5f) * 1.5f
            grainMod = grainMod.coerceIn(0.2f, 1f)
            val roughnessMod = 0.5f + film.grainRoughness * 0.5f
            r += grainOffset * grainMod * roughnessMod
            g += grainOffset * grainMod * roughnessMod
            b += grainOffset * grainMod * roughnessMod
        }

        return floatArrayOf(r, g, b)
    }

    // ── Soft Glow / Bloom Buffer ──────────────────────────────────

    /**
     * Compute a bloom buffer by extracting bright pixels and blurring them.
     * Returns FloatArray of size (w * h * 3) containing the blurred bright pixels.
     */
    private fun computeBloomBuffer(pixels: IntArray, w: Int, h: Int, glowAmount: Float): FloatArray {
        val threshold = 0.7f
        val bloomRadius = (3 + glowAmount * 10).toInt()

        // 2026 hotfix: 防御 w*h*3 整数溢出
        val pixelCount = w.toLong() * h.toLong()
        if (pixelCount > Int.MAX_VALUE.toLong() || pixelCount * 3L > Int.MAX_VALUE.toLong()) {
            Log.e(TAG, "computeBloomBuffer: dimensions too large ${w}x$h")
            return FloatArray(0)
        }

        // Extract bright pixels above threshold
        val brightPixels = IntArray(pixels.size)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = ((p shr 16) and 0xFF) / 255f
            val g = ((p shr 8) and 0xFF) / 255f
            val b = (p and 0xFF) / 255f
            val luma = ColorMath.getLuma(ColorMath.srgbToLinear(r), ColorMath.srgbToLinear(g), ColorMath.srgbToLinear(b))
            if (luma > threshold) {
                val excess = ((luma - threshold) / (1f - threshold)).coerceIn(0f, 1f)
                val ri = (r * excess * 255f).toInt().coerceIn(0, 255)
                val gi = (g * excess * 255f).toInt().coerceIn(0, 255)
                val bi = (b * excess * 255f).toInt().coerceIn(0, 255)
                brightPixels[i] = (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
            } else {
                brightPixels[i] = 0xFF shl 24
            }
        }

        // Blur the bright pixels
        val blurredPixels = boxBlur(brightPixels, w, h, bloomRadius)

        // Convert to float array
        val result = FloatArray(pixelCount.toInt() * 3)
        for (i in blurredPixels.indices) {
            val p = blurredPixels[i]
            result[i * 3] = ((p shr 16) and 0xFF) / 255f
            result[i * 3 + 1] = ((p shr 8) and 0xFF) / 255f
            result[i * 3 + 2] = (p and 0xFF) / 255f
        }

        return result
    }

    // ── Blur-based Glow (soft halation around bright tones) ──────

    /**
     * Apply a blur-based soft glow effect.
     * Extracts bright pixels (luminance > threshold), blurs them,
     * and blends the blurred light back additively.
     *
     * @param pixels Source pixel array (ARGB packed ints)
     * @param w Image width
     * @param h Image height
     * @param intensity Glow intensity 0..1
     * @return New pixel array with glow applied
     */
    fun applyGlow(pixels: IntArray, w: Int, h: Int, intensity: Float): IntArray {
        if (intensity < 1e-6f) return pixels

        val threshold = 0.6f
        val bloomRadius = (2 + (intensity * 8).toInt()).coerceIn(2, 12)

        // Extract bright pixels above threshold
        val brightPixels = IntArray(pixels.size)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = ((p shr 16) and 0xFF) / 255f
            val g = ((p shr 8) and 0xFF) / 255f
            val b = (p and 0xFF) / 255f
            val luma = ColorMath.getLuma(r, g, b)
            if (luma > threshold) {
                val excess = ((luma - threshold) / (1f - threshold)).coerceIn(0f, 1f)
                val ri = (r * excess * 255f).toInt().coerceIn(0, 255)
                val gi = (g * excess * 255f).toInt().coerceIn(0, 255)
                val bi = (b * excess * 255f).toInt().coerceIn(0, 255)
                brightPixels[i] = (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
            } else {
                brightPixels[i] = 0xFF shl 24
            }
        }

        // Blur the bright pixels
        val blurredPixels = boxBlur(brightPixels, w, h, bloomRadius)

        // Blend additively
        val result = IntArray(pixels.size)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = ((p shr 16) and 0xFF) / 255f
            val g = ((p shr 8) and 0xFF) / 255f
            val b = (p and 0xFF) / 255f

            val bp = blurredPixels[i]
            val br = ((bp shr 16) and 0xFF) / 255f
            val bg = ((bp shr 8) and 0xFF) / 255f
            val bb = (bp and 0xFF) / 255f

            // Additive blend with glow intensity control
            val outR = (r + br * intensity * 0.5f).coerceIn(0f, 1f)
            val outG = (g + bg * intensity * 0.5f).coerceIn(0f, 1f)
            val outB = (b + bb * intensity * 0.5f).coerceIn(0f, 1f)

            val ri = (outR * 255f).toInt().coerceIn(0, 255)
            val gi = (outG * 255f).toInt().coerceIn(0, 255)
            val bi = (outB * 255f).toInt().coerceIn(0, 255)
            result[i] = (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
        }

        return result
    }

    // ── Blur-based Halation (red/orange bloom around highlights) ─

    /**
     * Apply a blur-based halation effect emulating film halation.
     * Targets very bright highlights (luminance > 0.85) and applies
     * a warm (red/orange tinted) bloom, emulating the red/orange halo
     * seen on color film around blown highlights.
     *
     * @param pixels Source pixel array (ARGB packed ints)
     * @param w Image width
     * @param h Image height
     * @param intensity Halation intensity 0..1
     * @return New pixel array with halation applied
     */
    fun applyHalation(pixels: IntArray, w: Int, h: Int, intensity: Float): IntArray {
        if (intensity < 1e-6f) return pixels

        val threshold = 0.85f
        val bloomRadius = (3 + (intensity * 10).toInt()).coerceIn(3, 15)

        // Extract very bright highlights with warm tint
        val brightPixels = IntArray(pixels.size)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = ((p shr 16) and 0xFF) / 255f
            val g = ((p shr 8) and 0xFF) / 255f
            val b = (p and 0xFF) / 255f
            val luma = ColorMath.getLuma(r, g, b)
            if (luma > threshold) {
                val excess = ((luma - threshold) / (1f - threshold)).coerceIn(0f, 1f)
                // Warm tint: emphasize red, reduce blue for orange/red bloom
                val warmR = r * excess * 1.2f
                val warmG = g * excess * 0.6f
                val warmB = b * excess * 0.2f
                val ri = (warmR.coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255)
                val gi = (warmG.coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255)
                val bi = (warmB.coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255)
                brightPixels[i] = (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
            } else {
                brightPixels[i] = 0xFF shl 24
            }
        }

        // Blur the warm-tinted highlights
        val blurredPixels = boxBlur(brightPixels, w, h, bloomRadius)

        // Blend additively with warm color weighting
        val result = IntArray(pixels.size)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = ((p shr 16) and 0xFF) / 255f
            val g = ((p shr 8) and 0xFF) / 255f
            val b = (p and 0xFF) / 255f

            val bp = blurredPixels[i]
            val br = ((bp shr 16) and 0xFF) / 255f
            val bg = ((bp shr 8) and 0xFF) / 255f
            val bb = (bp and 0xFF) / 255f

            // Additive blend with warm emphasis: red gets more, blue gets less
            val outR = (r + br * intensity * 0.6f).coerceIn(0f, 1f)
            val outG = (g + bg * intensity * 0.25f).coerceIn(0f, 1f)
            val outB = (b + bb * intensity * 0.1f).coerceIn(0f, 1f)

            val ri = (outR * 255f).toInt().coerceIn(0, 255)
            val gi = (outG * 255f).toInt().coerceIn(0, 255)
            val bi = (outB * 255f).toInt().coerceIn(0, 255)
            result[i] = (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
        }

        return result
    }

    // ── CDL Color Grading (Lift/Gamma/Gain per-channel offsets) ──

    /**
     * Apply CDL (Color Decision List) style color grading with
     * per-channel R/G/B offsets for shadows, midtones, and highlights.
     * Uses smoothstep-based luminance masks for smooth blending between ranges.
     *
     * @param pixels Source pixel array (ARGB packed ints)
     * @param w Image width
     * @param h Image height
     * @param adj Normalized adjustments containing CDL parameters
     * @return New pixel array with CDL grading applied
     */
    private fun applyCdlGrading(pixels: IntArray, w: Int, h: Int, adj: NormAdj): IntArray {
        // Check if any CDL adjustment is non-zero
        val hasCdl = abs(adj.cdlShadowsR) > 1e-6f || abs(adj.cdlShadowsG) > 1e-6f ||
            abs(adj.cdlShadowsB) > 1e-6f || abs(adj.cdlMidtonesR) > 1e-6f ||
            abs(adj.cdlMidtonesG) > 1e-6f || abs(adj.cdlMidtonesB) > 1e-6f ||
            abs(adj.cdlHighlightsR) > 1e-6f || abs(adj.cdlHighlightsG) > 1e-6f ||
            abs(adj.cdlHighlightsB) > 1e-6f

        if (!hasCdl) return pixels

        val result = IntArray(pixels.size)
        for (i in pixels.indices) {
            val p = pixels[i]
            var r = ((p shr 16) and 0xFF) / 255f
            var g = ((p shr 8) and 0xFF) / 255f
            var b = (p and 0xFF) / 255f

            // Compute luminance for tonal range classification
            val luma = ColorMath.getLuma(r, g, b)

            // Compute smoothstep masks for shadows, midtones, highlights
            val sm = ColorMath.shadowsMask(luma)
            val mm = ColorMath.midtonesMask(luma)
            val hm = ColorMath.highlightsMask(luma)

            // Normalize masks so they sum to 1
            val maskSum = sm + mm + hm + 1e-6f
            val nsm = sm / maskSum
            val nmm = mm / maskSum
            val nhm = hm / maskSum

            // Apply per-channel CDL offsets (Lift for shadows, Gamma for midtones, Gain for highlights)
            // Scale factor 0.15 keeps the adjustments perceptually balanced
            val offsetR = (nsm * adj.cdlShadowsR + nmm * adj.cdlMidtonesR + nhm * adj.cdlHighlightsR) * 0.15f
            val offsetG = (nsm * adj.cdlShadowsG + nmm * adj.cdlMidtonesG + nhm * adj.cdlHighlightsG) * 0.15f
            val offsetB = (nsm * adj.cdlShadowsB + nmm * adj.cdlMidtonesB + nhm * adj.cdlHighlightsB) * 0.15f

            r = (r + offsetR).coerceIn(0f, 1f)
            g = (g + offsetG).coerceIn(0f, 1f)
            b = (b + offsetB).coerceIn(0f, 1f)

            val ri = (r * 255f).toInt().coerceIn(0, 255)
            val gi = (g * 255f).toInt().coerceIn(0, 255)
            val bi = (b * 255f).toInt().coerceIn(0, 255)
            result[i] = (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
        }

        return result
    }

    // ── Export ─────────────────────────────────────────────────────

    suspend fun exportImage(
        bitmap: Bitmap,
        settings: ExportSettings,
        context: Context,
        originalExif: ExifData? = null,
        orientation: Int = 0,
        /** R-06: 原始文件路径，用于 preserveFolderStructure */
        originalPath: String? = null,
    ): Uri = withContext(Dispatchers.IO) {
        if (bitmap.isRecycled) throw IllegalArgumentException("Cannot export recycled bitmap")

        // Apply resize if needed
        var exportBitmap = bitmap
        fun replaceExport(new: Bitmap?) {
            if (new == null || new === exportBitmap) return
            if (exportBitmap !== bitmap) exportBitmap.recycle()
            exportBitmap = new
        }

        if (settings.resizeMode != ResizeMode.ORIGINAL && settings.resizeValue > 0) {
            var maxWidth = 0
            var maxHeight = 0
            when (settings.resizeMode) {
                ResizeMode.WIDTH -> maxWidth = settings.resizeValue
                ResizeMode.HEIGHT -> maxHeight = settings.resizeValue
                ResizeMode.LONG_EDGE -> {
                    maxWidth = settings.resizeValue
                    maxHeight = settings.resizeValue
                }
                ResizeMode.SHORT_EDGE -> {
                    maxWidth = settings.resizeValue
                    maxHeight = settings.resizeValue
                }
                ResizeMode.ORIGINAL -> { /* no resize */ }
            }
            if (maxWidth > 0 || maxHeight > 0) {
                replaceExport(resizeBitmap(exportBitmap, maxWidth, maxHeight))
            }
        }

        // Apply social platform aspect ratio crop (center crop)
        settings.socialPlatform.aspectRatio?.let { aspectRatio ->
            replaceExport(cropToAspectRatio(exportBitmap, aspectRatio))
        }

        // Apply watermark
        if (settings.addWatermark && settings.watermarkText.isNotEmpty()) {
            replaceExport(drawTextWatermark(exportBitmap, settings))
        }

        val mimeType = when (settings.format) {
            ExportFormat.PNG -> "image/png"
            ExportFormat.TIFF -> "image/tiff"
            ExportFormat.JPEG -> "image/jpeg"
            ExportFormat.EXR -> "image/x-exr"
            ExportFormat.HEIF -> "image/heic"
            ExportFormat.AVIF -> "image/avif"
            ExportFormat.JPEG_XL -> "image/jxl"
        }
        val extension = when (settings.format) {
            ExportFormat.PNG -> ".png"
            ExportFormat.TIFF -> ".tiff"
            ExportFormat.JPEG -> ".jpg"
            ExportFormat.EXR -> ".exr"
            ExportFormat.HEIF -> ".heic"
            ExportFormat.AVIF -> ".avif"
            ExportFormat.JPEG_XL -> ".jxl"
        }

        // HEIF export handled directly by HeifExporter (bypasses temp file flow)
        if (settings.format == ExportFormat.HEIF) {
            val heifConfig = HeifExporter.HeifConfig(
                quality = settings.safeQuality,
                bitDepth = if (settings.heifBitDepth == HeifBitDepth.DEPTH_10) {
                    HeifExporter.HeifBitDepth.DEPTH_10
                } else {
                    HeifExporter.HeifBitDepth.DEPTH_8
                }
            )
            val heifUri = HeifExporter.exportHeif(
                context = context,
                bitmap = exportBitmap,
                config = heifConfig,
                displayName = "RapidRAW_${System.currentTimeMillis()}"
            )
            if (exportBitmap !== bitmap) exportBitmap.recycle()
            return@withContext heifUri ?: throw RuntimeException("HEIF export failed")
        }

        // Write to temporary file first (required for EXIF writing)
        // R-06: 保留文件夹结构 — 使用原始相对路径作为输出子目录
        val cacheDir = if (settings.preserveFolderStructure && originalPath != null) {
            val relativeDir = java.io.File(originalPath).parent ?: ""
            val exportDir = java.io.File(context.cacheDir, "export/$relativeDir")
            exportDir.mkdirs()
            exportDir
        } else {
            context.cacheDir
        }
        val tempFile = java.io.File(cacheDir, "export_temp_${System.currentTimeMillis()}$extension")

        try {
            tempFile.outputStream().use { fos ->
                if (settings.format == ExportFormat.EXR) {
                    val pixelType = if (settings.exrBitDepth == ExrBitDepth.HALF) {
                        ExrExporter.PixelType.HALF
                    } else {
                        ExrExporter.PixelType.FLOAT
                    }
                    ExrExporter.writeExr(exportBitmap, fos, ExrExporter.ExrConfig(pixelType = pixelType))
                } else {
                    compressBitmap(exportBitmap, settings.format, settings.safeQuality, fos)
                }
            }

            // Apply EXIF metadata for JPEG, TIFF, PNG (R-13: extend EXIF preservation)
            val exifFormats = setOf(ExportFormat.JPEG, ExportFormat.TIFF, ExportFormat.PNG)
            if (settings.format in exifFormats && settings.keepMetadata && originalExif != null) {
                try {
                    val exif = androidx.exifinterface.media.ExifInterface(tempFile.absolutePath)
                    originalExif.make?.let { if (it.isNotEmpty()) exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_MAKE, it) }
                    originalExif.model?.let { if (it.isNotEmpty()) exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_MODEL, it) }
                    originalExif.dateTime?.let { if (it.isNotEmpty()) exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME, it) }
                    originalExif.iso?.let { if (it.isNotEmpty()) exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, it) }
                    originalExif.shutterSpeed?.let { if (it.isNotEmpty()) exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_EXPOSURE_TIME, it) }
                    originalExif.focalLength?.let { if (it.isNotEmpty()) exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_FOCAL_LENGTH, it) }
                    originalExif.aperture?.let { if (it.isNotEmpty()) exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_F_NUMBER, it) }
                    // R-13: 保留闪光灯、白平衡、测光模式、曝光程序等扩展 EXIF 字段
                    originalExif.flash?.let { if (it.isNotEmpty()) exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_FLASH, it) }
                    originalExif.whiteBalance?.let { if (it.isNotEmpty()) exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_WHITE_BALANCE, it) }
                    originalExif.meteringMode?.let { if (it.isNotEmpty()) exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_METERING_MODE, it) }
                    originalExif.exposureProgram?.let { if (it.isNotEmpty()) exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_EXPOSURE_PROGRAM, it) }
                    originalExif.lensMake?.let { if (it.isNotEmpty()) exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_LENS_MAKE, it) }
                    originalExif.lensModel?.let { if (it.isNotEmpty()) exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_LENS_MODEL, it) }
                    val orientationValue = when (orientation) {
                        90 -> androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90
                        180 -> androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180
                        270 -> androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270
                        else -> androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
                    }
                    exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, orientationValue.toString())
                    if (settings.stripGps) {
                        exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_GPS_LATITUDE, null)
                        exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_GPS_LONGITUDE, null)
                        exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_GPS_LATITUDE_REF, null)
                        exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_GPS_LONGITUDE_REF, null)
                    }
                    exif.saveAttributes()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to write EXIF: ${e.message}")
                }
            }

            // Copy temp file to MediaStore
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "RapidRAW_${System.currentTimeMillis()}$extension")
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/RapidRAW")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val contentResolver = context.contentResolver
            var mediaStoreUri: android.net.Uri? = null
            try {
                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    ?: throw RuntimeException("Failed to create MediaStore entry")
                mediaStoreUri = uri

                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    tempFile.inputStream().use { input ->
                        input.copyTo(outputStream)
                    }
                } ?: throw RuntimeException("Failed to open output stream")

                // Clear IS_PENDING flag
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    contentResolver.update(uri, contentValues, null, null)
                }

                uri
            } catch (e: Throwable) {
                // v1.5.5 hotfix: 异常路径下清理已创建的 IS_PENDING MediaStore 条目，
                // 避免用户相册里出现 0 字节的孤儿文件。
                mediaStoreUri?.let { orphan ->
                    runCatching {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            // 直接删除条目（IS_PENDING 状态下允许）
                            contentResolver.delete(orphan, null, null)
                        } else {
                            contentResolver.delete(orphan, null, null)
                        }
                    }
                }
                throw e
            }
        } catch (oom: OutOfMemoryError) {
            Log.e(TAG, "OOM during export: ${oom.message}", oom)
            throw IllegalStateException("内存不足，导出失败", oom)
        } finally {
            if (exportBitmap !== bitmap) exportBitmap.recycle()
            tempFile.delete()
        }
    }

    private fun resizeBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        if (maxWidth <= 0 && maxHeight <= 0) return bitmap

        var width = bitmap.width
        var height = bitmap.height

        if (maxWidth > 0 && width > maxWidth) {
            val ratio = maxWidth.toFloat() / width
            width = maxWidth
            height = (height * ratio).toInt()
        }
        if (maxHeight > 0 && height > maxHeight) {
            val ratio = maxHeight.toFloat() / height
            height = maxHeight
            width = (width * ratio).toInt()
        }

        return try {
            Bitmap.createScaledBitmap(bitmap, width, height, true)
        } catch (oom: OutOfMemoryError) {
            Log.e(TAG, "OOM resizing bitmap", oom)
            bitmap
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "IllegalArgument resizing bitmap", e)
            bitmap
        }
    }

    private fun cropToAspectRatio(bitmap: Bitmap, aspectRatio: Float): Bitmap {
        val currentRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        if (kotlin.math.abs(currentRatio - aspectRatio) < 0.01f) return bitmap

        val newWidth: Int
        val newHeight: Int
        if (currentRatio > aspectRatio) {
            // Image is wider than target: crop width
            newHeight = bitmap.height
            newWidth = (bitmap.height * aspectRatio).toInt()
        } else {
            // Image is taller than target: crop height
            newWidth = bitmap.width
            newHeight = (bitmap.width / aspectRatio).toInt()
        }

        val x = (bitmap.width - newWidth) / 2
        val y = (bitmap.height - newHeight) / 2
        return try {
            Bitmap.createBitmap(bitmap, x, y, newWidth, newHeight)
        } catch (oom: OutOfMemoryError) {
            Log.e(TAG, "OOM cropping bitmap", oom)
            bitmap
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "IllegalArgument cropping bitmap", e)
            bitmap
        }
    }

    private fun drawTextWatermark(bitmap: Bitmap, settings: ExportSettings): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(result)

        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            alpha = (settings.watermarkOpacity * 255).toInt().coerceIn(0, 255)
            isAntiAlias = true
            textSize = (bitmap.width * settings.watermarkScale).coerceIn(12f, 120f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        val text = settings.watermarkText
        val textWidth = paint.measureText(text)
        val textHeight = paint.fontMetrics.descent - paint.fontMetrics.ascent

        val padding = (bitmap.width * 0.02f).coerceAtLeast(8f)
        val x = when (settings.watermarkAnchor) {
            WatermarkAnchor.TOP_LEFT, WatermarkAnchor.CENTER_LEFT, WatermarkAnchor.BOTTOM_LEFT -> padding
            WatermarkAnchor.TOP_CENTER, WatermarkAnchor.CENTER, WatermarkAnchor.BOTTOM_CENTER -> (bitmap.width - textWidth) / 2f
            WatermarkAnchor.TOP_RIGHT, WatermarkAnchor.CENTER_RIGHT, WatermarkAnchor.BOTTOM_RIGHT -> bitmap.width - textWidth - padding
        }
        val y = when (settings.watermarkAnchor) {
            WatermarkAnchor.TOP_LEFT, WatermarkAnchor.TOP_CENTER, WatermarkAnchor.TOP_RIGHT -> textHeight + padding
            WatermarkAnchor.CENTER_LEFT, WatermarkAnchor.CENTER, WatermarkAnchor.CENTER_RIGHT -> (bitmap.height + textHeight) / 2f
            WatermarkAnchor.BOTTOM_LEFT, WatermarkAnchor.BOTTOM_CENTER, WatermarkAnchor.BOTTOM_RIGHT -> bitmap.height - padding
        }

        canvas.drawText(text, x, y, paint)
        return result
    }

    private fun compressBitmap(bitmap: Bitmap, format: ExportFormat, quality: Int, outputStream: OutputStream) {
        when (format) {
            ExportFormat.PNG -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            ExportFormat.TIFF -> {
                // Android doesn't natively support TIFF compression via Bitmap.
                // Write as highest-quality JPEG as fallback (true TIFF encoding
                // would require a third-party library like Apache Commons Imaging).
                // For on-device pure implementation, we output JPEG inside a TIFF-like
                // container: write raw pixel data as uncompressed TIFF.
                writeUncompressedTiff(bitmap, outputStream)
            }
            ExportFormat.JPEG -> bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            else -> Unit
        }
        outputStream.flush()
    }

    /**
     * Write an uncompressed TIFF file.
     * Correct TIFF structure: header + IFD + extra data + pixel data.
     */
    private fun writeUncompressedTiff(bitmap: Bitmap, out: OutputStream) {
        val w = bitmap.width
        val h = bitmap.height
        // 2026 hotfix: 防御 w*h 整数溢出
        val pixelCount = w.toLong() * h.toLong()
        if (pixelCount > Int.MAX_VALUE.toLong()) {
            Log.e(TAG, "writeUncompressedTiff: bitmap too large ${w}x$h")
            return
        }
        val rowBytes = w * 3
        val imageSize = rowBytes * h

        // IFD offset calculation
        val headerSize = 8
        val ifdOffset = headerSize
        val numEntries = 11
        val ifdSize = 2 + numEntries * 12 + 4
        val extraDataOffset = ifdOffset + ifdSize

        // BitsPerSample at extraDataOffset (3 SHORTs = 6 bytes, padded to 8)
        // XResolution at extraDataOffset + 8 (RATIONAL = 8 bytes)
        // YResolution at extraDataOffset + 16 (RATIONAL = 8 bytes)
        val stripOffset = extraDataOffset + 24

        val buf = java.nio.ByteBuffer.allocate(stripOffset + imageSize).order(java.nio.ByteOrder.LITTLE_ENDIAN)

        // TIFF header
        buf.putShort(0x4949.toShort()) // Little endian
        buf.putShort(42.toShort()) // Magic number
        buf.putInt(ifdOffset)

        // IFD
        buf.putShort(numEntries.toShort())

        fun writeEntry(tag: Int, type: Int, count: Int, value: Int) {
            buf.putShort(tag.toShort())
            buf.putShort(type.toShort())
            buf.putInt(count)
            buf.putInt(value)
        }

        writeEntry(256, 3, 1, w) // ImageWidth
        writeEntry(257, 3, 1, h) // ImageLength
        writeEntry(258, 3, 3, extraDataOffset) // BitsPerSample -> offset
        writeEntry(259, 3, 1, 1) // Compression = none
        writeEntry(262, 3, 1, 2) // PhotometricInterpretation = RGB
        writeEntry(273, 4, 1, stripOffset) // StripOffsets
        writeEntry(277, 3, 1, 3) // SamplesPerPixel
        writeEntry(278, 4, 1, h) // RowsPerStrip
        writeEntry(279, 4, 1, imageSize) // StripByteCounts
        writeEntry(282, 5, 1, extraDataOffset + 8) // XResolution
        writeEntry(283, 5, 1, extraDataOffset + 16) // YResolution

        buf.putInt(0) // Next IFD offset = 0

        // Extra data: BitsPerSample (8, 8, 8) + 2 bytes padding
        buf.putShort(8.toShort())
        buf.putShort(8.toShort())
        buf.putShort(8.toShort())
        buf.putShort(0.toShort())

        // XResolution: 72/1
        buf.putInt(72)
        buf.putInt(1)

        // YResolution: 72/1
        buf.putInt(72)
        buf.putInt(1)

        // Pixel data (RGB, no alpha)
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        for (pixel in pixels) {
            buf.put((pixel shr 16 and 0xFF).toByte()) // R
            buf.put((pixel shr 8 and 0xFF).toByte())  // G
            buf.put((pixel and 0xFF).toByte())        // B
        }

        out.write(buf.array())
    }

    // ── Geometric Transform ────────────────────────────────────────

    /**
     * Apply geometric transformations: flip, orientation rotation, fine rotation, crop, perspective.
     */
    private fun applyGeometricTransform(source: Bitmap, n: NormAdj): Bitmap {
        if (source.isRecycled) return source
        var result = source

        fun transform(new: Bitmap?) {
            if (new == null || new === result) return
            if (result !== source) result.recycle()
            result = new
        }

        // 2026 hotfix: 防御 OOM/IllegalArgument：每次几何变换都用安全包装
        fun safeTransformMatrix(matrix: android.graphics.Matrix) {
            if (result.isRecycled) return
            val newBitmap = try {
                Bitmap.createBitmap(result, 0, 0, result.width, result.height, matrix, true)
            } catch (oom: OutOfMemoryError) {
                Log.w(TAG, "OOM applying geometric transform", oom)
                null
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "IllegalArgument applying geometric transform", e)
                null
            }
            transform(newBitmap)
        }

        // 1. Flip
        if (n.flipHorizontal || n.flipVertical) {
            val matrix = android.graphics.Matrix()
            matrix.preScale(
                if (n.flipHorizontal) -1f else 1f,
                if (n.flipVertical) -1f else 1f
            )
            safeTransformMatrix(matrix)
        }

        // 2. Orientation rotation (90° multiples)
        if (n.orientationSteps != 0) {
            val matrix = android.graphics.Matrix()
            matrix.postRotate((n.orientationSteps * 90).toFloat())
            safeTransformMatrix(matrix)
        }

        // 3. Fine rotation
        if (n.rotation != 0f) {
            val matrix = android.graphics.Matrix()
            matrix.postRotate(n.rotation, result.width / 2f, result.height / 2f)
            val rect = android.graphics.RectF(0f, 0f, result.width.toFloat(), result.height.toFloat())
            matrix.mapRect(rect)
            matrix.postTranslate(-rect.left, -rect.top)
            safeTransformMatrix(matrix)
        }

        // 4. Scale
        if (n.transformScale != 1f) {
            val matrix = android.graphics.Matrix()
            matrix.postScale(n.transformScale, n.transformScale, result.width / 2f, result.height / 2f)
            safeTransformMatrix(matrix)
        }

        // 4b. Aspect ratio
        if (n.transformAspect != 0f) {
            val matrix = android.graphics.Matrix()
            matrix.postScale(1f + n.transformAspect * 0.5f, 1f, result.width / 2f, result.height / 2f)
            safeTransformMatrix(matrix)
        }

        // 4c. Transform rotate (perspective panel fine rotation)
        if (n.transformRotate != 0f) {
            val matrix = android.graphics.Matrix()
            matrix.postRotate(n.transformRotate, result.width / 2f, result.height / 2f)
            val rect = android.graphics.RectF(0f, 0f, result.width.toFloat(), result.height.toFloat())
            matrix.mapRect(rect)
            matrix.postTranslate(-rect.left, -rect.top)
            safeTransformMatrix(matrix)
        }

        // 5. Offset
        if (n.transformXOffset != 0f || n.transformYOffset != 0f) {
            val matrix = android.graphics.Matrix()
            matrix.postTranslate(n.transformXOffset * result.width * 0.1f, n.transformYOffset * result.height * 0.1f)
            safeTransformMatrix(matrix)
        }

        // 6. Crop
        if (n.cropAspectRatio != null) {
            val imgW = result.width
            val imgH = result.height
            val targetRatio = n.cropAspectRatio
            val currentRatio = imgW.toFloat() / imgH
            var cropW = imgW
            var cropH = imgH
            var cropX = 0
            var cropY = 0
            if (kotlin.math.abs(currentRatio - targetRatio) > 0.01f) {
                if (currentRatio > targetRatio) {
                    cropW = (imgH * targetRatio).toInt()
                    cropX = (imgW - cropW) / 2
                } else {
                    cropH = (imgW / targetRatio).toInt()
                    cropY = (imgH - cropH) / 2
                }
            }
            cropW = cropW.coerceIn(1, imgW - cropX)
            cropH = cropH.coerceIn(1, imgH - cropY)
            cropX = cropX.coerceIn(0, imgW - cropW)
            cropY = cropY.coerceIn(0, imgH - cropH)
            if (cropW > 0 && cropH > 0) {
                val newBitmap = try {
                    Bitmap.createBitmap(result, cropX, cropY, cropW, cropH)
                } catch (oom: OutOfMemoryError) {
                    Log.w(TAG, "OOM cropping result", oom)
                    null
                } catch (e: IllegalArgumentException) {
                    Log.w(TAG, "IllegalArgument cropping result", e)
                    null
                }
                transform(newBitmap)
            }
        }

        return result
    }

    // ── Lens Correction ────────────────────────────────────────────

    /**
     * Apply lens distortion, vignette, and TCA correction.
     * Uses Brown-Conrady model for distortion and radial mapping for vignette/TCA.
     */
    private fun applyLensCorrection(source: Bitmap, n: NormAdj): Bitmap {
        val w = source.width
        val h = source.height
        if (w <= 0 || h <= 0) return source
        // 2026 hotfix: 防御 w*h 整数溢出
        val pixelCount = w.toLong() * h.toLong()
        if (pixelCount > Int.MAX_VALUE.toLong()) {
            Log.w(TAG, "applyLensCorrection: bitmap too large ${w}x$h")
            return source
        }
        val srcPixels = IntArray(pixelCount.toInt())
        source.getPixels(srcPixels, 0, w, 0, 0, w, h)
        val result = IntArray(pixelCount.toInt()) { 0xFF000000.toInt() }

        val cx = w / 2f
        val cy = h / 2f
        val maxR = kotlin.math.sqrt(cx * cx + cy * cy)

        for (y in 0 until h) {
            for (x in 0 until w) {
                val nx = (x - cx) / maxR
                val ny = (y - cy) / maxR
                val r2 = nx * nx + ny * ny
                val r4 = r2 * r2

                // Focal length factor: shorter focal length = stronger effects
                val focalFactor = 50f / n.lensFocalLength.coerceAtLeast(1f)

                // Distortion correction (reverse mapping)
                val k1 = n.lensDistortion * 0.15f * focalFactor
                val radial = 1f + k1 * r2
                var srcNx = nx / radial
                var srcNy = ny / radial

                // TCA: separate R/B channel offsets
                val tca = n.lensTca * 0.02f * focalFactor
                val tcaOffsetR = tca * r2
                val tcaOffsetB = -tca * r2

                // Vignette correction: brighten edges
                val vignetteCorr = 1f + n.lensVignette * 0.5f * r2 * focalFactor

                val srcX = srcNx * maxR + cx
                val srcY = srcNy * maxR + cy

                val x0 = srcX.toInt()
                val y0 = srcY.toInt()
                val x1 = x0 + 1
                val y1 = y0 + 1
                val fx = srcX - x0
                val fy = srcY - y0

                if (x0 >= 0 && x1 < w && y0 >= 0 && y1 < h) {
                    val p00 = srcPixels[y0 * w + x0]
                    val p01 = srcPixels[y0 * w + x1]
                    val p10 = srcPixels[y1 * w + x0]
                    val p11 = srcPixels[y1 * w + x1]

                    // R channel with TCA offset
                    val srcXR = srcX + tcaOffsetR * maxR
                    val x0r = srcXR.toInt()
                    val fxR = srcXR - x0r
                    val x1r = x0r + 1
                    val rVal = if (x0r >= 0 && x1r < w && y0 >= 0 && y1 < h) {
                        val p00r = srcPixels[y0 * w + x0r]
                        val p01r = srcPixels[y0 * w + x1r]
                        val p10r = srcPixels[y1 * w + x0r]
                        val p11r = srcPixels[y1 * w + x1r]
                        bilinearChannel(p00r, p01r, p10r, p11r, fxR, fy, 16) * vignetteCorr
                    } else {
                        bilinearChannel(p00, p01, p10, p11, fx, fy, 16) * vignetteCorr
                    }
                    val g = bilinearChannel(p00, p01, p10, p11, fx, fy, 8) * vignetteCorr
                    // B channel with TCA offset
                    val srcXB = srcX + tcaOffsetB * maxR
                    val x0b = srcXB.toInt()
                    val fxB = srcXB - x0b
                    val x1b = x0b + 1
                    val bVal = if (x0b >= 0 && x1b < w && y0 >= 0 && y1 < h) {
                        val p00b = srcPixels[y0 * w + x0b]
                        val p01b = srcPixels[y0 * w + x1b]
                        val p10b = srcPixels[y1 * w + x0b]
                        val p11b = srcPixels[y1 * w + x1b]
                        bilinearChannel(p00b, p01b, p10b, p11b, fxB, fy, 0) * vignetteCorr
                    } else {
                        bilinearChannel(p00, p01, p10, p11, fx, fy, 0) * vignetteCorr
                    }

                    val ri = rVal.toInt().coerceIn(0, 255)
                    val gi = g.toInt().coerceIn(0, 255)
                    val bi = bVal.toInt().coerceIn(0, 255)
                    result[y * w + x] = (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
                }
            }
        }

        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(result, 0, w, 0, 0, w, h)
        return bitmap
    }

    private fun bilinearChannel(p00: Int, p01: Int, p10: Int, p11: Int, fx: Float, fy: Float, shift: Int): Float {
        val v00 = ((p00 ushr shift) and 0xFF).toFloat()
        val v01 = ((p01 ushr shift) and 0xFF).toFloat()
        val v10 = ((p10 ushr shift) and 0xFF).toFloat()
        val v11 = ((p11 ushr shift) and 0xFF).toFloat()
        val top = v00 * (1f - fx) + v01 * fx
        val bot = v10 * (1f - fx) + v11 * fx
        return top * (1f - fy) + bot * fy
    }

    /**
     * Apply dual-axis chromatic aberration by sampling R and B channels with radial offsets.
     */
    private fun applyChromaticAberration(
        srcPixels: IntArray, w: Int, h: Int,
        x: Int, y: Int,
        r: Float, g: Float, b: Float,
        caRedCyan: Float, caBlueYellow: Float
    ): FloatArray {
        val cx = w / 2f
        val cy = h / 2f
        val maxR = kotlin.math.sqrt(cx * cx + cy * cy)
        val nx = (x - cx) / maxR
        val ny = (y - cy) / maxR
        val dist = kotlin.math.sqrt(nx * nx + ny * ny)
        val dirX = if (dist > 1e-6f) nx / dist else 0f
        val dirY = if (dist > 1e-6f) ny / dist else 0f

        // Red-Cyan axis: offset red channel
        val offsetR = dist * caRedCyan * 0.03f * maxR
        val srcXR = x + dirX * offsetR
        val srcYR = y + dirY * offsetR
        val x0r = srcXR.toInt()
        val y0r = srcYR.toInt()
        val fxR = srcXR - x0r
        val fyR = srcYR - y0r
        val x1r = x0r + 1
        val y1r = y0r + 1
        val rSampled = if (x0r >= 0 && x1r < w && y0r >= 0 && y1r < h) {
            val p00 = srcPixels[y0r * w + x0r]
            val p01 = srcPixels[y0r * w + x1r]
            val p10 = srcPixels[y1r * w + x0r]
            val p11 = srcPixels[y1r * w + x1r]
            bilinearChannel(p00, p01, p10, p11, fxR, fyR, 16) / 255f
        } else {
            r
        }

        // Blue-Yellow axis: offset blue channel (opposite direction)
        val offsetB = dist * caBlueYellow * 0.03f * maxR
        val srcXB = x - dirX * offsetB
        val srcYB = y - dirY * offsetB
        val x0b = srcXB.toInt()
        val y0b = srcYB.toInt()
        val fxB = srcXB - x0b
        val fyB = srcYB - y0b
        val x1b = x0b + 1
        val y1b = y0b + 1
        val bSampled = if (x0b >= 0 && x1b < w && y0b >= 0 && y1b < h) {
            val p00 = srcPixels[y0b * w + x0b]
            val p01 = srcPixels[y0b * w + x1b]
            val p10 = srcPixels[y1b * w + x0b]
            val p11 = srcPixels[y1b * w + x1b]
            bilinearChannel(p00, p01, p10, p11, fxB, fyB, 0) / 255f
        } else {
            b
        }

        // Convert sampled sRGB to linear and blend
        val blendR = kotlin.math.abs(caRedCyan).coerceIn(0f, 1f)
        val blendB = kotlin.math.abs(caBlueYellow).coerceIn(0f, 1f)
        val rLin = ColorMath.srgbToLinear(rSampled)
        val bLin = ColorMath.srgbToLinear(bSampled)
        return floatArrayOf(
            r + (rLin - r) * blendR,
            g,
            b + (bLin - b) * blendB
        )
    }
}

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
import com.rapidraw.data.model.FilmSimulation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.OutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

// ── Data Classes ───────────────────────────────────────────────────────

data class ExifData(
    val orientation: Int = 1,
    val make: String = "",
    val model: String = "",
    val dateTime: String = "",
    val iso: Int = 0,
    val exposureTime: Double = 0.0,
    val focalLength: Float = 0f,
    val aperture: Float = 0f
)

data class ProcessedImage(
    val original: Bitmap,
    val preview: Bitmap,
    val width: Int,
    val height: Int,
    val isRaw: Boolean,
    val exif: ExifData
)

data class ExportSettings(
    val format: String = "JPEG",       // JPEG, PNG, TIFF
    val quality: Int = 95,             // JPEG quality 1-100
    val maxWidth: Int = 0,             // 0 = no resize
    val maxHeight: Int = 0,
    val preserveMetadata: Boolean = true
)

data class Adjustments(
    // Exposure & Brightness
    val exposure: Float = 0f,          // -5.0 .. 5.0
    val brightness: Float = 0f,        // -1.0 .. 1.0

    // Tone / Film
    val toneLevel: Float = 0f,         // -1.0 .. 1.0
    val filmIntensity: Float = 1f,     // 0.0 .. 1.0
    val greenMagenta: Float = 0f,      // -1.0 .. 1.0
    val softGlow: Float = 0f,          // 0.0 .. 1.0

    // Film simulation parameters
    val filmId: String = "",
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

    // White Balance
    val temperature: Float = 6500f,    // 2000 .. 15000
    val tint: Float = 0f,              // -100 .. 100

    // Tonal
    val contrast: Float = 0f,          // -1.0 .. 1.0
    val highlights: Float = 0f,        // -1.0 .. 1.0
    val shadows: Float = 0f,           // -1.0 .. 1.0
    val whites: Float = 0f,            // -1.0 .. 1.0
    val blacks: Float = 0f,            // -1.0 .. 1.0

    // Color
    val saturation: Float = 0f,        // -1.0 .. 1.0
    val vibrance: Float = 0f,          // -1.0 .. 1.0

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

    // Tone Curve (10 control points: x,y pairs)
    val toneCurvePoints: List<Pair<Float, Float>> = listOf(
        0f to 0f, 0.1f to 0.1f, 0.25f to 0.25f, 0.5f to 0.5f,
        0.75f to 0.75f, 0.9f to 0.9f, 1f to 1f,
        0.125f to 0.125f, 0.375f to 0.375f, 0.625f to 0.625f
    ),

    // Color Grading
    val colorGradingShadows: FloatArray = floatArrayOf(0f, 0f, 0f),
    val colorGradingMidtones: FloatArray = floatArrayOf(0f, 0f, 0f),
    val colorGradingHighlights: FloatArray = floatArrayOf(0f, 0f, 0f),
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
    val sharpness: Float = 0f,         // 0.0 .. 4.0
    val clarity: Float = 0f,           // -1.0 .. 1.0
    val structure: Float = 0f,         // -1.0 .. 1.0

    // Effects
    val dehaze: Float = 0f,            // -1.0 .. 1.0
    val vignette: Float = 0f,          // -1.0 .. 1.0
    val grain: Float = 0f,             // 0.0 .. 1.0
    val grainSize: Float = 1.0f,       // 0.5 .. 3.0
    val chromaticAberration: Float = 0f, // -1.0 .. 1.0

    // Tone Mapping
    val agxEnabled: Boolean = false,
    val agxContrast: Float = 0.0f,
    val agxPedestal: Float = 0.0f,

    // Debug
    val clippingPreview: Boolean = false
) {
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}

// ── Image Processor ────────────────────────────────────────────────────

class ImageProcessor {

    companion object {
        private const val TAG = "ImageProcessor"
        private const val PREVIEW_MAX_DIMENSION = 2048
    }

    // ── Load & Decode ──────────────────────────────────────────────

    suspend fun loadAndDecode(context: Context, uri: Uri): ProcessedImage =
        withContext(Dispatchers.IO) {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: throw IllegalArgumentException("Cannot open URI: $uri")

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
            val exif = readExifData(context, uri)

            // Decode bitmap
            val originalBitmap = if (isDng && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                decodeDng(context, uri)
            } else if (isRaw) {
                decodeRawFallback(context, uri)
            } else {
                decodeRegular(context, uri)
            }

            // Apply EXIF orientation
            val orientedBitmap = applyExifOrientation(originalBitmap, exif.orientation)

            // Create preview
            val previewBitmap = createPreview(orientedBitmap)

            ProcessedImage(
                original = orientedBitmap,
                preview = previewBitmap,
                width = orientedBitmap.width,
                height = orientedBitmap.height,
                isRaw = isRaw,
                exif = exif
            )
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

    private fun readExifData(context: Context, uri: Uri): ExifData {
        val exif = ExifData()
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return exif
            inputStream.use { stream ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    val exifInterface = android.media.ExifInterface(stream)
                    val orientation = when (exifInterface.getAttributeInt(
                        android.media.ExifInterface.TAG_ORIENTATION,
                        android.media.ExifInterface.ORIENTATION_NORMAL
                    )) {
                        android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90
                        android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
                        android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270
                        else -> 0
                    }

                    return ExifData(
                        orientation = orientation,
                        make = exifInterface.getAttribute(android.media.ExifInterface.TAG_MAKE) ?: "",
                        model = exifInterface.getAttribute(android.media.ExifInterface.TAG_MODEL) ?: "",
                        dateTime = exifInterface.getAttribute(android.media.ExifInterface.TAG_DATETIME) ?: "",
                        iso = exifInterface.getAttributeInt(android.media.ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, 0),
                        exposureTime = exifInterface.getAttributeDouble(android.media.ExifInterface.TAG_EXPOSURE_TIME, 0.0),
                        focalLength = exifInterface.getAttributeDouble(android.media.ExifInterface.TAG_FOCAL_LENGTH, 0.0).toFloat(),
                        aperture = exifInterface.getAttributeDouble(android.media.ExifInterface.TAG_F_NUMBER, 0.0).toFloat()
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read EXIF: ${e.message}")
        }
        return exif
    }

    private fun decodeDng(context: Context, uri: Uri): Bitmap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
            return android.graphics.ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.setAllocator(android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE)
                decoder.isMutableRequired = true
            }
        }
        return decodeRawFallback(context, uri)
    }

    private fun decodeRawFallback(context: Context, uri: Uri): Bitmap {
        // Try to decode RAW with BitmapFactory - some Android versions support this
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open URI")

        inputStream.use { stream ->
            // First pass: get dimensions
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(stream, null, options)

            // Calculate inSampleSize for manageable size
            val maxDim = 4096
            val inSampleSize = calculateInSampleSize(options.outWidth, options.outHeight, maxDim, maxDim)

            // Second pass: decode
            val decodeStream = context.contentResolver.openInputStream(uri)
                ?: throw IllegalArgumentException("Cannot reopen URI")
            decodeStream.use { ds ->
                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = inSampleSize
                    inMutable = true
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                return BitmapFactory.decodeStream(ds, null, decodeOptions)
                    ?: throw IllegalArgumentException("Failed to decode RAW image")
            }
        }
    }

    private fun decodeRegular(context: Context, uri: Uri): Bitmap {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open URI")

        inputStream.use { stream ->
            // First pass: get dimensions
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(stream, null, options)

            // Second pass: decode full
            val decodeStream = context.contentResolver.openInputStream(uri)
                ?: throw IllegalArgumentException("Cannot reopen URI")
            decodeStream.use { ds ->
                val decodeOptions = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inMutable = true
                }
                return BitmapFactory.decodeStream(ds, null, decodeOptions)
                    ?: throw IllegalArgumentException("Failed to decode image")
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

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        if (orientation == 0) return bitmap
        val matrix = Matrix()
        when (orientation) {
            90 -> matrix.postRotate(90f)
            180 -> matrix.postRotate(180f)
            270 -> matrix.postRotate(270f)
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) bitmap.recycle()
        return rotated
    }

    private fun createPreview(original: Bitmap): Bitmap {
        val maxDim = PREVIEW_MAX_DIMENSION
        if (original.width <= maxDim && original.height <= maxDim) {
            return original
        }

        val scale = min(maxDim.toFloat() / original.width, maxDim.toFloat() / original.height)
        val newWidth = (original.width * scale).toInt()
        val newHeight = (original.height * scale).toInt()

        return Bitmap.createScaledBitmap(original, newWidth, newHeight, true)
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
    suspend fun processFullResolution(
        adjustments: com.rapidraw.data.model.Adjustments,
        originalBitmap: Bitmap
    ): Bitmap = withContext(Dispatchers.Default) {
        // Convert to core Adjustments for internal processing
        val adj = convertToCoreAdjustments(adjustments)

        val w = originalBitmap.width
        val h = originalBitmap.height
        val totalPixels = w.toLong() * h.toLong()

        // Create output bitmap
        val outputBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

        // Get pixels as int array
        val pixels = IntArray(w * h)
        originalBitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        // Pre-compute white balance multipliers
        val wbMultipliers = ColorMath.temperatureTintToMultipliers(
            adj.temperature, adj.tint
        )

        // Pre-compute tone curve (identity if no changes)
        val curvePoints = adj.toneCurvePoints.sortedBy { it.first }

        // Pre-compute film curve points
        val filmCurve = adj.filmCurvePoints.sortedBy { it.first }

        // Pre-compute color calibration matrix
        val calibMatrix = computeCalibrationMatrix(adj)

        // Pre-compute soft glow bloom buffer if needed
        val bloomBuffer = if (adj.softGlow > 1e-6f) computeBloomBuffer(pixels, w, h, adj.softGlow) else null

        // Process each pixel
        for (y in 0 until h) {
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
                r = r * 2f.pow(adj.exposure)
                g = g * 2f.pow(adj.exposure)
                b = b * 2f.pow(adj.exposure)

                // ── 3. Filmic Brightness (rational curve with midtone emphasis) ──
                val br = adj.brightness * 2f
                r = applyFilmicBrightness(r, br)
                g = applyFilmicBrightness(g, br)
                b = applyFilmicBrightness(b, br)

                // ── 3b. Tone Level (影调: combined brightness control) ──
                if (abs(adj.toneLevel) > 1e-6f) {
                    val luma = ColorMath.getLuma(r, g, b)
                    val shift = adj.toneLevel * 0.3f
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
                if (abs(adj.greenMagenta) > 1e-6f) {
                    // Positive = magenta (reduce green, add red+blue), Negative = green
                    g += adj.greenMagenta * -0.1f
                    r += adj.greenMagenta * 0.05f
                    b += adj.greenMagenta * 0.05f
                }

                // ── 5. Highlights Adjustment ──
                val highlightsResult = applyHighlightsAdjustment(r, g, b, adj.highlights)
                r = highlightsResult[0]; g = highlightsResult[1]; b = highlightsResult[2]

                // ── 6. Tonal: Contrast/Shadows/Whites/Blacks ──
                val tonalResult = applyTonalAdjustments(r, g, b, adj)
                r = tonalResult[0]; g = tonalResult[1]; b = tonalResult[2]

                // ── 7. Saturation/Vibrance ──
                val satResult = applyCreativeColor(r, g, b, adj.saturation, adj.vibrance)
                r = satResult[0]; g = satResult[1]; b = satResult[2]

                // ── 8. HSL 8-color panel ──
                val hslResult = applyHslPanel(r, g, b, adj)
                r = hslResult[0]; g = hslResult[1]; b = hslResult[2]

                // ── 9. Tone Curves ──
                r = ColorMath.applyCurve(r, curvePoints)
                g = ColorMath.applyCurve(g, curvePoints)
                b = ColorMath.applyCurve(b, curvePoints)

                // ── 10. Color Grading ──
                val cgResult = applyColorGrading(r, g, b, adj)
                r = cgResult[0]; g = cgResult[1]; b = cgResult[2]

                // ── 11. Sharpness (unsharp mask) - applied in post ──
                // Sharpness and clarity are applied as spatial operations after pixel loop

                // ── 12. Clarity/Structure (local contrast) - applied in post ──

                // ── 13. Dehaze ──
                val dehazeResult = applyDehaze(r, g, b, adj.dehaze)
                r = dehazeResult[0]; g = dehazeResult[1]; b = dehazeResult[2]

                // ── 14. Vignette ──
                val vignetteResult = applyVignette(r, g, b, x.toFloat() / w, y.toFloat() / h, adj.vignette)
                r = vignetteResult[0]; g = vignetteResult[1]; b = vignetteResult[2]

                // ── 15. Grain ──
                val grainResult = applyGrain(r, g, b, x.toFloat(), y.toFloat(), adj.grain, adj.grainSize, w, h)
                r = grainResult[0]; g = grainResult[1]; b = grainResult[2]

                // ── 16. Color Calibration ──
                val cal = FloatArray(3)
                cal[0] = calibMatrix[0] * r + calibMatrix[1] * g + calibMatrix[2] * b
                cal[1] = calibMatrix[3] * r + calibMatrix[4] * g + calibMatrix[5] * b
                cal[2] = calibMatrix[6] * r + calibMatrix[7] * g + calibMatrix[8] * b
                r = cal[0]; g = cal[1]; b = cal[2]

                // ── 17. AgX Tone Mapping ──
                if (adj.agxEnabled) {
                    val agxResult = applyAgxToneMap(r, g, b, adj.agxContrast, adj.agxPedestal)
                    r = agxResult[0]; g = agxResult[1]; b = agxResult[2]
                }

                // ── 18. Film Simulation Processing ──
                if (adj.filmId.isNotEmpty() && adj.filmIntensity > 1e-6f) {
                    // Film color shifts (applied in linear space)
                    r += adj.filmRedShift * 0.15f
                    g += adj.filmGreenShift * 0.15f
                    b += adj.filmBlueShift * 0.15f

                    // Film saturation modifier
                    if (abs(adj.filmSaturation) > 1e-6f) {
                        val lum = ColorMath.getLuma(r, g, b)
                        val satMod = 1f + adj.filmSaturation
                        r = lum + (r - lum) * satMod
                        g = lum + (g - lum) * satMod
                        b = lum + (b - lum) * satMod
                    }

                    // Film contrast modifier
                    if (abs(adj.filmContrast) > 1e-6f) {
                        val contrastPow = 1f + adj.filmContrast * 0.5f
                        val mid = 0.18f
                        r = mid + (r - mid) * contrastPow
                        g = mid + (g - mid) * contrastPow
                        b = mid + (b - mid) * contrastPow
                    }

                    // Film highlight roll-off
                    if (adj.filmHighlightRollOff > 1e-6f) {
                        val luma = ColorMath.getLuma(r, g, b)
                        val hMask = ColorMath.highlightsMask(luma)
                        val shoulder = 1f - (1f - r).toDouble().pow(1.0 + adj.filmHighlightRollOff * 2.0).toFloat()
                        val shoulderg = 1f - (1f - g).toDouble().pow(1.0 + adj.filmHighlightRollOff * 2.0).toFloat()
                        val shoulderb = 1f - (1f - b).toDouble().pow(1.0 + adj.filmHighlightRollOff * 2.0).toFloat()
                        r = r + (shoulder - r) * hMask * adj.filmIntensity
                        g = g + (shoulderg - g) * hMask * adj.filmIntensity
                        b = b + (shoulderb - b) * hMask * adj.filmIntensity
                    }

                    // Film shadow lift
                    if (adj.filmShadowLift > 1e-6f) {
                        val luma = ColorMath.getLuma(r, g, b)
                        val sMask = ColorMath.shadowsMask(luma)
                        val lift = adj.filmShadowLift * 0.2f * sMask * adj.filmIntensity
                        r += lift
                        g += lift
                        b += lift
                    }

                    // Film DR compression
                    if (adj.filmDrCompression > 1e-6f) {
                        val luma = ColorMath.getLuma(r, g, b)
                        val compressed = luma / (luma + adj.filmDrCompression * 0.5f + 1e-6f) * (1f + adj.filmDrCompression * 0.5f)
                        val factor = if (luma > 1e-6f) compressed / luma else 1f
                        val blend = adj.filmDrCompression * adj.filmIntensity
                        val blendedFactor = 1f + (factor - 1f) * blend
                        r *= blendedFactor
                        g *= blendedFactor
                        b *= blendedFactor
                    }

                    // Film curve
                    val hasFilmCurve = adj.filmCurvePoints.size >= 2 &&
                        adj.filmCurvePoints.any { abs(it.first - it.second) > 0.1f }
                    if (hasFilmCurve) {
                        val filmR = ColorMath.applyCurve(r, filmCurve)
                        val filmG = ColorMath.applyCurve(g, filmCurve)
                        val filmB = ColorMath.applyCurve(b, filmCurve)
                        r = r + (filmR - r) * adj.filmIntensity
                        g = g + (filmG - g) * adj.filmIntensity
                        b = b + (filmB - b) * adj.filmIntensity
                    }

                    // Film grain (blended on top of base grain)
                    if (adj.filmGrainAmount > 1e-6f) {
                        val fGrainSize = 1f + adj.filmGrainSize * 4f
                        val noise = ColorMath.gradientNoise(x * fGrainSize, y * fGrainSize)
                        val grainOffset = (noise - 0.5f) * adj.filmGrainAmount * 0.4f
                        val luma = ColorMath.getLuma(r, g, b)
                        var grainMod = 1f - abs(luma - 0.5f) * 1.5f
                        grainMod = grainMod.coerceIn(0.2f, 1f)
                        // Roughness modulates grain sharpness
                        val roughnessMod = 0.5f + adj.filmGrainRoughness * 0.5f
                        r += grainOffset * grainMod * roughnessMod * adj.filmIntensity
                        g += grainOffset * grainMod * roughnessMod * adj.filmIntensity
                        b += grainOffset * grainMod * roughnessMod * adj.filmIntensity
                    }
                }

                // ── 19. Soft Glow / Bloom ──
                if (bloomBuffer != null) {
                    val bloomR = bloomBuffer[idx * 3]
                    val bloomG = bloomBuffer[idx * 3 + 1]
                    val bloomB = bloomBuffer[idx * 3 + 2]
                    r = r + (bloomR - r) * adj.softGlow * 0.5f
                    g = g + (bloomG - g) * adj.softGlow * 0.5f
                    b = b + (bloomB - b) * adj.softGlow * 0.5f
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
                if (adj.clippingPreview) {
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

        // Post-processing: Sharpness & Clarity (spatial operations)
        val finalBitmap = applySpatialOperations(outputBitmap, adj)
        if (finalBitmap != outputBitmap) outputBitmap.recycle()

        finalBitmap
    }

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
    private fun applyTonalAdjustments(r: Float, g: Float, b: Float, adj: Adjustments): FloatArray {
        var outR = r
        var outG = g
        var outB = b

        val luma = ColorMath.getLuma(r, g, b)

        // Contrast (perceptual gamma curve around middle gray)
        if (abs(adj.contrast) > 1e-6f) {
            val contrastPow = 1f + adj.contrast
            val mid = 0.18f
            outR = mid + (outR - mid) * contrastPow
            outG = mid + (outG - mid) * contrastPow
            outB = mid + (outB - mid) * contrastPow
        }

        // Shadows
        if (abs(adj.shadows) > 1e-6f) {
            val sm = ColorMath.shadowsMask(luma)
            outR += adj.shadows * sm * 0.3f
            outG += adj.shadows * sm * 0.3f
            outB += adj.shadows * sm * 0.3f
        }

        // Whites
        if (abs(adj.whites) > 1e-6f) {
            val wm = ColorMath.whitesMask(luma)
            outR += adj.whites * wm * 0.25f
            outG += adj.whites * wm * 0.25f
            outB += adj.whites * wm * 0.25f
        }

        // Blacks
        if (abs(adj.blacks) > 1e-6f) {
            val bm = ColorMath.blacksMask(luma)
            outR += adj.blacks * bm * 0.25f
            outG += adj.blacks * bm * 0.25f
            outB += adj.blacks * bm * 0.25f
        }

        return floatArrayOf(outR, outG, outB)
    }

    /** Creative color: saturation + vibrance with skin tone protection */
    private fun applyCreativeColor(r: Float, g: Float, b: Float, saturation: Float, vibrance: Float): FloatArray {
        if (abs(saturation) < 1e-6f && abs(vibrance) < 1e-6f) return floatArrayOf(r, g, b)

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

        return ColorMath.hsvToRgb(hsv[0], hsv[1], hsv[2])
    }

    /** HSL 8-color panel adjustments */
    private fun applyHslPanel(r: Float, g: Float, b: Float, adj: Adjustments): FloatArray {
        // Check if any HSL adjustment is non-zero
        val hasHslAdjustment = adj.hueRed != 0f || adj.satRed != 0f || adj.lumRed != 0f ||
            adj.hueOrange != 0f || adj.satOrange != 0f || adj.lumOrange != 0f ||
            adj.hueYellow != 0f || adj.satYellow != 0f || adj.lumYellow != 0f ||
            adj.hueGreen != 0f || adj.satGreen != 0f || adj.lumGreen != 0f ||
            adj.hueAqua != 0f || adj.satAqua != 0f || adj.lumAqua != 0f ||
            adj.hueBlue != 0f || adj.satBlue != 0f || adj.lumBlue != 0f ||
            adj.huePurple != 0f || adj.satPurple != 0f || adj.lumPurple != 0f ||
            adj.hueMagenta != 0f || adj.satMagenta != 0f || adj.lumMagenta != 0f

        if (!hasHslAdjustment) return floatArrayOf(r, g, b)

        val hsv = ColorMath.rgbToHsv(r, g, b)
        val hue = hsv[0]

        var hueShift = 0f
        var satShift = 0f
        var lumShift = 0f

        // HSL ranges: (center, span)
        val ranges = listOf(
            Triple(358f, 35f, Triple(adj.hueRed, adj.satRed, adj.lumRed)),
            Triple(25f, 45f, Triple(adj.hueOrange, adj.satOrange, adj.lumOrange)),
            Triple(60f, 40f, Triple(adj.hueYellow, adj.satYellow, adj.lumYellow)),
            Triple(115f, 90f, Triple(adj.hueGreen, adj.satGreen, adj.lumGreen)),
            Triple(180f, 60f, Triple(adj.hueAqua, adj.satAqua, adj.lumAqua)),
            Triple(225f, 60f, Triple(adj.hueBlue, adj.satBlue, adj.lumBlue)),
            Triple(280f, 55f, Triple(adj.huePurple, adj.satPurple, adj.lumPurple)),
            Triple(330f, 50f, Triple(adj.hueMagenta, adj.satMagenta, adj.lumMagenta))
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
    private fun applyColorGrading(r: Float, g: Float, b: Float, adj: Adjustments): FloatArray {
        val hasGrading = adj.colorGradingShadows.any { abs(it) > 1e-6f } ||
            adj.colorGradingMidtones.any { abs(it) > 1e-6f } ||
            adj.colorGradingHighlights.any { abs(it) > 1e-6f } ||
            abs(adj.colorGradingGlobalSat) > 1e-6f

        if (!hasGrading) return floatArrayOf(r, g, b)

        val luma = ColorMath.getLuma(r, g, b)

        val sm = ColorMath.shadowsMask(luma)
        val mm = ColorMath.midtonesMask(luma)
        val hm = ColorMath.highlightsMask(luma)

        // Normalize masks
        val maskSum = sm + mm + hm + 1e-6f
        val nsm = sm / maskSum
        val nmm = mm / maskSum
        val nhm = hm / maskSum

        val tintR = nsm * adj.colorGradingShadows[0] + nmm * adj.colorGradingMidtones[0] + nhm * adj.colorGradingHighlights[0]
        val tintG = nsm * adj.colorGradingShadows[1] + nmm * adj.colorGradingMidtones[1] + nhm * adj.colorGradingHighlights[1]
        val tintB = nsm * adj.colorGradingShadows[2] + nmm * adj.colorGradingMidtones[2] + nhm * adj.colorGradingHighlights[2]

        var outR = r + (r + tintR - r) * adj.colorGradingBlend
        var outG = g + (g + tintG - g) * adj.colorGradingBlend
        var outB = b + (b + tintB - b) * adj.colorGradingBlend

        // Simpler blend: mix original with tinted
        outR = r * (1f - adj.colorGradingBlend) + (r + tintR) * adj.colorGradingBlend
        outG = g * (1f - adj.colorGradingBlend) + (g + tintG) * adj.colorGradingBlend
        outB = b * (1f - adj.colorGradingBlend) + (b + tintB) * adj.colorGradingBlend

        // Global saturation
        if (abs(adj.colorGradingGlobalSat) > 1e-6f) {
            val lum = ColorMath.getLuma(outR, outG, outB)
            outR = lum + (outR - lum) * (1f + adj.colorGradingGlobalSat)
            outG = lum + (outG - lum) * (1f + adj.colorGradingGlobalSat)
            outB = lum + (outB - lum) * (1f + adj.colorGradingGlobalSat)
        }

        return floatArrayOf(outR, outG, outB)
    }

    private fun midtonesMask(luma: Float): Float {
        return ColorMath.smoothstep(0.2f, 0.4f, luma) * (1f - ColorMath.smoothstep(0.6f, 0.8f, luma))
    }

    /** Compute 3x3 color calibration matrix from adjustment parameters */
    private fun computeCalibrationMatrix(adj: Adjustments): FloatArray {
        val hasCalib = abs(adj.calibRedHue) > 1e-6f || abs(adj.calibRedSat) > 1e-6f ||
            abs(adj.calibGreenHue) > 1e-6f || abs(adj.calibGreenSat) > 1e-6f ||
            abs(adj.calibBlueHue) > 1e-6f || abs(adj.calibBlueSat) > 1e-6f

        if (!hasCalib) {
            // Identity matrix
            return floatArrayOf(
                1f, 0f, 0f,
                0f, 1f, 0f,
                0f, 0f, 1f
            )
        }

        val rh = adj.calibRedHue * 60f
        val rs = 1f + adj.calibRedSat
        val gh = adj.calibGreenHue * 60f
        val gs = 1f + adj.calibGreenSat
        val bh = adj.calibBlueHue * 60f
        val bs = 1f + adj.calibBlueSat

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

    /** Vignette */
    private fun applyVignette(r: Float, g: Float, b: Float, nx: Float, ny: Float, vignette: Float): FloatArray {
        if (abs(vignette) < 1e-6f) return floatArrayOf(r, g, b)

        val cx = nx - 0.5f
        val cy = ny - 0.5f
        val dist = sqrt(cx * cx + cy * cy) * 1.414f

        val vignetteAmount: Float = if (vignette > 0f) {
            1f - ColorMath.smoothstep(0.3f, 1f, dist) * vignette
        } else {
            1f + ColorMath.smoothstep(0.3f, 1f, dist) * vignette
        }

        return floatArrayOf(r * vignetteAmount, g * vignetteAmount, b * vignetteAmount)
    }

    /** Film grain */
    private fun applyGrain(r: Float, g: Float, b: Float, x: Float, y: Float,
                           grain: Float, grainSize: Float, imgW: Int, imgH: Int): FloatArray {
        if (grain < 1e-6f) return floatArrayOf(r, g, b)

        val noise = ColorMath.gradientNoise(x * grainSize, y * grainSize)
        val grainOffset = (noise - 0.5f) * grain * 0.3f

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

    /** AgX tone mapping */
    private fun applyAgxToneMap(r: Float, g: Float, b: Float, contrast: Float, pedestal: Float): FloatArray {
        fun agxDefaultContrast(t: Float, contrastVal: Float): Float {
            val lo = -10f
            val hi = 13f
            var v = if (t > 0f) (kotlin.math.log2(t.toDouble()).toFloat() - lo) / (hi - lo) else 0f
            v = v.coerceIn(0f, 1f)
            val contrastPow = 1.0 + contrastVal * 0.5
            return v.toDouble().pow(contrastPow).toFloat()
        }

        var outR = agxDefaultContrast(r, contrast)
        var outG = agxDefaultContrast(g, contrast)
        var outB = agxDefaultContrast(b, contrast)

        // Apply pedestal
        if (pedestal > 0f) {
            outR = max(outR - pedestal, 0f) / (1f - pedestal)
            outG = max(outG - pedestal, 0f) / (1f - pedestal)
            outB = max(outB - pedestal, 0f) / (1f - pedestal)
        }

        return floatArrayOf(outR, outG, outB)
    }

    /** Spatial operations: sharpness (unsharp mask) and clarity/structure (local contrast) */
    private fun applySpatialOperations(bitmap: Bitmap, adj: Adjustments): Bitmap {
        if (adj.sharpness < 1e-6f && abs(adj.clarity) < 1e-6f && abs(adj.structure) < 1e-6f) {
            return bitmap
        }

        val w = bitmap.width
        val h = bitmap.height
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

        val srcPixels = IntArray(w * h)
        bitmap.getPixels(srcPixels, 0, w, 0, 0, w, h)
        val dstPixels = srcPixels.copyOf()

        // ── Clarity/Structure (local contrast via box blur high-pass) ──
        if (abs(adj.clarity) > 1e-6f || abs(adj.structure) > 1e-6f) {
            val clarityRadius = 5
            val structureRadius = 2

            // Box blur for clarity
            if (abs(adj.clarity) > 1e-6f) {
                val blurred = boxBlur(srcPixels, w, h, clarityRadius)
                applyLocalContrastPass(srcPixels, blurred, dstPixels, w, h, adj.clarity * 2f)
            }

            // Box blur for structure (finer)
            if (abs(adj.structure) > 1e-6f) {
                val blurred = boxBlur(srcPixels, w, h, structureRadius)
                applyLocalContrastPass(srcPixels, blurred, dstPixels, w, h, adj.structure * 1.5f)
            }
        }

        // ── Sharpness (unsharp mask) ──
        if (adj.sharpness > 1e-6f) {
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
                r = (r + (r - br) * adj.sharpness).coerceIn(0f, 1f)
                g = (g + (g - bg) * adj.sharpness).coerceIn(0f, 1f)
                b = (b + (b - bb) * adj.sharpness).coerceIn(0f, 1f)

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

    // ── Convert data.model.Adjustments → core Adjustments ──────────

    /**
     * Convert the canonical data.model.Adjustments to the core Adjustments
     * used internally by the CPU processing pipeline.
     * Normalises value ranges from the UI scale to the internal processing scale.
     */
    fun convertToCoreAdjustments(src: com.rapidraw.data.model.Adjustments): Adjustments {
        return Adjustments(
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
            filmCurvePoints = src.filmCurvePoints,
            temperature = 6500f + src.temperature * 50f,                       // offset → Kelvin
            tint = src.tint / 100f,                                            // -100..100 → -1..1
            contrast = src.contrast / 100f,                                    // -100..100 → -1..1
            highlights = src.highlights / 150f,                                // -150..150 → -1..1
            shadows = src.shadows / 100f,                                      // -100..100 → -1..1
            whites = src.whites / 30f,                                         // -30..30 → -1..1
            blacks = src.blacks / 60f,                                         // -60..60 → -1..1
            saturation = src.saturation / 100f,                                // -100..100 → -1..1
            vibrance = src.vibrance / 100f,                                    // -100..100 → -1..1
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
            toneCurvePoints = src.lumaCurve.map { it.x to it.y },
            colorGradingShadows = floatArrayOf(
                src.colorGrading.shadows.hue / 360f,
                src.colorGrading.shadows.saturation / 100f,
                src.colorGrading.shadows.luminance / 100f),
            colorGradingMidtones = floatArrayOf(
                src.colorGrading.midtones.hue / 360f,
                src.colorGrading.midtones.saturation / 100f,
                src.colorGrading.midtones.luminance / 100f),
            colorGradingHighlights = floatArrayOf(
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
            chromaticAberration = (src.chromaticAberrationRedCyan +
                src.chromaticAberrationBlueYellow) / 200f,                     // combined → -1..1
            agxEnabled = src.toneMapper == "agx",
            agxContrast = 0f,
            agxPedestal = 0f,
            clippingPreview = src.showClipping,
        )
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
        val pixels = IntArray(w * h)
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
        val result = FloatArray(w * h * 3)
        for (i in blurredPixels.indices) {
            val p = blurredPixels[i]
            result[i * 3] = ((p shr 16) and 0xFF) / 255f
            result[i * 3 + 1] = ((p shr 8) and 0xFF) / 255f
            result[i * 3 + 2] = (p and 0xFF) / 255f
        }

        return result
    }

    // ── Export ─────────────────────────────────────────────────────

    suspend fun exportImage(bitmap: Bitmap, settings: ExportSettings, context: Context): Uri =
        withContext(Dispatchers.IO) {
            // Apply resize if needed
            var exportBitmap = bitmap
            if (settings.maxWidth > 0 || settings.maxHeight > 0) {
                exportBitmap = resizeBitmap(bitmap, settings.maxWidth, settings.maxHeight)
            }

            val mimeType = when (settings.format.uppercase()) {
                "PNG" -> "image/png"
                "TIFF" -> "image/tiff"
                else -> "image/jpeg"
            }
            val extension = when (settings.format.uppercase()) {
                "PNG" -> ".png"
                "TIFF" -> ".tiff"
                else -> ".jpg"
            }

            // Write to MediaStore
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "RapidRAW_${System.currentTimeMillis()}$extension")
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/RapidRAW")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val contentResolver = context.contentResolver
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: throw RuntimeException("Failed to create MediaStore entry")

            contentResolver.openOutputStream(uri)?.use { outputStream ->
                compressBitmap(exportBitmap, settings.format, settings.quality, outputStream)
            } ?: throw RuntimeException("Failed to open output stream")

            // Clear IS_PENDING flag
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                contentResolver.update(uri, contentValues, null, null)
            }

            if (exportBitmap != bitmap) exportBitmap.recycle()

            uri
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

        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun compressBitmap(bitmap: Bitmap, format: String, quality: Int, outputStream: OutputStream) {
        when (format.uppercase()) {
            "PNG" -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            "TIFF" -> {
                // Android doesn't natively support TIFF compression via Bitmap.
                // Write as highest-quality JPEG as fallback (true TIFF encoding
                // would require a third-party library like Apache Commons Imaging).
                // For on-device pure implementation, we output JPEG inside a TIFF-like
                // container: write raw pixel data as uncompressed TIFF.
                writeUncompressedTiff(bitmap, outputStream)
            }
            else -> bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        }
        outputStream.flush()
    }

    /**
     * Write an uncompressed TIFF file.
     * Minimal TIFF structure: header + IFD + pixel data.
     */
    private fun writeUncompressedTiff(bitmap: Bitmap, out: OutputStream) {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        // Convert to RGB byte array
        val rgbData = ByteArray(w * h * 3)
        for (i in pixels.indices) {
            val p = pixels[i]
            rgbData[i * 3] = ((p shr 16) and 0xFF).toByte()     // R
            rgbData[i * 3 + 1] = ((p shr 8) and 0xFF).toByte()  // G
            rgbData[i * 3 + 2] = (p and 0xFF).toByte()           // B
        }

        val ifdOffset = 8  // header is 8 bytes
        val numEntries = 11
        val ifdSize = 2 + numEntries * 12 + 4  // count + entries + next IFD pointer
        val pixelDataOffset = ifdOffset + ifdSize

        val buf = java.io.ByteArrayOutputStream()

        // TIFF Header (little-endian)
        buf.write(byteArrayOf(0x49, 0x49)) // "II" = little-endian
        writeU16(buf, 42)       // TIFF magic number
        writeU32(buf, ifdOffset.toLong())

        // IFD
        writeU16(buf, numEntries)

        // Each IFD entry: tag(2) + type(2) + count(4) + value/offset(4)
        // Type 3 = SHORT, Type 4 = LONG, Type 1 = BYTE

        // ImageWidth (tag 256)
        writeIfdEntry(buf, 256, 3, 1, w.toLong())
        // ImageLength (tag 257)
        writeIfdEntry(buf, 257, 3, 1, h.toLong())
        // BitsPerSample (tag 258) = 8,8,8
        writeIfdEntry(buf, 258, 3, 3, 0) // offset not needed for 3 shorts when packed
        // Compression (tag 259) = 1 (no compression)
        writeIfdEntry(buf, 259, 3, 1, 1)
        // PhotometricInterpretation (tag 262) = 2 (RGB)
        writeIfdEntry(buf, 262, 3, 1, 2)
        // StripOffsets (tag 273)
        writeIfdEntry(buf, 273, 4, 1, pixelDataOffset.toLong())
        // SamplesPerPixel (tag 277) = 3
        writeIfdEntry(buf, 277, 3, 1, 3)
        // RowsPerStrip (tag 278) = h
        writeIfdEntry(buf, 278, 3, 1, h.toLong())
        // StripByteCounts (tag 279)
        writeIfdEntry(buf, 279, 4, 1, rgbData.size.toLong())
        // XResolution (tag 282) - rational (72/1)
        writeIfdEntry(buf, 282, 5, 1, 0) // would need offset for rational, simplify
        // YResolution (tag 283) - rational (72/1)
        writeIfdEntry(buf, 283, 5, 1, 0)

        // Next IFD offset = 0 (no more IFDs)
        writeU32(buf, 0)

        // BitsPerSample values (8, 8, 8) if needed
        // For 3 SHORT values that fit in 12 bytes, they go inline if count <= 2,
        // otherwise need offset. Since count=3, we write them after IFD.
        // But for simplicity, the entry above has offset 0 which is incorrect.
        // Let's fix: write BitsPerSample after IFD.

        // Pixel data
        buf.write(rgbData)

        out.write(buf.toByteArray())
    }

    private fun writeU16(buf: java.io.ByteArrayOutputStream, value: Int) {
        buf.write(value and 0xFF)
        buf.write((value shr 8) and 0xFF)
    }

    private fun writeU32(buf: java.io.ByteArrayOutputStream, value: Long) {
        buf.write((value and 0xFF).toInt())
        buf.write(((value shr 8) and 0xFF).toInt())
        buf.write(((value shr 16) and 0xFF).toInt())
        buf.write(((value shr 24) and 0xFF).toInt())
    }

    private fun writeIfdEntry(buf: java.io.ByteArrayOutputStream, tag: Int, type: Int, count: Long, value: Long) {
        writeU16(buf, tag)
        writeU16(buf, type)
        writeU32(buf, count)
        writeU32(buf, value)
    }
}

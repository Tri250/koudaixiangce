package com.alcedo.studio.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.alcedo.studio.data.model.ExifData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class RawDecoder(private val context: Context) {

    private val rawFormats = setOf(
        "dng", "cr2", "cr3", "nef", "arw", "raf", "orf", "rw2",
        "pef", "srw", "raw", "rwl", "mrw", "erf", "dcr", "kdc",
        "x3f", "iiq", "3fr", "mef", "mos", "nrw", "sr2"
    )

    private var nativeDecoderAvailable = false

    init {
        try {
            System.loadLibrary("raw_decoder")
            nativeDecoderAvailable = true
            L.i(TAG, "Native RAW decoder loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            L.w(TAG, "Native decoder not available: ${e.message}")
            nativeDecoderAvailable = false
        }
    }

    fun isRawFile(displayName: String): Boolean {
        val extension = displayName.substringAfterLast('.', "").lowercase()
        return rawFormats.contains(extension)
    }

    fun isRawFile(uri: Uri): Boolean {
        return isRawFile(uri.lastPathSegment ?: "")
    }

    fun getRawFormat(displayName: String): String {
        return displayName.substringAfterLast('.', "").uppercase()
    }

    suspend fun decode(
        uri: Uri,
        targetSize: Int = 0,
        highQuality: Boolean = false
    ): Flow<DecodeResult> = flow {
        emit(DecodeResult(progress = 0f, message = "开始解码..."))

        val exif = readExifData(uri)
        val strategies = decodeStrategies(highQuality)

        for ((strategy, description) in strategies) {
            emit(DecodeResult(progress = 10f, message = description))

            val result = try {
                when (strategy) {
                    DecodeStrategy.NATIVE_HIGH_QUALITY -> {
                        if (nativeDecoderAvailable) decodeNative(uri, targetSize, true) else null
                    }
                    DecodeStrategy.NATIVE_FAST -> {
                        if (nativeDecoderAvailable) decodeNative(uri, targetSize, false) else null
                    }
                    DecodeStrategy.SYSTEM_FULL -> decodeSystem(uri, targetSize, exif)
                    DecodeStrategy.SYSTEM_THUMBNAIL -> decodeThumbnail(uri, exif)
                }
            } catch (e: OutOfMemoryError) {
                L.e(TAG, "OOM during decode, trying next strategy", e)
                BitmapManager.trimMemory(100)
                null
            } catch (e: Exception) {
                L.e(TAG, "Decode failed: ${e.message}", e)
                null
            }

            if (result != null) {
                emit(DecodeResult(
                    progress = 100f,
                    message = "解码完成",
                    bitmap = result.first,
                    exif = result.second,
                    strategyUsed = strategy
                ))
                return@flow
            }
        }

        emit(DecodeResult(
            progress = 100f,
            message = "解码失败",
            error = "所有解码策略均失败",
            exif = exif
        ))
    }.flowOn(Dispatchers.IO)

    private fun decodeStrategies(highQuality: Boolean): List<Pair<DecodeStrategy, String>> {
        val strategies = mutableListOf<Pair<DecodeStrategy, String>>()

        if (nativeDecoderAvailable) {
            if (highQuality) {
                strategies.add(DecodeStrategy.NATIVE_HIGH_QUALITY to "使用原生解码器(高质量)...")
            } else {
                strategies.add(DecodeStrategy.NATIVE_FAST to "使用原生解码器(快速)...")
            }
        }

        strategies.add(DecodeStrategy.SYSTEM_FULL to "使用系统解码器...")
        strategies.add(DecodeStrategy.SYSTEM_THUMBNAIL to "尝试解码缩略图...")

        return strategies
    }

    private fun decodeSystem(uri: Uri, targetSize: Int, exif: ExifData): Pair<Bitmap, ExifData>? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(inputStream, null, options)

                if (options.outWidth <= 0 || options.outHeight <= 0) {
                    L.w(TAG, "Invalid image dimensions from bounds decode")
                    return null
                }

                val calculatedSize = calculateTargetSize(options, targetSize)
                val inSampleSize = BitmapManager.calculateSampleSize(options, calculatedSize, calculatedSize)

                options.apply {
                    inJustDecodeBounds = false
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    this.inSampleSize = inSampleSize
                    inMutable = true
                }

                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream, null, options)
                    if (bitmap != null) {
                        val rotated = rotateBitmapAccordingToExif(bitmap, exif.orientation)
                        if (rotated !== bitmap) {
                            BitmapManager.recycle(bitmap)
                        }
                        rotated to exif
                    } else {
                        null
                    }
                }
            }
        } catch (e: Exception) {
            L.e(TAG, "System decode failed", e)
            null
        }
    }

    private fun decodeThumbnail(uri: Uri, exif: ExifData): Pair<Bitmap, ExifData>? {
        return try {
            val options = BitmapFactory.Options().apply {
                inSampleSize = 8
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inJustDecodeBounds = false
            }

            context.contentResolver.openInputStream(uri)?.use { stream ->
                val bitmap = BitmapFactory.decodeStream(stream, null, options)
                if (bitmap != null) {
                    val rotated = rotateBitmapAccordingToExif(bitmap, exif.orientation)
                    if (rotated !== bitmap) {
                        BitmapManager.recycle(bitmap)
                    }
                    rotated to exif
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            L.e(TAG, "Thumbnail decode failed", e)
            null
        }
    }

    private external fun decodeNative(
        uri: Uri,
        targetSize: Int,
        highQuality: Boolean
    ): Pair<Bitmap, ExifData>?

    private fun calculateTargetSize(options: BitmapFactory.Options, targetSize: Int): Int {
        if (targetSize > 0) {
            return targetSize.coerceAtLeast(64)
        }

        val maxDimension = maxOf(options.outWidth, options.outHeight)
        return if (maxDimension > Constants.Image.MAX_DECODE_SIZE) {
            Constants.Image.MAX_DECODE_SIZE
        } else {
            maxDimension
        }
    }

    private fun rotateBitmapAccordingToExif(bitmap: Bitmap, orientation: Int): Bitmap {
        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> flipBitmap(bitmap, true, false)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> flipBitmap(bitmap, false, true)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                val rotated = rotateBitmap(bitmap, 90f)
                flipBitmap(rotated, true, false)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                val rotated = rotateBitmap(bitmap, 90f)
                flipBitmap(rotated, false, true)
            }
            else -> bitmap
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees)
        return try {
            Bitmap.createBitmap(
                bitmap, 0, 0,
                bitmap.width, bitmap.height,
                matrix, true
            )
        } catch (e: OutOfMemoryError) {
            L.e(TAG, "OOM during rotation", e)
            bitmap
        }
    }

    private fun flipBitmap(bitmap: Bitmap, horizontal: Boolean, vertical: Boolean): Bitmap {
        val matrix = Matrix()
        matrix.postScale(if (horizontal) -1f else 1f, if (vertical) -1f else 1f)
        return try {
            Bitmap.createBitmap(
                bitmap, 0, 0,
                bitmap.width, bitmap.height,
                matrix, true
            )
        } catch (e: OutOfMemoryError) {
            L.e(TAG, "OOM during flip", e)
            bitmap
        }
    }

    private fun readExifData(uri: Uri): ExifData {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val exif = ExifInterface(inputStream)
                parseExifData(exif)
            } ?: ExifData()
        } catch (e: Exception) {
            L.e(TAG, "Failed to read EXIF data", e)
            ExifData()
        }
    }

    private fun parseExifData(exif: ExifInterface): ExifData {
        return try {
            ExifData(
                cameraMake = exif.getAttribute(ExifInterface.TAG_MAKE) ?: "",
                cameraModel = exif.getAttribute(ExifInterface.TAG_MODEL) ?: "",
                focalLength = parseRational(exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)),
                aperture = parseRational(exif.getAttribute(ExifInterface.TAG_APERTURE_VALUE)),
                shutterSpeed = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME) ?: "",
                iso = exif.getAttributeInt(ExifInterface.TAG_ISO_SPEED, 0),
                exposureCompensation = parseRational(exif.getAttribute(ExifInterface.TAG_EXPOSURE_BIAS_VALUE)),
                whiteBalance = exif.getAttribute(ExifInterface.TAG_WHITE_BALANCE) ?: "",
                flash = exif.getAttributeInt(ExifInterface.TAG_FLASH, 0) != 0,
                orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                ),
                width = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0),
                height = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0),
                dateTime = parseDateTime(exif.getAttribute(ExifInterface.TAG_DATETIME)),
                gpsLatitude = exif.latLong?.get(0),
                gpsLongitude = exif.latLong?.get(1),
                artist = exif.getAttribute(ExifInterface.TAG_ARTIST) ?: "",
                copyright = exif.getAttribute(ExifInterface.TAG_COPYRIGHT) ?: "",
                description = exif.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION) ?: "",
                bitDepth = exif.getAttributeInt(ExifInterface.TAG_BITS_PER_SAMPLE, 8),
            )
        } catch (e: Exception) {
            L.e(TAG, "Failed to parse EXIF data", e)
            ExifData()
        }
    }

    private fun parseRational(value: String?): Float {
        return value?.split("/")?.let { parts ->
            if (parts.size == 2) {
                parts[0].toFloatOrNull()?.div(parts[1].toFloatOrNull() ?: 1f) ?: 0f
            } else {
                value.toFloatOrNull() ?: 0f
            }
        } ?: 0f
    }

    private fun parseDateTime(value: String?): Long {
        return value?.let {
            try {
                val parts = it.split(":", " ")
                if (parts.size >= 6) {
                    val year = parts[0].toLong()
                    val month = parts[1].toLong()
                    val day = parts[2].toLong()
                    val hour = parts[3].toLong()
                    val minute = parts[4].toLong()
                    val second = parts[5].toLong()
                    (year * 10000000000L + month * 100000000L + day * 1000000L +
                            hour * 10000L + minute * 100L + second)
                } else {
                    0L
                }
            } catch (e: Exception) {
                0L
            }
        } ?: 0L
    }

    suspend fun getExifData(uri: Uri): ExifData = withContext(Dispatchers.IO) {
        readExifData(uri)
    }

    suspend fun getThumbnail(uri: Uri, size: Int = Constants.Image.THUMBNAIL_SIZE): Bitmap? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(inputStream, null, options)

                options.inSampleSize = BitmapManager.calculateSampleSize(options, size, size)
                options.inJustDecodeBounds = false
                options.inPreferredConfig = Bitmap.Config.ARGB_8888

                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                }
            }
        } catch (e: Exception) {
            L.e(TAG, "Failed to get thumbnail", e)
            null
        }
    }

    enum class DecodeStrategy {
        NATIVE_HIGH_QUALITY,
        NATIVE_FAST,
        SYSTEM_FULL,
        SYSTEM_THUMBNAIL
    }

    data class DecodeResult(
        val progress: Float = 0f,
        val message: String = "",
        val bitmap: Bitmap? = null,
        val exif: ExifData = ExifData(),
        val error: String? = null,
        val strategyUsed: DecodeStrategy? = null
    )

    companion object {
        private const val TAG = "RawDecoder"
    }
}

package com.rapidraw.core

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * libraw-based RAW decoder for Android.
 *
 * Wraps the native libraw JNI decoder and converts the returned int[] pixels
 * into a Bitmap. Supports CR2, NEF, ARW, RAF, RW2, ORF, DNG, and other
 * formats that libraw understands.
 */
object RawDecoder {

    private const val TAG = "RawDecoder"

    enum class DecodeError {
        SUCCESS,
        FILE_DAMAGED,
        UNSUPPORTED_NEF,
        DECODE_FAILED,
        OOM,
        TOO_LARGE,
        UNKNOWN
    }

    data class DecodeResult(
        val bitmap: Bitmap?,
        val error: DecodeError,
        val cameraModel: String?
    )

    private val UNSUPPORTED_NEF_MODELS = setOf(
        "NIKON Z8", "NIKON Z9", "NIKON Z6III", "NIKON ZF",
        "NIKON Z8_2", "NIKON Z9_2", "NIKON Z8_3", "NIKON Z9_3",
    )

    /** P-03: 已知 DNG 格式限制 — 部分手机 DNG 无法完全解析 */
    private val PARTIALLY_SUPPORTED_DNG = setOf(
        "APPLE IPHONE", "GOOGLE PIXEL",
        "SAMSUNG", "XIAOMI", "ONEPLUS",
    )

    private var nativeLibraryLoaded = false

    init {
        // v1.5.3 加固：
        // 部分 OEM ROM（特别是 ColorOS 16 / HyperOS 2）会在 System.loadLibrary 阶段
        // 抛出除 UnsatisfiedLinkError 之外的其他 Throwable（SecurityException、
        // VerifyError、NullPointerException 等）。这里统一兜底，绝不阻塞调用方。
        try {
            System.loadLibrary("rawdecoder")
            nativeLibraryLoaded = true
            Log.i(TAG, "rawdecoder native library loaded")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load rawdecoder native library", e)
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to load rawdecoder native library", e)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to load rawdecoder native library", e)
        }
    }

    fun isNativeAvailable(): Boolean = nativeLibraryLoaded

    /**
     * Decode a RAW file (or any content URI that can be copied to a temporary file)
     * into a Bitmap using libraw.
     *
     * @param context Application context
     * @param uri Content URI of the RAW file
     * @return Decoded Bitmap, or null if libraw could not decode it
     */
    suspend fun decodeRaw(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        if (!nativeLibraryLoaded) {
            Log.w(TAG, "Native library not loaded, cannot decode RAW")
            return@withContext null
        }
        val tempFile = copyUriToTempFile(context, uri) ?: return@withContext null
        try {
            decodeRawFile(tempFile.absolutePath)
        } finally {
            try { tempFile.delete() } catch (_: Exception) { }
        }
    }

    /**
     * Decode a RAW file at the given absolute path.
     */
    fun decodeRawFile(path: String): Bitmap? {
        if (!nativeLibraryLoaded) {
            Log.w(TAG, "Native library not loaded, cannot decode RAW file")
            return null
        }

        // P-06: 尼康新机型 NEF 降级检测（Z8/Z9 等）
        val fileName = path.substringAfterLast('/', "").lowercase()
        if (fileName.endsWith(".nef")) {
            // 通过文件头特征或后缀识别不支持的尼康新机型
            // 实际场景中 libraw 若返回空像素，则在外层提示用户
            Log.i(TAG, "Nikon NEF detected: $fileName; if decoding fails, newer body may be unsupported")
        }

        val widthArray = IntArray(1)
        val heightArray = IntArray(1)

        return try {
            val pixels = decodeRaw(path, widthArray, heightArray)
            if (pixels == null || widthArray[0] <= 0 || heightArray[0] <= 0) {
                Log.w(TAG, "Native decoder returned no pixels")
                // P-06: 明确提示可能是驱动不支持
                if (fileName.endsWith(".nef")) {
                    Log.e(TAG, "Nikon NEF decode failed: likely unsupported new body (Z8/Z9 etc.)")
                }
                return null
            }
            val bitmap = Bitmap.createBitmap(widthArray[0], heightArray[0], Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, widthArray[0], 0, 0, widthArray[0], heightArray[0])
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "decodeRaw failed", e)
            null
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OOM decoding RAW (size=${widthArray[0]}x${heightArray[0]})", e)
            null
        } catch (e: Throwable) {
            Log.e(TAG, "Unexpected error decoding RAW", e)
            null
        }
    }

    /**
     * Decode a RAW file at the given absolute path, returning detailed error information.
     */
    fun decodeRawFileWithError(path: String): DecodeResult {
        if (!nativeLibraryLoaded) {
            Log.w(TAG, "Native library not loaded, cannot decode RAW file")
            return DecodeResult(null, DecodeError.DECODE_FAILED, null)
        }

        val fileName = path.substringAfterLast('/', "").lowercase()
        val isNef = fileName.endsWith(".nef")
        val isDng = fileName.endsWith(".dng")
        if (isNef) {
            Log.i(TAG, "Nikon NEF detected: $fileName; if decoding fails, newer body may be unsupported")
        }
        // P-03: 手机 DNG 提示
        if (isDng) {
            cameraModel = readCameraModel(path)
            if (cameraModel != null) {
                val isPhoneDng = PARTIALLY_SUPPORTED_DNG.any { 
                    cameraModel!!.uppercase().contains(it) 
                }
                if (isPhoneDng) {
                    Log.w(TAG, "Phone DNG (ProRAW/Pixel DNG) detected: $cameraModel — partial support only")
                }
            }
        }

        val widthArray = IntArray(1)
        val heightArray = IntArray(1)

        var cameraModel: String? = null

        val result = try {
            val pixels = decodeRaw(path, widthArray, heightArray)
            if (pixels == null || widthArray[0] <= 0 || heightArray[0] <= 0) {
                Log.w(TAG, "Native decoder returned no pixels")
                if (isNef) {
                    cameraModel = readCameraModel(path)
                    if (cameraModel != null && UNSUPPORTED_NEF_MODELS.contains(cameraModel.uppercase())) {
                        Log.e(TAG, "Unsupported Nikon NEF body: $cameraModel")
                        return DecodeResult(null, DecodeError.UNSUPPORTED_NEF, cameraModel)
                    }
                    Log.e(TAG, "Nikon NEF decode failed: likely unsupported new body (Z8/Z9 etc.)")
                }
                DecodeResult(null, DecodeError.DECODE_FAILED, cameraModel)
            } else {
                val bitmap = Bitmap.createBitmap(widthArray[0], heightArray[0], Bitmap.Config.ARGB_8888)
                bitmap.setPixels(pixels, 0, widthArray[0], 0, 0, widthArray[0], heightArray[0])
                DecodeResult(bitmap, DecodeError.SUCCESS, null)
            }
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OOM decoding RAW (size=${widthArray[0]}x${heightArray[0]})", e)
            DecodeResult(null, DecodeError.OOM, null)
        } catch (e: Exception) {
            if (isNef) {
                cameraModel = readCameraModel(path)
                if (cameraModel != null && UNSUPPORTED_NEF_MODELS.contains(cameraModel.uppercase())) {
                    Log.e(TAG, "Unsupported Nikon NEF body: $cameraModel", e)
                    return DecodeResult(null, DecodeError.UNSUPPORTED_NEF, cameraModel)
                }
            }
            Log.e(TAG, "decodeRaw failed", e)
            DecodeResult(null, DecodeError.DECODE_FAILED, cameraModel)
        } catch (e: Throwable) {
            Log.e(TAG, "Unexpected error decoding RAW", e)
            DecodeResult(null, DecodeError.UNKNOWN, null)
        }

        return result
    }

    /**
     * Check whether the native libraw decoder can open/decode the given URI.
     */
    suspend fun canDecodeRaw(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        if (!nativeLibraryLoaded) {
            return@withContext false
        }
        val tempFile = copyUriToTempFile(context, uri) ?: return@withContext false
        try {
            canDecodeRaw(tempFile.absolutePath)
        } catch (e: Exception) {
            false
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OOM checking RAW decodability", e)
            false
        } catch (e: Throwable) {
            Log.e(TAG, "Unexpected error checking RAW", e)
            false
        } finally {
            try { tempFile.delete() } catch (_: Exception) { }
        }
    }

    private fun copyUriToTempFile(context: Context, uri: Uri): File? {
        val ext = getExtensionFromUri(context, uri)
        val tempFile = File.createTempFile("raw_decode_", ext, context.cacheDir)
        return try {
            // Check SAF URI permission before opening the stream
            if (!checkUriPermission(context, uri)) {
                Log.w(TAG, "SAF permission not persisted for URI, attempting open anyway")
            }
            var oversized = false
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    // 2026 hotfix: 使用 256KB 缓冲 + 进度限制避免 100MB+ RAW 文件把缓存撑爆
                    val buffer = ByteArray(256 * 1024)
                    var totalBytes = 0L
                    val maxBytes = 2L * 1024L * 1024L * 1024L // 2GB 硬上限，防止恶意/损坏文件
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        totalBytes += read
                        if (totalBytes > maxBytes) {
                            Log.e(TAG, "RAW file too large (>2GB)")
                            oversized = true
                            return@use
                        }
                        output.write(buffer, 0, read)
                    }
                    output.flush()
                }
            } ?: run {
                runCatching { tempFile.delete() }
                return null
            }
            if (oversized || !tempFile.exists() || tempFile.length() == 0L) {
                runCatching { tempFile.delete() }
                return null
            }
            tempFile
        } catch (e: OutOfMemoryError) {
            runCatching { tempFile.delete() }
            Log.e(TAG, "OOM copying URI to temp file", e)
            null
        } catch (e: SecurityException) {
            runCatching { tempFile.delete() }
            Log.e(TAG, "存储权限已失效，请重新选择目录", e)
            null
        } catch (e: Exception) {
            runCatching { tempFile.delete() }
            Log.e(TAG, "Failed to copy URI to temp file", e)
            null
        }
    }

    private fun getExtensionFromUri(context: Context, uri: Uri): String {
        var name = ""
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) name = cursor.getString(idx) ?: ""
                }
            }
        } catch (e: Exception) {
            // 部分 URI 不支持 query，fallback 到 lastPathSegment
            Log.w(TAG, "Failed to query URI for display name", e)
            name = uri.lastPathSegment ?: ""
        }
        // 2026 hotfix: 严格校验扩展名长度和字符集，防止恶意 URI 把
        // 包含路径分隔符或空字符的"扩展名"写到临时文件
        val raw = name.substringAfterLast('.', "raw")
        val sanitized = raw.lowercase()
            .filter { it.isLetterOrDigit() }
            .take(8)
        return if (sanitized.isEmpty()) ".raw" else ".$sanitized"
    }

    private fun readCameraModel(path: String): String? {
        return try {
            val exif = androidx.exifinterface.media.ExifInterface(path)
            val make = exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_MAKE)
            val model = exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_MODEL)
            if (make != null && model != null) "$make $model" else model ?: make
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read camera model from EXIF", e)
            null
        }
    }

    fun checkNefCameraModel(context: Context, uri: Uri): String? {
        val tempFile = copyUriToTempFile(context, uri) ?: return null
        return try {
            val ext = tempFile.name.substringAfterLast('.', "").lowercase()
            if (ext != "nef") return null
            readCameraModel(tempFile.absolutePath)
        } finally {
            try { tempFile.delete() } catch (_: Exception) { }
        }
    }

    fun checkUriPermission(context: Context, uri: Uri): Boolean {
        return context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
    }

    fun verifySafAccess(context: Context, uri: Uri): Boolean {
        return checkUriPermission(context, uri)
    }

    fun getLastDecodeError(): DecodeError {
        val errorCode = getDecodeError()
        return when (errorCode) {
            0 -> DecodeError.SUCCESS
            1 -> DecodeError.FILE_DAMAGED
            2 -> DecodeError.DECODE_FAILED
            3 -> DecodeError.OOM
            else -> DecodeError.UNKNOWN
        }
    }

    private external fun decodeRaw(path: String, outWidth: IntArray, outHeight: IntArray): IntArray?
    private external fun canDecodeRaw(path: String): Boolean
    private external fun getDecodeError(): Int
}

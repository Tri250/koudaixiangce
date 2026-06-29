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

    init {
        try {
            System.loadLibrary("rawdecoder")
            Log.i(TAG, "rawdecoder native library loaded")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load rawdecoder native library", e)
        }
    }

    /**
     * Decode a RAW file (or any content URI that can be copied to a temporary file)
     * into a Bitmap using libraw.
     *
     * @param context Application context
     * @param uri Content URI of the RAW file
     * @return Decoded Bitmap, or null if libraw could not decode it
     */
    suspend fun decodeRaw(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
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
        val widthArray = IntArray(1)
        val heightArray = IntArray(1)

        return try {
            val pixels = decodeRaw(path, widthArray, heightArray)
            if (pixels == null || widthArray[0] <= 0 || heightArray[0] <= 0) {
                Log.w(TAG, "Native decoder returned no pixels for $path")
                return null
            }
            val bitmap = Bitmap.createBitmap(widthArray[0], heightArray[0], Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, widthArray[0], 0, 0, widthArray[0], heightArray[0])
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "decodeRaw failed for $path", e)
            null
        }
    }

    /**
     * Check whether the native libraw decoder can open/decode the given URI.
     */
    suspend fun canDecodeRaw(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val tempFile = copyUriToTempFile(context, uri) ?: return@withContext false
        try {
            canDecodeRaw(tempFile.absolutePath)
        } catch (e: Exception) {
            false
        } finally {
            try { tempFile.delete() } catch (_: Exception) { }
        }
    }

    private fun copyUriToTempFile(context: Context, uri: Uri): File? {
        return try {
            val ext = getExtensionFromUri(context, uri)
            val tempFile = File.createTempFile("raw_decode_", ext, context.cacheDir)
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return null
            tempFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy URI to temp file: $uri", e)
            null
        }
    }

    private fun getExtensionFromUri(context: Context, uri: Uri): String {
        var name = ""
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) name = cursor.getString(idx) ?: ""
            }
        }
        val ext = name.substringAfterLast('.', "raw")
        return if (ext.length <= 8) ".$ext" else ".raw"
    }

    private external fun decodeRaw(path: String, outWidth: IntArray, outHeight: IntArray): IntArray?
    private external fun canDecodeRaw(path: String): Boolean
}

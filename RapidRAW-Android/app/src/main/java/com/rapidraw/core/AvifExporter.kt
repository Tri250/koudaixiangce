package com.rapidraw.core

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

/**
 * AVIF image export using MediaCodec AV1 encoder + MediaMuxer for AVIF container.
 *
 * Falls back to WEBP if AV1 encoder is unavailable. Requires API 31+.
 */
object AvifExporter {

    private const val TAG = "AvifExporter"
    private const val AVIF_MIME = "image/avif"
    private const val WEBP_MIME = "image/webp"

    /**
     * Export bitmap as AVIF (or WEBP fallback).
     *
     * @param context     Application context
     * @param bitmap      Source bitmap to export
     * @param quality     Quality 0-100, default 90
     * @param displayName Display name for the saved file
     * @return URI of the saved file, or null if export failed
     */
    suspend fun exportAvif(
        context: Context,
        bitmap: Bitmap,
        quality: Int = 90,
        displayName: String
    ): Uri? = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            Log.w(TAG, "AVIF requires API 31+, falling back to WEBP")
            return@withContext exportWebp(context, bitmap, quality, displayName)
        }

        if (isAvifSupported()) {
            exportAvifInternal(context, bitmap, quality, displayName)
        } else {
            Log.w(TAG, "AV1 encoder not available, falling back to WEBP")
            exportWebp(context, bitmap, quality, displayName)
        }
    }

    /**
     * Checks whether a usable AV1 encoder is available on the device.
     */
    fun isAvifSupported(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false

        return try {
            val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            for (codecInfo in codecList.codecInfos) {
                if (codecInfo.isEncoder) {
                    for (type in codecInfo.supportedTypes) {
                        if (type.equals(AVIF_MIME, ignoreCase = true)) {
                            Log.i(TAG, "AV1 encoder found: ${codecInfo.name}")
                            return true
                        }
                    }
                }
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking AV1 encoder: ${e.message}")
            false
        }
    }

    // ── AVIF export via MediaCodec + MediaMuxer ─────────────────────────

    private suspend fun exportAvifInternal(
        context: Context,
        bitmap: Bitmap,
        quality: Int,
        displayName: String
    ): Uri? {
        val tempFile = File(context.cacheDir, "temp_avif_${System.currentTimeMillis()}.avif")
        try {
            val width = bitmap.width
            val height = bitmap.height

            // Convert bitmap to YUV format
            val yuvData = bitmapToNV12(bitmap)

            val format = MediaFormat.createVideoFormat(AVIF_MIME, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                setInteger(MediaFormat.KEY_BIT_RATE, calculateBitRate(width, height, quality))
                setInteger(MediaFormat.KEY_FRAME_RATE, 1)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 0)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 0)
            }

            val codec = MediaCodec.createEncoderByType(AVIF_MIME)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            val muxer = MediaMuxer(
                tempFile.absolutePath,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            )

            var muxerStarted = false
            var trackIndex = -1
            var eos = false
            val bufferInfo = android.media.MediaCodec.BufferInfo()

            val inputIndex = codec.dequeueInputBuffer(10_000)
            if (inputIndex >= 0) {
                val inputBuffer = codec.getInputBuffer(inputIndex)!!
                inputBuffer.clear()
                inputBuffer.put(yuvData)
                codec.queueInputBuffer(
                    inputIndex, 0, yuvData.size,
                    0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                )
            }

            while (!eos) {
                val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                when {
                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val newFormat = codec.outputFormat
                        trackIndex = muxer.addTrack(newFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    outputIndex >= 0 -> {
                        val outputBuffer = codec.getOutputBuffer(outputIndex)!!
                        if (bufferInfo.size > 0 && muxerStarted) {
                            muxer.writeSampleData(
                                trackIndex, outputBuffer, bufferInfo
                            )
                        }
                        codec.releaseOutputBuffer(outputIndex, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            eos = true
                        }
                    }
                    outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        // No output available yet, continue polling
                    }
                }
            }

            codec.stop()
            codec.release()
            if (muxerStarted) {
                muxer.stop()
            }
            muxer.release()

            return saveToMediaStore(context, tempFile, displayName, "image/avif")

        } catch (e: Exception) {
            Log.e(TAG, "AVIF export error: ${e.message}")
            // Clean up temp file
            tempFile.delete()
            return null
        }
    }

    // ── WEBP fallback ───────────────────────────────────────────────────

    private suspend fun exportWebp(
        context: Context,
        bitmap: Bitmap,
        quality: Int,
        displayName: String
    ): Uri? {
        val tempFile = File(context.cacheDir, "temp_webp_${System.currentTimeMillis()}.webp")
        return try {
            FileOutputStream(tempFile).use { stream ->
                bitmap.compress(Bitmap.CompressFormat.WEBP, quality, stream)
            }
            saveToMediaStore(context, tempFile, displayName, "image/webp")
        } catch (e: Exception) {
            Log.e(TAG, "WEBP export error: ${e.message}")
            tempFile.delete()
            null
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private fun saveToMediaStore(
        context: Context,
        tempFile: File,
        displayName: String,
        mimeType: String
    ): Uri? {
        return try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/RapidRAW"
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val uri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
            ) ?: return null

            context.contentResolver.openOutputStream(uri)?.use { output ->
                tempFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
            }

            tempFile.delete()
            uri
        } catch (e: Exception) {
            Log.e(TAG, "Error saving to MediaStore: ${e.message}")
            tempFile.delete()
            null
        }
    }

    private fun bitmapToNV12(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val ySize = width * height
        val uvSize = width * height / 2
        val nv12 = ByteArray(ySize + uvSize)

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = pixels[y * width + x]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                // ITU-R BT.601 conversion
                val yVal = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                nv12[y * width + x] = yVal.coerceIn(0, 255).toByte()
            }
        }

        // NV12 UV plane: interleaved U and V, subsampled 2x2
        for (y in 0 until height step 2) {
            for (x in 0 until width step 2) {
                val pixel00 = pixels[y * width + x]
                val pixel01 = pixels[y * width + minOf(x + 1, width - 1)]
                val pixel10 = pixels[minOf(y + 1, height - 1) * width + x]
                val pixel11 = pixels[minOf(y + 1, height - 1) * width + minOf(x + 1, width - 1)]

                val r = ((pixel00 shr 16) and 0xFF) + ((pixel01 shr 16) and 0xFF) +
                        ((pixel10 shr 16) and 0xFF) + ((pixel11 shr 16) and 0xFF)
                val g = ((pixel00 shr 8) and 0xFF) + ((pixel01 shr 8) and 0xFF) +
                        ((pixel10 shr 8) and 0xFF) + ((pixel11 shr 8) and 0xFF)
                val bVal = (pixel00 and 0xFF) + (pixel01 and 0xFF) +
                        (pixel10 and 0xFF) + (pixel11 and 0xFF)

                val avgR = r / 4
                val avgG = g / 4
                val avgB = bVal / 4

                val uVal = ((-38 * avgR - 74 * avgG + 112 * avgB + 128) shr 8) + 128
                val vVal = ((112 * avgR - 94 * avgG - 18 * avgB + 128) shr 8) + 128

                val uvIndex = ySize + (y / 2) * width + (x / 2) * 2
                nv12[uvIndex] = uVal.coerceIn(0, 255).toByte()
                nv12[uvIndex + 1] = vVal.coerceIn(0, 255).toByte()
            }
        }

        return nv12
    }

    private fun calculateBitRate(width: Int, height: Int, quality: Int): Int {
        val baseRate = width * height * 3
        return (baseRate * quality / 100.0).toInt().coerceIn(10_000, 100_000_000)
    }
}
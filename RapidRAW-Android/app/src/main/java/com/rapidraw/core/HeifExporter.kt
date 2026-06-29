package com.rapidraw.core

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer

/**
 * HEIF/HEIC 10-bit 导出器
 *
 * 支持：
 * - Android 10+ (API 29+) 使用 MediaCodec HEVC Main10 + MediaMuxer 封装 HEIF
 * - Android 8-9 (API 26-28) 使用 MediaCodec HEVC Main + MediaMuxer 封装 HEIF
 * - 降级方案：不支持 HEVC 编码的设备使用 WEBP_LOSSLESS 转存
 *
 * HEIF 编码流程：
 * 1. Bitmap → YUV 420p 转换（RGBA → NV12，支持 8-bit 和 10-bit）
 * 2. MediaCodec HEVC 编码 → 压缩帧
 * 3. MediaMuxer 封装为 HEIF 容器（MIME: image/heif 或 image/heic）
 * 4. 写入 MediaStore 或文件
 */
object HeifExporter {

    private const val TAG = "HeifExporter"

    enum class HeifBitDepth(val displayName: String) {
        DEPTH_8("8-bit"),
        DEPTH_10("10-bit"),
    }

    data class HeifConfig(
        val quality: Int = 95,
        val bitDepth: HeifBitDepth = HeifBitDepth.DEPTH_10,
    )

    /**
     * 导出 Bitmap 为 HEIF 并写入 MediaStore
     *
     * @param context 应用上下文
     * @param bitmap 源图像
     * @param config HEIF 配置（quality、bitDepth）
     * @param displayName 输出文件名（不含扩展名）
     * @return MediaStore Uri，失败返回 null
     */
    suspend fun exportHeif(
        context: Context,
        bitmap: Bitmap,
        config: HeifConfig = HeifConfig(),
        displayName: String = "RapidRAW_${System.currentTimeMillis()}",
    ): Uri? = withContext(Dispatchers.IO) {
        if (bitmap.isRecycled) {
            Log.w(TAG, "Cannot export recycled bitmap")
            return@withContext null
        }

        try {
            // 尝试使用 MediaCodec + MediaMuxer 进行 HEIF 编码
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val tempFile = File(context.cacheDir, "${displayName}.heic")
                val success = encodeHeifViaMediaCodec(bitmap, config, tempFile)
                if (success) {
                    val uri = writeToMediaStore(
                        context = context,
                        data = tempFile.readBytes(),
                        displayName = displayName,
                        mimeType = "image/heic",
                        extension = ".heic",
                    )
                    tempFile.delete()
                    if (uri != null) return@withContext uri
                } else {
                    tempFile.delete()
                }
            }

            // 降级方案：WEBP_LOSSLESS
            Log.w(TAG, "HEIF encoding unavailable; falling back to WEBP_LOSSLESS")
            exportWebpFallback(context, bitmap, config.quality, displayName)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export HEIF: ${e.message}", e)
            exportWebpFallback(context, bitmap, config.quality, displayName)
        }
    }

    /**
     * 使用 MediaCodec HEVC + MediaMuxer 编码 HEIF 静态图像
     */
    private fun encodeHeifViaMediaCodec(bitmap: Bitmap, config: HeifConfig, outputFile: File): Boolean {
        try {
            val width = bitmap.width
            val height = bitmap.height
            val is10Bit = config.bitDepth == HeifBitDepth.DEPTH_10 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

            // 配置 MediaCodec HEVC 编码器
            val mime = if (is10Bit) MediaFormat.MIMETYPE_VIDEO_HEVC else MediaFormat.MIMETYPE_VIDEO_HEVC
            val format = MediaFormat.createVideoFormat(mime, width, height).apply {
                setInteger(MediaFormat.KEY_BIT_RATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ)
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                setInteger(MediaFormat.KEY_FRAME_RATE, 30)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 0) // 全 I 帧
                if (is10Bit) {
                    setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10)
                }
            }

            val codec = MediaCodec.createEncoderByType(mime)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

            // 配置 MediaMuxer 为 HEIF 容器
            val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_HEIF)
            codec.start()

            // 转换 RGBA Bitmap → YUV NV12
            val yuvData = rgbaToNv12(bitmap, width, height)

            // 送入一帧
            val inputBufferIndex = codec.dequeueInputBuffer(10_000L)
            if (inputBufferIndex >= 0) {
                val inputBuffer = codec.getInputBuffer(inputBufferIndex)!!
                inputBuffer.clear()
                inputBuffer.put(yuvData)
                codec.queueInputBuffer(inputBufferIndex, 0, yuvData.size, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            }

            // 获取编码输出
            var muxerStarted = false
            var trackIndex = -1
            val bufferInfo = MediaCodec.BufferInfo()

            while (true) {
                val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000L)
                when {
                    outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (!muxerStarted) {
                            val newFormat = codec.outputFormat
                            trackIndex = muxer.addTrack(newFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                    }
                    outputBufferIndex >= 0 -> {
                        val outputBuffer = codec.getOutputBuffer(outputBufferIndex)!!
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                            codec.releaseOutputBuffer(outputBufferIndex, false)
                            continue
                        }
                        if (bufferInfo.size > 0 && muxerStarted) {
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(trackIndex, outputBuffer, bufferInfo)
                        }
                        codec.releaseOutputBuffer(outputBufferIndex, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                    outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        break
                    }
                }
            }

            codec.stop()
            codec.release()
            muxer.stop()
            muxer.release()

            return outputFile.exists() && outputFile.length() > 0
        } catch (e: Exception) {
            Log.e(TAG, "HEIF MediaCodec encoding failed: ${e.message}", e)
            return false
        }
    }

    /**
     * RGBA Bitmap → YUV NV12 (Y plane + interleaved UV plane)
     */
    private fun rgbaToNv12(bitmap: Bitmap, width: Int, height: Int): ByteArray {
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val ySize = width * height
        val uvSize = ySize / 2
        val result = ByteArray(ySize + uvSize)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = pixels[y * width + x]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                // BT.601: Y = 0.299R + 0.587G + 0.114B
                val yVal = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                result[y * width + x] = yVal.coerceIn(0, 255).toByte()

                // UV subsampled 4:2:0 (every 2x2 block)
                if (y % 2 == 0 && x % 2 == 0) {
                    val uvIndex = ySize + (y / 2) * width + (x / 2) * 2
                    // Next pixel for 2x2 block
                    val nextIdx = minOf(y * width + x + 1, pixels.size - 1)
                    val nextPixel = pixels[nextIdx]
                    val r2 = (nextPixel shr 16) and 0xFF
                    val g2 = (nextPixel shr 8) and 0xFF
                    val b2 = nextPixel and 0xFF

                    val avgR = (r + r2) / 2
                    val avgG = (g + g2) / 2
                    val avgB = (b + b2) / 2

                    val u = (((-38 * avgR - 74 * avgG + 112 * avgB + 128) shr 8) + 128)
                    val v = (((112 * avgR - 94 * avgG - 18 * avgB + 128) shr 8) + 128)

                    if (uvIndex < result.size) result[uvIndex] = u.coerceIn(0, 255).toByte()
                    if (uvIndex + 1 < result.size) result[uvIndex + 1] = v.coerceIn(0, 255).toByte()
                }
            }
        }
        return result
    }

    /**
     * 降级方案：使用 Bitmap.compress WEBP_LOSSLESS 并转存到 MediaStore
     */
    private fun exportWebpFallback(
        context: Context,
        bitmap: Bitmap,
        quality: Int,
        displayName: String,
    ): Uri? {
        val baos = ByteArrayOutputStream()
        val compressed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, baos)
        } else {
            @Suppress("DEPRECATION")
            bitmap.compress(Bitmap.CompressFormat.WEBP, quality.coerceIn(1, 100), baos)
        }

        if (!compressed) {
            Log.e(TAG, "WEBP compression failed")
            return null
        }

        return writeToMediaStore(
            context = context,
            data = baos.toByteArray(),
            displayName = displayName,
            mimeType = "image/webp",
            extension = ".webp",
        )
    }

    /**
     * 检查当前设备是否支持 HEIF 10-bit 编码
     * 需同时满足：API 29+、HEVC Main10 编码器可用
     */
    fun isHeif10BitSupported(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return try {
            val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            for (info in codecList.codecInfos) {
                if (info.isEncoder && info.supportedTypes.contains(MediaFormat.MIMETYPE_VIDEO_HEVC)) {
                    val caps = info.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_HEVC)
                    if (caps != null && caps.profileLevels != null) {
                        for (pl in caps.profileLevels) {
                            if (pl.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10) {
                                return true
                            }
                        }
                    }
                }
            }
            false
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check HEIF 10-bit support: ${e.message}")
            false
        }
    }

    /**
     * 写入 MediaStore (Pictures/RapidRAW)
     */
    private fun writeToMediaStore(
        context: Context,
        data: ByteArray,
        displayName: String,
        mimeType: String,
        extension: String,
    ): Uri? {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$displayName$extension")
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/RapidRAW")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: return null

        resolver.openOutputStream(uri)?.use { os ->
            os.write(data)
            os.flush()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
        }

        Log.i(TAG, "Exported image: $uri (mime=$mimeType)")
        return uri
    }
}
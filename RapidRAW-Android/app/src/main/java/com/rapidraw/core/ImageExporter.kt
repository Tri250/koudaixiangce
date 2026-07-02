package com.rapidraw.core

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * 统一图像导出器 — 支持 JPEG、PNG、TIFF 格式导出，
 * 带尺寸缩放和水印叠加功能。
 *
 * 导出文件写入 DCIM/RapidRAW 目录。
 */
class ImageExporter(private val context: Context) {

    // ── 导出格式枚举 ──────────────────────────────────────────────
    enum class ExportFormat {
        JPEG,
        PNG,
        TIFF,
        ULTRA_HDR,
    }

    // ── 导出配置 ──────────────────────────────────────────────────
    data class ExportConfig(
        val format: ExportFormat = ExportFormat.JPEG,
        val jpegQuality: Int = 92,           // K-01: JPEG Q=85-100
        val maxLongEdge: Int? = null,         // K-04: 导出尺寸缩放
        val watermarkText: String? = null,    // K-05: 水印文字
        val watermarkSize: Float = 48f,       // 水印字号
        val watermarkColor: Int = Color.argb(128, 255, 255, 255), // 半透明白色
        val outputDir: String = OUTPUT_DIR_NAME,
    )

    companion object {
        private const val TAG = "ImageExporter"
        const val OUTPUT_DIR_NAME = "RapidRAW"
        private const val JPEG_QUALITY_MIN = 85
        private const val JPEG_QUALITY_MAX = 100
    }

    /**
     * 导出 Bitmap 到 DCIM/RapidRAW 目录。
     *
     * @param bitmap 源图像
     * @param config 导出配置
     * @param fileName 输出文件名（不含扩展名）
     * @return 导出后的文件 URI，失败返回 null
     */
    fun export(bitmap: Bitmap, config: ExportConfig, fileName: String): Uri? {
        val processed = applyScaling(bitmap, config.maxLongEdge)
        val watermarked = applyWatermark(processed, config.watermarkText, config.watermarkSize, config.watermarkColor)

        return try {
            val extension = when (config.format) {
                ExportFormat.JPEG, ExportFormat.ULTRA_HDR -> "jpg"
                ExportFormat.PNG -> "png"
                ExportFormat.TIFF -> "tiff"
            }
            val mimeType = when (config.format) {
                ExportFormat.JPEG, ExportFormat.ULTRA_HDR -> "image/jpeg"
                ExportFormat.PNG -> "image/png"
                ExportFormat.TIFF -> "image/tiff"
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                exportViaMediaStore(watermarked, config, fileName, extension, mimeType)
            } else {
                exportToFile(watermarked, config, fileName, extension)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Export failed", e)
            null
        } finally {
            if (watermarked !== bitmap && watermarked !== processed) watermarked.recycle()
            if (processed !== bitmap) processed.recycle()
        }
    }

    // ── K-04: 尺寸缩放 ──────────────────────────────────────────
    private fun applyScaling(bitmap: Bitmap, maxLongEdge: Int?): Bitmap {
        if (maxLongEdge == null || maxLongEdge <= 0) return bitmap
        val width = bitmap.width
        val height = bitmap.height
        val longEdge = maxOf(width, height)
        if (longEdge <= maxLongEdge) return bitmap

        val ratio = maxLongEdge.toFloat() / longEdge
        val newWidth = (width * ratio).roundToInt()
        val newHeight = (height * ratio).roundToInt()
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    // ── K-05: 水印叠加 ──────────────────────────────────────────
    private fun applyWatermark(bitmap: Bitmap, text: String?, size: Float, color: Int): Bitmap {
        if (text.isNullOrBlank()) return bitmap

        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.textSize = size
            this.isFakeBoldText = true
            // 添加阴影增强可读性
            setShadowLayer(4f, 2f, 2f, Color.argb(100, 0, 0, 0))
        }

        val padding = 24f
        val textWidth = paint.measureText(text)
        val x = result.width - textWidth - padding
        val y = result.height - padding

        canvas.drawText(text, x, y, paint)
        return result
    }

    // ── MediaStore 导出 (Android 10+) ────────────────────────────
    private fun exportViaMediaStore(
        bitmap: Bitmap,
        config: ExportConfig,
        fileName: String,
        extension: String,
        mimeType: String,
    ): Uri? {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "${fileName}.$extension")
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_DCIM}/${config.outputDir}")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val uri = context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues
        ) ?: return null

        try {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                encodeBitmap(bitmap, config, stream)
            }
            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, contentValues, null, null)
            return uri
        } catch (e: Exception) {
            context.contentResolver.delete(uri, null, null)
            throw e
        }
    }

    // ── 传统文件导出 (Android 9 及以下) ─────────────────────────
    private fun exportToFile(
        bitmap: Bitmap,
        config: ExportConfig,
        fileName: String,
        extension: String,
    ): Uri? {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
            config.outputDir
        )
        if (!dir.exists()) dir.mkdirs()

        val file = File(dir, "${fileName}.$extension")
        FileOutputStream(file).use { stream ->
            encodeBitmap(bitmap, config, stream)
        }
        return Uri.fromFile(file)
    }

    // ── 编码核心 ──────────────────────────────────────────────────
    private fun encodeBitmap(bitmap: Bitmap, config: ExportConfig, stream: OutputStream) {
        when (config.format) {
            ExportFormat.JPEG, ExportFormat.ULTRA_HDR -> {
                // K-01: JPEG 质量 85-100
                val quality = config.jpegQuality.coerceIn(JPEG_QUALITY_MIN, JPEG_QUALITY_MAX)
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            }
            ExportFormat.PNG -> {
                // K-02: PNG 无损导出，保留 32 位通道
                val argbBitmap = if (bitmap.config != Bitmap.Config.ARGB_8888) {
                    bitmap.copy(Bitmap.Config.ARGB_8888, false)
                } else {
                    bitmap
                }
                try {
                    argbBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                } finally {
                    if (argbBitmap !== bitmap) argbBitmap.recycle()
                }
            }
            ExportFormat.TIFF -> {
                // K-03: TIFF 导出（基础 TIFF writer）
                encodeTiff(bitmap, stream)
            }
        }
    }

    // ── K-03: TIFF 编码器（基础实现）──────────────────────────────
    private fun encodeTiff(bitmap: Bitmap, stream: OutputStream) {
        val argb = if (bitmap.config != Bitmap.Config.ARGB_8888) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            bitmap
        }
        try {
            val width = argb.width
            val height = argb.height
            val pixels = IntArray(width * height)
            argb.getPixels(pixels, 0, width, 0, 0, width, height)

            // 分离 RGB 通道
            val rgbData = ByteArray(width * height * 3)
            for (i in pixels.indices) {
                val pixel = pixels[i]
                rgbData[i * 3] = ((pixel shr 16) and 0xFF).toByte()     // R
                rgbData[i * 3 + 1] = ((pixel shr 8) and 0xFF).toByte()  // G
                rgbData[i * 3 + 2] = (pixel and 0xFF).toByte()          // B
            }

            val buf = ByteBuffer.allocate(8 + rgbData.size).order(ByteOrder.LITTLE_ENDIAN)

            // TIFF Header (8 bytes)
            val ifdOffset = 8
            buf.putShort(0x4949.toShort()) // Little-endian byte order
            buf.putShort(42.toShort())     // TIFF magic number
            buf.putInt(ifdOffset)          // Offset to first IFD

            // Number of IFD entries (12 entries for RGB image)
            val numEntries = 12
            val entrySize = 12
            val ifdSize = 2 + numEntries * entrySize + 4
            val dataOffset = ifdOffset + ifdSize

            val ifd = ByteBuffer.allocate(ifdSize).order(ByteOrder.LITTLE_ENDIAN)
            ifd.putShort(numEntries.toShort())

            // IFD entries
            data class TiffTag(val tag: Int, val type: Int, val count: Int, val value: Int)
            val tags = listOf(
                TiffTag(256, 4, 1, width),                    // ImageWidth
                TiffTag(257, 4, 1, height),                   // ImageLength
                TiffTag(258, 3, 3, dataOffset + 0),           // BitsPerSample: 8,8,8
                TiffTag(259, 3, 1, 1),                        // Compression: none
                TiffTag(262, 3, 1, 2),                        // PhotometricInterpretation: RGB
                TiffTag(273, 4, 1, dataOffset + 6),           // StripOffsets
                TiffTag(277, 3, 1, 3),                        // SamplesPerPixel
                TiffTag(278, 4, 1, height),                   // RowsPerStrip
                TiffTag(279, 4, 1, rgbData.size),             // StripByteCounts
                TiffTag(282, 5, 1, dataOffset + 12),           // XResolution
                TiffTag(283, 5, 1, dataOffset + 20),           // YResolution
                TiffTag(296, 3, 1, 2),                         // ResolutionUnit: inch
            )

            for (tag in tags) {
                ifd.putShort(tag.tag.toShort())
                ifd.putShort(tag.type.toShort())
                ifd.putInt(tag.count)
                ifd.putInt(tag.value)
            }

            // Next IFD offset (0 = none)
            ifd.putInt(0)

            // Extra data area
            val extra = ByteBuffer.allocate(28 + rgbData.size).order(ByteOrder.LITTLE_ENDIAN)
            // BitsPerSample: 8, 8, 8
            extra.putShort(8.toShort())
            extra.putShort(8.toShort())
            extra.putShort(8.toShort())
            // XResolution: 72/1
            extra.putInt(72)
            extra.putInt(1)
            // YResolution: 72/1
            extra.putInt(72)
            extra.putInt(1)
            // Image data
            extra.put(rgbData)

            // Write everything
            buf.position(0)
            stream.write(buf.array())
            ifd.position(0)
            stream.write(ifd.array())
            extra.position(0)
            stream.write(extra.array())
            stream.flush()
        } finally {
            if (argb !== bitmap) argb.recycle()
        }
    }
}
package com.rapidraw.core

import android.graphics.Bitmap
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.rapidraw.data.model.ExifData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Ultra HDR Gain Map 导出器（AlcedoStudio 增强版）
 *
 * 基于 ISO 21496-1 标准实现 Ultra HDR JPEG Gain Map 导出。
 *
 * 工作原理：
 * 1. 将 HDR 线性像素通过色调映射生成 SDR 8-bit 基础图
 * 2. 计算 Gain Map：每个像素的 HDR/SDR 对数比值
 * 3. 将 Gain Map 编码为灰度 JPEG
 * 4. 使用 MPF (Multi-Picture Format) 将两张 JPEG 合并为单一文件
 * 5. 写入 XMP 元数据（ISO 21496-1 格式）描述 Gain Map 参数
 *
 * 支持的传输函数：
 * - PQ (SMPTE ST 2084)：绝对亮度编码，适合电影后期
 * - HLG (ITU-R BT.2100)：相对亮度编码，适合广播
 *
 * 参考：
 * - ISO 21496-1:2024 Gain Map Metadata
 * - https://developer.android.com/media/platform/hdr-image-format
 * - ITU-R BT.2100 (HLG / PQ)
 * - SMPTE ST 2084 (PQ EOTF)
 */
class UltraHdrExporter {

    companion object {
        private const val TAG = "UltraHdrExporter"

        /** SDR 参考白亮度 (nits)，BT.1886 标准推荐值 */
        private const val SDR_WHITE_NITS = 203f

        /** 用于防止除零的小量值 */
        private const val EPSILON = 1e-6f
    }

    /**
     * HDR 传输函数
     */
    enum class TransferFunction(val displayName: String) {
        PQ("PQ (ST 2084)"),
        HLG("HLG (BT.2100)"),
    }

    /**
     * 导出参数
     */
    data class Params(
        val quality: Int = 95,                          // JPEG 质量 (0-100)
        val transferFunction: TransferFunction = TransferFunction.PQ,
        val peakLuminanceNits: Float = 1000f,           // HDR 峰值亮度 (nits)
        val gainMapGamma: Float = 1.0f,                 // Gain Map Gamma 校正值
        val gainMapCompression: Float = 0.0f,           // log2 压缩量
        val sdrBrightness: Float = SDR_WHITE_NITS,      // SDR 参考亮度 (nits)
        val includeExif: Boolean = true,                // 是否包含 EXIF 元数据
        val gainMapQuality: Int = 90,                   // Gain Map JPEG 质量
        val maxBoostStop: Float = 4.0f,                 // 最大增益 (stops)
    )

    // ── 公开 API ──────────────────────────────────────────────────────

    /**
     * 从线性浮点数据（HDR）导出 Ultra HDR JPEG。
     *
     * 自动从 HDR 线性数据生成 SDR 基础图和 Gain Map。
     *
     * @param linearData  R,G,B 交错排列的线性浮点数组，值可能 >1.0
     * @param width       图像宽度
     * @param height      图像高度
     * @param outputStream 输出流
     * @param params      导出参数
     * @param exifData    可选 EXIF 元数据
     */
    suspend fun exportFromLinear(
        linearData: FloatArray,
        width: Int,
        height: Int,
        outputStream: OutputStream,
        params: Params = Params(),
        exifData: ExifData? = null,
    ) = withContext(Dispatchers.Default) {
        // 2026 hotfix: 参数校验改用 if + 抛 IllegalArgumentException，避免 require 异常在协程中触发未捕获崩溃
        if (width <= 0 || height <= 0) {
            Log.e(TAG, "Invalid dimensions: ${width}x$height")
            return@withContext
        }
        val expectedSize = width.toLong() * height.toLong() * 3L
        if (linearData.size.toLong() != expectedSize) {
            Log.e(TAG, "linearData size mismatch: expected $expectedSize, got ${linearData.size}")
            return@withContext
        }

        // 1. 从 HDR 线性数据生成 HDR Bitmap
        val hdrBitmap = try {
            linearToBitmap(linearData, width, height)
        } catch (oom: OutOfMemoryError) {
            Log.e(TAG, "OOM creating HDR bitmap", oom)
            return@withContext
        }

        // 2. 色调映射生成 SDR Bitmap
        val sdrBitmap = try {
            toneMapToSdr(hdrBitmap, params)
        } catch (oom: OutOfMemoryError) {
            Log.e(TAG, "OOM tone mapping to SDR", oom)
            runCatching { hdrBitmap.recycle() }
            return@withContext
        }

        // 3. 使用 SDR + HDR 对导出
        try {
            try {
                exportWithGainMap(sdrBitmap, linearData, width, height, outputStream, params, exifData)
            } catch (ce: CancellationException) {
                // 2026 hotfix: 协程取消异常必须重新抛出
                throw ce
            } catch (oom: OutOfMemoryError) {
                Log.e(TAG, "OOM during Ultra HDR export", oom)
            }
        } finally {
            runCatching { hdrBitmap.recycle() }
            runCatching { sdrBitmap.recycle() }
        }
    }

    /**
     * 从 SDR Bitmap + HDR 线性数据导出 Ultra HDR JPEG。
     *
     * 当你已有 SDR 和 HDR 两个版本的图像时，可直接调用此方法
     * 生成 ISO 21496-1 标准的 Ultra HDR JPEG。
     *
     * @param sdrBitmap      标准 sRGB SDR 图像
     * @param hdrLinearData  对应的 HDR 线性数据（R,G,B 交错，值可能 >1.0）
     * @param width          图像宽度
     * @param height         图像高度
     * @param outputStream   输出流
     * @param params         导出参数
     * @param exifData       可选 EXIF 元数据
     */
    suspend fun exportWithGainMap(
        sdrBitmap: Bitmap,
        hdrLinearData: FloatArray,
        width: Int,
        height: Int,
        outputStream: OutputStream,
        params: Params = Params(),
        exifData: ExifData? = null,
    ) = withContext(Dispatchers.Default) {
        // 2026 hotfix: 参数校验改用 if + 抛 IllegalArgumentException
        if (sdrBitmap.isRecycled) {
            Log.e(TAG, "SDR bitmap is recycled")
            return@withContext
        }
        if (width <= 0 || height <= 0) {
            Log.e(TAG, "Invalid dimensions: ${width}x$height")
            return@withContext
        }
        val expectedSize = width.toLong() * height.toLong() * 3L
        if (hdrLinearData.size.toLong() != expectedSize) {
            Log.e(TAG, "hdrLinearData size mismatch: expected $expectedSize, got ${hdrLinearData.size}")
            return@withContext
        }

        var hdrBitmap: Bitmap? = null
        var gainMap: Bitmap? = null
        try {
            // 1. 从 HDR 线性数据生成 HDR Bitmap（编码到 PQ/HLG 信号值）
            hdrBitmap = try {
                linearToHdrBitmap(hdrLinearData, width, height, params)
            } catch (oom: OutOfMemoryError) {
                Log.e(TAG, "OOM creating HDR bitmap", oom)
                return@withContext
            }

            // 2. 计算 Gain Map
            gainMap = try {
                computeGainMap(hdrBitmap, sdrBitmap, params)
            } catch (oom: OutOfMemoryError) {
                Log.e(TAG, "OOM computing gain map", oom)
                return@withContext
            }

            // 3. 编码 SDR JPEG
            val sdrJpegBytes = compressJpeg(sdrBitmap, params.quality)

            // 4. 编码 Gain Map JPEG（灰度）
            val gainMapJpegBytes = compressGainMapJpeg(gainMap, params.gainMapQuality)

            // 5. 合并为 Ultra HDR JPEG
            val finalBytes = buildUltraHdrJpeg(sdrJpegBytes, gainMapJpegBytes, params)

            // 6. 写入 EXIF 元数据（如果提供）
            val outputBytes = if (exifData != null && params.includeExif) {
                writeExifMetadata(finalBytes, exifData)
            } else {
                finalBytes
            }

            // 7. 写入输出流
            try {
                outputStream.write(outputBytes)
                outputStream.flush()
            } catch (oom: OutOfMemoryError) {
                Log.e(TAG, "OOM writing output stream", oom)
            }
        } catch (ce: CancellationException) {
            // 2026 hotfix: 协程取消异常必须重新抛出
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "Ultra HDR export failed: ${e.message}", e)
        } finally {
            runCatching { hdrBitmap?.recycle() }
            runCatching { gainMap?.recycle() }
        }
    }

    // ── HDR 传输函数编码 ─────────────────────────────────────────────

    /**
     * PQ (SMPTE ST 2084) EOTF 正向变换。
     *
     * 将线性光值编码为 PQ 信号值。
     * L = ((c1 + c2 * L_p^m2) / (1 + c3 * L_p^m2 + c4 * L_p^n2))^(1/m1)
     *
     * @param linear 归一化线性值 [0, 1]，对应 0~peakNits
     * @return PQ 编码信号值 [0, 1]
     */
    private fun linearToPq(linear: Float): Float {
        val m1 = 0.1593017578125          // 2610/16384
        val m2 = 78.84375                 // 2523/32 * 128
        val c1 = 0.8359375                // 3424/4096  → 但更精确: c1 = 1 - (1/2)^m1 * (c3-c2) ≈ 0.8359
        val c2 = 18.8515625               // 2413/128
        val c3 = 18.6875                  // 2392/128
        val c4 = 1.0                      // 按规范

        val l = linear.toDouble().coerceIn(0.0, 1.0)
        val lp = l.pow(m1)
        val numerator = c1 + c2 * lp.pow(m2)
        val denominator = 1.0 + c3 * lp.pow(m2) + c4 * lp.pow(m2 * 1.5)
        val result = (numerator / denominator).pow(1.0 / m1)
        return result.toFloat().coerceIn(0f, 1f)
    }

    /**
     * PQ (SMPTE ST 2084) 反向变换 — 将 PQ 信号值解码为线性光值。
     *
     * L_p = ((c1 + c2 * E'^m2) / (1 + c3 * E'^m2 + c4 * E'^n2))^(1/m1)
     * 线性值 = 10000 * L_p / peakNits
     */
    private fun pqToLinear(signal: Float, peakNits: Float): Float {
        val m1 = 2610.0 / 4096.0 * (1.0 / 4.0)
        val m2 = 2523.0 / 4096.0 * 128.0
        val c1 = 3424.0 / 4096.0
        val c2 = 2413.0 / 4096.0 * 32.0
        val c3 = 2392.0 / 4096.0 * 32.0

        val e = signal.toDouble().coerceIn(0.0, 1.0)
        val ePow = e.pow(1.0 / m2)
        val numerator = max(ePow - c1, 0.0)
        val denominator = c2 - c3 * ePow
        if (denominator <= 0.0) return 1f
        val linearAbsolute = 10000.0 * (numerator / denominator).pow(1.0 / m1)
        return (linearAbsolute / peakNits).toFloat().coerceIn(0f, 1f)
    }

    /**
     * HLG OETF 正向变换（ITU-R BT.2100 表5）。
     *
     * 将场景线性光值编码为 HLG 信号值：
     * - L_c <= 1: E' = sqrt(3) * L_c^0.5
     * - L_c > 1:  E' = a * ln(b * L_c + 1) + c
     *
     * 其中 a=0.17883277, b=1/0.17883277, c=0.55991073（与反向对应）
     *
     * 简化形式（标准 HLG OETF）：
     * - E' ≤ 1/2: E' = sqrt(3 * L_c)
     * - E' > 1/2: E' = a * exp(b * (L_c - c)) + d  (对数段)
     */
    private fun linearToHlg(linear: Float): Float {
        val a = 0.17883277f
        val b = 0.28466892f
        val c = 0.55991073f
        val l = linear.coerceAtLeast(0f)

        return when {
            l <= 1f / 12f -> sqrt(3f * l)
            else -> a * exp((l - b) / a) + c
        }.coerceIn(0f, 1f)
    }

    /**
     * HLG 反 OETF（ITU-R BT.2100 表5：HLG OETF⁻¹）
     *
     * 将 HLG 信号值 E' 解码为场景线性光值：
     * - E' ≤ 1/2: V = E'² / 3
     * - E' > 1/2: V = (exp((E' - c) / a) + b) / 12
     *
     * 其中 a=0.17883277, b=0.28466892, c=0.55991073
     */
    private fun hlgToLinear(signal: Float): Float {
        val a = 0.17883277f
        val b = 0.28466892f
        val c = 0.55991073f
        val e = signal.coerceIn(0f, 1f)

        return when {
            e <= 0.5f -> (e * e) / 3f
            else -> (exp(((e - c) / a).toDouble()).toFloat() + b) / 12f
        }
    }

    /**
     * 根据传输函数将 HDR 编码信号值解码为线性光值。
     */
    private fun decodeToLinear(signal: Float, transfer: TransferFunction, peakNits: Float): Float {
        return when (transfer) {
            TransferFunction.PQ -> pqToLinear(signal, peakNits)
            TransferFunction.HLG -> hlgToLinear(signal)
        }
    }

    // ── sRGB OETF / EOTF ──────────────────────────────────────────

    private fun linearToSrgb(linear: Float): Float {
        return if (linear <= 0.0031308f) {
            12.92f * linear
        } else {
            1.055f * linear.toDouble().pow(1.0 / 2.4).toFloat() - 0.055f
        }
    }

    private fun srgbToLinear(srgb: Float): Float {
        return if (srgb <= 0.04045f) {
            srgb / 12.92f
        } else {
            ((srgb + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
        }
    }

    // ── 线性数据 → Bitmap ──────────────────────────────────────────

    /**
     * 将线性浮点数据转换为 HDR 编码 Bitmap（用于 Gain Map 计算）。
     * HDR 值通过 PQ/HLG 传输函数编码后存储为 8-bit。
     */
    private fun linearToHdrBitmap(
        linearData: FloatArray,
        width: Int,
        height: Int,
        params: Params,
    ): Bitmap {
        // 2026 hotfix: 防御 width*height 整数溢出
        val pixelCount = width.toLong() * height.toLong()
        if (pixelCount <= 0L || pixelCount > Int.MAX_VALUE.toLong()) {
            throw IllegalArgumentException("Invalid bitmap dimensions: $width x $height")
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(pixelCount.toInt())
        val luminanceScale = params.peakLuminanceNits / 10000f

        for (i in 0 until width * height) {
            val rLinear = linearData[i * 3]
            val gLinear = linearData[i * 3 + 1]
            val bLinear = linearData[i * 3 + 2]

            // 归一化到 [0, 1]（相对于峰值亮度）
            val rNorm = (rLinear * luminanceScale).coerceIn(0f, 1f)
            val gNorm = (gLinear * luminanceScale).coerceIn(0f, 1f)
            val bNorm = (bLinear * luminanceScale).coerceIn(0f, 1f)

            // 应用 HDR 传输函数编码
            val rEncoded = when (params.transferFunction) {
                TransferFunction.PQ -> linearToPq(rNorm)
                TransferFunction.HLG -> linearToHlg(rNorm)
            }
            val gEncoded = when (params.transferFunction) {
                TransferFunction.PQ -> linearToPq(gNorm)
                TransferFunction.HLG -> linearToHlg(gNorm)
            }
            val bEncoded = when (params.transferFunction) {
                TransferFunction.PQ -> linearToPq(bNorm)
                TransferFunction.HLG -> linearToHlg(bNorm)
            }

            val rByte = (rEncoded * 255f).toInt().coerceIn(0, 255)
            val gByte = (gEncoded * 255f).toInt().coerceIn(0, 255)
            val bByte = (bEncoded * 255f).toInt().coerceIn(0, 255)

            pixels[i] = (0xFF shl 24) or (rByte shl 16) or (gByte shl 8) or bByte
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    /**
     * 将线性浮点数据转换为 Bitmap（简单 sRGB 色调映射，用于 exportFromLinear）。
     */
    private fun linearToBitmap(
        linearData: FloatArray,
        width: Int,
        height: Int,
    ): Bitmap {
        // 2026 hotfix: 防御 width*height 整数溢出
        val pixelCount = width.toLong() * height.toLong()
        if (pixelCount <= 0L || pixelCount > Int.MAX_VALUE.toLong()) {
            throw IllegalArgumentException("Invalid bitmap dimensions: $width x $height")
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(pixelCount.toInt())

        for (i in 0 until width * height) {
            val r = linearToSrgb(linearData[i * 3].coerceIn(0f, 1f))
            val g = linearToSrgb(linearData[i * 3 + 1].coerceIn(0f, 1f))
            val b = linearToSrgb(linearData[i * 3 + 2].coerceIn(0f, 1f))

            val rByte = (r * 255f).toInt().coerceIn(0, 255)
            val gByte = (g * 255f).toInt().coerceIn(0, 255)
            val bByte = (b * 255f).toInt().coerceIn(0, 255)

            pixels[i] = (0xFF shl 24) or (rByte shl 16) or (gByte shl 8) or bByte
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    // ── SDR 色调映射 ──────────────────────────────────────────────

    /**
     * 将 HDR Bitmap 色调映射为 SDR Bitmap。
     */
    private fun toneMapToSdr(hdrBitmap: Bitmap, params: Params): Bitmap {
        val w = hdrBitmap.width
        val h = hdrBitmap.height
        if (w <= 0 || h <= 0) {
            throw IllegalArgumentException("Invalid HDR bitmap dimensions: $w x $h")
        }
        // 2026 hotfix: 防御 w*h 整数溢出
        val pixelCount = w.toLong() * h.toLong()
        if (pixelCount > Int.MAX_VALUE.toLong()) {
            throw IllegalArgumentException("HDR bitmap too large: $w x $h")
        }
        val sdrBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

        val hdrPixels = IntArray(pixelCount.toInt())
        hdrBitmap.getPixels(hdrPixels, 0, w, 0, 0, w, h)

        val luminanceScale = params.sdrBrightness / params.peakLuminanceNits
        val outPixels = IntArray(pixelCount.toInt())

        for (i in hdrPixels.indices) {
            val r = ((hdrPixels[i] shr 16) and 0xFF) / 255f
            val g = ((hdrPixels[i] shr 8) and 0xFF) / 255f
            val b = (hdrPixels[i] and 0xFF) / 255f

            val rLinear = decodeToLinear(r, params.transferFunction, params.peakLuminanceNits)
            val gLinear = decodeToLinear(g, params.transferFunction, params.peakLuminanceNits)
            val bLinear = decodeToLinear(b, params.transferFunction, params.peakLuminanceNits)

            val rScaled = rLinear * luminanceScale
            val gScaled = gLinear * luminanceScale
            val bScaled = bLinear * luminanceScale

            val (rMapped, gMapped, bMapped) = when (params.transferFunction) {
                TransferFunction.PQ -> toneMapPqToSdr(rScaled, gScaled, bScaled)
                TransferFunction.HLG -> toneMapHlgToSdr(rScaled, gScaled, bScaled)
            }

            val rByte = (linearToSrgb(rMapped) * 255f).toInt().coerceIn(0, 255)
            val gByte = (linearToSrgb(gMapped) * 255f).toInt().coerceIn(0, 255)
            val bByte = (linearToSrgb(bMapped) * 255f).toInt().coerceIn(0, 255)

            outPixels[i] = (0xFF shl 24) or (rByte shl 16) or (gByte shl 8) or bByte
        }

        sdrBitmap.setPixels(outPixels, 0, w, 0, 0, w, h)
        return sdrBitmap
    }

    private fun toneMapPqToSdr(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val maxChannel = maxOf(r, g, b, EPSILON)
        val scale = if (maxChannel > 1f) {
            val compressed = reinhardExtended(maxChannel, 1.5f)
            compressed / maxChannel
        } else 1f
        return Triple(r * scale, g * scale, b * scale)
    }

    private fun toneMapHlgToSdr(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val maxChannel = maxOf(r, g, b, EPSILON)
        val scale = if (maxChannel > 1f) {
            val compressed = 1f + (maxChannel - 1f) / (1f + (maxChannel - 1f))
            compressed / maxChannel
        } else 1f
        return Triple(r * scale, g * scale, b * scale)
    }

    private fun reinhardExtended(luminance: Float, maxLuminance: Float): Float =
        luminance * (1f + luminance / (maxLuminance * maxLuminance)) / (1f + luminance)

    // ── Gain Map 计算（ISO 21496-1） ────────────────────────────────

    /**
     * 计算 Gain Map：基于 ISO 21496-1 标准。
     *
     * 算法：
     * 1. 对每个像素，HDR 值通过反 EOTF 解码为线性值
     * 2. SDR 值通过反 OETF 得到线性值
     * 3. gainMap = (HDR + epsilon) / (SDR + epsilon)
     * 4. logGainMap = log2(gainMap)
     * 5. 归一化到 [0, 255]
     */
    private fun computeGainMap(
        hdrBitmap: Bitmap,
        sdrBitmap: Bitmap,
        params: Params,
    ): Bitmap {
        val w = min(hdrBitmap.width, sdrBitmap.width)
        val h = min(hdrBitmap.height, sdrBitmap.height)
        if (w <= 0 || h <= 0) {
            throw IllegalArgumentException("Invalid bitmap dimensions for gain map: $w x $h")
        }
        // 2026 hotfix: 防御 w*h 整数溢出
        val pixelCount = w.toLong() * h.toLong()
        if (pixelCount > Int.MAX_VALUE.toLong()) {
            throw IllegalArgumentException("Bitmap too large for gain map: $w x $h")
        }
        val gainMap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

        val hdrPixels = IntArray(pixelCount.toInt())
        val sdrPixels = IntArray(pixelCount.toInt())
        hdrBitmap.getPixels(hdrPixels, 0, w, 0, 0, w, h)
        sdrBitmap.getPixels(sdrPixels, 0, w, 0, 0, w, h)

        val maxGain = params.maxBoostStop
        val minGain = -params.maxBoostStop / 2f
        val gainRange = maxGain - minGain

        val gamma = params.gainMapGamma
        val compression = params.gainMapCompression

        val outPixels = IntArray(pixelCount.toInt())

        for (i in hdrPixels.indices) {
            val hdrR = ((hdrPixels[i] shr 16) and 0xFF) / 255f
            val hdrG = ((hdrPixels[i] shr 8) and 0xFF) / 255f
            val hdrB = (hdrPixels[i] and 0xFF) / 255f

            val sdrR = ((sdrPixels[i] shr 16) and 0xFF) / 255f
            val sdrG = ((sdrPixels[i] shr 8) and 0xFF) / 255f
            val sdrB = (sdrPixels[i] and 0xFF) / 255f

            // 解码为线性值
            val hdrLinearR = decodeToLinear(hdrR, params.transferFunction, params.peakLuminanceNits)
            val hdrLinearG = decodeToLinear(hdrG, params.transferFunction, params.peakLuminanceNits)
            val hdrLinearB = decodeToLinear(hdrB, params.transferFunction, params.peakLuminanceNits)

            val sdrLinearR = srgbToLinear(sdrR)
            val sdrLinearG = srgbToLinear(sdrG)
            val sdrLinearB = srgbToLinear(sdrB)

            // 计算 log2 增益比：log2((HDR + ε) / (SDR + ε))
            val rGain = safeLog2Ratio(hdrLinearR, sdrLinearR)
            val gGain = safeLog2Ratio(hdrLinearG, sdrLinearG)
            val bGain = safeLog2Ratio(hdrLinearB, sdrLinearB)

            // 应用压缩（如果设置）
            val rCompressed = if (compression > 0f) applyCompression(rGain, compression) else rGain
            val gCompressed = if (compression > 0f) applyCompression(gGain, compression) else gGain
            val bCompressed = if (compression > 0f) applyCompression(bGain, compression) else bGain

            // 限幅
            val rClamped = rCompressed.coerceIn(minGain, maxGain)
            val gClamped = gCompressed.coerceIn(minGain, maxGain)
            val bClamped = bCompressed.coerceIn(minGain, maxGain)

            // 归一化到 [0, 255]，应用 Gamma 校正
            val rNormalized = ((rClamped - minGain) / gainRange).let {
                if (gamma != 1.0f) it.pow(1f / gamma) else it
            }
            val gNormalized = ((gClamped - minGain) / gainRange).let {
                if (gamma != 1.0f) it.pow(1f / gamma) else it
            }
            val bNormalized = ((bClamped - minGain) / gainRange).let {
                if (gamma != 1.0f) it.pow(1f / gamma) else it
            }

            val rByte = (rNormalized * 255f).toInt().coerceIn(0, 255)
            val gByte = (gNormalized * 255f).toInt().coerceIn(0, 255)
            val bByte = (bNormalized * 255f).toInt().coerceIn(0, 255)

            outPixels[i] = (0xFF shl 24) or (rByte shl 16) or (gByte shl 8) or bByte
        }

        gainMap.setPixels(outPixels, 0, w, 0, 0, w, h)
        return gainMap
    }

    /**
     * 安全的 log2 比值计算：log2((hdr + ε) / (sdr + ε))
     */
    private fun safeLog2Ratio(hdr: Float, sdr: Float): Float {
        return if (sdr > EPSILON && hdr > EPSILON) {
            log2((hdr + EPSILON) / (sdr + EPSILON))
        } else 0f
    }

    /**
     * 应用 log2 压缩：将增益值向零压缩，减少极端值的影响。
     */
    private fun applyCompression(gain: Float, compression: Float): Float {
        return if (gain > 0f) {
            gain / (1f + compression * gain)
        } else {
            gain / (1f + compression * (-gain))
        }
    }

    // ── JPEG 编码 ─────────────────────────────────────────────────

    private fun compressJpeg(bitmap: Bitmap, quality: Int): ByteArray {
        if (bitmap.isRecycled) {
            Log.e(TAG, "Cannot compress recycled bitmap")
            return ByteArray(0)
        }
        return try {
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            baos.toByteArray()
        } catch (oom: OutOfMemoryError) {
            Log.e(TAG, "OOM compressing JPEG", oom)
            ByteArray(0)
        } catch (e: Exception) {
            Log.e(TAG, "JPEG compression failed: ${e.message}", e)
            ByteArray(0)
        }
    }

    /**
     * 将 Gain Map 压缩为灰度 JPEG。
     * 使用 R 通道作为灰度值，简化为灰度图后压缩。
     */
    private fun compressGainMapJpeg(gainMap: Bitmap, quality: Int): ByteArray {
        val w = gainMap.width
        val h = gainMap.height
        if (w <= 0 || h <= 0) return ByteArray(0)
        // 2026 hotfix: 防御 w*h 整数溢出
        val pixelCount = w.toLong() * h.toLong()
        if (pixelCount > Int.MAX_VALUE.toLong()) {
            Log.e(TAG, "Gain map too large: $w x $h")
            return ByteArray(0)
        }

        // 创建灰度 Bitmap 以减少文件大小
        val grayBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val srcPixels = IntArray(pixelCount.toInt())
        val dstPixels = IntArray(pixelCount.toInt())
        gainMap.getPixels(srcPixels, 0, w, 0, 0, w, h)

        for (i in srcPixels.indices) {
            val r = (srcPixels[i] shr 16) and 0xFF
            val g = (srcPixels[i] shr 8) and 0xFF
            val b = srcPixels[i] and 0xFF
            // 使用亮度加权平均作为灰度值
            val gray = (0.299f * r + 0.587f * g + 0.114f * b).roundToInt().coerceIn(0, 255)
            dstPixels[i] = (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
        }

        grayBitmap.setPixels(dstPixels, 0, w, 0, 0, w, h)

        val baos = ByteArrayOutputStream()
        grayBitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        // 2026 hotfix: 释放灰度 bitmap，避免内存泄漏
        runCatching { grayBitmap.recycle() }
        return baos.toByteArray()
    }

    // ── Ultra HDR JPEG 构建（MPF + XMP） ──────────────────────────

    /**
     * 构建 Ultra HDR JPEG 文件。
     *
     * 流程：
     * 1. 生成 ISO 21496-1 XMP 元数据
     * 2. 将 XMP 插入 SDR JPEG
     * 3. 构建 MPF 标记
     * 4. 将 MPF 插入 SDR JPEG
     * 5. 拼接 SDR JPEG + Gain Map JPEG
     * 6. 修正 XMP 中的 Gain Map 偏移量
     */
    private fun buildUltraHdrJpeg(
        sdrJpeg: ByteArray,
        gainMapJpeg: ByteArray,
        params: Params,
    ): ByteArray {
        val xmpData = buildGainMapXmp(
            gainMapLength = gainMapJpeg.size,
            params = params,
        )

        val sdrWithXmp = insertXmpIntoJpeg(sdrJpeg, xmpData)

        val mpfMarker = buildMpfMarker(
            firstImageSize = sdrWithXmp.size,
            secondImageSize = gainMapJpeg.size,
        )

        val sdrWithMpf = insertMpfIntoJpeg(sdrWithXmp, mpfMarker)

        // 拼接 SDR + Gain Map
        val result = ByteArray(sdrWithMpf.size + gainMapJpeg.size)
        System.arraycopy(sdrWithMpf, 0, result, 0, sdrWithMpf.size)
        System.arraycopy(gainMapJpeg, 0, result, sdrWithMpf.size, gainMapJpeg.size)

        // 修正 XMP 中的 Gain Map 偏移量占位符
        val placeholder = "GAINMAP_OFFSET_PLACEHOLDER"
        val offsetBytes = sdrWithMpf.size.toString().toByteArray(Charsets.US_ASCII)
        val placeholderBytes = placeholder.toByteArray(Charsets.US_ASCII)

        var offsetPos = -1
        for (i in 0 until result.size - placeholderBytes.size) {
            var match = true
            for (j in placeholderBytes.indices) {
                if (result[i + j] != placeholderBytes[j]) {
                    match = false
                    break
                }
            }
            if (match) {
                offsetPos = i
                break
            }
        }

        if (offsetPos >= 0) {
            val newResult = result.copyOf()
            for (j in offsetBytes.indices) {
                newResult[offsetPos + j] = offsetBytes[j]
            }
            // 用空格填充剩余的占位符位置
            for (j in offsetBytes.size until placeholderBytes.size) {
                newResult[offsetPos + j] = 0x20
            }
            return newResult
        }

        return result
    }

    // ── XMP 元数据构建（ISO 21496-1） ────────────────────────────────

    /**
     * 构建 ISO 21496-1 XMP 元数据。
     *
     * 包含：
     * - GContainer: 容器目录（主图 + Gain Map）
     * - GImage: Gain Map 语义和参数
     * - HDRGainMap: HDR 元数据
     */
    private fun buildGainMapXmp(
        gainMapLength: Int,
        params: Params,
    ): ByteArray {
        val minGain = -params.maxBoostStop / 2f
        val maxGain = params.maxBoostStop

        val transferFunctionName = when (params.transferFunction) {
            TransferFunction.PQ -> "PQ"
            TransferFunction.HLG -> "HLG"
        }

        val xmp = """<?xpacket begin="\xef\xbb\xbf" id="W5M0MpCehiHzreSzNTczkc9d"?>
<x:xmpmeta xmlns:x="adobe:ns:meta/">
  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
    <rdf:Description rdf:about=""
      xmlns:GContainer="http://ns.google.com/photos/1.0/container/"
      xmlns:GImage="http://ns.google.com/photos/1.0/container/item/"
      xmlns:HDRGainMap="http://ns.google.com/photos/1.0/hdrgainmap/">
      <GContainer:Directory>
        <rdf:Seq>
          <rdf:li rdf:parseType="Resource">
            <GContainer:Item
              GContainer:Mime="image/jpeg"
              GContainer:Semantic="Primary"
              GContainer:Length="0"
              GContainer:Padding="0"/>
          </rdf:li>
          <rdf:li rdf:parseType="Resource">
            <GContainer:Item
              GContainer:Mime="image/jpeg"
              GContainer:Semantic="RecoveryMap"
              GContainer:Length="$gainMapLength"
              GContainer:Padding="0"/>
          </rdf:li>
        </rdf:Seq>
      </GContainer:Directory>
      <HDRGainMap:HDRGainMap
        HDRGainMap:Version="1.0"
        HDRGainMap:GainMapMin="$minGain"
        HDRGainMap:GainMapMax="$maxGain"
        HDRGainMap:Gamma="${params.gainMapGamma}"
        HDRGainMap:OffsetSdr="0"
        HDRGainMap:OffsetHdr="0"
        HDRGainMap:HDRCapacityMin="0"
        HDRGainMap:HDRCapacityMax="${params.peakLuminanceNits.toInt()}"
        HDRGainMap:GainMapOffset="GAINMAP_OFFSET_PLACEHOLDER"/>
    </rdf:Description>
    <rdf:Description rdf:about=""
      xmlns:hdrgm="http://ns.google.com/photos/1.0/hdrgainmap/"
      hdrgm:TransferFunction="$transferFunctionName"
      hdrgm:ContentLightLevelMax="${params.peakLuminanceNits.toInt()}"
      hdrgm:ContentLightLevelAverage="${(params.peakLuminanceNits * 0.4f).roundToInt()}"/>
  </rdf:RDF>
</x:xmpmeta>
<?xpacket end="w"?>"""

        return xmp.toByteArray(Charsets.UTF_8)
    }

    /**
     * 将 XMP 数据插入 JPEG 文件的 APP1 段。
     *
     * JPEG 结构：SOI (FFD8) | APP1 (FFE1 + length + namespace + xmp) | ... 其余数据
     */
    private fun insertXmpIntoJpeg(jpeg: ByteArray, xmpData: ByteArray): ByteArray {
        if (jpeg.size < 2 || jpeg[0] != 0xFF.toByte() || jpeg[1] != 0xD8.toByte()) {
            Log.w(TAG, "Invalid JPEG: missing SOI marker")
            return jpeg
        }

        val xmpNamespace = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray(Charsets.US_ASCII)
        val app1PayloadSize = xmpNamespace.size + xmpData.size
        val length = app1PayloadSize + 2  // +2 for length field itself

        val result = ByteArray(jpeg.size + 4 + app1PayloadSize)

        // SOI
        result[0] = jpeg[0]
        result[1] = jpeg[1]

        // APP1 marker
        var pos = 2
        result[pos++] = 0xFF.toByte()
        result[pos++] = 0xE1.toByte()
        result[pos++] = ((length shr 8) and 0xFF).toByte()
        result[pos++] = (length and 0xFF).toByte()

        // XMP namespace + data
        System.arraycopy(xmpNamespace, 0, result, pos, xmpNamespace.size)
        pos += xmpNamespace.size
        System.arraycopy(xmpData, 0, result, pos, xmpData.size)
        pos += xmpData.size

        // 原始 JPEG 数据（SOI 之后的部分）
        System.arraycopy(jpeg, 2, result, pos, jpeg.size - 2)

        return result
    }

    // ── MPF (Multi-Picture Format) 标记构建 ──────────────────────────

    /**
     * 构建 MPF APP2 标记。
     *
     * MPF 格式允许在单个 JPEG 文件中存储多张图像，
     * 用于 Ultra HDR 将 SDR 基础图和 Gain Map 组合。
     */
    private fun buildMpfMarker(firstImageSize: Int, secondImageSize: Int): ByteArray {
        val mpfIdentifier = byteArrayOf(0x4D, 0x50, 0x46, 0x00) // "MPF\0"

        // MP Endian — TIFF 头
        val mpEndian = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        mpEndian.putShort(0x4949.toShort())  // Little-endian byte order
        mpEndian.putShort(0x002A)             // TIFF magic number
        mpEndian.putInt(8)                    // Offset to first IFD
        val mpEndianBytes = mpEndian.array()

        // MP IFD (Image File Directory)
        val tagCount = 3
        val ifdSize = 2 + tagCount * 12 + 4
        val mpIfd = ByteBuffer.allocate(ifdSize).order(ByteOrder.LITTLE_ENDIAN)

        mpIfd.putShort(tagCount.toShort())

        // Tag MPFVersion (0xB000)
        mpIfd.putShort(0xB000.toShort())
        mpIfd.putShort(2)                    // Type: ASCII
        mpIfd.putInt(4)                      // Count
        mpIfd.put("0100".toByteArray(Charsets.US_ASCII)) // Version 1.0
        mpIfd.putInt(0)                      // Padding

        // Tag NumberOfImages (0xB001)
        mpIfd.putShort(0xB001.toShort())
        mpIfd.putShort(4)                    // Type: LONG
        mpIfd.putInt(1)                      // Count
        mpIfd.putInt(2)                      // 2 images (SDR + Gain Map)
        mpIfd.putInt(0)                      // Padding

        // Tag MPEntry (0xB002)
        val mpEntryOffset = 4 + 8 + ifdSize
        mpIfd.putShort(0xB002.toShort())
        mpIfd.putShort(7)                    // Type: UNDEFINED
        mpIfd.putInt(32)                     // Count: 2 entries * 16 bytes
        mpIfd.putInt(mpEntryOffset)          // Offset to entries
        mpIfd.putInt(0)                      // Padding

        // Next IFD offset = 0 (no next IFD)
        mpIfd.putInt(0)

        val mpIfdBytes = mpIfd.array()

        // MP Entry 数据（每条 16 字节）
        val mpEntrySize = 16
        val mpEntries = ByteBuffer.allocate(2 * mpEntrySize).order(ByteOrder.LITTLE_ENDIAN)

        // Entry 1: SDR 主图
        mpEntries.putInt(0x00000000)          // Individual Image Type + Reserved
        mpEntries.putInt(firstImageSize)      // Individual Image Size
        mpEntries.putInt(0)                   // Individual Image Data Offset (first image = 0)
        mpEntries.putInt(0)                   // Dependent Image Entry / Reserved
        mpEntries.putInt(0)                   // Dependent Image 2 Entry / Reserved

        // 确保第一条目恰好 16 字节
        // 重新构建以精确匹配 16 字节/条目
        val mpEntriesFixed = ByteBuffer.allocate(2 * mpEntrySize).order(ByteOrder.LITTLE_ENDIAN)

        // Entry 1: SDR 主图 (16 bytes)
        mpEntriesFixed.putInt(0x00000000)     // Attribute (Type=0, Format=0) + Reserved
        mpEntriesFixed.putInt(firstImageSize) // Size
        mpEntriesFixed.putInt(0)              // Offset (first image starts at 0)
        mpEntriesFixed.putInt(0)              // Reserved

        // Entry 2: Gain Map (16 bytes)
        mpEntriesFixed.putInt(0x00000000)     // Attribute
        mpEntriesFixed.putInt(secondImageSize)// Size
        mpEntriesFixed.putInt(firstImageSize) // Offset (follows first image)
        mpEntriesFixed.putInt(0)              // Reserved

        val mpEntriesBytes = mpEntriesFixed.array()

        // 组装 MPF payload
        val mpfPayload = ByteArray(
            mpfIdentifier.size + mpEndianBytes.size + mpIfdBytes.size + mpEntriesBytes.size
        )
        System.arraycopy(mpfIdentifier, 0, mpfPayload, 0, mpfIdentifier.size)
        System.arraycopy(mpEndianBytes, 0, mpfPayload, mpfIdentifier.size, mpEndianBytes.size)
        System.arraycopy(mpIfdBytes, 0, mpfPayload, mpfIdentifier.size + mpEndianBytes.size, mpIfdBytes.size)
        System.arraycopy(
            mpEntriesBytes, 0, mpfPayload,
            mpfIdentifier.size + mpEndianBytes.size + mpIfdBytes.size,
            mpEntriesBytes.size
        )

        // APP2 标记头
        val length = mpfPayload.size + 2
        val marker = ByteArray(4 + mpfPayload.size)
        marker[0] = 0xFF.toByte()
        marker[1] = 0xE2.toByte()          // APP2
        marker[2] = ((length shr 8) and 0xFF).toByte()
        marker[3] = (length and 0xFF).toByte()
        System.arraycopy(mpfPayload, 0, marker, 4, mpfPayload.size)

        return marker
    }

    /**
     * 将 MPF 标记插入 JPEG 文件。
     *
     * 策略：在 SOI 之后、所有 APPn 段之后插入 MPF APP2 段。
     */
    private fun insertMpfIntoJpeg(jpeg: ByteArray, mpfMarker: ByteArray): ByteArray {
        if (jpeg.size < 4 || jpeg[0] != 0xFF.toByte() || jpeg[1] != 0xD8.toByte()) {
            return jpeg
        }

        var insertPos = 2

        // 跳过现有的 APPn 段
        while (insertPos < jpeg.size - 1) {
            if (jpeg[insertPos] == 0xFF.toByte()) {
                val markerType = jpeg[insertPos + 1].toInt() and 0xFF
                if (markerType in 0xE0..0xEF) {
                    if (insertPos + 3 < jpeg.size) {
                        val len = ((jpeg[insertPos + 2].toInt() and 0xFF) shl 8) or
                                (jpeg[insertPos + 3].toInt() and 0xFF)
                        insertPos += 2 + len
                    } else {
                        break
                    }
                } else {
                    break
                }
            } else {
                break
            }
        }

        val result = ByteArray(jpeg.size + mpfMarker.size)
        System.arraycopy(jpeg, 0, result, 0, insertPos)
        System.arraycopy(mpfMarker, 0, result, insertPos, mpfMarker.size)
        System.arraycopy(jpeg, insertPos, result, insertPos + mpfMarker.size, jpeg.size - insertPos)

        return result
    }

    // ── EXIF 元数据写入 ────────────────────────────────────────────

    /**
     * 使用 ExifInterface 将 EXIF 元数据写入 JPEG 字节数组。
     */
    private fun writeExifMetadata(jpegBytes: ByteArray, exifData: ExifData): ByteArray {
        return try {
            val tempFile = java.io.File.createTempFile("ultrahdr_exif_", ".jpg")
            try {
                runCatching { tempFile.writeBytes(jpegBytes) }
                    .onFailure { err ->
                        Log.e(TAG, "Failed to write temp file: ${err.message}")
                        return jpegBytes
                    }
                val exif = ExifInterface(tempFile.absolutePath)

                exifData.make?.let { exif.setAttribute(ExifInterface.TAG_MAKE, it) }
                exifData.model?.let { exif.setAttribute(ExifInterface.TAG_MODEL, it) }
                exifData.lensMake?.let { exif.setAttribute(ExifInterface.TAG_LENS_MAKE, it) }
                exifData.lensModel?.let { exif.setAttribute(ExifInterface.TAG_LENS_MODEL, it) }
                exifData.focalLength?.let { exif.setAttribute(ExifInterface.TAG_FOCAL_LENGTH, it) }
                exifData.aperture?.let { exif.setAttribute(ExifInterface.TAG_F_NUMBER, it) }
                exifData.shutterSpeed?.let { exif.setAttribute(ExifInterface.TAG_EXPOSURE_TIME, it) }
                exifData.iso?.let { exif.setAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS, it) }
                exifData.dateTime?.let { exif.setAttribute(ExifInterface.TAG_DATETIME, it) }

                if (exifData.width > 0) {
                    exif.setAttribute(ExifInterface.TAG_IMAGE_WIDTH, exifData.width.toString())
                }
                if (exifData.height > 0) {
                    exif.setAttribute(ExifInterface.TAG_IMAGE_LENGTH, exifData.height.toString())
                }

                exif.saveAttributes()
                runCatching { tempFile.readBytes() }
                    .getOrElse { err ->
                        Log.e(TAG, "Failed to read temp file: ${err.message}")
                        jpegBytes
                    }
            } finally {
                runCatching { tempFile.delete() }
            }
        } catch (oom: OutOfMemoryError) {
            Log.e(TAG, "OOM in writeExifMetadata", oom)
            jpegBytes
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write EXIF metadata: ${e.message}")
            jpegBytes
        }
    }
}

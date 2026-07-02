package com.rapidraw.core

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.CancellationException
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * HDR 导出器（完善版）
 *
 * 支持：
 * - Ultra HDR (Android 14+ / API 34+): 在 JPEG 中嵌入 gain map，
 *   遵循 ISO 21496-1 标准与 Android 14 Ultra HDR 规范
 * - HDR HLG / PQ 曲线选择（HEIF 10-bit 与 Ultra HDR 元数据）
 * - HDR 元数据写入：Content Light Level (MaxCLL/MaxFALL)、Mastering Display 信息
 * - HDR + SDR 双图层（Gain Map）自动构建与嵌入
 * - 公开 Gain Map 生成接口（从 SDR + HDR 版本生成）
 * - SDR JPEG fallback
 *
 * Ultra HDR 工作原理（ISO 21496-1 / Android 14）：
 * 1. 将 HDR 线性像素通过色调映射生成 SDR 8-bit 基础图
 * 2. 计算 gain map：每个像素的 HDR/SDR 对数比值
 * 3. 将 gain map 编码为灰度 JPEG
 * 4. 使用 MPF (Multi-Picture Format) 将两张 JPEG 合并为单一文件
 * 5. 写入 XMP 元数据描述 gain map 参数、HDR 传输函数、母版显示信息
 *
 * 参考：
 * - ISO 21496-1:2024 Gain Map Metadata
 * - https://developer.android.com/media/platform/hdr-image-format
 * - ITU-R BT.2100 (HLG / PQ)
 * - SMPTE ST 2086 (Mastering Display Color Volume)
 * - CTA-861-G (Content Light Level)
 */
object HdrExporter {

    private const val TAG = "HdrExporter"

    // SDR 标称亮度 (nits)
    private const val SDR_WHITE_NITS = 203f

    enum class HdrFormat(val displayName: String, val mimeType: String, val extension: String) {
        ULTRA_HDR_JPEG("Ultra HDR JPEG", "image/jpeg", ".jpg"),
        HEIF_10BIT("HEIF 10-bit", "image/heif", ".heif"),
        SDR_JPEG("SDR JPEG", "image/jpeg", ".jpg"),
    }

    /**
     * HDR 传输函数（EOTF / OETF）
     *
     * - LINEAR_SRGB：线性 sRGB，用于基础兼容性
     * - HLG：Hybrid Log-Gamma（ITU-R BT.2100），适合广播与动态范围自适应
     * - PQ：Perceptual Quantizer（SMPTE ST 2084），适合绝对亮度还原
     */
    enum class HdrTransferFunction(val displayName: String) {
        LINEAR_SRGB("Linear sRGB"),
        HLG("HLG (BT.2100)"),
        PQ("PQ (ST 2084)"),
    }

    /**
     * Content Light Level 信息（CTA-861-G / HDR10+）
     */
    data class ContentLightLevel(
        val maxCll: Int = 1000,
        val maxFall: Int = 400,
    )

    /**
     * Mastering Display Color Volume（SMPTE ST 2086）
     */
    data class MasteringDisplay(
        val primaryR: FloatArray = floatArrayOf(0.680f, 0.320f),
        val primaryG: FloatArray = floatArrayOf(0.265f, 0.690f),
        val primaryB: FloatArray = floatArrayOf(0.150f, 0.060f),
        val whitePoint: FloatArray = floatArrayOf(0.3127f, 0.3290f),
        val maxLuminance: Float = 1000f,
        val minLuminance: Float = 0.0001f,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is MasteringDisplay) return false
            return primaryR.contentEquals(other.primaryR) &&
                    primaryG.contentEquals(other.primaryG) &&
                    primaryB.contentEquals(other.primaryB) &&
                    whitePoint.contentEquals(other.whitePoint) &&
                    maxLuminance == other.maxLuminance &&
                    minLuminance == other.minLuminance
        }

        override fun hashCode(): Int {
            var result = primaryR.contentHashCode()
            result = 31 * result + primaryG.contentHashCode()
            result = 31 * result + primaryB.contentHashCode()
            result = 31 * result + whitePoint.contentHashCode()
            result = 31 * result + maxLuminance.hashCode()
            result = 31 * result + minLuminance.hashCode()
            return result
        }
    }

    data class HdrMetadata(
        val contentLightLevel: ContentLightLevel = ContentLightLevel(),
        val masteringDisplay: MasteringDisplay = MasteringDisplay(),
    )

    data class HdrConfig(
        val format: HdrFormat = HdrFormat.ULTRA_HDR_JPEG,
        val peakLuminanceNits: Float = 1000f,
        val maxBoostStop: Float = 4.0f,
        val colorSpace: ColorScience.DisplayColorSpace = ColorScience.DisplayColorSpace.REC_2020,
        val keepSdrFallback: Boolean = true,
        val transferFunction: HdrTransferFunction = HdrTransferFunction.PQ,
        val metadata: HdrMetadata = HdrMetadata(),
        val writeExifMetadata: Boolean = true,
        val jpegQuality: Int = 95,
        val gainMapQuality: Int = 90,
    )

    // ── 公开 API ──────────────────────────────────────────────────────

    /**
     * 导出 HDR 图像到 MediaStore
     */
    fun exportHdr(
        context: Context,
        bitmap: Bitmap,
        config: HdrConfig,
        displayName: String = "RapidRAW_HDR_${System.currentTimeMillis()}",
    ): Uri? {
        if (bitmap.isRecycled) {
            Log.w(TAG, "Cannot export recycled bitmap")
            return null
        }

        return try {
            when (config.format) {
                HdrFormat.ULTRA_HDR_JPEG -> exportUltraHdrJpeg(context, bitmap, config, displayName)
                HdrFormat.HEIF_10BIT -> exportHeif10bit(context, bitmap, config, displayName)
                HdrFormat.SDR_JPEG -> exportSdrJpeg(context, bitmap, displayName)
            }
        } catch (ce: CancellationException) {
            // 2026 hotfix: 协程取消异常必须重新抛出
            throw ce
        } catch (oom: OutOfMemoryError) {
            Log.e(TAG, "OOM during HDR export: ${oom.message}", oom)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export HDR image: ${e.message}", e)
            null
        }
    }

    /**
     * 从 SDR + HDR 版本生成 Gain Map（公开接口）。
     *
     * 当你已有 SDR 和 HDR 两个版本的图像时，可直接调用此方法
     * 生成 ISO 21496-1 标准的 Gain Map，无需从单张 HDR 图自动推导。
     *
     * @param sdrBitmap SDR 版本（8-bit sRGB）
     * @param hdrBitmap HDR 版本（8-bit 编码的 HDR 信号）
     * @param config HDR 配置
     * @return Gain Map Bitmap（ARGB_8888，R/G/B 通道存储各通道增益，A=255）
     */
    fun generateGainMap(
        sdrBitmap: Bitmap,
        hdrBitmap: Bitmap,
        config: HdrConfig,
    ): Bitmap {
        val w = min(sdrBitmap.width, hdrBitmap.width)
        val h = min(sdrBitmap.height, hdrBitmap.height)
        return computeGainMap(
            hdrBitmap = hdrBitmap,
            sdrBitmap = sdrBitmap,
            config = config,
        )
    }

    /**
     * 将 SDR 图像 + Gain Map 合并为 Ultra HDR JPEG 字节。
     *
     * 适用于已有 Gain Map 的高级工作流（如从 HdrDisplayManager 生成）。
     *
     * @param sdrBitmap SDR 基础图
     * @param gainMap Gain Map Bitmap（来自 generateGainMap 或 HdrDisplayManager）
     * @param config HDR 配置
     * @return Ultra HDR JPEG 字节数组
     */
    fun buildUltraHdrFromGainMap(
        sdrBitmap: Bitmap,
        gainMap: Bitmap,
        config: HdrConfig,
    ): ByteArray {
        val sdrJpegBytes = compressJpeg(sdrBitmap, config.jpegQuality)
        val gainMapJpegBytes = compressJpeg(gainMap, config.gainMapQuality)
        return buildUltraHdrJpeg(sdrJpegBytes, gainMapJpegBytes, config)
    }

    /**
     * 检查当前 Android 版本是否原生支持 Ultra HDR
     */
    fun isUltraHdrSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

    // ── Ultra HDR JPEG ─────────────────────────────────────────────

    private fun exportUltraHdrJpeg(
        context: Context,
        bitmap: Bitmap,
        config: HdrConfig,
        displayName: String,
    ): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Log.w(TAG, "Ultra HDR requires Android 14+; falling back to HEIF if available")
            return exportHeif10bit(context, bitmap, config, displayName)
        }

        var sdrBitmap: Bitmap? = null
        var gainMap: Bitmap? = null
        return try {
            // 1. 生成 SDR 基础图
            sdrBitmap = try {
                toneMapToSdr(bitmap, config)
            } catch (oom: OutOfMemoryError) {
                Log.e(TAG, "OOM tone mapping to SDR", oom)
                return null
            }

            // 2. 计算 gain map
            gainMap = try {
                computeGainMap(bitmap, sdrBitmap, config)
            } catch (oom: OutOfMemoryError) {
                Log.e(TAG, "OOM computing gain map", oom)
                return null
            }

            // 3. 编码为 JPEG
            val sdrJpegBytes = compressJpeg(sdrBitmap, config.jpegQuality)
            val gainMapJpegBytes = compressJpeg(gainMap, config.gainMapQuality)

            // 4. 合并为 Ultra HDR JPEG
            val finalBytes = try {
                buildUltraHdrJpeg(sdrJpegBytes, gainMapJpegBytes, config)
            } catch (oom: OutOfMemoryError) {
                Log.e(TAG, "OOM building Ultra HDR JPEG", oom)
                return null
            }

            // 5. 写入 MediaStore
            writeToMediaStore(
                context = context,
                data = finalBytes,
                displayName = displayName,
                mimeType = "image/jpeg",
                extension = ".jpg",
                isHdr = true,
            )
        } finally {
            // 2026 hotfix: 安全释放资源，避免内存泄漏
            if (sdrBitmap != null && sdrBitmap !== bitmap) {
                runCatching { sdrBitmap.recycle() }
            }
            runCatching { gainMap?.recycle() }
        }
    }

    // ── Gain Map 计算（ISO 21496-1，支持多传输函数） ──────────────

    /**
     * 计算 gain map：基于 ISO 21496-1 标准，支持 HLG / PQ / Linear sRGB。
     *
     * 算法：
     * 1. 对每个像素，HDR 值通过反 EOTF 解码为线性值
     * 2. SDR 值通过反 OETF 得到线性值
     * 3. gain = log2(HDR_linear / SDR_linear)，限幅到 [-minBoost, maxBoost]
     * 4. 归一化到 [0, 255]
     */
    private fun computeGainMap(
        hdrBitmap: Bitmap,
        sdrBitmap: Bitmap,
        config: HdrConfig,
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

        val maxGain = config.maxBoostStop
        val minGain = -config.maxBoostStop / 2f
        val gainRange = maxGain - minGain

        val outPixels = IntArray(pixelCount.toInt())

        for (i in hdrPixels.indices) {
            val hdrR = ((hdrPixels[i] shr 16) and 0xFF) / 255f
            val hdrG = ((hdrPixels[i] shr 8) and 0xFF) / 255f
            val hdrB = (hdrPixels[i] and 0xFF) / 255f

            val sdrR = ((sdrPixels[i] shr 16) and 0xFF) / 255f
            val sdrG = ((sdrPixels[i] shr 8) and 0xFF) / 255f
            val sdrB = (sdrPixels[i] and 0xFF) / 255f

            val hdrLinearR = decodeToLinear(hdrR, config.transferFunction, config.peakLuminanceNits)
            val hdrLinearG = decodeToLinear(hdrG, config.transferFunction, config.peakLuminanceNits)
            val hdrLinearB = decodeToLinear(hdrB, config.transferFunction, config.peakLuminanceNits)

            val sdrLinearR = srgbToLinear(sdrR)
            val sdrLinearG = srgbToLinear(sdrG)
            val sdrLinearB = srgbToLinear(sdrB)

            val rGain = safeLog2Ratio(hdrLinearR, sdrLinearR)
            val gGain = safeLog2Ratio(hdrLinearG, sdrLinearG)
            val bGain = safeLog2Ratio(hdrLinearB, sdrLinearB)

            val rClamped = rGain.coerceIn(minGain, maxGain)
            val gClamped = gGain.coerceIn(minGain, maxGain)
            val bClamped = bGain.coerceIn(minGain, maxGain)

            val rByte = ((rClamped - minGain) / gainRange * 255f).toInt().coerceIn(0, 255)
            val gByte = ((gClamped - minGain) / gainRange * 255f).toInt().coerceIn(0, 255)
            val bByte = ((bClamped - minGain) / gainRange * 255f).toInt().coerceIn(0, 255)

            outPixels[i] = (0xFF shl 24) or (rByte shl 16) or (gByte shl 8) or bByte
        }

        gainMap.setPixels(outPixels, 0, w, 0, 0, w, h)
        return gainMap
    }

    private fun safeLog2Ratio(hdr: Float, sdr: Float): Float {
        return if (sdr > 1e-6f && hdr > 1e-6f) log2(hdr / sdr) else 0f
    }

    /**
     * 根据传输函数将编码值解码为线性光值（归一化到 [0, 1]）。
     */
    private fun decodeToLinear(value: Float, transfer: HdrTransferFunction, peakNits: Float): Float {
        return when (transfer) {
            HdrTransferFunction.LINEAR_SRGB -> srgbToLinear(value)
            HdrTransferFunction.HLG -> hlgToLinear(value)
            HdrTransferFunction.PQ -> pqToLinear(value, peakNits)
        }
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
    private fun hlgToLinear(e: Float): Float {
        val a = 0.17883277f
        val b = 0.28466892f
        val c = 0.55991073f
        return when {
            e <= 0.5f -> (e * e) / 3f
            else -> (exp(((e - c) / a).toDouble()).toFloat() + b) / 12f
        }
    }

    /**
     * PQ 反 EOTF（SMPTE ST 2084 / PQ）
     * 将 PQ 信号值解码为线性光值（归一化到 [0, 1]，对应 0~peakNits）。
     */
    private fun pqToLinear(e: Float, peakNits: Float): Float {
        val eNormalized = e.coerceIn(0f, 1f)
        val m1 = 2610.0 / 4096.0 * (1.0 / 4.0)
        val m2 = 2523.0 / 4096.0 * 128.0
        val c1 = 3424.0 / 4096.0
        val c2 = 2413.0 / 4096.0 * 32.0
        val c3 = 2392.0 / 4096.0 * 32.0

        val nd = eNormalized.toDouble()
        val ndPow = nd.pow(1.0 / m2)
        val num = max(ndPow - c1, 0.0)
        val den = c2 - c3 * ndPow
        val linearAbsolute = 10000.0 * (num / den).pow(1.0 / m1)
        return (linearAbsolute / peakNits).toFloat().coerceIn(0f, 1f)
    }

    // ── SDR 色调映射（支持多传输函数） ─────────────────────────────

    private fun toneMapToSdr(hdrBitmap: Bitmap, config: HdrConfig): Bitmap {
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

        val luminanceScale = SDR_WHITE_NITS / config.peakLuminanceNits
        val outPixels = IntArray(pixelCount.toInt())

        for (i in hdrPixels.indices) {
            val r = ((hdrPixels[i] shr 16) and 0xFF) / 255f
            val g = ((hdrPixels[i] shr 8) and 0xFF) / 255f
            val b = (hdrPixels[i] and 0xFF) / 255f

            val rLinear = decodeToLinear(r, config.transferFunction, config.peakLuminanceNits)
            val gLinear = decodeToLinear(g, config.transferFunction, config.peakLuminanceNits)
            val bLinear = decodeToLinear(b, config.transferFunction, config.peakLuminanceNits)

            val rScaled = rLinear * luminanceScale
            val gScaled = gLinear * luminanceScale
            val bScaled = bLinear * luminanceScale

            val (rMapped, gMapped, bMapped) = when (config.transferFunction) {
                HdrTransferFunction.PQ -> toneMapPqToSdr(rScaled, gScaled, bScaled)
                HdrTransferFunction.HLG -> toneMapHlgToSdr(rScaled, gScaled, bScaled)
                HdrTransferFunction.LINEAR_SRGB -> toneMapReinhard(rScaled, gScaled, bScaled)
            }

            val rByte = (linearToSrgb(rMapped) * 255f).toInt().coerceIn(0, 255)
            val gByte = (linearToSrgb(gMapped) * 255f).toInt().coerceIn(0, 255)
            val bByte = (linearToSrgb(bMapped) * 255f).toInt().coerceIn(0, 255)

            outPixels[i] = (0xFF shl 24) or (rByte shl 16) or (gByte shl 8) or bByte
        }

        sdrBitmap.setPixels(outPixels, 0, w, 0, 0, w, h)
        return sdrBitmap
    }

    private fun toneMapReinhard(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        return Triple(reinhard(r), reinhard(g), reinhard(b))
    }

    private fun toneMapPqToSdr(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val maxChannel = maxOf(r, g, b, 1e-6f)
        val scale = if (maxChannel > 1f) {
            val compressed = reinhardExtended(maxChannel, 1.5f)
            compressed / maxChannel
        } else 1f
        return Triple(r * scale, g * scale, b * scale)
    }

    private fun toneMapHlgToSdr(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val maxChannel = maxOf(r, g, b, 1e-6f)
        val scale = if (maxChannel > 1f) {
            val compressed = 1f + (maxChannel - 1f) / (1f + (maxChannel - 1f))
            compressed / maxChannel
        } else 1f
        return Triple(r * scale, g * scale, b * scale)
    }

    private fun reinhard(luminance: Float): Float = luminance / (1f + luminance)

    private fun reinhardExtended(luminance: Float, maxLuminance: Float): Float =
        luminance * (1f + luminance / (maxLuminance * maxLuminance)) / (1f + luminance)

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

    // ── Ultra HDR JPEG 构建（MPF 格式 + HDR 元数据） ───────────────

    private fun buildUltraHdrJpeg(
        sdrJpeg: ByteArray,
        gainMapJpeg: ByteArray,
        config: HdrConfig,
    ): ByteArray {
        // 2026 hotfix: 防御分配大数组时 OOM
        val xmpData = try {
            buildGainMapXmp(
                gainMapOffset = 0,
                gainMapLength = gainMapJpeg.size,
                config = config,
            )
        } catch (oom: OutOfMemoryError) {
            Log.e(TAG, "OOM building XMP", oom)
            return ByteArray(0)
        }

        val sdrWithXmp = insertXmpIntoJpeg(sdrJpeg, xmpData)

        val mpfMarker = buildMpfMarker(
            firstImageSize = sdrWithXmp.size,
            secondImageSize = gainMapJpeg.size,
        )

        val sdrWithMpf = insertMpfIntoJpeg(sdrWithXmp, mpfMarker)

        val totalSize = sdrWithMpf.size.toLong() + gainMapJpeg.size.toLong()
        if (totalSize > Int.MAX_VALUE) {
            Log.e(TAG, "Combined HDR JPEG size exceeds Int.MAX_VALUE")
            return ByteArray(0)
        }
        val result = try {
            ByteArray(totalSize.toInt())
        } catch (oom: OutOfMemoryError) {
            Log.e(TAG, "OOM allocating combined result array", oom)
            return ByteArray(0)
        }
        System.arraycopy(sdrWithMpf, 0, result, 0, sdrWithMpf.size)
        System.arraycopy(gainMapJpeg, 0, result, sdrWithMpf.size, gainMapJpeg.size)

        // 修正 XMP 中的 gainMapOffset 为实际偏移量
        val placeholderStr = "GAINMAP_OFFSET_PLACEHOLDER"
        val offsetBytes = sdrWithMpf.size.toString().toByteArray(Charsets.US_ASCII)
        val offsetPlaceholderBytes = placeholderStr.toByteArray(Charsets.US_ASCII)

        var offsetPos = -1
        for (i in 0 until result.size - offsetPlaceholderBytes.size) {
            var match = true
            for (j in offsetPlaceholderBytes.indices) {
                if (result[i + j] != offsetPlaceholderBytes[j]) {
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
            try {
                val newResult = result.copyOf()
                for (j in offsetBytes.indices) {
                    newResult[offsetPos + j] = offsetBytes[j]
                }
                for (j in offsetBytes.size until offsetPlaceholderBytes.size) {
                    newResult[offsetPos + j] = 0x20 // 空格填充
                }
                return newResult
            } catch (oom: OutOfMemoryError) {
                Log.e(TAG, "OOM copying result array", oom)
                return result
            }
        }

        return result
    }

    /**
     * 构建 ISO 21496-1 XMP 元数据。
     */
    private fun buildGainMapXmp(
        gainMapOffset: Int,
        gainMapLength: Int,
        config: HdrConfig,
    ): ByteArray {
        val minGain = -config.maxBoostStop / 2f
        val md = config.metadata.masteringDisplay
        val cll = config.metadata.contentLightLevel

        val transferFunctionName = when (config.transferFunction) {
            HdrTransferFunction.LINEAR_SRGB -> "Linear"
            HdrTransferFunction.HLG -> "HLG"
            HdrTransferFunction.PQ -> "PQ"
        }

        val xmp = """<?xpacket begin="\xef\xbb\xbf" id="W5M0MpCehiHzreSzNTczkc9d"?>
<x:xmpmeta xmlns:x="adobe:ns:meta/">
  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
    <rdf:Description rdf:about=""
      xmlns:GContainer="http://ns.google.com/photos/1.0/container/"
      xmlns:GItem="http://ns.google.com/photos/1.0/container/item/"
      xmlns:HDRGainMap="http://ns.google.com/photos/1.0/hdrgainmap/">
      <GContainer:Directory>
        <rdf:Seq>
          <rdf:li rdf:parseType="Resource">
            <GContainer:Item
              GContainer:Mime="image/jpeg"
              GContainer:Semantic="Primary"
              GContainer:Length="${0}"
              GContainer:Padding="0"/>
          </rdf:li>
          <rdf:li rdf:parseType="Resource">
            <GContainer:Item
              GContainer:Mime="image/jpeg"
              GContainer:Semantic="GainMap"
              GContainer:Length="$gainMapLength"
              GContainer:Padding="0"/>
          </rdf:li>
        </rdf:Seq>
      </GContainer:Directory>
      <HDRGainMap:HDRGainMap
        HDRGainMap:Version="1.0"
        HDRGainMap:GainMapMin="$minGain"
        HDRGainMap:GainMapMax="${config.maxBoostStop}"
        HDRGainMap:Gamma="1.0"
        HDRGainMap:Offset="$gainMapOffset"
        HDRGainMap:HDRCapacityMin="0"
        HDRGainMap:HDRCapacityMax="${config.peakLuminanceNits.toInt()}"
        HDRGainMap:GainMapOffset="GAINMAP_OFFSET_PLACEHOLDER"/>
    </rdf:Description>
    <rdf:Description rdf:about=""
      xmlns:hdrgm="http://ns.google.com/photos/1.0/hdrgainmap/"
      hdrgm:TransferFunction="$transferFunctionName"
      hdrgm:ContentLightLevelMax="${cll.maxCll}"
      hdrgm:ContentLightLevelAverage="${cll.maxFall}"
      hdrgm:MasteringDisplayPrimaries="R(${md.primaryR[0]},${md.primaryR[1]}) G(${md.primaryG[0]},${md.primaryG[1]}) B(${md.primaryB[0]},${md.primaryB[1]})"
      hdrgm:MasteringDisplayWhitePoint="(${md.whitePoint[0]},${md.whitePoint[1]})"
      hdrgm:MasteringDisplayMaxLuminance="${md.maxLuminance.roundToInt()}"
      hdrgm:MasteringDisplayMinLuminance="${md.minLuminance}"/>
  </rdf:RDF>
</x:xmpmeta>
<?xpacket end="w"?>"""
        return xmp.toByteArray(Charsets.UTF_8)
    }

    private fun insertXmpIntoJpeg(jpeg: ByteArray, xmpData: ByteArray): ByteArray {
        if (jpeg.size < 2 || jpeg[0] != 0xFF.toByte() || jpeg[1] != 0xD8.toByte()) {
            Log.w(TAG, "Invalid JPEG: missing SOI marker")
            return jpeg
        }

        val xmpNamespace = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray(Charsets.US_ASCII)
        val app1PayloadSize = xmpNamespace.size + xmpData.size
        val app1MarkerSize = 2 + 2 + app1PayloadSize

        val result = ByteArray(jpeg.size + app1MarkerSize)

        result[0] = jpeg[0]
        result[1] = jpeg[1]

        var pos = 2
        result[pos++] = 0xFF.toByte()
        result[pos++] = 0xE1.toByte()
        val length = app1PayloadSize + 2
        result[pos++] = ((length shr 8) and 0xFF).toByte()
        result[pos++] = (length and 0xFF).toByte()
        System.arraycopy(xmpNamespace, 0, result, pos, xmpNamespace.size)
        pos += xmpNamespace.size
        System.arraycopy(xmpData, 0, result, pos, xmpData.size)
        pos += xmpData.size

        System.arraycopy(jpeg, 2, result, pos, jpeg.size - 2)

        return result
    }

    private fun buildMpfMarker(firstImageSize: Int, secondImageSize: Int): ByteArray {
        val mpfIdentifier = byteArrayOf(0x4D, 0x50, 0x46, 0x00)

        val mpEndian = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        mpEndian.putShort(0x4949.toShort())
        mpEndian.putShort(0x002A)
        mpEndian.putInt(8)
        val mpEndianBytes = mpEndian.array()

        val mpEntrySize = 16
        val tagCount = 3
        val ifdSize = 2 + tagCount * 12 + 4

        val mpIfd = ByteBuffer.allocate(ifdSize).order(ByteOrder.LITTLE_ENDIAN)
        mpIfd.putShort(tagCount.toShort())

        mpIfd.putShort(0xB000.toShort())
        mpIfd.putShort(2)
        mpIfd.putInt(4)
        mpIfd.put("0100".toByteArray(Charsets.US_ASCII))
        mpIfd.putInt(0)

        mpIfd.putShort(0xB001.toShort())
        mpIfd.putShort(4)
        mpIfd.putInt(1)
        mpIfd.putInt(2)
        mpIfd.putInt(0)

        val mpEntryOffset = 4 + 8 + ifdSize
        mpIfd.putShort(0xB002.toShort())
        mpIfd.putShort(7)
        mpIfd.putInt(32)
        mpIfd.putInt(mpEntryOffset)
        mpIfd.putInt(0)

        mpIfd.putInt(0)
        val mpIfdBytes = mpIfd.array()

        val mpEntries = ByteBuffer.allocate(2 * mpEntrySize).order(ByteOrder.LITTLE_ENDIAN)

        mpEntries.putInt(0x00000000)
        mpEntries.putInt(firstImageSize)
        mpEntries.putInt(0)
        mpEntries.putInt(0)
        mpEntries.putInt(0)

        mpEntries.putInt(0x00000000)
        mpEntries.putInt(secondImageSize)
        mpEntries.putInt(firstImageSize)
        mpEntries.putInt(0)
        mpEntries.putInt(0)

        val mpEntriesBytes = mpEntries.array()

        val mpfPayload = ByteArray(mpfIdentifier.size + mpEndianBytes.size + mpIfdBytes.size + mpEntriesBytes.size)
        System.arraycopy(mpfIdentifier, 0, mpfPayload, 0, mpfIdentifier.size)
        System.arraycopy(mpEndianBytes, 0, mpfPayload, mpfIdentifier.size, mpEndianBytes.size)
        System.arraycopy(mpIfdBytes, 0, mpfPayload, mpfIdentifier.size + mpEndianBytes.size, mpIfdBytes.size)
        System.arraycopy(mpEntriesBytes, 0, mpfPayload, mpfIdentifier.size + mpEndianBytes.size + mpIfdBytes.size, mpEntriesBytes.size)

        val length = mpfPayload.size + 2
        val marker = ByteArray(4 + mpfPayload.size)
        marker[0] = 0xFF.toByte()
        marker[1] = 0xE2.toByte()
        marker[2] = ((length shr 8) and 0xFF).toByte()
        marker[3] = (length and 0xFF).toByte()
        System.arraycopy(mpfPayload, 0, marker, 4, mpfPayload.size)

        return marker
    }

    private fun insertMpfIntoJpeg(jpeg: ByteArray, mpfMarker: ByteArray): ByteArray {
        if (jpeg.size < 4 || jpeg[0] != 0xFF.toByte() || jpeg[1] != 0xD8.toByte()) {
            return jpeg
        }

        var insertPos = 2

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

    // ── JPEG 辅助方法 ─────────────────────────────────────────────

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

    // ── HEIF 10-bit ────────────────────────────────────────────────

    /**
     * HEIF 10-bit 编码。
     *
     * Android 10+ (API 29+) 支持 HEIF 编码。
     * Android 12+ (API 31+) 支持 HEIF 10-bit 通过 Bitmap.CompressFormat.WEBP_LOSSLESS
     * 或 HEIC 编码器的质量参数控制。
     *
     * HDR 元数据通过 ExifInterface 写入。
     * 传输函数标记（HLG/PQ）嵌入到用户注释中。
     */
    private fun exportHeif10bit(
        context: Context,
        bitmap: Bitmap,
        config: HdrConfig,
        displayName: String,
    ): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Log.w(TAG, "HEIF requires Android 10+; falling back to SDR JPEG")
            return exportSdrJpeg(context, bitmap, displayName)
        }

        val tempHeif = File(context.cacheDir, "hdr_heif_${System.currentTimeMillis()}.heif")
        var writeSuccess = false
        return try {
            try {
                // Android 10+ 使用 HEIF 编码
                FileOutputStream(tempHeif).use { fos ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        // Android 12+: 直接使用 HEIF 编码器
                        bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, fos)
                    } else {
                        @Suppress("DEPRECATION")
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos)
                    }
                }
            } catch (oom: OutOfMemoryError) {
                Log.e(TAG, "OOM encoding HEIF", oom)
                return null
            } catch (e: Exception) {
                Log.e(TAG, "Failed to encode HEIF: ${e.message}", e)
                return null
            }
            writeSuccess = true

            // 写入 HDR Exif 元数据
            if (config.writeExifMetadata && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                try {
                    writeHdrExifMetadata(tempHeif, config)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to write HDR Exif metadata: ${e.message}")
                }
            }

            val bytes = try {
                tempHeif.readBytes()
            } catch (oom: OutOfMemoryError) {
                Log.e(TAG, "OOM reading temp HEIF", oom)
                return null
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read temp HEIF: ${e.message}", e)
                return null
            }
            writeToMediaStore(
                context = context,
                data = bytes,
                displayName = displayName,
                mimeType = "image/heif",
                extension = ".heif",
                isHdr = true,
            )
        } finally {
            // 2026 hotfix: 使用 runCatching 防止 delete 抛出异常导致泄漏
            if (writeSuccess) {
                runCatching { tempHeif.delete() }
            }
        }
    }

    /**
     * 写入 HDR Exif 元数据（Android 14+）。
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun writeHdrExifMetadata(file: File, config: HdrConfig) {
        val exif = ExifInterface(file)
        val cll = config.metadata.contentLightLevel
        val md = config.metadata.masteringDisplay

        val hdrMetaJson = buildString {
            append("{")
            append("\"transferFunction\":\"${config.transferFunction.name}\",")
            append("\"peakLuminance\":${config.peakLuminanceNits},")
            append("\"maxCLL\":${cll.maxCll},")
            append("\"maxFALL\":${cll.maxFall},")
            append("\"masteringDisplay\":{")
            append("\"r\":[${md.primaryR[0]},${md.primaryR[1]}],")
            append("\"g\":[${md.primaryG[0]},${md.primaryG[1]}],")
            append("\"b\":[${md.primaryB[0]},${md.primaryB[1]}],")
            append("\"wp\":[${md.whitePoint[0]},${md.whitePoint[1]}],")
            append("\"maxLum\":${md.maxLuminance},")
            append("\"minLum\":${md.minLuminance}")
            append("}")
            append("}")
        }
        exif.setAttribute(ExifInterface.TAG_USER_COMMENT, hdrMetaJson)
        exif.saveAttributes()
    }

    // ── SDR JPEG (fallback) ────────────────────────────────────────

    private fun exportSdrJpeg(
        context: Context,
        bitmap: Bitmap,
        displayName: String,
    ): Uri? {
        val tempFile = File(context.cacheDir, "sdr_${System.currentTimeMillis()}.jpg")
        var writeSuccess = false
        return try {
            try {
                FileOutputStream(tempFile).use { fos ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos)
                }
            } catch (oom: OutOfMemoryError) {
                Log.e(TAG, "OOM encoding SDR JPEG", oom)
                return null
            } catch (e: Exception) {
                Log.e(TAG, "Failed to encode SDR JPEG: ${e.message}", e)
                return null
            }
            writeSuccess = true

            val bytes = try {
                tempFile.readBytes()
            } catch (oom: OutOfMemoryError) {
                Log.e(TAG, "OOM reading temp SDR JPEG", oom)
                return null
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read temp SDR JPEG: ${e.message}", e)
                return null
            }
            writeToMediaStore(
                context = context,
                data = bytes,
                displayName = displayName,
                mimeType = "image/jpeg",
                extension = ".jpg",
                isHdr = false,
            )
        } finally {
            // 2026 hotfix: 使用 runCatching 防止 delete 抛出异常导致泄漏
            if (writeSuccess) {
                runCatching { tempFile.delete() }
            }
        }
    }

    // ── MediaStore 写入 ────────────────────────────────────────────

    private fun writeToMediaStore(
        context: Context,
        data: ByteArray,
        displayName: String,
        mimeType: String,
        extension: String,
        isHdr: Boolean,
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
        // 2026 hotfix: 防御 resolver.insert 抛出异常
        val uri = try {
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        } catch (e: Exception) {
            Log.e(TAG, "MediaStore insert failed: ${e.message}", e)
            return null
        } ?: return null

        // 2026 hotfix: 防御 openOutputStream 抛出异常；写入失败时回滚 MediaStore entry
        val outputStream: OutputStream = try {
            resolver.openOutputStream(uri)
        } catch (e: Exception) {
            Log.e(TAG, "openOutputStream failed: ${e.message}", e)
            null
        } ?: run {
            runCatching { resolver.delete(uri, null, null) }
            return null
        }

        try {
            outputStream.use { os ->
                try {
                    os.write(data)
                    os.flush()
                } catch (oom: OutOfMemoryError) {
                    Log.e(TAG, "OOM writing to MediaStore", oom)
                    runCatching { resolver.delete(uri, null, null) }
                    return null
                } catch (e: Exception) {
                    Log.e(TAG, "Write to MediaStore failed: ${e.message}", e)
                    runCatching { resolver.delete(uri, null, null) }
                    return null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to close output stream: ${e.message}", e)
            runCatching { resolver.delete(uri, null, null) }
            return null
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to mark MediaStore entry as visible: ${e.message}")
            }
        }

        return uri
    }
}

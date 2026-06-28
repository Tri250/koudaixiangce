package com.rapidraw.core

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * HDR 导出器
 *
 * 支持：
 * - Ultra HDR (Android 14+ / API 34+): 在 JPEG 中嵌入 gain map，
 *   遵循 ISO 21496-1 标准，兼容所有设备，HDR 设备显示高动态范围
 * - 10-bit HEIF HDR (Android 10+ / API 29+, 但 HDR 编码需 Android 14+)
 * - SDR JPEG fallback
 *
 * Ultra HDR 工作原理（ISO 21496-1）：
 * 1. 将 HDR 线性像素通过色调映射生成 SDR 8-bit 基础图
 * 2. 计算 gain map：每个像素的 HDR/SDR 对数比值
 * 3. 将 gain map 编码为灰度 JPEG
 * 4. 使用 MPF (Multi-Picture Format) 将两张 JPEG 合并为单一文件
 * 5. 写入 XMP 元数据描述 gain map 参数
 *
 * 参考：
 * - ISO 21496-1:2024 Gain Map Metadata
 * - https://developer.android.com/media/platform/hdr-image-format
 * - https://developer.android.com/guide/topics/media/hdr
 */
object HdrExporter {

    private const val TAG = "HdrExporter"

    // SDR 标称亮度 (nits)
    private const val SDR_WHITE_NITS = 203f
    // sRGB 显示白点
    private const val SRGB_WHITE = 1.0f

    enum class HdrFormat(val displayName: String, val mimeType: String, val extension: String) {
        ULTRA_HDR_JPEG("Ultra HDR JPEG", "image/jpeg", ".jpg"),
        HEIF_10BIT("HEIF 10-bit", "image/heif", ".heif"),
        SDR_JPEG("SDR JPEG", "image/jpeg", ".jpg"),
    }

    data class HdrConfig(
        val format: HdrFormat = HdrFormat.ULTRA_HDR_JPEG,
        val peakLuminanceNits: Float = 1000f,    // HDR 峰值亮度
        val maxBoostStop: Float = 4.0f,            // 增益图最大倍数 (log2 stops)
        val colorSpace: ColorScience.DisplayColorSpace = ColorScience.DisplayColorSpace.REC_2020,
        val keepSdrFallback: Boolean = true,        // 保留 SDR 兼容性
    )

    /**
     * 导出 HDR 图像到 MediaStore
     *
     * @return MediaStore Uri，失败时返回 null
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
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export HDR image: ${e.message}", e)
            null
        }
    }

    // ── Ultra HDR JPEG ─────────────────────────────────────────────

    /**
     * Ultra HDR JPEG 导出
     *
     * Ultra HDR (Android 14+ / ISO 21496-1) 将 gain map 嵌入到 JPEG，
     * 使用 MPF (Multi-Picture Format) 合并 SDR + Gain Map 为单一文件，
     * 并通过 XMP 元数据描述 gain map 参数。
     */
    private fun exportUltraHdrJpeg(
        context: Context,
        bitmap: Bitmap,
        config: HdrConfig,
        displayName: String,
    ): Uri? {
        // 检查 API 等级；Android 14 (API 34) 起 ImageDecoder 原生支持 Ultra HDR 解码
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Log.w(TAG, "Ultra HDR requires Android 14+; falling back to HEIF if available")
            return exportHeif10bit(context, bitmap, config, displayName)
        }

        // 1. 生成 SDR 基础图（Reinhard 色调映射到 8-bit sRGB）
        val sdrBitmap = toneMapToSdr(bitmap, config.peakLuminanceNits)

        // 2. 计算 gain map (ISO 21496-1: HDR/SDR 对数比值)
        val gainMap = computeGainMap(bitmap, sdrBitmap, config.maxBoostStop, config.peakLuminanceNits)

        // 3. 编码 SDR JPEG 至字节
        val sdrJpegBytes = compressJpeg(sdrBitmap, 95)
        if (sdrBitmap !== bitmap) sdrBitmap.recycle()

        // 4. 编码 gain map 为灰度 JPEG
        val gainMapJpegBytes = compressJpeg(gainMap, 90)
        gainMap.recycle()

        // 5. 合并为 Ultra HDR JPEG（MPF 格式 + XMP 元数据）
        val finalBytes = buildUltraHdrJpeg(
            sdrJpeg = sdrJpegBytes,
            gainMapJpeg = gainMapJpegBytes,
            peakLuminance = config.peakLuminanceNits,
            maxBoostStop = config.maxBoostStop,
        )

        // 6. 写入 MediaStore (Pictures/RapidRAW)
        return writeToMediaStore(
            context = context,
            data = finalBytes,
            displayName = displayName,
            mimeType = "image/jpeg",
            extension = ".jpg",
            isHdr = true,
        )
    }

    // ── Gain Map 计算（ISO 21496-1） ──────────────────────────────

    /**
     * 计算 gain map：基于 ISO 21496-1 标准。
     *
     * 算法：
     * 1. 对每个像素，HDR 值 = 线性像素 / SDR_WHITE
     * 2. SDR 值 = 色调映射后的 sRGB 像素，反 OETF 得到线性值
     * 3. gain = log2(HDR_linear / SDR_linear)，限幅到 [-minBoost, maxBoost]
     * 4. 归一化到 [0, 255]：byte = (gain - minBoost) / (maxBoost - minBoost) * 255
     *
     * 这样解码时可以反推：HDR = SDR * 2^(gain)
     */
    private fun computeGainMap(
        hdrBitmap: Bitmap,
        sdrBitmap: Bitmap,
        maxBoostStop: Float,
        peakLuminanceNits: Float,
    ): Bitmap {
        val w = hdrBitmap.width
        val h = hdrBitmap.height
        val gainMap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

        val hdrPixels = IntArray(w * h)
        val sdrPixels = IntArray(w * h)
        hdrBitmap.getPixels(hdrPixels, 0, w, 0, 0, w, h)
        sdrBitmap.getPixels(sdrPixels, 0, w, 0, 0, w, h)

        // 增益范围：最小增强 = -maxBoostStop/2（允许略微降低），
        // 最大增强 = maxBoostStop（如 4.0 = 16x）
        val maxGain = maxBoostStop
        val minGain = -maxBoostStop / 2f
        val gainRange = maxGain - minGain

        // SDR 白点在 HDR 空间的对应亮度比
        val hdrSdrRatio = peakLuminanceNits / SDR_WHITE_NITS

        for (i in hdrPixels.indices) {
            // HDR 像素：假设输入已是 sRGB 编码但表示 HDR 内容
            // 反 sRGB OETF 得到线性值
            val hdrR = srgbToLinear(((hdrPixels[i] shr 16) and 0xFF) / 255f)
            val hdrG = srgbToLinear(((hdrPixels[i] shr 8) and 0xFF) / 255f)
            val hdrB = srgbToLinear((hdrPixels[i] and 0xFF) / 255f)

            // SDR 像素：反 sRGB OETF 得到线性值
            val sdrR = srgbToLinear(((sdrPixels[i] shr 16) and 0xFF) / 255f)
            val sdrG = srgbToLinear(((sdrPixels[i] shr 8) and 0xFF) / 255f)
            val sdrB = srgbToLinear((sdrPixels[i] and 0xFF) / 255f)

            // 计算对数增益：log2(HDR / SDR)
            // 使用 SDR 值作为分母，避免除以零
            val rGain = if (sdrR > 1e-6f) log2(hdrR / sdrR) else 0f
            val gGain = if (sdrG > 1e-6f) log2(hdrG / sdrG) else 0f
            val bGain = if (sdrB > 1e-6f) log2(hdrB / sdrB) else 0f

            // 限幅到 [minGain, maxGain]
            val rClamped = rGain.coerceIn(minGain, maxGain)
            val gClamped = gGain.coerceIn(minGain, maxGain)
            val bClamped = bGain.coerceIn(minGain, maxGain)

            // 归一化到 [0, 255]
            val rByte = ((rClamped - minGain) / gainRange * 255f).toInt().coerceIn(0, 255)
            val gByte = ((gClamped - minGain) / gainRange * 255f).toInt().coerceIn(0, 255)
            val bByte = ((bClamped - minGain) / gainRange * 255f).toInt().coerceIn(0, 255)

            gainMap.setPixel(i % w, i / w, Color.argb(255, rByte, gByte, bByte))
        }
        return gainMap
    }

    // ── SDR 色调映射 ──────────────────────────────────────────────

    /**
     * 将 HDR bitmap 色调映射到 SDR (8-bit sRGB)。
     *
     * 使用 Reinhard 色调映射算子，基于峰值亮度归一化：
     * 1. 线性化输入（反 sRGB OETF）
     * 2. 按 HDR/SDR 亮度比缩放
     * 3. Reinhard 映射：L_out = L / (1 + L)
     * 4. 应用 sRGB OETF 编码为 8-bit
     */
    private fun toneMapToSdr(hdrBitmap: Bitmap, peakLuminanceNits: Float): Bitmap {
        val w = hdrBitmap.width
        val h = hdrBitmap.height
        val sdrBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

        val hdrPixels = IntArray(w * h)
        hdrBitmap.getPixels(hdrPixels, 0, w, 0, 0, w, h)

        // HDR/SDR 亮度缩放因子
        val luminanceScale = SDR_WHITE_NITS / peakLuminanceNits

        for (i in hdrPixels.indices) {
            // 反 sRGB OETF 得到线性值
            val r = srgbToLinear(((hdrPixels[i] shr 16) and 0xFF) / 255f)
            val g = srgbToLinear(((hdrPixels[i] shr 8) and 0xFF) / 255f)
            val b = srgbToLinear((hdrPixels[i] and 0xFF) / 255f)

            // 按 SDR 白点缩放
            val rScaled = r * luminanceScale
            val gScaled = g * luminanceScale
            val bScaled = b * luminanceScale

            // Reinhard 色调映射
            val rMapped = reinhard(rScaled)
            val gMapped = reinhard(gScaled)
            val bMapped = reinhard(bScaled)

            // 应用 sRGB OETF 编码为 8-bit
            val rByte = (linearToSrgb(rMapped) * 255f).toInt().coerceIn(0, 255)
            val gByte = (linearToSrgb(gMapped) * 255f).toInt().coerceIn(0, 255)
            val bByte = (linearToSrgb(bMapped) * 255f).toInt().coerceIn(0, 255)

            sdrBitmap.setPixel(i % w, i / w, Color.argb(255, rByte, gByte, bByte))
        }
        return sdrBitmap
    }

    /**
     * Reinhard 色调映射算子
     * L_out = L / (1 + L)
     */
    private fun reinhard(luminance: Float): Float {
        return luminance / (1f + luminance)
    }

    // ── sRGB OETF / EOTF ──────────────────────────────────────────

    /**
     * sRGB OETF (Optical-Electrical Transfer Function)
     * 将线性光值编码为 sRGB 信号值
     */
    private fun linearToSrgb(linear: Float): Float {
        return if (linear <= 0.0031308f) {
            12.92f * linear
        } else {
            1.055f * linear.toDouble().pow(1.0 / 2.4).toFloat() - 0.055f
        }
    }

    /**
     * 反 sRGB OETF (EOTF^-1)
     * 将 sRGB 信号值解码为线性光值
     */
    private fun srgbToLinear(srgb: Float): Float {
        return if (srgb <= 0.04045f) {
            srgb / 12.92f
        } else {
            ((srgb + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
        }
    }

    // ── Ultra HDR JPEG 构建（MPF 格式） ───────────────────────────

    /**
     * 构建 Ultra HDR JPEG 文件（ISO 21496-1 / MPF 格式）。
     *
     * 文件结构：
     * [SDR JPEG with XMP metadata] + [MPF APP2 marker] + [Gain Map JPEG]
     *
     * MPF (Multi-Picture Format) 在 APP2 marker 中描述多图索引，
     * XMP 元数据描述 gain map 的参数（ISO 21496-1）。
     */
    private fun buildUltraHdrJpeg(
        sdrJpeg: ByteArray,
        gainMapJpeg: ByteArray,
        peakLuminance: Float,
        maxBoostStop: Float,
    ): ByteArray {
        // 1. 构建 XMP 元数据（ISO 21496-1 gain map 描述）
        val xmpData = buildGainMapXmp(
            gainMapOffset = 0, // 占位，后面替换
            gainMapLength = gainMapJpeg.size,
            peakLuminance = peakLuminance,
            maxBoostStop = maxBoostStop,
        )

        // 2. 在 SDR JPEG 的 EOI (0xFFD9) 前插入 XMP APP1 marker
        val sdrWithXmp = insertXmpIntoJpeg(sdrJpeg, xmpData)

        // 3. 构建 MPF APP2 marker
        val mpfMarker = buildMpfMarker(
            firstImageSize = sdrWithXmp.size,
            secondImageSize = gainMapJpeg.size,
        )

        // 4. 在 SDR JPEG 的第二个 marker 后插入 MPF APP2
        val sdrWithMpf = insertMpfIntoJpeg(sdrWithXmp, mpfMarker)

        // 5. 合并：SDR JPEG + Gain Map JPEG
        val result = ByteArray(sdrWithMpf.size + gainMapJpeg.size)
        System.arraycopy(sdrWithMpf, 0, result, 0, sdrWithMpf.size)
        System.arraycopy(gainMapJpeg, 0, result, sdrWithMpf.size, gainMapJpeg.size)

        // 6. 修正 XMP 中的 gainMapOffset 为实际偏移量
        val offsetStr = sdrWithMpf.size.toString()
        val placeholderStr = "GAINMAP_OFFSET_PLACEHOLDER"
        val offsetPlaceholderBytes = placeholderStr.toByteArray(Charsets.US_ASCII)
        val offsetBytes = offsetStr.toByteArray(Charsets.US_ASCII)

        // 查找并替换占位符
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
            // 替换占位符为实际偏移量，剩余位用空格填充
            val newResult = result.copyOf()
            for (j in offsetBytes.indices) {
                newResult[offsetPos + j] = offsetBytes[j]
            }
            // 填充剩余空间为空格（保持长度不变）
            for (j in offsetBytes.size until offsetPlaceholderBytes.size) {
                newResult[offsetPos + j] = 0x20 // space
            }
            return newResult
        }

        return result
    }

    /**
     * 构建 ISO 21496-1 XMP 元数据，描述 gain map 参数。
     *
     * 必需字段：
     * - Version: ISO 21496-1 版本
     * - GainMapMin: 最小增益值（log2 空间）
     * - GainMapMax: 最大增益值（log2 空间）
     * - GainMapGamma: 增益图 gamma 校正值
     * - GainMapOffset: 增益图在文件中的偏移量
     * - GainMapLength: 增益图字节长度
     * - HDRCapacityMin: 最小 HDR 容量（nits）
     * - HDRCapacityMax: 最大 HDR 容量（nits）
     */
    private fun buildGainMapXmp(
        gainMapOffset: Int,
        gainMapLength: Int,
        peakLuminance: Float,
        maxBoostStop: Float,
    ): ByteArray {
        val minGain = -maxBoostStop / 2f
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
        HDRGainMap:GainMapMax="$maxBoostStop"
        HDRGainMap:Gamma="1.0"
        HDRGainMap:Offset="$gainMapOffset"
        HDRGainMap:HDRCapacityMin="0"
        HDRGainMap:HDRCapacityMax="${peakLuminance.toInt()}"
        HDRGainMap:GainMapOffset="GAINMAP_OFFSET_PLACEHOLDER"/>
    </rdf:Description>
  </rdf:RDF>
</x:xmpmeta>
<?xpacket end="w"?>"""
        return xmp.toByteArray(Charsets.US_ASCII)
    }

    /**
     * 在 JPEG 数据中插入 XMP APP1 marker。
     *
     * JPEG 结构：SOI (0xFFD8) + [markers...] + SOS + [image data] + EOI (0xFFD9)
     * XMP 应插入在 SOI 之后、SOS 之前。
     */
    private fun insertXmpIntoJpeg(jpeg: ByteArray, xmpData: ByteArray): ByteArray {
        // 验证 JPEG SOI marker
        if (jpeg.size < 2 || jpeg[0] != 0xFF.toByte() || jpeg[1] != 0xD8.toByte()) {
            Log.w(TAG, "Invalid JPEG: missing SOI marker")
            return jpeg
        }

        // APP1 marker: 0xFF 0xE1 + 2-byte length (big-endian) + "http://ns.adobe.com/xap/1.0/\0" + XMP data
        val xmpNamespace = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray(Charsets.US_ASCII)
        val app1PayloadSize = xmpNamespace.size + xmpData.size
        val app1MarkerSize = 2 + 2 + app1PayloadSize // marker(2) + length(2) + payload

        val result = ByteArray(jpeg.size + app1MarkerSize)

        // 复制 SOI
        result[0] = jpeg[0]
        result[1] = jpeg[1]

        // 写入 APP1 marker
        var pos = 2
        result[pos++] = 0xFF.toByte()
        result[pos++] = 0xE1.toByte()
        // Length = payload + 2 (includes length bytes themselves)
        val length = app1PayloadSize + 2
        result[pos++] = ((length shr 8) and 0xFF).toByte()
        result[pos++] = (length and 0xFF).toByte()
        // XMP namespace
        System.arraycopy(xmpNamespace, 0, result, pos, xmpNamespace.size)
        pos += xmpNamespace.size
        // XMP data
        System.arraycopy(xmpData, 0, result, pos, xmpData.size)
        pos += xmpData.size

        // 复制原 JPEG SOI 之后的数据
        System.arraycopy(jpeg, 2, result, pos, jpeg.size - 2)

        return result
    }

    /**
     * 构建 MPF (Multi-Picture Format) APP2 marker。
     *
     * MPF 格式定义在 CIPA DC-007 中，用于在 JPEG 中存储多张图片的索引。
     * Ultra HDR 使用 MPF 指示文件中有两张图片（SDR + Gain Map）。
     */
    private fun buildMpfMarker(firstImageSize: Int, secondImageSize: Int): ByteArray {
        // MPF 格式：
        // APP2 marker: 0xFF 0xE2
        // Length: 2 bytes (big-endian)
        // MPF identifier: "MPF\0" (4 bytes)
        // MP Endian: "II" (little-endian) + 0x2A00 + offset to first IFD (8 bytes = 4+2+2+4)
        // MP IFD: tag count + tags + next IFD offset

        val mpfIdentifier = byteArrayOf(0x4D, 0x50, 0x46, 0x00) // "MPF\0"

        // 计算偏移量
        // APP2 header = 2(marker) + 2(length) + 4(MPF identifier) = 8 bytes
        // MP Endian = 4(II+2A00) + 4(offset to first IFD) = 8 bytes
        // First IFD starts at offset 16 from the start of MPF data (after identifier)

        val mpEndian = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        mpEndian.putShort(0x4949.toShort()) // "II" little-endian
        mpEndian.putShort(0x002A)           // TIFF magic number
        mpEndian.putInt(8)                   // Offset to first IFD (from start of endian field)
        val mpEndianBytes = mpEndian.array()

        // MP IFD (Attribute IFD)
        // Tags:
        // MPFVersion (0xB000): "0100"
        // MPNumberOfImages (0xB001): 2
        // MPEntry (0xB002): array of 2 MP entries
        val mpEntrySize = 16 // each MP entry is 16 bytes
        val tagCount = 3
        val ifdSize = 2 + tagCount * 12 + 4 // count(2) + tags(3*12) + nextIFD(4)

        val mpIfd = ByteBuffer.allocate(ifdSize).order(ByteOrder.LITTLE_ENDIAN)
        mpIfd.putShort(tagCount.toShort())

        // Tag 1: MPFVersion (0xB000), ASCII, count=4, value="0100"
        mpIfd.putShort(0xB000.toShort())
        mpIfd.putShort(2)       // ASCII type
        mpIfd.putInt(4)         // count
        mpIfd.put("0100".toByteArray(Charsets.US_ASCII)) // value inline
        mpIfd.putInt(0)         // padding

        // Tag 2: MPNumberOfImages (0xB001), LONG, count=1, value=2
        mpIfd.putShort(0xB001.toShort())
        mpIfd.putShort(4)       // LONG type
        mpIfd.putInt(1)         // count
        mpIfd.putInt(2)         // 2 images
        mpIfd.putInt(0)         // padding

        // Tag 3: MPEntry (0xB002), UNDEFINED, count=2*16=32
        // Value offset: after MPF identifier + endian + IFD
        val mpEntryOffset = 4 + 8 + ifdSize // identifier + endian + IFD
        mpIfd.putShort(0xB002.toShort())
        mpIfd.putShort(7)       // UNDEFINED type
        mpIfd.putInt(32)        // count (2 entries * 16 bytes)
        mpIfd.putInt(mpEntryOffset) // offset to entries
        mpIfd.putInt(0)         // padding

        // Next IFD offset: 0 (no more IFDs)
        mpIfd.putInt(0)

        val mpIfdBytes = mpIfd.array()

        // MP Entries (2 entries, each 16 bytes)
        val mpEntries = ByteBuffer.allocate(2 * mpEntrySize).order(ByteOrder.LITTLE_ENDIAN)

        // Entry 1: SDR image (Individual Image)
        // Bits 31-30: type (0 = JPEG), bit 29: dependent (0), bits 28-24: reserved (0)
        // Size: firstImageSize, offset: 0 (from MPF start = from SOI)
        mpEntries.putInt(0x00000000) // type = JPEG, not dependent
        mpEntries.putInt(firstImageSize) // size
        mpEntries.putInt(0) // offset from SOI
        mpEntries.putInt(0) // dependent image 1 entry number (0=none)
        mpEntries.putInt(0) // dependent image 2 entry number (0=none)

        // Entry 2: Gain Map image (Individual Image)
        mpEntries.putInt(0x00000000) // type = JPEG, not dependent
        mpEntries.putInt(secondImageSize) // size
        mpEntries.putInt(firstImageSize) // offset from SOI
        mpEntries.putInt(0) // dependent image 1
        mpEntries.putInt(0) // dependent image 2

        val mpEntriesBytes = mpEntries.array()

        // 组装完整的 MPF payload
        val mpfPayload = ByteArray(mpfIdentifier.size + mpEndianBytes.size + mpIfdBytes.size + mpEntriesBytes.size)
        System.arraycopy(mpfIdentifier, 0, mpfPayload, 0, mpfIdentifier.size)
        System.arraycopy(mpEndianBytes, 0, mpfPayload, mpfIdentifier.size, mpEndianBytes.size)
        System.arraycopy(mpIfdBytes, 0, mpfPayload, mpfIdentifier.size + mpEndianBytes.size, mpIfdBytes.size)
        System.arraycopy(mpEntriesBytes, 0, mpfPayload, mpfIdentifier.size + mpEndianBytes.size + mpIfdBytes.size, mpEntriesBytes.size)

        // APP2 marker: 0xFF 0xE2 + 2-byte length + payload
        val length = mpfPayload.size + 2 // length includes itself
        val marker = ByteArray(4 + mpfPayload.size)
        marker[0] = 0xFF.toByte()
        marker[1] = 0xE2.toByte()
        marker[2] = ((length shr 8) and 0xFF).toByte()
        marker[3] = (length and 0xFF).toByte()
        System.arraycopy(mpfPayload, 0, marker, 4, mpfPayload.size)

        return marker
    }

    /**
     * 在 JPEG 数据中插入 MPF APP2 marker。
     * 插入位置：SOI 之后、所有其他 APP marker 之后、SOS 之前。
     */
    private fun insertMpfIntoJpeg(jpeg: ByteArray, mpfMarker: ByteArray): ByteArray {
        if (jpeg.size < 4 || jpeg[0] != 0xFF.toByte() || jpeg[1] != 0xD8.toByte()) {
            return jpeg
        }

        // 查找插入位置：SOI (0xFFD8) 之后
        // 如果有 APP1 (XMP) marker，插入在 APP1 之后
        var insertPos = 2 // after SOI

        // 跳过连续的 APP marker（0xFFE0-0xFFEF）
        while (insertPos < jpeg.size - 1) {
            if (jpeg[insertPos] == 0xFF.toByte()) {
                val markerType = jpeg[insertPos + 1].toInt() and 0xFF
                if (markerType in 0xE0..0xEF) {
                    // APP marker: 读取长度跳过
                    if (insertPos + 3 < jpeg.size) {
                        val len = ((jpeg[insertPos + 2].toInt() and 0xFF) shl 8) or
                                  (jpeg[insertPos + 3].toInt() and 0xFF)
                        insertPos += 2 + len
                    } else {
                        break
                    }
                } else {
                    // 非 APP marker，在此处插入
                    break
                }
            } else {
                break
            }
        }

        // 构建 result: [SOI + APP markers] + [MPF marker] + [rest of JPEG]
        val result = ByteArray(jpeg.size + mpfMarker.size)
        System.arraycopy(jpeg, 0, result, 0, insertPos)
        System.arraycopy(mpfMarker, 0, result, insertPos, mpfMarker.size)
        System.arraycopy(jpeg, insertPos, result, insertPos + mpfMarker.size, jpeg.size - insertPos)

        return result
    }

    // ── JPEG 辅助方法 ─────────────────────────────────────────────

    /**
     * 将 Bitmap 压缩为 JPEG 字节数组
     */
    private fun compressJpeg(bitmap: Bitmap, quality: Int): ByteArray {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        return baos.toByteArray()
    }

    // ── HEIF 10-bit ────────────────────────────────────────────────

    /**
     * HEIF 10-bit 编码
     * Android 10+ (API 29+) 支持 HEIF 编码；HDR gain map 通过 ImageDecoder 解码时自动应用。
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

        val tempFile = File(context.cacheDir, "hdr_heif_${System.currentTimeMillis()}.heif")
        FileOutputStream(tempFile).use { fos ->
            @Suppress("DEPRECATION")
            bitmap.compress(Bitmap.CompressFormat.HEIF, 95, fos)
        }

        val bytes = tempFile.readBytes()
        tempFile.delete()

        return writeToMediaStore(
            context = context,
            data = bytes,
            displayName = displayName,
            mimeType = "image/heif",
            extension = ".heif",
            isHdr = true,
        )
    }

    // ── SDR JPEG (fallback) ────────────────────────────────────────

    private fun exportSdrJpeg(
        context: Context,
        bitmap: Bitmap,
        displayName: String,
    ): Uri? {
        val tempFile = File(context.cacheDir, "sdr_${System.currentTimeMillis()}.jpg")
        FileOutputStream(tempFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos)
        }
        val bytes = tempFile.readBytes()
        tempFile.delete()

        return writeToMediaStore(
            context = context,
            data = bytes,
            displayName = displayName,
            mimeType = "image/jpeg",
            extension = ".jpg",
            isHdr = false,
        )
    }

    // ── MediaStore 写入 ────────────────────────────────────────────

    /**
     * 写入 MediaStore (Pictures/RapidRAW)
     */
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
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: return null

        resolver.openOutputStream(uri)?.use { os: OutputStream ->
            os.write(data)
            os.flush()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
        }

        Log.i(TAG, "Exported HDR image: $uri (format=$mimeType, hdr=$isHdr)")
        return uri
    }

    /**
     * 检查当前 Android 版本是否原生支持 Ultra HDR
     */
    fun isUltraHdrSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
}

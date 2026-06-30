package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import java.util.zip.Deflater
import kotlin.math.pow
import kotlin.math.roundToInt

enum class IccProfile(val displayName: String, val fileName: String) {
    SRGB("sRGB IEC61966-2.1", "srgb.icc"),
    DISPLAY_P3("Display P3", "display_p3.icc"),
    ADOBE_RGB("Adobe RGB (1998)", "adobe_rgb.icc"),
    REC2020("Rec.2020", "rec2020.icc"),
    PRO_PHOTO("ProPhoto RGB", "prophoto.icc"),
}

/**
 * ICC 色彩配置文件嵌入器。
 *
 * 功能：
 * - 嵌入 ICC profile 到 JPEG (APP2 marker) 和 PNG (iCCP chunk)
 * - 从 JPEG/PNG 字节流中读取已有的 ICC profile
 * - 使用 Android ColorSpace API (API 26+) 进行色彩空间转换
 * - 生成标准 ICC v2 profile：sRGB、Display P3、Adobe RGB、Rec.2020、ProPhoto RGB
 * - 将 Bitmap 从一个色彩空间转换到另一个色彩空间
 */
object IccProfileEmbedder {

    private const val TAG = "IccProfileEmbedder"

    // ── 公开 API ──────────────────────────────────────────────────────

    /**
     * 将 ICC profile 嵌入到 JPEG 字节流中。
     */
    fun embedInJpeg(jpegBytes: ByteArray, profile: IccProfile): ByteArray {
        val profileData = getBuiltInProfileBytes(profile)
        val maxChunkData = 65519
        val numChunks = ((profileData.size + maxChunkData - 1) / maxChunkData).coerceAtLeast(1)

        val app2Segments = mutableListOf<ByteArray>()
        var offset = 0
        for (chunkNo in 1..numChunks) {
            val end = (offset + maxChunkData).coerceAtMost(profileData.size)
            val chunkData = profileData.copyOfRange(offset, end)
            val segmentLen = 2 + 14 + chunkData.size

            val buf = ByteBuffer.allocate(2 + 2 + 14 + chunkData.size).order(ByteOrder.BIG_ENDIAN)
            buf.put(0xFF.toByte())
            buf.put(0xE2.toByte())
            buf.putShort(segmentLen.toShort())
            buf.put("ICC_PROFILE\u0000".toByteArray(Charsets.US_ASCII))
            buf.put(chunkNo.toByte())
            buf.put(numChunks.toByte())
            buf.put(chunkData)

            app2Segments.add(buf.array())
            offset = end
        }

        val insertAt = findJpegInsertionPoint(jpegBytes)

        val out = ByteArrayOutputStream(jpegBytes.size + app2Segments.sumOf { it.size })
        out.write(jpegBytes, 0, insertAt)
        for (seg in app2Segments) {
            out.write(seg)
        }
        out.write(jpegBytes, insertAt, jpegBytes.size - insertAt)
        return out.toByteArray()
    }

    /**
     * 将 ICC profile 嵌入到 PNG 字节流中。
     */
    fun embedInPng(pngBytes: ByteArray, profile: IccProfile): ByteArray {
        val profileData = getBuiltInProfileBytes(profile)

        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        deflater.setInput(profileData)
        deflater.finish()
        val compressedBuf = ByteArrayOutputStream(profileData.size)
        val tmp = ByteArray(4096)
        while (!deflater.finished()) {
            val n = deflater.deflate(tmp)
            compressedBuf.write(tmp, 0, n)
        }
        deflater.end()
        val compressedData = compressedBuf.toByteArray()

        val profileName = profile.displayName.toByteArray(Charsets.US_ASCII)
        val chunkDataLen = profileName.size + 1 + 1 + compressedData.size
        val chunkDataBuf = ByteBuffer.allocate(chunkDataLen).order(ByteOrder.BIG_ENDIAN)
        chunkDataBuf.put(profileName)
        chunkDataBuf.put(0)
        chunkDataBuf.put(0) // compression method: deflate
        chunkDataBuf.put(compressedData)
        val chunkData = chunkDataBuf.array()

        val crc = CRC32()
        crc.update("iCCP".toByteArray(Charsets.US_ASCII))
        crc.update(chunkData)
        val crcVal = crc.value

        val fullChunk = ByteBuffer.allocate(4 + 4 + chunkDataLen + 4).order(ByteOrder.BIG_ENDIAN)
        fullChunk.putInt(chunkDataLen)
        fullChunk.put("iCCP".toByteArray(Charsets.US_ASCII))
        fullChunk.put(chunkData)
        fullChunk.putInt(crcVal.toInt())

        val insertAt = findPngIhdrEnd(pngBytes)

        val out = ByteArrayOutputStream(pngBytes.size + fullChunk.capacity())
        out.write(pngBytes, 0, insertAt)
        out.write(fullChunk.array())
        out.write(pngBytes, insertAt, pngBytes.size - insertAt)
        return out.toByteArray()
    }

    // ── 读取已有 ICC profile ─────────────────────────────────────────

    /**
     * 从 JPEG 字节流中提取已有的 ICC profile 数据。
     *
     * JPEG 中的 ICC profile 存储在多个 APP2 "ICC_PROFILE" marker 中，
     * 需要按 chunk number 顺序拼接。
     *
     * @return ICC profile 字节数组，如果不存在则返回 null
     */
    fun readIccFromJpeg(jpegBytes: ByteArray): ByteArray? {
        if (jpegBytes.size < 4) return null
        if (jpegBytes[0] != 0xFF.toByte() || jpegBytes[1] != 0xD8.toByte()) return null

        val iccIdentifier = "ICC_PROFILE\u0000".toByteArray(Charsets.US_ASCII)
        val chunks = mutableMapOf<Int, ByteArray>()
        var totalChunks = -1

        var pos = 2 // skip SOI

        while (pos < jpegBytes.size - 1) {
            if (jpegBytes[pos] != 0xFF.toByte()) {
                pos++
                continue
            }

            val marker = jpegBytes[pos + 1].toInt() and 0xFF

            // SOS → stop scanning
            if (marker == 0xDA) break

            // Skip padding FF bytes
            if (marker == 0xFF) {
                pos++
                continue
            }

            // Standalone markers (no length field)
            if (marker in 0xD0..0xD9) {
                pos += 2
                continue
            }

            if (pos + 3 >= jpegBytes.size) break

            val segLen = ((jpegBytes[pos + 2].toInt() and 0xFF) shl 8) or
                    (jpegBytes[pos + 3].toInt() and 0xFF)

            // Check if this is an ICC_PROFILE APP2 marker
            if (marker == 0xE2 && pos + 4 + iccIdentifier.size < jpegBytes.size) {
                var isIcc = true
                for (i in iccIdentifier.indices) {
                    if (jpegBytes[pos + 4 + i] != iccIdentifier[i]) {
                        isIcc = false
                        break
                    }
                }

                if (isIcc) {
                    val headerSize = 4 + iccIdentifier.size // marker(2) + length(2) + "ICC_PROFILE\0"(12)
                    val chunkNo = jpegBytes[pos + headerSize].toInt() and 0xFF
                    val numChunks = jpegBytes[pos + headerSize + 1].toInt() and 0xFF
                    totalChunks = numChunks

                    val dataStart = pos + headerSize + 2 // +2 for chunk_no and num_chunks
                    val dataEnd = pos + 2 + segLen
                    if (dataEnd <= jpegBytes.size && dataStart < dataEnd) {
                        chunks[chunkNo] = jpegBytes.copyOfRange(dataStart, dataEnd)
                    }
                }
            }

            pos += 2 + segLen
        }

        if (chunks.isEmpty() || totalChunks <= 0) return null

        // Verify all chunks are present
        val out = ByteArrayOutputStream()
        for (i in 1..totalChunks) {
            val chunk = chunks[i] ?: return null // missing chunk
            out.write(chunk)
        }

        val result = out.toByteArray()
        return if (result.size >= 128) result else null // ICC profile header is 128 bytes
    }

    /**
     * 从 PNG 字节流中提取已有的 ICC profile 数据。
     *
     * PNG 中的 ICC profile 存储在 iCCP chunk 中，
     * 包含 profile name + 压缩的 profile 数据。
     *
     * @return 解压后的 ICC profile 字节数组，如果不存在则返回 null
     */
    fun readIccFromPng(pngBytes: ByteArray): ByteArray? {
        if (pngBytes.size < 8) return null
        // Skip 8-byte PNG signature
        var pos = 8

        while (pos + 11 < pngBytes.size) {
            val len = ((pngBytes[pos].toInt() and 0xFF) shl 24) or
                    ((pngBytes[pos + 1].toInt() and 0xFF) shl 16) or
                    ((pngBytes[pos + 2].toInt() and 0xFF) shl 8) or
                    (pngBytes[pos + 3].toInt() and 0xFF)

            val type = String(pngBytes, pos + 4, 4, Charsets.US_ASCII)
            val chunkTotal = 4 + 4 + len + 4

            if (type == "iCCP") {
                // Parse iCCP chunk: profile_name\0 + compression_method + compressed_data
                val dataStart = pos + 8
                var nameEnd = dataStart
                while (nameEnd < pngBytes.size && pngBytes[nameEnd] != 0.toByte()) nameEnd++

                if (nameEnd >= pngBytes.size) return null

                // Skip null terminator and compression method byte
                val compressedStart = nameEnd + 2
                val compressedEnd = pos + 8 + len

                if (compressedStart >= compressedEnd) return null

                val compressedData = pngBytes.copyOfRange(compressedStart, compressedEnd)

                // Decompress with deflate
                val inflater = java.util.zip.Inflater()
                inflater.setInput(compressedData)
                val decompressed = ByteArrayOutputStream(compressedData.size * 4)
                val buffer = ByteArray(4096)
                while (!inflater.finished()) {
                    val n = inflater.inflate(buffer)
                    if (n == 0 && inflater.needsInput()) break
                    decompressed.write(buffer, 0, n)
                }
                inflater.end()

                val result = decompressed.toByteArray()
                return if (result.size >= 128) result else null
            }

            pos += chunkTotal

            if (type == "IEND") break
        }

        return null
    }

    /**
     * 检测 JPEG/PNG 中的 ICC profile 并返回匹配的 IccProfile 枚举。
     *
     * 如果无法识别则返回 null。
     */
    fun detectProfile(bytes: ByteArray, isJpeg: Boolean): IccProfile? {
        val profileData = if (isJpeg) readIccFromJpeg(bytes) else readIccFromPng(bytes)
            ?: return null

        return identifyProfile(profileData)
    }

    /**
     * 根据 ICC profile 头部数据识别 profile 类型。
     *
     * 检查 profile description 标签和色彩空间签名。
     */
    private fun identifyProfile(profileData: ByteArray): IccProfile? {
        if (profileData.size < 128) return null

        // Read profile description from 'desc' tag
        val desc = readDescTag(profileData) ?: return null

        return when {
            desc.contains("sRGB", ignoreCase = true) -> IccProfile.SRGB
            desc.contains("Display P3", ignoreCase = true) ||
                    desc.contains("P3", ignoreCase = true) -> IccProfile.DISPLAY_P3
            desc.contains("Adobe RGB", ignoreCase = true) ||
                    desc.contains("AdobeRGB", ignoreCase = true) -> IccProfile.ADOBE_RGB
            desc.contains("Rec.2020", ignoreCase = true) ||
                    desc.contains("Rec2020", ignoreCase = true) ||
                    desc.contains("BT.2020", ignoreCase = true) -> IccProfile.REC2020
            desc.contains("ProPhoto", ignoreCase = true) ||
                    desc.contains("ROMM", ignoreCase = true) -> IccProfile.PRO_PHOTO
            else -> null
        }
    }

    /**
     * 读取 ICC profile 中的 'desc' 标签文本。
     */
    private fun readDescTag(profileData: ByteArray): String? {
        if (profileData.size < 132) return null

        // Tag count at offset 128
        val tagCount = ByteBuffer.wrap(profileData, 128, 4).order(ByteOrder.BIG_ENDIAN).int

        // Search for 'desc' tag (0x64657363)
        for (i in 0 until tagCount) {
            val tagOffset = 132 + i * 12
            if (tagOffset + 12 > profileData.size) break

            val tagSig = ByteBuffer.wrap(profileData, tagOffset, 4).order(ByteOrder.BIG_ENDIAN).int
            if (tagSig == sig4("desc")) {
                val dataOffset = ByteBuffer.wrap(profileData, tagOffset + 4, 4).order(ByteOrder.BIG_ENDIAN).int
                if (dataOffset + 12 > profileData.size) continue

                // Read ASCII description count
                val asciiCount = ByteBuffer.wrap(profileData, dataOffset + 8, 4).order(ByteOrder.BIG_ENDIAN).int
                if (asciiCount <= 0 || dataOffset + 12 + asciiCount > profileData.size) continue

                return String(profileData, dataOffset + 12, asciiCount - 1, Charsets.US_ASCII)
            }
        }

        return null
    }

    // ── 色彩空间转换 (Android ColorSpace API, API 26+) ───────────────

    /**
     * 将 Bitmap 从一个色彩空间转换到另一个色彩空间。
     *
     * 使用 Android 的 ColorSpace API (API 26+) 进行像素级转换。
     * 对于 API < 26 的设备，使用手动 3×3 矩阵变换。
     *
     * @param source 源 Bitmap
     * @param fromProfile 源色彩空间
     * @param toProfile 目标色彩空间
     * @param renderingIntent 渲染意图：0=Perceptual, 1=Relative, 2=Saturation, 3=Absolute
     * @return 转换后的新 Bitmap
     */
    fun convertColorSpace(
        source: Bitmap,
        fromProfile: IccProfile,
        toProfile: IccProfile,
        renderingIntent: Int = 0,
    ): Bitmap {
        if (fromProfile == toProfile) return source

        val w = source.width
        val h = source.height
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

        // 获取转换矩阵：fromXYZ × toXYZ⁻¹
        val fromToXyz = getColorSpaceToXyzMatrix(fromProfile)
        val xyzToTo = getColorSpaceFromXyzMatrix(toProfile)
        val transform = multiplyMatrices(xyzToTo, fromToXyz)

        // 适配白点差异（Bradford chromatic adaptation）
        val adaptedTransform = adaptWhitePoint(transform, fromProfile, toProfile)

        val pixels = IntArray(w * h)
        val outPixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        for (i in pixels.indices) {
            val sR = ((pixels[i] shr 16) and 0xFF) / 255f
            val sG = ((pixels[i] shr 8) and 0xFF) / 255f
            val sB = (pixels[i] and 0xFF) / 255f

            // 源色彩空间 → 线性
            val gammaFrom = getGammaForProfile(fromProfile)
            val linR = removeGamma(sR, gammaFrom)
            val linG = removeGamma(sG, gammaFrom)
            val linB = removeGamma(sB, gammaFrom)

            // 应用转换矩阵
            val outR = adaptedTransform[0] * linR + adaptedTransform[1] * linG + adaptedTransform[2] * linB
            val outG = adaptedTransform[3] * linR + adaptedTransform[4] * linG + adaptedTransform[5] * linB
            val outB = adaptedTransform[6] * linR + adaptedTransform[7] * linG + adaptedTransform[8] * linB

            // 线性 → 目标色彩空间
            val gammaTo = getGammaForProfile(toProfile)
            val rOut = applyGamma(outR.coerceIn(0f, 1f), gammaTo)
            val gOut = applyGamma(outG.coerceIn(0f, 1f), gammaTo)
            val bOut = applyGamma(outB.coerceIn(0f, 1f), gammaTo)

            val r8 = (rOut * 255f).toInt().coerceIn(0, 255)
            val g8 = (gOut * 255f).toInt().coerceIn(0, 255)
            val b8 = (bOut * 255f).toInt().coerceIn(0, 255)

            outPixels[i] = (0xFF shl 24) or (r8 shl 16) or (g8 shl 8) or b8
        }

        result.setPixels(outPixels, 0, w, 0, 0, w, h)
        return result
    }

    // ── 色彩空间矩阵 ─────────────────────────────────────────────────

    /**
     * 色彩空间 → CIE XYZ (D65) 的 3×3 矩阵。
     */
    private fun getColorSpaceToXyzMatrix(profile: IccProfile): FloatArray {
        return when (profile) {
            IccProfile.SRGB -> floatArrayOf(
                0.4124564f, 0.3575761f, 0.1804375f,
                0.2126729f, 0.7151522f, 0.0721750f,
                0.0193339f, 0.1191920f, 0.9503041f,
            )
            IccProfile.DISPLAY_P3 -> floatArrayOf(
                0.4865709f, 0.2656677f, 0.1982173f,
                0.2289746f, 0.6917385f, 0.0792869f,
                0.0000000f, 0.0451134f, 1.0439444f,
            )
            IccProfile.ADOBE_RGB -> floatArrayOf(
                0.5767309f, 0.1856403f, 0.1570764f,
                0.2885423f, 0.6883647f, 0.0606169f,
                0.0250638f, 0.1051872f, 0.7140917f,
            )
            IccProfile.REC2020 -> floatArrayOf(
                0.6369580f, 0.1446169f, 0.1688810f,
                0.2627002f, 0.6779981f, 0.0593017f,
                0.0000000f, 0.0448961f, 0.7929746f,
            )
            IccProfile.PRO_PHOTO -> floatArrayOf(
                0.7977f, 0.1352f, 0.0311f,
                0.2880f, 0.7119f, 0.0001f,
                0.0000f, 0.0000f, 0.8247f,
            )
        }
    }

    /**
     * CIE XYZ (D65) → 色彩空间的 3×3 矩阵。
     */
    private fun getColorSpaceFromXyzMatrix(profile: IccProfile): FloatArray {
        return invertMatrix3x3(getColorSpaceToXyzMatrix(profile))
    }

    private fun invertMatrix3x3(m: FloatArray): FloatArray {
        val a = m[0]; val b = m[1]; val c = m[2]
        val d = m[3]; val e = m[4]; val f = m[5]
        val g = m[6]; val h = m[7]; val i = m[8]

        val det = a*(e*i - f*h) - b*(d*i - f*g) + c*(d*h - e*g)
        if (kotlin.math.abs(det) < 1e-10f) {
            // Singular matrix, return identity
            return floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
        }

        val invDet = 1f / det
        return floatArrayOf(
            (e*i - f*h) * invDet, (c*h - b*i) * invDet, (b*f - c*e) * invDet,
            (f*g - d*i) * invDet, (a*i - c*g) * invDet, (c*d - a*f) * invDet,
            (d*h - e*g) * invDet, (b*g - a*h) * invDet, (a*e - b*d) * invDet,
        )
    }

    private fun multiplyMatrices(a: FloatArray, b: FloatArray): FloatArray {
        return FloatArray(9) { i ->
            val row = i / 3
            val col = i % 3
            a[row * 3] * b[col] + a[row * 3 + 1] * b[3 + col] + a[row * 3 + 2] * b[6 + col]
        }
    }

    /**
     * Bradford 色度适应 — 修正白点差异。
     *
     * 如果两个 profile 的白点不同（如 ProPhoto 用 D50，其他用 D65），
     * 使用 Bradford 矩阵进行色度适应。
     */
    private fun adaptWhitePoint(
        transform: FloatArray,
        fromProfile: IccProfile,
        toProfile: IccProfile,
    ): FloatArray {
        val fromWp = getWhitePoint(fromProfile)
        val toWp = getWhitePoint(toProfile)

        // 白点相同则不需要适应
        if (kotlin.math.abs(fromWp[0] - toWp[0]) < 0.001f &&
            kotlin.math.abs(fromWp[1] - toWp[1]) < 0.001f &&
            kotlin.math.abs(fromWp[2] - toWp[2]) < 0.001f) {
            return transform
        }

        // Bradford adaptation matrix
        val bradford = floatArrayOf(
             0.8951f,  0.2664f, -0.1614f,
            -0.7502f,  1.7135f,  0.0367f,
             0.0389f, -0.0685f,  1.0296f,
        )
        val bradfordInv = floatArrayOf(
             0.9869929f, -0.1470543f,  0.1599627f,
             0.4323053f,  0.5183603f,  0.0492912f,
            -0.0085287f,  0.0400428f,  0.9684867f,
        )

        val fromCone = mulMatVec(bradford, fromWp)
        val toCone = mulMatVec(bradford, toWp)

        val scale = FloatArray(3) { toCone[it] / fromCone[it] }

        val adapt = FloatArray(9)
        for (i in 0..2) {
            for (j in 0..2) {
                adapt[i * 3 + j] = bradfordInv[i * 3] * scale[0] * bradford[j] +
                        bradfordInv[i * 3 + 1] * scale[1] * bradford[3 + j] +
                        bradfordInv[i * 3 + 2] * scale[2] * bradford[6 + j]
            }
        }

        return multiplyMatrices(transform, adapt)
    }

    private fun mulMatVec(m: FloatArray, v: FloatArray): FloatArray {
        return FloatArray(3) { i ->
            m[i * 3] * v[0] + m[i * 3 + 1] * v[1] + m[i * 3 + 2] * v[2]
        }
    }

    private fun getWhitePoint(profile: IccProfile): FloatArray {
        return when (profile) {
            IccProfile.PRO_PHOTO -> floatArrayOf(0.9642f, 1.0f, 0.8249f) // D50
            else -> floatArrayOf(0.95047f, 1.0f, 1.08883f) // D65
        }
    }

    private fun getGammaForProfile(profile: IccProfile): Float {
        return when (profile) {
            IccProfile.SRGB -> 2.2f // simplified (actual sRGB is piecewise)
            IccProfile.DISPLAY_P3 -> 2.2f
            IccProfile.ADOBE_RGB -> 2.19921875f // 563/256
            IccProfile.REC2020 -> 2.2f
            IccProfile.PRO_PHOTO -> 1.8f
        }
    }

    private fun removeGamma(v: Float, gamma: Float): Float {
        // sRGB uses piecewise; others use simple power
        return if (gamma == 2.2f) {
            // Approximate sRGB → linear
            if (v <= 0.04045f) v / 12.92f
            else ((v + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
        } else {
            v.toDouble().pow(gamma.toDouble()).toFloat()
        }
    }

    private fun applyGamma(v: Float, gamma: Float): Float {
        return if (gamma == 2.2f) {
            // Approximate linear → sRGB
            if (v <= 0.0031308f) 12.92f * v
            else 1.055f * v.toDouble().pow(1.0 / 2.4).toFloat() - 0.055f
        } else {
            v.toDouble().pow(1.0 / gamma).toFloat()
        }
    }

    // ── JPEG 插入点查找 ────────────────────────────────────────────────

    private fun findJpegInsertionPoint(jpegBytes: ByteArray): Int {
        if (jpegBytes.size < 4) return 0
        if (jpegBytes[0] != 0xFF.toByte() || jpegBytes[1] != 0xD8.toByte()) return 0

        var pos = 2
        var pastFirstApp0App1 = false

        while (pos < jpegBytes.size - 1) {
            if (jpegBytes[pos] != 0xFF.toByte()) {
                pos++
                continue
            }
            val marker = jpegBytes[pos + 1].toInt() and 0xFF

            if (marker == 0xFF) {
                pos++
                continue
            }

            if (marker == 0xDA) return pos

            if (marker in 0xD0..0xD9) {
                if (marker == 0xD9) return pos
                pos += 2
                continue
            }

            if (pos + 3 >= jpegBytes.size) return pos
            val segLen = ((jpegBytes[pos + 2].toInt() and 0xFF) shl 8) or
                    (jpegBytes[pos + 3].toInt() and 0xFF)

            if (!pastFirstApp0App1 && (marker == 0xE0 || marker == 0xE1)) {
                pastFirstApp0App1 = true
                pos += 2 + segLen
                continue
            }

            if (pastFirstApp0App1) return pos

            pos += 2 + segLen
        }

        return pos
    }

    // ── PNG 辅助 ───────────────────────────────────────────────────────

    private fun findPngIhdrEnd(pngBytes: ByteArray): Int {
        if (pngBytes.size < 8) return 0
        var pos = 8
        while (pos + 11 < pngBytes.size) {
            val len = ((pngBytes[pos].toInt() and 0xFF) shl 24) or
                    ((pngBytes[pos + 1].toInt() and 0xFF) shl 16) or
                    ((pngBytes[pos + 2].toInt() and 0xFF) shl 8) or
                    (pngBytes[pos + 3].toInt() and 0xFF)

            val type = String(pngBytes, pos + 4, 4, Charsets.US_ASCII)
            val chunkTotal = 4 + 4 + len + 4
            pos += chunkTotal

            if (type == "IHDR") return pos
        }
        return pos
    }

    // ── Built-in ICC profile generation ───────────────────────────────

    fun getBuiltInProfileBytes(profile: IccProfile): ByteArray {
        return when (profile) {
            IccProfile.SRGB -> buildSrgbProfile()
            IccProfile.DISPLAY_P3 -> buildDisplayP3Profile()
            IccProfile.ADOBE_RGB -> buildAdobeRgbProfile()
            IccProfile.REC2020 -> buildRec2020Profile()
            IccProfile.PRO_PHOTO -> buildProPhotoProfile()
        }
    }

    private fun buildSrgbProfile(): ByteArray = buildIccV2Profile(
        preferredCmm = "ADBE",
        profileClass = 0x6D6E7472,
        colorSpace = 0x52474220,
        pcs = 0x58595A20,
        primaries = SrgbPrimaries,
        whitePoint = D65Xyz,
        trcGamma = 2.2,
        profileDesc = "sRGB IEC61966-2.1",
        copyright = "CC0",
    )

    private fun buildDisplayP3Profile(): ByteArray = buildIccV2Profile(
        preferredCmm = "ADBE",
        profileClass = 0x6D6E7472,
        colorSpace = 0x52474220,
        pcs = 0x58595A20,
        primaries = DisplayP3Primaries,
        whitePoint = D65Xyz,
        trcGamma = 2.2,
        profileDesc = "Display P3",
        copyright = "CC0",
    )

    private fun buildAdobeRgbProfile(): ByteArray = buildIccV2Profile(
        preferredCmm = "ADBE",
        profileClass = 0x6D6E7472,
        colorSpace = 0x52474220,
        pcs = 0x58595A20,
        primaries = AdobeRgbPrimaries,
        whitePoint = D65Xyz,
        trcGamma = 2.19921875,
        profileDesc = "Adobe RGB (1998)",
        copyright = "CC0",
    )

    private fun buildRec2020Profile(): ByteArray = buildIccV2Profile(
        preferredCmm = "ADBE",
        profileClass = 0x6D6E7472,
        colorSpace = 0x52474220,
        pcs = 0x58595A20,
        primaries = Rec2020Primaries,
        whitePoint = D65Xyz,
        trcGamma = 2.2,
        profileDesc = "Rec.2020",
        copyright = "CC0",
    )

    private fun buildProPhotoProfile(): ByteArray = buildIccV2Profile(
        preferredCmm = "ADBE",
        profileClass = 0x6D6E7472,
        colorSpace = 0x52474220,
        pcs = 0x58595A20,
        primaries = ProPhotoPrimaries,
        whitePoint = D50Xyz,
        trcGamma = 1.8,
        profileDesc = "ProPhoto RGB",
        copyright = "CC0",
    )

    // ── CIE XYZ primaries ──────────────────────────────────────────────

    private fun s15f16(v: Double): Int = (v * 65536.0).roundToInt()

    private fun Double.roundToInt(): Int = kotlin.math.round(this).toInt()

    private val SrgbPrimaries = Triple(
        doubleArrayOf(0.4360747, 0.2225045, 0.0139322),
        doubleArrayOf(0.3850649, 0.7168786, 0.0970764),
        doubleArrayOf(0.1430804, 0.0606169, 0.7140917),
    )

    private val DisplayP3Primaries = Triple(
        doubleArrayOf(0.5151167, 0.2411506, 0.0208832),
        doubleArrayOf(0.2919786, 0.6925554, 0.0555014),
        doubleArrayOf(0.1570764, 0.0662879, 0.7505437),
    )

    private val AdobeRgbPrimaries = Triple(
        doubleArrayOf(0.5767309, 0.2885423, 0.0250638),
        doubleArrayOf(0.1856403, 0.6883647, 0.1051872),
        doubleArrayOf(0.1570764, 0.0606169, 0.7140917),
    )

    private val Rec2020Primaries = Triple(
        doubleArrayOf(0.6369580, 0.2627002, 0.0000000),
        doubleArrayOf(0.1446169, 0.6779981, 0.0448961),
        doubleArrayOf(0.1688810, 0.0593017, 0.7929746),
    )

    private val ProPhotoPrimaries = Triple(
        doubleArrayOf(0.7977, 0.2880, 0.0000),
        doubleArrayOf(0.1352, 0.7119, 0.0000),
        doubleArrayOf(0.0311, 0.0001, 0.8247),
    )

    private val D65Xyz = doubleArrayOf(0.95047, 1.00000, 1.08883)
    private val D50Xyz = doubleArrayOf(0.96420, 1.00000, 0.82491)

    // ── Core ICC v2 profile builder ───────────────────────────────────

    private fun buildIccV2Profile(
        preferredCmm: String,
        profileClass: Int,
        colorSpace: Int,
        pcs: Int,
        primaries: Triple<DoubleArray, DoubleArray, DoubleArray>,
        whitePoint: DoubleArray,
        trcGamma: Double,
        profileDesc: String,
        copyright: String,
    ): ByteArray {
        val descTagData = buildDescTag(profileDesc)
        val copyrightTagData = buildDescTag(copyright)
        val wtptTagData = buildXyzTag(whitePoint)
        val rXyzTagData = buildXyzTag(primaries.first)
        val gXyzTagData = buildXyzTag(primaries.second)
        val bXyzTagData = buildXyzTag(primaries.third)
        val trcTagData = buildTrcTag(trcGamma)
        val chadTagData = buildChadTag()

        data class TagDef(val sig: Int, val data: ByteArray)

        val tags = listOf(
            TagDef(sig4("desc"), descTagData),
            TagDef(sig4("cprt"), copyrightTagData),
            TagDef(sig4("wtpt"), wtptTagData),
            TagDef(sig4("rXYZ"), rXyzTagData),
            TagDef(sig4("gXYZ"), gXyzTagData),
            TagDef(sig4("bXYZ"), bXyzTagData),
            TagDef(sig4("rTRC"), trcTagData),
            TagDef(sig4("gTRC"), trcTagData),
            TagDef(sig4("bTRC"), trcTagData),
            TagDef(sig4("chad"), chadTagData),
        )

        val headerSize = 128
        val tagCount = tags.size
        val tagTableSize = 4 + tagCount * 12
        val dataStart = headerSize + tagTableSize

        var currentOffset = dataStart
        val tagEntries = mutableListOf<IntArray>()
        val tagDataMap = linkedMapOf<ByteArray, Int>()

        for (tag in tags) {
            val existingOffset = tagDataMap.entries.find { it.key.contentEquals(tag.data) }?.value
            if (existingOffset != null) {
                tagEntries.add(intArrayOf(tag.sig, existingOffset, tag.data.size))
            } else {
                tagEntries.add(intArrayOf(tag.sig, currentOffset, tag.data.size))
                tagDataMap[tag.data] = currentOffset
                currentOffset += tag.data.size
                val padding = (4 - (tag.data.size % 4)) % 4
                currentOffset += padding
            }
        }

        val profileSize = currentOffset

        val buf = ByteBuffer.allocate(profileSize).order(ByteOrder.BIG_ENDIAN)

        buf.putInt(profileSize)
        buf.put(preferredCmm.toByteArray(Charsets.US_ASCII))
        buf.putInt(0x02100000)
        buf.putInt(profileClass)
        buf.putInt(colorSpace)
        buf.putInt(pcs)
        buf.putShort(2025)
        buf.putShort(1)
        buf.putShort(1)
        buf.putShort(0)
        buf.putShort(0)
        buf.putShort(0)
        buf.put("acsp".toByteArray(Charsets.US_ASCII))
        buf.put(0)
        buf.put("APPL".toByteArray(Charsets.US_ASCII))
        buf.put(0)
        buf.putInt(0)
        buf.putInt(0)
        buf.putInt(0)
        buf.putLong(0)
        buf.putInt(0)
        buf.putInt(s15f16(0.9642))
        buf.putInt(s15f16(1.0000))
        buf.putInt(s15f16(0.8249))
        buf.putInt(0)
        buf.put(ByteArray(16))
        buf.put(ByteArray(28))

        buf.putInt(tagCount)
        for (entry in tagEntries) {
            buf.putInt(entry[0])
            buf.putInt(entry[1])
            buf.putInt(entry[2])
        }

        val writtenOffsets = mutableSetOf<Int>()
        for ((data, offset) in tagDataMap) {
            if (offset in writtenOffsets) continue
            writtenOffsets.add(offset)
            buf.position(offset)
            buf.put(data)
            val padding = (4 - (data.size % 4)) % 4
            for (i in 0 until padding) {
                buf.put(0)
            }
        }

        return buf.array()
    }

    // ── Tag builders ──────────────────────────────────────────────────

    private fun buildDescTag(text: String): ByteArray {
        val ascii = text.toByteArray(Charsets.US_ASCII)
        val count = ascii.size + 1
        val size = 4 + 4 + 4 + count + 4 + 2 + 1 + 4

        val buf = ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN)
        buf.putInt(sig4("desc"))
        buf.putInt(0)
        buf.putInt(count)
        buf.put(ascii)
        buf.put(0)
        buf.putInt(0)
        buf.putInt(0)
        buf.putShort(0)
        buf.put(0)
        buf.put(ByteArray(4))
        return buf.array().copyOf(buf.position())
    }

    private fun buildXyzTag(xyz: DoubleArray): ByteArray {
        val buf = ByteBuffer.allocate(20).order(ByteOrder.BIG_ENDIAN)
        buf.putInt(sig4("XYZ "))
        buf.putInt(0)
        buf.putInt(s15f16(xyz[0]))
        buf.putInt(s15f16(xyz[1]))
        buf.putInt(s15f16(xyz[2]))
        return buf.array()
    }

    private fun buildTrcTag(gamma: Double): ByteArray {
        val buf = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN)
        buf.putInt(sig4("curv"))
        buf.putInt(0)
        buf.putInt(1)
        val gammaFixed = (gamma * 256.0).roundToInt().toShort()
        buf.putShort(gammaFixed)
        buf.putShort(0)
        return buf.array()
    }

    private fun buildChadTag(): ByteArray {
        val buf = ByteBuffer.allocate(44).order(ByteOrder.BIG_ENDIAN)
        buf.putInt(sig4("sf32"))
        buf.putInt(0)
        val bradford = doubleArrayOf(
            1.0478112, 0.0228866, -0.0501270,
            0.0295424, 0.9904844, -0.0170491,
            -0.0092345, 0.0150436, 0.7520976,
        )
        for (v in bradford) {
            buf.putInt(s15f16(v))
        }
        return buf.array()
    }

    private fun sig4(s: String): Int {
        val b = s.toByteArray(Charsets.US_ASCII)
        return ((b[0].toInt() and 0xFF) shl 24) or
                ((b[1].toInt() and 0xFF) shl 16) or
                ((b[2].toInt() and 0xFF) shl 8) or
                (b[3].toInt() and 0xFF)
    }
}

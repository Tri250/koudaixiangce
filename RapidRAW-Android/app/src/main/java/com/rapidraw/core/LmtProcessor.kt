package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import android.opengl.GLES30
import android.util.Log
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Look Management Transform (LMT) 处理器
 * 适配自 AlcedoStudio OCIO LMT 系统，支持将 .cube LUT 文件作为创意外观
 * 应用于处理管线，同时提供 CPU 回退和 GPU 加速（OpenGL ES 3.0 3D 纹理）。
 *
 * .cube 解析器支持：
 * - LUT_3D_SIZE / LUT_1D_SIZE 指令
 * - DOMAIN_MIN / DOMAIN_MAX 域范围指令
 * - TITLE 标题指令
 * - 1D Shaper LUT（3D 数据之前的 LUT_1D_SIZE + 数据）
 * - # 注释行
 * - 空白行
 * - 每行三浮点空格分隔的 RGB 数据
 *
 * CPU 应用流程：
 * 1. 对每个像素，将 RGB 归一化到 [0,1] 后映射到 [domainMin, domainMax]
 * 2. 若存在 1D Shaper LUT，先对每个通道做 1D 查找
 * 3. 在 3D LUT 中执行三线性插值查找
 * 4. 将查找结果反映射回 [0,1]
 * 5. 根据 intensity 与原始值混合
 * 6. 按 order 顺序依次应用每个 LMT 条目
 */
class LmtProcessor {

    companion object {
        private const val TAG = "LmtProcessor"
        /** LUT 每维最大允许尺寸 */
        private const val MAX_LUT_SIZE = 65
        /** 最大允许文件大小（字节） */
        private const val MAX_FILE_SIZE = 50L * 1024 * 1024
    }

    // ── 数据结构 ──────────────────────────────────────────────────

    /**
     * LMT 条目：描述一个创意外观
     */
    data class LmtEntry(
        val id: String,
        val name: String,
        val lutFilePath: String,
        val intensity: Float = 1.0f,
        val enabled: Boolean = true,
        val order: Int = 0,
    )

    /**
     * .cube 文件解析结果
     */
    data class CubeLutData(
        val size: Int,
        val domainMin: FloatArray,
        val domainMax: FloatArray,
        val data: FloatArray,
        val title: String = "",
        /** 1D Shaper LUT 数据，null 表示无 1D LUT */
        val shaperLut1D: ShaperLut1D? = null,
    )

    /**
     * 1D Shaper LUT：在 3D 查找前对每个通道做独立映射
     */
    data class ShaperLut1D(
        val size: Int,
        val data: FloatArray,
    ) {
        /**
         * 对单个通道做 1D 线性插值查找
         */
        fun sampleChannel(value: Float, channelOffset: Int): Float {
            val maxIndex = size - 1
            val mapped = value.coerceIn(0f, 1f) * maxIndex
            val i0 = mapped.toInt().coerceIn(0, maxIndex)
            val i1 = (i0 + 1).coerceAtMost(maxIndex)
            val frac = mapped - i0
            val v0 = data[i0 * 3 + channelOffset]
            val v1 = data[i1 * 3 + channelOffset]
            return v0 * (1f - frac) + v1 * frac
        }

        /**
         * 对 RGB 三个通道做 1D 查找
         */
        fun sample(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
            return Triple(
                sampleChannel(r, 0),
                sampleChannel(g, 1),
                sampleChannel(b, 2),
            )
        }
    }

    // ── 缓存 ──────────────────────────────────────────────────────

    /** 已解析的 LUT 缓存：filePath -> CubeLutData */
    private val lutCache = mutableMapOf<String, CubeLutData?>()

    // ── .cube 文件解析 ────────────────────────────────────────────

    /**
     * 解析 .cube LUT 文件，返回 CubeLutData。
     * 结果会被缓存，相同路径不会重复解析。
     *
     * @param filePath .cube 文件的绝对路径
     * @return 解析成功返回 CubeLutData，失败返回 null
     */
    fun parseCubeLut(filePath: String): CubeLutData? {
        lutCache[filePath]?.let { return it }

        val file = File(filePath)
        if (!file.exists()) {
            Log.w(TAG, "LUT file not found: $filePath")
            lutCache[filePath] = null
            return null
        }
        if (file.length() > MAX_FILE_SIZE) {
            Log.w(TAG, "LUT file too large: ${file.length()} bytes (max $MAX_FILE_SIZE)")
            lutCache[filePath] = null
            return null
        }

        val result = file.inputStream().use { parseCubeStream(it) }
        lutCache[filePath] = result
        return result
    }

    /**
     * 从 InputStream 解析 .cube 文件
     */
    private fun parseCubeStream(inputStream: InputStream): CubeLutData? {
        val lines = inputStream.bufferedReader().readLines()

        var title = ""
        var size1D = 0
        var size3D = 0
        var domainMin = floatArrayOf(0f, 0f, 0f)
        var domainMax = floatArrayOf(1f, 1f, 1f)
        val values = mutableListOf<Float>()

        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() || trimmed.startsWith("#") -> {
                    // 跳过空行和注释
                }
                trimmed.startsWith("TITLE", ignoreCase = true) -> {
                    title = extractQuotedString(trimmed.substringAfter("TITLE", "").trim())
                        ?: trimmed.substringAfter("TITLE", "").trim().trim('"')
                }
                trimmed.startsWith("LUT_3D_SIZE", ignoreCase = true) -> {
                    size3D = trimmed.substringAfter(" ", "").trim().toIntOrNull() ?: 0
                }
                trimmed.startsWith("LUT_1D_SIZE", ignoreCase = true) -> {
                    size1D = trimmed.substringAfter(" ", "").trim().toIntOrNull() ?: 0
                }
                trimmed.startsWith("DOMAIN_MIN", ignoreCase = true) -> {
                    val parts = extractFloatTriple(trimmed.substringAfter("DOMAIN_MIN", ""))
                    if (parts != null) domainMin = parts
                }
                trimmed.startsWith("DOMAIN_MAX", ignoreCase = true) -> {
                    val parts = extractFloatTriple(trimmed.substringAfter("DOMAIN_MAX", ""))
                    if (parts != null) domainMax = parts
                }
                else -> {
                    // 数据行：三个空格分隔的浮点数
                    val parts = trimmed.split(Regex("\\s+"))
                    if (parts.size == 3) {
                        for (p in parts) {
                            val v = p.toFloatOrNull()
                            if (v != null) {
                                values.add(v)
                            } else {
                                Log.w(TAG, "Invalid LUT value: '$p'")
                            }
                        }
                    }
                }
            }
        }

        // 解析 1D Shaper LUT（如果存在）
        val shaperLut1D = if (size1D > 0) {
            val expected1D = size1D * 3
            if (size1D < 2 || size1D > MAX_LUT_SIZE) {
                Log.w(TAG, "1D LUT size $size1D out of valid range [2, $MAX_LUT_SIZE]")
                null
            } else if (values.size < expected1D) {
                Log.w(TAG, "1D LUT data mismatch: expected $expected1D values, got ${values.size}")
                null
            } else {
                ShaperLut1D(
                    size = size1D,
                    data = values.subList(0, expected1D).toFloatArray(),
                )
            }
        } else null

        // 3D LUT 数据从 1D 数据之后开始
        val remainingValues = if (shaperLut1D != null) {
            values.subList(size1D * 3, values.size)
        } else {
            values
        }

        // 验证 3D LUT
        if (size3D < 2 || size3D > MAX_LUT_SIZE) {
            Log.w(TAG, "3D LUT size $size3D out of valid range [2, $MAX_LUT_SIZE]")
            return null
        }

        val expected3D = size3D * size3D * size3D * 3
        if (remainingValues.size != expected3D) {
            Log.w(TAG, "3D LUT data mismatch: expected $expected3D values, got ${remainingValues.size}")
            return null
        }

        // 验证 DOMAIN 范围合法性
        for (i in 0..2) {
            if (domainMin[i] >= domainMax[i]) {
                Log.w(TAG, "DOMAIN_MIN[$i] (${domainMin[i]}) >= DOMAIN_MAX[$i] (${domainMax[i]})")
                return null
            }
        }

        return CubeLutData(
            size = size3D,
            domainMin = domainMin,
            domainMax = domainMax,
            data = remainingValues.toFloatArray(),
            title = title,
            shaperLut1D = shaperLut1D,
        )
    }

    // ── CPU 应用：单像素三线性插值 ────────────────────────────────

    /**
     * 对单个 RGB 像素在 3D LUT 中执行三线性插值查找
     *
     * @param r 红色通道 [0,1]
     * @param g 绿色通道 [0,1]
     * @param b 蓝色通道 [0,1]
     * @param lutData LUT 数据
     * @return 查找后的 RGB 值
     */
    private fun trilinearSample(
        r: Float, g: Float, b: Float,
        lutData: CubeLutData,
    ): Triple<Float, Float, Float> {
        val size = lutData.size
        val maxIndex = size - 1
        val data = lutData.data

        // 将 [0,1] 映射到 [domainMin, domainMax] 再归一化回 [0,1] 用于查找
        val domainR = remapToDomain(r, lutData.domainMin[0], lutData.domainMax[0])
        val domainG = remapToDomain(g, lutData.domainMin[1], lutData.domainMax[1])
        val domainB = remapToDomain(b, lutData.domainMin[2], lutData.domainMax[2])

        // 若存在 1D Shaper LUT，先做 1D 查找
        val (sr, sg, sb) = if (lutData.shaperLut1D != null) {
            lutData.shaperLut1D.sample(domainR, domainG, domainB)
        } else {
            Triple(domainR, domainG, domainB)
        }

        // 三线性插值
        val rf = sr.coerceIn(0f, 1f) * maxIndex
        val gf = sg.coerceIn(0f, 1f) * maxIndex
        val bf = sb.coerceIn(0f, 1f) * maxIndex

        val r0 = rf.toInt().coerceIn(0, maxIndex)
        val g0 = gf.toInt().coerceIn(0, maxIndex)
        val b0 = bf.toInt().coerceIn(0, maxIndex)
        val r1 = (r0 + 1).coerceAtMost(maxIndex)
        val g1 = (g0 + 1).coerceAtMost(maxIndex)
        val b1 = (b0 + 1).coerceAtMost(maxIndex)

        val dr = rf - r0
        val dg = gf - g0
        val db = bf - b0

        // 索引计算：R 变化最快，B 变化最慢（CUBE 标准排序）
        fun idx(ri: Int, gi: Int, bi: Int): Int = (bi * size * size + gi * size + ri) * 3

        fun fetch(ri: Int, gi: Int, bi: Int): Triple<Float, Float, Float> {
            val i = idx(ri, gi, bi)
            return Triple(data[i], data[i + 1], data[i + 2])
        }

        val c000 = fetch(r0, g0, b0)
        val c100 = fetch(r1, g0, b0)
        val c010 = fetch(r0, g1, b0)
        val c110 = fetch(r1, g1, b0)
        val c001 = fetch(r0, g0, b1)
        val c101 = fetch(r1, g0, b1)
        val c011 = fetch(r0, g1, b1)
        val c111 = fetch(r1, g1, b1)

        // R 方向插值
        val c00 = Triple(
            c000.first * (1f - dr) + c100.first * dr,
            c000.second * (1f - dr) + c100.second * dr,
            c000.third * (1f - dr) + c100.third * dr,
        )
        val c10 = Triple(
            c010.first * (1f - dr) + c110.first * dr,
            c010.second * (1f - dr) + c110.second * dr,
            c010.third * (1f - dr) + c110.third * dr,
        )
        val c01 = Triple(
            c001.first * (1f - dr) + c101.first * dr,
            c001.second * (1f - dr) + c101.second * dr,
            c001.third * (1f - dr) + c101.third * dr,
        )
        val c11 = Triple(
            c011.first * (1f - dr) + c111.first * dr,
            c011.second * (1f - dr) + c111.second * dr,
            c011.third * (1f - dr) + c111.third * dr,
        )

        // G 方向插值
        val c0 = Triple(
            c00.first * (1f - dg) + c10.first * dg,
            c00.second * (1f - dg) + c10.second * dg,
            c00.third * (1f - dg) + c10.third * dg,
        )
        val c1 = Triple(
            c01.first * (1f - dg) + c11.first * dg,
            c01.second * (1f - dg) + c11.second * dg,
            c01.third * (1f - dg) + c11.third * dg,
        )

        // B 方向插值
        val result = Triple(
            c0.first * (1f - db) + c1.first * db,
            c0.second * (1f - db) + c1.second * db,
            c0.third * (1f - db) + c1.third * db,
        )

        // 将查找结果从 [domainMin, domainMax] 反映射回 [0,1]
        return Triple(
            remapFromDomain(result.first, lutData.domainMin[0], lutData.domainMax[0]),
            remapFromDomain(result.second, lutData.domainMin[1], lutData.domainMax[1]),
            remapFromDomain(result.third, lutData.domainMin[2], lutData.domainMax[2]),
        )
    }

    /**
     * 将 [0,1] 映射到 [domainMin, domainMax] 范围
     */
    private fun remapToDomain(value: Float, domainMin: Float, domainMax: Float): Float {
        return domainMin + value.coerceIn(0f, 1f) * (domainMax - domainMin)
    }

    /**
     * 将 [domainMin, domainMax] 范围映射回 [0,1]
     */
    private fun remapFromDomain(value: Float, domainMin: Float, domainMax: Float): Float {
        return ((value - domainMin) / (domainMax - domainMin)).coerceIn(0f, 1f)
    }

    // ── CPU 应用：LMT 栈 ──────────────────────────────────────────

    /**
     * 将 LMT 栈应用到 Bitmap（CPU 回退路径）
     *
     * 处理流程：
     * 1. 按 order 排序启用的 LmtEntry
     * 2. 对每个条目解析 .cube 文件
     * 3. 逐像素：归一化 → 域映射 → 1D Shaper → 3D 三线性插值 → 反域映射 → 混合
     * 4. 若提供了 baseLut，在 LMT 栈之前先应用基础 LUT
     *
     * @param bitmap 输入图像
     * @param entries LMT 条目列表
     * @param baseLut 基础 LUT（可选，来自 CubeLutParser）
     * @return 应用后的新 Bitmap
     */
    fun applyLmtStack(
        bitmap: Bitmap,
        entries: List<LmtEntry>,
        baseLut: CubeLutParser.Lut3D?,
    ): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        // 按 order 排序，仅保留启用的条目
        val sortedEntries = entries
            .filter { it.enabled }
            .sortedBy { it.order }

        // 解析所有需要的 LUT 文件
        val lutDataList = sortedEntries.map { entry ->
            entry to parseCubeLut(entry.lutFilePath)
        }

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val outPixels = IntArray(width * height)

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val a = (pixel ushr 24) and 0xFF
            var r = ((pixel shr 16) and 0xFF) / 255f
            var g = ((pixel shr 8) and 0xFF) / 255f
            var b = (pixel and 0xFF) / 255f

            // 若有基础 LUT，先应用
            if (baseLut != null) {
                val (br, bg, bb) = baseLut.sample(r, g, b)
                r = br
                g = bg
                b = bb
            }

            // 按 order 依次应用每个 LMT 条目
            for ((entry, lutData) in lutDataList) {
                if (lutData == null) continue

                val (nr, ng, nb) = trilinearSample(r, g, b, lutData)
                val intensity = entry.intensity.coerceIn(0f, 1f)

                // 根据 intensity 混合原始与查找结果
                r = r + (nr - r) * intensity
                g = g + (ng - g) * intensity
                b = b + (nb - b) * intensity
            }

            outPixels[i] = (a shl 24) or
                ((r.coerceIn(0f, 1f) * 255).toInt().coerceIn(0, 255) shl 16) or
                ((g.coerceIn(0f, 1f) * 255).toInt().coerceIn(0, 255) shl 8) or
                ((b.coerceIn(0f, 1f) * 255).toInt().coerceIn(0, 255))
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(outPixels, 0, width, 0, 0, width, height)
        return result
    }

    // ── GPU 加速：3D 纹理创建 ─────────────────────────────────────

    /**
     * 从 CubeLutData 创建 OpenGL ES 3.0 3D 纹理
     *
     * 纹理参数：
     * - GL_TEXTURE_3D 目标
     * - GL_LINEAR 三线性过滤（GPU 硬件插值）
     * - GL_CLAMP_TO_EDGE 边缘寻址
     * - GL_RGB8 内部格式
     *
     * 注意：调用此方法前必须有当前 GL 上下文。
     * 返回的纹理 ID 由调用方负责释放（glDeleteTextures）。
     *
     * @param lutData 已解析的 LUT 数据
     * @return GL 纹理 ID，失败返回 0
     */
    fun createLutTexture(lutData: CubeLutData): Int {
        val size = lutData.size
        val pixelCount = size * size * size * 3

        // 将浮点数据转换为字节数据（8-bit 精度）
        val buffer = ByteBuffer.allocateDirect(pixelCount)
            .order(ByteOrder.nativeOrder())

        // 处理 DOMAIN 范围：将查找结果映射到 [0,1] 后转为字节
        for (i in 0 until (size * size * size)) {
            val rVal = remapFromDomain(lutData.data[i * 3], lutData.domainMin[0], lutData.domainMax[0])
            val gVal = remapFromDomain(lutData.data[i * 3 + 1], lutData.domainMin[1], lutData.domainMax[1])
            val bVal = remapFromDomain(lutData.data[i * 3 + 2], lutData.domainMin[2], lutData.domainMax[2])

            buffer.put((rVal.coerceIn(0f, 1f) * 255f).toInt().toByte())
            buffer.put((gVal.coerceIn(0f, 1f) * 255f).toInt().toByte())
            buffer.put((bVal.coerceIn(0f, 1f) * 255f).toInt().toByte())
        }
        buffer.rewind()

        // 生成并配置 3D 纹理
        val texArr = IntArray(1)
        GLES30.glGenTextures(1, texArr, 0)
        val textureId = texArr[0]
        if (textureId == 0) {
            Log.e(TAG, "Failed to generate GL texture")
            return 0
        }

        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, textureId)

        // 三线性过滤
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)

        // 边缘寻址
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_R, GLES30.GL_CLAMP_TO_EDGE)

        // 上传 3D 纹理数据
        GLES30.glTexImage3D(
            GLES30.GL_TEXTURE_3D, 0, GLES30.GL_RGB8,
            size, size, size, 0,
            GLES30.GL_RGB, GLES30.GL_UNSIGNED_BYTE, buffer,
        )

        // 检查 GL 错误
        val error = GLES30.glGetError()
        if (error != GLES30.GL_NO_ERROR) {
            Log.e(TAG, "glTexImage3D failed with GL error: $error")
            GLES30.glDeleteTextures(1, intArrayOf(textureId), 0)
            return 0
        }

        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, 0)
        return textureId
    }

    /**
     * 从 CubeLutData 创建半精度浮点 3D 纹理（更高精度）
     *
     * 使用 GL_RGBA16F 内部格式，保留浮点精度，
     * 适合 HDR 工作流或需要精确保留超范围值的场景。
     *
     * @param lutData 已解析的 LUT 数据
     * @return GL 纹理 ID，失败返回 0
     */
    fun createLutTextureHalfFloat(lutData: CubeLutData): Int {
        val size = lutData.size
        val total = size * size * size

        // 半精度浮点 RGBA 缓冲区
        val buffer = ByteBuffer.allocateDirect(total * 4 * 2) // 4 channels * 2 bytes each
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()

        for (i in 0 until total) {
            buffer.put(floatToHalf(lutData.data[i * 3]))
            buffer.put(floatToHalf(lutData.data[i * 3 + 1]))
            buffer.put(floatToHalf(lutData.data[i * 3 + 2]))
            buffer.put(0x3C00.toShort()) // 1.0 in half-float (alpha)
        }
        buffer.rewind()

        val texArr = IntArray(1)
        GLES30.glGenTextures(1, texArr, 0)
        val textureId = texArr[0]
        if (textureId == 0) {
            Log.e(TAG, "Failed to generate GL half-float texture")
            return 0
        }

        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, textureId)

        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_R, GLES30.GL_CLAMP_TO_EDGE)

        GLES30.glTexImage3D(
            GLES30.GL_TEXTURE_3D, 0, GLES30.GL_RGBA16F,
            size, size, size, 0,
            GLES30.GL_RGBA, GLES30.GL_HALF_FLOAT, buffer,
        )

        val error = GLES30.glGetError()
        if (error != GLES30.GL_NO_ERROR) {
            Log.e(TAG, "glTexImage3D (half-float) failed with GL error: $error")
            GLES30.glDeleteTextures(1, intArrayOf(textureId), 0)
            return 0
        }

        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, 0)
        return textureId
    }

    // ── 缓存管理 ──────────────────────────────────────────────────

    /**
     * 清除 LUT 解析缓存
     */
    fun clearCache() {
        lutCache.clear()
    }

    /**
     * 从缓存中移除指定路径的 LUT
     */
    fun removeFromCache(filePath: String) {
        lutCache.remove(filePath)
    }

    // ── 内部工具 ──────────────────────────────────────────────────

    private fun extractQuotedString(s: String): String? {
        val start = s.indexOf('"')
        val end = s.lastIndexOf('"')
        return if (start >= 0 && end > start) s.substring(start + 1, end) else null
    }

    private fun extractFloatTriple(s: String): FloatArray? {
        val parts = s.trim().split(Regex("\\s+"))
        if (parts.size < 3) return null
        val r = parts[0].toFloatOrNull() ?: return null
        val g = parts[1].toFloatOrNull() ?: return null
        val b = parts[2].toFloatOrNull() ?: return null
        return floatArrayOf(r, g, b)
    }

    /**
     * Float → Half-float (16-bit) 转换
     */
    private fun floatToHalf(f: Float): Short {
        val bits = java.lang.Float.floatToIntBits(f)
        val sign = (bits ushr 16) and 0x8000
        var value = (bits and 0x7FFFFFFF) ushr 13
        if (value > 0x7C00) value = 0x7C00 // clamp to max
        return (sign or value).toShort()
    }
}

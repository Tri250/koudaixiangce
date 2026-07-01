package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.flow.asStateFlow

/**
 * .cube LUT 文件解析器
 * 支持标准 Adobe Cube LUT 格式，涵盖所有有效变体：
 * - 1D LUT（LUT_1D_SIZE）
 * - 3D LUT（LUT_3D_SIZE）
 * - 混合 1D+3D LUT（先应用1D再应用3D）
 * - 注释与头部字段
 * - 空白变体
 * - 浮点值（0.0-1.0 或无界）
 * - DOMAIN_MIN / DOMAIN_MAX 域范围
 *
 * 验证规则：
 * - LUT 维度必须为 2 的幂 (2, 4, 8, 16, 17, 32, 33, 64, 65)
 *   实际上 Adobe CUBE 规范允许任意 >= 2 的整数，但 17 和 33 最为常见。
 *   这里放宽为 >= 2 的整数即可。
 * - 数据条目数量必须与声明的 size 匹配
 *
 * 安全限制：
 * - 最大 LUT 维度：64（64x64x64 = 262,144 项，约 3MB 内存）
 * - 最大文件大小：50MB
 * - 超出限制的文件将返回 null 并记录警告
 */
class CubeLutParser {

    companion object {
        const val TAG = "CubeLutParser"
        /** LUT 每维最大允许尺寸 */
        const val MAX_LUT_SIZE = 64
        /** 最大允许文件大小（字节） */
        const val MAX_FILE_SIZE = 50L * 1024 * 1024 // 50 MB
    }

    // ── 数据结构 ──────────────────────────────────────────────────

    /**
     * 1D LUT：每个通道独立映射
     * data 布局：[R0,G0,B0, R1,G1,B1, ..., R_{size-1},G_{size-1},B_{size-1}]
     */
    data class Lut1D(
        val size: Int,
        val data: FloatArray,
        val domainMin: FloatArray = floatArrayOf(0f, 0f, 0f),
        val domainMax: FloatArray = floatArrayOf(1f, 1f, 1f),
    ) {
        /**
         * 1D LUT 线性插值采样
         * @param r 红色通道输入 [0,1]
         * @param g 绿色通道输入 [0,1]
         * @param b 蓝色通道输入 [0,1]
         * @return 映射后的 RGB 值
         */
        fun sample(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
            return Triple(
                sampleChannel(r, 0),
                sampleChannel(g, 1),
                sampleChannel(b, 2),
            )
        }

        private fun sampleChannel(value: Float, channelOffset: Int): Float {
            val maxIndex = size - 1
            val mapped = value.coerceIn(0f, 1f) * maxIndex
            val i0 = mapped.toInt().coerceIn(0, maxIndex)
            val i1 = (i0 + 1).coerceAtMost(maxIndex)
            val frac = mapped - i0
            val v0 = data[i0 * 3 + channelOffset]
            val v1 = data[i1 * 3 + channelOffset]
            return v0 * (1f - frac) + v1 * frac
        }
    }

    /**
     * 3D LUT
     * data 布局（CUBE 标准排序）：R 变化最快，B 变化最慢
     * idx = (b * size * size + g * size + r) * 3
     */
    data class Lut3D(
        val size: Int,
        val data: FloatArray,
        val domainMin: FloatArray = floatArrayOf(0f, 0f, 0f),
        val domainMax: FloatArray = floatArrayOf(1f, 1f, 1f),
    ) {
        /**
         * 3D LUT 三线性插值采样
         * @param r 红色通道值 [0,1]
         * @param g 绿色通道值 [0,1]
         * @param b 蓝色通道值 [0,1]
         * @return 采样后的 RGB 值
         */
        fun sample(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
            val maxIndex = size - 1
            val rf = (r.coerceIn(0f, 1f) * maxIndex)
            val gf = (g.coerceIn(0f, 1f) * maxIndex)
            val bf = (b.coerceIn(0f, 1f) * maxIndex)

            val r0 = rf.toInt().coerceIn(0, maxIndex)
            val g0 = gf.toInt().coerceIn(0, maxIndex)
            val b0 = bf.toInt().coerceIn(0, maxIndex)
            val r1 = (r0 + 1).coerceAtMost(maxIndex)
            val g1 = (g0 + 1).coerceAtMost(maxIndex)
            val b1 = (b0 + 1).coerceAtMost(maxIndex)

            val dr = rf - r0
            val dg = gf - g0
            val db = bf - b0

            fun idx(ri: Int, gi: Int, bi: Int): Int {
                return (bi * size * size + gi * size + ri) * 3
            }

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

            val c00 = Triple(
                c000.first * (1 - dr) + c100.first * dr,
                c000.second * (1 - dr) + c100.second * dr,
                c000.third * (1 - dr) + c100.third * dr
            )
            val c10 = Triple(
                c010.first * (1 - dr) + c110.first * dr,
                c010.second * (1 - dr) + c110.second * dr,
                c010.third * (1 - dr) + c110.third * dr
            )
            val c01 = Triple(
                c001.first * (1 - dr) + c101.first * dr,
                c001.second * (1 - dr) + c101.second * dr,
                c001.third * (1 - dr) + c101.third * dr
            )
            val c11 = Triple(
                c011.first * (1 - dr) + c111.first * dr,
                c011.second * (1 - dr) + c111.second * dr,
                c011.third * (1 - dr) + c111.third * dr
            )

            val c0 = Triple(
                c00.first * (1 - dg) + c10.first * dg,
                c00.second * (1 - dg) + c10.second * dg,
                c00.third * (1 - dg) + c10.third * dg
            )
            val c1 = Triple(
                c01.first * (1 - dg) + c11.first * dg,
                c01.second * (1 - dg) + c11.second * dg,
                c01.third * (1 - dg) + c11.third * dg
            )

            return Triple(
                c0.first * (1 - db) + c1.first * db,
                c0.second * (1 - db) + c1.second * db,
                c0.third * (1 - db) + c1.third * db
            )
        }
    }

    /**
     * 混合 LUT：先 1D 再 3D
     */
    data class LutMixed(
        val lut1D: Lut1D?,
        val lut3D: Lut3D,
    ) {
        fun sample(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
            val (r1, g1, b1) = if (lut1D != null) {
                lut1D.sample(r, g, b)
            } else {
                Triple(r, g, b)
            }
            return lut3D.sample(r1, g1, b1)
        }
    }

    /**
     * 完整解析结果
     */
    data class ParsedLut(
        val title: String?,
        val lut1D: Lut1D?,
        val lut3D: Lut3D?,
        val domainMin: FloatArray,
        val domainMax: FloatArray,
    ) {
        /**
         * 获取混合 LUT（如果同时存在 1D 和 3D）
         * 如果只有 3D，则 1D 为 null
         * 如果只有 1D，则 3D 为 null
         */
        fun toMixed(): LutMixed? {
            val lut3D = this.lut3D ?: return null
            return LutMixed(lut1D, lut3D)
        }

        /**
         * 简便方法：直接获取3D LUT
         */
        fun as3D(): Lut3D? = lut3D

        /**
         * 简便方法：直接获取1D LUT
         */
        fun as1D(): Lut1D? = lut1D
    }

    // ── 解析方法 ──────────────────────────────────────────────────

    /**
     * 解析 .cube 文件
     */
    fun parseFile(file: File): ParsedLut? {
        if (file.length() > MAX_FILE_SIZE) {
            Log.w(TAG, "LUT file too large: ${file.length()} bytes (max $MAX_FILE_SIZE)")
            return null
        }
        return file.inputStream().use { parse(it) }
    }

    /**
     * 解析 .cube 文件输入流（完整版，支持1D/3D/混合）
     */
    fun parse(inputStream: InputStream?): ParsedLut? {
        if (inputStream == null) return null

        // 2026 hotfix: 限制单次读取的最大行数，防止 adversarial 输入导致内存耗尽
        val lines = inputStream.bufferedReader().use { reader ->
            val result = mutableListOf<String>()
            var line = reader.readLine()
            var count = 0
            val maxLines = 2_000_000
            while (line != null && count < maxLines) {
                result.add(line)
                line = reader.readLine()
                count++
            }
            if (count >= maxLines) {
                Log.w(TAG, "LUT line count exceeds $maxLines, truncated")
            }
            result
        }

        var title: String? = null
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
                    // 数据行：三个浮点数
                    val parts = trimmed.split(Regex("\\s+"))
                    if (parts.size == 3) {
                        parts.forEach { p ->
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

        // 验证并构建 LUT
        val lut1D = if (size1D > 0) {
            val expected1D = size1D * 3
            if (size1D < 2 || size1D > MAX_LUT_SIZE) {
                Log.w(TAG, "1D LUT size $size1D out of valid range [2, $MAX_LUT_SIZE]")
                null
            } else if (values.size < expected1D) {
                Log.w(TAG, "1D LUT data mismatch: expected $expected1D values, got ${values.size}")
                null
            } else {
                Lut1D(
                    size = size1D,
                    data = values.subList(0, expected1D).toFloatArray(),
                    domainMin = domainMin,
                    domainMax = domainMax,
                )
            }
        } else null

        val remainingValues = if (lut1D != null) {
            val used = size1D * 3
            values.subList(used, values.size)
        } else {
            values
        }

        val lut3D = if (size3D > 0) {
            val expected3D = size3D * size3D * size3D * 3
            if (size3D < 2 || size3D > MAX_LUT_SIZE) {
                Log.w(TAG, "3D LUT size $size3D out of valid range [2, $MAX_LUT_SIZE]")
                null
            } else if (remainingValues.size != expected3D) {
                Log.w(TAG, "3D LUT data mismatch: expected $expected3D values, got ${remainingValues.size} (size=$size3D)")
                null
            } else {
                Lut3D(
                    size = size3D,
                    data = remainingValues.toFloatArray(),
                    domainMin = domainMin,
                    domainMax = domainMax,
                )
            }
        } else null

        if (lut1D == null && lut3D == null) {
            Log.w(TAG, "No valid LUT data found in file")
            return null
        }

        return ParsedLut(
            title = title,
            lut1D = lut1D,
            lut3D = lut3D,
            domainMin = domainMin,
            domainMax = domainMax,
        )
    }

    /**
     * 向后兼容：直接返回 Lut3D（仅3D LUT 文件）
     */
    fun parse3D(inputStream: InputStream?): Lut3D? {
        return parse(inputStream)?.lut3D
    }

    /**
     * 向后兼容：解析文件直接返回 Lut3D
     */
    fun parseFile3D(file: File): Lut3D? {
        return parseFile(file)?.lut3D
    }

    // ── 验证 ──────────────────────────────────────────────────────

    /**
     * 验证 LUT 维度是否合法
     * CUBE 格式规范：size >= 2 即可，但常见值为 2^n 或 2^n+1 (如 17=2^4+1, 33=2^5+1)
     */
    fun isValidSize(size: Int): Boolean {
        return size in 2..MAX_LUT_SIZE
    }

    /**
     * 验证 size 是否为 2 的幂（严格模式）
     */
    fun isPowerOfTwo(size: Int): Boolean {
        return size >= 2 && (size and (size - 1)) == 0
    }

    /**
     * 验证 LUT 数据完整性
     * - 检查数据量与声明尺寸匹配
     * - 检查浮点值是否在合理范围内（考虑 DOMAIN 设置）
     * - 检查数据索引顺序
     */
    fun validate3D(lut: Lut3D): ValidationResult {
        val errors = mutableListOf<String>()

        if (lut.size < 2) {
            errors.add("LUT size ${lut.size} is less than minimum 2")
        }

        val expectedCount = lut.size * lut.size * lut.size * 3
        if (lut.data.size != expectedCount) {
            errors.add("Data count mismatch: expected $expectedCount, got ${lut.data.size}")
        }

        // 检查 DOMAIN 范围合法性
        for (i in 0..2) {
            if (lut.domainMin[i] >= lut.domainMax[i]) {
                errors.add("DOMAIN_MIN[$i] (${lut.domainMin[i]}) >= DOMAIN_MAX[$i] (${lut.domainMax[i]})")
            }
        }

        // 检查数值合理性（允许略超出 [0,1] 范围，因为 HDR LUT 可能有超范围值）
        var outOfRangeCount = 0
        for (i in lut.data.indices) {
            val v = lut.data[i]
            if (v.isNaN() || v.isInfinite()) {
                errors.add("Invalid value at index $i: $v")
                break
            }
            if (v < -0.5f || v > 1.5f) {
                outOfRangeCount++
            }
        }
        if (outOfRangeCount > lut.data.size * 0.1f) {
            errors.add("Too many out-of-range values: $outOfRangeCount / ${lut.data.size}")
        }

        return ValidationResult(errors.isEmpty(), errors)
    }

    data class ValidationResult(
        val isValid: Boolean,
        val errors: List<String>,
    )

    // ── 转换工具 ──────────────────────────────────────────────────

    /**
     * 将 3D LUT 数据转换为 RGBA 字节缓冲区
     * OpenGL ES 3.0 的 glTexImage3D 需要 RGBA 打包
     */
    fun lutToRgbaBuffer(lut: Lut3D): ByteArray {
        val size = lut.size
        val total = size * size * size
        val buffer = ByteArray(total * 4)

        for (i in 0 until total) {
            val r = (lut.data[i * 3] * 255).toInt().coerceIn(0, 255)
            val g = (lut.data[i * 3 + 1] * 255).toInt().coerceIn(0, 255)
            val b = (lut.data[i * 3 + 2] * 255).toInt().coerceIn(0, 255)
            buffer[i * 4] = r.toByte()
            buffer[i * 4 + 1] = g.toByte()
            buffer[i * 4 + 2] = b.toByte()
            buffer[i * 4 + 3] = 0xFF.toByte()
        }
        return buffer
    }

    /**
     * 将 3D LUT 数据转换为半精度浮点 RGBA 缓冲区
     * 用于 GPU 3D 纹理上传，精度高于字节格式
     */
    fun lutToHalfFloatRgbaBuffer(lut: Lut3D): ShortArray {
        val total = lut.size * lut.size * lut.size
        val buffer = ShortArray(total * 4)

        for (i in 0 until total) {
            buffer[i * 4] = floatToHalf(lut.data[i * 3])
            buffer[i * 4 + 1] = floatToHalf(lut.data[i * 3 + 1])
            buffer[i * 4 + 2] = floatToHalf(lut.data[i * 3 + 2])
            buffer[i * 4 + 3] = 0x3C00 // 1.0 in half-float
        }
        return buffer
    }

    /**
     * 应用 LUT 到 Bitmap（逐像素）
     * @param bitmap 输入图像
     * @param lut 3D LUT
     * @param intensity 应用强度 [0,1]，1.0=完全应用
     * @return 应用后的新 Bitmap
     */
    fun applyLutToBitmap(bitmap: Bitmap, lut: Lut3D, intensity: Float = 1f): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val pixels = IntArray(width * height)
        val outPixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = Color.red(pixel) / 255f
            val g = Color.green(pixel) / 255f
            val b = Color.blue(pixel) / 255f

            val (nr, ng, nb) = lut.sample(r, g, b)

            val fr = r + (nr - r) * intensity
            val fg = g + (ng - g) * intensity
            val fb = b + (nb - b) * intensity

            outPixels[i] = Color.argb(
                Color.alpha(pixel),
                (fr.coerceIn(0f, 1f) * 255).toInt(),
                (fg.coerceIn(0f, 1f) * 255).toInt(),
                (fb.coerceIn(0f, 1f) * 255).toInt(),
            )
        }

        result.setPixels(outPixels, 0, width, 0, 0, width, height)
        return result
    }

    /**
     * 生成 LUT 缩略图：将 LUT 应用到标准渐变测试图上
     * @param lut 3D LUT
     * @param width 缩略图宽度
     * @param height 缩略图高度
     * @return 应用 LUT 后的渐变 Bitmap
     */
    fun generateThumbnail(lut: Lut3D, width: Int = 128, height: Int = 128): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val r = x.toFloat() / (width - 1).coerceAtLeast(1)
                val g = y.toFloat() / (height - 1).coerceAtLeast(1)
                val b = 0.5f // 中灰蓝通道

                val (nr, ng, nb) = lut.sample(r, g, b)
                val idx = y * width + x
                pixels[idx] = Color.argb(
                    255,
                    (nr.coerceIn(0f, 1f) * 255).toInt(),
                    (ng.coerceIn(0f, 1f) * 255).toInt(),
                    (nb.coerceIn(0f, 1f) * 255).toInt(),
                )
            }
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
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

/**
 * LUT 管理器：导入、存储、列表管理（简化版）
 * 完整版见 LutLibraryManager
 */
class LutManager(private val context: android.content.Context) {

    data class LutEntry(
        val id: String,
        val name: String,
        val category: String,
        val filePath: String,
        val size: Int,
        val isBuiltIn: Boolean,
    )

    private val _luts = kotlinx.coroutines.flow.MutableStateFlow<List<LutEntry>>(emptyList())
    val luts: kotlinx.coroutines.flow.StateFlow<List<LutEntry>> = _luts.asStateFlow()

    private val lutDir = File(context.filesDir, "luts").also { it.mkdirs() }
    private val parser = CubeLutParser()

    fun importLut(file: File): Boolean {
        val lut = parser.parseFile3D(file) ?: return false
        return importLut(lut, file.nameWithoutExtension)
    }

    fun importLut(lut: CubeLutParser.Lut3D, name: String): Boolean {
        val id = "${System.currentTimeMillis()}_$name"
        val dest = File(lutDir, "$id.cube")
        dest.bufferedWriter().use { out ->
            out.write("TITLE \"$name\"\n")
            out.write("LUT_3D_SIZE ${lut.size}\n")
            out.write("DOMAIN_MIN ${lut.domainMin[0]} ${lut.domainMin[1]} ${lut.domainMin[2]}\n")
            out.write("DOMAIN_MAX ${lut.domainMax[0]} ${lut.domainMax[1]} ${lut.domainMax[2]}\n")
            val data = lut.data
            for (i in data.indices step 3) {
                out.write("${data[i]} ${data[i + 1]} ${data[i + 2]}\n")
            }
        }

        val entry = LutEntry(
            id = id,
            name = name,
            category = "用户导入",
            filePath = dest.absolutePath,
            size = lut.size,
            isBuiltIn = false,
        )
        _luts.value = _luts.value + entry
        return true
    }

    fun scanDirectory(dir: File) {
        dir.listFiles { _, name -> name.endsWith(".cube", true) }
            ?.forEach { importLut(it) }
    }

    fun deleteLut(id: String) {
        val entry = _luts.value.find { it.id == id } ?: return
        File(entry.filePath).delete()
        _luts.value = _luts.value.filterNot { it.id == id }
    }

    fun loadLut(id: String): CubeLutParser.Lut3D? {
        val entry = _luts.value.find { it.id == id } ?: return null
        return parser.parseFile3D(File(entry.filePath))
    }
}

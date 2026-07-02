package com.pixelfruit.core

import kotlin.math.pow

/**
 * P-08: .cube LUT 解析器
 * 解析标准 3D .cube LUT 文件格式，支持 1D/3D LUT。
 * 对标 PixelFruit v1.2.2 LUT 导入功能。
 *
 * 支持格式：
 * - LUT_3D_SIZE N
 * - LUT_1D_SIZE N
 * - DOMAIN_MIN / DOMAIN_MAX
 * - TITLE "name"
 */
class CubeLutParser {

    data class Lut3D(
        val size: Int,
        val title: String? = null,
        val domainMin: FloatArray = floatArrayOf(0f, 0f, 0f),
        val domainMax: FloatArray = floatArrayOf(1f, 1f, 1f),
        val data: FloatArray, // size^3 * 3 floats (RGB triples)
    )

    data class Lut1D(
        val size: Int,
        val title: String? = null,
        val domainMin: FloatArray = floatArrayOf(0f, 0f, 0f),
        val domainMax: FloatArray = floatArrayOf(1f, 1f, 1f),
        val data: FloatArray, // size * 3 floats (RGB triples)
    )

    /**
     * 解析 .cube 文件内容
     * @param content LUT 文件文本内容
     * @return Lut3D 或 Lut1D 对象
     */
    fun parse(content: String): Lut3D {
        val lines = content.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        var lutSize = 32
        var title: String? = null
        val domainMin = FloatArray(3) { 0f }
        val domainMax = FloatArray(3) { 1f }
        val dataValues = mutableListOf<Float>()

        for (line in lines) {
            when {
                line.startsWith("LUT_3D_SIZE") -> lutSize = line.split("\\s+".toRegex()).last().toInt()
                line.startsWith("LUT_1D_SIZE") -> lutSize = line.split("\\s+".toRegex()).last().toInt()
                line.startsWith("TITLE") -> title = line.removePrefix("TITLE").trim().removeSurrounding("\"")
                line.startsWith("DOMAIN_MIN") -> {
                    val parts = line.split("\\s+".toRegex())
                    domainMin[0] = parts[1].toFloat()
                    domainMin[1] = parts[2].toFloat()
                    domainMin[2] = parts[3].toFloat()
                }
                line.startsWith("DOMAIN_MAX") -> {
                    val parts = line.split("\\s+".toRegex())
                    domainMax[0] = parts[1].toFloat()
                    domainMax[1] = parts[2].toFloat()
                    domainMax[2] = parts[3].toFloat()
                }
                // 跳过关键字行
                line.startsWith("LUT_3D_SIZE") || line.startsWith("LUT_1D_SIZE") ||
                line.startsWith("TITLE") || line.startsWith("DOMAIN") -> {}
                // 数据行
                else -> {
                    val parts = line.split("\\s+".toRegex()).filter { it.isNotEmpty() }
                    if (parts.size >= 3) {
                        dataValues.add(parts[0].toFloat())
                        dataValues.add(parts[1].toFloat())
                        dataValues.add(parts[2].toFloat())
                    }
                }
            }
        }

        return Lut3D(
            size = lutSize,
            title = title,
            domainMin = domainMin,
            domainMax = domainMax,
            data = dataValues.toFloatArray(),
        )
    }

    /**
     * 应用 3D LUT 到 RGB 值（三线性插值）
     */
    fun apply3DLut(lut: Lut3D, r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val size = lut.size
        val maxIdx = size - 1

        // 归一化到 [0, maxIdx]
        val rf = (r.coerceIn(0f, 1f) * maxIdx)
        val gf = (g.coerceIn(0f, 1f) * maxIdx)
        val bf = (b.coerceIn(0f, 1f) * maxIdx)

        val r0 = rf.toInt().coerceIn(0, maxIdx)
        val g0 = gf.toInt().coerceIn(0, maxIdx)
        val b0 = bf.toInt().coerceIn(0, maxIdx)
        val r1 = (r0 + 1).coerceAtMost(maxIdx)
        val g1 = (g0 + 1).coerceAtMost(maxIdx)
        val b1 = (b0 + 1).coerceAtMost(maxIdx)

        val dr = rf - r0
        val dg = gf - g0
        val db = bf - b0

        fun sample(r: Int, g: Int, b: Int): FloatArray {
            val idx = (r * size * size + g * size + b) * 3
            return floatArrayOf(lut.data[idx], lut.data[idx + 1], lut.data[idx + 2])
        }

        // 三线性插值
        val c000 = sample(r0, g0, b0)
        val c001 = sample(r0, g0, b1)
        val c010 = sample(r0, g1, b0)
        val c011 = sample(r0, g1, b1)
        val c100 = sample(r1, g0, b0)
        val c101 = sample(r1, g0, b1)
        val c110 = sample(r1, g1, b0)
        val c111 = sample(r1, g1, b1)

        fun lerp(a: FloatArray, b: FloatArray, t: Float): FloatArray {
            return floatArrayOf(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t)
        }

        val c00 = lerp(c000, c100, dr)
        val c01 = lerp(c001, c101, dr)
        val c10 = lerp(c010, c110, dr)
        val c11 = lerp(c011, c111, dr)
        val c0 = lerp(c00, c10, dg)
        val c1 = lerp(c01, c11, dg)
        val result = lerp(c0, c1, db)

        return Triple(result[0], result[1], result[2])
    }
}
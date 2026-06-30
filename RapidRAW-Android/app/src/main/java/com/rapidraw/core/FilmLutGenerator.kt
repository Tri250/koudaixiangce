package com.rapidraw.core

import com.rapidraw.data.model.FilmSimulation
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 胶片3D LUT生成器 - 基于胶片特性参数生成3D查找表
 *
 * 核心算法：
 * 1. 胶片特征曲线（S曲线）= 趾部(toe) + 直线段(linear) + 肩部(shoulder)
 * 2. 每通道独立色调映射（红/绿/蓝各有不同的S曲线参数）
 * 3. 饱和度曲线（按亮度分段调制饱和度）
 * 4. 色彩偏移（在线性空间中对RGB进行偏移）
 * 5. 胶片颗粒（可选，生成时加入噪波扰动）
 *
 * 预设胶片模拟：
 * - Kodak Portra 400：暖色调、柔和高光
 * - Kodak Ektar 100：鲜艳饱和
 * - Fuji Velvia 50：超饱和、高对比
 * - Fuji Superia 400：冷色阴影、暖色中间调
 * - Agfa Vista 400：偏红肤色
 */
object FilmLutGenerator {

    // ── 数据模型 ──────────────────────────────────────────────────

    data class FilmLut(
        val size: Int,
        val data: FloatArray,  // RGB triplets, size^3 * 3
    ) {
        /**
         * 三线性插值采样
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
     * 胶片 LUT 生成参数
     * 每个参数均可独立配置，实现精细控制
     */
    data class FilmLutParams(
        val name: String,
        // ── 色调曲线参数 ──
        /** 趾部起始点（0-0.3，控制阴影起始位置） */
        val toeStart: Float = 0.0f,
        /** 趾部宽度（0-0.3，控制阴影过渡范围） */
        val toeWidth: Float = 0.1f,
        /** 趾部斜率（0-1，控制阴影提亮程度） */
        val toeSlope: Float = 0.5f,
        /** 直线段斜率（0.5-2.0，控制中间调对比度） */
        val midSlope: Float = 1.0f,
        /** 肩部起始点（0.7-1.0，控制高光起始位置） */
        val shoulderStart: Float = 0.85f,
        /** 肩部宽度（0-0.3，控制高光过渡范围） */
        val shoulderWidth: Float = 0.15f,
        /** 肩部衰减率（0-1，控制高光压缩程度） */
        val shoulderFalloff: Float = 0.5f,

        // ── 饱和度曲线 ──
        /** 阴影区域饱和度系数（-1..1，正值增加，负值降低） */
        val shadowSaturation: Float = 0.0f,
        /** 中间调饱和度系数 */
        val midSaturation: Float = 0.0f,
        /** 高光区域饱和度系数 */
        val highlightSaturation: Float = 0.0f,
        /** 全局饱和度乘数 */
        val globalSaturation: Float = 1.0f,

        // ── 色彩偏移 ──
        /** 红色通道偏移（-1..1） */
        val redShift: Float = 0.0f,
        /** 绿色通道偏移（-1..1） */
        val greenShift: Float = 0.0f,
        /** 蓝色通道偏移（-1..1） */
        val blueShift: Float = 0.0f,
        /** 阴影区域色温偏移（-1=冷, 1=暖） */
        val shadowTint: Float = 0.0f,
        /** 高光区域色温偏移 */
        val highlightTint: Float = 0.0f,

        // ── 对比度 ──
        /** 全局对比度调节（-1..1） */
        val contrast: Float = 0.0f,
        /** 黑场提升（0..1，提高黑点） */
        val blackLift: Float = 0.0f,
        /** 白场压缩（0..1，降低白点） */
        val whiteCompression: Float = 0.0f,

        // ── 颗粒 ──
        /** 颗粒强度（0..1，0=无颗粒） */
        val grainAmount: Float = 0.0f,
        /** 颗粒大小（0..1，0=极细） */
        val grainSize: Float = 0.3f,
        /** 颗粒粗糙度（0..1） */
        val grainRoughness: Float = 0.3f,
    )

    // ── 预设胶片参数 ─────────────────────────────────────────────

    /**
     * Kodak Portra 400
     * 特征：暖色调、柔和高光、优秀肤色、低对比
     * - 趾部宽：阴影提升多，暗部不堵死
     * - 肩部宽：高光柔和滚降，不突然截断
     * - 红移微暖：肤色自然红润
     * - 低饱和度：负片特有的淡雅
     */
    val KODAK_PORTRA_400 = FilmLutParams(
        name = "Kodak Portra 400",
        toeStart = 0.0f, toeWidth = 0.12f, toeSlope = 0.45f,
        midSlope = 0.92f,
        shoulderStart = 0.82f, shoulderWidth = 0.18f, shoulderFalloff = 0.55f,
        shadowSaturation = -0.08f, midSaturation = -0.05f, highlightSaturation = -0.15f,
        globalSaturation = 0.9f,
        redShift = 0.04f, greenShift = 0.0f, blueShift = -0.03f,
        shadowTint = 0.03f, highlightTint = 0.06f,
        contrast = -0.1f, blackLift = 0.01f, whiteCompression = 0.02f,
        grainAmount = 0.08f, grainSize = 0.3f, grainRoughness = 0.2f,
    )

    /**
     * Kodak Ektar 100
     * 特征：鲜艳饱和、高对比、蓝绿通透
     * - 高中间调斜率：对比度强
     * - 高饱和度：色彩极其鲜艳
     * - 蓝移：蓝绿色通道增强
     * - 极细颗粒：ISO 100 几乎无颗粒
     */
    val KODAK_EKTAR_100 = FilmLutParams(
        name = "Kodak Ektar 100",
        toeStart = 0.0f, toeWidth = 0.06f, toeSlope = 0.35f,
        midSlope = 1.12f,
        shoulderStart = 0.88f, shoulderWidth = 0.12f, shoulderFalloff = 0.4f,
        shadowSaturation = 0.1f, midSaturation = 0.2f, highlightSaturation = 0.1f,
        globalSaturation = 1.3f,
        redShift = -0.02f, greenShift = 0.03f, blueShift = 0.06f,
        shadowTint = -0.02f, highlightTint = -0.04f,
        contrast = 0.15f, blackLift = 0.0f, whiteCompression = 0.0f,
        grainAmount = 0.03f, grainSize = 0.18f, grainRoughness = 0.12f,
    )

    /**
     * Fuji Velvia 50
     * 特征：超饱和、高对比、红绿蓝爆发力
     * - 窄趾部：暗部较深
     * - 高斜率：中间调对比极强
     * - 窄肩部：高光截断较硬（反转片特征）
     * - 极高饱和度
     */
    val FUJI_VELVIA_50 = FilmLutParams(
        name = "Fuji Velvia 50",
        toeStart = 0.0f, toeWidth = 0.04f, toeSlope = 0.25f,
        midSlope = 1.2f,
        shoulderStart = 0.9f, shoulderWidth = 0.1f, shoulderFalloff = 0.35f,
        shadowSaturation = 0.15f, midSaturation = 0.35f, highlightSaturation = 0.2f,
        globalSaturation = 1.45f,
        redShift = 0.05f, greenShift = 0.02f, blueShift = 0.02f,
        shadowTint = -0.03f, highlightTint = 0.0f,
        contrast = 0.22f, blackLift = 0.0f, whiteCompression = 0.0f,
        grainAmount = 0.03f, grainSize = 0.18f, grainRoughness = 0.15f,
    )

    /**
     * Fuji Superia 400
     * 特征：冷色阴影、暖色中间调、偏绿
     * - 冷色阴影：阴影区域蓝色偏移
     * - 暖色中间调：中间调偏暖偏绿
     * - 中等颗粒：ISO 400 标准颗粒
     */
    val FUJI_SUPERIA_400 = FilmLutParams(
        name = "Fuji Superia 400",
        toeStart = 0.0f, toeWidth = 0.1f, toeSlope = 0.42f,
        midSlope = 0.95f,
        shoulderStart = 0.84f, shoulderWidth = 0.16f, shoulderFalloff = 0.5f,
        shadowSaturation = -0.1f, midSaturation = 0.05f, highlightSaturation = -0.1f,
        globalSaturation = 1.0f,
        redShift = -0.02f, greenShift = 0.04f, blueShift = 0.05f,
        shadowTint = -0.06f, highlightTint = 0.04f,
        contrast = -0.02f, blackLift = 0.005f, whiteCompression = 0.01f,
        grainAmount = 0.1f, grainSize = 0.35f, grainRoughness = 0.25f,
    )

    /**
     * Agfa Vista 400
     * 特征：偏红肤色、暖色调、日常感
     * - 偏红：肤色有明显的红色偏移
     * - 暖色：整体偏暖偏黄
     * - 粗犷颗粒：ISO 400 颗粒感
     */
    val AGFA_VISTA_400 = FilmLutParams(
        name = "Agfa Vista 400",
        toeStart = 0.0f, toeWidth = 0.1f, toeSlope = 0.4f,
        midSlope = 0.93f,
        shoulderStart = 0.83f, shoulderWidth = 0.17f, shoulderFalloff = 0.48f,
        shadowSaturation = -0.05f, midSaturation = 0.0f, highlightSaturation = -0.08f,
        globalSaturation = 0.95f,
        redShift = 0.08f, greenShift = 0.02f, blueShift = -0.08f,
        shadowTint = 0.05f, highlightTint = 0.08f,
        contrast = -0.05f, blackLift = 0.008f, whiteCompression = 0.015f,
        grainAmount = 0.12f, grainSize = 0.4f, grainRoughness = 0.3f,
    )

    /** 所有预设胶片参数 */
    val PRESETS = listOf(
        KODAK_PORTRA_400,
        KODAK_EKTAR_100,
        FUJI_VELVIA_50,
        FUJI_SUPERIA_400,
        AGFA_VISTA_400,
    )

    // ── 核心生成 ──────────────────────────────────────────────────

    /**
     * 从 FilmSimulation 参数生成 3D LUT
     * @param film 胶片模拟数据
     * @param lutSize LUT 尺寸（17=标准，33=高精度）
     */
    fun generateFromFilm(film: FilmSimulation, lutSize: Int = 17): FilmLut {
        val data = FloatArray(lutSize * lutSize * lutSize * 3)

        val curvePoints = film.toneCurvePoints.sortedBy { it.first }

        for (r in 0 until lutSize) {
            for (g in 0 until lutSize) {
                for (b in 0 until lutSize) {
                    val nr = r.toFloat() / (lutSize - 1)
                    val ng = g.toFloat() / (lutSize - 1)
                    val nb = b.toFloat() / (lutSize - 1)

                    // 1. 色彩偏移（在线性空间中）
                    var or = nr + film.redShift * 0.15f
                    var og = ng + film.greenShift * 0.15f
                    var ob = nb + film.blueShift * 0.15f

                    // 2. 胶片特征曲线（S曲线）
                    or = applyFilmCurve(or, curvePoints)
                    og = applyFilmCurve(og, curvePoints)
                    ob = applyFilmCurve(ob, curvePoints)

                    // 3. 饱和度调节
                    if (abs(film.saturationModifier) > 0.001f) {
                        val luma = 0.2126f * or + 0.7152f * og + 0.0722f * ob
                        val mod = 1f + film.saturationModifier
                        or = luma + (or - luma) * mod
                        og = luma + (og - luma) * mod
                        ob = luma + (ob - luma) * mod
                    }

                    // 4. 对比度调节
                    if (abs(film.contrastModifier) > 0.001f) {
                        val contrastPow = 1f + film.contrastModifier * 0.5f
                        val mid = 0.18f
                        or = mid + (or - mid) * contrastPow
                        og = mid + (og - mid) * contrastPow
                        ob = mid + (ob - mid) * contrastPow
                    }

                    // 5. 高光滚降
                    if (film.highlightRollOff > 0.001f) {
                        or = applyHighlightRollOff(or, film.highlightRollOff)
                        og = applyHighlightRollOff(og, film.highlightRollOff)
                        ob = applyHighlightRollOff(ob, film.highlightRollOff)
                    }

                    // 6. 阴影提升
                    if (film.shadowLift > 0.001f) {
                        val luma = 0.2126f * or + 0.7152f * og + 0.0722f * ob
                        val sMask = ColorMath.shadowsMask(luma)
                        val lift = film.shadowLift * 0.2f * sMask
                        or += lift
                        og += lift
                        ob += lift
                    }

                    // 7. 动态范围压缩
                    if (film.drCompression > 0.001f) {
                        or = applyDrCompression(or, film.drCompression)
                        og = applyDrCompression(og, film.drCompression)
                        ob = applyDrCompression(ob, film.drCompression)
                    }

                    // Clamp
                    or = or.coerceIn(0f, 1f)
                    og = og.coerceIn(0f, 1f)
                    ob = ob.coerceIn(0f, 1f)

                    val idx = (r * lutSize * lutSize + g * lutSize + b) * 3
                    data[idx] = or
                    data[idx + 1] = og
                    data[idx + 2] = ob
                }
            }
        }

        return FilmLut(size = lutSize, data = data)
    }

    /**
     * 从 FilmLutParams 生成 3D LUT（更精细的参数控制）
     * @param params 胶片 LUT 生成参数
     * @param lutSize LUT 尺寸
     */
    fun generateFromParams(params: FilmLutParams, lutSize: Int = 17): FilmLut {
        val data = FloatArray(lutSize * lutSize * lutSize * 3)

        for (r in 0 until lutSize) {
            for (g in 0 until lutSize) {
                for (b in 0 until lutSize) {
                    val nr = r.toFloat() / (lutSize - 1)
                    val ng = g.toFloat() / (lutSize - 1)
                    val nb = b.toFloat() / (lutSize - 1)

                    // 1. 全局色彩偏移
                    var or = nr + params.redShift * 0.15f
                    var og = ng + params.greenShift * 0.15f
                    var ob = nb + params.blueShift * 0.15f

                    // 2. 亮度依赖的色温偏移
                    val luma1 = 0.2126f * or + 0.7152f * og + 0.0722f * ob
                    val shadowWeight = ColorMath.shadowsMask(luma1)
                    val highlightWeight = ColorMath.highlightsMask(luma1)
                    val tintShift = params.shadowTint * shadowWeight + params.highlightTint * highlightTint
                    or += tintShift * 0.06f
                    og += tintShift * 0.02f
                    ob -= tintShift * 0.08f

                    // 3. 胶片特征曲线（S曲线 with toe/shoulder）
                    or = applySCurve(or, params)
                    og = applySCurve(og, params)
                    ob = applySCurve(ob, params)

                    // 4. 亮度依赖的饱和度调节
                    val luma2 = 0.2126f * or + 0.7152f * og + 0.0722f * ob
                    val satMod = calculateSaturationModifier(luma2, params) * params.globalSaturation
                    if (abs(satMod - 1f) > 0.001f) {
                        or = luma2 + (or - luma2) * satMod
                        og = luma2 + (og - luma2) * satMod
                        ob = luma2 + (ob - luma2) * satMod
                    }

                    // 5. 对比度调节（围绕中灰点）
                    if (abs(params.contrast) > 0.001f) {
                        val contrastPow = 1f + params.contrast * 0.5f
                        val mid = 0.18f
                        or = mid + (or - mid) * contrastPow
                        og = mid + (og - mid) * contrastPow
                        ob = mid + (ob - mid) * contrastPow
                    }

                    // 6. 黑场/白场
                    if (params.blackLift > 0.001f) {
                        or = params.blackLift + or * (1f - params.blackLift)
                        og = params.blackLift + og * (1f - params.blackLift)
                        ob = params.blackLift + ob * (1f - params.blackLift)
                    }
                    if (params.whiteCompression > 0.001f) {
                        or = or * (1f - params.whiteCompression)
                        og = og * (1f - params.whiteCompression)
                        ob = ob * (1f - params.whiteCompression)
                    }

                    // 7. 颗粒扰动（仅当强度 > 0 时）
                    if (params.grainAmount > 0.001f) {
                        val grain = generateGrain(r, g, b, params)
                        or += grain
                        og += grain
                        ob += grain
                    }

                    // Clamp
                    or = or.coerceIn(0f, 1f)
                    og = og.coerceIn(0f, 1f)
                    ob = ob.coerceIn(0f, 1f)

                    val idx = (r * lutSize * lutSize + g * lutSize + b) * 3
                    data[idx] = or
                    data[idx + 1] = og
                    data[idx + 2] = ob
                }
            }
        }

        return FilmLut(size = lutSize, data = data)
    }

    /**
     * 便捷方法：生成预设胶片 LUT
     */
    fun generatePreset(preset: FilmLutParams, lutSize: Int = 17): FilmLut {
        return generateFromParams(preset, lutSize)
    }

    /**
     * 生成所有预设 LUT
     */
    fun generateAllPresets(lutSize: Int = 17): Map<String, FilmLut> {
        return PRESETS.associateWith { generateFromParams(it, lutSize) }
    }

    /**
     * 将 FilmLut 转换为 CubeLutParser.Lut3D（用于兼容管线）
     */
    fun toLut3D(filmLut: FilmLut): CubeLutParser.Lut3D {
        return CubeLutParser.Lut3D(
            size = filmLut.size,
            data = filmLut.data,
        )
    }

    /**
     * 生成 .cube 文件内容
     */
    fun generateCubeFile(filmLut: FilmLut, title: String = ""): String {
        val sb = StringBuilder()
        if (title.isNotBlank()) {
            sb.append("TITLE \"$title\"\n")
        }
        sb.append("LUT_3D_SIZE ${filmLut.size}\n")
        sb.append("DOMAIN_MIN 0.0 0.0 0.0\n")
        sb.append("DOMAIN_MAX 1.0 1.0 1.0\n")
        sb.append("\n")

        val data = filmLut.data
        for (i in data.indices step 3) {
            sb.append("${formatFloat(data[i])} ${formatFloat(data[i + 1])} ${formatFloat(data[i + 2])}\n")
        }
        return sb.toString()
    }

    // ── S曲线实现 ─────────────────────────────────────────────────

    /**
     * 胶片特征曲线（S曲线）
     * 模拟胶片密度-曝光曲线的三个区域：
     * - 趾部(toe)：低曝光区域，密度增长缓慢
     * - 直线段(linear)：正常曝光区域，密度线性增长
     * - 肩部(shoulder)：过曝区域，密度增长放缓直至饱和
     *
     * @param x 输入值 [0,1]
     * @param params 胶片参数
     * @return 映射后的值
     */
    private fun applySCurve(x: Float, params: FilmLutParams): Float {
        if (x <= 0f) return params.blackLift
        if (x >= 1f) return 1f - params.whiteCompression

        val toeEnd = params.toeStart + params.toeWidth
        val shoulderBegin = params.shoulderStart

        return when {
            // 趾部区域
            x < toeEnd -> {
                val t = if (params.toeWidth > 0f) (x - params.toeStart) / params.toeWidth else 0f
                val tClamped = t.coerceIn(0f, 1f)
                // 二次曲线：从 toeStart 开始，斜率为 toeSlope * midSlope
                val linearAtToeEnd = params.toeStart + toeEnd * params.midSlope
                val curveAtToeEnd = linearAtToeEnd
                val toeValue = params.toeStart + (curveAtToeEnd - params.toeStart) * (tClamped * tClamped * (3f - 2f * tClamped))
                // 混合线性与曲线
                val linearVal = x * params.midSlope
                toeValue * (1f - params.toeSlope) + linearVal * params.toeSlope
            }
            // 直线段
            x < shoulderBegin -> {
                x * params.midSlope
            }
            // 肩部区域
            else -> {
                val linearAtShoulder = shoulderBegin * params.midSlope
                val remaining = 1f - linearAtShoulder - params.whiteCompression
                val t = if (params.shoulderWidth > 0f) (x - shoulderBegin) / params.shoulderWidth else 0f
                val tClamped = t.coerceIn(0f, 1f)
                // 1 - (1-t)^n 形式的肩部曲线
                val n = 1f + params.shoulderFalloff * 3f
                val shoulderCurve = 1f - (1f - tClamped).toDouble().pow(n.toDouble()).toFloat()
                linearAtShoulder + remaining * shoulderCurve
            }
        }
    }

    /**
     * 从 FilmSimulation 的色调控制点应用曲线
     */
    private fun applyFilmCurve(value: Float, points: List<Pair<Float, Float>>): Float {
        if (points.size < 2) return value
        val normalized = points.map { it.first / 255f to it.second / 255f }
        return ColorMath.applyCurve(value, normalized)
    }

    /**
     * 高光滚降
     */
    private fun applyHighlightRollOff(value: Float, rollOff: Float): Float {
        if (value < 0.5f) return value
        val hMask = ColorMath.highlightsMask(value)
        val shoulder = 1f - (1f - value).toDouble().pow(1.0 + rollOff * 2.0).toFloat()
        return value + (shoulder - value) * hMask
    }

    /**
     * 动态范围压缩
     * 使用 Reinhard-style 压缩
     */
    private fun applyDrCompression(value: Float, compression: Float): Float {
        if (compression < 0.001f) return value
        // Reinhard: L / (1 + L)
        val compressed = value / (1f + value * compression)
        // 混合原始值和压缩值
        return value * (1f - compression) + compressed * compression * 2f
    }

    // ── 饱和度曲线 ────────────────────────────────────────────────

    /**
     * 计算亮度依赖的饱和度修饰因子
     * 低亮度 → shadowSaturation
     * 中间调 → midSaturation
     * 高亮度 → highlightSaturation
     */
    private fun calculateSaturationModifier(luma: Float, params: FilmLutParams): Float {
        val shadowW = ColorMath.shadowsMask(luma)
        val highlightW = ColorMath.highlightsMask(luma)
        val midW = 1f - shadowW - highlightW

        val base = 1f
        return base +
            params.shadowSaturation * shadowW +
            params.midSaturation * midW +
            params.highlightSaturation * highlightW
    }

    // ── 颗粒生成 ──────────────────────────────────────────────────

    /**
     * 为 LUT 节点生成胶片颗粒扰动
     * 使用基于位置的海森堡噪波模拟颗粒
     */
    private fun generateGrain(r: Int, g: Int, b: Int, params: FilmLutParams): Float {
        if (params.grainAmount < 0.001f) return 0f

        // 使用 LUT 坐标作为噪波种子
        val scale = 1f + params.grainSize * 5f
        val nx = r * scale / 17f
        val ny = g * scale / 17f
        val nz = b * scale / 17f

        // 简单的值噪波
        val noise = (simpleHash(r * 73856093 + g * 19349669 + b * 83492791) - 0.5f) * 2f

        // 应用颗粒强度和粗糙度
        val grainIntensity = params.grainAmount * 0.03f
        val roughnessMod = 1f - params.grainRoughness * 0.5f

        return noise * grainIntensity * roughnessMod
    }

    /**
     * 简单哈希函数，返回 [0, 1] 的伪随机值
     */
    private fun simpleHash(n: Int): Float {
        var x = n.toLong() and 0xFFFFFFFFL
        x = ((x shr 16) xor x) * 0x45d9f3bL
        x = ((x shr 16) xor x) * 0x45d9f3bL
        x = (x shr 16) xor x
        return (x and 0xFFFFL) / 65536.0f
    }

    // ── 工具方法 ──────────────────────────────────────────────────

    private fun formatFloat(f: Float): String {
        return String.format("%.6f", f)
    }
}

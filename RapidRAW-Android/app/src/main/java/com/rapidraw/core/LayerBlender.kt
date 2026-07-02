package com.rapidraw.core

import android.graphics.Bitmap
import android.util.Log
import com.rapidraw.data.model.AdjustmentLayer
import com.rapidraw.data.model.BlendMode
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 图层混合器 — 支持标准混合模式、图层遮罩、调整图层。
 *
 * 混合模式（W3C CSS Compositing and Blending Level 1 + Photoshop 标准）：
 * - Normal: 直接覆盖
 * - Multiply: 正片叠底（暗化）
 * - Screen: 滤色（亮化）
 * - Overlay: 叠加（暗处 Multiply，亮处 Screen）
 * - Soft Light: 柔光（温和版 Overlay）
 * - Hard Light: 强光（强版 Overlay）
 * - Difference: 差值（绝对差）
 * - Color Dodge: 颜色减淡（提亮底色以反映上层）
 * - Color Burn: 颜色加深（加深底色以反映上层）
 *
 * 所有混合公式在 [0,1] 线性空间中定义，操作前将 8-bit sRGB 解码为线性值，
 * 混合后再编码回 sRGB。但传统实现中大多数混合模式直接在 gamma 空间操作
 * （与 Photoshop 行为一致），本实现也采用 gamma 空间混合。
 *
 * 图层遮罩：
 * - 独立的灰度 Bitmap，0=完全透明，255=完全不透明
 * - 与 opacity 相乘得到最终透明度
 *
 * 调整图层：
 * - 包含 Adjustments 而非 Bitmap 内容
 * - 混合时先对底图应用调整，再用指定混合模式+透明度与底图混合
 * - 本质上是"带混合模式的全图调整"
 */
object LayerBlender {

    /**
     * 图层定义 — 混合的基本单元。
     */
    data class Layer(
        val bitmap: Bitmap? = null,
        val adjustmentLayer: AdjustmentLayer? = null,
        val blendMode: BlendMode = BlendMode.NORMAL,
        val opacity: Float = 1f,
        val mask: Bitmap? = null,
    )

    // ── 公开 API ──────────────────────────────────────────────────────

    /**
     * 将多个图层从底到顶混合为一张图像。
     *
     * 第一层为基底（background），后续图层依次叠加。
     * 每个图层使用自己的混合模式、透明度和遮罩。
     *
     * @param layers 图层列表（从底到顶）
     * @param width 输出宽度
     * @param height 输出高度
     * @return 混合后的 Bitmap
     */
    fun blendLayers(
        layers: List<Layer>,
        width: Int,
        height: Int,
    ): Bitmap {
        if (layers.isEmpty()) {
            return try {
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            } catch (_: OutOfMemoryError) {
                Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            }
        }

        // 初始化为第一层（基底）
        val firstBitmap = layers[0].bitmap
        var result = try {
            if (firstBitmap != null) {
                firstBitmap.copy(Bitmap.Config.ARGB_8888, true)
            } else if (layers[0].adjustmentLayer != null) {
                // 调整图层作为基底时：创建白色画布并应用调整
                val white = createWhiteBitmap(width, height)
                val adjLayer = layers[0].adjustmentLayer
                if (adjLayer != null) {
                    applyAdjustmentLayer(white, adjLayer)
                }
                white
            } else {
                createWhiteBitmap(width, height)
            }
        } catch (e: OutOfMemoryError) {
            Log.e("LayerBlender", "OOM creating base bitmap ${width}x${height}", e)
            return try { Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888) } catch (_: OutOfMemoryError) { firstBitmap ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888) }
        }

        // 依次混合后续图层
        for (i in 1 until layers.size) {
            result = blendLayer(result, layers[i], width, height)
        }

        return result
    }

    /**
     * 将一个图层混合到基底图像上。
     *
     * @param base 基底图像（会被修改）
     * @param layer 要混合的图层
     */
    fun blendLayer(
        base: Bitmap,
        layer: Layer,
        width: Int = base.width,
        height: Int = base.height,
    ): Bitmap {
        val w = base.width
        val h = base.height

        // 获取图层内容像素
        val layerPixels = when {
            layer.bitmap != null -> getLayerPixels(layer.bitmap, w, h)
            layer.adjustmentLayer != null -> {
                // 调整图层：复制基底，应用调整，作为图层内容
                val adjusted = try {
                    base.copy(Bitmap.Config.ARGB_8888, true)
                } catch (e: OutOfMemoryError) {
                    Log.e("LayerBlender", "OOM copying base for adjustment layer", e)
                    return base
                }
                val adj = layer.adjustmentLayer
                if (adj != null) {
                    applyAdjustmentLayer(adjusted, adj)
                }
                getPixels(adjusted).also { adjusted.recycle() }
            }
            else -> return base // 空图层，不操作
        }

        val basePixels = getPixels(base)
        val maskPixels = layer.mask?.let { getMaskPixels(it, w, h) }

        val effectiveOpacity = layer.opacity.coerceIn(0f, 1f)

        // 逐像素混合
        for (i in basePixels.indices) {
            val basePx = basePixels[i]
            val layerPx = layerPixels[i]

            val baseA = (basePx shr 24) and 0xFF
            val baseR = (basePx shr 16) and 0xFF
            val baseG = (basePx shr 8) and 0xFF
            val baseB = basePx and 0xFF

            val layerA = (layerPx shr 24) and 0xFF
            val layerR = (layerPx shr 16) and 0xFF
            val layerG = (layerPx shr 8) and 0xFF
            val layerB = layerPx and 0xFF

            // 遮罩值（0=完全透明，255=完全不透明）
            val maskAlpha = maskPixels?.get(i) ?: 255

            // 综合透明度 = 图层 alpha × opacity × mask
            val combinedAlpha = (layerA / 255f) * effectiveOpacity * (maskAlpha / 255f)

            if (combinedAlpha < 0.001f) continue // 完全透明，跳过

            // 混合
            val bf = baseR / 255f
            val bg = baseG / 255f
            val bb = baseB / 255f

            val lf = layerR / 255f
            val lg = layerG / 255f
            val lb = layerB / 255f

            // 应用混合模式
            val (rBlend, gBlend, bBlend) = applyBlendMode(bf, bg, bb, lf, lg, lb, layer.blendMode)

            // Porter-Duff Source Over 合成
            val outR = lerp(bf, rBlend, combinedAlpha)
            val outG = lerp(bg, gBlend, combinedAlpha)
            val outB = lerp(bb, bBlend, combinedAlpha)
            val outA = baseA + (255 - baseA) * combinedAlpha

            basePixels[i] = (outA.toInt().coerceIn(0, 255) shl 24) or
                    (outR.toByte8() shl 16) or
                    (outG.toByte8() shl 8) or
                    outB.toByte8()
        }

        base.setPixels(basePixels, 0, w, 0, 0, w, h)
        return base
    }

    /**
     * 便捷方法：将两个 Bitmap 用指定混合模式和透明度混合。
     */
    fun blend(
        base: Bitmap,
        overlay: Bitmap,
        blendMode: BlendMode = BlendMode.NORMAL,
        opacity: Float = 1f,
        mask: Bitmap? = null,
    ): Bitmap {
        val result = try {
            base.copy(Bitmap.Config.ARGB_8888, true)
        } catch (e: OutOfMemoryError) {
            Log.e("LayerBlender", "OOM copying base bitmap", e)
            return base
        }
        return blendLayer(result, Layer(bitmap = overlay, blendMode = blendMode, opacity = opacity, mask = mask))
    }

    // ── 混合模式实现 ──────────────────────────────────────────────────

    /**
     * 应用混合模式，返回混合后的颜色值。
     *
     * 所有输入输出均为 [0,1] 范围。
     * 公式参考：W3C CSS Compositing and Blending Level 1
     *           Adobe Photoshop 混合模式规范
     */
    private fun applyBlendMode(
        baseR: Float, baseG: Float, baseB: Float,
        layerR: Float, layerG: Float, layerB: Float,
        mode: BlendMode,
    ): Triple<Float, Float, Float> {
        return when (mode) {
            BlendMode.NORMAL -> Triple(layerR, layerG, layerB)

            BlendMode.MULTIPLY -> Triple(
                baseR * layerR,
                baseG * layerG,
                baseB * layerB,
            )

            BlendMode.SCREEN -> Triple(
                1f - (1f - baseR) * (1f - layerR),
                1f - (1f - baseG) * (1f - layerG),
                1f - (1f - baseB) * (1f - layerB),
            )

            BlendMode.OVERLAY -> Triple(
                overlayChannel(baseR, layerR),
                overlayChannel(baseG, layerG),
                overlayChannel(baseB, layerB),
            )

            BlendMode.SOFT_LIGHT -> Triple(
                softLightChannel(baseR, layerR),
                softLightChannel(baseG, layerG),
                softLightChannel(baseB, layerB),
            )

            BlendMode.HARD_LIGHT -> Triple(
                hardLightChannel(baseR, layerR),
                hardLightChannel(baseG, layerG),
                hardLightChannel(baseB, layerB),
            )

            BlendMode.DIFFERENCE -> Triple(
                abs(baseR - layerR),
                abs(baseG - layerG),
                abs(baseB - layerB),
            )

            BlendMode.COLOR_DODGE -> Triple(
                colorDodgeChannel(baseR, layerR),
                colorDodgeChannel(baseG, layerG),
                colorDodgeChannel(baseB, layerB),
            )

            BlendMode.COLOR_BURN -> Triple(
                colorBurnChannel(baseR, layerR),
                colorBurnChannel(baseG, layerG),
                colorBurnChannel(baseB, layerB),
            )

            // Color: 用上层的色相+饱和度，底层的明度
            BlendMode.COLOR -> {
                val baseLum = luminance(baseR, baseG, baseB)
                val (_, layerSat, _) = hslFromRgb(layerR, layerG, layerB)
                val (or, og, ob) = hslToRgb(hueFromRgb(layerR, layerG, layerB), layerSat, baseLum)
                Triple(or, og, ob)
            }

            // Luminosity: 用上层的明度，底层的色相+饱和度
            BlendMode.LUMINOSITY -> {
                val layerLum = luminance(layerR, layerG, layerB)
                val (_, baseSat, _) = hslFromRgb(baseR, baseG, baseB)
                val (or, og, ob) = hslToRgb(hueFromRgb(baseR, baseG, baseB), baseSat, layerLum)
                Triple(or, og, ob)
            }
        }
    }

    /**
     * Overlay: 底层 ≤ 0.5 → 2×Multiply，底层 > 0.5 → 1 - 2×(1-base)×(1-layer)
     */
    private fun overlayChannel(base: Float, layer: Float): Float {
        return if (base <= 0.5f) {
            2f * base * layer
        } else {
            1f - 2f * (1f - base) * (1f - layer)
        }
    }

    /**
     * Soft Light: Photoshop 规范
     * - layer ≤ 0.5: base - (1 - 2*layer) * base * (1 - base)
     * - layer > 0.5: base + (2*layer - 1) * (d(base) - base)
     *   其中 d(x) = ((16x - 12)x + 4)x  (当 x ≤ 0.25)
     *              = sqrt(x)              (当 x > 0.25)
     */
    private fun softLightChannel(base: Float, layer: Float): Float {
        return if (layer <= 0.5f) {
            base - (1f - 2f * layer) * base * (1f - base)
        } else {
            val d = if (base <= 0.25f) {
                ((16f * base - 12f) * base + 4f) * base
            } else {
                sqrtSafe(base)
            }
            base + (2f * layer - 1f) * (d - base)
        }
    }

    /**
     * Hard Light: Overlay 的变体 — 以图层亮度决定分支
     * - layer ≤ 0.5: 2×base×layer (Multiply)
     * - layer > 0.5: 1 - 2×(1-base)×(1-layer) (Screen)
     */
    private fun hardLightChannel(base: Float, layer: Float): Float {
        return if (layer <= 0.5f) {
            2f * base * layer
        } else {
            1f - 2f * (1f - base) * (1f - layer)
        }
    }

    /**
     * Color Dodge: 减淡 — 底色变亮以反映上层
     * - layer = 1: 结果为 1（不除零）
     * - layer = 0: 结果为 0
     * - 否则: min(base / (1 - layer), 1)
     */
    private fun colorDodgeChannel(base: Float, layer: Float): Float {
        return when {
            layer >= 1f -> 1f
            layer <= 0f -> 0f
            else -> min(base / (1f - layer), 1f)
        }
    }

    /**
     * Color Burn: 加深 — 底色变暗以反映上层
     * - layer = 0: 结果为 0
     * - layer = 1: 结果为 1
     * - 否则: max(1 - (1 - base) / layer, 0)
     */
    private fun colorBurnChannel(base: Float, layer: Float): Float {
        return when {
            layer <= 0f -> 0f
            layer >= 1f -> 1f
            else -> max(1f - (1f - base) / layer, 0f)
        }
    }

    // ── 调整图层 ──────────────────────────────────────────────────────

    /**
     * 对 Bitmap 应用调整图层中的调整参数。
     *
     * 调整图层不含像素数据，而是描述对底图的调整。
     * 本质上相当于将 ImageProcessor 的调整应用到整张图。
     */
    private fun applyAdjustmentLayer(bitmap: Bitmap, adjustmentLayer: AdjustmentLayer) {
        // 使用 ImageProcessor 处理调整
        // 注意：这里简化为直接使用 ColorMath 对像素进行基本调整
        // 实际项目中应委托给 ImageProcessor
        val w = bitmap.width
        val h = bitmap.height
        // 2026 hotfix: 防御 w*h 整数溢出
        val pixelCount = w.toLong() * h.toLong()
        if (pixelCount > Int.MAX_VALUE.toLong()) {
            Log.e("LayerBlender", "applyAdjustmentLayer: bitmap too large ${w}x$h")
            return
        }
        val count = pixelCount.toInt()
        val pixels = IntArray(count)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val adjustments = adjustmentLayer.adjustments
        val brightness = adjustments.exposure // 复用 exposure 作为亮度偏移
        val contrast = adjustments.contrast
        val saturation = adjustments.saturation

        for (i in pixels.indices) {
            val px = pixels[i]
            var r = ((px shr 16) and 0xFF) / 255f
            var g = ((px shr 8) and 0xFF) / 255f
            var b = (px and 0xFF) / 255f

            // 亮度
            r = (r + brightness).coerceIn(0f, 1f)
            g = (g + brightness).coerceIn(0f, 1f)
            b = (b + brightness).coerceIn(0f, 1f)

            // 对比度
            r = applyContrast(r, contrast)
            g = applyContrast(g, contrast)
            b = applyContrast(b, contrast)

            // 饱和度
            val gray = 0.299f * r + 0.587f * g + 0.114f * b
            r = gray + (r - gray) * saturation
            g = gray + (g - gray) * saturation
            b = gray + (b - gray) * saturation

            r = r.coerceIn(0f, 1f)
            g = g.coerceIn(0f, 1f)
            b = b.coerceIn(0f, 1f)

            val r8 = (r * 255f).toInt().coerceIn(0, 255)
            val g8 = (g * 255f).toInt().coerceIn(0, 255)
            val b8 = (b * 255f).toInt().coerceIn(0, 255)

            pixels[i] = (0xFF shl 24) or (r8 shl 16) or (g8 shl 8) or b8
        }

        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
    }

    private fun applyContrast(value: Float, contrast: Float): Float {
        // contrast: 0=无对比度(灰), 1=正常, >1=增强
        return ((value - 0.5f) * contrast + 0.5f).coerceIn(0f, 1f)
    }

    // ── 辅助方法 ──────────────────────────────────────────────────────

    private fun getLayerPixels(layerBitmap: Bitmap, targetW: Int, targetH: Int): IntArray {
        val bmp = if (layerBitmap.width != targetW || layerBitmap.height != targetH) {
            try {
                Bitmap.createScaledBitmap(layerBitmap, targetW, targetH, true)
            } catch (e: OutOfMemoryError) {
                Log.e("LayerBlender", "OOM scaling layer bitmap to ${targetW}x${targetH}", e)
                layerBitmap
            }
        } else {
            layerBitmap
        }
        return getPixels(bmp)
    }

    private fun getPixels(bitmap: Bitmap): IntArray {
        val w = bitmap.width
        val h = bitmap.height
        // 2026 hotfix: 防御 w*h 整数溢出
        val pixelCount = w.toLong() * h.toLong()
        if (pixelCount > Int.MAX_VALUE.toLong()) {
            Log.e("LayerBlender", "getPixels: bitmap too large ${w}x$h")
            return IntArray(0)
        }
        val pixels = IntArray(pixelCount.toInt())
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        return pixels
    }

    /**
     * 从遮罩 Bitmap 获取灰度值数组。
     * 如果遮罩尺寸不匹配，自动缩放。
     * 返回 IntArray，每个值为 0~255 的灰度。
     */
    private fun getMaskPixels(maskBitmap: Bitmap, targetW: Int, targetH: Int): IntArray {
        val bmp = if (maskBitmap.width != targetW || maskBitmap.height != targetH) {
            try {
                Bitmap.createScaledBitmap(maskBitmap, targetW, targetH, true)
            } catch (e: OutOfMemoryError) {
                Log.e("LayerBlender", "OOM scaling mask bitmap to ${targetW}x${targetH}", e)
                maskBitmap
            } catch (e: IllegalArgumentException) {
                Log.e("LayerBlender", "IllegalArgument scaling mask bitmap", e)
                maskBitmap
            }
        } else {
            maskBitmap
        }

        // 2026 hotfix: 防御 targetW*targetH 整数溢出
        val maskPixelCount = targetW.toLong() * targetH.toLong()
        if (maskPixelCount > Int.MAX_VALUE.toLong()) {
            Log.e("LayerBlender", "getMaskPixels: too large ${targetW}x$targetH")
            return IntArray(0)
        }
        val pixels = IntArray(maskPixelCount.toInt())
        bmp.getPixels(pixels, 0, targetW, 0, 0, targetW, targetH)

        // 将 ARGB 转为灰度（使用绿色通道或亮度公式）
        return IntArray(pixels.size) { i ->
            val px = pixels[i]
            // 使用亮度公式或直接取绿色通道（遮罩通常是灰度图）
            val r = (px shr 16) and 0xFF
            val g = (px shr 8) and 0xFF
            val b = px and 0xFF
            (0.299 * r + 0.587 * g + 0.114 * b).toInt().coerceIn(0, 255)
        }
    }

    private fun createWhiteBitmap(w: Int, h: Int): Bitmap {
        val bmp = try {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        } catch (_: OutOfMemoryError) {
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }
        // 2026 hotfix: 防御 w*h 整数溢出
        val pixelCount = w.toLong() * h.toLong()
        if (pixelCount > Int.MAX_VALUE.toLong()) {
            Log.e("LayerBlender", "createWhiteBitmap: dimensions too large ${w}x$h")
            return bmp
        }
        val pixels = IntArray(pixelCount.toInt()) { 0xFFFFFFFF.toInt() }
        bmp.setPixels(pixels, 0, w, 0, 0, w, h)
        return bmp
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    private fun Float.toByte8(): Int = (this * 255f).toInt().coerceIn(0, 255)

    private fun sqrtSafe(v: Float): Float {
        return if (v > 0f) {
            val d = v.toDouble()
            Math.sqrt(d).toFloat()
        } else 0f
    }

    // ── HSL 颜色空间辅助 ──────────────────────────────────────────────

    private fun luminance(r: Float, g: Float, b: Float): Float = 0.299f * r + 0.587f * g + 0.114f * b

    private fun hueFromRgb(r: Float, g: Float, b: Float): Float {
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        if (max == min) return 0f
        val delta = max - min
        val hue = when (max) {
            r -> ((g - b) / delta) % 6f
            g -> (b - r) / delta + 2f
            else -> (r - g) / delta + 4f
        }
        return ((hue * 60f) + 360f) % 360f
    }

    private fun hslFromRgb(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val l = (max + min) / 2f
        if (max == min) return Triple(0f, 0f, l)
        val delta = max - min
        val s = if (l > 0.5f) delta / (2f - max - min) else delta / (max + min)
        val h = hueFromRgb(r, g, b)
        return Triple(h, s, l)
    }

    private fun hslToRgb(h: Float, s: Float, l: Float): Triple<Float, Float, Float> {
        if (s < 0.001f) return Triple(l, l, l)
        val c = (1f - abs(2f * l - 1f)) * s
        val x = c * (1f - abs((h / 60f) % 2f - 1f))
        val m = l - c / 2f
        val (r1, g1, b1) = when {
            h < 60f -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return Triple(r1 + m, g1 + m, b1 + m)
    }
}

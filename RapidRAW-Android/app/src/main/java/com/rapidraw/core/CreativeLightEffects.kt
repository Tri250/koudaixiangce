package com.rapidraw.core

import android.graphics.Bitmap
import android.util.Log
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 创意光效处理器 — 移植自 RapidRAW 桌面版的创意光效模块。
 *
 * 支持三种独立可控的光效：
 * - Glow（辉光）：基于亮度阈值的辉光扩散，带暖色偏移和屏幕混合
 * - Halation（光晕）：模拟胶片卤化银光晕，红色核心+橙色边缘
 * - Lens Flare（镜头光晕）：包含鬼影、星芒、变形拉伸和色散效果
 *
 * 高斯模糊采用三遍盒状模糊近似（separable），O(n) 每像素性能，
 * 支持大半径高效运算。
 */
class CreativeLightEffects {
    companion object {
        private const val TAG = "CreativeLightEffects"
    }

    data class GlowParams(
        val amount: Float = 0f,
        val radius: Float = 30f,
        val brightnessThreshold: Float = 0.7f,
        val warmShift: Float = 0.03f,
        val highlightProtection: Float = 0.8f
    )

    data class HalationParams(
        val amount: Float = 0f,
        val radius: Float = 20f,
        val coreColor: FloatArray = floatArrayOf(1f, 0.15f, 0.03f),
        val edgeColor: FloatArray = floatArrayOf(1f, 0.32f, 0.10f),
        val brightnessAdapt: Float = 0.5f,
        val desaturation: Float = 0.3f
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is HalationParams) return false
            return amount == other.amount &&
                    radius == other.radius &&
                    coreColor.contentEquals(other.coreColor) &&
                    edgeColor.contentEquals(other.edgeColor) &&
                    brightnessAdapt == other.brightnessAdapt &&
                    desaturation == other.desaturation
        }

        override fun hashCode(): Int {
            var result = amount.hashCode()
            result = 31 * result + radius.hashCode()
            result = 31 * result + coreColor.contentHashCode()
            result = 31 * result + edgeColor.contentHashCode()
            result = 31 * result + brightnessAdapt.hashCode()
            result = 31 * result + desaturation.hashCode()
            return result
        }
    }

    data class LensFlareParams(
        val amount: Float = 0f,
        val lightX: Float = 0.3f,
        val lightY: Float = 0.2f,
        val ghostCount: Int = 6,
        val streakCount: Int = 6,
        val streakLength: Float = 0.3f,
        val chromaticOffset: Float = 0.02f
    )

    data class Params(
        val glow: GlowParams = GlowParams(),
        val halation: HalationParams = HalationParams(),
        val flare: LensFlareParams = LensFlareParams()
    )

    /**
     * 对 Bitmap 应用所有启用的创意光效。
     * 各效果独立计算，依次叠加到原图上。
     */
    fun apply(bitmap: Bitmap, params: Params): Bitmap {
        var result = bitmap.copy(Bitmap.Config.ARGB_8888, true)

        if (params.glow.amount > 0f) {
            result = applyGlow(result, params.glow)
        }
        if (params.halation.amount > 0f) {
            result = applyHalation(result, params.halation)
        }
        if (params.flare.amount > 0f) {
            result = applyLensFlare(result, params.flare)
        }

        return result
    }

    // ══════════════════════════════════════════════════════════════════
    // Glow（辉光）
    // ══════════════════════════════════════════════════════════════════

    /**
     * Glow 算法：
     * 1. 提取高亮像素：计算亮度，创建亮度 > brightnessThreshold 的遮罩
     * 2. 对高亮像素层应用大半径高斯模糊（separable 水平+垂直）
     * 3. 对模糊层应用暖色偏移：R *= (1 + warmShift), B *= (1 - warmShift)
     * 4. Screen 混合辉光层与原图：result = 1 - (1 - base) * (1 - glow)
     * 5. 高光保护：原图亮度 > highlightProtection 处，降低辉光贡献
     */
    private fun applyGlow(bitmap: Bitmap, params: GlowParams): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        // 解码为 float [0,1] RGB
        val r = FloatArray(w * h)
        val g = FloatArray(w * h)
        val b = FloatArray(w * h)
        val luma = FloatArray(w * h)

        for (i in pixels.indices) {
            val px = pixels[i]
            r[i] = ((px shr 16) and 0xFF) / 255f
            g[i] = ((px shr 8) and 0xFF) / 255f
            b[i] = (px and 0xFF) / 255f
            luma[i] = luminance(r[i], g[i], b[i])
        }

        // Step 1: 提取亮度 > threshold 的像素
        val glowR = FloatArray(w * h)
        val glowG = FloatArray(w * h)
        val glowB = FloatArray(w * h)

        for (i in pixels.indices) {
            if (luma[i] > params.brightnessThreshold) {
                // 超过阈值的部分，按超出量加权
                val weight = (luma[i] - params.brightnessThreshold) /
                        (1f - params.brightnessThreshold).coerceAtLeast(0.01f)
                glowR[i] = r[i] * weight
                glowG[i] = g[i] * weight
                glowB[i] = b[i] * weight
            }
            // else: 0f (already initialized)
        }

        // Step 2: 高斯模糊辉光层
        val blurRadius = params.radius.coerceAtLeast(1f)
        val blurredR = gaussianBlur(glowR, w, h, blurRadius)
        val blurredG = gaussianBlur(glowG, w, h, blurRadius)
        val blurredB = gaussianBlur(glowB, w, h, blurRadius)

        // Step 3: 暖色偏移
        for (i in blurredR.indices) {
            blurredR[i] *= (1f + params.warmShift)
            blurredG[i] *= 1f // 绿色不变
            blurredB[i] *= (1f - params.warmShift)
        }

        // Step 4+5: Screen 混合 + 高光保护
        val result = IntArray(w * h)
        val amount = params.amount

        for (i in pixels.indices) {
            // Screen blend: result = 1 - (1 - base) * (1 - glow)
            var screenR = 1f - (1f - r[i]) * (1f - blurredR[i])
            var screenG = 1f - (1f - g[i]) * (1f - blurredG[i])
            var screenB = 1f - (1f - b[i]) * (1f - blurredB[i])

            // 高光保护：原图亮度超过阈值时，降低辉光贡献
            if (luma[i] > params.highlightProtection) {
                val overBright = (luma[i] - params.highlightProtection) /
                        (1f - params.highlightProtection).coerceAtLeast(0.01f)
                val protection = 1f - overBright.coerceIn(0f, 1f) * 0.7f
                screenR = r[i] + (screenR - r[i]) * protection
                screenG = g[i] + (screenG - g[i]) * protection
                screenB = b[i] + (screenB - b[i]) * protection
            }

            // 按 amount 混合
            val outR = (r[i] + (screenR - r[i]) * amount).coerceIn(0f, 1f)
            val outG = (g[i] + (screenG - g[i]) * amount).coerceIn(0f, 1f)
            val outB = (b[i] + (screenB - b[i]) * amount).coerceIn(0f, 1f)

            result[i] = (0xFF shl 24) or
                    ((outR * 255f).toInt().coerceIn(0, 255) shl 16) or
                    ((outG * 255f).toInt().coerceIn(0, 255) shl 8) or
                    ((outB * 255f).toInt().coerceIn(0, 255))
        }

        val out = try {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        } catch (oom: OutOfMemoryError) {
            Log.e(TAG, "OOM creating light effect output", oom)
            return source
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "IllegalArgument creating light effect output", e)
            return source
        }
        out.setPixels(result, 0, w, 0, 0, w, h)
        return out
    }

    // ══════════════════════════════════════════════════════════════════
    // Halation（光晕）
    // ══════════════════════════════════════════════════════════════════

    /**
     * Halation 算法：
     * 1. 计算每个像素的亮度
     * 2. 创建光晕层：对高亮像素（>0.5）应用红色核心色加权；对周围像素应用橙色边缘色
     * 3. 对光晕层应用大半径高斯模糊
     * 4. 按 amount * brightnessAdapt * pixelLuminance 缩放光晕层
     * 5. 对光晕层去饱和
     * 6. 加法混合到原图
     */
    private fun applyHalation(bitmap: Bitmap, params: HalationParams): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val r = FloatArray(w * h)
        val g = FloatArray(w * h)
        val b = FloatArray(w * h)
        val luma = FloatArray(w * h)

        for (i in pixels.indices) {
            val px = pixels[i]
            r[i] = ((px shr 16) and 0xFF) / 255f
            g[i] = ((px shr 8) and 0xFF) / 255f
            b[i] = (px and 0xFF) / 255f
            luma[i] = luminance(r[i], g[i], b[i])
        }

        // Step 2: 创建光晕层
        val haloR = FloatArray(w * h)
        val haloG = FloatArray(w * h)
        val haloB = FloatArray(w * h)

        for (i in pixels.indices) {
            val L = luma[i]
            if (L > 0.5f) {
                // 高亮像素：应用核心色（红色），按亮度加权
                val weight = (L - 0.5f) / 0.5f
                haloR[i] = params.coreColor[0] * L * weight
                haloG[i] = params.coreColor[1] * L * weight
                haloB[i] = params.coreColor[2] * L * weight
            } else if (L > 0.15f) {
                // 中间像素：应用边缘色（橙色），按距离加权
                val weight = (L - 0.15f) / 0.35f
                haloR[i] = params.edgeColor[0] * L * weight * 0.5f
                haloG[i] = params.edgeColor[1] * L * weight * 0.5f
                haloB[i] = params.edgeColor[2] * L * weight * 0.5f
            }
            // 暗像素光晕层保持为0
        }

        // Step 3: 高斯模糊光晕层
        val blurRadius = params.radius.coerceAtLeast(1f)
        val blurredR = gaussianBlur(haloR, w, h, blurRadius)
        val blurredG = gaussianBlur(haloG, w, h, blurRadius)
        val blurredB = gaussianBlur(haloB, w, h, blurRadius)

        // Step 4+5: 缩放 + 去饱和
        val amount = params.amount
        val brightnessAdapt = params.brightnessAdapt
        val desat = params.desaturation

        for (i in blurredR.indices) {
            // 按 amount * brightnessAdapt * 亮度 缩放
            val scale = amount * (brightnessAdapt + (1f - brightnessAdapt) * luma[i])
            blurredR[i] *= scale
            blurredG[i] *= scale
            blurredB[i] *= scale

            // 去饱和：向灰度值混合
            if (desat > 0f) {
                val gray = luminance(blurredR[i], blurredG[i], blurredB[i])
                blurredR[i] = blurredR[i] + (gray - blurredR[i]) * desat
                blurredG[i] = blurredG[i] + (gray - blurredG[i]) * desat
                blurredB[i] = blurredB[i] + (gray - blurredB[i]) * desat
            }
        }

        // Step 6: 加法混合
        val result = IntArray(w * h)
        for (i in pixels.indices) {
            val outR = (r[i] + blurredR[i]).coerceIn(0f, 1f)
            val outG = (g[i] + blurredG[i]).coerceIn(0f, 1f)
            val outB = (b[i] + blurredB[i]).coerceIn(0f, 1f)

            result[i] = (0xFF shl 24) or
                    ((outR * 255f).toInt().coerceIn(0, 255) shl 16) or
                    ((outG * 255f).toInt().coerceIn(0, 255) shl 8) or
                    ((outB * 255f).toInt().coerceIn(0, 255))
        }

        val out = try {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        } catch (oom: OutOfMemoryError) {
            Log.e(TAG, "OOM creating light effect output", oom)
            return source
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "IllegalArgument creating light effect output", e)
            return source
        }
        out.setPixels(result, 0, w, 0, 0, w, h)
        return out
    }

    // ══════════════════════════════════════════════════════════════════
    // Lens Flare（镜头光晕）
    // ══════════════════════════════════════════════════════════════════

    /**
     * Lens Flare 算法：
     * 1. 在 (lightX, lightY) 位置创建光源
     * 2. 生成鬼影元素：沿光源→图像中心线插值位置，大小变化，带色散
     * 3. 生成星芒：从光源径向辐射，高斯衰减，交替色散颜色
     * 4. 应用变形拉伸（水平方向 0.5x 挤压，电影感）
     * 5. 加法混合到原图
     */
    private fun applyLensFlare(bitmap: Bitmap, params: LensFlareParams): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        // 光源位置（像素坐标）
        val lx = params.lightX * w
        val ly = params.lightY * h

        // 图像中心
        val cx = w * 0.5f
        val cy = h * 0.5f

        // 方向向量（光源→中心）
        val dx = cx - lx
        val dy = cy - ly

        // 变形拉伸比：水平 0.5x
        val anamorphicSqueeze = 0.5f

        // 初始化 flare 层（在拉伸空间中计算，尺寸与原图一致）
        val flareR = FloatArray(w * h)
        val flareG = FloatArray(w * h)
        val flareB = FloatArray(w * h)

        // ── 光源核心光斑 ──
        val coreRadius = min(w, h) * 0.03f
        for (py in 0 until h) {
            for (px in 0 until w) {
                // 应用变形拉伸：将像素坐标映射到拉伸空间
                val sx = (px - lx) * anamorphicSqueeze + lx
                val sy = (py - ly).toFloat() + ly

                val distSq = (sx - lx) * (sx - lx) + (sy - ly) * (sy - ly)
                val coreR = coreRadius * coreRadius

                if (distSq < coreR * 16f) {
                    val dist = sqrt(distSq)
                    // 高斯衰减核心
                    val intensity = exp(-distSq / (2f * coreR)).coerceIn(0f, 1f)
                    val idx = py * w + px
                    flareR[idx] += intensity
                    flareG[idx] += intensity * 0.95f
                    flareB[idx] += intensity * 0.85f
                }
            }
        }

        // ── 鬼影元素 ──
        for (ghostIdx in 0 until params.ghostCount) {
            // 沿光源→中心线插值，使用非线性分布
            val t = (ghostIdx + 1).toFloat() / (params.ghostCount + 1)
            // 使用幂函数让鬼影更靠近中心分布
            val ratio = 0.2f + 0.8f * t

            // 鬼影位置
            val ghostX = lx + dx * ratio
            val ghostY = ly + dy * ratio

            // 鬼影大小：靠近中心更大
            val baseSize = min(w, h) * 0.02f
            val ghostSize = baseSize * (1f + 1.5f * (1f - t))

            // 色散：每个鬼影略微不同的颜色偏移
            val chromaAngle = ghostIdx.toFloat() / params.ghostCount * 2f * Math.PI.toFloat()
            val crOff = cos(chromaAngle) * params.chromaticOffset
            val cgOff = sin(chromaAngle + 2.094f) * params.chromaticOffset * 0.5f
            val cbOff = cos(chromaAngle + 4.189f) * params.chromaticOffset

            // 鬼影衰减：越远越暗
            val ghostIntensity = 0.4f * (1f - t * 0.5f)

            val ghostSizeSq = ghostSize * ghostSize

            // 计算鬼影影响范围（优化：只处理鬼影附近的像素）
            val range = (ghostSize * 4f / anamorphicSqueeze).toInt().coerceAtMost(w)
            val rangeY = (ghostSize * 4f).toInt().coerceAtMost(h)
            val startX = (ghostX - range / anamorphicSqueeze).toInt().coerceIn(0, w - 1)
            val endX = (ghostX + range / anamorphicSqueeze).toInt().coerceIn(0, w - 1)
            val startY = (ghostY - rangeY).toInt().coerceIn(0, h - 1)
            val endY = (ghostY + rangeY).toInt().coerceIn(0, h - 1)

            for (py in startY..endY) {
                for (px in startX..endX) {
                    // 变形拉伸映射
                    val sx = (px - ghostX) * anamorphicSqueeze + ghostX
                    val sy = (py - ghostY).toFloat() + ghostY

                    val dSq = (sx - ghostX) * (sx - ghostX) + (sy - ghostY) * (sy - ghostY)

                    if (dSq < ghostSizeSq * 16f) {
                        // 椭圆形鬼影（变形拉伸后是圆，原始空间是椭圆）
                        val normalizedDist = sqrt(dSq) / ghostSize
                        // 柔和边缘圆形
                        val falloff = if (normalizedDist < 1f) {
                            1f - normalizedDist * normalizedDist * (3f - 2f * normalizedDist)
                        } else {
                            0f
                        }

                        if (falloff > 0f) {
                            val idx = py * w + px
                            val intensity = falloff * ghostIntensity
                            flareR[idx] += intensity * (1f + crOff)
                            flareG[idx] += intensity * (1f + cgOff)
                            flareB[idx] += intensity * (1f + cbOff)
                        }
                    }
                }
            }
        }

        // ── 星芒（Streaks）──
        val streakLen = params.streakLength * max(w, h)
        for (streakIdx in 0 until params.streakCount) {
            val angle = streakIdx.toFloat() / params.streakCount * Math.PI.toFloat()
            val dirX = cos(angle)
            val dirY = sin(angle)

            // 交替色散颜色
            val isEvenStreak = streakIdx % 2 == 0
            val sR = if (isEvenStreak) 1f else 0.8f + cos(angle) * params.chromaticOffset * 5f
            val sG = 0.9f
            val sB = if (isEvenStreak) 0.8f else 1f + sin(angle) * params.chromaticOffset * 5f

            // 星芒宽度（高斯 sigma）
            val streakWidth = 2f

            // 沿星芒线采样
            val steps = (streakLen * 2f).toInt().coerceAtLeast(2)
            for (step in 0..steps) {
                val t = step.toFloat() / steps
                // 从光源沿两个方向延伸
                for (direction in -1..1 step 2) {
                    val sampleX = lx + dirX * streakLen * t * direction
                    val sampleY = ly + dirY * streakLen * t * direction

                    // 高斯衰减
                    val falloff = exp(-t * t * 3f)
                    if (falloff < 0.01f) continue

                    // 在垂直于星芒方向上绘制
                    val perpX = -dirY
                    val perpY = dirX
                    val halfWidth = (streakWidth * 3f).toInt()

                    for (offset in -halfWidth..halfWidth) {
                        val px = (sampleX + perpX * offset).toInt()
                        val py = (sampleY + perpY * offset).toInt()

                        if (px < 0 || px >= w || py < 0 || py >= h) continue

                        // 变形拉伸映射
                        val sx = (px - lx) * anamorphicSqueeze + lx
                        val sy = (py - ly).toFloat() + ly

                        // 距离采样线的距离
                        val distToLine = abs((sx - sampleX) * perpX + (sy - sampleY) * perpY)
                        val lineFalloff = exp(-distToLine * distToLine / (2f * streakWidth * streakWidth))

                        val intensity = falloff * lineFalloff
                        if (intensity < 0.001f) continue

                        val idx = py * w + px
                        flareR[idx] += intensity * sR * 0.3f
                        flareG[idx] += intensity * sG * 0.3f
                        flareB[idx] += intensity * sB * 0.3f
                    }
                }
            }
        }

        // ── 加法混合 ──
        val result = IntArray(w * h)
        val amount = params.amount

        for (i in pixels.indices) {
            val px = pixels[i]
            val origR = ((px shr 16) and 0xFF) / 255f
            val origG = ((px shr 8) and 0xFF) / 255f
            val origB = (px and 0xFF) / 255f

            val outR = (origR + flareR[i] * amount).coerceIn(0f, 1f)
            val outG = (origG + flareG[i] * amount).coerceIn(0f, 1f)
            val outB = (origB + flareB[i] * amount).coerceIn(0f, 1f)

            result[i] = (0xFF shl 24) or
                    ((outR * 255f).toInt().coerceIn(0, 255) shl 16) or
                    ((outG * 255f).toInt().coerceIn(0, 255) shl 8) or
                    ((outB * 255f).toInt().coerceIn(0, 255))
        }

        val out = try {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        } catch (oom: OutOfMemoryError) {
            Log.e(TAG, "OOM creating light effect output", oom)
            return source
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "IllegalArgument creating light effect output", e)
            return source
        }
        out.setPixels(result, 0, w, 0, 0, w, h)
        return out
    }

    // ══════════════════════════════════════════════════════════════════
    // 高斯模糊 — 三遍盒状模糊近似 (Separable IIR)
    // ══════════════════════════════════════════════════════════════════

    /**
     * 使用三遍盒状模糊近似高斯模糊。
     *
     * 原理：对信号进行三次均值滤波的卷积等价于一个近似高斯核
     * （中心极限定理）。每遍是 O(n) 的 running average，
     * 三遍后等价核接近真正的高斯分布。
     *
     * 实现为 separable（水平 + 垂直），总复杂度 O(w*h*passes)。
     *
     * @param data 输入 float 数组 (w*h)
     * @param w 宽度
     * @param h 高度
     * @param sigma 高斯 sigma（标准差），radius 通常 ≈ 3*sigma
     * @return 模糊后的 float 数组
     */
    private fun gaussianBlur(data: FloatArray, w: Int, h: Int, sigma: Float): FloatArray {
        if (sigma < 0.5f) return data.copyOf()

        // 三遍盒状模糊的等效半径
        // 对于 3-pass box blur，等价 sigma = sqrt((boxRadius^2 * 3) / 3)
        // 即 boxRadius ≈ sigma * sqrt(3 / 3) = sigma
        // 但更精确的公式：boxRadius = sqrt(sigma^2 * 12 / 3) / 2 ≈ sigma
        val boxRadius = computeBoxRadius(sigma, 3)

        var current = data.copyOf()
        // 三遍盒状模糊：每遍水平+垂直
        for (pass in 0 until 3) {
            val r = boxRadius[pass]
            current = boxBlurSeparable(current, w, h, r)
        }

        return current
    }

    /**
     * 计算三遍盒状模糊的每遍半径。
     *
     * 使用 B. Weiss 的公式：
     * 给定目标高斯 sigma 和遍数 n，计算每遍的盒半径，
     * 使得 n 遍盒状模糊的卷积等价于目标 sigma 的高斯模糊。
     */
    private fun computeBoxRadius(sigma: Float, passes: Int): IntArray {
        val n = passes.toFloat()
        // 等效方差公式: σ² = n * (w² / 12)，其中 w = 2*boxRadius + 1
        // 需要分配总方差 σ² 到 n 遍
        val wIdeal = sqrt(12f * sigma * sigma / n + 1f)
        var wl = ((wIdeal - 1f) / 2f).toInt()
        if (wl % 2 == 0) wl--
        val wu = wl + 2

        val mIdeal = (12f * sigma * sigma - n * wl * wl - 4f * n * wl - 3f * n) /
                (-4f * wl - 4f)
        val m = mIdeal.toInt().coerceIn(0, passes)

        val radii = IntArray(passes)
        for (i in 0 until passes) {
            radii[i] = if (i < m) wl else wu
            radii[i] = radii[i].coerceAtLeast(1)
        }
        return radii
    }

    /**
     * Separable 盒状模糊：先水平后垂直。
     * 每个方向使用 running average（滑动窗口求和），O(n) 每像素。
     */
    private fun boxBlurSeparable(data: FloatArray, w: Int, h: Int, radius: Int): FloatArray {
        val temp = FloatArray(w * h)
        val output = FloatArray(w * h)

        // 水平方向
        boxBlurH(data, temp, w, h, radius)
        // 垂直方向
        boxBlurV(temp, output, w, h, radius)

        return output
    }

    /**
     * 水平方向盒状模糊。
     * 使用 running average：维护窗口内的累加和，每次前进减去左端加上右端。
     */
    private fun boxBlurH(src: FloatArray, dst: FloatArray, w: Int, h: Int, r: Int) {
        val iarr = 1f / (2 * r + 1)

        for (y in 0 until h) {
            var sum = 0f

            // 初始化窗口：[0, r] 范围，左侧用边界值扩展
            for (x in -r..r) {
                val cx = x.coerceIn(0, w - 1)
                sum += src[y * w + cx]
            }

            // 计算第一个像素
            dst[y * w] = sum * iarr

            // 滑动窗口向右移动
            for (x in 1 until w) {
                // 新加入右端
                val addIdx = (x + r).coerceIn(0, w - 1)
                // 移出左端
                val subIdx = (x - r - 1).coerceIn(0, w - 1)
                sum += src[y * w + addIdx] - src[y * w + subIdx]
                dst[y * w + x] = sum * iarr
            }
        }
    }

    /**
     * 垂直方向盒状模糊。
     * 同样使用 running average。
     */
    private fun boxBlurV(src: FloatArray, dst: FloatArray, w: Int, h: Int, r: Int) {
        val iarr = 1f / (2 * r + 1)

        for (x in 0 until w) {
            var sum = 0f

            // 初始化窗口
            for (y in -r..r) {
                val cy = y.coerceIn(0, h - 1)
                sum += src[cy * w + x]
            }

            dst[x] = sum * iarr

            // 滑动窗口向下移动
            for (y in 1 until h) {
                val addIdx = (y + r).coerceIn(0, h - 1)
                val subIdx = (y - r - 1).coerceIn(0, h - 1)
                sum += src[addIdx * w + x] - src[subIdx * w + x]
                dst[y * w + x] = sum * iarr
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 工具方法
    // ══════════════════════════════════════════════════════════════════

    /** BT.709 亮度 */
    private fun luminance(r: Float, g: Float, b: Float): Float {
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }
}

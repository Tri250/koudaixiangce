package com.rapidraw.core

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 高光重建器：基于 RawTherapee hilite_recon 算法思想的多阶段裁剪高光重建。
 *
 * 工作于 Float32 线性 RGB 数据（值可超过 1.0 / HDR）。
 * 输入/输出均为扁平 FloatArray，每像素 3 个连续 float (R, G, B)。
 *
 * 算法四阶段：
 * 1. 裁剪检测 — 按通道标记超过阈值的像素
 * 2. 色度统计 — 对每个裁剪区域，计算周围未裁剪像素的平均色度
 * 3. 梯度传播 — 利用未裁剪通道的梯度信息重建裁剪通道
 * 4. 边界混合 — 基于距离的权重函数在裁剪区域边界做平滑过渡
 */
class HighlightReconstructor {

    enum class Method {
        CLIP,           // 简单裁剪（无重建）
        RECONSTRUCT,    // 完整梯度重建
        COLOR_BLEND     // 与周围颜色混合
    }

    data class Params(
        val method: Method = Method.RECONSTRUCT,
        val threshold: Float = 0.995f,    // 裁剪阈值（线性空间 0..1）
        val blendRadius: Int = 3,          // 边界混合半径
        val chromaWeight: Float = 0.7f,    // 来自邻域的色度权重
        val level: Int = 3                 // 重建迭代级别 (1-5)
    )

    // ── Per-pixel clipping state ────────────────────────────────────

    private class ClipState(
        val clippedR: BooleanArray,
        val clippedG: BooleanArray,
        val clippedB: BooleanArray,
        val anyClipped: BooleanArray,
        val allClipped: BooleanArray,
        val numClipped: IntArray          // 0..3 per pixel
    )

    // ── Public API ──────────────────────────────────────────────────

    /**
     * 对 Float32 线性 RGB 数据执行高光重建。
     *
     * @param data  扁平 FloatArray，每像素 3 个 float (R, G, B)
     * @param width 图像宽度
     * @param height 图像高度
     * @param params 重建参数
     * @return 重建后的 FloatArray（与输入同尺寸）
     */
    fun reconstruct(data: FloatArray, width: Int, height: Int, params: Params): FloatArray {
        if (data.size != width * height * 3) {
            throw IllegalArgumentException("Data size ${data.size} does not match ${width}x${height}x3")
        }
        val level = params.level.coerceIn(1, 5)

        return when (params.method) {
            Method.CLIP -> reconstructClip(data, width, height, params)
            Method.RECONSTRUCT -> reconstructFull(data, width, height, params, level)
            Method.COLOR_BLEND -> reconstructColorBlend(data, width, height, params, level)
        }
    }

    /**
     * Convenience method that reconstructs from a Bitmap and returns a FloatArray.
     */
    fun reconstruct(bitmap: android.graphics.Bitmap, params: Params = Params()): FloatArray {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val data = FloatArray(width * height * 3)
        for (i in pixels.indices) {
            val color = pixels[i]
            data[i * 3] = ((color shr 16) and 0xFF) / 255f
            data[i * 3 + 1] = ((color shr 8) and 0xFF) / 255f
            data[i * 3 + 2] = (color and 0xFF) / 255f
        }
        return reconstruct(data, width, height, params)
    }

    // ── CLIP method ─────────────────────────────────────────────────

    private fun reconstructClip(
        data: FloatArray, w: Int, h: Int, params: Params
    ): FloatArray {
        val out = data.copyOf()
        val maxVal = findMaxValue(data)
        val thresh = maxVal * params.threshold

        for (i in 0 until w * h) {
            val base = i * 3
            out[base]     = min(out[base],     thresh)
            out[base + 1] = min(out[base + 1], thresh)
            out[base + 2] = min(out[base + 2], thresh)
        }
        return out
    }

    // ── Full gradient-based reconstruction ──────────────────────────

    private fun reconstructFull(
        data: FloatArray, w: Int, h: Int, params: Params, level: Int
    ): FloatArray {
        val pixelCount = w * h
        val maxVal = findMaxValue(data)
        val thresh = maxVal * params.threshold

        // Stage 1: Build per-channel clipping mask
        val clip = buildClipMask(data, pixelCount, thresh)

        // Working buffer — start from original data
        val work = data.copyOf()

        // Stage 2–3: Iterative multi-pass reconstruction
        for (pass in 0 until level) {
            val passFactor = 1f - pass * 0.15f  // 渐进衰减
            reconstructPass(work, w, h, clip, params, passFactor, thresh)
        }

        // Stage 4: Boundary blending
        val result = applyBoundaryBlend(work, data, w, h, clip, params)

        return result
    }

    // ── Color blend reconstruction ──────────────────────────────────

    private fun reconstructColorBlend(
        data: FloatArray, w: Int, h: Int, params: Params, level: Int
    ): FloatArray {
        val pixelCount = w * h
        val maxVal = findMaxValue(data)
        val thresh = maxVal * params.threshold

        val clip = buildClipMask(data, pixelCount, thresh)
        val work = data.copyOf()

        // Compute neighborhood chroma statistics for clipped regions
        val chromaRef = computeNeighborChroma(data, w, h, clip, params)

        // Reconstruct using chroma blending
        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                if (!clip.anyClipped[idx]) continue

                val base = idx * 3
                val nc = clip.numClipped[idx]

                if (nc < 3) {
                    // Partial clip — use chroma correlation from unclipped channels
                    reconstructPartialClip(work, base, clip, idx, chromaRef, params, thresh)
                } else {
                    // Full clip — use neighbor chroma
                    reconstructFullClip(work, base, idx, chromaRef, w, h, params, thresh)
                }
            }
        }

        // Iterative refinement passes
        for (pass in 1 until level) {
            val clipAfter = buildClipMask(work, pixelCount, thresh)
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val idx = y * w + x
                    if (!clipAfter.anyClipped[idx]) continue
                    val base = idx * 3
                    val nc = clipAfter.numClipped[idx]
                    if (nc < 3) {
                        reconstructPartialClip(work, base, clipAfter, idx, chromaRef, params, thresh)
                    } else {
                        reconstructFullClip(work, base, idx, chromaRef, w, h, params, thresh)
                    }
                }
            }
        }

        return applyBoundaryBlend(work, data, w, h, clip, params)
    }

    // ── Stage 1: Clipping Detection ────────────────────────────────

    private fun buildClipMask(data: FloatArray, pixelCount: Int, thresh: Float): ClipState {
        val clippedR = BooleanArray(pixelCount)
        val clippedG = BooleanArray(pixelCount)
        val clippedB = BooleanArray(pixelCount)
        val anyClipped = BooleanArray(pixelCount)
        val allClipped = BooleanArray(pixelCount)
        val numClipped = IntArray(pixelCount)

        for (i in 0 until pixelCount) {
            val base = i * 3
            val cR = data[base]     > thresh
            val cG = data[base + 1] > thresh
            val cB = data[base + 2] > thresh

            clippedR[i] = cR
            clippedG[i] = cG
            clippedB[i] = cB

            val nc = (if (cR) 1 else 0) + (if (cG) 1 else 0) + (if (cB) 1 else 0)
            numClipped[i] = nc
            anyClipped[i] = nc > 0
            allClipped[i] = nc == 3
        }

        return ClipState(clippedR, clippedG, clippedB, anyClipped, allClipped, numClipped)
    }

    // ── Stage 2: Chroma Statistics ──────────────────────────────────

    /**
     * 对每个裁剪像素，计算周围未裁剪像素的加权平均色度 (r/g, b/g 比率)。
     * 返回 FloatArray[pixelCount * 2]: [rRatio, bRatio]
     */
    private fun computeNeighborChroma(
        data: FloatArray, w: Int, h: Int, clip: ClipState, params: Params
    ): FloatArray {
        val chromaRef = FloatArray(w * h * 2)
        val searchRadius = max(params.blendRadius, 5)

        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                if (!clip.anyClipped[idx]) continue

                var sumRG = 0f
                var sumBG = 0f
                var totalWeight = 0f

                // 在搜索半径内寻找未裁剪像素
                for (dy in -searchRadius..searchRadius) {
                    for (dx in -searchRadius..searchRadius) {
                        if (dx == 0 && dy == 0) continue
                        val nx = x + dx
                        val ny = y + dy
                        if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue

                        val nIdx = ny * w + nx
                        if (clip.anyClipped[nIdx]) continue

                        val nBase = nIdx * 3
                        val nR = data[nBase]
                        val nG = data[nBase + 1]
                        val nB = data[nBase + 2]

                        val dist = sqrt((dx * dx + dy * dy).toFloat())
                        val weight = exp(-dist * dist / (2f * searchRadius * searchRadius / 4f))

                        // 计算 r/g 和 b/g 色度比率（用绿色作参考）
                        if (nG > 1e-6f) {
                            sumRG += (nR / nG) * weight
                            sumBG += (nB / nG) * weight
                        } else {
                            // 绿色接近零时使用亮度加权的近似
                            val luma = 0.2126f * nR + 0.7152f * nG + 0.0722f * nB
                            if (luma > 1e-6f) {
                                sumRG += (nR / luma) * weight
                                sumBG += (nB / luma) * weight
                            }
                        }
                        totalWeight += weight
                    }
                }

                val base2 = idx * 2
                if (totalWeight > 1e-6f) {
                    chromaRef[base2]     = sumRG / totalWeight
                    chromaRef[base2 + 1] = sumBG / totalWeight
                } else {
                    // 无参考时使用中性色度
                    chromaRef[base2]     = 1f
                    chromaRef[base2 + 1] = 1f
                }
            }
        }

        return chromaRef
    }

    // ── Single reconstruction pass ──────────────────────────────────

    private fun reconstructPass(
        work: FloatArray, w: Int, h: Int,
        clip: ClipState, params: Params,
        passFactor: Float, thresh: Float
    ) {
        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                if (!clip.anyClipped[idx]) continue

                val base = idx * 3
                val nc = clip.numClipped[idx]

                when {
                    nc == 1 -> {
                        // 单通道裁剪 — 使用另两个通道的色度相关推断
                        reconstructOneChannel(work, base, clip, idx, w, h, params, passFactor, thresh)
                    }
                    nc == 2 -> {
                        // 双通道裁剪 — 使用唯一未裁剪通道推断
                        reconstructTwoChannels(work, base, clip, idx, w, h, params, passFactor, thresh)
                    }
                    else -> {
                        // 三通道全裁剪 — 使用邻域梯度外推
                        reconstructThreeChannels(work, base, idx, w, h, params, passFactor, thresh)
                    }
                }
            }
        }
    }

    /**
     * 单通道裁剪重建：利用未裁剪的两个通道的色度相关性。
     * 例如，若 R 裁剪而 G、B 未裁剪，则从 G、B 的梯度推断 R 的梯度。
     */
    private fun reconstructOneChannel(
        work: FloatArray, base: Int, clip: ClipState, idx: Int,
        w: Int, h: Int, params: Params, passFactor: Float, thresh: Float
    ) {
        val r = work[base]
        val g = work[base + 1]
        val b = work[base + 2]

        // 找到裁剪的通道
        val isClippedR = clip.clippedR[idx]
        val isClippedG = clip.clippedG[idx]
        val isClippedB = clip.clippedB[idx]

        // 从邻域计算未裁剪通道的梯度，并外推到裁剪通道
        val x = idx % w
        val y = idx / w

        var sumWeights = 0f
        var gradientEstimate = 0f
        var neighborValue = 0f

        // 使用 3x3 邻域计算梯度方向
        for (dy in -1..1) {
            for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                val nx = x + dx
                val ny = y + dy
                if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue

                val nIdx = ny * w + nx
                val nBase = nIdx * 3

                if (clip.anyClipped[nIdx]) continue

                val nR = work[nBase]
                val nG = work[nBase + 1]
                val nB = work[nBase + 2]

                val dist = sqrt((dx * dx + dy * dy).toFloat())
                val weight = 1f / (1f + dist)
                sumWeights += weight

                // 使用未裁剪通道间的比率关系推断裁剪通道
                when {
                    isClippedR -> {
                        // 用 G 通道的梯度推断 R
                        if (nG > 1e-6f) {
                            gradientEstimate += (nR / nG) * weight
                        }
                        neighborValue += nG * weight
                    }
                    isClippedG -> {
                        // 用 R 通道推断 G
                        if (nR > 1e-6f) {
                            gradientEstimate += (nG / nR) * weight
                        }
                        neighborValue += nR * weight
                    }
                    isClippedB -> {
                        // 用 G 通道推断 B
                        if (nG > 1e-6f) {
                            gradientEstimate += (nB / nG) * weight
                        }
                        neighborValue += nG * weight
                    }
                }
            }
        }

        if (sumWeights > 1e-6f) {
            val avgRatio = gradientEstimate / sumWeights
            val avgRef = neighborValue / sumWeights

            val reconstructed = when {
                isClippedR -> {
                    // 用 G 通道和色度比率重建 R
                    val fromRatio = if (g > 1e-6f) g * avgRatio else thresh
                    // 从邻居的梯度外推
                    val gradient = if (avgRef > 1e-6f) g / avgRef else 1f
                    val fromGradient = fromRatio * gradient
                    blendReconstruction(r, fromRatio, fromGradient, params.chromaWeight) * passFactor +
                        r * (1f - passFactor)
                }
                isClippedG -> {
                    val fromRatio = if (r > 1e-6f) r * avgRatio else thresh
                    val gradient = if (avgRef > 1e-6f) r / avgRef else 1f
                    val fromGradient = fromRatio * gradient
                    blendReconstruction(g, fromRatio, fromGradient, params.chromaWeight) * passFactor +
                        g * (1f - passFactor)
                }
                else -> { // isClippedB
                    val fromRatio = if (g > 1e-6f) g * avgRatio else thresh
                    val gradient = if (avgRef > 1e-6f) g / avgRef else 1f
                    val fromGradient = fromRatio * gradient
                    blendReconstruction(b, fromRatio, fromGradient, params.chromaWeight) * passFactor +
                        b * (1f - passFactor)
                }
            }

            when {
                isClippedR -> work[base]     = max(work[base], reconstructed)
                isClippedG -> work[base + 1] = max(work[base + 1], reconstructed)
                isClippedB -> work[base + 2] = max(work[base + 2], reconstructed)
            }
        }
    }

    /**
     * 双通道裁剪重建：利用唯一未裁剪的通道和色度统计推断两个裁剪通道。
     */
    private fun reconstructTwoChannels(
        work: FloatArray, base: Int, clip: ClipState, idx: Int,
        w: Int, h: Int, params: Params, passFactor: Float, thresh: Float
    ) {
        val r = work[base]
        val g = work[base + 1]
        val b = work[base + 2]

        val isClippedR = clip.clippedR[idx]
        val isClippedG = clip.clippedG[idx]
        val isClippedB = clip.clippedB[idx]

        val x = idx % w
        val y = idx / w

        // 收集邻域中未裁剪像素的通道间比率
        var sumRefValue = 0f
        var sumRatio1 = 0f  // 第一裁剪通道 / 参考通道
        var sumRatio2 = 0f  // 第二裁剪通道 / 参考通道
        var sumWeights = 0f

        for (dy in -2..2) {
            for (dx in -2..2) {
                if (dx == 0 && dy == 0) continue
                val nx = x + dx
                val ny = y + dy
                if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue

                val nIdx = ny * w + nx
                if (clip.anyClipped[nIdx]) continue

                val nBase = nIdx * 3
                val nR = work[nBase]
                val nG = work[nBase + 1]
                val nB = work[nBase + 2]

                val dist = sqrt((dx * dx + dy * dy).toFloat())
                val weight = exp(-dist * dist / 2f)

                when {
                    !isClippedG -> {
                        // G 是唯一未裁剪通道
                        if (nG > 1e-6f) {
                            sumRatio1 += (nR / nG) * weight
                            sumRatio2 += (nB / nG) * weight
                            sumRefValue += nG * weight
                        }
                    }
                    !isClippedR -> {
                        if (nR > 1e-6f) {
                            sumRatio1 += (nG / nR) * weight
                            sumRatio2 += (nB / nR) * weight
                            sumRefValue += nR * weight
                        }
                    }
                    !isClippedB -> {
                        if (nB > 1e-6f) {
                            sumRatio1 += (nR / nB) * weight
                            sumRatio2 += (nG / nB) * weight
                            sumRefValue += nB * weight
                        }
                    }
                }
                sumWeights += weight
            }
        }

        if (sumWeights > 1e-6f) {
            val avgRatio1 = sumRatio1 / sumWeights
            val avgRatio2 = sumRatio2 / sumWeights

            // 用本像素的未裁剪通道值乘以邻域比率
            when {
                !isClippedG -> {
                    // 重建 R 和 B
                    val newR = if (g > 1e-6f) g * avgRatio1 else thresh
                    val newB = if (g > 1e-6f) g * avgRatio2 else thresh

                    // 从邻域参考值的梯度做微调
                    val avgRef = sumRefValue / sumWeights
                    val gradScale = if (avgRef > 1e-6f) g / avgRef else 1f

                    val finalR = max(r, newR * gradScale * passFactor + r * (1f - passFactor))
                    val finalB = max(b, newB * gradScale * passFactor + b * (1f - passFactor))
                    work[base]     = finalR
                    work[base + 2] = finalB
                }
                !isClippedR -> {
                    val newG = if (r > 1e-6f) r * avgRatio1 else thresh
                    val newB = if (r > 1e-6f) r * avgRatio2 else thresh

                    val avgRef = sumRefValue / sumWeights
                    val gradScale = if (avgRef > 1e-6f) r / avgRef else 1f

                    val finalG = max(g, newG * gradScale * passFactor + g * (1f - passFactor))
                    val finalB = max(b, newB * gradScale * passFactor + b * (1f - passFactor))
                    work[base + 1] = finalG
                    work[base + 2] = finalB
                }
                !isClippedB -> {
                    val newR = if (b > 1e-6f) b * avgRatio1 else thresh
                    val newG = if (b > 1e-6f) b * avgRatio2 else thresh

                    val avgRef = sumRefValue / sumWeights
                    val gradScale = if (avgRef > 1e-6f) b / avgRef else 1f

                    val finalR = max(r, newR * gradScale * passFactor + r * (1f - passFactor))
                    val finalG = max(g, newG * gradScale * passFactor + g * (1f - passFactor))
                    work[base]     = finalR
                    work[base + 1] = finalG
                }
            }
        }
    }

    /**
     * 三通道全裁剪重建：使用距离加权邻域平均 + 梯度外推。
     */
    private fun reconstructThreeChannels(
        work: FloatArray, base: Int, idx: Int,
        w: Int, h: Int, params: Params, passFactor: Float, thresh: Float
    ) {
        val x = idx % w
        val y = idx / w

        var sumR = 0f
        var sumG = 0f
        var sumB = 0f
        var sumWeights = 0f

        // 梯度外推用的前向差分
        var gradR = 0f
        var gradG = 0f
        var gradB = 0f
        var gradWeights = 0f

        val searchR = 2 + params.level

        for (dy in -searchR..searchR) {
            for (dx in -searchR..searchR) {
                if (dx == 0 && dy == 0) continue
                val nx = x + dx
                val ny = y + dy
                if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue

                val nIdx = ny * w + nx
                val nBase = nIdx * 3

                // 优先使用完全未裁剪的邻居
                val isPartiallyClipped = (
                    work[nBase] > thresh || work[nBase + 1] > thresh || work[nBase + 2] > thresh
                )

                val dist = sqrt((dx * dx + dy * dy).toFloat())
                val baseWeight = exp(-dist * dist / (2f * (searchR / 2f) * (searchR / 2f)))

                // 降低部分裁剪邻居的权重
                val weight = if (isPartiallyClipped) baseWeight * 0.25f else baseWeight

                val nR = work[nBase]
                val nG = work[nBase + 1]
                val nB = work[nBase + 2]

                sumR += nR * weight
                sumG += nG * weight
                sumB += nB * weight
                sumWeights += weight

                // 梯度信息（仅从最近邻行获取）
                if (abs(dx) <= 1 && abs(dy) <= 1 && !isPartiallyClipped) {
                    // 方向性梯度
                    val dirX = if (dx != 0) dx else 1
                    val dirY = if (dy != 0) dy else 1

                    // 检查反方向是否有未裁剪像素
                    val ox = x - dirX
                    val oy = y - dirY
                    if (ox >= 0 && ox < w && oy >= 0 && oy < h) {
                        val oIdx = oy * w + ox
                        val oBase = oIdx * 3
                        val oR = work[oBase]
                        val oG = work[oBase + 1]
                        val oB = work[oBase + 2]

                        val oClipped = oR > thresh || oG > thresh || oB > thresh
                        if (!oClipped) {
                            // 中心差分 → 梯度
                            val gw = 1f / (dist + 0.1f)
                            gradR += (nR - oR) * 0.5f * gw
                            gradG += (nG - oG) * 0.5f * gw
                            gradB += (nB - oB) * 0.5f * gw
                            gradWeights += gw
                        }
                    }
                }
            }
        }

        if (sumWeights > 1e-6f) {
            var avgR = sumR / sumWeights
            var avgG = sumG / sumWeights
            var avgB = sumB / sumWeights

            // 应用梯度外推（从邻域边缘向裁剪区域中心推）
            if (gradWeights > 1e-6f) {
                val gR = gradR / gradWeights
                val gG = gradG / gradWeights
                val gB = gradB / gradWeights

                // 距离加权：越靠近边界梯度影响越大
                val gradientInfluence = 0.3f * params.chromaWeight
                avgR += gR * gradientInfluence
                avgG += gG * gradientInfluence
                avgB += gB * gradientInfluence
            }

            // 保留原始亮度信息：使用裁剪像素中最大通道值作为亮度锚点
            val origR = work[base]
            val origG = work[base + 1]
            val origB = work[base + 2]

            val origLuma = 0.2126f * origR + 0.7152f * origG + 0.0722f * origB
            val avgLuma = 0.2126f * avgR + 0.7152f * avgG + 0.0722f * avgB

            if (avgLuma > 1e-6f) {
                val scale = origLuma / avgLuma
                avgR *= scale
                avgG *= scale
                avgB *= scale
            }

            // 确保重建值不低于原始裁剪值（保留可能的 HDR 信息）
            val finalR = max(origR, avgR * passFactor + origR * (1f - passFactor))
            val finalG = max(origG, avgG * passFactor + origG * (1f - passFactor))
            val finalB = max(origB, avgB * passFactor + origB * (1f - passFactor))

            work[base]     = finalR
            work[base + 1] = finalG
            work[base + 2] = finalB
        }
    }

    // ── Partial clip reconstruction for COLOR_BLEND ─────────────────

    private fun reconstructPartialClip(
        work: FloatArray, base: Int, clip: ClipState, idx: Int,
        chromaRef: FloatArray, params: Params, thresh: Float
    ) {
        val r = work[base]
        val g = work[base + 1]
        val b = work[base + 2]

        val isClippedR = clip.clippedR[idx]
        val isClippedG = clip.clippedG[idx]
        val isClippedB = clip.clippedB[idx]

        val refIdx = idx * 2
        val rRatio = chromaRef[refIdx]      // r/g
        val bRatio = chromaRef[refIdx + 1]  // b/g

        val cw = params.chromaWeight

        when {
            isClippedR && isClippedG && !isClippedB -> {
                // B 未裁剪 → 用 B 和色度比率推断 R 和 G
                if (b > 1e-6f && bRatio > 1e-6f) {
                    val estG = b / bRatio
                    val estR = estG * rRatio
                    work[base]     = r * (1f - cw) + max(r, estR) * cw
                    work[base + 1] = g * (1f - cw) + max(g, estG) * cw
                }
            }
            isClippedR && !isClippedG && isClippedB -> {
                // G 未裁剪
                if (g > 1e-6f) {
                    val estR = g * rRatio
                    val estB = g * bRatio
                    work[base]     = r * (1f - cw) + max(r, estR) * cw
                    work[base + 2] = b * (1f - cw) + max(b, estB) * cw
                }
            }
            !isClippedR && isClippedG && isClippedB -> {
                // R 未裁剪
                if (r > 1e-6f && rRatio > 1e-6f) {
                    val estG = r / rRatio
                    val estB = estG * bRatio
                    work[base + 1] = g * (1f - cw) + max(g, estG) * cw
                    work[base + 2] = b * (1f - cw) + max(b, estB) * cw
                }
            }
            isClippedR && !isClippedG && !isClippedB -> {
                // 仅 R 裁剪
                if (g > 1e-6f) {
                    val estR = g * rRatio
                    work[base] = r * (1f - cw) + max(r, estR) * cw
                }
            }
            !isClippedR && isClippedG && !isClippedB -> {
                // 仅 G 裁剪
                if (r > 1e-6f && rRatio > 1e-6f) {
                    val estG = r / rRatio
                    work[base + 1] = g * (1f - cw) + max(g, estG) * cw
                }
            }
            !isClippedR && !isClippedG && isClippedB -> {
                // 仅 B 裁剪
                if (g > 1e-6f) {
                    val estB = g * bRatio
                    work[base + 2] = b * (1f - cw) + max(b, estB) * cw
                }
            }
        }
    }

    private fun reconstructFullClip(
        work: FloatArray, base: Int, idx: Int,
        chromaRef: FloatArray, w: Int, h: Int, params: Params, thresh: Float
    ) {
        val x = idx % w
        val y = idx / w

        var sumR = 0f
        var sumG = 0f
        var sumB = 0f
        var totalWeight = 0f

        val radius = max(params.blendRadius, 3)

        for (dy in -radius..radius) {
            for (dx in -radius..radius) {
                if (dx == 0 && dy == 0) continue
                val nx = x + dx
                val ny = y + dy
                if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue

                val nIdx = ny * w + nx
                val nBase = nIdx * 3

                val nR = work[nBase]
                val nG = work[nBase + 1]
                val nB = work[nBase + 2]

                // 跳过也是全裁剪的邻居
                if (nR > thresh && nG > thresh && nB > thresh) continue

                val dist = sqrt((dx * dx + dy * dy).toFloat())
                val weight = 1f / (1f + dist * dist)

                sumR += nR * weight
                sumG += nG * weight
                sumB += nB * weight
                totalWeight += weight
            }
        }

        if (totalWeight > 1e-6f) {
            val avgR = sumR / totalWeight
            val avgG = sumG / totalWeight
            val avgB = sumB / totalWeight

            // 保留原始亮度
            val origR = work[base]
            val origG = work[base + 1]
            val origB = work[base + 2]
            val origLuma = 0.2126f * origR + 0.7152f * origG + 0.0722f * origB
            val avgLuma = 0.2126f * avgR + 0.7152f * avgG + 0.0722f * avgB

            var newR = avgR
            var newG = avgG
            var newB = avgB

            // 应用色度参考
            val refIdx = idx * 2
            val rRatio = chromaRef[refIdx]
            val bRatio = chromaRef[refIdx + 1]

            if (avgG > 1e-6f) {
                val chromaR = avgG * rRatio
                val chromaB = avgG * bRatio
                newR = avgR * (1f - params.chromaWeight) + chromaR * params.chromaWeight
                newB = avgB * (1f - params.chromaWeight) + chromaB * params.chromaWeight
            }

            // 亮度缩放
            if (avgLuma > 1e-6f) {
                val scale = origLuma / avgLuma
                newR *= scale
                newG *= scale
                newB *= scale
            }

            work[base]     = max(origR, newR)
            work[base + 1] = max(origG, newG)
            work[base + 2] = max(origB, newB)
        }
    }

    // ── Stage 4: Boundary Blending ──────────────────────────────────

    /**
     * 在裁剪区域边界使用距离加权的 smoothstep 混合，避免硬边界。
     */
    private fun applyBoundaryBlend(
        work: FloatArray, original: FloatArray,
        w: Int, h: Int, clip: ClipState, params: Params
    ): FloatArray {
        val result = work.copyOf()
        val radius = params.blendRadius

        if (radius <= 0) return result

        // 计算每个像素到最近未裁剪像素的距离
        val distToUncut = FloatArray(w * h) { Float.MAX_VALUE }
        val nearUncut = BooleanArray(w * h)

        // 初始化：未裁剪像素距离为 0
        for (i in 0 until w * h) {
            if (!clip.anyClipped[i]) {
                distToUncut[i] = 0f
                nearUncut[i] = true
            }
        }

        // 双通道距离变换（简化版 Chamfer）
        // 前向扫描
        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                if (nearUncut[idx]) continue

                var minDist = distToUncut[idx]
                // 检查四邻域
                if (x > 0) minDist = min(minDist, distToUncut[idx - 1] + 1f)
                if (y > 0) minDist = min(minDist, distToUncut[idx - w] + 1f)
                distToUncut[idx] = minDist
            }
        }

        // 后向扫描
        for (y in h - 1 downTo 0) {
            for (x in w - 1 downTo 0) {
                val idx = y * w + x
                if (nearUncut[idx]) continue

                var minDist = distToUncut[idx]
                if (x < w - 1) minDist = min(minDist, distToUncut[idx + 1] + 1f)
                if (y < h - 1) minDist = min(minDist, distToUncut[idx + w] + 1f)
                distToUncut[idx] = minDist
            }
        }

        // 在边界区域做平滑混合
        val blendWidth = radius.toFloat()

        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                if (!clip.anyClipped[idx]) continue

                val dist = distToUncut[idx]
                if (dist > blendWidth * 2f) continue  // 远离边界的不做混合

                // smoothstep 混合权重：距离越近边界，原始数据权重越大
                val t = (dist / blendWidth).coerceIn(0f, 1f)
                val blendWeight = smoothstep(t)  // 0 at boundary, 1 far inside

                val base = idx * 3
                for (c in 0..2) {
                    val origVal = original[base + c]
                    val workVal = work[base + c]
                    // 在边界附近：保留更多原始值以保持连续性
                    result[base + c] = workVal * blendWeight + origVal * (1f - blendWeight)
                }
            }
        }

        return result
    }

    // ── Utility functions ───────────────────────────────────────────

    /** 找到数据中的最大通道值（用于确定裁剪阈值） */
    private fun findMaxValue(data: FloatArray): Float {
        var maxVal = 0f
        for (i in data.indices) {
            if (data[i] > maxVal) maxVal = data[i]
        }
        return if (maxVal > 1f) maxVal else 1f  // 至少用 1.0 作为 HDR 参考白
    }

    /** Smoothstep: Hermite 平滑插值 */
    private fun smoothstep(t: Float): Float {
        val clamped = t.coerceIn(0f, 1f)
        return clamped * clamped * (3f - 2f * clamped)
    }

    /** 混合色度推断和梯度推断两种重建结果 */
    private fun blendReconstruction(
        original: Float, fromChroma: Float, fromGradient: Float, chromaWeight: Float
    ): Float {
        val blended = fromChroma * chromaWeight + fromGradient * (1f - chromaWeight)
        return max(original, blended)
    }
}

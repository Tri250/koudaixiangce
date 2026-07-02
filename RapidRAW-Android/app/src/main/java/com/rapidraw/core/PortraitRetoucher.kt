package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import com.rapidraw.ai.FaceDetector
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 人像精修 — 肤质、五官、身形全方位修饰。
 *
 * 功能：
 * - 肤质平滑（边缘感知双边滤波，仅作用于肤色区域）
 * - 亮眼（从面部关键点定位眼部区域，提亮增亮）
 * - 美白牙齿（从面部下半区域检测牙齿，减饱和+提亮）
 * - 瘦脸/大眼/瘦鼻（网格变形，基于 FaceDetector 关键点）
 * - 瘦身/拉腿（垂直方向网格变形）
 * - 所有效果均有可调强度（0~1）
 *
 * 使用 FaceDetector（android.media.FaceDetector）获取面部信息，
 * 包括眼部位置、人脸边界框、眼距等，用于精确定位各修饰区域。
 */
class PortraitRetoucher {

    /**
     * 精修参数 — 所有强度值范围 0~1（0=无效果，1=最大效果）。
     */
    data class RetouchParams(
        val skinSmoothing: Float = 0f,
        val eyeBrightening: Float = 0f,
        val teethWhitening: Float = 0f,
        val bodySlimStrength: Float = 0f,
        val legLengthenStrength: Float = 0f,
        val faceSlimStrength: Float = 0f,
        val eyeEnlargeStrength: Float = 0f,
        val noseSlimStrength: Float = 0f,
        val lipAdjustStrength: Float = 0f,
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    // ── 公开 API ──────────────────────────────────────────────────────

    /**
     * 执行人像精修。
     *
     * 处理顺序：
     * 1. 先应用像素级修饰（肤质平滑、亮眼、美白牙齿）
     * 2. 再应用几何变形（瘦脸、大眼、瘦鼻、瘦身、拉腿）
     *
     * @param source 原图
     * @param faceResults FaceDetector 的检测结果
     * @param params 精修参数
     */
    fun retouch(
        source: Bitmap,
        faceResults: FaceDetector.DetectionResult,
        params: RetouchParams,
    ): Bitmap {
        if (source.isRecycled) return source

        // Step 1: 像素级修饰（肤质、亮眼、美白）
        var result = applyPixelRetouching(source, faceResults, params)

        // Step 2: 几何变形
        if (hasWarpParams(params) && faceResults.faces.isNotEmpty()) {
            result = applyWarpRetouching(result, faceResults, params)
        }

        return result
    }

    // ── 像素级修饰 ────────────────────────────────────────────────────

    /**
     * 应用像素级修饰：肤质平滑 + 亮眼 + 美白牙齿。
     *
     * 这些操作不改变像素位置，只改变像素值。
     */
    private fun applyPixelRetouching(
        source: Bitmap,
        faceResults: FaceDetector.DetectionResult,
        params: RetouchParams,
    ): Bitmap {
        val w = source.width
        val h = source.height

        // 如果没有需要像素级修饰的参数，直接返回原图副本
        if (params.skinSmoothing < 0.01f &&
            params.eyeBrightening < 0.01f &&
            params.teethWhitening < 0.01f
        ) {
            return source.copy(Bitmap.Config.ARGB_8888, true)
        }

        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(w * h)
        result.getPixels(pixels, 0, w, 0, 0, w, h)

        // 构建修饰遮罩：哪些像素需要被修饰
        val skinMask = if (params.skinSmoothing > 0.01f) {
            buildSkinMask(pixels, w, h, faceResults)
        } else {
            FloatArray(w * h) // 全零
        }

        // 1. 肤质平滑
        if (params.skinSmoothing > 0.01f) {
            applyBilateralSmooth(pixels, skinMask, w, h, params.skinSmoothing)
        }

        // 2. 亮眼
        if (params.eyeBrightening > 0.01f && faceResults.faces.isNotEmpty()) {
            for (face in faceResults.faces) {
                brightenEyes(pixels, face, w, h, params.eyeBrightening)
            }
        }

        // 3. 美白牙齿
        if (params.teethWhitening > 0.01f && faceResults.faces.isNotEmpty()) {
            for (face in faceResults.faces) {
                whitenTeeth(pixels, face, w, h, params.teethWhitening)
            }
        }

        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return result
    }

    // ── 肤质平滑（边缘感知双边滤波） ──────────────────────────────────

    /**
     * 构建肤色遮罩 — 仅标记肤色区域的像素。
     *
     * 使用 YCbCr 色彩空间阈值法检测肤色像素，
     * 并将面部边界框作为辅助约束。
     */
    private fun buildSkinMask(
        pixels: IntArray,
        w: Int,
        h: Int,
        faceResults: FaceDetector.DetectionResult,
    ): FloatArray {
        val mask = FloatArray(w * h)

        for (i in pixels.indices) {
            val px = pixels[i]
            val r = (px shr 16) and 0xFF
            val g = (px shr 8) and 0xFF
            val b = px and 0xFF

            // YCbCr 肤色模型
            val y = 0.299 * r + 0.587 * g + 0.114 * b
            val cb = 128 - 0.169 * r - 0.331 * g + 0.500 * b
            val cr = 128 + 0.500 * r - 0.419 * g - 0.081 * b

            val isSkin = y in 80.0..255.0 &&
                    cb in 77.0..127.0 &&
                    cr in 133.0..173.0 &&
                    r > 80 && g > 30 && b > 15 &&
                    (r - g) > 12 &&
                    r > g && r > b

            if (isSkin) {
                // 检查是否在面部区域内或附近
                val x = i % w
                val yPx = i / w

                var nearFace = faceResults.faces.isEmpty()
                for (face in faceResults.faces) {
                    // 扩展面部区域到 1.3 倍以包含颈部等
                    val expandedLeft = face.bounds.left - face.bounds.width() * 0.15f
                    val expandedTop = face.bounds.top - face.bounds.height() * 0.1f
                    val expandedRight = face.bounds.right + face.bounds.width() * 0.15f
                    val expandedBottom = face.bounds.bottom + face.bounds.height() * 0.3f

                    if (x >= expandedLeft && x <= expandedRight &&
                        yPx >= expandedTop && yPx <= expandedBottom
                    ) {
                        nearFace = true
                        break
                    }
                }

                mask[i] = if (nearFace) 1f else 0.5f // 面部附近完全平滑，远处弱平滑
            }
        }

        return mask
    }

    /**
     * 边缘感知双边滤波 — 仅平滑肤色区域，保留边缘细节。
     *
     * 双边滤波原理：
     * - 空间高斯核：距离越近权重越大
     * - 值域高斯核：颜色差异越小权重越大
     * - 两者的乘积确保只在颜色相近的邻域内平滑
     *
     * @param intensity 平滑强度 (0~1)
     */
    private fun applyBilateralSmooth(
        pixels: IntArray,
        skinMask: FloatArray,
        w: Int,
        h: Int,
        intensity: Float,
    ) {
        // 根据强度调整滤波参数
        val radius = (2 + intensity * 4).toInt().coerceIn(2, 6) // 2~6
        val spatialSigma = 1.0 + intensity * 2.0 // 1.0~3.0
        val rangeSigma = 20.0 + intensity * 40.0  // 20~60

        // 预计算空间高斯权重
        val spatialWeights = FloatArray((2 * radius + 1) * (2 * radius + 1))
        var idx = 0
        for (dy in -radius..radius) {
            for (dx in -radius..radius) {
                val dist2 = (dx * dx + dy * dy).toDouble()
                spatialWeights[idx++] = exp(-dist2 / (2 * spatialSigma * spatialSigma)).toFloat()
            }
        }

        val temp = pixels.copyOf()

        for (y in radius until h - radius) {
            for (x in radius until w - radius) {
                val i = y * w + x
                val maskVal = skinMask[i]
                if (maskVal < 0.01f) continue // 非肤色区域不处理

                val centerR = (temp[i] shr 16) and 0xFF
                val centerG = (temp[i] shr 8) and 0xFF
                val centerB = temp[i] and 0xFF

                var sumR = 0f
                var sumG = 0f
                var sumB = 0f
                var weightSum = 0f

                var wIdx = 0
                for (dy in -radius..radius) {
                    for (dx in -radius..radius) {
                        val ni = (y + dy) * w + (x + dx)
                        val nR = (temp[ni] shr 16) and 0xFF
                        val nG = (temp[ni] shr 8) and 0xFF
                        val nB = temp[ni] and 0xFF

                        // 值域权重
                        val diffR = (nR - centerR).toDouble()
                        val diffG = (nG - centerG).toDouble()
                        val diffB = (nB - centerB).toDouble()
                        val colorDist2 = diffR * diffR + diffG * diffG + diffB * diffB
                        val rangeWeight = exp(-colorDist2 / (2 * rangeSigma * rangeSigma)).toFloat()

                        val weight = spatialWeights[wIdx] * rangeWeight
                        sumR += nR * weight
                        sumG += nG * weight
                        sumB += nB * weight
                        weightSum += weight
                        wIdx++
                    }
                }

                if (weightSum > 0f) {
                    val smoothR = (sumR / weightSum).toInt().coerceIn(0, 255)
                    val smoothG = (sumG / weightSum).toInt().coerceIn(0, 255)
                    val smoothB = (sumB / weightSum).toInt().coerceIn(0, 255)

                    // 按强度混合原始值和平滑值
                    val alpha = intensity * maskVal // mask × intensity
                    val finalR = (centerR * (1f - alpha) + smoothR * alpha).toInt().coerceIn(0, 255)
                    val finalG = (centerG * (1f - alpha) + smoothG * alpha).toInt().coerceIn(0, 255)
                    val finalB = (centerB * (1f - alpha) + smoothB * alpha).toInt().coerceIn(0, 255)

                    pixels[i] = (0xFF shl 24) or (finalR shl 16) or (finalG shl 8) or finalB
                }
            }
        }
    }

    // ── 亮眼 ──────────────────────────────────────────────────────────

    /**
     * 提亮眼部区域。
     *
     * 使用 FaceDetector 提供的眼部位置（leftEye、rightEye），
     * 在眼部周围小区域内：
     * 1. 增加亮度（lighten）
     * 2. 提高对比度（使眼睛更明亮有神）
     * 3. 略微增加饱和度
     */
    private fun brightenEyes(
        pixels: IntArray,
        face: FaceDetector.FaceInfo,
        w: Int,
        h: Int,
        intensity: Float,
    ) {
        // 眼部半径约为眼距的 18%
        val eyeRadius = face.eyeDistance * 0.18f

        // 处理双眼
        val eyes = listOf(
            face.leftEye to face.rightEye
        )

        for ((leftEye, rightEye) in eyes) {
            brightenEyeRegion(pixels, leftEye.x, leftEye.y, eyeRadius, w, h, intensity)
            brightenEyeRegion(pixels, rightEye.x, rightEye.y, eyeRadius, w, h, intensity)
        }
    }

    private fun brightenEyeRegion(
        pixels: IntArray,
        cx: Float,
        cy: Float,
        radius: Float,
        w: Int,
        h: Int,
        intensity: Float,
    ) {
        val r = radius.toInt().coerceAtLeast(2)
        val featherR = (r * 1.5f).toInt()

        val x0 = max(0, (cx - featherR).toInt())
        val y0 = max(0, (cy - featherR).toInt())
        val x1 = min(w - 1, (cx + featherR).toInt())
        val y1 = min(h - 1, (cy + featherR).toInt())

        for (py in y0..y1) {
            for (px in x0..x1) {
                val dx = px - cx
                val dy = py - cy
                val dist = sqrt((dx * dx + dy * dy).toDouble()).toFloat()

                if (dist > featherR) continue

                // Feather 权重：内核完全应用，边缘渐变
                val weight = if (dist <= r) 1f
                else 1f - (dist - r) / (featherR - r)
                val alpha = weight * intensity * 0.5f // 最大 50% 提亮

                val i = py * w + px
                var red = (pixels[i] shr 16) and 0xFF
                var green = (pixels[i] shr 8) and 0xFF
                var blue = pixels[i] and 0xFF

                // 提亮：增加亮度 + 微增对比度
                val brighten = alpha * 60f // 最多提亮 60 级
                red = (red + brighten).toInt().coerceIn(0, 255)
                green = (green + brighten).toInt().coerceIn(0, 255)
                blue = (blue + brighten * 0.9f).toInt().coerceIn(0, 255) // 蓝色少提一点保持自然

                pixels[i] = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
            }
        }
    }

    // ── 美白牙齿 ──────────────────────────────────────────────────────

    /**
     * 美白牙齿区域。
     *
     * 面部下半部分（嘴唇下方到下巴之间）检测牙齿：
     * 1. 在目标区域内寻找偏白色像素（高亮度、低饱和度）
     * 2. 减少饱和度（去除黄色调）
     * 3. 提高亮度
     *
     * 定位策略：
     * - 牙齿在脸部下方 60%~85% 的高度范围内
     * - 水平范围在脸部宽度中间 60%
     */
    private fun whitenTeeth(
        pixels: IntArray,
        face: FaceDetector.FaceInfo,
        w: Int,
        h: Int,
        intensity: Float,
    ) {
        // 牙齿区域估计：嘴唇到下巴之间
        val faceW = face.bounds.width()
        val faceH = face.bounds.height()
        val cx = face.bounds.centerX()
        val cy = face.bounds.centerY()

        // 牙齿大约在脸部 60%~85% 高度处
        val teethTop = (cy + faceH * 0.1f).toInt()
        val teethBottom = (cy + faceH * 0.35f).toInt()
        val teethLeft = (cx - faceW * 0.2f).toInt()
        val teethRight = (cx + faceW * 0.2f).toInt()

        for (py in max(0, teethTop)..min(h - 1, teethBottom)) {
            for (px in max(0, teethLeft)..min(w - 1, teethRight)) {
                val i = py * w + px
                val red = (pixels[i] shr 16) and 0xFF
                val green = (pixels[i] shr 8) and 0xFF
                val blue = pixels[i] and 0xFF

                // 检测是否为牙齿颜色：高亮度、中低饱和度
                val maxC = maxOf(red, green, blue)
                val minC = minOf(red, green, blue)
                val saturation = if (maxC > 0) (maxC - minC).toFloat() / maxC else 0f
                val brightness = maxC / 255f

                // 牙齿特征：亮度 > 0.5，饱和度 < 0.4（偏白/偏黄白色）
                if (brightness > 0.5f && saturation < 0.4f) {
                    // 置信度：越亮越白越可能是牙齿
                    val confidence = (brightness - 0.5f) * 2f * (1f - saturation / 0.4f)
                    val alpha = confidence.coerceIn(0f, 1f) * intensity

                    if (alpha > 0.01f) {
                        // 减少黄色调（降低 R/G 相对 B 的比例）
                        var newR = red.toFloat()
                        var newG = green.toFloat()
                        var newB = blue.toFloat()

                        // 减饱和
                        val gray = (newR * 0.299f + newG * 0.587f + newB * 0.114f)
                        newR = newR + (gray - newR) * alpha * 0.6f
                        newG = newG + (gray - newG) * alpha * 0.6f
                        newB = newB + (gray - newB) * alpha * 0.4f // 蓝色少减，保持冷白感

                        // 提亮
                        val brighten = alpha * 30f
                        newR = (newR + brighten).coerceIn(0f, 255f)
                        newG = (newG + brighten).coerceIn(0f, 255f)
                        newB = (newB + brighten * 1.1f).coerceIn(0f, 255f) // 蓝色多提一点

                        pixels[i] = (0xFF shl 24) or
                                (newR.toInt().coerceIn(0, 255) shl 16) or
                                (newG.toInt().coerceIn(0, 255) shl 8) or
                                newB.toInt().coerceIn(0, 255)
                    }
                }
            }
        }
    }

    // ── 几何变形修饰 ──────────────────────────────────────────────────

    /**
     * 应用几何变形修饰（瘦脸、大眼、瘦鼻、瘦身、拉腿）。
     * 使用 MeshWarp 网格变形引擎。
     */
    private fun applyWarpRetouching(
        source: Bitmap,
        faceResults: FaceDetector.DetectionResult,
        params: RetouchParams,
    ): Bitmap {
        val w = source.width
        val h = source.height
        val meshWarp = MeshWarp(w, h, MESH_GRID_SIZE)

        for (face in faceResults.faces) {
            val bounds = face.bounds

            // 瘦脸
            if (abs(params.faceSlimStrength) > 0.01f) {
                val cx = bounds.centerX()
                val cy = bounds.centerY() + bounds.height() * 0.1f
                val radiusX = bounds.width() * 0.55f
                val radiusY = bounds.height() * 0.5f
                meshWarp.applyRadialScale(
                    cx, cy, radiusX, radiusY,
                    1f - params.faceSlimStrength * 0.25f,
                    feather = 0.3f,
                )
            }

            // 大眼
            if (params.eyeEnlargeStrength > 0.01f) {
                val eyeRadius = bounds.width() * 0.1f
                meshWarp.applyRadialScale(
                    face.leftEye.x, face.leftEye.y, eyeRadius, eyeRadius,
                    1f + params.eyeEnlargeStrength * 0.3f,
                    feather = 0.4f,
                )
                meshWarp.applyRadialScale(
                    face.rightEye.x, face.rightEye.y, eyeRadius, eyeRadius,
                    1f + params.eyeEnlargeStrength * 0.3f,
                    feather = 0.4f,
                )
            }

            // 瘦鼻
            if (params.noseSlimStrength > 0.01f) {
                // 鼻子大约在两眼中间下方
                val noseX = face.midPoint.x
                val noseY = face.midPoint.y + face.eyeDistance * 0.4f
                val noseW = bounds.width() * 0.12f
                val noseH = bounds.height() * 0.08f
                meshWarp.applyRadialScale(
                    noseX, noseY, noseW, noseH,
                    1f - params.noseSlimStrength * 0.3f,
                    feather = 0.3f,
                )
            }

            // 瘦身
            if (abs(params.bodySlimStrength) > 0.01f) {
                val bodyTop = bounds.bottom
                val bodyCx = bounds.centerX()
                val bodyWidth = bounds.width() * 2.5f
                meshWarp.applyVerticalSlim(
                    bodyCx, bodyTop.toFloat(), w.toFloat(), h.toFloat(),
                    bodyWidth,
                    1f - params.bodySlimStrength * 0.2f,
                )
            }

            // 拉腿
            if (params.legLengthenStrength > 0.01f) {
                val legStartY = h * 0.5f
                val legEndY = h.toFloat()
                meshWarp.applyVerticalStretch(
                    legStartY, legEndY,
                    1f + params.legLengthenStrength * 0.15f,
                )
            }
        }

        return meshWarp.render(source)
    }

    private fun hasWarpParams(params: RetouchParams): Boolean {
        return abs(params.faceSlimStrength) > 0.01f ||
                abs(params.bodySlimStrength) > 0.01f ||
                params.eyeEnlargeStrength > 0.01f ||
                params.noseSlimStrength > 0.01f ||
                params.legLengthenStrength > 0.01f ||
                abs(params.lipAdjustStrength) > 0.01f
    }

    companion object {
        private const val MESH_GRID_SIZE = 32
    }
}

package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 电影级胶片颗粒渲染器
 * 基于 Alasdair Newson 等人论文 "Realistic Film Grain Rendering" 思想。
 * 使用泊松盘采样生成颗粒中心，每个颗粒用高斯分布模拟银盐聚集。
 */
class FilmGrainRenderer {

    /**
     * 生成胶片颗粒纹理
     * @param width 纹理宽度
     * @param height 纹理高度
     * @param grainSize 颗粒平均大小（像素）
     * @param density 颗粒密度 [0,1]
     * @param roughness 颗粒粗糙度 [0,1]（0=均匀, 1=高方差）
     */
    fun generateGrainTexture(
        width: Int,
        height: Int,
        grainSize: Float = 2f,
        density: Float = 0.5f,
        roughness: Float = 0.3f,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val paint = Paint().apply {
            isAntiAlias = true
        }

        // Phase 1: 泊松盘采样生成颗粒中心
        val centers = poissonDiskSampling(width, height, grainSize * 2f)
        val random = java.util.Random(System.currentTimeMillis())

        // Phase 2: 每个颗粒使用对数正态分布生成大小和透明度
        for (center in centers) {
            // 颗粒大小遵循对数正态分布（真实胶片物理特性）
            val gauss = random.nextGaussian().toFloat()
            val sizeVariation = (1f - roughness) + roughness * exp(gauss * 0.5f).coerceIn(0.3f, 3f)
            val radius = grainSize * sizeVariation * 0.5f

            // 颗粒不透明度随机变化
            val baseAlpha = density * 255
            val alpha = (baseAlpha * (0.5f + 0.5f * random.nextFloat())).toInt().coerceIn(0, 255)

            // 使用径向渐变模拟高斯模糊颗粒形状
            val shader = RadialGradient(
                center.first, center.second, radius.coerceAtLeast(0.5f),
                intArrayOf(Color.argb(alpha, 255, 255, 255), Color.TRANSPARENT),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP,
            )
            paint.shader = shader
            canvas.drawCircle(center.first, center.second, radius.coerceAtLeast(0.5f), paint)
        }
        paint.shader = null

        return bitmap
    }

    /**
     * 将颗粒纹理叠加到图像上
     * @param source 原始图像
     * @param grainTexture 颗粒纹理
     * @param intensity 强度 [0,1]
     */
    fun applyGrain(source: Bitmap, grainTexture: Bitmap, intensity: Float): Bitmap {
        val w = source.width
        val h = source.height
        val srcPixels = IntArray(w * h)
        val grainPixels = IntArray(w * h)
        source.getPixels(srcPixels, 0, w, 0, 0, w, h)
        // 缩放 grainTexture 到匹配尺寸
        val scaledGrain = if (grainTexture.width != w || grainTexture.height != h) {
            Bitmap.createScaledBitmap(grainTexture, w, h, true)
        } else {
            grainTexture
        }
        scaledGrain.getPixels(grainPixels, 0, w, 0, 0, w, h)

        val result = IntArray(w * h)
        for (i in srcPixels.indices) {
            val sr = ((srcPixels[i] shr 16) and 0xFF) / 255f
            val sg = ((srcPixels[i] shr 8) and 0xFF) / 255f
            val sb = (srcPixels[i] and 0xFF) / 255f

            // 颗粒仅在亮度通道生效（避免色度噪声）
            val luma = 0.299f * sr + 0.587f * sg + 0.114f * sb
            val grainAlpha = ((grainPixels[i] ushr 24) and 0xFF) / 255f * intensity
            val grainEffect = (grainAlpha - 0.5f) * 2f * luma // 中调加权

            val newR = (sr + grainEffect).coerceIn(0f, 1f)
            val newG = (sg + grainEffect).coerceIn(0f, 1f)
            val newB = (sb + grainEffect).coerceIn(0f, 1f)

            result[i] = (0xFF shl 24) or
                ((newR * 255).toInt().coerceIn(0, 255) shl 16) or
                ((newG * 255).toInt().coerceIn(0, 255) shl 8) or
                ((newB * 255).toInt().coerceIn(0, 255))
        }

        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(result, 0, w, 0, 0, w, h)
        if (scaledGrain != grainTexture) scaledGrain.recycle()
        return bitmap
    }

    /**
     * Bridson 泊松盘采样算法 (O(N))
     * 生成空间均匀但随机的点集
     */
    private fun poissonDiskSampling(
        width: Int,
        height: Int,
        minDist: Float,
    ): List<Pair<Float, Float>> {
        val points = mutableListOf<Pair<Float, Float>>()
        val activeList = mutableListOf<Pair<Float, Float>>()
        val cellSize = minDist / sqrt(2f)
        val gridW = (width / cellSize).toInt() + 1
        val gridH = (height / cellSize).toInt() + 1
        val grid = arrayOfNulls<Pair<Float, Float>?>(gridW * gridH)

        val random = Random.Default
        // 初始随机点
        val first = random.nextFloat() * width to random.nextFloat() * height
        points.add(first)
        activeList.add(first)
        grid[((first.second / cellSize).toInt()).coerceIn(0, gridH - 1) * gridW +
              ((first.first / cellSize).toInt()).coerceIn(0, gridW - 1)] = first

        while (activeList.isNotEmpty()) {
            val idx = random.nextInt(activeList.size)
            val center = activeList[idx]
            var found = false

            for (i in 0 until 30) {
                val angle = random.nextFloat() * 2f * PI.toFloat()
                val r = minDist + random.nextFloat() * minDist
                val candidate = center.first + r * cos(angle) to center.second + r * sin(angle)

                if (candidate.first < 0 || candidate.first >= width ||
                    candidate.second < 0 || candidate.second >= height) continue

                val gx = (candidate.first / cellSize).toInt().coerceIn(0, gridW - 1)
                val gy = (candidate.second / cellSize).toInt().coerceIn(0, gridH - 1)
                var tooClose = false

                for (dy in -2..2) {
                    for (dx in -2..2) {
                        val nx = gx + dx
                        val ny = gy + dy
                        if (nx < 0 || nx >= gridW || ny < 0 || ny >= gridH) continue
                        val neighbor = grid[ny * gridW + nx]
                        if (neighbor != null) {
                            val d = sqrt(
                                (candidate.first - neighbor.first).pow2() +
                                (candidate.second - neighbor.second).pow2()
                            )
                            if (d < minDist) { tooClose = true; break }
                        }
                    }
                    if (tooClose) break
                }

                if (!tooClose) {
                    points.add(candidate)
                    activeList.add(candidate)
                    grid[gy * gridW + gx] = candidate
                    found = true
                    break
                }
            }

            if (!found) activeList.removeAt(idx)
        }

        return points
    }

    private fun Float.pow2(): Float = this * this
}

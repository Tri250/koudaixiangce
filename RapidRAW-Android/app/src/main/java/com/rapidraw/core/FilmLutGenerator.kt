package com.rapidraw.core

import com.rapidraw.data.model.FilmSimulation
import kotlin.math.abs
import kotlin.math.pow

/**
 * 胶片3D LUT生成器 - 基于胶片参数生成3D查找表
 * 用于精确的胶片模拟，替代纯参数化方法
 */
object FilmLutGenerator {
    
    data class FilmLut(
        val size: Int,
        val data: FloatArray,  // RGB triplets, size^3 * 3
    ) {
        fun sample(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
            val ri = (r * (size - 1)).toInt().coerceIn(0, size - 1)
            val gi = (g * (size - 1)).toInt().coerceIn(0, size - 1)
            val bi = (b * (size - 1)).toInt().coerceIn(0, size - 1)
            val idx = (ri * size * size + gi * size + bi) * 3
            return Triple(data[idx], data[idx + 1], data[idx + 2])
        }
    }
    
    /**
     * 从FilmSimulation参数生成3D LUT
     * @param film 胶片模拟数据
     * @param lutSize LUT尺寸（17=标准，33=高精度）
     */
    fun generateFromFilm(film: FilmSimulation, lutSize: Int = 17): FilmLut {
        val data = FloatArray(lutSize * lutSize * lutSize * 3)
        
        // 胶片色调曲线控制点
        val curvePoints = film.toneCurvePoints.sortedBy { it.first }
        
        for (r in 0 until lutSize) {
            for (g in 0 until lutSize) {
                for (b in 0 until lutSize) {
                    val nr = r.toFloat() / (lutSize - 1)
                    val ng = g.toFloat() / (lutSize - 1)
                    val nb = b.toFloat() / (lutSize - 1)
                    
                    // 应用胶片色彩偏移
                    var or = nr + film.redShift * 0.15f
                    var og = ng + film.greenShift * 0.15f
                    var ob = nb + film.blueShift * 0.15f
                    
                    // 应用色调曲线
                    or = applyCurve(or, curvePoints)
                    og = applyCurve(og, curvePoints)
                    ob = applyCurve(ob, curvePoints)
                    
                    // 应用饱和度修改
                    if (abs(film.saturationModifier) > 0.001f) {
                        val luma = 0.2126f * or + 0.7152f * og + 0.0722f * ob
                        val mod = 1f + film.saturationModifier
                        or = luma + (or - luma) * mod
                        og = luma + (og - luma) * mod
                        ob = luma + (ob - luma) * mod
                    }
                    
                    // 应用对比度修改
                    if (abs(film.contrastModifier) > 0.001f) {
                        val contrastPow = 1f + film.contrastModifier * 0.5f
                        val mid = 0.18f
                        or = mid + (or - mid) * contrastPow
                        og = mid + (og - mid) * contrastPow
                        ob = mid + (ob - mid) * contrastPow
                    }
                    
                    // 高光滚降
                    if (film.highlightRollOff > 0.001f) {
                        or = applyHighlightRollOff(or, film.highlightRollOff)
                        og = applyHighlightRollOff(og, film.highlightRollOff)
                        ob = applyHighlightRollOff(ob, film.highlightRollOff)
                    }
                    
                    // 阴影提升
                    if (film.shadowLift > 0.001f) {
                        val luma = 0.2126f * or + 0.7152f * og + 0.0722f * ob
                        val sMask = ColorMath.shadowsMask(luma)
                        val lift = film.shadowLift * 0.2f * sMask
                        or += lift
                        og += lift
                        ob += lift
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
    
    private fun applyCurve(value: Float, points: List<Pair<Float, Float>>): Float {
        if (points.size < 2) return value
        val normalized = points.map { it.first / 255f to it.second / 255f }
        return ColorMath.applyCurve(value, normalized)
    }
    
    private fun applyHighlightRollOff(value: Float, rollOff: Float): Float {
        if (value < 0.5f) return value
        val hMask = ColorMath.highlightsMask(value)
        val shoulder = 1f - (1f - value).toDouble().pow(1.0 + rollOff * 2.0).toFloat()
        return value + (shoulder - value) * hMask
    }
}

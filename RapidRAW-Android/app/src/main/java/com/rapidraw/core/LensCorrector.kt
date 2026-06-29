package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import com.rapidraw.data.model.ExifData
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 镜头畸变校正：Brown-Conrady 模型。
 * 径向畸变: r' = r * (1 + k1*r^2 + k2*r^4)
 * 切向畸变: + p1*(r^2+2x^2) + 2*p2*x*y
 * 使用反向映射 + 双线性插值避免空洞。
 */
class LensCorrector(
    private val k1: Float = 0f,
    private val k2: Float = 0f,
    private val p1: Float = 0f,
    private val p2: Float = 0f,
    private val scale: Float = 1f,
) {
    data class LensParams(val k1: Float, val k2: Float, val p1: Float, val p2: Float, val scale: Float)
    
    /**
     * 执行畸变校正。
     * 反向映射：对输出图每个像素，找到输入图对应的源位置。
     */
    fun correct(source: Bitmap): Bitmap {
        val w = source.width
        val h = source.height
        val cx = w / 2f
        val cy = h / 2f
        val maxR = sqrt(cx * cx + cy * cy)
        
        val srcPixels = IntArray(w * h)
        source.getPixels(srcPixels, 0, w, 0, 0, w, h)
        
        val result = IntArray(w * h) { 0xFF000000.toInt() }
        
        for (y in 0 until h) {
            for (x in 0 until w) {
                // 归一化坐标到 [-1, 1]
                val nx = (x - cx) / maxR
                val ny = (y - cy) / maxR
                val r2 = nx * nx + ny * ny
                
                // 反向畸变：从输出坐标反推输入坐标
                val radial = 1f + k1 * r2 + k2 * r2 * r2
                val tx = p1 * (r2 + 2f * nx * nx) + 2f * p2 * nx * ny
                val ty = 2f * p1 * nx * ny + p2 * (r2 + 2f * ny * ny)
                
                val srcNx = (nx / radial + tx) * scale
                val srcNy = (ny / radial + ty) * scale
                
                // 转回像素坐标
                val srcX = srcNx * maxR + cx
                val srcY = srcNy * maxR + cy
                
                // 双线性插值
                val x0 = srcX.toInt()
                val y0 = srcY.toInt()
                val x1 = x0 + 1
                val y1 = y0 + 1
                val fx = srcX - x0
                val fy = srcY - y0
                
                if (x0 >= 0 && x1 < w && y0 >= 0 && y1 < h) {
                    val p00 = srcPixels[y0 * w + x0]
                    val p01 = srcPixels[y0 * w + x1]
                    val p10 = srcPixels[y1 * w + x0]
                    val p11 = srcPixels[y1 * w + x1]
                    
                    val r = bilinear(p00, p01, p10, p11, fx, fy, 16)
                    val g = bilinear(p00, p01, p10, p11, fx, fy, 8)
                    val b = bilinear(p00, p01, p10, p11, fx, fy, 0)
                    result[y * w + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
        }
        
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(result, 0, w, 0, 0, w, h)
        return bitmap
    }
    
    private fun bilinear(p00: Int, p01: Int, p10: Int, p11: Int, fx: Float, fy: Float, shift: Int): Int {
        val v00 = (p00 ushr shift) and 0xFF
        val v01 = (p01 ushr shift) and 0xFF
        val v10 = (p10 ushr shift) and 0xFF
        val v11 = (p11 ushr shift) and 0xFF
        val top = v00 * (1 - fx) + v01 * fx
        val bot = v10 * (1 - fx) + v11 * fx
        return (top * (1 - fy) + bot * fy).toInt().coerceIn(0, 255)
    }
    
    companion object {
        /**
         * 根据焦距自动估算畸变参数（手动/估算回退）
         */
        fun autoEstimateParams(focalLength: Float): LensParams {
            return when {
                focalLength < 24f -> LensParams(k1 = 0.045f, k2 = 0.008f, p1 = 0.001f, p2 = 0.001f, scale = 1.08f)
                focalLength > 50f -> LensParams(k1 = -0.018f, k2 = 0.002f, p1 = -0.0003f, p2 = 0.0002f, scale = 0.98f)
                else -> LensParams(k1 = 0.01f, k2 = 0f, p1 = 0f, p2 = 0f, scale = 1.02f)
            }
        }

        /**
         * 使用镜头数据库进行校正。
         *
         * 优先从 LensDatabase 查找匹配的镜头配置并应用 ptlens + 渐晕 + TCA 校正；
         * 若数据库中未找到匹配，则回退到 Brown-Conrady 手动估算模式。
         *
         * @param image 输入 Bitmap
         * @param exif EXIF 数据（包含镜头 make/model/focalLength）
         * @return 校正后的 Bitmap
         */
        fun applyDatabaseCorrection(image: Bitmap, exif: ExifData): Bitmap {
            val make = exif.lensMake ?: exif.make
            val model = exif.lensModel
            val focalLength = exif.focalLength?.toFloatOrNull()

            // 尝试从数据库查找
            if (make != null && model != null) {
                val profile = if (focalLength != null && focalLength > 0f) {
                    LensDatabase.findProfileByFocalLength(make, model, focalLength)
                } else {
                    LensDatabase.findProfile(make, model)
                }

                if (profile != null) {
                    return LensCorrectionKernel.applyLensCorrection(
                        image, profile, focalLength ?: profile.focalMin
                    )
                }
            }

            // 回退：使用手动估算的 Brown-Conrady 参数
            if (focalLength != null && focalLength > 0f) {
                val params = autoEstimateParams(focalLength)
                return LensCorrector(
                    k1 = params.k1, k2 = params.k2,
                    p1 = params.p1, p2 = params.p2,
                    scale = params.scale
                ).correct(image)
            }

            // 无任何可用信息，返回原图
            return image
        }
    }
}

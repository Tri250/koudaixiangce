package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 镜头校正处理器 - 实现完整的Brown-Conrady畸变模型
 * 
 * 包含三种校正:
 * 1. 径向畸变校正 (Distortion Correction) - 桶形/枕形畸变
 *    使用Brown-Conrady模型的k1, k2, k3参数
 *    公式: r' = r(1 + k1*r^2 + k2*r^4 + k3*r^6)
 * 
 * 2. 横向色差校正 (Lateral Chromatic Aberration / TCA)
 *    边缘红/蓝色偏校正
 *    公式: R和B通道分别应用不同缩放因子
 * 
 * 3. 暗角校正 (Vignetting Correction)
 *    光学暗角补偿
 *    公式: 光学暗角近似为 cos^4(θ) 的衰减
 * 
 * 数学模型:
 * 
 * Brown-Conrady模型的完整公式:
 * 
 * x' = x(1 + k1*r^2 + k2*r^4 + k3*r^6) + 2*p1*x*y + p2*(r^2 + 2*x^2)
 * y' = y(1 + k1*r^2 + k2*r^4 + k3*r^6) + p1*(r^2 + 2*y^2) + 2*p2*x*y
 * 
 * 其中 r^2 = x^2 + y^2 (归一化坐标)
 * 
 * 反向映射用于避免空洞和重采样伪影。
 */
class LensCorrectionProcessor {

    /**
     * 镜头校正参数
     */
    @Serializable
    data class LensCorrectionParams(
        // 径向畸变系数 (Brown-Conrady model)
        val k1: Float = 0f,           // 二次项系数
        val k2: Float = 0f,           // 四次项系数
        val k3: Float = 0f,           // 六次项系数
        
        // 切向畸变系数
        val p1: Float = 0f,           // 切向畸变x方向
        val p2: Float = 0f,           // 切向畸变y方向
        
        // 横向色差 (TCA) 参数
        val tcaRed: Float = 0f,       // 红通道缩放偏移 (归一化)
        val tcaBlue: Float = 0f,      // 蓝通道缩放偏移 (归一化)
        
        // 暗角参数
        val vignetteK1: Float = 0f,   // 暗角系数1 (cos^4近似)
        val vignetteK2: Float = 0f,   // 暗角系数2 (更高阶)
        val vignetteK3: Float = 0f,   // 暗角系数3
        
        // 图像中心偏移
        val cx: Float = 0f,           // 光学中心x偏移 (归一化, 相对图像中心)
        val cy: Float = 0f,           // 光学中心y偏移
        
        // 缩放因子
        val scale: Float = 1f,        // 整体缩放
        
        // 焦距相关因子
        val focalLength: Float = 50f, // 焦距 (mm)
    ) {
        /**
         * 判断是否需要校正
         */
        fun needsCorrection(): Boolean {
            return abs(k1) > 1e-6f || abs(k2) > 1e-6f || abs(k3) > 1e-6f ||
                   abs(p1) > 1e-6f || abs(p2) > 1e-6f ||
                   abs(tcaRed) > 1e-6f || abs(tcaBlue) > 1e-6f ||
                   abs(vignetteK1) > 1e-6f || abs(vignetteK2) > 1e-6f
        }
        
        /**
         * 序列化为JSON字符串
         */
        fun toJson(): String = Json.encodeToString(serializer(), this)
        
        /**
         * 从JSON字符串反序列化
         */
        companion object {
            fun fromJson(json: String): LensCorrectionParams =
                Json.decodeFromString(serializer(), json)
        }
    }

    /**
     * 校正结果
     */
    data class CorrectionResult(
        val bitmap: Bitmap,
        val appliedParams: LensCorrectionParams,
        val effectiveCropRatio: Float,  // 实际裁剪比例
    )

    /**
     * 执行完整的镜头校正
     * 
     * @param source 输入图像
     * @param params 校正参数
     * @param enableDistortion 是否启用畸变校正
     * @param enableTca 是否启用色差校正
     * @param enableVignette 是否启用暗角校正
     * @return 校正后的图像
     */
    fun correct(
        source: Bitmap,
        params: LensCorrectionParams,
        enableDistortion: Boolean = true,
        enableTca: Boolean = true,
        enableVignette: Boolean = true,
    ): CorrectionResult {
        if (!params.needsCorrection()) {
            return CorrectionResult(source, params, 1f)
        }
        
        val w = source.width
        val h = source.height
        
        // 计算光学中心 (考虑偏移)
        val centerX = w / 2f + params.cx * w
        val centerY = h / 2f + params.cy * h
        
        // 最大半径 (用于归一化)
        val maxRadius = sqrt(centerX * centerX + centerY * centerY).coerceAtLeast(1f)
        
        // 获取源图像像素
        val srcPixels = IntArray(w * h)
        source.getPixels(srcPixels, 0, w, 0, 0, w, h)
        
        // 创建输出像素数组
        val resultPixels = IntArray(w * h) { Color.BLACK }
        
        // 计算有效裁剪比例
        var minCropRatio = 1f
        
        // 预计算暗角增益表 (如果启用)
        val vignetteGainTable = if (enableVignette) {
            FloatArray(w * h) { idx ->
                val x = idx % w
                val y = idx / w
                computeVignetteGain(x, y, centerX, centerY, maxRadius, params)
            }
        } else null
        
        // 反向映射遍历
        for (outY in 0 until h) {
            for (outX in 0 until w) {
                // 归一化坐标 (相对于光学中心)
                val nx = (outX - centerX) / maxRadius
                val ny = (outY - centerY) / maxRadius
                
                // 计算半径平方
                val r2 = nx * nx + ny * ny
                val r = sqrt(r2)
                
                // 计算畸变校正后的源坐标
                var srcNx = nx
                var srcNy = ny
                
                if (enableDistortion) {
                    // Brown-Conrady畸变校正
                    // 反向映射: 从输出坐标反推输入坐标
                    // 应用畸变模型: x_dist = x_undist * radial + tangential
                    
                    // 径向畸变因子
                    val radialDistortion = 1f + params.k1 * r2 + 
                                           params.k2 * r2 * r2 + 
                                           params.k3 * r2 * r2 * r2
                    
                    // 切向畸变
                    val tangentialX = 2f * params.p1 * nx * ny + 
                                     params.p2 * (r2 + 2f * nx * nx)
                    val tangentialY = params.p1 * (r2 + 2f * ny * ny) + 
                                     2f * params.p2 * nx * ny
                    
                    // 反向畸变 (校正畸变)
                    // 使用牛顿迭代法求解反向映射
                    // 简化: 直接使用逆变换近似
                    srcNx = nx / radialDistortion - tangentialX
                    srcNy = ny / radialDistortion - tangentialY
                    
                    // 应用缩放
                    srcNx *= params.scale
                    srcNy *= params.scale
                    
                    // 更新半径
                    val srcR2 = srcNx * srcNx + srcNy * srcNy
                    val srcR = sqrt(srcR2)
                    
                    // 检查边界并更新裁剪比例
                    val srcX = srcNx * maxRadius + centerX
                    val srcY = srcNy * maxRadius + centerY
                    if (srcX < 0 || srcX >= w || srcY < 0 || srcY >= h) {
                        // 更新裁剪比例
                        val cropRatio = (outX - centerX).absoluteValue / centerX.coerceAtLeast(1f)
                        minCropRatio = minOf(minCropRatio, cropRatio)
                    }
                }
                
                // 转换回像素坐标
                var srcX = srcNx * maxRadius + centerX
                var srcY = srcNy * maxRadius + centerY
                
                // 色差校正 - 分别采样R和B通道
                var rValue = 0f
                var gValue = 0f
                var bValue = 0f
                
                if (enableTca && (abs(params.tcaRed) > 1e-6f || abs(params.tcaBlue) > 1e-6f)) {
                    // 红通道采样
                    val srcXRed = srcX + params.tcaRed * srcNx * maxRadius * 0.5f
                    val srcYRed = srcY + params.tcaRed * srcNy * maxRadius * 0.5f
                    rValue = sampleBilinear(srcPixels, w, h, srcXRed, srcYRed, 16)
                    
                    // 绿通道 (中心采样)
                    gValue = sampleBilinear(srcPixels, w, h, srcX, srcY, 8)
                    
                    // 蓝通道采样
                    val srcXBlue = srcX + params.tcaBlue * srcNx * maxRadius * 0.5f
                    val srcYBlue = srcY + params.tcaBlue * srcNy * maxRadius * 0.5f
                    bValue = sampleBilinear(srcPixels, w, h, srcXBlue, srcYBlue, 0)
                } else {
                    // 无色差校正, 统一采样
                    rValue = sampleBilinear(srcPixels, w, h, srcX, srcY, 16)
                    gValue = sampleBilinear(srcPixels, w, h, srcX, srcY, 8)
                    bValue = sampleBilinear(srcPixels, w, h, srcX, srcY, 0)
                }
                
                // 暗角校正
                if (enableVignette && vignetteGainTable != null) {
                    val gain = vignetteGainTable[outY * w + outX]
                    rValue = (rValue * gain).coerceIn(0f, 255f)
                    gValue = (gValue * gain).coerceIn(0f, 255f)
                    bValue = (bValue * gain).coerceIn(0f, 255f)
                }
                
                // 写入结果
                resultPixels[outY * w + outX] = 
                    (0xFF shl 24) or 
                    (rValue.toInt() shl 16) or 
                    (gValue.toInt() shl 8) or 
                    bValue.toInt()
            }
        }
        
        // 创建输出Bitmap
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(resultPixels, 0, w, 0, 0, w, h)
        
        return CorrectionResult(result, params, minCropRatio)
    }

    /**
     * 计算暗角增益
     * 
     * 光学暗角公式: V(r) = 1 - k1*r^2 - k2*r^4 - k3*r^6
     * 增益: G = 1 / V(r)
     * 
     * 使用cos^4近似:
     * V(r) ≈ cos^4(θ) = (1/(1+r^2/f^2))^2
     * 对于归一化坐标, 焦距因子会影响衰减曲线
     */
    private fun computeVignetteGain(
        x: Float, y: Float,
        centerX: Float, centerY: Float,
        maxRadius: Float,
        params: LensCorrectionParams,
    ): Float {
        val dx = x - centerX
        val dy = y - centerY
        val r2 = (dx * dx + dy * dy) / (maxRadius * maxRadius)
        
        // 暗角衰减计算
        // 使用近似公式: V = 1 + k1*r^2 + k2*r^4 + k3*r^6
        // 增益为逆衰减
        val vignetteAmount = 1f + params.vignetteK1 * r2 + 
                             params.vignetteK2 * r2 * r2 + 
                             params.vignetteK3 * r2 * r2 * r2
        
        // 考虑焦距因子 (短焦镜头暗角更明显)
        val focalFactor = if (params.focalLength > 0) {
            // 焦距越短, 暗角增益需要更强
            50f / params.focalLength.coerceIn(10f, 200f)
        } else 1f
        
        // 增益限制 (避免过度增益导致噪声)
        val gain = vignetteAmount * focalFactor
        return gain.coerceIn(1f, 3f)
    }

    /**
     * 双线性插值采样
     */
    private fun sampleBilinear(
        pixels: IntArray,
        width: Int,
        height: Int,
        x: Float,
        y: Float,
        shift: Int,
    ): Float {
        // 边界镜像处理
        val x0 = x.toInt().coerceIn(0, width - 2)
        val y0 = y.toInt().coerceIn(0, height - 2)
        val x1 = (x0 + 1).coerceIn(0, width - 1)
        val y1 = (y0 + 1).coerceIn(0, height - 1)
        
        val fx = (x - x0).coerceIn(0f, 1f)
        val fy = (y - y0).coerceIn(0f, 1f)
        
        val p00 = pixels[y0 * width + x0]
        val p01 = pixels[y0 * width + x1]
        val p10 = pixels[y1 * width + x0]
        val p11 = pixels[y1 * width + x1]
        
        val v00 = (p00 ushr shift) and 0xFF
        val v01 = (p01 ushr shift) and 0xFF
        val v10 = (p10 ushr shift) and 0xFF
        val v11 = (p11 ushr shift) and 0xFF
        
        val top = v00 * (1f - fx) + v01 * fx
        val bottom = v10 * (1f - fx) + v11 * fx
        
        return top * (1f - fy) + bottom * fy
    }

    /**
     * 估算畸变参数 (基于焦距)
     * 
     * 一般规律:
     * - 广角镜头 (焦距 < 35mm): 桶形畸变 (k1 > 0)
     * - 长焦镜头 (焦距 > 85mm): 枕形畸变 (k1 < 0)
     * - 标准镜头 (35-85mm): 畸变较小
     */
    fun estimateParamsFromFocalLength(focalLength: Float): LensCorrectionParams {
        return when {
            focalLength < 16f -> {
                // 超广角 - 强桶形畸变
                LensCorrectionParams(
                    k1 = 0.12f,
                    k2 = 0.02f,
                    k3 = 0.005f,
                    p1 = 0.002f,
                    p2 = 0.002f,
                    scale = 1.15f,
                    vignetteK1 = 0.4f,
                    vignetteK2 = 0.15f,
                    vignetteK3 = 0.05f,
                    focalLength = focalLength,
                )
            }
            focalLength < 24f -> {
                // 广角 - 中等桶形畸变
                LensCorrectionParams(
                    k1 = 0.06f,
                    k2 = 0.01f,
                    k3 = 0.001f,
                    p1 = 0.001f,
                    p2 = 0.001f,
                    scale = 1.08f,
                    vignetteK1 = 0.25f,
                    vignetteK2 = 0.08f,
                    vignetteK3 = 0.02f,
                    focalLength = focalLength,
                )
            }
            focalLength < 35f -> {
                // 轻广角 - 轻桶形畸变
                LensCorrectionParams(
                    k1 = 0.02f,
                    k2 = 0.003f,
                    k3 = 0f,
                    p1 = 0.0005f,
                    p2 = 0.0005f,
                    scale = 1.04f,
                    vignetteK1 = 0.12f,
                    vignetteK2 = 0.03f,
                    vignetteK3 = 0f,
                    focalLength = focalLength,
                )
            }
            focalLength < 85f -> {
                // 标准镜头 - 几乎无畸变
                LensCorrectionParams(
                    k1 = 0f,
                    k2 = 0f,
                    k3 = 0f,
                    p1 = 0f,
                    p2 = 0f,
                    scale = 1f,
                    vignetteK1 = 0.05f,
                    vignetteK2 = 0f,
                    vignetteK3 = 0f,
                    focalLength = focalLength,
                )
            }
            focalLength < 135f -> {
                // 中长焦 - 轻枕形畸变
                LensCorrectionParams(
                    k1 = -0.01f,
                    k2 = 0.001f,
                    k3 = 0f,
                    p1 = -0.0003f,
                    p2 = 0.0002f,
                    scale = 0.98f,
                    vignetteK1 = 0.02f,
                    vignetteK2 = 0f,
                    vignetteK3 = 0f,
                    focalLength = focalLength,
                )
            }
            else -> {
                // 长焦 - 枕形畸变
                LensCorrectionParams(
                    k1 = -0.02f,
                    k2 = 0.002f,
                    k3 = -0.0005f,
                    p1 = -0.0005f,
                    p2 = 0.0003f,
                    scale = 0.96f,
                    vignetteK1 = 0f,
                    vignetteK2 = 0f,
                    vignetteK3 = 0f,
                    focalLength = focalLength,
                )
            }
        }
    }

    /**
     * 畸变网格可视化
     * 用于预览畸变效果
     */
    fun generateDistortionGrid(
        width: Int,
        height: Int,
        params: LensCorrectionParams,
        gridSize: Int = 20,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height) { Color.BLACK }
        
        val centerX = width / 2f + params.cx * width
        val centerY = height / 2f + params.cy * height
        val maxRadius = sqrt(centerX * centerX + centerY * centerY)
        
        // 绘制网格线
        val gridColor = Color.WHITE
        val gridStepX = width / gridSize
        val gridStepY = height / gridSize
        
        for (i in 0..gridSize) {
            // 垂直线
            for (j in 0 until height) {
                val origX = i * gridStepX
                val nx = (origX - centerX) / maxRadius
                val ny = (j - centerY) / maxRadius
                val r2 = nx * nx + ny * ny
                
                val radialDistortion = 1f + params.k1 * r2 + 
                                       params.k2 * r2 * r2 + 
                                       params.k3 * r2 * r2 * r2
                
                val srcNx = nx / radialDistortion * params.scale
                val srcX = (srcNx * maxRadius + centerX).toInt().coerceIn(0, width - 1)
                
                if (srcX >= 0 && srcX < width) {
                    pixels[j * width + srcX] = gridColor
                }
            }
            
            // 水平线
            for (j in 0 until width) {
                val origY = i * gridStepY
                val nx = (j - centerX) / maxRadius
                val ny = (origY - centerY) / maxRadius
                val r2 = nx * nx + ny * ny
                
                val radialDistortion = 1f + params.k1 * r2 + 
                                       params.k2 * r2 * r2 + 
                                       params.k3 * r2 * r2 * r2
                
                val srcNy = ny / radialDistortion * params.scale
                val srcY = (srcNy * maxRadius + centerY).toInt().coerceIn(0, height - 1)
                
                if (srcY >= 0 && srcY < height) {
                    pixels[srcY * width + j] = gridColor
                }
            }
        }
        
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    /**
     * 计算畸变强度因子 (用于UI显示)
     */
    fun computeDistortionFactor(params: LensCorrectionParams): Float {
        // 在边缘位置 (r = 1) 计算畸变程度
        val r2 = 1f
        val radial = 1f + params.k1 * r2 + params.k2 * r2 * r2 + params.k3 * r2 * r2 * r2
        return (radial - 1f) * 100f
    }

    /**
     * 计算色差强度因子
     */
    fun computeTcaFactor(params: LensCorrectionParams): Float {
        return (abs(params.tcaRed) + abs(params.tcaBlue)) * 50f
    }

    /**
     * 计算暗角强度因子
     */
    fun computeVignetteFactor(params: LensCorrectionParams): Float {
        val r2 = 1f
        val vignette = 1f + params.vignetteK1 * r2 + params.vignetteK2 * r2 * r2 + params.vignetteK3 * r2 * r2 * r2
        return (vignette - 1f) * 100f
    }

    companion object {
        const val MIN_K1 = -0.5f
        const val MAX_K1 = 0.5f
        const val MIN_K2 = -0.2f
        const val MAX_K2 = 0.2f
        const val MIN_K3 = -0.1f
        const val MAX_K3 = 0.1f
        
        const val MIN_TCA = -0.05f
        const val MAX_TCA = 0.05f
        
        const val MIN_VIGNETTE = 0f
        const val MAX_VIGNETTE = 1f
    }
}
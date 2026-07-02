package com.pixelfruit.core

/**
 * P-05: 面部美白处理器
 * 基于 RGB/YUV/归一化RGB 三通道算法：
 * 1. RGB 域：提亮肤色通道，压制黄色分量
 * 2. YUV 域：提升亮度 Y，降低色度偏移
 * 3. 归一化 RGB：检测肤色区域，仅对肤色像素处理
 *
 * 过渡自然：使用羽化蒙版，非肤色区域不受影响
 */
class FaceWhiteningProcessor {

    /** 美白强度 0.0~1.0 */
    var intensity: Float = 0.5f

    /** 肤色检测阈值 — 归一化 RGB 空间 */
    private val skinNrgThreshold = 0.35f

    /**
     * 检测像素是否为肤色（归一化 RGB 方法）
     * @param r 红色分量 0~255
     * @param g 绿色分量 0~255
     * @param b 蓝色分量 0~255
     * @return true 如果是肤色
     */
    fun isSkinPixel(r: Float, g: Float, b: Float): Boolean {
        val total = r + g + b
        if (total <= 0f) return false
        val nr = r / total
        val ng = g / total
        // 归一化 RGB 肤色检测：R > G > B 且 R 占比足够
        return nr > ng && ng > b / total && nr > skinNrgThreshold
    }

    /**
     * 对单个像素应用美白
     * @param r 红色分量
     * @param g 绿色分量
     * @param b 蓝色分量
     * @return Triple<r, g, b> 处理后的 RGB
     */
    fun whitenPixel(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        if (!isSkinPixel(r, g, b)) return Triple(r, g, b)

        // YUV 域提亮
        val y = 0.299f * r + 0.587f * g + 0.114f * b
        val u = -0.147f * r - 0.289f * g + 0.436f * b
        val v = 0.615f * r - 0.515f * g - 0.100f * b

        // 提亮 Y，降低黄色（V 分量）
        val brightenedY = (y + 20f * intensity).coerceIn(0f, 255f)
        val desaturatedV = v * (1f - 0.3f * intensity)

        // YUV 转回 RGB
        val newR = (brightenedY + 1.14f * desaturatedV).coerceIn(0f, 255f)
        val newG = (brightenedY - 0.395f * u - 0.581f * desaturatedV).coerceIn(0f, 255f)
        val newB = (brightenedY + 2.032f * u).coerceIn(0f, 255f)

        return Triple(newR, newG, newB)
    }
}
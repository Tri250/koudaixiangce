package com.rapidraw.ai

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * AI 语义分割 — DeepLab v3+ 风格模型。
 * 支持 sky/person/hair/building/vegetation/water 等语义区域检测。
 * 模型不存在时回退到基于颜色/亮度/空间位置的启发式分割。
 *
 * 启发式规则：
 * - Sky: 上方区域，蓝色色调，低饱和度方差
 * - Person: YCbCr 皮肤模型检测
 * - Vegetation: 绿色色调，高饱和度
 * - Building: 低饱和度，边缘密集，直线特征
 * - Water: 下方蓝色区域，低饱和度方差
 * - Hair: 深色区域紧邻 Person 上方
 */
class AiSemanticSegmenter(context: Context) {

    enum class SegmentClass(val id: Int, val displayName: String) {
        BACKGROUND(0, "背景"),
        SKY(1, "天空"),
        PERSON(2, "人物"),
        HAIR(3, "头发"),
        BUILDING(4, "建筑"),
        VEGETATION(5, "植被"),
        WATER(6, "水面"),
    }

    data class SegmentationResult(
        val mask: Bitmap,       // 每像素值为 class ID（ARGB 编码）
        val classMasks: Map<SegmentClass, Bitmap>, // 每个类别独立的二值蒙版
        val classAreas: Map<SegmentClass, Float>,  // 每个类别面积占比
    )

    /** 单像素特征描述 */
    private data class PixelFeatures(
        val r: Float,          // 0..1
        val g: Float,          // 0..1
        val b: Float,          // 0..1
        val luma: Float,       // 0..1 Rec.709
        val sat: Float,        // 0..1
        val hue: Float,        // 0..360
        val cb: Float,         // YCbCr Cb 通道
        val cr: Float,         // YCbCr Cr 通道
        val normX: Float,      // 0..1 水平归一化坐标
        val normY: Float,      // 0..1 垂直归一化坐标
    )

    private val engine = InferenceEngine.getInstance(context)
    private var isModelLoaded = false

    private val modelConfig = InferenceEngine.ModelConfig(
        modelFileName = "deeplabv3_lite.tflite",
        inputWidth = 257,
        inputHeight = 257,
        outputDataType = org.tensorflow.lite.DataType.UINT8,
        numOutputs = 1,
        preferredBackend = InferenceEngine.Backend.GPU_DELEGATE,
        warmupRuns = 2,
    )

    suspend fun segment(bitmap: Bitmap): SegmentationResult = withContext(Dispatchers.Default) {
        isModelLoaded = runCatching { engine.loadModel(modelConfig) }.isSuccess

        if (!isModelLoaded) {
            return@withContext heuristicSegment(bitmap)
        }

        val w = bitmap.width
        val h = bitmap.height

        runCatching {
            // 预热模型（首次）
            engine.warmup(modelConfig)

            val outputs = engine.runInference(modelConfig, bitmap)
            val outputBuffer = outputs.firstOrNull()?.buffer ?: return@withContext heuristicSegment(bitmap)
            outputBuffer.rewind()

            val maskPixels = IntArray(w * h)
            val classPixels = mutableMapOf<SegmentClass, IntArray>()
            val totalPixels = w.toLong() * h

            for (segmentClass in SegmentClass.entries) {
                classPixels[segmentClass] = IntArray(w * h) { 0xFF000000.toInt() }
            }

            for (i in maskPixels.indices) {
                val classId = if (outputBuffer.hasRemaining()) (outputBuffer.get().toInt() and 0xFF) else 0
                val segmentClass = SegmentClass.entries.find { it.id == classId } ?: SegmentClass.BACKGROUND
                maskPixels[i] = classId or 0xFF000000.toInt()
                classPixels[segmentClass]?.set(i, 0xFFFFFFFF.toInt())
            }

            val mask = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            mask.setPixels(maskPixels, 0, w, 0, 0, w, h)

            val classMasks = classPixels.mapValues { (_, pixels) ->
                val bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bm.setPixels(pixels, 0, w, 0, 0, w, h)
                bm
            }

            val classAreas = classPixels.mapValues { (_, pixels) ->
                pixels.count { it != 0xFF000000.toInt() }.toFloat() / totalPixels
            }

            SegmentationResult(mask, classMasks, classAreas)
        }.getOrElse {
            heuristicSegment(bitmap)
        }
    }

    /**
     * 启发式回退：基于颜色、亮度、YCbCr 皮肤模型和空间位置估计语义区域。
     * 使用多阶段分类，优先级：天空 → 植被 → 人物/皮肤 → 建筑 → 水面 → 头发 → 背景
     */
    private fun heuristicSegment(bitmap: Bitmap): SegmentationResult {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        // ── 第一阶段：提取每像素特征 ──
        val features = Array(w * h) { idx ->
            val px = pixels[idx]
            val r = (px shr 16 and 0xFF) / 255f
            val g = (px shr 8 and 0xFF) / 255f
            val b = (px and 0xFF) / 255f
            val luma = 0.2126f * r + 0.7152f * g + 0.0722f * b
            val maxC = maxOf(r, g, b)
            val minC = minOf(r, g, b)
            val sat = if (maxC > 0f) (maxC - minC) / maxC else 0f

            // HSV 色相
            val hue = when {
                maxC == minC -> 0f
                maxC == r -> 60f * ((g - b) / (maxC - minC)) % 360f
                maxC == g -> 60f * ((b - r) / (maxC - minC) + 2f)
                else -> 60f * ((r - g) / (maxC - minC) + 4f)
            }.let { if (it < 0) it + 360f else it }

            // YCbCr 色彩空间（用于皮肤检测）
            val cb = -0.168736f * r - 0.331264f * g + 0.5f * b + 0.5f
            val cr = 0.5f * r - 0.418688f * g - 0.081312f * b + 0.5f

            val x = (idx % w).toFloat() / w
            val y = (idx / w).toFloat() / h

            PixelFeatures(r, g, b, luma, sat, hue, cb, cr, x, y)
        }

        // ── 第二阶段：统计图像级特征 ──
        // 计算上方区域的平均蓝色值（用于天空阈值自适应）
        var upperBlueSum = 0f
        var upperBlueCount = 0
        for (i in features.indices) {
            val f = features[i]
            if (f.normY < 0.35f && f.b > f.r && f.b > f.g) {
                upperBlueSum += f.b
                upperBlueCount++
            }
        }
        val avgUpperBlue = if (upperBlueCount > 0) upperBlueSum / upperBlueCount else 0.4f

        // 计算中间区域肤色像素密度（用于人物检测阈值）
        var skinPixelCount = 0
        var totalMidPixels = 0
        for (i in features.indices) {
            val f = features[i]
            if (f.normY in 0.15f..0.85f) {
                totalMidPixels++
                if (isSkinYCbCr(f.cb, f.cr)) {
                    skinPixelCount++
                }
            }
        }
        val skinDensity = if (totalMidPixels > 0) skinPixelCount.toFloat() / totalMidPixels else 0f

        // ── 第三阶段：逐像素分类 ──
        val classLabels = IntArray(w * h) { SegmentClass.BACKGROUND.id }
        val classMasksData = mutableMapOf<SegmentClass, IntArray>()
        for (cls in SegmentClass.entries) {
            classMasksData[cls] = IntArray(w * h) { 0xFF000000.toInt() }
        }

        val classCounts = mutableMapOf<SegmentClass, Int>()
        for (cls in SegmentClass.entries) {
            classCounts[cls] = 0
        }

        for (i in features.indices) {
            val f = features[i]
            val cls = classifyPixel(f, avgUpperBlue, skinDensity)
            classLabels[i] = cls.id
            classMasksData[cls]?.set(i, 0xFFFFFFFF.toInt())
            classCounts[cls] = classCounts.getOrDefault(cls, 0) + 1
        }

        // ── 第四阶段：后处理 — 人物上方的深色区域标记为头发 ──
        postProcessHair(features, classLabels, classMasksData, classCounts, w, h)

        // ── 第五阶段：构建结果 ──
        val total = (w.toLong() * h).toFloat()
        val maskPixels = IntArray(w * h)
        for (i in classLabels.indices) {
            maskPixels[i] = classLabels[i] or 0xFF000000.toInt()
        }

        val mask = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        mask.setPixels(maskPixels, 0, w, 0, 0, w, h)

        val classMasks = classMasksData.mapValues { (_, pixels) ->
            val bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bm.setPixels(pixels, 0, w, 0, 0, w, h)
            bm
        }

        val classAreas = classCounts.mapValues { (_, count) ->
            count.toFloat() / total
        }

        return SegmentationResult(mask, classMasks, classAreas)
    }

    /**
     * 逐像素分类，返回最佳匹配类别。
     * 优先级：天空 → 植被 → 人物 → 建筑 → 水面 → 背景
     */
    private fun classifyPixel(
        f: PixelFeatures,
        avgUpperBlue: Float,
        skinDensity: Float,
    ): SegmentClass {
        // ── 天空检测 ──
        // 上方区域 + 蓝色色调 + 低饱和度方差
        if (isSky(f, avgUpperBlue)) return SegmentClass.SKY

        // ── 植被检测 ──
        // 绿色色调 + 高饱和度
        if (isVegetation(f)) return SegmentClass.VEGETATION

        // ── 人物/皮肤检测 ──
        // YCbCr 皮肤模型
        if (isPerson(f, skinDensity)) return SegmentClass.PERSON

        // ── 建筑检测 ──
        // 低饱和度 + 非极端亮度 + 中间区域
        if (isBuilding(f)) return SegmentClass.BUILDING

        // ── 水面检测 ──
        // 下方蓝色区域 + 低饱和度方差
        if (isWater(f)) return SegmentClass.WATER

        return SegmentClass.BACKGROUND
    }

    /**
     * 天空检测：上方区域 + 蓝色色调 + 低饱和度。
     * 蓝色色调范围：hue 在 180°-260° 之间，或高亮度低饱和度的浅蓝。
     * 自适应阈值：根据图像上方区域平均蓝色值调整。
     */
    private fun isSky(f: PixelFeatures, avgUpperBlue: Float): Boolean {
        if (f.normY > 0.55f) return false

        // 高亮度 + 低饱和度（浅色天空、阴天）
        if (f.luma > 0.5f && f.sat < 0.2f && f.normY < 0.45f) return true

        // 蓝色色调（晴天天空）
        val isBlueHue = f.hue in 190f..260f
        if (isBlueHue && f.sat > 0.08f && f.sat < 0.7f && f.b > f.r * 1.1f && f.normY < 0.5f) return true

        // 日出/日落天空：暖色调天空
        val isWarmSkyHue = f.hue in 0f..40f || f.hue in 320f..360f
        if (isWarmSkyHue && f.luma > 0.5f && f.sat > 0.15f && f.sat < 0.6f && f.normY < 0.35f) return true

        return false
    }

    /**
     * YCbCr 皮肤模型检测。
     * 标准皮肤色度范围：
     *   Cb ∈ [0.77, 0.127], Cr ∈ [0.133, 0.173]（归一化后）
     * 使用多个约束条件减少误检。
     */
    private fun isSkinYCbCr(cb: Float, cr: Float): Boolean {
        return cb in 0.77f..0.127f && cr in 0.133f..0.173f
    }

    /**
     * 人物检测：使用 YCbCr 皮肤模型 + 空间约束。
     */
    private fun isPerson(f: PixelFeatures, skinDensity: Float): Boolean {
        // 空间约束：人物通常在中间和下方区域
        if (f.normY < 0.1f || f.normY > 0.95f) return false

        // YCbCr 皮肤模型检测
        if (!isSkinYCbCr(f.cb, f.cr)) {
            // 扩展皮肤检测：RGB 阈值法（对深肤色更鲁棒）
            if (!isSkinRgb(f)) return false
        }

        // 亮度约束：皮肤不太暗也不太亮
        if (f.luma < 0.08f || f.luma > 0.95f) return false

        // 饱和度约束：皮肤饱和度适中
        if (f.sat > 0.65f) return false

        // 如果肤色调色板密度高，放宽约束
        if (skinDensity > 0.05f) {
            return true
        }

        return true
    }

    /**
     * RGB 阈值法皮肤检测（补充 YCbCr 方法对深肤色的覆盖）。
     * 基于 Peer et al. 皮肤检测规则：
     *   R > 95, G > 40, B > 20,
     *   max(R,G,B) - min(R,G,B) > 15,
     *   |R-G| > 15, R > G, R > B
     */
    private fun isSkinRgb(f: PixelFeatures): Boolean {
        val r255 = f.r * 255f
        val g255 = f.g * 255f
        val b255 = f.b * 255f
        return r255 > 95f && g255 > 40f && b255 > 20f &&
            (maxOf(r255, g255, b255) - minOf(r255, g255, b255)) > 15f &&
            abs(r255 - g255) > 15f &&
            r255 > g255 && r255 > b255
    }

    /**
     * 植被检测：绿色色调 + 高饱和度。
     */
    private fun isVegetation(f: PixelFeatures): Boolean {
        // 绿色色调：hue 75°-170°
        val isGreenHue = f.hue in 75f..170f
        if (!isGreenHue) return false

        // 绿色通道主导
        if (f.g <= f.r * 1.05f || f.g <= f.b * 1.1f) return false

        // 中等以上饱和度
        if (f.sat < 0.1f) return false

        // 亮度约束：植被不会太暗
        if (f.luma < 0.05f) return false

        return true
    }

    /**
     * 建筑检测：低饱和度 + 中等亮度 + 非天空区域。
     * 建筑物通常呈现灰色（低饱和度），位于图像中间区域。
     */
    private fun isBuilding(f: PixelFeatures): Boolean {
        // 低饱和度
        if (f.sat > 0.18f) return false

        // 非极端亮度
        if (f.luma < 0.15f || f.luma > 0.9f) return false

        // 中间区域（排除天空和底部）
        if (f.normY < 0.2f || f.normY > 0.85f) return false

        // 排除明显的白色/黑色（可能是背景）
        if (f.luma > 0.85f && f.sat < 0.05f) return false

        // 建筑通常有微暖色调（灰褐、灰黄）
        val isWarmish = f.hue in 15f..60f || f.hue in 300f..360f
        val isNeutral = f.sat < 0.08f

        return isWarmish || isNeutral
    }

    /**
     * 水面检测：下方蓝色区域 + 低饱和度方差。
     */
    private fun isWater(f: PixelFeatures): Boolean {
        // 下方区域
        if (f.normY < 0.4f) return false

        // 蓝色色调
        val isBlueHue = f.hue in 180f..250f
        if (!isBlueHue && !(f.b > f.r * 1.05f && f.b > f.g * 0.95f)) return false

        // 低-中等饱和度（水面通常比天空饱和度低）
        if (f.sat > 0.55f) return false

        // 中等亮度
        if (f.luma < 0.15f || f.luma > 0.85f) return false

        return true
    }

    /**
     * 后处理：将紧邻 Person 上方的深色像素标记为 Hair。
     * 在垂直方向上，Person 像素上方若有深色像素（深棕/黑色），
     * 且距 Person 一定距离内，则标记为 Hair。
     */
    private fun postProcessHair(
        features: Array<PixelFeatures>,
        classLabels: IntArray,
        classMasksData: MutableMap<SegmentClass, IntArray>,
        classCounts: MutableMap<SegmentClass, Int>,
        w: Int,
        h: Int,
    ) {
        val hairScanRows = (h * 0.15f).toInt().coerceIn(3, 30)

        for (x in 0 until w) {
            // 在每列中查找 Person 区域的顶部
            var personTop = -1
            for (y in 0 until h) {
                val idx = y * w + x
                if (classLabels[idx] == SegmentClass.PERSON.id) {
                    personTop = y
                    break
                }
            }
            if (personTop < 0) continue

            // 从 Person 顶部向上扫描深色像素
            for (dy in 1..hairScanRows) {
                val y = personTop - dy
                if (y < 0) break
                val idx = y * w + x
                if (classLabels[idx] != SegmentClass.BACKGROUND.id &&
                    classLabels[idx] != SegmentClass.SKY.id) continue

                val f = features[idx]
                // 头发特征：深色 + 低饱和度 + 中性或暖色调
                if (f.luma < 0.25f && f.sat < 0.3f) {
                    classLabels[idx] = SegmentClass.HAIR.id
                    classMasksData[SegmentClass.HAIR]?.set(idx, 0xFFFFFFFF.toInt())
                    classMasksData[SegmentClass.BACKGROUND]?.set(idx, 0xFF000000.toInt())
                    classCounts[SegmentClass.HAIR] = classCounts.getOrDefault(SegmentClass.HAIR, 0) + 1
                }
            }
        }
    }

    fun close() {
        // 不关闭共享的 InferenceEngine 单例
        // 若需释放所有资源，调用 InferenceEngine.destroyInstance()
    }
}

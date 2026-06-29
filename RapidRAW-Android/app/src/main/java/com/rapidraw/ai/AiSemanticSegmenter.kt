package com.rapidraw.ai

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AI 语义分割 — DeepLab v3+ 风格模型。
 * 支持 sky/person/foreground/hair/building 等语义区域检测。
 * 模型不存在时回退到颜色/亮度启发式分割。
 */
class AiSemanticSegmenter(context: Context) {

    enum class SegmentClass(val id: Int) {
        BACKGROUND(0),
        SKY(1),
        PERSON(2),
        HAIR(3),
        BUILDING(4),
        VEGETATION(5),
        WATER(6),
    }

    data class SegmentationResult(
        val mask: Bitmap,       // 每像素值为 class ID
        val classMasks: Map<SegmentClass, Bitmap>, // 每个类别独立的二值蒙版
        val classAreas: Map<SegmentClass, Float>,  // 每个类别面积占比
    )

    private val engine = InferenceEngine(context)
    private var isModelLoaded = false

    private val modelConfig = InferenceEngine.ModelConfig(
        modelFileName = "deeplabv3_lite.tflite",
        inputWidth = 257,
        inputHeight = 257,
        outputDataType = org.tensorflow.lite.DataType.UINT8,
        numOutputs = 1,
        preferredBackend = InferenceEngine.Backend.GPU_DELEGATE,
    )

    suspend fun segment(bitmap: Bitmap): SegmentationResult = withContext(Dispatchers.Default) {
        isModelLoaded = runCatching { engine.loadModel(modelConfig) }.isSuccess

        if (!isModelLoaded) {
            return@withContext heuristicSegment(bitmap)
        }

        val w = bitmap.width
        val h = bitmap.height

        runCatching {
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
     * 启发式回退：基于亮度和位置估计 sky/person/vegetation
     */
    private fun heuristicSegment(bitmap: Bitmap): SegmentationResult {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val skyMask = IntArray(w * h) { 0xFF000000.toInt() }
        val personMask = IntArray(w * h) { 0xFF000000.toInt() }
        val vegMask = IntArray(w * h) { 0xFF000000.toInt() }
        val mainMask = IntArray(w * h) { 0xFF000000.toInt() }
        var skyCount = 0; var personCount = 0; var vegCount = 0

        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                val px = pixels[idx]
                val r = (px shr 16 and 0xFF) / 255f
                val g = (px shr 8 and 0xFF) / 255f
                val b = (px and 0xFF) / 255f
                val luma = 0.299f * r + 0.587f * g + 0.114f * b
                val sat = kotlin.math.max(r, g, b) - kotlin.math.min(r, g, b)
                val isUpper = y < h * 0.45f

                // Sky: high brightness, low saturation, upper portion
                if (isUpper && luma > 0.55f && sat < 0.2f) {
                    skyMask[idx] = 0xFFFFFFFF.toInt()
                    mainMask[idx] = SegmentClass.SKY.id or 0xFF000000.toInt()
                    skyCount++
                }
                // Vegetation: high green component
                else if (g > r * 1.2f && g > b * 1.3f && sat > 0.1f) {
                    vegMask[idx] = 0xFFFFFFFF.toInt()
                    mainMask[idx] = SegmentClass.VEGETATION.id or 0xFF000000.toInt()
                    vegCount++
                }
                // Person: skin-tone colors in middle-lower portion
                else if (y > h * 0.15f && y < h * 0.9f &&
                    r > 0.35f && g > 0.25f && b > 0.15f &&
                    r > g && r > b && sat < 0.5f && luma in 0.2f..0.8f) {
                    personMask[idx] = 0xFFFFFFFF.toInt()
                    mainMask[idx] = SegmentClass.PERSON.id or 0xFF000000.toInt()
                    personCount++
                }
            }
        }

        val total = (w.toLong() * h).toFloat()
        val mask = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        mask.setPixels(mainMask, 0, w, 0, 0, w, h)

        val skyBm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { it.setPixels(skyMask, 0, w, 0, 0, w, h) }
        val personBm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { it.setPixels(personMask, 0, w, 0, 0, w, h) }
        val vegBm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { it.setPixels(vegMask, 0, w, 0, 0, w, h) }

        SegmentationResult(
            mask = mask,
            classMasks = mapOf(
                SegmentClass.SKY to skyBm,
                SegmentClass.PERSON to personBm,
                SegmentClass.VEGETATION to vegBm,
            ),
            classAreas = mapOf(
                SegmentClass.SKY to skyCount / total,
                SegmentClass.PERSON to personCount / total,
                SegmentClass.VEGETATION to vegCount / total,
            ),
        )
    }

    fun close() = engine.close()
}

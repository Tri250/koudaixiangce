package com.alcedo.studio.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import com.alcedo.studio.data.model.Adjustments
import com.alcedo.studio.data.model.CropData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

class ImageProcessor(private val context: Context) {

    suspend fun loadImage(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val options = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                BitmapFactory.decodeStream(inputStream, null, options)
            }
        } catch (e: OutOfMemoryError) {
            L.e("ImageProcessor", "OOM loading image", e)
            BitmapManager.trimMemory(100)
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val options = BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                        inSampleSize = 2
                    }
                    BitmapFactory.decodeStream(inputStream, null, options)
                }
            } catch (e2: Exception) {
                L.e("ImageProcessor", "Failed to load image after trim", e2)
                null
            }
        } catch (e: Exception) {
            L.e("ImageProcessor", "Failed to load image", e)
            null
        }
    }

    suspend fun process(
        sourceBitmap: Bitmap,
        adjustments: Adjustments,
        targetWidth: Int = 0,
        targetHeight: Int = 0,
    ): Flow<ProcessingProgress> = flow {
        emit(ProcessingProgress(0f, "准备中..."))

        val width = sourceBitmap.width
        val height = sourceBitmap.height

        val pixels = IntArray(width * height)
        sourceBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var floatPixels = FloatArray(width * height * 3)
        for (i in 0 until width * height) {
            val idx = i * 3
            floatPixels[idx] = Color.red(pixels[i]) / 255f
            floatPixels[idx + 1] = Color.green(pixels[i]) / 255f
            floatPixels[idx + 2] = Color.blue(pixels[i]) / 255f
        }

        emit(ProcessingProgress(5f, "转换色彩空间..."))
        floatPixels = ColorMath.srgbToLinearBulk(floatPixels)

        var currentWidth = width
        var currentHeight = height

        emit(ProcessingProgress(10f, "镜头校正..."))
        floatPixels = LensCorrector.apply(floatPixels, currentWidth, currentHeight, adjustments)

        emit(ProcessingProgress(15f, "白平衡..."))
        floatPixels = WhiteBalanceProcessor.apply(floatPixels, currentWidth, currentHeight, adjustments)

        emit(ProcessingProgress(20f, "基础调色..."))
        floatPixels = BasicAdjustmentsProcessor.apply(floatPixels, currentWidth, currentHeight, adjustments)

        emit(ProcessingProgress(30f, "色调曲线..."))
        floatPixels = ToneCurveProcessor.apply(floatPixels, currentWidth, currentHeight, adjustments)

        emit(ProcessingProgress(40f, "HSL 调整..."))
        floatPixels = HslProcessor.apply(floatPixels, currentWidth, currentHeight, adjustments)

        emit(ProcessingProgress(50f, "色彩分级..."))
        floatPixels = ColorGradingProcessor.apply(floatPixels, currentWidth, currentHeight, adjustments)

        emit(ProcessingProgress(55f, "色彩校准..."))
        floatPixels = ColorCalibrationProcessor.apply(floatPixels, currentWidth, currentHeight, adjustments)

        emit(ProcessingProgress(60f, "胶片模拟..."))
        floatPixels = FilmSimulationProcessor.apply(floatPixels, currentWidth, currentHeight, adjustments)

        emit(ProcessingProgress(65f, "细节增强..."))
        floatPixels = DetailProcessor.apply(floatPixels, currentWidth, currentHeight, adjustments)

        emit(ProcessingProgress(70f, "去雾..."))
        floatPixels = DehazeProcessor.apply(floatPixels, currentWidth, currentHeight, adjustments)

        emit(ProcessingProgress(75f, "晕影与颗粒..."))
        floatPixels = EffectProcessor.apply(floatPixels, currentWidth, currentHeight, adjustments)

        emit(ProcessingProgress(80f, "色调映射..."))
        floatPixels = ToneMapper.apply(floatPixels, currentWidth, currentHeight, adjustments)

        emit(ProcessingProgress(85f, "转换回 sRGB..."))
        floatPixels = ColorMath.linearToSrgbBulk(floatPixels)

        if (adjustments.crop != null) {
            emit(ProcessingProgress(90f, "裁剪..."))
            val cropResult = applyCrop(floatPixels, currentWidth, currentHeight, adjustments.crop!!)
            floatPixels = cropResult.first
            currentWidth = cropResult.second
            currentHeight = cropResult.third
        }

        if (adjustments.orientationSteps != 0 || adjustments.flipHorizontal || adjustments.flipVertical) {
            emit(ProcessingProgress(92f, "变换..."))
            val transformResult = applyTransform(
                floatPixels, currentWidth, currentHeight,
                adjustments.orientationSteps,
                adjustments.flipHorizontal, adjustments.flipVertical
            )
            floatPixels = transformResult.first
            currentWidth = transformResult.second
            currentHeight = transformResult.third
        }

        emit(ProcessingProgress(95f, "生成结果..."))
        val resultPixels = IntArray(currentWidth * currentHeight)
        for (i in 0 until currentWidth * currentHeight) {
            val idx = i * 3
            val r = ColorMath.clamp((floatPixels[idx] * 255f).toInt(), 0, 255)
            val g = ColorMath.clamp((floatPixels[idx + 1] * 255f).toInt(), 0, 255)
            val b = ColorMath.clamp((floatPixels[idx + 2] * 255f).toInt(), 0, 255)
            resultPixels[i] = Color.rgb(r, g, b)
        }

        var resultBitmap = Bitmap.createBitmap(currentWidth, currentHeight, Bitmap.Config.ARGB_8888)
        resultBitmap.setPixels(resultPixels, 0, currentWidth, 0, 0, currentWidth, currentHeight)

        if (targetWidth > 0 && targetHeight > 0 &&
            (targetWidth != currentWidth || targetHeight != currentHeight)
        ) {
            emit(ProcessingProgress(98f, "缩放..."))
            resultBitmap = Bitmap.createScaledBitmap(resultBitmap, targetWidth, targetHeight, true)
        }

        emit(ProcessingProgress(100f, "完成", resultBitmap))
    }.flowOn(Dispatchers.Default)

    private fun applyCrop(
        pixels: FloatArray,
        width: Int,
        height: Int,
        crop: CropData
    ): Triple<FloatArray, Int, Int> {
        val cropX = (crop.x * width).toInt().coerceIn(0, width - 1)
        val cropY = (crop.y * height).toInt().coerceIn(0, height - 1)
        val cropW = (crop.width * width).toInt().coerceIn(1, width - cropX)
        val cropH = (crop.height * height).toInt().coerceIn(1, height - cropY)

        val result = FloatArray(cropW * cropH * 3)
        for (y in 0 until cropH) {
            for (x in 0 until cropW) {
                val srcIdx = ((cropY + y) * width + (cropX + x)) * 3
                val dstIdx = (y * cropW + x) * 3
                result[dstIdx] = pixels[srcIdx]
                result[dstIdx + 1] = pixels[srcIdx + 1]
                result[dstIdx + 2] = pixels[srcIdx + 2]
            }
        }
        return Triple(result, cropW, cropH)
    }

    private fun applyTransform(
        pixels: FloatArray,
        width: Int,
        height: Int,
        orientationSteps: Int,
        flipH: Boolean,
        flipV: Boolean
    ): Triple<FloatArray, Int, Int> {
        var current = pixels
        var w = width
        var h = height

        if (flipH) {
            current = flipHorizontal(current, w, h)
        }
        if (flipV) {
            current = flipVertical(current, w, h)
        }

        val steps = ((orientationSteps % 4) + 4) % 4
        repeat(steps) {
            val rotated = rotate90(current, w, h)
            current = rotated.first
            val tmp = w
            w = h
            h = tmp
        }

        return Triple(current, w, h)
    }

    private fun flipHorizontal(pixels: FloatArray, width: Int, height: Int): FloatArray {
        val result = FloatArray(pixels.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val srcIdx = (y * width + x) * 3
                val dstIdx = (y * width + (width - 1 - x)) * 3
                result[dstIdx] = pixels[srcIdx]
                result[dstIdx + 1] = pixels[srcIdx + 1]
                result[dstIdx + 2] = pixels[srcIdx + 2]
            }
        }
        return result
    }

    private fun flipVertical(pixels: FloatArray, width: Int, height: Int): FloatArray {
        val result = FloatArray(pixels.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val srcIdx = (y * width + x) * 3
                val dstIdx = ((height - 1 - y) * width + x) * 3
                result[dstIdx] = pixels[srcIdx]
                result[dstIdx + 1] = pixels[srcIdx + 1]
                result[dstIdx + 2] = pixels[srcIdx + 2]
            }
        }
        return result
    }

    private fun rotate90(pixels: FloatArray, width: Int, height: Int): Pair<FloatArray, Int> {
        val newWidth = height
        val newHeight = width
        val result = FloatArray(newWidth * newHeight * 3)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val srcIdx = (y * width + x) * 3
                val dstX = height - 1 - y
                val dstY = x
                val dstIdx = (dstY * newWidth + dstX) * 3
                result[dstIdx] = pixels[srcIdx]
                result[dstIdx + 1] = pixels[srcIdx + 1]
                result[dstIdx + 2] = pixels[srcIdx + 2]
            }
        }
        return result to newWidth
    }

    fun calculateHistogram(bitmap: Bitmap): ColorMath.HistogramData {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val floatPixels = FloatArray(width * height * 3)
        for (i in 0 until width * height) {
            val idx = i * 3
            floatPixels[idx] = Color.red(pixels[i]) / 255f
            floatPixels[idx + 1] = Color.green(pixels[i]) / 255f
            floatPixels[idx + 2] = Color.blue(pixels[i]) / 255f
        }

        return ColorMath.calculateHistogram(floatPixels, width, height)
    }

    data class ProcessingProgress(
        val progress: Float,
        val message: String,
        val result: Bitmap? = null
    )
}

private fun ColorMath.Companion.srgbToLinearBulk(pixels: FloatArray): FloatArray {
    val result = FloatArray(pixels.size)
    for (i in pixels.indices) {
        result[i] = srgbToLinear(pixels[i])
    }
    return result
}

private fun ColorMath.Companion.linearToSrgbBulk(pixels: FloatArray): FloatArray {
    val result = FloatArray(pixels.size)
    for (i in pixels.indices) {
        result[i] = linearToSrgb(pixels[i])
    }
    return result
}

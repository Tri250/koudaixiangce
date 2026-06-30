package com.rapidraw.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.rapidraw.core.ImageProcessor
import com.rapidraw.data.model.ExportSettings
import com.rapidraw.data.model.ExifData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AI超分辨率导出器 - 在导出时提供AI增强放大选项
 * 将处理后的图像通过AI超分辨率模型放大2x，再导出
 */
object AiSuperResExporter {

    private const val TAG = "AiSuperResExporter"

    data class SuperResConfig(
        val scale: Float = 2f,          // 放大倍数（2x 或 4x）
        val enhanceDetails: Boolean = true, // 是否增强细节
        val denoiseFirst: Boolean = false,  // 是否先降噪再超分
    )

    /**
     * 处理并导出AI超分辨率图像
     * @param context 上下文
     * @param source 原始位图
     * @param adjustments 调整参数
     * @param exportSettings 导出设置
     * @param superResConfig 超分配置
     * @param exifData EXIF数据
     * @param orientation 方向
     * @param progressCallback 进度回调
     * @return 导出文件的URI
     */
    suspend fun exportWithSuperRes(
        context: Context,
        source: Bitmap,
        adjustments: com.rapidraw.data.model.Adjustments,
        exportSettings: ExportSettings,
        superResConfig: SuperResConfig = SuperResConfig(),
        exifData: ExifData? = null,
        orientation: Int = 0,
        progressCallback: ((Float) -> Unit)? = null,
    ): android.net.Uri? = withContext(Dispatchers.IO) {
        try {
            // Step 1: 正常处理
            progressCallback?.invoke(0.1f)
            val imageProcessor = ImageProcessor()
            val processed = imageProcessor.processFullResolution(adjustments, source)
            progressCallback?.invoke(0.4f)

            // Step 2: AI超分辨率
            val superRes = AiSuperResolution(context)
            val scaleFactor = if (superResConfig.scale >= 3f) {
                AiSuperResolution.ScaleFactor.X4
            } else {
                AiSuperResolution.ScaleFactor.X2
            }
            val scaled = superRes.upscale(processed, scaleFactor)
            progressCallback?.invoke(0.8f)

            // Step 3: 导出
            val uri = imageProcessor.exportImage(
                scaled, exportSettings, context, exifData, orientation
            )
            progressCallback?.invoke(1.0f)

            // 清理
            if (scaled !== processed) scaled.recycle()
            if (processed !== source) processed.recycle()

            uri
        } catch (e: Exception) {
            Log.e(TAG, "AI super resolution export failed", e)
            null
        }
    }

    /**
     * 仅执行AI超分辨率（不导出）
     */
    suspend fun upscale(context: Context, bitmap: Bitmap, scale: Int = 2): Bitmap {
        val superRes = AiSuperResolution(context)
        val scaleFactor = if (scale >= 3) {
            AiSuperResolution.ScaleFactor.X4
        } else {
            AiSuperResolution.ScaleFactor.X2
        }
        return superRes.upscale(bitmap, scaleFactor)
    }
}

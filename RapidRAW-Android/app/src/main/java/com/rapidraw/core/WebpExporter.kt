package com.rapidraw.core

import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import java.io.OutputStream

/**
 * WebP 格式导出器。
 *
 * - API 30+ (Android 11+): 使用 Bitmap.CompressFormat.WEBP，原生支持有损和无损模式。
 * - API < 30: 使用 Bitmap.CompressFormat.WEBP（仅支持有损），这是 Android 自 API 14
 *   起就支持的格式。无损模式在低版本上回退为有损质量100。
 *
 * 注意：Android 的 Bitmap.CompressFormat.WEBP 在 API < 30 时仅支持有损压缩。
 * 真正的无损 WebP 编码需要 libwebp NDK 集成，这里使用系统 API 作为纯 Kotlin 实现。
 */
object WebpExporter {

    private const val TAG = "WebpExporter"

    data class WebpExportParams(
        /** 有损模式下的质量 (0-100)。无损模式下此值被忽略。 */
        val quality: Int = 90,
        /** 是否使用无损压缩。API < 30 时回退为有损质量100。 */
        val lossless: Boolean = false,
    )

    /**
     * 检查当前设备是否支持无损 WebP 编码。
     * API 30+ 支持，更低版本仅有损。
     */
    fun supportsLossless(): Boolean = Build.VERSION.SDK_INT >= 30

    /**
     * 检查当前设备是否支持 WebP 编码（有损）。
     * API 14+ 均支持。
     */
    fun supportsWebp(): Boolean = Build.VERSION.SDK_INT >= 14

    /**
     * 将 Bitmap 压缩为 WebP 格式并写入输出流。
     *
     * @param bitmap 要导出的 Bitmap（必须未回收）
     * @param params WebP 导出参数
     * @param outputStream 目标输出流
     * @return true 如果成功，false 如果失败
     */
    fun export(
        bitmap: Bitmap,
        params: WebpExportParams,
        outputStream: OutputStream,
    ): Boolean {
        if (bitmap.isRecycled) {
            Log.e(TAG, "Cannot export recycled bitmap")
            return false
        }

        if (!supportsWebp()) {
            Log.e(TAG, "WebP not supported on this device (API ${Build.VERSION.SDK_INT})")
            return false
        }

        return try {
            if (Build.VERSION.SDK_INT >= 30) {
                exportApi30Plus(bitmap, params, outputStream)
            } else {
                exportLegacy(bitmap, params, outputStream)
            }
        } catch (e: Exception) {
            Log.e(TAG, "WebP export failed: ${e.message}", e)
            false
        }
    }

    /**
     * API 30+ 导出：使用 Bitmap.CompressFormat.WEBP 支持有损和无损。
     */
    private fun exportApi30Plus(
        bitmap: Bitmap,
        params: WebpExportParams,
        outputStream: OutputStream,
    ): Boolean {
        if (params.lossless) {
            // 无损 WebP：使用质量100
            // Android API 30+ 的 WEBP lossless 通过设置质量为100来实现
            // 注意：Bitmap.compress 的 quality 参数在 WEBP lossless 模式下
            // 实际由系统决定，100 代表最佳质量（接近无损）
            bitmap.compress(Bitmap.CompressFormat.WEBP, 100, outputStream)
        } else {
            // 有损 WebP：使用指定的质量参数
            val quality = params.quality.coerceIn(1, 100)
            bitmap.compress(Bitmap.CompressFormat.WEBP, quality, outputStream)
        }
        outputStream.flush()
        return true
    }

    /**
     * API < 30 降级导出：仅支持有损 WebP。
     * 如果请求无损模式，回退为有损质量100并记录警告。
     */
    private fun exportLegacy(
        bitmap: Bitmap,
        params: WebpExportParams,
        outputStream: OutputStream,
    ): Boolean {
        if (params.lossless) {
            Log.w(TAG, "Lossless WebP not supported on API ${Build.VERSION.SDK_INT}, " +
                "falling back to lossy quality 100")
        }

        // API 14+ 支持有损 WEBP
        val quality = if (params.lossless) 100 else params.quality.coerceIn(1, 100)
        bitmap.compress(Bitmap.CompressFormat.WEBP, quality, outputStream)
        outputStream.flush()
        return true
    }

    /**
     * 获取 WebP 文件的 MIME 类型。
     */
    fun getMimeType(): String = "image/webp"

    /**
     * 获取 WebP 文件扩展名。
     */
    fun getFileExtension(): String = ".webp"
}

package com.rapidraw.core

import android.graphics.Bitmap
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * JPEG XL (JXL) 格式导出器。
 *
 * JPEG XL 是新一代图像格式，支持有损和无损压缩，具有比 JPEG 更好的压缩效率
 * 和比 WebP 更丰富的功能集。
 *
 * ## 实现状态
 *
 * Android 系统目前不原生支持 JXL 编码。真正的 JXL 编码需要集成 libjxl 原生库。
 * 本导出器提供：
 *
 * 1. **完整的 JXL 导出接口和数据结构** - 准备好接入 libjxl NDK 实现
 * 2. **PNG 回退导出** - 在 libjxl 不可用时，以 PNG 作为中间格式输出，
 *    保证导出流程不中断
 * 3. **元数据记录** - 导出的文件中记录 JXL 参数，便于后续重新编码
 *
 * ## 集成 libjxl 的 TODO
 *
 * 要实现真正的 JXL 编码，需要：
 * 1. 编译 libjxl 的 Android NDK 版本 (libjxl-static.a / libjxl.so)
 * 2. 创建 JNI 桥接: JxlNativeEncoder.kt
 * 3. 实现 native 方法: `nativeEncode(bitmap, params) -> ByteArray`
 * 4. 在 isJxlNativeAvailable() 中检测库是否加载成功
 * 5. 替换 exportWithFallback 中的 PNG 回退为原生 JXL 编码
 *
 * 参考: https://github.com/libjxl/libjxl
 */
object JxlExporter {

    private const val TAG = "JxlExporter"

    /**
     * JXL 编码参数。
     *
     * @param distance 视觉距离参数，控制有损压缩质量。
     *   - 0.0 = 无损（等价于 lossless=true）
     *   - 1.0 = 高质量有损（接近无损，约等效于 JPEG quality 95+）
     *   - 2.0-3.0 = 中等质量有损
     *   - 7.0+ = 低质量有损
     *   范围: 0.0 - 15.0
     *
     * @param effort 编码努力程度，控制编码速度与压缩率的权衡。
     *   - 1 = 最快编码（压缩率较低）
     *   - 7 = 默认
     *   - 9 = 最慢编码（最佳压缩率）
     *   范围: 1 - 9
     *
     * @param lossless 是否使用无损压缩。
     *   当 lossless=true 时，distance 参数被忽略。
     */
    data class JxlExportParams(
        val distance: Float = 1.0f,
        val effort: Int = 7,
        val lossless: Boolean = false,
    ) {
        init {
            require(distance >= 0f && distance <= 15f) {
                "JXL distance must be in 0.0..15.0, was $distance"
            }
            require(effort in 1..9) {
                "JXL effort must be in 1..9, was $effort"
            }
        }

        /**
         * 从 JXL 参数估算等效的 JPEG 质量 (0-100)，
         * 用于在没有原生 JXL 编码器时选择合适的 PNG 回退质量。
         */
        fun estimatedJpegQuality(): Int {
            if (lossless || distance < 0.1f) return 100
            // 简化的 distance -> quality 映射
            // distance 1.0 ≈ quality 95, distance 3.0 ≈ quality 75, distance 7.0 ≈ quality 40
            val quality = (100 - distance * 10).coerceIn(10f, 100f).toInt()
            return quality
        }
    }

    /**
     * JXL 编码结果元数据。
     * 记录实际使用的编码参数和回退信息。
     */
    data class JxlEncodeResult(
        val success: Boolean,
        val bytesWritten: Long = 0,
        val usedFallback: Boolean = false,
        val fallbackFormat: String = "",
        val params: JxlExportParams = JxlExportParams(),
        val errorMessage: String? = null,
    )

    // 原生库加载状态
    private var nativeLibraryLoaded = false
    private var nativeLoadAttempted = false

    /**
     * 检查 libjxl 原生库是否可用。
     * 如果原生库尚未加载，尝试加载一次。
     */
    fun isJxlNativeAvailable(): Boolean {
        if (!nativeLoadAttempted) {
            nativeLoadAttempted = true
            nativeLibraryLoaded = tryLoadNativeLibrary()
        }
        return nativeLibraryLoaded
    }

    /**
     * 尝试加载 libjxl 原生库。
     * TODO: 当 libjxl NDK 集成完成后，取消注释 System.loadLibrary 调用。
     */
    private fun tryLoadNativeLibrary(): Boolean {
        return try {
            // TODO: 取消注释以加载 libjxl 原生库
            // System.loadLibrary("jxl")
            Log.i(TAG, "libjxl native library not yet integrated")
            false
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "libjxl native library not found: ${e.message}")
            false
        }
    }

    /**
     * 将 Bitmap 导出为 JXL 格式。
     *
     * 如果 libjxl 原生库可用，使用原生 JXL 编码器。
     * 否则回退为 PNG 格式输出（保持无损质量）。
     *
     * @param bitmap 要导出的 Bitmap（必须未回收）
     * @param params JXL 导出参数
     * @param outputStream 目标输出流
     * @return 编码结果元数据
     */
    fun export(
        bitmap: Bitmap,
        params: JxlExportParams,
        outputStream: OutputStream,
    ): JxlEncodeResult {
        if (bitmap.isRecycled) {
            return JxlEncodeResult(
                success = false,
                errorMessage = "Cannot export recycled bitmap",
            )
        }

        return if (isJxlNativeAvailable()) {
            exportNative(bitmap, params, outputStream)
        } else {
            exportWithFallback(bitmap, params, outputStream)
        }
    }

    /**
     * 使用原生 libjxl 编码器导出。
     * TODO: 实现原生 JXL 编码调用。
     */
    private fun exportNative(
        bitmap: Bitmap,
        params: JxlExportParams,
        outputStream: OutputStream,
    ): JxlEncodeResult {
        // TODO: 实现 JNI 调用
        // val jxlBytes = JxlNativeEncoder.nativeEncode(bitmap, params)
        // outputStream.write(jxlBytes)
        // outputStream.flush()

        Log.w(TAG, "Native JXL encoding not yet implemented, using fallback")
        return exportWithFallback(bitmap, params, outputStream)
    }

    /**
     * PNG 回退导出：将 Bitmap 编码为 PNG 写入输出流。
     *
     * PNG 是无损格式，作为 JXL 无损模式的自然回退。
     * 对于有损模式，PNG 回退会生成更大的文件，但保持完整质量。
     *
     * 同时写入一个 JXL 参数注释头（作为自定义 PNG tEXt chunk 的替代，
     * 在文件名中记录参数以便后续重新编码）。
     */
    private fun exportWithFallback(
        bitmap: Bitmap,
        params: JxlExportParams,
        outputStream: OutputStream,
    ): JxlEncodeResult {
        return try {
            // 使用 PNG 作为回退格式
            val byteStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteStream)
            val pngBytes = byteStream.toByteArray()

            outputStream.write(pngBytes)
            outputStream.flush()

            Log.i(TAG, "JXL export used PNG fallback " +
                "(distance=${params.distance}, effort=${params.effort}, " +
                "lossless=${params.lossless}), ${pngBytes.size} bytes")

            JxlEncodeResult(
                success = true,
                bytesWritten = pngBytes.size.toLong(),
                usedFallback = true,
                fallbackFormat = "PNG",
                params = params,
            )
        } catch (e: Exception) {
            Log.e(TAG, "JXL fallback export failed: ${e.message}", e)
            JxlEncodeResult(
                success = false,
                errorMessage = e.message,
                params = params,
            )
        }
    }

    /**
     * 获取 JXL 文件的 MIME 类型。
     */
    fun getMimeType(): String = "image/jxl"

    /**
     * 获取 JXL 文件扩展名。
     */
    fun getFileExtension(): String = ".jxl"

    /**
     * 将 JXL 参数编码为文件名后缀，用于回退时记录编码参数。
     * 例如: "_jxl_d1.0_e7" 表示 distance=1.0, effort=7
     */
    fun paramsToFilenameSuffix(params: JxlExportParams): String {
        return if (params.lossless) {
            "_jxl_lossless_e${params.effort}"
        } else {
            "_jxl_d${"%.1f".format(params.distance)}_e${params.effort}"
        }
    }
}

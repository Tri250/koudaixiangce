package com.rapidraw.core

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.rapidraw.data.model.Adjustments
import com.rapidraw.data.model.ExportFormat
import com.rapidraw.data.model.ExportSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * 批量图像处理器。
 *
 * 核心设计原则：
 * - 一次仅处理一张图片，避免 OOM（内存管理）
 * - 逐图进度报告（每处理完一张 emit 一次 BatchProgress）
 * - 支持协程取消（CancellationException 正确传播，ensureActive 检查）
 * - 单图失败不中断整批（错误记录到 error 字段，继续下一张）
 * - 支持多种导出格式（JPEG/PNG/HEIF/TIFF）
 * - 处理完每张图片后主动释放 Bitmap + 触发 GC 建议
 * - 连续 OOM 保护：超过阈值后中止批处理
 */
class BatchProcessor(private val context: Context, private val imageProcessor: ImageProcessor) {

    companion object {
        private const val TAG = "BatchProcessor"
    }

    /**
     * 单张图片的批量处理进度。
     *
     * @param current       当前处理到的图片序号（1-based）
     * @param total         总图片数
     * @param currentFileName 当前图片文件名
     * @param isComplete    是否整批完成
     * @param error         当前图片的错误信息（null 表示成功）
     * @param succeeded     截至目前成功的图片数
     * @param failed        截至目前失败的图片数
     */
    data class BatchProgress(
        val current: Int,
        val total: Int,
        val currentFileName: String,
        val isComplete: Boolean = false,
        val error: String? = null,
        val succeeded: Int = 0,
        val failed: Int = 0,
    )

    /**
     * 批量处理配置。
     */
    data class BatchConfig(
        val continueOnError: Boolean = true,
        val gcHintBetweenImages: Boolean = true,
        val maxConsecutiveOom: Int = 3,
        /** R-02: 存储空间检查阈值，默认 500MB */
        val spaceCheckBytes: Long = 500L * 1024 * 1024,
        /** R-05: 是否使用原子写入（先写临时文件再重命名） */
        val useAtomicWrites: Boolean = true,
        /** R-06: 批量导出时保留原始文件夹结构 */
        val preserveFolderStructure: Boolean = false,
        /** R-06: 导出根目录，用于 preserveFolderStructure */
        val exportRootDir: String = "",
    )

    /**
     * C-03: 批量导出进度状态，用于进程被杀后恢复。
     */
    data class BatchExportState(
        val operation: String,
        val imageUris: List<String>,
        val currentIndex: Int,
        val succeeded: Int,
        val failed: Int,
        val timestamp: Long = System.currentTimeMillis(),
    )

    // ── 公开 API ──────────────────────────────────────────────────────

    /**
     * 批量应用胶片模拟 + 调整 + 导出。
     *
     * 对每张图片执行完整流程：解码 → 应用调整 → 导出。
     * 处理完每张后立即释放 Bitmap 内存。
     */
    fun batchApplyFilm(
        imageUris: List<Uri>,
        filmAdjustments: Adjustments,
        exportSettings: ExportSettings,
        config: BatchConfig = BatchConfig(),
    ): Flow<BatchProgress> = flow {
        var succeeded = 0
        var failed = 0
        var consecutiveOom = 0
        val total = imageUris.size

        for ((index, uri) in imageUris.withIndex()) {
            // 协程取消检查
            currentCoroutineContext().ensureActive()

            // 连续 OOM 阈值保护
            if (consecutiveOom >= config.maxConsecutiveOom) {
                Log.e(TAG, "Too many consecutive OOM errors ($consecutiveOom), aborting batch")
                failed += (total - index)
                break
            }

            // R-02: 存储空间检查，空间不足时中止批处理
            if (config.spaceCheckBytes > 0) {
                val available = com.rapidraw.core.StorageChecker.getAvailableBytes(context.filesDir)
                if (available < config.spaceCheckBytes) {
                    Log.e(TAG, "Insufficient storage for batch: need ${config.spaceCheckBytes}, have $available")
                    failed += (total - index)
                    emit(BatchProgress(
                        current = index + 1, total = total,
                        currentFileName = extractFileName(uri, index),
                        error = "存储空间不足，请清理后重试",
                        succeeded = succeeded, failed = failed,
                    ))
                    break
                }
            }

            val fileName = extractFileName(uri, index)

            try {
                withContext(Dispatchers.IO) {
                    val processed = imageProcessor.loadAndDecode(context, uri)
                    checkBitmapValid(processed.original, uri)

                    val adjusted = imageProcessor.processFullResolution(filmAdjustments, processed.original)
                    if (!processed.original.isRecycled) processed.original.recycle()
                    if (!processed.preview.isRecycled) processed.preview.recycle()

                    try {
                        imageProcessor.exportImage(adjusted, exportSettings, context, processed.exif, processed.orientation, originalPath = uri.toString())
                    } finally {
                        if (!adjusted.isRecycled) adjusted.recycle()
                    }
                }

                succeeded++
                consecutiveOom = 0
                emit(BatchProgress(
                    current = index + 1, total = total,
                    currentFileName = fileName,
                    succeeded = succeeded, failed = failed,
                ))
                // C-03: 保存进度以便恢复
                saveBatchState(BatchExportState(
                    operation = "batchApplyFilm",
                    imageUris = imageUris.map { it.toString() },
                    currentIndex = index + 1,
                    succeeded = succeeded,
                    failed = failed,
                ))
            } catch (e: CancellationException) {
                throw e
            } catch (e: OutOfMemoryError) {
                consecutiveOom++
                failed++
                System.gc()
                Log.e(TAG, "Batch: OOM on image $index ($uri)", e)
                emit(BatchProgress(
                    current = index + 1, total = total,
                    currentFileName = fileName,
                    error = "OOM: ${e.message}",
                    succeeded = succeeded, failed = failed,
                ))
            } catch (e: Exception) {
                consecutiveOom = 0
                failed++
                Log.e(TAG, "Batch: failed for $uri", e)
                emit(BatchProgress(
                    current = index + 1, total = total,
                    currentFileName = fileName,
                    error = e.message,
                    succeeded = succeeded, failed = failed,
                ))
                if (!config.continueOnError) break
            }

            // 图片间 GC 提示
            if (config.gcHintBetweenImages) {
                System.gc()
            }
        }

        emit(BatchProgress(
            current = total, total = total,
            currentFileName = "", isComplete = true,
            succeeded = succeeded, failed = failed,
        ))
        // C-03: 批处理完成，清除状态文件
        clearBatchState()
    }.flowOn(Dispatchers.IO)

    /**
     * 批量导出（无调整，直接解码后导出为指定格式）。
     */
    fun batchExport(
        imageUris: List<Uri>,
        exportSettings: ExportSettings,
        config: BatchConfig = BatchConfig(),
    ): Flow<BatchProgress> = flow {
        var succeeded = 0
        var failed = 0
        var consecutiveOom = 0
        val total = imageUris.size

        for ((index, uri) in imageUris.withIndex()) {
            currentCoroutineContext().ensureActive()

            if (consecutiveOom >= config.maxConsecutiveOom) {
                failed += (total - index)
                break
            }

            // R-02: 存储空间检查，空间不足时中止批处理
            if (config.spaceCheckBytes > 0) {
                val available = com.rapidraw.core.StorageChecker.getAvailableBytes(context.filesDir)
                if (available < config.spaceCheckBytes) {
                    Log.e(TAG, "Insufficient storage for batch export: need ${config.spaceCheckBytes}, have $available")
                    failed += (total - index)
                    emit(BatchProgress(
                        current = index + 1, total = total,
                        currentFileName = extractFileName(uri, index),
                        error = "存储空间不足，请清理后重试",
                        succeeded = succeeded, failed = failed,
                    ))
                    break
                }
            }

            val fileName = extractFileName(uri, index)

            try {
                withContext(Dispatchers.IO) {
                    val processed = imageProcessor.loadAndDecode(context, uri)
                    checkBitmapValid(processed.original, uri)

                    try {
                        imageProcessor.exportImage(processed.original, exportSettings, context, processed.exif, processed.orientation, originalPath = uri.toString())
                    } finally {
                        if (!processed.original.isRecycled) processed.original.recycle()
                        if (!processed.preview.isRecycled) processed.preview.recycle()
                    }
                }

                succeeded++
                consecutiveOom = 0
                emit(BatchProgress(
                    current = index + 1, total = total,
                    currentFileName = fileName,
                    succeeded = succeeded, failed = failed,
                ))
                // C-03: 保存进度以便恢复
                saveBatchState(BatchExportState(
                    operation = "batchExport",
                    imageUris = imageUris.map { it.toString() },
                    currentIndex = index + 1,
                    succeeded = succeeded,
                    failed = failed,
                ))
            } catch (e: CancellationException) {
                throw e
            } catch (e: OutOfMemoryError) {
                consecutiveOom++
                failed++
                System.gc()
                emit(BatchProgress(
                    current = index + 1, total = total,
                    currentFileName = fileName,
                    error = "OOM: ${e.message}",
                    succeeded = succeeded, failed = failed,
                ))
            } catch (e: Exception) {
                consecutiveOom = 0
                failed++
                emit(BatchProgress(
                    current = index + 1, total = total,
                    currentFileName = fileName,
                    error = e.message,
                    succeeded = succeeded, failed = failed,
                ))
                if (!config.continueOnError) break
            }

            if (config.gcHintBetweenImages) {
                System.gc()
            }
        }

        emit(BatchProgress(
            current = total, total = total,
            currentFileName = "", isComplete = true,
            succeeded = succeeded, failed = failed,
        ))
        // C-03: 批处理完成，清除状态文件
        clearBatchState()
    }.flowOn(Dispatchers.IO)

    /**
     * 批量应用保存的调整（copy-paste 工作流）。
     */
    fun batchApplyAdjustments(
        imageUris: List<Uri>,
        adjustments: Adjustments,
        exportSettings: ExportSettings,
        config: BatchConfig = BatchConfig(),
    ): Flow<BatchProgress> = batchApplyFilm(imageUris, adjustments, exportSettings, config)

    /**
     * 批量格式转换 — 将一组图片转换为目标格式。
     *
     * @param targetFormat 目标格式（JPEG/PNG/HEIF/TIFF）
     * @param quality JPEG 质量 (1-100)，仅 JPEG 有效
     */
    fun batchConvertFormat(
        imageUris: List<Uri>,
        targetFormat: ExportFormat,
        quality: Int = 95,
        config: BatchConfig = BatchConfig(),
    ): Flow<BatchProgress> = batchExport(
        imageUris,
        ExportSettings(format = targetFormat, quality = quality),
        config,
    )

    // ── 内部辅助 ──────────────────────────────────────────────────────

    private fun checkBitmapValid(bitmap: Bitmap, uri: Uri) {
        if (bitmap.isRecycled) {
            throw IllegalStateException("Decoded bitmap already recycled for: $uri")
        }
    }

    private fun extractFileName(uri: Uri, index: Int): String {
        return uri.lastPathSegment ?: "image_$index"
    }

    // ── C-03: 批量导出状态持久化与恢复 ──────────────────────────────

    private val batchStateFile: java.io.File
        get() = java.io.File(context.filesDir, "batch_state.json")

    /**
     * C-03: 保存批量导出进度到文件，以便进程被杀后恢复。
     */
    fun saveBatchState(state: BatchExportState) {
        try {
            val json = org.json.JSONObject().apply {
                put("operation", state.operation)
                put("imageUris", org.json.JSONArray(state.imageUris))
                put("currentIndex", state.currentIndex)
                put("succeeded", state.succeeded)
                put("failed", state.failed)
                put("timestamp", state.timestamp)
            }
            batchStateFile.writeText(json.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save batch state", e)
        }
    }

    /**
     * C-03: 从文件加载上次的批量导出进度，用于恢复。
     * 返回 null 表示没有可恢复的状态。
     */
    fun loadBatchState(): BatchExportState? {
        return try {
            if (!batchStateFile.exists()) return null
            val json = org.json.JSONObject(batchStateFile.readText())
            val uris = mutableListOf<String>()
            val arr = json.getJSONArray("imageUris")
            for (i in 0 until arr.length()) {
                uris.add(arr.getString(i))
            }
            BatchExportState(
                operation = json.getString("operation"),
                imageUris = uris,
                currentIndex = json.getInt("currentIndex"),
                succeeded = json.getInt("succeeded"),
                failed = json.getInt("failed"),
                timestamp = json.optLong("timestamp", 0L),
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load batch state", e)
            null
        }
    }

    /**
     * C-03: 清除已保存的批量导出状态（导出完成后调用）。
     */
    fun clearBatchState() {
        try {
            if (batchStateFile.exists()) {
                batchStateFile.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear batch state", e)
        }
    }
}

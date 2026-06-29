package com.rapidraw.core

import android.content.Context
import android.net.Uri
import android.util.Log
import com.rapidraw.data.model.Adjustments
import com.rapidraw.data.model.ExportSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max

class BatchProcessor(private val context: Context, private val imageProcessor: ImageProcessor) {

    companion object {
        private const val TAG = "BatchProcessor"
        private const val MIN_MEMORY_MB_FOR_PARALLEL = 256 // 每个并行任务至少需要256MB可用内存
        private const val BYTES_PER_PIXEL = 4 // ARGB_8888
        private const val BITMAP_OVERHEAD_FACTOR = 3.0 // 解码+处理+输出，约3倍内存
    }

    // ── 批处理任务状态 ──────────────────────────────────────

    enum class BatchJobStatus {
        QUEUED,
        PROCESSING,
        COMPLETED,
        FAILED,
    }

    data class BatchJob(
        val id: String = java.util.UUID.randomUUID().toString(),
        val uri: Uri,
        val fileName: String,
        val status: BatchJobStatus = BatchJobStatus.QUEUED,
        val progress: Float = 0f,
        val error: String? = null,
        val resultUri: Uri? = null,
    )

    // ── 进度报告 ──────────────────────────────────────────

    data class BatchProgress(
        val current: Int,
        val total: Int,
        val currentFileName: String,
        val overallProgress: Float = 0f,
        val isComplete: Boolean = false,
        val error: String? = null,
        val failedCount: Int = 0,
        val completedCount: Int = 0,
        val perJobStatus: Map<String, BatchJob> = emptyMap(),
    )

    // ── 并行度配置 ────────────────────────────────────────

    data class ParallelismConfig(
        val maxConcurrency: Int = computeDefaultParallelism(),
        val memoryLimitMb: Long = computeAvailableMemoryMb(),
    )

    // ── 内部进度流 ────────────────────────────────────────

    private val _progressFlow = MutableSharedFlow<BatchProgress>(
        extraBufferCapacity = 64,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.SUSPEND,
    )
    val progressFlow: SharedFlow<BatchProgress> = _progressFlow.asSharedFlow()

    // ── 批处理作用域（支持取消） ───────────────────────────

    private val batchScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val activeJob = AtomicReference<CoroutineScope?>(null)

    // ── 取消当前批处理 ────────────────────────────────────

    fun cancelBatch() {
        activeJob.getAndSet(null)?.cancel()
    }

    // ── 计算默认并行度 ────────────────────────────────────

    private fun computeEffectiveParallelism(config: ParallelismConfig): Int {
        val cpuParallelism = config.maxConcurrency
        val memoryBasedLimit = (config.memoryLimitMb / MIN_MEMORY_MB_FOR_PARALLEL).toInt()
            .coerceAtLeast(1)
        val effective = minOf(cpuParallelism, memoryBasedLimit)
        Log.d(TAG, "并行度: CPU=$cpuParallelism, 内存限制=$memoryBasedLimit, 有效=$effective")
        return effective
    }

    // ── 批量导出 ──────────────────────────────────────────

    suspend fun batchExport(
        imageUris: List<Uri>,
        exportSettings: ExportSettings,
        config: ParallelismConfig = ParallelismConfig(),
    ): BatchProgress = executeBatch(
        imageUris = imageUris,
        config = config,
        processItem = { uri, _ ->
            val processed = imageProcessor.loadAndDecode(context, uri)
            val resultUri = imageProcessor.exportImage(
                processed.original, exportSettings, context, processed.exif, processed.orientation
            )
            processed.original.recycle()
            processed.preview.recycle()
            resultUri
        },
    )

    // ── 批量应用预设（胶片模拟） ──────────────────────────

    suspend fun batchApplyPreset(
        imageUris: List<Uri>,
        presetAdjustments: Adjustments,
        exportSettings: ExportSettings,
        config: ParallelismConfig = ParallelismConfig(),
    ): BatchProgress = executeBatch(
        imageUris = imageUris,
        config = config,
        processItem = { uri, _ ->
            val processed = imageProcessor.loadAndDecode(context, uri)
            val adjusted = imageProcessor.processFullResolution(presetAdjustments, processed.original)
            processed.original.recycle()
            processed.preview.recycle()
            val resultUri = imageProcessor.exportImage(
                adjusted, exportSettings, context, processed.exif, processed.orientation
            )
            if (adjusted != processed.original && !adjusted.isRecycled) adjusted.recycle()
            resultUri
        },
    )

    // ── 批量应用调整 ──────────────────────────────────────

    suspend fun batchApplyAdjustments(
        imageUris: List<Uri>,
        adjustments: Adjustments,
        exportSettings: ExportSettings,
        config: ParallelismConfig = ParallelismConfig(),
    ): BatchProgress = batchApplyPreset(imageUris, adjustments, exportSettings, config)

    // ── 批量复制调整（从源图像复制调整到目标图像） ────────

    suspend fun batchCopyAdjustments(
        sourceAdjustments: Adjustments,
        targetUris: List<Uri>,
        exportSettings: ExportSettings,
        config: ParallelismConfig = ParallelismConfig(),
    ): BatchProgress = batchApplyPreset(targetUris, sourceAdjustments, exportSettings, config)

    // ── 核心并行执行引擎 ──────────────────────────────────

    private suspend fun executeBatch(
        imageUris: List<Uri>,
        config: ParallelismConfig,
        processItem: suspend (Uri, String) -> Uri,
    ): BatchProgress {
        val total = imageUris.size
        if (total == 0) return BatchProgress(0, 0, "", 0f, true)

        val parallelism = computeEffectiveParallelism(config)
        val dispatcher = Dispatchers.IO.limitedParallelism(parallelism)

        val completedCount = AtomicInteger(0)
        val failedCount = AtomicInteger(0)
        val jobStatuses = mutableMapOf<String, BatchJob>()

        // 初始化所有任务状态
        imageUris.forEachIndexed { index, uri ->
            val fileName = resolveFileName(uri, index)
            val job = BatchJob(
                uri = uri,
                fileName = fileName,
                status = BatchJobStatus.QUEUED,
            )
            jobStatuses[job.id] = job
        }

        // 创建可取消的子作用域
        val jobScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        activeJob.set(jobScope)

        try {
            coroutineScope {
                val deferreds = imageUris.mapIndexed { index, uri ->
                    async(dispatcher) {
                        if (!isActive) return@async null

                        val fileName = resolveFileName(uri, index)
                        val jobId = jobStatuses.entries.first { it.value.uri == uri }.key

                        // 更新状态为处理中
                        jobStatuses[jobId] = jobStatuses[jobId]!!.copy(
                            status = BatchJobStatus.PROCESSING,
                            progress = 0f,
                        )
                        emitProgress(
                            completedCount.get(), total, fileName,
                            jobStatuses.toMap()
                        )

                        try {
                            val resultUri = processItem(uri, fileName)

                            val done = completedCount.incrementAndGet()
                            jobStatuses[jobId] = jobStatuses[jobId]!!.copy(
                                status = BatchJobStatus.COMPLETED,
                                progress = 1f,
                                resultUri = resultUri,
                            )
                            emitProgress(done, total, fileName, jobStatuses.toMap())

                            resultUri
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            val failCount = failedCount.incrementAndGet()
                            jobStatuses[jobId] = jobStatuses[jobId]!!.copy(
                                status = BatchJobStatus.FAILED,
                                error = e.message ?: "未知错误",
                            )
                            emitProgress(
                                completedCount.get(), total, fileName,
                                jobStatuses.toMap(),
                                error = e.message,
                                failedCount = failCount,
                            )
                            Log.w(TAG, "批处理失败 [$fileName]: ${e.message}")
                            null
                        }
                    }
                }

                // 等待所有任务完成
                deferreds.awaitAll()
            }
        } catch (e: CancellationException) {
            Log.d(TAG, "批处理被取消")
            // 将所有未完成的任务标记为失败
            jobStatuses.entries
                .filter { it.value.status == BatchJobStatus.QUEUED || it.value.status == BatchJobStatus.PROCESSING }
                .forEach { (id, job) ->
                    jobStatuses[id] = job.copy(
                        status = BatchJobStatus.FAILED,
                        error = "已取消",
                    )
                }
        } finally {
            activeJob.compareAndSet(jobScope, null)
            jobScope.cancel()
        }

        val finalCompleted = completedCount.get()
        val finalFailed = failedCount.get()
        val finalProgress = BatchProgress(
            current = finalCompleted + finalFailed,
            total = total,
            currentFileName = "",
            overallProgress = 1f,
            isComplete = true,
            failedCount = finalFailed,
            completedCount = finalCompleted,
            perJobStatus = jobStatuses.toMap(),
        )
        _progressFlow.emit(finalProgress)
        return finalProgress
    }

    // ── 进度发射 ──────────────────────────────────────────

    private suspend fun emitProgress(
        completedSoFar: Int,
        total: Int,
        currentFileName: String,
        perJobStatus: Map<String, BatchJob>,
        error: String? = null,
        failedCount: Int = 0,
    ) {
        val overallProgress = if (total > 0) {
            (completedSoFar + failedCount).toFloat() / total
        } else 0f

        _progressFlow.emit(BatchProgress(
            current = completedSoFar,
            total = total,
            currentFileName = currentFileName,
            overallProgress = overallProgress,
            error = error,
            failedCount = failedCount,
            completedCount = completedSoFar,
            perJobStatus = perJobStatus,
        ))
    }

    // ── 工具方法 ──────────────────────────────────────────

    private fun resolveFileName(uri: Uri, index: Int): String {
        var name = ""
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) name = it.getString(nameIndex)
            }
        }
        if (name.isEmpty()) name = uri.lastPathSegment ?: "image_$index"
        return name
    }

    // ── 静态工具 ──────────────────────────────────────────

    companion object {
        /**
         * 计算默认并行度：CPU核心数 - 1，最小为1
         */
        fun computeDefaultParallelism(): Int {
            return max(Runtime.getRuntime().availableProcessors() - 1, 1)
        }

        /**
         * 估算可用内存（MB）
         */
        fun computeAvailableMemoryMb(): Long {
            val runtime = Runtime.getRuntime()
            val maxMemory = runtime.maxMemory()
            val usedMemory = runtime.totalMemory() - runtime.freeMemory()
            val available = (maxMemory - usedMemory) / (1024 * 1024)
            return max(available, 128) // 至少128MB
        }

        /**
         * 估算处理一张图片所需的内存（MB），基于图片尺寸
         */
        fun estimateImageMemoryMb(width: Int, height: Int): Long {
            val pixels = width.toLong() * height.toLong()
            val bytes = pixels * BYTES_PER_PIXEL * BITMAP_OVERHEAD_FACTOR.toLong()
            return bytes / (1024 * 1024)
        }
    }
}

package com.rapidraw.ui.library

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.rapidraw.R
import com.rapidraw.core.ImageProcessor
import com.rapidraw.core.SidecarManager
import com.rapidraw.data.model.Adjustments
import com.rapidraw.data.model.ExportSettings
import com.rapidraw.data.model.FilmSimulation
import com.rapidraw.data.model.Preset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 批量处理器 - 支持多图应用预设/导出
 */
class BatchProcessor(private val context: Context) {

    data class BatchJob(
        val id: String,
        val imageUri: Uri,
        val fileName: String,
        val status: BatchJobStatus,
        val progress: Float = 0f,
        val error: String? = null,
        val resultUri: Uri? = null,
    )

    enum class BatchJobStatus { PENDING, PROCESSING, EXPORTING, COMPLETED, FAILED, CANCELLED }

    private val imageProcessor = ImageProcessor()

    private val _jobs = MutableStateFlow<List<BatchJob>>(emptyList())
    val jobs: StateFlow<List<BatchJob>> = _jobs.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _totalProgress = MutableStateFlow(0f)
    val totalProgress: StateFlow<Float> = _totalProgress.asStateFlow()

    // ── Notification-based progress reporting ──────────────────────
    private var notificationBuilder: NotificationCompat.Builder? = null
    private val notificationId = 1001
    private val batchChannelId = "rapidraw_batch_progress"

    private fun ensureNotificationChannel() {
        val channel = android.app.NotificationChannel(
            batchChannelId,
            "批量处理进度",
            android.app.NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "显示批量导出/预设应用的进度"
            setShowBadge(false)
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun showProgressNotification(completed: Int, total: Int, currentFile: String, isComplete: Boolean = false) {
        if (notificationBuilder == null) {
            ensureNotificationChannel()
            notificationBuilder = NotificationCompat.Builder(context, batchChannelId)
                .setSmallIcon(android.R.drawable.ic_menu_gallery)
                .setContentTitle("批量处理")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
        }
        notificationBuilder!!.apply {
            setContentText(if (isComplete) "完成 $completed/$total" else "$completed/$total — $currentFile")
            setProgress(total, completed, false)
            if (isComplete) {
                setOngoing(false)
                setContentTitle("批量处理完成")
                setProgress(0, 0, false)
            }
        }
        try {
            NotificationManagerCompat.from(context).notify(notificationId, notificationBuilder!!.build())
        } catch (_: SecurityException) {
            // 通知权限未授予，静默失败
        }
    }

    private fun cancelProgressNotification() {
        try {
            NotificationManagerCompat.from(context).cancel(notificationId)
        } catch (_: SecurityException) { }
        notificationBuilder = null
    }

    /**
     * 批量应用预设到多张图片（仅保存 sidecar，不触发导出）。
     * 将预设的 Adjustments 保存为 .rrdata 文件，原始 RAW 文件保持不变。
     *
     * @param imageUris 要处理的图片 URI 列表
     * @param preset 要应用的预设
     * @param sidecarManager SidecarManager 实例，用于保存 .rrdata 文件
     * @param continueOnError 单张失败是否继续处理后续
     * @param onProgress 进度回调，用于 UI 更新
     */
    suspend fun applyPresetToFiles(
        imageUris: List<Uri>,
        preset: Preset,
        sidecarManager: SidecarManager,
        continueOnError: Boolean = true,
        onProgress: ((completed: Int, total: Int, currentFile: String) -> Unit)? = null,
    ) {
        if (_isProcessing.value) return
        _isProcessing.value = true
        _totalProgress.value = 0f

        // 创建任务列表
        val newJobs = imageUris.mapIndexed { index, uri ->
            val fileName = getFileName(uri) ?: "image_$index"
            BatchJob(
                id = "preset_${System.currentTimeMillis()}_$index",
                imageUri = uri,
                fileName = fileName,
                status = BatchJobStatus.PENDING,
            )
        }
        _jobs.value = newJobs

        var completed = 0
        for (job in newJobs) {
            if (!_isProcessing.value) break

            updateJobStatus(job.id, BatchJobStatus.PROCESSING)
            onProgress?.invoke(completed, newJobs.size, job.fileName)
            showProgressNotification(completed, newJobs.size, job.fileName)

            try {
                // 将预设的 Adjustments 保存为 sidecar (.rrdata)
                withContext(Dispatchers.IO) {
                    sidecarManager.saveSidecar(
                        imageUri = job.imageUri,
                        adjustments = preset.adjustments,
                        filmId = preset.filmId,
                    )
                }
                updateJobProgress(job.id, 1.0f)
                updateJobResult(job.id, BatchJobStatus.COMPLETED)
                completed++
            } catch (e: Exception) {
                Log.e(TAG, "applyPresetToFiles failed: ${job.fileName}", e)
                updateJobResult(job.id, BatchJobStatus.FAILED, error = e.localizedMessage)
                if (!continueOnError) {
                    _isProcessing.value = false
                    cancelProgressNotification()
                    return
                }
                completed++
            }

            _totalProgress.value = completed.toFloat() / newJobs.size
            onProgress?.invoke(completed, newJobs.size, job.fileName)
        }

        showProgressNotification(completed, newJobs.size, "", isComplete = true)
        _isProcessing.value = false
    }

    /**
     * 开始批量处理任务
     * @param imageUris 要处理的图片URI列表
     * @param adjustments 要应用的调整参数（注意：预设一般不带蒙版，若有蒙版需确认是否应用到批量）
     * @param filmId 可选的胶片模拟ID
     * @param exportSettings 导出设置
     * @param continueOnError 单张失败是否继续处理后续（L05 关键断言：失败有红色⚠，已成功不删）
     * @param onProgress 进度回调，用于 UI 更新"3/20"式进度
     */
    suspend fun startBatch(
        imageUris: List<Uri>,
        adjustments: Adjustments,
        filmId: String? = null,
        exportSettings: ExportSettings = ExportSettings(),
        continueOnError: Boolean = true,
        onProgress: ((completed: Int, total: Int, currentFile: String) -> Unit)? = null,
    ) {
        if (_isProcessing.value) return
        _isProcessing.value = true
        _totalProgress.value = 0f

        // 创建任务列表
        val newJobs = imageUris.mapIndexed { index, uri ->
            val fileName = getFileName(uri) ?: "image_$index"
            BatchJob(
                id = "batch_${System.currentTimeMillis()}_$index",
                imageUri = uri,
                fileName = fileName,
                status = BatchJobStatus.PENDING,
            )
        }
        _jobs.value = newJobs

        // 准备调整参数（含胶片模拟；注意：蒙版类参数一般不带入批量，避免意外覆盖）
        val finalAdjustments = if (filmId != null) {
            FilmSimulation.getById(filmId)?.let { adjustments.withFilmSimulation(it) } ?: adjustments
        } else {
            adjustments.copy(flowMaskIntensity = 0f) // L05: 批量不带蒙版
        }

        // 逐个处理
        var completed = 0
        for (job in newJobs) {
            if (!_isProcessing.value) break // 被取消

            updateJobStatus(job.id, BatchJobStatus.PROCESSING)
            onProgress?.invoke(completed, newJobs.size, job.fileName)
            showProgressNotification(completed, newJobs.size, job.fileName)

            try {
                // 1. 解码
                val decoded = withContext(Dispatchers.IO) {
                    imageProcessor.loadAndDecode(context, job.imageUri)
                }
                updateJobProgress(job.id, 0.3f)

                // 2. 处理
                val processed = withContext(Dispatchers.Default) {
                    imageProcessor.processFullResolution(finalAdjustments, decoded.original)
                }
                updateJobProgress(job.id, 0.6f)

                // 3. 导出
                updateJobStatus(job.id, BatchJobStatus.EXPORTING)
                val exportUri = withContext(Dispatchers.IO) {
                    imageProcessor.exportImage(
                        processed, exportSettings, context, decoded.exif, decoded.orientation
                    )
                }
                updateJobProgress(job.id, 0.9f)

                // 回收
                if (processed !== decoded.original) processed.recycle()

                updateJobResult(job.id, BatchJobStatus.COMPLETED, resultUri = exportUri)
                completed++
            } catch (e: Exception) {
                Log.e(TAG, "Batch job failed: ${job.fileName}", e)
                updateJobResult(job.id, BatchJobStatus.FAILED, error = e.localizedMessage)
                if (!continueOnError) {
                    _isProcessing.value = false
                    return
                }
                completed++
            }

            _totalProgress.value = completed.toFloat() / newJobs.size
            onProgress?.invoke(completed, newJobs.size, job.fileName)
        }

        showProgressNotification(completed, newJobs.size, "", isComplete = true)
        _isProcessing.value = false
    }

    fun cancelBatch() {
        _isProcessing.value = false
        _jobs.value = _jobs.value.map {
            if (it.status == BatchJobStatus.PENDING || it.status == BatchJobStatus.PROCESSING) {
                it.copy(status = BatchJobStatus.CANCELLED)
            } else it
        }
    }

    fun clearCompletedJobs() {
        _jobs.value = _jobs.value.filter {
            it.status != BatchJobStatus.COMPLETED && it.status != BatchJobStatus.FAILED
        }
    }

    private suspend fun updateJobStatus(jobId: String, status: BatchJobStatus) {
        _jobs.value = _jobs.value.map {
            if (it.id == jobId) it.copy(status = status) else it
        }
    }

    private suspend fun updateJobProgress(jobId: String, progress: Float) {
        _jobs.value = _jobs.value.map {
            if (it.id == jobId) it.copy(progress = progress) else it
        }
    }

    private suspend fun updateJobResult(jobId: String, status: BatchJobStatus, resultUri: Uri? = null, error: String? = null) {
        _jobs.value = _jobs.value.map {
            if (it.id == jobId) it.copy(status = status, resultUri = resultUri, error = error, progress = if (status == BatchJobStatus.COMPLETED) 1f else it.progress) else it
        }
    }

    private fun getFileName(uri: Uri): String? {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) cursor.getString(idx) else null
            } else null
        }
    }

    companion object {
        private const val TAG = "BatchProcessor"
    }
}

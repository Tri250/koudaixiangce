package com.alcedo.studio.core

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.alcedo.studio.data.db.ExportJobEntity
import com.alcedo.studio.data.model.Adjustments
import com.alcedo.studio.data.model.ExportSettings
import com.alcedo.studio.data.repository.ExportJobRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BatchProcessor(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val imageProcessor = ImageProcessor(context)
    private val imageExporter = ImageExporter(context)
    private val exportJobRepository = ExportJobRepository(context)
    private var processingJob: Job? = null

    private val _batchState = MutableStateFlow(BatchState())
    val batchState: StateFlow<BatchState> = _batchState.asStateFlow()

    fun processBatch(
        items: List<BatchItem>,
        settings: ExportSettings,
        defaultAdjustments: Adjustments = Adjustments.Default
    ) {
        if (processingJob?.isActive == true) return

        _batchState.value = BatchState(
            totalItems = items.size,
            completedItems = 0,
            failedItems = 0,
            isProcessing = true,
            currentItemIndex = 0
        )

        processingJob = scope.launch {
            items.forEachIndexed { index, item ->
                _batchState.value = _batchState.value.copy(
                    currentItemIndex = index,
                    currentItemName = item.displayName,
                    currentProgress = 0f
                )

                try {
                    val bitmap = loadBitmap(item.uri)
                    if (bitmap != null) {
                        val adjustments = item.adjustments ?: defaultAdjustments

                        var finalBitmap: Bitmap? = null
                        imageProcessor.process(bitmap, adjustments).collect { progress ->
                            _batchState.value = _batchState.value.copy(
                                currentProgress = progress.progress * 0.7f
                            )
                            if (progress.result != null) {
                                finalBitmap = progress.result
                            }
                        }

                        finalBitmap?.let { bmp ->
                            imageExporter.export(bmp, settings, item.displayName).collect { expProgress ->
                                _batchState.value = _batchState.value.copy(
                                    currentProgress = 70f + expProgress.progress * 0.3f
                                )
                            }
                        }

                        _batchState.value = _batchState.value.copy(
                            completedItems = _batchState.value.completedItems + 1
                        )
                    } else {
                        _batchState.value = _batchState.value.copy(
                            failedItems = _batchState.value.failedItems + 1
                        )
                    }
                } catch (e: Exception) {
                    _batchState.value = _batchState.value.copy(
                        failedItems = _batchState.value.failedItems + 1
                    )
                }
            }

            _batchState.value = _batchState.value.copy(
                isProcessing = false,
                currentProgress = 100f
            )
        }
    }

    private suspend fun loadBitmap(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                android.graphics.BitmapFactory.decodeStream(input)
            }
        } catch (e: Exception) {
            null
        }
    }

    fun cancel() {
        processingJob?.cancel()
        _batchState.value = _batchState.value.copy(isProcessing = false)
    }

    fun reset() {
        _batchState.value = BatchState()
    }

    data class BatchState(
        val totalItems: Int = 0,
        val completedItems: Int = 0,
        val failedItems: Int = 0,
        val isProcessing: Boolean = false,
        val currentItemIndex: Int = 0,
        val currentItemName: String = "",
        val currentProgress: Float = 0f,
        val overallProgress: Float = 0f
    ) {
        val overallProgressFloat: Float
            get() = if (totalItems > 0) {
                (completedItems + currentProgress / 100f) / totalItems
            } else 0f
    }

    data class BatchItem(
        val uri: Uri,
        val displayName: String,
        val adjustments: Adjustments? = null
    )
}

class ExportQueueProcessor(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val imageProcessor = ImageProcessor(context)
    private val imageExporter = ImageExporter(context)
    private val repository = ExportJobRepository(context)
    private var processingJob: Job? = null

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    fun start() {
        if (processingJob?.isActive == true) return

        _isRunning.value = true
        processingJob = scope.launch {
            processPendingJobs()
            _isRunning.value = false
        }
    }

    private suspend fun processPendingJobs() {
        val jobs = repository.getPendingAndRunningJobs()

        jobs.collect { jobList ->
            jobList.filter { it.status == ExportJob.STATUS_PENDING }.firstOrNull()?.let { job ->
                processJob(job)
            }
        }
    }

    private suspend fun processJob(job: ExportJobEntity) {
        val updatedJob = job.copy(status = ExportJob.STATUS_RUNNING)
        repository.updateJob(updatedJob)

        try {
            val uri = Uri.parse(job.sourceUri)
            val bitmap = loadBitmap(uri)

            if (bitmap != null) {
                val settings = parseExportSettings(job.settingsJson)

                var finalBitmap: Bitmap? = null
                imageProcessor.process(bitmap, Adjustments.Default).collect { progress ->
                    if (progress.result != null) {
                        finalBitmap = progress.result
                    }
                }

                finalBitmap?.let { bmp ->
                    var outputUri: Uri? = null
                    imageExporter.export(bmp, settings).collect { expProgress ->
                        if (expProgress.outputUri != null) {
                            outputUri = expProgress.outputUri
                        }
                    }

                    outputUri?.let { outUri ->
                        repository.updateJob(
                            updatedJob.copy(
                                status = ExportJob.STATUS_COMPLETED,
                                progress = 100,
                                outputUri = outUri.toString(),
                                completedAt = System.currentTimeMillis()
                            )
                        )
                    }
                }
            } else {
                repository.updateJob(
                    updatedJob.copy(
                        status = ExportJob.STATUS_FAILED,
                        errorMessage = "无法加载图片"
                    )
                )
            }
        } catch (e: Exception) {
            repository.updateJob(
                updatedJob.copy(
                    status = ExportJob.STATUS_FAILED,
                    errorMessage = e.message ?: "未知错误"
                )
            )
        }
    }

    private suspend fun loadBitmap(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                android.graphics.BitmapFactory.decodeStream(input)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseExportSettings(json: String): ExportSettings {
        return try {
            kotlinx.serialization.json.Json.decodeFromString<ExportSettings>(json)
        } catch (e: Exception) {
            ExportSettings.Default
        }
    }

    fun cancel(jobId: Long) {
        scope.launch {
            repository.cancelJob(jobId)
        }
    }

    fun stop() {
        processingJob?.cancel()
        _isRunning.value = false
    }

    companion object {
        const val STATUS_PENDING = 0
        const val STATUS_RUNNING = 1
        const val STATUS_COMPLETED = 2
        const val STATUS_FAILED = 3
        const val STATUS_CANCELLED = 4
    }
}

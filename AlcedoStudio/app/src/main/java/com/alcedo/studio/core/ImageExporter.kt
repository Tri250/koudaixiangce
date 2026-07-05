package com.alcedo.studio.core

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.alcedo.studio.data.model.ExportFormat
import com.alcedo.studio.data.model.ExportJob
import com.alcedo.studio.data.model.ExportSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.OutputStream

class ImageExporter(private val context: Context) {

    suspend fun export(
        sourceBitmap: Bitmap,
        settings: ExportSettings,
        displayName: String = "export"
    ): Flow<ExportProgress> = flow {
        emit(ExportProgress(0f, "准备导出..."))

        val format = ExportFormat.from(settings.format)
        val finalBitmap = resizeBitmap(sourceBitmap, settings)

        emit(ExportProgress(30f, "编码中..."))

        val outputUri = saveToMediaStore(finalBitmap, settings, format, displayName)

        emit(ExportProgress(100f, "导出完成", outputUri))
    }.flowOn(Dispatchers.Default)

    private fun resizeBitmap(bitmap: Bitmap, settings: ExportSettings): Bitmap {
        val mode = com.alcedo.studio.data.model.ResizeMode.from(settings.resizeMode)
        if (mode == com.alcedo.studio.data.model.ResizeMode.NONE) return bitmap

        val (targetW, targetH) = when (mode) {
            com.alcedo.studio.data.model.ResizeMode.PERCENT -> {
                val pct = settings.resizePercent / 100f
                (bitmap.width * pct).toInt() to (bitmap.height * pct).toInt()
            }
            com.alcedo.studio.data.model.ResizeMode.LONG_EDGE -> {
                val longEdge = settings.resizeValue
                if (bitmap.width >= bitmap.height) {
                    longEdge to (bitmap.height * longEdge / bitmap.width)
                } else {
                    (bitmap.width * longEdge / bitmap.height) to longEdge
                }
            }
            com.alcedo.studio.data.model.ResizeMode.SHORT_EDGE -> {
                val shortEdge = settings.resizeValue
                if (bitmap.width <= bitmap.height) {
                    shortEdge to (bitmap.height * shortEdge / bitmap.width)
                } else {
                    (bitmap.width * shortEdge / bitmap.height) to shortEdge
                }
            }
            com.alcedo.studio.data.model.ResizeMode.DIMENSIONS -> {
                settings.resizeWidth to settings.resizeHeight
            }
            else -> return bitmap
        }

        if (targetW <= 0 || targetH <= 0) return bitmap
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }

    private fun saveToMediaStore(
        bitmap: Bitmap,
        settings: ExportSettings,
        format: ExportFormat,
        displayName: String
    ): Uri {
        val fileName = buildFileName(displayName, format)
        val mimeType = getMimeType(format)

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/AlcedoStudio"
                )
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val contentResolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val uri = contentResolver.insert(collection, contentValues)
            ?: throw IllegalStateException("无法创建媒体文件")

        try {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                val compressFormat = when (format) {
                    ExportFormat.JPEG -> Bitmap.CompressFormat.JPEG
                    ExportFormat.PNG -> Bitmap.CompressFormat.PNG
                    ExportFormat.WEBP -> Bitmap.CompressFormat.WEBP
                    else -> Bitmap.CompressFormat.JPEG
                }
                val quality = settings.quality.coerceIn(1, 100)
                bitmap.compress(compressFormat, quality, outputStream)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                contentResolver.update(uri, contentValues, null, null)
            }
        } catch (e: Exception) {
            contentResolver.delete(uri, null, null)
            throw e
        }

        return uri
    }

    private fun buildFileName(baseName: String, format: ExportFormat): String {
        val extension = when (format) {
            ExportFormat.JPEG -> "jpg"
            ExportFormat.PNG -> "png"
            ExportFormat.TIFF -> "tiff"
            ExportFormat.HEIF -> "heic"
            ExportFormat.WEBP -> "webp"
        }
        val timestamp = System.currentTimeMillis()
        return "${baseName}_${timestamp}.$extension"
    }

    private fun getMimeType(format: ExportFormat): String = when (format) {
        ExportFormat.JPEG -> "image/jpeg"
        ExportFormat.PNG -> "image/png"
        ExportFormat.TIFF -> "image/tiff"
        ExportFormat.HEIF -> "image/heic"
        ExportFormat.WEBP -> "image/webp"
    }

    data class ExportProgress(
        val progress: Float,
        val message: String,
        val outputUri: Uri? = null
    )
}

class ExportQueueManager(private val context: Context) {

    private val pendingJobs = mutableListOf<ExportJob>()
    private var isProcessing = false

    suspend fun addJob(job: ExportJob) {
        pendingJobs.add(job)
        if (!isProcessing) {
            processQueue()
        }
    }

    private suspend fun processQueue() {
        isProcessing = true
        while (pendingJobs.isNotEmpty()) {
            val job = pendingJobs.removeFirst()
            // Process job...
        }
        isProcessing = false
    }

    fun getPendingJobs(): List<ExportJob> = pendingJobs.toList()

    fun cancelJob(jobId: Long) {
        pendingJobs.removeAll { it.id == jobId }
    }
}

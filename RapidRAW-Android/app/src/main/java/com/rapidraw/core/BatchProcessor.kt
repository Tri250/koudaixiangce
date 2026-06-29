package com.rapidraw.core

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class BatchProcessor(private val context: Context, private val imageProcessor: ImageProcessor) {

    data class BatchProgress(
        val current: Int,
        val total: Int,
        val currentFileName: String,
        val isComplete: Boolean = false,
        val error: String? = null,
        val successCount: Int = 0,
        val failedCount: Int = 0,
    )

    data class BatchResult(
        val total: Int,
        val successCount: Int,
        val failedCount: Int,
        val failedFiles: List<String>,
    )

    private val threadCount = DeviceOptimizer.getOptimalExportThreadCount()

    // Apply the same film simulation to multiple images
    fun batchApplyFilm(
        imageUris: List<Uri>,
        filmAdjustments: com.rapidraw.data.model.Adjustments,
        exportSettings: com.rapidraw.data.model.ExportSettings,
    ): Flow<BatchProgress> = flow {
        val total = imageUris.size
        var successCount = 0
        var failedCount = 0
        val failedFiles = mutableListOf<String>()

        imageUris.forEachIndexed { index, uri ->
            try {
                val fileName = uri.lastPathSegment ?: "image_$index"
                emit(BatchProgress(
                    current = index + 1,
                    total = total,
                    currentFileName = fileName,
                    successCount = successCount,
                    failedCount = failedCount,
                ))

                withContext(Dispatchers.IO) {
                    val processed = imageProcessor.loadAndDecode(context, uri)
                    // Apply film adjustments to full resolution
                    val adjusted = imageProcessor.processFullResolution(filmAdjustments, processed.original)
                    // Release decoded bitmaps no longer needed (adjusted is a fresh bitmap)
                    processed.original.recycle()
                    processed.preview.recycle()
                    // Export
                    imageProcessor.exportImage(adjusted, exportSettings, context, processed.exif, processed.orientation)
                    // Clean up
                    if (adjusted != processed.original) adjusted.recycle()
                }
                successCount++
            } catch (e: Exception) {
                failedCount++
                failedFiles.add(uri.lastPathSegment ?: "unknown")
                emit(BatchProgress(
                    current = index + 1,
                    total = total,
                    currentFileName = uri.lastPathSegment ?: "unknown",
                    error = e.message,
                    successCount = successCount,
                    failedCount = failedCount,
                ))
            }
        }
        emit(BatchProgress(
            current = total,
            total = total,
            currentFileName = "",
            isComplete = true,
            successCount = successCount,
            failedCount = failedCount,
        ))
    }.flowOn(Dispatchers.IO)

    // Batch export with same settings
    fun batchExport(
        imageUris: List<Uri>,
        exportSettings: com.rapidraw.data.model.ExportSettings,
    ): Flow<BatchProgress> = flow {
        val total = imageUris.size
        var successCount = 0
        var failedCount = 0

        imageUris.forEachIndexed { index, uri ->
            try {
                val fileName = uri.lastPathSegment ?: "image_$index"
                emit(BatchProgress(
                    current = index + 1,
                    total = total,
                    currentFileName = fileName,
                    successCount = successCount,
                    failedCount = failedCount,
                ))

                withContext(Dispatchers.IO) {
                    val processed = imageProcessor.loadAndDecode(context, uri)
                    imageProcessor.exportImage(processed.original, exportSettings, context, processed.exif, processed.orientation)
                    // Release decoded bitmaps after export
                    processed.original.recycle()
                    processed.preview.recycle()
                }
                successCount++
            } catch (e: Exception) {
                failedCount++
                emit(BatchProgress(
                    current = index + 1,
                    total = total,
                    currentFileName = uri.lastPathSegment ?: "unknown",
                    error = e.message,
                    successCount = successCount,
                    failedCount = failedCount,
                ))
            }
        }
        emit(BatchProgress(
            current = total,
            total = total,
            currentFileName = "",
            isComplete = true,
            successCount = successCount,
            failedCount = failedCount,
        ))
    }.flowOn(Dispatchers.IO)

    // Batch apply saved adjustments (copy-paste workflow)
    fun batchApplyAdjustments(
        imageUris: List<Uri>,
        adjustments: com.rapidraw.data.model.Adjustments,
        exportSettings: com.rapidraw.data.model.ExportSettings,
    ): Flow<BatchProgress> = batchApplyFilm(imageUris, adjustments, exportSettings)

    // Batch rename with pattern
    fun batchRename(
        imageUris: List<Uri>,
        pattern: String,
        startIndex: Int = 1,
    ): Flow<BatchProgress> = flow {
        val total = imageUris.size
        var successCount = 0
        var failedCount = 0

        imageUris.forEachIndexed { index, uri ->
            try {
                val fileName = uri.lastPathSegment ?: "image_$index"
                emit(BatchProgress(
                    current = index + 1,
                    total = total,
                    currentFileName = fileName,
                    successCount = successCount,
                    failedCount = failedCount,
                ))

                withContext(Dispatchers.IO) {
                    val newName = pattern
                        .replace("{index}", String.format("%03d", startIndex + index))
                        .replace("{original}", fileName.substringBeforeLast('.'))
                    val extension = fileName.substringAfterLast('.', "jpg")
                    val newFileName = "$newName.$extension"

                    if (uri.scheme == "file") {
                        val file = java.io.File(uri.path!!)
                        val parent = file.parentFile
                        if (parent != null && file.exists()) {
                            val newFile = java.io.File(parent, newFileName)
                            file.renameTo(newFile)
                        }
                    }
                }
                successCount++
            } catch (e: Exception) {
                failedCount++
                emit(BatchProgress(
                    current = index + 1,
                    total = total,
                    currentFileName = uri.lastPathSegment ?: "unknown",
                    error = e.message,
                    successCount = successCount,
                    failedCount = failedCount,
                ))
            }
        }
        emit(BatchProgress(
            current = total,
            total = total,
            currentFileName = "",
            isComplete = true,
            successCount = successCount,
            failedCount = failedCount,
        ))
    }.flowOn(Dispatchers.IO)

    // Batch resize
    fun batchResize(
        imageUris: List<Uri>,
        maxWidth: Int,
        maxHeight: Int,
        exportSettings: com.rapidraw.data.model.ExportSettings,
    ): Flow<BatchProgress> = flow {
        val total = imageUris.size
        var successCount = 0
        var failedCount = 0

        imageUris.forEachIndexed { index, uri ->
            try {
                val fileName = uri.lastPathSegment ?: "image_$index"
                emit(BatchProgress(
                    current = index + 1,
                    total = total,
                    currentFileName = fileName,
                    successCount = successCount,
                    failedCount = failedCount,
                ))

                withContext(Dispatchers.IO) {
                    val processed = imageProcessor.loadAndDecode(context, uri)
                    val original = processed.original

                    val ratio = minOf(
                        maxWidth.toFloat() / original.width,
                        maxHeight.toFloat() / original.height,
                        1f
                    )

                    val resized = if (ratio < 1f) {
                        Bitmap.createScaledBitmap(
                            original,
                            (original.width * ratio).toInt(),
                            (original.height * ratio).toInt(),
                            true
                        )
                    } else {
                        original
                    }

                    imageProcessor.exportImage(resized, exportSettings, context, processed.exif, processed.orientation)

                    processed.original.recycle()
                    processed.preview.recycle()
                    if (resized != original) resized.recycle()
                }
                successCount++
            } catch (e: Exception) {
                failedCount++
                emit(BatchProgress(
                    current = index + 1,
                    total = total,
                    currentFileName = uri.lastPathSegment ?: "unknown",
                    error = e.message,
                    successCount = successCount,
                    failedCount = failedCount,
                ))
            }
        }
        emit(BatchProgress(
            current = total,
            total = total,
            currentFileName = "",
            isComplete = true,
            successCount = successCount,
            failedCount = failedCount,
        ))
    }.flowOn(Dispatchers.IO)

    // Batch format conversion
    fun batchConvertFormat(
        imageUris: List<Uri>,
        targetFormat: com.rapidraw.data.model.ExportFormat,
        quality: Int = 85,
    ): Flow<BatchProgress> = flow {
        val total = imageUris.size
        var successCount = 0
        var failedCount = 0

        imageUris.forEachIndexed { index, uri ->
            try {
                val fileName = uri.lastPathSegment ?: "image_$index"
                emit(BatchProgress(
                    current = index + 1,
                    total = total,
                    currentFileName = fileName,
                    successCount = successCount,
                    failedCount = failedCount,
                ))

                withContext(Dispatchers.IO) {
                    val processed = imageProcessor.loadAndDecode(context, uri)
                    val exportSettings = com.rapidraw.data.model.ExportSettings(
                        format = targetFormat,
                        quality = quality,
                    )
                    imageProcessor.exportImage(processed.original, exportSettings, context, processed.exif, processed.orientation)
                    processed.original.recycle()
                    processed.preview.recycle()
                }
                successCount++
            } catch (e: Exception) {
                failedCount++
                emit(BatchProgress(
                    current = index + 1,
                    total = total,
                    currentFileName = uri.lastPathSegment ?: "unknown",
                    error = e.message,
                    successCount = successCount,
                    failedCount = failedCount,
                ))
            }
        }
        emit(BatchProgress(
            current = total,
            total = total,
            currentFileName = "",
            isComplete = true,
            successCount = successCount,
            failedCount = failedCount,
        ))
    }.flowOn(Dispatchers.IO)
}

package com.rapidraw.core

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class BatchProcessor(private val context: Context, private val imageProcessor: ImageProcessor) {

    companion object {
        private const val TAG = "BatchProcessor"
    }

    data class BatchProgress(
        val current: Int,
        val total: Int,
        val currentFileName: String,
        val isComplete: Boolean = false,
        val error: String? = null,
    )

    // Apply the same film simulation to multiple images
    fun batchApplyFilm(
        imageUris: List<Uri>,
        filmAdjustments: com.rapidraw.data.model.Adjustments,
        exportSettings: com.rapidraw.data.model.ExportSettings,
    ): Flow<BatchProgress> = flow {
        val total = imageUris.size
        imageUris.forEachIndexed { index, uri ->
            try {
                val fileName = uri.lastPathSegment ?: "image_$index"
                emit(BatchProgress(current = index + 1, total = total, currentFileName = fileName))

                withContext(Dispatchers.IO) {
                    val processed = try {
                        imageProcessor.loadAndDecode(context, uri)
                    } catch (e: OutOfMemoryError) {
                        Log.e(TAG, "OOM loading image for batch: $uri", e)
                        throw e
                    }
                    // 2026 hotfix: processed 内部位图可能因 OOM/解码失败为 null，做空检查
                    if (processed.original.isRecycled || processed.preview.isRecycled) {
                        throw IllegalStateException("Decoded bitmap already recycled for: $uri")
                    }
                    // Apply film adjustments to full resolution
                    val adjusted = imageProcessor.processFullResolution(filmAdjustments, processed.original)
                    // Release decoded bitmaps no longer needed (adjusted is a fresh bitmap)
                    if (!processed.original.isRecycled) processed.original.recycle()
                    if (!processed.preview.isRecycled) processed.preview.recycle()
                    try {
                        // Export
                        imageProcessor.exportImage(adjusted, exportSettings, context, processed.exif, processed.orientation)
                    } finally {
                        // Clean up
                        if (adjusted !== processed.original && !adjusted.isRecycled) adjusted.recycle()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Batch apply film failed for $uri", e)
                emit(BatchProgress(
                    current = index + 1, total = total,
                    currentFileName = uri.lastPathSegment ?: "unknown",
                    error = e.message
                ))
            }
        }
        emit(BatchProgress(current = total, total = total, currentFileName = "", isComplete = true))
    }.flowOn(Dispatchers.IO)

    // Batch export with same settings
    fun batchExport(
        imageUris: List<Uri>,
        exportSettings: com.rapidraw.data.model.ExportSettings,
    ): Flow<BatchProgress> = flow {
        val total = imageUris.size
        imageUris.forEachIndexed { index, uri ->
            try {
                val fileName = uri.lastPathSegment ?: "image_$index"
                emit(BatchProgress(current = index + 1, total = total, currentFileName = fileName))

                withContext(Dispatchers.IO) {
                    val processed = imageProcessor.loadAndDecode(context, uri)
                    if (processed.original.isRecycled || processed.preview.isRecycled) {
                        throw IllegalStateException("Decoded bitmap already recycled for: $uri")
                    }
                    try {
                        imageProcessor.exportImage(processed.original, exportSettings, context, processed.exif, processed.orientation)
                    } finally {
                        // Release decoded bitmaps after export
                        if (!processed.original.isRecycled) processed.original.recycle()
                        if (!processed.preview.isRecycled) processed.preview.recycle()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Batch export failed for $uri", e)
                emit(BatchProgress(
                    current = index + 1, total = total,
                    currentFileName = uri.lastPathSegment ?: "unknown",
                    error = e.message
                ))
            }
        }
        emit(BatchProgress(current = total, total = total, currentFileName = "", isComplete = true))
    }.flowOn(Dispatchers.IO)

    // Batch apply saved adjustments (copy-paste workflow)
    fun batchApplyAdjustments(
        imageUris: List<Uri>,
        adjustments: com.rapidraw.data.model.Adjustments,
        exportSettings: com.rapidraw.data.model.ExportSettings,
    ): Flow<BatchProgress> = batchApplyFilm(imageUris, adjustments, exportSettings)
}


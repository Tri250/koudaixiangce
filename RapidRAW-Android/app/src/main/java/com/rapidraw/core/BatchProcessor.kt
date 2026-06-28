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
                    val processed = imageProcessor.loadAndDecode(context, uri)
                    // Apply film adjustments to full resolution
                    val adjusted = imageProcessor.processFullResolution(filmAdjustments, processed.original)
                    // Release decoded bitmaps no longer needed (adjusted is a fresh bitmap)
                    processed.original.recycle()
                    processed.preview.recycle()
                    // Export
                    imageProcessor.exportImage(adjusted, exportSettings.toCore(), context)
                    // Clean up
                    if (adjusted != processed.original) adjusted.recycle()
                }
            } catch (e: Exception) {
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
                    imageProcessor.exportImage(processed.original, exportSettings.toCore(), context)
                    // Release decoded bitmaps after export
                    processed.original.recycle()
                    processed.preview.recycle()
                }
            } catch (e: Exception) {
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

    private fun com.rapidraw.data.model.ExportSettings.toCore(): ExportSettings {
        var maxWidth = 0
        var maxHeight = 0
        when (this.resizeMode) {
            com.rapidraw.data.model.ResizeMode.WIDTH -> maxWidth = this.resizeValue
            com.rapidraw.data.model.ResizeMode.HEIGHT -> maxHeight = this.resizeValue
            com.rapidraw.data.model.ResizeMode.LONG_EDGE -> {
                maxWidth = this.resizeValue
                maxHeight = this.resizeValue
            }
            com.rapidraw.data.model.ResizeMode.SHORT_EDGE -> {
                maxWidth = this.resizeValue
                maxHeight = this.resizeValue
            }
            com.rapidraw.data.model.ResizeMode.ORIGINAL -> {
                // leave as 0
            }
        }
        return ExportSettings(
            format = this.format.name,
            quality = this.quality,
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            preserveMetadata = this.keepMetadata,
            socialAspectRatio = this.socialPlatform.aspectRatio,
            addWatermark = this.addWatermark,
            watermarkText = this.watermarkText,
            watermarkAnchor = this.watermarkAnchor.name,
            watermarkScale = this.watermarkScale,
            watermarkOpacity = this.watermarkOpacity,
        )
    }
}

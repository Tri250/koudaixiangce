package com.alcedo.studio.ui.editor

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alcedo.studio.core.BitmapManager
import com.alcedo.studio.core.ImageProcessor
import com.alcedo.studio.core.L
import com.alcedo.studio.core.SidecarManager
import com.alcedo.studio.core.VirtualCopyManager
import com.alcedo.studio.data.model.Adjustments
import com.alcedo.studio.data.model.ExportSettings
import com.alcedo.studio.data.model.Preset
import com.alcedo.studio.data.model.Project
import com.alcedo.studio.data.repository.PresetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class EditorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val presetRepository: PresetRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private var imageProcessor: ImageProcessor? = null
    private var sidecarManager: SidecarManager? = null
    private var virtualCopyManager: VirtualCopyManager? = null

    private var currentProject: Project? = null
    private var sourceBitmap: Bitmap? = null
    private var currentAdjustments: Adjustments = Adjustments.Default

    fun loadImage(uri: Uri, displayName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, sourceUri = uri)

            try {
                imageProcessor = ImageProcessor(context)
                sidecarManager = SidecarManager(uri, context)
                virtualCopyManager = VirtualCopyManager(uri, context)

                val bitmap = imageProcessor?.loadImage(uri)

                if (bitmap != null) {
                    sourceBitmap = bitmap
                    currentAdjustments = sidecarManager?.loadAdjustments() ?: Adjustments.Default

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        processedBitmap = bitmap,
                        adjustments = currentAdjustments,
                        canUndo = false,
                        canRedo = false,
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "无法加载图片",
                    )
                }
            } catch (e: Exception) {
                L.e("EditorViewModel", "Failed to load image", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message,
                )
            }
        }
    }

    fun updateAdjustments(block: (Adjustments) -> Adjustments) {
        val newAdjustments = block(currentAdjustments)
        currentAdjustments = newAdjustments

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                adjustments = newAdjustments,
                isProcessing = true,
                canUndo = true,
            )

            try {
                val processed = withContext(Dispatchers.Default) {
                    val bitmap = sourceBitmap ?: return@withContext null
                    val processor = imageProcessor ?: return@withContext null

                    var result: Bitmap? = null
                    processor.process(bitmap, newAdjustments).collect { progress ->
                        if (progress.result != null) {
                            result = progress.result
                        }
                    }
                    result
                }

                _uiState.value = _uiState.value.copy(
                    processedBitmap = processed,
                    isProcessing = false,
                )
            } catch (e: Exception) {
                L.e("EditorViewModel", "Failed to process image", e)
                _uiState.value = _uiState.value.copy(isProcessing = false)
            }
        }
    }

    fun undo() {
    }

    fun redo() {
    }

    fun rotateLeft() {
        updateAdjustments { it.copy(rotation = (it.rotation - 90f) % 360f) }
    }

    fun rotateRight() {
        updateAdjustments { it.copy(rotation = (it.rotation + 90f) % 360f) }
    }

    fun flipHorizontal() {
        updateAdjustments { it.copy(flipHorizontal = !it.flipHorizontal) }
    }

    fun flipVertical() {
        updateAdjustments { it.copy(flipVertical = !it.flipVertical) }
    }

    fun resetAdjustments() {
        currentAdjustments = Adjustments.Default
        updateAdjustments { Adjustments.Default }
    }

    fun applyPreset(preset: Preset) {
        updateAdjustments { preset.adjustments }
    }

    fun getPresets(): Flow<List<Preset>> = presetRepository.getAllPresets()

    fun exportImage(settings: ExportSettings) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExporting = true, exportProgress = 0f)

            try {
                withContext(Dispatchers.IO) {
                }

                _uiState.value = _uiState.value.copy(isExporting = false, exportProgress = 1f)
            } catch (e: Exception) {
                L.e("EditorViewModel", "Export failed", e)
                _uiState.value = _uiState.value.copy(isExporting = false)
            }
        }
    }

    fun saveProject() {
        viewModelScope.launch {
            val uri = _uiState.value.sourceUri ?: return@launch
            val displayName = uri.lastPathSegment ?: ""

            currentProject = Project(
                sourceUri = uri.toString(),
                displayName = displayName,
                adjustments = currentAdjustments,
            )

            sidecarManager?.saveAdjustments(currentAdjustments)
        }
    }

    override fun onCleared() {
        super.onCleared()
        sourceBitmap?.let { BitmapManager.returnBitmap(it) }
        sourceBitmap = null
    }
}

data class EditorUiState(
    val isLoading: Boolean = false,
    val isProcessing: Boolean = false,
    val isExporting: Boolean = false,
    val processingProgress: Float = 0f,
    val exportProgress: Float = 0f,
    val sourceUri: Uri? = null,
    val processedBitmap: Bitmap? = null,
    val adjustments: Adjustments = Adjustments.Default,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val error: String? = null,
)

package com.rawlab.android

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import java.io.File

enum class Operation { NONE, IMPORT, IMPORT_LOOK, PICK_EXPORT, EXPORT }
enum class EditSaveState { NONE, RESTORED, SAVED, FAILED }
data class PreviewPair(val neutral: Bitmap, val result: Bitmap, val temperature: Float, val tint: Float, val lens: LensInfo? = null)
data class EditorState(
    val photo: ImportedPhoto? = null,
    val edits: EditSettings = EditSettings(),
    val looks: List<LookChoice> = LookChoice.builtIns,
    val preview: PreviewPair? = null,
    val operation: Operation = Operation.NONE,
    val rendering: Boolean = false,
    val previewResizePending: Boolean = false,
    val exact: Boolean = false,
    val error: String? = null,
    val saveState: EditSaveState = EditSaveState.NONE,
    val message: String? = null,
    val lookImportReport: String? = null,
    val gpuEnabled: Boolean = true,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val showOriginal: Boolean = false,
    val originalBitmap: Bitmap? = null,
) {
    val canExport get() = photo != null && preview != null && exact && !rendering && !previewResizePending && operation == Operation.NONE
    val controlsEnabled get() = photo != null && operation == Operation.NONE
}

private sealed interface Work {
    data class Import(val uri: Uri, val gpuMode: Int) : Work
    data class Preview(val photo: ImportedPhoto, val edits: EditSettings, val interactive: Boolean,
        val gpuMode: Int, val importing: Boolean = false, val restored: Boolean = false,
        val edge: Int = 1600) : Work
    data class Export(val photo: ImportedPhoto, val edits: EditSettings, val destination: Uri?, val format: ExportFormat,
        val longEdge: Int?, val gpuMode: Int) : Work
    data class AutoExposure(val photo: ImportedPhoto, val edits: EditSettings) : Work
    data class PickWb(val photo: ImportedPhoto, val nx: Float, val ny: Float) : Work
}
private sealed interface WorkResult {
    data class Preview(val request: Work.Preview, val pair: PreviewPair) : WorkResult
    data class MissingLook(val photo: ImportedPhoto, val edits: EditSettings) : WorkResult
    data object Export : WorkResult
    data class AutoExposure(val request: Work.AutoExposure, val exposure: Float) : WorkResult
    data class PickWb(val request: Work.PickWb, val temperature: Float?, val tint: Float?) : WorkResult
}
private sealed interface LookWork {
    data object Load : LookWork
    data class Import(val uris: List<Uri>) : LookWork
    data class Rename(val id: String, val name: String) : LookWork
    data class Delete(val id: String) : LookWork
}
private sealed interface LookResult {
    data class Loaded(val looks: List<LookChoice>) : LookResult
    data class Imported(val imported: List<ManagedLook>, val failures: List<String>, val looks: List<LookChoice>) : LookResult
    data class Renamed(val look: ManagedLook, val looks: List<LookChoice>) : LookResult
    data class Deleted(val id: String, val looks: List<LookChoice>) : LookResult
    data class Failed(val error: Throwable, val reload: Boolean) : LookResult
}

class EditorViewModel(application: Application) : AndroidViewModel(application) {
    val storage = PhotoStorage(application)
    private var editStore: EditStore? = runCatching { EditStore(File(application.filesDir, "edits")) }.getOrNull()
    private val mutableBatch = MutableStateFlow<BatchExportModel?>(null)
    val batch = mutableBatch.asStateFlow()
    private val mutablePendingBatch = MutableStateFlow<List<BatchJob>>(emptyList())
    val pendingBatch = mutablePendingBatch.asStateFlow()
    private var processor: NativeProcessor? = null
    private var original: Pair<java.io.File, Bitmap>? = null
    // 普通照片的解码缓存（长边 1600），预览与自动测光共用，切换照片时失效。
    private var stillPixels: Pair<java.io.File, StillImage.Decoded>? = null
    private var currentRevision = 0L
    private var previewLongEdge = 1600
    private var requestedPreviewLongEdge = 1600
    private var previewResizeJob: Job? = null
    private var lookRevision = 0L
    private var releases = 0
    // FR-04: session-scoped snapshot history; drags merge into one step.
    private val history = EditHistory()
    private var dragActive = false
    // FR-05: hold-to-compare baseline. Development reference of the current
    // exposure mode with every user edit zeroed; cached per file+mode and
    // never recorded in the undo stack.
    private var baselineCache: Triple<File, Int, Bitmap>? = null
    private var baselineJob: Job? = null
    private var baselineRequest: Pair<File, Int>? = null
    private val mutable = MutableStateFlow(EditorState())
    val state = mutable.asStateFlow()
    private var disposed = false
    private val queue = RenderQueue<Work, WorkResult>(::perform, { revision, result ->
        viewModelScope.launch {
            if (!disposed && revision == currentRevision) accept(result)
        }
    }, { processor?.close(); releaseStorage() })
    private val lookQueue = RenderQueue<LookWork, LookResult>(::performLook, { revision, result ->
        viewModelScope.launch {
            if (!disposed && revision == lookRevision) acceptLook(result)
        }
    }, ::releaseStorage)

    init {
        if (editStore == null) mutable.value = mutable.value.copy(saveState = EditSaveState.FAILED,
            error = text(R.string.edit_storage_error))
        lookRevision = lookQueue.submit(LookWork.Load)
        refreshPendingBatch()
    }

    private fun engine() = processor ?: NativeProcessor(lensDatabase = storage.lensDatabase()).also { processor = it }

    // 照片管线的 LUT 选择：内置胶片 cube 是 F-Log2 Log 输入 LUT，仅适用于 RAW；
    // 照片只接受大师配方（.rlook）或无外观，否则核心会拒绝并破坏编辑流程。
    private fun photoLook(settings: EditSettings): File? =
        if (settings.recipeID != null || settings.film == "neutral") storage.lookPath(settings) else null

    // 托管文件从解析路径到 native 读取完成期间不能被另一队列删除。
    private fun perform(work: Work): WorkResult = synchronized(storage) { when (work) {
        is Work.Import -> {
            val photo = storage.import(work.uri)
            val restored = editStore?.load(photo.identity)
            // 照片类型在渲染线程按文件头判定，无需持久化标记。
            val still = RawFiles.kindOf(photo.file) == RawFiles.FileKind.STILL_IMAGE
            try {
                if (restored != null && runCatching { storage.lookPath(restored) }.isFailure) {
                    // 照片无需 native 中性预览校验，直接提示缺失的外观。
                    if (!still) engine().preview(photo.file, null, restored.copy(film = "neutral", recipeID = null), 256, false)
                    WorkResult.MissingLook(photo, restored)
                } else perform(Work.Preview(photo, restored ?: EditSettings(), false, work.gpuMode,
                    importing = true, restored = restored != null))
            }
            catch (error: Throwable) { photo.file.delete(); throw error }
        }
        is Work.Preview -> {
            val edge = work.edge
            val native = engine()
            native.setGpuMode(work.gpuMode)
            val still = RawFiles.kindOf(work.photo.file) == RawFiles.FileKind.STILL_IMAGE
            if (still) {
                if (original?.first != work.photo.file) {
                    original = null
                    stillPixels = null
                    val decoded = StillImage.decode(work.photo.file, 1600)
                    stillPixels = work.photo.file to decoded
                    original = work.photo.file to decoded.bitmap()
                }
                val pixels = stillPixels?.takeIf { it.first == work.photo.file }?.second
                    ?: StillImage.decode(work.photo.file, 1600).also { stillPixels = work.photo.file to it }
                val film = native.previewPhoto(pixels, photoLook(work.edits), work.edits, edge, work.interactive)
                // 照片不走下方 RAW 管线：掉进去会让 LibRaw 打开 JPEG 并报 io error。
                return@synchronized WorkResult.Preview(work, PreviewPair(original!!.second, film.bitmap(), film.temperature, film.tint))
            }
            if (original?.first != work.photo.file) {
                original = null
                original = work.photo.file to native.preview(work.photo.file, null, EditSettings(), 1600, false).bitmap()
            }
            val lut = storage.lookPath(work.edits)
            val film = native.preview(work.photo.file, lut, work.edits, edge, work.interactive)
            WorkResult.Preview(work, PreviewPair(original!!.second, film.bitmap(), film.temperature, film.tint, film.lens))
        }
        is Work.Export -> {
            val output = storage.temporaryOutput(work.format)
            try {
                engine().setGpuMode(work.gpuMode)
                if (RawFiles.kindOf(work.photo.file) == RawFiles.FileKind.STILL_IMAGE) {
                    // 导出按原始分辨率重新解码，不占用预览解码缓存。
                    val pixels = StillImage.decode(work.photo.file)
                    engine().exportPhoto(work.photo.file, pixels, photoLook(work.edits), work.edits, output, work.format, work.longEdge)
                } else {
                    engine().export(work.photo.file, storage.lookPath(work.edits), work.edits, output, work.format, work.longEdge)
                }
                if (work.destination == null) storage.saveAlbum(output, work.format)
                else storage.saveDocument(output, work.destination, work.photo.uri)
            } finally { output.delete() }
            WorkResult.Export
        }
        is Work.AutoExposure -> {
            if (RawFiles.kindOf(work.photo.file) != RawFiles.FileKind.STILL_IMAGE)
                return@synchronized WorkResult.AutoExposure(work, engine().autoExposure(work.photo.file, work.edits))
            val pixels = stillPixels?.takeIf { it.first == work.photo.file }?.second
                ?: StillImage.decode(work.photo.file, 1600).also { stillPixels = work.photo.file to it }
            WorkResult.AutoExposure(work, engine().autoExposurePhoto(pixels, work.edits))
        }
        is Work.PickWb -> {
            // Sample over the RAW decoded by the most recent preview on this
            // session; any engine refusal (including UNSUPPORTED) becomes the
            // UI's "cannot sample this point" notice.
            val picked = runCatching { engine().pickWhiteBalance(work.nx, work.ny) }.getOrNull()
            WorkResult.PickWb(work, picked?.getOrNull(0), picked?.getOrNull(1))
        }
    } }

    private fun performLook(work: LookWork): LookResult = synchronized(storage) { try {
        when (work) {
            LookWork.Load -> LookResult.Loaded(storage.looks())
            is LookWork.Import -> {
                val imported = mutableListOf<ManagedLook>()
                val failures = mutableListOf<String>()
                for (uri in work.uris) {
                    try { imported += storage.importLook(uri) }
                    catch (error: Exception) { failures += error.message ?: error.javaClass.simpleName }
                }
                LookResult.Imported(imported, failures, storage.looks())
            }
            is LookWork.Rename -> {
                val look = storage.renameLook(work.id, work.name)
                LookResult.Renamed(look, storage.looks())
            }
            is LookWork.Delete -> {
                storage.deleteLook(work.id)
                LookResult.Deleted(work.id, storage.looks())
            }
        }
    } catch (error: Throwable) {
        LookResult.Failed(error, work != LookWork.Load)
    } }

    private fun accept(result: Result<WorkResult>) {
        result.fold({ value ->
            when (value) {
                is WorkResult.MissingLook -> {
                    mutable.value.photo?.file?.takeIf { it != value.photo.file }?.delete()
                    mutable.value = EditorStateReducer.renderFinished(mutable.value).copy(photo = value.photo,
                        edits = value.edits, preview = null, exact = false,
                        saveState = EditSaveState.RESTORED, error = text(R.string.look_unavailable))
                }
                is WorkResult.Preview -> {
                    val request = value.request
                    if (request.importing) {
                        mutable.value.photo?.file?.takeIf { it != request.photo.file }?.delete()
                    }
                    val settings = if (!request.edits.customWb && value.pair.temperature.isFinite())
                        request.edits.copy(temperature = value.pair.temperature.coerceIn(2000f, 50000f),
                            tint = if (request.edits.recipeID == null) value.pair.tint.coerceIn(-150f, 150f) else request.edits.tint)
                    else request.edits
                    val saved = when {
                        request.importing -> if (editStore == null) EditSaveState.FAILED else if (request.restored) EditSaveState.RESTORED else EditSaveState.NONE
                        request.interactive -> mutable.value.saveState
                        else -> persist(request.photo, settings)
                    }
                    mutable.value = EditorStateReducer.renderFinished(mutable.value).copy(
                        photo = request.photo, edits = settings, preview = value.pair,
                        exact = !request.interactive, error = null,
                        saveState = if (request.restored && saved == EditSaveState.SAVED) EditSaveState.RESTORED else saved)
                }
                WorkResult.Export -> mutable.value = mutable.value.copy(operation = Operation.NONE, message = text(R.string.export_done))
                is WorkResult.AutoExposure -> {
                    val current = mutable.value
                    if (current.photo?.identity == value.request.photo.identity && current.edits == value.request.edits) {
                        edit(current.edits.copy(exposure = value.exposure))
                    }
                }
                is WorkResult.PickWb -> {
                    mutable.value = withHistory(EditorStateReducer.renderFinished(mutable.value))
                    val current = mutable.value
                    val picked = value.temperature != null && value.tint != null &&
                        value.request.photo.identity == current.photo?.identity
                    if (picked) {
                        // Eyedropper sampling is a single undo step (FR-03/FR-04).
                        edit(current.edits.copy(customWb = true,
                            temperature = value.temperature!!.coerceIn(2000f, 50000f),
                            tint = value.tint!!.coerceIn(-150f, 150f)), false)
                    } else {
                        mutable.value = current.copy(message = text(R.string.wb_pick_failed))
                    }
                }
            }
            fadeSaveNotice()
        }, { error ->
            mutable.value = EditorStateReducer.renderFinished(mutable.value).copy(error = failure(error))
        })
        updatePreviewSize(requestedPreviewLongEdge)
    }

    private fun acceptLook(result: Result<LookResult>) {
        result.fold(::acceptLookValue, { error ->
            mutable.value = mutable.value.copy(operation = Operation.NONE,
                error = text(R.string.look_storage_error) + "\n" + (error.message ?: error.javaClass.simpleName))
        })
        updatePreviewSize(requestedPreviewLongEdge)
    }

    private fun acceptLookValue(result: LookResult) {
        when (result) {
            is LookResult.Loaded -> mutable.value = EditorStateReducer.looksLoaded(mutable.value, result.looks)
            is LookResult.Imported -> {
                val mutation = result.imported.lastOrNull()?.let { EditorStateReducer.lookImported(mutable.value, it) }
                    ?: EditorMutation(mutable.value.copy(operation = Operation.NONE), null)
                val report = if (result.failures.isEmpty()) null else
                    AppStrings.format(R.string.look_import_summary, result.imported.size, result.failures.size) +
                        "\n\n" + result.failures.joinToString("\n")
                mutable.value = mutation.state.copy(looks = result.looks, lookImportReport = report,
                    message = if (result.imported.isEmpty()) null else text(R.string.look_imported))
                scheduleLookPreview(mutation.preview)
            }
            is LookResult.Renamed -> {
                val mutation = EditorStateReducer.lookRenamed(mutable.value, result.look)
                mutable.value = mutation.state.copy(looks = result.looks)
            }
            is LookResult.Deleted -> {
                val mutation = EditorStateReducer.lookDeleted(mutable.value, result.id)
                mutable.value = mutation.state.copy(looks = result.looks)
                scheduleLookPreview(mutation.preview)
            }
            is LookResult.Failed -> {
                if (result.reload) lookRevision = lookQueue.submit(LookWork.Load)
                if (result.reload) {
                    val error = result.error
                    mutable.value = mutable.value.copy(operation = Operation.NONE,
                        error = text(R.string.look_error) + "\n" + (error.message ?: error.javaClass.simpleName))
                } else {
                    val error = result.error
                    mutable.value = mutable.value.copy(operation = Operation.NONE,
                        error = text(R.string.look_storage_error) + "\n" + (error.message ?: error.javaClass.simpleName))
                }
            }
        }
    }

    private fun scheduleLookPreview(edits: EditSettings?) {
        val photo = mutable.value.photo ?: return
        if (edits == null) return
        currentRevision = queue.submit(Work.Preview(photo, edits, false, gpuMode(), edge = previewLongEdge))
    }

    fun importPhoto(uri: Uri?) {
        if (uri == null) return
        // 渲染/导出进行中不丢弃用户选择，给出明确反馈避免"点了没反应"。
        if (mutable.value.operation != Operation.NONE) {
            mutable.value = mutable.value.copy(message = text(R.string.busy_message))
            return
        }
        val current = mutable.value
        if (current.photo != null && persist(current.photo, current.edits) == EditSaveState.FAILED) {
            mutable.value = current.copy(saveState = EditSaveState.FAILED, error = text(R.string.edit_storage_error))
            return
        }
        // FR-04: the history is session-scoped and never crosses photos.
        history.clear()
        dragActive = false
        mutable.value = mutable.value.copy(operation = Operation.IMPORT, rendering = true, error = null,
            canUndo = false, canRedo = false, showOriginal = false, originalBitmap = null)
        previewResizeJob?.cancel()
        previewResizeJob = null
        previewLongEdge = 1600
        requestedPreviewLongEdge = 1600
        mutable.value = mutable.value.copy(previewResizePending = false)
        currentRevision = queue.submit(Work.Import(uri, gpuMode()))
    }

    fun importLook(uri: Uri?) {
        if (uri != null) importLooks(listOf(uri))
    }

    fun importLooks(uris: List<Uri>) {
        if (uris.isEmpty() || mutable.value.operation != Operation.NONE) return
        mutable.value = mutable.value.copy(operation = Operation.IMPORT_LOOK, error = null, lookImportReport = null)
        lookRevision = lookQueue.submit(LookWork.Import(uris.toList()))
    }

    fun dismissLookImportReport() { mutable.value = mutable.value.copy(lookImportReport = null) }

    fun renameLook(id: String, name: String) {
        if (mutable.value.operation != Operation.NONE) return
        mutable.value = mutable.value.copy(operation = Operation.IMPORT_LOOK, error = null)
        lookRevision = lookQueue.submit(LookWork.Rename(id, name))
    }

    fun deleteLook(id: String) {
        if (mutable.value.operation != Operation.NONE) return
        mutable.value = mutable.value.copy(operation = Operation.IMPORT_LOOK, error = null)
        lookRevision = lookQueue.submit(LookWork.Delete(id))
    }

    fun edit(edits: EditSettings, interactive: Boolean = false) {
        val current = mutable.value
        if (!current.controlsEnabled) return
        val look = current.looks.firstOrNull { it.id == edits.film }
        if (look?.managed == true && !look.available) {
            mutable.value = current.copy(error = text(R.string.look_unavailable))
            return
        }
        // FR-04: a committed step records the pre-change state once. A slider
        // drag records it at its first moving tick and merges the rest; recipe
        // applies, eyedropper samples and crop commits arrive non-interactive.
        if (edits != current.edits) {
            if (interactive) {
                if (!dragActive) history.commit(current.edits)
            } else {
                history.commit(current.edits)
            }
        }
        dragActive = interactive
        renderEdits(edits, interactive)
    }

    private fun renderEdits(edits: EditSettings, interactive: Boolean) {
        val current = mutable.value
        if (!current.controlsEnabled) return
        if (!edits.denoise.enabled && previewResizeJob != null) {
            previewResizeJob?.cancel()
            previewResizeJob = null
            previewLongEdge = requestedPreviewLongEdge
        }
        val saved = if (interactive) current.saveState else persist(current.photo!!, edits)
        mutable.value = withHistory(current.copy(edits = edits, rendering = true, exact = false, error = null, saveState = saved,
            previewResizePending = previewResizeJob != null))
        currentRevision = queue.submit(Work.Preview(current.photo!!, edits, interactive, gpuMode(), edge = previewLongEdge))
    }

    private fun lookUsable(edits: EditSettings, state: EditorState): Boolean {
        val look = state.looks.firstOrNull { it.id == edits.film }
        if (look?.managed == true && !look.available) {
            mutable.value = state.copy(error = text(R.string.look_unavailable))
            return false
        }
        return true
    }

    fun undo() {
        val current = mutable.value
        if (!current.controlsEnabled || !current.canUndo) return
        val previous = history.peekUndo() ?: return
        if (!lookUsable(previous, current)) return
        dragActive = false
        history.undo(current.edits)
        renderEdits(previous, false)
    }

    fun redo() {
        val current = mutable.value
        if (!current.controlsEnabled || !current.canRedo) return
        val next = history.peekRedo() ?: return
        if (!lookUsable(next, current)) return
        dragActive = false
        history.redo(current.edits)
        renderEdits(next, false)
    }

    private fun withHistory(state: EditorState) = state.copy(canUndo = history.canUndo, canRedo = history.canRedo)

    /** FR-03: sample a neutral-gray point (normalized preview coordinates). */
    fun pickWhiteBalance(nx: Float, ny: Float) {
        val current = mutable.value
        if (!current.controlsEnabled || current.preview == null || current.rendering) return
        val photo = current.photo ?: return
        mutable.value = current.copy(rendering = true, error = null)
        currentRevision = queue.submit(Work.PickWb(photo, nx.coerceIn(0f, 1f), ny.coerceIn(0f, 1f)))
    }

    /** FR-05: hold-to-compare previews the develop baseline of the current
     * exposure mode with every user edit zeroed; it never touches the edits
     * or the undo history. */
    fun setOriginalVisible(visible: Boolean) {
        val current = mutable.value
        if (!visible) {
            if (current.showOriginal) mutable.value = current.copy(showOriginal = false)
            return
        }
        val photo = current.photo ?: return
        if (current.preview == null || current.operation != Operation.NONE) return
        val mode = current.edits.exposureMode
        // The neutral preview is rendered from default settings, so it already
        // is the baseline of the default exposure mode.
        val defaultMode = EditSettings().exposureMode
        val baseline = if (mode == defaultMode) current.preview?.neutral
            else baselineCache?.takeIf { it.first == photo.file && it.second == mode }?.third
        mutable.value = current.copy(showOriginal = true, originalBitmap = baseline)
        if (baseline != null || mode == defaultMode) return
        val request = photo.file to mode
        if (baselineJob?.isActive == true) {
            if (baselineRequest == request) return
            baselineJob?.cancel()
        }
        baselineRequest = request
        baselineJob = viewModelScope.launch {
            val rendered = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                runCatching {
                    synchronized(storage) {
                        val native = engine()
                        native.setGpuMode(gpuMode())
                        native.preview(photo.file, null, EditSettings(exposureMode = mode), 1600, false).bitmap()
                    }
                }.getOrNull()
            } ?: return@launch
            baselineCache = Triple(photo.file, mode, rendered)
            if (disposed) return@launch
            val state = mutable.value
            if (state.photo?.file == photo.file && state.edits.exposureMode == mode)
                mutable.value = state.copy(originalBitmap = rendered)
        }
    }

    fun updatePreviewSize(edge: Int) {
        val current = mutable.value
        if (disposed || current.photo == null || edge !in 1..1600 ||
            (edge == requestedPreviewLongEdge && (edge == previewLongEdge || previewResizeJob != null))) return
        requestedPreviewLongEdge = edge
        if (!current.controlsEnabled) return
        previewResizeJob?.cancel()
        previewResizeJob = null
        mutable.value = current.copy(previewResizePending = false)
        if (edge == previewLongEdge) return
        if (!current.edits.denoise.enabled) {
            previewLongEdge = edge
            edit(mutable.value.edits)
            return
        }
        mutable.value = mutable.value.copy(previewResizePending = true)
        previewResizeJob = viewModelScope.launch {
            delay(200)
            previewResizeJob = null
            mutable.value = mutable.value.copy(previewResizePending = false)
            if (!mutable.value.controlsEnabled) return@launch
            previewLongEdge = requestedPreviewLongEdge
            edit(mutable.value.edits)
        }
    }

    fun retry() { edit(mutable.value.edits) }
    fun autoExposure() {
        val current = mutable.value
        if (!current.controlsEnabled || current.preview == null || current.rendering) return
        mutable.value = current.copy(rendering = true, exact = false, error = null)
        currentRevision = queue.submit(Work.AutoExposure(current.photo!!, current.edits))
    }
    fun retrySave() {
        val photo = mutable.value.photo
        if (photo != null) mutable.value = mutable.value.copy(saveState = persist(photo, mutable.value.edits))
        else try {
            editStore = EditStore(File(getApplication<Application>().filesDir, "edits"))
            mutable.value = mutable.value.copy(saveState = EditSaveState.NONE, error = null)
        } catch (_: Exception) { mutable.value = mutable.value.copy(saveState = EditSaveState.FAILED) }
        fadeSaveNotice()
    }
    fun reset() { edit(mutable.value.edits.reset()) }

    private var saveNotice: kotlinx.coroutines.Job? = null
    private fun fadeSaveNotice() {
        saveNotice?.cancel()
        if (mutable.value.saveState !in setOf(EditSaveState.SAVED, EditSaveState.RESTORED)) return
        saveNotice = viewModelScope.launch {
            kotlinx.coroutines.delay(3000)
            if (mutable.value.saveState in setOf(EditSaveState.SAVED, EditSaveState.RESTORED)) mutable.value = mutable.value.copy(saveState = EditSaveState.NONE)
        }
    }

    private fun gpuMode() = if (mutable.value.gpuEnabled) NativeProcessor.AUTO else NativeProcessor.CPU

    private fun persist(photo: ImportedPhoto, edits: EditSettings): EditSaveState = try {
        if (editStore == null) editStore = EditStore(File(getApplication<Application>().filesDir, "edits"))
        editStore!!.save(photo.identity, edits)
        EditSaveState.SAVED
    } catch (_: Throwable) { EditSaveState.FAILED }
    fun setGpuEnabled(enabled: Boolean) {
        if (mutable.value.operation != Operation.NONE) return
        mutable.value = mutable.value.copy(gpuEnabled = enabled)
        if (mutable.value.photo != null) edit(mutable.value.edits)
    }

    fun beginExport(): Boolean {
        if (!mutable.value.canExport) return false
        mutable.value = mutable.value.copy(operation = Operation.PICK_EXPORT, error = null)
        return true
    }
    fun cancelExport() {
        if (mutable.value.operation == Operation.PICK_EXPORT) mutable.value = mutable.value.copy(operation = Operation.NONE)
        updatePreviewSize(requestedPreviewLongEdge)
    }
    fun export(destination: Uri?, format: ExportFormat, longEdge: Int? = null) {
        val current = mutable.value
        if (current.operation != Operation.PICK_EXPORT || current.photo == null) return
        if (destination == null && !format.allowsGallery) {
            mutable.value = current.copy(operation = Operation.NONE, error = text(R.string.dng_file_only))
            return
        }
        if (!ExportSize.isValid(longEdge)) {
            mutable.value = current.copy(operation = Operation.NONE, error = text(R.string.output_size_error))
            updatePreviewSize(requestedPreviewLongEdge)
            return
        }
        mutable.value = current.copy(operation = Operation.EXPORT)
        currentRevision = queue.submit(Work.Export(current.photo, current.edits, destination, format, longEdge, gpuMode()))
    }
    fun dismissMessage() { mutable.value = mutable.value.copy(message = null) }

    fun beginBatch() {
        val current = mutable.value
        if (!current.canExport || mutableBatch.value != null) return
        val photo = current.photo ?: return
        mutableBatch.value = BatchExportModel(getApplication(), storage,
            BatchSourceSnapshot(photo.identity, photo.file, current.edits, photo.name))
    }

    fun resumeBatch(job: BatchJob) {
        if (mutableBatch.value != null) return
        mutableBatch.value = BatchExportModel(getApplication(), storage, job.source, job)
    }

    fun closeBatch() {
        val model = mutableBatch.value ?: return
        if (model.state.value.phase in setOf(BatchPhase.RUNNING, BatchPhase.PREPARING)) return
        model.discardDraftOrCompleted()
        model.close()
        mutableBatch.value = null
        refreshPendingBatch()
    }

    private fun refreshPendingBatch() {
        viewModelScope.launch {
            try {
                mutablePendingBatch.value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val journal = BatchJournal(File(getApplication<Application>().filesDir, "RawLab/BatchJobs"))
                    journal.pending().mapNotNull(journal::load)
                }
            } catch (error: Exception) { mutable.value = mutable.value.copy(error = getApplication<Application>().getString(R.string.batch_refresh_error, error.message)) }
        }
    }

    fun onBackground() {
        mutableBatch.value?.cancel()
        mutable.value.photo?.let { photo ->
            if (persist(photo, mutable.value.edits) == EditSaveState.FAILED) mutable.value = mutable.value.copy(saveState = EditSaveState.FAILED)
        }
    }
    private fun text(id: Int) = getApplication<Application>().getString(id)
    private fun failure(error: Throwable): String = when {
        error is OutOfMemoryError -> text(R.string.memory_error)
        // 导入失败的文案在 PhotoStorage 中已按原因分类，直接展示并留排查日志。
        error is ImportException -> {
            android.util.Log.w("RawLab", "Import failed: ${error.message}", error)
            error.message ?: text(R.string.process_error)
        }
        else -> text(R.string.process_error) + "\n" + (error.message ?: error.javaClass.simpleName)
    }

    override fun onCleared() {
        disposed = true
        previewResizeJob?.cancel()
        mutableBatch.value?.close()
        lookQueue.close()
        queue.close()
    }

    private fun releaseStorage() {
        synchronized(this) {
            releases += 1
            if (releases == 2) storage.close()
        }
    }
}

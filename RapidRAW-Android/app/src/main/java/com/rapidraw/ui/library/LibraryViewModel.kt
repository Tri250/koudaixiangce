package com.rapidraw.ui.library

import android.app.Application
import android.content.ContentUris
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.rapidraw.core.CrashHandler
import com.rapidraw.core.DamProjectManager
import com.rapidraw.core.ThumbnailDiskCache
import com.rapidraw.ai.NaturalLanguageSearcher
import com.rapidraw.ai.SemanticTag
import com.rapidraw.data.model.ColorLabel
import com.rapidraw.data.model.ImageFile
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.math.max

enum class SortOrder {
    DATE,
    RATING,
    NAME,
}

enum class FormatFilter { ALL, RAW_ONLY, JPEG_ONLY }

enum class SceneFilter { ALL, PORTRAIT, LANDSCAPE, NIGHT }

class LibraryViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {

    private val contentResolver = application.contentResolver

    // v1.5.5 hotfix: 使用 lazy 初始化，避免构造器中磁盘操作失败（如 cacheDir 不可写）
    // 导致 ViewModel 创建失败从而整个页面闪退。
    private val thumbnailCache by lazy { ThumbnailDiskCache(application.cacheDir) }

    // DAM 项目管理器（提供图库统计和分面数据）
    val damProjectManager = DamProjectManager(application)
    val damLibraryStats: StateFlow<com.rapidraw.data.model.DamLibraryStats> = damProjectManager.libraryStats
    val damFacetData: StateFlow<com.rapidraw.data.model.DamFacetData> = damProjectManager.facetData

    private val _showDamOverview = MutableStateFlow(false)
    val showDamOverview: StateFlow<Boolean> = _showDamOverview.asStateFlow()

    fun toggleDamOverview() {
        _showDamOverview.value = !_showDamOverview.value
    }

    private val _images = MutableStateFlow<List<ImageFile>>(emptyList())
    val images: StateFlow<List<ImageFile>> = _images.asStateFlow()

    private val _folders = MutableStateFlow<List<String>>(emptyList())
    val folders: StateFlow<List<String>> = _folders.asStateFlow()

    private val _selectedFolder = MutableStateFlow<String?>(null)
    val selectedFolder: StateFlow<String?> = _selectedFolder.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.DATE)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val _filterRaw = MutableStateFlow(false)
    val filterRaw: StateFlow<Boolean> = _filterRaw.asStateFlow()

    private val _formatFilter = MutableStateFlow(FormatFilter.ALL)
    val formatFilter: StateFlow<FormatFilter> = _formatFilter.asStateFlow()

    private val _sceneFilter = MutableStateFlow(SceneFilter.ALL)
    val sceneFilter: StateFlow<SceneFilter> = _sceneFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // v1.10.6: 添加错误状态，让 UI 能感知并展示错误信息
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun clearError() { _error.value = null }

    private val _thumbnails = MutableStateFlow<Map<String, Bitmap>>(emptyMap())
    val thumbnails: StateFlow<Map<String, Bitmap>> = _thumbnails.asStateFlow()

    private var thumbnailJob: Job? = null

    private val _isBatchMode = MutableStateFlow(false)
    val isBatchMode: StateFlow<Boolean> = _isBatchMode.asStateFlow()

    private val _selectedImagePaths = MutableStateFlow<Set<String>>(emptySet())
    val selectedImagePaths: StateFlow<Set<String>> = _selectedImagePaths.asStateFlow()

    // Alias for backward compatibility
    val selectedImages: StateFlow<Set<String>> = _selectedImagePaths.asStateFlow()

    // Multi-select mode (additional layer for long-press entry)
    private val _isMultiSelectMode = MutableStateFlow(false)
    val isMultiSelectMode: StateFlow<Boolean> = _isMultiSelectMode.asStateFlow()

    fun enterMultiSelectMode() {
        _isMultiSelectMode.value = true
        _isBatchMode.value = true
        _hasCopiedAdjustments.value = com.rapidraw.core.AdjustmentClipboard.hasData()
    }

    fun exitMultiSelectMode() {
        _isMultiSelectMode.value = false
        _isBatchMode.value = false
        _selectedImagePaths.value = emptySet()
    }

    private val _hasCopiedAdjustments = MutableStateFlow(com.rapidraw.core.AdjustmentClipboard.hasData())
    val hasCopiedAdjustments: StateFlow<Boolean> = _hasCopiedAdjustments.asStateFlow()

    private val _batchProgress = MutableStateFlow<com.rapidraw.core.BatchProcessor.BatchProgress?>(null)
    val batchProgress: StateFlow<com.rapidraw.core.BatchProcessor.BatchProgress?> = _batchProgress.asStateFlow()

    private val SUPPORTED_EXTENSIONS = setOf(
        "jpg", "jpeg", "png", "tiff", "tif",
        "dng", "cr2", "cr3", "nef", "arw", "raf", "orf", "rw2", "pef", "srw",
    )

    /**
     * v1.5.3: 全局协程异常 handler，避免 viewModelScope.launch 抛出未捕获异常时直接闪退。
     * 与主线程 UncaughtExceptionHandler 互不冲突。
     */
    private val coroutineExceptionHandler: CoroutineExceptionHandler =
        CrashHandler.coroutineExceptionHandler(getApplication())

    init {
        // v1.10.5: 从 SavedStateHandle 恢复进程死亡前的状态
        restoreSavedState()
        // v1.5.5 hotfix: init 块中的 try-catch 只能捕获协程启动异常，
        // 无法捕获协程体内部的异常。协程体已由 coroutineExceptionHandler 保护。
        // 此处额外保护 loadImages 调用本身可能抛出的同步异常。
        try {
            loadImages(_selectedFolder.value)
        } catch (e: Exception) {
            // 防止 MediaStore 查询异常导致 ViewModel 构造失败 → 页面闪退
            Log.w(TAG, "init loadImages failed", e)
            _isLoading.value = false
        }
    }

    /**
     * v1.10.5: 从 SavedStateHandle 恢复进程死亡前的状态。
     */
    private fun restoreSavedState() {
        try {
            savedStateHandle.get<String>("selected_folder")?.let { _selectedFolder.value = it }
            savedStateHandle.get<String>("sort_order")?.let {
                _sortOrder.value = try { SortOrder.valueOf(it) } catch (_: Exception) { SortOrder.DATE }
            }
            savedStateHandle.get<String>("format_filter")?.let {
                _formatFilter.value = try { FormatFilter.valueOf(it) } catch (_: Exception) { FormatFilter.ALL }
            }
            savedStateHandle.get<String>("search_query")?.let { _searchQuery.value = it }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to restore SavedStateHandle state", e)
        }
    }

    /**
     * v1.10.5: 保存当前状态到 SavedStateHandle（进程死亡恢复）。
     */
    private fun saveStateToHandle() {
        try {
            savedStateHandle["selected_folder"] = _selectedFolder.value
            savedStateHandle["sort_order"] = _sortOrder.value.name
            savedStateHandle["format_filter"] = _formatFilter.value.name
            savedStateHandle["search_query"] = _searchQuery.value
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save state to SavedStateHandle", e)
        }
    }

    fun loadImages(folder: String?) {
        _selectedFolder.value = folder
        _isLoading.value = true
        _error.value = null
        _selectedImagePaths.value = emptySet()
        // v1.10.5: 保存状态到 SavedStateHandle
        saveStateToHandle()

        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            try {
                val imageList = queryImages(folder)
                val folderSet = mutableSetOf<String>()

                for (image in imageList) {
                    val parentFolder = image.folderPath
                    if (parentFolder.isNotEmpty()) {
                        folderSet.add(parentFolder)
                    }
                }

                _folders.value = folderSet.toList().sorted()
                applyFilters(imageList)
            } catch (e: Exception) {
                // v1.10.6: 确保错误状态传播到 UI
                Log.e(TAG, "loadImages failed", e)
                _error.value = e.localizedMessage ?: "图片加载失败"
            } finally {
                // v1.10.6: 确保 isLoading 在任何退出路径上都被重置
                _isLoading.value = false
            }
        }
    }

    private suspend fun queryImages(folder: String?): List<ImageFile> =
        withContext(Dispatchers.IO) {
            val images = mutableListOf<ImageFile>()

            try {
                val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }

                val projection = arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DATA,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.DATE_MODIFIED,
                    MediaStore.Images.Media.SIZE,
                    MediaStore.Images.Media.WIDTH,
                    MediaStore.Images.Media.HEIGHT,
                )

                val selectionBuilder = StringBuilder()
                val selectionArgs = mutableListOf<String>()

                val extensionConditions = SUPPORTED_EXTENSIONS.map { ext ->
                    "${MediaStore.Images.Media.DISPLAY_NAME} LIKE '%.$ext'"
                } + SUPPORTED_EXTENSIONS.map { ext ->
                    "${MediaStore.Images.Media.DISPLAY_NAME} LIKE '%.${ext.uppercase()}'"
                }
                selectionBuilder.append("(${extensionConditions.joinToString(" OR ")})")

                if (folder != null) {
                    selectionBuilder.append(" AND ${MediaStore.Images.Media.DATA} LIKE ?")
                    selectionArgs.add("$folder/%")
                }

                val sortOrderClause = when (_sortOrder.value) {
                    SortOrder.DATE -> "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
                    SortOrder.RATING -> "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
                    SortOrder.NAME -> "${MediaStore.Images.Media.DISPLAY_NAME} ASC"
                }

                val cursor: Cursor? = contentResolver.query(
                    collection,
                    projection,
                    selectionBuilder.toString(),
                    selectionArgs.toTypedArray(),
                    sortOrderClause,
                )

                cursor?.use {
                    val idColumn = it.getColumnIndex(MediaStore.Images.Media._ID)
                    val dataColumn = it.getColumnIndex(MediaStore.Images.Media.DATA)
                    val nameColumn = it.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                    val dateColumn = it.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED)
                    val sizeColumn = it.getColumnIndex(MediaStore.Images.Media.SIZE)
                    val widthColumn = it.getColumnIndex(MediaStore.Images.Media.WIDTH)
                    val heightColumn = it.getColumnIndex(MediaStore.Images.Media.HEIGHT)

                    // 必要列缺失时直接跳过，避免 getColumnIndexOrThrow 崩溃
                    if (idColumn < 0 || dataColumn < 0 || nameColumn < 0) {
                        Log.w(TAG, "Required MediaStore columns missing")
                        return@withContext images
                    }

                    while (it.moveToNext()) {
                        val path = it.getString(dataColumn) ?: continue
                        val fileName = it.getString(nameColumn) ?: continue
                        val extension = fileName.substringAfterLast('.', "").lowercase()

                        if (extension !in SUPPORTED_EXTENSIONS) continue

                        val folderPath = path.substringBeforeLast('/', "")
                        val isRaw = extension in ImageFile.RAW_EXTENSIONS

                        images.add(
                            ImageFile(
                                path = path,
                                fileName = fileName,
                                folderPath = folderPath,
                                isRaw = isRaw,
                                width = if (widthColumn >= 0) it.getInt(widthColumn) else 0,
                                height = if (heightColumn >= 0) it.getInt(heightColumn) else 0,
                                fileSize = if (sizeColumn >= 0) it.getLong(sizeColumn) else 0L,
                                dateModified = if (dateColumn >= 0) it.getLong(dateColumn) * 1000L else 0L,
                                rating = 0,
                            )
                        )
                    }
                } ?: Log.w(TAG, "MediaStore cursor returned null")
            } catch (e: SecurityException) {
                Log.w(TAG, "MediaStore query SecurityException", e)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "MediaStore query IllegalArgumentException", e)
            } catch (e: Exception) {
                Log.w(TAG, "MediaStore query failed", e)
            }

            images
        }

    fun loadThumbnails() {
        // 取消上一次未完成的缩略图加载，避免结果覆盖与资源浪费
        thumbnailJob?.cancel()
        thumbnailJob = viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            val currentImages = _images.value.take(128)
            // 限制并发数为 4，防止同时解码过多 Bitmap 导致内存峰值过高
            val semaphore = Semaphore(4)

            val results = currentImages.map { image ->
                async {
                    ensureActive()
                    try {
                        semaphore.withPermit {
                            ensureActive()
                            loadThumbnailForImage(image)?.let { image.path to it }
                        }
                    } catch (_: Exception) {
                        null
                    }
                }
            }.awaitAll().filterNotNull()

            ensureActive()
            // v1.5.5 hotfix: 替换缩略图 Map 前主动回收被丢弃的位图，
            // 否则新图加载时旧图仍驻留内存，最终触发 OOM。
            val previous = _thumbnails.value
            _thumbnails.value = results.toMap()
            val keptKeys = _thumbnails.value.keys
            for ((oldKey, oldBmp) in previous) {
                if (oldKey !in keptKeys && !oldBmp.isRecycled) {
                    runCatching { oldBmp.recycle() }.onFailure { Log.w(TAG, "recycle failed", it) }
                }
            }
        }
    }

    private fun loadThumbnailForImage(image: ImageFile): Bitmap? {
        // 先检查磁盘/内存二级缓存，避免重复解码
        val lastModified = image.dateModified.coerceAtLeast(0L)
        // v1.5.5 hotfix: thumbnailCache 是 lazy 初始化，首次访问可能因磁盘不可写抛异常
        val cached = try {
            thumbnailCache.get(image.path, lastModified)
        } catch (_: Exception) {
            null
        }
        cached?.let { return it }

        val bitmap = when {
            image.path.startsWith("content://") -> {
                val parsedUri = Uri.parse(image.path)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        // Android 10+ 可直接对任意 content URI 加载缩略图
                        try {
                            contentResolver.loadThumbnail(parsedUri, android.util.Size(256, 256), null)
                        } catch (e: OutOfMemoryError) {
                            // 2026 hotfix: 缩略图 OOM 降级为更小尺寸
                            Log.w(TAG, "OOM loading thumbnail at 256, falling back to 128", e)
                            contentResolver.loadThumbnail(parsedUri, android.util.Size(128, 128), null)
                        }
                    } else {
                        // 低版本仅处理 MediaStore URI，兼容 document id 形式 image:123
                        val mediaStoreId = parsedUri.lastPathSegment
                            ?.substringAfterLast(":", "")
                            ?.toLongOrNull()
                            ?: return null
                        @Suppress("DEPRECATION")
                        MediaStore.Images.Thumbnails.getThumbnail(
                            contentResolver,
                            mediaStoreId,
                            MediaStore.Images.Thumbnails.MINI_KIND,
                            null,
                        )
                    }
                } catch (e: OutOfMemoryError) {
                    Log.w(TAG, "OOM loading thumbnail for ${image.fileName}", e)
                    null
                } catch (_: Exception) {
                    null
                }
            }
            image.path.startsWith("file://") || image.path.startsWith("/") -> {
                val filePath = image.path.removePrefix("file://")
                val file = java.io.File(filePath)
                if (!file.exists()) return null

                // 先获取图片尺寸，再计算 inSampleSize，复用同一个 Options 对象
                val options = android.graphics.BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                android.graphics.BitmapFactory.decodeFile(filePath, options)
                if (options.outWidth <= 0 || options.outHeight <= 0) return null

                try {
                    android.graphics.BitmapFactory.decodeFile(filePath, android.graphics.BitmapFactory.Options().apply {
                        inSampleSize = max(options.outWidth / 256, options.outHeight / 256).coerceAtLeast(1)
                        inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
                    })
                } catch (e: OutOfMemoryError) {
                    Log.w(TAG, "OOM decoding thumbnail for ${image.fileName}", e)
                    null
                }
            }
            else -> null
        }

        if (bitmap != null) {
            try {
                thumbnailCache.put(image.path, lastModified, bitmap)
            } catch (_: Exception) {
                // 磁盘缓存写入失败不影响内存中的缩略图使用
            }
        }
        return bitmap
    }

    /**
     * 导入用户通过 Photo Picker 选择的图片。
     * 获取持久化读取权限并刷新图库（MediaStore 会扫描到新图片）。
     */
    fun importImages(uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            uris.forEach { uri ->
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                } catch (_: Exception) {
                    // 某些 provider 不支持持久化权限，忽略
                }
            }
            // 刷新图片列表，新导入的图片会被 MediaStore 扫描到
            loadImages(_selectedFolder.value)
        }
    }

    fun searchImages(query: String) {
        _searchQuery.value = query
        _isLoading.value = true
        _error.value = null
        saveStateToHandle()
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            try {
                val allImages = queryImages(_selectedFolder.value)
                applyFilters(allImages)
            } catch (e: Exception) {
                Log.e(TAG, "searchImages failed", e)
                _error.value = e.localizedMessage ?: "搜索失败"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun toggleSortOrder() {
        _sortOrder.value = when (_sortOrder.value) {
            SortOrder.DATE -> SortOrder.RATING
            SortOrder.RATING -> SortOrder.NAME
            SortOrder.NAME -> SortOrder.DATE
        }
        saveStateToHandle()
        _isLoading.value = true
        _error.value = null
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            try {
                val allImages = queryImages(_selectedFolder.value)
                applyFilters(allImages)
            } catch (e: Exception) {
                Log.e(TAG, "toggleSortOrder failed", e)
                _error.value = e.localizedMessage ?: "排序失败"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun toggleFilterRaw() {
        _filterRaw.value = !_filterRaw.value
        _isLoading.value = true
        _error.value = null
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            try {
                val allImages = queryImages(_selectedFolder.value)
                applyFilters(allImages)
            } catch (e: Exception) {
                Log.e(TAG, "toggleFilterRaw failed", e)
                _error.value = e.localizedMessage ?: "筛选失败"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setFormatFilter(filter: FormatFilter) {
        _formatFilter.value = filter
        // 同步旧的 filterRaw 状态以保持兼容
        _filterRaw.value = filter == FormatFilter.RAW_ONLY
        saveStateToHandle()
        _isLoading.value = true
        _error.value = null
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            try {
                val allImages = queryImages(_selectedFolder.value)
                applyFilters(allImages)
            } catch (e: Exception) {
                Log.e(TAG, "setFormatFilter failed", e)
                _error.value = e.localizedMessage ?: "格式筛选失败"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setSceneFilter(filter: SceneFilter) {
        _sceneFilter.value = filter
        _isLoading.value = true
        _error.value = null
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            try {
                val allImages = queryImages(_selectedFolder.value)
                applyFilters(allImages)
            } catch (e: Exception) {
                Log.e(TAG, "setSceneFilter failed", e)
                _error.value = e.localizedMessage ?: "场景筛选失败"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun enterBatchMode() {
        _isBatchMode.value = true
        _hasCopiedAdjustments.value = com.rapidraw.core.AdjustmentClipboard.hasData()
    }
    fun exitBatchMode() {
        _isBatchMode.value = false
        _selectedImagePaths.value = emptySet()
    }
    fun toggleImageSelection(path: String) {
        val current = _selectedImagePaths.value.toMutableSet()
        if (current.contains(path)) current.remove(path) else current.add(path)
        _selectedImagePaths.value = current
    }
    fun selectAll() { _selectedImagePaths.value = _images.value.map { it.path }.toSet() }
    fun clearSelection() { _selectedImagePaths.value = emptySet() }

    fun hasCopiedAdjustments(): Boolean = com.rapidraw.core.AdjustmentClipboard.hasData()

    fun pasteAdjustmentsToSelected(
        batchProcessor: com.rapidraw.core.BatchProcessor,
        exportSettings: com.rapidraw.data.model.ExportSettings,
    ) {
        val adjustments = com.rapidraw.core.AdjustmentClipboard.adjustments ?: return
        val selected = _selectedImagePaths.value.toList()
        if (selected.isEmpty()) return

        val uris = selected.map { android.net.Uri.parse(it) }
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            batchProcessor.batchApplyAdjustments(uris, adjustments, exportSettings)
                .collect { progress ->
                    _batchProgress.value = progress
                    if (progress.isComplete) {
                        _batchProgress.value = null
                        exitBatchMode()
                        loadImages(_selectedFolder.value)
                    }
                }
        }
    }

    fun batchApplyFilm(
        batchProcessor: com.rapidraw.core.BatchProcessor,
        film: com.rapidraw.data.model.FilmSimulation,
        exportSettings: com.rapidraw.data.model.ExportSettings,
    ) {
        val selected = _selectedImagePaths.value.toList()
        if (selected.isEmpty()) return

        val adjustments = com.rapidraw.data.model.Adjustments().withFilmSimulation(film)
        val uris = selected.map { android.net.Uri.parse(it) }

        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            batchProcessor.batchApplyFilm(uris, adjustments, exportSettings)
                .collect { progress ->
                    _batchProgress.value = progress
                    if (progress.isComplete) {
                        _batchProgress.value = null
                        exitBatchMode()
                        loadImages(_selectedFolder.value)
                    }
                }
        }
    }

    fun batchExport(
        batchProcessor: com.rapidraw.core.BatchProcessor,
        exportSettings: com.rapidraw.data.model.ExportSettings,
    ) {
        val selected = _selectedImagePaths.value.toList()
        if (selected.isEmpty()) return

        val uris = selected.map { android.net.Uri.parse(it) }

        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            batchProcessor.batchExport(uris, exportSettings)
                .collect { progress ->
                    _batchProgress.value = progress
                    if (progress.isComplete) {
                        _batchProgress.value = null
                        exitBatchMode()
                        loadImages(_selectedFolder.value)
                    }
                }
        }
    }

    fun updateRating(path: String, rating: Int) {
        _images.update { list ->
            list.map { image ->
                if (image.path == path) image.copy(rating = rating.coerceIn(0, 5)) else image
            }
        }
    }

    fun updateColorLabel(path: String, label: ColorLabel) {
        _images.update { list ->
            list.map { image ->
                if (image.path == path) image.copy(colorLabel = label) else image
            }
        }
    }

    // L08 修复：创建虚拟副本时同时复制原片的 .rrdata 到副本路径，确保非破坏性分支独立
    fun createVirtualCopy(image: ImageFile) {
        val copyFileName = image.fileName.substringBeforeLast('.') + "_copy"
        val virtualCopy = image.copy(
            path = image.path + ".virtual_copy",
            fileName = copyFileName,
            virtualCopyOf = image.path,
            rating = 0,
            colorLabel = ColorLabel.NONE,
            adjustments = image.adjustments,
        )
        // 复制原片的 .rrdata 到副本路径
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val sidecarManager = SidecarManager(context)
                val originalSidecar = sidecarManager.loadSidecar(image.path)
                if (originalSidecar != null) {
                    sidecarManager.saveSidecar(
                        virtualCopy.path,
                        originalSidecar.adjustments,
                        originalSidecar.filmSimulationId,
                        originalSidecar.editHistoryEntries
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to copy sidecar for virtual copy", e)
            }
        }
        _images.update { list -> list + virtualCopy }
    }

    private val nlSearcher by lazy { NaturalLanguageSearcher() }

    /** 图片路径 → AI 语义标签缓存 */
    private val semanticTagCache = mutableMapOf<String, List<SemanticTag>>()

    private fun applyFilters(allImages: List<ImageFile>) {
        var filtered = allImages

        // 格式筛选（整合 FormatFilter，兼容旧的 filterRaw）
        when (_formatFilter.value) {
            FormatFilter.ALL -> { /* 不筛选 */ }
            FormatFilter.RAW_ONLY -> filtered = filtered.filter { it.isRaw }
            FormatFilter.JPEG_ONLY -> filtered = filtered.filter { !it.isRaw }
        }
        // 旧的 filterRaw 开关兼容（如果 formatFilter 为 ALL 但 filterRaw 为 true，仍然过滤）
        if (_formatFilter.value == FormatFilter.ALL && _filterRaw.value) {
            filtered = filtered.filter { it.isRaw }
        }

        // 场景筛选（基于 AI 语义标签）
        if (_sceneFilter.value != SceneFilter.ALL) {
            val targetTag = when (_sceneFilter.value) {
                SceneFilter.PORTRAIT -> "人像"
                SceneFilter.LANDSCAPE -> "山野"
                SceneFilter.NIGHT -> "夜晚"
                SceneFilter.ALL -> null
            }
            if (targetTag != null) {
                filtered = filtered.filter { image ->
                    // 匹配 image 的 tags 列表或语义标签缓存
                    image.tags.any { it.contains(targetTag) } ||
                    semanticTagCache[image.path]?.any { tag ->
                        tag.value.contains(targetTag)
                    } ?: false
                }
            }
        }

        val query = _searchQuery.value.trim()
        if (query.isNotEmpty()) {
            // 使用自然语言搜索（AlcedoStudio 对标）
            val searchQuery = nlSearcher.parseQuery(query)
            if (searchQuery.sceneTags.isNotEmpty() || searchQuery.subjectTags.isNotEmpty() ||
                searchQuery.moodTags.isNotEmpty() || searchQuery.styleTags.isNotEmpty() ||
                searchQuery.colorToneTags.isNotEmpty() || searchQuery.timeOfDayTags.isNotEmpty()
            ) {
                // 语义搜索：匹配文件名或已有语义标签
                val lowerQuery = query.lowercase()
                filtered = filtered.filter { image ->
                    // 文件名匹配
                    image.fileName.lowercase().contains(lowerQuery) ||
                    // 标签匹配
                    image.tags.any { tag -> searchQuery.sceneTags.contains(tag) || searchQuery.subjectTags.contains(tag) } ||
                    // 语义标签缓存匹配
                    semanticTagCache[image.path]?.let { tags ->
                        nlSearcher.match(searchQuery, tags) > 0.2f
                    } ?: false
                }
            } else {
                // 普通文本搜索
                val lowerQuery = query.lowercase()
                filtered = filtered.filter { it.fileName.lowercase().contains(lowerQuery) }
            }
        }

        filtered = when (_sortOrder.value) {
            SortOrder.DATE -> filtered.sortedByDescending { it.dateModified }
            SortOrder.RATING -> filtered.sortedByDescending { it.rating }
            SortOrder.NAME -> filtered.sortedBy { it.fileName.lowercase() }
        }

        _images.value = filtered
    }

    /**
     * 删除选中的图片（真实文件 + Sidecar 清理）
     */
    fun deleteSelected() {
        val selected = _selectedImagePaths.value.toList()
        if (selected.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            val sidecarManager = com.rapidraw.core.SidecarManager(getApplication())
            for (path in selected) {
                try {
                    val uri = android.net.Uri.parse(path)
                    when (uri.scheme) {
                        "file" -> {
                            val filePath = uri.path ?: continue
                            val file = java.io.File(filePath)
                            if (file.exists()) {
                                // 清理 Sidecar
                                sidecarManager.deleteSidecar(path)
                                val deleted = file.delete()
                                if (deleted) {
                                    // 2026 hotfix: 用 MediaScannerConnection 替代 deprecated 的
                                    // ACTION_MEDIA_SCANNER_SCAN_FILE 广播（Android 10+ 不可用）
                                    android.media.MediaScannerConnection.scanFile(
                                        getApplication(),
                                        arrayOf(file.absolutePath),
                                        arrayOf("image/*"),
                                        null,
                                    )
                                }
                            }
                        }
                        "content" -> {
                            // 通过 ContentResolver 删除
                            try {
                                getApplication<Application>().contentResolver.delete(uri, null, null)
                            } catch (_: Exception) { /* 忽略权限不足 */ }
                            sidecarManager.deleteSidecar(path)
                        }
                    }
                } catch (_: Exception) { /* 继续删除下一张 */ }
            }

            withContext(Dispatchers.Main) {
                exitBatchMode()
                loadImages(_selectedFolder.value)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        thumbnailJob?.cancel()
        // 回收已加载的缩略图 Bitmap，避免 ViewModel 销毁后泄漏
        val currentThumbnails = _thumbnails.value
        _thumbnails.value = emptyMap()
        currentThumbnails.values.forEach { bitmap ->
            if (!bitmap.isRecycled) bitmap.recycle()
        }
        // v1.10.6: 关闭缩略图磁盘缓存，取消未完成的写入任务并释放线程池。
        thumbnailCache.shutdown()
    }

    companion object {
        private const val TAG = "LibraryViewModel"
    }
}

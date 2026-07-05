package com.rapidraw.ui.dam

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rapidraw.core.DamProjectManager
import com.rapidraw.core.ImageProcessor
import com.rapidraw.core.SafePreferences
import com.rapidraw.data.model.Adjustments
import com.rapidraw.data.model.DamProjectFile
import com.rapidraw.data.model.ExportSettings
import com.rapidraw.data.model.ImageFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

data class DamProjectDetailState(
    val project: DamProjectFile? = null,
    val isLoading: Boolean = false,
    val images: List<ImageFile> = emptyList(),
    val errorMessage: String? = null,
)

data class ShareState(
    val isSharing: Boolean = false,
    val shareUri: Uri? = null,
    val error: String? = null,
)

data class ExportProgress(
    val isRunning: Boolean = false,
    val completed: Int = 0,
    val total: Int = 0,
    val failed: Int = 0,
    val currentFile: String = "",
    val resultUris: List<Uri> = emptyList(),
    val isCancelled: Boolean = false,
    val isCompleted: Boolean = false,
    val error: String? = null,
)

class DamProjectViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "DamProjectViewModel"
    }

    class Factory(
        private val projectId: String,
        private val context: Context,
    ) : ViewModelProvider.NewInstanceFactory() {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            return DamProjectViewModel(context.applicationContext as Application) as T
        }
    }

    private val _state = MutableStateFlow(DamProjectDetailState())
    val state: StateFlow<DamProjectDetailState> = _state.asStateFlow()

    val damProjectManager: DamProjectManager = DamProjectManager(application)

    val isLoading: StateFlow<Boolean> = damProjectManager.isLoading

    val currentProject: StateFlow<DamProjectFile?> = damProjectManager.currentProject

    private val _recentProjects = MutableStateFlow<List<DamProjectManager.DamProjectInfo>>(emptyList())
    val recentProjects: StateFlow<List<DamProjectManager.DamProjectInfo>> = _recentProjects.asStateFlow()

    private val _showCreateDialog = MutableStateFlow(false)
    val showCreateDialog: StateFlow<Boolean> = _showCreateDialog.asStateFlow()

    private val _createProjectName = MutableStateFlow("")
    val createProjectName: StateFlow<String> = _createProjectName.asStateFlow()

    private val _createProjectPath = MutableStateFlow("")
    val createProjectPath: StateFlow<String> = _createProjectPath.asStateFlow()

    private val _shareState = MutableStateFlow(ShareState())
    val shareState: StateFlow<ShareState> = _shareState.asStateFlow()

    private val _exportProgress = MutableStateFlow(ExportProgress())
    val exportProgress: StateFlow<ExportProgress> = _exportProgress.asStateFlow()

    private val imageProcessor = ImageProcessor()
    private val exportCancelled = AtomicBoolean(false)

    private val prefs: android.content.SharedPreferences by lazy {
        SafePreferences.get(getApplication(), "dam_projects", Context.MODE_PRIVATE)
    }

    init {
        loadRecentProjects()
    }

    fun loadProject() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            try {
                val project = withContext(Dispatchers.IO) {
                    // 优先复用 DamProjectManager 已加载的项目
                    var proj = damProjectManager.currentProject.value
                    if (proj == null) {
                        // 回退到最近一次打开的项目路径
                        val recentPath = _recentProjects.value.firstOrNull()?.path
                        if (recentPath != null) {
                            proj = damProjectManager.loadProject(recentPath)
                        }
                    }
                    proj
                }

                if (project == null) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = "项目加载失败：文件不存在或已损坏",
                    )
                    return@launch
                }

                // 解析项目中的图片列表 (DamImageEntry -> ImageFile)
                val images = withContext(Dispatchers.IO) {
                    project.imageEntries.map { entry ->
                        ImageFile(
                            path = entry.path,
                            fileName = entry.fileName,
                            folderPath = entry.folderPath,
                            isRaw = entry.isRaw,
                            width = entry.width,
                            height = entry.height,
                            fileSize = entry.fileSize,
                            dateModified = entry.dateModified,
                            rating = entry.rating,
                            colorLabel = entry.colorLabel,
                            tags = entry.tags,
                            thumbnailPath = entry.thumbnailPath,
                            virtualCopyOf = entry.virtualCopyOf,
                            lastEdited = entry.lastEdited,
                        )
                    }
                }

                _state.value = _state.value.copy(
                    project = project,
                    images = images,
                    isLoading = false,
                    errorMessage = null,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "loadProject failed", e)
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = e.localizedMessage ?: "项目加载失败",
                )
            }
        }
    }

    fun toggleStar() {
        viewModelScope.launch {
            val project = _state.value.project ?: return@launch
            _state.value = _state.value.copy(
                project = project.copy(isStarred = !project.isStarred)
            )
        }
    }

    fun shareProject() {
        viewModelScope.launch {
            val project = _state.value.project ?: run {
                _shareState.value = ShareState(error = "未加载项目，无法分享")
                return@launch
            }
            _shareState.value = ShareState(isSharing = true)
            try {
                val app = getApplication<Application>()

                // 将项目 JSON 写入缓存目录，通过 FileProvider 生成可分享 URI
                val uri = withContext(Dispatchers.IO) {
                    val shareDir = File(app.cacheDir, "dam_project_share").apply { mkdirs() }
                    val safeName = project.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
                        .ifBlank { "project" }
                    val shareFile = File(shareDir, "$safeName.json")
                    shareFile.writeText(DamProjectFile.toJson(project))
                    FileProvider.getUriForFile(
                        app,
                        "${app.packageName}.fileprovider",
                        shareFile,
                    )
                }

                _shareState.value = ShareState(isSharing = false, shareUri = uri)

                // 触发 ACTION_SEND 分享
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(sendIntent, "分享项目").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching {
                    app.startActivity(chooser)
                }.onFailure {
                    Log.w(TAG, "No app available to share project", it)
                    _shareState.value = ShareState(
                        isSharing = false,
                        shareUri = uri,
                        error = "未找到可分享的应用",
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "shareProject failed", e)
                _shareState.value = ShareState(
                    isSharing = false,
                    error = e.localizedMessage ?: "分享失败",
                )
            }
        }
    }

    fun resetShareState() {
        _shareState.value = ShareState()
    }

    fun batchExport() {
        viewModelScope.launch {
            val project = _state.value.project ?: run {
                _exportProgress.value = ExportProgress(error = "未加载项目，无法导出")
                return@launch
            }
            val entries = project.imageEntries
            if (entries.isEmpty()) {
                _exportProgress.value = ExportProgress(error = "项目中没有可导出的图片")
                return@launch
            }

            exportCancelled.set(false)
            val total = entries.size
            _exportProgress.value = ExportProgress(
                isRunning = true,
                completed = 0,
                total = total,
                failed = 0,
                currentFile = "",
                resultUris = emptyList(),
            )

            val app = getApplication<Application>()
            // 项目预设调整参数：DAM 项目不存储每图调整，使用默认 Adjustments 作为导出基准
            val adjustments = Adjustments()
            val exportSettings = ExportSettings(preserveFolderStructure = true)
            val results = mutableListOf<Uri>()
            var failed = 0
            var completed = 0

            for (entry in entries) {
                // 处理用户取消与协程取消
                if (exportCancelled.get() || !isActive) {
                    _exportProgress.value = _exportProgress.value.copy(
                        isRunning = false,
                        isCancelled = true,
                        completed = completed,
                        total = total,
                        failed = failed,
                        resultUris = results.toList(),
                    )
                    return@launch
                }

                _exportProgress.value = _exportProgress.value.copy(
                    completed = completed,
                    total = total,
                    failed = failed,
                    currentFile = entry.fileName,
                    resultUris = results.toList(),
                )

                try {
                    val sourceFile = File(entry.path)
                    if (!sourceFile.exists()) {
                        Log.w(TAG, "Skipping missing file: ${entry.path}")
                        failed++
                    } else {
                        // 复用 ImageProcessor: 解码 -> 全分辨率处理 -> 导出
                        val sourceUri = Uri.fromFile(sourceFile)
                        val decoded = imageProcessor.loadAndDecode(app, sourceUri)
                        val processed = imageProcessor.processFullResolution(adjustments, decoded.original)
                        val exportUri = imageProcessor.exportImage(
                            processed,
                            exportSettings,
                            app,
                            decoded.exif,
                            decoded.orientation,
                            originalPath = entry.path,
                        )
                        if (processed !== decoded.original) processed.recycle()
                        results.add(exportUri)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // 单张失败继续处理后续
                    Log.e(TAG, "Batch export failed: ${entry.fileName}", e)
                    failed++
                }
                completed++
            }

            _exportProgress.value = ExportProgress(
                isRunning = false,
                isCompleted = true,
                completed = completed,
                total = total,
                failed = failed,
                currentFile = "",
                resultUris = results.toList(),
            )
        }
    }

    fun cancelBatchExport() {
        exportCancelled.set(true)
    }

    fun resetExportProgress() {
        exportCancelled.set(false)
        _exportProgress.value = ExportProgress()
    }

    fun removeImage(path: String) {
        _state.value = _state.value.copy(
            images = _state.value.images.filter { it.path != path }
        )
    }

    fun deleteProject() {
        viewModelScope.launch {
            _state.value = _state.value.copy(project = null)
        }
    }

    fun renameProject(newName: String) {
        viewModelScope.launch {
            val project = _state.value.project ?: return@launch
            _state.value = _state.value.copy(
                project = project.copy(name = newName)
            )
        }
    }

    private fun loadRecentProjects() {
        val count = SafePreferences.getInt(prefs, "recent_count", 0)
        val projects = mutableListOf<DamProjectManager.DamProjectInfo>()
        for (i in 0 until count) {
            val path = SafePreferences.getString(prefs, "recent_${i}_path", null) ?: continue
            val name = SafePreferences.getString(prefs, "recent_${i}_name", "") ?: ""
            val thumb = SafePreferences.getString(prefs, "recent_${i}_thumb", null)
            val lastOpened = SafePreferences.getLong(prefs, "recent_${i}_time", 0L)
            val imageCount = SafePreferences.getInt(prefs, "recent_${i}_count", 0)
            projects.add(DamProjectManager.DamProjectInfo(path, name, thumb, lastOpened, imageCount))
        }
        _recentProjects.value = projects.sortedByDescending { it.lastOpened }
    }

    fun showCreateProjectDialog() {
        _showCreateDialog.value = true
    }

    fun dismissCreateProjectDialog() {
        _showCreateDialog.value = false
        _createProjectName.value = ""
        _createProjectPath.value = ""
    }

    fun updateCreateProjectName(name: String) {
        _createProjectName.value = name
    }

    fun updateCreateProjectPath(path: String) {
        _createProjectPath.value = path
    }

    fun createProject(name: String, path: String) {
        viewModelScope.launch {
            _showCreateDialog.value = false
            _createProjectName.value = ""
            _createProjectPath.value = ""
            withContext(Dispatchers.IO) {
                damProjectManager.createNewProject(name, path)
            }
            loadRecentProjects()
        }
    }

    fun openProject(path: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                damProjectManager.loadProject(path)
            }
            loadRecentProjects()
        }
    }

    fun deleteRecentProject(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val count = SafePreferences.getInt(prefs, "recent_count", 0)
            val remaining = mutableListOf<DamProjectManager.DamProjectInfo>()

            for (i in 0 until count) {
                val p = SafePreferences.getString(prefs, "recent_${i}_path", null) ?: continue
                if (p == path) continue
                val name = SafePreferences.getString(prefs, "recent_${i}_name", "") ?: ""
                val thumb = SafePreferences.getString(prefs, "recent_${i}_thumb", null)
                val lastOpened = SafePreferences.getLong(prefs, "recent_${i}_time", 0L)
                val imageCount = SafePreferences.getInt(prefs, "recent_${i}_count", 0)
                remaining.add(
                    DamProjectManager.DamProjectInfo(p, name, thumb, lastOpened, imageCount)
                )
            }

            SafePreferences.clear(prefs)
            SafePreferences.putInt(prefs, "recent_count", remaining.size)
            remaining.forEachIndexed { index, info ->
                SafePreferences.putString(prefs, "recent_${index}_path", info.path)
                SafePreferences.putString(prefs, "recent_${index}_name", info.name)
                SafePreferences.putString(prefs, "recent_${index}_thumb", info.thumbnailPath)
                SafePreferences.putLong(prefs, "recent_${index}_time", info.lastOpened)
                SafePreferences.putInt(prefs, "recent_${index}_count", info.imageCount)
            }

            _recentProjects.value = remaining.sortedByDescending { it.lastOpened }
        }
    }
}
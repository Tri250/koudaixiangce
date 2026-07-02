package com.rapidraw.ui.dam

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rapidraw.core.DamProjectManager
import com.rapidraw.data.model.DamProjectFile
import com.rapidraw.data.model.ImageFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DamProjectDetailState(
    val project: DamProjectFile? = null,
    val isLoading: Boolean = false,
    val images: List<ImageFile> = emptyList(),
)

class DamProjectViewModel(application: Application) : AndroidViewModel(application) {

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

    private val prefs: android.content.SharedPreferences by lazy {
        getApplication<Application>().getSharedPreferences("dam_projects", Context.MODE_PRIVATE)
    }

    init {
        loadRecentProjects()
    }

    fun loadProject() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            try {
                withContext(Dispatchers.IO) {
                    // Project loading logic
                }
                _state.value = _state.value.copy(isLoading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false)
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
        // Stub for compilation
    }

    fun batchExport() {
        viewModelScope.launch {
            // Stub for compilation
        }
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
        val count = prefs.getInt("recent_count", 0)
        val projects = mutableListOf<DamProjectManager.DamProjectInfo>()
        for (i in 0 until count) {
            val path = prefs.getString("recent_${i}_path", null) ?: continue
            val name = prefs.getString("recent_${i}_name", "") ?: ""
            val thumb = prefs.getString("recent_${i}_thumb", null)
            val lastOpened = prefs.getLong("recent_${i}_time", 0L)
            val imageCount = prefs.getInt("recent_${i}_count", 0)
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
            val count = prefs.getInt("recent_count", 0)
            val remaining = mutableListOf<DamProjectManager.DamProjectInfo>()

            for (i in 0 until count) {
                val p = prefs.getString("recent_${i}_path", null) ?: continue
                if (p == path) continue
                val name = prefs.getString("recent_${i}_name", "") ?: ""
                val thumb = prefs.getString("recent_${i}_thumb", null)
                val lastOpened = prefs.getLong("recent_${i}_time", 0L)
                val imageCount = prefs.getInt("recent_${i}_count", 0)
                remaining.add(
                    DamProjectManager.DamProjectInfo(p, name, thumb, lastOpened, imageCount)
                )
            }

            val editor = prefs.edit()
            editor.clear()
            editor.putInt("recent_count", remaining.size)
            remaining.forEachIndexed { index, info ->
                editor.putString("recent_${index}_path", info.path)
                editor.putString("recent_${index}_name", info.name)
                editor.putString("recent_${index}_thumb", info.thumbnailPath)
                editor.putLong("recent_${index}_time", info.lastOpened)
                editor.putInt("recent_${index}_count", info.imageCount)
            }
            editor.apply()

            _recentProjects.value = remaining.sortedByDescending { it.lastOpened }
        }
    }
}
package com.rapidraw.core

import android.content.Context
import android.net.Uri
import android.util.Log
import com.rapidraw.data.model.DamCameraStats
import com.rapidraw.data.model.DamDateGroup
import com.rapidraw.data.model.DamFacetData
import com.rapidraw.data.model.DamImageEntry
import com.rapidraw.data.model.DamLibraryStats
import com.rapidraw.data.model.DamLensStats
import com.rapidraw.data.model.DamProjectFile
import com.rapidraw.data.model.DamSearchQuery
import com.rapidraw.data.model.ImageFile
import com.rapidraw.data.model.ColorLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class DamProjectManager(private val context: Context) {

    companion object {
        private const val TAG = "DamProjectManager"
        private const val PROJECT_FILE_NAME = "project.json"
    }

    private val _currentProject = MutableStateFlow<DamProjectFile?>(null)
    val currentProject: StateFlow<DamProjectFile?> = _currentProject.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _recentProjects = MutableStateFlow<List<DamProjectInfo>>(emptyList())
    val recentProjects: StateFlow<List<DamProjectInfo>> = _recentProjects.asStateFlow()

    private val _searchResults = MutableStateFlow<List<DamImageEntry>>(emptyList())
    val searchResults: StateFlow<List<DamImageEntry>> = _searchResults.asStateFlow()

    private val _facetData = MutableStateFlow(DamFacetData())
    val facetData: StateFlow<DamFacetData> = _facetData.asStateFlow()

    private val _libraryStats = MutableStateFlow(DamLibraryStats())
    val libraryStats: StateFlow<DamLibraryStats> = _libraryStats.asStateFlow()

    data class DamProjectInfo(
        val path: String,
        val name: String,
        val thumbnailPath: String?,
        val lastOpened: Long,
        val imageCount: Int,
    )

    init {
        loadRecentProjects()
    }

    suspend fun createNewProject(name: String, savePath: String): DamProjectFile =
        withContext(Dispatchers.IO) {
            val project = DamProjectFile(
                name = name,
                createdAt = System.currentTimeMillis(),
                modifiedAt = System.currentTimeMillis(),
            )
            saveProject(project, savePath)
            _currentProject.value = project
            addToRecentProjects(savePath, name, null, 0)
            project
        }

    suspend fun loadProject(path: String): DamProjectFile? =
        withContext(Dispatchers.IO) {
            _isLoading.value = true
            try {
                val file = File(path)
                if (!file.exists()) {
                    Log.e(TAG, "Project file not found: $path")
                    return@withContext null
                }

                val project = if (path.endsWith(".alcd", ignoreCase = true)) {
                    loadFromAlcdFile(path)
                } else {
                    loadFromJsonFile(path)
                }

                if (project != null) {
                    _currentProject.value = project
                    _facetData.value = project.facetData
                    _libraryStats.value = project.libraryStats
                    addToRecentProjects(
                        path,
                        project.name,
                        null,
                        project.imageEntries.size
                    )
                }

                project
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load project", e)
                null
            } finally {
                _isLoading.value = false
            }
        }

    private fun loadFromAlcdFile(path: String): DamProjectFile? {
        return try {
            ZipInputStream(FileInputStream(path)).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    if (entry.name == PROJECT_FILE_NAME) {
                        val json = zis.bufferedReader().readText()
                        return DamProjectFile.fromJson(json)
                    }
                    entry = zis.nextEntry
                }
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load .alcd file", e)
            null
        }
    }

    private fun loadFromJsonFile(path: String): DamProjectFile? {
        return try {
            val json = File(path).readText()
            DamProjectFile.fromJson(json)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load JSON project", e)
            null
        }
    }

    suspend fun saveProject(project: DamProjectFile, path: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val updatedProject = project.copy(modifiedAt = System.currentTimeMillis())

                if (path.endsWith(".alcd", ignoreCase = true)) {
                    saveToAlcdFile(updatedProject, path)
                } else {
                    saveToJsonFile(updatedProject, path)
                }

                _currentProject.value = updatedProject
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save project", e)
                false
            }
        }

    private fun saveToAlcdFile(project: DamProjectFile, path: String) {
        ZipOutputStream(FileOutputStream(path)).use { zos ->
            val json = DamProjectFile.toJson(project)
            val entry = ZipEntry(PROJECT_FILE_NAME)
            zos.putNextEntry(entry)
            zos.write(json.toByteArray())
            zos.closeEntry()
        }
    }

    private fun saveToJsonFile(project: DamProjectFile, path: String) {
        File(path).writeText(DamProjectFile.toJson(project))
    }

    suspend fun addImagesToProject(
        images: List<ImageFile>,
        projectPath: String
    ): Boolean = withContext(Dispatchers.IO) {
        val project = _currentProject.value ?: return@withContext false

        val newEntries = images.map { img ->
            DamImageEntry(
                id = UUID.randomUUID().toString(),
                path = img.path,
                fileName = img.fileName,
                folderPath = img.folderPath,
                isRaw = img.isRaw,
                width = img.width,
                height = img.height,
                fileSize = img.fileSize,
                dateModified = img.dateModified,
                rating = img.rating,
                colorLabel = img.colorLabel,
                tags = img.tags,
                thumbnailPath = img.thumbnailPath,
                hasAdjustments = img.adjustments != null,
                virtualCopyOf = img.virtualCopyOf,
                lastEdited = img.lastEdited,
            )
        }

        val updatedEntries = project.imageEntries + newEntries
        val updatedStats = computeLibraryStats(updatedEntries)
        val updatedFacets = computeFacetData(updatedEntries)

        val updatedProject = project.copy(
            imageEntries = updatedEntries,
            libraryStats = updatedStats,
            facetData = updatedFacets,
            modifiedAt = System.currentTimeMillis(),
        )

        saveProject(updatedProject, projectPath)
    }

    suspend fun removeImagesFromProject(
        imageIds: List<String>,
        projectPath: String
    ): Boolean = withContext(Dispatchers.IO) {
        val project = _currentProject.value ?: return@withContext false

        val updatedEntries = project.imageEntries.filter { it.id !in imageIds }
        val updatedStats = computeLibraryStats(updatedEntries)
        val updatedFacets = computeFacetData(updatedEntries)

        val updatedProject = project.copy(
            imageEntries = updatedEntries,
            libraryStats = updatedStats,
            facetData = updatedFacets,
            modifiedAt = System.currentTimeMillis(),
        )

        saveProject(updatedProject, projectPath)
    }

    fun searchImages(query: DamSearchQuery) {
        val project = _currentProject.value ?: return

        val results = project.imageEntries.filter { entry ->
            if (query.text.isNotBlank()) {
                val text = query.text.lowercase()
                entry.fileName.lowercase().contains(text) ||
                        entry.tags.any { it.lowercase().contains(text) } ||
                        (entry.cameraModel?.lowercase()?.contains(text) == true) ||
                        (entry.lensModel?.lowercase()?.contains(text) == true)
            } else true
        }.filter { entry ->
            entry.rating in query.ratingMin..query.ratingMax
        }.filter { entry ->
            if (query.colorLabels.isNotEmpty()) {
                entry.colorLabel in query.colorLabels
            } else true
        }.filter { entry ->
            if (query.cameras.isNotEmpty()) {
                entry.cameraModel in query.cameras
            } else true
        }.filter { entry ->
            if (query.lenses.isNotEmpty()) {
                entry.lensModel in query.lenses
            } else true
        }.filter { entry ->
            if (query.dateFrom != null) {
                entry.dateTaken >= query.dateFrom
            } else true
        }.filter { entry ->
            if (query.dateTo != null) {
                entry.dateTaken <= query.dateTo
            } else true
        }.filter { entry ->
            if (query.fileTypes.isNotEmpty()) {
                val ext = entry.fileName.substringAfterLast('.', "").lowercase()
                ext in query.fileTypes
            } else true
        }.filter { entry ->
            if (query.tags.isNotEmpty()) {
                query.tags.all { it in entry.tags }
            } else true
        }.filter { entry ->
            when (query.hasAdjustments) {
                true -> entry.hasAdjustments
                false -> !entry.hasAdjustments
                null -> true
            }
        }.filter { entry ->
            if (query.flagStatus != null) {
                entry.flagStatus == query.flagStatus
            } else true
        }.sortedWith(compareBy<DamImageEntry> {
            when (query.sortBy) {
                "fileName" -> it.fileName
                "dateTaken" -> it.dateTaken.toString()
                "dateModified" -> it.dateModified.toString()
                "rating" -> it.rating.toString()
                "fileSize" -> it.fileSize.toString()
                else -> it.dateTaken.toString()
            }
        }.run {
            if (query.sortOrder == "desc") reversed() else this
        })

        _searchResults.value = results
    }

    fun computeLibraryStats(entries: List<DamImageEntry>): DamLibraryStats {
        var rawCount = 0
        var jpegCount = 0
        var totalSize = 0L
        var editedCount = 0
        var ratedCount = 0

        for (entry in entries) {
            if (entry.isRaw) rawCount++ else jpegCount++
            totalSize += entry.fileSize
            if (entry.hasAdjustments) editedCount++
            if (entry.rating > 0) ratedCount++
        }

        return DamLibraryStats(
            totalImages = entries.size,
            rawCount = rawCount,
            jpegCount = jpegCount,
            totalFileSize = totalSize,
            editedCount = editedCount,
            ratedCount = ratedCount,
        )
    }

    fun computeFacetData(entries: List<DamImageEntry>): DamFacetData {
        val cameraMap = mutableMapOf<String, MutableList<String>>()
        val lensMap = mutableMapOf<String, Int>()
        val dateMap = mutableMapOf<String, Int>()
        val focalLengthMap = mutableMapOf<Int, Int>()
        val apertureMap = mutableMapOf<String, Int>()
        val isoMap = mutableMapOf<Int, Int>()

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        for (entry in entries) {
            val cameraKey = "${entry.cameraMake ?: "Unknown"} ${entry.cameraModel ?: "Unknown"}"
            if (entry.cameraModel != null) {
                val make = entry.cameraMake ?: "Unknown"
                if (!cameraMap.containsKey(make)) {
                    cameraMap[make] = mutableListOf()
                }
                if (entry.cameraModel !in cameraMap[make]!!) {
                    cameraMap[make]!!.add(entry.cameraModel)
                }
            }

            if (entry.lensModel != null) {
                lensMap[entry.lensModel] = (lensMap[entry.lensModel] ?: 0) + 1
            }

            if (entry.dateTaken > 0) {
                val dateStr = dateFormat.format(Date(entry.dateTaken))
                dateMap[dateStr] = (dateMap[dateStr] ?: 0) + 1
            }

            entry.focalLength?.let { fl ->
                val flInt = (fl / 10f).toInt() * 10
                focalLengthMap[flInt] = (focalLengthMap[flInt] ?: 0) + 1
            }

            entry.aperture?.let { ap ->
                val apStr = "f/${String.format(Locale.US, "%.1f", ap)}"
                apertureMap[apStr] = (apertureMap[apStr] ?: 0) + 1
            }

            entry.iso?.let { iso ->
                val isoBucket = when {
                    iso < 200 -> 100
                    iso < 400 -> 200
                    iso < 800 -> 400
                    iso < 1600 -> 800
                    iso < 3200 -> 1600
                    iso < 6400 -> 3200
                    else -> 6400
                }
                isoMap[isoBucket] = (isoMap[isoBucket] ?: 0) + 1
            }
        }

        val cameras = cameraMap.flatMap { (make, models) ->
            models.map { model ->
                DamCameraStats(make, model, entries.count {
                    it.cameraMake == make && it.cameraModel == model
                })
            }
        }.sortedByDescending { it.count }

        val lenses = lensMap.map { (lens, count) ->
            DamLensStats(lens, count)
        }.sortedByDescending { it.count }

        val dateGroups = dateMap.map { (date, count) ->
            DamDateGroup(date, count)
        }.sortedByDescending { it.date }

        return DamFacetData(
            cameras = cameras,
            lenses = lenses,
            dateGroups = dateGroups,
            focalLengths = focalLengthMap.toSortedMap(),
            apertureValues = apertureMap.toSortedMap(),
            isoValues = isoMap.toSortedMap(),
        )
    }

    fun updateImageEntry(imageId: String, updates: (DamImageEntry) -> DamImageEntry) {
        val project = _currentProject.value ?: return
        val updatedEntries = project.imageEntries.map { entry ->
            if (entry.id == imageId) updates(entry) else entry
        }
        val updatedStats = computeLibraryStats(updatedEntries)
        val updatedFacets = computeFacetData(updatedEntries)

        _currentProject.value = project.copy(
            imageEntries = updatedEntries,
            libraryStats = updatedStats,
            facetData = updatedFacets,
            modifiedAt = System.currentTimeMillis(),
        )
        _facetData.value = updatedFacets
        _libraryStats.value = updatedStats
    }

    private fun loadRecentProjects() {
        val prefs = context.getSharedPreferences("dam_projects", Context.MODE_PRIVATE)
        val count = prefs.getInt("recent_count", 0)
        val projects = mutableListOf<DamProjectInfo>()
        for (i in 0 until count) {
            val path = prefs.getString("recent_${i}_path", null) ?: continue
            val name = prefs.getString("recent_${i}_name", "") ?: ""
            val thumb = prefs.getString("recent_${i}_thumb", null)
            val lastOpened = prefs.getLong("recent_${i}_time", 0L)
            val imageCount = prefs.getInt("recent_${i}_count", 0)
            projects.add(DamProjectInfo(path, name, thumb, lastOpened, imageCount))
        }
        _recentProjects.value = projects.sortedByDescending { it.lastOpened }
    }

    private fun addToRecentProjects(
        path: String,
        name: String,
        thumbnailPath: String?,
        imageCount: Int
    ) {
        val prefs = context.getSharedPreferences("dam_projects", Context.MODE_PRIVATE)
        val editor = prefs.edit()

        val existing = _recentProjects.value.filter { it.path != path }.take(9)
        val updated = listOf(
            DamProjectInfo(path, name, thumbnailPath, System.currentTimeMillis(), imageCount)
        ) + existing

        editor.putInt("recent_count", updated.size)
        updated.forEachIndexed { index, info ->
            editor.putString("recent_${index}_path", info.path)
            editor.putString("recent_${index}_name", info.name)
            editor.putString("recent_${index}_thumb", info.thumbnailPath)
            editor.putLong("recent_${index}_time", info.lastOpened)
            editor.putInt("recent_${index}_count", info.imageCount)
        }
        editor.apply()

        _recentProjects.value = updated
    }

    suspend fun exportProjectToZip(project: DamProjectFile, outputPath: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                ZipOutputStream(FileOutputStream(outputPath)).use { zos ->
                    val json = DamProjectFile.toJson(project)
                    zos.putNextEntry(ZipEntry("project.alcd"))
                    zos.write(json.toByteArray())
                    zos.closeEntry()

                    zos.putNextEntry(ZipEntry("manifest.txt"))
                    val manifest = buildString {
                        append("Alcedo Studio Project\n")
                        append("Version: ${project.version}\n")
                        append("Name: ${project.name}\n")
                        append("Created: ${Date(project.createdAt)}\n")
                        append("Modified: ${Date(project.modifiedAt)}\n")
                        append("Images: ${project.imageEntries.size}\n")
                    }
                    zos.write(manifest.toByteArray())
                    zos.closeEntry()
                }
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to export project", e)
                false
            }
        }
}

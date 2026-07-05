package com.alcedo.studio.data.repository

import com.alcedo.studio.data.db.AppDatabase
import com.alcedo.studio.data.db.PresetEntity
import com.alcedo.studio.data.db.ProjectEntity
import com.alcedo.studio.data.db.FavoriteEntity
import com.alcedo.studio.data.db.ExportJobEntity
import com.alcedo.studio.data.model.Adjustments
import com.alcedo.studio.data.model.FilmSimulation
import com.alcedo.studio.data.model.Preset
import com.alcedo.studio.data.model.Project
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ProjectRepository(database: AppDatabase) {

    private val projectDao = database.projectDao()

    fun getAllProjects(): Flow<List<Project>> =
        projectDao.getAllProjects().map { list -> list.map { it.toModel() } }

    suspend fun getProjectBySourceUri(sourceUri: String): Project? =
        projectDao.getProjectBySourceUri(sourceUri)?.toModel()

    suspend fun getProjectById(id: Long): Project? =
        projectDao.getProjectById(id)?.toModel()

    suspend fun saveProject(project: Project): Long {
        val entity = project.toEntity()
        return if (project.id > 0) {
            projectDao.updateProject(entity)
            project.id
        } else {
            projectDao.insertProject(entity)
        }
    }

    suspend fun deleteProject(id: Long) {
        projectDao.softDeleteProject(id)
    }

    private fun ProjectEntity.toModel(): Project = Project(
        id = id,
        sourceUri = sourceUri,
        displayName = displayName,
        adjustments = adjustments,
        editHistory = editHistory,
        version = version,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastEditedAt = lastEditedAt,
        rating = rating,
        colorLabel = colorLabel,
        isDeleted = isDeleted,
    )

    private fun Project.toEntity(): ProjectEntity = ProjectEntity(
        id = id,
        sourceUri = sourceUri,
        displayName = displayName,
        adjustments = adjustments,
        editHistory = editHistory,
        version = version,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastEditedAt = lastEditedAt,
        rating = rating,
        colorLabel = colorLabel,
        isDeleted = isDeleted,
    )
}

class PresetRepository(database: AppDatabase) {

    private val presetDao = database.presetDao()

    fun getAllPresets(): Flow<List<Preset>> =
        presetDao.getAllPresets().map { list -> list.map { it.toModel() } }

    fun getPresetsByCategory(category: String): Flow<List<Preset>> =
        presetDao.getPresetsByCategory(category).map { list -> list.map { it.toModel() } }

    fun getFavoritePresets(): Flow<List<Preset>> =
        presetDao.getFavoritePresets().map { list -> list.map { it.toModel() } }

    suspend fun getPresetById(id: Long): Preset? =
        presetDao.getPresetById(id)?.toModel()

    suspend fun savePreset(preset: Preset): Long {
        val entity = preset.toEntity()
        return if (preset.id > 0) {
            presetDao.updatePreset(entity)
            preset.id
        } else {
            presetDao.insertPreset(entity)
        }
    }

    suspend fun deletePreset(preset: Preset) {
        presetDao.deletePreset(preset.toEntity())
    }

    suspend fun setFavorite(id: Long, isFavorite: Boolean) {
        presetDao.setFavorite(id, isFavorite)
    }

    suspend fun incrementUsage(id: Long) {
        presetDao.incrementUsage(id)
    }

    suspend fun getCategories(): List<String> = presetDao.getCategories()

    suspend fun ensureBuiltInPresets() {
        val count = getCategories().size
        if (count == 0) {
            FilmSimulation.BuiltInList.forEachIndexed { index, film ->
                presetDao.insertPreset(
                    PresetEntity(
                        id = (index + 1).toLong(),
                        name = film.name,
                        description = film.description,
                        category = "胶片模拟",
                        adjustments = film.adjustments,
                        isBuiltIn = true,
                    )
                )
            }
        }
    }

    private fun PresetEntity.toModel(): Preset = Preset(
        id = id,
        name = name,
        description = description,
        category = category,
        adjustments = adjustments,
        thumbnail = thumbnail,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isBuiltIn = isBuiltIn,
        isFavorite = isFavorite,
        usageCount = usageCount,
    )

    private fun Preset.toEntity(): PresetEntity = PresetEntity(
        id = id,
        name = name,
        description = description,
        category = category,
        adjustments = adjustments,
        thumbnail = thumbnail,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isBuiltIn = isBuiltIn,
        isFavorite = isFavorite,
        usageCount = usageCount,
    )
}

class FavoriteRepository(database: AppDatabase) {

    private val favoriteDao = database.favoriteDao()

    fun getAllFavorites(): Flow<List<FavoriteEntity>> = favoriteDao.getAllFavorites()

    suspend fun isFavorite(mediaStoreId: Long): Boolean = favoriteDao.isFavorite(mediaStoreId)

    suspend fun addFavorite(
        mediaStoreId: Long,
        uri: String,
        displayName: String
    ) {
        favoriteDao.addFavorite(
            FavoriteEntity(
                mediaStoreId = mediaStoreId,
                uri = uri,
                displayName = displayName,
            )
        )
    }

    suspend fun removeFavorite(mediaStoreId: Long) {
        favoriteDao.removeFavorite(mediaStoreId)
    }

    suspend fun toggleFavorite(
        mediaStoreId: Long,
        uri: String,
        displayName: String
    ): Boolean {
        return if (isFavorite(mediaStoreId)) {
            removeFavorite(mediaStoreId)
            false
        } else {
            addFavorite(mediaStoreId, uri, displayName)
            true
        }
    }

    suspend fun getFavoriteCount(): Int = favoriteDao.getFavoriteCount()
}

class ExportJobRepository(database: AppDatabase) {

    private val exportJobDao = database.exportJobDao()

    fun getAllJobs(): Flow<List<ExportJobEntity>> = exportJobDao.getAllJobs()

    fun getPendingAndRunningJobs(): Flow<List<ExportJobEntity>> =
        exportJobDao.getPendingAndRunningJobs()

    fun getCompletedJobs(): Flow<List<ExportJobEntity>> = exportJobDao.getCompletedJobs()

    suspend fun addJob(job: ExportJobEntity): Long = exportJobDao.insertJob(job)

    suspend fun updateJob(job: ExportJobEntity) = exportJobDao.updateJob(job)

    suspend fun cancelJob(id: Long) = exportJobDao.cancelJob(id)

    suspend fun clearCompletedJobs() = exportJobDao.clearCompletedJobs()
}

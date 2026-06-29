package com.rapidraw.data.repository

import android.content.Context
import com.rapidraw.data.db.ProjectEntity
import com.rapidraw.data.db.RecipeDatabase
import com.rapidraw.data.model.Project
import com.rapidraw.data.model.ProjectSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ProjectRepository(context: Context) {
    private val dao = RecipeDatabase.getInstance(context).projectDao()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun createProject(name: String, settings: ProjectSettings? = null): Project {
        val project = Project(name = name, settings = settings)
        dao.insert(project.toEntity())
        return project
    }

    suspend fun updateProject(project: Project) {
        dao.update(project.copy(modifiedAt = System.currentTimeMillis()).toEntity())
    }

    suspend fun deleteProject(projectId: String) {
        dao.deleteById(projectId)
    }

    fun getAllProjects(): Flow<List<Project>> = dao.getAll().map { list ->
        list.map { it.toProject() }
    }

    suspend fun getProjectById(id: String): Project? {
        return dao.getById(id)?.toProject()
    }

    suspend fun addImageToProject(projectId: String, imagePath: String) {
        val entity = dao.getById(projectId) ?: return
        val project = entity.toProject()
        if (imagePath !in project.imagePaths) {
            val updated = project.copy(
                imagePaths = project.imagePaths + imagePath,
                modifiedAt = System.currentTimeMillis()
            )
            dao.update(updated.toEntity())
        }
    }

    suspend fun removeImageFromProject(projectId: String, imagePath: String) {
        val entity = dao.getById(projectId) ?: return
        val project = entity.toProject()
        val updated = project.copy(
            imagePaths = project.imagePaths - imagePath,
            modifiedAt = System.currentTimeMillis()
        )
        dao.update(updated.toEntity())
    }

    private fun Project.toEntity(): ProjectEntity {
        return ProjectEntity(
            id = id,
            name = name,
            createdAt = createdAt,
            modifiedAt = modifiedAt,
            imagePaths = imagePaths,
            thumbnailPath = thumbnailPath,
            settingsJson = settings?.let { json.encodeToString(it) }
        )
    }

    private fun ProjectEntity.toProject(): Project {
        return Project(
            id = id,
            name = name,
            createdAt = createdAt,
            modifiedAt = modifiedAt,
            imagePaths = imagePaths,
            thumbnailPath = thumbnailPath,
            settings = settingsJson?.let {
                try {
                    json.decodeFromString<ProjectSettings>(it)
                } catch (_: Exception) {
                    null
                }
            }
        )
    }
}

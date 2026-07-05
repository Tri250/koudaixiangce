package com.rapidraw.data.repository

import android.content.Context
import com.rapidraw.data.db.ProjectEntity
import com.rapidraw.data.db.RecipeDatabase
import com.rapidraw.data.model.Project
import com.rapidraw.data.model.ProjectSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DAM 项目数据仓库。
 *
 * ── Hilt 依赖注入迁移 (v2.51.1) ────────────────────────────────
 * 迁移步骤 8: 添加 @Inject constructor + @Singleton 注解。
 *
 * @Inject 使 Hilt 能够自动创建此 Repository 实例。
 * @Singleton 确保全局唯一实例（数据一致性）。
 * @ApplicationContext 注入 Application Context（避免 Activity Context 泄漏）。
 *
 * v2026.07 hotfix: 移除 ApplicationModule 中的 @Provides 方法，仅使用 @Inject constructor。
 * Hilt 不允许同一类型同时通过 @Inject constructor 和 @Provides 提供，
 * 会触发 DuplicateBindings 编译错误。
 *
 * @since v1.10.0（Hilt DI 迁移）
 */
@Singleton
class ProjectRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
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

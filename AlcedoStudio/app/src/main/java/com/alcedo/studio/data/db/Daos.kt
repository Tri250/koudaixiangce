package com.alcedo.studio.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {

    @Query("SELECT * FROM projects WHERE isDeleted = 0 ORDER BY updatedAt DESC")
    fun getAllProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE sourceUri = :sourceUri AND isDeleted = 0 LIMIT 1")
    suspend fun getProjectBySourceUri(sourceUri: String): ProjectEntity?

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getProjectById(id: Long): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity): Long

    @Update
    suspend fun updateProject(project: ProjectEntity)

    @Delete
    suspend fun deleteProject(project: ProjectEntity)

    @Query("UPDATE projects SET isDeleted = 1 WHERE id = :id")
    suspend fun softDeleteProject(id: Long)

    @Query("DELETE FROM projects WHERE isDeleted = 1")
    suspend fun purgeDeletedProjects()

    @Query("SELECT COUNT(*) FROM projects WHERE isDeleted = 0")
    suspend fun getProjectCount(): Int
}

@Dao
interface PresetDao {

    @Query("SELECT * FROM presets ORDER BY isBuiltIn DESC, usageCount DESC, name ASC")
    fun getAllPresets(): Flow<List<PresetEntity>>

    @Query("SELECT * FROM presets WHERE category = :category ORDER BY name ASC")
    fun getPresetsByCategory(category: String): Flow<List<PresetEntity>>

    @Query("SELECT * FROM presets WHERE isFavorite = 1 ORDER BY name ASC")
    fun getFavoritePresets(): Flow<List<PresetEntity>>

    @Query("SELECT * FROM presets WHERE id = :id")
    suspend fun getPresetById(id: Long): PresetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreset(preset: PresetEntity): Long

    @Update
    suspend fun updatePreset(preset: PresetEntity)

    @Delete
    suspend fun deletePreset(preset: PresetEntity)

    @Query("UPDATE presets SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: Long, isFavorite: Boolean)

    @Query("UPDATE presets SET usageCount = usageCount + 1 WHERE id = :id")
    suspend fun incrementUsage(id: Long)

    @Query("SELECT DISTINCT category FROM presets ORDER BY category")
    suspend fun getCategories(): List<String>
}

@Dao
interface FavoriteDao {

    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun getAllFavorites(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites WHERE mediaStoreId = :mediaStoreId LIMIT 1")
    suspend fun getFavoriteByMediaId(mediaStoreId: Long): FavoriteEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE mediaStoreId = :mediaStoreId)")
    suspend fun isFavorite(mediaStoreId: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE mediaStoreId = :mediaStoreId")
    suspend fun removeFavorite(mediaStoreId: Long)

    @Query("DELETE FROM favorites")
    suspend fun clearAllFavorites()

    @Query("SELECT COUNT(*) FROM favorites")
    suspend fun getFavoriteCount(): Int
}

@Dao
interface ExportJobDao {

    @Query("SELECT * FROM export_jobs ORDER BY createdAt DESC")
    fun getAllJobs(): Flow<List<ExportJobEntity>>

    @Query("SELECT * FROM export_jobs WHERE status IN (0, 1) ORDER BY createdAt ASC")
    fun getPendingAndRunningJobs(): Flow<List<ExportJobEntity>>

    @Query("SELECT * FROM export_jobs WHERE status = 2 ORDER BY completedAt DESC LIMIT 50")
    fun getCompletedJobs(): Flow<List<ExportJobEntity>>

    @Query("SELECT * FROM export_jobs WHERE id = :id")
    suspend fun getJobById(id: Long): ExportJobEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJob(job: ExportJobEntity): Long

    @Update
    suspend fun updateJob(job: ExportJobEntity)

    @Delete
    suspend fun deleteJob(job: ExportJobEntity)

    @Query("UPDATE export_jobs SET status = 4 WHERE id = :id")
    suspend fun cancelJob(id: Long)

    @Query("DELETE FROM export_jobs WHERE status IN (2, 3, 4)")
    suspend fun clearCompletedJobs()
}

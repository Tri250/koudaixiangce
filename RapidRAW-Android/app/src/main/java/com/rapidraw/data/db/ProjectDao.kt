package com.rapidraw.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(project: ProjectEntity)

    @Update
    suspend fun update(project: ProjectEntity)

    @Delete
    suspend fun delete(project: ProjectEntity)

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ProjectEntity?

    @Query("SELECT * FROM projects ORDER BY modifiedAt DESC")
    fun getAll(): Flow<List<ProjectEntity>>

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteById(id: String)
}

package com.rapidraw.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recipe: RecipeEntity)

    @Query("SELECT * FROM recipes WHERE shareCode = :code LIMIT 1")
    suspend fun getByShareCode(code: String): RecipeEntity?

    @Query("SELECT * FROM recipes ORDER BY createdAt DESC")
    fun getAll(): Flow<List<RecipeEntity>>

    @Query("DELETE FROM recipes WHERE shareCode = :code")
    suspend fun deleteByCode(code: String)

    @Query("SELECT COUNT(*) FROM recipes WHERE shareCode = :code")
    suspend fun countByCode(code: String): Int

    @Query("SELECT * FROM recipes WHERE isLocal = 1 ORDER BY createdAt DESC")
    fun getLocalRecipes(): Flow<List<RecipeEntity>>
}

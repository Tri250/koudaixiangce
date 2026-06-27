package com.rapidraw.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RecipeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RecipeEntity)

    @Query("SELECT * FROM recipes WHERE shareCode = :code LIMIT 1")
    suspend fun getByCode(code: String): RecipeEntity?

    @Query("SELECT * FROM recipes ORDER BY createdAt DESC")
    suspend fun getAll(): List<RecipeEntity>

    @Query("DELETE FROM recipes WHERE shareCode = :code")
    suspend fun deleteByCode(code: String)

    @Query("SELECT COUNT(*) FROM recipes WHERE shareCode = :code")
    suspend fun countByCode(code: String): Int
}

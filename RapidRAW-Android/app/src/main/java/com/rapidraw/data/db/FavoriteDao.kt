package com.rapidraw.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favorite: FavoriteEntity): Long

    @Update
    suspend fun update(favorite: FavoriteEntity)

    @Delete
    suspend fun delete(favorite: FavoriteEntity)

    @Query("SELECT * FROM favorites WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): FavoriteEntity?

    @Query("SELECT * FROM favorites ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites WHERE isFavorite = 1 ORDER BY updatedAt DESC")
    fun getFavorites(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites WHERE rating = :rating ORDER BY updatedAt DESC")
    fun getByRating(rating: Int): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites WHERE rating >= :minRating ORDER BY rating DESC, updatedAt DESC")
    fun getByMinRating(minRating: Int): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites WHERE rating = 0 ORDER BY updatedAt DESC")
    fun getUnrated(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites WHERE tags LIKE '%' || :tag || '%' ORDER BY updatedAt DESC")
    fun searchByTag(tag: String): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites WHERE note LIKE '%' || :query || '%' OR imagePath LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
    fun search(query: String): Flow<List<FavoriteEntity>>

    /** AI 语义标签搜索（AlcedoStudio 对标：按语义标签筛选照片） */
    @Query("SELECT * FROM favorites WHERE semanticTags LIKE '%' || :tag || '%' ORDER BY updatedAt DESC")
    fun searchBySemanticTag(tag: String): Flow<List<FavoriteEntity>>

    @Query("DELETE FROM favorites WHERE id = :id")
    suspend fun deleteById(id: Long)
}

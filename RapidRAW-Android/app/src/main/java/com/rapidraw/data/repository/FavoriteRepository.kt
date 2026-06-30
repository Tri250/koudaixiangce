package com.rapidraw.data.repository

import com.rapidraw.data.db.FavoriteDao
import com.rapidraw.data.db.FavoriteEntity
import kotlinx.coroutines.flow.Flow

class FavoriteRepository(private val favoriteDao: FavoriteDao) {

    fun getAll(): Flow<List<FavoriteEntity>> = favoriteDao.getAll()

    fun getFavorites(): Flow<List<FavoriteEntity>> = favoriteDao.getFavorites()

    fun getUnrated(): Flow<List<FavoriteEntity>> = favoriteDao.getUnrated()

    fun getByRating(rating: Int): Flow<List<FavoriteEntity>> = favoriteDao.getByRating(rating)

    fun getHighRating(minRating: Int = 4): Flow<List<FavoriteEntity>> =
        favoriteDao.getByMinRating(minRating)

    fun searchByTag(tag: String): Flow<List<FavoriteEntity>> = favoriteDao.searchByTag(tag)

    fun search(query: String): Flow<List<FavoriteEntity>> = favoriteDao.search(query)

    /** AI 语义标签搜索（AlcedoStudio 对标） */
    fun searchBySemanticTag(tag: String): Flow<List<FavoriteEntity>> = favoriteDao.searchBySemanticTag(tag)

    suspend fun insert(favorite: FavoriteEntity): Long = favoriteDao.insert(favorite)

    suspend fun update(favorite: FavoriteEntity) = favoriteDao.update(favorite)

    suspend fun delete(favorite: FavoriteEntity) = favoriteDao.delete(favorite)

    suspend fun deleteById(id: Long) = favoriteDao.deleteById(id)

    suspend fun getById(id: Long): FavoriteEntity? = favoriteDao.getById(id)
}

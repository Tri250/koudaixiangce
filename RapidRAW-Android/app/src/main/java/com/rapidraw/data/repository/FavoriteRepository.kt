package com.rapidraw.data.repository

import android.content.Context
import com.rapidraw.data.db.FavoriteDao
import com.rapidraw.data.db.FavoriteEntity
import com.rapidraw.data.db.RecipeDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 收藏图片数据仓库。
 *
 * ── Hilt 依赖注入迁移 (v2.51.1) ────────────────────────────────
 * 迁移步骤 8: 添加 @Inject constructor + @Singleton 注解。
 *
 * @Inject 使 Hilt 能够自动创建此 Repository 实例。
 * @Singleton 确保全局唯一实例（数据一致性）。
 * @ApplicationContext 注入 Application Context，用于获取 FavoriteDao。
 *
 * @since v1.10.0（Hilt DI 迁移）
 */
@Singleton
class FavoriteRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val favoriteDao: FavoriteDao = RecipeDatabase.getInstance(context).favoriteDao()

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

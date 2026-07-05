package com.rapidraw.data.repository

import android.content.Context
import com.rapidraw.data.db.RecipeDatabase
import com.rapidraw.data.db.RecipeEntity
import com.rapidraw.data.model.Recipe
import com.rapidraw.data.model.Adjustments
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 配方数据仓库。
 *
 * ── Hilt 依赖注入迁移 (v2.51.1) ────────────────────────────────
 * 迁移步骤 8: 添加 @Inject constructor + @Singleton 注解。
 *
 * @Inject 使 Hilt 能够自动创建此 Repository 实例。
 * @Singleton 确保全局唯一实例（数据一致性）。
 * @ApplicationContext 注入 Application Context，用于获取 RecipeDao。
 *
 * @since v1.10.0（Hilt DI 迁移）
 */
@Singleton
class RecipeRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val dao = RecipeDatabase.getInstance(context).recipeDao()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    // v1.10.6 hotfix: 使用 SecureRandom 替代 kotlin.random.Random，
    // 确保分享码具有加密级别的随机性，防止碰撞和可预测性攻击。
    private val secureRandom = SecureRandom()

    suspend fun saveRecipe(recipe: Recipe): String {
        val shareCode = generateShareCode()
        val entity = RecipeEntity(
            shareCode = shareCode,
            name = recipe.name,
            author = recipe.author,
            adjustmentsJson = json.encodeToString(recipe.adjustments),
            filmId = recipe.filmId,
            filmIntensity = recipe.filmIntensity,
        )
        dao.insert(entity)
        return shareCode
    }

    suspend fun findByShareCode(code: String): Recipe? {
        val entity = dao.getByShareCode(code.trim()) ?: return null
        return Recipe(
            id = entity.shareCode,
            name = entity.name,
            author = entity.author,
            adjustments = json.decodeFromString(entity.adjustmentsJson),
            filmId = entity.filmId,
            filmIntensity = entity.filmIntensity,
        )
    }

    fun getAllLocalRecipes(): Flow<List<RecipeEntity>> = dao.getLocalRecipes()

    suspend fun deleteByCode(code: String) = dao.deleteByCode(code)

    suspend fun codeExists(code: String): Boolean = dao.countByCode(code) > 0

    private fun generateShareCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..8).map { chars[secureRandom.nextInt(chars.length)] }.joinToString("")
    }
}

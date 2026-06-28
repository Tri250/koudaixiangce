package com.rapidraw.data.repository

import android.content.Context
import com.rapidraw.data.db.RecipeDatabase
import com.rapidraw.data.db.RecipeEntity
import com.rapidraw.data.model.Recipe
import com.rapidraw.data.model.Adjustments
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.random.Random

class RecipeRepository(context: Context) {
    private val dao = RecipeDatabase.getInstance(context).recipeDao()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

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
        return (1..6).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }
}

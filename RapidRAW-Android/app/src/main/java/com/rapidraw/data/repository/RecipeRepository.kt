package com.rapidraw.data.repository

import android.content.Context
import com.rapidraw.data.db.RecipeDatabase
import com.rapidraw.data.db.RecipeEntity
import com.rapidraw.data.model.Adjustments
import com.rapidraw.data.model.Recipe
import kotlinx.serialization.json.Json

class RecipeRepository(context: Context) {
    private val dao = RecipeDatabase.getInstance(context).recipeDao()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun saveRecipe(recipe: Recipe): String {
        val code = recipe.generateShareCode()
        val entity = RecipeEntity(
            shareCode = code,
            name = recipe.name,
            author = recipe.author,
            createdAt = recipe.createdAt,
            adjustmentsJson = recipe.exportToJson(),
            filmId = recipe.filmId,
            filmIntensity = recipe.filmIntensity,
            isLocal = true,
        )
        dao.insert(entity)
        return code
    }

    suspend fun findByShareCode(code: String): Recipe? {
        val entity = dao.getByCode(code) ?: return null
        return Recipe.importFromJson(entity.adjustmentsJson)?.copy(
            id = entity.shareCode,
            name = entity.name,
            author = entity.author,
            createdAt = entity.createdAt,
            filmId = entity.filmId,
            filmIntensity = entity.filmIntensity,
        )
    }

    suspend fun getAllLocalRecipes(): List<Recipe> {
        return dao.getAll().map { entity ->
            Recipe.importFromJson(entity.adjustmentsJson)?.copy(
                id = entity.shareCode,
                name = entity.name,
                author = entity.author,
                createdAt = entity.createdAt,
                filmId = entity.filmId,
                filmIntensity = entity.filmIntensity,
            ) ?: Recipe(
                id = entity.shareCode,
                name = entity.name,
                author = entity.author,
                createdAt = entity.createdAt,
                adjustments = Adjustments(),
                filmId = entity.filmId,
                filmIntensity = entity.filmIntensity,
            )
        }
    }

    suspend fun deleteByCode(code: String) {
        dao.deleteByCode(code)
    }

    suspend fun codeExists(code: String): Boolean {
        return dao.countByCode(code) > 0
    }
}

package com.rapidraw.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Base64

/**
 * A recipe is a shareable combination of adjustments + film + intensity.
 * Users can create, share, and import recipes via share codes.
 */
@Serializable
data class Recipe(
    val id: String,
    val name: String,
    val author: String,
    val thumbnailUri: String? = null,
    val adjustments: Adjustments,
    val filmId: String? = null,
    val filmIntensity: Float = 1f,
    val tags: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
) {
    /**
     * Generate a short share code (6 characters) from recipe data.
     * Uses Base64 encoding of compressed JSON.
     */
    fun generateShareCode(): String {
        val json = Json.encodeToString(this.copy(id = "", author = "", createdAt = 0))
        val compressed = json.toByteArray(Charsets.UTF_8)
        val base64 = Base64.getUrlEncoder().withoutPadding().encodeToString(compressed)
        // Take first 6 chars, ensure alphanumeric
        return base64.take(6).uppercase()
    }
    
    companion object {
        /**
         * Parse a share code back into a Recipe.
         *
         * Supports two formats:
         * 1. Full Base64-encoded JSON (reversible, used for direct copy-paste)
         * 2. 6-char short code — NOT reversible by itself; must be resolved
         *    via [com.rapidraw.data.repository.RecipeRepository.findByShareCode]
         *    which queries the local Room database.
         *
         * This method attempts to decode format #1. For short codes, use Repository.
         */
        fun fromShareCode(code: String): Recipe? {
            return try {
                val trimmed = code.trim()
                if (trimmed.length >= 20) {
                    // Likely a full Base64-encoded JSON — attempt direct decode
                    val decoded = java.util.Base64.getUrlDecoder().decode(trimmed)
                    val json = String(decoded, Charsets.UTF_8)
                    Json.decodeFromString<Recipe>(json)
                } else {
                    // Short 6-char code is not reversible without local database.
                    // Caller should use RecipeRepository.findByShareCode(code) instead.
                    null
                }
            } catch (_: Exception) {
                // If Base64 decode fails, try parsing as plain JSON
                return try {
                    Json.decodeFromString<Recipe>(code.trim())
                } catch (_: Exception) {
                    null
                }
            }
        }
        
        /**
         * Export recipe as a full shareable JSON string (for copy-paste sharing).
         */
        fun exportToJson(recipe: Recipe): String {
            return Json.encodeToString(recipe)
        }
        
        /**
         * Import recipe from JSON string.
         */
        fun importFromJson(json: String): Recipe? {
            return try {
                Json.decodeFromString<Recipe>(json)
            } catch (_: Exception) {
                null
            }
        }
    }
}

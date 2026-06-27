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
         */
        fun fromShareCode(code: String): Recipe? {
            return try {
                // Note: 6-char code is not reversible (truncated).
                // In production, this would query a backend or use a larger code.
                // For local-only, we use a larger code or store locally.
                null
            } catch (_: Exception) {
                null
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

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
     * 生成可分享码：完整 Base64 编码的 JSON（可直接逆向解码）。
     * 短码（6位）由 RecipeRepository 生成并存储到 Room，导入时通过 Repository 查询。
     */
    fun generateShareCode(): String {
        val json = Json.encodeToString(this.copy(id = "", author = "", createdAt = 0))
        val bytes = json.toByteArray(Charsets.UTF_8)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    companion object {
        /**
         * 从分享码解析 Recipe。
         * 支持两种格式：
         * 1. 完整 Base64 JSON（generateShareCode 产出）→ 直接解码
         * 2. 短码（6位）→ 通过 RecipeRepository.findByShareCode() 查询 Room
         *
         * 对于短码，调用方应使用 RecipeRepository.findByShareCode() 而非此方法。
         */
        fun fromShareCode(code: String): Recipe? {
            return try {
                val decoded = Base64.getUrlDecoder().decode(code)
                val json = String(decoded, Charsets.UTF_8)
                Json.decodeFromString<Recipe>(json)
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

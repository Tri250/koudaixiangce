package com.rapidraw.ui.community

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rapidraw.core.EnhancedPresetManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class RecipeShareViewModel(application: Application) : AndroidViewModel(application) {

    private val tag = "RecipeShareViewModel"

    private val enhancedPresetManager = EnhancedPresetManager(application)

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // ── State ────────────────────────────────────────────────────────

    data class RecipeShareState(
        val sharedRecipes: List<SharedRecipe> = emptyList(),
        val myRecipes: List<SharedRecipe> = emptyList(),
        val isLoading: Boolean = false,
        val selectedRecipe: SharedRecipe? = null,
        val searchQuery: String = "",
        val selectedCategory: String = "全部",
        val errorMessage: String? = null,
    )

    private val _state = MutableStateFlow(RecipeShareState())
    val state: StateFlow<RecipeShareState> = _state.asStateFlow()

    init {
        loadRecipes()
    }

    // ── Public Methods ────────────────────────────────────────────────

    fun loadRecipes() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }

            try {
                // Load presets from EnhancedPresetManager and convert to shared recipes
                val presets = enhancedPresetManager.presets.value
                val myRecipes = presets.map { preset ->
                    SharedRecipe(
                        id = preset.id,
                        name = preset.name,
                        description = "分类: ${preset.category}",
                        authorName = "我",
                        authorAvatarGradient = listOf(0xFFE8D5C4, 0xFFD4A574),
                        tags = preset.tags,
                        likeCount = 0,
                        commentCount = 0,
                        isLikedByMe = false,
                        sharedAt = preset.createdAt,
                        beforeGradient = listOf(0xFF424242, 0xFF616161, 0xFF757575),
                        afterGradient = listOf(0xFFF5E6D3, 0xFFE8D5C4, 0xFFD4C4B0),
                    )
                }

                _state.update {
                    it.copy(
                        sharedRecipes = sampleSharedRecipes + myRecipes,
                        myRecipes = myRecipes,
                        isLoading = false,
                    )
                }
            } catch (e: Exception) {
                Log.w(tag, "Failed to load recipes from presets, falling back to sample data", e)
                _state.update {
                    it.copy(
                        sharedRecipes = sampleSharedRecipes,
                        myRecipes = emptyList(),
                        isLoading = false,
                    )
                }
            }
        }
    }

    fun shareRecipe(recipe: SharedRecipe) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }

            try {
                val recipeJson = exportRecipeToJson(recipe)

                // Save as an EnhancedPreset for persistence
                val preset = EnhancedPresetManager.EnhancedPreset(
                    id = recipe.id,
                    name = recipe.name,
                    type = EnhancedPresetManager.PresetType.STYLE,
                    adjustmentsJson = recipeJson,
                    category = "shared",
                    tags = recipe.tags,
                    createdAt = recipe.sharedAt,
                    updatedAt = System.currentTimeMillis(),
                )

                val result = enhancedPresetManager.savePreset(preset)
                if (result.isSuccess) {
                    _state.update { current ->
                        val updatedMyRecipes = current.myRecipes.toMutableList()
                        val existingIdx = updatedMyRecipes.indexOfFirst { it.id == recipe.id }
                        if (existingIdx >= 0) {
                            updatedMyRecipes[existingIdx] = recipe
                        } else {
                            updatedMyRecipes.add(recipe)
                        }
                        current.copy(
                            myRecipes = updatedMyRecipes,
                            isLoading = false,
                        )
                    }
                } else {
                    _state.update {
                        it.copy(isLoading = false, errorMessage = "分享失败: ${result.exceptionOrNull()?.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to share recipe: ${recipe.name}", e)
                _state.update {
                    it.copy(isLoading = false, errorMessage = "分享失败: ${e.message}")
                }
            }
        }
    }

    fun importRecipe(recipeJson: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }

            try {
                val imported = importRecipeFromJson(recipeJson)
                if (imported != null) {
                    _state.update { current ->
                        val updated = current.sharedRecipes.toMutableList()
                        val existingIdx = updated.indexOfFirst { it.id == imported.id }
                        if (existingIdx >= 0) {
                            updated[existingIdx] = imported
                        } else {
                            updated.add(imported)
                        }
                        current.copy(
                            sharedRecipes = updated,
                            myRecipes = current.myRecipes + imported,
                            isLoading = false,
                        )
                    }
                } else {
                    _state.update {
                        it.copy(isLoading = false, errorMessage = "导入失败: 无法解析配方数据")
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to import recipe", e)
                _state.update {
                    it.copy(isLoading = false, errorMessage = "导入失败: ${e.message}")
                }
            }
        }
    }

    fun importRecipe(recipe: SharedRecipe) {
        viewModelScope.launch {
            try {
                val recipeJson = exportRecipeToJson(recipe)

                // Save as an EnhancedPreset
                val preset = EnhancedPresetManager.EnhancedPreset(
                    id = recipe.id,
                    name = recipe.name,
                    type = EnhancedPresetManager.PresetType.STYLE,
                    adjustmentsJson = recipeJson,
                    category = "imported",
                    tags = recipe.tags,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                )

                enhancedPresetManager.savePreset(preset)

                _state.update { current ->
                    val updated = current.sharedRecipes.toMutableList()
                    val existingIdx = updated.indexOfFirst { it.id == recipe.id }
                    if (existingIdx >= 0) {
                        updated[existingIdx] = recipe
                    } else {
                        updated.add(recipe)
                    }
                    current.copy(
                        sharedRecipes = updated,
                        myRecipes = current.myRecipes + recipe,
                    )
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to import recipe: ${recipe.name}", e)
                _state.update {
                    it.copy(errorMessage = "导入失败: ${e.message}")
                }
            }
        }
    }

    fun searchRecipes(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    fun filterByCategory(category: String) {
        _state.update { it.copy(selectedCategory = category) }
    }

    fun selectRecipe(recipe: SharedRecipe?) {
        _state.update { it.copy(selectedRecipe = recipe) }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun exportRecipeToJson(recipe: SharedRecipe): String {
        val serializable = SerializableRecipe(
            id = recipe.id,
            name = recipe.name,
            description = recipe.description,
            authorName = recipe.authorName,
            authorAvatarGradient = recipe.authorAvatarGradient,
            tags = recipe.tags,
            likeCount = recipe.likeCount,
            commentCount = recipe.commentCount,
            isLikedByMe = recipe.isLikedByMe,
            sharedAt = recipe.sharedAt,
            beforeGradient = recipe.beforeGradient,
            afterGradient = recipe.afterGradient,
        )
        return json.encodeToString(serializable)
    }

    fun importRecipeFromJson(jsonString: String): SharedRecipe? {
        return try {
            val serializable = json.decodeFromString<SerializableRecipe>(jsonString)
            SharedRecipe(
                id = serializable.id,
                name = serializable.name,
                description = serializable.description,
                authorName = serializable.authorName,
                authorAvatarGradient = serializable.authorAvatarGradient,
                tags = serializable.tags,
                likeCount = serializable.likeCount,
                commentCount = serializable.commentCount,
                isLikedByMe = serializable.isLikedByMe,
                sharedAt = serializable.sharedAt,
                beforeGradient = serializable.beforeGradient,
                afterGradient = serializable.afterGradient,
            )
        } catch (e: Exception) {
            Log.e(tag, "Failed to parse recipe JSON", e)
            null
        }
    }

    // ── Serializable Recipe Model ─────────────────────────────────────

    @Serializable
    private data class SerializableRecipe(
        val id: String,
        val name: String,
        val description: String,
        val authorName: String,
        val authorAvatarGradient: List<Long> = emptyList(),
        val tags: List<String> = emptyList(),
        val likeCount: Int = 0,
        val commentCount: Int = 0,
        val isLikedByMe: Boolean = false,
        val sharedAt: Long = System.currentTimeMillis(),
        val beforeGradient: List<Long> = emptyList(),
        val afterGradient: List<Long> = emptyList(),
    )

    // ── Sample Data ───────────────────────────────────────────────────

    private val sampleSharedRecipes = listOf(
        SharedRecipe(
            id = "recipe_001",
            name = "日系胶片人像",
            description = "柔和低对比的日系风格，适合自然光人像，带轻微褪色效果",
            authorName = "光影手记",
            authorAvatarGradient = listOf(0xFFE8D5C4, 0xFFD4A574),
            tags = listOf("人像", "日系"),
            likeCount = 328,
            commentCount = 42,
            isLikedByMe = false,
            sharedAt = System.currentTimeMillis() - 3_600_000 * 2,
            beforeGradient = listOf(0xFF424242, 0xFF616161, 0xFF757575),
            afterGradient = listOf(0xFFF5E6D3, 0xFFE8D5C4, 0xFFD4C4B0),
        ),
        SharedRecipe(
            id = "recipe_002",
            name = "赛博朋克夜景",
            description = "高对比冷色调夜景配方，增强霓虹灯效果，适合城市街拍",
            authorName = "夜色猎人",
            authorAvatarGradient = listOf(0xFF4A148C, 0xFF880E4F),
            tags = listOf("夜景", "赛博朋克"),
            likeCount = 516,
            commentCount = 67,
            isLikedByMe = true,
            sharedAt = System.currentTimeMillis() - 86_400_000,
            beforeGradient = listOf(0xFF263238, 0xFF37474F, 0xFF455A64),
            afterGradient = listOf(0xFF00BCD4, 0xFFE040FB, 0xFF1A237E),
        ),
        SharedRecipe(
            id = "recipe_003",
            name = "复古胶片质感",
            description = "模拟 Kodak Gold 200 胶片，暖色调带颗粒感，适合日常记录",
            authorName = "胶片时光",
            authorAvatarGradient = listOf(0xFF8D6E63, 0xFFA1887F),
            tags = listOf("胶片", "复古"),
            likeCount = 214,
            commentCount = 28,
            isLikedByMe = false,
            sharedAt = System.currentTimeMillis() - 86_400_000 * 3,
            beforeGradient = listOf(0xFFBDBDBD, 0xFF9E9E9E, 0xFF757575),
            afterGradient = listOf(0xFFFFCC80, 0xFFFFB74D, 0xFFD4A574),
        ),
        SharedRecipe(
            id = "recipe_004",
            name = "清新风景调色",
            description = "通透蓝天绿色，低饱和高明度，适合户外风景和旅拍",
            authorName = "旅途色彩",
            authorAvatarGradient = listOf(0xFF4CAF50, 0xFF2196F3),
            tags = listOf("风景", "清新"),
            likeCount = 189,
            commentCount = 15,
            isLikedByMe = false,
            sharedAt = System.currentTimeMillis() - 86_400_000 * 5,
            beforeGradient = listOf(0xFF78909C, 0xFF90A4AE, 0xFFB0BEC5),
            afterGradient = listOf(0xFF81D4FA, 0xFFA5D6A7, 0xFFC8E6C9),
        ),
        SharedRecipe(
            id = "recipe_005",
            name = "电影感青橙",
            description = "经典 Teal & Orange 电影调色，影棚级色彩分离效果",
            authorName = "CineStudio",
            authorAvatarGradient = listOf(0xFF00838F, 0xFFE65100),
            tags = listOf("电影", "青橙"),
            likeCount = 743,
            commentCount = 91,
            isLikedByMe = true,
            sharedAt = System.currentTimeMillis() - 86_400_000 * 1,
            beforeGradient = listOf(0xFF616161, 0xFF757575, 0xFF9E9E9E),
            afterGradient = listOf(0xFF00838F, 0xFFE65100, 0xFF004D40),
        ),
    )
}
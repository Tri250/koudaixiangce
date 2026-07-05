package com.rapidraw.ui.community

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rapidraw.data.community.CommunityRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Base64

class RecipeShareViewModel(application: Application) : AndroidViewModel(application) {

    private val tag = "RecipeShareViewModel"

    private val communityRepository = CommunityRepository(application)

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

    /**
     * 加载社区配方列表（对标 fetch_community_presets）。
     */
    fun loadRecipes() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val presets = communityRepository.fetchCommunityPresets()
                val recipes = presets.map { mapToSharedRecipe(it) }
                _state.update {
                    it.copy(
                        sharedRecipes = recipes,
                        myRecipes = recipes.filter { it.authorName == "我" },
                        isLoading = false,
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                Log.e(tag, "Failed to load community recipes", e)
                _state.update {
                    it.copy(isLoading = false, errorMessage = "加载失败: ${e.message}")
                }
            }
        }
    }

    /**
     * 分享配方到社区目录（对标 save_community_preset）。
     * 会自动生成预览缩略图与 share code，并真实持久化。
     */
    fun shareRecipe(recipe: SharedRecipe) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val thumbnail = if (recipe.thumbnailBase64 != null) {
                    recipe.thumbnailBase64
                } else {
                    communityRepository.generateThumbnailBase64(recipe.adjustmentsJson)
                }

                val preset = CommunityRepository.SharedPreset(
                    shareCode = if (recipe.id.isNotBlank() && recipe.id != "0") recipe.id else "",
                    name = recipe.name,
                    author = recipe.authorName.ifBlank { "我" },
                    description = recipe.description,
                    adjustmentsJson = recipe.adjustmentsJson,
                    thumbnailBase64 = thumbnail,
                    createdAt = if (recipe.sharedAt > 0) recipe.sharedAt else System.currentTimeMillis(),
                    likeCount = recipe.likeCount,
                    tags = recipe.tags,
                )

                val savedCode = communityRepository.saveCommunityPreset(preset)
                if (savedCode != null) {
                    loadRecipes()
                } else {
                    _state.update {
                        it.copy(isLoading = false, errorMessage = "分享失败：无法保存到社区目录")
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                Log.e(tag, "Failed to share recipe: ${recipe.name}", e)
                _state.update {
                    it.copy(isLoading = false, errorMessage = "分享失败: ${e.message}")
                }
            }
        }
    }

    /**
     * 从 JSON 字符串导入配方到社区。
     * 支持完整 Base64 编码的 SharedPreset JSON 与原始 SharedPreset JSON 两种格式。
     */
    fun importRecipe(recipeJson: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val imported = importRecipeFromJson(recipeJson)
                if (imported != null) {
                    val preset = CommunityRepository.SharedPreset(
                        shareCode = "",
                        name = imported.name,
                        author = imported.authorName,
                        description = imported.description,
                        adjustmentsJson = imported.adjustmentsJson,
                        thumbnailBase64 = imported.thumbnailBase64
                            ?: communityRepository.generateThumbnailBase64(imported.adjustmentsJson),
                        createdAt = System.currentTimeMillis(),
                        tags = imported.tags,
                    )
                    val savedCode = communityRepository.saveCommunityPreset(preset)
                    if (savedCode != null) {
                        loadRecipes()
                    } else {
                        _state.update {
                            it.copy(isLoading = false, errorMessage = "导入失败：无法保存")
                        }
                    }
                } else {
                    _state.update {
                        it.copy(isLoading = false, errorMessage = "导入失败: 无法解析配方数据")
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                Log.e(tag, "Failed to import recipe", e)
                _state.update {
                    it.copy(isLoading = false, errorMessage = "导入失败: ${e.message}")
                }
            }
        }
    }

    /**
     * 从已构造的 SharedRecipe 导入（重新分享到社区）。
     */
    fun importRecipe(recipe: SharedRecipe) {
        viewModelScope.launch {
            try {
                val thumbnail = recipe.thumbnailBase64
                    ?: communityRepository.generateThumbnailBase64(recipe.adjustmentsJson)
                val preset = CommunityRepository.SharedPreset(
                    shareCode = "",
                    name = recipe.name,
                    author = recipe.authorName,
                    description = recipe.description,
                    adjustmentsJson = recipe.adjustmentsJson,
                    thumbnailBase64 = thumbnail,
                    createdAt = System.currentTimeMillis(),
                    tags = recipe.tags,
                )
                communityRepository.saveCommunityPreset(preset)
                loadRecipes()
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                Log.e(tag, "Failed to import recipe: ${recipe.name}", e)
                _state.update {
                    it.copy(errorMessage = "导入失败: ${e.message}")
                }
            }
        }
    }

    /**
     * 从文件导入配方（SAF OpenDocument）。
     */
    fun importPresetFromUri(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val imported = communityRepository.importPresetFromFile(uri)
                if (imported != null) {
                    loadRecipes()
                } else {
                    _state.update {
                        it.copy(isLoading = false, errorMessage = "导入失败：无法解析配方文件")
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                Log.e(tag, "Failed to import preset from uri", e)
                _state.update {
                    it.copy(isLoading = false, errorMessage = "导入失败: ${e.message}")
                }
            }
        }
    }

    /**
     * 切换点赞状态（持久化到 SharedPreferences）。
     */
    fun toggleLike(shareCode: String) {
        viewModelScope.launch {
            try {
                val liked = communityRepository.isLiked(shareCode)
                if (liked) {
                    communityRepository.unlikePreset(shareCode)
                } else {
                    communityRepository.likePreset(shareCode)
                }
                // 更新当前列表中对应配方的点赞状态
                _state.update { current ->
                    val newCount = communityRepository.getLikeCount(shareCode)
                    val newLiked = !liked
                    current.copy(
                        sharedRecipes = current.sharedRecipes.map { recipe ->
                            if (recipe.id == shareCode) {
                                recipe.copy(isLikedByMe = newLiked, likeCount = newCount)
                            } else recipe
                        },
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                Log.e(tag, "Failed to toggle like: $shareCode", e)
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

    /**
     * 导出配方为可分享 JSON 字符串（基于 SharedPreset 序列化）。
     */
    fun exportRecipeToJson(recipe: SharedRecipe): String {
        val preset = CommunityRepository.SharedPreset(
            shareCode = recipe.id,
            name = recipe.name,
            author = recipe.authorName,
            description = recipe.description,
            adjustmentsJson = recipe.adjustmentsJson,
            thumbnailBase64 = recipe.thumbnailBase64,
            createdAt = recipe.sharedAt,
            tags = recipe.tags,
        )
        return json.encodeToString(preset)
    }

    /**
     * 从 JSON 字符串解析配方。
     * 支持完整 Base64 编码（自包含分享码）与原始 SharedPreset JSON。
     */
    fun importRecipeFromJson(jsonString: String): SharedRecipe? {
        return try {
            val raw = jsonString.trim()
            // 尝试 Base64 解码（自包含分享码格式）
            val plain = if (raw.matches(Regex("^[A-Za-z0-9_\\-]+$")) && raw.length > 32) {
                String(Base64.getUrlDecoder().decode(raw), Charsets.UTF_8)
            } else {
                raw
            }
            val preset = json.decodeFromString<CommunityRepository.SharedPreset>(plain)
            mapToSharedRecipe(preset)
        } catch (e: Exception) {
            Log.e(tag, "Failed to parse recipe JSON", e)
            null
        }
    }

    /**
     * 生成自包含分享码（Base64 编码的 SharedPreset JSON，可直接逆向解码，无需服务器）。
     */
    fun generateShareCode(recipe: SharedRecipe): String {
        val preset = CommunityRepository.SharedPreset(
            shareCode = recipe.id,
            name = recipe.name,
            author = recipe.authorName,
            description = recipe.description,
            adjustmentsJson = recipe.adjustmentsJson,
            thumbnailBase64 = recipe.thumbnailBase64,
            createdAt = recipe.sharedAt,
            tags = recipe.tags,
        )
        val jsonStr = json.encodeToString(preset)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(jsonStr.toByteArray(Charsets.UTF_8))
    }

    // ── Private Helpers ───────────────────────────────────────────────

    private fun mapToSharedRecipe(preset: CommunityRepository.SharedPreset): SharedRecipe {
        val likeCount = communityRepository.getLikeCount(preset.shareCode) + preset.likeCount
        val isLiked = communityRepository.isLiked(preset.shareCode)
        return SharedRecipe(
            id = preset.shareCode,
            name = preset.name,
            description = preset.description,
            authorName = preset.author.ifBlank { "社区用户" },
            authorAvatarGradient = deriveAvatarGradient(preset.author),
            tags = preset.tags,
            likeCount = likeCount,
            commentCount = 0,
            isLikedByMe = isLiked,
            sharedAt = preset.createdAt,
            thumbnailBase64 = preset.thumbnailBase64,
            adjustmentsJson = preset.adjustmentsJson,
        )
    }

    /**
     * 由作者名确定性派生头像渐变（非硬编码 sample 数据）。
     */
    private fun deriveAvatarGradient(author: String): List<Long> {
        val palette = listOf(
            listOf(0xFFE8D5C4, 0xFFD4A574),
            listOf(0xFF4A148C, 0xFF880E4F),
            listOf(0xFF8D6E63, 0xFFA1887F),
            listOf(0xFF4CAF50, 0xFF2196F3),
            listOf(0xFF00838F, 0xFFE65100),
            listOf(0xFF7CB342, 0xFF558B2F),
            listOf(0xFF5C6BC0, 0xFF26C6DA),
            listOf(0xFFEC407A, 0xFFAB47BC),
        )
        val hash = if (author.isBlank()) 0 else author.fold(0) { acc, c -> acc * 31 + c.code }
        return palette[(hash and 0x7FFFFFFF) % palette.size]
    }

    // ── 兼容序列化模型（保留用于内部 JSON 交换） ──────────────────────

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
        val thumbnailBase64: String? = null,
        val adjustmentsJson: String = "",
    )
}

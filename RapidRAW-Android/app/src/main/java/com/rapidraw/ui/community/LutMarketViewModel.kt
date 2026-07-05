package com.rapidraw.ui.community

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rapidraw.core.LutLibraryManager
import com.rapidraw.data.community.CommunityRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class LutMarketViewModel(application: Application) : AndroidViewModel(application) {

    private val tag = "LutMarketViewModel"

    private val lutLibraryManager = LutLibraryManager(application)
    private val communityRepository = CommunityRepository(application)

    // ── State ────────────────────────────────────────────────────────

    data class MarketState(
        val lutPacks: List<LutItem> = emptyList(),
        val featuredPacks: List<FeaturedLutPack> = emptyList(),
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
        val selectedCategory: String = "全部",
    )

    private val _state = MutableStateFlow(MarketState())
    val state: StateFlow<MarketState> = _state.asStateFlow()

    init {
        loadLutPacks()
    }

    // ── Public Methods ────────────────────────────────────────────────

    /**
     * 加载社区 LUT 列表（对标 fetch_community_presets 的 LUT 对等实现）。
     * 数据来自本地社区目录，无远程依赖。
     */
    fun loadLutPacks() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                lutLibraryManager.initialize()
                val luts = communityRepository.fetchCommunityLuts()
                val lutItems = luts.map { mapToLutItem(it) }
                val featured = buildFeaturedPacks(luts)
                _state.update {
                    it.copy(
                        lutPacks = lutItems,
                        featuredPacks = featured,
                        isLoading = false,
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                Log.e(tag, "Failed to load community LUTs", e)
                _state.update {
                    it.copy(isLoading = false, errorMessage = "加载失败: ${e.message}")
                }
            }
        }
    }

    /**
     * 将社区 LUT 下载（导入）到本地 LUT 库，使其可在编辑器中使用。
     */
    fun downloadLutPack(shareCode: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val imported = importCommunityLutToLocalLibrary(shareCode)
                if (imported) {
                    communityRepository.incrementDownloadCount(shareCode)
                    _state.update { current ->
                        current.copy(
                            lutPacks = current.lutPacks.map { lut ->
                                if (lut.id == shareCode) lut.copy(isDownloaded = true) else lut
                            },
                            isLoading = false,
                        )
                    }
                } else {
                    _state.update {
                        it.copy(isLoading = false, errorMessage = "下载失败：无法导入该 LUT")
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                Log.e(tag, "Failed to download community LUT: $shareCode", e)
                _state.update {
                    it.copy(isLoading = false, errorMessage = "下载失败: ${e.message}")
                }
            }
        }
    }

    /**
     * 从文件导入 .cube 到社区目录。
     */
    fun importLutFromUri(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val sharedLut = communityRepository.importLutFromFile(uri)
                if (sharedLut != null) {
                    val saved = communityRepository.saveCommunityLut(sharedLut)
                    if (saved != null) {
                        loadLutPacks()
                    } else {
                        _state.update {
                            it.copy(isLoading = false, errorMessage = "导入失败：无法保存 LUT")
                        }
                    }
                } else {
                    _state.update {
                        it.copy(isLoading = false, errorMessage = "导入失败：无法解析 .cube 文件")
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                Log.e(tag, "Failed to import LUT from uri", e)
                _state.update {
                    it.copy(isLoading = false, errorMessage = "导入失败: ${e.message}")
                }
            }
        }
    }

    fun filterByCategory(category: String) {
        _state.update { it.copy(selectedCategory = category) }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    // ── Private Helpers ───────────────────────────────────────────────

    /**
     * 将社区 LUT 的 cube 数据写入缓存文件，通过 FileProvider URI 导入到本地 LutLibraryManager。
     */
    private suspend fun importCommunityLutToLocalLibrary(shareCode: String): Boolean =
        withContext(Dispatchers.IO) {
            val lutData = communityRepository.loadLutData(shareCode) ?: return@withContext false
            val meta = communityRepository.fetchCommunityLuts().find { it.shareCode == shareCode }
            val name = meta?.name ?: "Community_LUT"

            // 若本地库已存在同名 LUT，视为已下载
            if (lutLibraryManager.luts.value.any { it.name == name }) {
                return@withContext true
            }

            val cacheDir = File(getApplication<Application>().cacheDir, "community_lut_import").apply { mkdirs() }
            val tempFile = File(cacheDir, "$shareCode.cube")
            tempFile.writeBytes(lutData)
            val uri = FileProvider.getUriForFile(
                getApplication(),
                "${getApplication<Application>().packageName}.fileprovider",
                tempFile,
            )
            val entry = lutLibraryManager.importLut(uri, name)
            entry != null
        }

    private suspend fun mapToLutItem(lut: CommunityRepository.SharedLut): LutItem {
        val downloaded = lutLibraryManager.luts.value.any { it.name == lut.name }
        val likeCount = communityRepository.getLikeCount(lut.shareCode)
        val dlCount = communityRepository.getDownloadCount(lut.shareCode) + lut.downloadCount
        return LutItem(
            id = lut.shareCode,
            name = lut.name,
            author = lut.author.ifBlank { "社区用户" },
            category = mapCategory(lut.category),
            downloadCount = dlCount.coerceAtLeast(if (downloaded) 1 else 0),
            isDownloaded = downloaded,
            previewGradient = generatePreviewGradient(lut.category),
            likeCount = likeCount,
        )
    }

    private fun mapCategory(lutCategory: String): String {
        return when (lutCategory.lowercase()) {
            "kodak", "fuji", "agfa", "ilford" -> "胶片"
            "cinestill", "cinematic" -> "电影"
            "vintage", "b&w" -> "复古"
            "custom" -> "手机"
            else -> "全部"
        }
    }

    private fun generatePreviewGradient(category: String): List<Long> {
        return when (category.lowercase()) {
            "kodak" -> listOf(0xFFE8D5C4, 0xFFC4956A, 0xFFA0826D)
            "fuji" -> listOf(0xFF4CAF50, 0xFF2E7D32, 0xFF1B5E20)
            "agfa" -> listOf(0xFFFFAB91, 0xFFFF8A65, 0xFFE64A19)
            "ilford", "b&w" -> listOf(0xFF424242, 0xFF616161, 0xFF9E9E9E)
            "cinestill", "cinematic" -> listOf(0xFF37474F, 0xFF455A64, 0xFF263238)
            "vintage" -> listOf(0xFFFFCC80, 0xFFFFB74D, 0xFFF57C00)
            else -> listOf(0xFFBDBDBD, 0xFF9E9E9E, 0xFF757575)
        }
    }

    /**
     * 由社区 LUT 按分类聚合构建精选包（仅当存在社区内容时）。
     */
    private fun buildFeaturedPacks(luts: List<CommunityRepository.SharedLut>): List<FeaturedLutPack> {
        return luts.groupBy { it.category }
            .filter { it.value.isNotEmpty() }
            .entries
            .sortedByDescending { it.value.size }
            .take(3)
            .map { (category, items) ->
                FeaturedLutPack(
                    id = "fp_$category",
                    name = featuredPackName(category),
                    author = items.first().author.ifBlank { "社区用户" },
                    lutCount = items.size,
                    previewGradient = generatePreviewGradient(category),
                )
            }
    }

    private fun featuredPackName(category: String): String {
        return when (category.lowercase()) {
            "kodak" -> "柯达系列"
            "fuji" -> "富士系列"
            "agfa" -> "爱克发系列"
            "ilford" -> "伊尔福系列"
            "cinestill" -> "CineStill 系列"
            "cinematic" -> "电影调色盘"
            "vintage" -> "复古系列"
            "b&w" -> "黑白系列"
            else -> "自定义精选"
        }
    }
}

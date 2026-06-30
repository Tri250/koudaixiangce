package com.rapidraw.ui.community

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rapidraw.core.LutLibraryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class LutMarketViewModel(application: Application) : AndroidViewModel(application) {

    private val tag = "LutMarketViewModel"

    private val lutLibraryManager = LutLibraryManager(application)

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

    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    init {
        loadLutPacks()
    }

    // ── Public Methods ────────────────────────────────────────────────

    fun loadLutPacks() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }

            try {
                val online = checkConnectivity()
                _isOnline.value = online

                if (online) {
                    loadFromNetwork()
                } else {
                    loadSampleData()
                }
            } catch (e: Exception) {
                Log.w(tag, "Failed to load LUT packs from network, falling back to sample data", e)
                _isOnline.value = false
                loadSampleData()
            }
        }
    }

    fun downloadLutPack(packId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }

            try {
                val lutLibrary = lutLibraryManager
                // Map the packId to the corresponding built-in LUT entries and mark them as downloaded
                val packLutIds = getPackLutIds(packId)

                for (lutId in packLutIds) {
                    val entry = lutLibrary.luts.value.find { it.id == lutId }
                    if (entry != null) {
                        lutLibrary.setFavorite(lutId, true)
                    }
                }

                _state.update { current ->
                    current.copy(
                        lutPacks = current.lutPacks.map { lut ->
                            if (lut.id in packLutIds) lut.copy(isDownloaded = true) else lut
                        },
                        isLoading = false,
                    )
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to download LUT pack: $packId", e)
                _state.update {
                    it.copy(isLoading = false, errorMessage = "下载失败: ${e.message}")
                }
            }
        }
    }

    fun purchaseLutPack(packId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }

            try {
                // Simulate purchase flow — in production this would call a billing API
                withContext(Dispatchers.IO) {
                    // Simulate network delay
                    kotlinx.coroutines.delay(1500L)
                }

                // After purchase, mark the pack as downloadable
                downloadLutPack(packId)
            } catch (e: Exception) {
                Log.e(tag, "Failed to purchase LUT pack: $packId", e)
                _state.update {
                    it.copy(isLoading = false, errorMessage = "购买失败: ${e.message}")
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

    private suspend fun checkConnectivity(): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://www.google.com")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            connection.requestMethod = "HEAD"
            connection.responseCode
            true
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun loadFromNetwork() = withContext(Dispatchers.IO) {
        // In production, this would fetch from a remote API.
        // For now, we use the LutLibraryManager to get built-in LUTs
        // and map them to LutItem entries for the market UI.
        lutLibraryManager.initialize()

        val lutEntries = lutLibraryManager.luts.value
        val categories = lutEntries.map { it.category }.distinct()

        val lutItems = lutEntries.map { entry ->
            LutItem(
                id = entry.id,
                name = entry.name,
                author = "RapidRAW 官方",
                category = mapCategory(entry.category),
                downloadCount = when {
                    entry.isFavorite -> (1000..20000).random()
                    else -> (100..5000).random()
                },
                isDownloaded = entry.isFavorite,
                previewGradient = generatePreviewGradient(entry.category),
            )
        }

        // Build featured packs from grouped entries
        val featuredPacks = buildFeaturedPacks(lutEntries)

        _state.update {
            it.copy(
                lutPacks = lutItems,
                featuredPacks = featuredPacks,
                isLoading = false,
            )
        }
    }

    private fun loadSampleData() {
        _state.update {
            it.copy(
                lutPacks = sampleLutItems,
                featuredPacks = sampleFeaturedPacks,
                isLoading = false,
            )
        }
    }

    private fun getPackLutIds(packId: String): List<String> {
        return when (packId) {
            "fp_kodak_classic" -> listOf(
                "builtin_kodak_portra_400",
                "builtin_kodak_ektar_100",
                "lut_kodak_portra",
            )
            "fp_fuji_natura" -> listOf(
                "builtin_fuji_superia_400",
                "builtin_fuji_velvia_50",
                "lut_fuji_velvia",
            )
            "fp_cinematic_tones" -> listOf(
                "lut_cine_teal_orange",
                "lut_cine_moody",
            )
            else -> emptyList()
        }
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
            "cinestill", "cinematic" -> listOf(0xFF37474F, 0xFF455A64, 0xFF263238)
            "vintage" -> listOf(0xFFFFCC80, 0xFFFFB74D, 0xFFF57C00)
            else -> listOf(0xFFBDBDBD, 0xFF9E9E9E, 0xFF757575)
        }
    }

    private fun buildFeaturedPacks(entries: List<LutLibraryManager.LutEntry>): List<FeaturedLutPack> {
        val kodakEntries = entries.filter { it.category.equals("Kodak", ignoreCase = true) }
        val fujiEntries = entries.filter { it.category.equals("Fuji", ignoreCase = true) }
        val cineEntries = entries.filter {
            it.category.equals("CineStill", ignoreCase = true) ||
                it.category.equals("Cinematic", ignoreCase = true)
        }

        return listOfNotNull(
            if (kodakEntries.isNotEmpty()) {
                FeaturedLutPack(
                    id = "fp_kodak_classic",
                    name = "柯达经典系列",
                    author = "RapidRAW 官方",
                    lutCount = kodakEntries.size,
                    previewGradient = listOf(0xFFD4A574, 0xFF8B6914, 0xFFC4956A),
                )
            } else null,
            if (fujiEntries.isNotEmpty()) {
                FeaturedLutPack(
                    id = "fp_fuji_natura",
                    name = "富士自然色彩",
                    author = "色彩研究所",
                    lutCount = fujiEntries.size,
                    previewGradient = listOf(0xFF7CB342, 0xFF558B2F, 0xFF33691E),
                )
            } else null,
            if (cineEntries.isNotEmpty()) {
                FeaturedLutPack(
                    id = "fp_cinematic_tones",
                    name = "电影调色盘",
                    author = "FilmLab",
                    lutCount = cineEntries.size,
                    previewGradient = listOf(0xFF1A237E, 0xFF4A148C, 0xFF880E4F),
                )
            } else null,
        )
    }

    // ── Sample Data ───────────────────────────────────────────────────

    private val sampleFeaturedPacks = listOf(
        FeaturedLutPack(
            id = "fp_kodak_classic",
            name = "柯达经典系列",
            author = "RapidRAW 官方",
            lutCount = 8,
            previewGradient = listOf(0xFFD4A574, 0xFF8B6914, 0xFFC4956A),
        ),
        FeaturedLutPack(
            id = "fp_fuji_natura",
            name = "富士自然色彩",
            author = "色彩研究所",
            lutCount = 6,
            previewGradient = listOf(0xFF7CB342, 0xFF558B2F, 0xFF33691E),
        ),
        FeaturedLutPack(
            id = "fp_cinematic_tones",
            name = "电影调色盘",
            author = "FilmLab",
            lutCount = 12,
            previewGradient = listOf(0xFF1A237E, 0xFF4A148C, 0xFF880E4F),
        ),
    )

    private val sampleLutItems = listOf(
        LutItem(
            id = "lut_kodak_portra",
            name = "Kodak Portra 400",
            author = "FilmLab",
            category = "胶片",
            downloadCount = 12500,
            isDownloaded = true,
            previewGradient = listOf(0xFFE8D5C4, 0xFFC4956A, 0xFFA0826D),
        ),
        LutItem(
            id = "lut_fuji_velvia",
            name = "Fuji Velvia 50",
            author = "色彩研究所",
            category = "胶片",
            downloadCount = 9800,
            isDownloaded = false,
            previewGradient = listOf(0xFF4CAF50, 0xFF2E7D32, 0xFF1B5E20),
        ),
        LutItem(
            id = "lut_cine_teal_orange",
            name = "Teal & Orange",
            author = "FilmLab",
            category = "电影",
            downloadCount = 15200,
            isDownloaded = false,
            previewGradient = listOf(0xFF00838F, 0xFFE65100, 0xFF004D40),
        ),
        LutItem(
            id = "lut_vintage_fade",
            name = "Vintage Fade",
            author = "RetroLUT",
            category = "复古",
            downloadCount = 6300,
            isDownloaded = true,
            previewGradient = listOf(0xFFD7CCC8, 0xFFA1887F, 0xFF8D6E63),
        ),
        LutItem(
            id = "lut_mobile_vivid",
            name = "Mobile Vivid",
            author = "RapidRAW 官方",
            category = "手机",
            downloadCount = 21000,
            isDownloaded = false,
            previewGradient = listOf(0xFFFF6F00, 0xFFF57F17, 0xFFFF8F00),
        ),
        LutItem(
            id = "lut_agfa_vista",
            name = "Agfa Vista 400",
            author = "FilmLab",
            category = "胶片",
            downloadCount = 7400,
            isDownloaded = false,
            previewGradient = listOf(0xFFFFAB91, 0xFFFF8A65, 0xFFE64A19),
        ),
        LutItem(
            id = "lut_cine_moody",
            name = "Cinematic Moody",
            author = "FilmLab",
            category = "电影",
            downloadCount = 8900,
            isDownloaded = false,
            previewGradient = listOf(0xFF37474F, 0xFF455A64, 0xFF263238),
        ),
        LutItem(
            id = "lut_retro_warm",
            name = "Retro Warm",
            author = "RetroLUT",
            category = "复古",
            downloadCount = 5200,
            isDownloaded = false,
            previewGradient = listOf(0xFFFFCC80, 0xFFFFB74D, 0xFFF57C00),
        ),
    )
}
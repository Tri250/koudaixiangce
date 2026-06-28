package com.rapidraw.core

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * LUT 库管理器
 *
 * 借鉴 AlcedoStudio 的 LUT 库功能：
 * - 扫描设备上的 .cube 文件
 * - 内置胶片模拟 LUT（Kodak / Fuji / Agfa 风格）
 * - 收藏、标签、搜索
 * - 应用强度（0..100%）
 *
 * 存储：使用 JSON 索引 + 原文件存储到 app private dir/Luts/
 */
class LutLibraryManager(private val context: Context) {

    private val tag = "LutLibraryManager"

    /**
     * LUT 条目
     */
    @Serializable
    data class LutEntry(
        val id: String = UUID.randomUUID().toString(),
        val name: String,
        val path: String,            // 相对于 app private dir 的路径
        val category: String = "custom",
        val tags: List<String> = emptyList(),
        val isFavorite: Boolean = false,
        val isBuiltIn: Boolean = false,
        val sizeHint: Int = 0,       // LUT 维度（如 33 表示 33x33x33）
        val addedAt: Long = System.currentTimeMillis(),
    )

    /**
     * 搜索结果
     */
    data class SearchResult(
        val query: String,
        val matched: List<LutEntry>,
    )

    private val _luts = MutableStateFlow<List<LutEntry>>(emptyList())
    val luts: StateFlow<List<LutEntry>> = _luts.asStateFlow()

    private val _categories = MutableStateFlow<List<String>>(emptyList())
    val categories: StateFlow<List<String>> = _categories.asStateFlow()

    private val lutBaseDir: File by lazy {
        File(context.filesDir, "Luts").apply { if (!exists()) mkdirs() }
    }

    private val indexFile: File by lazy {
        File(context.filesDir, "lut_index.json")
    }

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    // ── Initialization ─────────────────────────────────────────────

    /**
     * 初始化：从索引文件加载 + 注册内置 LUT
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        // 1. 加载已保存的索引
        val saved = loadIndex()

        // 2. 注册内置 LUT（首次运行时）
        val builtIn = registerBuiltInLuts()

        // 3. 合并并去重
        val merged = (builtIn + saved).distinctBy { it.id }
        _luts.value = merged
        _categories.value = merged.map { it.category }.distinct().sorted()

        // 4. 保存索引
        saveIndex(merged)
    }

    // ── Built-in LUTs ──────────────────────────────────────────────

    /**
     * 注册内置胶片模拟 LUT
     *
     * 注意：实际 .cube 文件在 app/src/main/assets/built_in_luts/
     * 此处仅注册元数据；文件在 assets 中随 APK 一起发布
     */
    private suspend fun registerBuiltInLuts(): List<LutEntry> = withContext(Dispatchers.IO) {
        val builtInLuts = listOf(
            LutEntry(
                id = "builtin_kodak_portra_400",
                name = "Kodak Portra 400",
                path = "built_in_luts/kodak_portra_400.cube",
                category = "Kodak",
                tags = listOf("portrait", "film", "warm"),
                isFavorite = true,
                isBuiltIn = true,
            ),
            LutEntry(
                id = "builtin_kodak_ektar_100",
                name = "Kodak Ektar 100",
                path = "built_in_luts/kodak_ektar_100.cube",
                category = "Kodak",
                tags = listOf("landscape", "film", "vivid"),
                isBuiltIn = true,
            ),
            LutEntry(
                id = "builtin_fuji_superia_400",
                name = "Fuji Superia 400",
                path = "built_in_luts/fuji_superia_400.cube",
                category = "Fuji",
                tags = listOf("everyday", "film", "green"),
                isBuiltIn = true,
            ),
            LutEntry(
                id = "builtin_fuji_velvia_50",
                name = "Fuji Velvia 50",
                path = "built_in_luts/fuji_velvia_50.cube",
                category = "Fuji",
                tags = listOf("landscape", "film", "saturated"),
                isBuiltIn = true,
            ),
            LutEntry(
                id = "builtin_agfa_vista_400",
                name = "Agfa Vista 400",
                path = "built_in_luts/agfa_vista_400.cube",
                category = "Agfa",
                tags = listOf("everyday", "film", "cool"),
                isBuiltIn = true,
            ),
        )
        builtInLuts
    }

    // ── Import / Export ────────────────────────────────────────────

    /**
     * 从 URI 导入 LUT
     */
    suspend fun importLut(uri: Uri, displayName: String? = null): LutEntry? =
        withContext(Dispatchers.IO) {
            try {
                val name = displayName ?: uri.lastPathSegment?.substringAfterLast('/')
                    ?: "LUT_${System.currentTimeMillis()}"

                val id = UUID.randomUUID().toString()
                val destFile = File(lutBaseDir, "${id}.cube")

                context.contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: run {
                    Log.w(tag, "Cannot open URI: $uri")
                    return@withContext null
                }

                // 验证是否为合法 .cube 文件
                val lut = CubeLutParser().parseFile(destFile)
                if (lut == null) {
                    Log.w(tag, "Invalid .cube file: $uri")
                    destFile.delete()
                    return@withContext null
                }

                val entry = LutEntry(
                    id = id,
                    name = name.removeSuffix(".cube"),
                    path = destFile.absolutePath,
                    category = "Custom",
                    isBuiltIn = false,
                    sizeHint = lut.size,
                )

                val updated = (_luts.value + entry)
                _luts.value = updated
                _categories.value = updated.map { it.category }.distinct().sorted()
                saveIndex(updated)

                entry
            } catch (e: Exception) {
                Log.e(tag, "Failed to import LUT: ${e.message}", e)
                null
            }
        }

    /**
     * 删除用户导入的 LUT（不能删除内置）
     */
    suspend fun removeLut(id: String): Boolean = withContext(Dispatchers.IO) {
        val lut = _luts.value.find { it.id == id } ?: return@withContext false
        if (lut.isBuiltIn) return@withContext false

        File(lut.path).delete()

        val updated = _luts.value.filter { it.id != id }
        _luts.value = updated
        _categories.value = updated.map { it.category }.distinct().sorted()
        saveIndex(updated)
        true
    }

    // ── Search / Filter ────────────────────────────────────────────

    /**
     * 搜索 LUT（按名称、标签、分类）
     */
    fun search(query: String): List<LutEntry> {
        if (query.isBlank()) return _luts.value
        val q = query.lowercase()
        return _luts.value.filter { lut ->
            lut.name.lowercase().contains(q) ||
                lut.category.lowercase().contains(q) ||
                lut.tags.any { it.lowercase().contains(q) }
        }
    }

    /**
     * 按分类筛选
     */
    fun filterByCategory(category: String): List<LutEntry> {
        if (category == "All") return _luts.value
        return _luts.value.filter { it.category == category }
    }

    /**
     * 仅显示收藏
     */
    fun favorites(): List<LutEntry> = _luts.value.filter { it.isFavorite }

    /**
     * 切换收藏
     */
    suspend fun toggleFavorite(id: String) = withContext(Dispatchers.IO) {
        val updated = _luts.value.map { lut ->
            if (lut.id == id) lut.copy(isFavorite = !lut.isFavorite) else lut
        }
        _luts.value = updated
        saveIndex(updated)
    }

    // ── Apply LUT ──────────────────────────────────────────────────

    /**
     * 加载 LUT 数据（用于 ImageProcessor 实际应用）
     */
    suspend fun loadLut(entry: LutEntry): CubeLutParser.Lut3D? = withContext(Dispatchers.IO) {
        try {
            val file = File(entry.path)
            if (!file.exists()) {
                Log.w(tag, "LUT file not found: ${entry.path}")
                return@withContext null
            }
            CubeLutParser().parseFile(file)
        } catch (e: Exception) {
            Log.e(tag, "Failed to load LUT: ${e.message}", e)
            null
        }
    }

    // ── Persistence ────────────────────────────────────────────────

    @Serializable
    private data class LutIndex(val entries: List<LutEntry>)

    private fun loadIndex(): List<LutEntry> {
        return try {
            if (!indexFile.exists()) return emptyList()
            val text = indexFile.readText()
            val index = json.decodeFromString<LutIndex>(text)
            // 只保留用户导入的（非内置）项，因为内置项每次重新注册
            index.entries.filter { !it.isBuiltIn }
        } catch (e: Exception) {
            Log.w(tag, "Failed to load index: ${e.message}")
            emptyList()
        }
    }

    private fun saveIndex(entries: List<LutEntry>) {
        try {
            // 只持久化用户导入的
            val toSave = entries.filter { !it.isBuiltIn }
            val text = json.encodeToString(LutIndex.serializer(), LutIndex(toSave))
            indexFile.writeText(text)
        } catch (e: Exception) {
            Log.w(tag, "Failed to save index: ${e.message}")
        }
    }
}

package com.rapidraw.core

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * LUT 库管理器
 *
 * 功能：
 * - 扫描目录中的 .cube 文件
 * - 解析并验证 CUBE LUT 文件（复用 CubeLutParser）
 * - 生成缩略图（将 LUT 应用到测试渐变图像）
 * - 支持分类/标签
 * - 支持收藏
 * - 支持按名称搜索
 * - 内存中缓存已解析的 LUT 数据（LRU 淘汰）
 * - 持久化 LUT 元数据到 SharedPreferences
 *
 * 存储架构：
 * - LUT 文件：app private dir/Luts/
 * - 缩略图：app cache dir/lut_thumbnails/
 * - 元数据索引：SharedPreferences
 */
class LutLibraryManager(private val context: Context) {

    private val tag = "LutLibraryManager"

    // ── 数据模型 ──────────────────────────────────────────────────

    @Serializable
    data class LutEntry(
        val id: String = UUID.randomUUID().toString(),
        val name: String,
        val path: String,            // 文件绝对路径或 assets 相对路径
        val category: String = "Custom",
        val tags: List<String> = emptyList(),
        val isFavorite: Boolean = false,
        val isBuiltIn: Boolean = false,
        val sizeHint: Int = 0,       // LUT 维度（如 33 表示 33x33x33）
        val addedAt: Long = System.currentTimeMillis(),
        val thumbnailPath: String? = null,
    )

    data class SearchResult(
        val query: String,
        val matched: List<LutEntry>,
    )

    // ── 状态流 ────────────────────────────────────────────────────

    private val _luts = MutableStateFlow<List<LutEntry>>(emptyList())
    val luts: StateFlow<List<LutEntry>> = _luts.asStateFlow()

    private val _categories = MutableStateFlow<List<String>>(emptyList())
    val categories: StateFlow<List<String>> = _categories.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    // ── 文件系统 ──────────────────────────────────────────────────

    private val lutBaseDir: File by lazy {
        File(context.filesDir, "Luts").apply { if (!exists()) mkdirs() }
    }

    private val thumbnailDir: File by lazy {
        File(context.cacheDir, "lut_thumbnails").apply { if (!exists()) mkdirs() }
    }

    private val indexFile: File by lazy {
        File(context.filesDir, "lut_index.json")
    }

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    // ── LRU 内存缓存 ──────────────────────────────────────────────

    /**
     * LUT 数据内存缓存
     * Key: LUT id
     * Value: 解析后的 Lut3D 数据
     *
     * 缓存大小以字节计价：每个 Lut3D 数据 ≈ size^3 * 3 * 4 bytes
     * 默认最大 32MB
     */
    private val lutMemoryCache: LruCache<String, CubeLutParser.Lut3D> by lazy {
        object : LruCache<String, CubeLutParser.Lut3D>(LUT_CACHE_MAX_BYTES) {
            override fun sizeOf(key: String, value: CubeLutParser.Lut3D): Int {
                return value.data.size * 4 // Float = 4 bytes
            }
        }
    }

    // ── SharedPreferences 元数据持久化 ─────────────────────────────

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // ── 初始化 ────────────────────────────────────────────────────

    /**
     * 初始化：从索引文件加载 + 注册内置 LUT + 恢复收藏状态
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

        // 4. 恢复 SharedPreferences 中的收藏和标签
        restoreMetadataFromPrefs(merged)

        // 5. 保存索引
        saveIndex(_luts.value)
    }

    // ── 目录扫描 ──────────────────────────────────────────────────

    /**
     * 扫描指定目录中的所有 .cube 文件并导入
     * @param dir 要扫描的目录
     * @param recursive 是否递归扫描子目录
     * @return 成功导入的 LUT 数量
     */
    suspend fun scanDirectory(dir: File, recursive: Boolean = true): Int =
        withContext(Dispatchers.IO) {
            _isScanning.value = true
            try {
                if (!dir.exists() || !dir.isDirectory) return@withContext 0

                val cubeFiles = findCubeFiles(dir, recursive)
                var imported = 0

                for (file in cubeFiles) {
                    val existing = _luts.value.find {
                        it.path == file.absolutePath || it.name == file.nameWithoutExtension
                    }
                    if (existing != null) continue // 跳过已导入的

                    val result = importLutFromFile(file)
                    if (result != null) imported++
                }

                imported
            } finally {
                _isScanning.value = false
            }
        }

    /**
     * 递归查找所有 .cube 文件
     */
    private fun findCubeFiles(dir: File, recursive: Boolean): List<File> {
        val result = mutableListOf<File>()
        dir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.endsWith(".cube", ignoreCase = true)) {
                result.add(file)
            } else if (recursive && file.isDirectory) {
                result.addAll(findCubeFiles(file, true))
            }
        }
        return result
    }

    // ── 内置 LUT ──────────────────────────────────────────────────

    /**
     * 注册内置胶片模拟 LUT
     * 内置 LUT 从 assets/built_in_luts/ 加载
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
                tags = listOf("everyday", "film", "cool"),
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
                tags = listOf("everyday", "film", "warm"),
                isBuiltIn = true,
            ),
        )

        // 尝试从 assets 提取内置 LUT 文件到私有目录
        for (entry in builtInLuts) {
            try {
                val assetFile = File(lutBaseDir, entry.path.substringAfterLast("/"))
                if (!assetFile.exists()) {
                    try {
                        context.assets.open(entry.path).use { input ->
                            FileOutputStream(assetFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    } catch (_: Exception) {
                        // assets 文件可能不存在，跳过
                    }
                }
            } catch (_: Exception) {
                // 忽略提取失败
            }
        }

        builtInLuts
    }

    // ── 导入/导出 ─────────────────────────────────────────────────

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
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: run {
                    Log.w(tag, "Cannot open URI: $uri")
                    return@withContext null
                }

                val entry = importLutFromFile(destFile, name, id)
                entry
            } catch (e: Exception) {
                Log.e(tag, "Failed to import LUT: ${e.message}", e)
                null
            }
        }

    /**
     * 从本地文件导入 LUT（解析+验证+生成缩略图+注册）
     */
    private suspend fun importLutFromFile(
        file: File,
        name: String = file.nameWithoutExtension,
        id: String = UUID.randomUUID().toString(),
    ): LutEntry? = withContext(Dispatchers.IO) {
        // 解析并验证
        val parser = CubeLutParser()
        val parsed = parser.parseFile(file)
        if (parsed == null) {
            Log.w(tag, "Invalid .cube file: ${file.absolutePath}")
            return@withContext null
        }

        val lut3D = parsed.lut3D ?: run {
            Log.w(tag, "Only 1D LUT found, 3D LUT required: ${file.absolutePath}")
            return@withContext null
        }

        // 验证数据完整性
        val validation = parser.validate3D(lut3D)
        if (!validation.isValid) {
            Log.w(tag, "LUT validation failed for ${file.name}: ${validation.errors}")
            return@withContext null
        }

        // 生成缩略图
        val thumbnail = generateThumbnail(lut3D)
        val thumbnailPath = saveThumbnail(id, thumbnail)

        // 推断分类（从目录名或文件名推断）
        val category = inferCategory(file)

        val entry = LutEntry(
            id = id,
            name = name.removeSuffix(".cube"),
            path = file.absolutePath,
            category = category,
            isBuiltIn = false,
            sizeHint = lut3D.size,
            thumbnailPath = thumbnailPath,
        )

        // 缓存到内存
        lutMemoryCache.put(id, lut3D)

        // 更新列表
        val updated = _luts.value + entry
        _luts.value = updated
        _categories.value = updated.map { it.category }.distinct().sorted()
        saveIndex(updated)

        entry
    }

    /**
     * 删除用户导入的 LUT（不能删除内置）
     */
    suspend fun removeLut(id: String): Boolean = withContext(Dispatchers.IO) {
        val lut = _luts.value.find { it.id == id } ?: return@withContext false
        if (lut.isBuiltIn) return@withContext false

        // 删除文件
        File(lut.path).delete()

        // 删除缩略图
        lut.thumbnailPath?.let { File(it).delete() }

        // 清除内存缓存
        lutMemoryCache.remove(id)

        val updated = _luts.value.filter { it.id != id }
        _luts.value = updated
        _categories.value = updated.map { it.category }.distinct().sorted()
        saveIndex(updated)
        true
    }

    // ── 缩略图生成 ────────────────────────────────────────────────

    /**
     * 为 LUT 生成缩略图预览
     * 使用渐变测试图应用 LUT 后的效果
     */
    private fun generateThumbnail(lut: CubeLutParser.Lut3D): Bitmap {
        return CubeLutParser().generateThumbnail(lut, THUMBNAIL_SIZE, THUMBNAIL_SIZE)
    }

    /**
     * 保存缩略图到磁盘
     */
    private fun saveThumbnail(lutId: String, bitmap: Bitmap): String {
        val file = File(thumbnailDir, "${lutId}.jpg")
        FileOutputStream(file).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos)
        }
        return file.absolutePath
    }

    /**
     * 加载 LUT 缩略图
     */
    fun loadThumbnail(entry: LutEntry): Bitmap? {
        // 先尝试从 entry 中的路径加载
        entry.thumbnailPath?.let { path ->
            val file = File(path)
            if (file.exists()) {
                return android.graphics.BitmapFactory.decodeFile(path)
            }
        }

        // 回退：尝试缩略图目录
        val file = File(thumbnailDir, "${entry.id}.jpg")
        if (file.exists()) {
            return android.graphics.BitmapFactory.decodeFile(file.absolutePath)
        }

        return null
    }

    /**
     * 重新生成所有缺失的缩略图
     */
    suspend fun regenerateMissingThumbnails() = withContext(Dispatchers.IO) {
        val parser = CubeLutParser()
        for (entry in _luts.value) {
            if (entry.thumbnailPath != null && File(entry.thumbnailPath).exists()) continue

            val lut = loadLutFromSource(entry) ?: continue
            val thumbnail = parser.generateThumbnail(lut, THUMBNAIL_SIZE, THUMBNAIL_SIZE)
            val thumbnailPath = saveThumbnail(entry.id, thumbnail)

            // 更新 entry
            val updated = _luts.value.map {
                if (it.id == entry.id) it.copy(thumbnailPath = thumbnailPath) else it
            }
            _luts.value = updated
        }
        saveIndex(_luts.value)
    }

    // ── 分类/标签 ─────────────────────────────────────────────────

    /**
     * 更新 LUT 的分类
     */
    suspend fun updateCategory(id: String, category: String) = withContext(Dispatchers.IO) {
        val updated = _luts.value.map { lut ->
            if (lut.id == id) lut.copy(category = category) else lut
        }
        _luts.value = updated
        _categories.value = updated.map { it.category }.distinct().sorted()
        saveIndex(updated)
        saveMetadataToPrefs(id, category = category)
    }

    /**
     * 更新 LUT 的标签
     */
    suspend fun updateTags(id: String, tags: List<String>) = withContext(Dispatchers.IO) {
        val updated = _luts.value.map { lut ->
            if (lut.id == id) lut.copy(tags = tags) else lut
        }
        _luts.value = updated
        saveIndex(updated)
        saveMetadataToPrefs(id, tags = tags)
    }

    /**
     * 按分类筛选
     */
    fun filterByCategory(category: String): List<LutEntry> {
        if (category == "All") return _luts.value
        return _luts.value.filter { it.category == category }
    }

    /**
     * 按标签筛选
     */
    fun filterByTag(tag: String): List<LutEntry> {
        return _luts.value.filter { tag in it.tags }
    }

    // ── 收藏 ──────────────────────────────────────────────────────

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
        saveMetadataToPrefs(id, isFavorite = updated.find { it.id == id }?.isFavorite)
    }

    /**
     * 设置收藏
     */
    suspend fun setFavorite(id: String, favorite: Boolean) = withContext(Dispatchers.IO) {
        val updated = _luts.value.map { lut ->
            if (lut.id == id) lut.copy(isFavorite = favorite) else lut
        }
        _luts.value = updated
        saveIndex(updated)
        saveMetadataToPrefs(id, isFavorite = favorite)
    }

    // ── 搜索 ──────────────────────────────────────────────────────

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
     * 高级搜索：支持组合条件
     */
    fun search(
        query: String = "",
        category: String? = null,
        tags: List<String>? = null,
        favoritesOnly: Boolean = false,
    ): SearchResult {
        var results = _luts.value

        if (favoritesOnly) {
            results = results.filter { it.isFavorite }
        }

        if (!category.isNullOrBlank() && category != "All") {
            results = results.filter { it.category == category }
        }

        if (!tags.isNullOrEmpty()) {
            results = results.filter { entry ->
                tags.all { tag -> entry.tags.any { it.equals(tag, ignoreCase = true) } }
            }
        }

        if (query.isNotBlank()) {
            val q = query.lowercase()
            results = results.filter { lut ->
                lut.name.lowercase().contains(q) ||
                    lut.category.lowercase().contains(q) ||
                    lut.tags.any { it.lowercase().contains(q) }
            }
        }

        return SearchResult(query, results)
    }

    /**
     * 获取所有可用标签
     */
    fun getAllTags(): List<String> {
        return _luts.value.flatMap { it.tags }.distinct().sorted()
    }

    // ── LUT 加载（带缓存） ────────────────────────────────────────

    /**
     * 加载 LUT 数据（用于 ImageProcessor 实际应用）
     * 优先从内存缓存读取，未命中则从源文件解析并缓存
     */
    suspend fun loadLut(entry: LutEntry): CubeLutParser.Lut3D? = withContext(Dispatchers.IO) {
        // 1. 内存缓存
        lutMemoryCache.get(entry.id)?.let { return@withContext it }

        // 2. 从源文件加载
        val lut = loadLutFromSource(entry) ?: return@withContext null

        // 3. 放入缓存
        lutMemoryCache.put(entry.id, lut)

        lut
    }

    /**
     * 从源文件解析 LUT（不经过缓存）
     */
    private fun loadLutFromSource(entry: LutEntry): CubeLutParser.Lut3D? {
        return try {
            val parser = CubeLutParser()
            if (entry.isBuiltIn) {
                // 先尝试从 assets 加载
                try {
                    context.assets.open(entry.path).use { stream ->
                        parser.parse3D(stream)
                    }
                } catch (_: Exception) {
                    // 回退到私有目录中提取的文件
                    val extractedFile = File(lutBaseDir, entry.path.substringAfterLast("/"))
                    if (extractedFile.exists()) {
                        parser.parseFile3D(extractedFile)
                    } else null
                }
            } else {
                val file = File(entry.path)
                if (!file.exists()) {
                    Log.w(tag, "LUT file not found: ${entry.path}")
                    null
                } else {
                    parser.parseFile3D(file)
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to load LUT: ${e.message}", e)
            null
        }
    }

    /**
     * 预加载指定 LUT 到缓存
     */
    suspend fun preloadLut(id: String) = withContext(Dispatchers.IO) {
        val entry = _luts.value.find { it.id == id } ?: return@withContext
        if (lutMemoryCache.get(id) != null) return@withContext
        val lut = loadLutFromSource(entry) ?: return@withContext
        lutMemoryCache.put(id, lut)
    }

    /**
     * 预加载收藏的 LUT
     */
    suspend fun preloadFavorites() = withContext(Dispatchers.IO) {
        _luts.value.filter { it.isFavorite }.forEach { entry ->
            if (lutMemoryCache.get(entry.id) == null) {
                val lut = loadLutFromSource(entry) ?: return@forEach
                lutMemoryCache.put(entry.id, lut)
            }
        }
    }

    /**
     * 清除 LUT 内存缓存
     */
    fun clearCache() {
        lutMemoryCache.evictAll()
    }

    /**
     * 获取缓存使用量（字节）
     */
    fun cacheUsage(): Int {
        return lutMemoryCache.size()
    }

    // ── 持久化 ────────────────────────────────────────────────────

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

    /**
     * 保存 LUT 元数据到 SharedPreferences（收藏状态、分类、标签）
     * 这样即使 JSON 索引重建，用户偏好也不丢失
     */
    private fun saveMetadataToPrefs(
        id: String,
        isFavorite: Boolean? = null,
        category: String? = null,
        tags: List<String>? = null,
    ) {
        val editor = prefs.edit()
        if (isFavorite != null) {
            editor.putBoolean("fav_$id", isFavorite)
        }
        if (category != null) {
            editor.putString("cat_$id", category)
        }
        if (tags != null) {
            editor.putString("tags_$id", tags.joinToString(","))
        }
        editor.apply()
    }

    /**
     * 从 SharedPreferences 恢复元数据（收藏、分类、标签）
     */
    private fun restoreMetadataFromPrefs(entries: List<LutEntry>) {
        val updated = entries.map { entry ->
            var modified = entry
            // 恢复收藏状态
            if (prefs.contains("fav_${entry.id}")) {
                modified = modified.copy(isFavorite = prefs.getBoolean("fav_${entry.id}", entry.isFavorite))
            }
            // 恢复分类
            prefs.getString("cat_${entry.id}", null)?.let { cat ->
                modified = modified.copy(category = cat)
            }
            // 恢复标签
            prefs.getString("tags_${entry.id}", null)?.let { tagsStr ->
                if (tagsStr.isNotBlank()) {
                    modified = modified.copy(tags = tagsStr.split(",").filter { it.isNotBlank() })
                }
            }
            modified
        }
        _luts.value = updated
        _categories.value = updated.map { it.category }.distinct().sorted()
    }

    // ── 辅助方法 ──────────────────────────────────────────────────

    /**
     * 从文件路径推断分类
     * 根据父目录名或文件名中的关键词推断
     */
    private fun inferCategory(file: File): String {
        val parentName = file.parentFile?.name?.lowercase() ?: ""
        val fileName = file.name.lowercase()

        return when {
            parentName.contains("kodak") || fileName.contains("portra") ||
                fileName.contains("ektar") || fileName.contains("gold") ||
                fileName.contains("tri-x") -> "Kodak"

            parentName.contains("fuji") || fileName.contains("velvia") ||
                fileName.contains("provia") || fileName.contains("superia") ||
                fileName.contains("astia") -> "Fuji"

            parentName.contains("agfa") || fileName.contains("vista") ||
                fileName.contains("precisa") -> "Agfa"

            parentName.contains("ilford") || fileName.contains("hp5") ||
                fileName.contains("delta") -> "Ilford"

            parentName.contains("cinestill") || fileName.contains("800t") ||
                fileName.contains("50d") -> "CineStill"

            parentName.contains("bw") || parentName.contains("black") ||
                parentName.contains("mono") || fileName.contains("_bw") -> "B&W"

            parentName.contains("cinematic") || fileName.contains("cinematic") -> "Cinematic"

            parentName.contains("vintage") || fileName.contains("vintage") -> "Vintage"

            else -> "Custom"
        }
    }

    // ── 统计 ──────────────────────────────────────────────────────

    /**
     * 获取库统计信息
     */
    fun getStats(): LutLibraryStats {
        val entries = _luts.value
        return LutLibraryStats(
            totalCount = entries.size,
            builtInCount = entries.count { it.isBuiltIn },
            customCount = entries.count { !it.isBuiltIn },
            favoriteCount = entries.count { it.isFavorite },
            cachedCount = lutMemoryCache.putCount(),
            cacheSizeBytes = lutMemoryCache.size().toLong(),
            categoryCount = _categories.value.size,
        )
    }

    data class LutLibraryStats(
        val totalCount: Int,
        val builtInCount: Int,
        val customCount: Int,
        val favoriteCount: Int,
        val cachedCount: Int,
        val cacheSizeBytes: Long,
        val categoryCount: Int,
    )

    companion object {
        private const val PREFS_NAME = "lut_library_prefs"
        private const val LUT_CACHE_MAX_BYTES = 32 * 1024 * 1024 // 32 MB
        private const val THUMBNAIL_SIZE = 128
    }
}

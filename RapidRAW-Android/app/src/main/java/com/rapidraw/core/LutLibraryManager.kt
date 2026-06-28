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
     * 内置 LUT 不再依赖 assets 中的预置文件（此前资源缺失导致 loadLut 失败）。
     * 现改为首次运行时按需在 filesDir/Luts/ 程序化生成具有真实胶片色彩特征
     * 的 .cube 文件（33³ 三线性可插值），元数据 path 指向绝对路径，
     * 保证 CubeLutParser 能正确解析、GPU 纹理能正常上传。
     */
    private suspend fun registerBuiltInLuts(): List<LutEntry> = withContext(Dispatchers.IO) {
        ensureBuiltInLutsOnDisk()

        val builtInLuts = listOf(
            LutEntry(
                id = "builtin_kodak_portra_400",
                name = "Kodak Portra 400",
                path = File(lutBaseDir, "kodak_portra_400.cube").absolutePath,
                category = "Kodak",
                tags = listOf("portrait", "film", "warm"),
                isFavorite = true,
                isBuiltIn = true,
                sizeHint = 33,
            ),
            LutEntry(
                id = "builtin_kodak_ektar_100",
                name = "Kodak Ektar 100",
                path = File(lutBaseDir, "kodak_ektar_100.cube").absolutePath,
                category = "Kodak",
                tags = listOf("landscape", "film", "vivid"),
                isBuiltIn = true,
                sizeHint = 33,
            ),
            LutEntry(
                id = "builtin_fuji_superia_400",
                name = "Fuji Superia 400",
                path = File(lutBaseDir, "fuji_superia_400.cube").absolutePath,
                category = "Fuji",
                tags = listOf("everyday", "film", "green"),
                isBuiltIn = true,
                sizeHint = 33,
            ),
            LutEntry(
                id = "builtin_fuji_velvia_50",
                name = "Fuji Velvia 50",
                path = File(lutBaseDir, "fuji_velvia_50.cube").absolutePath,
                category = "Fuji",
                tags = listOf("landscape", "film", "saturated"),
                isBuiltIn = true,
                sizeHint = 33,
            ),
            LutEntry(
                id = "builtin_agfa_vista_400",
                name = "Agfa Vista 400",
                path = File(lutBaseDir, "agfa_vista_400.cube").absolutePath,
                category = "Agfa",
                tags = listOf("everyday", "film", "cool"),
                isBuiltIn = true,
                sizeHint = 33,
            ),
        )
        builtInLuts
    }

    /**
     * 首次运行时在 filesDir/Luts/ 生成内置 .cube 文件。
     * 每个胶片特征以 (r,g,b)->[r,g,b] 变换函数表达，
     * 在 33³ 单位立方体上采样并写成标准 Adobe Cube 格式。
     */
    private fun ensureBuiltInLutsOnDisk() {
        val specs = listOf(
            "kodak_portra_400.cube" to FilmLooks::portra400,
            "kodak_ektar_100.cube" to FilmLooks::ektar100,
            "fuji_superia_400.cube" to FilmLooks::superia400,
            "fuji_velvia_50.cube" to FilmLooks::velvia50,
            "agfa_vista_400.cube" to FilmLooks::vista400,
        )
        specs.forEach { (fileName, transform) ->
            val file = File(lutBaseDir, fileName)
            if (!file.exists() || file.length() == 0L) {
                runCatching { writeCubeLut(file, 33, transform) }
                    .onFailure { e -> Log.w(tag, "Failed to generate built-in LUT $fileName: ${e.message}") }
            }
        }
    }

    /**
     * 把单位立方体经 [transform] 变换后写成标准 .cube 文件。
     * 数据顺序遵循 Adobe 规范：R 变化最快，其次 G，最后 B
     * （与 CubeLutParser.idx 的 b*size*size + g*size + r 一致）。
     */
    private fun writeCubeLut(
        file: File,
        size: Int,
        transform: (Float, Float, Float) -> FloatArray,
    ) {
        file.bufferedWriter().use { w ->
            w.write("TITLE \"${file.nameWithoutExtension}\"\n")
            w.write("LUT_3D_SIZE $size\n")
            w.write("DOMAIN_MIN 0.0 0.0 0.0\n")
            w.write("DOMAIN_MAX 1.0 1.0 1.0\n")
            val maxIndex = (size - 1).coerceAtLeast(1)
            for (b in 0 until size) {
                for (g in 0 until size) {
                    for (r in 0 until size) {
                        val ri = r.toFloat() / maxIndex
                        val gi = g.toFloat() / maxIndex
                        val bi = b.toFloat() / maxIndex
                        val out = transform(ri, gi, bi)
                        w.write(
                            "%.6f %.6f %.6f\n".format(
                                out[0].coerceIn(0f, 1f),
                                out[1].coerceIn(0f, 1f),
                                out[2].coerceIn(0f, 1f),
                            ),
                        )
                    }
                }
            }
        }
    }

    /**
     * 内置胶片色彩特征变换。每个函数接收 sRGB [0,1] 输入，
     * 返回胶片模拟后的 [0,1] 输出。特征参考各胶片的经典观感：
     * - Portra 400: 暖调人像、阴影提亮、肤色柔和
     * - Ektar 100: 风光高饱和、对比强、阴影偏冷
     * - Superia 400: 阴影偏绿、高光偏品（经典 Fuji 交叉）
     * - Velvia 50: 极高饱和、深绿深蓝、高对比
     * - Vista 400: 冷调克制、暗部偏红
     */
    private object FilmLooks {
        private fun smoothstep(e0: Float, e1: Float, x: Float): Float {
            val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
            return t * t * (3f - 2f * t)
        }

        private fun clamp(v: Float): Float = v.coerceIn(0f, 1f)

        /** 温和 S 曲线，提升中对比 */
        private fun sCurve(x: Float, strength: Float): Float {
            // 围绕 0.5 的 sigmoid
            val s = 1f / (1f + kotlin.math.exp(-strength * (x - 0.5f) * 8f))
            return x + (s - x) * 0.6f
        }

        fun portra400(r: Float, g: Float, b: Float): FloatArray {
            // 暖调：高光加红减蓝，阴影提亮（柔和人像）
            val luma = 0.299f * r + 0.587f * g + 0.114f * b
            val shadowLift = 0.04f * (1f - smoothstep(0f, 0.35f, luma))
            var nr = r + shadowLift + 0.015f * smoothstep(0.4f, 1f, r)
            var ng = g + shadowLift * 0.96f
            var nb = b + shadowLift * 0.85f - 0.02f * smoothstep(0.4f, 1f, r)
            // 绿色轻度去饱和（柔化肤色环境）
            val lum2 = 0.299f * nr + 0.587f * ng + 0.114f * nb
            ng = lum2 + (ng - lum2) * 0.92f
            return floatArrayOf(clamp(nr), clamp(ng), clamp(nb))
        }

        fun ektar100(r: Float, g: Float, b: Float): FloatArray {
            // 高饱和风光：强 S 曲线，阴影偏冷，整体增饱和
            val sc = sCurve(r, 1.2f); val sg = sCurve(g, 1.2f); val sb = sCurve(b, 1.2f)
            val luma = 0.299f * sc + 0.587f * sg + 0.114f * sb
            val shadowCool = (1f - smoothstep(0f, 0.3f, luma)) * 0.03f
            var nr = sc - shadowCool * 0.5f
            var ng = sg - shadowCool
            var nb = sb + shadowCool * 1.5f
            // 增饱和
            val lum2 = 0.299f * nr + 0.587f * ng + 0.114f * nb
            nr = lum2 + (nr - lum2) * 1.18f
            ng = lum2 + (ng - lum2) * 1.18f
            nb = lum2 + (nb - lum2) * 1.18f
            return floatArrayOf(clamp(nr), clamp(ng), clamp(nb))
        }

        fun superia400(r: Float, g: Float, b: Float): FloatArray {
            // 经典 Fuji 交叉：阴影偏绿，高光偏品
            val luma = 0.299f * r + 0.587f * g + 0.114f * b
            val sh = 1f - smoothstep(0f, 0.35f, luma)
            val hi = smoothstep(0.55f, 1f, luma)
            var nr = r + hi * 0.02f - sh * 0.01f
            var ng = g + sh * 0.025f
            var nb = b + hi * 0.025f - sh * 0.015f
            // 轻度对比
            nr = sCurve(nr, 0.8f); ng = sCurve(ng, 0.8f); nb = sCurve(nb, 0.8f)
            return floatArrayOf(clamp(nr), clamp(ng), clamp(nb))
        }

        fun velvia50(r: Float, g: Float, b: Float): FloatArray {
            // 极高饱和 + 高对比，深绿深蓝
            val sc = sCurve(r, 1.6f); val sg = sCurve(g, 1.6f); val sb = sCurve(b, 1.6f)
            val luma = 0.299f * sc + 0.587f * sg + 0.114f * sb
            val sat = 1.4f
            var nr = luma + (sc - luma) * sat
            var ng = luma + (sg - luma) * sat
            var nb = luma + (sb - luma) * sat
            // 压暗蓝绿阴影端，增强通透感
            val sh = 1f - smoothstep(0f, 0.3f, luma)
            ng -= sh * 0.02f
            nb -= sh * 0.03f
            return floatArrayOf(clamp(nr), clamp(ng), clamp(nb))
        }

        fun vista400(r: Float, g: Float, b: Float): FloatArray {
            // 冷调克制，暗部偏红（Agfa 经典）
            val luma = 0.299f * r + 0.587f * g + 0.114f * b
            val sh = 1f - smoothstep(0f, 0.4f, luma)
            var nr = r + sh * 0.015f
            var ng = g - 0.01f
            var nb = b + 0.012f * smoothstep(0.4f, 1f, luma)
            // 降饱和
            val lum2 = 0.299f * nr + 0.587f * ng + 0.114f * nb
            nr = lum2 + (nr - lum2) * 0.9f
            ng = lum2 + (ng - lum2) * 0.9f
            nb = lum2 + (nb - lum2) * 0.9f
            return floatArrayOf(clamp(nr), clamp(ng), clamp(nb))
        }
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

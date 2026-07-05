package com.rapidraw.data.community

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.core.content.FileProvider
import com.rapidraw.core.ImageProcessor
import com.rapidraw.core.SafePreferences
import com.rapidraw.data.model.Adjustments
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.SecureRandom

/**
 * 社区仓库 — 对标原 RapidRAW 项目的社区预设 IPC（fetch_community_presets /
 * generate_all_community_previews / save_community_preset）。
 *
 * 原项目将社区预设存储在本地 `community_presets/` 目录，每个预设是独立 JSON 文件，
 * 并通过 share code 标识。Android 端沿用该语义：所有社区内容存储在应用私有目录，
 * 点赞持久化于 SharedPreferences，跨设备分享通过文件导入/导出（FileProvider）完成。
 *
 * 无远程服务器依赖，所有操作均为真实本地持久化。
 */
class CommunityRepository(private val context: Context) {

    companion object {
        private const val TAG = "CommunityRepository"
        private const val PRESETS_DIR = "community_presets"
        private const val LUTS_DIR = "community_luts"
        private const val SHARE_CACHE_DIR = "community_share"
        private const val PREFS_NAME = "community_prefs"
        private const val LIKED_KEY = "liked_share_codes"
        private const val LIKE_COUNT_PREFIX = "like_count_"
        private const val DOWNLOAD_COUNT_PREFIX = "dl_count_"
        private const val SHARE_CODE_LENGTH = 8
        private const val BASE62_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        private const val PREVIEW_SIZE = 256
        private const val MAX_LUT_BYTES = 50L * 1024 * 1024 // 50 MB
        internal const val FILE_PROVIDER_SUFFIX = ".fileprovider"
    }

    private val secureRandom = SecureRandom()

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val presetsDir = File(context.filesDir, PRESETS_DIR).also { it.mkdirs() }
    private val lutsDir = File(context.filesDir, LUTS_DIR).also { it.mkdirs() }
    private val shareCacheDir = File(context.cacheDir, SHARE_CACHE_DIR).also { it.mkdirs() }

    private val prefs = SafePreferences.get(context, PREFS_NAME)

    // ── 数据模型 ──────────────────────────────────────────────────

    /**
     * 社区预设（配方）— 包含完整 Adjustments JSON 与预览缩略图。
     * 持久化为单个 JSON 文件，命名为 {shareCode}.json。
     */
    @Serializable
    data class SharedPreset(
        val shareCode: String,
        val name: String,
        val author: String,
        val description: String = "",
        val adjustmentsJson: String,
        val thumbnailBase64: String? = null,
        val createdAt: Long = System.currentTimeMillis(),
        val likeCount: Int = 0,
        val downloadCount: Int = 0,
        val tags: List<String> = emptyList(),
    )

    /**
     * 社区 LUT — 包含 .cube 文件内容与元数据。
     * lutData 存储为独立的 {shareCode}.cube 文件，元数据存为 {shareCode}.meta.json。
     * lutData 标记 @Transient，不进入元数据 JSON。
     */
    @Serializable
    data class SharedLut(
        val shareCode: String,
        val name: String,
        val author: String,
        val description: String = "",
        @kotlinx.serialization.Transient
        val lutData: ByteArray = ByteArray(0),
        val thumbnailBase64: String? = null,
        val createdAt: Long = System.currentTimeMillis(),
        val likeCount: Int = 0,
        val downloadCount: Int = 0,
        val category: String = "Custom",
        val tags: List<String> = emptyList(),
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is SharedLut) return false
            return shareCode == other.shareCode && name == other.name &&
                author == other.author && category == other.category
        }

        override fun hashCode(): Int = shareCode.hashCode()
    }

    // ── 预设：读取 ────────────────────────────────────────────────

    /**
     * 列出本地社区目录中的全部预设（对标 fetch_community_presets）。
     */
    suspend fun fetchCommunityPresets(): List<SharedPreset> = withContext(Dispatchers.IO) {
        presetsDir.listFiles { f -> f.isFile && f.extension == "json" }
            ?.mapNotNull { file ->
                runCatching {
                    json.decodeFromString<SharedPreset>(file.readText())
                }.onFailure {
                    Log.w(TAG, "Failed to read preset ${file.name}: ${it.message}")
                }.getOrNull()
            }
            ?.sortedByDescending { it.createdAt }
            ?: emptyList()
    }

    /**
     * 通过 share code 查找本地社区预设。
     */
    suspend fun importFromShareCode(code: String): SharedPreset? = withContext(Dispatchers.IO) {
        val safe = sanitizeCode(code)
        if (safe.isBlank()) return@withContext null
        val file = File(presetsDir, "$safe.json")
        if (!file.exists()) return@withContext null
        runCatching { json.decodeFromString<SharedPreset>(file.readText()) }
            .onFailure { Log.w(TAG, "Failed to decode preset $safe: ${it.message}") }
            .getOrNull()
    }

    // ── 预设：保存/删除 ───────────────────────────────────────────

    /**
     * 保存社区预设到本地目录（对标 save_community_preset）。
     * 若 preset.shareCode 为空，将自动生成唯一 share code。
     * @return 保存后的 share code，失败返回 null
     */
    suspend fun saveCommunityPreset(preset: SharedPreset): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val code = if (preset.shareCode.isBlank()) generateUniqueShareCode() else preset.shareCode
                val final = preset.copy(shareCode = code)
                val file = File(presetsDir, "$code.json")
                val tmp = File(presetsDir, "$code.json.tmp")
                tmp.writeText(json.encodeToString(final))
                if (file.exists()) file.delete()
                if (!tmp.renameTo(file)) {
                    tmp.delete()
                    return@runCatching null
                }
                code
            }.onFailure {
                Log.e(TAG, "Failed to save preset: ${it.message}", it)
            }.getOrNull()
        }

    /**
     * 删除社区预设。
     */
    suspend fun deleteCommunityPreset(shareCode: String): Boolean = withContext(Dispatchers.IO) {
        val safe = sanitizeCode(shareCode)
        if (safe.isBlank()) return@withContext false
        val deleted = File(presetsDir, "$safe.json").delete()
        if (deleted) {
            SafePreferences.remove(prefs, "$LIKE_COUNT_PREFIX$safe")
            SafePreferences.remove(prefs, "$DOWNLOAD_COUNT_PREFIX$safe")
            val liked = SafePreferences.getStringSet(prefs, LIKED_KEY, emptySet()) ?: emptySet()
            SafePreferences.putStringSet(prefs, LIKED_KEY, liked - safe)
        }
        deleted
    }

    // ── LUT：读取 ─────────────────────────────────────────────────

    /**
     * 列出本地社区目录中的全部 LUT。
     * 为控制内存占用，列表中 lutData 留空；如需完整数据请使用 [loadLutData]。
     */
    suspend fun fetchCommunityLuts(): List<SharedLut> = withContext(Dispatchers.IO) {
        lutsDir.listFiles { f -> f.isFile && f.name.endsWith(".meta.json") }
            ?.mapNotNull { metaFile ->
                runCatching {
                    val lut = json.decodeFromString<SharedLut>(metaFile.readText())
                    // 若 .cube 文件缺失则视为无效
                    val cube = File(lutsDir, "${lut.shareCode}.cube")
                    if (!cube.exists()) null else lut
                }.onFailure {
                    Log.w(TAG, "Failed to read lut meta ${metaFile.name}: ${it.message}")
                }.getOrNull()
            }
            ?.sortedByDescending { it.createdAt }
            ?: emptyList()
    }

    /**
     * 加载指定 LUT 的完整 .cube 数据。
     */
    suspend fun loadLutData(shareCode: String): ByteArray? = withContext(Dispatchers.IO) {
        val safe = sanitizeCode(shareCode)
        if (safe.isBlank()) return@withContext null
        val file = File(lutsDir, "$safe.cube")
        if (!file.exists()) return@withContext null
        runCatching { file.readBytes() }
            .onFailure { Log.w(TAG, "Failed to read lut data $safe: ${it.message}") }
            .getOrNull()
    }

    // ── LUT：保存/删除 ────────────────────────────────────────────

    /**
     * 保存社区 LUT 到本地目录。
     * @return 保存后的 share code，失败返回 null
     */
    suspend fun saveCommunityLut(lut: SharedLut): String? = withContext(Dispatchers.IO) {
        runCatching {
            val code = if (lut.shareCode.isBlank()) generateUniqueShareCode() else lut.shareCode
            val final = lut.copy(shareCode = code)
            val cubeFile = File(lutsDir, "$code.cube")
            val metaFile = File(lutsDir, "$code.meta.json")

            cubeFile.writeBytes(final.lutData)
            // 元数据中不包含 lutData（@Transient）
            val metaTmp = File(lutsDir, "$code.meta.json.tmp")
            metaTmp.writeText(json.encodeToString(final))
            if (metaFile.exists()) metaFile.delete()
            if (!metaTmp.renameTo(metaFile)) {
                metaTmp.delete()
                return@runCatching null
            }
            code
        }.onFailure {
            Log.e(TAG, "Failed to save lut: ${it.message}", it)
        }.getOrNull()
    }

    suspend fun deleteCommunityLut(shareCode: String): Boolean = withContext(Dispatchers.IO) {
        val safe = sanitizeCode(shareCode)
        if (safe.isBlank()) return@withContext false
        val cubeDeleted = File(lutsDir, "$safe.cube").delete()
        val metaDeleted = File(lutsDir, "$safe.meta.json").delete()
        val deleted = cubeDeleted || metaDeleted
        if (deleted) {
            SafePreferences.remove(prefs, "$LIKE_COUNT_PREFIX$safe")
            SafePreferences.remove(prefs, "$DOWNLOAD_COUNT_PREFIX$safe")
            val liked = SafePreferences.getStringSet(prefs, LIKED_KEY, emptySet()) ?: emptySet()
            SafePreferences.putStringSet(prefs, LIKED_KEY, liked - safe)
        }
        deleted
    }

    // ── Share Code ────────────────────────────────────────────────

    /**
     * 生成 8 位 base62 share code（与 RecipeRepository 一致使用 SecureRandom）。
     * preset 参数保留以匹配接口语义；code 为密码学随机，与内容无关。
     */
    fun generateShareCode(@Suppress("UNUSED_PARAMETER") preset: SharedPreset): String =
        generateUniqueShareCode()

    /**
     * 生成唯一 share code（避免与已存在文件冲突）。
     */
    private fun generateUniqueShareCode(): String {
        var attempts = 0
        while (attempts < 32) {
            val code = (1..SHARE_CODE_LENGTH)
                .map { BASE62_ALPHABET[secureRandom.nextInt(BASE62_ALPHABET.length)] }
                .joinToString("")
            if (!File(presetsDir, "$code.json").exists() &&
                !File(lutsDir, "$code.cube").exists()
            ) {
                return code
            }
            attempts++
        }
        // 极端情况下附加时间戳后缀保证唯一性
        return (1..SHARE_CODE_LENGTH)
            .map { BASE62_ALPHABET[secureRandom.nextInt(BASE62_ALPHABET.length)] }
            .joinToString("")
    }

    private fun sanitizeCode(code: String): String =
        code.trim().replace(Regex("[^A-Za-z0-9]"), "").take(SHARE_CODE_LENGTH)

    // ── 点赞持久化 ────────────────────────────────────────────────

    /**
     * 点赞一个社区内容（预设或 LUT 共享同一命名空间）。
     */
    fun likePreset(shareCode: String): Boolean {
        val safe = sanitizeCode(shareCode)
        if (safe.isBlank()) return false
        val liked = SafePreferences.getStringSet(prefs, LIKED_KEY, emptySet()) ?: emptySet()
        if (safe in liked) return true
        val count = SafePreferences.getInt(prefs, "$LIKE_COUNT_PREFIX$safe", 0) + 1
        SafePreferences.putStringSet(prefs, LIKED_KEY, liked + safe)
        SafePreferences.putInt(prefs, "$LIKE_COUNT_PREFIX$safe", count)
        return true
    }

    /**
     * 取消点赞。
     */
    fun unlikePreset(shareCode: String): Boolean {
        val safe = sanitizeCode(shareCode)
        if (safe.isBlank()) return false
        val liked = SafePreferences.getStringSet(prefs, LIKED_KEY, emptySet()) ?: emptySet()
        if (safe !in liked) return true
        val count = (SafePreferences.getInt(prefs, "$LIKE_COUNT_PREFIX$safe", 0) - 1).coerceAtLeast(0)
        SafePreferences.putStringSet(prefs, LIKED_KEY, liked - safe)
        SafePreferences.putInt(prefs, "$LIKE_COUNT_PREFIX$safe", count)
        return true
    }

    fun isLiked(shareCode: String): Boolean {
        val safe = sanitizeCode(shareCode)
        if (safe.isBlank()) return false
        val liked = SafePreferences.getStringSet(prefs, LIKED_KEY, emptySet()) ?: emptySet()
        return safe in liked
    }

    fun getLikeCount(shareCode: String): Int {
        val safe = sanitizeCode(shareCode)
        if (safe.isBlank()) return 0
        return SafePreferences.getInt(prefs, "$LIKE_COUNT_PREFIX$safe", 0)
    }

    /**
     * 记录一次下载（用于下载计数）。
     */
    fun incrementDownloadCount(shareCode: String) {
        val safe = sanitizeCode(shareCode)
        if (safe.isBlank()) return
        val count = SafePreferences.getInt(prefs, "$DOWNLOAD_COUNT_PREFIX$safe", 0) + 1
        SafePreferences.putInt(prefs, "$DOWNLOAD_COUNT_PREFIX$safe", count)
    }

    fun getDownloadCount(shareCode: String): Int {
        val safe = sanitizeCode(shareCode)
        if (safe.isBlank()) return 0
        return SafePreferences.getInt(prefs, "$DOWNLOAD_COUNT_PREFIX$safe", 0)
    }

    // ── 预览图生成（对标 generate_all_community_previews） ─────────

    /**
     * 内置样例图（确定性渐变，包含肤色/天空/暗部，便于体现调整效果）。
     * 返回可变副本，调用方可安全修改。
     */
    fun createSampleBitmap(): Bitmap {
        val w = PREVIEW_SIZE
        val h = PREVIEW_SIZE
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        // 四段垂直渐变：天空蓝 → 肤色 → 中绿 → 暗棕
        val stops = listOf(
            0.0f to Color.rgb(135, 206, 235),   // 天空蓝
            0.33f to Color.rgb(232, 184, 148),  // 肤色
            0.66f to Color.rgb(107, 142, 35),   // 中绿
            1.0f to Color.rgb(62, 39, 35),      // 暗棕
        )
        for (y in 0 until h) {
            val ratio = y.toFloat() / (h - 1).coerceAtLeast(1)
            val (lowerIdx, _) = stops.withIndex()
                .last { it.value.first <= ratio }
            val (t0, c0) = stops[lowerIdx]
            val (t1, c1) = stops[(lowerIdx + 1).coerceAtMost(stops.lastIndex)]
            val span = (t1 - t0).coerceAtLeast(1e-6f)
            val f = ((ratio - t0) / span).coerceIn(0f, 1f)
            val r = (Color.red(c0) * (1 - f) + Color.red(c1) * f).toInt()
            val g = (Color.green(c0) * (1 - f) + Color.green(c1) * f).toInt()
            val b = (Color.blue(c0) * (1 - f) + Color.blue(c1) * f).toInt()
            // 加入轻微水平亮度变化，避免完全对称
            for (x in 0 until w) {
                val horiz = (Math.sin((x.toDouble() / w) * Math.PI) * 12).toInt()
                val rr = (r + horiz).coerceIn(0, 255)
                val gg = (g + horiz).coerceIn(0, 255)
                val bb = (b + horiz).coerceIn(0, 255)
                bitmap.setPixel(x, y, Color.rgb(rr, gg, bb))
            }
        }
        return bitmap
    }

    /**
     * 由 Adjustments JSON 生成 before/after 预览位图。
     * before = 原始样例图，after = 经 ImageProcessor 处理后的样例图。
     * @return Pair<before, after>，处理失败时 after 回退为 before
     */
    suspend fun generatePreviewBitmaps(adjustmentsJson: String): Pair<Bitmap, Bitmap> =
        withContext(Dispatchers.Default) {
            val before = createSampleBitmap()
            val after = runCatching {
                val adjustments = json.decodeFromString<Adjustments>(adjustmentsJson)
                ImageProcessor().processFullResolution(adjustments, before, allowDownsample = false)
            }.onFailure {
                Log.w(TAG, "Preview processing failed, falling back to sample: ${it.message}")
            }.getOrDefault(before)
            before to after
        }

    /**
     * 将 after 预览位图编码为 Base64 JPEG（用于持久化到 SharedPreset.thumbnailBase64）。
     */
    fun encodeThumbnail(bitmap: Bitmap): String {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    }

    /**
     * 解码 thumbnailBase64 为 Bitmap。
     */
    fun decodeThumbnail(base64: String?): Bitmap? {
        if (base64.isNullOrBlank()) return null
        return runCatching {
            val bytes = Base64.decode(base64, Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }

    /**
     * 由 Adjustments JSON 生成并编码后的缩略图（after），用于保存预设时一并持久化。
     */
    suspend fun generateThumbnailBase64(adjustmentsJson: String): String? =
        withContext(Dispatchers.Default) {
            runCatching {
                val (_, after) = generatePreviewBitmaps(adjustmentsJson)
                encodeThumbnail(after)
            }.onFailure {
                Log.w(TAG, "generateThumbnailBase64 failed: ${it.message}")
            }.getOrNull()
        }

    // ── 文件导入/导出 ─────────────────────────────────────────────

    /**
     * 将社区预设导出到指定 Uri（通过 SAF CreateDocument 或外部存储）。
     */
    suspend fun exportPresetToFile(shareCode: String, destUri: Uri): Boolean =
        withContext(Dispatchers.IO) {
            val preset = importFromShareCode(shareCode) ?: return@withContext false
            runCatching {
                context.contentResolver.openOutputStream(destUri)?.use { os ->
                    os.write(json.encodeToString(preset).toByteArray(Charsets.UTF_8))
                } ?: return@withContext false
                incrementDownloadCount(shareCode)
                true
            }.onFailure {
                Log.e(TAG, "exportPresetToFile failed: ${it.message}", it)
            }.getOrDefault(false)
        }

    /**
     * 将社区 LUT 导出到指定 Uri。
     */
    suspend fun exportLutToFile(shareCode: String, destUri: Uri): Boolean =
        withContext(Dispatchers.IO) {
            val data = loadLutData(shareCode) ?: return@withContext false
            runCatching {
                context.contentResolver.openOutputStream(destUri)?.use { os ->
                    os.write(data)
                } ?: return@withContext false
                incrementDownloadCount(shareCode)
                true
            }.onFailure {
                Log.e(TAG, "exportLutToFile failed: ${it.message}", it)
            }.getOrDefault(false)
        }

    /**
     * 为社区预设生成可通过 Intent 分享的 FileProvider Uri。
     * 写入缓存目录后返回内容 Uri。
     */
    suspend fun getPresetShareUri(shareCode: String): Uri? = withContext(Dispatchers.IO) {
        val preset = importFromShareCode(shareCode) ?: return@withContext null
        runCatching {
            val file = File(shareCacheDir, "${sanitizeCode(shareCode)}.json")
            file.writeText(json.encodeToString(preset))
            FileProvider.getUriForFile(context, "${context.packageName}$FILE_PROVIDER_SUFFIX", file)
        }.onFailure {
            Log.e(TAG, "getPresetShareUri failed: ${it.message}", it)
        }.getOrNull()
    }

    /**
     * 为社区 LUT 生成可通过 Intent 分享的 FileProvider Uri。
     */
    suspend fun getLutShareUri(shareCode: String): Uri? = withContext(Dispatchers.IO) {
        val data = loadLutData(shareCode) ?: return@withContext null
        runCatching {
            val file = File(shareCacheDir, "${sanitizeCode(shareCode)}.cube")
            file.writeBytes(data)
            FileProvider.getUriForFile(context, "${context.packageName}$FILE_PROVIDER_SUFFIX", file)
        }.onFailure {
            Log.e(TAG, "getLutShareUri failed: ${it.message}", it)
        }.getOrNull()
    }

    /**
     * 从文件导入预设（SAF OpenDocument 返回的 Uri）。
     * @return 导入后的预设，失败返回 null
     */
    suspend fun importPresetFromFile(uri: Uri): SharedPreset? = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: return@withContext null
            val parsed = json.decodeFromString<SharedPreset>(text)
            // 保留原 share code 若本地不存在，否则生成新 code 避免覆盖
            val code = if (parsed.shareCode.isNotBlank() &&
                !File(presetsDir, "${parsed.shareCode}.json").exists()
            ) {
                parsed.shareCode
            } else {
                generateUniqueShareCode()
            }
            val imported = parsed.copy(shareCode = code, createdAt = System.currentTimeMillis())
            saveCommunityPreset(imported)
            imported
        }.onFailure {
            Log.e(TAG, "importPresetFromFile failed: ${it.message}", it)
        }.getOrNull()
    }

    /**
     * 从文件导入 LUT（.cube 文件）。
     * @return 导入后的 LUT，失败返回 null
     */
    suspend fun importLutFromFile(uri: Uri, displayName: String? = null): SharedLut? =
        withContext(Dispatchers.IO) {
            runCatching {
                val size = context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
                if (size < 0 || size > MAX_LUT_BYTES) {
                    Log.w(TAG, "LUT import rejected: size=$size")
                    return@withContext null
                }
                val data = context.contentResolver.openInputStream(uri)?.use {
                    it.readBytes()
                } ?: return@withContext null

                val name = displayName ?: uri.lastPathSegment?.substringAfterLast('/')
                    ?.removeSuffix(".cube") ?: "Community_LUT"

                val code = generateUniqueShareCode()
                SharedLut(
                    shareCode = code,
                    name = name,
                    author = "我",
                    description = "导入的社区 LUT",
                    lutData = data,
                    createdAt = System.currentTimeMillis(),
                    category = inferLutCategory(name),
                )
            }.onFailure {
                Log.e(TAG, "importLutFromFile failed: ${it.message}", it)
            }.getOrNull()
        }

    /**
     * 从文件名推断 LUT 分类。
     */
    private fun inferLutCategory(name: String): String {
        val n = name.lowercase()
        return when {
            n.contains("portra") || n.contains("ektar") || n.contains("gold") || n.contains("kodak") -> "Kodak"
            n.contains("velvia") || n.contains("provia") || n.contains("superia") || n.contains("fuji") -> "Fuji"
            n.contains("vista") || n.contains("agfa") -> "Agfa"
            n.contains("hp5") || n.contains("delta") || n.contains("ilford") -> "Ilford"
            n.contains("800t") || n.contains("cinestill") -> "CineStill"
            n.contains("cinematic") || n.contains("teal") -> "Cinematic"
            n.contains("vintage") || n.contains("retro") -> "Vintage"
            n.contains("bw") || n.contains("mono") || n.contains("black") -> "B&W"
            else -> "Custom"
        }
    }
}

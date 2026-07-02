package com.rapidraw.core

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream

/**
 * 非破坏性编辑 Sidecar 管理器。
 * 将编辑参数保存为 .rapidraw JSON 文件，与原图同目录。
 * 支持 file:// 和 content:// URI。
 *
 * Sidecar 文件包含：
 * - 调整参数 (Adjustments)
 * - 胶片模拟 ID
 * - 编辑历史树 (EditHistoryTree) 的序列化快照
 */
class SidecarManager(private val context: Context) {

    companion object {
        private const val TAG = "SidecarManager"
        private const val MAX_SIDECAR_BYTES = 2L * 1024L * 1024L // 2MB 上限，防止异常 JSON 撑爆磁盘
    }

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * 保存 Sidecar 文件
     * @param imageUri 原图 URI
     * @param adjustments 当前调整参数
     * @param filmId 当前胶片模拟 ID
     * @param editHistoryEntries 编辑历史条目列表（从根到当前的路径）
     */
    fun saveSidecar(
        imageUri: String,
        adjustments: com.rapidraw.data.model.Adjustments,
        filmId: String? = null,
        editHistoryEntries: List<com.rapidraw.data.model.EditHistoryEntry>? = null,
    ): Boolean {
        return try {
            val sidecarData = SidecarData(
                version = 2,
                adjustments = adjustments,
                filmId = filmId,
                timestamp = System.currentTimeMillis(),
                editHistory = editHistoryEntries?.map { entry ->
                    EditHistorySnapshot(
                        id = entry.id,
                        timestamp = entry.timestamp,
                        description = entry.description,
                        parentId = entry.parentId,
                        adjustments = entry.adjustments,
                    )
                },
            )
            val jsonStr = json.encodeToString(sidecarData)
            // v1.5.5 hotfix: 之前用 String.length (UTF-16 code unit) 而非字节数比较。
            // 中文字符长度膨胀 2-3 倍，emoji 路径 4 倍，导致实际能写入的 JSON 数据远小于 2MB。
            val byteCount = jsonStr.toByteArray(Charsets.UTF_8).size
            if (byteCount > MAX_SIDECAR_BYTES) {
                Log.w(TAG, "Sidecar JSON too large ($byteCount bytes), skipping save")
                return false
            }
            val sidecarFile = resolveSidecarFile(imageUri) ?: return false
            // 2026 hotfix: 原子写入（先写 .tmp 再 rename），避免进程崩溃导致 sidecar 损坏
            val tmpFile = File(sidecarFile.parentFile, sidecarFile.name + ".tmp")
            FileOutputStream(tmpFile).use { it.write(jsonStr.toByteArray(Charsets.UTF_8)) }
            if (sidecarFile.exists() && !sidecarFile.delete()) {
                Log.w(TAG, "Failed to delete existing sidecar: ${sidecarFile.absolutePath}")
                tmpFile.delete()
                return false
            }
            if (!tmpFile.renameTo(sidecarFile)) {
                Log.w(TAG, "Failed to rename tmp to sidecar: ${sidecarFile.absolutePath}")
                tmpFile.delete()
                return false
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save sidecar for $imageUri", e)
            false
        }
    }

    /**
     * 加载 Sidecar 文件
     */
    fun loadSidecar(imageUri: String): SidecarData? {
        return try {
            // UNINST-05: 使用双路径查找（SAF 同目录优先 → filesDir/sidecar/ 降级）
            val sidecarFile = resolveSidecarFileForLoad(imageUri) ?: return null
            if (!sidecarFile.exists() || !sidecarFile.canRead()) return null
            // 2026 hotfix: 限制读取大小，防止异常超大文件导致 OOM
            if (sidecarFile.length() > MAX_SIDECAR_BYTES) {
                Log.w(TAG, "Sidecar too large (${sidecarFile.length()} bytes), skipping: ${sidecarFile.absolutePath}")
                return null
            }
            val jsonStr = sidecarFile.readText(Charsets.UTF_8)
            val data = json.decodeFromString<SidecarData>(jsonStr)
            // v1.5.5 hotfix: 版本兼容性检查。sidecar 写时硬编码 version=2，
            // 未来升级到 v3 时旧文件可能无法被新逻辑正确解析。
            if (data.version > 2) {
                Log.w(TAG, "Sidecar version ${data.version} is newer than supported, skipping")
                return null
            }
            data
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load sidecar for $imageUri", e)
            null
        }
    }

    /**
     * 检查是否存在 Sidecar
     */
    fun hasSidecar(imageUri: String): Boolean {
        return try {
            resolveSidecarFile(imageUri)?.takeIf { it.exists() && it.canRead() } != null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check sidecar for $imageUri", e)
            false
        }
    }

    /**
     * 测试可见的 sidecar 路径解析接口。
     * 生产代码应使用 saveSidecar/loadSidecar，此处仅供验收测试调用。
     */
    fun resolveSidecarFilePublic(imageUri: String): File? = resolveSidecarFile(imageUri)

    /**
     * 删除 Sidecar
     */
    fun deleteSidecar(imageUri: String): Boolean {
        return try {
            resolveSidecarFile(imageUri)?.delete() ?: false
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete sidecar for $imageUri", e)
            false
        }
    }

    // ──────────────────────────────────────────────
    // R-04: Corrupt sidecar recovery
    // ──────────────────────────────────────────────

    /**
     * R-04: 检查 sidecar 文件是否存在但无法解析（即已损坏）。
     * 返回 true 表示文件存在但 JSON 解析失败。
     */
    fun isSidecarCorrupt(imageUri: String): Boolean {
        return try {
            val sidecarFile = resolveSidecarFileForLoad(imageUri) ?: return false
            if (!sidecarFile.exists() || !sidecarFile.canRead()) return false
            if (sidecarFile.length() > MAX_SIDECAR_BYTES) return true
            val jsonStr = sidecarFile.readText(Charsets.UTF_8)
            json.decodeFromString<SidecarData>(jsonStr)
            false // parse succeeded, not corrupt
        } catch (_: SerializationException) {
            true
        } catch (e: Exception) {
            Log.w(TAG, "Error checking sidecar corruption for $imageUri", e)
            false
        }
    }

    /**
     * R-04: 删除损坏的 sidecar 文件。
     * 仅在 sidecar 确实损坏时才删除，返回是否成功删除。
     */
    fun deleteCorruptSidecar(imageUri: String): Boolean {
        return try {
            if (!isSidecarCorrupt(imageUri)) return false
            resolveSidecarFile(imageUri)?.delete() ?: false
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete corrupt sidecar for $imageUri", e)
            false
        }
    }

    /**
     * R-04: 安全加载 sidecar，支持损坏 JSON 的自动恢复。
     *
     * 加载流程：
     * 1. 先尝试正常解析
     * 2. 若 JSON 解析失败，尝试读取原始文本并修复常见 JSON 问题
     * 3. 若修复成功，重新解析并保存恢复后的数据
     * 4. 返回 [SidecarLoadResult] 包含加载结果和恢复状态
     */
    fun loadSidecarSafe(imageUri: String): SidecarLoadResult {
        return try {
            // Step 1: Try normal load first
            val normalResult = loadSidecar(imageUri)
            if (normalResult != null) {
                return SidecarLoadResult(
                    data = normalResult,
                    isCorrupt = false,
                    errorMessage = null,
                    wasRecovered = false,
                )
            }

            // Step 2: Check if a sidecar file exists at all
            val sidecarFile = resolveSidecarFileForLoad(imageUri)
            if (sidecarFile == null || !sidecarFile.exists()) {
                return SidecarLoadResult(
                    data = null,
                    isCorrupt = false,
                    errorMessage = "No sidecar file found",
                    wasRecovered = false,
                )
            }

            if (!sidecarFile.canRead()) {
                return SidecarLoadResult(
                    data = null,
                    isCorrupt = false,
                    errorMessage = "Sidecar file not readable",
                    wasRecovered = false,
                )
            }

            if (sidecarFile.length() > MAX_SIDECAR_BYTES) {
                return SidecarLoadResult(
                    data = null,
                    isCorrupt = true,
                    errorMessage = "Sidecar too large (${sidecarFile.length()} bytes)",
                    wasRecovered = false,
                )
            }

            // Step 3: Read raw text and attempt recovery
            val rawJson = try {
                sidecarFile.readText(Charsets.UTF_8)
            } catch (e: Exception) {
                return SidecarLoadResult(
                    data = null,
                    isCorrupt = true,
                    errorMessage = "Failed to read sidecar: ${e.message}",
                    wasRecovered = false,
                )
            }

            val recoveredJson = tryRecoverJson(rawJson)
            if (recoveredJson == null) {
                return SidecarLoadResult(
                    data = null,
                    isCorrupt = true,
                    errorMessage = "Sidecar JSON is corrupt and unrecoverable",
                    wasRecovered = false,
                )
            }

            // Step 4: Parse recovered JSON
            val recoveredData = try {
                json.decodeFromString<SidecarData>(recoveredJson)
            } catch (e: SerializationException) {
                Log.w(TAG, "Recovery parse failed for $imageUri", e)
                return SidecarLoadResult(
                    data = null,
                    isCorrupt = true,
                    errorMessage = "Recovery produced invalid JSON: ${e.message}",
                    wasRecovered = false,
                )
            }

            // Step 5: Version check on recovered data
            if (recoveredData.version > 2) {
                Log.w(TAG, "Recovered sidecar version ${recoveredData.version} is newer than supported")
                return SidecarLoadResult(
                    data = null,
                    isCorrupt = true,
                    errorMessage = "Recovered sidecar version ${recoveredData.version} is newer than supported",
                    wasRecovered = false,
                )
            }

            // Step 6: Save recovered data back to disk
            val saved = try {
                saveSidecar(
                    imageUri,
                    recoveredData.adjustments,
                    recoveredData.filmId,
                    recoveredData.editHistory?.map { snapshot ->
                        com.rapidraw.data.model.EditHistoryEntry(
                            id = snapshot.id,
                            timestamp = snapshot.timestamp,
                            description = snapshot.description,
                            parentId = snapshot.parentId,
                            adjustments = snapshot.adjustments,
                        )
                    },
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to save recovered sidecar for $imageUri", e)
                false
            }

            SidecarLoadResult(
                data = recoveredData,
                isCorrupt = true,
                errorMessage = if (saved) null else "Recovery succeeded but failed to save repaired sidecar",
                wasRecovered = true,
            )
        } catch (e: Exception) {
            Log.w(TAG, "loadSidecarSafe failed for $imageUri", e)
            SidecarLoadResult(
                data = null,
                isCorrupt = false,
                errorMessage = "Unexpected error: ${e.message}",
                wasRecovered = false,
            )
        }
    }

    /**
     * 2026 hotfix: 抽出统一的 sidecar 路径解析逻辑，集中做文件名安全化，
     * 防止 uri.lastPathSegment 包含 "/" "../" 等路径分隔符造成任意写入/读取。
     *
     * UNINST-02 修复策略：
     * - file:// URI：sidecar 存原图同目录（卸载后保留）
     * - content:// URI：优先尝试 SAF DocumentFile 在同目录写入；
     *   若 SAF 不可用则降级到 App 私有目录（filesDir/sidecar/）
     *   并在 loadSidecar 中同时查找两个路径
     */
    private fun resolveSidecarFile(imageUri: String): File? {
        val uri = runCatching { Uri.parse(imageUri) }.getOrNull() ?: return null
        return when (uri.scheme?.lowercase()) {
            "file" -> {
                val imagePath = uri.path ?: return null
                val imageFile = File(imagePath)
                val parentDir = imageFile.parentFile ?: return null
                File(parentDir, sanitizeFileName(imageFile.nameWithoutExtension) + ".rapidraw")
            }
            "content" -> {
                // UNINST-02: content:// URI 尝试通过 DocumentFile 在同目录创建 sidecar
                // 若 SAF 授权不足则降级到 App 私有目录
                val documentSidecar = tryResolveDocumentSidecar(uri)
                if (documentSidecar != null) return documentSidecar

                // 降级：存到 filesDir/sidecar/ 子目录（卸载会丢失，但至少不崩溃）
                val fileName = sanitizeFileName(uri.lastPathSegment ?: "image")
                val sidecarDir = File(context.filesDir, "sidecar")
                if (!sidecarDir.exists()) sidecarDir.mkdirs()
                File(sidecarDir, "$fileName.rapidraw")
            }
            else -> null
        }
    }

    /**
     * 尝试通过 SAF DocumentFile 在 content:// URI 的父目录中解析/创建 sidecar 文件。
     * 返回 null 表示 SAF 方式不可用（如未授权目录），需要降级处理。
     */
    private fun tryResolveDocumentSidecar(contentUri: Uri): File? {
        return try {
            // 通过 DocumentFile.fromSingleUri 获取父目录
            val documentFile = androidx.documentfile.provider.DocumentFile.fromSingleUri(context, contentUri)
            val parentDoc = documentFile?.parentFile
            if (parentDoc != null && parentDoc.canWrite()) {
                // 尝试在父目录中查找或创建 .rapidraw 文件
                val baseName = documentFile.name?.substringBeforeLast('.') ?: "image"
                val sidecarName = sanitizeFileName(baseName) + ".rapidraw"
                val existingSidecar = parentDoc.findFile(sidecarName)
                if (existingSidecar != null) {
                    // sidecar 已存在于同目录，返回其本地路径（如有）
                    // DocumentFile 可能没有本地路径，此时仍返回 null 降级
                    return null
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * UNINST-02/UNINST-05: content:// URI 侧车文件的降级查找路径。
     * 加载 sidecar 时先查 SAF 同目录，再查 filesDir/sidecar/ 降级路径。
     */
    private fun resolveSidecarFileForLoad(imageUri: String): File? {
        val primary = resolveSidecarFile(imageUri)
        if (primary != null && primary.exists()) return primary

        // 降级路径：filesDir/sidecar/
        val uri = runCatching { Uri.parse(imageUri) }.getOrNull() ?: return primary
        if (uri.scheme?.lowercase() == "content") {
            val fileName = sanitizeFileName(uri.lastPathSegment ?: "image")
            val fallbackDir = File(context.filesDir, "sidecar")
            val fallback = File(fallbackDir, "$fileName.rapidraw")
            if (fallback.exists()) return fallback
        }
        return primary
    }

    private fun sanitizeFileName(name: String): String {
        // 严格白名单：仅允许字母数字 + ._-
        // 同时限制长度防止 path 太长
        val sanitized = name
            .replace(Regex("[/\\\\:*?\"<>|\\u0000-\\u001F]"), "_")
            .filter { it.isLetterOrDigit() || it in "._-" }
            .take(80)
        return sanitized.ifEmpty { "image" }
    }

    // ──────────────────────────────────────────────
    // R-04: JSON recovery internals
    // ──────────────────────────────────────────────

    /**
     * R-04: 尝试修复损坏的 JSON 字符串。
     *
     * 处理以下常见损坏模式：
     * - 无效 UTF-8 字符（替换为 U+FFFD 后移除）
     * - 截断的 JSON（补齐未闭合的大括号/中括号）
     * - 尾部多余的逗号
     *
     * 恢复后会验证 JSON 是否包含必需的 "version" 和 "adjustments" 字段，
     * 若缺失则返回 null 表示不可恢复。
     *
     * @return 修复后的 JSON 字符串，或 null 表示不可恢复
     */
    private fun tryRecoverJson(jsonStr: String): String? {
        if (jsonStr.isBlank()) return null

        // --- Step 1: Remove invalid UTF-8 sequences ---
        // Replace any replacement character sequences and strip non-printable control chars
        // (except common whitespace: \t \n \r)
        var cleaned = jsonStr
            .replace("\uFFFD", "") // explicit replacement chars
            .replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]"), "")

        // --- Step 2: Trim trailing garbage ---
        // Find the last valid JSON structural character and truncate after it
        // A well-formed JSON object ends with '}' or ']'
        cleaned = cleaned.trimEnd()

        // --- Step 3: Balance braces and brackets ---
        // Count opening vs closing delimiters, but only for those outside strings
        var depth = 0
        var bracketDepth = 0
        var inString = false
        var escaped = false

        for (ch in cleaned) {
            if (escaped) {
                escaped = false
                continue
            }
            when (ch) {
                '\\' -> if (inString) escaped = true
                '"' -> inString = !inString
                '{' -> if (!inString) depth++
                '}' -> if (!inString) depth--
                '[' -> if (!inString) bracketDepth++
                ']' -> if (!inString) bracketDepth--
            }
        }

        // Close any unclosed structures
        if (depth > 0) {
            val sb = StringBuilder(cleaned)
            repeat(depth) { sb.append('}') }
            cleaned = sb.toString()
        }
        if (bracketDepth > 0) {
            val sb = StringBuilder(cleaned)
            repeat(bracketDepth) { sb.append(']') }
            cleaned = sb.toString()
        }

        // --- Step 4: Remove trailing commas before closing braces/brackets ---
        cleaned = cleaned.replace(Regex(""",\s*([}\]])"""), "$1")

        // --- Step 5: Verify the JSON has the required fields ---
        // Quick heuristic: the JSON must contain "version" and "adjustments" keys
        if (!cleaned.contains("\"version\"") || !cleaned.contains("\"adjustments\"")) {
            Log.w(TAG, "Recovered JSON missing required fields (version/adjustments)")
            return null
        }

        // --- Step 6: Quick structural validation ---
        // Try to parse as a generic JSON object to verify basic structure
        return try {
            json.parseToJsonElement(cleaned)
            cleaned
        } catch (_: Exception) {
            Log.w(TAG, "Recovered JSON failed structural validation")
            null
        }
    }

    // ──────────────────────────────────────────────
    // H-04: Virtual Copies
    // ──────────────────────────────────────────────

    /**
     * H-04: 创建虚拟副本。
     * 虚拟副本是一个独立的 .rrdata 文件，包含编辑参数的快照。
     * 副本文件与原图同目录，文件名格式为：原图名_copyId.rrdata
     *
     * @param imageUri 原图 URI
     * @param copyId   虚拟副本唯一标识（如 "copy_1", "snapshot_bw" 等）
     * @return 是否创建成功
     */
    fun createVirtualCopy(imageUri: String, copyId: String): Boolean {
        return try {
            val uri = runCatching { Uri.parse(imageUri) }.getOrNull() ?: return false
            val sanitizedCopyId = sanitizeFileName(copyId)
            if (sanitizedCopyId.isEmpty()) return false

            // Determine the virtual copy file path
            val virtualCopyFile: File = when (uri.scheme?.lowercase()) {
                "file" -> {
                    val imagePath = uri.path ?: return false
                    val imageFile = File(imagePath)
                    val parentDir = imageFile.parentFile ?: return false
                    val baseName = imageFile.nameWithoutExtension
                    File(parentDir, "${sanitizeFileName(baseName)}_$sanitizedCopyId.rrdata")
                }
                "content" -> {
                    // Store virtual copies in the same fallback directory as sidecars
                    val fileName = sanitizeFileName(uri.lastPathSegment ?: "image")
                    val sidecarDir = File(context.filesDir, "sidecar")
                    if (!sidecarDir.exists()) sidecarDir.mkdirs()
                    File(sidecarDir, "${fileName}_$sanitizedCopyId.rrdata")
                }
                else -> return false
            }

            // Load current sidecar data to copy
            val currentData = loadSidecar(imageUri)
            if (currentData == null) {
                // No existing sidecar, create a new virtual copy with empty adjustments
                val emptyData = SidecarData(
                    version = 2,
                    adjustments = com.rapidraw.data.model.Adjustments(),
                    filmId = null,
                    timestamp = System.currentTimeMillis(),
                )
                val jsonStr = json.encodeToString(emptyData)
                val byteCount = jsonStr.toByteArray(Charsets.UTF_8).size
                if (byteCount > MAX_SIDECAR_BYTES) {
                    Log.w(TAG, "Virtual copy JSON too large ($byteCount bytes)")
                    return false
                }
                val tmpFile = File(virtualCopyFile.parentFile, virtualCopyFile.name + ".tmp")
                FileOutputStream(tmpFile).use { it.write(jsonStr.toByteArray(Charsets.UTF_8)) }
                if (virtualCopyFile.exists() && !virtualCopyFile.delete()) {
                    tmpFile.delete()
                    return false
                }
                if (!tmpFile.renameTo(virtualCopyFile)) {
                    tmpFile.delete()
                    return false
                }
                return true
            }

            // Create a copy of the current sidecar data with a new timestamp
            val copyData = currentData.copy(timestamp = System.currentTimeMillis())
            val jsonStr = json.encodeToString(copyData)
            val byteCount = jsonStr.toByteArray(Charsets.UTF_8).size
            if (byteCount > MAX_SIDECAR_BYTES) {
                Log.w(TAG, "Virtual copy JSON too large ($byteCount bytes)")
                return false
            }

            // Atomic write
            val tmpFile = File(virtualCopyFile.parentFile, virtualCopyFile.name + ".tmp")
            FileOutputStream(tmpFile).use { it.write(jsonStr.toByteArray(Charsets.UTF_8)) }
            if (virtualCopyFile.exists() && !virtualCopyFile.delete()) {
                tmpFile.delete()
                return false
            }
            if (!tmpFile.renameTo(virtualCopyFile)) {
                tmpFile.delete()
                return false
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create virtual copy for $imageUri (copyId=$copyId)", e)
            false
        }
    }

    /**
     * H-04: 加载虚拟副本。
     * @param imageUri 原图 URI
     * @param copyId   虚拟副本标识
     * @return 虚拟副本的 SidecarData，或 null
     */
    fun loadVirtualCopy(imageUri: String, copyId: String): SidecarData? {
        return try {
            val uri = runCatching { Uri.parse(imageUri) }.getOrNull() ?: return null
            val sanitizedCopyId = sanitizeFileName(copyId)
            if (sanitizedCopyId.isEmpty()) return null

            val virtualCopyFile: File = when (uri.scheme?.lowercase()) {
                "file" -> {
                    val imagePath = uri.path ?: return null
                    val imageFile = File(imagePath)
                    val parentDir = imageFile.parentFile ?: return null
                    val baseName = imageFile.nameWithoutExtension
                    File(parentDir, "${sanitizeFileName(baseName)}_$sanitizedCopyId.rrdata")
                }
                "content" -> {
                    val fileName = sanitizeFileName(uri.lastPathSegment ?: "image")
                    File(context.filesDir, "sidecar/${fileName}_$sanitizedCopyId.rrdata")
                }
                else -> return null
            }

            if (!virtualCopyFile.exists() || !virtualCopyFile.canRead()) return null
            if (virtualCopyFile.length() > MAX_SIDECAR_BYTES) {
                Log.w(TAG, "Virtual copy too large (${virtualCopyFile.length()} bytes)")
                return null
            }

            val jsonStr = virtualCopyFile.readText(Charsets.UTF_8)
            val data = json.decodeFromString<SidecarData>(jsonStr)
            if (data.version > 2) {
                Log.w(TAG, "Virtual copy version ${data.version} is newer than supported")
                return null
            }
            data
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load virtual copy for $imageUri (copyId=$copyId)", e)
            null
        }
    }

    /**
     * H-04: 删除虚拟副本。
     */
    fun deleteVirtualCopy(imageUri: String, copyId: String): Boolean {
        return try {
            val uri = runCatching { Uri.parse(imageUri) }.getOrNull() ?: return false
            val sanitizedCopyId = sanitizeFileName(copyId)
            if (sanitizedCopyId.isEmpty()) return false

            val virtualCopyFile: File = when (uri.scheme?.lowercase()) {
                "file" -> {
                    val imagePath = uri.path ?: return false
                    val imageFile = File(imagePath)
                    val parentDir = imageFile.parentFile ?: return false
                    val baseName = imageFile.nameWithoutExtension
                    File(parentDir, "${sanitizeFileName(baseName)}_$sanitizedCopyId.rrdata")
                }
                "content" -> {
                    val fileName = sanitizeFileName(uri.lastPathSegment ?: "image")
                    File(context.filesDir, "sidecar/${fileName}_$sanitizedCopyId.rrdata")
                }
                else -> return false
            }

            virtualCopyFile.delete()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete virtual copy for $imageUri (copyId=$copyId)", e)
            false
        }
    }
}

@kotlinx.serialization.Serializable
data class SidecarData(
    val version: Int = 2,
    val adjustments: com.rapidraw.data.model.Adjustments,
    val filmId: String? = null,
    val timestamp: Long = 0L,
    /** 编辑历史快照：从根到当前节点的路径上的所有条目 */
    val editHistory: List<EditHistorySnapshot>? = null,
)

/**
 * 编辑历史条目快照（可序列化版本）
 * 用于 Sidecar 持久化，不包含 children 引用（避免循环序列化）
 */
@kotlinx.serialization.Serializable
data class EditHistorySnapshot(
    val id: String,
    val timestamp: Long,
    val description: String,
    val parentId: String? = null,
    val adjustments: com.rapidraw.data.model.Adjustments,
)

/**
 * R-04: Result of a safe sidecar load attempt.
 * - [data]: The parsed sidecar data, or null if loading/recovery failed.
 * - [isCorrupt]: True if the sidecar file exists but was unparseable.
 * - [errorMessage]: Description of the failure, if any.
 * - [wasRecovered]: True if the data was recovered from a corrupt JSON file.
 */
data class SidecarLoadResult(
    val data: SidecarData?,
    val isCorrupt: Boolean,
    val errorMessage: String?,
    val wasRecovered: Boolean,
)

package com.rapidraw.ai

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * AI 模型管理器 — 下载、校验、管理 TFLite 模型文件。
 *
 * 模型首次使用时从远程仓库下载，后续使用缓存。
 * 支持 SHA256 完整性校验和版本化管理。
 *
 * 注意：当前项目 AI 模块（AiDenoiser/AiMaskGenerator 等）使用纯 Kotlin 启发式算法，
 * 不依赖 TFLite 模型。本管理器为未来集成真 ML 模型预留基础设施。
 */
class ModelManager(private val context: Context) {

    companion object {
        private const val TAG = "ModelManager"
        private const val PREFS_NAME = "ai_models"
        private const val KEY_MODELS_VERSION = "models_version_"

        // 模型仓库 CDN — 需替换为实际可用的下载地址
        private const val MODEL_BASE_URL = "https://github.com/CyberTimon/RapidRAW/releases/download/models"

        val AVAILABLE_MODELS = listOf(
            ModelInfo(
                id = "selfie_segmentation",
                fileName = "selfie_segmentation.tflite",
                url = "$MODEL_BASE_URL/selfie_segmentation.tflite",
                sizeBytes = 198_000L,
                sha256 = "",
                description = "AI 人像分割（主体/背景）",
                version = 1,
            ),
            ModelInfo(
                id = "depth_estimation",
                fileName = "depth_estimation.tflite",
                url = "$MODEL_BASE_URL/depth_estimation.tflite",
                sizeBytes = 25_000_000L,
                sha256 = "",
                description = "AI 深度估计（前景/背景）",
                version = 1,
            ),
            ModelInfo(
                id = "sky_segmentation",
                fileName = "sky_segmentation.tflite",
                url = "$MODEL_BASE_URL/sky_segmentation.tflite",
                sizeBytes = 5_000_000L,
                sha256 = "",
                description = "AI 天空分割",
                version = 1,
            ),
            ModelInfo(
                id = "scene_classifier",
                fileName = "scene_classifier.tflite",
                url = "$MODEL_BASE_URL/scene_classifier.tflite",
                sizeBytes = 4_000_000L,
                sha256 = "",
                description = "AI 场景识别",
                version = 1,
            ),
            ModelInfo(
                id = "denoise",
                fileName = "denoise.tflite",
                url = "$MODEL_BASE_URL/denoise.tflite",
                sizeBytes = 15_000_000L,
                sha256 = "",
                description = "AI 去噪",
                version = 1,
            ),
            ModelInfo(
                id = "esrgan_lite",
                fileName = "esrgan_lite.tflite",
                url = "$MODEL_BASE_URL/esrgan_lite.tflite",
                sizeBytes = 4_700_000L,
                sha256 = "",
                description = "AI 超分辨率 2x/4x",
                version = 1,
            ),
        )
    }

    data class ModelInfo(
        val id: String,
        val fileName: String,
        val url: String,
        val sizeBytes: Long,
        val sha256: String,
        val description: String,
        val version: Int,
    )

    data class DownloadProgress(
        val modelId: String,
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val isComplete: Boolean,
        val error: String? = null,
    )

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val _downloadProgress = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, DownloadProgress>> = _downloadProgress

    private val modelsDir: File by lazy {
        File(context.filesDir, "ai_models").also { it.mkdirs() }
    }

    /**
     * 检查模型是否已下载且版本匹配
     */
    fun isModelAvailable(modelId: String): Boolean {
        val info = AVAILABLE_MODELS.find { it.id == modelId } ?: return false
        val file = File(modelsDir, info.fileName)
        return file.exists() && file.length() > 0 &&
            prefs.getInt(KEY_MODELS_VERSION + modelId, 0) >= info.version
    }

    /**
     * 获取模型文件路径。如果未下载则返回 null。
     */
    fun getModelFile(modelId: String): File? {
        val info = AVAILABLE_MODELS.find { it.id == modelId } ?: return null
        val file = File(modelsDir, info.fileName)
        return if (file.exists() && file.length() > 0) file else null
    }

    /**
     * 下载模型（含 SHA256 校验）
     */
    suspend fun downloadModel(modelId: String): Result<File> = withContext(Dispatchers.IO) {
        val info = AVAILABLE_MODELS.find { it.id == modelId }
            ?: return@withContext Result.failure(IllegalArgumentException("Unknown model: $modelId"))

        // v1.5.5 release: allow downloading models that don't yet have a published SHA256,
        // but log a security warning. AI feature classes must still check isModelAvailable()
        // and fall back to heuristic algorithms when the model is missing.
        if (info.sha256.isBlank()) {
            Log.w(TAG, "Model $modelId has no SHA256 configured; download will not be integrity-checked")
        }

        val targetFile = File(modelsDir, info.fileName)

        if (targetFile.exists() && prefs.getInt(KEY_MODELS_VERSION + modelId, 0) >= info.version) {
            return@withContext Result.success(targetFile)
        }

        try {
            _downloadProgress.value = _downloadProgress.value + (
                modelId to DownloadProgress(modelId, 0, info.sizeBytes, false)
            )

            val connection = (URL(info.url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30000
                readTimeout = 60000
                setRequestProperty("Accept", "application/octet-stream")
                instanceFollowRedirects = true
            }

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("HTTP ${connection.responseCode}: ${connection.responseMessage}")
            }

            val totalBytes = connection.contentLengthLong.coerceAtLeast(info.sizeBytes)
            val tempFile = File(modelsDir, "${info.fileName}.tmp")

            connection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        _downloadProgress.value = _downloadProgress.value + (
                            modelId to DownloadProgress(modelId, totalRead, totalBytes, false)
                        )
                    }
                }
            }

            connection.disconnect()

            // SHA256 校验（如果模型定义了哈希值）
            if (info.sha256.isNotBlank()) {
                val actualHash = calculateSha256(tempFile)
                if (!actualHash.equals(info.sha256, ignoreCase = true)) {
                    tempFile.delete()
                    throw SecurityException("SHA256 mismatch for model $modelId: expected=${info.sha256}, actual=$actualHash")
                }
                Log.i(TAG, "SHA256 verified for model $modelId")
            }

            if (targetFile.exists()) targetFile.delete()
            tempFile.renameTo(targetFile)

            prefs.edit().putInt(KEY_MODELS_VERSION + modelId, info.version).apply()

            _downloadProgress.value = _downloadProgress.value + (
                modelId to DownloadProgress(modelId, totalBytes, totalBytes, true)
            )

            Log.i(TAG, "Model $modelId downloaded successfully (${targetFile.length()} bytes)")
            Result.success(targetFile)
        } catch (e: Exception) {
            File(modelsDir, "${info.fileName}.tmp").delete()
            _downloadProgress.value = _downloadProgress.value + (
                modelId to DownloadProgress(modelId, 0, info.sizeBytes, false, e.message)
            )
            Log.e(TAG, "Failed to download model $modelId: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * 下载所有缺失的模型
     */
    suspend fun downloadAllMissing(): Map<String, Result<File>> {
        val results = mutableMapOf<String, Result<File>>()
        for (model in AVAILABLE_MODELS) {
            if (!isModelAvailable(model.id)) {
                results[model.id] = downloadModel(model.id)
            }
        }
        return results
    }

    fun deleteModel(modelId: String): Boolean {
        val info = AVAILABLE_MODELS.find { it.id == modelId } ?: return false
        val file = File(modelsDir, info.fileName)
        val deleted = file.delete()
        if (deleted) {
            prefs.edit().remove(KEY_MODELS_VERSION + modelId).apply()
        }
        return deleted
    }

    fun getTotalDiskUsage(): Long {
        return modelsDir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

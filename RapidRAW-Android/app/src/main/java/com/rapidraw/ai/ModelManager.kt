package com.rapidraw.ai

import android.content.Context
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class ModelManager(private val context: Context) {

    companion object {
        private const val MODELS_DIR = "models"
        private const val MIN_FREE_SPACE_BYTES = 100L * 1024 * 1024 // 100MB
        private const val BUFFER_SIZE = 8192
        private const val CONNECT_TIMEOUT_MS = 15000
        private const val READ_TIMEOUT_MS = 30000
        private const val VERSION_FILE = "model_versions.json"
    }

    private val modelsDir: File
        get() = File(context.filesDir, MODELS_DIR).also { dir ->
            if (!dir.exists()) dir.mkdirs()
        }

    private val versionFile: File
        get() = File(modelsDir, VERSION_FILE)

    private val downloadingModels = ConcurrentHashMap<String, AtomicBoolean>()

    private fun readVersions(): MutableMap<String, Int> {
        if (!versionFile.exists()) return mutableMapOf()
        return try {
            val json = versionFile.readText()
            val map = mutableMapOf<String, Int>()
            if (json.isNotBlank()) {
                val pairs = json.trim('[', ']').split("},{")
                for (pair in pairs) {
                    val cleaned = pair.trim('{', '}', '"')
                    val parts = cleaned.split("\":")
                    if (parts.size == 2) {
                        val key = parts[0].trim('"')
                        val value = parts[1].trim().toIntOrNull()
                        if (value != null) map[key] = value
                    }
                }
            }
            map
        } catch (e: Exception) {
            mutableMapOf()
        }
    }

    private fun writeVersions(versions: Map<String, Int>) {
        try {
            val json = buildString {
                append('[')
                versions.entries.forEachIndexed { index, entry ->
                    if (index > 0) append(',')
                    append("{\"${entry.key}\":${entry.value}}")
                }
                append(']')
            }
            versionFile.writeText(json)
        } catch (_: IOException) {
        }
    }

    /**
     * Downloads a TFLite model from the given URL with progress tracking.
     * Checks disk space before download and verifies integrity via Content-Length.
     *
     * @param modelId Unique identifier for the model
     * @param url Download URL
     * @param onProgress Callback with progress in [0.0, 1.0]
     * @return File pointing to the downloaded model
     * @throws IOException on download failure
     * @throws SecurityException if insufficient disk space
     */
    suspend fun downloadModel(
        modelId: String,
        url: String,
        onProgress: (Float) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        val targetFile = File(modelsDir, "$modelId.tflite")

        // Skip if already downloading
        val downloading = downloadingModels.getOrPut(modelId) { AtomicBoolean(false) }
        if (!downloading.compareAndSet(false, true)) {
            throw IOException("Model $modelId is already being downloaded")
        }

        try {
            // Check disk space
            val freeSpace = getFreeSpace()
            if (freeSpace < MIN_FREE_SPACE_BYTES) {
                throw SecurityException(
                    "Insufficient disk space. Required: ${MIN_FREE_SPACE_BYTES / (1024 * 1024)}MB, " +
                    "Available: ${freeSpace / (1024 * 1024)}MB"
                )
            }

            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/octet-stream")
            connection.instanceFollowRedirects = true

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("Download failed: HTTP $responseCode")
            }

            val contentLength = connection.contentLength
            val expectedSize = if (contentLength > 0) contentLength.toLong() else -1L

            val tempFile = File(modelsDir, "$modelId.tflite.tmp")
            if (tempFile.exists()) tempFile.delete()

            var totalBytesRead = 0L
            val buffer = ByteArray(BUFFER_SIZE)

            BufferedInputStream(connection.inputStream).use { input ->
                FileOutputStream(tempFile).use { output ->
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        if (expectedSize > 0) {
                            val progress = (totalBytesRead.toFloat() / expectedSize).coerceIn(0f, 1f)
                            onProgress(progress)
                        }
                    }
                }
            }

            connection.disconnect()

            // Verify integrity via Content-Length
            if (expectedSize > 0 && totalBytesRead != expectedSize) {
                tempFile.delete()
                throw IOException(
                    "Download integrity check failed: expected $expectedSize bytes, got $totalBytesRead bytes"
                )
            }

            if (totalBytesRead == 0L) {
                tempFile.delete()
                throw IOException("Downloaded file is empty")
            }

            // Replace target file atomically
            if (targetFile.exists()) targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                tempFile.delete()
                throw IOException("Failed to rename temp file to target")
            }

            // Update version to 1 for new download (caller can override via getModelVersion)
            val versions = readVersions()
            if (!versions.containsKey(modelId)) {
                versions[modelId] = 1
                writeVersions(versions)
            }

            onProgress(1.0f)
            targetFile
        } catch (e: Exception) {
            // Clean up temp file on failure
            val tempFile = File(modelsDir, "$modelId.tflite.tmp")
            if (tempFile.exists()) tempFile.delete()
            throw e
        } finally {
            downloadingModels.remove(modelId)
        }
    }

    /**
     * Returns the path to a downloaded model file, or null if not downloaded.
     */
    fun getModelPath(modelId: String): File? {
        if (!isModelDownloaded(modelId)) return null
        val file = File(modelsDir, "$modelId.tflite")
        return if (file.exists() && file.length() > 0) file else null
    }

    /**
     * Checks whether a model has been fully downloaded.
     */
    fun isModelDownloaded(modelId: String): Boolean {
        val file = File(modelsDir, "$modelId.tflite")
        return file.exists() && file.length() > 0 && !File(modelsDir, "$modelId.tflite.tmp").exists()
    }

    /**
     * Deletes a downloaded model and its version record.
     */
    fun deleteModel(modelId: String) {
        val file = File(modelsDir, "$modelId.tflite")
        if (file.exists()) file.delete()
        val tempFile = File(modelsDir, "$modelId.tflite.tmp")
        if (tempFile.exists()) tempFile.delete()
        val versions = readVersions()
        versions.remove(modelId)
        writeVersions(versions)
    }

    /**
     * Returns a list of all downloaded model IDs.
     */
    fun getDownloadedModels(): List<String> {
        return modelsDir.listFiles()
            ?.filter { it.name.endsWith(".tflite") && !it.name.endsWith(".tflite.tmp") && it.length() > 0 }
            ?.map { it.nameWithoutExtension }
            ?: emptyList()
    }

    /**
     * Returns the version of a downloaded model, or 0 if not downloaded.
     */
    fun getModelVersion(modelId: String): Int {
        val versions = readVersions()
        return versions[modelId] ?: 0
    }

    /**
     * Returns available free space in the internal files directory.
     */
    private fun getFreeSpace(): Long {
        return try {
            val stat = StatFs(context.filesDir.absolutePath)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (e: Exception) {
            0L
        }
    }
}
package com.rapidraw.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * ONNX 模型下载器 — 对齐原 RapidRAW（Rust）`ensure_model` 语义。
 *
 * 职责：
 *  1. 维护 7 类共 8 个模型文件清单（SAM 拆为 encoder + decoder），托管于
 *     HuggingFace `CyberTimon/RapidRAW-Models`。
 *  2. HTTP 下载（OkHttp）+ SHA256 校验 + 失败重下（最多 3 次）。
 *  3. 断点续传：HTTP `Range` 头 + `.tmp` 中间文件；校验通过后原子重命名。
 *  4. 进度回调：`Flow<DownloadProgress>`（已下载 / 总字节 / 状态）。
 *  5. 并发控制：同一模型并发请求只下载一次（`ConcurrentHashMap<ModelId, CompletableDeferred>`）。
 *  6. 存储位置：`context.filesDir/onnx/<fileName>`，与 `OnnxInferenceEngine.getModelPath()` 兼容。
 *
 * @since v1.6.8
 */
class OnnxModelManager(private val context: Context) {

    /** 模型标识（与原 RapidRAW 项目一致，托管于 HuggingFace CyberTimon/RapidRAW-Models）。 */
    enum class ModelId(val fileName: String, val displayName: String, val approxSizeMb: Int) {
        U2NET("u2net.onnx", "Subject Mask (U2-Net)", 176),
        SKYSEG("skyseg_u2net.onnx", "Sky Segmentation", 176),
        SAM_ENCODER("sam_vit_b_01ec64_encoder.onnx", "SAM Encoder (ViT-B)", 358),
        SAM_DECODER("sam_vit_b_01ec64_decoder.onnx", "SAM Decoder", 18),
        DEPTH_ANYTHING("depth_anything_v2_vits.onnx", "Depth Anything V2 Small", 99),
        NIND_DENOISE("nind_denoise_utnet_684.onnx", "NIND Denoise (UTNet)", 17),
        LAMA_INPAINT("lama_fp16.onnx", "LaMa Inpainting (FP16)", 200),
        CLIP("clip_model.onnx", "CLIP Zero-shot Tagger", 340),
    }

    /** 下载状态机。 */
    enum class DownloadState { IDLE, DOWNLOADING, VERIFYING, COMPLETED, FAILED }

    /** 单次下载进度的不可变快照。`totalBytes < 0` 表示未知。 */
    data class DownloadProgress(
        val modelId: ModelId,
        val state: DownloadState,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val error: String? = null,
    )

    /** 模型清单条目。 */
    data class ModelDescriptor(
        val modelId: ModelId,
        val fileName: String,
        val displayName: String,
        val approxSizeMb: Int,
        val sha256: String,
        val url: String,
    )

    companion object {
        private const val TAG = "OnnxModelManager"
        private const val ONNX_DIR = "onnx"
        private const val TMP_SUFFIX = ".tmp"
        private const val HF_BASE = "https://huggingface.co/CyberTimon/RapidRAW-Models/resolve/main/"
        private const val MAX_ATTEMPTS = 3
        private const val CONNECT_TIMEOUT_S = 30L
        private const val READ_TIMEOUT_S = 120L
        private const val WRITE_TIMEOUT_S = 120L
        private const val PROGRESS_REPORT_THRESHOLD = 256L * 1024

        /**
         * SHA256 校验和（小写十六进制）。
         *
         * TODO(首次部署): 用 `sha256sum <model_file>` 获取真实哈希填入下方空串。
         *  - 留空（占位）时：校验逻辑仍会真实运行 —— 计算 SHA256 并打印到日志
         *    （便于首次部署采集哈希），视为校验通过。
         *  - 填入后：严格比对，不匹配则删除文件并抛出异常（强制失败重下）。
         */
        private val SHA256_CHECKSUMS: Map<ModelId, String> = mapOf(
            ModelId.U2NET to "",
            ModelId.SKYSEG to "",
            ModelId.SAM_ENCODER to "",
            ModelId.SAM_DECODER to "",
            ModelId.DEPTH_ANYTHING to "",
            ModelId.NIND_DENOISE to "",
            ModelId.LAMA_INPAINT to "",
            ModelId.CLIP to "",
        )

        @Volatile
        private var sharedClient: OkHttpClient? = null

        private fun client(): OkHttpClient = sharedClient ?: synchronized(this) {
            sharedClient ?: OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
                .writeTimeout(WRITE_TIMEOUT_S, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .retryOnConnectionFailure(true)
                .build()
                .also { sharedClient = it }
        }

        /** 通过文件名反查 ModelId（供 OnnxInferenceEngine 集成使用）。 */
        fun matchByFileName(fileName: String): ModelId? =
            ModelId.entries.firstOrNull { it.fileName == fileName }
    }

    // ── 状态容器 ─────────────────────────────────────────────────

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 每个模型的进度流（懒创建；已下载则初值为 COMPLETED）。 */
    private val progressFlows = ConcurrentHashMap<ModelId, MutableStateFlow<DownloadProgress>>()

    /** 进行中的下载任务：同一模型并发请求只触发一次实际下载并共享结果。 */
    private val inFlight = ConcurrentHashMap<ModelId, CompletableDeferred<File>>()

    // ── 公共 API ─────────────────────────────────────────────────

    /** 完整模型清单。 */
    val allModels: List<ModelDescriptor>
        get() = ModelId.entries.map { descriptorFor(it) }

    /** 下载或返回已存在的模型文件（线程安全 / 协程安全）。 */
    suspend fun ensureModel(modelId: ModelId): File {
        // 1) 已下载则直接返回
        getModelFile(modelId)?.let { file ->
            updateProgress(modelId, DownloadState.COMPLETED, file.length(), file.length())
            return file
        }
        // 2) 已有进行中的下载 → 共享同一 Deferred
        inFlight[modelId]?.let { return it.await() }
        // 3) 发起新下载（putIfAbsent 保证竞态下只启动一次）
        val deferred = CompletableDeferred<File>()
        val existing = inFlight.putIfAbsent(modelId, deferred)
        if (existing != null) return existing.await()
        scope.launch {
            try {
                val file = downloadModel(modelId)
                deferred.complete(file)
            } catch (e: Throwable) {
                deferred.completeExceptionally(e)
            } finally {
                inFlight.remove(modelId, deferred)
            }
        }
        return deferred.await()
    }

    /** 模型是否已下载到本地（仅检查文件存在且非空，不重算哈希）。 */
    fun isModelDownloaded(modelId: ModelId): Boolean = getModelFile(modelId) != null

    /** 返回已存在的模型文件，不存在返回 null。 */
    fun getModelFile(modelId: ModelId): File? {
        val file = File(File(context.filesDir, ONNX_DIR), modelId.fileName)
        return if (file.exists() && file.length() > 0L) file else null
    }

    /** 该模型的下载进度流（StateFlow，重放最近一次状态）。 */
    fun getDownloadProgress(modelId: ModelId): Flow<DownloadProgress> =
        flowFor(modelId).asStateFlow()

    /** 删除单个模型（含残留 .tmp）。返回是否最终不存在。 */
    suspend fun deleteModel(modelId: ModelId): Boolean = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, ONNX_DIR)
        val target = File(dir, modelId.fileName)
        val tmp = File(dir, modelId.fileName + TMP_SUFFIX)
        tmp.delete()
        if (target.exists()) target.delete()
        val gone = !target.exists()
        if (gone) updateProgress(modelId, DownloadState.IDLE, 0L, -1L)
        gone
    }

    /** 删除全部模型（仅清 .onnx 文件，不删除目录）。返回删除数量。 */
    suspend fun deleteAllModels(): Int = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, ONNX_DIR)
        if (!dir.exists()) return@withContext 0
        var count = 0
        dir.listFiles()?.forEach { f ->
            if (f.isFile && f.name.endsWith(".onnx") && f.delete()) count++
        }
        ModelId.entries.forEach { updateProgress(it, DownloadState.IDLE, 0L, -1L) }
        count
    }

    /** onnx 目录占用字节数（含 .tmp 残留）。 */
    fun getTotalCacheSize(): Long {
        val dir = File(context.filesDir, ONNX_DIR)
        if (!dir.exists()) return 0L
        var size = 0L
        dir.walkTopDown().forEach { if (it.isFile) size += it.length() }
        return size
    }

    // ── 下载核心 ─────────────────────────────────────────────────

    private fun descriptorFor(id: ModelId): ModelDescriptor = ModelDescriptor(
        modelId = id,
        fileName = id.fileName,
        displayName = id.displayName,
        approxSizeMb = id.approxSizeMb,
        sha256 = SHA256_CHECKSUMS[id].orEmpty(),
        url = HF_BASE + id.fileName,
    )

    private fun flowFor(modelId: ModelId): MutableStateFlow<DownloadProgress> =
        progressFlows.getOrPut(modelId) {
            val file = getModelFile(modelId)
            if (file != null) {
                MutableStateFlow(
                    DownloadProgress(modelId, DownloadState.COMPLETED, file.length(), file.length(), null),
                )
            } else {
                MutableStateFlow(DownloadProgress(modelId, DownloadState.IDLE, 0L, -1L, null))
            }
        }

    private fun updateProgress(
        modelId: ModelId,
        state: DownloadState,
        downloaded: Long,
        total: Long,
        error: String? = null,
    ) {
        flowFor(modelId).value = DownloadProgress(modelId, state, downloaded, total, error)
    }

    private suspend fun downloadModel(modelId: ModelId): File = withContext(Dispatchers.IO) {
        val descriptor = descriptorFor(modelId)
        val dir = File(context.filesDir, ONNX_DIR).apply { if (!exists()) mkdirs() }
        val finalFile = File(dir, modelId.fileName)
        val tmpFile = File(dir, modelId.fileName + TMP_SUFFIX)

        // 已存在且（占位态或校验通过）→ 直接返回
        if (finalFile.exists() && verifyFile(modelId, finalFile, silent = true)) {
            updateProgress(modelId, DownloadState.COMPLETED, finalFile.length(), finalFile.length())
            return@withContext finalFile
        }

        updateProgress(modelId, DownloadState.DOWNLOADING, 0L, -1L)

        var lastError: Exception? = null
        for (attempt in 1..MAX_ATTEMPTS) {
            try {
                downloadOnce(modelId, descriptor.url, tmpFile)
                // 校验
                updateProgress(modelId, DownloadState.VERIFYING, tmpFile.length(), tmpFile.length())
                if (!verifyFile(modelId, tmpFile, silent = false)) {
                    tmpFile.delete()
                    throw IOException("SHA256 verification failed for ${modelId.fileName}")
                }
                // 原子重命名 .tmp → 最终文件
                if (finalFile.exists()) finalFile.delete()
                if (!tmpFile.renameTo(finalFile)) {
                    // nio 原子移动（API 26+ 可用），覆盖目标
                    Files.move(
                        tmpFile.toPath(),
                        finalFile.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                }
                updateProgress(modelId, DownloadState.COMPLETED, finalFile.length(), finalFile.length())
                Log.i(TAG, "Model downloaded & verified: ${modelId.fileName} (${finalFile.length()} bytes)")
                return@withContext finalFile
            } catch (ce: CancellationException) {
                tmpFile.delete()
                updateProgress(modelId, DownloadState.FAILED, 0L, -1L, ce.message)
                throw ce
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "Download attempt $attempt/${MAX_ATTEMPTS} failed for ${modelId.fileName}: ${e.message}")
                if (attempt < MAX_ATTEMPTS) {
                    delay(1000L * attempt) // 线性退避
                    updateProgress(
                        modelId,
                        DownloadState.DOWNLOADING,
                        if (tmpFile.exists()) tmpFile.length() else 0L,
                        -1L,
                    )
                }
            }
        }
        // 重试耗尽
        tmpFile.delete()
        updateProgress(modelId, DownloadState.FAILED, 0L, -1L, lastError?.message)
        throw lastError ?: IOException("Download failed for ${descriptor.url}")
    }

    /** 单次下载（含断点续传）。失败时抛出，调用方负责重试。 */
    private fun downloadOnce(modelId: ModelId, url: String, tmpFile: File) {
        val existingBytes = if (tmpFile.exists()) tmpFile.length() else 0L

        val requestBuilder = Request.Builder().url(url).get()
            .header("Accept-Encoding", "identity") // 避免 gzip 破坏断点续传的字节偏移
        if (existingBytes > 0L) {
            requestBuilder.header("Range", "bytes=$existingBytes-")
        }

        client().newCall(requestBuilder.build()).execute().use { resp: Response ->
            if (!resp.isSuccessful) {
                throw IOException("HTTP ${resp.code} ${resp.message} for $url")
            }
            val body = resp.body ?: throw IOException("Empty response body for $url")

            // 解析 Range 响应：206 → 续传；200 → 服务器忽略 Range，整体重下
            val isPartial = resp.code == 206
            val resumeFrom = if (isPartial) existingBytes else 0L
            val totalSize = if (isPartial) {
                parseContentRangeTotal(resp.header("Content-Range"))
            } else {
                resp.header("Content-Length")?.toLongOrNull() ?: -1L
            }

            RandomAccessFile(tmpFile, "rw").use { out ->
                if (!isPartial) {
                    out.setLength(0L) // 截断旧 tmp（服务器未支持续传）
                }
                out.seek(resumeFrom)
                val source = body.byteStream()
                val buffer = ByteArray(64 * 1024)
                var downloaded = resumeFrom
                var lastReport = downloaded
                while (true) {
                    val read = source.read(buffer)
                    if (read == -1) break
                    out.write(buffer, 0, read)
                    downloaded += read
                    if (downloaded - lastReport >= PROGRESS_REPORT_THRESHOLD ||
                        (totalSize > 0L && downloaded == totalSize)
                    ) {
                        updateProgress(modelId, DownloadState.DOWNLOADING, downloaded, totalSize)
                        lastReport = downloaded
                    }
                }
                out.setLength(downloaded) // 防止 tmp 曾比实际更大留下的尾部脏字节
                out.fd.sync()
            }
        }
    }

    /** 解析 `Content-Range: bytes start-end/total` 中的 total；无法解析返回 -1。 */
    private fun parseContentRangeTotal(header: String?): Long {
        if (header.isNullOrBlank()) return -1L
        // 形如 "bytes 1000-1999/5000" 或 "bytes */5000"
        val slashIdx = header.lastIndexOf('/')
        if (slashIdx < 0 || slashIdx == header.length - 1) return -1L
        val totalPart = header.substring(slashIdx + 1).trim()
        if (totalPart == "*") return -1L
        return totalPart.toLongOrNull() ?: -1L
    }

    // ── SHA256 校验 ──────────────────────────────────────────────

    /**
     * 校验文件 SHA256。
     * @param silent true 时仅返回布尔结果（用于已存在文件的快速判定），不打错误日志。
     * @return true 通过；false 失败（仅在预期哈希非空且不匹配时）。
     */
    private fun verifyFile(modelId: ModelId, file: File, silent: Boolean): Boolean {
        val expected = SHA256_CHECKSUMS[modelId].orEmpty()
        val actual = computeSha256(file)
        if (expected.isEmpty()) {
            // 占位符未填：校验逻辑已真实运行，打印哈希便于首次部署采集；视为通过。
            if (!silent) {
                Log.w(
                    TAG,
                    "SHA256 placeholder empty for ${modelId.fileName}; computed=$actual — " +
                        "fill SHA256_CHECKSUMS before release to enforce strict verification.",
                )
            }
            return true
        }
        val ok = expected.equals(actual, ignoreCase = true)
        if (!ok && !silent) {
            Log.e(TAG, "SHA256 mismatch for ${modelId.fileName}: expected=$expected actual=$actual")
        }
        return ok
    }

    private fun computeSha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                md.update(buffer, 0, read)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}

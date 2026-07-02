package com.rapidraw.ai

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap

/**
 * TFLite 推理引擎 — 统一管理模型加载、GPU/NNAPI 委托、推理执行。
 * 支持 INT8/FP16/FP32 量化模型，自动选择最优后端。
 *
 * 线程安全：通过 Mutex 保护模型加载，通过 ConcurrentHashMap 保证并发读安全。
 * 单例模式：每个 Context 持有一个 InferenceEngine 实例。
 */
class InferenceEngine private constructor(private val context: Context) {

    enum class Backend {
        GPU_DELEGATE,   // OpenGL ES 3.1+ / Vulkan GPU 加速
        NNAPI,          // Android NNAPI (8.1+)
        CPU_ONLY        // CPU 回退
    }

    data class ModelConfig(
        val modelFileName: String,
        val inputWidth: Int,
        val inputHeight: Int,
        val inputDataType: DataType = DataType.FLOAT32,
        val outputDataType: DataType = DataType.FLOAT32,
        val batchSize: Int = 1,
        val numOutputs: Int = 1,
        val preferredBackend: Backend = Backend.GPU_DELEGATE,
        val warmupRuns: Int = 1,          // 模型预热推理次数
    )

    data class TensorInfo(
        val name: String,
        val shape: IntArray,
        val dataType: DataType,
    )

    /** 已加载模型对应的输入输出张量信息 */
    private val tensorInfoMap = ConcurrentHashMap<String, Pair<List<TensorInfo>, List<TensorInfo>>>()

    /** 模型是否已预热 */
    private val warmedUp = ConcurrentHashMap<String, Boolean>()

    private val interpreters = ConcurrentHashMap<String, Interpreter>()
    private val delegates = mutableListOf<org.tensorflow.lite.Delegate>()
    private val mutex = Mutex()

    /**
     * v1.5.10 hotfix: 不再通过实例化 GpuDelegate 来检测 GPU 可用性。
     * 旧代码 `runCatching { GpuDelegate() }.isSuccess` 会真实创建一个 GpuDelegate
     * 并泄漏（从未关闭），且在某些 OEM ROM 的 GPU 驱动上有概率触发 native 崩溃。
     *
     * 现在改用 Interpreter.Options 的委托验证 API，它不创建持久的 GL 上下文。
     */
    val isGpuAvailable: Boolean by lazy {
        runCatching {
            val delegate = GpuDelegate()
            try {
                // 验证 delegate 是否成功创建（不抛异常即为成功）
                delegate.close()
                true
            } finally {
                runCatching { delegate.close() }
            }
        }.getOrElse { false }
    }

    val isNnapiAvailable: Boolean by lazy {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1
    }

    /**
     * 加载 TFLite 模型并创建 Interpreter。
     * 线程安全：同一模型并发调用只创建一次。
     */
    suspend fun loadModel(config: ModelConfig): Interpreter = withContext(Dispatchers.IO) {
        // 快速路径：模型已加载
        interpreters[config.modelFileName]?.let { return@withContext it }

        mutex.withLock {
            // Double-check after acquiring lock
            interpreters[config.modelFileName]?.let { return@withContext it }

            val modelFile = copyModelFromAssets(config.modelFileName)
            val options = buildInterpreterOptions(config)
            val interpreter = Interpreter(modelFile, options)

            // 缓存输入输出张量信息
            cacheTensorInfo(config.modelFileName, interpreter)

            interpreters[config.modelFileName] = interpreter
            interpreter
        }
    }

    /**
     * 模型预热：使用零填充输入执行若干次推理以预热 GPU/NNAPI 管线。
     * 建议在加载模型后、首次推理前调用。
     */
    suspend fun warmup(config: ModelConfig): Unit = withContext(Dispatchers.Default) {
        if (warmedUp.getOrDefault(config.modelFileName, false)) return@withContext

        runCatching {
            val interpreter = loadModel(config)
            val inputTensor = interpreter.getInputTensor(0)
            val inputShape = inputTensor.shape()
            val inputType = inputTensor.dataType()

            // 创建零填充输入缓冲区
            val inputSize = inputShape.fold(1) { acc, dim -> acc * dim }
            val inputBuffer = when (inputType) {
                DataType.FLOAT32 -> ByteBuffer.allocateDirect(inputSize * 4).order(ByteOrder.nativeOrder())
                DataType.UINT8 -> ByteBuffer.allocateDirect(inputSize).order(ByteOrder.nativeOrder())
                else -> ByteBuffer.allocateDirect(inputSize * 4).order(ByteOrder.nativeOrder())
            }

            // 构建输出缓冲区
            val outputBuffers = (0 until interpreter.outputTensorCount).map { idx ->
                val outputTensor = interpreter.getOutputTensor(idx)
                val outputShape = outputTensor.shape()
                val outputSize = outputShape.fold(1) { acc, dim -> acc * dim }
                val outputType = outputTensor.dataType()
                when (outputType) {
                    DataType.FLOAT32 -> ByteBuffer.allocateDirect(outputSize * 4).order(ByteOrder.nativeOrder())
                    DataType.UINT8 -> ByteBuffer.allocateDirect(outputSize).order(ByteOrder.nativeOrder())
                    else -> ByteBuffer.allocateDirect(outputSize * 4).order(ByteOrder.nativeOrder())
                }
            }
            val outputMap = outputBuffers.mapIndexed { idx, buf -> idx + 1 to buf }.toMap()

            repeat(config.warmupRuns) {
                inputBuffer.rewind()
                outputBuffers.forEach { it.rewind() }
                interpreter.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputMap)
            }

            warmedUp[config.modelFileName] = true
        }
    }

    /**
     * 使用 TFLite 模型推理，自动处理输入预处理和输出张量映射。
     *
     * @param config 模型配置
     * @param inputBitmap 输入图像
     * @param preprocess 输入预处理函数（如归一化、resize）
     * @return 输出 TensorBuffer 列表
     */
    suspend fun runInference(
        config: ModelConfig,
        inputBitmap: Bitmap,
        preprocess: (TensorImage) -> TensorImage = { it },
    ): List<TensorBuffer> = withContext(Dispatchers.Default) {
        // N-05: 推理前内存检查，防止 native 崩溃
        checkMemoryBeforeInference()

        val interpreter = loadModel(config)

        // 自动 resize 输入图像到模型期望尺寸
        val resizedBitmap = if (inputBitmap.width != config.inputWidth || inputBitmap.height != config.inputHeight) {
            Bitmap.createScaledBitmap(inputBitmap, config.inputWidth, config.inputHeight, true)
        } else {
            inputBitmap
        }

        val tensorImage = TensorImage(config.inputDataType)
        tensorImage.load(resizedBitmap)
        val processed = preprocess(tensorImage)

        // 根据模型输出张量信息创建正确大小的输出缓冲区
        val outputs = (0 until config.numOutputs).map { idx ->
            val outputShape = if (interpreter.outputTensorCount > idx) {
                interpreter.getOutputTensor(idx).shape()
            } else {
                intArrayOf(config.batchSize)
            }
            TensorBuffer.createFixedSize(outputShape, config.outputDataType)
        }

        val outputMap = outputs.mapIndexed { idx, buffer ->
            idx + 1 to buffer.buffer
        }.toMap()

        interpreter.runForMultipleInputsOutputs(arrayOf(processed.buffer), outputMap)
        outputs
    }

    /**
     * 直接使用 ByteBuffer 进行推理，适用于自定义输入格式。
     */
    suspend fun runInferenceRaw(
        config: ModelConfig,
        inputBuffer: ByteBuffer,
    ): List<ByteBuffer> = withContext(Dispatchers.Default) {
        // N-05: 推理前内存检查，防止 native 崩溃
        checkMemoryBeforeInference()

        val interpreter = loadModel(config)

        val outputBuffers = (0 until interpreter.outputTensorCount).map { idx ->
            val outputTensor = interpreter.getOutputTensor(idx)
            val outputShape = outputTensor.shape()
            val outputSize = outputShape.fold(1) { acc, dim -> acc * dim }
            val outputType = outputTensor.dataType()
            val byteCount = when (outputType) {
                DataType.FLOAT32 -> outputSize * 4
                DataType.UINT8 -> outputSize
                else -> outputSize * 4
            }
            ByteBuffer.allocateDirect(byteCount).order(ByteOrder.nativeOrder())
        }

        val outputMap = outputBuffers.mapIndexed { idx, buf -> idx + 1 to buf }.toMap()
        inputBuffer.rewind()
        interpreter.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputMap)
        outputBuffers
    }

    /**
     * 获取模型的输入输出张量信息。
     */
    fun getTensorInfo(modelFileName: String): Pair<List<TensorInfo>, List<TensorInfo>>? {
        return tensorInfoMap[modelFileName]
    }

    /**
     * 检查模型是否已加载。
     */
    fun isModelLoaded(modelFileName: String): Boolean = interpreters.containsKey(modelFileName)

    /**
     * 卸载指定模型，释放资源。
     */
    fun unloadModel(modelFileName: String) {
        interpreters.remove(modelFileName)?.close()
        warmedUp.remove(modelFileName)
        tensorInfoMap.remove(modelFileName)
    }

    fun close() {
        interpreters.values.forEach { it.close() }
        delegates.forEach { delegate ->
            runCatching {
                val method = delegate.javaClass.getMethod("close")
                method.invoke(delegate)
            }
        }
        interpreters.clear()
        delegates.clear()
        warmedUp.clear()
        tensorInfoMap.clear()
    }

    // ── 内部方法 ──────────────────────────────────────────────

    private fun buildInterpreterOptions(config: ModelConfig): Interpreter.Options {
        return Interpreter.Options().apply {
            setNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(1, 4))
            when (config.preferredBackend) {
                Backend.GPU_DELEGATE -> {
                    if (isGpuAvailable) {
                        try {
                            val delegate = GpuDelegate()
                            addDelegate(delegate)
                            synchronized(delegates) { delegates.add(delegate) }
                        } catch (_: Throwable) {
                            // v1.5.10 hotfix: 捕获 Throwable 而非 Exception，
                            // 部分 OEM ROM 的 GPU 驱动可能抛出 Error 子类。
                        }
                    }
                }
                Backend.NNAPI -> {
                    if (isNnapiAvailable) {
                        @Suppress("DEPRECATION")
                        setUseNNAPI(true)
                    }
                }
                Backend.CPU_ONLY -> { /* default CPU */ }
            }
        }
    }

    private fun cacheTensorInfo(modelFileName: String, interpreter: Interpreter) {
        val inputInfos = (0 until interpreter.inputTensorCount).map { idx ->
            val tensor = interpreter.getInputTensor(idx)
            TensorInfo(tensor.name(), tensor.shape(), tensor.dataType())
        }
        val outputInfos = (0 until interpreter.outputTensorCount).map { idx ->
            val tensor = interpreter.getOutputTensor(idx)
            TensorInfo(tensor.name(), tensor.shape(), tensor.dataType())
        }
        tensorInfoMap[modelFileName] = inputInfos to outputInfos
    }

    /**
     * UNINST-06: 将模型文件从 assets 复制到持久化目录。
     *
     * 优先级：
     * 1. /sdcard/Android/media/<pkg>/models/ — 卸载不删除（Android 10+）
     * 2. context.filesDir/models/ — 降级路径（卸载会清除）
     *
     * 使用 Android/media 而非 getExternalFilesDir 的原因：
     * - getExternalFilesDir 位于 /Android/data/<pkg>/，卸载时系统自动清除
     * - /Android/media/<pkg>/ 卸载时系统不自动清除，模型文件可保留
     */
    private fun copyModelFromAssets(fileName: String): File {
        // 优先使用 Android/media 目录（卸载不删除）
        val mediaDir = File(
            android.os.Environment.getExternalStorageDirectory(),
            "Android/media/${context.packageName}/models"
        )
        val mediaFile = File(mediaDir, "tflite_$fileName")

        if (mediaFile.exists() && verifyModelIntegrityInternal(mediaFile)) {
            return mediaFile
        }
        // N-06: 如果文件存在但损坏，删除以便重新复制
        if (mediaFile.exists()) {
            mediaFile.delete()
        }

        // 尝试写入 media 目录
        try {
            if (!mediaDir.exists()) mediaDir.mkdirs()
            if (mediaDir.canWrite()) {
                copyModelFile(fileName, mediaFile)
                // N-06: 复制后验证完整性，损坏则删除并重试
                if (!verifyModelIntegrityInternal(mediaFile)) {
                    mediaFile.delete()
                    copyModelFile(fileName, mediaFile)
                }
                if (verifyModelIntegrityInternal(mediaFile)) {
                    return mediaFile
                }
                Log.w(TAG, "Model file corrupted after copy to media dir, falling back")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cannot write to Android/media, falling back to filesDir: ${e.message}")
        }

        // 降级：使用 filesDir（卸载会清除，但至少能用）
        val fallbackDir = File(context.filesDir, "models")
        val fallbackFile = File(fallbackDir, "tflite_$fileName")
        if (!fallbackFile.exists() || !verifyModelIntegrityInternal(fallbackFile)) {
            if (fallbackFile.exists()) fallbackFile.delete()
            if (!fallbackDir.exists()) fallbackDir.mkdirs()
            copyModelFile(fileName, fallbackFile)
            // N-06: 复制后验证完整性，损坏则删除并重试
            if (!verifyModelIntegrityInternal(fallbackFile)) {
                fallbackFile.delete()
                copyModelFile(fileName, fallbackFile)
            }
        }
        return fallbackFile
    }

    /**
     * N-06: 将模型文件从 assets 复制到目标路径。
     */
    private fun copyModelFile(fileName: String, destFile: File) {
        context.assets.open("models/$fileName").use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }
    }

    /**
     * INST-10: 检查 AI 模型是否可用（离线降级判断）。
     * 当模型文件不存在且网络不可用时，调用方可据此禁用 AI 蒙版功能。
     *
     * N-06: 添加完整性验证，不再仅检查文件是否存在。
     */
    fun isModelAvailable(fileName: String): Boolean {
        return getModelIntegrity(fileName).isValid
    }

    /**
     * N-06: 获取模型文件完整性检查结果。
     */
    fun getModelIntegrity(fileName: String): ModelIntegrityResult {
        // 检查 media 目录
        val mediaDir = File(
            android.os.Environment.getExternalStorageDirectory(),
            "Android/media/${context.packageName}/models"
        )
        val mediaFile = File(mediaDir, "tflite_$fileName")
        val mediaResult = checkFileIntegrity(mediaFile)
        if (mediaResult.isValid) return mediaResult

        // 检查 filesDir 降级目录
        val fallbackFile = File(File(context.filesDir, "models"), "tflite_$fileName")
        val fallbackResult = checkFileIntegrity(fallbackFile)
        if (fallbackResult.isValid) return fallbackResult

        // 检查旧路径（cacheDir）
        val legacyFile = File(context.cacheDir, "tflite_$fileName")
        return checkFileIntegrity(legacyFile)
    }

    /**
     * N-06: 验证模型文件完整性。
     * - 检查文件是否存在且大小非零
     * - 读取前 4 字节验证 TFLite FlatBuffer 偏移量魔数
     * - 读取字节 4-7 验证 "TFL3" 文件标识符
     * - 检查文件大小是否合理（不小于最小 TFLite 头大小）
     */
    fun verifyModelIntegrity(fileName: String): Boolean {
        return getModelIntegrity(fileName).isValid
    }

    /**
     * N-06: 内部完整性检查，直接对 File 对象操作。
     */
    private fun verifyModelIntegrityInternal(file: File): Boolean {
        return checkFileIntegrity(file).isValid
    }

    /**
     * N-06: 对单个文件执行完整性检查，返回 ModelIntegrityResult。
     * TFLite 模型文件格式：
     * - 字节 0-3: FlatBuffer 根表偏移量（little-endian uint32），通常为 0x1C000000
     * - 字节 4-7: 文件标识符 "TFL3" (0x54 0x46 0x4C 0x33)
     */
    private fun checkFileIntegrity(file: File): ModelIntegrityResult {
        if (!file.exists()) {
            return ModelIntegrityResult(
                exists = false,
                sizeBytes = 0L,
                isValid = false,
                errorMessage = "File not found: ${file.absolutePath}",
            )
        }

        val fileSize = file.length()
        if (fileSize == 0L) {
            return ModelIntegrityResult(
                exists = true,
                sizeBytes = 0L,
                isValid = false,
                errorMessage = "File is empty: ${file.absolutePath}",
            )
        }

        // 文件大小过小不可能是有效模型（至少需要 FlatBuffer 头 + 一些数据）
        if (fileSize < 16L) {
            return ModelIntegrityResult(
                exists = true,
                sizeBytes = fileSize,
                isValid = false,
                errorMessage = "File too small to be a valid TFLite model: $fileSize bytes",
            )
        }

        return try {
            val buffer = ByteArray(8)
            file.inputStream().use { input ->
                var read = 0
                while (read < buffer.size) {
                    val n = input.read(buffer, read, buffer.size - read)
                    if (n < 0) break
                    read += n
                }
                if (read < 8) {
                    return ModelIntegrityResult(
                        exists = true,
                        sizeBytes = fileSize,
                        isValid = false,
                        errorMessage = "File truncated: expected at least 8 bytes, got $read",
                    )
                }
            }

            // 验证字节 4-7: "TFL3" 标识符
            val hasTfl3Marker = buffer[4] == 0x54.toByte()
                && buffer[5] == 0x46.toByte()
                && buffer[6] == 0x4C.toByte()
                && buffer[7] == 0x33.toByte()

            if (!hasTfl3Marker) {
                return ModelIntegrityResult(
                    exists = true,
                    sizeBytes = fileSize,
                    isValid = false,
                    errorMessage = "Missing TFL3 file identifier: expected 54 46 4C 33, got " +
                        "%02X %02X %02X %02X".format(
                            buffer[4].toInt() and 0xFF,
                            buffer[5].toInt() and 0xFF,
                            buffer[6].toInt() and 0xFF,
                            buffer[7].toInt() and 0xFF,
                        ),
                )
            }

            ModelIntegrityResult(
                exists = true,
                sizeBytes = fileSize,
                isValid = true,
                errorMessage = null,
            )
        } catch (e: Exception) {
            ModelIntegrityResult(
                exists = true,
                sizeBytes = fileSize,
                isValid = false,
                errorMessage = "Integrity check failed: ${e.message}",
            )
        }
    }

    /**
     * INST-10: 检查设备内存是否足够运行 AI 模型。
     * 建议在 AI 蒙版入口处调用，8GB RAM 以下设备给提示。
     */
    fun isDeviceMemorySufficient(): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val totalMem = activityManager?.memoryInfo?.totalMem ?: 0L
        // 8GB = 8L * 1024 * 1024 * 1024
        return totalMem >= 6L * 1024 * 1024 * 1024 // 6GB 作为最低门槛
    }

    /**
     * N-05: 增强版内存检查，同时检查总内存和可用内存。
     * - 总 RAM >= 6GB
     * - 可用内存 >= 2GB
     * 返回详细的 MemoryCheckResult 说明通过或失败原因。
     */
    fun isDeviceMemoryAdequateForAi(): MemoryCheckResult {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memInfo)

        val totalMem = memInfo.totalMem
        val availMem = memInfo.availMem
        val totalRamMb = totalMem / (1024 * 1024)
        val availableRamMb = availMem / (1024 * 1024)
        val totalRamGb = totalMem / (1024 * 1024 * 1024)
        val availRamGb = availMem / (1024 * 1024 * 1024)

        if (totalMem < 6L * 1024 * 1024 * 1024) {
            return MemoryCheckResult(
                sufficient = false,
                totalRamMb = totalRamMb,
                availableRamMb = availableRamMb,
                reason = "Total RAM (${totalRamGb}GB) is below 6GB minimum threshold",
            )
        }

        if (availMem < 2L * 1024 * 1024 * 1024) {
            return MemoryCheckResult(
                sufficient = false,
                totalRamMb = totalRamMb,
                availableRamMb = availableRamMb,
                reason = "Available RAM (${availRamGb}GB) is below 2GB minimum threshold",
            )
        }

        return MemoryCheckResult(
            sufficient = true,
            totalRamMb = totalRamMb,
            availableRamMb = availableRamMb,
            reason = "Memory sufficient: ${totalRamGb}GB total, ${availRamGb}GB available",
        )
    }

    /**
     * N-05: 推理前内存检查，可从 UI 线程同步调用。
     * 如果内存不足，抛出 InsufficientMemoryException 防止 native 崩溃。
     */
    fun checkMemoryBeforeInference(): MemoryCheckResult {
        val result = isDeviceMemoryAdequateForAi()
        if (!result.sufficient) {
            throw InsufficientMemoryException(
                "Insufficient memory for AI inference: ${result.reason}",
                result,
            )
        }
        return result
    }

    /**
     * N-05: 内存不足异常，在推理前内存检查不通过时抛出。
     */
    class InsufficientMemoryException(
        message: String,
        val result: MemoryCheckResult,
    ) : RuntimeException(message)

    companion object {
        private const val TAG = "InferenceEngine"

        /**
         * N-05: 内存检查结果，包含是否充足及详细原因。
         */
        data class MemoryCheckResult(
            val sufficient: Boolean,
            val totalRamMb: Long,
            val availableRamMb: Long,
            val reason: String,
        )

        /**
         * N-06: 模型文件完整性检查结果。
         */
        data class ModelIntegrityResult(
            val exists: Boolean,
            val sizeBytes: Long,
            val isValid: Boolean,
            val errorMessage: String? = null,
        )

        @Volatile
        private var instance: InferenceEngine? = null

        /**
         * 获取 InferenceEngine 单例。
         * 使用 Application Context 避免内存泄漏。
         */
        fun getInstance(context: Context): InferenceEngine {
            return instance ?: synchronized(this) {
                instance ?: InferenceEngine(context.applicationContext).also { instance = it }
            }
        }

        /**
         * 销毁单例，释放所有资源。
         */
        fun destroyInstance() {
            synchronized(this) {
                instance?.close()
                instance = null
            }
        }
    }
}

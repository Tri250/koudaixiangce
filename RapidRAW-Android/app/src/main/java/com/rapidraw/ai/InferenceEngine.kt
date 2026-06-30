package com.rapidraw.ai

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.nnapi.NnapiDelegate
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

    val isGpuAvailable: Boolean by lazy {
        runCatching { GpuDelegate() }.isSuccess
    }

    val isNnapiAvailable: Boolean by lazy {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 &&
            runCatching { NnapiDelegate() }.isSuccess
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
                        } catch (_: Exception) {
                            // GPU delegate 创建失败，回退到 CPU
                        }
                    }
                }
                Backend.NNAPI -> {
                    if (isNnapiAvailable) {
                        try {
                            val delegate = NnapiDelegate()
                            addDelegate(delegate)
                            synchronized(delegates) { delegates.add(delegate) }
                        } catch (_: Exception) {
                            // NNAPI 不可用，回退到 CPU
                        }
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

    private fun copyModelFromAssets(fileName: String): File {
        val outFile = File(context.cacheDir, "tflite_$fileName")
        if (!outFile.exists() || outFile.length() == 0L) {
            context.assets.open("models/$fileName").use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            }
        }
        return outFile
    }

    companion object {
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

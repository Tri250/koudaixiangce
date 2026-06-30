package com.rapidraw.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * TFLite 推理引擎 — 统一管理模型加载、GPU/NNAPI 委托、推理执行。
 * 支持 INT8/FP16/FP32 量化模型，自动选择最优后端。
 */
class InferenceEngine(private val context: Context) {

    enum class Backend {
        GPU_DELEGATE,   // OpenGL ES 3.1+ GPU 加速
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
    )

    private val interpreters = mutableMapOf<String, Interpreter>()
    private val delegates = mutableListOf<org.tensorflow.lite.Delegate>()

    val isGpuAvailable: Boolean by lazy {
        runCatching { GpuDelegate() }.isSuccess
    }

    suspend fun loadModel(config: ModelConfig): Interpreter = withContext(Dispatchers.IO) {
        interpreters.getOrPut(config.modelFileName) {
            val modelFile = copyModelFromAssets(config.modelFileName)
            val options = Interpreter.Options().apply {
                setNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(1, 4))
                when (config.preferredBackend) {
                    Backend.GPU_DELEGATE -> {
                        if (isGpuAvailable) {
                            val delegate = GpuDelegate()
                            addDelegate(delegate)
                            delegates.add(delegate)
                        }
                    }
                    Backend.CPU_ONLY -> { /* default CPU */ }
                }
            }
            Interpreter(modelFile, options)
        }
    }

    /**
     * 从 assets/models/ 目录复制模型文件到缓存目录。
     * 如果 assets 中不存在该模型文件（当前 AI 模块使用纯 Kotlin 启发式算法，
     * 不依赖 TFLite 模型），则尝试从 ModelManager 下载的模型目录加载。
     * 两者都不可用时抛出明确异常，避免 FileNotFoundException 崩溃。
     */
    private fun copyModelFromAssets(fileName: String): File {
        val outFile = File(context.cacheDir, "tflite_$fileName")
        if (outFile.exists() && outFile.length() > 0) {
            return outFile
        }

        // 尝试从 assets 加载
        try {
            val assetPath = "models/$fileName"
            val assetList = context.assets.list("models") ?: emptyArray()
            if (assetList.contains(fileName)) {
                context.assets.open(assetPath).use { input ->
                    FileOutputStream(outFile).use { output ->
                        input.copyTo(output)
                    }
                }
                return outFile
            }
        } catch (e: IOException) {
            Log.w(TAG, "Model file not found in assets: $fileName", e)
        }

        // 尝试从 ModelManager 下载目录加载
        val downloadedModel = File(context.filesDir, "ai_models/$fileName")
        if (downloadedModel.exists() && downloadedModel.length() > 0) {
            return downloadedModel
        }

        throw IOException(
            "TFLite model '$fileName' not found. Neither assets/models/ nor ai_models/ contains this file. " +
            "Current AI features use Kotlin heuristic algorithms and do not require TFLite models."
        )
    }

    companion object {
        private const val TAG = "InferenceEngine"
    }

    suspend fun runInference(
        config: ModelConfig,
        inputBitmap: Bitmap,
        preprocess: (TensorImage) -> TensorImage = { it },
    ): List<TensorBuffer> = withContext(Dispatchers.Default) {
        val interpreter = loadModel(config)
        val tensorImage = TensorImage(config.inputDataType)
        tensorImage.load(inputBitmap)
        val processed = preprocess(tensorImage)

        val outputs = (0 until config.numOutputs).map {
            TensorBuffer.createFixedSize(intArrayOf(config.batchSize), config.outputDataType)
        }
        val outputMap = outputs.mapIndexed { idx, buffer ->
            idx + 1 to buffer.buffer
        }.toMap()

        interpreter.runForMultipleInputsOutputs(arrayOf(processed.buffer), outputMap)
        outputs
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
    }
}

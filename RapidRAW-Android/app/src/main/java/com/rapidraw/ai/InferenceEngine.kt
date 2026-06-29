package com.rapidraw.ai

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.nnapi.NnApiDelegate
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * TFLite 推理引擎 — 统一管理模型加载、GPU/NNAPI 委托、推理执行。
 * 支持 INT8/FP16/FP32 量化模型，自动选择最优后端。
 */
class InferenceEngine(private val context: Context) {

    enum class Backend {
        GPU_DELEGATE,   // OpenGL ES 3.1+ GPU 加速
        NNAPI,          // Android NNAPI (芯片 NPU)
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
        runCatching { GpuDelegate.Options().setForceBackend(GpuDelegate.Options.Backend.OPENCL) }.isSuccess
    }

    val isNnapiAvailable: Boolean by lazy {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    }

    suspend fun loadModel(config: ModelConfig): Interpreter = withContext(Dispatchers.IO) {
        interpreters.getOrPut(config.modelFileName) {
            val modelFile = copyModelFromAssets(config.modelFileName)
            val options = Interpreter.Options().apply {
                setNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(1, 4))
                when (config.preferredBackend) {
                    Backend.GPU_DELEGATE -> {
                        if (isGpuAvailable) {
                            val gpuOptions = GpuDelegate.Options().apply {
                                setForceBackend(GpuDelegate.Options.Backend.OPENCL)
                                setPrecisionLossAllowed(false)
                            }
                            val delegate = GpuDelegate(gpuOptions)
                            addDelegate(delegate)
                            delegates.add(delegate)
                        }
                    }
                    Backend.NNAPI -> {
                        if (isNnapiAvailable) {
                            val nnapiOptions = NnApiDelegate.Options().apply {
                                setUseNnapiCaching(true)
                                setModelToken(config.modelFileName)
                            }
                            val delegate = NnApiDelegate(nnapiOptions)
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

    private fun copyModelFromAssets(fileName: String): File {
        val outFile = File(context.cacheDir, "tflite_$fileName")
        if (!outFile.exists()) {
            context.assets.open("models/$fileName").use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            }
        }
        return outFile
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

        interpreter.runForMultipleInputsOutputs(arrayOf(processed.buffer), outputMap.mapKeys { (k, _) -> k.toString() })
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

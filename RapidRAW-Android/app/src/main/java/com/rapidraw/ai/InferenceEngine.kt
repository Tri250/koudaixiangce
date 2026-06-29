package com.rapidraw.ai

import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class InferenceEngine {

    companion object {
        private const val NUM_THREADS = 4
    }

    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null
    private val mutex = Mutex()
    private var loaded = false

    /**
     * Loads a TFLite model from the given file path.
     * Attempts GPU delegate acceleration first, falls back to CPU with multi-threading.
     *
     * @param modelPath Absolute path to the .tflite model file
     * @return true if model was loaded successfully
     */
    suspend fun loadModel(modelPath: String): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            release()

            val modelFile = File(modelPath)
            if (!modelFile.exists() || !modelFile.canRead()) {
                return@withLock false
            }

            try {
                val modelBuffer = loadModelFile(modelFile)
                val options = Interpreter.Options()

                // Try GPU delegate
                val compatList = CompatibilityList()
                if (compatList.isDelegateSupportedOnThisDevice) {
                    try {
                        gpuDelegate = GpuDelegate(compatList.bestOptionsForThisDevice)
                        options.addDelegate(gpuDelegate)
                    } catch (e: Exception) {
                        gpuDelegate?.close()
                        gpuDelegate = null
                        // Fall back to CPU
                        options.setNumThreads(NUM_THREADS)
                    }
                } else {
                    options.setNumThreads(NUM_THREADS)
                }

                options.setUseXNNPACK(true)
                interpreter = Interpreter(modelBuffer, options)
                loaded = true
                true
            } catch (e: Exception) {
                release()
                false
            }
        }
    }

    /**
     * Runs inference on raw ByteBuffer input.
     *
     * @param input Pre-formatted ByteBuffer matching the model's input shape
     * @param outputSizes Size of each output tensor (number of float elements)
     * @return Array of output float arrays, one per output tensor
     */
    fun runInference(input: ByteBuffer, outputSizes: IntArray): Array<FloatArray> {
        val interpreter = this.interpreter
            ?: throw IllegalStateException("Model not loaded. Call loadModel() first.")

        if (!loaded) {
            throw IllegalStateException("Model not loaded. Call loadModel() first.")
        }

        val outputs = Array(outputSizes.size) { i ->
            val output = ByteBuffer.allocateDirect(outputSizes[i] * 4)
            output.order(ByteOrder.nativeOrder())
            output
        }

        synchronized(interpreter) {
            interpreter.run(input, outputs)
        }

        return Array(outputSizes.size) { i ->
            val floatArray = FloatArray(outputSizes[i])
            outputs[i].rewind()
            outputs[i].asFloatBuffer().get(floatArray)
            floatArray
        }
    }

    /**
     * Convenience method: runs inference on a Bitmap and returns a processed Bitmap.
     * Assumes the model takes a single Bitmap input and produces a single Bitmap output
     * of the same dimensions.
     *
     * @param input Source bitmap (will be resized to match model input size)
     * @return Processed bitmap
     */
    fun runInferenceBitmap(input: Bitmap): Bitmap {
        val interpreter = this.interpreter
            ?: throw IllegalStateException("Model not loaded. Call loadModel() first.")

        if (!loaded) {
            throw IllegalStateException("Model not loaded. Call loadModel() first.")
        }

        val inputIndex = 0
        val inputShape = interpreter.getInputTensor(inputIndex).shape()
        val inputWidth = inputShape[2]
        val inputHeight = inputShape[1]

        val resized = Bitmap.createScaledBitmap(input, inputWidth, inputHeight, true)

        val inputBuffer = bitmapToByteBuffer(resized, inputWidth, inputHeight)

        val outputIndex = 0
        val outputShape = interpreter.getOutputTensor(outputIndex).shape()
        val outputSize = outputShape.fold(1) { acc, dim -> acc * dim }

        val outputBuffer = ByteBuffer.allocateDirect(outputSize * 4)
        outputBuffer.order(ByteOrder.nativeOrder())

        synchronized(interpreter) {
            interpreter.run(inputBuffer, outputBuffer)
        }

        outputBuffer.rewind()
        val outputPixels = IntArray(outputSize)
        val floatValues = FloatArray(outputSize)
        outputBuffer.asFloatBuffer().get(floatValues)

        for (i in outputPixels.indices) {
            val value = (floatValues[i] * 255f).toInt().coerceIn(0, 255)
            outputPixels[i] = (0xFF shl 24) or (value shl 16) or (value shl 8) or value
        }

        val outputHeight = outputShape[1]
        val outputWidth = outputShape[2]
        val outputBitmap = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        outputBitmap.setPixels(outputPixels, 0, outputWidth, 0, 0, outputWidth, outputHeight)

        if (!resized.isRecycled) {
            resized.recycle()
        }

        return outputBitmap
    }

    /**
     * Releases all resources (interpreter and GPU delegate).
     * Safe to call multiple times.
     */
    fun release() {
        synchronized(this) {
            try {
                interpreter?.close()
            } catch (_: Exception) {
            }
            interpreter = null

            try {
                gpuDelegate?.close()
            } catch (_: Exception) {
            }
            gpuDelegate = null

            loaded = false
        }
    }

    /**
     * Returns whether a model is currently loaded and ready for inference.
     */
    fun isLoaded(): Boolean = loaded && interpreter != null

    /**
     * Loads a model file into a MappedByteBuffer for efficient memory access.
     */
    private fun loadModelFile(file: File): MappedByteBuffer {
        FileChannel.open(file.toPath()).use { channel ->
            return channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
        }
    }

    /**
     * Converts a Bitmap to a ByteBuffer suitable for TFLite model input.
     * Output format: RGB float [0, 1] with shape [1, height, width, 3].
     */
    private fun bitmapToByteBuffer(bitmap: Bitmap, width: Int, height: Int): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(width * height * 3 * 4)
        buffer.order(ByteOrder.nativeOrder())
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (pixel in pixels) {
            val r = ((pixel shr 16) and 0xFF) / 255.0f
            val g = ((pixel shr 8) and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f
            buffer.putFloat(r)
            buffer.putFloat(g)
            buffer.putFloat(b)
        }

        return buffer
    }
}
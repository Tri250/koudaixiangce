package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 延时摄影处理器 — 从图像序列创建延时摄影视频或处理后的图像序列。
 *
 * 完整管线：
 * 1. 加载有序图像序列
 * 2. 去闪烁处理（曝光归一化）
 * 3. 可选关键帧调整
 * 4. 导出为图像序列或视频（通过 MediaMuxer）
 * 5. 可配置帧率、分辨率、进度追踪
 */
class TimelapseProcessor {

    companion object {
        private const val TAG = "TimelapseProcessor"
        private const val DEFAULT_FPS = 30
        private const val MIN_FPS = 1
        private const val MAX_FPS = 60
        private const val TIMEOUT_US = 10_000L
    }

    /** 进度阶段 */
    enum class Stage {
        LOADING,
        DEFLICKERING,
        PROCESSING,
        ENCODING,
        EXPORTING,
    }

    /** 进度回调 */
    data class Progress(
        val stage: Stage,
        val progress: Float,
        val message: String,
    )

    /** 导出格式 */
    enum class ExportFormat {
        VIDEO_MP4,
        IMAGE_SEQUENCE,
    }

    /** 分辨率缩放 */
    enum class ResolutionScale(val displayName: String, val ratio: Float) {
        ORIGINAL("原始分辨率", 1.0f),
        UHD_4K("4K UHD", 3840f / 3840f), // 标记为原始分辨率
        FHD_1080P("1080p", 0.5f),
        HD_720P("720p", 0.35f),
        SD_480P("480p", 0.25f),
    }

    /** 配置参数 */
    data class Config(
        val fps: Int = DEFAULT_FPS,
        val resolutionScale: ResolutionScale = ResolutionScale.ORIGINAL,
        val deflicker: Boolean = true,
        val outputFormat: ExportFormat = ExportFormat.VIDEO_MP4,
        val videoBitrate: Int = 8_000_000, // 8 Mbps
        val keyframeInterval: Int = 1, // 每帧都是关键帧
    ) {
        init {
            require(fps in MIN_FPS..MAX_FPS) { "帧率必须在 ${MIN_FPS}-${MAX_FPS} 之间" }
        }
    }

    // ── 公开 API ──────────────────────────────────────────────────

    /**
     * 从 Bitmap 列表创建延时摄影。
     */
    suspend fun createTimelapse(
        images: List<Bitmap>,
        outputPath: String,
        config: Config = Config(),
        onProgress: (Progress) -> Unit = {},
    ): Boolean = withContext(Dispatchers.Default) {
        if (images.isEmpty()) {
            Log.e(TAG, "图像列表为空")
            return@withContext false
        }

        try {
            // 1. 去闪烁
            val processed = if (config.deflicker) {
                onProgress(Progress(Stage.DEFLICKERING, 0f, "去闪烁处理..."))
                deflicker(images) { p ->
                    onProgress(Progress(Stage.DEFLICKERING, p, "去闪烁处理..."))
                }
            } else {
                images
            }

            // 2. 缩放
            val scaled = if (config.resolutionScale != ResolutionScale.ORIGINAL) {
                onProgress(Progress(Stage.PROCESSING, 0f, "缩放处理..."))
                scaleImages(processed, config.resolutionScale) { p ->
                    onProgress(Progress(Stage.PROCESSING, p, "缩放处理..."))
                }
            } else {
                processed
            }

            // 3. 导出
            val success = when (config.outputFormat) {
                ExportFormat.VIDEO_MP4 -> {
                    onProgress(Progress(Stage.ENCODING, 0f, "编码视频..."))
                    encodeVideo(scaled, outputPath, config) { p ->
                        onProgress(Progress(Stage.ENCODING, p, "编码视频..."))
                    }
                }
                ExportFormat.IMAGE_SEQUENCE -> {
                    onProgress(Progress(Stage.EXPORTING, 0f, "导出图像序列..."))
                    exportImageSequence(scaled, outputPath) { p ->
                        onProgress(Progress(Stage.EXPORTING, p, "导出图像序列..."))
                    }
                }
            }

            onProgress(Progress(Stage.EXPORTING, 1f, "完成"))
            success
        } catch (e: CancellationException) {
            throw e
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "Timelapse OOM: ${e.message}", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Timelapse failed: ${e.message}", e)
            false
        }
    }

    /**
     * 从文件路径列表创建延时摄影。
     */
    suspend fun createTimelapseFromFiles(
        filePaths: List<String>,
        outputPath: String,
        config: Config = Config(),
        onProgress: (Progress) -> Unit = {},
    ): Boolean = withContext(Dispatchers.Default) {
        val sortedPaths = filePaths.sorted()
        val images = mutableListOf<Bitmap>()

        for ((i, path) in sortedPaths.withIndex()) {
            onProgress(Progress(Stage.LOADING, i.toFloat() / sortedPaths.size, "加载: $path"))
            val bitmap = runCatching { BitmapFactory.decodeFile(path) }
                .getOrElse { e -> Log.e(TAG, "Failed to load $path: ${e.message}", e); null }
            if (bitmap != null) {
                images.add(bitmap)
            }
        }

        if (images.isEmpty()) {
            Log.e(TAG, "没有成功加载的图像")
            return@withContext false
        }

        createTimelapse(images, outputPath, config, onProgress)
    }

    // ── 去闪烁 ────────────────────────────────────────────────────

    /**
     * 通过归一化平均亮度来消除帧间闪烁。
     */
    private fun deflicker(
        images: List<Bitmap>,
        onProgress: (Float) -> Unit,
    ): List<Bitmap> {
        val n = images.size
        if (n <= 1) return images

        // 计算每帧的平均亮度
        val meanLuminances = FloatArray(n)
        for ((i, img) in images.withIndex()) {
            meanLuminances[i] = computeMeanLuminance(img)
        }

        // 计算全局平均亮度
        val globalMean = meanLuminances.average().toFloat()
        if (globalMean < 1e-6f) return images

        // 应用亮度校正
        val corrected = mutableListOf<Bitmap>()
        for ((i, img) in images.withIndex()) {
            val scale = globalMean / max(meanLuminances[i], 1e-6f)
            val correctedImg = adjustBrightness(img, scale)
            corrected.add(correctedImg)
            onProgress((i + 1).toFloat() / n)
        }

        return corrected
    }

    private fun computeMeanLuminance(bitmap: Bitmap): Float {
        val w = bitmap.width
        val h = bitmap.height
        // 2026 hotfix: 防御 w*h 整数溢出
        val pixelCount = w.toLong() * h.toLong()
        if (pixelCount > Int.MAX_VALUE.toLong()) return 0f
        val count = pixelCount.toInt()
        val pixels = IntArray(count)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        var sum = 0f
        for (p in pixels) {
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            sum += 0.2126f * r + 0.7152f * g + 0.0722f * b
        }

        return sum / count
    }

    private fun adjustBrightness(bitmap: Bitmap, scale: Float): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        // 2026 hotfix: 防御 w*h 整数溢出
        val pixelCount = w.toLong() * h.toLong()
        if (pixelCount > Int.MAX_VALUE.toLong()) return bitmap
        val count = pixelCount.toInt()
        val pixels = IntArray(count)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val outPixels = IntArray(count)

        for (i in pixels.indices) {
            val p = pixels[i]
            val r = ((p shr 16) and 0xFF) * scale
            val g = ((p shr 8) and 0xFF) * scale
            val b = (p and 0xFF) * scale
            val ri = r.toInt().coerceIn(0, 255)
            val gi = g.toInt().coerceIn(0, 255)
            val bi = b.toInt().coerceIn(0, 255)
            outPixels[i] = (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
        }

        result.setPixels(outPixels, 0, w, 0, 0, w, h)
        return result
    }

    // ── 缩放 ──────────────────────────────────────────────────────

    private fun scaleImages(
        images: List<Bitmap>,
        scale: ResolutionScale,
        onProgress: (Float) -> Unit,
    ): List<Bitmap> {
        val scaled = mutableListOf<Bitmap>()
        for ((i, img) in images.withIndex()) {
            val newW = max(1, (img.width * scale.ratio).toInt())
            val newH = max(1, (img.height * scale.ratio).toInt())
            val resized = Bitmap.createScaledBitmap(img, newW, newH, true)
            scaled.add(resized)
            onProgress((i + 1).toFloat() / images.size)
        }
        return scaled
    }

    // ── 视频编码 ──────────────────────────────────────────────────

    private fun encodeVideo(
        images: List<Bitmap>,
        outputPath: String,
        config: Config,
        onProgress: (Float) -> Unit,
    ): Boolean {
        val w = images[0].width
        val h = images[0].height

        return try {
            val muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val format = MediaFormat.createVideoFormat(
                MediaFormat.MIMETYPE_VIDEO_AVC,
                w - (w % 2), // 确保偶数尺寸
                h - (h % 2),
            ).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, config.videoBitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, config.fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, config.keyframeInterval)
            }

            val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val surface = codec.createInputSurface()
            codec.start()

            var trackIndex = -1
            val bufferInfo = MediaCodec.BufferInfo()

            val frameDuration = 1_000_000L / config.fps // 微秒

            for ((i, img) in images.withIndex()) {
                val canvas = surface.lockCanvas(null)
                canvas.drawBitmap(img, 0f, 0f, null)
                surface.unlockCanvasAndPost(canvas)

                // 提取编码输出
                var done = false
                while (!done) {
                    val outputBufferId = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                    when {
                        outputBufferId == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            done = true
                        }
                        outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            trackIndex = muxer.addTrack(codec.outputFormat)
                            muxer.start()
                        }
                        outputBufferId >= 0 -> {
                            val outputBuffer = codec.getOutputBuffer(outputBufferId)
                            if (outputBuffer != null && bufferInfo.size > 0 &&
                                (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                            ) {
                                bufferInfo.presentationTimeUs = i * frameDuration
                                muxer.writeSampleData(trackIndex, outputBuffer, bufferInfo)
                            }
                            codec.releaseOutputBuffer(outputBufferId, false)
                        }
                    }
                }
                onProgress((i + 1).toFloat() / images.size)
            }

            // 发送 EOS
            codec.signalEndOfInputStream()
            var eos = false
            while (!eos) {
                val outputBufferId = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                when {
                    outputBufferId == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        // 继续等待
                    }
                    outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        // 忽略
                    }
                    outputBufferId >= 0 -> {
                        val outputBuffer = codec.getOutputBuffer(outputBufferId)
                        if (outputBuffer != null && bufferInfo.size > 0 &&
                            (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                        ) {
                            muxer.writeSampleData(trackIndex, outputBuffer, bufferInfo)
                        }
                        codec.releaseOutputBuffer(outputBufferId, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            eos = true
                        }
                    }
                }
            }

            codec.stop()
            codec.release()
            muxer.stop()
            muxer.release()
            surface.release()

            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Video encoding failed: ${e.message}", e)
            false
        }
    }

    // ── 图像序列导出 ──────────────────────────────────────────────

    private fun exportImageSequence(
        images: List<Bitmap>,
        outputDir: String,
        onProgress: (Float) -> Unit,
    ): Boolean {
        return try {
            val dir = File(outputDir)
            if (!dir.exists()) dir.mkdirs()

            for ((i, img) in images.withIndex()) {
                val frameFile = File(dir, "frame_${i.toString().padStart(6, '0')}.jpg")
                FileOutputStream(frameFile).use { fos ->
                    img.compress(Bitmap.CompressFormat.JPEG, 95, fos)
                }
                onProgress((i + 1).toFloat() / images.size)
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Image sequence export failed: ${e.message}", e)
            false
        }
    }
}
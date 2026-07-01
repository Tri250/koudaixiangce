package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
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

/**
 * 视频导出处理器 — 从图像序列创建视频（幻灯片或延时摄影）。
 *
 * 完整管线：
 * 1. 创建图像序列视频
 * 2. 使用 Android MediaCodec + MediaMuxer 编码
 * 3. 可配置分辨率、码率、帧率
 * 4. 添加背景音乐
 * 5. 过渡效果（淡入淡出、滑动）
 * 6. 导出为 MP4 (H.264) 或 WEBM (VP8)
 * 7. 进度回调
 */
class VideoExportProcessor {

    companion object {
        private const val TAG = "VideoExportProcessor"
        private const val TIMEOUT_US = 10_000L
        private const val DEFAULT_FPS = 30
        private const val DEFAULT_BITRATE = 8_000_000
    }

    /** 导出格式 */
    enum class VideoFormat(val mimeType: String, val extension: String) {
        MP4_H264(MediaFormat.MIMETYPE_VIDEO_AVC, ".mp4"),
        WEBM_VP8(MediaFormat.MIMETYPE_VIDEO_VP8, ".webm"),
        WEBM_VP9(MediaFormat.MIMETYPE_VIDEO_VP9, ".webm"),
    }

    /** 过渡效果 */
    enum class TransitionEffect {
        NONE,
        FADE,
        SLIDE_LEFT,
        SLIDE_RIGHT,
        SLIDE_UP,
        SLIDE_DOWN,
    }

    /** 配置参数 */
    data class Config(
        val format: VideoFormat = VideoFormat.MP4_H264,
        val width: Int = 1920,
        val height: Int = 1080,
        val fps: Int = DEFAULT_FPS,
        val bitrate: Int = DEFAULT_BITRATE,
        val transitionEffect: TransitionEffect = TransitionEffect.FADE,
        val transitionDurationMs: Long = 500,
        val slideDurationMs: Long = 3000,
        val audioFilePath: String? = null,
        val audioVolume: Float = 0.8f,
        val keyframeInterval: Int = 1,
    )

    /** 进度阶段 */
    enum class Stage {
        LOADING,
        ENCODING,
        MIXING_AUDIO,
        FINALIZING,
    }

    /** 进度回调 */
    data class Progress(
        val stage: Stage,
        val progress: Float,
        val message: String,
    )

    // ── 公开 API ──────────────────────────────────────────────────

    /**
     * 从 Bitmap 列表创建幻灯片视频。
     */
    suspend fun createSlideshow(
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
            onProgress(Progress(Stage.ENCODING, 0f, "编码视频..."))

            val success = encodeSlideshowVideo(images, outputPath, config) { p ->
                onProgress(Progress(Stage.ENCODING, p, "编码视频..."))
            }

            if (!success) {
                return@withContext false
            }

            // 如果有音频，混合音频
            if (config.audioFilePath != null) {
                onProgress(Progress(Stage.MIXING_AUDIO, 0f, "混合音频..."))
                val mixedSuccess = mixAudio(outputPath, config.audioFilePath, config.audioVolume)
                if (!mixedSuccess) {
                    Log.w(TAG, "Audio mixing failed, video without audio")
                }
                onProgress(Progress(Stage.MIXING_AUDIO, 1f, "音频混合完成"))
            }

            onProgress(Progress(Stage.FINALIZING, 1f, "完成"))
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "Video export OOM: ${e.message}", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Video export failed: ${e.message}", e)
            false
        }
    }

    /**
     * 从文件路径列表创建视频。
     */
    suspend fun createVideoFromFiles(
        filePaths: List<String>,
        outputPath: String,
        config: Config = Config(),
        onProgress: (Progress) -> Unit = {},
    ): Boolean = withContext(Dispatchers.Default) {
        val images = mutableListOf<Bitmap>()
        for ((i, path) in filePaths.withIndex()) {
            onProgress(Progress(Stage.LOADING, i.toFloat() / filePaths.size, "加载: $path"))
            val bitmap = runCatching { BitmapFactory.decodeFile(path) }
                .getOrElse { e -> Log.e(TAG, "Failed to load $path: ${e.message}", e); null }
            if (bitmap != null) images.add(bitmap)
        }
        if (images.isEmpty()) {
            Log.e(TAG, "没有成功加载的图像")
            return@withContext false
        }
        createSlideshow(images, outputPath, config, onProgress)
    }

    // ── 视频编码 ──────────────────────────────────────────────────

    private fun encodeSlideshowVideo(
        images: List<Bitmap>,
        outputPath: String,
        config: Config,
        onProgress: (Float) -> Unit,
    ): Boolean {
        val outputWidth = config.width - (config.width % 2)
        val outputHeight = config.height - (config.height % 2)

        return try {
            val muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val format = MediaFormat.createVideoFormat(
                config.format.mimeType,
                outputWidth,
                outputHeight,
            ).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, config.bitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, config.fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, config.keyframeInterval)
            }

            val codec = MediaCodec.createEncoderByType(config.format.mimeType)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val surface = codec.createInputSurface()
            codec.start()

            var trackIndex = -1
            val bufferInfo = MediaCodec.BufferInfo()
            val frameDurationUs = 1_000_000L / config.fps

            val framesPerSlide = ((config.slideDurationMs * config.fps) / 1000).toInt().coerceAtLeast(1)
            val transitionFrames = ((config.transitionDurationMs * config.fps) / 1000).toInt().coerceAtLeast(0)

            var totalFrameIndex = 0L
            val totalFrames = images.size * framesPerSlide.toLong()

            for ((imgIdx, img) in images.withIndex()) {
                val scaledBitmap = Bitmap.createScaledBitmap(img, outputWidth, outputHeight, true)

                val prevBitmap = if (imgIdx > 0 && config.transitionEffect != TransitionEffect.NONE) {
                    Bitmap.createScaledBitmap(images[imgIdx - 1], outputWidth, outputHeight, true)
                } else null

                for (f in 0 until framesPerSlide) {
                    val frameBitmap = if (f < transitionFrames && prevBitmap != null) {
                        val progress = f.toFloat() / transitionFrames.toFloat()
                        renderTransition(prevBitmap, scaledBitmap, config.transitionEffect, progress)
                    } else {
                        scaledBitmap
                    }

                    val canvas = surface.lockCanvas(null)
                    canvas.drawBitmap(frameBitmap, 0f, 0f, null)
                    surface.unlockCanvasAndPost(canvas)

                    // 提取编码输出
                    drainEncoder(codec, muxer, trackIndex, bufferInfo, totalFrameIndex * frameDurationUs) { ti ->
                        trackIndex = ti
                    }

                    totalFrameIndex++
                    onProgress(totalFrameIndex.toFloat() / totalFrames.toFloat())
                }
            }

            // 发送 EOS
            codec.signalEndOfInputStream()
            drainEncoderEos(codec, muxer, trackIndex, bufferInfo)

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

    private fun drainEncoder(
        codec: MediaCodec,
        muxer: MediaMuxer,
        trackIndex: Int,
        bufferInfo: MediaCodec.BufferInfo,
        presentationTimeUs: Long,
        onTrackReady: (Int) -> Unit,
    ): Int {
        var ti = trackIndex
        var done = false
        while (!done) {
            val outputBufferId = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            when {
                outputBufferId == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    done = true
                }
                outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    ti = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    onTrackReady(ti)
                }
                outputBufferId >= 0 -> {
                    val outputBuffer = codec.getOutputBuffer(outputBufferId)
                    if (outputBuffer != null && bufferInfo.size > 0 &&
                        (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                    ) {
                        bufferInfo.presentationTimeUs = presentationTimeUs
                        muxer.writeSampleData(ti, outputBuffer, bufferInfo)
                    }
                    codec.releaseOutputBuffer(outputBufferId, false)
                }
            }
        }
        return ti
    }

    private fun drainEncoderEos(
        codec: MediaCodec,
        muxer: MediaMuxer,
        trackIndex: Int,
        bufferInfo: MediaCodec.BufferInfo,
    ) {
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
    }

    // ── 过渡效果 ──────────────────────────────────────────────────

    private fun renderTransition(
        from: Bitmap,
        to: Bitmap,
        effect: TransitionEffect,
        progress: Float,
    ): Bitmap {
        val w = from.width
        val h = from.height
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint().apply { isAntiAlias = true }

        when (effect) {
            TransitionEffect.NONE -> {
                canvas.drawBitmap(to, 0f, 0f, null)
            }
            TransitionEffect.FADE -> {
                // 淡出 from
                paint.alpha = ((1f - progress) * 255).toInt()
                canvas.drawBitmap(from, 0f, 0f, paint)
                // 淡入 to
                paint.alpha = (progress * 255).toInt()
                canvas.drawBitmap(to, 0f, 0f, paint)
            }
            TransitionEffect.SLIDE_LEFT -> {
                val offsetX = -(w * progress)
                canvas.drawBitmap(from, offsetX, 0f, null)
                canvas.drawBitmap(to, offsetX + w, 0f, null)
            }
            TransitionEffect.SLIDE_RIGHT -> {
                val offsetX = w * progress
                canvas.drawBitmap(from, offsetX, 0f, null)
                canvas.drawBitmap(to, offsetX - w, 0f, null)
            }
            TransitionEffect.SLIDE_UP -> {
                val offsetY = -(h * progress)
                canvas.drawBitmap(from, 0f, offsetY, null)
                canvas.drawBitmap(to, 0f, offsetY + h, null)
            }
            TransitionEffect.SLIDE_DOWN -> {
                val offsetY = h * progress
                canvas.drawBitmap(from, 0f, offsetY, null)
                canvas.drawBitmap(to, 0f, offsetY - h, null)
            }
        }

        return result
    }

    // ── 音频混合 ──────────────────────────────────────────────────

    /**
     * 将背景音乐混合到视频中（简化版 — 仅添加音频轨道）。
     *
     * 注意：这是一个功能框架。完整的音频混合需要使用 MediaExtractor
     * 读取音频文件，然后通过 MediaMuxer 添加到视频中。
     */
    private fun mixAudio(
        videoPath: String,
        audioFilePath: String,
        volume: Float,
    ): Boolean {
        return try {
            val audioFile = File(audioFilePath)
            if (!audioFile.exists()) {
                Log.w(TAG, "Audio file not found: $audioFilePath")
                return false
            }

            // 创建临时输出文件
            val tempOutput = videoPath + ".tmp.mp4"

            val audioExtractor = MediaExtractor()
            audioExtractor.setDataSource(audioFilePath)

            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null

            for (i in 0 until audioExtractor.trackCount) {
                val trackFormat = audioExtractor.getTrackFormat(i)
                val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioExtractor.selectTrack(i)
                    audioTrackIndex = i
                    audioFormat = trackFormat
                    break
                }
            }

            if (audioTrackIndex < 0 || audioFormat == null) {
                audioExtractor.release()
                Log.w(TAG, "No audio track found in $audioFilePath")
                return false
            }

            // 视频提取器
            val videoExtractor = MediaExtractor()
            videoExtractor.setDataSource(videoPath)

            var videoTrackIndex = -1
            var videoFormat: MediaFormat? = null
            for (i in 0 until videoExtractor.trackCount) {
                val trackFormat = videoExtractor.getTrackFormat(i)
                val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/")) {
                    videoExtractor.selectTrack(i)
                    videoTrackIndex = i
                    videoFormat = trackFormat
                    break
                }
            }

            if (videoTrackIndex < 0 || videoFormat == null) {
                videoExtractor.release()
                audioExtractor.release()
                return false
            }

            // 创建输出的 Muxer
            val muxer = MediaMuxer(tempOutput, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val outVideoTrack = muxer.addTrack(videoFormat)
            val outAudioTrack = muxer.addTrack(audioFormat)
            muxer.start()

            // 写入视频轨道
            val bufferInfo = MediaCodec.BufferInfo()
            val buffer = ByteBuffer.allocate(256 * 1024)

            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = videoExtractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) break
                bufferInfo.presentationTimeUs = videoExtractor.sampleTime
                bufferInfo.flags = videoExtractor.sampleFlags
                muxer.writeSampleData(outVideoTrack, buffer, bufferInfo)
                videoExtractor.advance()
            }

            // 写入音频轨道
            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = audioExtractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) break
                bufferInfo.presentationTimeUs = audioExtractor.sampleTime
                bufferInfo.flags = audioExtractor.sampleFlags
                muxer.writeSampleData(outAudioTrack, buffer, bufferInfo)
                audioExtractor.advance()
            }

            muxer.stop()
            muxer.release()
            videoExtractor.release()
            audioExtractor.release()

            // 替换原文件
            val originalFile = File(videoPath)
            originalFile.delete()
            File(tempOutput).renameTo(originalFile)

            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Audio mixing failed: ${e.message}", e)
            false
        }
    }
}
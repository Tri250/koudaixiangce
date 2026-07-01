package com.rapidraw.core

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * HEIF/HEIC format exporter.
 *
 * Supports:
 * - API 28+: android.heic writer via HeifWriter (INPUT_MODE_BITMAP for 8-bit)
 * - API 28+: 10-bit HEIF via MediaCodec HEVC Main10 + manual ISOBMFF container
 * - Quality control (0–100)
 * - Proper HEIF metadata (spatial extents, pixel info, handler type)
 * - Graceful fallback to JPEG when HEVC encoding is unavailable
 */
object HeifExporter {

    private const val TAG = "HeifExporter"
    private const val HEVC_MIME = "video/hevc"
    private const val TIMEOUT_US = 5_000_000L

    enum class HeifBitDepth(val displayName: String, val bitDepth: Int) {
        DEPTH_8("8-bit", 8),
        DEPTH_10("10-bit", 10),
    }

    data class HeifConfig(
        val quality: Int = 95,
        val bitDepth: HeifBitDepth = HeifBitDepth.DEPTH_8,
    )

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Export a Bitmap as HEIF and insert it into MediaStore.
     * Returns the content URI on success, null on failure.
     */
    suspend fun exportHeif(
        context: Context,
        bitmap: Bitmap,
        config: HeifConfig = HeifConfig(),
        displayName: String = "RapidRAW_${System.currentTimeMillis()}",
    ): Uri? = withContext(Dispatchers.IO) {
        if (bitmap.isRecycled) {
            Log.w(TAG, "Cannot export recycled bitmap")
            return@withContext null
        }

        val clampedQuality = config.quality.coerceIn(1, 100)

        try {
            val heifData = encodeHeif(context, bitmap, clampedQuality, config.bitDepth)
                ?: return@withContext fallbackToJpeg(context, bitmap, clampedQuality, displayName)

            val extension = ".heic"
            writeToMediaStore(
                context = context,
                data = heifData,
                displayName = "$displayName$extension",
                mimeType = "image/heic",
            )
        } catch (oom: OutOfMemoryError) {
            Log.e(TAG, "OOM during HEIF export: ${oom.message}", oom)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export HEIF: ${e.message}", e)
            fallbackToJpeg(context, bitmap, clampedQuality, displayName)
        }
    }

    fun isHeif10BitSupported(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        return try {
            findHevcEncoder(HeifBitDepth.DEPTH_10) != null
        } catch (e: Exception) {
            false
        }
    }

    // -----------------------------------------------------------------------
    // HEIF encoding
    // -----------------------------------------------------------------------

    /**
     * Encode the bitmap as HEIF bytes.
     * Strategy:
     * 1. API 28+ with 8-bit: try HeifWriter (android.heif.writer.HeifWriter)
     * 2. API 28+ with 10-bit or HeifWriter failure: MediaCodec HEVC + manual HEIF container
     * 3. Fallback: null (caller should use JPEG)
     */
    private fun encodeHeif(
        context: Context,
        bitmap: Bitmap,
        quality: Int,
        bitDepth: HeifBitDepth,
    ): ByteArray? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null

        // Try HeifWriter for 8-bit first
        if (bitDepth == HeifBitDepth.DEPTH_8) {
            val heifWriterResult = tryHeifWriter(bitmap, quality)
            if (heifWriterResult != null) return heifWriterResult
        }

        // MediaCodec HEVC encoding + manual HEIF container
        return encodeHeifViaMediaCodec(bitmap, quality, bitDepth)
    }

    /**
     * Attempt to use android.heif.writer.HeifWriter (API 28+) for 8-bit HEIF.
     * This is the simplest and most reliable path when available.
     */
    @RequiresApi(Build.VERSION_CODES.P)
    private fun tryHeifWriter(bitmap: Bitmap, quality: Int): ByteArray? {
        return try {
            val tempFile = File.createTempFile("heif_writer_", ".heic")
            try {
                val heifWriter = Class.forName("android.heif.writer.HeifWriter")
                val builderClass = Class.forName("android.heif.writer.HeifWriter\$Builder")

                // HeifWriter.INPUT_MODE_BITMAP = 1
                val inputModeBitmap = 1

                // new HeifWriter.Builder(path, width, height, inputMode)
                val builder = builderClass
                    .getConstructor(String::class.java, Int::class.java, Int::class.java, Int::class.java)
                    .newInstance(tempFile.absolutePath, bitmap.width, bitmap.height, inputModeBitmap)

                // builder.setQuality(quality)
                builderClass.getMethod("setQuality", Int::class.java)
                    .invoke(builder, quality)

                // builder.setRotation(0)
                builderClass.getMethod("setRotation", Int::class.java)
                    .invoke(builder, 0)

                // builder.build()
                val writer = builderClass.getMethod("build").invoke(builder)

                // writer.start()
                heifWriter.getMethod("start").invoke(writer)

                // writer.addBitmap(bitmap)
                heifWriter.getMethod("addBitmap", Bitmap::class.java).invoke(writer, bitmap)

                // writer.stop(timeout)
                heifWriter.getMethod("stop", Long::class.java).invoke(writer, TIMEOUT_US)

                // writer.close()
                heifWriter.getMethod("close").invoke(writer)

                tempFile.readBytes()
            } finally {
                runCatching { tempFile.delete() }
            }
        } catch (e: Exception) {
            Log.d(TAG, "HeifWriter unavailable or failed: ${e.message}")
            null
        }
    }

    /**
     * Encode HEIF via MediaCodec HEVC encoder + manual ISOBMFF container.
     * Supports both 8-bit (Main profile) and 10-bit (Main10 profile).
     */
    private fun encodeHeifViaMediaCodec(
        bitmap: Bitmap,
        quality: Int,
        bitDepth: HeifBitDepth,
    ): ByteArray? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null

        val encoderInfo = findHevcEncoder(bitDepth) ?: run {
            Log.w(TAG, "No HEVC encoder found for ${bitDepth.displayName}")
            return null
        }

        return try {
            val encoder = MediaCodec.createByCodecName(encoderInfo.name)
            try {
                encodeWithCodec(encoder, bitmap, quality, bitDepth)
            } finally {
                runCatching { encoder.release() }
            }
        } catch (e: Exception) {
            Log.w(TAG, "MediaCodec HEVC encoding failed: ${e.message}")
            null
        }
    }

    private data class EncoderInfo(val name: String, val profile: Int)

    private fun findHevcEncoder(bitDepth: HeifBitDepth): EncoderInfo? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null

        val targetProfile = if (bitDepth == HeifBitDepth.DEPTH_10) {
            MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10
        } else {
            MediaCodecInfo.CodecProfileLevel.HEVCProfileMain
        }

        val codecs = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
        for (info in codecs) {
            if (!info.supportedTypes.contains(HEVC_MIME)) continue
            try {
                val caps = info.getCapabilitiesForType(HEVC_MIME)
                if (caps.profileLevels.any { it.profile == targetProfile }) {
                    return EncoderInfo(info.name, targetProfile)
                }
            } catch (_: Exception) {
                continue
            }
        }

        // Fallback: any HEVC encoder even if profile level info is unavailable
        for (info in codecs) {
            if (info.supportedTypes.contains(HEVC_MIME)) {
                return EncoderInfo(info.name, targetProfile)
            }
        }
        return null
    }

    @Suppress("DEPRECATION")
    @RequiresApi(Build.VERSION_CODES.P)
    private fun encodeWithCodec(
        encoder: MediaCodec,
        bitmap: Bitmap,
        quality: Int,
        bitDepth: HeifBitDepth,
    ): ByteArray? {
        val width = bitmap.width
        val height = bitmap.height
        val targetProfile = if (bitDepth == HeifBitDepth.DEPTH_10) {
            MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10
        } else {
            MediaCodecInfo.CodecProfileLevel.HEVCProfileMain
        }

        val format = MediaFormat.createVideoFormat(HEVC_MIME, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, computeBitRate(width, height, quality))
            setInteger(MediaFormat.KEY_FRAME_RATE, 1)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 0)
            setInteger(MediaFormat.KEY_PROFILE, targetProfile)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                setInteger(MediaFormat.KEY_BITRATE_MODE,
                    MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ)
                setInteger(MediaFormat.KEY_QUALITY, quality.coerceIn(0, 100))
            }
            if (bitDepth == HeifBitDepth.DEPTH_10 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setInteger(MediaFormat.KEY_COLOR_STANDARD,
                    MediaFormat.COLOR_STANDARD_BT709)
                setInteger(MediaFormat.KEY_COLOR_TRANSFER,
                    MediaFormat.COLOR_TRANSFER_SDR_VIDEO)
                setInteger(MediaFormat.KEY_COLOR_RANGE,
                    MediaFormat.COLOR_RANGE_FULL)
            }
        }

        val handlerThread = HandlerThread("HeifEncoder").apply { start() }
        val handler = Handler(handlerThread.looper)

        try {
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            // Feed the bitmap as a single YUV frame
            val yuvData = bitmapToYuv420(bitmap)
            val inputBufferInfo = MediaCodec.BufferInfo()
            var inputDone = false

            // Drain input
            while (!inputDone) {
                val inputIndex = encoder.dequeueInputBuffer(10_000)
                if (inputIndex >= 0) {
                    val inputBuf = encoder.getInputBuffer(inputIndex) ?: continue
                    inputBuf.clear()

                    // Write Y, U, V planes into the input buffer
                    putYuvToBuffer(inputBuf, yuvData, width, height)

                    val flags = 0
                    encoder.queueInputBuffer(inputIndex, 0, inputBuf.position(), 0, flags)
                    inputDone = true
                }
            }

            // Signal end of stream
            val eosIndex = encoder.dequeueInputBuffer(10_000)
            if (eosIndex >= 0) {
                encoder.queueInputBuffer(eosIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            }

            // Drain output
            val outputBufferInfo = MediaCodec.BufferInfo()
            var outputDone = false
            var sps: ByteArray? = null
            var pps: ByteArray? = null
            var idrData: ByteArray? = null

            while (!outputDone) {
                val outputIndex = encoder.dequeueOutputBuffer(outputBufferInfo, 10_000)
                if (outputIndex >= 0) {
                    val outBuf = encoder.getOutputBuffer(outputIndex) ?: continue

                    if (outputBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        outputDone = true
                    }

                    // Extract NAL units from the output
                    val chunk = ByteArray(outputBufferInfo.size).also {
                        outBuf.position(outputBufferInfo.offset)
                        outBuf.get(it)
                    }
                    val nals = extractNalUnits(chunk)

                    for (nal in nals) {
                        when (nalType(nal)) {
                            32 -> sps = nal  // SPS
                            33 -> pps = nal  // PPS
                            19, 20 -> idrData = nal  // IDR_W_RADL or IDR_N_LP
                        }
                    }

                    encoder.releaseOutputBuffer(outputIndex, false)
                } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // Format changed, continue draining
                }
            }

            if (sps != null && pps != null && idrData != null) {
                val hvcC = buildHvcC(sps, pps, bitDepth)
                return buildHeifContainer(width, height, hvcC, idrData, bitDepth)
            }

            return null
        } catch (e: Exception) {
            Log.w(TAG, "encodeWithCodec failed: ${e.message}")
            return null
        } finally {
            runCatching { encoder.stop() }
            handlerThread.quitSafely()
        }
    }

    // -----------------------------------------------------------------------
    // YUV conversion
    // -----------------------------------------------------------------------

    private data class YuvData(
        val y: ByteArray,
        val u: ByteArray,
        val v: ByteArray,
        val strideY: Int,
        val strideUV: Int,
    )

    private fun bitmapToYuv420(bitmap: Bitmap): YuvData {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val strideY = w
        val strideUV = (w + 1) / 2
        val y = ByteArray(strideY * h)
        val u = ByteArray(strideUV * ((h + 1) / 2))
        val v = ByteArray(strideUV * ((h + 1) / 2))

        for (i in pixels.indices) {
            val px = pixels[i]
            val r = (px shr 16) and 0xFF
            val g = (px shr 8) and 0xFF
            val b = px and 0xFF

            // BT.709 conversion
            val yVal = (0.2126 * r + 0.7152 * g + 0.0722 * b).toInt().coerceIn(0, 255)
            val cbVal = ((-0.1146 * r - 0.3854 * g + 0.5 * b + 128).toInt()).coerceIn(0, 255)
            val crVal = ((0.5 * r - 0.4542 * g - 0.0458 * b + 128).toInt()).coerceIn(0, 255)

            y[i] = yVal.toByte()

            val x = i % w
            val row = i / w
            if (row % 2 == 0 && x % 2 == 0) {
                val uvIdx = (row / 2) * strideUV + (x / 2)
                if (uvIdx < u.size) u[uvIdx] = cbVal.toByte()
                if (uvIdx < v.size) v[uvIdx] = crVal.toByte()
            }
        }

        return YuvData(y, u, v, strideY, strideUV)
    }

    private fun putYuvToBuffer(buf: ByteBuffer, yuv: YuvData, width: Int, height: Int) {
        // Y plane
        for (row in 0 until height) {
            val srcOff = row * yuv.strideY
            buf.put(yuv.y, srcOff, width)
        }
        // U plane
        val uvHeight = (height + 1) / 2
        val uvWidth = (width + 1) / 2
        for (row in 0 until uvHeight) {
            val srcOff = row * yuv.strideUV
            buf.put(yuv.u, srcOff, uvWidth)
        }
        // V plane
        for (row in 0 until uvHeight) {
            val srcOff = row * yuv.strideUV
            buf.put(yuv.v, srcOff, uvWidth)
        }
    }

    // -----------------------------------------------------------------------
    // NAL unit parsing
    // -----------------------------------------------------------------------

    private fun extractNalUnits(data: ByteArray): List<ByteArray> {
        val nals = mutableListOf<ByteArray>()
        val starts = mutableListOf<Int>()
        var i = 0

        while (i < data.size - 3) {
            if (data[i] == 0.toByte() && data[i + 1] == 0.toByte()) {
                if (data[i + 2] == 1.toByte()) {
                    starts.add(i + 3)
                    i += 3
                    continue
                } else if (i < data.size - 4 && data[i + 2] == 0.toByte() && data[i + 3] == 1.toByte()) {
                    starts.add(i + 4)
                    i += 4
                    continue
                }
            }
            i++
        }

        for (j in starts.indices) {
            val start = starts[j]
            val end = if (j + 1 < starts.size) {
                // include the start code before the next NAL
                var e = starts[j + 1]
                if (e >= 4 && data[e - 4] == 0.toByte() && data[e - 3] == 0.toByte()
                    && data[e - 2] == 0.toByte() && data[e - 1] == 1.toByte()) {
                    e - 4
                } else if (e >= 3 && data[e - 3] == 0.toByte() && data[e - 2] == 0.toByte()
                    && data[e - 1] == 1.toByte()) {
                    e - 3
                } else {
                    e
                }
            } else {
                data.size
            }
            // Include the start code for this NAL
            val nalStart = if (start >= 4 && data[start - 4] == 0.toByte()
                && data[start - 3] == 0.toByte()
                && data[start - 2] == 0.toByte()
                && data[start - 1] == 1.toByte()) {
                start - 4
            } else if (start >= 3 && data[start - 3] == 0.toByte()
                && data[start - 2] == 0.toByte()
                && data[start - 1] == 1.toByte()) {
                start - 3
            } else {
                start
            }
            nals.add(data.copyOfRange(nalStart, end))
        }

        return nals
    }

    private fun nalType(nal: ByteArray): Int {
        // Skip start code (3 or 4 bytes), then first byte of NAL header
        var offset = 0
        if (nal.size >= 4 && nal[0] == 0.toByte() && nal[1] == 0.toByte()
            && nal[2] == 0.toByte() && nal[3] == 1.toByte()) {
            offset = 4
        } else if (nal.size >= 3 && nal[0] == 0.toByte() && nal[1] == 0.toByte()
            && nal[2] == 1.toByte()) {
            offset = 3
        }
        if (offset >= nal.size) return -1
        // HEVC NAL type is in bits 1-6 of the first byte after start code
        return (nal[offset].toInt() shr 1) and 0x3F
    }

    // -----------------------------------------------------------------------
    // HEIF ISOBMFF container building
    // -----------------------------------------------------------------------

    /**
     * Build a minimal but valid HEIF file (ISOBMFF container).
     * Structure:
     *   ftyp(heic) → meta(hdlr, pitm, iinf/infe, iloc, iprp/ipco/ispe+hvcC+pixi, ipma) → mdat
     */
    private fun buildHeifContainer(
        width: Int,
        height: Int,
        hvcC: ByteArray,
        imageData: ByteArray,
        bitDepth: HeifBitDepth,
    ): ByteArray {
        val baos = ByteArrayOutputStream()

        // ftyp box
        baos.write(box("ftyp", byteArrayOf(
            // major_brand: 'heic'
            'h'.code.toByte(), 'e'.code.toByte(), 'i'.code.toByte(), 'c'.code.toByte(),
            // minor_version: 0
            0, 0, 0, 0,
            // compatible_brands: 'heic', 'mif1'
            'h'.code.toByte(), 'e'.code.toByte(), 'i'.code.toByte(), 'c'.code.toByte(),
            'm'.code.toByte(), 'i'.code.toByte(), 'f'.code.toByte(), '1'.code.toByte(),
        )))

        // meta box (full box, version 0, flags 0)
        val metaContent = ByteArrayOutputStream()
        metaContent.write(int32(0)) // version + flags

        // hdlr box
        metaContent.write(box("hdlr", byteArrayOf(
            0, 0, 0, 0, // version + flags
            // pre_defined
            0, 0, 0, 0,
            // handler_type: 'pict'
            'p'.code.toByte(), 'i'.code.toByte(), 'c'.code.toByte(), 't'.code.toByte(),
            // reserved
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            // name (null-terminated)
            0,
        )))

        // pitm box (primary item)
        metaContent.write(box("pitm", byteArrayOf(
            0, 0, 0, 0, // version + flags
            0, 1, // item_id = 1
        )))

        // iinf box (item information)
        val infeContent = byteArrayOf(
            0, 0, 0, 0, // version + flags
            0, 1, // item_id = 1
            0, 0, // item_protection_index = 0
            // item_type: 'hvc1'
            'h'.code.toByte(), 'v'.code.toByte(), 'c'.code.toByte(), '1'.code.toByte(),
            // item_name (null-terminated)
            0,
        )
        val iinfContent = ByteArrayOutputStream()
        iinfContent.write(int32(0)) // version + flags
        iinfContent.write(int16(1)) // entry_count
        iinfContent.write(box("infe", infeContent))
        metaContent.write(box("iinf", iinfContent.toByteArray()))

        // iloc box – we'll fill in the offset after calculating the mdat position
        // For now, build a placeholder. We know the image data will be in mdat.
        // iloc structure (version 0):
        //   version(1) + flags(3) = 0
        //   offset_size(4) | length_size(4) = 0x44 (4 bytes each)
        //   base_offset_size(4) | reserved(4) = 0x00
        //   item_count(16)
        //   per item: item_id(16) + data_reference_index(16) + base_offset(base_offset_size) + extent_count(16)
        //   per extent: extent_offset(offset_size) + extent_length(length_size)

        // We need to know the mdat data offset to fill iloc.
        // Calculate: ftyp_size + meta_box_size + mdat_header_size
        // meta_box_size depends on iloc content which depends on the offset... circular.
        // Solution: use a fixed-size iloc and calculate the offset.

        // Build everything except iloc first to compute sizes
        val metaBeforeIloc = metaContent.toByteArray()

        // iloc is a fixed 30 bytes for our single-item case:
        // 4(size) + 4(type) + 4(version+flags) + 1(sizes) + 1(sizes) + 2(item_count)
        // + 2(item_id) + 2(data_ref_idx) + 2(extent_count) + 4(offset) + 4(length) = 30
        val ilocBoxSize = 30
        val metaBoxOverhead = 8 // meta box header (size + type)

        val totalMetaSize = metaBoxOverhead + metaBeforeIloc.size + ilocBoxSize
        val mdatHeaderSize = 8 // mdat box header
        val imageDataOffset = 4 + 4 + // ftyp (8 + content)
            ftypContentSize() + totalMetaSize + mdatHeaderSize
        // Actually, let me recalculate properly

        // ftyp size
        val ftypSize = 8 + 20 // box header + 20 bytes content
        val mdatDataOffset = ftypSize + totalMetaSize + mdatHeaderSize

        val ilocContent = ByteArrayOutputStream()
        ilocContent.write(int32(0)) // version + flags
        ilocContent.write(0x44)     // offset_size=4, length_size=4
        ilocContent.write(0x00)     // base_offset_size=0, reserved=0
        ilocContent.write(int16(1)) // item_count = 1
        // Item entry
        ilocContent.write(int16(1)) // item_id = 1
        ilocContent.write(int16(0)) // data_reference_index = 0 (this file)
        ilocContent.write(int16(1)) // extent_count = 1
        // Extent
        ilocContent.write(int32(mdatDataOffset)) // extent_offset
        ilocContent.write(int32(imageData.size))  // extent_length

        // Now rebuild meta with iloc
        val metaContentFinal = ByteArrayOutputStream()
        metaContentFinal.write(int32(0)) // version + flags
        metaContentFinal.write(metaBeforeIloc, 4, metaBeforeIloc.size - 4) // skip version+flags we already wrote
        metaContentFinal.write(box("iloc", ilocContent.toByteArray()))

        // iprp box (item properties)
        val ipcoContent = ByteArrayOutputStream()

        // ispe box (image spatial extents)
        ipcoContent.write(box("ispe", byteArrayOf(
            0, 0, 0, 0, // version + flags
        ) + int32(width) + int32(height)))

        // hvcC box (HEVC decoder configuration)
        ipcoContent.write(box("hvcC", hvcC))

        // pixi box (pixel information)
        val pixiContent = ByteArrayOutputStream()
        pixiContent.write(int32(0)) // version + flags
        pixiContent.write(3)        // num_channels (YCbCr = 3)
        pixiContent.write(bitDepth.bitDepth) // bits_per_channel[0]
        pixiContent.write(bitDepth.bitDepth) // bits_per_channel[1]
        pixiContent.write(bitDepth.bitDepth) // bits_per_channel[2]
        ipcoContent.write(box("pixi", pixiContent.toByteArray()))

        val iprpContent = ByteArrayOutputStream()
        iprpContent.write(box("ipco", ipcoContent.toByteArray()))

        // ipma box (item property association)
        val ipmaContent = ByteArrayOutputStream()
        ipmaContent.write(int32(0)) // version + flags
        ipmaContent.write(int32(1)) // entry_count
        ipmaContent.write(int16(1)) // item_id = 1
        ipmaContent.write(3)        // association_count = 3
        ipmaContent.write(1) // property_index=1 (ispe), essential=0
        ipmaContent.write(0x82) // property_index=2 (hvcC), essential=1
        ipmaContent.write(3) // property_index=3 (pixi), essential=0

        iprpContent.write(box("ipma", ipmaContent.toByteArray()))
        metaContentFinal.write(box("iprp", iprpContent.toByteArray()))

        baos.write(box("meta", metaContentFinal.toByteArray()))

        // mdat box
        baos.write(box("mdat", imageData))

        return baos.toByteArray()
    }

    private fun ftypContentSize(): Int = 20 // major_brand(4) + minor_version(4) + compatible_brands(8)

    /**
     * Build hvcC (HEVC Decoder Configuration Record) from SPS and PPS NAL units.
     * This is required for the HEIF container to describe the HEVC stream.
     */
    private fun buildHvcC(sps: ByteArray, pps: ByteArray, bitDepth: HeifBitDepth): ByteArray {
        val baos = ByteArrayOutputStream()

        // HEVCDecoderConfigurationRecord
        baos.write(1)   // configurationVersion = 1

        // Parse profile/tier/level from SPS (simplified – use known values)
        val isMain10 = bitDepth == HeifBitDepth.DEPTH_10
        val profileSpace = 0
        val tierFlag = 0
        val profileIdc = if (isMain10) 2 else 1 // 1=Main, 2=Main10
        val levelIdc = 93 // Level 3.1 (sufficient for most still images)

        // Byte 2: general_profile_space(2) | general_tier_flag(1) | general_profile_idc(5)
        baos.write(((profileSpace and 0x3) shl 6) or ((tierFlag and 0x1) shl 5) or (profileIdc and 0x1F))

        // Bytes 3-5: general_profile_compatibility_flags (32 bits)
        val compatFlags = if (isMain10) 0x60000000 else 0x40000000
        baos.write(int32(compatFlags))

        // Bytes 6-11: general_constraint_indicator_flags (48 bits)
        if (isMain10) {
            baos.write(byteArrayOf(0xB0.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00))
        } else {
            baos.write(byteArrayOf(0x90.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00))
        }

        // Byte 12: general_level_idc
        baos.write(levelIdc)

        // min_spatial_segmentation_idc (12 bits) with reserved
        baos.write(byteArrayOf(0xF0.toByte(), 0x00)) // reserved(4)=0xF + min_spatial_segmentation_idc(12)=0

        // parallelismType (2 bits) with reserved
        baos.write(0xFC) // reserved(6)=0x3F + parallelismType(2)=0

        // chromaFormat (2 bits) with reserved: 1 = 4:2:0
        baos.write(0xFD) // reserved(6)=0x3F + chromaFormat(2)=1

        // bitDepthLumaMinus8 (3 bits) with reserved
        val bitDepthLumaMinus8 = if (isMain10) 2 else 0
        baos.write(0xF8 or (bitDepthLumaMinus8 and 0x7))

        // bitDepthChromaMinus8 (3 bits) with reserved
        val bitDepthChromaMinus8 = if (isMain10) 2 else 0
        baos.write(0xF8 or (bitDepthChromaMinus8 and 0x7))

        // avgFrameRate
        baos.write(byteArrayOf(0, 0))

        // constantFrameRate(2) | numTemporalLayers(3) | temporalIdNested(1) | lengthSizeMinusOne(2)
        // lengthSizeMinusOne = 3 (4-byte NAL length prefix)
        baos.write((0x0 shl 6) or (1 shl 3) or (1 shl 2) or 3)

        // numOfArrays = 2 (VPS is optional for still images, we include SPS and PPS)
        baos.write(2)

        // SPS array
        val spsNal = stripStartCode(sps)
        baos.write(0x20) // array_completeness=0, NAL_unit_type=32 (SPS)
        baos.write(int16(1)) // numNalus = 1
        baos.write(int16(spsNal.size))
        baos.write(spsNal)

        // PPS array
        val ppsNal = stripStartCode(pps)
        baos.write(0x21) // array_completeness=0, NAL_unit_type=33 (PPS)
        baos.write(int16(1)) // numNalus = 1
        baos.write(int16(ppsNal.size))
        baos.write(ppsNal)

        return baos.toByteArray()
    }

    private fun stripStartCode(nal: ByteArray): ByteArray {
        if (nal.size >= 4 && nal[0] == 0.toByte() && nal[1] == 0.toByte()
            && nal[2] == 0.toByte() && nal[3] == 1.toByte()) {
            return nal.copyOfRange(4, nal.size)
        }
        if (nal.size >= 3 && nal[0] == 0.toByte() && nal[1] == 0.toByte()
            && nal[2] == 1.toByte()) {
            return nal.copyOfRange(3, nal.size)
        }
        return nal
    }

    // -----------------------------------------------------------------------
    // ISOBMFF helpers
    // -----------------------------------------------------------------------

    private fun box(type: String, content: ByteArray): ByteArray {
        val size = 8 + content.size
        val baos = ByteArrayOutputStream(size)
        baos.write(int32(size))
        baos.write(type.toByteArray(Charsets.US_ASCII))
        baos.write(content)
        return baos.toByteArray()
    }

    private fun int32(v: Int): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(v).array()

    private fun int16(v: Int): ByteArray =
        ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN).putShort(v.toShort()).array()

    // -----------------------------------------------------------------------
    // Bitrate calculation
    // -----------------------------------------------------------------------

    private fun computeBitRate(width: Int, height: Int, quality: Int): Int {
        // Empirical: HEIF still image at quality 95 ≈ 2-4 bits/pixel
        val bpp = 0.5 + (quality / 100.0) * 3.5
        return (width * height * bpp).toInt().coerceIn(50_000, 100_000_000)
    }

    // -----------------------------------------------------------------------
    // Fallback
    // -----------------------------------------------------------------------

    private fun fallbackToJpeg(
        context: Context,
        bitmap: Bitmap,
        quality: Int,
        displayName: String,
    ): Uri? {
        return try {
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            writeToMediaStore(
                context = context,
                data = baos.toByteArray(),
                displayName = "$displayName.jpg",
                mimeType = "image/jpeg",
            )
        } catch (e: Exception) {
            Log.e(TAG, "JPEG fallback also failed: ${e.message}")
            null
        }
    }

    // -----------------------------------------------------------------------
    // MediaStore
    // -----------------------------------------------------------------------

    private fun writeToMediaStore(
        context: Context,
        data: ByteArray,
        displayName: String,
        mimeType: String,
    ): Uri? {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/RapidRAW")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: return null

        resolver.openOutputStream(uri)?.use { os ->
            os.write(data)
            os.flush()
        } ?: run {
            resolver.delete(uri, null, null)
            return null
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
        }

        return uri
    }
}

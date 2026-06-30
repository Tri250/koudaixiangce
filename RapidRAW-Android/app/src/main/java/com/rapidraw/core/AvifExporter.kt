package com.rapidraw.core

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * AVIF format exporter.
 *
 * Encoding strategy (in order of preference):
 * 1. API 34+: Bitmap.CompressFormat.AVIF – native Android AVIF encoder
 * 2. API 31+: MediaCodec AV1 encoder + manual AVIF/ISOBMFF container construction
 * 3. Fallback: WebP with quality mapping
 *
 * Supported features:
 * - Lossy and lossless encoding modes
 * - Chroma subsampling: 4:4:4, 4:2:2, 4:2:0
 * - Quality control (0–100) for lossy mode
 * - Proper AVIF metadata (spatial extents, pixel info, AV1 codec configuration)
 */
class AvifExporter {

    companion object {
        private const val TAG = "AvifExporter"
        private const val AV1_MIME = "video/av01"
        private const val TIMEOUT_US = 5_000_000L

        fun isNativeAvifSupported(): Boolean = Build.VERSION.SDK_INT >= 34
        fun isAvifDecodeSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }

    enum class ChromaSubsampling(val label: String, val subsamplingX: Int, val subsamplingY: Int) {
        CS_444("4:4:4", 0, 0),
        CS_422("4:2:2", 1, 0),
        CS_420("4:2:0", 1, 1),
    }

    data class AvifExportConfig(
        val quality: Int = 90,
        val lossless: Boolean = false,
        val chromaSubsampling: ChromaSubsampling = ChromaSubsampling.CS_420,
        val includeExif: Boolean = true,
        val includeXmp: Boolean = true,
    )

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    suspend fun exportAvif(
        context: Context,
        bitmap: Bitmap,
        displayName: String,
        config: AvifExportConfig = AvifExportConfig(),
    ): Uri? = withContext(Dispatchers.IO) {
        if (bitmap.isRecycled) {
            Log.e(TAG, "Cannot export recycled bitmap")
            return@withContext null
        }

        try {
            val data = encodeAvif(bitmap, config)
                ?: return@withContext fallbackToWebp(context, bitmap, displayName, config)

            writeToMediaStore(context, data, displayName, "image/avif")
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OOM during AVIF export", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "AVIF export failed: ${e.message}")
            fallbackToWebp(context, bitmap, displayName, config)
        }
    }

    // -----------------------------------------------------------------------
    // AVIF encoding
    // -----------------------------------------------------------------------

    private fun encodeAvif(bitmap: Bitmap, config: AvifExportConfig): ByteArray? {
        // Path 1: API 34+ native AVIF compression
        val nativeResult = tryNativeAvif(bitmap, config)
        if (nativeResult != null) return nativeResult

        // Path 2: API 31+ MediaCodec AV1 + manual AVIF container
        val codecResult = tryCodecAvif(bitmap, config)
        if (codecResult != null) return codecResult

        Log.i(TAG, "All AVIF encoding paths failed, falling back to WebP")
        return null
    }

    /**
     * API 34+: Use Bitmap.CompressFormat.AVIF directly.
     * This is the highest quality and most compatible path.
     */
    private fun tryNativeAvif(bitmap: Bitmap, config: AvifExportConfig): ByteArray? {
        if (Build.VERSION.SDK_INT < 34) {
            Log.d(TAG, "Native AVIF requires API 34+, current: ${Build.VERSION.SDK_INT}")
            return null
        }

        return try {
            val compressFormat = getAvifCompressFormat() ?: return null
            val outputStream = ByteArrayOutputStream()

            val quality = if (config.lossless) 100 else config.quality.coerceIn(1, 100)
            bitmap.compress(compressFormat, quality, outputStream)
            outputStream.toByteArray()
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "OOM encoding AVIF natively: ${e.message}")
            null
        } catch (e: Exception) {
            Log.w(TAG, "Native AVIF encoding failed: ${e.message}")
            null
        }
    }

    private fun getAvifCompressFormat(): Bitmap.CompressFormat? {
        return try {
            val field = Bitmap.CompressFormat::class.java.getField("AVIF")
            field.get(null) as Bitmap.CompressFormat
        } catch (_: NoSuchFieldException) {
            Log.d(TAG, "CompressFormat.AVIF not found on this device")
            null
        } catch (e: Exception) {
            Log.d(TAG, "Failed to access CompressFormat.AVIF: ${e.message}")
            null
        }
    }

    /**
     * API 31+: Encode AVIF via MediaCodec AV1 encoder + manual ISOBMFF container.
     * This works on devices with hardware AV1 encoders (increasingly common).
     */
    private fun tryCodecAvif(bitmap: Bitmap, config: AvifExportConfig): ByteArray? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            Log.d(TAG, "MediaCodec AVIF requires API 31+, current: ${Build.VERSION.SDK_INT}")
            return null
        }

        val encoderName = findAv1Encoder() ?: run {
            Log.d(TAG, "No AV1 encoder found on this device")
            return null
        }

        return try {
            val encoder = MediaCodec.createByCodecName(encoderName)
            try {
                encodeAv1Frame(encoder, bitmap, config)
            } finally {
                runCatching { encoder.release() }
            }
        } catch (e: Exception) {
            Log.w(TAG, "MediaCodec AV1 encoding failed: ${e.message}")
            null
        }
    }

    private fun findAv1Encoder(): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null

        val codecs = MediaCodec.listEncoders() ?: return null
        for (info in codecs) {
            if (info.supportedTypes.contains(AV1_MIME)) {
                return info.name
            }
        }
        return null
    }

    /**
     * Encode a single AV1 frame using MediaCodec and wrap it in an AVIF container.
     */
    private fun encodeAv1Frame(
        encoder: MediaCodec,
        bitmap: Bitmap,
        config: AvifExportConfig,
    ): ByteArray? {
        val width = bitmap.width
        val height = bitmap.height

        val format = MediaFormat.createVideoFormat(AV1_MIME, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, computeAvifBitRate(width, height, config))
            setInteger(MediaFormat.KEY_FRAME_RATE, 1)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 0)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (config.lossless) {
                    setInteger(MediaFormat.KEY_BITRATE_MODE,
                        MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ)
                    setInteger(MediaFormat.KEY_QUALITY, 100)
                } else {
                    setInteger(MediaFormat.KEY_BITRATE_MODE,
                        MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setInteger(MediaFormat.KEY_COLOR_STANDARD,
                    MediaFormat.COLOR_STANDARD_BT709)
                setInteger(MediaFormat.KEY_COLOR_TRANSFER,
                    MediaFormat.COLOR_TRANSFER_SDR_VIDEO)
                setInteger(MediaFormat.KEY_COLOR_RANGE,
                    MediaFormat.COLOR_RANGE_FULL)
            }

            // Request AV1 profile based on chroma subsampling
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val profile = when (config.chromaSubsampling) {
                    ChromaSubsampling.CS_444 -> 2  // AV1ProfileMain444
                    ChromaSubsampling.CS_422 -> 4  // AV1ProfileMain422
                    ChromaSubsampling.CS_420 -> 1  // AV1ProfileMain
                }
                setInteger(MediaFormat.KEY_PROFILE, profile)
            }
        }

        try {
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            // Feed the bitmap as YUV
            val yuvData = bitmapToYuv420(bitmap, config.chromaSubsampling)
            var inputDone = false

            while (!inputDone) {
                val inputIndex = encoder.dequeueInputBuffer(10_000)
                if (inputIndex >= 0) {
                    val inputBuf = encoder.getInputBuffer(inputIndex) ?: continue
                    inputBuf.clear()
                    putYuvToBuffer(inputBuf, yuvData, width, height)
                    encoder.queueInputBuffer(inputIndex, 0, inputBuf.position(), 0, 0)
                    inputDone = true
                }
            }

            // Signal EOS
            val eosIndex = encoder.dequeueInputBuffer(10_000)
            if (eosIndex >= 0) {
                encoder.queueInputBuffer(eosIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            }

            // Drain output
            val bufferInfo = MediaCodec.BufferInfo()
            var outputDone = false
            val encodedChunks = mutableListOf<ByteArray>()
            var outputFormat: MediaFormat? = null

            while (!outputDone) {
                val outputIndex = encoder.dequeueOutputBuffer(bufferInfo, 10_000)
                when {
                    outputIndex >= 0 -> {
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                        val outBuf = encoder.getOutputBuffer(outputIndex) ?: continue
                        val chunk = ByteArray(bufferInfo.size).also {
                            outBuf.position(bufferInfo.offset)
                            outBuf.get(it)
                        }
                        encodedChunks.add(chunk)
                        encoder.releaseOutputBuffer(outputIndex, false)
                    }
                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        outputFormat = encoder.outputFormat
                    }
                }
            }

            if (encodedChunks.isEmpty()) return null

            // Combine all output chunks into one OBU stream
            val obuData = encodedChunks.reduce { acc, bytes -> acc + bytes }

            // Build AV1 codec configuration from output format
            val av1C = buildAv1C(config, width, height)
            return buildAvifContainer(width, height, av1C, obuData, config)
        } catch (e: Exception) {
            Log.w(TAG, "AV1 frame encoding failed: ${e.message}")
            return null
        } finally {
            runCatching { encoder.stop() }
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

    private fun bitmapToYuv420(bitmap: Bitmap, subsampling: ChromaSubsampling): YuvData {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val strideY = w
        val uvW = (w + subsampling.subsamplingX) / (1 + subsampling.subsamplingX)
        val uvH = (h + subsampling.subsamplingY) / (1 + subsampling.subsamplingY)
        val strideUV = uvW

        val y = ByteArray(strideY * h)
        val u = ByteArray(strideUV * uvH)
        val v = ByteArray(strideUV * uvH)

        for (i in pixels.indices) {
            val px = pixels[i]
            val r = (px shr 16) and 0xFF
            val g = (px shr 8) and 0xFF
            val b = px and 0xFF

            // BT.709
            val yVal = (0.2126 * r + 0.7152 * g + 0.0722 * b).toInt().coerceIn(0, 255)
            y[i] = yVal.toByte()

            val x = i % w
            val row = i / w
            if (row % (1 + subsampling.subsamplingY) == 0 &&
                x % (1 + subsampling.subsamplingX) == 0) {
                val cbVal = ((-0.1146 * r - 0.3854 * g + 0.5 * b + 128).toInt()).coerceIn(0, 255)
                val crVal = ((0.5 * r - 0.4542 * g - 0.0458 * b + 128).toInt()).coerceIn(0, 255)
                val uvIdx = (row / (1 + subsampling.subsamplingY)) * strideUV +
                    (x / (1 + subsampling.subsamplingX))
                if (uvIdx < u.size) u[uvIdx] = cbVal.toByte()
                if (uvIdx < v.size) v[uvIdx] = crVal.toByte()
            }
        }

        return YuvData(y, u, v, strideY, strideUV)
    }

    private fun putYuvToBuffer(buf: ByteBuffer, yuv: YuvData, width: Int, height: Int) {
        for (row in 0 until height) {
            buf.put(yuv.y, row * yuv.strideY, width)
        }
        val uvHeight = (height + 1) / 2
        val uvWidth = (width + 1) / 2
        for (row in 0 until uvHeight) {
            buf.put(yuv.u, row * yuv.strideUV, uvWidth)
        }
        for (row in 0 until uvHeight) {
            buf.put(yuv.v, row * yuv.strideUV, uvWidth)
        }
    }

    // -----------------------------------------------------------------------
    // AVIF ISOBMFF container
    // -----------------------------------------------------------------------

    /**
     * Build a minimal but valid AVIF file (ISOBMFF container with AV1 codec).
     *
     * Structure:
     *   ftyp(avif) → meta(hdlr, pitm, iinf/infe, iloc, iprp/ipco/ispe+av1C+pixi+colr, ipma) → mdat
     */
    private fun buildAvifContainer(
        width: Int,
        height: Int,
        av1C: ByteArray,
        obuData: ByteArray,
        config: AvifExportConfig,
    ): ByteArray {
        val baos = ByteArrayOutputStream()

        // --- ftyp box ---
        baos.write(box("ftyp", byteArrayOf(
            // major_brand: 'avif'
            'a'.code.toByte(), 'v'.code.toByte(), 'i'.code.toByte(), 'f'.code.toByte(),
            // minor_version: 0
            0, 0, 0, 0,
            // compatible_brands: 'avif', 'mif1', 'msf1'
            'a'.code.toByte(), 'v'.code.toByte(), 'i'.code.toByte(), 'f'.code.toByte(),
            'm'.code.toByte(), 'i'.code.toByte(), 'f'.code.toByte(), '1'.code.toByte(),
        )))

        // --- meta box ---
        val metaContent = ByteArrayOutputStream()
        metaContent.write(int32(0)) // version + flags

        // hdlr box
        metaContent.write(box("hdlr", byteArrayOf(
            0, 0, 0, 0, // version + flags
            0, 0, 0, 0, // pre_defined
            'p'.code.toByte(), 'i'.code.toByte(), 'c'.code.toByte(), 't'.code.toByte(), // handler_type: pict
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, // reserved
            0, // name (null)
        )))

        // pitm box
        metaContent.write(box("pitm", byteArrayOf(
            0, 0, 0, 0, // version + flags
            0, 1, // item_id = 1
        )))

        // iinf box
        val infeContent = byteArrayOf(
            0, 0, 0, 0, // version + flags
            0, 1, // item_id = 1
            0, 0, // item_protection_index
            // item_type: 'av01'
            'a'.code.toByte(), 'v'.code.toByte(), '0'.code.toByte(), '1'.code.toByte(),
            0, // name (null)
        )
        val iinfContent = ByteArrayOutputStream()
        iinfContent.write(int32(0)) // version + flags
        iinfContent.write(int16(1)) // entry_count
        iinfContent.write(box("infe", infeContent))
        metaContent.write(box("iinf", iinfContent.toByteArray()))

        // iloc box - placeholder offset, will be recalculated
        // First pass: calculate sizes without iloc to determine data offset
        val metaBeforeIloc = metaContent.toByteArray()
        val ilocBoxSize = 30 // fixed size for single-item iloc
        val metaBoxOverhead = 8
        val ftypSize = 8 + 20 // box header + content
        val totalMetaSize = metaBoxOverhead + metaBeforeIloc.size + ilocBoxSize +
            estimatePropertyBoxesSize(av1C.size)
        val mdatHeaderSize = 8
        val mdatDataOffset = ftypSize + totalMetaSize + mdatHeaderSize

        // iloc content
        val ilocContent = ByteArrayOutputStream()
        ilocContent.write(int32(0)) // version + flags
        ilocContent.write(0x44)     // offset_size=4, length_size=4
        ilocContent.write(0x00)     // base_offset_size=0, reserved
        ilocContent.write(int16(1)) // item_count = 1
        ilocContent.write(int16(1)) // item_id = 1
        ilocContent.write(int16(0)) // data_reference_index = 0
        ilocContent.write(int16(1)) // extent_count = 1
        ilocContent.write(int32(mdatDataOffset))
        ilocContent.write(int32(obuData.size))

        // Rebuild meta with iloc
        val metaContentFinal = ByteArrayOutputStream()
        metaContentFinal.write(int32(0)) // version + flags
        metaContentFinal.write(metaBeforeIloc, 4, metaBeforeIloc.size - 4)
        metaContentFinal.write(box("iloc", ilocContent.toByteArray()))

        // iprp box (item properties)
        val ipcoContent = ByteArrayOutputStream()

        // ispe box (image spatial extents)
        ipcoContent.write(box("ispe", byteArrayOf(
            0, 0, 0, 0, // version + flags
        ) + int32(width) + int32(height)))

        // av1C box (AV1 codec configuration)
        ipcoContent.write(box("av1C", av1C))

        // pixi box (pixel information)
        val pixiContent = ByteArrayOutputStream()
        pixiContent.write(int32(0)) // version + flags
        pixiContent.write(3)        // num_channels
        pixiContent.write(8)        // bits_per_channel[0]
        pixiContent.write(8)        // bits_per_channel[1]
        pixiContent.write(8)        // bits_per_channel[2]
        ipcoContent.write(box("pixi", pixiContent.toByteArray()))

        // colr box (color information) - BT.709 with sRGB transfer
        val colrContent = ByteArrayOutputStream()
        colrContent.write("nclx".toByteArray(Charsets.US_ASCII))
        colrContent.write(int16(1))  // colour_primaries = BT.709
        colrContent.write(int16(13)) // transfer_characteristics = sRGB
        colrContent.write(int16(1))  // matrix_coefficients = BT.709
        colrContent.write(0x80)      // full_range_flag = 1 (full range)
        ipcoContent.write(box("colr", colrContent.toByteArray()))

        val iprpContent = ByteArrayOutputStream()
        iprpContent.write(box("ipco", ipcoContent.toByteArray()))

        // ipma box (item property association)
        val ipmaContent = ByteArrayOutputStream()
        ipmaContent.write(int32(0)) // version + flags
        ipmaContent.write(int32(1)) // entry_count
        ipmaContent.write(int16(1)) // item_id = 1
        ipmaContent.write(4)        // association_count = 4
        ipmaContent.write(0x81.toByte()) // essential=1, property_index=1 (ispe)
        ipmaContent.write(0x82.toByte()) // essential=1, property_index=2 (av1C)
        ipmaContent.write(0x03.toByte()) // essential=0, property_index=3 (pixi)
        ipmaContent.write(0x04.toByte()) // essential=0, property_index=4 (colr)

        iprpContent.write(box("ipma", ipmaContent.toByteArray()))
        metaContentFinal.write(box("iprp", iprpContent.toByteArray()))

        baos.write(box("meta", metaContentFinal.toByteArray()))

        // --- mdat box ---
        baos.write(box("mdat", obuData))

        return baos.toByteArray()
    }

    /**
     * Build the av1C box content (AV1 Codec Configuration Record).
     * This follows the AV1-ISOBMFF specification.
     */
    private fun buildAv1C(config: AvifExportConfig, width: Int, height: Int): ByteArray {
        val baos = ByteArrayOutputStream()
        val subsampling = config.chromaSubsampling

        // marker (1 bit) = 1
        // version (7 bits) = 1
        baos.write(0x81)

        // seq_profile (3 bits): 0=Main(420), 1=High(444), 2=Professional(422)
        val profile = when (subsampling) {
            ChromaSubsampling.CS_420 -> 0
            ChromaSubsampling.CS_444 -> 1
            ChromaSubsampling.CS_422 -> 2
        }

        // seq_level_idx_0 (5 bits): level based on image size
        val level = computeAv1Level(width, height)

        // Byte 2: seq_profile(3) | seq_level_idx_0(5)
        baos.write(((profile and 0x7) shl 5) or (level and 0x1F))

        // Byte 3: seq_tier_0(1) | high_bitdepth(1) | twelve_bit(1) | monochrome(1) |
        //          chroma_subsampling_x(1) | chroma_subsampling_y(1) | chroma_sample_position(2)
        val highBitdepth = if (config.lossless) 0 else 0 // 8-bit
        val twelveBit = 0
        val monochrome = 0
        val chromaSamplePosition = 0 // CSP_UNKNOWN

        baos.write(
            ((0 and 0x1) shl 7) or       // seq_tier_0 = 0
            ((highBitdepth and 0x1) shl 6) or
            ((twelveBit and 0x1) shl 5) or
            ((monochrome and 0x1) shl 4) or
            ((subsampling.subsamplingX and 0x1) shl 3) or
            ((subsampling.subsamplingY and 0x1) shl 2) or
            (chromaSamplePosition and 0x3)
        )

        // Byte 4: reserved(3) | initial_presentation_delay_present(1) |
        //         initial_presentation_delay_minus_one(4)
        baos.write(0x00) // no initial presentation delay

        // configOBUs - for still image AVIF, we don't include additional OBUs here
        // The actual frame data goes in mdat

        return baos.toByteArray()
    }

    private fun computeAv1Level(width: Int, height: Int): Int {
        val pixels = width.toLong() * height.toLong()
        return when {
            pixels <= 147456 -> 0   // Level 2.0
            pixels <= 278784 -> 1   // Level 2.1
            pixels <= 614400 -> 4   // Level 3.0
            pixels <= 921600 -> 5   // Level 3.1
            pixels <= 1474560 -> 8  // Level 4.0
            pixels <= 2211840 -> 9  // Level 4.1
            pixels <= 3565152 -> 12 // Level 5.0
            pixels <= 5308416 -> 13 // Level 5.1
            pixels <= 8912896 -> 16 // Level 6.0
            pixels <= 13369344 -> 17 // Level 6.1
            else -> 18              // Level 6.2
        }
    }

    private fun estimatePropertyBoxesSize(av1CSize: Int): Int {
        // ispe(20) + av1C(8+av1CSize) + pixi(16) + colr(18) + ipco(8) + ipma(22) + iprp(8)
        return 20 + 8 + av1CSize + 16 + 18 + 8 + 22 + 8
    }

    // -----------------------------------------------------------------------
    // Bitrate
    // -----------------------------------------------------------------------

    private fun computeAvifBitRate(width: Int, height: Int, config: AvifExportConfig): Int {
        if (config.lossless) {
            // Lossless: very high bitrate to ensure no information loss
            return width * height * 24 // 24 bits per pixel (RGB)
        }
        // Lossy: quality-based bitrate estimation
        val bpp = 0.3 + (config.quality / 100.0) * 2.7
        return (width * height * bpp).toInt().coerceIn(50_000, 100_000_000)
    }

    // -----------------------------------------------------------------------
    // WebP fallback
    // -----------------------------------------------------------------------

    private suspend fun fallbackToWebp(
        context: Context,
        bitmap: Bitmap,
        displayName: String,
        config: AvifExportConfig,
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val outputStream = ByteArrayOutputStream()
            val quality = if (config.lossless) 100 else config.quality.coerceIn(1, 100)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val format = if (config.lossless) {
                    Bitmap.CompressFormat.WEBP_LOSSLESS
                } else {
                    Bitmap.CompressFormat.WEBP_LOSSY
                }
                bitmap.compress(format, quality, outputStream)
            } else {
                @Suppress("DEPRECATION")
                bitmap.compress(Bitmap.CompressFormat.WEBP, quality, outputStream)
            }
            val data = outputStream.toByteArray()
            writeToMediaStore(context, data, "${displayName}_webp", "image/webp")
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OOM during WebP fallback", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "WebP fallback also failed: ${e.message}")
            null
        }
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
    // MediaStore
    // -----------------------------------------------------------------------

    private fun writeToMediaStore(
        context: Context,
        data: ByteArray,
        displayName: String,
        mimeType: String,
    ): Uri? {
        val extension = when (mimeType) {
            "image/avif" -> ".avif"
            "image/webp" -> ".webp"
            else -> ""
        }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$displayName$extension")
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/RapidRAW")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return null

        context.contentResolver.openOutputStream(uri)?.use { os ->
            os.write(data)
            os.flush()
        } ?: run {
            context.contentResolver.delete(uri, null, null)
            return null
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
        }

        return uri
    }
}

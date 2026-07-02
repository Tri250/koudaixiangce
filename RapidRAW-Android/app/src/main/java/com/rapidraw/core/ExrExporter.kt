package com.rapidraw.core

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Deflater

/**
 * Pure Kotlin OpenEXR encoder.
 * Implements the OpenEXR binary format specification (version 2, single-part scanline).
 *
 * Supported features:
 * - 16-bit half-float (PixelType.HALF) and 32-bit float (PixelType.FLOAT) pixel data
 * - RGB (3-channel) and RGBA (4-channel) output
 * - No compression, ZIPS (per-scanline deflate), and ZIP (32-scanline block deflate)
 * - Scanline-based file layout with proper offset table
 * - Correct OpenEXR header with channel list, data/display windows, and line order
 */
object ExrExporter {

    enum class PixelType(val code: Int) {
        HALF(1),
        FLOAT(2),
    }

    enum class Compression(val code: Int, val scanlinesPerBlock: Int) {
        NONE(0, 1),
        ZIPS(2, 1),
        ZIP(3, 32),
    }

    enum class ChannelMode(val channels: List<String>) {
        // EXR channels must be listed alphabetically in the header
        RGB(listOf("B", "G", "R")),
        RGBA(listOf("A", "B", "G", "R")),
    }

    data class ExrConfig(
        val pixelType: PixelType = PixelType.HALF,
        val compression: Compression = Compression.ZIPS,
        val channelMode: ChannelMode = ChannelMode.RGBA,
    )

    /**
     * Write EXR data directly to an OutputStream.
     */
    fun writeExr(bitmap: Bitmap, out: OutputStream, config: ExrConfig = ExrConfig()) {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) {
            throw IllegalArgumentException("Invalid bitmap dimensions: $w x $h")
        }
        // 2026 hotfix: 防御 w*h 整数溢出
        val pixelCount = w.toLong() * h.toLong()
        if (pixelCount > Int.MAX_VALUE.toLong()) {
            throw IllegalArgumentException("Bitmap too large for EXR: $w x $h")
        }
        val channels = config.channelMode.channels
        val compression = config.compression

        // --- Magic number and version field ---
        // Magic: 0x762f3101 (little-endian)
        // Version: 2, single-part scanline (no flags set)
        val headerBytes = buildHeader(w, h, channels, config.pixelType, compression)

        // Number of scanline blocks depends on compression type
        val numBlocks = (h + compression.scanlinesPerBlock - 1) / compression.scanlinesPerBlock
        val headerSize = 4 + 4 + headerBytes.size // magic(4) + version(4) + header bytes
        val offsetTableSize = numBlocks.toLong() * 8

        // Build all scanline blocks (with compression applied)
        val blocks = buildScanlineBlocks(bitmap, config)

        // --- Build the offset table ---
        // Each entry is an int64 file-offset pointing to the start of a scanline block.
        val offsetTable = ByteBuffer.allocate(numBlocks * 8).order(ByteOrder.LITTLE_ENDIAN)
        var currentOffset = headerSize + offsetTableSize
        for (block in blocks) {
            offsetTable.putLong(currentOffset)
            currentOffset += block.size
        }

        // --- Write the file ---
        out.write(ByteArray(4).also { buf ->
            ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).putInt(0x762f3101)
        })
        out.write(ByteArray(4).also { buf ->
            ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).putInt(2)
        })
        out.write(headerBytes)
        offsetTable.flip()
        if (offsetTable.hasArray()) {
            out.write(offsetTable.array(), offsetTable.arrayOffset(), offsetTable.remaining())
        } else {
            val tmp = ByteArray(offsetTable.remaining())
            offsetTable.get(tmp)
            out.write(tmp)
        }
        for (block in blocks) {
            out.write(block)
        }
    }

    /**
     * Export a Bitmap as EXR directly to MediaStore.
     */
    fun export(
        bitmap: Bitmap,
        context: Context,
        config: ExrConfig = ExrConfig(),
        fileName: String = "RapidRAW_${System.currentTimeMillis()}.exr",
    ): Uri {
        val cacheDir = context.cacheDir
        val tempFile = java.io.File(cacheDir, "export_exr_${System.currentTimeMillis()}.exr")

        try {
            tempFile.outputStream().use { fos ->
                writeExr(bitmap, fos, config)
            }

            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/x-exr")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/RapidRAW")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val contentResolver = context.contentResolver
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: throw RuntimeException("Failed to create MediaStore entry")

            contentResolver.openOutputStream(uri)?.use { outputStream ->
                tempFile.inputStream().use { input ->
                    input.copyTo(outputStream)
                }
            } ?: throw RuntimeException("Failed to open output stream")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                contentResolver.update(uri, contentValues, null, null)
            }

            return uri
        } finally {
            runCatching { tempFile.delete() }
        }
    }

    // ---------------------------------------------------------------------------
    // Header construction
    // ---------------------------------------------------------------------------

    private fun buildHeader(
        w: Int,
        h: Int,
        channels: List<String>,
        pixelType: PixelType,
        compression: Compression,
    ): ByteArray {
        val baos = ByteArrayOutputStream()

        // channels attribute – chlist
        writeAttribute(baos, "channels", "chlist") {
            for (ch in channels) {
                writeNullTerminatedString(it, ch)
                // pixelType: 1=HALF, 2=FLOAT
                writeInt(it, pixelType.code)
                it.write(0) // pLinear
                it.write(byteArrayOf(0, 0, 0)) // reserved
                writeInt(it, 1) // xSampling
                writeInt(it, 1) // ySampling
            }
            it.write(0) // null-terminator ends the channel list
        }

        // compression attribute
        writeAttribute(baos, "compression", "compression") {
            it.write(compression.code)
        }

        // dataWindow – box2i (xMin, yMin, xMax, yMax)
        writeAttribute(baos, "dataWindow", "box2i") {
            writeInt(it, 0); writeInt(it, 0)
            writeInt(it, w - 1); writeInt(it, h - 1)
        }

        // displayWindow – box2i
        writeAttribute(baos, "displayWindow", "box2i") {
            writeInt(it, 0); writeInt(it, 0)
            writeInt(it, w - 1); writeInt(it, h - 1)
        }

        // lineOrder – INCREASING_Y
        writeAttribute(baos, "lineOrder", "lineOrder") {
            it.write(0)
        }

        // pixelAspectRatio – float
        writeAttribute(baos, "pixelAspectRatio", "float") {
            writeFloat(it, 1.0f)
        }

        // screenWindowCenter – v2f
        writeAttribute(baos, "screenWindowCenter", "v2f") {
            writeFloat(it, 0.0f)
            writeFloat(it, 0.0f)
        }

        // screenWindowWidth – float
        writeAttribute(baos, "screenWindowWidth", "float") {
            writeFloat(it, 1.0f)
        }

        // End of header
        baos.write(0)

        return baos.toByteArray()
    }

    // ---------------------------------------------------------------------------
    // Scanline block construction
    // ---------------------------------------------------------------------------

    /**
     * Build scanline blocks according to the compression mode.
     * Each block is: [y_coordinate: Int32][data_size: Int32][pixel_data]
     * For compressed blocks, pixel_data contains the deflated raw data.
     */
    private fun buildScanlineBlocks(bitmap: Bitmap, config: ExrConfig): List<ByteArray> {
        val w = bitmap.width
        val h = bitmap.height
        val channels = config.channelMode.channels
        val pixelType = config.pixelType
        val compression = config.compression
        val blockHeight = compression.scanlinesPerBlock

        // Extract all pixels once
        val pixelCount = w.toLong() * h.toLong()
        if (pixelCount > Int.MAX_VALUE.toLong()) {
            throw IllegalArgumentException("Bitmap too large: $w x $h")
        }
        val pixels = IntArray(pixelCount.toInt())
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val channelSize = if (pixelType == PixelType.HALF) 2 else 4
        val numChannels = channels.size
        // 2026 hotfix: 防御 bytesPerScanline 整数溢出
        val bytesPerScanline = w.toLong() * channelSize * numChannels
        if (bytesPerScanline > Int.MAX_VALUE.toLong()) {
            throw IllegalArgumentException("Scanline size overflow")
        }

        val blocks = mutableListOf<ByteArray>()
        var y = 0

        while (y < h) {
            val blockEnd = minOf(y + blockHeight, h)
            val linesInBlock = blockEnd - y

            // Build raw pixel data for all scanlines in this block
            val rawSizeLong = linesInBlock * bytesPerScanline
            if (rawSizeLong > Int.MAX_VALUE.toLong()) {
                throw IllegalArgumentException("Block size overflow")
            }
            val rawSize = rawSizeLong.toInt()
            val rawBuf = ByteBuffer.allocate(rawSize).order(ByteOrder.LITTLE_ENDIAN)

            for (line in y until blockEnd) {
                for (channelName in channels) {
                    for (x in 0 until w) {
                        val pixel = pixels[line * w + x]
                        val intValue = when (channelName) {
                            "R" -> (pixel shr 16) and 0xFF
                            "G" -> (pixel shr 8) and 0xFF
                            "B" -> pixel and 0xFF
                            "A" -> (pixel shr 24) and 0xFF
                            else -> 0
                        }
                        val value = intValue / 255f

                        if (pixelType == PixelType.HALF) {
                            val half = floatToHalf(value)
                            rawBuf.putShort(half)
                        } else {
                            rawBuf.putFloat(value)
                        }
                    }
                }
            }

            rawBuf.flip()
            val rawData = ByteArray(rawBuf.remaining())
            rawBuf.get(rawData)

            // Apply compression
            val pixelData = when (compression) {
                Compression.NONE -> rawData
                Compression.ZIPS, Compression.ZIP -> deflateBlock(rawData)
            }

            // Build the block: [y_coordinate][data_size][pixel_data]
            val baos = ByteArrayOutputStream()
            writeInt(baos, y) // y coordinate of first scanline in block
            writeInt(baos, pixelData.size) // size of (possibly compressed) data
            baos.write(pixelData)

            blocks.add(baos.toByteArray())
            y = blockEnd
        }

        return blocks
    }

    /**
     * Deflate-compress a block of raw pixel data.
     * Uses Deflater with BEST_SPEED for a good balance of speed vs ratio.
     */
    private fun deflateBlock(rawData: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_SPEED, true) // nowrap = true (zlib without header)
        deflater.setInput(rawData)
        deflater.finish()

        val baos = ByteArrayOutputStream(rawData.size / 2)
        val buffer = ByteArray(8192)
        while (!deflater.finished()) {
            val n = deflater.deflate(buffer)
            if (n > 0) baos.write(buffer, 0, n)
        }
        deflater.end()
        return baos.toByteArray()
    }

    // ---------------------------------------------------------------------------
    // Attribute writers
    // ---------------------------------------------------------------------------

    private fun writeAttribute(
        baos: ByteArrayOutputStream,
        name: String,
        type: String,
        writer: (ByteArrayOutputStream) -> Unit,
    ) {
        val valueBaos = ByteArrayOutputStream()
        writer(valueBaos)
        val valueBytes = valueBaos.toByteArray()

        writeNullTerminatedString(baos, name)
        writeNullTerminatedString(baos, type)
        writeInt(baos, valueBytes.size)
        baos.write(valueBytes)
    }

    private fun writeNullTerminatedString(os: ByteArrayOutputStream, s: String) {
        os.write(s.toByteArray(Charsets.US_ASCII))
        os.write(0)
    }

    private fun writeInt(os: ByteArrayOutputStream, v: Int) {
        val buf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()
        os.write(buf)
    }

    private fun writeFloat(os: ByteArrayOutputStream, v: Float) {
        val buf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(v).array()
        os.write(buf)
    }

    // ---------------------------------------------------------------------------
    // Float ↔ Half conversion
    // ---------------------------------------------------------------------------

    /**
     * Convert an IEEE 754 single-precision float to IEEE 754 half-precision float.
     * Handles normals, denormals, overflow (→ Infinity), underflow (→ signed zero),
     * and preserves NaN/Inf.
     */
    private fun floatToHalf(f: Float): Short {
        val bits = f.toBits()
        val sign = (bits ushr 31) and 0x1
        val exponent = ((bits ushr 23) and 0xFF) - 127
        val mantissa = bits and 0x7FFFFF

        // NaN or Infinity (exponent == 128 in float)
        if (exponent == 128) {
            return ((sign shl 15) or 0x7C00 or (mantissa ushr 13)).toShort()
        }

        val newExponent = exponent + 15
        return when {
            newExponent >= 31 -> {
                // Overflow → Infinity
                ((sign shl 15) or 0x7C00).toShort()
            }
            newExponent > 0 -> {
                // Normalized half-float
                // Apply rounding: add 0x1000 (the first bit that gets dropped) and check carry
                val roundedMantissa = mantissa + 0x1000
                val carry = (roundedMantissa ushr 23) and 0x1
                val finalExp = newExponent + carry
                val finalMantissa = (roundedMantissa ushr 13) and 0x3FF
                if (finalExp >= 31) {
                    ((sign shl 15) or 0x7C00).toShort()
                } else {
                    ((sign shl 15) or (finalExp shl 10) or finalMantissa).toShort()
                }
            }
            newExponent > -11 -> {
                // Denormalized half-float
                val shift = 14 - newExponent
                val halfMantissa = (mantissa or 0x800000) ushr shift
                // Rounding for denormals
                val roundBit = (mantissa ushr (shift - 1)) and 0x1
                val adjusted = halfMantissa + roundBit
                ((sign shl 15) or adjusted).toShort()
            }
            else -> {
                // Underflow → signed zero
                (sign shl 15).toShort()
            }
        }
    }
}

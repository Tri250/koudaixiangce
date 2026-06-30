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

/**
 * Pure Kotlin OpenEXR encoder.
 * Supports 16-bit half-float and 32-bit float RGBA output.
 */
object ExrExporter {

    enum class PixelType { HALF, FLOAT }

    /**
     * Write EXR data directly to an OutputStream.
     */
    fun writeExr(bitmap: Bitmap, out: OutputStream, pixelType: PixelType = PixelType.HALF) {
        val w = bitmap.width
        val h = bitmap.height
        val channels = listOf("R", "G", "B", "A")

        val headerBytes = buildHeader(w, h, channels, pixelType)

        val numScanlines = h
        val offsetTableSize = numScanlines * 8L
        val headerEndOffset = 4 + 4 + headerBytes.size // magic + version + header
        val offsetTableOffset = headerEndOffset
        val pixelDataStartOffset = offsetTableOffset + offsetTableSize

        val scanlineData = buildScanlineData(bitmap, pixelType)

        // Offset table
        val offsetTable = ByteArray(numScanlines * 8)
        val offsetBuf = ByteBuffer.wrap(offsetTable).order(ByteOrder.LITTLE_ENDIAN)
        var currentOffset = pixelDataStartOffset
        for (i in 0 until numScanlines) {
            offsetBuf.putLong(currentOffset)
            currentOffset += scanlineData[i].size
        }

        // Write file
        val magic = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(0x762f3101).array()
        val version = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(2).array()

        out.write(magic)
        out.write(version)
        out.write(headerBytes)
        out.write(offsetTable)
        for (scanline in scanlineData) {
            out.write(scanline)
        }
    }

    /**
     * Export a Bitmap as EXR directly to MediaStore.
     */
    fun export(
        bitmap: Bitmap,
        context: Context,
        pixelType: PixelType = PixelType.HALF,
        fileName: String = "RapidRAW_${System.currentTimeMillis()}.exr"
    ): Uri {
        val cacheDir = context.cacheDir
        val tempFile = java.io.File(cacheDir, "export_exr_${System.currentTimeMillis()}.exr")

        try {
            tempFile.outputStream().use { fos ->
                writeExr(bitmap, fos, pixelType)
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

    private fun buildHeader(w: Int, h: Int, channels: List<String>, pixelType: PixelType): ByteArray {
        val baos = ByteArrayOutputStream()

        // channels attribute
        writeAttribute(baos, "channels", "chlist") {
            for (ch in channels) {
                writeNullTerminatedString(it, ch)
                writeInt(it, if (pixelType == PixelType.HALF) 1 else 2) // 1=HALF, 2=FLOAT
                it.write(0) // pLinear
                it.write(byteArrayOf(0, 0, 0)) // reserved
                writeInt(it, 1) // xSampling
                writeInt(it, 1) // ySampling
            }
            it.write(0) // end of channel list
        }

        // compression = none
        writeAttribute(baos, "compression", "compression") {
            it.write(0) // NO_COMPRESSION
        }

        // dataWindow
        writeAttribute(baos, "dataWindow", "box2i") {
            writeInt(it, 0)
            writeInt(it, 0)
            writeInt(it, w - 1)
            writeInt(it, h - 1)
        }

        // displayWindow
        writeAttribute(baos, "displayWindow", "box2i") {
            writeInt(it, 0)
            writeInt(it, 0)
            writeInt(it, w - 1)
            writeInt(it, h - 1)
        }

        // lineOrder
        writeAttribute(baos, "lineOrder", "lineOrder") {
            it.write(0) // INCREASING_Y
        }

        // pixelAspectRatio
        writeAttribute(baos, "pixelAspectRatio", "float") {
            writeFloat(it, 1.0f)
        }

        // screenWindowCenter
        writeAttribute(baos, "screenWindowCenter", "v2f") {
            writeFloat(it, 0.0f)
            writeFloat(it, 0.0f)
        }

        // screenWindowWidth
        writeAttribute(baos, "screenWindowWidth", "float") {
            writeFloat(it, 1.0f)
        }

        baos.write(0) // end of header

        return baos.toByteArray()
    }

    private fun writeAttribute(baos: ByteArrayOutputStream, name: String, type: String, writer: (ByteArrayOutputStream) -> Unit) {
        val valueBaos = ByteArrayOutputStream()
        writer(valueBaos)
        val valueBytes = valueBaos.toByteArray()

        writeNullTerminatedString(baos, name)
        writeNullTerminatedString(baos, type)
        writeInt(baos, valueBytes.size)
        baos.write(valueBytes)
    }

    private fun writeNullTerminatedString(os: ByteArrayOutputStream, s: String) {
        os.write(s.toByteArray(Charsets.UTF_8))
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

    private fun buildScanlineData(bitmap: Bitmap, pixelType: PixelType): List<ByteArray> {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val channelSize = if (pixelType == PixelType.HALF) 2 else 4
        val bytesPerLine = w * channelSize * 4 // RGBA

        return (0 until h).map { y ->
            val baos = ByteArrayOutputStream()
            writeInt(baos, y) // y coordinate
            writeInt(baos, bytesPerLine) // data size (uncompressed)

            for (ch in 0..3) {
                for (x in 0 until w) {
                    val pixel = pixels[y * w + x]
                    val intValue = when (ch) {
                        0 -> (pixel shr 16) and 0xFF // R
                        1 -> (pixel shr 8) and 0xFF  // G
                        2 -> pixel and 0xFF          // B
                        else -> (pixel shr 24) and 0xFF // A
                    }
                    val value = intValue / 255f

                    if (pixelType == PixelType.HALF) {
                        val half = floatToHalf(value)
                        baos.write(half.toInt() and 0xFF)
                        baos.write((half.toInt() shr 8) and 0xFF)
                    } else {
                        writeFloat(baos, value)
                    }
                }
            }

            baos.toByteArray()
        }
    }

    /**
     * Convert an IEEE 754 float to IEEE 754 half-precision float.
     */
    private fun floatToHalf(f: Float): Short {
        val bits = f.toBits()
        val sign = (bits ushr 31) and 0x1
        var exponent = ((bits ushr 23) and 0xFF) - 127
        var mantissa = bits and 0x7FFFFF

        if (exponent == 128) { // Inf or NaN
            return ((sign shl 15) or 0x7C00 or (mantissa ushr 13)).toShort()
        }

        val newExponent = exponent + 15
        return when {
            newExponent >= 31 -> {
                // Overflow -> Infinity
                ((sign shl 15) or 0x7C00).toShort()
            }
            newExponent > 0 -> {
                // Normalized
                ((sign shl 15) or (newExponent shl 10) or (mantissa ushr 13)).toShort()
            }
            newExponent > -11 -> {
                // Denormalized
                val shift = 14 - newExponent
                val halfMantissa = (mantissa or 0x800000) ushr shift
                ((sign shl 15) or halfMantissa).toShort()
            }
            else -> {
                // Underflow -> Zero
                (sign shl 15).toShort()
            }
        }
    }
}

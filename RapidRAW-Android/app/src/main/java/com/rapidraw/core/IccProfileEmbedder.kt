package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import java.util.zip.Deflater

enum class IccProfile(val displayName: String, val fileName: String) {
    SRGB("sRGB IEC61966-2.1", "srgb.icc"),
    DISPLAY_P3("Display P3", "display_p3.icc"),
    ADOBE_RGB("Adobe RGB (1998)", "adobe_rgb.icc"),
    REC2020("Rec.2020", "rec2020.icc"),
    PRO_PHOTO("ProPhoto RGB", "prophoto.icc"),
}

/**
 * Embeds ICC colour profiles into JPEG (APP2 marker) and PNG (iCCP chunk) byte streams.
 * Inspired by AlcedoStudio's professional export pipeline with ICC profile support.
 */
object IccProfileEmbedder {

    // ── JPEG helpers ──────────────────────────────────────────────────────

    /**
     * Embeds an ICC profile into a JPEG byte array using APP2 markers.
     *
     * JPEG APP2 layout for ICC:
     *   FF E2  [length:2]  "ICC_PROFILE\u0000"  [chunk_no:1]  [num_chunks:1]  [data]
     *
     * Maximum data per APP2 = 65519 bytes (65535 − 2 length − 14 identifier bytes).
     */
    fun embedInJpeg(jpegBytes: ByteArray, profile: IccProfile): ByteArray {
        val profileData = getBuiltInProfileBytes(profile)
        val maxChunkData = 65519
        val numChunks = ((profileData.size + maxChunkData - 1) / maxChunkData).coerceAtLeast(1)

        // Build all APP2 marker segments
        val app2Segments = mutableListOf<ByteArray>()
        var offset = 0
        for (chunkNo in 1..numChunks) {
            val end = (offset + maxChunkData).coerceAtMost(profileData.size)
            val chunkData = profileData.copyOfRange(offset, end)
            val segmentLen = 2 + 14 + chunkData.size  // length field includes itself

            val buf = ByteBuffer.allocate(2 + 2 + 14 + chunkData.size).order(ByteOrder.BIG_ENDIAN)
            // Marker
            buf.put(0xFF.toByte())
            buf.put(0xE2.toByte())
            // Length (big-endian, includes itself)
            buf.putShort(segmentLen.toShort())
            // "ICC_PROFILE\u0000"
            buf.put("ICC_PROFILE\u0000".toByteArray(Charsets.US_ASCII))
            // Chunk number & total chunks (1-based)
            buf.put(chunkNo.toByte())
            buf.put(numChunks.toByte())
            // Profile data chunk
            buf.put(chunkData)

            app2Segments.add(buf.array())
            offset = end
        }

        // Find insertion point: after first APP0 (JFIF) or APP1 (EXIF), before first SOS
        val insertAt = findJpegInsertionPoint(jpegBytes)

        val out = ByteArrayOutputStream(jpegBytes.size + app2Segments.sumOf { it.size })
        out.write(jpegBytes, 0, insertAt)
        for (seg in app2Segments) {
            out.write(seg)
        }
        out.write(jpegBytes, insertAt, jpegBytes.size - insertAt)
        return out.toByteArray()
    }

    /**
     * Scans JPEG markers to find where ICC APP2 should be inserted:
     * after the first APP0/APP1 marker segment, but before SOS (FFDA).
     */
    private fun findJpegInsertionPoint(jpegBytes: ByteArray): Int {
        if (jpegBytes.size < 4) return 0
        // Verify SOI
        if (jpegBytes[0] != 0xFF.toByte() || jpegBytes[1] != 0xD8.toByte()) return 0

        var pos = 2 // skip SOI
        var pastFirstApp0App1 = false

        while (pos < jpegBytes.size - 1) {
            if (jpegBytes[pos] != 0xFF.toByte()) {
                pos++
                continue
            }
            val marker = jpegBytes[pos + 1].toInt() and 0xFF

            // Skip padding FF bytes
            if (marker == 0xFF) {
                pos++
                continue
            }

            // SOS – start of scan; insert before this
            if (marker == 0xDA) {
                return pos
            }

            // Standalone markers (no length field)
            if (marker in 0xD0..0xD9) {
                if (marker == 0xD9) return pos // EOI
                pos += 2
                continue
            }

            // Read marker segment length
            if (pos + 3 >= jpegBytes.size) return pos
            val segLen = ((jpegBytes[pos + 2].toInt() and 0xFF) shl 8) or
                    (jpegBytes[pos + 3].toInt() and 0xFF)

            // If this is APP0 (FFE0) or APP1 (FFE1), skip it and continue
            if (!pastFirstApp0App1 && (marker == 0xE0 || marker == 0xE1)) {
                pastFirstApp0App1 = true
                pos += 2 + segLen
                continue
            }

            // If we've passed APP0/APP1, insert before the next marker
            if (pastFirstApp0App1) {
                return pos
            }

            pos += 2 + segLen
        }

        return pos
    }

    // ── PNG helpers ───────────────────────────────────────────────────────

    /**
     * Embeds an ICC profile into a PNG byte array using an iCCP chunk.
     *
     * iCCP chunk layout:
     *   [length:4] "iCCP" [profile_name\0 compression_method:1] [compressed_profile] [CRC32:4]
     */
    fun embedInPng(pngBytes: ByteArray, profile: IccProfile): ByteArray {
        val profileData = getBuiltInProfileBytes(profile)

        // Compress profile data with deflate
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        deflater.setInput(profileData)
        deflater.finish()
        val compressedBuf = ByteArrayOutputStream(profileData.size)
        val tmp = ByteArray(4096)
        while (!deflater.finished()) {
            val n = deflater.deflate(tmp)
            compressedBuf.write(tmp, 0, n)
        }
        deflater.end()
        val compressedData = compressedBuf.toByteArray()

        // Build iCCP chunk data: profile_name\0 + compression_method(0=deflate) + compressed_data
        val profileName = profile.displayName.toByteArray(Charsets.US_ASCII)
        val chunkDataLen = profileName.size + 1 + 1 + compressedData.size // name + null + method + data
        val chunkDataBuf = ByteBuffer.allocate(chunkDataLen).order(ByteOrder.BIG_ENDIAN)
        chunkDataBuf.put(profileName)
        chunkDataBuf.put(0) // null terminator
        chunkDataBuf.put(0) // compression method: deflate
        chunkDataBuf.put(compressedData)
        val chunkData = chunkDataBuf.array()

        // Build full chunk: length + type + data + crc
        val crc = CRC32()
        crc.update("iCCP".toByteArray(Charsets.US_ASCII))
        crc.update(chunkData)
        val crcVal = crc.value

        val fullChunk = ByteBuffer.allocate(4 + 4 + chunkDataLen + 4).order(ByteOrder.BIG_ENDIAN)
        fullChunk.putInt(chunkDataLen)
        fullChunk.put("iCCP".toByteArray(Charsets.US_ASCII))
        fullChunk.put(chunkData)
        fullChunk.putInt(crcVal.toInt())

        // Find insertion point: after IHDR chunk
        val insertAt = findPngIhdrEnd(pngBytes)

        val out = ByteArrayOutputStream(pngBytes.size + fullChunk.capacity())
        out.write(pngBytes, 0, insertAt)
        out.write(fullChunk.array())
        out.write(pngBytes, insertAt, pngBytes.size - insertAt)
        return out.toByteArray()
    }

    /**
     * Finds the byte offset immediately after the IHDR chunk in a PNG.
     * PNG structure: 8-byte signature, then chunks [length:4][type:4][data:length][crc:4].
     */
    private fun findPngIhdrEnd(pngBytes: ByteArray): Int {
        if (pngBytes.size < 8) return 0
        // Skip 8-byte PNG signature
        var pos = 8
        while (pos + 11 < pngBytes.size) {
            val len = ((pngBytes[pos].toInt() and 0xFF) shl 24) or
                    ((pngBytes[pos + 1].toInt() and 0xFF) shl 16) or
                    ((pngBytes[pos + 2].toInt() and 0xFF) shl 8) or
                    (pngBytes[pos + 3].toInt() and 0xFF)

            val type = String(pngBytes, pos + 4, 4, Charsets.US_ASCII)
            val chunkTotal = 4 + 4 + len + 4 // length + type + data + crc
            pos += chunkTotal

            if (type == "IHDR") {
                return pos
            }
        }
        return pos
    }

    // ── Built-in ICC profile generation ───────────────────────────────────

    /**
     * Returns built-in ICC profile bytes for the given [profile].
     * Generates a minimal valid ICC v2 profile structure.
     * For sRGB the profile contains the standard colourimetric values;
     * other profiles use generic primaries with appropriate signatures.
     */
    fun getBuiltInProfileBytes(profile: IccProfile): ByteArray {
        return when (profile) {
            IccProfile.SRGB -> buildSrgbProfile()
            IccProfile.DISPLAY_P3 -> buildDisplayP3Profile()
            IccProfile.ADOBE_RGB -> buildAdobeRgbProfile()
            IccProfile.REC2020 -> buildRec2020Profile()
            IccProfile.PRO_PHOTO -> buildProPhotoProfile()
        }
    }

    // ── Minimal ICC v2 profile builder ────────────────────────────────────

    /**
     * Builds a minimal valid sRGB IEC61966-2.1 ICC v2 profile.
     *
     * Required tags: desc, wtpt, rXYZ, gXYZ, bXYZ, rTRC, gTRC, bTRC, chad
     * The colourimetric values follow the sRGB specification.
     */
    private fun buildSrgbProfile(): ByteArray {
        return buildIccV2Profile(
            preferredCmm = "ADBE",
            profileClass = 0x6D6E7472, // 'mntr'
            colorSpace = 0x52474220,   // 'RGB '
            pcs = 0x58595A20,          // 'XYZ '
            primaries = SrgbPrimaries,
            whitePoint = D65Xyz,
            trcGamma = 2.2, // approximate gamma for simplified TRC
            profileDesc = "sRGB IEC61966-2.1",
            copyright = "CC0",
        )
    }

    private fun buildDisplayP3Profile(): ByteArray {
        return buildIccV2Profile(
            preferredCmm = "ADBE",
            profileClass = 0x6D6E7472,
            colorSpace = 0x52474220,
            pcs = 0x58595A20,
            primaries = DisplayP3Primaries,
            whitePoint = D65Xyz,
            trcGamma = 2.2,
            profileDesc = "Display P3",
            copyright = "CC0",
        )
    }

    private fun buildAdobeRgbProfile(): ByteArray {
        return buildIccV2Profile(
            preferredCmm = "ADBE",
            profileClass = 0x6D6E7472,
            colorSpace = 0x52474220,
            pcs = 0x58595A20,
            primaries = AdobeRgbPrimaries,
            whitePoint = D65Xyz,
            trcGamma = 2.19921875, // Adobe RGB uses 563/256 ≈ 2.199
            profileDesc = "Adobe RGB (1998)",
            copyright = "CC0",
        )
    }

    private fun buildRec2020Profile(): ByteArray {
        return buildIccV2Profile(
            preferredCmm = "ADBE",
            profileClass = 0x6D6E7472,
            colorSpace = 0x52474220,
            pcs = 0x58595A20,
            primaries = Rec2020Primaries,
            whitePoint = D65Xyz,
            trcGamma = 2.2,
            profileDesc = "Rec.2020",
            copyright = "CC0",
        )
    }

    private fun buildProPhotoProfile(): ByteArray {
        return buildIccV2Profile(
            preferredCmm = "ADBE",
            profileClass = 0x6D6E7472,
            colorSpace = 0x52474220,
            pcs = 0x58595A20,
            primaries = ProPhotoPrimaries,
            whitePoint = D50Xyz,
            trcGamma = 1.8,
            profileDesc = "ProPhoto RGB",
            copyright = "CC0",
        )
    }

    // ── CIE XYZ primaries (fixed-point s15Fixed16Number: sign + 16-bit integer + 16-bit frac) ─

    /** s15Fixed16Number encoding: value * 65536 */
    private fun s15f16(v: Double): Int {
        return (v * 65536.0).roundToInt()
    }

    private val Int.roundToInt: Int get() = if (this >= 0) this else this

    private fun Double.roundToInt(): Int = kotlin.math.round(this).toInt()

    // sRGB primaries (D65 adapted)
    private val SrgbPrimaries = Triple(
        doubleArrayOf(0.4360747, 0.2225045, 0.0139322), // rXYZ
        doubleArrayOf(0.3850649, 0.7168786, 0.0970764), // gXYZ
        doubleArrayOf(0.1430804, 0.0606169, 0.7140917), // bXYZ
    )

    // Display P3 primaries (D65 adapted)
    private val DisplayP3Primaries = Triple(
        doubleArrayOf(0.5151167, 0.2411506, 0.0208832),
        doubleArrayOf(0.2919786, 0.6925554, 0.0555014),
        doubleArrayOf(0.1570764, 0.0662879, 0.7505437),
    )

    // Adobe RGB primaries (D65 adapted)
    private val AdobeRgbPrimaries = Triple(
        doubleArrayOf(0.5767309, 0.2885423, 0.0250638),
        doubleArrayOf(0.1856403, 0.6883647, 0.1051872),
        doubleArrayOf(0.1570764, 0.0606169, 0.7140917),
    )

    // Rec.2020 primaries (D65 adapted)
    private val Rec2020Primaries = Triple(
        doubleArrayOf(0.6369580, 0.2627002, 0.0000000),
        doubleArrayOf(0.1446169, 0.6779981, 0.0448961),
        doubleArrayOf(0.1688810, 0.0593017, 0.7929746),
    )

    // ProPhoto RGB primaries (D50 adapted)
    private val ProPhotoPrimaries = Triple(
        doubleArrayOf(0.7977, 0.2880, 0.0000),
        doubleArrayOf(0.1352, 0.7119, 0.0000),
        doubleArrayOf(0.0311, 0.0001, 0.8247),
    )

    private val D65Xyz = doubleArrayOf(0.95047, 1.00000, 1.08883)
    private val D50Xyz = doubleArrayOf(0.96420, 1.00000, 0.82491)

    // ── Core ICC v2 profile builder ───────────────────────────────────────

    /**
     * Builds a minimal ICC v2.1 profile with the given parameters.
     *
     * Structure:
     *   128-byte header
     *   Tag table
     *   Tag data
     *
     * Tags included: desc, wtpt, rXYZ, gXYZ, bXYZ, rTRC, gTRC, bTRC, chad
     */
    private fun buildIccV2Profile(
        preferredCmm: String,
        profileClass: Int,
        colorSpace: Int,
        pcs: Int,
        primaries: Triple<DoubleArray, DoubleArray, DoubleArray>,
        whitePoint: DoubleArray,
        trcGamma: Double,
        profileDesc: String,
        copyright: String,
    ): ByteArray {
        // ── Prepare tag data blobs ──────────────────────────────────────

        val descTagData = buildDescTag(profileDesc)
        val copyrightTagData = buildDescTag(copyright)
        val wtptTagData = buildXyzTag(whitePoint)
        val rXyzTagData = buildXyzTag(primaries.first)
        val gXyzTagData = buildXyzTag(primaries.second)
        val bXyzTagData = buildXyzTag(primaries.third)
        val trcTagData = buildTrcTag(trcGamma)
        val chadTagData = buildChadTag()

        // Tag definitions: sig, offset, size
        data class TagDef(val sig: Int, val data: ByteArray)

        val tags = listOf(
            TagDef(sig4("desc"), descTagData),
            TagDef(sig4("cprt"), copyrightTagData),
            TagDef(sig4("wtpt"), wtptTagData),
            TagDef(sig4("rXYZ"), rXyzTagData),
            TagDef(sig4("gXYZ"), gXyzTagData),
            TagDef(sig4("bXYZ"), bXyzTagData),
            TagDef(sig4("rTRC"), trcTagData),
            TagDef(sig4("gTRC"), trcTagData), // shared
            TagDef(sig4("bTRC"), trcTagData), // shared
            TagDef(sig4("chad"), chadTagData),
        )

        val headerSize = 128
        val tagCount = tags.size
        val tagTableSize = 4 + tagCount * 12 // count + entries
        val dataStart = headerSize + tagTableSize

        // Calculate offsets, pad each tag data to 4-byte boundary
        var currentOffset = dataStart
        val tagEntries = mutableListOf<IntArray>() // [sig, offset, size]
        val tagDataMap = linkedMapOf<ByteArray, Int>() // deduplicated data → offset

        for (tag in tags) {
            val existingOffset = tagDataMap.entries.find { it.key.contentEquals(tag.data) }?.value
            if (existingOffset != null) {
                tagEntries.add(intArrayOf(tag.sig, existingOffset, tag.data.size))
            } else {
                tagEntries.add(intArrayOf(tag.sig, currentOffset, tag.data.size))
                tagDataMap[tag.data] = currentOffset
                currentOffset += tag.data.size
                // Pad to 4-byte boundary
                val padding = (4 - (tag.data.size % 4)) % 4
                currentOffset += padding
            }
        }

        val profileSize = currentOffset

        // ── Build header (128 bytes) ────────────────────────────────────

        val buf = ByteBuffer.allocate(profileSize).order(ByteOrder.BIG_ENDIAN)

        // 0-3:   Profile size
        buf.putInt(profileSize)
        // 4-7:   Preferred CMM type
        buf.put(preferredCmm.toByteArray(Charsets.US_ASCII))
        // 8-11:  Profile version (2.1.0)
        buf.putInt(0x02100000)
        // 12-15: Profile/device class
        buf.putInt(profileClass)
        // 16-19: Color space
        buf.putInt(colorSpace)
        // 20-23: PCS
        buf.putInt(pcs)
        // 24-35: Date/time (2025-01-01 00:00:00)
        buf.putShort(2025) // year
        buf.putShort(1)    // month
        buf.putShort(1)    // day
        buf.putShort(0)    // hour
        buf.putShort(0)    // minute
        buf.putShort(0)    // second
        // 36-39: 'acsp' signature
        buf.put("acsp".toByteArray(Charsets.US_ASCII))
        buf.put(0)
        // 40-43: Primary platform
        buf.put("APPL".toByteArray(Charsets.US_ASCII))
        buf.put(0)
        // 44-47: Profile flags
        buf.putInt(0)
        // 48-51: Device manufacturer
        buf.putInt(0)
        // 52-55: Device model
        buf.putInt(0)
        // 56-63: Device attributes
        buf.putLong(0)
        // 64-67: Rendering intent (perceptual = 0)
        buf.putInt(0)
        // 68-79: PCS illuminant (D50 in XYZ as s15Fixed16Number)
        buf.putInt(s15f16(0.9642))
        buf.putInt(s15f16(1.0000))
        buf.putInt(s15f16(0.8249))
        // 80-83: Profile creator
        buf.putInt(0)
        // 84-99: Profile ID (MD5, leave zero)
        buf.put(ByteArray(16))
        // 100-127: Reserved
        buf.put(ByteArray(28))

        // ── Tag table ───────────────────────────────────────────────────

        buf.putInt(tagCount)
        for (entry in tagEntries) {
            buf.putInt(entry[0]) // sig
            buf.putInt(entry[1]) // offset
            buf.putInt(entry[2]) // size
        }

        // ── Tag data ────────────────────────────────────────────────────

        val writtenOffsets = mutableSetOf<Int>()
        for ((data, offset) in tagDataMap) {
            if (offset in writtenOffsets) continue
            writtenOffsets.add(offset)
            buf.position(offset)
            buf.put(data)
            // Pad to 4-byte boundary
            val padding = (4 - (data.size % 4)) % 4
            for (i in 0 until padding) {
                buf.put(0)
            }
        }

        return buf.array()
    }

    // ── Tag builders ──────────────────────────────────────────────────────

    /**
     * 'desc' tag: profileDescriptionType (ICC v2 textDescription).
     * typeSig = 'desc' (0x64657363), reserved = 0,
     * ASCII description count (including null), ASCII string + null,
     * Unicode count = 0, ScriptCode count = 0.
     */
    private fun buildDescTag(text: String): ByteArray {
        val ascii = text.toByteArray(Charsets.US_ASCII)
        val count = ascii.size + 1 // include null terminator
        val size = 4 + 4 + 4 + count + 4 + 2 + 1 + 4
        // typeSig + reserved + asciiCount + ascii+null + unicodeLang+count + scriptcode

        val buf = ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN)
        buf.putInt(sig4("desc"))
        buf.putInt(0) // reserved
        buf.putInt(count)
        buf.put(ascii)
        buf.put(0) // null terminator
        buf.putInt(0) // Unicode language code
        buf.putInt(0) // Unicode count = 0
        buf.putShort(0) // ScriptCode code = 0
        buf.put(0)      // ScriptCode count = 0
        buf.put(ByteArray(4)) // pad
        return buf.array().copyOf(buf.position())
    }

    /**
     * XYZ tag: XYZType with 3 s15Fixed16Number values.
     */
    private fun buildXyzTag(xyz: DoubleArray): ByteArray {
        val buf = ByteBuffer.allocate(20).order(ByteOrder.BIG_ENDIAN)
        buf.putInt(sig4("XYZ "))  // type sig
        buf.putInt(0)             // reserved
        buf.putInt(s15f16(xyz[0]))
        buf.putInt(s15f16(xyz[1]))
        buf.putInt(s15f16(xyz[2]))
        return buf.array()
    }

    /**
     * TRC tag: curveType with a single gamma value.
     * Format: typeSig('curv'), reserved, count=1, gamma as u8Fixed8Number.
     * u8Fixed8Number = gamma * 256.
     */
    private fun buildTrcTag(gamma: Double): ByteArray {
        val buf = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN)
        buf.putInt(sig4("curv"))
        buf.putInt(0) // reserved
        buf.putInt(1) // count = 1 → single gamma
        val gammaFixed = (gamma * 256.0).roundToInt().toShort()
        buf.putShort(gammaFixed)
        buf.putShort(0) // pad to 4-byte boundary
        return buf.array()
    }

    /**
     * Chad (chromatic adaptation) tag: s15Fixed16Number 3×3 matrix.
     * Bradford D50→D65 adaptation matrix for sRGB-like profiles.
     */
    private fun buildChadTag(): ByteArray {
        val buf = ByteBuffer.allocate(44).order(ByteOrder.BIG_ENDIAN)
        buf.putInt(sig4("sf32")) // s15Fixed16ArrayType
        buf.putInt(0)            // reserved
        // Bradford D50→D65 matrix (3×3 = 9 values)
        val bradford = doubleArrayOf(
            1.0478112,  0.0228866, -0.0501270,
            0.0295424,  0.9904844, -0.0170491,
            -0.0092345,  0.0150436,  0.7520976,
        )
        for (v in bradford) {
            buf.putInt(s15f16(v))
        }
        return buf.array()
    }

    // ── Utility ───────────────────────────────────────────────────────────

    /** Encodes a 4-char string as a big-endian 32-bit integer. */
    private fun sig4(s: String): Int {
        val b = s.toByteArray(Charsets.US_ASCII)
        return ((b[0].toInt() and 0xFF) shl 24) or
                ((b[1].toInt() and 0xFF) shl 16) or
                ((b[2].toInt() and 0xFF) shl 8) or
                (b[3].toInt() and 0xFF)
    }
}

package com.rawlab.android

import org.junit.Assert.*
import org.junit.Test

class RawFilesTest {
    private fun header(vararg bytes: Int) = bytes.map { it.toByte() }.toByteArray()

    @Test fun acceptsKnownExtensionsCaseInsensitively() {
        assertTrue(RawFiles.accepts("IMG_0001.DNG", null))
        assertTrue(RawFiles.accepts("photo.Raf", null))
        assertTrue(RawFiles.accepts("IMG_0001.arw", null))
        assertFalse(RawFiles.accepts("IMG_0001.jpg", null))
        assertFalse(RawFiles.accepts("noext", null))
    }

    @Test fun acceptsVendorDngMimeTypes() {
        assertTrue(RawFiles.accepts("unnamed", "image/x-adobe-dng"))
        assertTrue(RawFiles.accepts("unnamed", "image/DNG"))
        assertTrue(RawFiles.accepts("unnamed", "image/x-dng"))
        assertFalse(RawFiles.accepts("unnamed", "image/jpeg"))
        assertFalse(RawFiles.accepts("unnamed", null))
    }

    @Test fun sniffRecognizesTiffBasedRaw() {
        assertEquals(RawFiles.FileKind.RAW, RawFiles.sniff(header(0x49, 0x49, 0x2A, 0x00, 1, 2, 3, 4)))
        assertEquals(RawFiles.FileKind.RAW, RawFiles.sniff(header(0x4D, 0x4D, 0x00, 0x2A, 1, 2, 3, 4)))
    }

    @Test fun sniffRecognizesFujiCrwAndX3f() {
        val raf = "FUJIFILMCCD-RAW 0201".toByteArray(Charsets.US_ASCII)
        assertEquals(RawFiles.FileKind.RAW, RawFiles.sniff(raf))
        val crw = header(0x49, 0x49, 0x1A, 0x00, 0, 0, 0, 0) + "HEAPCCDR".toByteArray(Charsets.US_ASCII)
        assertEquals(RawFiles.FileKind.RAW, RawFiles.sniff(crw))
        assertEquals(RawFiles.FileKind.RAW, RawFiles.sniff(header(0x46, 0x4F, 0x56, 0x62, 0, 0)))
    }

    @Test fun sniffRecognizesStillImages() {
        assertEquals(RawFiles.FileKind.STILL_IMAGE, RawFiles.sniff(header(0xFF, 0xD8, 0xFF, 0xE1)))
        assertEquals(RawFiles.FileKind.STILL_IMAGE, RawFiles.sniff(header(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)))
        assertEquals(RawFiles.FileKind.STILL_IMAGE, RawFiles.sniff(header(0, 0, 0, 0x18) + "ftypheic".toByteArray(Charsets.US_ASCII)))
    }

    @Test fun sniffRejectsShortOrUnknownHeaders() {
        assertEquals(RawFiles.FileKind.UNKNOWN, RawFiles.sniff(header(0x49, 0x49), 2))
        assertEquals(RawFiles.FileKind.UNKNOWN, RawFiles.sniff(ByteArray(0), 0))
        assertEquals(RawFiles.FileKind.UNKNOWN, RawFiles.sniff(header(1, 2, 3, 4, 5, 6, 7, 8)))
    }

    @Test fun classifyTrustsMetadataBeforeSniffing() {
        // 扩展名/MIME 可识别：即使内容魔数可疑也按 RAW 放行，交给解码器判定。
        assertEquals(RawFiles.FileKind.RAW, RawFiles.classify("a.dng", "image/jpeg", header(0xFF, 0xD8, 0xFF)))
        assertEquals(RawFiles.FileKind.RAW, RawFiles.classify("IMG.DNG", null, ByteArray(0), 0))
        // 元数据认不出：按魔数兜底。
        assertEquals(RawFiles.FileKind.RAW, RawFiles.classify("noext", null, header(0x49, 0x49, 0x2A, 0x00)))
        assertEquals(RawFiles.FileKind.STILL_IMAGE, RawFiles.classify("noext", null, header(0xFF, 0xD8, 0xFF, 0xE1)))
        assertEquals(RawFiles.FileKind.UNKNOWN, RawFiles.classify("noext", "application/octet-stream", header(1, 2, 3, 4)))
    }

    @Test fun probeReadsHeaderAndRestoresStream() {
        val payload = header(0x49, 0x49, 0x2A, 0x00) + ByteArray(64) { it.toByte() }
        val probe = RawFiles.probe(payload.inputStream())
        assertEquals(RawFiles.HEADER_BYTES, probe.length)
        assertEquals(RawFiles.FileKind.RAW, RawFiles.sniff(probe.header, probe.length))
        // 推回后可完整读取原始内容。
        val restored = probe.stream.readBytes()
        assertTrue(payload.contentEquals(restored))
    }

    @Test fun probeHandlesShortStreams() {
        val probe = RawFiles.probe(header(0x49, 0x49).inputStream())
        assertEquals(2, probe.length)
        assertEquals(RawFiles.FileKind.UNKNOWN, RawFiles.sniff(probe.header, probe.length))
        assertTrue(header(0x49, 0x49).contentEquals(probe.stream.readBytes()))
        val empty = RawFiles.probe(ByteArray(0).inputStream())
        assertEquals(0, empty.length)
    }
}

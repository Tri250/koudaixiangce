package com.rapidraw.core

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.rapidraw.data.model.ExifData
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.FileOutputStream
import java.io.StringWriter
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * EXIF 写回 + XMP 评分/标签同步。
 *
 * 参照原 RapidRAW Rust 项目 `exif_processing.rs` 的语义：
 * - EXIF 写回用 `androidx.exifinterface.media.ExifInterface`（JPEG 原生支持）
 * - XMP 评分 / 颜色标签 / 标签列表由本类自行解析 / 生成 XML，嵌入 JPEG APP1 段
 *
 * 写入均为原子操作（临时文件 + rename）。XMP 解析使用 XmlPullParser，
 * XMP 生成 / 更新使用 DOM（保留未修改字段）。
 */
class ExifWriter {

    companion object {
        private const val TAG = "ExifWriter"

        /** XMP APP1 段命名空间（null 结尾） */
        private val XMP_NAMESPACE = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray(Charsets.US_ASCII)

        // XML 命名空间 URI
        private const val NS_XMP = "http://ns.adobe.com/xap/1.0/"
        private const val NS_DC = "http://purl.org/dc/elements/1.1/"
        private const val NS_RDF = "http://www.w3.org/1999/02/22-rdf-syntax-ns#"
        private const val NS_X = "adobe:ns:meta/"

        /** 小写颜色名 <-> XMP Label 文本 */
        private val LABEL_TO_XMP = mapOf(
            "red" to "Red",
            "yellow" to "Yellow",
            "green" to "Green",
            "blue" to "Blue",
            "purple" to "Purple",
        )
        private val XMP_TO_LABEL: Map<String, String> =
            LABEL_TO_XMP.entries.associate { (k, v) -> v.lowercase() to k }

        /** ExifInterface 支持有限 / 不支持的格式（仅记录 warning，仍尝试） */
        private val LIMITED_FORMATS = setOf("tif", "tiff", "png")

        /** 有理数（rational）类 EXIF 字段，需把小数字符串转成 n/d 形式 */
        private val RATIONAL_FIELDS = setOf(
            "FocalLength", "FNumber", "Aperture", "ExposureTime", "ShutterSpeed",
        )

        /** 友好字段名 -> ExifInterface tag 常量 */
        private val FIELD_TO_TAG: Map<String, String> = mapOf(
            "DateTimeOriginal" to ExifInterface.TAG_DATETIME_ORIGINAL,
            "DateTime" to ExifInterface.TAG_DATETIME,
            "Make" to ExifInterface.TAG_MAKE,
            "Model" to ExifInterface.TAG_MODEL,
            "LensMake" to ExifInterface.TAG_LENS_MAKE,
            "LensModel" to ExifInterface.TAG_LENS_MODEL,
            "FocalLength" to ExifInterface.TAG_FOCAL_LENGTH,
            "FNumber" to ExifInterface.TAG_F_NUMBER,
            "Aperture" to ExifInterface.TAG_F_NUMBER,
            "ExposureTime" to ExifInterface.TAG_EXPOSURE_TIME,
            "ShutterSpeed" to ExifInterface.TAG_EXPOSURE_TIME,
            "ISO" to ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
            "Orientation" to ExifInterface.TAG_ORIENTATION,
            "Artist" to ExifInterface.TAG_ARTIST,
            "Copyright" to ExifInterface.TAG_COPYRIGHT,
            "UserComment" to ExifInterface.TAG_USER_COMMENT,
            "Flash" to ExifInterface.TAG_FLASH,
            "WhiteBalance" to ExifInterface.TAG_WHITE_BALANCE,
            "MeteringMode" to ExifInterface.TAG_METERING_MODE,
            "ExposureProgram" to ExifInterface.TAG_EXPOSURE_PROGRAM,
        )

        /** XMP APP1 payload 上限：长度字段 2 字节，最大 65535，含 2 字节长度自身 */
        private const val MAX_APP1_PAYLOAD = 65533
    }

    // ──────────────────────────────────────────────
    // EXIF 写回
    // ──────────────────────────────────────────────

    /**
     * 写回 EXIF 数据到 JPEG 文件。
     * ExifInterface.saveAttributes() 内部即“临时文件 + rename”，已为原子写入。
     * @return 是否成功
     */
    fun writeExif(filePath: String, exif: ExifData): Boolean {
        val file = File(filePath)
        if (!file.exists() || !file.canWrite()) {
            Log.w(TAG, "File not writable or missing: $filePath")
            return false
        }
        warnIfLimitedFormat(file)
        return try {
            val exifInterface = ExifInterface(filePath)
            applyExifData(exifInterface, exif)
            exifInterface.saveAttributes()
            true
        } catch (e: Exception) {
            Log.w(TAG, "writeExif failed for $filePath: ${e.message}")
            false
        }
    }

    /**
     * 写回 EXIF 数据到 URI（SAF）。
     * 流程：拷贝到 cacheDir 临时文件 -> 写 EXIF -> 写回 URI（"wt" 截断模式）。
     */
    fun writeExif(context: Context, uri: Uri, exif: ExifData): Boolean {
        return try {
            val tmp = File.createTempFile("exifwrite", ".jpg", context.cacheDir)
            try {
                context.contentResolver.openInputStream(uri).use { input ->
                    if (input == null) {
                        Log.w(TAG, "Cannot open input URI: $uri")
                        return false
                    }
                    FileOutputStream(tmp).use { out -> input.copyTo(out) }
                }
                val exifInterface = ExifInterface(tmp.absolutePath)
                applyExifData(exifInterface, exif)
                exifInterface.saveAttributes()
                val resolver = context.contentResolver
                resolver.openOutputStream(uri, "wt").use { out ->
                    if (out == null) {
                        Log.w(TAG, "Cannot open output URI: $uri")
                        return false
                    }
                    tmp.inputStream().use { it.copyTo(out) }
                    out.flush()
                }
                true
            } finally {
                tmp.delete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "writeExif (URI) failed for $uri: ${e.message}")
            false
        }
    }

    /**
     * 更新部分 EXIF 字段（不覆盖未提供的字段）。
     * @param updates 字段名 -> 值。支持 DateTimeOriginal/Make/Model/LensModel/LensMake/
     *                FocalLength/FNumber/Aperture/ExposureTime/ShutterSpeed/ISO/Orientation/
     *                Artist/Copyright/UserComment/Flash/WhiteBalance/MeteringMode/
     *                ExposureProgram/GPSLatitude/GPSLongitude/GPSAltitude。
     *                GPS 值为十进制度数字符串。
     */
    fun updateExifFields(filePath: String, updates: Map<String, String>): Boolean {
        val file = File(filePath)
        if (!file.exists() || !file.canWrite()) {
            Log.w(TAG, "File not writable or missing: $filePath")
            return false
        }
        warnIfLimitedFormat(file)
        return try {
            val exifInterface = ExifInterface(filePath)
            applyExifUpdates(exifInterface, updates)
            exifInterface.saveAttributes()
            true
        } catch (e: Exception) {
            Log.w(TAG, "updateExifFields failed for $filePath: ${e.message}")
            false
        }
    }

    // ──────────────────────────────────────────────
    // XMP 评分
    // ──────────────────────────────────────────────

    /** 读取 XMP 评分（0-5）。无 XMP / 无评分返回 0。 */
    fun readXmpRating(filePath: String): Int {
        val seg = readXmpSegment(filePath) ?: return 0
        return parseXmp(seg.xml).rating
    }

    /** 写入 XMP 评分（0-5）。 */
    fun writeXmpRating(filePath: String, rating: Int): Boolean =
        modifyXmp(filePath) { doc -> setRatingAttr(doc, rating.coerceIn(0, 5)) }

    // ──────────────────────────────────────────────
    // XMP 颜色标签
    // ──────────────────────────────────────────────

    /**
     * 读取 XMP 颜色标签。
     * @return "red"/"yellow"/"green"/"blue"/"purple"，或 null。
     */
    fun readXmpColorLabel(filePath: String): String? {
        val seg = readXmpSegment(filePath) ?: return null
        val raw = parseXmp(seg.xml).label ?: return null
        val key = raw.lowercase()
        return XMP_TO_LABEL[key]
    }

    /**
     * 写入 XMP 颜色标签。
     * @param label 小写颜色名；null 清除标签。
     */
    fun writeXmpColorLabel(filePath: String, label: String?): Boolean =
        modifyXmp(filePath) { doc -> setLabelAttr(doc, label) }

    // ──────────────────────────────────────────────
    // XMP 标签列表
    // ──────────────────────────────────────────────

    /** 读取 XMP 标签列表（dc:subject）。 */
    fun readXmpTags(filePath: String): List<String> {
        val seg = readXmpSegment(filePath) ?: return emptyList()
        return parseXmp(seg.xml).tags
    }

    /** 写入 XMP 标签列表（dc:subject）。空列表清除。 */
    fun writeXmpTags(filePath: String, tags: List<String>): Boolean =
        modifyXmp(filePath) { doc -> setSubjectTags(doc, tags) }

    /**
     * 一次性写入评分 / 颜色标签 / 标签列表（单次读-改-写）。
     * 任意参数为 null 表示不修改该字段；label 为空串表示清除。
     * 供 sidecar 保存时同步 XMP 使用（对应原项目 save_metadata_and_update_thumbnail）。
     */
    fun writeXmpMetadata(
        filePath: String,
        rating: Int? = null,
        label: String? = null,
        tags: List<String>? = null,
    ): Boolean = modifyXmp(filePath) { doc ->
        rating?.let { setRatingAttr(doc, it.coerceIn(0, 5)) }
        label?.let { setLabelAttr(doc, it) }
        tags?.let { setSubjectTags(doc, it) }
    }

    // ──────────────────────────────────────────────
    // EXIF 字段映射内部实现
    // ──────────────────────────────────────────────

    private fun applyExifData(exif: ExifInterface, data: ExifData) {
        data.make?.takeIf { it.isNotBlank() }?.let { exif.setAttribute(ExifInterface.TAG_MAKE, it) }
        data.model?.takeIf { it.isNotBlank() }?.let { exif.setAttribute(ExifInterface.TAG_MODEL, it) }
        data.lensMake?.takeIf { it.isNotBlank() }?.let { exif.setAttribute(ExifInterface.TAG_LENS_MAKE, it) }
        data.lensModel?.takeIf { it.isNotBlank() }?.let { exif.setAttribute(ExifInterface.TAG_LENS_MODEL, it) }
        data.focalLength?.takeIf { it.isNotBlank() }?.let {
            exif.setAttribute(ExifInterface.TAG_FOCAL_LENGTH, toRationalString(it))
        }
        data.aperture?.takeIf { it.isNotBlank() }?.let {
            exif.setAttribute(ExifInterface.TAG_F_NUMBER, toRationalString(it))
        }
        data.shutterSpeed?.takeIf { it.isNotBlank() }?.let {
            exif.setAttribute(ExifInterface.TAG_EXPOSURE_TIME, toRationalString(it))
        }
        data.iso?.takeIf { it.isNotBlank() }?.let {
            exif.setAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, it)
        }
        data.dateTime?.takeIf { it.isNotBlank() }?.let {
            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, it)
            exif.setAttribute(ExifInterface.TAG_DATETIME, it)
        }
        data.flash?.takeIf { it.isNotBlank() }?.let { exif.setAttribute(ExifInterface.TAG_FLASH, it) }
        data.whiteBalance?.takeIf { it.isNotBlank() }?.let { exif.setAttribute(ExifInterface.TAG_WHITE_BALANCE, it) }
        data.meteringMode?.takeIf { it.isNotBlank() }?.let { exif.setAttribute(ExifInterface.TAG_METERING_MODE, it) }
        data.exposureProgram?.takeIf { it.isNotBlank() }?.let { exif.setAttribute(ExifInterface.TAG_EXPOSURE_PROGRAM, it) }
        val lat = data.gpsLatitude?.let { parseGpsToDecimal(it) }
        val lon = data.gpsLongitude?.let { parseGpsToDecimal(it) }
        if (lat != null && lon != null) {
            exif.setLatLong(lat, lon)
        } else {
            if (lat != null) setGpsDms(exif, isLatitude = true, lat)
            if (lon != null) setGpsDms(exif, isLatitude = false, lon)
        }
    }

    private fun applyExifUpdates(exif: ExifInterface, updates: Map<String, String>) {
        var lat: Double? = null
        var lon: Double? = null
        for ((field, rawValue) in updates) {
            val value = rawValue.trim()
            when (field) {
                "GPSLatitude" -> parseGpsToDecimal(value)?.let { lat = it }
                "GPSLongitude" -> parseGpsToDecimal(value)?.let { lon = it }
                "GPSAltitude" -> {
                    val rational = toRationalString(value)
                    exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, rational)
                    val negative = value.toDoubleOrNull()?.let { it < 0 } ?: false
                    exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, if (negative) "1" else "0")
                }
                else -> {
                    val tag = FIELD_TO_TAG[field]
                    if (tag != null) {
                        val v = if (field in RATIONAL_FIELDS) toRationalString(value) else value
                        exif.setAttribute(tag, v)
                    }
                }
            }
        }
        if (lat != null && lon != null) {
            exif.setLatLong(lat, lon)
        } else {
            lat?.let { setGpsDms(exif, isLatitude = true, it) }
            lon?.let { setGpsDms(exif, isLatitude = false, it) }
        }
    }

    private fun warnIfLimitedFormat(file: File) {
        val ext = file.extension.lowercase()
        if (ext in LIMITED_FORMATS) {
            Log.w(TAG, "Format '$ext' has limited ExifInterface support: ${file.absolutePath}")
        }
    }

    /** 把十进制度数写入 GPS DMS 属性（含 REF），用于仅有纬度或经度的情况。 */
    private fun setGpsDms(exif: ExifInterface, isLatitude: Boolean, decimal: Double) {
        val dms = decimalToDms(decimal)
        if (isLatitude) {
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, dms)
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, if (decimal >= 0) "N" else "S")
        } else {
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, dms)
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, if (decimal >= 0) "E" else "W")
        }
    }

    // ──────────────────────────────────────────────
    // XMP 读写内部实现
    // ──────────────────────────────────────────────

    private class XmpSegment(val xml: String, val start: Int, val totalLen: Int)

    private data class XmpInfo(val rating: Int, val label: String?, val tags: List<String>)

    /** 读取并解析 JPEG 中的 XMP APP1 段；非 JPEG / 无 XMP 返回 null。 */
    private fun readXmpSegment(filePath: String): XmpSegment? {
        return try {
            val file = File(filePath)
            if (!file.exists() || !file.canRead()) return null
            val bytes = file.readBytes()
            findXmpSegment(bytes)
        } catch (e: Exception) {
            Log.w(TAG, "readXmpSegment failed for $filePath: ${e.message}")
            null
        }
    }

    /**
     * 读-改-写 XMP：读取 JPEG -> 解析或新建 XMP DOM -> 应用 [modifier] -> 写回（原子）。
     */
    private fun modifyXmp(filePath: String, modifier: (Document) -> Unit): Boolean {
        val file = File(filePath)
        if (!file.exists() || !file.canWrite()) {
            Log.w(TAG, "File not writable or missing: $filePath")
            return false
        }
        return try {
            val bytes = file.readBytes()
            if (!isJpeg(bytes)) {
                Log.w(TAG, "Not a JPEG, XMP write unsupported: $filePath")
                return false
            }
            val existing = findXmpSegment(bytes)
            val doc = existing?.let { parseXmpDom(it.xml) } ?: createBlankXmpDocument()
            modifier(doc)
            val newXml = serializeXmp(doc)
            val maxXml = MAX_APP1_PAYLOAD - XMP_NAMESPACE.size
            if (newXml.size > maxXml) {
                Log.w(TAG, "XMP too large (${newXml.size} > $maxXml bytes), skipping: $filePath")
                return false
            }
            val outBytes = spliceXmp(bytes, existing, newXml)
            atomicWriteBytes(filePath, outBytes)
        } catch (e: Exception) {
            Log.w(TAG, "modifyXmp failed for $filePath: ${e.message}")
            false
        }
    }

    /** 在 JPEG 字节流中定位首个 XMP APP1 段。 */
    private fun findXmpSegment(bytes: ByteArray): XmpSegment? {
        if (!isJpeg(bytes)) return null
        var i = 2
        while (i + 3 < bytes.size) {
            if (bytes[i] != 0xFF.toByte()) return null
            val marker = bytes[i + 1].toInt() and 0xFF
            // SOI / EOI
            if (marker == 0xD8 || marker == 0xD9) { i += 2; continue }
            // 无长度独立标记：TEM / RSTn
            if (marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) { i += 2; continue }
            // SOS：之后为熵编码数据，APP 段不会再出现
            if (marker == 0xDA) return null
            val len = ((bytes[i + 2].toInt() and 0xFF) shl 8) or (bytes[i + 3].toInt() and 0xFF)
            if (len < 2 || i + 2 + len > bytes.size) return null
            if (marker == 0xE1) {
                val payloadStart = i + 4
                val payloadEnd = i + 2 + len
                if (payloadEnd - payloadStart >= XMP_NAMESPACE.size &&
                    bytes.copyOfRange(payloadStart, payloadStart + XMP_NAMESPACE.size).contentEquals(XMP_NAMESPACE)
                ) {
                    val xmlBytes = bytes.copyOfRange(payloadStart + XMP_NAMESPACE.size, payloadEnd)
                    val xml = xmlBytes.toString(Charsets.UTF_8).trimEnd('\u0000').trim()
                    return XmpSegment(xml, i, 2 + len)
                }
            }
            i += 2 + len
        }
        return null
    }

    /**
     * 把新的 XMP XML 字节拼入 JPEG：已有 XMP 段则替换，否则在 SOI / APP0 之后插入新 APP1。
     */
    private fun spliceXmp(bytes: ByteArray, seg: XmpSegment?, newXml: ByteArray): ByteArray {
        val xmpPayload = XMP_NAMESPACE + newXml
        val lenField = xmpPayload.size + 2
        check(lenField <= 0xFFFF) { "XMP APP1 segment exceeds 65535 bytes" }
        val newSeg = ByteArray(4 + xmpPayload.size)
        newSeg[0] = 0xFF.toByte()
        newSeg[1] = 0xE1.toByte()
        newSeg[2] = ((lenField shr 8) and 0xFF).toByte()
        newSeg[3] = (lenField and 0xFF).toByte()
        System.arraycopy(xmpPayload, 0, newSeg, 4, xmpPayload.size)

        return if (seg != null) {
            val out = ByteArray(bytes.size - seg.totalLen + newSeg.size)
            System.arraycopy(bytes, 0, out, 0, seg.start)
            System.arraycopy(newSeg, 0, out, seg.start, newSeg.size)
            val tailSrc = seg.start + seg.totalLen
            System.arraycopy(bytes, tailSrc, out, seg.start + newSeg.size, bytes.size - tailSrc)
            out
        } else {
            val pos = findInsertPosition(bytes)
            val out = ByteArray(bytes.size + newSeg.size)
            System.arraycopy(bytes, 0, out, 0, pos)
            System.arraycopy(newSeg, 0, out, pos, newSeg.size)
            System.arraycopy(bytes, pos, out, pos + newSeg.size, bytes.size - pos)
            out
        }
    }

    /** XMP 插入位置：SOI 之后；若紧随 APP0(JFIF) 则跳过它。 */
    private fun findInsertPosition(bytes: ByteArray): Int {
        var i = 2
        if (i + 3 < bytes.size && bytes[i] == 0xFF.toByte() && bytes[i + 1] == 0xE0.toByte()) {
            val len = ((bytes[i + 2].toInt() and 0xFF) shl 8) or (bytes[i + 3].toInt() and 0xFF)
            if (len >= 2 && i + 2 + len <= bytes.size) {
                i += 2 + len
            }
        }
        return i
    }

    private fun isJpeg(bytes: ByteArray): Boolean =
        bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()

    /** 原子写入字节数组：临时文件 + rename（同文件系统下 rename 原子）。 */
    private fun atomicWriteBytes(filePath: String, bytes: ByteArray): Boolean {
        val file = File(filePath)
        val parent = file.parentFile
        if (parent == null || !parent.canWrite()) {
            Log.w(TAG, "Parent dir not writable: ${parent?.absolutePath}")
            return false
        }
        val tmp = File(parent, file.name + ".exiftmp")
        return try {
            FileOutputStream(tmp).use { it.write(bytes) }
            if (!tmp.renameTo(file)) {
                // 跨文件系统等极端情况：复制回退（非原子，但优于失败）
                FileOutputStream(file).use { out -> tmp.inputStream().use { it.copyTo(out) } }
                tmp.delete()
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Atomic write failed for $filePath: ${e.message}")
            tmp.delete()
            false
        }
    }

    // ──────────────────────────────────────────────
    // XMP 解析（XmlPullParser）
    // ──────────────────────────────────────────────

    /**
     * 用 XmlPullParser 解析 XMP，同时支持属性形式（xmp:Rating="5"）
     * 与元素形式（<xmp:Rating>5</xmp:Rating>）。
     */
    private fun parseXmp(xml: String): XmpInfo {
        var rating = 0
        var label: String? = null
        val tags = mutableListOf<String>()
        val text = StringBuilder()
        var inSubject = false
        try {
            val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()
            parser.setInput(xml.reader())
            var ev = parser.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                when (ev) {
                    XmlPullParser.START_TAG -> {
                        text.setLength(0)
                        // 属性形式：扫描所有属性
                        for (i in 0 until parser.attributeCount) {
                            val ans = parser.getAttributeNamespace(i)
                            val an = parser.getAttributeName(i)
                            val av = parser.getAttributeValue(i)
                            if (ans == NS_XMP && an == "Rating" && !av.isNullOrEmpty()) {
                                rating = av.toIntOrNull() ?: rating
                            } else if (ans == NS_XMP && an == "Label" && av != null) {
                                label = av
                            }
                        }
                        val ns = parser.namespace
                        val name = parser.name
                        if (ns == NS_DC && name == "subject") inSubject = true
                    }
                    XmlPullParser.TEXT -> text.append(parser.text ?: "")
                    XmlPullParser.END_TAG -> {
                        val ns = parser.namespace
                        val name = parser.name
                        val t = text.toString().trim()
                        when {
                            ns == NS_XMP && name == "Rating" -> t.toIntOrNull()?.let { rating = it }
                            ns == NS_XMP && name == "Label" -> if (t.isNotEmpty()) label = t
                            ns == NS_RDF && name == "li" && inSubject -> if (t.isNotEmpty()) tags.add(t)
                            ns == NS_DC && name == "subject" -> inSubject = false
                        }
                        text.setLength(0)
                    }
                }
                ev = parser.next()
            }
        } catch (e: Exception) {
            Log.w(TAG, "XMP parse error: ${e.message}")
        }
        return XmpInfo(rating, label, tags)
    }

    // ──────────────────────────────────────────────
    // XMP 生成 / 更新（DOM）
    // ──────────────────────────────────────────────

    private fun parseXmpDom(xml: String): Document? = try {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        factory.isValidating = false
        factory.isIgnoringElementContentWhitespace = true
        // XXE 防护：Android 解析器可能不支持某些 feature，逐个尝试并忽略
        trySetFeature(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        trySetFeature(factory, "http://xml.org/sax/features/external-general-entities", false)
        trySetFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false)
        factory.newDocumentBuilder().parse(xml.byteInputStream(Charsets.UTF_8))
    } catch (e: Exception) {
        Log.w(TAG, "XMP DOM parse failed: ${e.message}")
        null
    }

    private fun trySetFeature(factory: DocumentBuilderFactory, name: String, value: Boolean) {
        try {
            factory.setFeature(name, value)
        } catch (_: Throwable) {
            // Android 内置解析器可能不支持该 feature，忽略
        }
    }

    private fun createBlankXmpDocument(): Document {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val doc = factory.newDocumentBuilder().newDocument()
        doc.xmlStandalone = true
        val meta = doc.createElementNS(NS_X, "x:xmpmeta")
        val rdf = doc.createElementNS(NS_RDF, "rdf:RDF")
        val desc = doc.createElementNS(NS_RDF, "rdf:Description")
        desc.setAttributeNS(NS_RDF, "rdf:about", "")
        rdf.appendChild(desc)
        meta.appendChild(rdf)
        doc.appendChild(meta)
        return doc
    }

    private fun firstDescription(doc: Document): Element {
        val list = doc.getElementsByTagNameNS(NS_RDF, "Description")
        if (list.length > 0) return list.item(0) as Element
        val rdf = doc.getElementsByTagNameNS(NS_RDF, "RDF").item(0) as? Element
            ?: doc.documentElement as Element
        val desc = doc.createElementNS(NS_RDF, "rdf:Description")
        desc.setAttributeNS(NS_RDF, "rdf:about", "")
        rdf.appendChild(desc)
        return desc
    }

    private fun setRatingAttr(doc: Document, rating: Int) {
        firstDescription(doc).setAttributeNS(NS_XMP, "xmp:Rating", rating.coerceIn(0, 5).toString())
    }

    private fun setLabelAttr(doc: Document, label: String?) {
        val desc = firstDescription(doc)
        if (label.isNullOrBlank()) {
            desc.removeAttributeNS(NS_XMP, "Label")
        } else {
            val xmpLabel = LABEL_TO_XMP[label.lowercase()] ?: label
            desc.setAttributeNS(NS_XMP, "xmp:Label", xmpLabel)
        }
    }

    private fun setSubjectTags(doc: Document, tags: List<String>) {
        val desc = firstDescription(doc)
        // 移除已有 dc:subject
        val toRemove = mutableListOf<Node>()
        val existing = desc.getElementsByTagNameNS(NS_DC, "subject")
        for (i in 0 until existing.length) toRemove.add(existing.item(i))
        toRemove.forEach { it.parentNode?.removeChild(it) }
        if (tags.isEmpty()) return
        val subject = doc.createElementNS(NS_DC, "dc:subject")
        val bag = doc.createElementNS(NS_RDF, "rdf:Bag")
        tags.map { it.trim() }.filter { it.isNotEmpty() }.forEach { tag ->
            val li = doc.createElementNS(NS_RDF, "rdf:li")
            li.textContent = tag
            bag.appendChild(li)
        }
        subject.appendChild(bag)
        desc.appendChild(subject)
    }

    private fun serializeXmp(doc: Document): ByteArray {
        val sw = StringWriter()
        val tf = TransformerFactory.newInstance()
        try {
            tf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        } catch (_: Throwable) {
            // 部分平台不支持，忽略
        }
        val transformer = tf.newTransformer().apply {
            setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
            setOutputProperty(OutputKeys.ENCODING, "UTF-8")
            setOutputProperty(OutputKeys.INDENT, "no")
        }
        transformer.transform(DOMSource(doc), StreamResult(sw))
        val packet = buildString {
            append("<?xpacket begin=\"\uFEFF\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>\n")
            append(sw.toString())
            append("\n<?xpacket end=\"w\"?>")
        }
        return packet.toByteArray(Charsets.UTF_8)
    }

    // ──────────────────────────────────────────────
    // 数值转换工具
    // ──────────────────────────────────────────────

    /** 把小数字符串转为 EXIF 有理数 "n/d" 形式；已是 "n/d" 则原样返回。 */
    private fun toRationalString(value: String): String {
        val v = value.trim()
        if (v.contains('/')) return v
        val d = v.toDoubleOrNull() ?: return v
        if (d.isNaN() || d.isInfinite()) return v
        val negative = d < 0
        val abs = kotlin.math.abs(d)
        val denom = 1_000_000L
        val num = (abs * denom).toLong()
        val g = gcd(num, denom)
        val n = num / g
        val dd = denom / g
        return if (negative) "-$n/$dd" else "$n/$dd"
    }

    private fun gcd(a: Long, b: Long): Long {
        var x = kotlin.math.abs(a)
        var y = kotlin.math.abs(b)
        while (y != 0L) {
            val t = y
            y = x % y
            x = t
        }
        return if (x == 0L) 1L else x
    }

    /**
     * 解析 GPS 字符串为十进制度数。
     * 支持："12.345"（十进制，保留正负号）/ "12,30,0"（DMS）/ "12/1,30/1,0/1"（DMS 有理数）。
     * DMS 形式取绝对值（南北/东西半球由 REF 决定，DMS 本身恒为正）。
     */
    private fun parseGpsToDecimal(value: String): Double? {
        val v = value.trim()
        if (v.isEmpty()) return null
        v.toDoubleOrNull()?.let { return it }
        val parts = v.split(',')
        if (parts.size == 3) {
            val d = parseRational(parts[0])?.let { kotlin.math.abs(it) } ?: return null
            val m = parseRational(parts[1])?.let { kotlin.math.abs(it) } ?: return null
            val s = parseRational(parts[2])?.let { kotlin.math.abs(it) } ?: return null
            return d + m / 60.0 + s / 3600.0
        }
        return null
    }

    private fun parseRational(s: String): Double? {
        val t = s.trim()
        return if (t.contains('/')) {
            val (n, d) = t.split('/')
            n.toDoubleOrNull()?.let { nn ->
                d.toDoubleOrNull()?.let { dd -> if (dd != 0.0) nn / dd else null }
            }
        } else {
            t.toDoubleOrNull()
        }
    }

    /** 十进制度数 -> EXIF DMS 有理数字符串 "d/1,m/1,s/1000"。 */
    private fun decimalToDms(value: Double): String {
        val abs = kotlin.math.abs(value)
        val d = abs.toInt()
        val mFull = (abs - d) * 60.0
        val m = mFull.toInt()
        val s = (mFull - m) * 60.0
        val sNum = (s * 1000).toLong()
        return "$d/1,$m/1,$sNum/1000"
    }
}

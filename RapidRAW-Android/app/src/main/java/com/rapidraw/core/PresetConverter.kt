package com.rapidraw.core

import android.content.ContentResolver
import android.net.Uri
import com.rapidraw.data.model.Adjustments
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Document
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Lightroom preset converter.
 * Imports .xmp (Adobe XMP) and .lrtemplate (Lua-based) preset files
 * and maps their adjustment parameters to our [Adjustments] model.
 */
object PresetConverter {

    data class ImportResult(
        val name: String,
        val adjustments: Adjustments,
    )

    /**
     * Import an XMP preset file.
     * Parses the XML-based Adobe XMP format and extracts crs:* adjustment values.
     */
    fun importXmp(content: String): ImportResult? {
        return try {
            val builder = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                // v1.5.5 hotfix: 禁用 DTD 解析和外部实体，防止恶意 XMP 文件触发 XXE 攻击。
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
                isXIncludeAware = false
                isExpandEntityReferences = false
            }.newDocumentBuilder()
            val doc = builder.parse(content.byteInputStream())
            val name = extractXmpName(doc)
            val adjustments = parseXmpAdjustments(doc)
            ImportResult(name = name, adjustments = adjustments)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Import an .lrtemplate preset file.
     * Uses regex to parse the Lua-based Lightroom preset format.
     */
    fun importLrtemplate(content: String): ImportResult? {
        return try {
            val name = extractLrtemplateName(content)
            val adjustments = parseLrtemplateAdjustments(content)
            ImportResult(name = name, adjustments = adjustments)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Import a preset file by URI.
     * Determines the file type from the extension and delegates to the appropriate parser.
     * v1.5.5 hotfix: 改为 suspend 函数，强制在 IO 调度器上执行，
     * 避免在主线程上解析大型 XMP 文件导致 ANR。
     */
    suspend fun importFile(uri: Uri, contentResolver: ContentResolver): ImportResult? =
        withContext(Dispatchers.IO) {
            try {
                val content = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: return@withContext null
                val fileName = uri.lastPathSegment ?: uri.toString()
                when {
                    fileName.endsWith(".xmp", ignoreCase = true) -> importXmp(content)
                    fileName.endsWith(".lrtemplate", ignoreCase = true) -> importLrtemplate(content)
                    else -> null
                }
            } catch (_: Exception) {
                null
            }
        }

    // ── XMP Parsing ─────────────────────────────────────────────

    private fun extractXmpName(doc: Document): String {
        // Try crs:Name first
        val nameNodes = doc.getElementsByTagNameNS("http://ns.adobe.com/camera-raw-settings/1.0/", "Name")
        if (nameNodes.length > 0) {
            return nameNodes.item(0).textContent.trim().ifBlank { "Untitled Preset" }
        }
        // Fallback: try rdf:about attribute on Description element
        val descNodes = doc.getElementsByTagNameNS("http://www.w3.org/1999/02/22-rdf-syntax-ns#", "Description")
        if (descNodes.length > 0) {
            val about = descNodes.item(0).attributes?.getNamedItemNS("http://www.w3.org/1999/02/22-rdf-syntax-ns#", "about")?.nodeValue
            if (!about.isNullOrBlank() && about != "") {
                return about.trim()
            }
        }
        return "Untitled Preset"
    }

    private fun parseXmpAdjustments(doc: Document): Adjustments {
        val crsNs = "http://ns.adobe.com/camera-raw-settings/1.0/"
        return Adjustments(
            exposure = getXmpFloat(doc, crsNs, "Exposure2012"),
            contrast = getXmpFloat(doc, crsNs, "Contrast2012"),
            highlights = getXmpFloat(doc, crsNs, "Highlights2012"),
            shadows = getXmpFloat(doc, crsNs, "Shadows2012"),
            whites = getXmpFloat(doc, crsNs, "Whites2012"),
            blacks = getXmpFloat(doc, crsNs, "Blacks2012"),
            temperature = getXmpFloat(doc, crsNs, "Temperature"),
            tint = getXmpFloat(doc, crsNs, "Tint"),
            saturation = getXmpFloat(doc, crsNs, "Saturation"),
            vibrance = getXmpFloat(doc, crsNs, "Vibrance"),
            sharpness = getXmpFloat(doc, crsNs, "Sharpness"),
            clarity = getXmpFloat(doc, crsNs, "Clarity2012"),
            dehaze = getXmpFloat(doc, crsNs, "Dehaze"),
            lumaNoiseReduction = getXmpFloat(doc, crsNs, "LuminanceSmoothing"),
            colorNoiseReduction = getXmpFloat(doc, crsNs, "ColorNoiseReduction"),
            vignetteAmount = getXmpFloat(doc, crsNs, "VignetteAmount"),
            vignetteMidpoint = getXmpFloat(doc, crsNs, "VignetteMidpoint"),
            grainAmount = getXmpFloat(doc, crsNs, "GrainAmount"),
            grainSize = getXmpFloat(doc, crsNs, "GrainSize"),
            chromaticAberrationRedCyan = getXmpFloat(doc, crsNs, "ChromaticAberrationR"),
        )
    }

    private fun getXmpFloat(doc: Document, namespace: String, localName: String): Float {
        return try {
            val nodes = doc.getElementsByTagNameNS(namespace, localName)
            if (nodes.length > 0) nodes.item(0).textContent.trim().toFloat() else 0f
        } catch (_: Exception) {
            0f
        }
    }

    // ── .lrtemplate Parsing ─────────────────────────────────────

    private val lrKeyValueRegex = Regex("""(\w+)\s*=\s*(-?[\d.]+)""")

    /** Lua key name → Adjustments field setter */
    private val lrKeyMap = mapOf(
        "Exposure2012" to "exposure",
        "Contrast2012" to "contrast",
        "Highlights2012" to "highlights",
        "Shadows2012" to "shadows",
        "Whites2012" to "whites",
        "Blacks2012" to "blacks",
        "Temperature" to "temperature",
        "Tint" to "tint",
        "Saturation" to "saturation",
        "Vibrance" to "vibrance",
        "Sharpness" to "sharpness",
        "Clarity2012" to "clarity",
        "Dehaze" to "dehaze",
        "LuminanceSmoothing" to "lumaNoiseReduction",
        "ColorNoiseReduction" to "colorNoiseReduction",
        "VignetteAmount" to "vignetteAmount",
        "VignetteMidpoint" to "vignetteMidpoint",
        "GrainAmount" to "grainAmount",
        "GrainSize" to "grainSize",
        "ChromaticAberrationR" to "chromaticAberrationRedCyan",
    )

    private fun extractLrtemplateName(content: String): String {
        // Look for value= line (e.g., value = "My Preset Name")
        val nameRegex = Regex("""value\s*=\s*"([^"]+)"""")
        val match = nameRegex.find(content)
        return match?.groupValues?.get(1)?.trim()?.ifBlank { "Untitled Preset" } ?: "Untitled Preset"
    }

    private fun parseLrtemplateAdjustments(content: String): Adjustments {
        // Find the "s = {" block containing adjustment parameters
        val settingsRegex = Regex("""s\s*=\s*\{([^}]*)\}""", RegexOption.DOT_MATCHES_ALL)
        val settingsBlock = settingsRegex.find(content)?.groupValues?.get(1) ?: return Adjustments()

        val parsed = mutableMapOf<String, Float>()
        lrKeyValueRegex.findAll(settingsBlock).forEach { match ->
            val key = match.groupValues[1]
            val value = match.groupValues[2].toFloatOrNull() ?: return@forEach
            val mappedKey = lrKeyMap[key] ?: return@forEach
            parsed[mappedKey] = value
        }

        return Adjustments(
            exposure = parsed.getOrDefault("exposure", 0f),
            contrast = parsed.getOrDefault("contrast", 0f),
            highlights = parsed.getOrDefault("highlights", 0f),
            shadows = parsed.getOrDefault("shadows", 0f),
            whites = parsed.getOrDefault("whites", 0f),
            blacks = parsed.getOrDefault("blacks", 0f),
            temperature = parsed.getOrDefault("temperature", 0f),
            tint = parsed.getOrDefault("tint", 0f),
            saturation = parsed.getOrDefault("saturation", 0f),
            vibrance = parsed.getOrDefault("vibrance", 0f),
            sharpness = parsed.getOrDefault("sharpness", 0f),
            clarity = parsed.getOrDefault("clarity", 0f),
            dehaze = parsed.getOrDefault("dehaze", 0f),
            lumaNoiseReduction = parsed.getOrDefault("lumaNoiseReduction", 0f),
            colorNoiseReduction = parsed.getOrDefault("colorNoiseReduction", 0f),
            vignetteAmount = parsed.getOrDefault("vignetteAmount", 0f),
            vignetteMidpoint = parsed.getOrDefault("vignetteMidpoint", 0f),
            grainAmount = parsed.getOrDefault("grainAmount", 0f),
            grainSize = parsed.getOrDefault("grainSize", 0f),
            chromaticAberrationRedCyan = parsed.getOrDefault("chromaticAberrationRedCyan", 0f),
        )
    }
}

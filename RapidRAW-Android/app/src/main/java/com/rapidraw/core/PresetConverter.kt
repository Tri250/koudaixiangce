package com.rapidraw.core

import android.util.Log
import com.rapidraw.data.model.Adjustments
import com.rapidraw.data.model.Coord
import com.rapidraw.data.model.HslChannel
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

/**
 * XMP / LrTemplate 预设导入转换器。
 *
 * 参照原 RapidRAW (CyberTimon/RapidRAW) 的 Rust 实现 `preset_converter.rs`，
 * 将 Lightroom 的 XMP / LrTemplate 预设映射到 RapidRAW 的 [Adjustments]。
 *
 * - XMP: 使用 Android 内置的 [XmlPullParser] 真实解析 XML，提取 `crs:` 命名空间属性、
 *   `<rdf:Seq>` 内的 ToneCurve 点序列，以及 `<crs:Name><rdf:Alt><rdf:li>` 多语言名称。
 * - LrTemplate: 使用递归下降解析器解析 Lightroom 旧版 key-value 字典格式。
 *
 * 未识别或无对应目标的字段会记录到 [PresetConversionResult.warnings]，不会静默丢弃。
 */
data class PresetConversionResult(
    val name: String,
    val adjustments: Adjustments,
    val warnings: List<String> = emptyList(),
)

object PresetConverter {

    private const val TAG = "PresetConverter"
    private const val DEFAULT_PRESET_NAME = "Imported Preset"

    // Temperature AsShot 基准换算常量（与原 Rust 实现一致）
    private const val AS_SHOT_DEFAULT_K = 5500.0
    private const val MAX_MIRED_SHIFT = 150.0

    // ToneCurvePV2012 阴影端衰减常量（与原 Rust 实现一致）
    private const val SHADOW_RANGE_END = 64.0
    private const val SHADOW_DAMPEN_START = 0.8
    private const val SHADOW_DAMPEN_END = 1.0

    /** 从 XMP 字符串导入预设。 */
    fun importFromXmp(xmpContent: String): PresetConversionResult {
        val warnings = mutableListOf<String>()
        val attrs = LinkedHashMap<String, String>()
        val curves = LinkedHashMap<String, MutableList<Pair<Int, Int>>>()
        var name: String? = null

        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xmpContent))

            var currentCurveKey: String? = null
            var currentCurvePoints: MutableList<Pair<Int, Int>>? = null
            var pendingCurveLi = false
            var inCrsName = false
            var nameCaptured = false

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        val tagLocal = crsLocal(parser.name, parser.prefix)

                        // 收集所有 crs: 命名空间下的属性（标量调整值通常作为属性出现）
                        for (i in 0 until parser.attributeCount) {
                            val attrKey = crsLocal(parser.getAttributeName(i), parser.getAttributePrefix(i))
                            if (attrKey != null) {
                                attrs[attrKey] = parser.getAttributeValue(i)
                            }
                        }

                        when {
                            tagLocal == "Name" -> {
                                inCrsName = true
                            }
                            tagLocal != null && tagLocal.startsWith("ToneCurve") -> {
                                currentCurveKey = tagLocal
                                currentCurvePoints = mutableListOf()
                            }
                            isRdfLi(parser.name, parser.prefix) && currentCurveKey != null -> {
                                pendingCurveLi = true
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        val text = parser.text ?: ""

                        // crs:Name 的第一个非空文本（直接文本或 rdf:li 内文本）
                        if (inCrsName && !nameCaptured) {
                            val trimmed = text.trim()
                            if (trimmed.isNotEmpty()) {
                                name = trimmed
                                nameCaptured = true
                            }
                        }

                        // ToneCurve rdf:li 内的 "x, y" 文本
                        if (pendingCurveLi && currentCurvePoints != null) {
                            val trimmed = text.trim()
                            if (trimmed.isNotEmpty()) {
                                val parts = trimmed.split(",").map { it.trim() }
                                if (parts.size == 2) {
                                    val x = parts[0].toIntOrNull()
                                    val y = parts[1].toIntOrNull()
                                    if (x != null && y != null) {
                                        currentCurvePoints!!.add(x to y)
                                    }
                                }
                            }
                            pendingCurveLi = false
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val tagLocal = crsLocal(parser.name, parser.prefix)
                        when {
                            tagLocal == "Name" -> {
                                inCrsName = false
                            }
                            tagLocal != null && tagLocal.startsWith("ToneCurve")
                                && currentCurveKey != null && currentCurvePoints != null -> {
                                if (currentCurvePoints!!.isNotEmpty()) {
                                    curves[currentCurveKey!!] = currentCurvePoints!!
                                }
                                currentCurveKey = null
                                currentCurvePoints = null
                                pendingCurveLi = false
                            }
                            isRdfLi(parser.name, parser.prefix) -> {
                                pendingCurveLi = false
                            }
                        }
                    }
                }
                event = parser.next()
            }
        } catch (e: XmlPullParserException) {
            warnings.add("XMP parse error: ${e.message}")
        } catch (e: Exception) {
            warnings.add("XMP read error: ${e.message}")
        }

        val resolvedName = name?.takeIf { it.isNotBlank() } ?: DEFAULT_PRESET_NAME
        val adjustments = buildAdjustments(attrs, curves, warnings)
        return PresetConversionResult(
            name = resolvedName,
            adjustments = adjustments,
            warnings = warnings,
        )
    }

    /** 从 LrTemplate 字符串导入预设。 */
    fun importFromLrTemplate(templateContent: String): PresetConversionResult {
        val warnings = mutableListOf<String>()
        val parsed = try {
            LrTemplateParser(templateContent).parseTopLevel()
        } catch (e: Exception) {
            warnings.add("LrTemplate parse error: ${e.message}")
            emptyMap<String, Any>()
        }

        val settings = selectSettingsDict(parsed)
        val name = extractLrName(parsed)

        val raw = LinkedHashMap<String, String>()
        val curves = LinkedHashMap<String, List<Pair<Int, Int>>>()

        for ((k, v) in settings) {
            when (k) {
                "ToneCurvePV2012", "ToneCurvePV2012Red",
                "ToneCurvePV2012Green", "ToneCurvePV2012Blue" -> {
                    val pts = parseCurveValue(v)
                    if (pts.isNotEmpty()) curves[k] = pts
                }
                else -> {
                    val s = anyToRawString(v)
                    if (s != null) raw[k] = s
                }
            }
        }

        val adjustments = buildAdjustments(raw, curves, warnings)
        return PresetConversionResult(
            name = name,
            adjustments = adjustments,
            warnings = warnings,
        )
    }

    /** 自动检测格式并导入。 */
    fun importAuto(content: String, fileName: String): PresetConversionResult {
        val trimmed = content.trimStart()
        val lowerName = fileName.lowercase()
        return when {
            trimmed.startsWith("<?xml") ||
                trimmed.startsWith("<x:xmpmeta") ||
                trimmed.startsWith("<xmpmeta") ||
                trimmed.startsWith("<rdf:RDF") -> importFromXmp(content)
            lowerName.endsWith(".xmp") -> importFromXmp(content)
            lowerName.endsWith(".lrtemplate") -> importFromLrTemplate(content)
            trimmed.startsWith("s =") || trimmed.startsWith("s=") ||
                trimmed.startsWith("{") -> importFromLrTemplate(content)
            content.contains("crs:") || content.contains("<rdf:") -> importFromXmp(content)
            else -> {
                Log.w(TAG, "Could not reliably detect preset format for '$fileName'; trying LrTemplate.")
                importFromLrTemplate(content)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 核心：将解析得到的标量属性 + 曲线点构建为 Adjustments
    // ─────────────────────────────────────────────────────────────────────────────

    private fun buildAdjustments(
        raw: Map<String, String>,
        curves: Map<String, List<Pair<Int, Int>>>,
        warnings: MutableList<String>,
    ): Adjustments {
        var adj = Adjustments()
        val consumed = mutableSetOf<String>()

        fun num(key: String): Float? {
            val v = raw[key] ?: return null
            val f = v.trimStart('+').toFloatOrNull()
            if (f != null) consumed.add(key)
            return f
        }

        // —— 直接标量映射（无特殊换算）——
        num("Exposure2012")?.let { adj = adj.copy(exposure = it) }
        num("Contrast2012")?.let { adj = adj.copy(contrast = it) }
        num("Highlights2012")?.let { adj = adj.copy(highlights = it) }
        num("Whites2012")?.let { adj = adj.copy(whites = it) }
        num("Blacks2012")?.let { adj = adj.copy(blacks = it) }
        num("Clarity2012")?.let { adj = adj.copy(clarity = it) }
        num("Dehaze")?.let { adj = adj.copy(dehaze = it) }
        num("Vibrance")?.let { adj = adj.copy(vibrance = it) }
        num("Saturation")?.let { adj = adj.copy(saturation = it) }
        num("Texture")?.let { adj = adj.copy(structure = it) }
        num("LuminanceSmoothing")?.let { adj = adj.copy(lumaNoiseReduction = it) }
        num("ColorNoiseReduction")?.let { adj = adj.copy(colorNoiseReduction = it) }
        num("PostCropVignetteAmount")?.let { adj = adj.copy(vignetteAmount = it) }
        num("PostCropVignetteMidpoint")?.let { adj = adj.copy(vignetteMidpoint = it) }
        num("PostCropVignetteFeather")?.let { adj = adj.copy(vignetteFeather = it) }
        num("PostCropVignetteRoundness")?.let { adj = adj.copy(vignetteRoundness = it) }
        num("GrainAmount")?.let { adj = adj.copy(grainAmount = it) }
        num("GrainSize")?.let { adj = adj.copy(grainSize = it) }
        num("GrainFrequency")?.let { adj = adj.copy(grainRoughness = it) }

        // Chromatic Aberration：同时兼容 RedCyan/BlueYellow 与 R/B 两种 XMP key 变体
        num("ChromaticAberrationRedCyan")?.let { adj = adj.copy(chromaticAberrationRedCyan = it) }
        num("ChromaticAberrationR")?.let { adj = adj.copy(chromaticAberrationRedCyan = it) }
        num("ChromaticAberrationBlueYellow")?.let { adj = adj.copy(chromaticAberrationBlueYellow = it) }
        num("ChromaticAberrationB")?.let { adj = adj.copy(chromaticAberrationBlueYellow = it) }

        // —— Shadows2012：×1.5 并封顶 100（原 Rust 特殊换算）——
        num("Shadows2012")?.let { v ->
            adj = adj.copy(shadows = (v * 1.5f).coerceAtMost(100f))
        }

        // —— Sharpness：(/150)*100，钳制到 0..100（原 Rust 特殊换算）——
        num("Sharpness")?.let { v ->
            adj = adj.copy(sharpness = (v / 150f * 100f).coerceIn(0f, 100f))
        }

        // —— Temperature：AsShot 基准的 mired 换算（原 Rust 特殊换算）——
        val tempRaw = raw["Temperature"]?.trimStart('+')?.toFloatOrNull()
        if (tempRaw != null) {
            consumed.add("Temperature")
            val asShotRaw = raw["AsShotTemperature"]?.trimStart('+')?.toFloatOrNull()
            if (asShotRaw != null) consumed.add("AsShotTemperature")
            if (raw.containsKey("AsShotTint")) consumed.add("AsShotTint")

            val asShotK = asShotRaw?.toDouble() ?: AS_SHOT_DEFAULT_K
            val adjustedK = tempRaw.toDouble()
            val miredAdjusted = 1_000_000.0 / adjustedK
            val miredAsShot = 1_000_000.0 / asShotK
            val miredDelta = miredAdjusted - miredAsShot
            val tempValue = ((-miredDelta / MAX_MIRED_SHIFT) * 100.0)
                .toFloat()
                .coerceIn(-100f, 100f)
            adj = adj.copy(temperature = tempValue)
        }

        // —— Tint：(/150)*100，钳制到 -100..100（原 Rust 特殊换算）——
        num("Tint")?.let { v ->
            adj = adj.copy(tint = (v / 150f * 100f).coerceIn(-100f, 100f))
        }

        // —— HSL 8 色混合 ——
        // 注意：Rust 实现中 Hue 分量会乘以 0.75
        adj = adj.copy(hslReds = applyHsl(raw, consumed, "Red", adj.hslReds))
        adj = adj.copy(hslOranges = applyHsl(raw, consumed, "Orange", adj.hslOranges))
        adj = adj.copy(hslYellows = applyHsl(raw, consumed, "Yellow", adj.hslYellows))
        adj = adj.copy(hslGreens = applyHsl(raw, consumed, "Green", adj.hslGreens))
        adj = adj.copy(hslAquas = applyHsl(raw, consumed, "Aqua", adj.hslAquas))
        adj = adj.copy(hslBlues = applyHsl(raw, consumed, "Blue", adj.hslBlues))
        adj = adj.copy(hslPurples = applyHsl(raw, consumed, "Purple", adj.hslPurples))
        adj = adj.copy(hslMagentas = applyHsl(raw, consumed, "Magenta", adj.hslMagentas))

        // —— Color Grading（SplitToning + ColorGrade 合并到 colorGrading）——
        var cg = adj.colorGrading
        var cgChanged = false
        fun cgNum(key: String): Float? {
            val v = raw[key]?.trimStart('+')?.toFloatOrNull()
            if (v != null) consumed.add(key)
            return v
        }
        // shadows
        cgNum("SplitToningShadowHue")?.let { cg = cg.copy(shadows = cg.shadows.copy(hue = it)); cgChanged = true }
        cgNum("SplitToningShadowSaturation")?.let { cg = cg.copy(shadows = cg.shadows.copy(saturation = it)); cgChanged = true }
        cgNum("ColorGradeShadowLum")?.let { cg = cg.copy(shadows = cg.shadows.copy(luminance = it)); cgChanged = true }
        // midtones
        cgNum("ColorGradeMidtoneHue")?.let { cg = cg.copy(midtones = cg.midtones.copy(hue = it)); cgChanged = true }
        cgNum("ColorGradeMidtoneSat")?.let { cg = cg.copy(midtones = cg.midtones.copy(saturation = it)); cgChanged = true }
        cgNum("ColorGradeMidtoneLum")?.let { cg = cg.copy(midtones = cg.midtones.copy(luminance = it)); cgChanged = true }
        // highlights
        cgNum("SplitToningHighlightHue")?.let { cg = cg.copy(highlights = cg.highlights.copy(hue = it)); cgChanged = true }
        cgNum("SplitToningHighlightSaturation")?.let { cg = cg.copy(highlights = cg.highlights.copy(saturation = it)); cgChanged = true }
        cgNum("ColorGradeHighlightLum")?.let { cg = cg.copy(highlights = cg.highlights.copy(luminance = it)); cgChanged = true }
        // blending / balance
        cgNum("ColorGradeBlending")?.let { cg = cg.copy(blending = it); cgChanged = true }
        cgNum("SplitToningBalance")?.let { cg = cg.copy(balance = it); cgChanged = true }
        if (cgChanged) adj = adj.copy(colorGrading = cg)

        // —— Tone Curve 点序列 → List<Coord> ——
        // ToneCurvePV2012 (luma) 应用阴影端衰减，其余通道直接使用（与原 Rust 一致）
        applyCurve(curves, "ToneCurvePV2012", applyDamp = true) { c -> adj = adj.copy(lumaCurve = c) }
        applyCurve(curves, "ToneCurvePV2012Red", applyDamp = false) { c -> adj = adj.copy(redCurve = c) }
        applyCurve(curves, "ToneCurvePV2012Green", applyDamp = false) { c -> adj = adj.copy(greenCurve = c) }
        applyCurve(curves, "ToneCurvePV2012Blue", applyDamp = false) { c -> adj = adj.copy(blueCurve = c) }

        // —— 未识别 / 无对应目标字段的警告 ——
        for ((k, v) in raw) {
            if (k in consumed) continue
            if (k in RECOGNIZED_NO_TARGET) {
                warnings.add("Field '$k' has no corresponding Adjustments target (value='$v'), skipped")
            } else {
                warnings.add("Unmapped field '$k'='$v' (no converter mapping)")
            }
        }

        return adj
    }

    /** 已知但 Adjustments 中无对应目标字段（原 Rust 输出 JSON 中存在，但 Kotlin 模型无此字段）。 */
    private val RECOGNIZED_NO_TARGET = setOf(
        "SharpenRadius",
        "SharpenDetail",
        "SharpenEdgeMasking",
        "ColorNoiseReductionDetail",
        "ColorNoiseReductionSmoothness",
        "ColorGradeGlobalHue",
        "ColorGradeGlobalSat",
        "ColorGradeGlobalLum",
    )

    /**
     * 将某一 HSL 色彩通道的 Hue/Saturation/Luminance 合并到 [current] 通道上。
     * 仅覆盖预设中实际出现的分量，保留其余分量不变。返回合并后的通道。
     */
    private fun applyHsl(
        raw: Map<String, String>,
        consumed: MutableSet<String>,
        src: String,
        current: HslChannel,
    ): HslChannel {
        var ch = current

        val hueRaw = raw["HueAdjustment$src"]?.trimStart('+')?.toFloatOrNull()
        if (hueRaw != null) {
            ch = ch.copy(hue = hueRaw * 0.75f)
            consumed.add("HueAdjustment$src")
        }
        val satRaw = raw["SaturationAdjustment$src"]?.trimStart('+')?.toFloatOrNull()
        if (satRaw != null) {
            ch = ch.copy(saturation = satRaw)
            consumed.add("SaturationAdjustment$src")
        }
        val lumRaw = raw["LuminanceAdjustment$src"]?.trimStart('+')?.toFloatOrNull()
        if (lumRaw != null) {
            ch = ch.copy(luminance = lumRaw)
            consumed.add("LuminanceAdjustment$src")
        }
        return ch
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 工具函数
    // ─────────────────────────────────────────────────────────────────────────────

    /** 给定一个完整限定名（"crs:Name"）或本地名+前缀，返回 crs 命名空间的本地名，否则 null。 */
    private fun crsLocal(name: String?, prefix: String?): String? {
        if (name == null) return null
        if (name.startsWith("crs:")) return name.substring(4)
        if (prefix == "crs") return name
        return null
    }

    /** 判断是否为 rdf:li 元素（兼容 namespace-aware 开/关两种模式）。 */
    private fun isRdfLi(name: String?, prefix: String?): Boolean =
        name == "rdf:li" || (name == "li" && prefix == "rdf")

    private fun applyCurve(
        curves: Map<String, List<Pair<Int, Int>>>,
        key: String,
        applyDamp: Boolean,
        set: (List<Coord>) -> Unit,
    ) {
        val pts = curves[key] ?: return
        val coords = pts.map { (x, y) ->
            var fy = y
            if (applyDamp) {
                val xf = x.toDouble()
                val yf = y.toDouble()
                if (yf > xf && xf < SHADOW_RANGE_END) {
                    val lift = yf - xf
                    val progress = xf / SHADOW_RANGE_END
                    val dampening = SHADOW_DAMPEN_START + (SHADOW_DAMPEN_END - SHADOW_DAMPEN_START) * progress
                    val new_y = xf + lift * dampening
                    fy = kotlin.math.round(new_y).toInt().coerceIn(0, 255)
                }
            }
            Coord(x.toFloat(), fy.toFloat())
        }
        set(coords)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // LrTemplate 解析
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * 递归下降解析 Lightroom 旧版 key-value 字典格式。
     * 支持 `key = value;`、字符串、数字、布尔、嵌套字典 `{}`、列表 `{1, 2, 3}` 与注释。
     */
    private class LrTemplateParser(private val src: String) {
        private val n = src.length
        private var pos = 0

        fun parseTopLevel(): Map<String, Any> {
            val map = LinkedHashMap<String, Any>()
            skipWsAndComments()
            var hadBrace = false
            if (peek() == '{') {
                pos++
                hadBrace = true
                skipWsAndComments()
            }
            while (pos < n) {
                skipWsAndComments()
                val c = peek() ?: break
                if (c == '}') break
                val key = readKey()
                if (key == null) {
                    // 无法识别为 key，推进一个字符避免死循环
                    pos++
                    continue
                }
                skipWsAndComments()
                if (peek() == '=') {
                    pos++
                    val v = parseValue()
                    map[key] = v
                }
                skipWsAndComments()
                if (peek() == ',' || peek() == ';') pos++
            }
            if (hadBrace && peek() == '}') pos++
            return map
        }

        private fun peek(): Char? = if (pos < n) src[pos] else null

        private fun skipWsAndComments() {
            while (pos < n) {
                val c = src[pos]
                if (c.isWhitespace()) {
                    pos++
                    continue
                }
                if (c == '/' && pos + 1 < n) {
                    when (src[pos + 1]) {
                        '/' -> {
                            pos += 2
                            while (pos < n && src[pos] != '\n') pos++
                            continue
                        }
                        '*' -> {
                            pos += 2
                            while (pos < n && !(src[pos] == '*' && pos + 1 < n && src[pos + 1] == '/')) pos++
                            if (pos < n) pos += 2
                            continue
                        }
                    }
                }
                break
            }
        }

        private fun readKey(): String? {
            skipWsAndComments()
            if (pos >= n) return null
            val c = src[pos]
            if (c == '"') return readString()
            if (c.isLetter() || c == '_') {
                val start = pos
                while (pos < n && (src[pos].isLetterOrDigit() || src[pos] == '_' || src[pos] == '.')) pos++
                return src.substring(start, pos)
            }
            return null
        }

        private fun readString(): String {
            pos++ // 跳过开始引号
            val sb = StringBuilder()
            while (pos < n && src[pos] != '"') {
                if (src[pos] == '\\' && pos + 1 < n) {
                    pos++
                    when (src[pos]) {
                        'n' -> sb.append('\n')
                        't' -> sb.append('\t')
                        'r' -> sb.append('\r')
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        else -> sb.append(src[pos])
                    }
                    pos++
                } else {
                    sb.append(src[pos])
                    pos++
                }
            }
            if (pos < n) pos++ // 跳过结束引号
            return sb.toString()
        }

        private fun parseValue(): Any {
            skipWsAndComments()
            if (pos >= n) return ""
            val c = src[pos]
            if (c == '{') return parseBraced()
            if (c == '"') return readString()
            if (c == '(') return parseParenList()
            val start = pos
            while (pos < n) {
                val ch = src[pos]
                if (ch == ',' || ch == ';' || ch == '}' || ch == ')'
                    || ch.isWhitespace() || ch == '{' || ch == '('
                ) break
                pos++
            }
            val tok = src.substring(start, pos).trim()
            if (tok.isEmpty()) return ""
            tok.toIntOrNull()?.let { return it }
            tok.toFloatOrNull()?.let { return it }
            tok.toDoubleOrNull()?.let { return it.toFloat() }
            if (tok == "true") return true
            if (tok == "false") return false
            if (tok == "null" || tok == "nil") return ""
            return tok
        }

        private fun parseBraced(): Any {
            pos++ // 跳过 '{'
            skipWsAndComments()
            val isDict = lookaheadKeyEquals()
            return if (isDict) {
                val map = LinkedHashMap<String, Any>()
                while (true) {
                    skipWsAndComments()
                    if (pos >= n || src[pos] == '}') break
                    val key = readKey()
                    if (key == null) {
                        pos++
                        continue
                    }
                    skipWsAndComments()
                    if (peek() == '=') {
                        pos++
                        map[key] = parseValue()
                    } else {
                        break
                    }
                    skipWsAndComments()
                    if (peek() == ',' || peek() == ';') pos++
                }
                if (pos < n && src[pos] == '}') pos++
                map
            } else {
                val list = mutableListOf<Any>()
                while (true) {
                    skipWsAndComments()
                    if (pos >= n || src[pos] == '}') break
                    list.add(parseValue())
                    skipWsAndComments()
                    if (peek() == ',' || peek() == ';') pos++
                }
                if (pos < n && src[pos] == '}') pos++
                list
            }
        }

        private fun parseParenList(): List<Any> {
            pos++ // 跳过 '('
            val list = mutableListOf<Any>()
            while (true) {
                skipWsAndComments()
                if (pos >= n || src[pos] == ')') break
                list.add(parseValue())
                skipWsAndComments()
                if (peek() == ',' || peek() == ';') pos++
            }
            if (pos < n && src[pos] == ')') pos++
            return list
        }

        /** 前瞻：当前位置后是否为 `key =` 模式（用于区分字典与列表）。不改变 pos。 */
        private fun lookaheadKeyEquals(): Boolean {
            val save = pos
            skipWsAndComments()
            val c = peek()
            if (c == null || !(c.isLetter() || c == '_' || c == '"')) {
                pos = save
                return false
            }
            val key = readKey()
            if (key == null) {
                pos = save
                return false
            }
            skipWsAndComments()
            val eq = peek() == '='
            pos = save
            return eq
        }
    }

    /** 选择包含实际调整设置的子字典（`s.value` / `s` / `value` / 顶层）。 */
    private fun selectSettingsDict(parsed: Map<String, Any>): Map<String, Any> {
        val sMap = parsed["s"]
        if (sMap is Map<*, *>) {
            val value = sMap["value"]
            if (value is Map<*, *>) {
                @Suppress("UNCHECKED_CAST")
                return value as Map<String, Any>
            }
            @Suppress("UNCHECKED_CAST")
            return sMap as Map<String, Any>
        }
        val value = parsed["value"]
        if (value is Map<*, *>) {
            @Suppress("UNCHECKED_CAST")
            return value as Map<String, Any>
        }
        return parsed
    }

    private fun extractLrName(parsed: Map<String, Any>): String {
        val sMap = parsed["s"]
        if (sMap is Map<*, *>) {
            val v = sMap["internalName"]?.toString()
                ?: sMap["title"]?.toString()
                ?: sMap["displayName"]?.toString()
            if (!v.isNullOrEmpty()) return v
        }
        val top = parsed["internalName"]?.toString()
            ?: parsed["title"]?.toString()
            ?: parsed["displayName"]?.toString()
        if (!top.isNullOrEmpty()) return top
        return DEFAULT_PRESET_NAME
    }

    /** 将 LrTemplate 值转为可用于标量映射的字符串；列表/字典返回 null（由曲线/嵌套逻辑处理）。 */
    private fun anyToRawString(v: Any?): String? = when (v) {
        is String -> v
        is Number -> v.toString()
        is Boolean -> if (v) "1" else "0"
        is List<*>, is Map<*, *> -> null
        null -> null
        else -> v.toString()
    }

    /** 将曲线值（扁平/嵌套列表、字符串）解析为 (x, y) 点序列。 */
    private fun parseCurveValue(v: Any): List<Pair<Int, Int>> {
        val nums = mutableListOf<Int>()
        collectNumbers(v, nums)
        val pts = mutableListOf<Pair<Int, Int>>()
        var i = 0
        while (i + 1 < nums.size) {
            pts.add(nums[i] to nums[i + 1])
            i += 2
        }
        return pts
    }

    private fun collectNumbers(v: Any?, out: MutableList<Int>) {
        when (v) {
            is Number -> out.add(v.toInt())
            is String -> {
                v.trim().split(Regex("[,\\s]+")).forEach {
                    val t = it.trim()
                    if (t.isNotEmpty()) t.toIntOrNull()?.let { n -> out.add(n) }
                }
            }
            is List<*> -> v.forEach { collectNumbers(it, out) }
            is Map<*, *> -> {
                // 字典形式（罕见），收集所有数值
                v.values.forEach { collectNumbers(it, out) }
            }
        }
    }
}

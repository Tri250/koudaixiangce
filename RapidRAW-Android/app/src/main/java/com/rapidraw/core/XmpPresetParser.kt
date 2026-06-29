package com.rapidraw.core

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.io.InputStream

/**
 * Adobe Lightroom XMP 预设解析器
 *
 * 解析 Lightroom 导出的 .xmp 预设文件，提取 crs: 命名空间下的
 * 调整参数，包括曝光、对比度、HSL、色调曲线、色彩分级等。
 *
 * XMP 文件结构示例：
 * <x:xmpmeta>
 *   <rdf:RDF>
 *     <rdf:Description
 *       crs:Exposure2012="0.5"
 *       crs:Contrast2012="10"
 *       crs:HueAdjustmentRed="5"
 *       ... />
 *   </rdf:RDF>
 * </x:xmpmeta>
 */
class XmpPresetParser {

    /**
     * 解析后的 XMP 预设数据
     */
    data class XmpPreset(
        val name: String = "",
        val version: String = "",

        // 基本调整
        val exposure: Float? = null,
        val contrast: Float? = null,
        val highlights: Float? = null,
        val shadows: Float? = null,
        val whites: Float? = null,
        val blacks: Float? = null,
        val temperature: Float? = null,
        val tint: Float? = null,
        val saturation: Float? = null,
        val vibrance: Float? = null,
        val clarity: Float? = null,
        val sharpness: Float? = null,
        val lumaNoiseReduction: Float? = null,
        val colorNoiseReduction: Float? = null,

        // 色调曲线 (Point Curve)
        val toneCurvePV: List<Pair<Float, Float>> = emptyList(),

        // 参数曲线
        val toneCurveParametricShadows: Float? = null,
        val toneCurveParametricDarks: Float? = null,
        val toneCurveParametricLights: Float? = null,
        val toneCurveParametricHighlights: Float? = null,
        val toneCurveParametricHighlightSplit: Float? = null,

        // HSL - 每通道 3 个值 (Hue, Saturation, Luminance)
        val hueAdjustmentRed: Float? = null,
        val hueAdjustmentOrange: Float? = null,
        val hueAdjustmentYellow: Float? = null,
        val hueAdjustmentGreen: Float? = null,
        val hueAdjustmentAqua: Float? = null,
        val hueAdjustmentBlue: Float? = null,
        val hueAdjustmentPurple: Float? = null,
        val hueAdjustmentMagenta: Float? = null,

        val saturationAdjustmentRed: Float? = null,
        val saturationAdjustmentOrange: Float? = null,
        val saturationAdjustmentYellow: Float? = null,
        val saturationAdjustmentGreen: Float? = null,
        val saturationAdjustmentAqua: Float? = null,
        val saturationAdjustmentBlue: Float? = null,
        val saturationAdjustmentPurple: Float? = null,
        val saturationAdjustmentMagenta: Float? = null,

        val luminanceAdjustmentRed: Float? = null,
        val luminanceAdjustmentOrange: Float? = null,
        val luminanceAdjustmentYellow: Float? = null,
        val luminanceAdjustmentGreen: Float? = null,
        val luminanceAdjustmentAqua: Float? = null,
        val luminanceAdjustmentBlue: Float? = null,
        val luminanceAdjustmentPurple: Float? = null,
        val luminanceAdjustmentMagenta: Float? = null,

        // 色彩分级 (Split Toning)
        val splitToningShadowHue: Float? = null,
        val splitToningShadowSaturation: Float? = null,
        val splitToningHighlightHue: Float? = null,
        val splitToningHighlightSaturation: Float? = null,

        // 相机配置
        val cameraProfile: String? = null,
    )

    /**
     * 从 InputStream 解析 XMP 预设文件
     *
     * @param inputStream XMP 文件输入流
     * @return 解析后的 XmpPreset
     * @throws XmlPullParserException 如果 XML 格式无效
     * @throws IOException 如果读取失败
     */
    fun parse(inputStream: InputStream): XmpPreset {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(inputStream, "UTF-8")

        var preset = XmpPreset()
        var inRdfDescription = false
        var currentArrayName: String? = null
        var currentArrayItems = mutableListOf<Pair<Float, Float>>()
        var inSeq = false
        var inListItem = false
        var listItemName: String? = null
        var listItemValue: Float? = null

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    val tagName = parser.name
                    val prefix = parser.prefix

                    // 检测 rdf:Description 元素（主要属性载体）
                    if (prefix == "rdf" && tagName == "Description") {
                        inRdfDescription = true
                        // 提取 Description 上的所有 crs: 属性
                        preset = extractCrsAttributes(parser, preset)
                    }

                    // 检测 crs:ToneCurvePV2012 序列
                    if (prefix == "crs" && tagName == "ToneCurvePV2012") {
                        currentArrayName = "ToneCurvePV2012"
                        currentArrayItems = mutableListOf()
                    }

                    // rdf:Seq 表示数组开始
                    if (prefix == "rdf" && tagName == "Seq") {
                        inSeq = true
                    }

                    // rdf:li 表示数组项
                    if (prefix == "rdf" && tagName == "li" && inSeq) {
                        inListItem = true
                        listItemName = null
                        listItemValue = null
                    }

                    // crs:Name / crs:Version 等（可能作为子元素而非属性）
                    if (prefix == "crs" && !inSeq) {
                        when (tagName) {
                            "Name" -> {
                                // 读取文本内容
                                val text = parser.nextText()
                                if (text != null) {
                                    preset = preset.copy(name = text.trim())
                                }
                            }
                            "Version" -> {
                                val text = parser.nextText()
                                if (text != null) {
                                    preset = preset.copy(version = text.trim())
                                }
                            }
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    val tagName = parser.name
                    val prefix = parser.prefix

                    if (prefix == "rdf" && tagName == "Description") {
                        inRdfDescription = false
                    }

                    if (prefix == "rdf" && tagName == "Seq") {
                        inSeq = false
                        // 完成数组收集
                        if (currentArrayName == "ToneCurvePV2012" && currentArrayItems.isNotEmpty()) {
                            preset = preset.copy(toneCurvePV = currentArrayItems.toList())
                        }
                        currentArrayName = null
                        currentArrayItems = mutableListOf()
                    }

                    if (prefix == "rdf" && tagName == "li") {
                        inListItem = false
                        // 将 name/value 对添加到数组
                        if (listItemName != null && listItemValue != null) {
                            currentArrayItems.add(Pair(listItemName!!, listItemValue!!))
                        }
                        listItemName = null
                        listItemValue = null
                    }
                }

                XmlPullParser.TEXT -> {
                    if (inListItem && currentArrayName == "ToneCurvePV2012") {
                        // ToneCurvePV2012 的 li 格式：成对的 name/value
                        // Lightroom 格式通常是 "0,0" 或直接数值
                        val text = parser.text?.trim() ?: ""
                        if (text.contains(",")) {
                            val parts = text.split(",")
                            if (parts.size == 2) {
                                val x = parts[0].trim().toFloatOrNull()
                                val y = parts[1].trim().toFloatOrNull()
                                if (x != null && y != null) {
                                    currentArrayItems.add(Pair(x, y))
                                }
                            }
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        return preset
    }

    /**
     * 从 rdf:Description 元素提取所有 crs: 属性
     */
    private fun extractCrsAttributes(parser: XmlPullParser, preset: XmpPreset): XmpPreset {
        var result = preset
        val nsCount = parser.namespaceCount(parser.depth - 1)
        val attrCount = parser.attributeCount

        for (i in 0 until attrCount) {
            val attrName = parser.getAttributeName(i)
            val attrValue = parser.getAttributeValue(i)
            val attrPrefix = parser.getAttributePrefix(i) ?: ""

            // 仅处理 crs: 命名空间的属性
            if (attrPrefix != "crs") continue

            result = applyCrsAttribute(result, attrName, attrValue)
        }

        return result
    }

    /**
     * 将单个 crs: 属性应用到 XmpPreset
     */
    private fun applyCrsAttribute(preset: XmpPreset, name: String, value: String): XmpPreset {
        val floatValue = value.toFloatOrNull()

        return when (name) {
            // 基本调整
            "Exposure2012" -> preset.copy(exposure = floatValue)
            "Contrast2012" -> preset.copy(contrast = floatValue)
            "Highlights2012" -> preset.copy(highlights = floatValue)
            "Shadows2012" -> preset.copy(shadows = floatValue)
            "Whites2012" -> preset.copy(whites = floatValue)
            "Blacks2012" -> preset.copy(blacks = floatValue)
            "Temperature" -> preset.copy(temperature = floatValue)
            "Tint" -> preset.copy(tint = floatValue)
            "Saturation" -> preset.copy(saturation = floatValue)
            "Vibrance" -> preset.copy(vibrance = floatValue)
            "Clarity2012" -> preset.copy(clarity = floatValue)
            "Sharpness" -> preset.copy(sharpness = floatValue)
            "LuminanceSmoothing" -> preset.copy(lumaNoiseReduction = floatValue)
            "ColorNoiseReduction" -> preset.copy(colorNoiseReduction = floatValue)

            // 参数色调曲线
            "ToneCurveShadows2012" -> preset.copy(toneCurveParametricShadows = floatValue)
            "ToneCurveDarkRegions2012" -> preset.copy(toneCurveParametricDarks = floatValue)
            "ToneCurveLightRegions2012" -> preset.copy(toneCurveParametricLights = floatValue)
            "ToneCurveHighlightRegions2012" -> preset.copy(toneCurveParametricHighlights = floatValue)
            "ToneCurveSplit2012" -> preset.copy(toneCurveParametricHighlightSplit = floatValue)

            // HSL - Hue
            "HueAdjustmentRed" -> preset.copy(hueAdjustmentRed = floatValue)
            "HueAdjustmentOrange" -> preset.copy(hueAdjustmentOrange = floatValue)
            "HueAdjustmentYellow" -> preset.copy(hueAdjustmentYellow = floatValue)
            "HueAdjustmentGreen" -> preset.copy(hueAdjustmentGreen = floatValue)
            "HueAdjustmentAqua" -> preset.copy(hueAdjustmentAqua = floatValue)
            "HueAdjustmentBlue" -> preset.copy(hueAdjustmentBlue = floatValue)
            "HueAdjustmentPurple" -> preset.copy(hueAdjustmentPurple = floatValue)
            "HueAdjustmentMagenta" -> preset.copy(hueAdjustmentMagenta = floatValue)

            // HSL - Saturation
            "SaturationAdjustmentRed" -> preset.copy(saturationAdjustmentRed = floatValue)
            "SaturationAdjustmentOrange" -> preset.copy(saturationAdjustmentOrange = floatValue)
            "SaturationAdjustmentYellow" -> preset.copy(saturationAdjustmentYellow = floatValue)
            "SaturationAdjustmentGreen" -> preset.copy(saturationAdjustmentGreen = floatValue)
            "SaturationAdjustmentAqua" -> preset.copy(saturationAdjustmentAqua = floatValue)
            "SaturationAdjustmentBlue" -> preset.copy(saturationAdjustmentBlue = floatValue)
            "SaturationAdjustmentPurple" -> preset.copy(saturationAdjustmentPurple = floatValue)
            "SaturationAdjustmentMagenta" -> preset.copy(saturationAdjustmentMagenta = floatValue)

            // HSL - Luminance
            "LuminanceAdjustmentRed" -> preset.copy(luminanceAdjustmentRed = floatValue)
            "LuminanceAdjustmentOrange" -> preset.copy(luminanceAdjustmentOrange = floatValue)
            "LuminanceAdjustmentYellow" -> preset.copy(luminanceAdjustmentYellow = floatValue)
            "LuminanceAdjustmentGreen" -> preset.copy(luminanceAdjustmentGreen = floatValue)
            "LuminanceAdjustmentAqua" -> preset.copy(luminanceAdjustmentAqua = floatValue)
            "LuminanceAdjustmentBlue" -> preset.copy(luminanceAdjustmentBlue = floatValue)
            "LuminanceAdjustmentPurple" -> preset.copy(luminanceAdjustmentPurple = floatValue)
            "LuminanceAdjustmentMagenta" -> preset.copy(luminanceAdjustmentMagenta = floatValue)

            // 色彩分级 (Split Toning)
            "SplitToningShadowHue" -> preset.copy(splitToningShadowHue = floatValue)
            "SplitToningShadowSaturation" -> preset.copy(splitToningShadowSaturation = floatValue)
            "SplitToningHighlightHue" -> preset.copy(splitToningHighlightHue = floatValue)
            "SplitToningHighlightSaturation" -> preset.copy(splitToningHighlightSaturation = floatValue)

            // 相机配置
            "CameraProfile" -> preset.copy(cameraProfile = value)

            // 名称和版本（可能在属性中）
            "Name" -> preset.copy(name = value)
            "Version" -> preset.copy(version = value)

            else -> preset
        }
    }
}

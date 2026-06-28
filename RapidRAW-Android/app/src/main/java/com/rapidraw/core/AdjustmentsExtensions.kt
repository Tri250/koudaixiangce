package com.rapidraw.core

import com.rapidraw.core.ColorScience
import com.rapidraw.core.HdrExporter
import com.rapidraw.data.model.Adjustments

/**
 * Adjustments ↔ ColorScience.Config 互转扩展
 *
 * 避免 ColorScience.Config 直接嵌入 Adjustments 引起的循环依赖
 */
fun Adjustments.toColorScienceConfig(): ColorScience.Config = ColorScience.Config(
    mode = ColorScience.Mode.entries.getOrElse(colorScienceMode) { ColorScience.Mode.AGX },
    displaySpace = ColorScience.DisplayColorSpace.entries
        .getOrElse(displayColorSpace) { ColorScience.DisplayColorSpace.SRGB },
    eotf = ColorScience.Eotf.entries.getOrElse(eotf) { ColorScience.Eotf.SDR },
    peakLuminanceNits = peakLuminanceNits,
    contrast = agxContrast,
    pedestal = agxPedestal,
)

fun Adjustments.copyWithColorScience(config: ColorScience.Config): Adjustments = copy(
    colorScienceMode = config.mode.ordinal,
    displayColorSpace = config.displaySpace.ordinal,
    eotf = config.eotf.ordinal,
    peakLuminanceNits = config.peakLuminanceNits,
    agxContrast = config.contrast,
    agxPedestal = config.pedestal,
)

/**
 * Adjustments → HdrExporter.HdrConfig
 */
fun Adjustments.toHdrConfig(): HdrExporter.HdrConfig = HdrExporter.HdrConfig(
    format = HdrExporter.HdrFormat.entries.getOrElse(hdrExportFormat) { HdrExporter.HdrFormat.SDR_JPEG },
    peakLuminanceNits = hdrPeakLuminance,
    maxBoostStop = hdrMaxBoostStop,
    colorSpace = ColorScience.DisplayColorSpace.entries
        .getOrElse(displayColorSpace) { ColorScience.DisplayColorSpace.REC_2020 },
)

fun Adjustments.copyWithHdrConfig(config: HdrExporter.HdrConfig): Adjustments = copy(
    hdrExportFormat = config.format.ordinal,
    hdrPeakLuminance = config.peakLuminanceNits,
    hdrMaxBoostStop = config.maxBoostStop,
    displayColorSpace = config.colorSpace.ordinal,
)

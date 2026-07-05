package com.pixelfruit.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.*

/**
 * PixelFruit RAW 解析引擎 — P-01
 * 支持 ARW/CR2/RAF/RW2/ORF 等品牌 RAW 的 12-16bit 解析。
 * 委托 RapidRAW-Android 的 RawDecoder 完成实际解码。
 *
 * 已知不足：
 * - P-02: 尼康 Z8/Z9 NEF → 无法解析（上游 LibRaw 不支持）
 * - P-03: iPhone ProRAW / Pixel DNG → 大部分无法打开（上游 DNG 支持有限）
 */
class PixelFruitRawDecoder(private val rawDecoder: Any? = null) {

    /** P-02: 硬编码不支持型号列表，与 RapidRAW 保持一致 */
    val unsupportedNefModels = setOf(
        "Z 8", "Z 8 ", "Z 9", "Z 9 ",
        "Z8", "Z9", "NIKON Z 8", "NIKON Z 9",
    )

    /** 支持的 RAW 格式扩展名 */
    val supportedExtensions = setOf(
        "arw", "cr2", "cr3", "raf", "rw2", "orf", "pef", "srw",
        "nef", "dng", "sr2", "rwl", "raw", "mrw", "erf", "dcr",
    )

    /** P-01: 检测 RAW 格式是否支持 */
    fun isFormatSupported(extension: String): Boolean {
        return supportedExtensions.contains(extension.lowercase())
    }

    /** P-01: 检查是否为不支持的尼康机型 */
    fun isUnsupportedNef(model: String): Boolean {
        return unsupportedNefModels.any { model.contains(it, ignoreCase = true) }
    }
}
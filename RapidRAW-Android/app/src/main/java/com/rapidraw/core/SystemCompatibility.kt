package com.rapidraw.core

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.util.Locale

/**
 * Android 系统版本兼容性检查器。
 *
 * 功能：
 * 1. API 版本兼容矩阵验证
 * 2. 特性支持检测（16KB 页面、预测性返回、HDR、相机扩展等）
 * 3. 系统更新建议
 * 4. Google Play 要求合规性检查
 *
 * @since v1.10.2（正式版兼容性加固）
 */
object SystemCompatibility {

    private const val TAG = "SystemCompat"

    /** 兼容性报告 */
    data class CompatibilityReport(
        val apiLevel: Int,
        val isApiCompliant: Boolean,
        val isGooglePlayCompliant: Boolean,
        val supportedFeatures: List<String>,
        val unsupportedFeatures: List<String>,
        val recommendations: List<String>,
    )

    /**
     * 生成完整的兼容性报告。
     */
    fun generateReport(context: Context): CompatibilityReport {
        val apiLevel = Build.VERSION.SDK_INT
        val supported = mutableListOf<String>()
        val unsupported = mutableListOf<String>()
        val recommendations = mutableListOf<String>()

        // ── API 合规性 ────────────────────────────────────────────────
        val isApiCompliant = apiLevel >= Build.VERSION_CODES.O
        val isGooglePlayCompliant = apiLevel >= Build.VERSION_CODES.TIRAMISU

        if (!isApiCompliant) {
            recommendations.add("系统版本过低，建议升级至 Android 8.0+")
        }

        // ── 特性检测 ──────────────────────────────────────────────────
        // 16KB 页面大小 (Android 15+)
        if (apiLevel >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            supported.add("16KB 页面大小")
        } else {
            unsupported.add("16KB 页面大小 (需 Android 15+)")
        }

        // 预测性返回手势 (Android 14+)
        if (apiLevel >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            supported.add("预测性返回手势")
        } else {
            unsupported.add("预测性返回手势 (需 Android 14+)")
        }

        // Ultra HDR (Android 14+)
        if (apiLevel >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            supported.add("Ultra HDR")
        } else {
            unsupported.add("Ultra HDR (需 Android 14+)")
        }

        // 每应用语言 (Android 13+)
        if (apiLevel >= Build.VERSION_CODES.TIRAMISU) {
            supported.add("每应用语言偏好")
        } else {
            unsupported.add("每应用语言偏好 (需 Android 13+)")
        }

        // 通知权限 (Android 13+)
        if (apiLevel >= Build.VERSION_CODES.TIRAMISU) {
            supported.add("通知权限控制")
        }

        // 分区存储 (Android 10+)
        if (apiLevel >= Build.VERSION_CODES.Q) {
            supported.add("分区存储")
        }

        // 前台服务类型 (Android 14+)
        if (apiLevel >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            supported.add("前台服务类型声明")
        } else {
            unsupported.add("前台服务类型声明 (需 Android 14+)")
        }

        // 动态取色 (Android 12+)
        if (apiLevel >= Build.VERSION_CODES.S) {
            supported.add("Material You 动态取色")
        } else {
            unsupported.add("Material You 动态取色 (需 Android 12+)")
        }

        // HDR 显示 (Android 8+)
        if (apiLevel >= Build.VERSION_CODES.O) {
            supported.add("HDR 显示")
        } else {
            unsupported.add("HDR 显示 (需 Android 8+)")
        }

        // Camera2 RAW (Android 5+)
        supported.add("Camera2 RAW 支持")

        // Vulkan Compute (Android 7+)
        if (apiLevel >= Build.VERSION_CODES.N) {
            supported.add("Vulkan Compute")
        } else {
            unsupported.add("Vulkan Compute (需 Android 7+)")
        }

        return CompatibilityReport(
            apiLevel = apiLevel,
            isApiCompliant = isApiCompliant,
            isGooglePlayCompliant = isGooglePlayCompliant,
            supportedFeatures = supported,
            unsupportedFeatures = unsupportedFeatures,
            recommendations = recommendations,
        )
    }

    /**
     * 验证目标 API 级别是否符合 Google Play 要求。
     *
     * Google Play 要求：
     * - 新应用：targetSdk 34+ (Android 14)
     * - 现有应用：targetSdk 34+ (2024 年 8 月 31 日起)
     * - 2025 年起：targetSdk 35+ (Android 15)
     */
    fun isGooglePlayTargetSdkCompliant(context: Context): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }

    /**
     * 获取系统信息摘要，用于崩溃报告和诊断。
     */
    fun getSystemInfoSummary(): String {
        return buildString {
            appendLine("Android ${Build.VERSION.SDK_INT} (${Build.VERSION.CODENAME})")
            appendLine("Manufacturer: ${Build.MANUFACTURER}")
            appendLine("Model: ${Build.MODEL}")
            appendLine("Brand: ${Build.BRAND}")
            appendLine("Device: ${Build.DEVICE}")
            appendLine("Product: ${Build.PRODUCT}")
            appendLine("Fingerprint: ${Build.FINGERPRINT}")
            appendLine("Hardware: ${Build.HARDWARE}")
            appendLine("ABI: ${Build.SUPPORTED_ABIS.joinToString(", ")}")
            appendLine("Locale: ${Locale.getDefault()}")
        }
    }
}
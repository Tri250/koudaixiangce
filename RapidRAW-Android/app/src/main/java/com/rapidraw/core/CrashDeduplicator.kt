package com.rapidraw.core

import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃去重与聚合分析器。
 *
 * 对本地崩溃日志进行指纹提取、分组、去重和统计，帮助快速定位高频崩溃。
 *
 * 特性：
 * 1. 指纹提取：从堆栈中提取"异常类名 + 关键帧"作为崩溃指纹
 * 2. 自动分组：相同指纹的崩溃归为一组
 * 3. 统计排序：按发生次数降序排列
 * 4. 趋势分析：对比最近两个时间窗口的崩溃频率变化
 *
 * @since v1.7.0（正式版崩溃防护增强）
 */
object CrashDeduplicator {

    private const val TAG = "CrashDeduplicator"
    private const val MAX_FINGERPRINT_FRAMES = 3

    /** 崩溃分组结果 */
    data class CrashGroup(
        val fingerprint: String,
        val exceptionClass: String,
        val exceptionMessage: String,
        val topFrame: String,
        val count: Int,
        val firstSeen: Long,
        val lastSeen: Long,
        val sampleLogFile: String?,
    )

    /** 趋势分析结果 */
    data class TrendResult(
        val recentCount: Int,
        val previousCount: Int,
        val changePercent: Float, // 正数=增长，负数=下降
        val isEscalating: Boolean, // 是否在恶化
    )

    /**
     * 从崩溃堆栈提取指纹。
     * 指纹格式：异常类名 + 前 N 个调用帧特征
     */
    fun extractFingerprint(throwable: Throwable): String {
        return extractFingerprint(
            throwable.javaClass.name,
            throwable.message ?: "",
            throwable.stackTrace,
        )
    }

    fun extractFingerprint(
        exceptionClass: String,
        exceptionMessage: String,
        stackTrace: Array<StackTraceElement>,
    ): String {
        val topFrames = stackTrace
            .take(MAX_FINGERPRINT_FRAMES)
            .joinToString("|") { "${it.className}.${it.methodName}:${it.lineNumber}" }
        return "${exceptionClass}|${topFrames}"
    }

    fun extractFingerprintFromStackTrace(
        exceptionClass: String,
        exceptionMessage: String,
        stackTrace: String,
    ): String {
        // 从文本堆栈中提取前几帧
        val lines = stackTrace.lines()
        val topFrames = lines
            .filter { it.trimStart().startsWith("at ") }
            .take(MAX_FINGERPRINT_FRAMES)
            .joinToString("|") { it.trim() }
        return "${exceptionClass}|${topFrames}"
    }

    /**
     * 对崩溃日志目录中的文件进行分组分析。
     *
     * @param crashLogDir 崩溃日志目录
     * @return 按发生次数降序排列的崩溃分组列表
     */
    fun analyze(crashLogDir: File): List<CrashGroup> {
        if (!crashLogDir.exists() || !crashLogDir.isDirectory) return emptyList()

        val files = crashLogDir.listFiles { f -> f.name.endsWith(".log") }
            ?.sortedByDescending { it.lastModified() } ?: return emptyList()

        val groups = mutableMapOf<String, MutableList<File>>()

        for (file in files) {
            val content = runCatching { file.readText() }.getOrNull() ?: continue
            val fingerprint = extractFingerprintFromFile(content)
            groups.getOrPut(fingerprint) { mutableListOf() }.add(file)
        }

        return groups.map { (fingerprint, groupFiles) ->
            val sampleContent = runCatching { groupFiles.first().readText() }.getOrDefault("")
            val (exceptionClass, exceptionMessage, topFrame) = parseFromFingerprint(fingerprint, sampleContent)
            CrashGroup(
                fingerprint = fingerprint,
                exceptionClass = exceptionClass,
                exceptionMessage = exceptionMessage,
                topFrame = topFrame,
                count = groupFiles.size,
                firstSeen = groupFiles.minOf { it.lastModified() },
                lastSeen = groupFiles.maxOf { it.lastModified() },
                sampleLogFile = groupFiles.first().name,
            )
        }.sortedByDescending { it.count }
    }

    /**
     * 趋势分析：对比最近 24 小时与之前 24 小时的崩溃频率。
     */
    fun trendAnalysis(crashLogDir: File): TrendResult {
        val now = System.currentTimeMillis()
        val recent24h = now - 24 * 60 * 60 * 1000
        val previous48h = now - 48 * 60 * 60 * 1000

        val files = crashLogDir.listFiles { f -> f.name.endsWith(".log") }
            ?: return TrendResult(0, 0, 0f, false)

        val recentCount = files.count { it.lastModified() >= recent24h }
        val previousCount = files.count {
            it.lastModified() in previous48h until recent24h
        }

        val changePercent = if (previousCount > 0) {
            ((recentCount - previousCount).toFloat() / previousCount) * 100f
        } else if (recentCount > 0) {
            100f
        } else {
            0f
        }

        return TrendResult(
            recentCount = recentCount,
            previousCount = previousCount,
            changePercent = changePercent,
            isEscalating = changePercent > 20f && recentCount > 0,
        )
    }

    /**
     * 生成崩溃摘要报告（用于"反馈/上传"功能中附带的诊断信息）。
     */
    fun generateSummary(crashLogDir: File): String {
        val groups = analyze(crashLogDir)
        val trend = trendAnalysis(crashLogDir)
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

        return buildString {
            appendLine("=== RapidRAW Crash Summary ===")
            appendLine("Generated: $ts")
            appendLine("Total crash groups: ${groups.size}")
            appendLine("Total crashes: ${groups.sumOf { it.count }}")
            appendLine()
            appendLine("--- Trend ---")
            appendLine("Recent 24h: ${trend.recentCount}")
            appendLine("Previous 24h: ${trend.previousCount}")
            appendLine("Change: ${"%.1f".format(trend.changePercent)}%")
            appendLine("Escalating: ${trend.isEscalating}")
            appendLine()
            appendLine("--- Top Crash Groups ---")
            groups.take(10).forEachIndexed { index, group ->
                appendLine("${index + 1}. [${group.count}x] ${group.exceptionClass}")
                appendLine("   Message: ${group.exceptionMessage}")
                appendLine("   Top Frame: ${group.topFrame}")
                appendLine("   Last seen: ${Date(group.lastSeen)}")
                if (group.sampleLogFile != null) {
                    appendLine("   Sample: ${group.sampleLogFile}")
                }
                appendLine()
            }
        }
    }

    // ── 内部实现 ──────────────────────────────────────────────────────

    private fun extractFingerprintFromFile(content: String): String {
        val lines = content.lines()
        val exceptionClass = lines
            .firstOrNull { it.contains(":") && !it.startsWith("at ") && !it.startsWith("\t") }
            ?.substringBefore(":")?.trim() ?: "Unknown"
        val topFrames = lines
            .filter { it.trimStart().startsWith("at ") }
            .take(MAX_FINGERPRINT_FRAMES)
            .joinToString("|") { it.trim() }
        return "${exceptionClass}|${topFrames}"
    }

    private fun parseFromFingerprint(fingerprint: String, sampleContent: String): Triple<String, String, String> {
        val parts = fingerprint.split("|", limit = 2)
        val exceptionClass = parts.getOrElse(0) { "Unknown" }
        val topFrame = parts.getOrElse(1) { "" }
        val exceptionMessage = sampleContent.lines()
            .firstOrNull { it.contains(exceptionClass) && it.contains(":") }
            ?.substringAfter(":")?.trim() ?: ""
        return Triple(exceptionClass, exceptionMessage, topFrame)
    }
}
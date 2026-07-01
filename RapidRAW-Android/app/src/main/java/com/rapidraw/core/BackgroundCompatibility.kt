package com.rapidraw.core

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

/**
 * 后台执行兼容性管理器。
 *
 * 处理 Android 各版本的后台执行限制，确保长时间任务（导出、同步、AI 处理）
 * 在不同 OEM 和 Android 版本上稳定运行。
 *
 * 功能：
 * 1. 电池优化白名单请求（防止导出被 Doze 杀死）
 * 2. 前台服务类型声明验证（Android 14+）
 * 3. App Standby Buckets 感知
 * 4. 后台启动限制处理
 * 5. OEM 特定限制解决（MIUI 自启动、ColorOS 后台冻结）
 *
 * @since v1.10.2（正式版兼容性加固）
 */
object BackgroundCompatibility {

    private const val TAG = "BackgroundCompat"

    // ── 电池优化 ──────────────────────────────────────────────────────

    /**
     * 检查当前是否已加入电池优化白名单。
     */
    fun isBatteryOptimizationExempt(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return false
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * 请求电池优化豁免。
     *
     * 触发系统设置页面，引导用户将应用加入白名单。
     * 适用于长时间导出/同步场景。
     */
    fun requestBatteryOptimizationExemption(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        if (isBatteryOptimizationExempt(activity)) return
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${activity.packageName}")
            }
            activity.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to request battery optimization exemption", e)
        }
    }

    // ── 前台服务类型 ──────────────────────────────────────────────────

    /**
     * 验证 Android 14+ 前台服务类型声明。
     *
     * Android 14+ 要求所有前台服务必须声明 serviceType。
     * 返回未声明的服务类型列表。
     */
    fun validateForegroundServiceTypes(context: Context): List<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return emptyList()
        val required = listOf("mediaProcessing", "dataSync")
        val declared = runCatching {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SERVICES
            )
        }.getOrNull()?.services?.flatMap { service ->
            service.metaData?.keySet()?.toList() ?: emptyList()
        } ?: emptyList()
        return required.filter { type -> declared.none { it.contains(type) } }
    }

    // ── App Standby Buckets ───────────────────────────────────────────

    /**
     * 获取当前 App Standby Bucket。
     *
     * Android 9+ 引入 App Standby Buckets，系统根据使用频率
     * 限制应用的后台资源：
     * - STANDBY_BUCKET_ACTIVE (10): 正在使用，无限制
     * - STANDBY_BUCKET_WORKING_SET (20): 经常使用，轻度限制
     * - STANDBY_BUCKET_FREQUENT (30): 偶尔使用，中度限制
     * - STANDBY_BUCKET_RARE (40): 很少使用，重度限制
     * - STANDBY_BUCKET_RESTRICTED (45): 未使用，极度限制
     */
    fun getAppStandbyBucket(context: Context): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return 0
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE)
            as? android.app.usage.UsageStatsManager ?: return 0
        return usageStatsManager.appStandbyBucket
    }

    /**
     * 检查当前是否处于重度限制状态。
     * 如果是，应推迟非关键后台任务。
     */
    fun isHeavilyRestricted(context: Context): Boolean {
        val bucket = getAppStandbyBucket(context)
        return bucket >= android.app.usage.UsageStatsManager.STANDBY_BUCKET_RARE
    }

    /**
     * 获取 App Standby Bucket 的人类可读名称。
     */
    fun getStandbyBucketName(context: Context): String {
        return when (getAppStandbyBucket(context)) {
            android.app.usage.UsageStatsManager.STANDBY_BUCKET_ACTIVE -> "ACTIVE"
            android.app.usage.UsageStatsManager.STANDBY_BUCKET_WORKING_SET -> "WORKING_SET"
            android.app.usage.UsageStatsManager.STANDBY_BUCKET_FREQUENT -> "FREQUENT"
            android.app.usage.UsageStatsManager.STANDBY_BUCKET_RARE -> "RARE"
            android.app.usage.UsageStatsManager.STANDBY_BUCKET_RESTRICTED -> "RESTRICTED"
            else -> "UNKNOWN"
        }
    }

    // ── 后台启动限制 ──────────────────────────────────────────────────

    /**
     * 检查是否可以从后台启动 Activity。
     *
     * Android 10+ 限制了后台启动 Activity。对于导出完成通知等场景，
     * 应使用 Notification 而非直接启动 Activity。
     */
    fun canStartBackgroundActivity(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
    }
}
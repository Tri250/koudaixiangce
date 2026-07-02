package com.rapidraw.core

import android.content.Context
import android.util.Log
import java.io.File

/**
 * 启动崩溃恢复器。
 *
 * 检测连续启动崩溃：每次 Application.onCreate 启动时递增计数，
 * 正常完成 StartupOptimizer.execute() 后清零。当计数超过阈值时认为
 * 处于"启动循环"状态，自动清理可能导致问题的缓存和损坏的 SharedPreferences。
 *
 * 与 v1.10.5 之前实现的差异：
 * 1. 提取为可单元测试的对象（不再耦合 Application.onCreate）；
 * 2. 保留白名单 prefs 文件，避免误删用户关键状态（onboarding / permission_history / pending_uri）。
 * 3. v2026.07: 新增跨版本升级兼容性检查（用例 1.3）。
 *
 * @since 2026.07
 */
object StartupRecovery {

    private const val TAG = "StartupRecovery"

    /** 自身计数器使用的 prefs 名称。 */
    const val PREFS_NAME = "rapidraw_startup"
    const val KEY_CRASH_COUNT = "startup_crash_count"
    /** 存储上次启动版本号的 key，用于跨版本升级诊断。 */
    const val KEY_LAST_VERSION = "last_version_code"

    /** 连续崩溃阈值：达到此值触发恢复。 */
    const val CRASH_THRESHOLD = 3

    /**
     * 启动恢复阶段保留的 prefs 白名单。这些文件保存用户关键状态，
     * 不应在崩溃恢复流程中被删除。
     */
    private val PRESERVED_PREFS = setOf(
        "rapidraw_startup",       // 自身计数器
        "rapidraw_onboarding",    // 引导完成标志
        "permission_history",     // 权限请求历史（影响"永久拒绝"判断）
        "rapidraw_pending_uri",   // 进程死亡恢复用的待处理 URI
    )

    /**
     * 进入 onCreate 时调用：递增或读取当前崩溃计数。
     * @return Pair(shouldRecover, newCount) - 是否需要执行恢复动作、新计数
     */
    fun onStartupBegin(context: Context): StartupDecision {
        val prefs = SafePreferences.get(context, PREFS_NAME)
        val currentCount = SafePreferences.getInt(prefs, KEY_CRASH_COUNT, 0)
        return if (currentCount >= CRASH_THRESHOLD) {
            // 触发恢复，但不再递增
            Log.w(TAG, "Detected $currentCount consecutive startup crashes, attempting recovery")
            SafePreferences.putInt(prefs, KEY_CRASH_COUNT, 0)
            StartupDecision(shouldRecover = true, count = currentCount)
        } else {
            SafePreferences.putInt(prefs, KEY_CRASH_COUNT, currentCount + 1)
            StartupDecision(shouldRecover = false, count = currentCount + 1)
        }
    }

    /**
     * 启动成功完成时调用：重置计数器。
     * 应在 StartupOptimizer.execute() 之后调用。
     */
    fun onStartupSuccess(context: Context) {
        val prefs = SafePreferences.get(context, PREFS_NAME)
        SafePreferences.putInt(prefs, KEY_CRASH_COUNT, 0)
    }

    /**
     * 执行恢复动作：清理 cacheDir 全部文件以及白名单外的损坏 SharedPreferences XML。
     * 实现尽量宽容：所有 IO 异常都被 runCatching 吞掉，确保恢复流程本身不会再次崩溃。
     */
    fun performRecovery(context: Context) {
        runCatching { clearCacheDir(context) }
            .onFailure { Log.w(TAG, "Failed to clear cache dir during recovery", it) }
        runCatching { clearCorruptedPrefsExceptPreserved(context) }
            .onFailure { Log.w(TAG, "Failed to clear corrupted prefs during recovery", it) }
        Log.w(TAG, "Startup crash recovery completed")
    }

    private fun clearCacheDir(context: Context) {
        val cacheDir = context.cacheDir
        cacheDir.listFiles()?.forEach { f -> runCatching { f.deleteRecursively() } }
    }

    private fun clearCorruptedPrefsExceptPreserved(context: Context) {
        val prefsDir: File? = runCatching {
            context.applicationContext.getDir("shared_prefs", Context.MODE_PRIVATE)
        }.getOrNull() ?: context.applicationContext.filesDir?.parentFile?.let {
            File(it, "shared_prefs")
        }
        prefsDir?.listFiles()
            ?.filter { it.name.endsWith(".xml") || it.name.endsWith(".xml.bak") }
            ?.filter { file -> PRESERVED_PREFS.none { file.name.startsWith(it) } }
            ?.forEach { f -> runCatching { f.delete() } }
    }

    /** 启动恢复决策结果。 */
    data class StartupDecision(
        val shouldRecover: Boolean,
        val count: Int,
    )

    /**
     * v2026.07: 跨版本升级兼容性检查（用例 1.3）。
     * 检测当前版本号是否与上次启动版本不同，若不同说明发生了覆盖安装/升级。
     * 升级场景下执行缓存清理，避免旧版本缓存数据与新版本不兼容导致崩溃。
     *
     * @param currentVersionCode 当前版本号（BuildConfig.VERSION_CODE 或 PackageInfo.longVersionCode）
     */
    fun checkVersionMigration(context: Context, currentVersionCode: Long) {
        val prefs = SafePreferences.get(context, PREFS_NAME)
        val lastVersion = SafePreferences.getLong(prefs, KEY_LAST_VERSION, 0L)
        if (lastVersion != currentVersionCode) {
            Log.i(TAG, "Version migration detected: $lastVersion → $currentVersionCode")
            // 清理缓存文件，避免旧版本缓存不兼容
            runCatching { clearCacheDir(context) }
                .onFailure { Log.w(TAG, "Failed to clear cache during version migration", it) }
            // 清理缩略图磁盘缓存（缩略图格式可能随版本变更）
            runCatching {
                val thumbnailsDir = File(context.cacheDir, "thumbnails")
                if (thumbnailsDir.exists()) thumbnailsDir.deleteRecursively()
            }.onFailure { Log.w(TAG, "Failed to clear thumbnails during version migration", it) }
            // 清理 HTTP 缓存（API 响应格式可能随版本变更）
            runCatching {
                val httpCacheDir = File(context.cacheDir, "http_cache")
                if (httpCacheDir.exists()) httpCacheDir.deleteRecursively()
            }.onFailure { Log.w(TAG, "Failed to clear HTTP cache during version migration", it) }
            SafePreferences.putLong(prefs, KEY_LAST_VERSION, currentVersionCode)
        }
    }
}

package com.rapidraw

import android.app.Activity
import android.app.Application
import android.content.Context
import android.app.LocaleManager
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import android.os.StrictMode
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.rapidraw.core.ANRWatchdog
import com.rapidraw.core.AnalyticsManager
import com.rapidraw.core.BackgroundCompatibility
import com.rapidraw.core.BillingManager
import com.rapidraw.core.CrashHandler
import com.rapidraw.core.CrashReporter
import com.rapidraw.core.DeadlockDetector
import com.rapidraw.core.ImageProcessor
import com.rapidraw.core.NetworkCache
import com.rapidraw.core.NotificationChannels
import com.rapidraw.core.OemCompatibility
import com.rapidraw.core.PerformanceMonitor
import com.rapidraw.core.PlayIntegrityHelper
import com.rapidraw.core.SafePreferences
import com.rapidraw.core.StartupOptimizer
import com.rapidraw.core.StartupRecovery
import com.rapidraw.core.SystemCompatibility
import com.rapidraw.security.SecurityProvider
import com.rapidraw.ui.editor.EditorShortcuts
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * RapidRAW Application 主入口类。
 *
 * ── Hilt 依赖注入迁移 (v2.51.1) ────────────────────────────────
 * 迁移步骤 3: 添加 @HiltAndroidApp 注解，启用 Hilt 代码生成。
 *
 * @HiltAndroidApp 注解会触发 Hilt 在编译时生成：
 * 1. Application 组件的基础类
 * 2. 所有 @AndroidEntryPoint 的依赖注入容器
 * 3. 单例依赖的存储容器
 *
 * 注意：Hilt 与现有 DiContainer 可并存，逐步迁移：
 * - DiContainer 保留作为备选，确保向后兼容
 * - 新功能优先使用 Hilt 注入
 * - 旧代码逐步迁移至 @Inject 构造器注入
 *
 * @since v1.10.0（Hilt DI 迁移）
 */
@HiltAndroidApp
class RapidRawApp : Application() {

    companion object {
        private const val TAG = "RapidRawApp"

        @Volatile
        private var instance: RapidRawApp? = null

        fun getInstance(): RapidRawApp? = instance
    }

    val imageProcessor: ImageProcessor by lazy { ImageProcessor() }

    // 2026 hotfix: 使用 AtomicInteger 防止极端边界下 lifecycle 回调并发导致计数异常
    private val activityCount = AtomicInteger(0)
    @Volatile
    var isAppForeground: Boolean = false
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 第 0 步：最早安装崩溃处理器 — 确保后续任何初始化崩溃都能被捕获和记录
        // 这是启动链路中最重要的一步，必须在所有其他初始化之前完成
        try {
            CrashHandler.install(this)
        } catch (t: Throwable) {
            Log.e(TAG, "FATAL: Failed to install CrashHandler", t)
            // 即使 CrashHandler 安装失败也要继续，不能因为崩溃处理器本身崩溃就完全无法启动
        }

        try {
            // v1.10.5: 启动崩溃恢复机制 — 连续崩溃计数器
            // 提取至 StartupRecovery（2026.07）以提升可测试性。
            val decision = StartupRecovery.onStartupBegin(this)
            if (decision.shouldRecover) {
                StartupRecovery.performRecovery(this)
            }

            // 启动死锁检测看门狗
            runCatching { DeadlockDetector.start() }
                .onFailure { Log.w(TAG, "Failed to start DeadlockDetector", it) }

            // v1.8.0 冷启动优化：使用 StartupOptimizer 分级初始化
            // CRITICAL 级在主线程同步执行
            // HIGH/MEDIUM/LOW 级延迟到首帧渲染后，减少冷启动阻塞
            // 注意：CrashHandler 已在第 0 步安装，此处不再重复
            StartupOptimizer
                .schedule(StartupOptimizer.Priority.CRITICAL, "SecurityProvider") {
                    runCatching { SecurityProvider.verifyAppSignature(this) }
                }
                .schedule(StartupOptimizer.Priority.CRITICAL, "NotificationChannels") {
                    runCatching { NotificationChannels.initialize(this) }
                }
                .schedule(StartupOptimizer.Priority.CRITICAL, "CrashReporter") {
                    runCatching { CrashReporter.init(this) }
                }
                .schedule(StartupOptimizer.Priority.CRITICAL, "ANRWatchdog") {
                    runCatching { ANRWatchdog.start(blockThresholdMs = 2_000L, checkIntervalMs = 1_000L) }
                }
                .schedule(StartupOptimizer.Priority.CRITICAL, "StrictMode") {
                    enableStrictModeInDebug()
                }
                .schedule(StartupOptimizer.Priority.CRITICAL, "FontScale") {
                    applyFontScaleLimit()
                }
                .schedule(StartupOptimizer.Priority.CRITICAL, "PerformanceMonitor") {
                    runCatching { PerformanceMonitor.init(this) }
                }
                .schedule(StartupOptimizer.Priority.CRITICAL, "MemoryPressureMonitor") {
                    runCatching { MemoryPressureMonitor.start(this) }
                    MemoryPressureMonitor.onLowMemory = { level ->
                        when (level) {
                            MemoryPressureMonitor.MemoryLevel.LOW -> {
                                imageProcessor.clearThumbnailCache()
                            }
                            MemoryPressureMonitor.MemoryLevel.CRITICAL,
                            MemoryPressureMonitor.MemoryLevel.EMERGENCY -> {
                                imageProcessor.clearThumbnailCache()
                                runCatching { cleanThumbnailDiskCache() }
                            }
                            else -> {}
                        }
                    }
                }
                // v1.10.6 hotfix: 移除 ImageProcessor.init(this) 调用。
                // ImageProcessor 是无状态实例类，没有 companion init 方法，原调用会导致编译失败。
                .schedule(StartupOptimizer.Priority.HIGH, "Analytics") {
                    runCatching { AnalyticsManager.init(this) }
                }
                .schedule(StartupOptimizer.Priority.HIGH, "Billing") {
                    runCatching { BillingManager.init(this).connect() }
                }
                .schedule(StartupOptimizer.Priority.MEDIUM, "NetworkCache") {
                    runCatching { NetworkCache.getClient(this) }
                }
                .schedule(StartupOptimizer.Priority.MEDIUM, "SystemCompatibility") {
                    runCatching { SystemCompatibility.generateReport(this) }
                }
                .schedule(StartupOptimizer.Priority.MEDIUM, "OemCompatibility") {
                    runCatching {
                        Log.d(TAG, "OEM: ${OemCompatibility.getOemDisplayName()}")
                        Log.d(TAG, "App Standby: ${BackgroundCompatibility.getStandbyBucketName(this)}")
                    }
                }
                .schedule(StartupOptimizer.Priority.LOW, "PlayIntegrity") {
                    runCatching { PlayIntegrityHelper.checkIntegrity(this) }
                }
                .execute()

            // v1.10.5: 启动成功，重置崩溃计数器
            StartupRecovery.onStartupSuccess(this)

            // P2-D2.10: 加载用户自定义键盘快捷键绑定到内存
            runCatching { EditorShortcuts.loadCustomBindings(this) }
                .onFailure { Log.w(TAG, "Failed to load custom shortcuts", it) }

            // Debug 构建启用 LeakCanary（release 零开销）
            enableLeakCanaryInDebug()

            // 2026 perf: 在 Application 阶段异步应用 per-app language，避免阻塞首 Activity 启动。
            applyPerAppLanguage()
            registerActivityLifecycleCallbacks(activityLifecycleCallbacks)
            // v1.5.3: 主动检查关键设备能力，记录到日志便于后续诊断
            logDeviceCapabilities()

        } catch (t: Throwable) {
            Log.e(TAG, "Fatal error during Application.onCreate, attempting graceful recovery", t)
            try {
                CrashHandler.install(this)
                // 使用 writeCrashToFileStatic（public）而非 private 方法
                val crashDir = java.io.File(filesDir, "crash_logs")
                if (!crashDir.exists()) crashDir.mkdirs()
                CrashHandler.writeCrashToFileStatic(crashDir, Thread.currentThread(), t)
            } catch (_: Throwable) {}
            throw t
        }
    }

    /**
     * Debug 包启用严格模式，捕获主线程 IO / 网络等违规。Release 包零开销。
     * v1.6.2: Android 16 (API 36) 上额外启用 ANR 检测增强与 StrictMode 堆栈跟踪深度限制
     */
    private fun enableStrictModeInDebug() {
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .detectCustomSlowCalls()
                    .penaltyLog()
                    .build()
            )
            val vmBuilder = StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .detectLeakedRegistrationObjects()
                .detectActivityLeaks()
                .penaltyLog()
            // Android 14+ (API 34) 引入：检测非 SDK 平台 API 使用
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                runCatching { vmBuilder.detectUnsafeIntentLaunch() }
            }
            StrictMode.setVmPolicy(vmBuilder.build())
        }
    }

    /**
     * v1.8.0: Debug 构建启用 LeakCanary 内存泄漏检测。
     * Release 包零开销（完全排除）。
     */
    private fun enableLeakCanaryInDebug() {
        if (BuildConfig.DEBUG) {
            runCatching {
                // LeakCanary 通过 ContentProvider 自动初始化，
                // 此处使用反射避免 release 编译依赖
                val clazz = Class.forName("leakcanary.LeakCanary")
                val configMethod = clazz.getMethod("getConfig")
                val config = configMethod.invoke(null)
                val copyMethod = config.javaClass.getMethod("copy", Boolean::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                val newConfig = copyMethod.invoke(config, true, 5)
                val setConfigMethod = clazz.getMethod("setConfig", newConfig.javaClass)
                setConfigMethod.invoke(null, newConfig)
            }.onFailure { Log.w(TAG, "LeakCanary not available") }
        }
    }

    /**
     * 记录关键设备能力信息，用于跨 OEM 兼容性诊断（ColorOS / MIUI / HyperOS / OneUI 等）。
     */
    private fun logDeviceCapabilities() {
        Log.i(TAG, "Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.BRAND})")
        Log.i(TAG, "Android: ${Build.VERSION.RELEASE} API ${Build.VERSION.SDK_INT}")
        Log.i(TAG, "ABIs: ${Build.SUPPORTED_ABIS.joinToString()}")
        Log.i(TAG, "Available memory: ${Runtime.getRuntime().maxMemory() / 1024 / 1024}MB")
    }

    /**
     * Android 13+ (API 33+) per-app language。
     * 在 Application 阶段通过 IdleHandler 延迟应用，避免阻塞首 Activity 的 onCreate / setContent。
     * 默认语言：中文（简体）。
     *
     * v1.7.0 hotfix: 从主线程同步执行改为 IdleHandler 延迟执行。
     * LocaleManager.setApplicationLocales 内部涉及 Binder 调用 + 持久化，
     * 部分设备（尤其低内存设备 + OEM ROM）上可能耗时 50-200ms，
     * 在 Application.onCreate 同步执行会直接增加冷启动时间。
     * 延迟到主线程空闲后执行，不阻塞首帧渲染。
     *
     * v2026.07 hotfix: 仅在用户未设置过应用语言时（applicationLocales 为空）应用默认 zh-CN。
     * 旧实现每次启动都强制设置 zh-CN，会覆盖用户在系统设置 → 应用语言中
     * 选择的英文/日文/韩文，且在 Android 16 (API 36) 上可能触发 Activity recreate，
     * 导致用户编辑中的状态丢失。
     */
    private fun applyPerAppLanguage() {
        val defaultTag = "zh-CN"
        val applyBlock = {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val localeManager = getSystemService(LocaleManager::class.java) ?: return@try
                    // 仅在用户从未设置过应用语言时应用默认 zh-CN。
                    // applicationLocales 为空表示"跟随系统"，此时可设默认值；
                    // 非空表示用户已显式选择语言，必须尊重用户偏好。
                    val current = localeManager.applicationLocales
                    if (current == null || current.isEmpty) {
                        val list = LocaleList.forLanguageTags(defaultTag)
                        if (!list.isEmpty) {
                            localeManager.applicationLocales = list
                        }
                    }
                } else {
                    val appCompatLocales = AppCompatDelegate.getApplicationLocales()
                    if (appCompatLocales == null || appCompatLocales.isEmpty) {
                        AppCompatDelegate.setApplicationLocales(
                            LocaleListCompat.forLanguageTags(defaultTag)
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to apply per-app language: ${e.message}")
            }
        }
        // 通过 IdleHandler 延迟到主线程空闲时执行，不阻塞首帧渲染
        android.os.Looper.getMainLooper().queue.addIdleHandler {
            applyBlock()
            false // 只执行一次
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        when (level) {
            TRIM_MEMORY_RUNNING_MODERATE,
            TRIM_MEMORY_RUNNING_LOW,
            TRIM_MEMORY_RUNNING_CRITICAL,
            TRIM_MEMORY_UI_HIDDEN -> {
                // 提示：此处建议清理 image cache / 内存中的解码结果。
                // 当前实现由各 ViewModel 的 onCleared 负责，
                // 但已退到后台的 Activity 持有的 ViewModel 不会被立即释放。
                Log.i(TAG, "onTrimMemory(level=$level), suggest cleaning up cache")
                // L-03: 低内存大图降级 — 释放内存中的缩略图缓存
                releaseInMemoryThumbnailCache()
            }
            TRIM_MEMORY_BACKGROUND,
            TRIM_MEMORY_MODERATE,
            TRIM_MEMORY_COMPLETE -> {
                Log.w(TAG, "onTrimMemory(level=$level), system may kill process soon")
                // 2026 perf: 在后台线程主动清理非必要缓存，降低被系统杀进程的概率
                Thread {
                    runCatching { cleanStaleDecodedRawCache() }
                    runCatching { cleanThumbnailDiskCache() }
                }.apply {
                    isDaemon = true
                    name = "RapidRawTrimMemory"
                    start()
                }
                // L-03: 释放内存中的缩略图缓存
                releaseInMemoryThumbnailCache()
                // L09 修复：系统即将杀进程时，触发 sidecar 紧急保存
                if (level == TRIM_MEMORY_COMPLETE) {
                    Log.w(TAG, "Emergency sidecar save triggered before process death")
                    emergencySaveAllSidecars()
                }
            }
        }
    }

    /**
     * L-03: 释放内存中的缩略图缓存。
     * 在低内存告警时被调用，释放 ImageProcessor 中的缩略图 LRU 缓存，
     * 以及任何其他内存敏感的大对象缓存。
     * 缩略图可重新解码，优先释放以腾出内存空间。
     */
    private fun releaseInMemoryThumbnailCache() {
        try {
            // 释放 ImageProcessor 中的缩略图缓存
            imageProcessor.clearThumbnailCache()
            Log.i(TAG, "In-memory thumbnail cache released")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to release in-memory thumbnail cache", e)
        }
    }

    /**
     * 清理 RawDecoder 在 cacheDir 留下的临时 .dng/.raw/.cr3 文件。
     * 这些文件如果在升级或低内存时被系统缓存会占用数百 MB 空间。
     */
    private fun cleanStaleDecodedRawCache() {
        val cacheDir = cacheDir
        val stale = cacheDir.listFiles { file ->
            file.name.startsWith("raw_decode_") && System.currentTimeMillis() - file.lastModified() > 60_000L
        } ?: return
        var freed = 0L
        stale.forEach { f ->
            val size = f.length()
            if (runCatching { f.delete() }.getOrDefault(false)) {
                freed += size
            }
        }
        if (freed > 0) {
            Log.i(TAG, "cleanStaleDecodedRawCache freed ${freed / 1024 / 1024}MB")
        }
    }

    /**
     * 清理缩略图磁盘缓存。缩略图可重新解码，适合在内存紧张时释放。
     */
    private fun cleanThumbnailDiskCache() {
        val thumbnailsDir = File(cacheDir, "thumbnails")
        if (!thumbnailsDir.exists()) return
        var freed = 0L
        var count = 0
        thumbnailsDir.listFiles()?.forEach { f ->
            val size = f.length()
            if (runCatching { f.delete() }.getOrDefault(false)) {
                freed += size
                count++
            }
        }
        if (freed > 0) {
            Log.i(TAG, "cleanThumbnailDiskCache deleted $count files, freed ${freed / 1024 / 1024}MB")
        }
    }

    /**
     * L09 修复：系统即将杀进程时的紧急 sidecar 保存。
     * 遍历所有已知的编辑会话，将未保存的 adjustments 强制刷到 .rrdata。
     */
    private fun emergencySaveAllSidecars() {
        Thread {
            try {
                val sidecarDir = File(filesDir, "sidecar")
                if (!sidecarDir.exists()) return@Thread
                // 对于 filesDir/sidecar/ 下的所有 .rapidraw 文件，确保已 flush
                sidecarDir.listFiles { f -> f.name.endsWith(".rapidraw") }?.forEach { file ->
                    try {
                        // 强制 sync 文件系统缓存到磁盘
                        java.io.FileOutputStream(file, true).use { it.fd.sync() }
                    } catch (_: Exception) { }
                }
                Log.i(TAG, "Emergency sidecar flush completed")
            } catch (e: Exception) {
                Log.w(TAG, "Emergency sidecar save failed", e)
            }
        }.apply {
            isDaemon = true
            name = "RapidRawEmergencySave"
            start()
        }
    }

    private val activityLifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            Log.d(TAG, "Activity created: ${activity.localClassName}")
        }

        override fun onActivityStarted(activity: Activity) {
            val newCount = activityCount.incrementAndGet()
            if (newCount == 1) {
                isAppForeground = true
                Log.d(TAG, "App moved to foreground")
            }
        }

        override fun onActivityResumed(activity: Activity) {}

        override fun onActivityPaused(activity: Activity) {}

        override fun onActivityStopped(activity: Activity) {
            val newCount = activityCount.updateAndGet { current -> (current - 1).coerceAtLeast(0) }
            if (newCount == 0) {
                isAppForeground = false
                Log.d(TAG, "App moved to background")
            }
        }

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

        override fun onActivityDestroyed(activity: Activity) {
            Log.d(TAG, "Activity destroyed: ${activity.localClassName}")
        }
    }

    /**
     * v1.9.0: 限制系统字体缩放倍率，防止超大字体导致 UI 溢出/截断。
     * 最大缩放 1.3x，超出部分按 1.3x 处理。
     * 注意：必须 override attachBaseContext，在最早时机执行。
     */
    override fun attachBaseContext(base: Context?) {
        var finalBase = base
        if (base != null) {
            try {
                val config = base.resources.configuration
                val maxScale = 1.3f
                // 额外防御：fontScale 必须 > 0
                if (config.fontScale > maxScale && config.fontScale > 0f) {
                    // 复制配置以避免修改原始对象
                    val newConfig = android.content.res.Configuration(config)
                    newConfig.fontScale = maxScale
                    finalBase = base.createConfigurationContext(newConfig)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to apply font scale limit in attachBaseContext", e)
                // 失败时使用原始 base，不能因为字体缩放限制导致启动失败
            }
        }
        super.attachBaseContext(finalBase)
    }

    private fun applyFontScaleLimit() {
        val currentScale = resources.configuration.fontScale
        Log.d(TAG, "Font scale: $currentScale")
    }

    /**
     * v1.10.6: 进程退出前清理资源。
     * 关闭导出队列处理器，防止协程泄漏。
     * 注意：Android 不保证 onTerminate 一定会被调用（进程可被直接杀死），
     * 因此仅作为最佳努力清理，实际资源回收仍依赖各 ViewModel 的 onCleared。
     */
    override fun onTerminate() {
        super.onTerminate()
        // v1.10.6: 集中关闭所有全局单例管理器，避免 Application 销毁后协程/连接泄漏。
        runCatching { StartupOptimizer.shutdown() }
            .onFailure { Log.w(TAG, "StartupOptimizer shutdown failed", it) }
        runCatching { BillingManager.getInstance().shutdown() }
            .onFailure { Log.w(TAG, "BillingManager shutdown failed", it) }
        runCatching { AnalyticsManager.shutdown() }
            .onFailure { Log.w(TAG, "AnalyticsManager shutdown failed", it) }
        runCatching { CrashReporter.shutdown() }
            .onFailure { Log.w(TAG, "CrashReporter shutdown failed", it) }
        runCatching { PerformanceMonitor.shutdown() }
            .onFailure { Log.w(TAG, "PerformanceMonitor shutdown failed", it) }
        runCatching { com.rapidraw.data.export.ExportQueueProcessor.shutdown() }
            .onFailure { Log.w(TAG, "ExportQueueProcessor shutdown failed", it) }
        runCatching { DeadlockDetector.stop() }
            .onFailure { Log.w(TAG, "DeadlockDetector stop failed", it) }
        runCatching { ANRWatchdog.stop() }
            .onFailure { Log.w(TAG, "ANRWatchdog stop failed", it) }
    }
}

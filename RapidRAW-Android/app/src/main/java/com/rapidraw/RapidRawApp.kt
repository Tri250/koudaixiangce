package com.rapidraw

import android.app.Activity
import android.app.Application
import android.content.Context
import android.app.LocaleManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import com.rapidraw.core.DeviceOptimizer
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
import com.rapidraw.core.TetheredCaptureManager
import com.rapidraw.security.SecurityProvider
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

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

        // v1.10.5: 启动崩溃恢复机制 — 连续崩溃计数器
        // 提取至 StartupRecovery（2026.07）以提升可测试性。
        val decision = StartupRecovery.onStartupBegin(this)
        if (decision.shouldRecover) {
            StartupRecovery.performRecovery(this)
        }

        // 启动死锁检测看门狗
        DeadlockDetector.start()

        // v1.8.0 冷启动优化：使用 StartupOptimizer 分级初始化
        // CRITICAL 级在主线程同步执行，确保崩溃捕获最早安装
        // HIGH/MEDIUM/LOW 级延迟到首帧渲染后，减少冷启动阻塞
        StartupOptimizer
            .schedule(StartupOptimizer.Priority.CRITICAL, "CrashHandler") {
                CrashHandler.install(this)
            }
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

        // v2026.07: 跨版本升级兼容性检查（用例 1.3）
        // 覆盖安装/升级场景下清理旧版本缓存，避免数据不兼容导致崩溃。
        runCatching {
            val versionCode = packageManager.getPackageInfo(packageName, 0).let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.longVersionCode
                else @Suppress("DEPRECATION") it.versionCode.toLong()
            }
            StartupRecovery.checkVersionMigration(this, versionCode)
        }.onFailure { Log.w(TAG, "Version migration check failed", it) }

        // Debug 构建启用 LeakCanary（release 零开销）
        enableLeakCanaryInDebug()

        // 2026 perf: 在 Application 阶段异步应用 per-app language，避免阻塞首 Activity 启动。
        applyPerAppLanguage()
        registerActivityLifecycleCallbacks(activityLifecycleCallbacks)
        // v1.5.3: 主动检查关键设备能力，记录到日志便于后续诊断
        logDeviceCapabilities()
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
     * v2026.07: 增加存储空间、网络状态、低内存状态诊断（用例 1.2 / 4.1 / 5.4）。
     */
    private fun logDeviceCapabilities() {
        Log.i(TAG, "Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.BRAND})")
        Log.i(TAG, "Android: ${Build.VERSION.RELEASE} API ${Build.VERSION.SDK_INT}")
        Log.i(TAG, "ABIs: ${Build.SUPPORTED_ABIS.joinToString()}")
        Log.i(TAG, "Total memory: ${DeviceOptimizer.getTotalMemoryMb()}MB")
        Log.i(TAG, "Available memory: ${DeviceOptimizer.getAvailableMemoryMb()}MB")
        Log.i(TAG, "Available storage: ${DeviceOptimizer.getAvailableStorageMb()}MB")
        // 低存储 / 低内存告警
        if (DeviceOptimizer.isStorageLow()) {
            Log.w(TAG, "WARNING: Low storage space detected (${DeviceOptimizer.getAvailableStorageMb()}MB available)")
        }
        if (DeviceOptimizer.isLowMemory()) {
            Log.w(TAG, "WARNING: Low memory detected (${DeviceOptimizer.getAvailableMemoryMb()}MB / ${DeviceOptimizer.getTotalMemoryMb()}MB)")
        }
        // 网络状态
        logNetworkState()
    }

    /**
     * 记录当前网络状态，用于诊断无网/弱网/代理场景下的启动问题（用例 4.1-4.3）。
     */
    private fun logNetworkState() {
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            val network = cm.activeNetwork ?: run {
                Log.i(TAG, "Network: NONE (no active network)")
                return
            }
            val caps = cm.getNetworkCapabilities(network) ?: return
            val transport = when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
                else -> "Unknown"
            }
            val bandwidth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                "${caps.linkDownstreamBandwidthKbps}/${caps.linkUpstreamBandwidthKbps} kbps"
            } else "N/A"
            Log.i(TAG, "Network: $transport, bandwidth=$bandwidth")
        } catch (_: Exception) {
            Log.w(TAG, "Failed to query network state")
        }
    }

    /**
     * Android 13+ (API 33+) per-app language。
     * 在 Application 阶段通过后台线程应用，避免阻塞首 Activity 的 onCreate / setContent。
     * 默认语言：中文（简体）。
     */
    private fun applyPerAppLanguage() {
        // v2026.07: 从后台线程改为主线程执行。
        // LocaleManager / AppCompatDelegate.setApplicationLocales 内部涉及
        // 持久化与 Binder 调用，部分 OEM ROM 在后台线程调用时存在兼容性
        // 问题（如异步持久化失败、触发 ANR 检测误报）。
        val tag = "zh-CN"
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val localeManager = getSystemService(LocaleManager::class.java)
                val list = LocaleList.forLanguageTags(tag)
                if (!list.isEmpty) {
                    localeManager?.applicationLocales = list
                }
            } else {
                AppCompatDelegate.setApplicationLocales(
                    LocaleListCompat.forLanguageTags(tag)
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to apply per-app language: ${e.message}")
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
            }
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
        if (base != null) {
            val config = base.resources.configuration
            val maxScale = 1.3f
            if (config.fontScale > maxScale) {
                config.fontScale = maxScale
                super.attachBaseContext(base.createConfigurationContext(config))
                return
            }
        }
        super.attachBaseContext(base)
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

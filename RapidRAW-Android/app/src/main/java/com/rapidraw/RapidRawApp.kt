package com.rapidraw

import android.app.Activity
import android.app.Application
import android.app.LocaleManager
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import android.os.StrictMode
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.rapidraw.core.CrashHandler
import com.rapidraw.core.ImageProcessor
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

        try {
            // v1.5.3: 全局崩溃捕获 + 本地持久化，必须早于其他业务初始化
            // CrashHandler.install 已包含完整链路（日志写入 + 委托默认 handler），
            // 不再额外包装 setupUncaughtExceptionHandler（避免双层 handler 冗余）。
            CrashHandler.install(this)
            enableStrictModeInDebug()
            // 2026 perf: 在 Application 阶段异步应用 per-app language，避免阻塞首 Activity 启动。
            applyPerAppLanguageAsync()
            registerActivityLifecycleCallbacks(activityLifecycleCallbacks)
            // v1.5.3: 主动检查关键设备能力，记录到日志便于后续诊断
            logDeviceCapabilities()
        } catch (e: Exception) {
            Log.e(TAG, "Error in Application.onCreate", e)
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
     * 在 Application 阶段通过后台线程应用，避免阻塞首 Activity 的 onCreate / setContent。
     * 默认语言：中文（简体）。
     */
    private fun applyPerAppLanguageAsync() {
        val tag = "zh-CN"
        Thread {
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
        }.apply {
            isDaemon = true
            name = "RapidRawLocale"
            start()
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
}

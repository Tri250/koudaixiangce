package com.rapidraw

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.os.StrictMode
import android.util.Log
import com.rapidraw.core.CrashHandler
import com.rapidraw.core.ImageProcessor

class RapidRawApp : Application() {

    companion object {
        private const val TAG = "RapidRawApp"

        @Volatile
        private var instance: RapidRawApp? = null

        fun getInstance(): RapidRawApp? = instance
    }

    val imageProcessor: ImageProcessor by lazy { ImageProcessor() }

    private var activityCount = 0
    var isAppForeground: Boolean = false
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        try {
            // v1.5.3: 全局崩溃捕获 + 本地持久化，必须早于其他业务初始化
            CrashHandler.install(this)
            setupUncaughtExceptionHandler()
            enableStrictModeInDebug()
            registerActivityLifecycleCallbacks(activityLifecycleCallbacks)
            // v1.5.3: 主动检查关键设备能力，记录到日志便于后续诊断
            logDeviceCapabilities()
        } catch (e: Exception) {
            Log.e(TAG, "Error in Application.onCreate", e)
        }
    }

    /**
     * 兼容旧逻辑：保留一份最简的额外兜底（CrashHandler 已做主捕获）。
     * 这里仅记录堆栈长度 + 设备信息，确保日志格式与原 v1.5.2 兼容。
     */
    private fun setupUncaughtExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Uncaught exception on thread '${thread.name}', " +
                "type=${throwable.javaClass.name}, message=${throwable.message}")
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    /**
     * Debug 包启用严格模式，捕获主线程 IO / 网络等违规。Release 包零开销。
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
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedClosableObjects()
                    .detectLeakedRegistrationObjects()
                    .detectActivityLeaks()
                    .penaltyLog()
                    .build()
            )
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

    private val activityLifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            Log.d(TAG, "Activity created: ${activity.localClassName}")
        }

        override fun onActivityStarted(activity: Activity) {
            activityCount++
            if (activityCount == 1) {
                isAppForeground = true
                Log.d(TAG, "App moved to foreground")
            }
        }

        override fun onActivityResumed(activity: Activity) {}

        override fun onActivityPaused(activity: Activity) {}

        override fun onActivityStopped(activity: Activity) {
            activityCount--
            if (activityCount == 0) {
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

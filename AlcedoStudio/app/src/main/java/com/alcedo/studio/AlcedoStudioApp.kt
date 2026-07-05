package com.alcedo.studio

import android.app.Application
import com.alcedo.studio.core.ANRWatchdog
import com.alcedo.studio.core.CrashHandler
import com.alcedo.studio.core.L
import com.alcedo.studio.core.StartupCrashProtector
import com.alcedo.studio.core.StartupRecovery
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@HiltAndroidApp
class AlcedoStudioApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        instance = this

        StartupCrashProtector.install(this)

        if (StartupCrashProtector.getInstance(this).shouldSafeMode()) {
            L.w("AlcedoStudioApp", "Safe mode activated - skipping heavy initialization")
            return
        }

        try {
            CrashHandler.init(this)
        } catch (e: Exception) {
            android.util.Log.e("AlcedoStudioApp", "CrashHandler init failed", e)
        }

        try {
            StartupRecovery.init(this)
        } catch (e: Exception) {
            L.e("AlcedoStudioApp", "StartupRecovery init failed", e)
        }

        try {
            ANRWatchdog.start(timeoutMs = 5000L)
        } catch (e: Exception) {
            L.e("AlcedoStudioApp", "ANRWatchdog start failed", e)
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        L.w("AlcedoStudioApp", "onLowMemory called")
        com.alcedo.studio.core.BitmapManager.trimMemory(level = 80)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        L.d("AlcedoStudioApp", "onTrimMemory level=$level")
        when (level) {
            TRIM_MEMORY_RUNNING_CRITICAL,
            TRIM_MEMORY_COMPLETE -> com.alcedo.studio.core.BitmapManager.trimMemory(level = 100)
            TRIM_MEMORY_RUNNING_LOW,
            TRIM_MEMORY_MODERATE -> com.alcedo.studio.core.BitmapManager.trimMemory(level = 50)
            TRIM_MEMORY_RUNNING_MODERATE -> com.alcedo.studio.core.BitmapManager.trimMemory(level = 30)
        }
    }

    companion object {
        @Volatile
        private var instance: AlcedoStudioApp? = null

        fun get(): AlcedoStudioApp = instance
            ?: throw IllegalStateException("Application not initialized")
    }
}

package com.rapidraw

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import com.rapidraw.core.ImageProcessor
import org.acra.annotation.AcraCore
import org.acra.annotation.AcraHttpSender
import org.acra.sender.HttpSender

@AcraCore(buildConfigClass = BuildConfig::class, reportFormat = org.acra.data.StringFormat.KEY_VALUE_LIST)
@AcraHttpSender(uri = "https://your-crash-server.com/acra", httpMethod = HttpSender.Method.POST)
class RapidRawApp : Application() {

    companion object {
        private const val TAG = "RapidRawApp"

        @Volatile
        private lateinit var instance: RapidRawApp

        fun getInstance(): RapidRawApp = instance
    }

    val imageProcessor: ImageProcessor by lazy { ImageProcessor() }

    private var activityCount = 0
    var isAppForeground: Boolean = false
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        setupUncaughtExceptionHandler()
        registerActivityLifecycleCallbacks(activityLifecycleCallbacks)
    }

    private fun setupUncaughtExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Uncaught exception on thread '${thread.name}'", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
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

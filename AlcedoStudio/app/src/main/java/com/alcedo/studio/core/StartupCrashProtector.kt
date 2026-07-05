package com.alcedo.studio.core

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Process
import kotlin.system.exitProcess

class StartupCrashProtector private constructor(
    private val context: Context
) : Application.ActivityLifecycleCallbacks {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var activityCount = 0
    private var isCrashedInLastLaunch = false

    init {
        isCrashedInLastLaunch = prefs.getBoolean(KEY_CRASHED, false)
    }

    fun markLaunchStart() {
        prefs.edit()
            .putBoolean(KEY_CRASHED, true)
            .putInt(KEY_LAUNCH_COUNT, prefs.getInt(KEY_LAUNCH_COUNT, 0) + 1)
            .apply()
    }

    fun markLaunchSuccess() {
        prefs.edit()
            .putBoolean(KEY_CRASHED, false)
            .putInt(KEY_CRASH_COUNT, 0)
            .apply()
    }

    fun shouldSafeMode(): Boolean {
        val crashCount = prefs.getInt(KEY_CRASH_COUNT, 0)
        return isCrashedInLastLaunch && crashCount >= MAX_CRASH_THRESHOLD
    }

    fun recordCrash() {
        val crashCount = prefs.getInt(KEY_CRASH_COUNT, 0) + 1
        prefs.edit()
            .putBoolean(KEY_CRASHED, true)
            .putInt(KEY_CRASH_COUNT, crashCount)
            .apply()

        if (crashCount >= MAX_CRASH_THRESHOLD) {
            enterSafeMode()
        }
    }

    private fun enterSafeMode() {
        L.e(TAG, "Entering safe mode due to repeated startup crashes")
    }

    fun performEmergencyRestart() {
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            am?.let {
                val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            L.e(TAG, "Emergency restart failed", e)
        }
        Process.killProcess(Process.myPid())
        exitProcess(1)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        activityCount++
    }

    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityResumed(activity: Activity) {
        markLaunchSuccess()
    }
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {
        activityCount--
    }

    companion object {
        private const val TAG = "StartupCrashProtector"
        private const val PREFS_NAME = "startup_crash_protector"
        private const val KEY_CRASHED = "crashed_in_last_launch"
        private const val KEY_CRASH_COUNT = "consecutive_crash_count"
        private const val KEY_LAUNCH_COUNT = "launch_count"
        private const val MAX_CRASH_THRESHOLD = 3

        @Volatile
        private var instance: StartupCrashProtector? = null

        fun getInstance(context: Context): StartupCrashProtector {
            return instance ?: synchronized(this) {
                instance ?: StartupCrashProtector(context.applicationContext).also {
                    instance = it
                }
            }
        }

        fun install(application: Application) {
            val protector = getInstance(application)
            application.registerActivityLifecycleCallbacks(protector)
            protector.markLaunchStart()
        }

        fun handleUncaughtException(context: Context, thread: Thread, throwable: Throwable) {
            L.e(TAG, "Uncaught exception in thread ${thread.name}", throwable)
            getInstance(context).recordCrash()
        }
    }
}

package com.alcedo.studio.core

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Looper
import android.os.Process
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

object CrashHandler {

    private const val TAG = "CrashHandler"
    private const val CRASH_LOG_DIR = "crashes"
    private const val MAX_CRASH_LOGS = 10

    private var defaultHandler: Thread.UncaughtExceptionHandler? = null
    private lateinit var context: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun init(ctx: Context) {
        context = ctx.applicationContext
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            handleException(thread, throwable)
        }
    }

    private fun handleException(thread: Thread, throwable: Throwable) {
        L.e(TAG, "Uncaught exception in thread ${thread.name}", throwable)

        StartupCrashProtector.handleUncaughtException(context, thread, throwable)

        val crashInfo = buildCrashInfo(thread, throwable)
        saveCrashLog(crashInfo)

        if (Looper.myLooper() == Looper.getMainLooper()) {
            scope.launch {
                try {
                    Toast.makeText(context, "应用发生错误，即将重启", Toast.LENGTH_SHORT).show()
                    delay(1500)
                } catch (_: Exception) {
                }
                restartApp()
            }
        } else {
            restartApp()
        }

        defaultHandler?.uncaughtException(thread, throwable)
        Process.killProcess(Process.myPid())
        exitProcess(1)
    }

    private fun buildCrashInfo(thread: Thread, throwable: Throwable): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw)

        pw.println("=".repeat(60))
        pw.println("CRASH REPORT")
        pw.println("=".repeat(60))
        pw.println("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
        pw.println("Thread: ${thread.name} (ID: ${thread.id})")
        pw.println("Process: ${Process.myPid()}")
        pw.println("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        pw.println("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        pw.println("App Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        pw.println("-".repeat(60))
        throwable.printStackTrace(pw)
        pw.println("=".repeat(60))

        return sw.toString()
    }

    private fun saveCrashLog(crashInfo: String) {
        try {
            val dir = File(context.filesDir, CRASH_LOG_DIR)
            if (!dir.exists()) dir.mkdirs()

            val files = dir.listFiles()?.sortedBy { it.lastModified() } ?: emptyList()
            if (files.size >= MAX_CRASH_LOGS) {
                files.take(files.size - MAX_CRASH_LOGS + 1).forEach { it.delete() }
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(dir, "crash_$timestamp.txt")
            file.writeText(crashInfo)
            L.i(TAG, "Crash log saved to ${file.absolutePath}")
        } catch (e: Exception) {
            L.e(TAG, "Failed to save crash log", e)
        }
    }

    private fun restartApp() {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            L.e(TAG, "Failed to restart app", e)
        }
    }
}

package com.rapidraw.core

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 崩溃日志上传器 — 将本地 crash_logs 目录中的日志上传到服务器。
 * 遵循隐私政策：仅上传崩溃堆栈和设备信息，不上传用户照片/位置等隐私数据。
 *
 * 隐私合规要求：
 * - 上传前必须检查用户同意状态 [isCrashReportConsentGiven]
 * - 用户可在设置页面随时撤回同意
 * - 未获同意时仅本地存储日志，不上传
 */
class CrashLogUploader(private val context: Context) {

    companion object {
        private const val TAG = "CrashLogUploader"
        // 上传地址 — 替换为实际服务器
        private const val UPLOAD_ENDPOINT = "https://api.rapidraw.app/v1/crash/report"
        private const val MAX_RETRIES = 2
        private const val PREFS_NAME = "crash_uploader"
        private const val KEY_INSTALL_ID = "install_id"
        private const val KEY_CONSENT_GIVEN = "crash_report_consent"
    }

    data class UploadResult(
        val uploaded: Int,
        val failed: Int,
        val deleted: Int,
    )

    /**
     * 检查用户是否已同意上传崩溃日志。
     * 默认为 false，需在设置页面或首次启动隐私弹窗中显式同意。
     */
    fun isCrashReportConsentGiven(): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_CONSENT_GIVEN, false)
    }

    /**
     * 设置用户对崩溃日志上传的同意状态。
     * 应在设置页面或隐私弹窗中调用。
     */
    fun setCrashReportConsent(consent: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_CONSENT_GIVEN, consent).apply()
        Log.i(TAG, "Crash report consent updated: $consent")
    }

    /**
     * 上传所有待上传的崩溃日志。
     * 前置条件：用户已同意上传 [isCrashReportConsentGiven]。
     * 上传成功后删除本地文件。
     * 仅在 Wi-Fi 且用户同意时上传（需调用方检查）。
     *
     * @throws IllegalStateException 如果用户未同意上传
     */
    suspend fun uploadPendingLogs(wifiOnly: Boolean = true): UploadResult = withContext(Dispatchers.IO) {
        if (!isCrashReportConsentGiven()) {
            Log.w(TAG, "Cannot upload crash logs: user consent not given")
            return@withContext UploadResult(0, 0, 0)
        }

        val crashDir = File(context.filesDir, "crash_logs")
        if (!crashDir.exists()) return@withContext UploadResult(0, 0, 0)

        val logs = crashDir.listFiles()?.filter { it.extension == "log" } ?: return@withContext UploadResult(0, 0, 0)
        var uploaded = 0
        var failed = 0
        var deleted = 0

        for (logFile in logs) {
            var success = false
            repeat(MAX_RETRIES) { attempt ->
                try {
                    val content = logFile.readText()
                    val connection = URL(UPLOAD_ENDPOINT).openConnection() as HttpURLConnection
                    connection.requestMethod = "POST"
                    connection.setRequestProperty("Content-Type", "text/plain")
                    connection.setRequestProperty("X-App-Version", getAppVersion())
                    connection.setRequestProperty("X-Device-Id", getDeviceId())
                    connection.doOutput = true
                    connection.connectTimeout = 10000
                    connection.readTimeout = 15000

                    connection.outputStream.use { os ->
                        os.write(content.toByteArray(Charsets.UTF_8))
                    }

                    val responseCode = connection.responseCode
                    connection.disconnect()

                    if (responseCode in 200..299) {
                        success = true
                        return@repeat
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Upload attempt ${attempt + 1} failed: ${e.message}")
                }
            }

            if (success) {
                logFile.delete()
                uploaded++
                deleted++
            } else {
                failed++
                // 超过 7 天的日志即使上传失败也删除
                if (System.currentTimeMillis() - logFile.lastModified() > 7 * 24 * 3600 * 1000L) {
                    logFile.delete()
                    deleted++
                }
            }
        }

        UploadResult(uploaded, failed, deleted)
    }

    private fun getAppVersion(): String {
        return runCatching {
            val pi = context.packageManager.getPackageInfo(context.packageName, 0)
            val versionCode = androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(pi)
            "${pi.versionName}($versionCode)"
        }.getOrDefault("unknown")
    }

    private fun getDeviceId(): String {
        // 不使用 IMEI/Android ID 等隐私标识，使用随机安装 ID
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_INSTALL_ID, null) ?: run {
            val id = java.util.UUID.randomUUID().toString().replace("-", "").take(16)
            prefs.edit().putString(KEY_INSTALL_ID, id).apply()
            id
        }
    }
}

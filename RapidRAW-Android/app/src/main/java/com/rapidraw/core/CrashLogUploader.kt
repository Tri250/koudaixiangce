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
 */
class CrashLogUploader(private val context: Context) {

    companion object {
        private const val TAG = "CrashLogUploader"
        // 上传地址 — 替换为实际服务器
        private const val UPLOAD_ENDPOINT = "https://api.rapidraw.app/v1/crash/report"
        private const val MAX_RETRIES = 2
    }

    data class UploadResult(
        val uploaded: Int,
        val failed: Int,
        val deleted: Int,
    )

    /**
     * 上传所有待上传的崩溃日志。
     * 上传成功后删除本地文件。
     * 仅在 Wi-Fi 且用户同意时上传（需调用方检查）。
     */
    suspend fun uploadPendingLogs(wifiOnly: Boolean = true): UploadResult = withContext(Dispatchers.IO) {
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
            "${pi.versionName}(${pi.longVersionCode})"
        }.getOrDefault("unknown")
    }

    private fun getDeviceId(): String {
        // 不使用 IMEI/Android ID 等隐私标识，使用随机安装 ID
        val prefs = context.getSharedPreferences("crash_uploader", Context.MODE_PRIVATE)
        return prefs.getString("install_id", null) ?: run {
            val id = java.util.UUID.randomUUID().toString().replace("-", "").take(16)
            prefs.edit().putString("install_id", id).apply()
            id
        }
    }
}

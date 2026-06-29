package com.rapidraw.core

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Uploads crash logs to a configurable endpoint.
 *
 * Collects device information, strips PII from log content, and uploads
 * via HTTP POST using HttpURLConnection.
 */
object CrashLogUploader {

    private const val TAG = "CrashLogUploader"
    private const val CRASH_LOGS_DIR = "crash_logs"
    private const val CONNECTION_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000

    /**
     * Uploads a crash log file to the specified server endpoint.
     *
     * @param logFilePath Absolute path to the crash log file
     * @param serverUrl   URL of the crash log collection endpoint
     * @return true if upload was successful
     */
    suspend fun uploadCrashLog(logFilePath: String, serverUrl: String): Boolean =
        withContext(Dispatchers.IO) {
            val logFile = File(logFilePath)
            if (!logFile.exists() || !logFile.canRead()) {
                Log.e(TAG, "Crash log file not found or not readable: $logFilePath")
                return@withContext false
            }

            return@withContext try {
                val sanitizedContent = sanitizeLogContent(logFile.readText())
                val deviceInfo = collectDeviceInfo(null)

                // Build multipart/form-data or JSON payload
                val jsonPayload = buildJsonPayload(sanitizedContent, deviceInfo, logFile)

                val url = URL(serverUrl)
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    doInput = true
                    connectTimeout = CONNECTION_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "RapidRAW-CrashReporter/1.0")
                }

                connection.connect()

                OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(jsonPayload)
                    writer.flush()
                }

                val responseCode = connection.responseCode
                val success = responseCode in 200..299

                if (!success) {
                    Log.e(TAG, "Upload failed with HTTP $responseCode")
                    // Read error stream for debugging
                    try {
                        val errorBody = connection.errorStream?.let {
                            BufferedReader(InputStreamReader(it)).readText()
                        }
                        Log.e(TAG, "Error body: $errorBody")
                    } catch (_: Exception) {
                    }
                } else {
                    Log.i(TAG, "Crash log uploaded successfully")
                    // Delete the log file after successful upload
                    logFile.delete()
                }

                connection.disconnect()
                success

            } catch (e: Exception) {
                Log.e(TAG, "Error uploading crash log: ${e.message}")
                false
            }
        }

    /**
     * Collects device information for diagnostic purposes.
     *
     * @param context Optional context for app version info
     * @return Map of device information keys to values
     */
    fun collectDeviceInfo(context: Context? = null): Map<String, String> {
        val info = mutableMapOf<String, String>()

        info["device_model"] = Build.MODEL
        info["device_manufacturer"] = Build.MANUFACTURER
        info["device_brand"] = Build.BRAND
        info["device_product"] = Build.PRODUCT
        info["android_version"] = Build.VERSION.RELEASE
        info["android_sdk"] = Build.VERSION.SDK_INT.toString()
        info["build_fingerprint"] = Build.FINGERPRINT
        info["cpu_abi"] = Build.SUPPORTED_ABIS.joinToString(", ")
        info["hardware"] = Build.HARDWARE

        // Memory info
        try {
            val runtime = Runtime.getRuntime()
            info["available_memory_mb"] = ((runtime.maxMemory() - runtime.totalMemory() + runtime.freeMemory()) / (1024 * 1024)).toString()
            info["total_memory_mb"] = (runtime.maxMemory() / (1024 * 1024)).toString()
        } catch (_: Exception) {
            info["available_memory_mb"] = "unknown"
            info["total_memory_mb"] = "unknown"
        }

        // App version
        if (context != null) {
            try {
                val packageInfo = context.packageManager.getPackageInfo(
                    context.packageName, 0
                )
                info["app_version"] = packageInfo.versionName ?: "unknown"
                info["app_version_code"] = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode.toString()
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode.toString()
                }
            } catch (e: PackageManager.NameNotFoundException) {
                info["app_version"] = "unknown"
                info["app_version_code"] = "unknown"
            }
        }

        // Crash timestamp
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
        info["crash_timestamp"] = dateFormat.format(Date())

        return info
    }

    /**
     * Returns a list of pending crash log files that have not been uploaded yet.
     *
     * @param context Application context for accessing app files directory
     * @return List of crash log files
     */
    fun getPendingCrashLogs(context: Context): List<File> {
        val crashDir = File(context.filesDir, CRASH_LOGS_DIR)
        if (!crashDir.exists() || !crashDir.isDirectory) {
            return emptyList()
        }

        return crashDir.listFiles { file ->
            file.isFile && file.name.startsWith("crash_") && file.name.endsWith(".log")
        }?.toList() ?: emptyList()
    }

    /**
     * Saves a crash log to the app's crash logs directory.
     *
     * @param context Application context
     * @param content Crash log content
     * @return The saved file, or null if saving failed
     */
    fun saveCrashLog(context: Context, content: String): File? {
        return try {
            val crashDir = File(context.filesDir, CRASH_LOGS_DIR)
            crashDir.mkdirs()

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
            val fileName = "crash_$timestamp.log"
            val file = File(crashDir, fileName)
            file.writeText(content)
            Log.i(TAG, "Crash log saved: ${file.absolutePath}")
            file
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save crash log: ${e.message}")
            null
        }
    }

    // ── Private helpers ─────────────────────────────────────────────────

    private fun buildJsonPayload(
        logContent: String,
        deviceInfo: Map<String, String>,
        logFile: File
    ): String {
        val sb = StringBuilder()
        sb.append("{")
        sb.append("\"log_content\":${jsonEscape(logContent)},")
        sb.append("\"log_file_name\":${jsonEscape(logFile.name)},")
        sb.append("\"log_file_size\":${logFile.length()},")
        sb.append("\"device_info\":{")
        var first = true
        for ((key, value) in deviceInfo) {
            if (!first) sb.append(",")
            sb.append("${jsonEscape(key)}:${jsonEscape(value)}")
            first = false
        }
        sb.append("}")
        sb.append("}")
        return sb.toString()
    }

    private fun jsonEscape(value: String): String {
        val escaped = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        return "\"$escaped\""
    }

    /**
     * Strips potentially identifiable information from log content before upload.
     *
     * Removes: email addresses, IP addresses, MAC addresses, IMEI-like numbers,
     * file paths with usernames, and other PII patterns.
     */
    private fun sanitizeLogContent(content: String): String {
        var sanitized = content

        // Email addresses
        sanitized = sanitized.replace(
            Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"),
            "[EMAIL_REDACTED]"
        )

        // IPv4 addresses
        sanitized = sanitized.replace(
            Regex("\\b(?:[0-9]{1,3}\\.){3}[0-9]{1,3}\\b"),
            "[IP_REDACTED]"
        )

        // IPv6 addresses (simplified)
        sanitized = sanitized.replace(
            Regex("([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}"),
            "[IPV6_REDACTED]"
        )

        // MAC addresses
        sanitized = sanitized.replace(
            Regex("([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})"),
            "[MAC_REDACTED]"
        )

        // IMEI-like numbers (15 digits)
        sanitized = sanitized.replace(
            Regex("\\b\\d{15}\\b"),
            "[IMEI_REDACTED]"
        )

        // Phone numbers (common formats)
        sanitized = sanitized.replace(
            Regex("\\+?\\d{1,3}[-.\\s]?\\(?\\d{2,4}\\)?[-.\\s]?\\d{2,4}[-.\\s]?\\d{2,4}"),
            "[PHONE_REDACTED]"
        )

        // File paths containing /home/ or /Users/ (Unix/Mac user paths)
        sanitized = sanitized.replace(
            Regex("(/home/[^/\\s]+/[^\\s]*)"),
            "[FILEPATH_REDACTED]"
        )
        sanitized = sanitized.replace(
            Regex("(/Users/[^/\\s]+/[^\\s]*)"),
            "[FILEPATH_REDACTED]"
        )

        // Android-specific: /data/data/<package>/... paths with user-specific data
        // Keep package name but redact user-specific subdirectories
        sanitized = sanitized.replace(
            Regex("/data/data/[^/\\s]+/files/[^\\s]*"),
            "[APP_DATA_PATH_REDACTED]"
        )

        return sanitized
    }
}
package com.rapidraw.core

import android.content.Context
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 崩溃离线存储。
 *
 * 无网络时暂存崩溃记录到本地，待网络恢复后由 [CrashReporter] 批量上报。
 * 使用文件系统而非数据库，降低依赖，避免 Room 初始化失败影响崩溃记录。
 */
internal object CrashStorage {

    private const val TAG = "CrashStorage"
    private const val STORAGE_DIR = "crash_reports"
    private const val MAX_REPORTS = 100

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class StoredEntry(
        val id: String,
        val timestamp: Long,
        val type: String,
        val threadName: String,
        val exceptionClass: String,
        val exceptionMessage: String,
        val stackTrace: String,
        val manufacturer: String,
        val model: String,
        val brand: String,
        val androidVersion: String,
        val sdkInt: Int,
        val abis: String,
        val availableMemoryMb: Long,
        val appVersion: String,
        val appVersionCode: Long,
        val tagsJson: String,
    )

    fun toStoredEntry(entry: CrashReporter.CrashEntry): StoredEntry = StoredEntry(
        id = entry.id,
        timestamp = entry.timestamp,
        type = entry.type.name,
        threadName = entry.threadName,
        exceptionClass = entry.exceptionClass,
        exceptionMessage = entry.exceptionMessage,
        stackTrace = entry.stackTrace,
        manufacturer = entry.deviceInfo.manufacturer,
        model = entry.deviceInfo.model,
        brand = entry.deviceInfo.brand,
        androidVersion = entry.deviceInfo.androidVersion,
        sdkInt = entry.deviceInfo.sdkInt,
        abis = entry.deviceInfo.abis,
        availableMemoryMb = entry.deviceInfo.availableMemoryMb,
        appVersion = entry.appVersion,
        appVersionCode = entry.appVersionCode,
        tagsJson = Json.encodeToString(entry.tags),
    )

    fun toCrashEntry(stored: StoredEntry): CrashReporter.CrashEntry = CrashReporter.CrashEntry(
        id = stored.id,
        timestamp = stored.timestamp,
        type = runCatching { CrashReporter.CrashType.valueOf(stored.type) }.getOrDefault(CrashReporter.CrashType.UNKNOWN),
        threadName = stored.threadName,
        exceptionClass = stored.exceptionClass,
        exceptionMessage = stored.exceptionMessage,
        stackTrace = stored.stackTrace,
        deviceInfo = CrashReporter.DeviceInfo(
            manufacturer = stored.manufacturer,
            model = stored.model,
            brand = stored.brand,
            androidVersion = stored.androidVersion,
            sdkInt = stored.sdkInt,
            abis = stored.abis,
            availableMemoryMb = stored.availableMemoryMb,
        ),
        appVersion = stored.appVersion,
        appVersionCode = stored.appVersionCode,
        tags = runCatching { Json.decodeFromString<Map<String, String>>(stored.tagsJson) }.getOrDefault(emptyMap()),
    )

    fun append(context: Context, entry: CrashReporter.CrashEntry) {
        val dir = storageDir(context)
        if (!dir.exists()) dir.mkdirs()
        // 清理超量
        runCatching {
            val files = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
            if (files.size >= MAX_REPORTS) {
                files.drop(MAX_REPORTS - 1).forEach { runCatching { it.delete() } }
            }
        }
        val file = File(dir, "${entry.id}.json")
        val stored = toStoredEntry(entry)
        runCatching {
            file.writeText(json.encodeToString(stored))
        }.onFailure { Log.e(TAG, "Failed to write crash report", it) }
    }

    fun remove(context: Context, reportId: String) {
        val file = File(storageDir(context), "${reportId}.json")
        runCatching { file.delete() }
    }

    fun readAll(context: Context): List<CrashReporter.CrashEntry> {
        val dir = storageDir(context)
        if (!dir.exists()) return emptyList()
        val files = dir.listFiles { f -> f.name.endsWith(".json") } ?: return emptyList()
        return files.mapNotNull { file ->
            runCatching {
                val stored = json.decodeFromString<StoredEntry>(file.readText())
                toCrashEntry(stored)
            }.getOrNull()
        }.sortedByDescending { it.timestamp }
    }

    fun pendingCount(context: Context): Int {
        val dir = storageDir(context)
        if (!dir.exists()) return 0
        return dir.listFiles { f -> f.name.endsWith(".json") }?.size ?: 0
    }

    fun clearAll(context: Context) {
        val dir = storageDir(context)
        runCatching { dir.deleteRecursively() }
    }

    private fun storageDir(context: Context): File {
        return File(context.filesDir, STORAGE_DIR)
    }
}
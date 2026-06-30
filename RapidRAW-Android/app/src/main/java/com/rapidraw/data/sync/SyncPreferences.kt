package com.rapidraw.data.sync

import android.content.Context
import android.content.SharedPreferences

/**
 * 同步偏好设置 - 持久化同步状态和配置
 */
class SyncPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("rapidraw_sync", Context.MODE_PRIVATE)

    var lastSyncTime: Long
        get() = prefs.getLong("last_sync_time", 0L)
        set(value) = prefs.edit().putLong("last_sync_time", value).apply()

    var syncEnabled: Boolean
        get() = prefs.getBoolean("sync_enabled", false)
        set(value) = prefs.edit().putBoolean("sync_enabled", value).apply()

    var syncOnWifiOnly: Boolean
        get() = prefs.getBoolean("sync_wifi_only", true)
        set(value) = prefs.edit().putBoolean("sync_wifi_only", value).apply()

    var syncProviderName: String
        get() = prefs.getString("sync_provider", "") ?: ""
        set(value) = prefs.edit().putString("sync_provider", value).apply()

    var authToken: String
        get() = prefs.getString("auth_token", "") ?: ""
        set(value) = prefs.edit().putString("auth_token", value).apply()

    fun getSyncedProjectIds(): Set<String> =
        prefs.getStringSet("synced_project_ids", emptySet()) ?: emptySet()

    fun markProjectSynced(projectId: String) {
        val current = getSyncedProjectIds().toMutableSet()
        current.add(projectId)
        prefs.edit().putStringSet("synced_project_ids", current).apply()
    }

    fun removeProjectSync(projectId: String) {
        val current = getSyncedProjectIds().toMutableSet()
        current.remove(projectId)
        prefs.edit().putStringSet("synced_project_ids", current).apply()
    }
}

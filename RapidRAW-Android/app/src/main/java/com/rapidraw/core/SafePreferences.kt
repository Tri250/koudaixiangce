package com.rapidraw.core

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * SharedPreferences 安全包装器。
 *
 * 解决 SharedPreferences XML 损坏时导致应用崩溃的问题：
 * 1. 所有读取操作包裹 try-catch，损坏时返回默认值
 * 2. 写入操作同样包裹 try-catch，防止写入异常导致崩溃
 * 3. 检测到损坏时自动清除并重建 SharedPreferences 文件
 *
 * @since v1.10.5（稳定性增强）
 */
object SafePreferences {

    private const val TAG = "SafePreferences"

    /**
     * 安全获取 SharedPreferences 实例。
     * 如果检测到 XML 损坏，自动清除并重建。
     */
    fun get(context: Context, name: String, mode: Int = Context.MODE_PRIVATE): SharedPreferences {
        val prefs = context.getSharedPreferences(name, mode)
        // 验证可读性：尝试读取一个不存在的 key，如果抛异常说明 XML 损坏
        try {
            prefs.contains("__safe_prefs_probe__")
        } catch (e: Exception) {
            Log.w(TAG, "SharedPreferences corrupted for '$name', attempting recovery", e)
            try {
                // 删除损坏的 XML 文件
                val prefsDir = context.applicationContext.getDir("shared_prefs", Context.MODE_PRIVATE)
                    ?: context.applicationContext.filesDir?.parentFile?.let {
                        java.io.File(it, "shared_prefs")
                    }
                val xmlFile = prefsDir?.let { java.io.File(it, "$name.xml") }
                if (xmlFile?.exists() == true) {
                    xmlFile.delete()
                }
                val bakFile = prefsDir?.let { java.io.File(it, "$name.xml.bak") }
                if (bakFile?.exists() == true) {
                    bakFile.delete()
                }
            } catch (e2: Exception) {
                Log.w(TAG, "Failed to clean corrupted SharedPreferences file", e2)
            }
            // 返回全新的实例
            return context.getSharedPreferences(name, mode)
        }
        return prefs
    }

    /** 安全读取 String */
    fun getString(prefs: SharedPreferences, key: String, defaultValue: String? = null): String? {
        return try {
            prefs.getString(key, defaultValue)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read String preference '$key'", e)
            defaultValue
        }
    }

    /** 安全读取 StringSet */
    fun getStringSet(prefs: SharedPreferences, key: String, defaultValue: Set<String>? = null): Set<String>? {
        return try {
            prefs.getStringSet(key, defaultValue)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read StringSet preference '$key'", e)
            defaultValue
        }
    }

    /** 安全读取 Int */
    fun getInt(prefs: SharedPreferences, key: String, defaultValue: Int = 0): Int {
        return try {
            prefs.getInt(key, defaultValue)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read Int preference '$key'", e)
            defaultValue
        }
    }

    /** 安全读取 Long */
    fun getLong(prefs: SharedPreferences, key: String, defaultValue: Long = 0L): Long {
        return try {
            prefs.getLong(key, defaultValue)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read Long preference '$key'", e)
            defaultValue
        }
    }

    /** 安全读取 Float */
    fun getFloat(prefs: SharedPreferences, key: String, defaultValue: Float = 0f): Float {
        return try {
            prefs.getFloat(key, defaultValue)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read Float preference '$key'", e)
            defaultValue
        }
    }

    /** 安全读取 Boolean */
    fun getBoolean(prefs: SharedPreferences, key: String, defaultValue: Boolean = false): Boolean {
        return try {
            prefs.getBoolean(key, defaultValue)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read Boolean preference '$key'", e)
            defaultValue
        }
    }

    /** 安全写入 String */
    fun putString(prefs: SharedPreferences, key: String, value: String?) {
        try {
            prefs.edit().putString(key, value).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write String preference '$key'", e)
        }
    }

    /** 安全写入 StringSet */
    fun putStringSet(prefs: SharedPreferences, key: String, value: Set<String>?) {
        try {
            prefs.edit().putStringSet(key, value).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write StringSet preference '$key'", e)
        }
    }

    /** 安全写入 Int */
    fun putInt(prefs: SharedPreferences, key: String, value: Int) {
        try {
            prefs.edit().putInt(key, value).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write Int preference '$key'", e)
        }
    }

    /** 安全写入 Long */
    fun putLong(prefs: SharedPreferences, key: String, value: Long) {
        try {
            prefs.edit().putLong(key, value).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write Long preference '$key'", e)
        }
    }

    /** 安全写入 Float */
    fun putFloat(prefs: SharedPreferences, key: String, value: Float) {
        try {
            prefs.edit().putFloat(key, value).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write Float preference '$key'", e)
        }
    }

    /** 安全写入 Boolean */
    fun putBoolean(prefs: SharedPreferences, key: String, value: Boolean) {
        try {
            prefs.edit().putBoolean(key, value).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write Boolean preference '$key'", e)
        }
    }

    /** 安全移除 */
    fun remove(prefs: SharedPreferences, key: String) {
        try {
            prefs.edit().remove(key).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to remove preference '$key'", e)
        }
    }

    /** 安全清除所有 */
    fun clear(prefs: SharedPreferences) {
        try {
            prefs.edit().clear().apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear preferences", e)
        }
    }
}
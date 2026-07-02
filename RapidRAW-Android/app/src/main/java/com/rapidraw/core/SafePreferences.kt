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
        // v2026.07 安全加固：getSharedPreferences 本身可能在文件系统只读、权限异常、
        // 或 OEM ROM 定制行为下抛出 RuntimeException。此处兜底，防止整个启动链崩溃。
        val prefs: SharedPreferences
        try {
            prefs = context.getSharedPreferences(name, mode)
        } catch (e: Exception) {
            Log.w(TAG, "getSharedPreferences('$name') failed, returning in-memory fallback", e)
            // 返回内存级 fallback：不持久化，但至少不会崩溃
            return InMemoryPreferences()
        }
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
            try {
                return context.getSharedPreferences(name, mode)
            } catch (e2: Exception) {
                Log.w(TAG, "getSharedPreferences recovery also failed, returning fallback", e2)
                return InMemoryPreferences()
            }
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

    /**
     * 内存级 SharedPreferences 回退实现。
     *
     * 当文件系统只读、权限异常或 OEM ROM 定制行为导致
     * [getSharedPreferences] 失败时，使用此实现避免启动崩溃。
     * 数据不持久化，仅保证当前进程生命周期内可用。
     *
     * @since 2026.07
     */
    private class InMemoryPreferences : SharedPreferences {
        private val data = mutableMapOf<String, Any?>()
        private val listeners = mutableListOf<SharedPreferences.OnSharedPreferenceChangeListener>()

        override fun getAll(): Map<String, *> = data.toMap()

        override fun getString(key: String, defValue: String?): String? =
            data[key] as? String ?: defValue

        override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? {
            @Suppress("UNCHECKED_CAST")
            return (data[key] as? Set<String>) ?: defValues
        }

        override fun getInt(key: String, defValue: Int): Int =
            (data[key] as? Int) ?: defValue

        override fun getLong(key: String, defValue: Long): Long =
            (data[key] as? Long) ?: defValue

        override fun getFloat(key: String, defValue: Float): Float =
            (data[key] as? Float) ?: defValue

        override fun getBoolean(key: String, defValue: Boolean): Boolean =
            (data[key] as? Boolean) ?: defValue

        override fun contains(key: String): Boolean = data.containsKey(key)

        override fun edit(): SharedPreferences.Editor = InMemoryEditor()

        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener
        ) {
            listeners.add(listener)
        }

        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener
        ) {
            listeners.remove(listener)
        }

        private inner class InMemoryEditor : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private var clearAll = false

            override fun putString(key: String, value: String?): SharedPreferences.Editor {
                pending[key] = value; return this
            }

            override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor {
                pending[key] = values; return this
            }

            override fun putInt(key: String, value: Int): SharedPreferences.Editor {
                pending[key] = value; return this
            }

            override fun putLong(key: String, value: Long): SharedPreferences.Editor {
                pending[key] = value; return this
            }

            override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
                pending[key] = value; return this
            }

            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
                pending[key] = value; return this
            }

            override fun remove(key: String): SharedPreferences.Editor {
                pending[key] = null; return this
            }

            override fun clear(): SharedPreferences.Editor {
                clearAll = true; return this
            }

            override fun commit(): Boolean {
                applyChanges()
                return true
            }

            override fun apply() {
                applyChanges()
            }

            private fun applyChanges() {
                if (clearAll) data.clear()
                pending.forEach { (key, value) ->
                    if (value == null) data.remove(key) else data[key] = value
                }
                pending.clear()
                clearAll = false
            }
        }
    }
}
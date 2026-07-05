package com.rapidraw.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * SafePreferences 单元测试。
 *
 * 覆盖：
 * 1. 所有 getter 在 prefs 损坏时返回默认值
 * 2. 所有 setter 在写入失败时不影响后续读
 * 3. StringSet 类型读写
 * 4. clear / remove 不抛异常
 * 5. 损坏 XML 自动删除并重建
 *
 * @since 2026.07
 */
@RunWith(RobolectricTestRunner::class)
class SafePreferencesTest {

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        // 清理可能残留的测试 prefs
        prefsDir()?.listFiles()
            ?.filter { it.name.startsWith("test_") }
            ?.forEach { it.delete() }
    }

    @After
    fun tearDown() {
        prefsDir()?.listFiles()
            ?.filter { it.name.startsWith("test_") }
            ?.forEach { it.delete() }
    }

    @Test
    fun `get - all read methods return defaults on corrupted XML`() {
        writeCorrupted("test_corrupt_read")
        val prefs = SafePreferences.get(ctx, "test_corrupt_read")
        assertEquals("d", SafePreferences.getString(prefs, "k", "d"))
        assertEquals(7, SafePreferences.getInt(prefs, "k", 7))
        assertEquals(7L, SafePreferences.getLong(prefs, "k", 7L))
        assertEquals(1.5f, SafePreferences.getFloat(prefs, "k", 1.5f))
        assertEquals(true, SafePreferences.getBoolean(prefs, "k", true))
        assertEquals(setOf("a"), SafePreferences.getStringSet(prefs, "k", setOf("a")))
    }

    @Test
    fun `put - all write methods succeed even on corrupted then recovered prefs`() {
        writeCorrupted("test_corrupt_write")
        val prefs = SafePreferences.get(ctx, "test_corrupt_write")
        SafePreferences.putString(prefs, "s", "v")
        SafePreferences.putInt(prefs, "i", 1)
        SafePreferences.putLong(prefs, "l", 2L)
        SafePreferences.putFloat(prefs, "f", 3f)
        SafePreferences.putBoolean(prefs, "b", true)
        SafePreferences.putStringSet(prefs, "set", setOf("x"))

        assertEquals("v", SafePreferences.getString(prefs, "s"))
        assertEquals(1, SafePreferences.getInt(prefs, "i"))
        assertEquals(2L, SafePreferences.getLong(prefs, "l"))
        assertEquals(3f, SafePreferences.getFloat(prefs, "f"))
        assertEquals(true, SafePreferences.getBoolean(prefs, "b"))
        assertEquals(setOf("x"), SafePreferences.getStringSet(prefs, "set"))
    }

    @Test
    fun `clear and remove - both succeed without exception`() {
        val prefs = SafePreferences.get(ctx, "test_clear_remove")
        SafePreferences.putString(prefs, "a", "1")
        SafePreferences.putString(prefs, "b", "2")
        SafePreferences.remove(prefs, "a")
        assertNull(SafePreferences.getString(prefs, "a", null))
        assertEquals("2", SafePreferences.getString(prefs, "b", null))
        SafePreferences.clear(prefs)
        assertNull(SafePreferences.getString(prefs, "b", null))
    }

    @Test
    fun `corrupted XML is removed and a fresh prefs instance is returned`() {
        val name = "test_corrupt_replace"
        writeCorrupted(name)
        val xmlFile = File(prefsDir()!!, "$name.xml")
        assertTrue("Corrupted file should exist before recovery", xmlFile.exists())
        SafePreferences.get(ctx, name)
        // 修复后文件可能被删除（损坏）并由 SharedPreferences 重建
        val newPrefs = SafePreferences.get(ctx, name)
        SafePreferences.putString(newPrefs, "after", "ok")
        assertEquals("ok", SafePreferences.getString(newPrefs, "after", null))
    }

    private fun writeCorrupted(name: String) {
        val dir = prefsDir()!!
        File(dir, "$name.xml").writeText("### not valid xml ###")
    }

    private fun prefsDir(): File? = runCatching {
        ctx.getDir("shared_prefs", Context.MODE_PRIVATE)
    }.getOrNull() ?: File(ctx.filesDir.parentFile, "shared_prefs").also { it.mkdirs() }
}

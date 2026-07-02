package com.rapidraw.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EncryptedPreferencesTest {

    private lateinit var context: Context
    private lateinit var prefs: EncryptedPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        prefs = EncryptedPreferences.getInstance(context)
        prefs.clear()
    }

    @Test
    fun testPutAndGetString() {
        prefs.putString("test_key", "test_value")
        assertEquals("test_value", prefs.getString("test_key"))
    }

    @Test
    fun testGetStringDefaultValue() {
        assertEquals("default", prefs.getString("nonexistent", "default"))
    }

    @Test
    fun testPutAndGetInt() {
        prefs.putInt("int_key", 42)
        assertEquals(42, prefs.getInt("int_key"))
    }

    @Test
    fun testGetIntDefaultValue() {
        assertEquals(100, prefs.getInt("nonexistent", 100))
    }

    @Test
    fun testPutAndGetBoolean() {
        prefs.putBoolean("bool_key", true)
        assertTrue(prefs.getBoolean("bool_key"))
    }

    @Test
    fun testPutAndGetLong() {
        prefs.putLong("long_key", 9999999999L)
        assertEquals(9999999999L, prefs.getLong("long_key"))
    }

    @Test
    fun testRemove() {
        prefs.putString("remove_key", "value")
        prefs.remove("remove_key")
        assertNull(prefs.getString("remove_key"))
    }

    @Test
    fun testContains() {
        prefs.putString("contains_key", "value")
        assertTrue(prefs.contains("contains_key"))
        assertFalse(prefs.contains("nonexistent"))
    }

    @Test
    fun testClear() {
        prefs.putString("key1", "val1")
        prefs.putString("key2", "val2")
        prefs.clear()
        assertNull(prefs.getString("key1"))
        assertNull(prefs.getString("key2"))
    }

    @Test
    fun testSingleton() {
        val instance1 = EncryptedPreferences.getInstance(context)
        val instance2 = EncryptedPreferences.getInstance(context)
        assertSame(instance1, instance2)
    }
}
package com.rapidraw.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class CrashHandlerPiiTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `sanitized crash log does not contain PII`() {
        val raw = """
            Path: /storage/emulated/0/DCIM/user_photo.jpg
            Data dir: /data/data/com.rapidraw
            User: /Users/john.doe/work
            Token: deadbeef1234567890abcdef12345678
            Contact: john.doe@example.com
            Server: 192.168.1.100
            Phone: 1381234567890
        """.trimIndent()
        val sanitized = CrashHandler.javaClass.getDeclaredMethod(
            "sanitizePii", String::class.java
        ).apply { isAccessible = true }.invoke(CrashHandler, raw) as String

        assertTrue(sanitized.contains("<user_path>"))
        assertTrue(sanitized.contains("<app_data>"))
        assertTrue(sanitized.contains("<username>"))
        assertTrue(sanitized.contains("<id>"))
        assertTrue(sanitized.contains("<email>"))
        assertTrue(sanitized.contains("<ip>"))
        assertTrue(sanitized.contains("<number>"))
        assertFalse(sanitized.contains("john.doe@example.com"))
        assertFalse(sanitized.contains("192.168.1.100"))
        assertFalse(sanitized.contains("1381234567890"))
    }
}

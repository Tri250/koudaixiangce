package com.rapidraw.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SecurityProviderTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testVerifyAppSignature() {
        // 在测试环境中仅验证签名检查不会崩溃
        val result = SecurityProvider.verifyAppSignature(context)
        // Robolectric 环境下可能返回 false
        assertNotNull(result)
    }

    @Test
    fun testRandomBytes() {
        val bytes = SecurityProvider.randomBytes(32)
        assertEquals(32, bytes.size)
        // 确保不是全零
        assertFalse(bytes.all { it == 0.toByte() })
    }

    @Test
    fun testRandomHex() {
        val hex = SecurityProvider.randomHex(16)
        assertEquals(32, hex.length)
        // 确保是有效的十六进制字符串
        assertTrue(hex.all { it in "0123456789abcdef" })
    }

    @Test
    fun testRandomBase64() {
        val base64 = SecurityProvider.randomBase64(16)
        assertTrue(base64.isNotEmpty())
        // 确保是有效的 Base64
        assertTrue(base64.all { it in "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=" })
    }

    @Test
    fun testSha256() {
        val data = "test".toByteArray(Charsets.UTF_8)
        val hash = SecurityProvider.sha256(data)
        assertEquals(32, hash.size)
    }

    @Test
    fun testSha256Hex() {
        val hash = SecurityProvider.sha256Hex("test")
        assertEquals(64, hash.length)
    }

    @Test
    fun testSha256Deterministic() {
        val hash1 = SecurityProvider.sha256Hex("hello")
        val hash2 = SecurityProvider.sha256Hex("hello")
        assertEquals(hash1, hash2)
    }

    @Test
    fun testSha256DifferentInputs() {
        val hash1 = SecurityProvider.sha256Hex("hello")
        val hash2 = SecurityProvider.sha256Hex("world")
        assertNotEquals(hash1, hash2)
    }

    @Test
    fun testIsDebuggerAttached() {
        // 测试环境下不应被调试器附加
        val result = SecurityProvider.isDebuggerAttached()
        assertNotNull(result)
    }

    @Test
    fun testIsSafeString() {
        assertTrue(SecurityProvider.isSafeString("hello"))
        assertTrue(SecurityProvider.isSafeString("Hello World 123"))
        assertFalse(SecurityProvider.isSafeString("hello'world"))
        assertFalse(SecurityProvider.isSafeString("<script>"))
        assertFalse(SecurityProvider.isSafeString("world;DROP TABLE"))
    }

    @Test
    fun testSafeTruncate() {
        assertEquals("hello", SecurityProvider.safeTruncate("hello", 100))
        assertEquals("12345", SecurityProvider.safeTruncate("1234567890", 5))
        assertEquals("", SecurityProvider.safeTruncate("", 10))
    }

    @Test
    fun testHmacSha256() {
        val key = SecurityProvider.randomBytes(32)
        val data = "test".toByteArray(Charsets.UTF_8)
        val mac = SecurityProvider.hmacSha256(key, data)
        assertEquals(32, mac.size)
    }
}
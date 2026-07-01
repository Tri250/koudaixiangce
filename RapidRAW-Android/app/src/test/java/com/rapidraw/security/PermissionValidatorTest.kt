package com.rapidraw.security

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
class PermissionValidatorTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testGetMissingPermissions() {
        val permissions = arrayOf(
            Manifest.permission.INTERNET,
            Manifest.permission.READ_MEDIA_IMAGES,
        )
        val missing = PermissionValidator.getMissingPermissions(context, permissions)
        // 在测试环境中，这些权限通常未被授予
        assertNotNull(missing)
    }

    @Test
    fun testHasAllPermissionsEmpty() {
        // 空权限列表应该返回 true
        assertTrue(PermissionValidator.hasAllPermissions(context, emptyArray()))
    }

    @Test
    fun testHasInternetPermission() {
        // 不抛异常即为通过
        val result = PermissionValidator.hasInternetPermission(context)
        assertNotNull(result)
    }

    @Test
    fun testHasStorageReadPermission() {
        val result = PermissionValidator.hasStorageReadPermission(context)
        assertNotNull(result)
    }

    @Test
    fun testHasNotificationPermission() {
        val result = PermissionValidator.hasNotificationPermission(context)
        assertNotNull(result)
    }

    @Test
    fun testGetDeclaredPermissions() {
        val result = PermissionValidator.getDeclaredPermissions(context)
        assertNotNull(result)
        // 应用至少声明了 INTERNET 权限
        assertTrue(result.any { it.contains("INTERNET") })
    }
}
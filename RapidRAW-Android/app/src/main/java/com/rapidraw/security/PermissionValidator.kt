package com.rapidraw.security

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * 运行时权限验证工具。
 *
 * 提供统一的权限检查 API，避免 Activity 中分散的权限判断代码。
 * 同时为 Android 版本差异提供兼容层。
 *
 * @since v1.10.0（正式版安全性加固）
 */
object PermissionValidator {

    /** 检查是否有存储读取权限（Android 13+/13- 自动适配） */
    fun hasStorageReadPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    /** 检查是否有通知权限（Android 13+） */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /** 检查是否有网络权限 */
    fun hasInternetPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.INTERNET) ==
            PackageManager.PERMISSION_GRANTED
    }

    /** 获取仍未授权的权限列表 */
    fun getMissingPermissions(context: Context, permissions: Array<String>): List<String> {
        return permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
    }

    /** 检查所有权限是否已授权 */
    fun hasAllPermissions(context: Context, permissions: Array<String>): Boolean {
        return getMissingPermissions(context, permissions).isEmpty()
    }

    /** 获取当前应用中声明的所有权限列表 */
    fun getDeclaredPermissions(context: Context): Array<String> {
        return runCatching {
            context.packageManager
                .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
                .requestedPermissions ?: emptyArray()
        }.getOrDefault(emptyArray())
    }
}
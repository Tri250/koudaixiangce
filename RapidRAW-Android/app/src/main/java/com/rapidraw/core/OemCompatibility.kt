package com.rapidraw.core

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log

/**
 * OEM 兼容性适配器。
 *
 * 处理各厂商 ROM 的特定限制和行为差异：
 * - MIUI / HyperOS: 自启动管理、后台弹出界面权限
 * - ColorOS / OxygenOS: 后台冻结、自动启动
 * - OneUI: 省电模式、后台限制
 * - EMUI / HarmonyOS: 启动管理
 *
 * @since v1.10.2（正式版兼容性加固）
 */
object OemCompatibility {

    private const val TAG = "OemCompatibility"

    /** OEM 类型 */
    enum class OemType {
        XIAOMI,    // MIUI / HyperOS
        OPPO,      // ColorOS / OxygenOS
        SAMSUNG,   // OneUI
        HUAWEI,    // EMUI / HarmonyOS
        VIVO,      // Funtouch OS / OriginOS
        GOOGLE,    // Pixel Experience
        LENOVO,    // ZUI
        OTHER,     // 其他
    }

    /** 检测当前 OEM 类型 */
    fun detectOem(): OemType {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        return when {
            manufacturer.contains("xiaomi") || brand.contains("xiaomi") ||
                manufacturer.contains("redmi") || brand.contains("redmi") -> OemType.XIAOMI
            manufacturer.contains("oppo") || brand.contains("oppo") ||
                manufacturer.contains("realme") || brand.contains("realme") ||
                manufacturer.contains("oneplus") || brand.contains("oneplus") -> OemType.OPPO
            manufacturer.contains("samsung") || brand.contains("samsung") -> OemType.SAMSUNG
            manufacturer.contains("huawei") || brand.contains("huawei") ||
                manufacturer.contains("honor") || brand.contains("honor") -> OemType.HUAWEI
            manufacturer.contains("vivo") || brand.contains("vivo") -> OemType.VIVO
            manufacturer.contains("google") -> OemType.GOOGLE
            manufacturer.contains("lenovo") || brand.contains("lenovo") -> OemType.LENOVO
            else -> OemType.OTHER
        }
    }

    // ── MIUI / HyperOS ────────────────────────────────────────────────

    /**
     * 检查 MIUI 自启动管理设置页面是否可达。
     * v2026.07: 重命名方法以反映实际行为（此前方法名暗示"检查权限是否已启用"，
     * 但实际仅检测设置 Activity 是否存在）。MIUI 不提供公开 API 查询自启动状态，
     * 因此只能引导用户到设置页面手动确认。
     */
    fun isXiaomiAutoStartSettingReachable(context: Context): Boolean {
        if (detectOem() != OemType.XIAOMI) return true
        return try {
            val intent = Intent()
            intent.component = android.content.ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            )
            context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null
        } catch (_: Exception) {
            false
        }
    }

    /** 旧方法名保留为别名，避免调用方编译错误。 */
    @Deprecated("命名不准确，请使用 isXiaomiAutoStartSettingReachable", ReplaceWith("isXiaomiAutoStartSettingReachable(context)"))
    fun isXiaomiAutoStartEnabled(context: Context): Boolean = isXiaomiAutoStartSettingReachable(context)

    /**
     * 打开 MIUI 自启动管理页面。
     */
    fun openXiaomiAutoStartSettings(context: Context) {
        try {
            val intent = Intent().apply {
                component = android.content.ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            Log.w(TAG, "Cannot open MIUI auto-start settings")
        }
    }

    // ── ColorOS / OxygenOS ────────────────────────────────────────────

    /**
     * 检查 ColorOS 后台冻结状态。
     * OPPO/OnePlus 设备可能在应用进入后台后冻结进程。
     */
    fun isColorOSBackgroundFrozen(): Boolean {
        return detectOem() == OemType.OPPO
    }

    /**
     * 打开 ColorOS 应用设置页面。
     * 引导用户关闭"耗电异常优化"和"后台冻结"。
     */
    fun openColorOSAppSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            Log.w(TAG, "Cannot open ColorOS app settings")
        }
    }

    // ── Samsung OneUI ─────────────────────────────────────────────────

    /**
     * 检查 Samsung 省电模式是否影响后台任务。
     * OneUI 5.0+ 的省电模式会限制后台 CPU 和网络。
     */
    fun isSamsungPowerSaveRestricting(context: Context): Boolean {
        if (detectOem() != OemType.SAMSUNG) return false
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            pm?.isPowerSaveMode == true
        } catch (_: Exception) {
            false
        }
    }

    // ── 通用 ──────────────────────────────────────────────────────────

    /**
     * 获取当前 OEM 的人类可读名称。
     */
    fun getOemDisplayName(): String {
        return when (detectOem()) {
            OemType.XIAOMI -> "MIUI / HyperOS"
            OemType.OPPO -> "ColorOS / OxygenOS"
            OemType.SAMSUNG -> "One UI"
            OemType.HUAWEI -> "EMUI / HarmonyOS"
            OemType.VIVO -> "Funtouch OS / OriginOS"
            OemType.GOOGLE -> "Pixel Experience"
            OemType.LENOVO -> "ZUI"
            OemType.OTHER -> Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        }
    }

    /**
     * 检测是否存在已知的 OEM 后台限制。
     * 返回 true 表示需要引导用户调整系统设置。
     */
    fun hasKnownBackgroundRestrictions(context: Context): Boolean {
        return when (detectOem()) {
            OemType.XIAOMI -> !isXiaomiAutoStartEnabled(context)
            OemType.OPPO -> true  // ColorOS 默认冻结后台
            OemType.SAMSUNG -> isSamsungPowerSaveRestricting(context)
            OemType.HUAWEI -> true // EMUI 启动管理
            OemType.VIVO -> true  // Funtouch OS 限制后台
            OemType.GOOGLE -> false
            OemType.LENOVO -> false
            OemType.OTHER -> false
        }
    }
}
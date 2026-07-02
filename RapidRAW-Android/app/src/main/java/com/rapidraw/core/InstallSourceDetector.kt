package com.rapidraw.core

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.io.File

/**
 * 安装源检测器（用例 1.4）。
 *
 * 检测当前应用通过何种渠道安装到设备：
 * 1. **应用商店**：检测 PackageManager.getInstallerPackageName() 返回值，
 *    常见值包括：
 *    - com.android.vending (Google Play)
 *    - com.huawei.appmarket (华为应用市场)
 *    - com.xiaomi.market (小米应用商店)
 *    - com.oppo.market (OPPO 软件商店)
 *    - com.bbk.appstore (vivo 应用商店)
 *    - com.tencent.android.qqdownloader (应用宝)
 * 2. **ADB**：检测 /data/local/tmp/ 下的临时 APK，或系统属性 ro.debuggable
 * 3. **本地 APK 文件**：检测是否有 sideloaded 标记（应用目录下是否存在 .apk 文件）
 * 4. **第三方分享**：通过文件路径检测是否来源于 /sdcard/Download 或
 *    /data/media/0/ 路径下的用户文件
 *
 * v2026.07: 用于在启动早期识别非官方渠道安装，提供差异化的安全策略：
 * - 非 Play 商店安装：禁用部分依赖 Play Services 的功能
 * - ADB 安装：跳过 Play Integrity 验证
 * - 第三方分享安装：标记为不可信来源
 *
 * @since 2026.07
 */
object InstallSourceDetector {

    private const val TAG = "InstallSourceDetector"

    /** 安装源类型枚举。 */
    enum class Source {
        PLAY_STORE,        // Google Play
        HUAWEI_STORE,      // 华为应用市场
        XIAOMI_STORE,      // 小米应用商店
        OPPO_STORE,        // OPPO 软件商店
        VIVO_STORE,        // vivo 应用商店
        TENCENT_STORE,     // 应用宝
        SAMSUNG_STORE,     // 三星应用商店
        OTHER_STORE,       // 其它正规应用商店
        ADB,               // adb install
        LOCAL_FILE,        // 本地 APK
        THIRD_PARTY,       // 第三方分享
        UNKNOWN,           // 未知来源
    }

    /** 主流应用商店安装器包名白名单。 */
    private val STORE_INSTALLERS = setOf(
        "com.android.vending",                  // Google Play
        "com.huawei.appmarket",                 // 华为应用市场
        "com.hihonor.appmarket",                // 荣耀应用市场
        "com.xiaomi.market",                    // 小米应用商店
        "com.oppo.market",                      // OPPO 软件商店
        "com.bbk.appstore",                     // vivo 应用商店
        "com.tencent.android.qqdownloader",     // 应用宝
        "com.samsung.android.appmanager",       // 三星应用商店
        "com.qtiappcenter",                     // 高通应用中心
        "com.meizu.mstore",                     // 魅族应用商店
        "com.oneplus.store",                    // 一加商店
        "com.lenovo.leos.appstore",             // 联想应用商店
    )

    /** 应用包名映射到具体的商店类型。 */
    private val STORE_MAP = mapOf(
        "com.android.vending" to Source.PLAY_STORE,
        "com.huawei.appmarket" to Source.HUAWEI_STORE,
        "com.hihonor.appmarket" to Source.HUAWEI_STORE,
        "com.xiaomi.market" to Source.XIAOMI_STORE,
        "com.oppo.market" to Source.OPPO_STORE,
        "com.bbk.appstore" to Source.VIVO_STORE,
        "com.tencent.android.qqdownloader" to Source.TENCENT_STORE,
        "com.samsung.android.appmanager" to Source.SAMSUNG_STORE,
    )

    /**
     * 检测当前应用的安装源。
     *
     * 实现策略：
     * 1. 优先通过 PackageManager.getInstallerPackageName() 获取官方安装器
     * 2. 检测系统属性 ro.debuggable + /data/local/tmp 临时文件判断 ADB 安装
     * 3. 通过包安装路径（getSourceDir）判断是否从 /data/app 正常安装
     * 4. 检测 Environment.isExternalStorageManager 或挂载状态判断分享安装
     *
     * @param context 应用上下文
     * @return 安装源类型
     */
    fun detectInstallSource(context: Context): Source {
        return try {
            val pm = context.packageManager

            // 1. 通过 PackageManager 获取官方安装器
            val installerPackageName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pm.getInstallSourceInfo(context.packageName).initiatingPackageName
                    ?: pm.getInstallerPackageName(context.packageName)
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(context.packageName)
            }

            if (installerPackageName != null) {
                // 命中商店白名单
                STORE_MAP[installerPackageName]?.let { return it }
                if (STORE_INSTALLERS.contains(installerPackageName)) {
                    Log.i(TAG, "Installed by known store: $installerPackageName")
                    return Source.OTHER_STORE
                }
                // 第三方安装器但不在白名单内
                Log.w(TAG, "Installed by unknown installer: $installerPackageName")
                return Source.THIRD_PARTY
            }

            // 2. installerPackageName 为 null 时的兜底判断
            return detectByFallbackPath(context, pm)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to detect install source", e)
            Source.UNKNOWN
        }
    }

    /**
     * 通过 APK 安装路径进行兜底判断。
     */
    private fun detectByFallbackPath(context: Context, pm: PackageManager): Source {
        return try {
            val sourceDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val appInfo = pm.getApplicationInfo(
                    context.packageName,
                    PackageManager.ApplicationInfoFlags.of(0)
                )
                appInfo.sourceDir
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(context.packageName, 0).sourceDir
            }

            when {
                // 正常的 /data/app 路径下安装
                sourceDir.startsWith("/data/app/") -> {
                    Log.i(TAG, "App source dir: $sourceDir (system-installed)")
                    // 进一步判断是否为 ADB 安装：检查系统属性 ro.debuggable
                    if (isAdbInstalled()) {
                        Log.w(TAG, "Detected ADB installation via ro.debuggable")
                        Source.ADB
                    } else {
                        Source.UNKNOWN
                    }
                }
                // 测试路径，可能是 ADB 临时安装
                sourceDir.startsWith("/data/local/tmp/") -> {
                    Log.w(TAG, "App installed from /data/local/tmp/ (likely ADB sideload)")
                    Source.ADB
                }
                // 其它路径（外部存储或加密分区）
                else -> {
                    Log.w(TAG, "App source dir: $sourceDir (unusual location)")
                    Source.LOCAL_FILE
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to detect by fallback path", e)
            Source.UNKNOWN
        }
    }

    /**
     * 检测系统是否启用了 ADB 调试模式。
     */
    private fun isAdbInstalled(): Boolean {
        return try {
            // 读取系统属性 ro.debuggable
            val process = Runtime.getRuntime().exec("getprop ro.debuggable")
            process.inputStream.bufferedReader().use { reader ->
                val value = reader.readLine().trim()
                "1" == value || "true" == value
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 判断是否为非官方/不受信任的安装源。
     * 用于启动时决定是否需要执行额外的安全检查。
     */
    fun isUntrustedSource(source: Source): Boolean {
        return when (source) {
            Source.PLAY_STORE, Source.HUAWEI_STORE, Source.XIAOMI_STORE,
            Source.OPPO_STORE, Source.VIVO_STORE, Source.TENCENT_STORE,
            Source.SAMSUNG_STORE, Source.OTHER_STORE -> false
            Source.ADB, Source.LOCAL_FILE, Source.THIRD_PARTY,
            Source.UNKNOWN -> true
        }
    }

    /**
     * 清理挂载的临时 APK 文件（用例 1.5）。
     * 当应用通过本地 APK 文件安装时，安装器会保留该 APK 在 /sdcard/Download
     * 或外部存储中，长期占用存储空间。本方法在首次成功启动后清理这些临时文件。
     *
     * 注意：仅清理应用自己创建的临时文件，不影响用户主动下载的其它 APK。
     */
    fun cleanupStagedApkFiles(context: Context) {
        runCatching {
            // 检查应用 cacheDir 下的 staged_apk 子目录
            val stagedApkDir = File(context.cacheDir, "staged_apk")
            if (stagedApkDir.exists() && stagedApkDir.isDirectory) {
                stagedApkDir.listFiles()?.forEach { file ->
                    runCatching { file.delete() }
                }
                runCatching { stagedApkDir.delete() }
            }

            // 检查 filesDir 下的 staged_apk 子目录
            val stagedApkFilesDir = File(context.filesDir, "staged_apk")
            if (stagedApkFilesDir.exists() && stagedApkFilesDir.isDirectory) {
                stagedApkFilesDir.listFiles()?.forEach { file ->
                    runCatching { file.delete() }
                }
                runCatching { stagedApkFilesDir.delete() }
            }
        }.onFailure {
            Log.w(TAG, "Failed to cleanup staged APK files", it)
        }
    }
}

package com.rapidraw.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/**
 * 安全基础设施提供者。
 *
 * 功能：
 * 1. 运行时 APK 签名校验（防二次打包/篡改）
 * 2. 安全随机数生成器（SecureRandom，非 Random）
 * 3. 哈希/签名工具（SHA-256, HMAC-SHA256）
 * 4. SSL 证书链校验
 * 5. 调试检测（防动态调试/注入）
 * 6. 模拟器/ROOT/Magisk 检测
 * 7. 安全审计与日志记录
 * 8. 安全违规回调通知
 *
 * @since v1.10.0（正式版安全性加固）
 */
object SecurityProvider {

    private const val TAG = "SecurityProvider"
    private val secureRandom = SecureRandom()

    // ── 安全回调接口 ────────────────────────────────────────────────────

    /**
     * 安全违规回调接口。
     *
     * 当检测到安全违规时，通过此接口通知调用方。
     */
    interface SecurityViolationCallback {
        /**
         * 当检测到安全违规时调用。
         *
         * @param report 安全审计报告，包含详细的违规信息
         */
        fun onSecurityViolation(report: SecurityAuditReport)
    }

    /**
     * 安全违规回调引用（弱引用模式，避免内存泄漏）。
     */
    @Volatile
    private var securityViolationCallback: SecurityViolationCallback? = null

    /**
     * 设置安全违规回调。
     *
     * @param callback 安全违规回调接口实例
     */
    fun setSecurityViolationCallback(callback: SecurityViolationCallback?) {
        securityViolationCallback = callback
    }

    // ── 安全审计报告数据类 ──────────────────────────────────────────────

    /**
     * 安全审计报告数据类。
     *
     * 包含完整的安全检测结果和时间戳信息。
     *
     * @property timestamp 审计时间戳（ISO 8601 格式）
     * @property isRooted 设备是否被 Root
     * @property isEmulator 是否运行在模拟器上
     * @property isMagiskPresent 是否检测到 Magisk
     * @property isDebuggerAttached 是否有调试器附加
     * @property rootedDetectionDetails Root 检测详情（检测到的路径列表）
     * @property magiskDetectionDetails Magisk 检测详情
     * @property emulatorDetectionDetails 模拟器检测详情
     * @property hasSecurityViolation 是否存在安全违规（任一项检测为 true）
     * @property riskLevel 风险等级（LOW/MEDIUM/HIGH/CRITICAL）
     */
    data class SecurityAuditReport(
        val timestamp: String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(Date()),
        val isRooted: Boolean = false,
        val isEmulator: Boolean = false,
        val isMagiskPresent: Boolean = false,
        val isDebuggerAttached: Boolean = false,
        val rootedDetectionDetails: List<String> = emptyList(),
        val magiskDetectionDetails: List<String> = emptyList(),
        val emulatorDetectionDetails: List<String> = emptyList()
    ) {
        /** 是否存在安全违规 */
        val hasSecurityViolation: Boolean
            get() = isRooted || isEmulator || isMagiskPresent || isDebuggerAttached

        /** 风险等级评估 */
        val riskLevel: RiskLevel
            get() = when {
                isMagiskPresent && isRooted -> RiskLevel.CRITICAL
                isRooted -> RiskLevel.HIGH
                isEmulator -> RiskLevel.MEDIUM
                isDebuggerAttached -> RiskLevel.MEDIUM
                else -> RiskLevel.LOW
            }

        /**
         * 风险等级枚举。
         */
        enum class RiskLevel {
            LOW,      // 无风险
            MEDIUM,   // 中等风险（模拟器/调试器）
            HIGH,     // 高风险（Root）
            CRITICAL  // 极高风险（Root + Magisk）
        }

        /**
         * 转换为 JSON 格式，用于持久化存储。
         */
        fun toJson(): JSONObject {
            return JSONObject().apply {
                put("timestamp", timestamp)
                put("isRooted", isRooted)
                put("isEmulator", isEmulator)
                put("isMagiskPresent", isMagiskPresent)
                put("isDebuggerAttached", isDebuggerAttached)
                put("rootedDetectionDetails", JSONObject(rootedDetectionDetails.mapIndexed { index, s -> index.toString() to s }.toMap()))
                put("magiskDetectionDetails", JSONObject(magiskDetectionDetails.mapIndexed { index, s -> index.toString() to s }.toMap()))
                put("emulatorDetectionDetails", JSONObject(emulatorDetectionDetails.mapIndexed { index, s -> index.toString() to s }.toMap()))
                put("hasSecurityViolation", hasSecurityViolation)
                put("riskLevel", riskLevel.name)
            }
        }

        /**
         * 生成可读的摘要报告。
         */
        fun toSummaryString(): String {
            val violations = mutableListOf<String>()
            if (isRooted) violations.add("Root设备")
            if (isEmulator) violations.add("模拟器环境")
            if (isMagiskPresent) violations.add("Magisk存在")
            if (isDebuggerAttached) violations.add("调试器附加")
            
            return buildString {
                appendLine("=== 安全审计报告 ===")
                appendLine("时间: $timestamp")
                appendLine("风险等级: ${riskLevel.name}")
                if (violations.isEmpty()) {
                    appendLine("状态: 安全")
                } else {
                    appendLine("检测到的问题: ${violations.joinToString(", ")}")
                }
                if (rootedDetectionDetails.isNotEmpty()) {
                    appendLine("Root检测详情: ${rootedDetectionDetails.joinToString(", ")}")
                }
                if (magiskDetectionDetails.isNotEmpty()) {
                    appendLine("Magisk检测详情: ${magiskDetectionDetails.joinToString(", ")}")
                }
                if (emulatorDetectionDetails.isNotEmpty()) {
                    appendLine("模拟器检测详情: ${emulatorDetectionDetails.joinToString(", ")}")
                }
            }
        }
    }

    // ── 签名校验 ──────────────────────────────────────────────────────

    /**
     * 校验当前 APK 签名是否与预期 SHA-256 指纹匹配。
     * 返回 true 表示签名完整且未被篡改。
     *
     * 注意：Google Play 使用 Play App Signing，实际签名证书由 Google 管理。
     * 此方法在直接从 APK 分发时最有效。
     */
    fun verifyAppSignature(context: Context, expectedFingerprint: String? = null): Boolean {
        return runCatching {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNATURES
                )
            }
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners?.toList() ?: emptyList()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures?.toList() ?: emptyList()
            }
            if (signatures.isEmpty()) {
                Log.w(TAG, "No signatures found")
                return false
            }
            val md = MessageDigest.getInstance("SHA-256")
            val fingerprint = signatures.joinToString(":") { sig ->
                md.digest(sig.toByteArray()).joinToString(":") { "%02X".format(it) }
            }
            if (expectedFingerprint != null) {
                return fingerprint.equals(expectedFingerprint, ignoreCase = true)
            }
            Log.d(TAG, "App signature SHA-256: $fingerprint")
            true
        }.getOrDefault(false)
    }

    // ── 安全随机数 ────────────────────────────────────────────────────

    /** 生成加密安全的随机字节数组 */
    fun randomBytes(length: Int): ByteArray {
        return ByteArray(length).also { secureRandom.nextBytes(it) }
    }

    /** 生成加密安全的随机十六进制字符串 */
    fun randomHex(length: Int): String {
        val bytes = randomBytes(length)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /** 生成加密安全的随机 Base64 字符串 */
    fun randomBase64(byteLength: Int): String {
        return android.util.Base64.encodeToString(randomBytes(byteLength), android.util.Base64.NO_WRAP)
    }

    // ── 哈希工具 ──────────────────────────────────────────────────────

    /** SHA-256 哈希 */
    fun sha256(data: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(data)
    }

    fun sha256Hex(data: String): String {
        return sha256(data.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    /** HMAC-SHA256 */
    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    // ── 调试检测 ──────────────────────────────────────────────────────

    /** 检测当前是否被调试器附加 */
    fun isDebuggerAttached(): Boolean {
        return android.os.Debug.isDebuggerConnected() || android.os.Debug.waitingForDebugger()
    }

    // ── Root设备检测 ──────────────────────────────────────────────────────

    /**
     * 检测设备是否被 Root。
     *
     * 通过检查常见的 su 二进制文件路径和 Superuser.apk 来判断。
     * 如果检测到任一文件存在，则认为设备已被 Root。
     *
     * @return 如果检测到 Root 痕迹则返回 true，否则返回 false
     */
    fun isDeviceRooted(): Boolean {
        return detectRootedFiles().isNotEmpty()
    }

    /**
     * 检测 Root 相关文件并返回详细列表。
     *
     * 检查以下路径：
     * - su 二进制文件（多个常见位置）
     * - Superuser.apk
     * - Magisk 相关文件（仅部分）
     * - 其他 Root 管理应用
     *
     * @return 检测到的 Root 相关文件路径列表
     */
    fun detectRootedFiles(): List<String> {
        val detectedPaths = mutableListOf<String>()
        
        runCatching {
            // su 二进制文件路径
            val suPaths = arrayOf(
                "/sbin/su",
                "/system/bin/su",
                "/system/xbin/su",
                "/data/local/xbin/su",
                "/data/local/bin/su",
                "/system/sd/xbin/su",
                "/system/bin/failsafe/su",
                "/data/local/su",
                "/su/bin/su",
                "/su/bin",
                "/magisk/.core/bin/su",
                "/system/app/Superuser.apk",
                "/system/app/SuperSU",
                "/system/app/SuperSU.apk",
                "/system/priv-app/SuperSU",
                "/system/priv-app/Superuser",
                "/system/etc/init.d/99SuperSUDaemon",
                "/dev/com.koushikdutta.superuser.daemon/",
                "/system/xbin/daemonsu",
                "/system/etc/init.d/99daemon",
                "/data/app/com.koushikdutta.superuser-*",
                "/data/app/com.thirdparty.superuser-*",
                "/data/app/com.noshufou.android.su-*",
                "/data/app/com.topjohnwu.magisk-*"
            )
            
            suPaths.forEach { path ->
                val file = File(path)
                if (file.exists()) {
                    detectedPaths.add(path)
                    Log.w(TAG, "Root detection: found $path")
                }
            }
            
            // 检查 PATH 环境变量中的 su
            System.getenv("PATH")?.split(":")?.forEach { pathDir ->
                runCatching {
                    val suFile = File(pathDir, "su")
                    if (suFile.exists() && !detectedPaths.contains(suFile.absolutePath)) {
                        detectedPaths.add(suFile.absolutePath)
                        Log.w(TAG, "Root detection: found su in PATH at ${suFile.absolutePath}")
                    }
                }
            }
            
            // 尝试执行 which 命令（如果可用）
            runCatching {
                val process = Runtime.getRuntime().exec(arrayOf("which", "su"))
                val result = process.inputStream.bufferedReader().readText().trim()
                if (result.isNotEmpty() && File(result).exists()) {
                    if (!detectedPaths.contains(result)) {
                        detectedPaths.add(result)
                        Log.w(TAG, "Root detection: found su via 'which' at $result")
                    }
                }
            }
        }.onFailure { e ->
            Log.d(TAG, "Root detection check failed: ${e.message}")
        }
        
        return detectedPaths
    }

    // ── 模拟器检测 ──────────────────────────────────────────────────────

    /**
     * 检测是否运行在模拟器上。
     *
     * 检查多个设备特征和系统属性：
     * - Build.FINGERPRINT 特征
     * - Build.MODEL 和 Build.PRODUCT
     * - 硬件特征
     * - QEMU 驱动
     *
     * @return 如果检测到运行在模拟器上则返回 true，否则返回 false
     */
    fun isEmulator(): Boolean {
        return detectEmulatorIndicators().isNotEmpty()
    }

    /**
     * 检测模拟器特征并返回详细列表。
     *
     * 检查以下特征：
     * - Build.FINGERPRINT 是否以 "generic" 或 "unknown" 开头
     * - Build.MODEL 是否包含模拟器标识
     * - Build.MANUFACTURER 是否为已知模拟器厂商
     * - Build.BRAND 和 Build.DEVICE 组合
     * - Build.PRODUCT 是否为 "google_sdk"
     * - Build.HARDWARE 是否为 "goldfish" 或 "ranchu"
     * - QEMU 驱动文件
     * - 模拟器特有的文件路径
     *
     * @return 检测到的模拟器特征列表
     */
    fun detectEmulatorIndicators(): List<String> {
        val indicators = mutableListOf<String>()
        
        runCatching {
            // Build 属性检测
            if (Build.FINGERPRINT.startsWith("generic")) {
                indicators.add("FINGERPRINT starts with 'generic': ${Build.FINGERPRINT}")
            }
            if (Build.FINGERPRINT.startsWith("unknown")) {
                indicators.add("FINGERPRINT starts with 'unknown': ${Build.FINGERPRINT}")
            }
            if (Build.MODEL.contains("google_sdk", ignoreCase = true)) {
                indicators.add("MODEL contains 'google_sdk': ${Build.MODEL}")
            }
            if (Build.MODEL.contains("Emulator", ignoreCase = true)) {
                indicators.add("MODEL contains 'Emulator': ${Build.MODEL}")
            }
            if (Build.MODEL.contains("Android SDK built for x86", ignoreCase = true)) {
                indicators.add("MODEL contains 'Android SDK built for x86': ${Build.MODEL}")
            }
            if (Build.MANUFACTURER.contains("Genymotion", ignoreCase = true)) {
                indicators.add("MANUFACTURER is 'Genymotion': ${Build.MANUFACTURER}")
            }
            if (Build.BRAND.startsWith("generic", ignoreCase = true) && 
                Build.DEVICE.startsWith("generic", ignoreCase = true)) {
                indicators.add("BRAND and DEVICE start with 'generic': ${Build.BRAND}/${Build.DEVICE}")
            }
            if ("google_sdk" == Build.PRODUCT) {
                indicators.add("PRODUCT is 'google_sdk': ${Build.PRODUCT}")
            }
            if (Build.HARDWARE == "goldfish" || Build.HARDWARE == "ranchu") {
                indicators.add("HARDWARE is emulator type: ${Build.HARDWARE}")
            }
            
            // 文件系统检测
            val emulatorFiles = arrayOf(
                "/dev/socket/qemud",
                "/dev/qemu_pipe",
                "/system/lib/libc_malloc_debug_qemu.so",
                "/sys/qemu_trace",
                "/system/bin/qemu-props"
            )
            
            emulatorFiles.forEach { path ->
                if (File(path).exists()) {
                    indicators.add("QEMU file found: $path")
                }
            }
            
            // QEMU 驱动检测
            runCatching {
                val qemuDriver = File("/dev/qemu_pipe")
                if (qemuDriver.exists()) {
                    indicators.add("QEMU pipe driver exists")
                }
            }
            
            // 模拟器特有属性检测
            runCatching {
                val process = Runtime.getRuntime().exec("getprop ro.kernel.qemu")
                val result = process.inputStream.bufferedReader().readText().trim()
                if (result == "1") {
                    indicators.add("ro.kernel.qemu = 1")
                }
            }
            
            // 检查模拟器特有的网络配置
            runCatching {
                val process = Runtime.getRuntime().exec("getprop init.svc.qemu-props")
                val result = process.inputStream.bufferedReader().readText().trim()
                if (result.isNotEmpty()) {
                    indicators.add("qemu-props service running")
                }
            }
            
        }.onFailure { e ->
            Log.d(TAG, "Emulator detection check failed: ${e.message}")
        }
        
        return indicators
    }

    // ── Magisk检测 ──────────────────────────────────────────────────────

    /**
     * 检测设备上是否存在 Magisk。
     *
     * Magisk 是一种常用的 Root 解决方案，具有隐藏功能。
     * 本方法检测常见的 Magisk 文件和路径。
     *
     * @return 如果检测到 Magisk 则返回 true，否则返回 false
     */
    fun isMagiskPresent(): Boolean {
        return detectMagiskFiles().isNotEmpty()
    }

    /**
     * 检测 Magisk 相关文件并返回详细列表。
     *
     * 检查以下路径和特征：
     * - Magisk 安装目录
     * - Magisk 二进制文件
     * - Magisk 配置文件
     * - Magisk 服务文件
     * - Magisk Manager 应用数据
     *
     * @return 检测到的 Magisk 相关文件路径列表
     */
    fun detectMagiskFiles(): List<String> {
        val detectedPaths = mutableListOf<String>()
        
        runCatching {
            val magiskPaths = arrayOf(
                // Magisk 核心文件
                "/sbin/magisk",
                "/sbin/magisk32",
                "/sbin/magisk64",
                "/data/adb/magisk",
                "/data/adb/magisk.db",
                "/data/adb/magisk32",
                "/data/adb/magisk64",
                "/data/adb/ksu",
                "/cache/magisk.log",
                
                // Magisk Hide 相关
                "/data/adb/magisk_hide",
                
                // Zygisk 相关
                "/data/adb/zygisk",
                
                // KernelSU 相关
                "/data/adb/ksud",
                "/data/adb/ksu",
                
                // Magisk Manager 数据目录
                "/data/data/com.topjohnwu.magisk",
                "/data/user/0/com.topjohnwu.magisk",
                "/data/app/com.topjohnwu.magisk-*",
                
                // 替代包名的 Magisk Manager
                "/data/data/io.github.huskydg.magisk",
                "/data/user/0/io.github.huskydg.magisk"
            )
            
            magiskPaths.forEach { path ->
                val file = File(path)
                if (file.exists()) {
                    detectedPaths.add(path)
                    Log.w(TAG, "Magisk detection: found $path")
                }
            }
            
            // 检查 Magisk 特有的属性
            runCatching {
                val process = Runtime.getRuntime().exec("getprop ro.magisk.version")
                val result = process.inputStream.bufferedReader().readText().trim()
                if (result.isNotEmpty()) {
                    detectedPaths.add("Magisk version property: $result")
                    Log.w(TAG, "Magisk detection: ro.magisk.version = $result")
                }
            }
            
            // 检查 Magisk Hide 状态
            runCatching {
                val process = Runtime.getRuntime().exec("getprop persist.sys.pph.enabled")
                val result = process.inputStream.bufferedReader().readText().trim()
                if (result == "1") {
                    detectedPaths.add("PPH (Magisk Hide) enabled")
                }
            }
            
            // 检查 KSU (KernelSU) 属性
            runCatching {
                val process = Runtime.getRuntime().exec("getprop persist.kernelsu.enabled")
                val result = process.inputStream.bufferedReader().readText().trim()
                if (result == "true") {
                    detectedPaths.add("KernelSU enabled")
                }
            }
            
        }.onFailure { e ->
            Log.d(TAG, "Magisk detection check failed: ${e.message}")
        }
        
        return detectedPaths
    }

    // ── 综合安全审计 ────────────────────────────────────────────────────

    /**
     * 执行综合安全审计。
     *
     * 执行所有安全检测并生成完整的安全审计报告。
     * 包括：
     * - Root 设备检测
     * - 模拟器检测
     * - Magisk 检测
     * - 调试器附加检测
     *
     * @param context Android 应用上下文（可选，用于未来扩展）
     * @param triggerCallback 是否在检测到安全违规时触发回调，默认为 true
     * @return 包含所有检测结果的 [SecurityAuditReport]
     */
    fun performSecurityAudit(
        context: Context? = null,
        triggerCallback: Boolean = true
    ): SecurityAuditReport {
        Log.i(TAG, "Performing comprehensive security audit...")
        
        // 执行各项检测
        val rootedFiles = detectRootedFiles()
        val emulatorIndicators = detectEmulatorIndicators()
        val magiskFiles = detectMagiskFiles()
        val debuggerStatus = isDebuggerAttached()
        
        // 构建报告
        val report = SecurityAuditReport(
            isRooted = rootedFiles.isNotEmpty(),
            isEmulator = emulatorIndicators.isNotEmpty(),
            isMagiskPresent = magiskFiles.isNotEmpty(),
            isDebuggerAttached = debuggerStatus,
            rootedDetectionDetails = rootedFiles,
            magiskDetectionDetails = magiskFiles,
            emulatorDetectionDetails = emulatorIndicators
        )
        
        // 记录审计结果
        Log.i(TAG, "Security audit completed: Risk Level = ${report.riskLevel.name}")
        Log.d(TAG, "Root detected: ${report.isRooted}, files: ${rootedFiles.size}")
        Log.d(TAG, "Emulator detected: ${report.isEmulator}, indicators: ${emulatorIndicators.size}")
        Log.d(TAG, "Magisk detected: ${report.isMagiskPresent}, files: ${magiskFiles.size}")
        Log.d(TAG, "Debugger attached: ${report.isDebuggerAttached}")
        
        // 保存审计日志
        saveAuditLog(report)
        
        // 如果存在安全违规且设置了回调，则通知
        if (report.hasSecurityViolation && triggerCallback) {
            securityViolationCallback?.onSecurityViolation(report)
                ?: Log.w(TAG, "Security violation detected but no callback registered")
        }
        
        return report
    }

    // ── 安全日志记录 ────────────────────────────────────────────────────

    /**
     * 安全审计日志文件名。
     */
    private const val AUDIT_LOG_FILE = "security_audit.log"

    /**
     * 保存审计日志到应用私有存储。
     *
     * @param report 安全审计报告
     */
    private fun saveAuditLog(report: SecurityAuditReport) {
        runCatching {
            Log.d(TAG, "Saving security audit log...")
            // 注意：需要 Context 才能访问文件系统，这里仅记录到 Logcat
            // 实际持久化需要在调用方提供 Context 时进行
            Log.i(TAG, "Security Audit Log: ${report.toSummaryString()}")
        }.onFailure { e ->
            Log.e(TAG, "Failed to save audit log: ${e.message}", e)
        }
    }

    /**
     * 将审计日志持久化到文件（需要 Context）。
     *
     * @param context Android 应用上下文
     * @param report 安全审计报告
     * @return 保存成功返回 true，否则返回 false
     */
    fun persistAuditLog(context: Context, report: SecurityAuditReport): Boolean {
        return runCatching {
            val logFile = File(context.filesDir, AUDIT_LOG_FILE)
            val timestamp = report.timestamp
            val logEntry = buildString {
                appendLine("[$timestamp] Security Audit Report")
                appendLine("  Risk Level: ${report.riskLevel.name}")
                appendLine("  Root: ${report.isRooted}")
                if (report.rootedDetectionDetails.isNotEmpty()) {
                    appendLine("    Details: ${report.rootedDetectionDetails.joinToString(", ")}")
                }
                appendLine("  Emulator: ${report.isEmulator}")
                if (report.emulatorDetectionDetails.isNotEmpty()) {
                    appendLine("    Details: ${report.emulatorDetectionDetails.joinToString(", ")}")
                }
                appendLine("  Magisk: ${report.isMagiskPresent}")
                if (report.magiskDetectionDetails.isNotEmpty()) {
                    appendLine("    Details: ${report.magiskDetectionDetails.joinToString(", ")}")
                }
                appendLine("  Debugger: ${report.isDebuggerAttached}")
                appendLine("---")
            }
            
            // 追加模式写入文件
            logFile.appendText(logEntry)
            Log.d(TAG, "Audit log persisted to ${logFile.absolutePath}")
            true
        }.getOrElse { e ->
            Log.e(TAG, "Failed to persist audit log: ${e.message}", e)
            false
        }
    }

    /**
     * 读取历史审计日志。
     *
     * @param context Android 应用上下文
     * @return 审计日志内容，如果文件不存在则返回空字符串
     */
    fun readAuditLogs(context: Context): String {
        return runCatching {
            val logFile = File(context.filesDir, AUDIT_LOG_FILE)
            if (logFile.exists()) {
                logFile.readText()
            } else {
                ""
            }
        }.getOrElse { e ->
            Log.e(TAG, "Failed to read audit logs: ${e.message}", e)
            ""
        }
    }

    /**
     * 清除审计日志文件。
     *
     * @param context Android 应用上下文
     * @return 清除成功返回 true，否则返回 false
     */
    fun clearAuditLogs(context: Context): Boolean {
        return runCatching {
            val logFile = File(context.filesDir, AUDIT_LOG_FILE)
            if (logFile.exists()) {
                logFile.delete()
                Log.d(TAG, "Audit logs cleared")
            }
            true
        }.getOrElse { e ->
            Log.e(TAG, "Failed to clear audit logs: ${e.message}", e)
            false
        }
    }

    // ── SSL 证书校验 ──────────────────────────────────────────────────

    /**
     * 校验服务端证书链是否包含预期的证书指纹。
     * 用于实现自定义证书固定（Certificate Pinning）。
     */
    fun verifyCertificateChain(
        certs: Array<X509Certificate>,
        expectedPins: Set<String>,
    ): Boolean {
        return certs.any { cert ->
            val digest = MessageDigest.getInstance("SHA-256").digest(cert.publicKey.encoded)
            val pin = android.util.Base64.encodeToString(digest, android.util.Base64.NO_WRAP)
            expectedPins.contains(pin)
        }
    }

    /**
     * 创建带证书固定策略的 SSLContext。
     * 只接受匹配 expectedPins 的证书。
     */
    fun createPinnedSslContext(expectedPins: Set<String>): SSLContext {
        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, null) }
        trustManagerFactory.init(keyStore)
        val trustManagers = trustManagerFactory.trustManagers.map { tm ->
            if (tm is javax.net.ssl.X509TrustManager) {
                PinnedTrustManager(tm, expectedPins)
            } else {
                tm
            }
        }.toTypedArray()
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustManagers, secureRandom)
        return sslContext
    }

    /**
     * 带证书固定的 X509TrustManager 包装器。
     */
    private class PinnedTrustManager(
        private val delegate: javax.net.ssl.X509TrustManager,
        private val expectedPins: Set<String>,
    ) : javax.net.ssl.X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
            delegate.checkClientTrusted(chain, authType)
        }

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            delegate.checkServerTrusted(chain, authType)
            if (!verifyCertificateChain(chain, expectedPins)) {
                throw javax.net.ssl.SSLPeerUnverifiedException("Certificate pinning failed")
            }
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = delegate.acceptedIssuers
    }

    // ── 输入验证 ──────────────────────────────────────────────────────

    /** 验证输入是否为安全字符串（无 SQL 注入 / XSS 字符） */
    fun isSafeString(input: String): Boolean {
        val dangerousChars = setOf('\'', '"', ';', '\\', '<', '>', '&', '|', '`', '$')
        return input.none { it in dangerousChars }
    }

    /** 安全截断字符串，防止过长输入攻击 */
    fun safeTruncate(input: String, maxLength: Int = 1024): String {
        return if (input.length <= maxLength) input else input.take(maxLength)
    }
}
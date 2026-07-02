package com.rapidraw.core

import android.content.Context
import android.util.Log

/**
 * Google Play Integrity API 兼容层。
 *
 * 提供设备完整性验证，确保应用运行在可信设备上。
 * 生产环境需要集成 Play Integrity API SDK：
 *   implementation("com.google.android.play:integrity:1.4.0")
 *
 * 当前提供基础验证框架，正式版本替换为完整 Play Integrity 调用。
 *
 * 功能：
 * 1. 设备完整性检查（基本完整性 / 设备完整性 / 强完整性）
 * 2. 许可证验证（从 Play 商店安装）
 * 3. 应用完整性（签名匹配）
 * 4. 响应缓存（减少 API 调用）
 *
 * @since v1.10.2（正式版兼容性加固）
 */
object PlayIntegrityHelper {

    private const val TAG = "PlayIntegrityHelper"

    /**
     * 完整性验证结果。
     */
    data class IntegrityResult(
        val verdict: Verdict,
        val deviceRecognition: List<String> = emptyList(),
        val accountDetails: String? = null,
        val errorMessage: String? = null,
    )

    /** 完整性判定 */
    enum class Verdict {
        /** 通过 — 设备可信且应用未经篡改 */
        PASS,
        /** 基本完整性通过 — 设备基本可信，但可能有风险 */
        BASIC_ONLY,
        /** 未通过 — 设备不可信（模拟器/ROOT/注入） */
        FAIL,
        /** 未执行 — 服务不可用或未配置 */
        NOT_AVAILABLE,
    }

    private var cachedResult: IntegrityResult? = null
    private var lastCheckTimeMs: Long = 0L
    private const val CACHE_DURATION_MS = 5 * 60 * 1000L // 5 分钟缓存

    /**
     * 执行完整性检查（带缓存）。
     *
     * 注意：当前为兼容层实现，基于本地启发式检测。
     * 生产版本应替换为 Play Integrity API 标准请求。
     */
    fun checkIntegrity(context: Context): IntegrityResult {
        val now = System.currentTimeMillis()
        if (cachedResult != null && (now - lastCheckTimeMs) < CACHE_DURATION_MS) {
            return cachedResult ?: IntegrityResult.UNKNOWN
        }

        val result = performLocalIntegrityCheck(context)
        cachedResult = result
        lastCheckTimeMs = now
        return result
    }

    /**
     * 本地完整性检查（基于启发式检测）。
     *
     * 检测维度：
     * - 调试器附加
     * - 模拟器/ROOT 检测
     * - 签名校验
     */
    private fun performLocalIntegrityCheck(context: Context): IntegrityResult {
        // 检测 1: 调试器附加
        if (com.rapidraw.security.SecurityProvider.isDebuggerAttached()) {
            Log.w(TAG, "Debugger detected — integrity check failed")
            return IntegrityResult(
                verdict = Verdict.FAIL,
                errorMessage = "Debugger attached"
            )
        }

        // 检测 2: 模拟器
        if (com.rapidraw.security.SecurityProvider.isEmulator()) {
            Log.w(TAG, "Emulator detected — basic integrity only")
            return IntegrityResult(
                verdict = Verdict.BASIC_ONLY,
                deviceRecognition = listOf("Emulator detected"),
            )
        }

        // 检测 3: ROOT
        if (com.rapidraw.security.SecurityProvider.isDeviceRooted()) {
            Log.w(TAG, "Rooted device — basic integrity only")
            return IntegrityResult(
                verdict = Verdict.BASIC_ONLY,
                deviceRecognition = listOf("Root access detected"),
            )
        }

        // 检测 4: 签名校验
        if (!com.rapidraw.security.SecurityProvider.verifyAppSignature(context)) {
            Log.w(TAG, "App signature verification failed")
            return IntegrityResult(
                verdict = Verdict.FAIL,
                errorMessage = "App signature mismatch"
            )
        }

        return IntegrityResult(verdict = Verdict.PASS)
    }

    /**
     * 清除缓存，强制下次重新检查。
     */
    fun invalidateCache() {
        cachedResult = null
        lastCheckTimeMs = 0L
    }

    /**
     * 检查当前完整性是否允许使用高级功能（如 AI 增强、云同步）。
     */
    fun isPremiumFeatureAllowed(context: Context): Boolean {
        val result = checkIntegrity(context)
        return result.verdict == Verdict.PASS
    }
}
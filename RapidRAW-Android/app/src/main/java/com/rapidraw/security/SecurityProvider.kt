package com.rapidraw.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.security.MessageDigest
import java.security.SecureRandom
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
 * 6. 模拟器/ROOT 检测
 *
 * @since v1.10.0（正式版安全性加固）
 */
object SecurityProvider {

    private const val TAG = "SecurityProvider"
    private val secureRandom = SecureRandom()

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

    /** 检测是否运行在模拟器上 */
    fun isEmulator(): Boolean {
        return Build.FINGERPRINT.startsWith("generic")
            || Build.FINGERPRINT.startsWith("unknown")
            || Build.MODEL.contains("google_sdk")
            || Build.MODEL.contains("Emulator")
            || Build.MODEL.contains("Android SDK built for x86")
            || Build.MANUFACTURER.contains("Genymotion")
            || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
            || "google_sdk" == Build.PRODUCT
    }

    /** 检测是否被 ROOT（通过检查 su 二进制文件） */
    fun isDeviceRooted(): Boolean {
        return runCatching {
            val paths = arrayOf(
                "/system/app/Superuser.apk",
                "/sbin/su",
                "/system/bin/su",
                "/system/xbin/su",
                "/data/local/xbin/su",
                "/data/local/bin/su",
                "/system/sd/xbin/su",
                "/system/bin/failsafe/su",
                "/data/local/su",
            )
            paths.any { java.io.File(it).exists() }
        }.getOrDefault(false)
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
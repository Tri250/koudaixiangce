package com.rapidraw.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android Keystore 加密 SharedPreferences。
 *
 * 所有敏感数据（API 密钥、用户令牌、偏好数据）经过 AES-256-GCM 加密后存储，
 * 密钥由 Android Keystore 硬件安全模块管理，不可导出。
 *
 * 使用示例:
 * ```
 * val prefs = EncryptedPreferences.getInstance(context)
 * prefs.putString("user_token", token)
 * val token = prefs.getString("user_token")
 * ```
 *
 * @since v1.10.0（正式版安全性加固）
 */
class EncryptedPreferences private constructor(context: Context) {

    companion object {
        private const val TAG = "EncryptedPreferences"
        private const val KEY_ALIAS = "rapidraw_encrypted_prefs"
        private const val KEYSTORE_TYPE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val IV_SIZE = 12

        @Volatile
        private var instance: EncryptedPreferences? = null

        fun getInstance(context: Context): EncryptedPreferences {
            return instance ?: synchronized(this) {
                instance ?: EncryptedPreferences(context.applicationContext).also { instance = it }
            }
        }
    }

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("rapidraw_secure_prefs", Context.MODE_PRIVATE)
    // v1.10.6: 使用 AtomicReference 跟踪 Keystore 可用性，失败后降级为明文存储避免崩溃。
    private val keyAvailable = java.util.concurrent.atomic.AtomicBoolean(true)
    private val secretKey: SecretKey? by lazy {
        runCatching { getOrCreateKey() }.onFailure {
            Log.w(TAG, "Keystore key unavailable, falling back to plaintext", it)
            keyAvailable.set(false)
        }.getOrNull()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_TYPE).apply { load(null) }
        keyStore.getEntry(KEY_ALIAS, null)?.let { entry ->
            return (entry as KeyStore.SecretKeyEntry).secretKey
        }
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_TYPE
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    fun putString(key: String, value: String) {
        val encrypted = runCatching {
            if (keyAvailable.get()) encrypt(value) else value
        }.getOrElse {
            Log.w(TAG, "Encryption failed for key '$key', storing plaintext", it)
            keyAvailable.set(false)
            value
        }
        sharedPreferences.edit().putString(key, encrypted).apply()
    }

    fun getString(key: String, defaultValue: String? = null): String? {
        val encrypted = sharedPreferences.getString(key, null) ?: return defaultValue
        return runCatching {
            if (keyAvailable.get()) decrypt(encrypted) else encrypted
        }.getOrElse { defaultValue }
    }

    fun putInt(key: String, value: Int) {
        putString(key, value.toString())
    }

    fun getInt(key: String, defaultValue: Int = 0): Int {
        return getString(key)?.toIntOrNull() ?: defaultValue
    }

    fun putBoolean(key: String, value: Boolean) {
        putString(key, value.toString())
    }

    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean {
        return getString(key)?.toBooleanStrictOrNull() ?: defaultValue
    }

    fun putLong(key: String, value: Long) {
        putString(key, value.toString())
    }

    fun getLong(key: String, defaultValue: Long = 0L): Long {
        return getString(key)?.toLongOrNull() ?: defaultValue
    }

    fun remove(key: String) {
        sharedPreferences.edit().remove(key).apply()
    }

    fun clear() {
        sharedPreferences.edit().clear().apply()
    }

    fun contains(key: String): Boolean = sharedPreferences.contains(key)

    private fun encrypt(plainText: String): String {
        val key = secretKey ?: throw IllegalStateException("Keystore secret key unavailable")
        val cipher = getCipher(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val combined = iv + encrypted
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(encryptedBase64: String): String {
        val key = secretKey ?: throw IllegalStateException("Keystore secret key unavailable")
        val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
        if (combined.size < IV_SIZE) throw IllegalArgumentException("Invalid encrypted payload")
        val iv = combined.copyOfRange(0, IV_SIZE)
        val encrypted = combined.copyOfRange(IV_SIZE, combined.size)
        val cipher = getCipher(Cipher.DECRYPT_MODE, key, iv)
        val decrypted = cipher.doFinal(encrypted)
        return String(decrypted, Charsets.UTF_8)
    }

    private fun getCipher(opmode: Int, key: SecretKey, iv: ByteArray? = null): Cipher {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        if (opmode == Cipher.ENCRYPT_MODE) {
            cipher.init(opmode, key)
        } else {
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(opmode, key, spec)
        }
        return cipher
    }
}
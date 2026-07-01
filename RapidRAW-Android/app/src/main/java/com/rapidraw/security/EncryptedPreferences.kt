package com.rapidraw.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
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
    private val secretKey: SecretKey by lazy { getOrCreateKey() }

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
        val encrypted = encrypt(value)
        sharedPreferences.edit().putString(key, encrypted).apply()
    }

    fun getString(key: String, defaultValue: String? = null): String? {
        val encrypted = sharedPreferences.getString(key, null) ?: return defaultValue
        return runCatching { decrypt(encrypted) }.getOrElse { defaultValue }
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
        val cipher = getCipher(Cipher.ENCRYPT_MODE)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val combined = iv + encrypted
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(encryptedBase64: String): String {
        val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
        val iv = combined.copyOfRange(0, IV_SIZE)
        val encrypted = combined.copyOfRange(IV_SIZE, combined.size)
        val cipher = getCipher(Cipher.DECRYPT_MODE, iv)
        val decrypted = cipher.doFinal(encrypted)
        return String(decrypted, Charsets.UTF_8)
    }

    private fun getCipher(opmode: Int, iv: ByteArray? = null): Cipher {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        if (opmode == Cipher.ENCRYPT_MODE) {
            cipher.init(opmode, secretKey)
        } else {
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(opmode, secretKey, spec)
        }
        return cipher
    }
}
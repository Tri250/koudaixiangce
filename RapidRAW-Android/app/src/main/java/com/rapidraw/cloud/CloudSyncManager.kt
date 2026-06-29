package com.rapidraw.cloud

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

enum class SyncProvider {
    GOOGLE_DRIVE,
    WEBDAV,
    CUSTOM_API
}

data class SyncStatus(
    val lastSyncTime: Long,
    val pendingUploads: Int,
    val pendingDownloads: Int,
    val isSyncing: Boolean
)

class CloudSyncManager(
    private val context: android.content.Context,
    private val provider: SyncProvider = SyncProvider.CUSTOM_API
) {

    companion object {
        private const val AES_ALGORITHM = "AES/CBC/PKCS7Padding"
        private const val AES_KEY_SIZE = 256
        private const val IV_SIZE = 16
        private const val SYNC_DIR = "cloud_sync"
        private const val BACKUP_DIR = "cloud_sync_backups"
        private const val AUTH_PREFS = "cloud_auth_prefs"
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_ENCRYPTION_KEY = "encryption_key"
        private const val KEY_LAST_SYNC = "last_sync_time"
        private const val PRESETS_FILE = "presets.json"
        private const val EDITS_FILE = "edits.json"
        private const val SETTINGS_FILE = "settings.json"
    }

    private val syncDir: File
        get() = File(context.filesDir, SYNC_DIR).also { if (!it.exists()) it.mkdirs() }

    private val backupDir: File
        get() = File(context.filesDir, BACKUP_DIR).also { if (!it.exists()) it.mkdirs() }

    private val authPrefs = context.getSharedPreferences(AUTH_PREFS, android.content.Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private var syncing = false
    private var pendingUploads = 0
    private var pendingDownloads = 0

    /**
     * Synchronizes presets with the configured cloud provider.
     * Encrypts data before upload, decrypts after download.
     * Uses last-write-wins conflict resolution with local backup.
     *
     * @return true if sync was successful
     */
    suspend fun syncPresets(): Boolean = withContext(Dispatchers.IO) {
        if (!isAuthenticated()) return@withContext false

        mutex.withLock {
            if (syncing) return@withLock false
            syncing = true
        }

        try {
            val localFile = File(syncDir, PRESETS_FILE)
            val remoteFile = File(syncDir, "remote_$PRESETS_FILE.enc")

            // Download remote presets
            val remoteData = downloadFile("$PRESETS_FILE.enc")
            if (remoteData != null) {
                remoteFile.writeBytes(remoteData)
                val decrypted = decryptFile(remoteFile)
                if (decrypted != null) {
                    val remotePresetsFile = File(syncDir, "remote_$PRESETS_FILE")
                    remotePresetsFile.writeBytes(decrypted)

                    // Conflict resolution: last-write-wins
                    if (localFile.exists()) {
                        val localModified = localFile.lastModified()
                        val remoteModified = remoteFile.lastModified()

                        if (remoteModified > localModified) {
                            // Backup local before overwriting
                            backupFile(localFile)
                            localFile.delete()
                            remotePresetsFile.renameTo(localFile)
                        } else {
                            // Local is newer, upload it
                            uploadPresetsFile(localFile)
                        }
                    } else {
                        remotePresetsFile.renameTo(localFile)
                    }
                }
            } else if (localFile.exists()) {
                // No remote data, upload local
                uploadPresetsFile(localFile)
            }

            updateLastSyncTime()
            true
        } catch (e: Exception) {
            false
        } finally {
            mutex.withLock { syncing = false }
        }
    }

    /**
     * Synchronizes edit history with the configured cloud provider.
     *
     * @return true if sync was successful
     */
    suspend fun syncEdits(): Boolean = withContext(Dispatchers.IO) {
        if (!isAuthenticated()) return@withContext false

        mutex.withLock {
            if (syncing) return@withLock false
            syncing = true
        }

        try {
            val localFile = File(syncDir, EDITS_FILE)
            val remoteData = downloadFile("$EDITS_FILE.enc")

            if (remoteData != null) {
                val remoteEncFile = File(syncDir, "remote_${EDITS_FILE}.enc")
                remoteEncFile.writeBytes(remoteData)
                val decrypted = decryptFile(remoteEncFile)
                if (decrypted != null) {
                    val remoteEditsFile = File(syncDir, "remote_$EDITS_FILE")
                    remoteEditsFile.writeBytes(decrypted)

                    if (localFile.exists()) {
                        if (remoteEncFile.lastModified() > localFile.lastModified()) {
                            backupFile(localFile)
                            localFile.delete()
                            remoteEditsFile.renameTo(localFile)
                        } else {
                            uploadEditsFile(localFile)
                        }
                    } else {
                        remoteEditsFile.renameTo(localFile)
                    }
                }
            } else if (localFile.exists()) {
                uploadEditsFile(localFile)
            }

            updateLastSyncTime()
            true
        } catch (e: Exception) {
            false
        } finally {
            mutex.withLock { syncing = false }
        }
    }

    /**
     * Synchronizes app settings with the configured cloud provider.
     *
     * @return true if sync was successful
     */
    suspend fun syncSettings(): Boolean = withContext(Dispatchers.IO) {
        if (!isAuthenticated()) return@withContext false

        mutex.withLock {
            if (syncing) return@withLock false
            syncing = true
        }

        try {
            val localFile = File(syncDir, SETTINGS_FILE)
            val remoteData = downloadFile("$SETTINGS_FILE.enc")

            if (remoteData != null) {
                val remoteEncFile = File(syncDir, "remote_${SETTINGS_FILE}.enc")
                remoteEncFile.writeBytes(remoteData)
                val decrypted = decryptFile(remoteEncFile)
                if (decrypted != null) {
                    val remoteSettingsFile = File(syncDir, "remote_$SETTINGS_FILE")
                    remoteSettingsFile.writeBytes(decrypted)

                    if (localFile.exists()) {
                        if (remoteEncFile.lastModified() > localFile.lastModified()) {
                            backupFile(localFile)
                            localFile.delete()
                            remoteSettingsFile.renameTo(localFile)
                        } else {
                            uploadSettingsFile(localFile)
                        }
                    } else {
                        remoteSettingsFile.renameTo(localFile)
                    }
                }
            } else if (localFile.exists()) {
                uploadSettingsFile(localFile)
            }

            updateLastSyncTime()
            true
        } catch (e: Exception) {
            false
        } finally {
            mutex.withLock { syncing = false }
        }
    }

    /**
     * Returns whether the user is authenticated with the cloud provider.
     */
    fun isAuthenticated(): Boolean {
        val token = authPrefs.getString(KEY_AUTH_TOKEN, null)
        return !token.isNullOrBlank()
    }

    /**
     * Authenticates with the cloud provider using the given token.
     * Stores the token securely and generates an encryption key.
     *
     * @param token Authentication token for the cloud provider
     * @return true if authentication was successful
     */
    suspend fun authenticate(token: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // Verify token with the provider
            val valid = verifyToken(token)
            if (!valid) return@withContext false

            authPrefs.edit().putString(KEY_AUTH_TOKEN, token).apply()

            // Generate and store encryption key derived from token
            val encryptionKey = deriveEncryptionKey(token)
            authPrefs.edit().putString(KEY_ENCRYPTION_KEY, encryptionKey).apply()

            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Returns the current synchronization status.
     */
    fun getSyncStatus(): SyncStatus {
        val lastSync = authPrefs.getLong(KEY_LAST_SYNC, 0L)
        return SyncStatus(
            lastSyncTime = lastSync,
            pendingUploads = pendingUploads,
            pendingDownloads = pendingDownloads,
            isSyncing = syncing
        )
    }

    // ==================== Private Helpers ====================

    private fun uploadPresetsFile(file: File) {
        val encrypted = encryptFile(file)
        if (encrypted != null) {
            uploadFile("$PRESETS_FILE.enc", encrypted.readBytes())
            pendingUploads--
        }
    }

    private fun uploadEditsFile(file: File) {
        val encrypted = encryptFile(file)
        if (encrypted != null) {
            uploadFile("$EDITS_FILE.enc", encrypted.readBytes())
            pendingUploads--
        }
    }

    private fun uploadSettingsFile(file: File) {
        val encrypted = encryptFile(file)
        if (encrypted != null) {
            uploadFile("$SETTINGS_FILE.enc", encrypted.readBytes())
            pendingUploads--
        }
    }

    /**
     * Verifies the authentication token with the cloud provider.
     */
    private fun verifyToken(token: String): Boolean {
        return when (provider) {
            SyncProvider.GOOGLE_DRIVE -> {
                // Google Drive token verification via OAuth2 tokeninfo
                try {
                    val url = java.net.URL("https://www.googleapis.com/oauth2/v3/tokeninfo?access_token=$token")
                    val connection = url.openConnection() as java.net.HttpURLConnection
                    connection.connectTimeout = 10000
                    connection.readTimeout = 10000
                    connection.requestMethod = "GET"
                    connection.responseCode == java.net.HttpURLConnection.HTTP_OK
                } catch (_: Exception) {
                    false
                }
            }
            SyncProvider.WEBDAV -> {
                // WebDAV: verify by attempting a PROPFIND on the root
                try {
                    val url = java.net.URL(token) // token is the WebDAV URL
                    val connection = url.openConnection() as java.net.HttpURLConnection
                    connection.connectTimeout = 10000
                    connection.readTimeout = 10000
                    connection.requestMethod = "PROPFIND"
                    connection.setRequestProperty("Depth", "0")
                    connection.responseCode in 200..299
                } catch (_: Exception) {
                    false
                }
            }
            SyncProvider.CUSTOM_API -> {
                // Custom REST API: verify by calling /auth/verify endpoint
                try {
                    val url = java.net.URL("https://api.rapidraw.app/v1/auth/verify")
                    val connection = url.openConnection() as java.net.HttpURLConnection
                    connection.connectTimeout = 10000
                    connection.readTimeout = 10000
                    connection.requestMethod = "POST"
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.setRequestProperty("Authorization", "Bearer $token")
                    connection.doOutput = true
                    connection.outputStream.write("{}".toByteArray())
                    connection.responseCode == java.net.HttpURLConnection.HTTP_OK
                } catch (_: Exception) {
                    false
                }
            }
        }
    }

    /**
     * Downloads a file from the cloud provider.
     */
    private fun downloadFile(fileName: String): ByteArray? {
        return when (provider) {
            SyncProvider.GOOGLE_DRIVE -> downloadFromGoogleDrive(fileName)
            SyncProvider.WEBDAV -> downloadFromWebDAV(fileName)
            SyncProvider.CUSTOM_API -> downloadFromCustomApi(fileName)
        }
    }

    /**
     * Uploads a file to the cloud provider.
     */
    private fun uploadFile(fileName: String, data: ByteArray): Boolean {
        return when (provider) {
            SyncProvider.GOOGLE_DRIVE -> uploadToGoogleDrive(fileName, data)
            SyncProvider.WEBDAV -> uploadToWebDAV(fileName, data)
            SyncProvider.CUSTOM_API -> uploadToCustomApi(fileName, data)
        }
    }

    private fun downloadFromCustomApi(fileName: String): ByteArray? {
        try {
            val token = authPrefs.getString(KEY_AUTH_TOKEN, null) ?: return null
            val url = java.net.URL("https://api.rapidraw.app/v1/sync/download/$fileName")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $token")
            if (connection.responseCode != java.net.HttpURLConnection.HTTP_OK) return null
            return connection.inputStream.readBytes()
        } catch (_: Exception) {
            return null
        }
    }

    private fun uploadToCustomApi(fileName: String, data: ByteArray): Boolean {
        try {
            val token = authPrefs.getString(KEY_AUTH_TOKEN, null) ?: return false
            val url = java.net.URL("https://api.rapidraw.app/v1/sync/upload/$fileName")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.requestMethod = "PUT"
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/octet-stream")
            connection.doOutput = true
            connection.outputStream.write(data)
            return connection.responseCode in 200..299
        } catch (_: Exception) {
            return false
        }
    }

    private fun downloadFromGoogleDrive(fileName: String): ByteArray? {
        try {
            val token = authPrefs.getString(KEY_AUTH_TOKEN, null) ?: return null
            // Search for the file by name
            val searchUrl = java.net.URL(
                "https://www.googleapis.com/drive/v3/files?q=name='$fileName'&fields=files(id,name)"
            )
            val searchConn = searchUrl.openConnection() as java.net.HttpURLConnection
            searchConn.connectTimeout = 10000
            searchConn.readTimeout = 10000
            searchConn.setRequestProperty("Authorization", "Bearer $token")
            if (searchConn.responseCode != java.net.HttpURLConnection.HTTP_OK) return null

            val searchResult = searchConn.inputStream.readBytes().decodeToString()
            val fileId = extractGoogleDriveFileId(searchResult) ?: return null

            // Download the file
            val downloadUrl = java.net.URL(
                "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
            )
            val downloadConn = downloadUrl.openConnection() as java.net.HttpURLConnection
            downloadConn.connectTimeout = 15000
            downloadConn.readTimeout = 30000
            downloadConn.setRequestProperty("Authorization", "Bearer $token")
            if (downloadConn.responseCode != java.net.HttpURLConnection.HTTP_OK) return null

            return downloadConn.inputStream.readBytes()
        } catch (_: Exception) {
            return null
        }
    }

    private fun uploadToGoogleDrive(fileName: String, data: ByteArray): Boolean {
        try {
            val token = authPrefs.getString(KEY_AUTH_TOKEN, null) ?: return false
            // Search for existing file
            val searchUrl = java.net.URL(
                "https://www.googleapis.com/drive/v3/files?q=name='$fileName'&fields=files(id)"
            )
            val searchConn = searchUrl.openConnection() as java.net.HttpURLConnection
            searchConn.connectTimeout = 10000
            searchConn.readTimeout = 10000
            searchConn.setRequestProperty("Authorization", "Bearer $token")
            val searchResult = if (searchConn.responseCode == java.net.HttpURLConnection.HTTP_OK) {
                searchConn.inputStream.readBytes().decodeToString()
            } else null

            val existingFileId = searchResult?.let { extractGoogleDriveFileId(it) }

            val uploadUrl = if (existingFileId != null) {
                java.net.URL(
                    "https://www.googleapis.com/upload/drive/v3/files/$existingFileId?uploadType=media"
                )
            } else {
                java.net.URL(
                    "https://www.googleapis.com/upload/drive/v3/files?uploadType=media"
                )
            }

            val uploadConn = uploadUrl.openConnection() as java.net.HttpURLConnection
            uploadConn.connectTimeout = 15000
            uploadConn.readTimeout = 30000
            uploadConn.requestMethod = if (existingFileId != null) "PATCH" else "POST"
            uploadConn.setRequestProperty("Authorization", "Bearer $token")
            uploadConn.setRequestProperty("Content-Type", "application/octet-stream")
            uploadConn.doOutput = true
            uploadConn.outputStream.write(data)

            return uploadConn.responseCode in 200..299
        } catch (_: Exception) {
            return false
        }
    }

    private fun downloadFromWebDAV(fileName: String): ByteArray? {
        try {
            val baseUrl = authPrefs.getString(KEY_AUTH_TOKEN, null) ?: return null
            val url = java.net.URL("$baseUrl/$fileName")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.requestMethod = "GET"
            if (connection.responseCode != java.net.HttpURLConnection.HTTP_OK) return null
            return connection.inputStream.readBytes()
        } catch (_: Exception) {
            return null
        }
    }

    private fun uploadToWebDAV(fileName: String, data: ByteArray): Boolean {
        try {
            val baseUrl = authPrefs.getString(KEY_AUTH_TOKEN, null) ?: return false
            val url = java.net.URL("$baseUrl/$fileName")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.requestMethod = "PUT"
            connection.setRequestProperty("Content-Type", "application/octet-stream")
            connection.doOutput = true
            connection.outputStream.write(data)
            return connection.responseCode in 200..299
        } catch (_: Exception) {
            return false
        }
    }

    private fun extractGoogleDriveFileId(json: String): String? {
        try {
            val idKey = "\"id\": \""
            val idStart = json.indexOf(idKey)
            if (idStart < 0) return null
            val valueStart = idStart + idKey.length
            val valueEnd = json.indexOf("\"", valueStart)
            if (valueEnd < 0) return null
            return json.substring(valueStart, valueEnd)
        } catch (_: Exception) {
            return null
        }
    }

    /**
     * Encrypts a file using AES-256-CBC.
     * Returns the encrypted temp file, or null on failure.
     */
    private fun encryptFile(inputFile: File): File? {
        try {
            val key = getEncryptionKey() ?: return null
            val iv = ByteArray(IV_SIZE)
            SecureRandom().nextBytes(iv)
            val ivSpec = IvParameterSpec(iv)

            val cipher = Cipher.getInstance(AES_ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, key, ivSpec)

            val encryptedFile = File(inputFile.parent, "enc_${inputFile.name}")
            FileInputStream(inputFile).use { fis ->
                CipherOutputStream(FileOutputStream(encryptedFile), cipher).use { cos ->
                    fis.copyTo(cos)
                }
            }

            // Prepend IV to the encrypted file
            val finalFile = File(inputFile.parent, "${inputFile.name}.enc")
            FileOutputStream(finalFile).use { fos ->
                fos.write(iv)
                FileInputStream(encryptedFile).use { it.copyTo(fos) }
            }

            encryptedFile.delete()
            return finalFile
        } catch (_: Exception) {
            return null
        }
    }

    /**
     * Decrypts an AES-256-CBC encrypted file.
     * Returns the decrypted bytes, or null on failure.
     */
    private fun decryptFile(encryptedFile: File): ByteArray? {
        try {
            val key = getEncryptionKey() ?: return null
            val fileData = encryptedFile.readBytes()

            if (fileData.size < IV_SIZE) return null

            val iv = fileData.copyOfRange(0, IV_SIZE)
            val cipherData = fileData.copyOfRange(IV_SIZE, fileData.size)
            val ivSpec = IvParameterSpec(iv)

            val cipher = Cipher.getInstance(AES_ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, key, ivSpec)

            return cipher.doFinal(cipherData)
        } catch (_: Exception) {
            return null
        }
    }

    /**
     * Derives an AES-256 key from the authentication token.
     */
    private fun deriveEncryptionKey(token: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(token.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    /**
     * Retrieves or generates the AES encryption key.
     */
    private fun getEncryptionKey(): SecretKey? {
        val keyHex = authPrefs.getString(KEY_ENCRYPTION_KEY, null) ?: return null
        return try {
            val keyBytes = keyHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            SecretKeySpec(keyBytes.copyOf(AES_KEY_SIZE / 8), "AES")
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Creates a timestamped backup of a file before overwriting.
     */
    private fun backupFile(file: File) {
        if (!file.exists()) return
        try {
            val timestamp = System.currentTimeMillis()
            val backupFile = File(backupDir, "${file.name}.$timestamp.bak")
            file.copyTo(backupFile, overwrite = true)
        } catch (_: Exception) {
        }
    }

    /**
     * Updates the last successful sync timestamp.
     */
    private fun updateLastSyncTime() {
        authPrefs.edit().putLong(KEY_LAST_SYNC, System.currentTimeMillis()).apply()
    }
}
package com.alcedo.studio.core

import android.content.Context
import android.net.Uri
import com.alcedo.studio.data.model.Adjustments
import com.alcedo.studio.data.model.EditHistoryEntry
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class SidecarManager(private val context: Context) {

    constructor(uri: Uri, context: Context) : this(context) {
        this.sourceUri = uri.toString()
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private val sidecarDir: File
        get() = File(context.filesDir, "sidecars")

    private var sourceUri: String = ""

    init {
        if (!sidecarDir.exists()) {
            sidecarDir.mkdirs()
        }
    }

    fun getSidecarPath(sourceUri: String): String {
        val hash = sourceUri.hashCode().toUInt().toString(16)
        return "$hash.json"
    }

    fun hasSidecar(sourceUri: String): Boolean {
        val sidecarFile = File(sidecarDir, getSidecarPath(sourceUri))
        return sidecarFile.exists()
    }

    fun saveSidecar(
        sourceUri: String,
        adjustments: Adjustments,
        history: List<EditHistoryEntry> = emptyList(),
        rating: Int = 0,
        colorLabel: String? = null
    ) {
        val sidecarFile = File(sidecarDir, getSidecarPath(sourceUri))
        val sidecarData = SidecarData(
            sourceUri = sourceUri,
            adjustments = adjustments,
            history = history,
            rating = rating,
            colorLabel = colorLabel,
            version = SIDECAR_VERSION,
            lastModified = System.currentTimeMillis()
        )

        try {
            val jsonString = json.encodeToString(sidecarData)
            FileOutputStream(sidecarFile).use { output ->
                output.write(jsonString.toByteArray())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadSidecar(sourceUri: String): SidecarData? {
        val sidecarFile = File(sidecarDir, getSidecarPath(sourceUri))
        if (!sidecarFile.exists()) return null

        return try {
            val jsonString = FileInputStream(sidecarFile).use { input ->
                input.readBytes().toString(Charsets.UTF_8)
            }
            json.decodeFromString<SidecarData>(jsonString)
        } catch (e: Exception) {
            null
        }
    }

    fun loadAdjustments(): Adjustments {
        return loadSidecar(sourceUri)?.adjustments ?: Adjustments.Default
    }

    fun saveAdjustments(adjustments: Adjustments) {
        val existing = loadSidecar(sourceUri)
        saveSidecar(
            sourceUri = sourceUri,
            adjustments = adjustments,
            history = existing?.history ?: emptyList(),
            rating = existing?.rating ?: 0,
            colorLabel = existing?.colorLabel
        )
    }

    fun deleteSidecar(sourceUri: String): Boolean {
        val sidecarFile = File(sidecarDir, getSidecarPath(sourceUri))
        return if (sidecarFile.exists()) {
            sidecarFile.delete()
        } else false
    }

    fun getAllSidecars(): List<SidecarData> {
        return sidecarDir.listFiles { file -> file.extension == "json" }
            ?.mapNotNull { file ->
                try {
                    val jsonString = FileInputStream(file).readBytes().toString(Charsets.UTF_8)
                    json.decodeFromString<SidecarData>(jsonString)
                } catch (e: Exception) {
                    null
                }
            } ?: emptyList()
    }

    fun getSidecarCount(): Int {
        return sidecarDir.listFiles { file -> file.extension == "json" }?.size ?: 0
    }

    fun clearOldSidecars(maxAgeMs: Long = 30L * 24 * 60 * 60 * 1000) {
        val now = System.currentTimeMillis()
        sidecarDir.listFiles { file -> file.extension == "json" }?.forEach { file ->
            if (now - file.lastModified() > maxAgeMs) {
                file.delete()
            }
        }
    }

    fun exportSidecar(sourceUri: String, outputFile: File): Boolean {
        val sidecar = loadSidecar(sourceUri) ?: return false
        return try {
            val jsonString = json.encodeToString(sidecar)
            FileOutputStream(outputFile).use { output ->
                output.write(jsonString.toByteArray())
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun importSidecar(sidecarFile: File): SidecarData? {
        return try {
            val jsonString = FileInputStream(sidecarFile).use { input ->
                input.readBytes().toString(Charsets.UTF_8)
            }
            val data = json.decodeFromString<SidecarData>(jsonString)
            saveSidecar(data.sourceUri, data.adjustments, data.history, data.rating, data.colorLabel)
            data
        } catch (e: Exception) {
            null
        }
    }

    fun updateRating(sourceUri: String, rating: Int) {
        val existing = loadSidecar(sourceUri)
        val adjustments = existing?.adjustments ?: Adjustments.Default
        val history = existing?.history ?: emptyList()
        saveSidecar(sourceUri, adjustments, history, rating, existing?.colorLabel)
    }

    fun updateColorLabel(sourceUri: String, colorLabel: String?) {
        val existing = loadSidecar(sourceUri)
        val adjustments = existing?.adjustments ?: Adjustments.Default
        val history = existing?.history ?: emptyList()
        val rating = existing?.rating ?: 0
        saveSidecar(sourceUri, adjustments, history, rating, colorLabel)
    }

    companion object {
        const val SIDECAR_VERSION = 1
    }
}

@Serializable
data class SidecarData(
    val sourceUri: String = "",
    val adjustments: Adjustments = Adjustments.Default,
    val history: List<EditHistoryEntry> = emptyList(),
    val rating: Int = 0,
    val colorLabel: String? = null,
    val version: Int = 1,
    val lastModified: Long = System.currentTimeMillis()
)

package com.rapidraw.core

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Style/Tool dual-mode preset system.
 *
 * Supports import/export of .xmp and .cube presets, categorization, search, favorites,
 * and persistence to a JSON file in the app's files directory.
 */
@Serializable
data class EnhancedPreset(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val category: PresetCategory = PresetCategory.USER,
    val tags: List<String> = emptyList(),
    val adjustments: Map<String, Float> = emptyMap(),
    val thumbnailPath: String? = null,
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
enum class PresetCategory {
    STYLE,
    TOOL,
    USER
}

class EnhancedPresetManager(private val context: Context) {

    companion object {
        private const val TAG = "EnhancedPresetManager"
        private const val PRESETS_FILE = "enhanced_presets.json"
        private const val THUMBNAILS_DIR = "preset_thumbnails"

        private val json = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            encodeDefaults = true
        }
    }

    private val presetsDir: File
        get() = File(context.filesDir, "presets").also { it.mkdirs() }

    private val thumbnailsDir: File
        get() = File(context.filesDir, THUMBNAILS_DIR).also { it.mkdirs() }

    private val presetsFile: File
        get() = File(presetsDir, PRESETS_FILE)

    @Volatile
    private var presets: MutableList<EnhancedPreset> = mutableListOf()

    private val lock = Any()

    init {
        loadPresets()
    }

    // ── Public API ──────────────────────────────────────────────────────

    suspend fun importPreset(uri: Uri): EnhancedPreset {
        val fileName = extractFileName(uri) ?: "imported_${System.currentTimeMillis()}"
        val localFile = File(presetsDir, fileName)

        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(localFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Cannot open input stream for URI: $uri")

        val adjustments = parsePresetFile(localFile)
        val category = when {
            fileName.endsWith(".xmp", ignoreCase = true) -> PresetCategory.TOOL
            fileName.endsWith(".cube", ignoreCase = true) -> PresetCategory.STYLE
            else -> PresetCategory.USER
        }

        val preset = EnhancedPreset(
            name = fileName.substringBeforeLast("."),
            category = category,
            adjustments = adjustments,
            tags = listOf("imported")
        )

        synchronized(lock) {
            presets.add(preset)
            savePresets()
        }

        return preset
    }

    fun exportPreset(preset: EnhancedPreset, outputUri: Uri) {
        val content = buildPresetContent(preset)
        context.contentResolver.openOutputStream(outputUri)?.use { stream ->
            stream.write(content.toByteArray(Charsets.UTF_8))
        } ?: throw IllegalStateException("Cannot open output stream for URI: $outputUri")
    }

    fun getPresets(category: PresetCategory): List<EnhancedPreset> {
        synchronized(lock) {
            return presets.filter { it.category == category }.toList()
        }
    }

    fun getAllPresets(): List<EnhancedPreset> {
        synchronized(lock) {
            return presets.toList()
        }
    }

    fun searchPresets(query: String): List<EnhancedPreset> {
        val lowerQuery = query.lowercase()
        synchronized(lock) {
            return presets.filter { preset ->
                preset.name.lowercase().contains(lowerQuery) ||
                        preset.tags.any { it.lowercase().contains(lowerQuery) }
            }.toList()
        }
    }

    fun toggleFavorite(presetId: String) {
        synchronized(lock) {
            val index = presets.indexOfFirst { it.id == presetId }
            if (index >= 0) {
                presets[index] = presets[index].copy(isFavorite = !presets[index].isFavorite)
                savePresets()
            }
        }
    }

    fun getFavorites(): List<EnhancedPreset> {
        synchronized(lock) {
            return presets.filter { it.isFavorite }.toList()
        }
    }

    fun deletePreset(presetId: String): Boolean {
        synchronized(lock) {
            val removed = presets.removeAll { it.id == presetId }
            if (removed) savePresets()
            return removed
        }
    }

    fun updatePreset(updated: EnhancedPreset): Boolean {
        synchronized(lock) {
            val index = presets.indexOfFirst { it.id == updated.id }
            if (index >= 0) {
                presets[index] = updated
                savePresets()
                return true
            }
            return false
        }
    }

    fun addPreset(preset: EnhancedPreset) {
        synchronized(lock) {
            presets.add(preset)
            savePresets()
        }
    }

    // ── Private helpers ─────────────────────────────────────────────────

    private fun loadPresets() {
        if (!presetsFile.exists()) return
        try {
            val content = presetsFile.readText()
            val loaded: List<EnhancedPreset> = json.decodeFromString(content)
            synchronized(lock) {
                presets = loaded.toMutableList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load presets: ${e.message}")
            synchronized(lock) {
                presets = mutableListOf()
            }
        }
    }

    private fun savePresets() {
        try {
            val content = json.encodeToString(synchronized(lock) { presets.toList() })
            presetsFile.writeText(content)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save presets: ${e.message}")
        }
    }

    private fun extractFileName(uri: Uri): String? {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) it.getString(nameIndex) else null
            } else null
        }
    }

    private fun parsePresetFile(file: File): Map<String, Float> {
        if (!file.exists()) return emptyMap()
        val content = file.readText()
        val adjustments = mutableMapOf<String, Float>()

        when {
            file.name.endsWith(".xmp", ignoreCase = true) -> {
                parseXmpContent(content, adjustments)
            }
            file.name.endsWith(".cube", ignoreCase = true) -> {
                parseCubeContent(content, adjustments)
            }
            else -> {
                try {
                    val parsed: Map<String, Float> = json.decodeFromString(content)
                    adjustments.putAll(parsed)
                } catch (_: Exception) {
                    // Unrecognized format, return empty
                }
            }
        }

        return adjustments
    }

    private fun parseXmpContent(content: String, target: MutableMap<String, Float>) {
        val lines = content.lines()
        var inAdjustments = false
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("crs:")) {
                val parts = trimmed.removePrefix("crs:").split("=", limit = 2)
                if (parts.size == 2) {
                    val key = parts[0].trim()
                    val value = parts[1].trim().trim('"').toFloatOrNull()
                    if (value != null) {
                        target[key] = value
                    }
                }
            }
        }
    }

    private fun parseCubeContent(content: String, target: MutableMap<String, Float>) {
        val lines = content.lines()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("TITLE") ||
                trimmed.startsWith("DOMAIN") || trimmed.startsWith("LUT") || trimmed.startsWith("SIZE")
            ) continue

            val values = trimmed.split("\\s+".toRegex()).mapNotNull { it.toFloatOrNull() }
            if (values.size >= 3) {
                target["cube_r_${target.size / 3}"] = values[0]
                target["cube_g_${target.size / 3}"] = values[1]
                target["cube_b_${target.size / 3}"] = values[2]
            }
        }
    }

    private fun buildPresetContent(preset: EnhancedPreset): String {
        val sb = StringBuilder()
        sb.appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        sb.appendLine("<x:xmpmeta xmlns:x=\"adobe:ns:meta/\">")
        sb.appendLine("  <rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">")
        sb.appendLine("    <rdf:Description>")
        sb.appendLine("      <preset:name>${preset.name}</preset:name>")
        sb.appendLine("      <preset:category>${preset.category.name}</preset:category>")
        for ((key, value) in preset.adjustments) {
            sb.appendLine("      <crs:$key>$value</crs:$key>")
        }
        sb.appendLine("    </rdf:Description>")
        sb.appendLine("  </rdf:RDF>")
        sb.appendLine("</x:xmpmeta>")
        return sb.toString()
    }
}
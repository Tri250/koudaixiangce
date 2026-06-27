package com.rapidraw.core

import android.content.Context
import android.net.Uri
import com.rapidraw.data.model.Adjustments
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Non-destructive sidecar workflow manager.
 * Persists edit parameters to `.rapidraw` JSON sidecar files alongside original images.
 */
object SidecarManager {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Save adjustments to a sidecar file next to the original image.
     * For MediaStore URIs, stores in app-private sidecar directory keyed by URI hash.
     */
    fun saveSidecar(context: Context, imageUri: Uri, adjustments: Adjustments, filmId: String?) {
        val sidecar = RapidRawSidecar(
            version = 1,
            sourceUri = imageUri.toString(),
            filmId = filmId,
            adjustments = adjustments,
        )
        val jsonText = json.encodeToString(sidecar)

        // Try to write next to original if it's a file URI
        if (imageUri.scheme == "file") {
            val originalFile = File(imageUri.path!!)
            val sidecarFile = File(originalFile.parent, originalFile.nameWithoutExtension + ".rapidraw")
            sidecarFile.writeText(jsonText)
        } else {
            // For content URIs, store in app-private sidecar directory
            val sidecarDir = File(context.filesDir, "sidecars").apply { mkdirs() }
            val sidecarFile = File(sidecarDir, "${imageUri.toString().hashCode()}.rapidraw")
            sidecarFile.writeText(jsonText)
        }
    }

    /**
     * Load sidecar adjustments if they exist.
     */
    fun loadSidecar(context: Context, imageUri: Uri): RapidRawSidecar? {
        val sidecarFile = findSidecarFile(context, imageUri)
        return try {
            sidecarFile?.let { json.decodeFromString<RapidRawSidecar>(it.readText()) }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Check if a sidecar exists for the given image.
     */
    fun hasSidecar(context: Context, imageUri: Uri): Boolean {
        return findSidecarFile(context, imageUri)?.exists() == true
    }

    /**
     * Delete sidecar for the given image.
     */
    fun deleteSidecar(context: Context, imageUri: Uri) {
        findSidecarFile(context, imageUri)?.delete()
    }

    private fun findSidecarFile(context: Context, imageUri: Uri): File? {
        if (imageUri.scheme == "file") {
            val originalFile = File(imageUri.path!!)
            val sidecarFile = File(originalFile.parent, originalFile.nameWithoutExtension + ".rapidraw")
            if (sidecarFile.exists()) return sidecarFile
        }
        val sidecarDir = File(context.filesDir, "sidecars")
        val sidecarFile = File(sidecarDir, "${imageUri.toString().hashCode()}.rapidraw")
        return if (sidecarFile.exists()) sidecarFile else null
    }
}

@Serializable
data class RapidRawSidecar(
    val version: Int = 1,
    val sourceUri: String,
    val filmId: String? = null,
    val adjustments: Adjustments,
)

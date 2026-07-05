package com.alcedo.studio.core

import android.content.Context
import android.net.Uri
import com.alcedo.studio.data.model.Adjustments
import com.alcedo.studio.data.model.EditHistoryEntry
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class VirtualCopyManager(private val context: Context) {

    constructor(uri: Uri, context: Context) : this(context)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val copyDir: File
        get() = File(context.filesDir, "virtual_copies")

    init {
        if (!copyDir.exists()) {
            copyDir.mkdirs()
        }
    }

    fun createVirtualCopy(
        sourceUri: String,
        copyName: String,
        adjustments: Adjustments = Adjustments.Default,
        history: List<EditHistoryEntry> = emptyList()
    ): VirtualCopy {
        val copy = VirtualCopy(
            id = UUID.randomUUID().toString(),
            sourceUri = sourceUri,
            name = copyName,
            adjustments = adjustments,
            history = history,
            createdAt = System.currentTimeMillis(),
            lastEditedAt = System.currentTimeMillis()
        )

        saveCopy(copy)
        return copy
    }

    fun getVirtualCopiesForSource(sourceUri: String): List<VirtualCopy> {
        return getAllCopies().filter { it.sourceUri == sourceUri }
            .sortedByDescending { it.lastEditedAt }
    }

    fun getAllCopies(): List<VirtualCopy> {
        return copyDir.listFiles { file -> file.extension == "json" }
            ?.mapNotNull { file ->
                try {
                    json.decodeFromString<VirtualCopy>(file.readText())
                } catch (e: Exception) {
                    null
                }
            } ?: emptyList()
    }

    fun getCopy(copyId: String): VirtualCopy? {
        val file = File(copyDir, "$copyId.json")
        return if (file.exists()) {
            try {
                json.decodeFromString<VirtualCopy>(file.readText())
            } catch (e: Exception) {
                null
            }
        } else null
    }

    fun saveCopy(copy: VirtualCopy) {
        try {
            val file = File(copyDir, "${copy.id}.json")
            val updatedCopy = copy.copy(lastEditedAt = System.currentTimeMillis())
            file.writeText(json.encodeToString(updatedCopy))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun updateCopyAdjustments(copyId: String, adjustments: Adjustments) {
        val copy = getCopy(copyId) ?: return
        saveCopy(copy.copy(adjustments = adjustments))
    }

    fun renameCopy(copyId: String, newName: String) {
        val copy = getCopy(copyId) ?: return
        saveCopy(copy.copy(name = newName))
    }

    fun deleteCopy(copyId: String): Boolean {
        val file = File(copyDir, "$copyId.json")
        return if (file.exists()) {
            file.delete()
        } else false
    }

    fun deleteCopiesForSource(sourceUri: String) {
        getVirtualCopiesForSource(sourceUri).forEach { copy ->
            deleteCopy(copy.id)
        }
    }

    fun duplicateCopy(copyId: String, newName: String): VirtualCopy? {
        val original = getCopy(copyId) ?: return null
        return createVirtualCopy(
            sourceUri = original.sourceUri,
            copyName = newName,
            adjustments = original.adjustments,
            history = original.history
        )
    }

    fun getCopyCount(): Int {
        return copyDir.listFiles { file -> file.extension == "json" }?.size ?: 0
    }

    fun searchCopies(query: String): List<VirtualCopy> {
        val lowerQuery = query.lowercase()
        return getAllCopies().filter {
            it.name.lowercase().contains(lowerQuery)
        }
    }
}

@Serializable
data class VirtualCopy(
    val id: String,
    val sourceUri: String,
    val name: String,
    val adjustments: Adjustments = Adjustments.Default,
    val history: List<EditHistoryEntry> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val lastEditedAt: Long = System.currentTimeMillis(),
    val rating: Int = 0,
    val colorLabel: String? = null,
)

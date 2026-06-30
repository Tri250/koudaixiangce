package com.rapidraw.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class DamLibraryStats(
    val totalImages: Int = 0,
    val rawCount: Int = 0,
    val jpegCount: Int = 0,
    val totalFileSize: Long = 0L,
    val editedCount: Int = 0,
    val ratedCount: Int = 0,
)

@Serializable
data class DamCameraStats(
    val make: String,
    val model: String,
    val count: Int,
)

@Serializable
data class DamLensStats(
    val lens: String,
    val count: Int,
)

@Serializable
data class DamDateGroup(
    val date: String,
    val count: Int,
    val thumbnailPaths: List<String> = emptyList(),
)

@Serializable
data class DamFacetData(
    val cameras: List<DamCameraStats> = emptyList(),
    val lenses: List<DamLensStats> = emptyList(),
    val dateGroups: List<DamDateGroup> = emptyList(),
    val focalLengths: Map<Int, Int> = emptyMap(),
    val apertureValues: Map<String, Int> = emptyMap(),
    val isoValues: Map<Int, Int> = emptyMap(),
)

@Immutable
@Serializable
data class DamProjectFile(
    val version: Int = 1,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis(),
    val description: String = "",
    val libraryStats: DamLibraryStats = DamLibraryStats(),
    val facetData: DamFacetData = DamFacetData(),
    val imageEntries: List<DamImageEntry> = emptyList(),
    val folderTree: List<DamFolderNode> = emptyList(),
    val tags: List<DamTag> = emptyList(),
    val albums: List<DamAlbum> = emptyList(),
    val smartAlbums: List<DamSmartAlbum> = emptyList(),
    val settings: DamProjectSettings = DamProjectSettings(),
) {
    companion object {
        const val CURRENT_VERSION = 1
        const val FILE_EXTENSION = "alcd"

        fun fromJson(json: String): DamProjectFile = Json.decodeFromString(json)

        fun toJson(project: DamProjectFile): String = Json.encodeToString(project)
    }
}

@Serializable
data class DamImageEntry(
    val id: String,
    val path: String,
    val fileName: String,
    val folderPath: String,
    val isRaw: Boolean,
    val width: Int = 0,
    val height: Int = 0,
    val fileSize: Long = 0L,
    val dateModified: Long = 0L,
    val dateTaken: Long = 0L,
    val rating: Int = 0,
    val colorLabel: ColorLabel = ColorLabel.NONE,
    val tags: List<String> = emptyList(),
    val cameraMake: String? = null,
    val cameraModel: String? = null,
    val lensModel: String? = null,
    val focalLength: Float? = null,
    val aperture: Float? = null,
    val shutterSpeed: String? = null,
    val iso: Int? = null,
    val thumbnailPath: String? = null,
    val hasAdjustments: Boolean = false,
    val virtualCopyOf: String? = null,
    val lastEdited: Long? = null,
    val flagStatus: Int = 0,
)

@Serializable
data class DamFolderNode(
    val path: String,
    val name: String,
    val imageCount: Int = 0,
    val children: List<DamFolderNode> = emptyList(),
    val isExpanded: Boolean = false,
    val icon: String? = null,
)

@Serializable
data class DamTag(
    val id: String,
    val name: String,
    val color: Long = 0xFFE8600C,
    val count: Int = 0,
)

@Serializable
data class DamAlbum(
    val id: String,
    val name: String,
    val imageIds: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis(),
    val coverImageId: String? = null,
)

@Serializable
data class DamSmartAlbum(
    val id: String,
    val name: String,
    val query: DamSearchQuery,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
data class DamSearchQuery(
    val text: String = "",
    val ratingMin: Int = 0,
    val ratingMax: Int = 5,
    val colorLabels: List<ColorLabel> = emptyList(),
    val cameras: List<String> = emptyList(),
    val lenses: List<String> = emptyList(),
    val dateFrom: Long? = null,
    val dateTo: Long? = null,
    val fileTypes: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val hasAdjustments: Boolean? = null,
    val focalLengthMin: Float? = null,
    val focalLengthMax: Float? = null,
    val apertureMin: Float? = null,
    val apertureMax: Float? = null,
    val isoMin: Int? = null,
    val isoMax: Int? = null,
    val flagStatus: Int? = null,
    val sortBy: String = "dateTaken",
    val sortOrder: String = "desc",
)

@Serializable
data class DamProjectSettings(
    val thumbnailSize: Int = 256,
    val thumbnailQuality: Int = 80,
    val autoGenerateThumbnails: Boolean = true,
    val sidecarFormat: String = "json",
    val autoSaveSidecar: Boolean = true,
    val defaultColorSpace: String = "sRGB",
    val defaultToneMapper: String = "agx",
    val showFileExtensions: Boolean = true,
    val libraryViewMode: String = "grid",
    val gridSize: Int = 4,
)

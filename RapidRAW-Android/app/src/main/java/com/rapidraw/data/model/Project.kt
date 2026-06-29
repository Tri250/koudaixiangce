package com.rapidraw.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Project(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis(),
    val imagePaths: List<String> = emptyList(),
    val thumbnailPath: String? = null,
    val settings: ProjectSettings? = null,
)

@Serializable
data class ProjectSettings(
    val defaultExportSettings: ExportSettings? = null,
    val sortOrder: SortOrder = SortOrder.DATE_DESC,
)

@Serializable
enum class SortOrder {
    DATE_DESC,
    DATE_ASC,
    NAME_ASC,
    NAME_DESC,
}

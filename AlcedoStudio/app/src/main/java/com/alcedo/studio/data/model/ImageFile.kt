package com.alcedo.studio.data.model

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ImageFile(
    val id: Long = 0L,
    val uri: Uri = Uri.EMPTY,
    val displayName: String = "",
    val mimeType: String = "",
    val size: Long = 0L,
    val dateAdded: Long = 0L,
    val dateModified: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val orientation: Int = 0,
    val isRaw: Boolean = false,
    val rawFormat: String = "",
    val bucketId: String = "",
    val bucketDisplayName: String = "",
    val exifData: ExifData = ExifData(),
    val rating: Int = 0,
    val colorLabel: String? = null,
    val isFavorite: Boolean = false,
) : Parcelable {
    val isPortrait: Boolean get() = orientation % 180 == 0
        ? height > width
        : width > height
}

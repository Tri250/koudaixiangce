package com.alcedo.studio.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
data class ExifData(
    val cameraMake: String = "",
    val cameraModel: String = "",
    val lensMake: String = "",
    val lensModel: String = "",
    val focalLength: Float = 0f,
    val focalLength35mm: Float = 0f,
    val aperture: Float = 0f,
    val shutterSpeed: String = "",
    val iso: Int = 0,
    val exposureCompensation: Float = 0f,
    val whiteBalance: String = "",
    val flash: Boolean = false,
    val orientation: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val dateTime: Long = 0L,
    val gpsLatitude: Double? = null,
    val gpsLongitude: Double? = null,
    val gpsAltitude: Double? = null,
    val artist: String = "",
    val copyright: String = "",
    val description: String = "",
    val rawFormat: String = "",
    val bitDepth: Int = 0,
    val colorSpace: String = "sRGB",
) : Parcelable

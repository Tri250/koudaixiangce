package com.rapidraw.data.model

data class ExifData(
    val make: String? = null,
    val model: String? = null,
    val lensMake: String? = null,
    val lensModel: String? = null,
    val focalLength: String? = null,
    val aperture: String? = null,
    val shutterSpeed: String? = null,
    val iso: String? = null,
    val dateTime: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val flash: String? = null,
    val whiteBalance: String? = null,
    val meteringMode: String? = null,
    val exposureProgram: String? = null,
    val gpsLatitude: String? = null,
    val gpsLongitude: String? = null,
)

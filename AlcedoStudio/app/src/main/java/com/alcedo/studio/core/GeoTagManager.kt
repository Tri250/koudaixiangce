package com.alcedo.studio.core

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.alcedo.studio.data.model.ExifData
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class GeoTagManager(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val geoDir: File
        get() = File(context.filesDir, "geotags")

    init {
        if (!geoDir.exists()) {
            geoDir.mkdirs()
        }
    }

    fun getGeoTagFromUri(uri: String): GeoLocation? {
        return try {
            val inputStream = context.contentResolver.openInputStream(Uri.parse(uri)) ?: return null
            val exif = ExifInterface(inputStream)

            val latLong = exif.latLong
            if (latLong != null && latLong.size == 2) {
                val altitude = if (exif.getAttribute(ExifInterface.TAG_GPS_ALTITUDE) != null) {
                    val altStr = exif.getAttribute(ExifInterface.TAG_GPS_ALTITUDE)!!
                    val parts = altStr.split("/")
                    if (parts.size == 2) {
                        parts[0].toDouble() / parts[1].toDouble()
                    } else {
                        altStr.toDoubleOrNull()
                    }
                } else null

                GeoLocation(
                    latitude = latLong[0],
                    longitude = latLong[1],
                    altitude = altitude,
                    timestamp = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                )
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun saveGeoTag(photoUri: String, location: GeoLocation) {
        try {
            val tag = PhotoGeoTag(
                id = UUID.randomUUID().toString(),
                photoUri = photoUri,
                location = location,
                createdAt = System.currentTimeMillis()
            )
            val file = File(geoDir, "${tag.id}.json")
            file.writeText(json.encodeToString(tag))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getGeoTags(): List<PhotoGeoTag> {
        return geoDir.listFiles { file -> file.extension == "json" }
            ?.mapNotNull { file ->
                try {
                    json.decodeFromString<PhotoGeoTag>(file.readText())
                } catch (e: Exception) {
                    null
                }
            } ?: emptyList()
    }

    fun getPhotosInBounds(
        minLat: Double,
        maxLat: Double,
        minLng: Double,
        maxLng: Double
    ): List<PhotoGeoTag> {
        return getGeoTags().filter { tag ->
            tag.location.latitude in minLat..maxLat &&
                tag.location.longitude in minLng..maxLng
        }
    }

    fun getLocationCluster(photos: List<PhotoGeoTag>, gridSize: Double = 0.01): List<LocationCluster> {
        val clusters = mutableMapOf<String, MutableList<PhotoGeoTag>>()

        for (photo in photos) {
            val gridLat = (photo.location.latitude / gridSize).toInt()
            val gridLng = (photo.location.longitude / gridSize).toInt()
            val key = "$gridLat,$gridLng"

            clusters.getOrPut(key) { mutableListOf() }.add(photo)
        }

        return clusters.map { (_, photosInCluster) ->
            val avgLat = photosInCluster.sumOf { it.location.latitude } / photosInCluster.size
            val avgLng = photosInCluster.sumOf { it.location.longitude } / photosInCluster.size

            LocationCluster(
                center = GeoLocation(latitude = avgLat, longitude = avgLng),
                photoCount = photosInCluster.size,
                photos = photosInCluster
            )
        }.sortedByDescending { it.photoCount }
    }

    fun formatLatitude(latitude: Double): String {
        val direction = if (latitude >= 0) "N" else "S"
        val absLat = kotlin.math.abs(latitude)
        val degrees = absLat.toInt()
        val minutes = ((absLat - degrees) * 60).toInt()
        val seconds = (((absLat - degrees) * 60 - minutes) * 60).toInt()
        return "$degrees° $minutes' $seconds\" $direction"
    }

    fun formatLongitude(longitude: Double): String {
        val direction = if (longitude >= 0) "E" else "W"
        val absLng = kotlin.math.abs(longitude)
        val degrees = absLng.toInt()
        val minutes = ((absLng - degrees) * 60).toInt()
        val seconds = (((absLng - degrees) * 60 - minutes) * 60).toInt()
        return "$degrees° $minutes' $seconds\" $direction"
    }

    fun calculateDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val earthRadius = 6371.0

        val dLat = kotlin.math.toRadians(lat2 - lat1)
        val dLng = kotlin.math.toRadians(lng2 - lng1)

        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(kotlin.math.toRadians(lat1)) *
            kotlin.math.cos(kotlin.math.toRadians(lat2)) *
            kotlin.math.sin(dLng / 2) * kotlin.math.sin(dLng / 2)

        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))

        return earthRadius * c
    }
}

@Serializable
data class GeoLocation(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null,
    val timestamp: String? = null,
    val address: String? = null,
    val placeName: String? = null,
)

@Serializable
data class PhotoGeoTag(
    val id: String,
    val photoUri: String,
    val location: GeoLocation,
    val createdAt: Long,
)

@Serializable
data class LocationCluster(
    val center: GeoLocation,
    val photoCount: Int,
    val photos: List<PhotoGeoTag>
)

package com.rapidraw.ui.map

import android.app.Application
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rapidraw.data.model.ExifData
import com.rapidraw.data.model.ImageFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 地理标记 ViewModel — 加载带有 GPS 坐标的图像，管理地图标注数据。
 *
 * 暴露：
 * - markers: 地图标记列表
 * - selectedMarker: 当前选中的标记
 * - isLoading: 加载状态
 * - dateRange: 日期过滤范围
 */
class GeotagViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "GeotagViewModel"
    }

    /** 地图标记 */
    data class GeoMarker(
        val imageFile: ImageFile,
        val latitude: Double,
        val longitude: Double,
        val thumbnailPath: String?,
        val dateTime: String?,
        val exifData: ExifData?,
    ) {
        /** 标记 ID（基于路径） */
        val id: String get() = imageFile.path
    }

    /** 用于聚类的聚合标记 */
    data class ClusterMarker(
        val latitude: Double,
        val longitude: Double,
        val count: Int,
        val markers: List<GeoMarker>,
    )

    private val contentResolver = application.contentResolver

    private val _markers = MutableStateFlow<List<GeoMarker>>(emptyList())
    val markers: StateFlow<List<GeoMarker>> = _markers.asStateFlow()

    private val _selectedMarker = MutableStateFlow<GeoMarker?>(null)
    val selectedMarker: StateFlow<GeoMarker?> = _selectedMarker.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _dateRange = MutableStateFlow<Pair<Long, Long>?>(null)
    val dateRange: StateFlow<Pair<Long, Long>?> = _dateRange.asStateFlow()

    private val allMarkers = mutableListOf<GeoMarker>()

    /**
     * 从当前项目/图库加载带有 GPS 数据的图像。
     */
    fun loadImages(images: List<ImageFile>) {
        viewModelScope.launch {
            _isLoading.value = true
            allMarkers.clear()

            try {
                val results = withContext(Dispatchers.IO) {
                    images.mapNotNull { imageFile ->
                        loadGeoData(imageFile)
                    }
                }

                allMarkers.addAll(results)
                applyFilters()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load geo data: ${e.message}", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 从设备媒体库加载带有 GPS 数据的图像。
     */
    fun loadFromMediaStore() {
        viewModelScope.launch {
            _isLoading.value = true
            allMarkers.clear()

            try {
                val results = withContext(Dispatchers.IO) {
                    val markerList = mutableListOf<GeoMarker>()

                    val projection = arrayOf(
                        MediaStore.Images.Media.DATA,
                        MediaStore.Images.Media.DISPLAY_NAME,
                        MediaStore.Images.Media.DATE_MODIFIED,
                        MediaStore.Images.Media.SIZE,
                        MediaStore.Images.Media.WIDTH,
                        MediaStore.Images.Media.HEIGHT,
                        MediaStore.Images.Media.LATITUDE,
                        MediaStore.Images.Media.LONGITUDE,
                        MediaStore.Images.Media.DATE_TAKEN,
                    )

                    val cursor = contentResolver.query(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        projection,
                        "${MediaStore.Images.Media.LATITUDE} IS NOT NULL AND ${MediaStore.Images.Media.LONGITUDE} IS NOT NULL",
                        null,
                        "${MediaStore.Images.Media.DATE_TAKEN} DESC",
                    )

                    cursor?.use { c ->
                        val dataIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                        val nameIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                        val dateIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                        val sizeIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                        val widthIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                        val heightIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
                        val latIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media.LATITUDE)
                        val lonIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media.LONGITUDE)
                        val takenIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)

                        while (c.moveToNext()) {
                            val path = c.getString(dataIdx) ?: continue
                            val lat = c.getDouble(latIdx)
                            val lon = c.getDouble(lonIdx)

                            if (lat == 0.0 && lon == 0.0) continue

                            val fileName = c.getString(nameIdx) ?: "unknown"
                            val folderPath = File(path).parent ?: ""
                            val dateModified = c.getLong(dateIdx) * 1000L
                            val fileSize = c.getLong(sizeIdx)
                            val width = c.getInt(widthIdx)
                            val height = c.getInt(heightIdx)
                            val dateTaken = c.getLong(takenIdx)

                            val imageFile = ImageFile(
                                path = path,
                                fileName = fileName,
                                folderPath = folderPath,
                                isRaw = ImageFile.isRawFile(path),
                                width = width,
                                height = height,
                                fileSize = fileSize,
                                dateModified = dateModified,
                            )

                            // 读取详细 EXIF
                            val exifData = readExifData(path)

                            markerList.add(
                                GeoMarker(
                                    imageFile = imageFile,
                                    latitude = lat,
                                    longitude = lon,
                                    thumbnailPath = null,
                                    dateTime = if (dateTaken > 0) dateTaken.toString() else null,
                                    exifData = exifData,
                                )
                            )
                        }
                    }

                    markerList
                }

                allMarkers.addAll(results)
                applyFilters()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load from MediaStore: ${e.message}", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 选择标记。
     */
    fun selectMarker(marker: GeoMarker?) {
        _selectedMarker.value = marker
    }

    /**
     * 设置日期范围过滤。
     */
    fun setDateRange(start: Long?, end: Long?) {
        if (start != null && end != null) {
            _dateRange.value = Pair(start, end)
        } else {
            _dateRange.value = null
        }
        applyFilters()
    }

    /**
     * 清除日期过滤。
     */
    fun clearDateFilter() {
        _dateRange.value = null
        applyFilters()
    }

    /**
     * 聚类标记（基于距离）。
     */
    fun clusterMarkers(clusterRadius: Double = 0.01): List<ClusterMarker> {
        val markers = _markers.value
        if (markers.isEmpty()) return emptyList()

        val visited = BooleanArray(markers.size)
        val clusters = mutableListOf<ClusterMarker>()

        for (i in markers.indices) {
            if (visited[i]) continue
            visited[i] = true

            val clusterMembers = mutableListOf(markers[i])
            for (j in (i + 1) until markers.size) {
                if (visited[j]) continue
                val dist = haversineDistance(
                    markers[i].latitude, markers[i].longitude,
                    markers[j].latitude, markers[j].longitude,
                )
                if (dist < clusterRadius) {
                    visited[j] = true
                    clusterMembers.add(markers[j])
                }
            }

            if (clusterMembers.size == 1) {
                val m = clusterMembers[0]
                clusters.add(ClusterMarker(m.latitude, m.longitude, 1, clusterMembers))
            } else {
                // 聚类中心
                val avgLat = clusterMembers.map { it.latitude }.average()
                val avgLon = clusterMembers.map { it.longitude }.average()
                clusters.add(ClusterMarker(avgLat, avgLon, clusterMembers.size, clusterMembers))
            }
        }

        return clusters
    }

    // ── 私有方法 ──────────────────────────────────────────────────

    private fun loadGeoData(imageFile: ImageFile): GeoMarker? {
        return try {
            val exif = try {
                ExifInterface(imageFile.path)
            } catch (e: Exception) {
                null
            } ?: return null

            val latLong = exif.latLong ?: return null
            val lat = latLong[0]
            val lon = latLong[1]

            if (lat == 0.0 && lon == 0.0) return null

            val exifData = readExifData(imageFile.path)

            GeoMarker(
                imageFile = imageFile,
                latitude = lat,
                longitude = lon,
                thumbnailPath = imageFile.thumbnailPath,
                dateTime = exif.getAttribute(ExifInterface.TAG_DATETIME),
                exifData = exifData,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load geo data for ${imageFile.path}: ${e.message}")
            null
        }
    }

    private fun readExifData(path: String): ExifData? {
        return try {
            val exif = ExifInterface(path)
            ExifData(
                make = exif.getAttribute(ExifInterface.TAG_MAKE),
                model = exif.getAttribute(ExifInterface.TAG_MODEL),
                focalLength = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH),
                aperture = exif.getAttribute(ExifInterface.TAG_APERTURE_VALUE),
                shutterSpeed = exif.getAttribute(ExifInterface.TAG_SHUTTER_SPEED_VALUE),
                iso = exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY),
                dateTime = exif.getAttribute(ExifInterface.TAG_DATETIME),
                flash = exif.getAttribute(ExifInterface.TAG_FLASH),
                whiteBalance = exif.getAttribute(ExifInterface.TAG_WHITE_BALANCE),
                gpsLatitude = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE),
                gpsLongitude = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE),
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun applyFilters() {
        var filtered = allMarkers.toList()

        // 日期过滤
        val range = _dateRange.value
        if (range != null) {
            filtered = filtered.filter { marker ->
                val dateTime = marker.dateTime
                if (dateTime != null) {
                    try {
                        val timestamp = dateTime.toLongOrNull()
                        timestamp != null && timestamp in range.first..range.second
                    } catch (_: Exception) {
                        true
                    }
                } else true
            }
        }

        _markers.value = filtered
    }

    /**
     * Haversine 距离计算（公里）。
     */
    private fun haversineDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double,
    ): Double {
        val R = 6371.0 // 地球半径（公里）
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }
}
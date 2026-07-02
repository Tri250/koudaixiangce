package com.rapidraw.ui.metadata

import androidx.exifinterface.media.ExifInterface
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

/**
 * B-10: EXIF 浮窗查看
 * 使用 ModalBottomSheet 展示图片的 EXIF 元数据信息。
 * 显示：快门、光圈、ISO、焦距、镜头型号、拍摄时间、机身型号。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExifBottomSheet(
    imagePath: String,
    onDismiss: () -> Unit,
) {
    val exifData = remember(imagePath) { readExifData(imagePath) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A2E),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = "EXIF 信息",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            if (exifData.isEmpty()) {
                Text(
                    text = "未找到 EXIF 数据",
                    color = Color.Gray,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 400.dp),
                ) {
                    items(exifData) { (label, value) ->
                        ExifRow(label = label, value = value)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ExifRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = Color.Gray,
            fontSize = 14.sp,
            modifier = Modifier.weight(0.4f),
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.6f),
        )
    }
    HorizontalDivider(color = Color(0xFF2A2A3E), thickness = 0.5.dp)
}

private fun readExifData(imagePath: String): List<Pair<String, String>> {
    val data = mutableListOf<Pair<String, String>>()

    try {
        val exif = ExifInterface(imagePath)

        // 快门速度
        val exposureTime = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)
        if (!exposureTime.isNullOrBlank()) {
            val numerator = exposureTime.toDoubleOrNull()
            val shutterDisplay = if (numerator != null && numerator < 1.0 && numerator > 0.0) {
                val denominator = (1.0 / numerator).toInt()
                "1/$denominator"
            } else {
                exposureTime
            }
            data.add("快门" to "${shutterDisplay}s")
        }

        // 光圈
        val aperture = exif.getAttribute(ExifInterface.TAG_F_NUMBER)
        if (!aperture.isNullOrBlank()) {
            data.add("光圈" to "f/$aperture")
        }

        // ISO
        val iso = exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)
            ?: exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)
        if (!iso.isNullOrBlank()) {
            data.add("ISO" to iso)
        }

        // 焦距
        val focalLength = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)
        if (!focalLength.isNullOrBlank()) {
            val focalFloat = focalLength.toDoubleOrNull()
            val focalDisplay = if (focalFloat != null) {
                String.format("%.0fmm", focalFloat)
            } else {
                "${focalLength}mm"
            }
            data.add("焦距" to focalDisplay)
        }

        // 35mm 等效焦距
        val focal35mm = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM)
        if (!focal35mm.isNullOrBlank()) {
            data.add("35mm等效焦距" to "${focal35mm}mm")
        }

        // 镜头型号
        val lensModel = exif.getAttribute(ExifInterface.TAG_LENS_MODEL)
        if (!lensModel.isNullOrBlank()) {
            data.add("镜头型号" to lensModel)
        }

        // 机身型号
        val make = exif.getAttribute(ExifInterface.TAG_MAKE)
        val model = exif.getAttribute(ExifInterface.TAG_MODEL)
        if (!make.isNullOrBlank() || !model.isNullOrBlank()) {
            val camera = listOfNotNull(
                make?.takeIf { it.isNotBlank() },
                model?.takeIf { it.isNotBlank() }
            ).joinToString(" ")
            data.add("机身型号" to camera)
        }

        // 拍摄时间
        val dateTime = exif.getAttribute(ExifInterface.TAG_DATETIME)
            ?: exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
        if (!dateTime.isNullOrBlank()) {
            try {
                val inputFormat = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault())
                val outputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val date = inputFormat.parse(dateTime)
                if (date != null) {
                    data.add("拍摄时间" to outputFormat.format(date))
                } else {
                    data.add("拍摄时间" to dateTime)
                }
            } catch (e: Exception) {
                data.add("拍摄时间" to dateTime)
            }
        }

        // 曝光补偿
        val exposureBias = exif.getAttribute(ExifInterface.TAG_EXPOSURE_BIAS_VALUE)
        if (!exposureBias.isNullOrBlank()) {
            val bias = exposureBias.toDoubleOrNull()
            if (bias != null) {
                data.add("曝光补偿" to "${if (bias >= 0) "+" else ""}${bias} EV")
            }
        }

        // 闪光灯
        val flash = exif.getAttribute(ExifInterface.TAG_FLASH)
        if (!flash.isNullOrBlank()) {
            val flashState = when (flash.toIntOrNull()) {
                0 -> "未闪光"
                1 -> "闪光"
                5 -> "闪光（未检测到返回光）"
                7 -> "闪光（检测到返回光）"
                9 -> "强制闪光"
                16 -> "未闪光（强制）"
                24 -> "自动闪光"
                else -> "未知"
            }
            data.add("闪光灯" to flashState)
        }

        // 图像尺寸
        val width = exif.getAttribute(ExifInterface.TAG_IMAGE_WIDTH)
        val height = exif.getAttribute(ExifInterface.TAG_IMAGE_LENGTH)
        if (!width.isNullOrBlank() && !height.isNullOrBlank()) {
            data.add("图像尺寸" to "${width} × ${height}")
        }

        // GPS 信息
        val latRef = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF)
        val lat = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE)
        val lonRef = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF)
        val lon = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE)
        if (!lat.isNullOrBlank() && !lon.isNullOrBlank()) {
            val latDms = parseDms(lat)
            val lonDms = parseDms(lon)
            if (latDms != null && lonDms != null) {
                data.add("GPS" to "%.6f, %.6f".format(latDms, lonDms))
            }
        }

    } catch (e: IOException) {
        // 无法读取 EXIF
    }

    return data
}

/**
 * 解析 DMS (度分秒) 格式的 GPS 坐标为十进制
 */
private fun parseDms(dms: String): Double? {
    val parts = dms.split(",")
    if (parts.size < 3) return null
    return try {
        val degrees = parts[0].trim().split("/").let {
            if (it.size == 2) it[0].toDouble() / it[1].toDouble() else it[0].toDouble()
        }
        val minutes = parts[1].trim().split("/").let {
            if (it.size == 2) it[0].toDouble() / it[1].toDouble() else it[0].toDouble()
        }
        val seconds = parts[2].trim().split("/").let {
            if (it.size == 2) it[0].toDouble() / it[1].toDouble() else it[0].toDouble()
        }
        degrees + minutes / 60.0 + seconds / 3600.0
    } catch (e: Exception) {
        null
    }
}
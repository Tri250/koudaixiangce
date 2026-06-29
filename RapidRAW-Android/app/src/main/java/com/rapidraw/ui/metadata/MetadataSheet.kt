package com.rapidraw.ui.metadata

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rapidraw.data.model.ExifData
import com.rapidraw.ui.adjustments.CollapsibleSection
import com.rapidraw.ui.theme.EditorBorder
import com.rapidraw.ui.theme.EditorSurface
import com.rapidraw.ui.theme.TextPrimary
import com.rapidraw.ui.theme.TextSecondary
import com.rapidraw.ui.theme.TextTertiary

@Composable
fun MetadataSheet(exif: ExifData?) {
    var cameraExpanded by remember { mutableStateOf(true) }
    var lensExpanded by remember { mutableStateOf(true) }
    var exposureExpanded by remember { mutableStateOf(true) }
    var imageExpanded by remember { mutableStateOf(true) }
    var gpsExpanded by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(EditorSurface)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp),
    ) {
        if (exif == null) {
            Text(
                text = "无EXIF数据",
                color = TextTertiary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(24.dp),
            )
            return
        }

        // ── 相机 ──────────────────────────────────────────
        CollapsibleSection(
            title = "相机",
            expanded = cameraExpanded,
            onToggle = { cameraExpanded = !cameraExpanded },
        ) {
            MetadataRow(label = "制造商", value = exif.make)
            MetadataRow(label = "型号", value = exif.model)
        }

        // ── 镜头 ──────────────────────────────────────────
        CollapsibleSection(
            title = "镜头",
            expanded = lensExpanded,
            onToggle = { lensExpanded = !lensExpanded },
        ) {
            MetadataRow(label = "镜头制造", value = exif.lensMake)
            MetadataRow(label = "镜头型号", value = exif.lensModel)
            MetadataRow(label = "焦距", value = exif.focalLength)
        }

        // ── 曝光 ──────────────────────────────────────────
        CollapsibleSection(
            title = "曝光",
            expanded = exposureExpanded,
            onToggle = { exposureExpanded = !exposureExpanded },
        ) {
            MetadataRow(label = "快门速度", value = exif.shutterSpeed)
            MetadataRow(label = "光圈", value = exif.aperture)
            MetadataRow(label = "ISO", value = exif.iso)
            MetadataRow(label = "曝光补偿", value = exif.exposureProgram)
            MetadataRow(label = "测光模式", value = exif.meteringMode)
        }

        // ── 图像 ──────────────────────────────────────────
        CollapsibleSection(
            title = "图像",
            expanded = imageExpanded,
            onToggle = { imageExpanded = !imageExpanded },
        ) {
            MetadataRow(
                label = "尺寸",
                value = if (exif.width > 0 && exif.height > 0) "${exif.width} × ${exif.height}" else null,
            )
            MetadataRow(label = "日期", value = exif.dateTime)
            MetadataRow(label = "白平衡", value = exif.whiteBalance)
        }

        // ── GPS ────────────────────────────────────────────
        CollapsibleSection(
            title = "GPS",
            expanded = gpsExpanded,
            onToggle = { gpsExpanded = !gpsExpanded },
        ) {
            MetadataRow(label = "纬度", value = exif.gpsLatitude)
            MetadataRow(label = "经度", value = exif.gpsLongitude)
        }
    }
}

@Composable
private fun MetadataRow(label: String, value: String?) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = label,
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = value ?: "—",
                color = TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(2f),
            )
        }
        HorizontalDivider(
            color = EditorBorder,
            thickness = 0.5.dp,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

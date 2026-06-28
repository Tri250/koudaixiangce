package com.rapidraw.ui.adjustments

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Rotate90DegreesCcw
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rapidraw.data.model.Adjustments
import com.rapidraw.ui.components.HasselSlider
import com.rapidraw.ui.theme.EditorSurfaceVariant
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.TextPrimary
import com.rapidraw.ui.theme.TextSecondary
import com.rapidraw.ui.theme.TextTertiary

@Composable
fun TransformPanel(
    adjustments: Adjustments,
    onUpdate: (String, Any) -> Unit,
    onInteractiveCrop: () -> Unit = {},
) {
    var cropExpanded by remember { mutableStateOf(true) }
    var rotationExpanded by remember { mutableStateOf(true) }
    var perspectiveExpanded by remember { mutableStateOf(false) }
    var lensExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        // ── 裁剪 ──────────────────────────────────────────
        CollapsibleSection(
            title = "裁剪",
            expanded = cropExpanded,
            onToggle = { cropExpanded = !cropExpanded },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AspectRatioButton(
                    label = "自由",
                    isSelected = adjustments.crop?.aspectRatio == null,
                    onClick = { onUpdate("cropAspectRatio", null) },
                    modifier = Modifier.weight(1f),
                )
                AspectRatioButton(
                    label = "1:1",
                    isSelected = adjustments.crop?.aspectRatio == 1f,
                    onClick = { onUpdate("cropAspectRatio", 1f) },
                    modifier = Modifier.weight(1f),
                )
                AspectRatioButton(
                    label = "4:3",
                    isSelected = adjustments.crop?.aspectRatio == 4f / 3f,
                    onClick = { onUpdate("cropAspectRatio", 4f / 3f) },
                    modifier = Modifier.weight(1f),
                )
                AspectRatioButton(
                    label = "3:2",
                    isSelected = adjustments.crop?.aspectRatio == 3f / 2f,
                    onClick = { onUpdate("cropAspectRatio", 3f / 2f) },
                    modifier = Modifier.weight(1f),
                )
                AspectRatioButton(
                    label = "16:9",
                    isSelected = adjustments.crop?.aspectRatio == 16f / 9f,
                    onClick = { onUpdate("cropAspectRatio", 16f / 9f) },
                    modifier = Modifier.weight(1f),
                )
                AspectRatioButton(
                    label = "65:24",
                    subLabel = "XPAN",
                    isSelected = adjustments.crop?.aspectRatio == 65f / 24f,
                    onClick = { onUpdate("cropAspectRatio", 65f / 24f) },
                    modifier = Modifier.weight(1f),
                    showIndicator = adjustments.crop?.aspectRatio == 65f / 24f,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { onUpdate("flipHorizontal", !adjustments.flipHorizontal) },
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            if (adjustments.flipHorizontal) HasselbladOrange else EditorSurfaceVariant,
                            RoundedCornerShape(6.dp),
                        ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Flip,
                        contentDescription = "水平翻转",
                        tint = if (adjustments.flipHorizontal) TextPrimary else TextSecondary,
                    )
                }
                IconButton(
                    onClick = { onUpdate("flipVertical", !adjustments.flipVertical) },
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            if (adjustments.flipVertical) HasselbladOrange else EditorSurfaceVariant,
                            RoundedCornerShape(6.dp),
                        ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Flip,
                        contentDescription = "垂直翻转",
                        tint = if (adjustments.flipVertical) TextPrimary else TextSecondary,
                    )
                }
            }

            // Interactive crop button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .background(
                        color = HasselbladOrange,
                        shape = RoundedCornerShape(8.dp),
                    )
                    .clickable { onInteractiveCrop() }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Crop,
                        contentDescription = "交互裁剪",
                        tint = TextPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "交互裁剪",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        // ── 旋转 ──────────────────────────────────────────
        CollapsibleSection(
            title = "旋转",
            expanded = rotationExpanded,
            onToggle = { rotationExpanded = !rotationExpanded },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconButton(
                    onClick = { onUpdate("orientationSteps", (adjustments.orientationSteps + 3) % 4) },
                    modifier = Modifier
                        .size(44.dp)
                        .background(EditorSurfaceVariant, RoundedCornerShape(6.dp)),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Rotate90DegreesCcw,
                        contentDescription = "逆时针旋转90°",
                        tint = TextPrimary,
                    )
                }
                IconButton(
                    onClick = { onUpdate("orientationSteps", (adjustments.orientationSteps + 1) % 4) },
                    modifier = Modifier
                        .size(44.dp)
                        .background(EditorSurfaceVariant, RoundedCornerShape(6.dp)),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Rotate90DegreesCw,
                        contentDescription = "顺时针旋转90°",
                        tint = TextPrimary,
                    )
                }
            }

            HasselSlider(
                label = "旋转",
                value = adjustments.rotation,
                range = -45f..45f,
                onValueChange = { onUpdate("rotation", it) },
                defaultValue = 0f,
                stepSize = 0.1f,
                format = { v -> String.format("%.1f°", v) },
            )
        }

        // ── 透视 ──────────────────────────────────────────
        CollapsibleSection(
            title = "透视",
            expanded = perspectiveExpanded,
            onToggle = { perspectiveExpanded = !perspectiveExpanded },
        ) {
            HasselSlider(
                label = "畸变",
                value = adjustments.transformDistortion,
                range = -100f..100f,
                onValueChange = { onUpdate("transformDistortion", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
            HasselSlider(
                label = "垂直",
                value = adjustments.transformVertical,
                range = -100f..100f,
                onValueChange = { onUpdate("transformVertical", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
            HasselSlider(
                label = "水平",
                value = adjustments.transformHorizontal,
                range = -100f..100f,
                onValueChange = { onUpdate("transformHorizontal", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
            HasselSlider(
                label = "旋转",
                value = adjustments.transformRotate,
                range = -45f..45f,
                onValueChange = { onUpdate("transformRotate", it) },
                defaultValue = 0f,
                stepSize = 0.1f,
                format = { v -> String.format("%.1f°", v) },
            )
            HasselSlider(
                label = "长宽",
                value = adjustments.transformAspect,
                range = -100f..100f,
                onValueChange = { onUpdate("transformAspect", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
            HasselSlider(
                label = "缩放",
                value = adjustments.transformScale,
                range = 10f..200f,
                onValueChange = { onUpdate("transformScale", it) },
                defaultValue = 100f,
                stepSize = 1f,
                format = { v -> "${v.toInt()}%" },
            )
            HasselSlider(
                label = "X偏移",
                value = adjustments.transformXOffset,
                range = -100f..100f,
                onValueChange = { onUpdate("transformXOffset", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
            HasselSlider(
                label = "Y偏移",
                value = adjustments.transformYOffset,
                range = -100f..100f,
                onValueChange = { onUpdate("transformYOffset", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
        }

        // ── 镜头校正 ──────────────────────────────────────
        CollapsibleSection(
            title = "镜头校正",
            expanded = lensExpanded,
            onToggle = { lensExpanded = !lensExpanded },
        ) {
            Text(
                text = "镜头: 未知",
                color = TextTertiary,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            HasselSlider(
                label = "畸变",
                value = adjustments.lensDistortion,
                range = -100f..100f,
                onValueChange = { onUpdate("lensDistortion", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
            HasselSlider(
                label = "暗角",
                value = adjustments.lensVignette,
                range = -100f..100f,
                onValueChange = { onUpdate("lensVignette", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
            HasselSlider(
                label = "TCA",
                value = adjustments.lensTca,
                range = -100f..100f,
                onValueChange = { onUpdate("lensTca", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
            HasselSlider(
                label = "焦距 (mm)",
                value = adjustments.lensFocalLength,
                range = 1f..1000f,
                onValueChange = { onUpdate("lensFocalLength", it) },
                defaultValue = 50f,
                stepSize = 1f,
            )
        }
    }
}

@Composable
private fun AspectRatioButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subLabel: String? = null,
    showIndicator: Boolean = false,
) {
    Box(
        modifier = modifier
            .background(
                color = if (isSelected) HasselbladOrange else EditorSurfaceVariant,
                shape = RoundedCornerShape(6.dp),
            )
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                color = if (isSelected) TextPrimary else TextSecondary,
                fontSize = 12.sp,
            )
            if (subLabel != null) {
                Text(
                    text = subLabel,
                    color = if (isSelected) TextPrimary else TextTertiary,
                    fontSize = 8.sp,
                )
            }
            if (showIndicator) {
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(4.dp)
                        .background(HasselbladOrange, CircleShape),
                )
            }
        }
    }
}

@Composable
private fun LensToggleChip(
    label: String,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Box(
        modifier = Modifier
            .padding(end = 8.dp)
            .background(
                color = if (isEnabled) HasselbladOrange else EditorSurfaceVariant,
                shape = RoundedCornerShape(4.dp),
            )
            .clickable { onToggle(!isEnabled) }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            color = if (isEnabled) TextPrimary else TextSecondary,
            fontSize = 11.sp,
        )
    }
}

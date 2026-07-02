package com.rapidraw.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rapidraw.ui.theme.EditorBackground
import com.rapidraw.ui.theme.EditorBorder
import com.rapidraw.ui.theme.EditorSurface
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.TextSecondary
import com.rapidraw.ui.theme.TextTertiary

enum class MaskType(
    val label: String,
    val icon: ImageVector,
) {
    BRUSH("画笔", Icons.Default.Brush),
    AI_SEMANTIC("AI语义", Icons.Default.AutoAwesome),
    RADIAL("径向", Icons.Default.Circle),
    GRADIENT("渐变", Icons.Default.Flip),
}

enum class AiSubjectType(val label: String) {
    PORTRAIT("人像"),
    SKY("天空"),
    ARCHITECTURE("建筑"),
}

@Composable
fun MaskToolPanel(
    selectedMaskType: MaskType,
    onSelectMaskType: (MaskType) -> Unit,
    brushSize: Float,
    onBrushSizeChange: (Float) -> Unit,
    brushOpacity: Float,
    onBrushOpacityChange: (Float) -> Unit,
    brushHardness: Float,
    onBrushHardnessChange: (Float) -> Unit,
    isErasing: Boolean,
    onErasingChange: (Boolean) -> Unit,
    gradientOpacity: Float,
    onGradientOpacityChange: (Float) -> Unit,
    gradientFeather: Float,
    onGradientFeatherChange: (Float) -> Unit,
    maskVisible: Boolean,
    onMaskVisibleChange: (Boolean) -> Unit,
    maskInverted: Boolean,
    onMaskInvertedChange: (Boolean) -> Unit,
    flowMaskIntensity: Float,
    onFlowMaskIntensityChange: (Float) -> Unit,
    isAiProcessing: Boolean,
    onGenerateAiMask: () -> Unit,
    hasAiMaskResult: Boolean,
    onDeleteMask: () -> Unit,
    aiSubjectType: AiSubjectType,
    onAiSubjectTypeChange: (AiSubjectType) -> Unit,
    radialCenterX: Float,
    onRadialCenterXChange: (Float) -> Unit,
    radialCenterY: Float,
    onRadialCenterYChange: (Float) -> Unit,
    radialRadius: Float,
    onRadialRadiusChange: (Float) -> Unit,
    gradientAngle: Float,
    onGradientAngleChange: (Float) -> Unit,
    gradientMidpoint: Float,
    onGradientMidpointChange: (Float) -> Unit,
    isNetworkAvailable: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = EditorSurface,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            // ── Mask Type Chips ─────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MaskType.entries.forEach { type ->
                    val isSelected = type == selectedMaskType
                    val isAiOffline = type == MaskType.AI_SEMANTIC && !isNetworkAvailable
                    val chipColor = when {
                        isAiOffline -> EditorBorder
                        isSelected -> HasselbladOrange
                        else -> EditorBorder
                    }
                    val contentColor = when {
                        isAiOffline -> TextTertiary
                        isSelected -> EditorBackground
                        else -> TextSecondary
                    }
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(enabled = !isAiOffline) { onSelectMaskType(type) },
                        color = chipColor,
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 6.dp),
                        ) {
                            Icon(
                                imageVector = type.icon,
                                contentDescription = type.label,
                                tint = contentColor,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = if (isAiOffline) "需要网络连接" else type.label,
                                color = contentColor,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected && !isAiOffline) FontWeight.Medium else FontWeight.Normal,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // ── Type-specific Settings ──────────────────────────────
            when (selectedMaskType) {
                MaskType.BRUSH -> BrushSettings(
                    brushSize = brushSize,
                    onBrushSizeChange = onBrushSizeChange,
                    brushOpacity = brushOpacity,
                    onBrushOpacityChange = onBrushOpacityChange,
                    brushHardness = brushHardness,
                    onBrushHardnessChange = onBrushHardnessChange,
                    isErasing = isErasing,
                    onErasingChange = onErasingChange,
                )
                MaskType.GRADIENT -> GradientMaskSettings(
                    angle = gradientAngle,
                    onAngleChange = onGradientAngleChange,
                    midpoint = gradientMidpoint,
                    onMidpointChange = onGradientMidpointChange,
                    opacity = gradientOpacity,
                    onOpacityChange = onGradientOpacityChange,
                    feather = gradientFeather,
                    onFeatherChange = onGradientFeatherChange,
                )
                MaskType.RADIAL -> RadialMaskSettings(
                    centerX = radialCenterX,
                    onCenterXChange = onRadialCenterXChange,
                    centerY = radialCenterY,
                    onCenterYChange = onRadialCenterYChange,
                    radius = radialRadius,
                    onRadiusChange = onRadialRadiusChange,
                    opacity = gradientOpacity,
                    onOpacityChange = onGradientOpacityChange,
                    feather = gradientFeather,
                    onFeatherChange = onGradientFeatherChange,
                )
                MaskType.AI_SEMANTIC -> AiMaskSettings(
                    isProcessing = isAiProcessing,
                    onGenerate = onGenerateAiMask,
                    hasResult = hasAiMaskResult,
                    subjectType = aiSubjectType,
                    onSubjectTypeChange = onAiSubjectTypeChange,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // ── Common Controls ─────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Visibility toggle
                IconButton(
                    onClick = { onMaskVisibleChange(!maskVisible) },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = if (maskVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = if (maskVisible) "隐藏遮罩" else "显示遮罩",
                        tint = if (maskVisible) HasselbladOrange else TextTertiary,
                        modifier = Modifier.size(20.dp),
                    )
                }

                // Invert toggle
                Surface(
                    modifier = Modifier.clickable { onMaskInvertedChange(!maskInverted) },
                    color = if (maskInverted) HasselbladOrange else EditorBorder,
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Flip,
                            contentDescription = "反转遮罩",
                            tint = if (maskInverted) EditorBackground else TextSecondary,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "反转",
                            color = if (maskInverted) EditorBackground else TextSecondary,
                            fontSize = 12.sp,
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Delete mask
                IconButton(
                    onClick = onDeleteMask,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "删除遮罩",
                        tint = TextTertiary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            // Flow mask intensity
            HasselSlider(
                label = "强度",
                value = flowMaskIntensity,
                range = 0f..100f,
                onValueChange = onFlowMaskIntensityChange,
                defaultValue = 100f,
            )
        }
    }
}

@Composable
private fun BrushSettings(
    brushSize: Float,
    onBrushSizeChange: (Float) -> Unit,
    brushOpacity: Float,
    onBrushOpacityChange: (Float) -> Unit,
    brushHardness: Float,
    onBrushHardnessChange: (Float) -> Unit,
    isErasing: Boolean,
    onErasingChange: (Boolean) -> Unit,
) {
    Column {
        HasselSlider(
            label = "大小",
            value = brushSize,
            range = 1f..100f,
            onValueChange = onBrushSizeChange,
            defaultValue = 50f,
        )
        HasselSlider(
            label = "不透明",
            value = brushOpacity,
            range = 0f..100f,
            onValueChange = onBrushOpacityChange,
            defaultValue = 100f,
        )
        HasselSlider(
            label = "硬度",
            value = brushHardness,
            range = 0f..100f,
            onValueChange = onBrushHardnessChange,
            defaultValue = 50f,
        )

        // Paint / Erase toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (!isErasing) HasselbladOrange else EditorBorder)
                    .clickable { onErasingChange(false) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text(
                    text = "绘制",
                    color = if (!isErasing) EditorBackground else TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isErasing) HasselbladOrange else EditorBorder)
                    .clickable { onErasingChange(true) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text(
                    text = "擦除",
                    color = if (isErasing) EditorBackground else TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun RadialMaskSettings(
    centerX: Float,
    onCenterXChange: (Float) -> Unit,
    centerY: Float,
    onCenterYChange: (Float) -> Unit,
    radius: Float,
    onRadiusChange: (Float) -> Unit,
    opacity: Float,
    onOpacityChange: (Float) -> Unit,
    feather: Float,
    onFeatherChange: (Float) -> Unit,
) {
    Column {
        HasselSlider(
            label = "中心 X",
            value = centerX,
            range = 0f..100f,
            onValueChange = onCenterXChange,
            defaultValue = 50f,
        )
        HasselSlider(
            label = "中心 Y",
            value = centerY,
            range = 0f..100f,
            onValueChange = onCenterYChange,
            defaultValue = 50f,
        )
        HasselSlider(
            label = "半径",
            value = radius,
            range = 5f..100f,
            onValueChange = onRadiusChange,
            defaultValue = 50f,
        )
        HasselSlider(
            label = "不透明",
            value = opacity,
            range = 0f..100f,
            onValueChange = onOpacityChange,
            defaultValue = 100f,
        )
        HasselSlider(
            label = "羽化",
            value = feather,
            range = 0f..100f,
            onValueChange = onFeatherChange,
            defaultValue = 30f,
        )
    }
}

@Composable
private fun GradientMaskSettings(
    angle: Float,
    onAngleChange: (Float) -> Unit,
    midpoint: Float,
    onMidpointChange: (Float) -> Unit,
    opacity: Float,
    onOpacityChange: (Float) -> Unit,
    feather: Float,
    onFeatherChange: (Float) -> Unit,
) {
    Column {
        HasselSlider(
            label = "角度",
            value = angle,
            range = 0f..360f,
            onValueChange = onAngleChange,
            defaultValue = 0f,
        )
        HasselSlider(
            label = "中点",
            value = midpoint,
            range = 0f..100f,
            onValueChange = onMidpointChange,
            defaultValue = 50f,
        )
        HasselSlider(
            label = "不透明",
            value = opacity,
            range = 0f..100f,
            onValueChange = onOpacityChange,
            defaultValue = 100f,
        )
        HasselSlider(
            label = "羽化",
            value = feather,
            range = 0f..100f,
            onValueChange = onFeatherChange,
            defaultValue = 30f,
        )
    }
}

@Composable
private fun AiMaskSettings(
    isProcessing: Boolean,
    onGenerate: () -> Unit,
    hasResult: Boolean,
    subjectType: AiSubjectType,
    onSubjectTypeChange: (AiSubjectType) -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        // Subject type selector
        Text(
            text = "主体类型",
            color = TextSecondary,
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AiSubjectType.entries.forEach { type ->
                val isSelected = type == subjectType
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isSelected) HasselbladOrange else EditorBorder)
                        .clickable { onSubjectTypeChange(type) }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = type.label,
                        color = if (isSelected) EditorBackground else TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !isProcessing) { onGenerate() },
            color = if (isProcessing) EditorBorder else HasselbladOrange,
            shape = RoundedCornerShape(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = EditorBackground,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "生成中...",
                        color = EditorBackground,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.AutoFixHigh,
                        contentDescription = null,
                        tint = EditorBackground,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (hasResult) "重新生成遮罩" else "生成遮罩",
                        color = EditorBackground,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        if (hasResult) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "遮罩已生成，可在预览中查看",
                color = TextTertiary,
                fontSize = 11.sp,
            )
        }
    }
}

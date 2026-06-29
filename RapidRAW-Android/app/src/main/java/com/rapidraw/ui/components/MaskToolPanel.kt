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
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Gradient
import androidx.compose.material.icons.filled.Lens
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tune
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
    AI_SUBJECT("AI 主体", Icons.Default.AutoFixHigh),
    AI_SKY("AI 天空", Icons.Default.AutoFixHigh),
    BRUSH("画笔", Icons.Default.Brush),
    LINEAR_GRADIENT("线性渐变", Icons.Default.Gradient),
    RADIAL_GRADIENT("径向渐变", Icons.Default.Lens),
    COLOR_RANGE("色彩范围", Icons.Default.Palette),
    LUMINANCE_RANGE("亮度范围", Icons.Default.Tune),
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
    // ── 线性渐变参数 ──────────────────────────────────────────────
    gradientStartX: Float = 0f,
    onGradientStartXChange: (Float) -> Unit = {},
    gradientStartY: Float = 0f,
    onGradientStartYChange: (Float) -> Unit = {},
    gradientEndX: Float = 100f,
    onGradientEndXChange: (Float) -> Unit = {},
    gradientEndY: Float = 100f,
    onGradientEndYChange: (Float) -> Unit = {},
    // ── 径向渐变参数 ──────────────────────────────────────────────
    radialCenterX: Float = 50f,
    onRadialCenterXChange: (Float) -> Unit = {},
    radialCenterY: Float = 50f,
    onRadialCenterYChange: (Float) -> Unit = {},
    radialRadius: Float = 50f,
    onRadialRadiusChange: (Float) -> Unit = {},
    radialAspectRatio: Float = 100f,
    onRadialAspectRatioChange: (Float) -> Unit = {},
    radialRotation: Float = 0f,
    onRadialRotationChange: (Float) -> Unit = {},
    // ── 色彩范围参数 ──────────────────────────────────────────────
    colorRangeHue: Float = 0f,
    onColorRangeHueChange: (Float) -> Unit = {},
    colorRangeHueTolerance: Float = 30f,
    onColorRangeHueToleranceChange: (Float) -> Unit = {},
    colorRangeSatMin: Float = 10f,
    onColorRangeSatMinChange: (Float) -> Unit = {},
    colorRangeSatMax: Float = 100f,
    onColorRangeSatMaxChange: (Float) -> Unit = {},
    colorRangeLumMin: Float = 0f,
    onColorRangeLumMinChange: (Float) -> Unit = {},
    colorRangeLumMax: Float = 100f,
    onColorRangeLumMaxChange: (Float) -> Unit = {},
    colorRangeFeather: Float = 10f,
    onColorRangeFeatherChange: (Float) -> Unit = {},
    // ── 亮度范围参数 ──────────────────────────────────────────────
    luminanceRangeMin: Float = 0f,
    onLuminanceRangeMinChange: (Float) -> Unit = {},
    luminanceRangeMax: Float = 100f,
    onLuminanceRangeMaxChange: (Float) -> Unit = {},
    luminanceRangeFeather: Float = 5f,
    onLuminanceRangeFeatherChange: (Float) -> Unit = {},
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
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                MaskType.entries.forEach { type ->
                    val isSelected = type == selectedMaskType
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelectMaskType(type) },
                        color = if (isSelected) HasselbladOrange else EditorBorder,
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 4.dp),
                        ) {
                            Icon(
                                imageVector = type.icon,
                                contentDescription = type.label,
                                tint = if (isSelected) EditorBackground else TextSecondary,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = type.label,
                                color = if (isSelected) EditorBackground else TextSecondary,
                                fontSize = 8.sp,
                                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
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
                MaskType.LINEAR_GRADIENT -> LinearGradientSettings(
                    opacity = gradientOpacity,
                    onOpacityChange = onGradientOpacityChange,
                    feather = gradientFeather,
                    onFeatherChange = onGradientFeatherChange,
                    startX = gradientStartX,
                    onStartXChange = onGradientStartXChange,
                    startY = gradientStartY,
                    onStartYChange = onGradientStartYChange,
                    endX = gradientEndX,
                    onEndXChange = onGradientEndXChange,
                    endY = gradientEndY,
                    onEndYChange = onGradientEndYChange,
                )
                MaskType.RADIAL_GRADIENT -> RadialGradientSettings(
                    opacity = gradientOpacity,
                    onOpacityChange = onGradientOpacityChange,
                    feather = gradientFeather,
                    onFeatherChange = onGradientFeatherChange,
                    centerX = radialCenterX,
                    onCenterXChange = onRadialCenterXChange,
                    centerY = radialCenterY,
                    onCenterYChange = onRadialCenterYChange,
                    radius = radialRadius,
                    onRadiusChange = onRadialRadiusChange,
                    aspectRatio = radialAspectRatio,
                    onAspectRatioChange = onRadialAspectRatioChange,
                    rotation = radialRotation,
                    onRotationChange = onRadialRotationChange,
                )
                MaskType.COLOR_RANGE -> ColorRangeSettings(
                    hue = colorRangeHue,
                    onHueChange = onColorRangeHueChange,
                    hueTolerance = colorRangeHueTolerance,
                    onHueToleranceChange = onColorRangeHueToleranceChange,
                    satMin = colorRangeSatMin,
                    onSatMinChange = onColorRangeSatMinChange,
                    satMax = colorRangeSatMax,
                    onSatMaxChange = onColorRangeSatMaxChange,
                    lumMin = colorRangeLumMin,
                    onLumMinChange = onColorRangeLumMinChange,
                    lumMax = colorRangeLumMax,
                    onLumMaxChange = onColorRangeLumMaxChange,
                    feather = colorRangeFeather,
                    onFeatherChange = onColorRangeFeatherChange,
                )
                MaskType.LUMINANCE_RANGE -> LuminanceRangeSettings(
                    lumMin = luminanceRangeMin,
                    onLumMinChange = onLuminanceRangeMinChange,
                    lumMax = luminanceRangeMax,
                    onLumMaxChange = onLuminanceRangeMaxChange,
                    feather = luminanceRangeFeather,
                    onFeatherChange = onLuminanceRangeFeatherChange,
                )
                MaskType.AI_SUBJECT, MaskType.AI_SKY -> AiMaskSettings(
                    isProcessing = isAiProcessing,
                    onGenerate = onGenerateAiMask,
                    hasResult = hasAiMaskResult,
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

// ═══════════════════════════════════════════════════════════════════
// Brush Settings
// ═══════════════════════════════════════════════════════════════════

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

// ═══════════════════════════════════════════════════════════════════
// Linear Gradient Settings
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun LinearGradientSettings(
    opacity: Float,
    onOpacityChange: (Float) -> Unit,
    feather: Float,
    onFeatherChange: (Float) -> Unit,
    startX: Float,
    onStartXChange: (Float) -> Unit,
    startY: Float,
    onStartYChange: (Float) -> Unit,
    endX: Float,
    onEndXChange: (Float) -> Unit,
    endY: Float,
    onEndYChange: (Float) -> Unit,
) {
    Column {
        // 渐变方向控制：起点/终点坐标（百分比 0-100）
        Text(
            text = "渐变方向",
            color = TextSecondary,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 16.dp, top = 4.dp, bottom = 2.dp),
        )
        HasselSlider(
            label = "起点X",
            value = startX,
            range = 0f..100f,
            onValueChange = onStartXChange,
            defaultValue = 0f,
        )
        HasselSlider(
            label = "起点Y",
            value = startY,
            range = 0f..100f,
            onValueChange = onStartYChange,
            defaultValue = 0f,
        )
        HasselSlider(
            label = "终点X",
            value = endX,
            range = 0f..100f,
            onValueChange = onEndXChange,
            defaultValue = 100f,
        )
        HasselSlider(
            label = "终点Y",
            value = endY,
            range = 0f..100f,
            onValueChange = onEndYChange,
            defaultValue = 100f,
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
            defaultValue = 50f,
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
// Radial Gradient Settings
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun RadialGradientSettings(
    opacity: Float,
    onOpacityChange: (Float) -> Unit,
    feather: Float,
    onFeatherChange: (Float) -> Unit,
    centerX: Float,
    onCenterXChange: (Float) -> Unit,
    centerY: Float,
    onCenterYChange: (Float) -> Unit,
    radius: Float,
    onRadiusChange: (Float) -> Unit,
    aspectRatio: Float,
    onAspectRatioChange: (Float) -> Unit,
    rotation: Float,
    onRotationChange: (Float) -> Unit,
) {
    Column {
        Text(
            text = "径向参数",
            color = TextSecondary,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 16.dp, top = 4.dp, bottom = 2.dp),
        )
        HasselSlider(
            label = "中心X",
            value = centerX,
            range = 0f..100f,
            onValueChange = onCenterXChange,
            defaultValue = 50f,
        )
        HasselSlider(
            label = "中心Y",
            value = centerY,
            range = 0f..100f,
            onValueChange = onCenterYChange,
            defaultValue = 50f,
        )
        HasselSlider(
            label = "半径",
            value = radius,
            range = 1f..100f,
            onValueChange = onRadiusChange,
            defaultValue = 50f,
        )
        HasselSlider(
            label = "宽高比",
            value = aspectRatio,
            range = 10f..300f,
            onValueChange = onAspectRatioChange,
            defaultValue = 100f,
        )
        HasselSlider(
            label = "旋转",
            value = rotation,
            range = 0f..360f,
            onValueChange = onRotationChange,
            defaultValue = 0f,
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
            defaultValue = 50f,
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
// Color Range Settings
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun ColorRangeSettings(
    hue: Float,
    onHueChange: (Float) -> Unit,
    hueTolerance: Float,
    onHueToleranceChange: (Float) -> Unit,
    satMin: Float,
    onSatMinChange: (Float) -> Unit,
    satMax: Float,
    onSatMaxChange: (Float) -> Unit,
    lumMin: Float,
    onLumMinChange: (Float) -> Unit,
    lumMax: Float,
    onLumMaxChange: (Float) -> Unit,
    feather: Float,
    onFeatherChange: (Float) -> Unit,
) {
    Column {
        Text(
            text = "色彩范围",
            color = TextSecondary,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 16.dp, top = 4.dp, bottom = 2.dp),
        )
        // 色相环选择器
        HasselSlider(
            label = "色相",
            value = hue,
            range = 0f..360f,
            onValueChange = onHueChange,
            defaultValue = 0f,
        )
        HasselSlider(
            label = "容差",
            value = hueTolerance,
            range = 1f..180f,
            onValueChange = onHueToleranceChange,
            defaultValue = 30f,
        )
        HasselSlider(
            label = "饱和↓",
            value = satMin,
            range = 0f..100f,
            onValueChange = onSatMinChange,
            defaultValue = 10f,
        )
        HasselSlider(
            label = "饱和↑",
            value = satMax,
            range = 0f..100f,
            onValueChange = onSatMaxChange,
            defaultValue = 100f,
        )
        HasselSlider(
            label = "亮度↓",
            value = lumMin,
            range = 0f..100f,
            onValueChange = onLumMinChange,
            defaultValue = 0f,
        )
        HasselSlider(
            label = "亮度↑",
            value = lumMax,
            range = 0f..100f,
            onValueChange = onLumMaxChange,
            defaultValue = 100f,
        )
        HasselSlider(
            label = "羽化",
            value = feather,
            range = 0f..100f,
            onValueChange = onFeatherChange,
            defaultValue = 10f,
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
// Luminance Range Settings
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun LuminanceRangeSettings(
    lumMin: Float,
    onLumMinChange: (Float) -> Unit,
    lumMax: Float,
    onLumMaxChange: (Float) -> Unit,
    feather: Float,
    onFeatherChange: (Float) -> Unit,
) {
    Column {
        Text(
            text = "亮度范围",
            color = TextSecondary,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 16.dp, top = 4.dp, bottom = 2.dp),
        )
        HasselSlider(
            label = "亮度↓",
            value = lumMin,
            range = 0f..100f,
            onValueChange = onLumMinChange,
            defaultValue = 0f,
        )
        HasselSlider(
            label = "亮度↑",
            value = lumMax,
            range = 0f..100f,
            onValueChange = onLumMaxChange,
            defaultValue = 100f,
        )
        HasselSlider(
            label = "羽化",
            value = feather,
            range = 0f..50f,
            onValueChange = onFeatherChange,
            defaultValue = 5f,
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
// Gradient Settings (shared for LINEAR/RADIAL - legacy compatibility)
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun GradientSettings(
    gradientOpacity: Float,
    onGradientOpacityChange: (Float) -> Unit,
    gradientFeather: Float,
    onGradientFeatherChange: (Float) -> Unit,
) {
    Column {
        HasselSlider(
            label = "不透明",
            value = gradientOpacity,
            range = 0f..100f,
            onValueChange = onGradientOpacityChange,
            defaultValue = 100f,
        )
        HasselSlider(
            label = "羽化",
            value = gradientFeather,
            range = 0f..100f,
            onValueChange = onGradientFeatherChange,
            defaultValue = 50f,
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
// AI Mask Settings
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun AiMaskSettings(
    isProcessing: Boolean,
    onGenerate: () -> Unit,
    hasResult: Boolean,
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
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

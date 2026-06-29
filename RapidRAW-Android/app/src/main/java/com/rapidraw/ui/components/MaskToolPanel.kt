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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.rapidraw.ui.theme.EditorTypography

enum class MaskType(
    val label: String,
    val icon: ImageVector,
) {
    AI_SUBJECT("AI 主体", Icons.Default.AutoFixHigh),
    AI_SKY("AI 天空", Icons.Default.AutoFixHigh),
    BRUSH("画笔", Icons.Default.Brush),
    LINEAR_GRADIENT("线性渐变", Icons.Default.Flip),
    RADIAL_GRADIENT("径向渐变", Icons.Default.Flip),
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
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelectMaskType(type) },
                        color = if (isSelected) HasselbladOrange else EditorBorder,
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 6.dp),
                        ) {
                            Icon(
                                imageVector = type.icon,
                                contentDescription = type.label,
                                tint = if (isSelected) EditorBackground else TextSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = type.label,
                                color = if (isSelected) EditorBackground else TextSecondary,
                                style = EditorTypography.badge,
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
                MaskType.LINEAR_GRADIENT, MaskType.RADIAL_GRADIENT -> GradientSettings(
                    gradientOpacity = gradientOpacity,
                    onGradientOpacityChange = onGradientOpacityChange,
                    gradientFeather = gradientFeather,
                    onGradientFeatherChange = onGradientFeatherChange,
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
                            color = if (isSelected) EditorBackground else TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
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
                    style = MaterialTheme.typography.bodySmall,
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
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

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
                        style = MaterialTheme.typography.bodyMedium,
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
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        if (hasResult) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "遮罩已生成，可在预览中查看",
                color = TextTertiary,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

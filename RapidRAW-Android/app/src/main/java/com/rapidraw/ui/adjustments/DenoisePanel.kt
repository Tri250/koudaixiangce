package com.rapidraw.ui.adjustments

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rapidraw.data.model.Adjustments
import com.rapidraw.data.model.DenoiseMode
import com.rapidraw.ui.components.BeforeAfterCompare
import com.rapidraw.ui.components.HasselSlider
import com.rapidraw.ui.theme.EditorBorder
import com.rapidraw.ui.theme.EditorSurface
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.HasselbladOrangeBright
import com.rapidraw.ui.theme.TextPrimary
import com.rapidraw.ui.theme.TextSecondary
import com.rapidraw.ui.theme.TextTertiary

/**
 * 降噪调节面板
 * 展示降噪选项：算法选择器、参数调节滑块、前后对比预览
 */
@Composable
fun DenoisePanel(
    adjustments: Adjustments,
    onUpdate: (String, Float) -> Unit,
    onModeChange: (DenoiseMode) -> Unit,
    beforeBitmap: android.graphics.Bitmap? = null,
    afterBitmap: android.graphics.Bitmap? = null,
) {
    var denoiseExpanded by remember { mutableStateOf(true) }
    var advancedExpanded by remember { mutableStateOf(false) }
    var compareExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        // ── 降噪模式选择 ──────────────────────────────────────────
        CollapsibleSection(
            title = "降噪",
            expanded = denoiseExpanded,
            onToggle = { denoiseExpanded = !denoiseExpanded },
        ) {
            // 算法选择器
            DenoiseModeSelector(
                currentMode = adjustments.denoiseMode,
                onModeChange = onModeChange,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 降噪强度滑块
            HasselSlider(
                label = "强度",
                value = adjustments.denoiseStrength,
                range = 0f..100f,
                onValueChange = { onUpdate("denoiseStrength", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )

            // 窗口大小滑块（仅MEAN/MEDIAN/GAUSSIAN模式）
            if (adjustments.denoiseMode != DenoiseMode.AI) {
                HasselSlider(
                    label = "窗口",
                    value = adjustments.denoiseWindowSize.toFloat(),
                    range = 3f..7f,
                    onValueChange = { onUpdate("denoiseWindowSize", it) },
                    defaultValue = 3f,
                    stepSize = 2f,  // 只支持 3, 5, 7
                    format = { v -> "${v.toInt()}" },
                )
            }

            // 高斯 sigma 滑块（仅GAUSSIAN模式）
            if (adjustments.denoiseMode == DenoiseMode.GAUSSIAN) {
                HasselSlider(
                    label = "Sigma",
                    value = adjustments.gaussianSigma,
                    range = 0.5f..5.0f,
                    onValueChange = { onUpdate("gaussianSigma", it) },
                    defaultValue = 1.0f,
                    stepSize = 0.1f,
                    format = { v -> String.format("%.1f", v) },
                )
            }
        }

        // ── 高级选项 ──────────────────────────────────────────
        CollapsibleSection(
            title = "高级降噪",
            expanded = advancedExpanded,
            onToggle = { advancedExpanded = !advancedExpanded },
        ) {
            // AI模式的亮度降噪（保留原有功能）
            HasselSlider(
                label = "亮度降噪",
                value = adjustments.lumaNoiseReduction,
                range = 0f..100f,
                onValueChange = { onUpdate("lumaNoiseReduction", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )

            // AI模式的色彩降噪（保留原有功能）
            HasselSlider(
                label = "色彩降噪",
                value = adjustments.colorNoiseReduction,
                range = 0f..100f,
                onValueChange = { onUpdate("colorNoiseReduction", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )

            // 算法说明
            AlgorithmDescription(mode = adjustments.denoiseMode)
        }

        // ── 前后对比预览 ──────────────────────────────────────────
        if (beforeBitmap != null && afterBitmap != null) {
            CollapsibleSection(
                title = "前后对比",
                expanded = compareExpanded,
                onToggle = { compareExpanded = !compareExpanded },
            ) {
                BeforeAfterCompare(
                    beforeImage = beforeBitmap,
                    afterImage = afterBitmap,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(EditorSurface),
                )
            }
        }
    }
}

/**
 * 降噪模式选择器
 * 提供四种降噪算法的快速切换
 */
@Composable
fun DenoiseModeSelector(
    currentMode: DenoiseMode,
    onModeChange: (DenoiseMode) -> Unit,
) {
    val modes = listOf(
        DenoiseMode.AI to "AI保边",
        DenoiseMode.MEAN to "均值",
        DenoiseMode.MEDIAN to "中值",
        DenoiseMode.GAUSSIAN to "高斯",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        modes.forEach { (mode, label) ->
            val isSelected = currentMode == mode
            val backgroundColor = if (isSelected) HasselbladOrangeBright else EditorSurface
            val textColor = if (isSelected) TextPrimary else TextSecondary
            val borderColor = if (isSelected) HasselbladOrange else EditorBorder

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(backgroundColor)
                    .border(1.dp, borderColor, RoundedCornerShape(4.dp))
                    .clickable { onModeChange(mode) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = textColor,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

/**
 * 算法说明文本
 * 根据当前选择的降噪模式显示相应的说明
 */
@Composable
fun AlgorithmDescription(mode: DenoiseMode) {
    val description = when (mode) {
        DenoiseMode.AI -> "AI保边降噪：使用引导滤波实现边缘保护，无ML模型依赖。适合保留细节的同时平滑噪声。"
        DenoiseMode.MEAN -> "均值滤波：计算窗口内像素平均值。适合处理高斯噪声，但会损失细节。"
        DenoiseMode.MEDIAN -> "中值滤波：取窗口内像素中值。最适合去除椒盐噪声，边缘保留较好。"
        DenoiseMode.GAUSSIAN -> "高斯滤波：使用高斯核加权平均。平滑效果自然，细节保留比均值滤波好。"
    }

    Text(
        text = description,
        color = TextTertiary,
        fontSize = 11.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
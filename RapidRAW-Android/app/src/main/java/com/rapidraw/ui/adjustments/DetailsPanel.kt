package com.rapidraw.ui.adjustments

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rapidraw.data.model.Adjustments
import com.rapidraw.data.model.DenoiseMode
import com.rapidraw.ui.components.HasselSlider

@Composable
fun DetailsPanel(
    adjustments: Adjustments,
    onUpdate: (String, Float) -> Unit,
    onDenoiseModeChange: (DenoiseMode) -> Unit = {},
    beforeBitmap: android.graphics.Bitmap? = null,
    afterBitmap: android.graphics.Bitmap? = null,
) {
    var sharpenExpanded by remember { mutableStateOf(true) }
    var denoiseExpanded by remember { mutableStateOf(true) }
    var localContrastExpanded by remember { mutableStateOf(false) }
    var dehazeExpanded by remember { mutableStateOf(false) }
    var chromaticExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        // ── 锐化 ──────────────────────────────────────────
        CollapsibleSection(
            title = "锐化",
            expanded = sharpenExpanded,
            onToggle = { sharpenExpanded = !sharpenExpanded },
        ) {
            HasselSlider(
                label = "锐化",
                value = adjustments.sharpness,
                range = 0f..150f,
                onValueChange = { onUpdate("sharpness", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
        }

        // ── 降噪（传统降噪 + AI降噪）──────────────────────
        CollapsibleSection(
            title = "降噪",
            expanded = denoiseExpanded,
            onToggle = { denoiseExpanded = !denoiseExpanded },
        ) {
            // 降噪模式选择器
            DenoiseModeSelector(
                currentMode = adjustments.denoiseMode,
                onModeChange = onDenoiseModeChange,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 降噪强度
            HasselSlider(
                label = "降噪强度",
                value = adjustments.denoiseStrength,
                range = 0f..100f,
                onValueChange = { onUpdate("denoiseStrength", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )

            // 窗口大小（仅传统降噪模式）
            if (adjustments.denoiseMode != DenoiseMode.AI) {
                HasselSlider(
                    label = "窗口大小",
                    value = adjustments.denoiseWindowSize.toFloat(),
                    range = 3f..7f,
                    onValueChange = { onUpdate("denoiseWindowSize", it) },
                    defaultValue = 3f,
                    stepSize = 2f,
                    format = { v -> "${v.toInt()}" },
                )
            }

            // 高斯 sigma（仅GAUSSIAN模式）
            if (adjustments.denoiseMode == DenoiseMode.GAUSSIAN) {
                HasselSlider(
                    label = "高斯Sigma",
                    value = adjustments.gaussianSigma,
                    range = 0.5f..5.0f,
                    onValueChange = { onUpdate("gaussianSigma", it) },
                    defaultValue = 1.0f,
                    stepSize = 0.1f,
                    format = { v -> String.format("%.1f", v) },
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // AI降噪原有参数（亮度降噪、色彩降噪）
            HasselSlider(
                label = "亮度降噪",
                value = adjustments.lumaNoiseReduction,
                range = 0f..100f,
                onValueChange = { onUpdate("lumaNoiseReduction", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
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

        // ── 局部对比 ──────────────────────────────────────
        CollapsibleSection(
            title = "局部对比",
            expanded = localContrastExpanded,
            onToggle = { localContrastExpanded = !localContrastExpanded },
        ) {
            HasselSlider(
                label = "清晰度",
                value = adjustments.clarity,
                range = -100f..100f,
                onValueChange = { onUpdate("clarity", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
            HasselSlider(
                label = "结构",
                value = adjustments.structure,
                range = -100f..100f,
                onValueChange = { onUpdate("structure", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
            HasselSlider(
                label = "中心",
                value = adjustments.centre,
                range = -100f..100f,
                onValueChange = { onUpdate("centre", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
        }

        // ── 去雾 ──────────────────────────────────────────
        CollapsibleSection(
            title = "去雾",
            expanded = dehazeExpanded,
            onToggle = { dehazeExpanded = !dehazeExpanded },
        ) {
            HasselSlider(
                label = "去雾",
                value = adjustments.dehaze,
                range = -100f..100f,
                onValueChange = { onUpdate("dehaze", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
        }

        // ── 差 ──────────────────────────────────────────
        CollapsibleSection(
            title = "色差",
            expanded = chromaticExpanded,
            onToggle = { chromaticExpanded = !chromaticExpanded },
        ) {
            HasselSlider(
                label = "红/青",
                value = adjustments.chromaticAberrationRedCyan,
                range = -100f..100f,
                onValueChange = { onUpdate("chromaticAberrationRedCyan", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
            HasselSlider(
                label = "蓝/黄",
                value = adjustments.chromaticAberrationBlueYellow,
                range = -100f..100f,
                onValueChange = { onUpdate("chromaticAberrationBlueYellow", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
        }
    }
}

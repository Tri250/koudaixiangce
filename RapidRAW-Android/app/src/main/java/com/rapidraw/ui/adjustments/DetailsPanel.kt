package com.rapidraw.ui.adjustments

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.rapidraw.data.model.Adjustments
import com.rapidraw.ui.components.HasselSlider

@Composable
fun DetailsPanel(
    adjustments: Adjustments,
    onUpdate: (String, Float) -> Unit,
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

        // ── 降噪 ──────────────────────────────────────────
        CollapsibleSection(
            title = "降噪",
            expanded = denoiseExpanded,
            onToggle = { denoiseExpanded = !denoiseExpanded },
        ) {
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

        // ── 色差 ──────────────────────────────────────────
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

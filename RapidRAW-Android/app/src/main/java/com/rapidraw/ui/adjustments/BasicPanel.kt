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
fun BasicPanel(
    adjustments: Adjustments,
    onUpdate: (String, Float) -> Unit,
) {
    var basicExpanded by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        CollapsibleSection(
            title = "基础",
            expanded = basicExpanded,
            onToggle = { basicExpanded = !basicExpanded },
        ) {
            HasselSlider(
                label = "曝光",
                value = adjustments.exposure,
                range = -5f..5f,
                onValueChange = { onUpdate("exposure", it) },
                defaultValue = 0f,
                stepSize = 0.01f,
                format = { v -> String.format("%.2f", v) },
            )
            HasselSlider(
                label = "亮度",
                value = adjustments.brightness,
                range = -5f..5f,
                onValueChange = { onUpdate("brightness", it) },
                defaultValue = 0f,
                stepSize = 0.01f,
                format = { v -> String.format("%.2f", v) },
            )
            HasselSlider(
                label = "对比",
                value = adjustments.contrast,
                range = -100f..100f,
                onValueChange = { onUpdate("contrast", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
            HasselSlider(
                label = "高光",
                value = adjustments.highlights,
                range = -150f..150f,
                onValueChange = { onUpdate("highlights", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
            HasselSlider(
                label = "阴影",
                value = adjustments.shadows,
                range = -100f..100f,
                onValueChange = { onUpdate("shadows", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
            HasselSlider(
                label = "白色",
                value = adjustments.whites,
                range = -30f..30f,
                onValueChange = { onUpdate("whites", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
            HasselSlider(
                label = "黑色",
                value = adjustments.blacks,
                range = -60f..60f,
                onValueChange = { onUpdate("blacks", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
        }
    }
}

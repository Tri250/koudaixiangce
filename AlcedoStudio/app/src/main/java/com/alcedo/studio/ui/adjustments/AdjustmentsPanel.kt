package com.alcedo.studio.ui.adjustments

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alcedo.studio.data.model.Adjustments

@Composable
fun LightAdjustmentsPanel(
    adjustments: Adjustments,
    onAdjustmentsChange: (Adjustments) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(16.dp)) {
        Text("基础调整", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        AdjustmentSlider("曝光", adjustments.exposure, -2f, 2f) {
            onAdjustmentsChange(adjustments.copy(exposure = it))
        }
        AdjustmentSlider("对比度", adjustments.contrast, -100f, 100f) {
            onAdjustmentsChange(adjustments.copy(contrast = it))
        }
        AdjustmentSlider("高光", adjustments.highlights, -100f, 100f) {
            onAdjustmentsChange(adjustments.copy(highlights = it))
        }
        AdjustmentSlider("阴影", adjustments.shadows, -100f, 100f) {
            onAdjustmentsChange(adjustments.copy(shadows = it))
        }
        AdjustmentSlider("白色色阶", adjustments.whites, -100f, 100f) {
            onAdjustmentsChange(adjustments.copy(whites = it))
        }
        AdjustmentSlider("黑色色阶", adjustments.blacks, -100f, 100f) {
            onAdjustmentsChange(adjustments.copy(blacks = it))
        }
    }
}

@Composable
fun ColorAdjustmentsPanel(
    adjustments: Adjustments,
    onAdjustmentsChange: (Adjustments) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(16.dp)) {
        Text("色彩调整", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        AdjustmentSlider("色温", adjustments.temperature, -100f, 100f) {
            onAdjustmentsChange(adjustments.copy(temperature = it))
        }
        AdjustmentSlider("色调", adjustments.tint, -100f, 100f) {
            onAdjustmentsChange(adjustments.copy(tint = it))
        }
        AdjustmentSlider("自然饱和度", adjustments.vibrance, -100f, 100f) {
            onAdjustmentsChange(adjustments.copy(vibrance = it))
        }
        AdjustmentSlider("饱和度", adjustments.saturation, -100f, 100f) {
            onAdjustmentsChange(adjustments.copy(saturation = it))
        }
    }
}

@Composable
fun EffectsAdjustmentsPanel(
    adjustments: Adjustments,
    onAdjustmentsChange: (Adjustments) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(16.dp)) {
        Text("效果", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        AdjustmentSlider("清晰度", adjustments.clarity, -100f, 100f) {
            onAdjustmentsChange(adjustments.copy(clarity = it))
        }
        AdjustmentSlider("去朦胧", adjustments.dehaze, -100f, 100f) {
            onAdjustmentsChange(adjustments.copy(dehaze = it))
        }
        AdjustmentSlider("晕影", adjustments.vignetteAmount, -100f, 100f) {
            onAdjustmentsChange(adjustments.copy(vignetteAmount = it))
        }
        AdjustmentSlider("颗粒", adjustments.grainAmount, 0f, 100f) {
            onAdjustmentsChange(adjustments.copy(grainAmount = it))
        }
    }
}

@Composable
fun DetailsAdjustmentsPanel(
    adjustments: Adjustments,
    onAdjustmentsChange: (Adjustments) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(16.dp)) {
        Text("细节", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        AdjustmentSlider("锐化", adjustments.sharpness, 0f, 150f) {
            onAdjustmentsChange(adjustments.copy(sharpness = it))
        }
        AdjustmentSlider("降噪", adjustments.lumaNoiseReduction, 0f, 100f) {
            onAdjustmentsChange(adjustments.copy(lumaNoiseReduction = it))
        }
        AdjustmentSlider("颜色降噪", adjustments.colorNoiseReduction, 0f, 100f) {
            onAdjustmentsChange(adjustments.copy(colorNoiseReduction = it))
        }
    }
}

@Composable
fun GeometryAdjustmentsPanel(
    adjustments: Adjustments,
    onAdjustmentsChange: (Adjustments) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(16.dp)) {
        Text("几何", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        AdjustmentSlider("旋转", adjustments.rotation, -180f, 180f) {
            onAdjustmentsChange(adjustments.copy(rotation = it))
        }
        AdjustmentSlider("垂直透视", adjustments.transformVertical, -100f, 100f) {
            onAdjustmentsChange(adjustments.copy(transformVertical = it))
        }
        AdjustmentSlider("水平透视", adjustments.transformHorizontal, -100f, 100f) {
            onAdjustmentsChange(adjustments.copy(transformHorizontal = it))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdjustmentSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text("$label: ${value.toInt()}", style = MaterialTheme.typography.bodySmall)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

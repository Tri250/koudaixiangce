package com.rapidraw.ui.adjustments

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rapidraw.data.model.Adjustments
import com.rapidraw.ui.components.HasselSlider
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.TextSecondary

private data class QuickParamDef(
    val label: String,
    val key: String,
    val range: ClosedFloatingPointRange<Float>,
    val stepSize: Float,
    val defaultValue: Float,
    val format: (Float) -> String,
)

private val QUICK_PARAMS = listOf(
    QuickParamDef(
        label = "强度",
        key = "filmIntensity",
        range = 0f..1f,
        stepSize = 0.01f,
        defaultValue = 1f,
        format = { v -> String.format("%.2f", v) },
    ),
    QuickParamDef(
        label = "柔光",
        key = "softGlow",
        range = 0f..1f,
        stepSize = 0.01f,
        defaultValue = 0f,
        format = { v -> String.format("%.2f", v) },
    ),
    QuickParamDef(
        label = "影调",
        key = "toneLevel",
        range = -1f..1f,
        stepSize = 0.01f,
        defaultValue = 0f,
        format = { v -> String.format("%.2f", v) },
    ),
    QuickParamDef(
        label = "饱和度",
        key = "saturation",
        range = -100f..100f,
        stepSize = 1f,
        defaultValue = 0f,
        format = { v -> v.toInt().toString() },
    ),
    QuickParamDef(
        label = "冷暖",
        key = "temperature",
        range = -100f..100f,
        stepSize = 1f,
        defaultValue = 0f,
        format = { v -> v.toInt().toString() },
    ),
    QuickParamDef(
        label = "青品",
        key = "greenMagenta",
        range = -1f..1f,
        stepSize = 0.01f,
        defaultValue = 0f,
        format = { v -> String.format("%.2f", v) },
    ),
    QuickParamDef(
        label = "锐度",
        key = "sharpness",
        range = 0f..100f,
        stepSize = 1f,
        defaultValue = 0f,
        format = { v -> v.toInt().toString() },
    ),
    QuickParamDef(
        label = "暗角",
        key = "vignetteAmount",
        range = 0f..100f,
        stepSize = 1f,
        defaultValue = 0f,
        format = { v -> v.toInt().toString() },
    ),
    QuickParamDef(
        label = "去雾",
        key = "dehaze",
        range = 0f..100f,
        stepSize = 1f,
        defaultValue = 0f,
        format = { v -> v.toInt().toString() },
    ),
)

private fun getQuickValue(adjustments: Adjustments, key: String): Float {
    return when (key) {
        "filmIntensity" -> adjustments.filmIntensity
        "softGlow"      -> adjustments.softGlow
        "toneLevel"     -> adjustments.toneLevel
        "saturation"    -> adjustments.saturation
        "temperature"   -> adjustments.temperature
        "greenMagenta"  -> adjustments.greenMagenta
        "sharpness"     -> adjustments.sharpness.coerceIn(0f, 100f)
        "vignetteAmount" -> adjustments.vignetteAmount.coerceIn(0f, 100f)
        "dehaze"        -> adjustments.dehaze.coerceIn(0f, 100f)
        else            -> 0f
    }
}

@Composable
fun QuickAdjustPanel(
    adjustments: Adjustments,
    onUpdate: (String, Float) -> Unit,
    onAdvancedClick: () -> Unit,
    onChannelMixerClick: () -> Unit = {},
    onSplitToningClick: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        QUICK_PARAMS.forEach { param ->
            HasselSlider(
                label = param.label,
                value = getQuickValue(adjustments, param.key),
                range = param.range,
                onValueChange = { onUpdate(param.key, it) },
                defaultValue = param.defaultValue,
                stepSize = param.stepSize,
                format = param.format,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onAdvancedClick() }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "高级调整 →",
                color = TextSecondary,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onChannelMixerClick() }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "通道混合器 →",
                color = HasselbladOrange,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSplitToningClick() }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "分色调 →",
                color = HasselbladOrange,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

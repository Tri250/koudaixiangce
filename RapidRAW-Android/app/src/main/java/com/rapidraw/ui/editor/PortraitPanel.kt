package com.rapidraw.ui.editor

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rapidraw.core.PortraitRetoucher
import com.rapidraw.ui.theme.ColorOS16Colors
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.HasselbladOrangeBright

/**
 * 人像精修面板 — 瘦脸/大眼/瘦鼻/瘦身/拉腿
 * 五档调节 + 实时预览 + 一键应用
 */
@Composable
fun PortraitPanel(
    params: PortraitRetoucher.RetouchParams = PortraitRetoucher.RetouchParams(),
    onParamsChange: (PortraitRetoucher.RetouchParams) -> Unit = {},
    onApply: () -> Unit = {},
    onReset: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── 标题 ──
        Text(
            text = "人像精修",
            color = ColorOS16Colors.TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )

        // ── 面部调整 ──
        SectionHeader("面部")

        PortraitSlider(
            label = "瘦脸",
            value = params.faceSlimStrength,
            onValueChange = { onParamsChange(params.copy(faceSlimStrength = it)) },
            valueRange = -1f..1f,
        )

        PortraitSlider(
            label = "大眼",
            value = params.eyeEnlargeStrength,
            onValueChange = { onParamsChange(params.copy(eyeEnlargeStrength = it)) },
            valueRange = 0f..1f,
        )

        PortraitSlider(
            label = "瘦鼻",
            value = params.noseSlimStrength,
            onValueChange = { onParamsChange(params.copy(noseSlimStrength = it)) },
            valueRange = 0f..1f,
        )

        PortraitSlider(
            label = "嘴唇",
            value = params.lipAdjustStrength,
            onValueChange = { onParamsChange(params.copy(lipAdjustStrength = it)) },
            valueRange = -1f..1f,
        )

        // ── 身体调整 ──
        SectionHeader("身体")

        PortraitSlider(
            label = "瘦身",
            value = params.bodySlimStrength,
            onValueChange = { onParamsChange(params.copy(bodySlimStrength = it)) },
            valueRange = -1f..1f,
        )

        PortraitSlider(
            label = "拉腿",
            value = params.legLengthenStrength,
            onValueChange = { onParamsChange(params.copy(legLengthenStrength = it)) },
            valueRange = 0f..1f,
        )

        // ── 操作按钮 ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onReset,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorOS16Colors.TextTertiary),
            ) {
                Text("重置")
            }
            Button(
                onClick = onApply,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = HasselbladOrange),
            ) {
                Text("应用", color = ColorOS16Colors.AMOLEDBlack)
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = HasselbladOrangeBright,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun PortraitSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = -1f..1f,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, color = ColorOS16Colors.TextSecondary, fontSize = 13.sp)
            Text(
                String.format("%.0f", value * 100),
                color = if (value != 0f) HasselbladOrangeBright else ColorOS16Colors.TextTertiary,
                fontSize = 13.sp,
                fontWeight = if (value != 0f) FontWeight.Medium else FontWeight.Normal,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = HasselbladOrange,
                activeTrackColor = HasselbladOrange,
                inactiveTrackColor = ColorOS16Colors.Surface4,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

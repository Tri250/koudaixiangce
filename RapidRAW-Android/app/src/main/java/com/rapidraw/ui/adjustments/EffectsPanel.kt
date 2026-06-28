package com.rapidraw.ui.adjustments

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rapidraw.data.model.Adjustments
import com.rapidraw.ui.components.HasselSlider
import com.rapidraw.ui.theme.EditorSurfaceVariant
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.TextPrimary
import com.rapidraw.ui.theme.TextSecondary

@Composable
fun EffectsPanel(
    adjustments: Adjustments,
    onUpdate: (String, Any) -> Unit,
) {
    var vignetteExpanded by remember { mutableStateOf(true) }
    var grainExpanded by remember { mutableStateOf(false) }
    var creativeExpanded by remember { mutableStateOf(false) }
    var toneMappingExpanded by remember { mutableStateOf(false) }
    var lutExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        // ── 暗角 ──────────────────────────────────────────
        CollapsibleSection(
            title = "暗角",
            expanded = vignetteExpanded,
            onToggle = { vignetteExpanded = !vignetteExpanded },
        ) {
            HasselSlider(
                label = "数量",
                value = adjustments.vignetteAmount,
                range = -100f..100f,
                onValueChange = { onUpdate("vignetteAmount", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
            HasselSlider(
                label = "中点",
                value = adjustments.vignetteMidpoint,
                range = 0f..100f,
                onValueChange = { onUpdate("vignetteMidpoint", it) },
                defaultValue = 50f,
                stepSize = 1f,
            )
            HasselSlider(
                label = "圆度",
                value = adjustments.vignetteRoundness,
                range = -100f..100f,
                onValueChange = { onUpdate("vignetteRoundness", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
            HasselSlider(
                label = "羽化",
                value = adjustments.vignetteFeather,
                range = 0f..100f,
                onValueChange = { onUpdate("vignetteFeather", it) },
                defaultValue = 50f,
                stepSize = 1f,
            )
        }

        // ── 颗粒 ──────────────────────────────────────────
        CollapsibleSection(
            title = "颗粒",
            expanded = grainExpanded,
            onToggle = { grainExpanded = !grainExpanded },
        ) {
            HasselSlider(
                label = "数量",
                value = adjustments.grainAmount,
                range = 0f..100f,
                onValueChange = { onUpdate("grainAmount", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
            HasselSlider(
                label = "大小",
                value = adjustments.grainSize,
                range = 0f..100f,
                onValueChange = { onUpdate("grainSize", it) },
                defaultValue = 25f,
                stepSize = 1f,
            )
            HasselSlider(
                label = "粗糙度",
                value = adjustments.grainRoughness,
                range = 0f..100f,
                onValueChange = { onUpdate("grainRoughness", it) },
                defaultValue = 50f,
                stepSize = 1f,
            )
        }

        // ── 创意效果 ──────────────────────────────────────
        CollapsibleSection(
            title = "创意效果",
            expanded = creativeExpanded,
            onToggle = { creativeExpanded = !creativeExpanded },
        ) {
            HasselSlider(
                label = "发光",
                value = adjustments.glowAmount,
                range = 0f..100f,
                onValueChange = { onUpdate("glowAmount", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
            HasselSlider(
                label = "光晕",
                value = adjustments.halationAmount,
                range = 0f..100f,
                onValueChange = { onUpdate("halationAmount", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
            HasselSlider(
                label = "镜头光斑",
                value = adjustments.flareAmount,
                range = 0f..100f,
                onValueChange = { onUpdate("flareAmount", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
        }

        // ── 色调映射 ──────────────────────────────────────
        CollapsibleSection(
            title = "色调映射",
            expanded = toneMappingExpanded,
            onToggle = { toneMappingExpanded = !toneMappingExpanded },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToneMapperTab(
                    label = "基础",
                    isActive = adjustments.toneMapper == "basic",
                    onClick = { onUpdate("toneMapper", "basic") },
                    modifier = Modifier.weight(1f),
                )
                ToneMapperTab(
                    label = "AgX",
                    isActive = adjustments.toneMapper == "agx",
                    onClick = { onUpdate("toneMapper", "agx") },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // ── LUT ────────────────────────────────────────────
        CollapsibleSection(
            title = "LUT",
            expanded = lutExpanded,
            onToggle = { lutExpanded = !lutExpanded },
        ) {
            HasselSlider(
                label = "强度",
                value = adjustments.lutIntensity,
                range = 0f..100f,
                onValueChange = { onUpdate("lutIntensity", it) },
                defaultValue = 100f,
                stepSize = 1f,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .background(EditorSurfaceVariant, RoundedCornerShape(6.dp))
                    .clickable { onUpdate("selectLut", true) }
                    .padding(vertical = 12.dp, horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "选择LUT",
                    color = HasselbladOrange,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun ToneMapperTab(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .padding(horizontal = 4.dp)
            .background(
                color = if (isActive) HasselbladOrange else EditorSurfaceVariant,
                shape = RoundedCornerShape(6.dp),
            )
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (isActive) TextPrimary else TextSecondary,
            fontSize = 13.sp,
        )
    }
}

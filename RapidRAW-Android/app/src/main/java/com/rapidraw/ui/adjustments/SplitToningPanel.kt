package com.rapidraw.ui.adjustments

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.rapidraw.ui.theme.TextPrimary

@Composable
fun SplitToningPanel(
    adjustments: Adjustments,
    onUpdate: (String, Float) -> Unit,
    onBack: () -> Unit,
) {
    var expandedSection by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        // ── Back button header ──────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = TextPrimary,
                )
            }
            Text(
                text = "分色调",
                color = TextPrimary,
                fontSize = 16.sp,
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // ── 高光 Section ────────────────────────────────────────────────
        CollapsibleSection(
            title = "高光",
            expanded = expandedSection == "highlights",
            onToggle = {
                expandedSection = if (expandedSection == "highlights") null else "highlights"
            },
        ) {
            HasselSlider(
                label = "色相",
                value = adjustments.splitToningHighlightHue,
                range = 0f..360f,
                onValueChange = { onUpdate("splitToningHighlightHue", it) },
                defaultValue = 0f,
                stepSize = 1f,
                format = { v -> "${v.toInt()}°" },
            )
            HasselSlider(
                label = "饱和度",
                value = adjustments.splitToningHighlightSaturation,
                range = 0f..100f,
                onValueChange = { onUpdate("splitToningHighlightSaturation", it) },
                defaultValue = 0f,
            )
        }

        // ── 阴影 Section ────────────────────────────────────────────────
        CollapsibleSection(
            title = "阴影",
            expanded = expandedSection == "shadows",
            onToggle = {
                expandedSection = if (expandedSection == "shadows") null else "shadows"
            },
        ) {
            HasselSlider(
                label = "色相",
                value = adjustments.splitToningShadowHue,
                range = 0f..360f,
                onValueChange = { onUpdate("splitToningShadowHue", it) },
                defaultValue = 0f,
                stepSize = 1f,
                format = { v -> "${v.toInt()}°" },
            )
            HasselSlider(
                label = "饱和度",
                value = adjustments.splitToningShadowSaturation,
                range = 0f..100f,
                onValueChange = { onUpdate("splitToningShadowSaturation", it) },
                defaultValue = 0f,
            )
        }

        // ── 平衡 Section ────────────────────────────────────────────────
        HasselSlider(
            label = "平衡",
            value = adjustments.splitToningBalance,
            range = -100f..100f,
            onValueChange = { onUpdate("splitToningBalance", it) },
            defaultValue = 0f,
        )
    }
}

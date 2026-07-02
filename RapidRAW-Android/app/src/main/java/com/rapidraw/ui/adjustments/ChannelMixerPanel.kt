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
import androidx.compose.material3.Switch
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
fun ChannelMixerPanel(
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
                text = "通道混合器",
                color = TextPrimary,
                fontSize = 16.sp,
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // ── 红输出 Section ──────────────────────────────────────────────
        CollapsibleSection(
            title = "红输出",
            expanded = expandedSection == "redOut",
            onToggle = {
                expandedSection = if (expandedSection == "redOut") null else "redOut"
            },
        ) {
            HasselSlider(
                label = "红→红",
                value = adjustments.channelMixerRedOutRed,
                range = -200f..200f,
                onValueChange = { onUpdate("channelMixerRedOutRed", it) },
                defaultValue = 100f,
                stepSize = 1f,
            )
            HasselSlider(
                label = "绿→红",
                value = adjustments.channelMixerRedOutGreen,
                range = -200f..200f,
                onValueChange = { onUpdate("channelMixerRedOutGreen", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
            HasselSlider(
                label = "蓝→红",
                value = adjustments.channelMixerRedOutBlue,
                range = -200f..200f,
                onValueChange = { onUpdate("channelMixerRedOutBlue", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
        }

        // ── 绿输出 Section ──────────────────────────────────────────────
        CollapsibleSection(
            title = "绿输出",
            expanded = expandedSection == "greenOut",
            onToggle = {
                expandedSection = if (expandedSection == "greenOut") null else "greenOut"
            },
        ) {
            HasselSlider(
                label = "红→绿",
                value = adjustments.channelMixerGreenOutRed,
                range = -200f..200f,
                onValueChange = { onUpdate("channelMixerGreenOutRed", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
            HasselSlider(
                label = "绿→绿",
                value = adjustments.channelMixerGreenOutGreen,
                range = -200f..200f,
                onValueChange = { onUpdate("channelMixerGreenOutGreen", it) },
                defaultValue = 100f,
                stepSize = 1f,
            )
            HasselSlider(
                label = "蓝→绿",
                value = adjustments.channelMixerGreenOutBlue,
                range = -200f..200f,
                onValueChange = { onUpdate("channelMixerGreenOutBlue", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
        }

        // ── 蓝输出 Section ──────────────────────────────────────────────
        CollapsibleSection(
            title = "蓝输出",
            expanded = expandedSection == "blueOut",
            onToggle = {
                expandedSection = if (expandedSection == "blueOut") null else "blueOut"
            },
        ) {
            HasselSlider(
                label = "红→蓝",
                value = adjustments.channelMixerBlueOutRed,
                range = -200f..200f,
                onValueChange = { onUpdate("channelMixerBlueOutRed", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
            HasselSlider(
                label = "绿→蓝",
                value = adjustments.channelMixerBlueOutGreen,
                range = -200f..200f,
                onValueChange = { onUpdate("channelMixerBlueOutGreen", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
            HasselSlider(
                label = "蓝→蓝",
                value = adjustments.channelMixerBlueOutBlue,
                range = -200f..200f,
                onValueChange = { onUpdate("channelMixerBlueOutBlue", it) },
                defaultValue = 100f,
                stepSize = 1f,
            )
        }

        // ── 单色 Toggle ────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "单色",
                color = TextPrimary,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = adjustments.channelMixerMonochrome,
                onCheckedChange = { onUpdate("channelMixerMonochrome", if (it) 1f else 0f) },
            )
        }
    }
}

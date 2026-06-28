package com.rapidraw.ui.adjustments

import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rapidraw.data.model.Adjustments
import com.rapidraw.ui.components.HasselSlider
import com.rapidraw.ui.theme.EditorSurfaceVariant
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.HasselbladOrangeBright
import com.rapidraw.ui.theme.TextPrimary
import com.rapidraw.ui.theme.TextSecondary
import com.rapidraw.ui.theme.TextTertiary

@Composable
fun BasicPanel(
    adjustments: Adjustments,
    onUpdate: (String, Float) -> Unit,
) {
    var toneExpanded by remember { mutableStateOf(true) }
    var wbExpanded by remember { mutableStateOf(true) }
    var colorExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        // ── 影调 ──────────────────────────────────────────
        CollapsibleSection(
            title = "影调",
            expanded = toneExpanded,
            onToggle = { toneExpanded = !toneExpanded },
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
            HasselSlider(
                label = "影调",
                value = adjustments.toneLevel,
                range = -1f..1f,
                onValueChange = { onUpdate("toneLevel", it) },
                defaultValue = 0f,
                stepSize = 0.01f,
                format = { v -> String.format("%.2f", v) },
            )
        }

        // ── 白平衡 ──────────────────────────────────────────
        CollapsibleSection(
            title = "白平衡",
            expanded = wbExpanded,
            onToggle = { wbExpanded = !wbExpanded },
        ) {
            // Kelvin visual indicator
            val kelvinTemp = ((adjustments.temperature + 100f) / 200f * 13000f + 2000f).coerceIn(2000f, 15000f)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "色温",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.width(48.dp),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF6495ED),
                                    Color(0xFFFFFFFF),
                                    Color(0xFFFFD700),
                                    Color(0xFFFF8C00),
                                ),
                            ),
                        ),
                )
                Text(
                    text = "${kelvinTemp.toInt()}K",
                    color = if (adjustments.temperature != 0f) HasselbladOrangeBright else TextTertiary,
                    fontSize = 11.sp,
                    modifier = Modifier.width(50.dp).padding(start = 8.dp),
                    textAlign = TextAlign.End,
                )
            }
            HasselSlider(
                label = "色温",
                value = adjustments.temperature,
                range = -100f..100f,
                onValueChange = { onUpdate("temperature", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )

            // Tint green-magenta axis
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "色调",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.width(48.dp),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF00AA00),
                                    Color(0xFFFFFFFF),
                                    Color(0xFFFF00FF),
                                ),
                            ),
                        ),
                )
                Text(
                    text = if (adjustments.tint > 0f) "品" else if (adjustments.tint < 0f) "绿" else "0",
                    color = if (adjustments.tint != 0f) HasselbladOrangeBright else TextTertiary,
                    fontSize = 11.sp,
                    modifier = Modifier.width(50.dp).padding(start = 8.dp),
                    textAlign = TextAlign.End,
                )
            }
            HasselSlider(
                label = "色调",
                value = adjustments.tint,
                range = -100f..100f,
                onValueChange = { onUpdate("tint", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )

            // Green-Magenta tint axis
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "青品",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.width(48.dp),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF00AA00),
                                    Color(0xFFFFFFFF),
                                    Color(0xFFFF00FF),
                                ),
                            ),
                        ),
                )
                Text(
                    text = if (adjustments.greenMagenta > 0f) "品" else if (adjustments.greenMagenta < 0f) "青" else "0",
                    color = if (adjustments.greenMagenta != 0f) HasselbladOrangeBright else TextTertiary,
                    fontSize = 11.sp,
                    modifier = Modifier.width(50.dp).padding(start = 8.dp),
                    textAlign = TextAlign.End,
                )
            }
            HasselSlider(
                label = "青品",
                value = adjustments.greenMagenta,
                range = -1f..1f,
                onValueChange = { onUpdate("greenMagenta", it) },
                defaultValue = 0f,
                stepSize = 0.01f,
                format = { v -> String.format("%.2f", v) },
            )
        }

        // ── 色彩 ──────────────────────────────────────────
        CollapsibleSection(
            title = "色彩",
            expanded = colorExpanded,
            onToggle = { colorExpanded = !colorExpanded },
        ) {
            HasselSlider(
                label = "饱和度",
                value = adjustments.saturation,
                range = -100f..100f,
                onValueChange = { onUpdate("saturation", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
            HasselSlider(
                label = "自然饱和度",
                value = adjustments.vibrance,
                range = -100f..100f,
                onValueChange = { onUpdate("vibrance", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── 重置按钮 ──────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            ResetButton(
                label = "重置全部",
                onClick = {
                    onUpdate("exposure", 0f)
                    onUpdate("brightness", 0f)
                    onUpdate("contrast", 0f)
                    onUpdate("highlights", 0f)
                    onUpdate("shadows", 0f)
                    onUpdate("whites", 0f)
                    onUpdate("blacks", 0f)
                    onUpdate("toneLevel", 0f)
                    onUpdate("temperature", 0f)
                    onUpdate("tint", 0f)
                    onUpdate("greenMagenta", 0f)
                    onUpdate("saturation", 0f)
                    onUpdate("vibrance", 0f)
                },
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun ResetButton(
    label: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(EditorSurfaceVariant)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            color = HasselbladOrange,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
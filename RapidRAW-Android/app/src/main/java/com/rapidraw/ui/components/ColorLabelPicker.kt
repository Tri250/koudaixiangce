package com.rapidraw.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.TextTertiary

/**
 * 颜色标签选择器 — 支持 6 种颜色标签（红/黄/绿/蓝/紫/橙）
 */
@Composable
fun ColorLabelPicker(
    selectedLabel: Int,
    onLabelChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = listOf(
        0 to TextTertiary,
        1 to Color(0xFFFF3B30),
        2 to Color(0xFFFFCC00),
        3 to Color(0xFF34C759),
        4 to Color(0xFF007AFF),
        5 to Color(0xFFAF52DE),
        6 to HasselbladOrange,
    )

    Row(modifier = modifier) {
        colors.forEach { (label, color) ->
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(
                        color = color,
                        alpha = if (selectedLabel == label) 1f else 0.3f
                    )
                    .clickable { onLabelChange(if (selectedLabel == label) 0 else label) },
            )
        }
    }
}

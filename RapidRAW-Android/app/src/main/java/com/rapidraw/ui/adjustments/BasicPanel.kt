package com.rapidraw.ui.adjustments

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
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
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.TextPrimary
import com.rapidraw.ui.theme.TextSecondary

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

@Composable
fun CollapsibleSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight,
                contentDescription = if (expanded) "收起" else "展开",
                tint = HasselbladOrange,
                modifier = Modifier.padding(end = 8.dp),
            )
            Text(
                text = title,
                color = HasselbladOrange,
                fontSize = 14.sp,
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column {
                content()
            }
        }
    }
}

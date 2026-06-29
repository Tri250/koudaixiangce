package com.rapidraw.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rapidraw.ui.theme.TextPrimary

/**
 * Color label picker for image organization.
 * Shows a row of colored circles representing labels: red, orange, yellow, green, blue, purple, none.
 */
@Composable
fun ColorLabelPicker(
    selectedLabel: String,
    onLabelSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ColorLabel.entries.forEach { label ->
            val isSelected = selectedLabel == label.key
            val borderColor by animateColorAsState(
                targetValue = if (isSelected) TextPrimary else Color.Transparent,
                animationSpec = spring(dampingRatio = 0.5f),
                label = "border_${label.key}",
            )

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .then(
                        if (isSelected) {
                            Modifier.border(2.dp, borderColor, CircleShape)
                        } else {
                            Modifier
                        }
                    )
                    .background(label.color, CircleShape)
                    .clickable { onLabelSelected(label.key) },
                contentAlignment = Alignment.Center,
            ) {
                if (label.key == "none") {
                    Icon(
                        imageVector = Icons.Filled.Block,
                        contentDescription = "Clear label",
                        tint = TextPrimary.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

private data class ColorLabel(
    val key: String,
    val color: Color,
) {
    companion object {
        val entries = listOf(
            ColorLabel("red", Color(0xFFE53935)),
            ColorLabel("orange", Color(0xFFFB8C00)),
            ColorLabel("yellow", Color(0xFFFDD835)),
            ColorLabel("green", Color(0xFF43A047)),
            ColorLabel("blue", Color(0xFF1E88E5)),
            ColorLabel("purple", Color(0xFF8E24AA)),
            ColorLabel("none", Color(0xFF3A3A3A)),
        )
    }
}
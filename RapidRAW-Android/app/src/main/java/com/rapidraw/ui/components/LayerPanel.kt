package com.rapidraw.ui.components

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rapidraw.data.model.AdjustmentLayer
import com.rapidraw.data.model.BlendMode
import com.rapidraw.data.model.LayerStack
import com.rapidraw.ui.theme.EditorBackground
import com.rapidraw.ui.theme.EditorBorder
import com.rapidraw.ui.theme.EditorSurface
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.TextPrimary
import com.rapidraw.ui.theme.TextSecondary
import com.rapidraw.ui.theme.TextTertiary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayerPanel(
    visible: Boolean,
    layerStack: LayerStack,
    onAddLayer: () -> Unit,
    onRemoveLayer: (String) -> Unit,
    onUpdateLayer: (String, (AdjustmentLayer) -> AdjustmentLayer) -> Unit,
    onSelectLayer: (String) -> Unit,
    onMoveLayer: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = EditorSurface,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "图层",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                IconButton(onClick = onAddLayer) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "添加图层",
                        tint = HasselbladOrange,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Layer list (from top to bottom, newest first)
            val reversedLayers = layerStack.layers.reversed()
            reversedLayers.forEachIndexed { reversedIndex, layer ->
                val actualIndex = layerStack.layers.lastIndex - reversedIndex
                val isActive = layer.id == layerStack.activeLayerId
                val isBackground = reversedIndex == reversedLayers.lastIndex

                LayerItem(
                    layer = layer,
                    isActive = isActive,
                    isBackground = isBackground,
                    canMoveUp = reversedIndex > 0,
                    canMoveDown = reversedIndex < reversedLayers.lastIndex,
                    onSelect = { onSelectLayer(layer.id) },
                    onToggleVisibility = {
                        onUpdateLayer(layer.id) { it.copy(enabled = !it.enabled) }
                    },
                    onOpacityChange = { opacity ->
                        onUpdateLayer(layer.id) { it.copy(opacity = opacity) }
                    },
                    onBlendModeChange = { mode ->
                        onUpdateLayer(layer.id) { it.copy(blendMode = mode) }
                    },
                    onMoveUp = {
                        if (actualIndex > 0) {
                            onMoveLayer(actualIndex, actualIndex - 1)
                        }
                    },
                    onMoveDown = {
                        if (actualIndex < layerStack.layers.lastIndex) {
                            onMoveLayer(actualIndex, actualIndex + 1)
                        }
                    },
                    onDelete = { onRemoveLayer(layer.id) },
                )

                if (reversedIndex < reversedLayers.lastIndex) {
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun LayerItem(
    layer: AdjustmentLayer,
    isActive: Boolean,
    isBackground: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onSelect: () -> Unit,
    onToggleVisibility: () -> Unit,
    onOpacityChange: (Float) -> Unit,
    onBlendModeChange: (BlendMode) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
) {
    var showBlendModeMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isActive) HasselbladOrange.copy(alpha = 0.12f) else EditorBorder.copy(alpha = 0.3f))
            .clickable(onClick = onSelect)
            .padding(10.dp),
    ) {
        // Top row: name, visibility, move buttons, delete
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Visibility toggle
            IconButton(
                onClick = onToggleVisibility,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = if (layer.enabled) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = if (layer.enabled) "隐藏" else "显示",
                    tint = if (layer.enabled) HasselbladOrange else TextTertiary,
                    modifier = Modifier.size(16.dp),
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Layer name
            Text(
                text = layer.name,
                color = if (layer.enabled) TextPrimary else TextTertiary,
                fontSize = 13.sp,
                fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
                modifier = Modifier.weight(1f),
            )

            // Move up button
            if (canMoveUp) {
                IconButton(
                    onClick = onMoveUp,
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = "上移",
                        tint = TextTertiary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }

            // Move down button
            if (canMoveDown) {
                IconButton(
                    onClick = onMoveDown,
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = "下移",
                        tint = TextTertiary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }

            // Delete button (not for background layer)
            if (!isBackground) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = TextTertiary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }

        // Opacity slider
        HasselSlider(
            label = "不透明",
            value = layer.opacity * 100f,
            range = 0f..100f,
            onValueChange = { onOpacityChange(it / 100f) },
            defaultValue = 100f,
        )

        // Blend mode selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "混合:",
                color = TextTertiary,
                fontSize = 11.sp,
            )
            BlendMode.entries.forEach { mode ->
                val isSelected = mode == layer.blendMode
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (isSelected) HasselbladOrange.copy(alpha = 0.3f) else EditorBorder)
                        .clickable { onBlendModeChange(mode) }
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = mode.displayName,
                        color = if (isSelected) HasselbladOrange else TextTertiary,
                        fontSize = 9.sp,
                    )
                }
            }
        }
    }
}

private val BlendMode.displayName: String
    get() = when (this) {
        BlendMode.NORMAL -> "正常"
        BlendMode.MULTIPLY -> "正片叠底"
        BlendMode.SCREEN -> "滤色"
        BlendMode.OVERLAY -> "叠加"
        BlendMode.SOFT_LIGHT -> "柔光"
        BlendMode.HARD_LIGHT -> "强光"
        BlendMode.DIFFERENCE -> "差值"
        BlendMode.COLOR_DODGE -> "颜色减淡"
        BlendMode.COLOR_BURN -> "颜色加深"
        BlendMode.COLOR -> "颜色"
        BlendMode.LUMINOSITY -> "明度"
    }

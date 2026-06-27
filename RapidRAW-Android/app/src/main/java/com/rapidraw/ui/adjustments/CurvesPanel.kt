package com.rapidraw.ui.adjustments

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import com.rapidraw.data.model.Coord
import com.rapidraw.ui.components.CurveChannel
import com.rapidraw.ui.components.CurveEditor
import com.rapidraw.ui.theme.EditorSurfaceVariant
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.TextPrimary
import com.rapidraw.ui.theme.TextSecondary
import com.rapidraw.ui.theme.TextTertiary

@Composable
fun CurvesPanel(
    adjustments: Adjustments,
    onUpdate: (String, Any) -> Unit,
) {
    var activeChannel by remember { mutableStateOf(CurveChannel.LUMA) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        CurveEditor(
            points = when (activeChannel) {
                CurveChannel.LUMA -> adjustments.lumaCurve.map { Pair(it.x, it.y) }
                CurveChannel.RED -> adjustments.redCurve.map { Pair(it.x, it.y) }
                CurveChannel.GREEN -> adjustments.greenCurve.map { Pair(it.x, it.y) }
                CurveChannel.BLUE -> adjustments.blueCurve.map { Pair(it.x, it.y) }
            },
            onPointsChanged = { newPoints ->
                val key = when (activeChannel) {
                    CurveChannel.LUMA -> "lumaCurve"
                    CurveChannel.RED -> "redCurve"
                    CurveChannel.GREEN -> "greenCurve"
                    CurveChannel.BLUE -> "blueCurve"
                }
                onUpdate(key, newPoints.map { Coord(it.first, it.second) })
            },
            activeChannel = activeChannel,
            onChannelChanged = { activeChannel = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        // Preset curve buttons
        Text(
            text = "预设曲线",
            color = TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CurvePresetButton(
                label = "线性",
                onClick = {
                    val key = when (activeChannel) {
                        CurveChannel.LUMA -> "lumaCurve"
                        CurveChannel.RED -> "redCurve"
                        CurveChannel.GREEN -> "greenCurve"
                        CurveChannel.BLUE -> "blueCurve"
                    }
                    onUpdate(key, listOf(Coord(0f, 0f), Coord(255f, 255f)))
                },
                modifier = Modifier.weight(1f),
            )
            CurvePresetButton(
                label = "高对比",
                onClick = {
                    val key = when (activeChannel) {
                        CurveChannel.LUMA -> "lumaCurve"
                        CurveChannel.RED -> "redCurve"
                        CurveChannel.GREEN -> "greenCurve"
                        CurveChannel.BLUE -> "blueCurve"
                    }
                    onUpdate(key, listOf(Coord(0f, 0f), Coord(64f, 30f), Coord(192f, 225f), Coord(255f, 255f)))
                },
                modifier = Modifier.weight(1f),
            )
            CurvePresetButton(
                label = "中对比",
                onClick = {
                    val key = when (activeChannel) {
                        CurveChannel.LUMA -> "lumaCurve"
                        CurveChannel.RED -> "redCurve"
                        CurveChannel.GREEN -> "greenCurve"
                        CurveChannel.BLUE -> "blueCurve"
                    }
                    onUpdate(key, listOf(Coord(0f, 0f), Coord(96f, 80f), Coord(160f, 175f), Coord(255f, 255f)))
                },
                modifier = Modifier.weight(1f),
            )
            CurvePresetButton(
                label = "反相",
                onClick = {
                    val key = when (activeChannel) {
                        CurveChannel.LUMA -> "lumaCurve"
                        CurveChannel.RED -> "redCurve"
                        CurveChannel.GREEN -> "greenCurve"
                        CurveChannel.BLUE -> "blueCurve"
                    }
                    onUpdate(key, listOf(Coord(0f, 255f), Coord(255f, 0f)))
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CurvePresetButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(EditorSurfaceVariant, RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = TextPrimary,
            fontSize = 12.sp,
        )
    }
}

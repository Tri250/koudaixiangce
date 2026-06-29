package com.rapidraw.ui.adjustments

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.rapidraw.ui.components.BezierInterpolation
import com.rapidraw.ui.components.CurveEditorChannel
import com.rapidraw.ui.components.CurveEditorView
import com.rapidraw.ui.components.toCoordList
import com.rapidraw.ui.components.toCurvePointList
import com.rapidraw.ui.theme.EditorSurfaceVariant
import com.rapidraw.ui.theme.TextSecondary

/**
 * Curves adjustment panel with professional curve editor.
 * Supports RGB composite, R, G, B separate, and Luma curves.
 * Includes curve presets and auto histogram matching.
 */
@Composable
fun CurvesPanel(
    adjustments: Adjustments,
    onUpdate: (String, Any) -> Unit,
    histogram: IntArray? = null,
    modifier: Modifier = Modifier
) {
    var activeChannel by remember { mutableStateOf(CurveEditorChannel.RGB) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        // Main curve editor
        CurveEditorView(
            points = when (activeChannel) {
                CurveEditorChannel.RGB -> adjustments.lumaCurve.toCurvePointList()
                CurveEditorChannel.RED -> adjustments.redCurve.toCurvePointList()
                CurveEditorChannel.GREEN -> adjustments.greenCurve.toCurvePointList()
                CurveEditorChannel.BLUE -> adjustments.blueCurve.toCurvePointList()
                CurveEditorChannel.LUMA -> adjustments.lumaCurve.toCurvePointList()
            },
            onPointsChanged = { newPoints ->
                val key = when (activeChannel) {
                    CurveEditorChannel.RGB -> "lumaCurve"
                    CurveEditorChannel.RED -> "redCurve"
                    CurveEditorChannel.GREEN -> "greenCurve"
                    CurveEditorChannel.BLUE -> "blueCurve"
                    CurveEditorChannel.LUMA -> "lumaCurve"
                }
                onUpdate(key, newPoints.toCoordList())
            },
            activeChannel = activeChannel,
            onChannelChanged = { activeChannel = it },
            histogram = histogram,
            showPresets = true,
            onAutoMatch = if (histogram != null) {
                {
                    // Apply auto curve matching
                    val autoPoints = BezierInterpolation.autoMatchHistogram(
                        histogram,
                        targetMean = 128f,
                        smoothness = 0.3f
                    )
                    val key = when (activeChannel) {
                        CurveEditorChannel.RGB -> "lumaCurve"
                        CurveEditorChannel.RED -> "redCurve"
                        CurveEditorChannel.GREEN -> "greenCurve"
                        CurveEditorChannel.BLUE -> "blueCurve"
                        CurveEditorChannel.LUMA -> "lumaCurve"
                    }
                    onUpdate(key, autoPoints.toCoordList())
                }
            } else null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Reset all curves button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .background(EditorSurfaceVariant, RoundedCornerShape(4.dp))
                .clickable {
                    onUpdate("lumaCurve", listOf(Coord(0f, 0f), Coord(255f, 255f)))
                    onUpdate("redCurve", listOf(Coord(0f, 0f), Coord(255f, 255f)))
                    onUpdate("greenCurve", listOf(Coord(0f, 0f), Coord(255f, 255f)))
                    onUpdate("blueCurve", listOf(Coord(0f, 0f), Coord(255f, 255f)))
                }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "重置全部曲线",
                color = TextSecondary,
                fontSize = 12.sp
            )
        }
    }
}
package com.rapidraw.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rapidraw.ui.theme.EditorBorder
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.TextSecondary
import com.rapidraw.ui.theme.EditorTypography

@Composable
fun BeforeAfterToggle(
    isShowingOriginal: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isLongPressing by remember { mutableStateOf(false) }
    val showingOriginal = isShowingOriginal || isLongPressing

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(
            modifier = Modifier
                .size(40.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = {
                            // Long-press starts: show original
                            isLongPressing = true
                        },
                        onTap = { onToggle() },
                        onPress = {
                            awaitRelease()
                            isLongPressing = false
                        },
                    )
                }
        ) {
            val canvasSize = size
            val center = Offset(canvasSize.width / 2f, canvasSize.height / 2f)
            val radius = canvasSize.width / 2f
            val rect = Rect(
                center.x - radius,
                center.y - radius,
                center.x + radius,
                center.y + radius
            )

            // Left half - gray
            val leftPath = Path().apply {
                moveTo(center.x, center.y - radius)
                arcTo(
                    rect = rect,
                    startAngleDegrees = 90f,
                    sweepAngleDegrees = 180f,
                    forceMoveTo = false,
                )
                close()
            }
            drawPath(
                path = leftPath,
                color = EditorBorder,
                style = Fill,
            )

            // Right half - Hasselblad Orange
            val rightPath = Path().apply {
                moveTo(center.x, center.y - radius)
                arcTo(
                    rect = rect,
                    startAngleDegrees = -90f,
                    sweepAngleDegrees = 180f,
                    forceMoveTo = false,
                )
                close()
            }
            drawPath(
                path = rightPath,
                color = HasselbladOrange,
                style = Fill,
            )

            // Dividing line
            drawLine(
                color = Color.White,
                start = Offset(center.x, center.y - radius + 2.dp.toPx()),
                end = Offset(center.x, center.y + radius - 2.dp.toPx()),
                strokeWidth = 1.5.dp.toPx(),
            )

            // Outer border circle
            drawCircle(
                color = Color.White.copy(alpha = 0.3f),
                radius = radius,
                center = center,
                style = Stroke(width = 1.5.dp.toPx()),
            )
        }

        Text(
            text = if (showingOriginal) "原图" else "效果",
            color = TextSecondary,
            style = EditorTypography.badge,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

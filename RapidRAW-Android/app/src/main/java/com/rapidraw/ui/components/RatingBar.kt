package com.rapidraw.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.TextTertiary

/**
 * Star rating component (0-5 stars) for image library.
 * Supports half-star ratings via tapping position and animated fill transitions.
 */
@Composable
fun RatingBar(
    rating: Int,
    onRatingChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    starSize: Dp = 24.dp,
    editable: Boolean = true,
) {
    var starWidthPx by remember { mutableIntStateOf(0) }

    val currentRating = rating.coerceIn(0, 5)

    Row(
        modifier = modifier
            .onSizeChanged { starWidthPx = it.width / 5 }
            .then(
                if (editable) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures { offset ->
                            if (starWidthPx > 0) {
                                val starIndex = (offset.x / starWidthPx).toInt().coerceIn(0, 4)
                                val fractionInStar =
                                    (offset.x - starIndex * starWidthPx) / starWidthPx.toFloat()
                                val newRating = if (fractionInStar <= 0.5f) {
                                    starIndex + 1
                                } else {
                                    starIndex + 1
                                }
                                val finalRating = if (currentRating == newRating) {
                                    if (fractionInStar <= 0.5f && currentRating == starIndex + 1) {
                                        starIndex
                                    } else {
                                        newRating
                                    }
                                } else {
                                    newRating
                                }
                                onRatingChange(finalRating.coerceIn(0, 5))
                            }
                        }
                    }
                } else {
                    Modifier
                }
            ),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (index in 0 until 5) {
            val starValue = index + 1
            val isFilled = starValue <= currentRating
            val isHalf = false

            val scaleAnim by animateFloatAsState(
                targetValue = if (isFilled) 1.1f else 1.0f,
                animationSpec = spring(dampingRatio = 0.5f),
                label = "starScale_$index",
            )

            val alphaAnim by animateFloatAsState(
                targetValue = if (isFilled) 1.0f else 0.38f,
                animationSpec = spring(dampingRatio = 0.5f),
                label = "starAlpha_$index",
            )

            Box(
                modifier = Modifier
                    .size(starSize)
                    .scale(scaleAnim)
                    .graphicsLayer { alpha = alphaAnim },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isFilled) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = if (editable) "Rate $starValue stars" else "$starValue stars",
                    tint = if (isFilled) HasselbladOrange else TextTertiary,
                    modifier = Modifier.size(starSize),
                )
            }
        }
    }
}
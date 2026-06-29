package com.rapidraw.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.TextTertiary

/**
 * 星标评分覆盖组件（Compose）
 *
 * 在缩略图上显示 1-5 星星标，支持点击修改评分。
 * 提供紧凑模式（小星标，适合缩略图角标）和交互模式（大星标，适合弹窗或详情页）。
 *
 * @param rating 当前评分（0-5）
 * @param onRatingChange 评分变化回调
 * @param maxStars 最大星数（默认 5）
 * @param starSize 星星尺寸
 * @param interactive 是否支持交互（点击修改）
 * @param modifier 外部修饰符
 */
@Composable
fun StarRatingOverlay(
    rating: Int,
    onRatingChange: (Int) -> Unit = {},
    maxStars: Int = 5,
    starSize: Dp = 14.dp,
    interactive: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var hoverRating by remember { mutableIntStateOf(0) }

    val displayRating = if (interactive && hoverRating > 0) hoverRating else rating.coerceIn(0, maxStars)

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(maxStars) { index ->
            val starValue = index + 1
            val isFilled = starValue <= displayRating
            val tint = if (isFilled) HasselbladOrange else TextTertiary.copy(alpha = 0.6f)

            Icon(
                imageVector = if (isFilled) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = "${starValue} 星",
                tint = tint,
                modifier = Modifier
                    .size(starSize)
                    .then(
                        if (interactive) {
                            Modifier.clickable {
                                onRatingChange(if (rating == starValue) 0 else starValue)
                            }
                        } else Modifier
                    ),
            )
        }
    }
}

/**
 * 纯星标展示组件（无背景，无交互）
 *
 * 用于列表项、卡片中紧凑显示评分。
 *
 * @param rating 当前评分（0-5）
 * @param maxStars 最大星数（默认 5）
 * @param starSize 星星尺寸
 * @param filledColor 填充色
 * @param emptyColor 空星颜色
 * @param modifier 外部修饰符
 */
@Composable
fun StarRatingRow(
    rating: Int,
    maxStars: Int = 5,
    starSize: Dp = 14.dp,
    filledColor: Color = HasselbladOrange,
    emptyColor: Color = TextTertiary.copy(alpha = 0.5f),
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(maxStars) { index ->
            val isFilled = (index + 1) <= rating.coerceIn(0, maxStars)
            Icon(
                imageVector = if (isFilled) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = null,
                tint = if (isFilled) filledColor else emptyColor,
                modifier = Modifier.size(starSize),
            )
        }
    }
}

/**
 * 可交互星标编辑器（弹窗/全屏场景）
 *
 * 提供更大的触摸区域和视觉反馈，适合评分弹窗。
 *
 * @param rating 当前评分（0-5）
 * @param onRatingChange 评分变化回调
 * @param maxStars 最大星数（默认 5）
 * @param starSize 星星尺寸（默认 32.dp）
 * @param modifier 外部修饰符
 */
@Composable
fun StarRatingEditor(
    rating: Int,
    onRatingChange: (Int) -> Unit,
    maxStars: Int = 5,
    starSize: Dp = 32.dp,
    modifier: Modifier = Modifier,
) {
    var previewRating by remember { mutableIntStateOf(0) }
    val activeRating = if (previewRating > 0) previewRating else rating

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(maxStars) { index ->
            val starValue = index + 1
            val isFilled = starValue <= activeRating

            Box(
                modifier = Modifier
                    .size(starSize + 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        onRatingChange(if (rating == starValue) 0 else starValue)
                    }
                    .padding(4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isFilled) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    contentDescription = "${starValue} 星",
                    tint = if (isFilled) HasselbladOrange else TextTertiary.copy(alpha = 0.5f),
                    modifier = Modifier.size(starSize),
                )
            }
        }
    }
}

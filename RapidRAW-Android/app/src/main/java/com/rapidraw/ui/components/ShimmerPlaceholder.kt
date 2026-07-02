package com.rapidraw.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 骨架屏/闪烁占位符组件 — v1.9.0 交互体验优化新增。
 *
 * 替代传统的 CircularProgressIndicator，在图片加载、列表加载等场景
 * 提供更流畅的视觉过渡体验。
 *
 * 使用方式：
 * if (isLoading) {
 *     ShimmerGridItem()
 * } else {
 *     ImageGridItem(...)
 * }
 *
 * @since v1.9.0
 */
object ShimmerPlaceholder {

    private val shimmerColor = Color(0xFFE0E0E0)
    private val shimmerHighlight = Color(0xFFF5F5F5)
    private val shimmerDarkColor = Color(0xFF303030)
    private val shimmerDarkHighlight = Color(0xFF454545)

    /**
     * 创建闪烁动画的渐变刷子。
     * @param isDark 是否为暗色主题
     */
    @Composable
    fun shimmerBrush(isDark: Boolean = false): Brush {
        val baseColor = if (isDark) shimmerDarkColor else shimmerColor
        val highlightColor = if (isDark) shimmerDarkHighlight else shimmerHighlight

        val transition = rememberInfiniteTransition(label = "shimmer")
        val translateAnim by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1000f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "shimmer_translate",
        )

        return Brush.linearGradient(
            colors = listOf(baseColor, highlightColor, baseColor),
            start = Offset(translateAnim - 200, translateAnim - 200),
            end = Offset(translateAnim, translateAnim),
        )
    }

    /** 图片网格占位符 */
    @Composable
    fun ImageGridItem(modifier: Modifier = Modifier, isDark: Boolean = false) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(8.dp))
                .background(shimmerBrush(isDark)),
        )
    }

    /** 列表行占位符 */
    @Composable
    fun ListRow(isDark: Boolean = false) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(shimmerBrush(isDark)),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerBrush(isDark)),
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerBrush(isDark)),
                )
            }
        }
    }

    /** 编辑器占位符（模拟大图区域） */
    @Composable
    fun EditorPlaceholder(isDark: Boolean = false) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            // 大图区域
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(shimmerBrush(isDark)),
            )
            Spacer(Modifier.height(16.dp))
            // 底部调整栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
            ) {
                repeat(5) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(shimmerBrush(isDark)),
                    )
                }
            }
        }
    }

    /** 设置项占位符 */
    @Composable
    fun SettingsRow(isDark: Boolean = false) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(shimmerBrush(isDark)),
            )
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(shimmerBrush(isDark)),
            )
        }
    }

    /** 图片网格骨架（批量） */
    @Composable
    fun ImageGrid(
        columns: Int = 3,
        count: Int = 9,
        isDark: Boolean = false,
        spacing: Dp = 4.dp,
    ) {
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(columns),
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(spacing),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(spacing),
            userScrollEnabled = false,
        ) {
            items(count) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(shimmerBrush(isDark)),
                )
            }
        }
    }
}
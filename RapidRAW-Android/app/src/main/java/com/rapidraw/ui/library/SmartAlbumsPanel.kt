package com.rapidraw.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rapidraw.data.model.SmartAlbum
import com.rapidraw.ui.theme.EditorBackground
import com.rapidraw.ui.theme.EditorSurface
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.TextPrimary
import com.rapidraw.ui.theme.TextSecondary
import com.rapidraw.ui.theme.TextTertiary

/**
 * 智能相册面板（Compose UI）
 *
 * 显示收藏夹、高评分、最近编辑、RAW 文件等智能相册入口。
 * 采用横向滚动卡片布局，适配摄影图库的快捷筛选场景。
 *
 * @param albums 智能相册列表（通常使用 SmartAlbum.predefined）
 * @param counts 各相册对应的照片数量映射
 * @param selectedAlbum 当前选中的相册
 * @param onAlbumClick 点击相册回调
 * @param modifier 外部修饰符
 */
@Composable
fun SmartAlbumsPanel(
    albums: List<SmartAlbum> = SmartAlbum.predefined,
    counts: Map<SmartAlbum, Int> = emptyMap(),
    selectedAlbum: SmartAlbum? = null,
    onAlbumClick: (SmartAlbum) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(EditorBackground)
            .padding(vertical = 8.dp),
    ) {
        Text(
            text = "智能相册",
            style = MaterialTheme.typography.titleSmall,
            color = TextPrimary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        Spacer(modifier = Modifier.height(4.dp))

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(albums, key = { it.title }) { album ->
                val isSelected = selectedAlbum == album
                val count = counts[album] ?: 0
                SmartAlbumCard(
                    album = album,
                    count = count,
                    isSelected = isSelected,
                    onClick = { onAlbumClick(album) },
                )
            }
            // AI 语义智能相册（AlcedoStudio 对标）
            if (SmartAlbum.aiSemanticAlbums.isNotEmpty()) {
                item {
                    Text(
                        text = "AI",
                        color = HasselbladOrange,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
                    )
                }
                items(SmartAlbum.aiSemanticAlbums, key = { it.title }) { album ->
                    val isSelected = selectedAlbum == album
                    val count = counts[album] ?: 0
                    SmartAlbumCard(
                        album = album,
                        count = count,
                        isSelected = isSelected,
                        onClick = { onAlbumClick(album) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SmartAlbumCard(
    album: SmartAlbum,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val (icon, accentColor) = albumIconAndColor(album)

    Surface(
        modifier = Modifier
            .width(120.dp)
            .clickable(onClick = onClick),
        color = if (isSelected) HasselbladOrange.copy(alpha = 0.18f) else EditorSurface,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = album.title,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = album.title,
                color = if (isSelected) HasselbladOrange else TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "${count} 张",
                color = TextTertiary,
                fontSize = 11.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun albumIconAndColor(album: SmartAlbum): Pair<ImageVector, androidx.compose.ui.graphics.Color> {
    return when (album) {
        SmartAlbum.Favorites -> Icons.Default.Favorite to HasselbladOrange
        SmartAlbum.Unrated -> Icons.Outlined.StarBorder to TextSecondary
        SmartAlbum.HighRating -> Icons.Default.Star to androidx.compose.ui.graphics.Color(0xFFFFC107)
        SmartAlbum.RecentlyEdited -> Icons.Default.AccessTime to androidx.compose.ui.graphics.Color(0xFF64D2FF)
        SmartAlbum.RawFiles -> Icons.Default.PhotoCamera to androidx.compose.ui.graphics.Color(0xFF30D158)
        is SmartAlbum.ByDate -> Icons.Default.AccessTime to TextSecondary
        is SmartAlbum.AiSemantic -> Icons.Default.AutoAwesome to HasselbladOrange
        SmartAlbum.AiPortraits -> Icons.Default.Face to Color(0xFFFF6B9D)
        SmartAlbum.AiLandscapes -> Icons.Default.Landscape to Color(0xFF4CAF50)
        SmartAlbum.AiNight -> Icons.Default.NightsStay to Color(0xFF7C4DFF)
        SmartAlbum.AiFood -> Icons.Default.Restaurant to Color(0xFFFF9800)
        SmartAlbum.AiArchitecture -> Icons.Default.Apartment to Color(0xFF78909C)
        SmartAlbum.AiWarmTone -> Icons.Default.WbSunny to Color(0xFFFFAB40)
        SmartAlbum.AiCoolTone -> Icons.Default.AcUnit to Color(0xFF40C4FF)
        SmartAlbum.AiRomantic -> Icons.Default.Favorite to Color(0xFFE91E63)
        SmartAlbum.AiDramatic -> Icons.Default.FlashOn to Color(0xFFFF6E40)
    }
}

/**
 * 竖向智能相册列表（用于侧边栏或底部抽屉场景）
 *
 * @param albums 智能相册列表
 * @param counts 各相册对应的照片数量映射
 * @param selectedAlbum 当前选中的相册
 * @param onAlbumClick 点击相册回调
 * @param modifier 外部修饰符
 */
@Composable
fun SmartAlbumsList(
    albums: List<SmartAlbum> = SmartAlbum.predefined,
    counts: Map<SmartAlbum, Int> = emptyMap(),
    selectedAlbum: SmartAlbum? = null,
    onAlbumClick: (SmartAlbum) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(EditorBackground)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = "智能相册",
            style = MaterialTheme.typography.titleSmall,
            color = TextPrimary,
            modifier = Modifier.padding(vertical = 4.dp),
        )

        Spacer(modifier = Modifier.height(8.dp))

        albums.forEach { album ->
            val isSelected = selectedAlbum == album
            val count = counts[album] ?: 0
            val (icon, accentColor) = albumIconAndColor(album)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) HasselbladOrange.copy(alpha = 0.12f) else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onAlbumClick(album) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) HasselbladOrange else accentColor,
                    modifier = Modifier.size(20.dp),
                )

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = album.title,
                    color = if (isSelected) HasselbladOrange else TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                    modifier = Modifier.weight(1f),
                )

                Text(
                    text = count.toString(),
                    color = TextTertiary,
                    fontSize = 12.sp,
                )
            }

            Spacer(modifier = Modifier.height(2.dp))
        }
    }
}

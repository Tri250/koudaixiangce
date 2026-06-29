package com.rapidraw.ui.community

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rapidraw.ui.theme.ColorOS16Colors
import com.rapidraw.ui.theme.EditorBackground
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.TextPrimary
import com.rapidraw.ui.theme.TextSecondary
import com.rapidraw.ui.theme.TextTertiary

/**
 * 配方分享社区页面 — 共享编辑配方的信息流，支持点赞/评论/分享操作。
 * 配方预览（前后对比），分享按钮，用户头像 + 名称 + 配方名称。
 * 使用 ColorOS16Colors 主题。
 */
@Composable
fun RecipeShareScreen(
    onBack: () -> Unit,
    onShareRecipe: () -> Unit = {},
    onRecipeClick: (SharedRecipe) -> Unit = {},
    onLikeRecipe: (SharedRecipe) -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EditorBackground),
    ) {
        // ── Top Bar ──────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(android.R.drawable.ic_menu_revert),
                    contentDescription = "返回",
                    tint = TextPrimary,
                )
            }
            Text(
                text = "配方社区",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )

            // 分享我的配方按钮
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(HasselbladOrange)
                    .clickable { onShareRecipe() }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "分享配方",
                    color = ColorOS16Colors.TextHigh,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        HorizontalDivider(color = ColorOS16Colors.Hairline, thickness = 0.5.dp)

        // ── Recipe Feed ──────────────────────────────────────────────
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            items(sampleSharedRecipes) { recipe ->
                RecipeCard(
                    recipe = recipe,
                    onRecipeClick = { onRecipeClick(recipe) },
                    onLike = { onLikeRecipe(recipe) },
                )
                HorizontalDivider(
                    color = ColorOS16Colors.Hairline,
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun RecipeCard(
    recipe: SharedRecipe,
    onRecipeClick: () -> Unit,
    onLike: () -> Unit,
) {
    var liked by remember { mutableStateOf(recipe.isLikedByMe) }
    var likeCount by remember { mutableStateOf(recipe.likeCount) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onRecipeClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        // ── 用户信息 ─────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 头像
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(recipe.authorAvatarGradient.map { Color(it) }),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = recipe.authorName.take(1),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = recipe.authorName,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = formatTimeAgo(recipe.sharedAt),
                    color = TextTertiary,
                    fontSize = 12.sp,
                )
            }

            // 标签
            if (recipe.tags.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(HasselbladOrange.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = recipe.tags.first(),
                        color = HasselbladOrange,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── 配方名称和描述 ───────────────────────────────────────────
        Text(
            text = recipe.name,
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )

        if (recipe.description.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = recipe.description,
                color = TextSecondary,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 20.sp,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── 前后对比预览 ─────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(10.dp),
                ),
        ) {
            // Before
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(recipe.beforeGradient.map { Color(it) }),
                    ),
                contentAlignment = Alignment.BottomStart,
            ) {
                Text(
                    text = "Before",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(8.dp),
                )
            }

            // 分割线
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(HasselbladOrange),
            )

            // After
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(recipe.afterGradient.map { Color(it) }),
                    ),
                contentAlignment = Alignment.BottomStart,
            ) {
                Text(
                    text = "After",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── 互动栏 ───────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 点赞
            Row(
                modifier = Modifier.clickable {
                    liked = !liked
                    likeCount = if (liked) likeCount + 1 else likeCount - 1
                    onLike()
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(
                        if (liked) android.R.drawable.btn_star_big_on
                        else android.R.drawable.btn_star_big_off,
                    ),
                    contentDescription = "点赞",
                    tint = if (liked) HasselbladOrange else TextTertiary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = likeCount.toString(),
                    color = if (liked) HasselbladOrange else TextTertiary,
                    fontSize = 13.sp,
                )
            }

            // 评论
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(android.R.drawable.ic_menu_agenda),
                    contentDescription = "评论",
                    tint = TextTertiary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = recipe.commentCount.toString(),
                    color = TextTertiary,
                    fontSize = 13.sp,
                )
            }

            // 分享
            Row(
                modifier = Modifier.clickable { /* TODO: 分享配方 */ },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(android.R.drawable.ic_menu_share),
                    contentDescription = "分享",
                    tint = TextTertiary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "分享",
                    color = TextTertiary,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

private fun formatTimeAgo(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3_600_000 -> "${diff / 60_000}分钟前"
        diff < 86_400_000 -> "${diff / 3_600_000}小时前"
        diff < 604_800_000 -> "${diff / 86_400_000}天前"
        else -> "${diff / 604_800_000}周前"
    }
}

// ── Data Model ────────────────────────────────────────────────────────

data class SharedRecipe(
    val id: String,
    val name: String,
    val description: String,
    val authorName: String,
    val authorAvatarGradient: List<Long> = emptyList(),
    val tags: List<String> = emptyList(),
    val likeCount: Int = 0,
    val commentCount: Int = 0,
    val isLikedByMe: Boolean = false,
    val sharedAt: Long = System.currentTimeMillis(),
    val beforeGradient: List<Long> = emptyList(),
    val afterGradient: List<Long> = emptyList(),
)

// ── Sample Data ───────────────────────────────────────────────────────

private val sampleSharedRecipes = listOf(
    SharedRecipe(
        id = "recipe_001",
        name = "日系胶片人像",
        description = "柔和低对比的日系风格，适合自然光人像，带轻微褪色效果",
        authorName = "光影手记",
        authorAvatarGradient = listOf(0xFFE8D5C4, 0xFFD4A574),
        tags = listOf("人像", "日系"),
        likeCount = 328,
        commentCount = 42,
        isLikedByMe = false,
        sharedAt = System.currentTimeMillis() - 3_600_000 * 2,
        beforeGradient = listOf(0xFF424242, 0xFF616161, 0xFF757575),
        afterGradient = listOf(0xFFF5E6D3, 0xFFE8D5C4, 0xFFD4C4B0),
    ),
    SharedRecipe(
        id = "recipe_002",
        name = "赛博朋克夜景",
        description = "高对比冷色调夜景配方，增强霓虹灯效果，适合城市街拍",
        authorName = "夜色猎人",
        authorAvatarGradient = listOf(0xFF4A148C, 0xFF880E4F),
        tags = listOf("夜景", "赛博朋克"),
        likeCount = 516,
        commentCount = 67,
        isLikedByMe = true,
        sharedAt = System.currentTimeMillis() - 86_400_000,
        beforeGradient = listOf(0xFF263238, 0xFF37474F, 0xFF455A64),
        afterGradient = listOf(0xFF00BCD4, 0xFFE040FB, 0xFF1A237E),
    ),
    SharedRecipe(
        id = "recipe_003",
        name = "复古胶片质感",
        description = "模拟 Kodak Gold 200 胶片，暖色调带颗粒感，适合日常记录",
        authorName = "胶片时光",
        authorAvatarGradient = listOf(0xFF8D6E63, 0xFFA1887F),
        tags = listOf("胶片", "复古"),
        likeCount = 214,
        commentCount = 28,
        isLikedByMe = false,
        sharedAt = System.currentTimeMillis() - 86_400_000 * 3,
        beforeGradient = listOf(0xFFBDBDBD, 0xFF9E9E9E, 0xFF757575),
        afterGradient = listOf(0xFFFFCC80, 0xFFFFB74D, 0xFFD4A574),
    ),
    SharedRecipe(
        id = "recipe_004",
        name = "清新风景调色",
        description = "通透蓝天绿色，低饱和高明度，适合户外风景和旅拍",
        authorName = "旅途色彩",
        authorAvatarGradient = listOf(0xFF4CAF50, 0xFF2196F3),
        tags = listOf("风景", "清新"),
        likeCount = 189,
        commentCount = 15,
        isLikedByMe = false,
        sharedAt = System.currentTimeMillis() - 86_400_000 * 5,
        beforeGradient = listOf(0xFF78909C, 0xFF90A4AE, 0xFFB0BEC5),
        afterGradient = listOf(0xFF81D4FA, 0xFFA5D6A7, 0xFFC8E6C9),
    ),
    SharedRecipe(
        id = "recipe_005",
        name = "电影感青橙",
        description = "经典 Teal & Orange 电影调色，影棚级色彩分离效果",
        authorName = "CineStudio",
        authorAvatarGradient = listOf(0xFF00838F, 0xFFE65100),
        tags = listOf("电影", "青橙"),
        likeCount = 743,
        commentCount = 91,
        isLikedByMe = true,
        sharedAt = System.currentTimeMillis() - 86_400_000 * 1,
        beforeGradient = listOf(0xFF616161, 0xFF757575, 0xFF9E9E9E),
        afterGradient = listOf(0xFF00838F, 0xFFE65100, 0xFF004D40),
    ),
)

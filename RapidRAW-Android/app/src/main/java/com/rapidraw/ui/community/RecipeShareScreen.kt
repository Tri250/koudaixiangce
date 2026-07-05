package com.rapidraw.ui.community

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rapidraw.data.community.CommunityRepository
import com.rapidraw.ui.theme.ColorOS16Colors
import com.rapidraw.ui.theme.EditorBackground
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.TextPrimary
import com.rapidraw.ui.theme.TextSecondary
import com.rapidraw.ui.theme.TextTertiary

/**
 * 配方分享社区页面 — 共享编辑配方的信息流，支持点赞/分享/导入操作。
 * 配方预览（前后对比）由 ImageProcessor 处理内置样例图生成，点赞通过 CommunityRepository 持久化。
 * 使用 ColorOS16Colors 主题。
 */
@Composable
fun RecipeShareScreen(
    onBack: () -> Unit,
    onShareRecipe: () -> Unit = {},
    onRecipeClick: (SharedRecipe) -> Unit = {},
    onLikeRecipe: (SharedRecipe) -> Unit = {},
    vm: RecipeShareViewModel = viewModel(),
) {
    val shareState by vm.state.collectAsState()
    val recipes = shareState.sharedRecipes
    val context = LocalContext.current

    // 内置样例图（before 预览，所有配方共用同一基准样例以保证可比性）
    val sampleBefore = remember {
        runCatching { CommunityRepository(context).createSampleBitmap() }.getOrNull()
    }

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
        if (recipes.isEmpty() && !shareState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painter = painterResource(android.R.drawable.ic_menu_agenda),
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "社区还没有配方",
                        color = TextTertiary,
                        fontSize = 15.sp,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "点击「分享配方」发布你的第一个配方",
                        color = TextTertiary,
                        fontSize = 13.sp,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                items(recipes, key = { it.id }) { recipe ->
                    RecipeCard(
                        recipe = recipe,
                        sampleBefore = sampleBefore,
                        onRecipeClick = { onRecipeClick(recipe) },
                        onLike = {
                            vm.toggleLike(recipe.id)
                            onLikeRecipe(recipe)
                        },
                        onShare = {
                            shareRecipeText(context, recipe, vm)
                        },
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
}

private fun shareRecipeText(
    context: android.content.Context,
    recipe: SharedRecipe,
    vm: RecipeShareViewModel,
) {
    val shareCode = runCatching { vm.generateShareCode(recipe) }.getOrNull()
    val text = buildString {
        append("RapidRAW 配方: ${recipe.name}")
        if (recipe.description.isNotBlank()) {
            append("\n${recipe.description}")
        }
        if (!shareCode.isNullOrBlank()) {
            append("\n\n分享码（可在「导入配方」中粘贴）：\n$shareCode")
        }
    }
    val sendIntent = android.content.Intent().apply {
        action = android.content.Intent.ACTION_SEND
        putExtra(android.content.Intent.EXTRA_TEXT, text)
        type = "text/plain"
    }
    try {
        context.startActivity(android.content.Intent.createChooser(sendIntent, "分享配方"))
    } catch (_: Exception) {
        android.widget.Toast.makeText(context, "无法分享配方", android.widget.Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun RecipeCard(
    recipe: SharedRecipe,
    sampleBefore: Bitmap?,
    onRecipeClick: () -> Unit,
    onLike: () -> Unit,
    onShare: () -> Unit,
) {
    // after 预览图：从 thumbnailBase64 解码（保存时由 ImageProcessor 生成）
    val afterBitmap = remember(recipe.thumbnailBase64) {
        if (recipe.thumbnailBase64.isNullOrBlank()) sampleBefore
        else decodeBase64(recipe.thumbnailBase64) ?: sampleBefore
    }

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

        // ── 前后对比预览（真实 ImageProcessor 生成的位图） ─────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(10.dp)),
        ) {
            // Before
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .background(ColorOS16Colors.Surface3),
                contentAlignment = Alignment.BottomStart,
            ) {
                val beforeImg = sampleBefore
                if (beforeImg != null) {
                    Image(
                        bitmap = beforeImg.asImageBitmap(),
                        contentDescription = "Before",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Text(
                    text = "Before",
                    color = Color.White.copy(alpha = 0.85f),
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
                    .background(ColorOS16Colors.Surface3),
                contentAlignment = Alignment.BottomStart,
            ) {
                val afterImg = afterBitmap
                if (afterImg != null) {
                    Image(
                        bitmap = afterImg.asImageBitmap(),
                        contentDescription = "After",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Text(
                    text = "After",
                    color = Color.White.copy(alpha = 0.85f),
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
            // 点赞（通过 VM 持久化，非本地 remember）
            Row(
                modifier = Modifier.clickable { onLike() },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(
                        if (recipe.isLikedByMe) android.R.drawable.btn_star_big_on
                        else android.R.drawable.btn_star_big_off,
                    ),
                    contentDescription = "点赞",
                    tint = if (recipe.isLikedByMe) HasselbladOrange else TextTertiary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = recipe.likeCount.toString(),
                    color = if (recipe.isLikedByMe) HasselbladOrange else TextTertiary,
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
                modifier = Modifier.clickable { onShare() },
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

private fun decodeBase64(base64: String): Bitmap? {
    return runCatching {
        val bytes = android.util.Base64.decode(base64, android.util.Base64.NO_WRAP)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
}

private fun formatTimeAgo(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 0 -> "刚刚"
        diff < 60_000 -> "刚刚"
        diff < 3_600_000 -> "${diff / 60_000}分钟前"
        diff < 86_400_000 -> "${diff / 3_600_000}小时前"
        diff < 604_800_000 -> "${diff / 86_400_000}天前"
        else -> "${diff / 604_800_000}周前"
    }
}

// ── Data Model ────────────────────────────────────────────────────────

@Immutable
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
    /** After 预览图（Base64 JPEG，由 ImageProcessor 处理内置样例图生成） */
    val thumbnailBase64: String? = null,
    /** 完整 Adjustments JSON，用于按需生成预览或应用到图像 */
    val adjustmentsJson: String = "",
)

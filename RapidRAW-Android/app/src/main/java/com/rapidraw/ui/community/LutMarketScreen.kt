package com.rapidraw.ui.community

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
 * LUT 市场 / 社区页面 — 精选 LUT 包轮播、分类标签、LUT 卡片网格、搜索栏、"我的收藏" 标签页。
 * 使用 HasselbladOrange 主题。
 */
@Composable
fun LutMarketScreen(
    onBack: () -> Unit,
    onDownloadLut: (LutItem) -> Unit = {},
    onImportLut: (LutItem) -> Unit = {},
    vm: LutMarketViewModel = viewModel(),
) {
    val marketState by vm.state.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("发现", "我的收藏")
    var selectedCategory by remember { mutableStateOf(marketState.selectedCategory) }
    val categories = listOf("全部", "胶片", "电影", "复古", "手机")

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
                text = "LUT 市场",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        // ── Tab Row ──────────────────────────────────────────────────
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = TextPrimary,
            indicator = { tabPositions ->
                TabRowDefaults.Indicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    height = 3.dp,
                    color = HasselbladOrange,
                )
            },
            divider = {},
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = title,
                            color = if (selectedTab == index) TextPrimary else TextTertiary,
                            fontSize = 15.sp,
                            fontWeight = if (selectedTab == index) FontWeight.Medium else FontWeight.Normal,
                        )
                    },
                )
            }
        }

        // ── Content ──────────────────────────────────────────────────
        when (selectedTab) {
            0 -> LutDiscoveryTab(
                selectedCategory = selectedCategory,
                onCategorySelected = {
                    selectedCategory = it
                    vm.filterByCategory(it)
                },
                onDownloadLut = { lut -> vm.downloadLutPack(lut.id); onDownloadLut(lut) },
                onImportLut = onImportLut,
                categories = categories,
                featuredPacks = marketState.featuredPacks,
                lutItems = if (marketState.lutPacks.isNotEmpty()) marketState.lutPacks else sampleLutItems,
                isLoading = marketState.isLoading,
            )
            1 -> LutMyCollectionTab(
                onImportLut = onImportLut,
                myLuts = marketState.lutPacks.filter { it.isDownloaded },
            )
        }
    }
}

@Composable
private fun LutDiscoveryTab(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    onDownloadLut: (LutItem) -> Unit,
    onImportLut: (LutItem) -> Unit,
    categories: List<String> = listOf("全部", "胶片", "电影", "复古", "手机"),
    featuredPacks: List<FeaturedLutPack> = emptyList(),
    lutItems: List<LutItem> = sampleLutItems,
    isLoading: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // 搜索栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(ColorOS16Colors.Surface2)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(android.R.drawable.ic_menu_search),
                contentDescription = "搜索",
                tint = TextTertiary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "搜索 LUT 包…",
                color = TextTertiary,
                fontSize = 14.sp,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 精选 LUT 包轮播
        Text(
            text = "精选推荐",
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(10.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(end = 4.dp),
        ) {
            items(
                items = if (featuredPacks.isNotEmpty()) featuredPacks else featuredLutPacks,
                key = { it.id },
            ) { pack ->
                FeaturedLutPackCard(pack = pack)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 分类标签
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            categories.forEach { category ->
                val isSelected = category == selectedCategory
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (isSelected) HasselbladOrange else ColorOS16Colors.Surface3,
                        )
                        .clickable { onCategorySelected(category) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = category,
                        color = if (isSelected) ColorOS16Colors.TextHigh else TextSecondary,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // LUT 卡片网格
        val filteredLuts = if (selectedCategory == "全部") {
            lutItems
        } else {
            lutItems.filter { it.category == selectedCategory }
        }

        // 使用列布局模拟网格（2 列）
        filteredLuts.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowItems.forEach { lut ->
                    LutCard(
                        lut = lut,
                        onDownload = { onDownloadLut(lut) },
                        onImport = { onImportLut(lut) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowItems.size < 2) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        Spacer(modifier = Modifier.height(48.dp))
    }
}

@Composable
private fun LutMyCollectionTab(
    onImportLut: (LutItem) -> Unit,
    myLuts: List<LutItem> = sampleLutItems.filter { it.isDownloaded },
) {

    if (myLuts.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    painter = painterResource(android.R.drawable.ic_menu_gallery),
                    contentDescription = null,
                    tint = TextTertiary,
                    modifier = Modifier.size(48.dp),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "还没有收藏的 LUT",
                    color = TextTertiary,
                    fontSize = 15.sp,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "去「发现」页面下载喜欢的 LUT",
                    color = TextTertiary,
                    fontSize = 13.sp,
                )
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            myLuts.chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    rowItems.forEach { lut ->
                        LutCard(
                            lut = lut,
                            onDownload = {},
                            onImport = { onImportLut(lut) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (rowItems.size < 2) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
private fun FeaturedLutPackCard(pack: FeaturedLutPack) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .width(220.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ColorOS16Colors.Surface2)
            .clickable {
                // 打开 LUT 包详情页：将包名和 LUT 列表传递给导航
                // 在实际应用中，这里会导航到 LUT 包详情 Composable
                val intent = android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("rapidraw://lut-pack/${pack.id}")
                )
                // 2026 正式版: 防止无应用处理该 Intent 时崩溃
                try {
                    context.startActivity(intent)
                } catch (_: Exception) {
                    android.widget.Toast.makeText(context, "无法打开 LUT 包详情", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            .padding(12.dp),
    ) {
        // 预览渐变
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Brush.linearGradient(
                        pack.previewGradient.map { Color(it) },
                    ),
                ),
            contentAlignment = Alignment.BottomEnd,
        ) {
            // LUT 数量标签
            Box(
                modifier = Modifier
                    .padding(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "${pack.lutCount} 款 LUT",
                    color = Color.White,
                    fontSize = 11.sp,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = pack.name,
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Text(
            text = pack.author,
            color = TextTertiary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LutCard(
    lut: LutItem,
    onDownload: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(ColorOS16Colors.Surface2)
            .clickable { if (lut.isDownloaded) onImport() else onDownload() }
            .padding(10.dp),
    ) {
        // 预览色块
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.2f)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Brush.linearGradient(
                        lut.previewGradient.map { Color(it) },
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (lut.isDownloaded) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(HasselbladOrange.copy(alpha = 0.8f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "✓",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = lut.name,
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = lut.author,
                color = TextTertiary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = formatDownloadCount(lut.downloadCount),
                color = HasselbladOrange,
                fontSize = 11.sp,
            )
        }
    }
}

private fun formatDownloadCount(count: Int): String {
    return when {
        count >= 10000 -> "${count / 10000}万"
        count >= 1000 -> "${count / 1000}k"
        else -> count.toString()
    }
}

// ── Data Models ───────────────────────────────────────────────────────

data class LutItem(
    val id: String,
    val name: String,
    val author: String,
    val category: String,
    val downloadCount: Int,
    val isDownloaded: Boolean = false,
    val previewGradient: List<Long> = emptyList(),
)

@Immutable
data class FeaturedLutPack(
    val id: String,
    val name: String,
    val author: String,
    val lutCount: Int,
    val previewGradient: List<Long> = emptyList(),
)

// ── Sample Data ───────────────────────────────────────────────────────

private val featuredLutPacks = listOf(
    FeaturedLutPack(
        id = "fp_kodak_classic",
        name = "柯达经典系列",
        author = "RapidRAW 官方",
        lutCount = 8,
        previewGradient = listOf(0xFFD4A574, 0xFF8B6914, 0xFFC4956A),
    ),
    FeaturedLutPack(
        id = "fp_fuji_natura",
        name = "富士自然色彩",
        author = "色彩研究所",
        lutCount = 6,
        previewGradient = listOf(0xFF7CB342, 0xFF558B2F, 0xFF33691E),
    ),
    FeaturedLutPack(
        id = "fp_cinematic_tones",
        name = "电影调色盘",
        author = "FilmLab",
        lutCount = 12,
        previewGradient = listOf(0xFF1A237E, 0xFF4A148C, 0xFF880E4F),
    ),
)

private val sampleLutItems = listOf(
    LutItem("lut_kodak_portra", "Kodak Portra 400", "FilmLab", "胶片", 12500, true, listOf(0xFFE8D5C4, 0xFFC4956A, 0xFFA0826D)),
    LutItem("lut_fuji_velvia", "Fuji Velvia 50", "色彩研究所", "胶片", 9800, false, listOf(0xFF4CAF50, 0xFF2E7D32, 0xFF1B5E20)),
    LutItem("lut_cine_teal_orange", "Teal & Orange", "FilmLab", "电影", 15200, false, listOf(0xFF00838F, 0xFFE65100, 0xFF004D40)),
    LutItem("lut_vintage_fade", "Vintage Fade", "RetroLUT", "复古", 6300, true, listOf(0xFFD7CCC8, 0xFFA1887F, 0xFF8D6E63)),
    LutItem("lut_mobile_vivid", "Mobile Vivid", "RapidRAW 官方", "手机", 21000, false, listOf(0xFFFF6F00, 0xFFF57F17, 0xFFFF8F00)),
    LutItem("lut_agfa_vista", "Agfa Vista 400", "FilmLab", "胶片", 7400, false, listOf(0xFFFFAB91, 0xFFFF8A65, 0xFFE64A19)),
    LutItem("lut_cine_moody", "Cinematic Moody", "FilmLab", "电影", 8900, false, listOf(0xFF37474F, 0xFF455A64, 0xFF263238)),
    LutItem("lut_retro_warm", "Retro Warm", "RetroLUT", "复古", 5200, false, listOf(0xFFFFCC80, 0xFFFFB74D, 0xFFF57C00)),
)

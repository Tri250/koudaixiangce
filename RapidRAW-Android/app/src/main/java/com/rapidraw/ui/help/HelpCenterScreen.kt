package com.rapidraw.ui.help

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Filter
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rapidraw.ui.theme.TextSecondary
import com.rapidraw.ui.theme.TextTertiary

/**
 * 帮助中心屏幕 — v1.7.0 正式版新增。
 *
 * 提供：
 * - 功能分类导航（入门、编辑、导出、AI、预设、常见问题）
 * - 带图标的可展开帮助条目
 * - 常见问题解答
 * - 反馈入口
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpCenterScreen(
    onBack: () -> Unit,
    onNavigateToFeedback: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("帮助中心") },
                navigationIcon = {
                    IconButton(onClick = onBack, content = {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    })
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            // ── 功能分类 ──────────────────────────────────────────────
            item {
                Text(
                    "功能指南",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            item { HelpSection("入门指南", Icons.Filled.Camera, gettingStartedItems) }
            item { HelpSection("图片编辑", Icons.Filled.Tune, editingItems) }
            item { HelpSection("导出与分享", Icons.Filled.Share, exportItems) }
            item { HelpSection("AI 功能", Icons.Filled.Brush, aiItems) }
            item { HelpSection("预设与胶片", Icons.Filled.Filter, presetItems) }

            // ── 常见问题 ──────────────────────────────────────────────
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "常见问题",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            items(faqItems) { faq ->
                HelpSection(faq.first, Icons.Filled.HelpOutline, listOf(faq.second))
            }

            // ── 反馈入口 ──────────────────────────────────────────────
            item {
                Spacer(Modifier.height(8.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToFeedback() },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "还有问题？",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                "向我们反馈，我们会尽快回复",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

// ── 帮助条目 ────────────────────────────────────────────────────────

@Composable
private fun HelpSection(
    title: String,
    icon: ImageVector,
    content: List<String>,
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(title, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Icon(
                    Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = TextSecondary,
                )
            }

            AnimatedVisibility(visible = expanded, enter = fadeIn(), exit = fadeOut()) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    content.forEach { item ->
                        Text(
                            "• $item",
                            fontSize = 14.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

// ── 帮助内容数据 ─────────────────────────────────────────────────────

private val gettingStartedItems = listOf(
    "打开应用后，图库页面会自动显示设备中的 RAW 和 JPEG 照片",
    "点击任意照片进入编辑器，支持 RAW 格式的完整解析",
    "使用底部标签切换「调整」「裁剪」「胶片」「遮罩」工具面板",
    "长按照片可进入多选模式，进行批量操作",
)

private val editingItems = listOf(
    "「调整」面板：包含基础/颜色/曲线/细节/效果/高级/变换七个子面板",
    "「曲线」工具：支持 RGB 通道独立调整和亮度曲线",
    "「裁剪」：支持自由裁剪、旋转、翻转、透视矫正和自动拉直",
    "「遮罩」：支持画笔/渐变/颜色范围/亮度范围四种遮罩模式",
    "图层：支持叠加、柔光、强光、差值等 15 种混合模式",
    "编辑历史：支持撤销/重做和分支历史，可随时回退到任意编辑节点",
)

private val exportItems = listOf(
    "支持导出格式：JPEG、PNG、HEIF、AVIF、WebP、TIFF",
    "HDR 导出：支持 Ultra HDR (JPEG) 和 HDR AVIF/HEIF",
    "导出队列：可批量添加导出任务，后台自动处理",
    "水印：支持自定义文字和图片水印",
    "ICC 色彩配置：可嵌入 sRGB / Display P3 / Adobe RGB 等配置文件",
)

private val aiItems = listOf(
    "AI 去噪：基于 Guided Filter 的保边降噪算法",
    "AI 消除：智能移除照片中的不需要对象",
    "AI 遮罩：自动检测主体/天空/背景生成精确遮罩",
    "AI 场景检测：自动识别场景类型并推荐最佳调整参数",
    "AI 调色：通过 LLM 自然语言描述自动调整色彩（需配置 API Key）",
)

private val presetItems = listOf(
    "「大师配方」：内置 30+ 经典胶片模拟（Portra、Velvia、Tri-X 等）",
    "自定义预设：可将当前编辑参数保存为预设，随时调用",
    "配方分享：可将编辑参数导出为配方文件，分享给其他用户",
    "LUT 市场：浏览和下载社区分享的 LUT 色彩查找表",
    "预设导入：支持 .xmp 和 .cube 格式的预设导入",
)

private val faqItems = listOf(
    "为什么 RAW 文件加载较慢？" to "RAW 文件包含完整的传感器数据，体积较大（通常 20-80MB），解码需要一定时间。我们使用了 Vulkan GPU 加速和分块处理来优化加载速度。",
    "如何获得最佳的 HDR 导出效果？" to "建议使用 HEIF 或 AVIF 格式导出 HDR，并确保目标设备支持 HDR 显示。可在导出设置中调整增益映射强度。",
    "AI 消除功能需要联网吗？" to "AI 消除功能需要连接 ComfyUI 服务器或配置 API Key。基础的去噪和场景检测功能完全在本地运行，无需联网。",
    "如何恢复误删的预设？" to "预设保存后存储在应用私有目录中。如果误删，可以尝试从「配方分享」中重新导入，或从备份中恢复。",
    "应用支持哪些相机型号的 RAW？" to "RapidRAW 基于 LibRaw 库，支持超过 1000 种相机型号的 RAW 格式，包括 Canon、Nikon、Sony、Fujifilm、Leica 等主流品牌。",
)
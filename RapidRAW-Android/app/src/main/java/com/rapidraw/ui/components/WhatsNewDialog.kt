package com.rapidraw.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 版本更新日志弹窗。
 *
 * 首次启动新版本时展示，引导用户了解新功能。
 * 使用 SharedPreferences 记录上次显示的版本，同版本不重复展示。
 *
 * @since v1.10.3（正式版功能完整性）
 */
@Composable
fun WhatsNewDialog(
    versionName: String,
    releaseDate: String,
    features: List<FeatureItem>,
    onDismiss: () -> Unit,
    onLearnMore: (() -> Unit)? = null,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 标题
                Text(
                    text = "RapidRAW $versionName",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = releaseDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(20.dp))

                // 新功能列表
                features.forEach { feature ->
                    FeatureRow(feature)
                    Spacer(modifier = Modifier.height(12.dp))
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 操作按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (onLearnMore != null) {
                        OutlinedButton(
                            onClick = onLearnMore,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("了解更多")
                        }
                    }
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("开始使用")
                    }
                }
            }
        }
    }
}

/**
 * 新功能条目。
 */
data class FeatureItem(
    val emoji: String,
    val title: String,
    val description: String,
)

@Composable
private fun FeatureRow(feature: FeatureItem) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = feature.emoji,
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = feature.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = feature.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 获取当前版本的新功能列表。
 * 每次发版时更新此函数。
 */
fun getWhatsNewFeatures(versionName: String): List<FeatureItem> {
    return when {
        versionName.startsWith("1.10") -> listOf(
            FeatureItem(
                emoji = "\uD83D\uDD12",
                title = "安全加固",
                description = "Android Keystore 加密存储、证书固定、安全完整性检测"
            ),
            FeatureItem(
                emoji = "\uD83D\uDCE6",
                title = "构建优化",
                description = "并行构建、配置缓存、版本目录、构建变体支持"
            ),
            FeatureItem(
                emoji = "\uD83D\uDCF1",
                title = "全面兼容",
                description = "MIUI/HyperOS/ColorOS/OneUI 适配、App Standby 感知"
            ),
            FeatureItem(
                emoji = "\uD83C\uDFA8",
                title = "Material You 染客",
                description = "动态取色、骨架屏、响应式布局、压感笔支持"
            ),
            FeatureItem(
                emoji = "\u2699\uFE0F",
                title = "性能飞跃",
                description = "冷启动优化、帧率监控、基线配置文件、网络缓存"
            ),
        )
        else -> listOf(
            FeatureItem(
                emoji = "\u2728",
                title = "全新版本",
                description = "RapidRAW 持续进化，为你带来更好的 RAW 编辑体验"
            ),
        )
    }
}
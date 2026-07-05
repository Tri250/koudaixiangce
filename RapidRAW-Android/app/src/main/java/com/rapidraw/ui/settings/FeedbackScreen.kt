package com.rapidraw.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rapidraw.ui.theme.ColorOS16Colors
import com.rapidraw.ui.theme.EditorBackground
import com.rapidraw.ui.theme.EditorSurface
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.TextPrimary
import com.rapidraw.ui.theme.TextSecondary
import com.rapidraw.ui.theme.TextTertiary

/**
 * 用户反馈页面 — 支持问题反馈、功能建议、其他意见。
 * 使用 ColorOS16Colors + HasselbladOrange 主题。
 */
@Composable
fun FeedbackScreen(
    onBack: () -> Unit,
    onSubmit: (type: String, content: String, email: String?) -> Unit = { _, _, _ -> },
) {
    var selectedType by remember { mutableStateOf("问题反馈") }
    var feedbackContent by remember { mutableStateOf(TextFieldValue("")) }
    var emailContent by remember { mutableStateOf(TextFieldValue("")) }

    val feedbackTypes = listOf("问题反馈", "功能建议", "其他")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EditorBackground)
            .statusBarsPadding()
            .imePadding(),
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
                text = "反馈与建议",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        // ── Scrollable Content ───────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // 反馈类型
            Text(
                text = "反馈类型",
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                feedbackTypes.forEach { type ->
                    val isSelected = type == selectedType
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (isSelected) HasselbladOrange else ColorOS16Colors.Surface3,
                            )
                            .clickable { selectedType = type }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = type,
                            color = if (isSelected) ColorOS16Colors.TextHigh else TextSecondary,
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 反馈内容
            Text(
                text = "反馈内容",
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 文本输入框
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ColorOS16Colors.Surface2)
                    .border(
                        width = 1.dp,
                        color = ColorOS16Colors.HairlineStrong,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .padding(16.dp),
            ) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (feedbackContent.text.isEmpty()) {
                        Text(
                            text = when (selectedType) {
                                "问题反馈" -> "请描述您遇到的问题，如崩溃、闪退、功能异常等…"
                                "功能建议" -> "请描述您希望添加或改进的功能…"
                                else -> "请输入您的反馈…"
                            },
                            color = TextTertiary,
                            fontSize = 14.sp,
                        )
                    }
                    androidx.compose.foundation.text.BasicTextField(
                        value = feedbackContent,
                        onValueChange = { feedbackContent = it },
                        modifier = Modifier.fillMaxSize(),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = TextPrimary,
                            fontSize = 14.sp,
                            lineHeight = 22.sp,
                        ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 邮箱（可选）
            Text(
                text = "联系邮箱（可选）",
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ColorOS16Colors.Surface2)
                    .border(
                        width = 1.dp,
                        color = ColorOS16Colors.HairlineStrong,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                if (emailContent.text.isEmpty()) {
                    Text(
                        text = "方便我们跟进回复",
                        color = TextTertiary,
                        fontSize = 14.sp,
                    )
                }
                androidx.compose.foundation.text.BasicTextField(
                    value = emailContent,
                    onValueChange = { emailContent = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = TextPrimary,
                        fontSize = 14.sp,
                    ),
                    singleLine = true,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "我们不会将您的邮箱用于其他用途",
                color = TextTertiary,
                fontSize = 12.sp,
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 提交按钮
            val canSubmit = feedbackContent.text.trim().isNotEmpty()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (canSubmit) HasselbladOrange else ColorOS16Colors.Surface4,
                    )
                    .clickable(enabled = canSubmit) {
                        onSubmit(
                            selectedType,
                            feedbackContent.text.trim(),
                            emailContent.text.trim().ifBlank { null },
                        )
                    }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "提交反馈",
                    color = if (canSubmit) ColorOS16Colors.TextHigh else TextTertiary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 崩溃日志提示
            if (selectedType == "问题反馈") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(ColorOS16Colors.Surface2)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(android.R.drawable.ic_menu_info_details),
                        contentDescription = null,
                        tint = ColorOS16Colors.InfoBlue,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text(
                        text = "提交崩溃相关问题时，崩溃日志将自动附带上传以帮助我们定位问题",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

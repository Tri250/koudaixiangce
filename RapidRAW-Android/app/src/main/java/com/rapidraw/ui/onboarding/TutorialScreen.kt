package com.rapidraw.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rapidraw.ui.theme.ColorOS16Colors
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.HasselbladOrangeDark
import com.rapidraw.ui.theme.HasselbladOrangeLight
import com.rapidraw.ui.theme.TextPrimary
import com.rapidraw.ui.theme.TextSecondary
import com.rapidraw.ui.theme.TextTertiary
import kotlinx.coroutines.launch

/**
 * ViewPager-style tutorial / onboarding screen.
 *
 * 5 pages introducing core RapidRAW features:
 * 0 - Professional RAW Processing
 * 1 - AI Smart Editing
 * 2 - Professional Scopes
 * 3 - HDR Export
 * 4 - Portrait Retouching
 */
@Composable
fun TutorialScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = viewModel(),
) {
    val isCompleted by viewModel.isCompleted.collectAsState()

    LaunchedEffect(isCompleted) {
        if (isCompleted) onComplete()
    }

    if (isCompleted) return

    val totalPages = 5
    val pagerState = rememberPagerState(pageCount = { totalPages })
    val scope = rememberCoroutineScope()
    val currentPage = pagerState.currentPage

    LaunchedEffect(currentPage) {
        viewModel.setCurrentPage(currentPage)
    }

    val tutorialPages = listOf(
        TutorialPageData(
            icon = Icons.Outlined.CameraAlt,
            iconBackgroundColor = ColorOS16Colors.HasselbladOrangeTint,
            iconTint = HasselbladOrange,
            title = "专业 RAW 处理",
            description = "支持 650+ RAW 格式，AgX/ACES 色彩科学，电影级色调渲染。",
        ),
        TutorialPageData(
            icon = Icons.Outlined.AutoFixHigh,
            iconBackgroundColor = Color(0x1FBF5AF2),
            iconTint = ColorOS16Colors.AiPurple,
            title = "AI 智能修图",
            description = "AI 去噪、智能优化、语义分割，一键提升照片质感。",
        ),
        TutorialPageData(
            icon = Icons.Outlined.Speed,
            iconBackgroundColor = Color(0x1F30D158),
            iconTint = ColorOS16Colors.ScopeTraceGreen,
            title = "专业示波器",
            description = "波形监视器、RGB Parade、矢量示波器，精确曝光与色彩分析。",
        ),
        TutorialPageData(
            icon = Icons.Outlined.HighQuality,
            iconBackgroundColor = Color(0x1F0A84FF),
            iconTint = ColorOS16Colors.InfoBlue,
            title = "HDR 导出",
            description = "Ultra HDR JPEG、EXR、10-bit HEIF，震撼高动态范围输出。",
        ),
        TutorialPageData(
            icon = Icons.Outlined.Face,
            iconBackgroundColor = Color(0x1FFF9F0A),
            iconTint = ColorOS16Colors.ScopeSkinTone,
            title = "人像精修",
            description = "人脸检测、瘦脸、拉腿，专业人像精修工具。",
        ),
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorOS16Colors.AmoledBlack),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            // Pager content
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { page ->
                val data = tutorialPages[page]
                TutorialPage(
                    data = data,
                    isLast = page == totalPages - 1,
                    onGetStarted = {
                        viewModel.completeOnboarding()
                        onComplete()
                    },
                )
            }

            // Bottom section: page indicators + navigation
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Page indicator dots
                TutorialPageIndicator(
                    totalPages = totalPages,
                    currentPage = currentPage,
                    modifier = Modifier.padding(vertical = 16.dp),
                )

                // Skip / Next button row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Skip button
                    if (currentPage < totalPages - 1) {
                        Text(
                            text = "跳过",
                            color = TextTertiary,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .clickable {
                                    viewModel.completeOnboarding()
                                    onComplete()
                                }
                                .padding(8.dp),
                        )
                    } else {
                        Spacer(modifier = Modifier.size(60.dp))
                    }

                    // Next / Get Started button
                    Button(
                        onClick = {
                            if (currentPage < totalPages - 1) {
                                scope.launch {
                                    pagerState.animateScrollToPage(currentPage + 1)
                                }
                            } else {
                                viewModel.completeOnboarding()
                                onComplete()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = HasselbladOrange,
                        ),
                        shape = RoundedCornerShape(24.dp),
                    ) {
                        Text(
                            text = if (currentPage < totalPages - 1) "下一步" else "开始使用",
                            color = TextPrimary,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

// ── Data model for a single tutorial page ────────────────────────────

private data class TutorialPageData(
    val icon: ImageVector,
    val iconBackgroundColor: Color,
    val iconTint: Color,
    val title: String,
    val description: String,
)

// ── Single tutorial page composable ──────────────────────────────────

@Composable
private fun TutorialPage(
    data: TutorialPageData,
    isLast: Boolean = false,
    onGetStarted: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Illustration area — colored box with icon
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(data.iconBackgroundColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = data.icon,
                contentDescription = data.title,
                tint = data.iconTint,
                modifier = Modifier.size(56.dp),
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Title
        Text(
            text = data.title,
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Description
        Text(
            text = data.description,
            color = TextSecondary,
            fontSize = 15.sp,
            lineHeight = 24.sp,
            textAlign = TextAlign.Center,
        )

        // Last page: inline "Get Started" button
        if (isLast && onGetStarted != null) {
            Spacer(modifier = Modifier.height(40.dp))

            Button(
                onClick = onGetStarted,
                colors = ButtonDefaults.buttonColors(
                    containerColor = HasselbladOrange,
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Text(
                    text = "开始使用",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

// ── Page indicator dots ──────────────────────────────────────────────

@Composable
private fun TutorialPageIndicator(
    totalPages: Int,
    currentPage: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(totalPages) { index ->
            val isSelected = index == currentPage
            val dotColor by animateColorAsState(
                targetValue = if (isSelected) HasselbladOrange
                else TextTertiary.copy(alpha = 0.4f),
                animationSpec = tween(300),
                label = "dot_color",
            )
            Box(
                modifier = Modifier
                    .size(if (isSelected) 8.dp else 6.dp)
                    .clip(CircleShape)
                    .background(color = dotColor),
            )
        }
    }
}

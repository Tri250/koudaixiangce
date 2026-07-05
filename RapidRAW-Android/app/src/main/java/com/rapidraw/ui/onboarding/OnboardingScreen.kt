package com.rapidraw.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rapidraw.ui.theme.EditorBackground
import com.rapidraw.ui.theme.EditorSurfaceVariant
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.HasselbladOrangeDark
import com.rapidraw.ui.theme.HasselbladOrangeLight
import com.rapidraw.ui.theme.TextPrimary
import com.rapidraw.ui.theme.TextSecondary
import com.rapidraw.ui.theme.TextTertiary
import kotlinx.coroutines.launch

/**
 * First-launch onboarding experience.
 *
 * Pages:
 * 0 - Welcome page with logo, tagline, and "开始使用" button
 * 1 - Permission page explaining storage access
 * 2-4 - Feature highlights (3 swipeable pages)
 * 5 - Get started (final page)
 */
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = viewModel(),
) {
    val isCompleted by viewModel.isCompleted.collectAsState()

    // v1.5.5 hotfix: 防止 onComplete 被多次调用导致 Navigation Compose 二次导航崩溃。
    // 旧代码在 click handler 中直接调用 onComplete()，同时 LaunchedEffect(isCompleted)
    // 也会在 isCompleted 变为 true 时再次调用 onComplete()，导致双重导航。
    var hasNavigated by remember { mutableStateOf(false) }

    val navigateOnce: () -> Unit = {
        if (!hasNavigated) {
            hasNavigated = true
            onComplete()
        }
    }

    // If onboarding already completed, skip immediately
    LaunchedEffect(isCompleted) {
        if (isCompleted) navigateOnce()
    }

    if (isCompleted) return

    val totalPages = 5 // Welcome, Permission, 3 Features
    val pagerState = rememberPagerState(pageCount = { totalPages })
    val scope = rememberCoroutineScope()

    val currentPage = pagerState.currentPage
    LaunchedEffect(currentPage) {
        viewModel.setCurrentPage(currentPage)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EditorBackground),
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
                when (page) {
                    0 -> WelcomePage(
                        onGetStarted = {
                            scope.launch {
                                pagerState.animateScrollToPage(1)
                            }
                        },
                    )
                    1 -> PermissionPage(
                        onGrant = {
                            scope.launch {
                                pagerState.animateScrollToPage(2)
                            }
                        },
                        onSkip = {
                            scope.launch {
                                pagerState.animateScrollToPage(2)
                            }
                        },
                    )
                    2 -> FeaturePage(
                        icon = "\u2728", // ✨
                        title = "智能优化",
                        description = "AI 驱动的智能场景识别与自动优化，一键提升照片质感。自动分析曝光、白平衡、色彩，为你推荐最佳调整方案。",
                    )
                    3 -> FeaturePage(
                        icon = "\uD83C\uDFA8", // 🎨
                        title = "专业调色",
                        description = "CDL 色彩分级、曲线调整、8 色 HSL 面板。专业级色彩控制工具，轻松实现电影级调色效果。",
                    )
                    4 -> FeaturePage(
                        icon = "\uD83D\uDCF7", // 📷
                        title = "胶片模拟",
                        description = "20+ 经典胶片模拟预设，从哈苏到柯达，从富士到伊尔福。真实还原胶片色彩与颗粒感。",
                        isLast = true,
                        onGetStarted = {
                            viewModel.completeOnboarding()
                            navigateOnce()
                        },
                    )
                }
            }

            // Bottom section: page indicators + navigation
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Page indicator dots
                PageIndicator(
                    totalPages = totalPages,
                    currentPage = currentPage,
                    modifier = Modifier.padding(vertical = 16.dp),
                )

                // Skip / Next button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Skip button (not on last page)
                    if (currentPage < totalPages - 1) {
                        Text(
                            text = "跳过",
                            color = TextTertiary,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .clickable {
                                    viewModel.completeOnboarding()
                                    navigateOnce()
                                }
                                .padding(8.dp),
                        )
                    } else {
                        Spacer(modifier = Modifier.width(60.dp))
                    }

                    // Next / Get Started button
                    if (currentPage < totalPages - 1) {
                        Button(
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(currentPage + 1)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = HasselbladOrange,
                            ),
                            shape = RoundedCornerShape(24.dp),
                        ) {
                            Text(
                                text = "下一步",
                                color = TextPrimary,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomePage(onGetStarted: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Logo area
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(HasselbladOrangeDark, HasselbladOrange, HasselbladOrangeLight),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "R",
                color = TextPrimary,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "RapidRAW",
            color = TextPrimary,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "专业级 RAW 照片编辑器",
            color = TextSecondary,
            fontSize = 16.sp,
        )

        Spacer(modifier = Modifier.height(48.dp))

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

@Composable
private fun PermissionPage(
    onGrant: () -> Unit,
    onSkip: () -> Unit,
) {
    val context = LocalContext.current

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ ->
        onGrant()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Storage icon
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(EditorSurfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "\uD83D\uDCC1", // 📁
                fontSize = 36.sp,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "访问你的照片",
            color = TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "RapidRAW 需要访问照片权限来：\n" +
                "\u2022 浏览和导入你的 RAW 与 JPG 照片\n" +
                "\u2022 保存编辑后的图片到相册\n" +
                "\u2022 读取 EXIF 元数据信息",
            color = TextSecondary,
            fontSize = 14.sp,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                val permissions = mutableListOf<String>().apply {
                    when {
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                            add(Manifest.permission.READ_MEDIA_IMAGES)
                            add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                        }
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                            add(Manifest.permission.READ_MEDIA_IMAGES)
                        }
                        else -> {
                            add(Manifest.permission.READ_EXTERNAL_STORAGE)
                        }
                    }
                    // Android 13+ 通知权限与存储权限一并引导申请。
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                val allGranted = permissions.all {
                    ContextCompat.checkSelfPermission(context, it) ==
                        PermissionChecker.PERMISSION_GRANTED
                }
                if (allGranted) {
                    onGrant()
                } else {
                    permissionLauncher.launch(permissions.toTypedArray())
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = HasselbladOrange,
            ),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Text(
                text = "授权访问",
                color = TextPrimary,
                fontWeight = FontWeight.Medium,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "稍后授权",
            color = TextTertiary,
            fontSize = 14.sp,
            modifier = Modifier
                .clickable(onClick = onSkip)
                .padding(8.dp),
        )
    }
}

@Composable
private fun FeaturePage(
    icon: String,
    title: String,
    description: String,
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
        // Feature icon
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(EditorSurfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = icon,
                fontSize = 44.sp,
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = title,
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = description,
            color = TextSecondary,
            fontSize = 15.sp,
            lineHeight = 24.sp,
            textAlign = TextAlign.Center,
        )

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
                    text = "开始编辑",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

/**
 * Page indicator dots at the bottom of the onboarding.
 */
@Composable
private fun PageIndicator(
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
            Box(
                modifier = Modifier
                    .size(if (index == currentPage) 8.dp else 6.dp)
                    .clip(CircleShape)
                    .background(
                        color = if (index == currentPage) HasselbladOrange
                        else TextTertiary.copy(alpha = 0.4f),
                    ),
            )
        }
    }
}

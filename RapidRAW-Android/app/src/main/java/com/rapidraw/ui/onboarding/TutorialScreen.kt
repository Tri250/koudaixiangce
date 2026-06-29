package com.rapidraw.ui.onboarding

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * First-launch tutorial/walkthrough screen.
 * 4 pages with swipe navigation introducing key RapidRAW features.
 * Saves a completion flag to SharedPreferences on completion.
 */
@Composable
fun TutorialScreen(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val totalPages = 4
    val pagerState = rememberPagerState(pageCount = { totalPages })
    val scope = rememberCoroutineScope()
    val currentPage = pagerState.currentPage

    Box(
        modifier = modifier
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
                    0 -> TutorialPage(
                        icon = "\uD83D\uDCF7",
                        title = "Professional RAW Editing",
                        description = "导入并编辑 RAW 照片，支持 600+ 相机型号。\n完全控制曝光、白平衡、色彩和细节。",
                    )
                    1 -> TutorialPage(
                        icon = "\uD83E\uDD16",
                        title = "AI-Powered Tools",
                        description = "智能降噪、超分辨率、场景检测。\nAI 自动优化，一键提升照片质感。",
                    )
                    2 -> TutorialPage(
                        icon = "\uD83C\uDFA5",
                        title = "Film Simulations",
                        description = "多款经典胶片模拟，支持 3D LUT。\n真实还原胶片色彩与颗粒感。",
                    )
                    3 -> TutorialPage(
                        icon = "\u2601\uFE0F",
                        title = "Cloud & Community",
                        description = "同步预设，分享配方。\n加入 RapidRAW 创作者社区。",
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
                Row(
                    modifier = Modifier.padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(totalPages) { index ->
                        val isActive = index == currentPage
                        val dotSize by animateFloatAsState(
                            targetValue = if (isActive) 1f else 0.6f,
                            animationSpec = spring(dampingRatio = 0.5f),
                            label = "dotScale_$index",
                        )
                        Box(
                            modifier = Modifier
                                .size((8f * dotSize).dp)
                                .clip(CircleShape)
                                .background(
                                    color = if (isActive) HasselbladOrange
                                    else TextTertiary.copy(alpha = 0.4f),
                                ),
                        )
                    }
                }

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
                            text = "Skip",
                            color = TextTertiary,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .clickable {
                                    saveTutorialCompleted(context)
                                    onComplete()
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
                                text = "Next",
                                color = TextPrimary,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    } else {
                        Button(
                            onClick = {
                                saveTutorialCompleted(context)
                                onComplete()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = HasselbladOrange,
                            ),
                            shape = RoundedCornerShape(24.dp),
                        ) {
                            Text(
                                text = "Get Started",
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
private fun TutorialPage(
    icon: String,
    title: String,
    description: String,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Icon container
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            HasselbladOrangeDark.copy(alpha = 0.3f),
                            EditorSurfaceVariant,
                        ),
                    ),
                ),
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
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = description,
            color = TextSecondary,
            fontSize = 15.sp,
            lineHeight = 24.sp,
            textAlign = TextAlign.Center,
        )
    }
}

private fun saveTutorialCompleted(context: Context) {
    val prefs = context.getSharedPreferences(TUTORIAL_PREFS, Context.MODE_PRIVATE)
    prefs.edit().putBoolean(KEY_TUTORIAL_COMPLETED, true).apply()
}

private const val TUTORIAL_PREFS = "rapidraw_tutorial"
private const val KEY_TUTORIAL_COMPLETED = "tutorial_completed"
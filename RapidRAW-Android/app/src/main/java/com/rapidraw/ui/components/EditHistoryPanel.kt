package com.rapidraw.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rapidraw.data.model.EditHistoryEntry
import com.rapidraw.data.model.EditHistoryTree
import com.rapidraw.ui.theme.EditorBackground
import com.rapidraw.ui.theme.EditorBorder
import com.rapidraw.ui.theme.EditorSurface
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.TextPrimary
import com.rapidraw.ui.theme.TextSecondary
import com.rapidraw.ui.theme.TextTertiary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 编辑历史面板：从右侧滑入，展示树形编辑历史时间线。
 * 支持跳转到任意历史状态、长按创建分支、左滑删除。
 */
@Composable
fun EditHistoryPanel(
    historyTree: EditHistoryTree?,
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onJumpToEntry: (EditHistoryEntry) -> Unit,
    onBranchFromEntry: (EditHistoryEntry) -> Unit,
    onDeleteEntry: (EditHistoryEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInHorizontally(initialOffsetX = { it }),
        exit = slideOutHorizontally(targetOffsetX = { it }),
        modifier = modifier,
    ) {
        Surface(
            color = EditorSurface,
            shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
            modifier = Modifier
                .fillMaxHeight()
                .width(320.dp),
            tonalElevation = 4.dp,
        ) {
            Column(
                modifier = Modifier.fillMaxHeight(),
            ) {
                // ── 标题栏 ──────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "关闭",
                            tint = TextSecondary,
                        )
                    }
                    Text(
                        text = "编辑历史",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier.size(20.dp),
                    )
                }

                // ── 当前分支标签 ────────────────────────────────
                if (historyTree != null) {
                    val branchDepth = historyTree.currentBranch.size
                    val branchLabel = if (branchDepth <= 1) "主分支" else "分支 ${branchDepth - 1}"
                    Surface(
                        color = HasselbladOrange.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = branchLabel,
                            color = HasselbladOrange,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // ── 历史时间线列表 ────────────────────────────────
                if (historyTree != null) {
                    val entries = historyTree.getEntriesOnBranch(historyTree.current)
                    val currentId = historyTree.current.id
                    val branchPath = historyTree.currentBranch.toSet()

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                        items(
                            items = entries,
                            key = { it.id },
                        ) { entry ->
                            HistoryTimelineItem(
                                entry = entry,
                                isCurrent = entry.id == currentId,
                                isOnBranch = entry.id in branchPath,
                                hasBranches = entry.children.size > 1,
                                onJumpTo = { onJumpToEntry(entry) },
                                onBranchFrom = { onBranchFromEntry(entry) },
                                onDelete = { onDeleteEntry(entry) },
                            )
                        }

                        // 如果有其他分支，显示分支入口
                        val leaves = historyTree.getAllLeaves()
                        val otherBranchLeaves = leaves.filter { leaf ->
                            leaf.id != historyTree.current.id && leaf.id !in branchPath
                        }
                        if (otherBranchLeaves.isNotEmpty()) {
                            item {
                                Surface(
                                    color = EditorBorder.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(
                                            text = "其他分支",
                                            color = TextTertiary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        otherBranchLeaves.forEach { leaf ->
                                            val leafTime = formatTimestamp(leaf.timestamp)
                                            Text(
                                                text = "${leaf.description} ($leafTime)",
                                                color = TextSecondary,
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        onJumpToEntry(leaf)
                                                    }
                                                    .padding(vertical = 4.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // 无历史记录
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = TextTertiary,
                                modifier = Modifier.size(40.dp),
                            )
                            Text(
                                text = "暂无编辑记录",
                                color = TextTertiary,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 时间线中的单个历史条目。
 * 支持点击跳转、长按分支、左滑删除。
 */
@Composable
private fun HistoryTimelineItem(
    entry: EditHistoryEntry,
    isCurrent: Boolean,
    isOnBranch: Boolean,
    hasBranches: Boolean,
    onJumpTo: () -> Unit,
    onBranchFrom: () -> Unit,
    onDelete: () -> Unit,
) {
    var swipeOffset by remember { mutableFloatStateOf(0f) }

    // 时间线竖线颜色
    val lineColor = if (isOnBranch) HasselbladOrange.copy(alpha = 0.5f) else EditorBorder
    // 当前节点指示器
    val dotColor = when {
        isCurrent -> HasselbladOrange
        isOnBranch -> HasselbladOrange.copy(alpha = 0.6f)
        else -> TextTertiary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        if (swipeOffset < -200f) {
                            onDelete()
                        }
                        swipeOffset = 0f
                    },
                    onDragCancel = { swipeOffset = 0f },
                ) { change, dragAmount ->
                    change.consume()
                    // 仅响应左滑
                    if (dragAmount.x < 0) {
                        swipeOffset += dragAmount.x
                    }
                }
            },
    ) {
        // ── 时间线竖线 + 圆点 ─────────────────────────────
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(36.dp),
        ) {
            // 上半段竖线
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(12.dp)
                    .background(lineColor),
            )
            // 节点圆点
            Box(
                modifier = Modifier
                    .size(if (isCurrent) 12.dp else 8.dp)
                    .background(dotColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (hasBranches) {
                    // 分支指示：小圆环
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .background(EditorBackground, CircleShape),
                    )
                }
            }
            // 下半段竖线
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(12.dp)
                    .background(lineColor),
            )
        }

        // ── 内容区 ────────────────────────────────────────
        Box(
            modifier = Modifier
                .weight(1f)
                .offset(x = swipeOffset.dp),
        ) {
            Surface(
                color = if (isCurrent) HasselbladOrange.copy(alpha = 0.12f) else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onJumpTo() },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 小缩略图/图标占位
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(EditorBorder),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = TextTertiary,
                            modifier = Modifier.size(14.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                    ) {
                        Text(
                            text = entry.description,
                            color = if (isCurrent) TextPrimary else TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = if (isCurrent) FontWeight.Medium else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = formatTimestamp(entry.timestamp),
                            color = TextTertiary,
                            fontSize = 10.sp,
                        )
                    }

                    // 当前状态标记
                    if (isCurrent) {
                        Surface(
                            color = HasselbladOrange.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp),
                        ) {
                            Text(
                                text = "当前",
                                color = HasselbladOrange,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }

                    // 分支入口
                    if (hasBranches) {
                        Surface(
                            color = EditorBorder.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.padding(start = 4.dp),
                        ) {
                            Text(
                                text = "+${entry.children.size - 1} 分支",
                                color = TextTertiary,
                                fontSize = 9.sp,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            )
                        }
                    }
                }
            }

            // 左滑删除提示（滑动时渐显）
            if (swipeOffset < -50f) {
                Surface(
                    color = Color.Red.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .offset(x = (-swipeOffset).dp / 2),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "删除",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "删除",
                            color = Color.White,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

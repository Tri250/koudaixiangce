package com.rapidraw.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rapidraw.data.model.EditHistoryEntry
import com.rapidraw.data.model.EditHistoryTree
import com.rapidraw.ui.theme.EditorBorder
import com.rapidraw.ui.theme.EditorSurface
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.TextPrimary
import com.rapidraw.ui.theme.TextSecondary
import com.rapidraw.ui.theme.TextTertiary

/**
 * 可分支编辑历史面板
 *
 * 借鉴 AlcedoStudio 标志性 Git-like 版本控制 UI：
 * - 顶部显示当前分支路径与操作按钮（撤销/重做/新建分支）
 * - 中间为节点列表（从根到当前 head）
 * - 点击节点可"检出"（jumpTo）历史版本
 * - 点击节点可创建分支
 *
 * 使用项目现有的 [EditHistoryTree] 数据结构。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditHistoryPanel(
    visible: Boolean,
    history: EditHistoryTree?,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onCheckout: (EditHistoryEntry) -> Unit,
    onCreateBranch: (EditHistoryEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pendingBranchEntry by remember { mutableStateOf<EditHistoryEntry?>(null) }
    var newBranchName by remember { mutableStateOf("") }

    if (visible && history != null) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = EditorSurface,
        ) {
            EditHistoryContent(
                history = history,
                onUndo = onUndo,
                onRedo = onRedo,
                onCheckout = onCheckout,
                onCreateBranch = { entry -> pendingBranchEntry = entry },
            )
        }
    }

    if (pendingBranchEntry != null) {
        ModalBottomSheet(
            onDismissRequest = { pendingBranchEntry = null; newBranchName = "" },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = EditorSurface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            ) {
                Text(
                    "从该节点创建分支",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = newBranchName,
                    onValueChange = { newBranchName = it },
                    label = { Text("新分支名称") },
                    placeholder = { Text("e.g. 黑白实验") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    androidx.compose.material3.TextButton(onClick = {
                        pendingBranchEntry = null
                        newBranchName = ""
                    }) {
                        Text("取消")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    androidx.compose.material3.Button(
                        onClick = onConfirmCreateBranch@{
                            if (newBranchName.isBlank()) return@onConfirmCreateBranch
                            pendingBranchEntry?.let { onCreateBranch(it) }
                            pendingBranchEntry = null
                            newBranchName = ""
                        },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = HasselbladOrange,
                        ),
                    ) {
                        Text("创建", color = Color.Black)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun EditHistoryContent(
    history: EditHistoryTree,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onCheckout: (EditHistoryEntry) -> Unit,
    onCreateBranch: (EditHistoryEntry) -> Unit,
) {
    val currentId = history.current.id
    val branchPath = remember(history) {
        history.getEntriesOnBranch(history.current)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .height(480.dp),
    ) {
        // 标题
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.AccountTree,
                contentDescription = null,
                tint = HasselbladOrange,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "编辑历史",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onUndo) {
                Icon(Icons.Filled.Undo, contentDescription = "撤销", tint = TextSecondary)
            }
            IconButton(onClick = onRedo) {
                Icon(Icons.Filled.Redo, contentDescription = "重做", tint = TextSecondary)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 分支信息
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(EditorBorder.copy(alpha = 0.3f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = "当前分支",
                color = TextTertiary,
                fontSize = 11.sp,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "main / ${branchPath.size} 节点",
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "总分支: ${history.getAllLeaves().size}",
                color = TextTertiary,
                fontSize = 11.sp,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 当前路径节点列表
        Text(
            text = "当前分支路径",
            color = TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(branchPath.reversed()) { entry ->
                HistoryNodeRow(
                    entry = entry,
                    isHead = entry.id == currentId,
                    onClick = { onCheckout(entry) },
                    onBranchClick = { onCreateBranch(entry) },
                )
            }
        }

        // 底部操作栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "点击节点可跳转到该历史版本",
                color = TextTertiary,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f),
            )
            androidx.compose.material3.TextButton(onClick = { onCreateBranch(history.current) }) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = null,
                    tint = HasselbladOrange,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("新建分支", color = HasselbladOrange, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun HistoryNodeRow(
    entry: EditHistoryEntry,
    isHead: Boolean,
    onClick: () -> Unit,
    onBranchClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isHead) HasselbladOrange.copy(alpha = 0.2f) else Color.Transparent)
            .border(1.dp, if (isHead) HasselbladOrange else EditorBorder.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (isHead) HasselbladOrange else TextTertiary),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.description,
                color = if (isHead) HasselbladOrange else TextPrimary,
                fontSize = 13.sp,
                fontWeight = if (isHead) FontWeight.Bold else FontWeight.Normal,
            )
            Text(
                text = entry.id.take(8) + "  ·  " + formatTime(entry.timestamp),
                color = TextTertiary,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        androidx.compose.material3.TextButton(
            onClick = onBranchClick,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Text("分支", color = TextTertiary, fontSize = 10.sp)
        }
    }
}

private fun formatTime(timestamp: Long): String {
    val date = java.util.Date(timestamp)
    val format = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
    return format.format(date)
}

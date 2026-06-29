package com.rapidraw.ui.components

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewSidebar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.rapidraw.data.model.Adjustments
import com.rapidraw.ui.theme.ColorOS16Colors
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.HasselbladOrangeBright
import com.rapidraw.ui.theme.TextPrimary
import com.rapidraw.ui.theme.TextSecondary
import com.rapidraw.ui.theme.TextTertiary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * OPPO Find 专业摄影编辑工作流
 *
 * 实现类似 OPPO 专业模式的完整工作流：
 * - 快捷操作面板（类似 OPPO 专业模式）
 * - 参数记忆功能（记住用户常用参数）
 * - 快速预设切换（侧滑手势）
 * - 专业模式/简易模式切换
 *
 * 符合 OPPO Find 系列的高端交互标准。
 */

// ══════════════════════════════════════════════════════════════════════
// 参数记忆功能
// ══════════════════════════════════════════════════════════════════════

/**
 * 用户常用参数记录
 *
 * 记录用户调整频率最高的参数，用于智能推荐。
 */
data class ParameterUsageRecord(
    val key: String,
    val parameterName: String,
    val averageValue: Float,
    val usageCount: Int,
    val lastUsedTimestamp: Long,
)

/**
 * 参数记忆管理器
 *
 * 学习用户习惯，记住常用参数值：
 * - 自动记录参数调整历史
 * - 计算用户偏好平均值
 * - 提供智能初始值推荐
 * - 持久化存储用户偏好
 */
class ParameterMemoryManager(
    private val context: Context,
) {

    private val memoryFile = File(context.filesDir, "parameter_memory.json")

    // 参数使用记录
    private val _usageRecords = MutableStateFlow<Map<String, ParameterUsageRecord>>(emptyMap())
    val usageRecords: StateFlow<Map<String, ParameterUsageRecord>> = _usageRecords.asStateFlow()

    // 常用参数排行
    private val _topParameters = MutableStateFlow<List<ParameterUsageRecord>>(emptyList())
    val topParameters: StateFlow<List<ParameterUsageRecord>> = _topParameters.asStateFlow()

    init {
        loadMemory()
    }

    /**
     * 记录参数调整
     *
     * @param key 参数键名
     * @param parameterName 参数显示名称
     * @param value 参数值
     */
    fun recordParameterAdjustment(key: String, parameterName: String, value: Float) {
        val currentRecords = _usageRecords.value.toMutableMap()
        val existing = currentRecords[key]

        val newRecord = if (existing != null) {
            // 更新现有记录：计算新平均值
            val newAverage = (existing.averageValue * existing.usageCount + value) /
                (existing.usageCount + 1)
            ParameterUsageRecord(
                key = key,
                parameterName = parameterName,
                averageValue = newAverage,
                usageCount = existing.usageCount + 1,
                lastUsedTimestamp = System.currentTimeMillis()
            )
        } else {
            // 新记录
            ParameterUsageRecord(
                key = key,
                parameterName = parameterName,
                averageValue = value,
                usageCount = 1,
                lastUsedTimestamp = System.currentTimeMillis()
            )
        }

        currentRecords[key] = newRecord
        _usageRecords.value = currentRecords

        // 更新排行
        updateTopParameters()

        // 持久化
        saveMemory()
    }

    /**
     * 获取参数的推荐初始值
     *
     * 根据用户历史习惯返回建议值。
     */
    fun getRecommendedValue(key: String, defaultValue: Float): Float {
        val record = _usageRecords.value[key]
        return record?.averageValue ?: defaultValue
    }

    /**
     * 获取用户偏好配置
     *
     * 返回所有参数的建议值集合。
     */
    fun getUserPreferredDefaults(): Map<String, Float> {
        return _usageRecords.value.mapValues { it.value.averageValue }
    }

    /**
     * 清除记忆
     */
    fun clearMemory() {
        _usageRecords.value = emptyMap()
        _topParameters.value = emptyList()
        if (memoryFile.exists()) {
            memoryFile.delete()
        }
    }

    private fun updateTopParameters() {
        val sorted = _usageRecords.value.values
            .sortedByDescending { it.usageCount }
            .take(10)
        _topParameters.value = sorted
    }

    private fun saveMemory() {
        try {
            val json = JSONObject()
            _usageRecords.value.forEach { (key, record) ->
                val recordJson = JSONObject()
                recordJson.put("key", record.key)
                recordJson.put("parameterName", record.parameterName)
                recordJson.put("averageValue", record.averageValue)
                recordJson.put("usageCount", record.usageCount)
                recordJson.put("lastUsedTimestamp", record.lastUsedTimestamp)
                json.put(key, recordJson)
            }

            FileOutputStream(memoryFile).use { output ->
                output.write(json.toString().encodeToByteArray())
            }
        } catch (e: Exception) {
            // 忽略保存错误
        }
    }

    private fun loadMemory() {
        try {
            if (!memoryFile.exists()) return

            val json = JSONObject(
                FileInputStream(memoryFile).use { input ->
                    input.readBytes().decodeToString()
                }
            )

            val records = mutableMapOf<String, ParameterUsageRecord>()
            json.keys().forEach { key ->
                val recordJson = json.getJSONObject(key)
                records[key] = ParameterUsageRecord(
                    key = recordJson.getString("key"),
                    parameterName = recordJson.getString("parameterName"),
                    averageValue = recordJson.getDouble("averageValue").toFloat(),
                    usageCount = recordJson.getInt("usageCount"),
                    lastUsedTimestamp = recordJson.getLong("lastUsedTimestamp")
                )
            }

            _usageRecords.value = records
            updateTopParameters()
        } catch (e: Exception) {
            // 忽略加载错误
        }
    }
}

/**
 * Composable 记忆化的参数记忆管理器
 */
@Composable
fun rememberParameterMemoryManager(): ParameterMemoryManager {
    val context = LocalContext.current
    return remember {
        ParameterMemoryManager(context)
    }
}

// ══════════════════════════════════════════════════════════════════════
// 快捷操作面板
// ══════════════════════════════════════════════════════════════════════

/**
 * 快捷操作项
 */
data class QuickAction(
    val id: String,
    val icon: ImageVector,
    val label: String,
    val action: () -> Unit,
    val isAvailable: Boolean = true,
)

/**
 * 快捷操作面板
 *
 * 类似 OPPO 专业模式的快捷操作：
 * - 智能优化一键应用
 * - 快速预设切换
 * - 常用调整参数
 * - 最近使用功能
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickActionPanel(
    actions: List<QuickAction>,
    onActionClick: (QuickAction) -> Unit,
    modifier: Modifier = Modifier,
    recentParameters: List<ParameterUsageRecord> = emptyList(),
    onParameterClick: (ParameterUsageRecord) -> Unit = {},
) {
    val haptic = rememberHapticFeedbackManager()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        // 快捷操作按钮行
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            actions.forEach { action ->
                QuickActionButton(
                    action = action,
                    onClick = {
                        haptic.click()
                        onActionClick(action)
                    },
                )
            }
        }

        // 最近使用的参数（用户习惯）
        if (recentParameters.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "常用参数",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )

            Spacer(modifier = Modifier.height(6.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(recentParameters.take(5)) { param ->
                    RecentParameterCard(
                        parameter = param,
                        onClick = {
                            haptic.click()
                            onParameterClick(param)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickActionButton(
    action: QuickAction,
    onClick: () -> Unit,
) {
    val backgroundColor by animateFloatAsState(
        targetValue = if (action.isAvailable) 0.15f else 0.05f,
        animationSpec = ColorOSMotion.sliderSpring(),
        label = "bgAlpha"
    )

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (action.isAvailable) HasselbladOrange.copy(alpha = backgroundColor)
                else Color.White.copy(alpha = backgroundColor)
            )
            .clickable(enabled = action.isAvailable) { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = action.icon,
                contentDescription = action.label,
                tint = if (action.isAvailable) HasselbladOrangeBright else TextTertiary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = action.label,
                color = if (action.isAvailable) TextPrimary else TextTertiary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun RecentParameterCard(
    parameter: ParameterUsageRecord,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(ColorOS16Colors.Surface3)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = parameter.parameterName,
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "%.1f".format(parameter.averageValue),
                color = HasselbladOrange,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
// 快速预设切换（侧滑手势）
// ══════════════════════════════════════════════════════════════════════

/**
 * 预设数据
 */
data class QuickPreset(
    val id: String,
    val name: String,
    val icon: ImageVector? = null,
    val description: String = "",
    val adjustments: Adjustments,
)

/**
 * 快速预设切换面板
 *
 * 通过侧滑手势快速切换预设：
 * - 左滑切换到下一个预设
 * - 右滑切换到上一个预设
 * - 显示当前预设名称
 * - 预设列表浏览
 */
@Composable
fun QuickPresetSwitcher(
    presets: List<QuickPreset>,
    currentIndex: Int,
    onPresetChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    showSwipeHint: Boolean = true,
) {
    val haptic = rememberHapticFeedbackManager()
    var dragOffset by remember { mutableStateOf(0f) }
    val scope = rememberCoroutineScope()

    if (presets.isEmpty()) return

    val currentPreset = presets.getOrElse(currentIndex) { presets.first() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(presets, currentIndex) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        haptic.click()
                    },
                    onDragEnd = {
                        // 判断是否切换预设
                        if (abs(dragOffset) > 100f) {
                            val newIndex = if (dragOffset > 0) {
                                currentIndex - 1
                            } else {
                                currentIndex + 1
                            }.coerceIn(0, presets.size - 1)

                            if (newIndex != currentIndex) {
                                haptic.gestureConfirm()
                                onPresetChange(newIndex)
                            }
                        }
                        dragOffset = 0f
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        dragOffset += dragAmount
                    }
                )
            }
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        ColorOS16Colors.Surface3,
                        ColorOS16Colors.Surface2,
                    )
                )
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左箭头（上一个预设）
            if (currentIndex > 0) {
                IconButton(
                    onClick = {
                        haptic.click()
                        onPresetChange(currentIndex - 1)
                    },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "上一个预设",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                            .graphicsLayer { rotationZ = 180f },
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(32.dp))
            }

            // 当前预设信息
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (currentPreset.icon != null) {
                        Icon(
                            imageVector = currentPreset.icon,
                            contentDescription = null,
                            tint = HasselbladOrangeBright,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = currentPreset.name,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                if (currentPreset.description.isNotEmpty()) {
                    Text(
                        text = currentPreset.description,
                        color = TextTertiary,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            // 右箭头（下一个预设）
            if (currentIndex < presets.size - 1) {
                IconButton(
                    onClick = {
                        haptic.click()
                        onPresetChange(currentIndex + 1)
                    },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Explore,
                        contentDescription = "下一个预设",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(32.dp))
            }
        }

        // 侧滑提示
        if (showSwipeHint) {
            Text(
                text = "← 侧滑切换 →",
                color = TextTertiary.copy(alpha = 0.5f),
                fontSize = 10.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(top = 4.dp),
            )
        }
    }
}

/**
 * 预设列表侧滑切换容器
 *
 * 支持侧滑手势的预设列表容器。
 */
@Composable
fun PresetSwipeContainer(
    presets: List<QuickPreset>,
    currentIndex: Int,
    onPresetChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (QuickPreset) -> Unit,
) {
    val haptic = rememberHapticFeedbackManager()
    var swipeProgress by remember { mutableStateOf(0f) }

    Box(
        modifier = modifier
            .pointerInput(presets) {
                detectHorizontalDragGestures(
                    onDragStart = { haptic.click() },
                    onDragEnd = {
                        if (abs(swipeProgress) > 0.5f) {
                            val newIndex = if (swipeProgress > 0) {
                                currentIndex - 1
                            } else {
                                currentIndex + 1
                            }.coerceIn(0, presets.size - 1)

                            if (newIndex != currentIndex) {
                                haptic.gestureConfirm()
                                onPresetChange(newIndex)
                            }
                        }
                        swipeProgress = 0f
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        swipeProgress += dragAmount / size.width
                    }
                )
            }
    ) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn() + slideInHorizontally(
                initialOffsetX = { if (swipeProgress > 0) -it else it },
                animationSpec = ColorOSMotion.normalTween()
            ),
            exit = fadeOut() + slideOutHorizontally(
                targetOffsetX = { if (swipeProgress > 0) it else -it },
                animationSpec = ColorOSMotion.fastTween()
            ),
        ) {
            content(presets.getOrElse(currentIndex) { presets.first() })
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
// 专业模式/简易模式切换
// ══════════════════════════════════════════════════════════════════════

/**
 * 编辑模式类型
 */
enum class EditorMode {
    /** 简易模式：仅显示基础调整，适合新手 */
    SIMPLE,

    /** 专业模式：显示所有调整面板，适合专业用户 */
    PROFESSIONAL,

    /** 自定义模式：用户自定义显示内容 */
    CUSTOM
}

/**
 * 编辑模式切换器
 *
 * 类似 OPPO 专业模式的模式切换：
 * - 简易模式：仅基础参数 + 滤镜预设
 * - 专业模式：所有调整面板 + 曲线 + HSL
 * - 平滑切换动画
 * - 触觉反馈
 */
@Composable
fun EditorModeSwitcher(
    currentMode: EditorMode,
    onModeChange: (EditorMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = rememberHapticFeedbackManager()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ColorOS16Colors.Surface3)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        EditorMode.values().forEach { mode ->
            val isSelected = mode == currentMode

            val modeLabel = when (mode) {
                EditorMode.SIMPLE -> "简易"
                EditorMode.PROFESSIONAL -> "专业"
                EditorMode.CUSTOM -> "自定义"
            }

            val modeIcon = when (mode) {
                EditorMode.SIMPLE -> Icons.Default.PhotoCamera
                EditorMode.PROFESSIONAL -> Icons.Default.Tune
                EditorMode.CUSTOM -> Icons.Default.ViewSidebar
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSelected) HasselbladOrange else Color.Transparent
                    )
                    .clickable {
                        if (mode != currentMode) {
                            haptic.heavyClick()
                            onModeChange(mode)
                        }
                    }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = modeIcon,
                        contentDescription = modeLabel,
                        tint = if (isSelected) Color.White else TextSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = modeLabel,
                        color = if (isSelected) Color.White else TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

/**
 * 模式配置
 */
data class EditorModeConfig(
    val showBasicPanel: Boolean = true,
    val showAdvancedPanel: Boolean = false,
    val showCurvesPanel: Boolean = false,
    val showHslPanel: Boolean = false,
    val showColorGradingPanel: Boolean = false,
    val showMaskPanel: Boolean = false,
    val showHistogram: Boolean = false,
    val showWaveform: Boolean = false,
    val showScopeOverlay: Boolean = false,
    val showPixelView: Boolean = false,
) {
    companion object {
        val SIMPLE = EditorModeConfig(
            showBasicPanel = true,
            showAdvancedPanel = false,
            showCurvesPanel = false,
            showHslPanel = false,
            showColorGradingPanel = false,
            showMaskPanel = false,
            showHistogram = false,
            showWaveform = false,
            showScopeOverlay = false,
            showPixelView = false,
        )

        val PROFESSIONAL = EditorModeConfig(
            showBasicPanel = true,
            showAdvancedPanel = true,
            showCurvesPanel = true,
            showHslPanel = true,
            showColorGradingPanel = true,
            showMaskPanel = true,
            showHistogram = true,
            showWaveform = true,
            showScopeOverlay = true,
            showPixelView = true,
        )
    }

    fun fromMode(mode: EditorMode): EditorModeConfig {
        return when (mode) {
            EditorMode.SIMPLE -> SIMPLE
            EditorMode.PROFESSIONAL -> PROFESSIONAL
            EditorMode.CUSTOM -> this
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
// 专业工作流集成面板
// ══════════════════════════════════════════════════════════════════════

/**
 * 专业工作流面板
 *
 * 集成所有专业工作流功能的完整面板：
 * - 快捷操作
 * - 参数记忆
 * - 预设切换
 * - 模式切换
 */
@Composable
fun ProfessionalWorkflowPanel(
    quickActions: List<QuickAction>,
    onQuickAction: (QuickAction) -> Unit,
    presets: List<QuickPreset>,
    currentPresetIndex: Int,
    onPresetChange: (Int) -> Unit,
    currentMode: EditorMode,
    onModeChange: (EditorMode) -> Unit,
    modifier: Modifier = Modifier,
    memoryManager: ParameterMemoryManager = rememberParameterMemoryManager(),
    onApplyPreferredParameters: () -> Unit = {},
) {
    val recentParameters by memoryManager.topParameters.collectAsState()
    val haptic = rememberHapticFeedbackManager()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        // 模式切换
        EditorModeSwitcher(
            currentMode = currentMode,
            onModeChange = onModeChange,
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 快捷操作面板
        QuickActionPanel(
            actions = quickActions,
            onActionClick = onQuickAction,
            recentParameters = recentParameters,
            onParameterClick = { param ->
                // 应用用户的常用参数值
                haptic.click()
                onApplyPreferredParameters()
            },
        )

        // 预设切换（如果有预设）
        if (presets.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))

            QuickPresetSwitcher(
                presets = presets,
                currentIndex = currentPresetIndex,
                onPresetChange = onPresetChange,
                showSwipeHint = true,
            )
        }
    }
}

/**
 * 创建默认快捷操作列表
 */
fun createDefaultQuickActions(
    onSmartOptimize: () -> Unit,
    onReset: () -> Unit,
    onCompare: () -> Unit,
    onSave: () -> Unit,
    onHistory: () -> Unit,
    onApplyPreferred: () -> Unit,
): List<QuickAction> {
    return listOf(
        QuickAction(
            id = "smart_optimize",
            icon = Icons.Default.AutoFixHigh,
            label = "智能优化",
            action = onSmartOptimize,
        ),
        QuickAction(
            id = "reset",
            icon = Icons.Default.Refresh,
            label = "重置",
            action = onReset,
        ),
        QuickAction(
            id = "compare",
            icon = Icons.Default.Brush,
            label = "对比",
            action = onCompare,
        ),
        QuickAction(
            id = "save",
            icon = Icons.Default.Save,
            label = "保存",
            action = onSave,
        ),
        QuickAction(
            id = "history",
            icon = Icons.Default.History,
            label = "历史",
            action = onHistory,
        ),
        QuickAction(
            id = "preferred",
            icon = Icons.Default.Book,
            label = "偏好",
            action = onApplyPreferred,
        ),
    )
}

/**
 * 工作流状态管理器
 *
 * 集中管理专业工作流的所有状态。
 */
class ProfessionalWorkflowState(
    private val memoryManager: ParameterMemoryManager,
) {

    private val _currentMode = MutableStateFlow(EditorMode.SIMPLE)
    val currentMode: StateFlow<EditorMode> = _currentMode.asStateFlow()

    private val _currentPresetIndex = MutableStateFlow(0)
    val currentPresetIndex: StateFlow<Int> = _currentPresetIndex.asStateFlow()

    private val _isQuickPanelVisible = MutableStateFlow(false)
    val isQuickPanelVisible: StateFlow<Boolean> = _isQuickPanelVisible.asStateFlow()

    private val _presets = MutableStateFlow<List<QuickPreset>>(emptyList())
    val presets: StateFlow<List<QuickPreset>> = _presets.asStateFlow()

    fun setMode(mode: EditorMode) {
        _currentMode.value = mode
    }

    fun setPresetIndex(index: Int) {
        _currentPresetIndex.value = index.coerceIn(0, _presets.value.size - 1)
    }

    fun toggleQuickPanel() {
        _isQuickPanelVisible.value = !_isQuickPanelVisible.value
    }

    fun setPresets(newPresets: List<QuickPreset>) {
        _presets.value = newPresets
        _currentPresetIndex.value = _currentPresetIndex.value.coerceIn(0, newPresets.size - 1)
    }

    fun recordParameterAdjustment(key: String, name: String, value: Float) {
        memoryManager.recordParameterAdjustment(key, name, value)
    }

    fun getPreferredValue(key: String, defaultValue: Float): Float {
        return memoryManager.getRecommendedValue(key, defaultValue)
    }

    fun getModeConfig(): EditorModeConfig {
        return EditorModeConfig.PROFESSIONAL.fromMode(_currentMode.value)
    }
}

/**
 * Composable 记忆化的工作流状态
 */
@Composable
fun rememberProfessionalWorkflowState(): ProfessionalWorkflowState {
    val memoryManager = rememberParameterMemoryManager()
    return remember {
        ProfessionalWorkflowState(memoryManager)
    }
}
package com.rapidraw.ui.settings

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import android.content.Context
import android.content.Intent
import android.opengl.EGL14
import android.opengl.EGLExt
import android.widget.Toast
import com.rapidraw.core.HdrDisplayManager
import com.rapidraw.data.model.FilmSimulation
import com.rapidraw.ui.components.HasselSlider
import com.rapidraw.ui.theme.EditorBackground
import com.rapidraw.ui.theme.EditorSurface
import com.rapidraw.ui.theme.EditorBorder
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.TextPrimary
import com.rapidraw.ui.theme.TextSecondary
import com.rapidraw.ui.theme.TextTertiary

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToPrivacy: () -> Unit = {},
    onNavigateToUserAgreement: () -> Unit = {},
    onNavigateToFeedback: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel(),
) {
    val context = LocalContext.current

    val gpuAcceleration by viewModel.gpuAcceleration.collectAsState()
    val previewQuality by viewModel.previewQuality.collectAsState()
    val threadCount by viewModel.threadCount.collectAsState()

    val hdrDisplay by viewModel.hdrDisplay.collectAsState()
    val histogramType by viewModel.histogramType.collectAsState()
    val clippingWarning by viewModel.clippingWarning.collectAsState()
    val hapticFeedback by viewModel.hapticFeedback.collectAsState()

    val defaultFilmSimulation by viewModel.defaultFilmSimulation.collectAsState()
    val autoSaveEdits by viewModel.autoSaveEdits.collectAsState()
    val saveSidecar by viewModel.saveSidecar.collectAsState()

    val defaultExportFormat by viewModel.defaultExportFormat.collectAsState()
    val defaultJpegQuality by viewModel.defaultJpegQuality.collectAsState()
    val keepMetadata by viewModel.keepMetadata.collectAsState()
    val stripGps by viewModel.stripGps.collectAsState()

    // Capability checks
    val isGpuAvailable = isGles3Available(context)
    val isHdrAvailable = HdrDisplayManager(context).isHdrSupported()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EditorBackground)
            .statusBarsPadding(),
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
                text = "设置",
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
            // ═══════════════════════════════════════════════════════════
            // 性能
            // ═══════════════════════════════════════════════════════════
            SettingsCategoryHeader(title = "性能")

            SettingsCard {
                SwitchRow(
                    title = "GPU加速",
                    subtitle = if (isGpuAvailable) "OpenGL ES 3.0 渲染管线" else "当前设备不支持",
                    checked = gpuAcceleration,
                    onCheckedChange = viewModel::setGpuAcceleration,
                    enabled = isGpuAvailable,
                )

                HorizontalDivider(color = EditorBorder, thickness = 0.5.dp)

                ChoiceRow(
                    title = "预览质量",
                    options = listOf("低", "中", "高"),
                    selectedOption = previewQuality,
                    onOptionSelected = viewModel::setPreviewQuality,
                )

                HorizontalDivider(color = EditorBorder, thickness = 0.5.dp)

                ChoiceRow(
                    title = "线程数",
                    options = listOf("自动", "2", "4", "8"),
                    selectedOption = threadCount,
                    onOptionSelected = viewModel::setThreadCount,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ═══════════════════════════════════════════════════════════
            // 显示
            // ═══════════════════════════════════════════════════════════
            SettingsCategoryHeader(title = "显示")

            SettingsCard {
                SwitchRow(
                    title = "HDR显示",
                    subtitle = if (isHdrAvailable) "高动态范围预览" else "当前设备不支持HDR",
                    checked = hdrDisplay,
                    onCheckedChange = viewModel::setHdrDisplay,
                    enabled = isHdrAvailable,
                )

                HorizontalDivider(color = EditorBorder, thickness = 0.5.dp)

                ChoiceRow(
                    title = "直方图类型",
                    options = listOf("RGB", "Luma", "亮度"),
                    selectedOption = histogramType,
                    onOptionSelected = viewModel::setHistogramType,
                )

                HorizontalDivider(color = EditorBorder, thickness = 0.5.dp)

                SwitchRow(
                    title = "裁剪警告",
                    subtitle = "高光/阴影溢出提示",
                    checked = clippingWarning,
                    onCheckedChange = viewModel::setClippingWarning,
                )

                HorizontalDivider(color = EditorBorder, thickness = 0.5.dp)

                SwitchRow(
                    title = "触觉反馈",
                    subtitle = "滑块与操作振动反馈",
                    checked = hapticFeedback,
                    onCheckedChange = viewModel::setHapticFeedback,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ═══════════════════════════════════════════════════════════
            // 编辑
            // ═══════════════════════════════════════════════════════════
            SettingsCategoryHeader(title = "编辑")

            SettingsCard {
                ChoiceRow(
                    title = "默认胶片模拟",
                    options = listOf("无") + FilmSimulation.ALL.map { it.displayName },
                    selectedOption = defaultFilmSimulation,
                    onOptionSelected = viewModel::setDefaultFilmSimulation,
                )

                HorizontalDivider(color = EditorBorder, thickness = 0.5.dp)

                SwitchRow(
                    title = "自动保存编辑",
                    subtitle = "退出编辑器时自动保存",
                    checked = autoSaveEdits,
                    onCheckedChange = viewModel::setAutoSaveEdits,
                )

                HorizontalDivider(color = EditorBorder, thickness = 0.5.dp)

                SwitchRow(
                    title = "保存sidecar文件",
                    subtitle = "XMP 侧边栏文件同步保存",
                    checked = saveSidecar,
                    onCheckedChange = viewModel::setSaveSidecar,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ═══════════════════════════════════════════════════════════
            // 导出
            // ═══════════════════════════════════════════════════════════
            SettingsCategoryHeader(title = "导出")

            SettingsCard {
                ChoiceRow(
                    title = "默认导出格式",
                    options = listOf("JPEG", "PNG", "TIFF"),
                    selectedOption = defaultExportFormat,
                    onOptionSelected = viewModel::setDefaultExportFormat,
                )

                HorizontalDivider(color = EditorBorder, thickness = 0.5.dp)

                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(
                        text = "默认JPEG质量",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    HasselSlider(
                        label = "质量",
                        value = defaultJpegQuality,
                        range = 1f..100f,
                        onValueChange = viewModel::setDefaultJpegQuality,
                        defaultValue = 95f,
                        stepSize = 1f,
                        format = { v -> v.toInt().toString() },
                    )
                }

                HorizontalDivider(color = EditorBorder, thickness = 0.5.dp)

                SwitchRow(
                    title = "保留元数据",
                    subtitle = "导出时保留EXIF信息",
                    checked = keepMetadata,
                    onCheckedChange = viewModel::setKeepMetadata,
                )

                HorizontalDivider(color = EditorBorder, thickness = 0.5.dp)

                SwitchRow(
                    title = "移除GPS",
                    subtitle = "导出时移除GPS定位信息",
                    checked = stripGps,
                    onCheckedChange = viewModel::setStripGps,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ═══════════════════════════════════════════════════════════
            // 关于
            // ═══════════════════════════════════════════════════════════
            SettingsCategoryHeader(title = "关于")

            SettingsCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "版本号",
                        color = TextPrimary,
                        fontSize = 14.sp,
                    )
                    Text(
                        text = getAppVersion(context),
                        color = TextTertiary,
                        fontSize = 14.sp,
                    )
                }

                HorizontalDivider(color = EditorBorder, thickness = 0.5.dp)

                ClickableRow(
                    title = "隐私政策",
                    onClick = onNavigateToPrivacy,
                )

                HorizontalDivider(color = EditorBorder, thickness = 0.5.dp)

                ClickableRow(
                    title = "用户协议",
                    onClick = onNavigateToUserAgreement,
                )

                HorizontalDivider(color = EditorBorder, thickness = 0.5.dp)

                ClickableRow(
                    title = "开源许可",
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://rapidraw.app/oss-licenses"))
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            Toast.makeText(context, "无法打开浏览器", Toast.LENGTH_SHORT).show()
                        }
                    },
                )

                HorizontalDivider(color = EditorBorder, thickness = 0.5.dp)

                ClickableRow(
                    title = "反馈与建议",
                    onClick = onNavigateToFeedback,
                )
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

// ── Reusable Composable Components ───────────────────────────────────────

@Composable
private fun SettingsCategoryHeader(title: String) {
    Text(
        text = title,
        color = HasselbladOrange,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsCard(
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(EditorSurface, RoundedCornerShape(12.dp))
            .padding(vertical = 4.dp),
    ) {
        content()
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (enabled) TextPrimary else TextTertiary,
                fontSize = 14.sp,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = TextTertiary,
                    fontSize = 12.sp,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = HasselbladOrange,
                checkedThumbColor = TextPrimary,
                uncheckedTrackColor = EditorBorder,
                uncheckedThumbColor = TextSecondary,
                disabledCheckedTrackColor = HasselbladOrange.copy(alpha = 0.38f),
                disabledCheckedThumbColor = TextPrimary.copy(alpha = 0.38f),
            ),
        )
    }
}

@Composable
private fun ChoiceRow(
    title: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 14.sp,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                val isSelected = option == selectedOption
                Box(
                    modifier = Modifier
                        .background(
                            color = if (isSelected) HasselbladOrange.copy(alpha = 0.15f) else EditorBorder.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(8.dp),
                        )
                        .clickable { onOptionSelected(option) }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = option,
                        color = if (isSelected) HasselbladOrange else TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun ClickableRow(
    title: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 14.sp,
        )
        Icon(
            painter = painterResource(android.R.drawable.ic_media_play),
            contentDescription = null,
            tint = TextTertiary,
        )
    }
}

/**
 * Check whether the device supports OpenGL ES 3.0 (required by GpuPipeline).
 * Uses EGL config query to determine GLES 3.0 capability.
 *
 * v1.5.10 hotfix: 旧代码直接调用 eglInitialize 可能与其他 EGL 上下文冲突，
 * 部分 OEM ROM 在已初始化 EGL 后再次调用 eglInitialize 会返回错误。
 * 现在使用 try-catch 包裹整个流程，任何失败都回退到 false。
 * 同时增加 EGL_BAD_DISPLAY / EGL_NOT_INITIALIZED 等错误检查。
 */
private fun isGles3Available(context: Context): Boolean {
    return try {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display === EGL14.EGL_NO_DISPLAY) return false
        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
            // 可能已初始化，尝试获取版本号
            EGL14.eglQueryContext(display, display, EGL14.EGL_CONTEXT_CLIENT_VERSION, version, 0)
            if (EGL14.eglGetError() != EGL14.EGL_SUCCESS) {
                return false
            }
        }

        val attribList = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGLExt.EGL_OPENGL_ES3_BIT_KHR,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
        val numConfigs = IntArray(1)
        val result = EGL14.eglChooseConfig(display, attribList, 0, configs, 0, 1, numConfigs, 0)

        // 不终止 display，避免影响其他 EGL 客户端
        result && numConfigs[0] > 0
    } catch (_: Throwable) {
        // v1.5.10 hotfix: 捕获 Throwable，部分设备 EGL 调用可能抛 Error
        false
    }
}

private fun getAppVersion(context: Context): String {
    return runCatching {
        val pi = context.packageManager.getPackageInfo(context.packageName, 0)
        pi.versionName ?: "?"
    }.getOrDefault("?")
}

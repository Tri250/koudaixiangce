package com.rapidraw.ui.presets

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rapidraw.ui.theme.EditorBackground
import com.rapidraw.ui.theme.EditorSurface
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.TextPrimary
import com.rapidraw.ui.theme.TextSecondary
import com.rapidraw.ui.theme.TextTertiary
import com.rapidraw.core.PresetConverter
import com.rapidraw.core.CubeLutParser
import com.rapidraw.data.model.Preset
import com.rapidraw.data.model.Adjustments
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log

@Composable
fun PresetImportScreen(
    onBack: () -> Unit,
    onImportPreset: (Preset) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        selectedUri = uri
        // v1.10.6: 选择新文件时清除旧错误
        importError = null
    }

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
                text = "导入预设",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        // ── Content ──────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // File picker button
            OutlinedButton(
                onClick = {
                    filePickerLauncher.launch(
                        arrayOf(
                            "application/octet-stream",
                            "*/*",
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = if (selectedUri != null) "已选择文件" else "选择预设或LUT文件 (.cube / .xmp / .lrtemplate)",
                    color = if (selectedUri != null) HasselbladOrange else TextSecondary,
                    fontSize = if (selectedUri == null) 13.sp else 14.sp,
                )
            }

            val uri = selectedUri
            if (uri != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = uri.lastPathSegment ?: "未知文件",
                    color = TextTertiary,
                    fontSize = 13.sp,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Import button
            Button(
                onClick = {
                    val uri = selectedUri
                    if (uri != null) {
                        importError = null
                        scope.launch(Dispatchers.IO) {
                            val fileName = uri.lastPathSegment ?: "unknown"
                            val isCubeFile = fileName.endsWith(".cube", ignoreCase = true)

                            if (isCubeFile) {
                                // LUT (.cube) file import
                                val result: CubeLutParser.ParsedLut? = try {
                                    val parser = CubeLutParser()
                                    context.contentResolver.openInputStream(uri)?.use { stream ->
                                        parser.parse(stream, "cube")
                                    }
                                } catch (e: Exception) {
                                    null
                                }
                                withContext(Dispatchers.Main) {
                                    if (result != null) {
                                        val preset = Preset(
                                            id = UUID.randomUUID().toString(),
                                            name = fileName.removeSuffix(".cube").removeSuffix(".CUBE"),
                                            description = "LUT 导入: $fileName",
                                            category = "LUT",
                                            adjustments = result.toAdjustments(),
                                            createdAt = System.currentTimeMillis(),
                                        )
                                        onImportPreset(preset)
                                    } else {
                                        importError = "无法解析该 .cube LUT 文件"
                                    }
                                }
                            } else {
                                // Preset file import (.xmp / .lrtemplate)
                                val result = runCatching {
                                    PresetConverter.importFile(uri, context.contentResolver)
                                }.getOrNull()
                                withContext(Dispatchers.Main) {
                                    if (result != null) {
                                        val preset = Preset(
                                            id = UUID.randomUUID().toString(),
                                            name = result.name,
                                            adjustments = result.adjustments,
                                            createdAt = System.currentTimeMillis(),
                                        )
                                        onImportPreset(preset)
                                    } else {
                                        importError = "无法识别该预设文件（仅支持 .cube / .xmp / .lrtemplate）"
                                    }
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedUri != null,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HasselbladOrange,
                    disabledContainerColor = HasselbladOrange.copy(alpha = 0.38f),
                ),
            ) {
                Text(
                    text = "导入",
                    color = TextPrimary,
                    fontWeight = FontWeight.Medium,
                )
            }

            if (importError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = importError ?: "",
                    color = HasselbladOrange,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

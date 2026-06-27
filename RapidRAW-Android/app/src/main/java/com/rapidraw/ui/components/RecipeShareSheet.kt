package com.rapidraw.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rapidraw.data.model.Adjustments
import com.rapidraw.data.model.Recipe
import com.rapidraw.data.repository.RecipeRepository
import com.rapidraw.ui.theme.EditorSurface
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.TextPrimary
import com.rapidraw.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeShareSheet(
    visible: Boolean,
    adjustments: Adjustments,
    filmId: String?,
    filmIntensity: Float,
    onDismiss: () -> Unit,
    onApplyRecipe: (Recipe) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboardManager: ClipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { RecipeRepository(context) }

    if (!visible) return

    var recipeName by remember { mutableStateOf("我的配方") }
    var importJson by remember { mutableStateOf("") }
    var importCode by remember { mutableStateOf("") }
    var showImport by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }

    val currentRecipe = Recipe(
        id = System.currentTimeMillis().toString(),
        name = recipeName,
        author = "我",
        adjustments = adjustments,
        filmId = filmId,
        filmIntensity = filmIntensity,
    )

    val shareJson = remember(currentRecipe) {
        Recipe.exportToJson(currentRecipe)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = EditorSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = if (showImport) "导入配方" else "分享配方",
                color = TextPrimary,
                fontSize = 18.sp,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            if (!showImport) {
                // Export section
                OutlinedTextField(
                    value = recipeName,
                    onValueChange = { recipeName = it },
                    label = { Text("配方名称", color = TextSecondary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                    ),
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Share code display
                val shareCode = remember(currentRecipe) { currentRecipe.generateShareCode() }
                OutlinedTextField(
                    value = shareCode,
                    onValueChange = {},
                    label = { Text("分享码", color = TextSecondary) },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                    ),
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Full JSON (for advanced sharing)
                OutlinedTextField(
                    value = shareJson,
                    onValueChange = {},
                    label = { Text("配方JSON（长按复制）", color = TextSecondary) },
                    readOnly = true,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                    ),
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(shareJson))
                        // Persist to local Room database so share code is resolvable
                        scope.launch {
                            repository.saveRecipe(currentRecipe)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HasselbladOrange,
                    ),
                ) {
                    Text("复制配方并保存", color = TextPrimary)
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = { showImport = true; importError = null },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("导入配方", color = TextSecondary)
                }
            } else {
                // Import by share code
                OutlinedTextField(
                    value = importCode,
                    onValueChange = { importCode = it; importError = null },
                    label = { Text("输入分享码", color = TextSecondary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                    ),
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        scope.launch {
                            val recipe = repository.findByShareCode(importCode.trim())
                            if (recipe != null) {
                                onApplyRecipe(recipe)
                                onDismiss()
                            } else {
                                importError = "未找到该分享码，请确认后重试"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HasselbladOrange,
                    ),
                ) {
                    Text("通过分享码导入", color = TextPrimary)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "或通过 JSON 导入",
                    color = TextSecondary,
                    fontSize = 12.sp,
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Import by JSON
                OutlinedTextField(
                    value = importJson,
                    onValueChange = { importJson = it; importError = null },
                    label = { Text("粘贴配方JSON", color = TextSecondary) },
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                    ),
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        Recipe.importFromJson(importJson)?.let { recipe ->
                            onApplyRecipe(recipe)
                            onDismiss()
                        } ?: run { importError = "JSON 格式错误，无法解析" }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HasselbladOrange,
                    ),
                ) {
                    Text("通过 JSON 导入", color = TextPrimary)
                }

                importError?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = it, color = androidx.compose.ui.graphics.Color(0xFFFF4444), fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = { showImport = false; importError = null },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("返回分享", color = TextSecondary)
                }
            }
        }
    }
}

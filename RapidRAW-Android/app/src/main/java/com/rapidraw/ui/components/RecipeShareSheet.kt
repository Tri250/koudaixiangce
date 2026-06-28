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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rapidraw.data.model.Adjustments
import com.rapidraw.data.model.Recipe
import com.rapidraw.ui.theme.EditorSurface
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.TextPrimary
import com.rapidraw.ui.theme.TextSecondary

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
    var recipeName by remember { mutableStateOf("我的配方") }
    var importJson by remember { mutableStateOf("") }
    var showImport by remember { mutableStateOf(false) }

    if (!visible) return
    
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
                OutlinedTextField(
                    value = currentRecipe.generateShareCode(),
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
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HasselbladOrange,
                    ),
                ) {
                    Text("复制配方到剪贴板", color = TextPrimary)
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                TextButton(
                    onClick = { showImport = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("导入配方", color = TextSecondary)
                }
            } else {
                // Import section
                OutlinedTextField(
                    value = importJson,
                    onValueChange = { importJson = it },
                    label = { Text("粘贴配方JSON", color = TextSecondary) },
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                    ),
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = {
                        // 优先尝试分享码解析（Base64），失败则尝试 JSON
                        val recipe = Recipe.fromShareCode(importJson.trim())
                            ?: Recipe.importFromJson(importJson.trim())
                        recipe?.let {
                            onApplyRecipe(it)
                            onDismiss()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HasselbladOrange,
                    ),
                ) {
                    Text("导入并应用", color = TextPrimary)
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                TextButton(
                    onClick = { showImport = false },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("返回分享", color = TextSecondary)
                }
            }
        }
    }
}

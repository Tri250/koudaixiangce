package com.rapidraw.ui.components

import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rapidraw.ui.theme.EditorBackground
import com.rapidraw.ui.theme.HasselbladOrange

/**
 * Android 16 Photo Picker 按钮。
 *
 * 使用 [ActivityResultContracts.PickVisualMedia] 选择单张图片，
 * 使用 [ActivityResultContracts.PickMultipleVisualMedia] 批量导入。
 * 在 Android 13 以下版本回退到 [ActivityResultContracts.OpenDocument]。
 */
@Composable
fun PhotoPickerButton(
    onImagesPicked: (List<Uri>) -> Unit,
    modifier: Modifier = Modifier,
    batchMode: Boolean = false,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        PhotoPickerModern(
            onImagesPicked = onImagesPicked,
            modifier = modifier,
            batchMode = batchMode,
        )
    } else {
        PhotoPickerLegacy(
            onImagesPicked = onImagesPicked,
            modifier = modifier,
            batchMode = batchMode,
        )
    }
}

@Composable
private fun PhotoPickerModern(
    onImagesPicked: (List<Uri>) -> Unit,
    modifier: Modifier,
    batchMode: Boolean,
) {
    val singlePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) onImagesPicked(listOf(uri))
    }

    val multiplePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris ->
        if (uris.isNotEmpty()) onImagesPicked(uris)
    }

    Button(
        onClick = {
            if (batchMode) {
                multiplePicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            } else {
                singlePicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
        },
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = HasselbladOrange,
            contentColor = EditorBackground,
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Icon(
            imageVector = Icons.Default.AddPhotoAlternate,
            contentDescription = "导入照片",
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = "Import Photos",
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

@Composable
private fun PhotoPickerLegacy(
    onImagesPicked: (List<Uri>) -> Unit,
    modifier: Modifier,
    batchMode: Boolean,
) {
    val legacyPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) onImagesPicked(uris)
    }

    Button(
        onClick = {
            legacyPicker.launch(arrayOf("image/*"))
        },
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = HasselbladOrange,
            contentColor = EditorBackground,
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Icon(
            imageVector = Icons.Default.AddPhotoAlternate,
            contentDescription = "导入照片",
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = "Import Photos",
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

package com.rawlab.android

import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchExportScreen(
    model: BatchExportModel,
    storage: PhotoStorage,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val state by model.state.collectAsStateWithLifecycle()
    var album by rememberSaveable { mutableStateOf(false) }
    var discard by rememberSaveable { mutableStateOf(false) }
    var effectTarget by remember { mutableStateOf<BatchRow?>(null) }
    var format by rememberSaveable(state.job.id) { mutableStateOf(state.job.outputFormat) }
    var exportSizeKey by rememberSaveable(state.job.id) {
        mutableStateOf(exportSizeChoice(state.job.outputLongEdge).key)
    }
    var exportCustomSize by rememberSaveable(state.job.id) {
        mutableStateOf(if (state.job.outputLongEdge != null && exportSizeChoice(state.job.outputLongEdge) == ExportSizeChoice.CUSTOM)
            state.job.outputLongEdge.toString() else "")
    }
    var destination by rememberSaveable(state.job.id) { mutableStateOf<Uri?>(state.job.destination?.let(Uri::parse)) }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { model.addUris(it) }
    val directory = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            destination = uri
            model.setDestination(uri)
        }
    }
    BackHandler { if (state.phase !in setOf(BatchPhase.RUNNING, BatchPhase.PREPARING)) onBack() }

    if (album) {
        var selected by rememberSaveable { mutableStateOf(state.job.rows.map { it.id }) }
        AlbumScreen(storage, onBack = { album = false }, onFile = { album = false; files.launch(arrayOf("*/*")) },
            onPhoto = { uri -> selected = if (uri.toString() in selected) selected - uri.toString() else selected + uri.toString() },
            selectedPhotos = selected.toSet(), onDone = {
                state.job.rows.filter { it.id !in selected }.forEach { model.remove(it.id) }
                model.addUris(selected.filter { id -> state.job.rows.none { it.id == id } }.map(Uri::parse))
                album = false
            })
        return
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.batch_export)) }, navigationIcon = {
            IconButton(onClick = onBack, enabled = state.phase != BatchPhase.RUNNING && state.phase != BatchPhase.PREPARING) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back))
            }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when (state.phase) {
                BatchPhase.SELECTING -> BatchConfirmContent(state, storage, format, destination,
                    ExportSizeChoice.fromKey(exportSizeKey), exportCustomSize,
                    onAlbum = { album = true }, onFiles = { files.launch(arrayOf("*/*")) },
                    onDirectory = { directory.launch(null) }, onAlbumOutput = { destination = null; model.setDestination(null) }, onFormat = { format = it },
                    onSize = { choice -> exportSizeKey = choice.key }, onCustomSize = { exportCustomSize = it },
                    onRemove = model::remove, onInspect = { effectTarget = it },
                    onStart = { model.start(format, destination, ExportSize.parse(ExportSizeChoice.fromKey(exportSizeKey), exportCustomSize)) })
                BatchPhase.PREPARING, BatchPhase.RUNNING -> BatchProgressContent(state, model::cancel)
                BatchPhase.COMPLETE, BatchPhase.CANCELLED, BatchPhase.FAILED -> BatchResultContent(state,
                    onRetry = model::retryFailed, onClose = onBack, onDirectory = { directory.launch(state.destination) },
                    onConfirm = model::confirmOutput, onDiscard = { discard = true })
            }
        }
    }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text(stringResource(R.string.batch_discard_title)) },
        text = { Text(stringResource(R.string.batch_discard_body)) },
        confirmButton = { TextButton(onClick = { if (model.discard()) onBack(); discard = false }) { Text(stringResource(R.string.batch_discard_confirm)) } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text(stringResource(R.string.cancel)) } })
    effectTarget?.let { row ->
        var preview by remember(row.id) { mutableStateOf<android.graphics.Bitmap?>(null) }
        var error by remember(row.id) { mutableStateOf<String?>(null) }
        LaunchedEffect(row.id) {
            try { preview = model.preview(row.target) }
            catch (failure: Exception) { error = failure.message }
        }
        AlertDialog(onDismissRequest = { effectTarget = null }, title = { Text(row.target.displayName) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                        preview?.let { Image(it.asImageBitmap(), row.target.displayName, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
                        if (preview == null && error == null) CircularProgressIndicator()
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                }
            }, confirmButton = { TextButton(onClick = { effectTarget = null }) { Text(stringResource(R.string.confirm)) } })
    }
}

@Composable
private fun BatchConfirmContent(
    state: BatchExportState,
    storage: PhotoStorage,
    format: ExportFormat,
    destination: Uri?,
    sizeChoice: ExportSizeChoice,
    customSize: String,
    onAlbum: () -> Unit,
    onFiles: () -> Unit,
    onDirectory: () -> Unit,
    onAlbumOutput: () -> Unit,
    onFormat: (ExportFormat) -> Unit,
    onSize: (ExportSizeChoice) -> Unit,
    onCustomSize: (String) -> Unit,
    onRemove: (String) -> Unit,
    onInspect: (BatchRow) -> Unit,
    onStart: () -> Unit,
) {
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val recipes = remember(context) { MasterRecipe.load(context) }
    val sourceSettings = state.job.source.settings
    val sourceLookName = recipes.firstOrNull { it.id == sourceSettings.recipeID }?.name
        ?: if (sourceSettings.film == "neutral") stringResource(R.string.neutral_look)
        else Film.all.firstOrNull { it.id == sourceSettings.film }?.name ?: sourceSettings.film
    Column(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(bottom = 12.dp)) {
        item {
            Text(stringResource(R.string.batch_source_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(20.dp, 16.dp, 20.dp, 8.dp))
            Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(80.dp)) { BatchThumbnail(storage, state.job.source.identity.value, state.job.source.sourceFile) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(state.job.source.displayName, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val settings = state.job.source.settings
                    Text("$sourceLookName · ${String.format(java.util.Locale.ROOT, "%.0f", settings.strength * 100f)}%",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.batch_exposure_format, settings.exposure), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onAlbum, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.PhotoLibrary, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.batch_album_multi)) }
                OutlinedButton(onClick = onFiles, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.FolderOpen, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.batch_file_multi)) }
            }
        }
        item {
            Text(stringResource(R.string.batch_target_title, state.selectedCount), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(20.dp, 20.dp, 20.dp, 8.dp))
        }
        if (state.job.rows.isEmpty()) item {
            Text(stringResource(R.string.batch_target_empty), color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        }
        items(state.job.rows, key = { it.id }) { row ->
            Row(Modifier.fillMaxWidth().clickable { onInspect(row) }.padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp)) { BatchThumbnail(storage, row.target.sourceUri, row.target.sourceFile) }
                Spacer(Modifier.width(12.dp))
                Text(row.target.displayName, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = { onInspect(row) }) { Text(stringResource(R.string.batch_view)) }
                IconButton(onClick = { onRemove(row.id) }) { Icon(Icons.Outlined.Close, stringResource(R.string.batch_remove)) }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(stringResource(R.string.batch_all_settings), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { showSettings = !showSettings }) { Text(if (showSettings) stringResource(R.string.batch_collapse) else stringResource(R.string.batch_expand)) }
            }
            if (showSettings) SettingsSummary(state.job.source.settings, sourceLookName)
        }
        item {
            Text(stringResource(R.string.batch_output_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(20.dp, 16.dp, 20.dp, 4.dp))
            Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = format == ExportFormat.JPEG, onClick = { onFormat(ExportFormat.JPEG) }, label = { Text(stringResource(R.string.jpeg)) })
                FilterChip(selected = format == ExportFormat.PNG, onClick = { onFormat(ExportFormat.PNG) }, label = { Text(stringResource(R.string.png)) })
                FilterChip(selected = format == ExportFormat.DNG, onClick = { onFormat(ExportFormat.DNG) }, label = { Text(stringResource(R.string.dng)) })
            }
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (Build.VERSION.SDK_INT >= 29) {
                    FilterChip(selected = destination == null, onClick = onAlbumOutput, label = { Text(stringResource(R.string.batch_photo_gallery)) })
                }
                OutlinedButton(onClick = onDirectory) { Icon(Icons.Outlined.FolderOpen, null); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.batch_choose_directory)) }
            }
            ExportSizeControls(
                selected = sizeChoice,
                customText = customSize,
                onSelected = onSize,
                onCustomText = onCustomSize,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            Text(destination?.lastPathSegment ?: stringResource(R.string.batch_photo_gallery), Modifier.padding(horizontal = 20.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            state.error?.let { Text(it, Modifier.padding(20.dp), color = MaterialTheme.colorScheme.error) }
        }
    }
    Text(stringResource(R.string.batch_only_this_time), Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodySmall)
    val longEdge = ExportSize.parse(sizeChoice, customSize)
    val sizeValid = sizeChoice != ExportSizeChoice.CUSTOM || longEdge != null
    Button(onClick = onStart, enabled = sizeValid && state.job.rows.isNotEmpty() && (Build.VERSION.SDK_INT >= 29 || destination != null), modifier = Modifier.fillMaxWidth().padding(20.dp)) {
        Text(stringResource(R.string.batch_export_count, state.job.rows.size))
    }
    }
}

private fun exportSizeChoice(longEdge: Int?): ExportSizeChoice = when (longEdge) {
    null -> ExportSizeChoice.ORIGINAL
    2048 -> ExportSizeChoice.EDGE_2048
    3000 -> ExportSizeChoice.EDGE_3000
    4096 -> ExportSizeChoice.EDGE_4096
    else -> ExportSizeChoice.CUSTOM
}

@Composable
private fun BatchProgressContent(state: BatchExportState, onCancel: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(when {
            state.stopping -> stringResource(R.string.batch_stopping)
            state.phase == BatchPhase.PREPARING -> stringResource(R.string.batch_preparing)
            else -> stringResource(R.string.batch_exporting)
        }, style = MaterialTheme.typography.titleLarge)
        LinearProgressIndicator(progress = { (state.successCount + state.failedCount).toFloat() / state.job.rows.size.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth())
        Text(stringResource(R.string.batch_progress_count, state.successCount, state.job.rows.size))
        LazyColumn(Modifier.weight(1f)) { items(state.job.rows, key = { it.id }) { row -> BatchRowLine(row) } }
        OutlinedButton(onClick = onCancel, enabled = !state.stopping, modifier = Modifier.fillMaxWidth()) { Text(if (state.stopping) stringResource(R.string.batch_stopping) else stringResource(R.string.cancel)) }
    }
}

@Composable
private fun BatchResultContent(state: BatchExportState, onRetry: () -> Unit, onClose: () -> Unit,
    onDirectory: () -> Unit, onConfirm: (String, Boolean) -> Unit, onDiscard: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(when {
            state.phase == BatchPhase.COMPLETE -> stringResource(R.string.batch_result_complete)
            state.failedCount > 0 -> stringResource(R.string.batch_result_partial)
            else -> stringResource(R.string.batch_result_title)
        }, style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.batch_result_summary, state.successCount, state.failedCount, state.unfinishedCount - state.failedCount))
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        LazyColumn(Modifier.weight(1f)) { items(state.job.rows, key = { it.id }) { row ->
            BatchRowLine(row)
            if (row.status == BatchRowStatus.NEEDS_CONFIRMATION) {
                Text(stringResource(R.string.batch_uncertain_result), style = MaterialTheme.typography.bodySmall)
                Row {
                    TextButton(onClick = { onConfirm(row.id, true) }) { Text(stringResource(R.string.batch_found_output)) }
                    TextButton(onClick = { onConfirm(row.id, false) }) { Text(stringResource(R.string.batch_not_saved_retry)) }
                }
            }
        } }
        if (state.unfinishedCount > 0) OutlinedButton(onClick = onDirectory, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.FolderOpen, null); Text(stringResource(R.string.batch_rechoose_directory)) }
        if (state.unfinishedCount > 0) Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Refresh, null); Spacer(Modifier.width(6.dp)); Text(if (state.failedCount == state.unfinishedCount) stringResource(R.string.batch_retry_failed) else stringResource(R.string.batch_continue_unfinished)) }
        OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.batch_close)) }
        if (state.phase != BatchPhase.COMPLETE) TextButton(onClick = onDiscard, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.batch_discard_confirm)) }
    }
}

@Composable
private fun BatchRowLine(row: BatchRow) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        val icon = when (row.status) {
            BatchRowStatus.SUCCESS -> Icons.Outlined.CheckCircle
            BatchRowStatus.FAILED -> Icons.Outlined.Close
            else -> Icons.Outlined.Refresh
        }
        Icon(icon, null, tint = if (row.status == BatchRowStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(row.target.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(when (row.status) {
                BatchRowStatus.PENDING -> stringResource(R.string.batch_status_pending)
                BatchRowStatus.PROCESSING -> stringResource(R.string.batch_status_processing)
                BatchRowStatus.PUBLISHING -> stringResource(R.string.batch_status_publishing)
                BatchRowStatus.NEEDS_CONFIRMATION -> stringResource(R.string.batch_status_needs_confirmation)
                BatchRowStatus.SUCCESS -> stringResource(R.string.batch_status_success)
                BatchRowStatus.FAILED -> stringResource(R.string.batch_status_failed)
                BatchRowStatus.CANCELLED -> stringResource(R.string.batch_status_cancelled)
            }, style = MaterialTheme.typography.labelSmall)
            row.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun SettingsSummary(settings: EditSettings, lookName: String) {
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val exposureModes = listOf(R.string.exposure_scene, R.string.exposure_preview, R.string.exposure_sensor)
        SettingLine(stringResource(R.string.batch_settings_film), Film.all.firstOrNull { it.id == settings.film }?.name ?: settings.film)
        SettingLine(stringResource(R.string.batch_settings_strength), String.format("%.0f%%", settings.strength * 100))
        SettingLine(stringResource(R.string.batch_settings_exposure), String.format("%+.2f EV", settings.exposure))
        SettingLine(stringResource(R.string.batch_settings_exposure_mode), stringResource(exposureModes[settings.exposureMode]))
        SettingLine(stringResource(R.string.batch_settings_hsl), if (settings.hsl.isIdentity) stringResource(R.string.batch_settings_hsl_identity) else stringResource(R.string.batch_settings_hsl_adjusted))
        SettingLine(stringResource(R.string.batch_settings_highlights), String.format("%+.0f%%", settings.highlights * 100))
        SettingLine(stringResource(R.string.batch_settings_shadows), String.format("%+.0f%%", settings.shadows * 100))
        SettingLine(stringResource(R.string.batch_settings_contrast), String.format("%+.0f%%", settings.contrast * 100))
        SettingLine(stringResource(R.string.batch_settings_tone_curve), String.format("%+.0f%%", settings.toneCurve * 100))
        SettingLine(stringResource(R.string.batch_settings_saturation), String.format("%+.0f%%", settings.saturation * 100))
        SettingLine(stringResource(R.string.batch_settings_white_balance), if (settings.customWb) stringResource(R.string.batch_settings_wb_custom) else stringResource(R.string.batch_settings_wb_as_shot))
        if (settings.customWb) {
            SettingLine(stringResource(R.string.batch_settings_temperature), String.format("%.0f K", settings.temperature))
            SettingLine(stringResource(R.string.batch_settings_tint), String.format("%+.0f", settings.tint))
        }
        SettingLine(stringResource(R.string.batch_settings_sharpening), String.format("%.0f%%", settings.sharpening * 100))
        SettingLine(stringResource(R.string.batch_settings_denoise), if (settings.denoise.enabled) stringResource(R.string.batch_settings_on) else stringResource(R.string.batch_settings_off))
        SettingLine(stringResource(R.string.batch_settings_denoise_luma), String.format("%.0f", settings.denoise.luma))
        SettingLine(stringResource(R.string.batch_settings_denoise_chroma), String.format("%.0f", settings.denoise.chroma))
        SettingLine(stringResource(R.string.batch_settings_denoise_coarse), String.format("%.0f", settings.denoise.coarse))
        SettingLine(stringResource(R.string.batch_settings_display_chroma), settings.displayChromaDenoise.toString())
        SettingLine(stringResource(R.string.batch_settings_vignette), String.format(stringResource(R.string.batch_vignette_format),
            settings.effects.vignetteAmount, settings.effects.vignetteMidpoint, settings.effects.vignetteRoundness,
            settings.effects.vignetteFeather, settings.effects.vignetteHighlights))
        SettingLine(stringResource(R.string.batch_settings_grain), String.format(stringResource(R.string.batch_grain_format),
            settings.effects.grainAmount, settings.effects.grainSize, settings.effects.grainRoughness))
    }
}

@Composable
private fun SettingLine(title: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) { Text(title, Modifier.weight(1f)); Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun BatchThumbnail(storage: PhotoStorage, uri: String?, file: java.io.File) {
    val thumbnail by produceState<android.graphics.Bitmap?>(null, uri) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            uri?.let { storage.thumbnail(Uri.parse(it)) } ?: runCatching {
                val exif = androidx.exifinterface.media.ExifInterface(file)
                val original = exif.thumbnailBitmap ?: return@runCatching null
                val matrix = android.graphics.Matrix().apply {
                    if (exif.isFlipped) postScale(-1f, 1f)
                    postRotate(exif.rotationDegrees.toFloat())
                }
                android.graphics.Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
            }.getOrNull()
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        thumbnail?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
            ?: Icon(Icons.Outlined.PhotoLibrary, null)
    }
}

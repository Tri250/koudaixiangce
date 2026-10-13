package com.rawlab.android

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun LensControls(value: LensSettings, info: LensInfo?, enabled: Boolean, change: (LensSettings) -> Unit) {
    var choosing by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                listOf("自动", "手动").forEachIndexed { index, title ->
                    SegmentedButton(selected = value.manual == (index == 1),
                        onClick = { change(value.copy(manual = index == 1)) }, enabled = enabled,
                        shape = SegmentedButtonDefaults.itemShape(index, 2)) { Text(title) }
                }
            }
            IconButton(onClick = { change(LensSettings()) }, enabled = enabled && value != LensSettings(),
                modifier = Modifier.testTag("lens.reset")) { Icon(Icons.Outlined.RestartAlt, "重置镜头校正") }
        }
        LensToggle("畸变矫正", value.distortion, enabled, "lens.distortion") { change(value.copy(distortion = it)) }
        LensToggle("横向色差", value.tca, enabled, "lens.tca") { change(value.copy(tca = it)) }
        LensToggle("自动裁边", value.autoCrop, enabled, "lens.crop") { change(value.copy(autoCrop = it)) }
        val selected = if (value.manual) info?.candidates?.firstOrNull { it.id == value.profileId }?.title
            else info?.selectedLens?.takeIf { it.isNotEmpty() }
        TextButton(onClick = { choosing = true }, modifier = Modifier.fillMaxWidth().testTag("lens.profile"),
            enabled = enabled && value.manual && info?.candidates?.isNotEmpty() == true) {
            Text(selected ?: if (value.manual) "选择镜头" else "未匹配镜头")
        }
        Text(info?.message ?: "镜头信息尚未就绪", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("lens.status"))
        info?.detectedLens?.takeIf { it.isNotEmpty() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (info != null && info.cropScale > 1.0001f) {
            Text("裁边 %.1f%%".format((1 - 1 / info.cropScale) * 100), style = MaterialTheme.typography.bodySmall)
        }
    }
    if (choosing) LensChooser(info?.candidates.orEmpty(), value.profileId, { choosing = false }) {
        change(value.copy(manual = true, profileId = it.id)); choosing = false
    }
}

@Composable
private fun LensToggle(title: String, checked: Boolean, enabled: Boolean, tag: String, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = change, enabled = enabled, modifier = Modifier.testTag(tag))
    }
}

@Composable
private fun LensChooser(candidates: List<LensCandidate>, selected: String?, dismiss: () -> Unit, select: (LensCandidate) -> Unit) {
    var search by remember { mutableStateOf("") }
    val filtered = remember(candidates, search) {
        candidates.filter { it.title.contains(search, ignoreCase = true) }
            .sortedWith(compareByDescending<LensCandidate> { it.isMatch }.thenBy { it.title })
    }
    AlertDialog(onDismissRequest = dismiss, title = { Text("镜头配置") }, confirmButton = {
        TextButton(onClick = dismiss) { Text("取消") }
    }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(search, { search = it }, singleLine = true, label = { Text("搜索镜头") },
                modifier = Modifier.fillMaxWidth().testTag("lens.search"))
            if (filtered.isEmpty()) Text("没有匹配的镜头")
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                items(filtered, key = { it.id }) { candidate ->
                    ListItem(modifier = Modifier.clickable { select(candidate) }.testTag("lens.candidate"),
                        headlineContent = { Text(candidate.title) }, supportingContent = {
                            Text(listOfNotNull("畸变".takeIf { candidate.hasDistortion }, "横向色差".takeIf { candidate.hasTca },
                                "元数据匹配".takeIf { candidate.isMatch }).joinToString(" / "))
                        }, trailingContent = { RadioButton(selected == candidate.id, onClick = { select(candidate) }) })
                }
            }
        }
    })
}

/*
 * Copyright 2025 LanXin Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lanxin.android.builtin.systemtools.presentation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lanxin.android.builtin.systemtools.domain.NotesExportFormat
import com.lanxin.android.builtin.systemtools.domain.NotesImportStrategy
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemToolsScreen(
    onBackAction: () -> Unit,
    viewModel: SystemToolsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val config = state.config
    val perms = state.permissions
    val lifecycleOwner = LocalLifecycleOwner.current

    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { viewModel.exportNotesToUri(it.toString(), NotesExportFormat.JSON) }
    }
    val exportMdLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri ->
        uri?.let { viewModel.exportNotesToUri(it.toString(), NotesExportFormat.MARKDOWN) }
    }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            viewModel.importNotesFromUri(it.toString(), NotesImportStrategy.MERGE)
        }
    }
    val userFilePickLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importUserFileFromUri(it.toString()) }
    }
    val userFileCreateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        uri?.let {
            val stamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
            viewModel.exportUserTextToUri(
                it.toString(),
                text = "兰心用户文件导出 $stamp
",
                mime = "text/plain"
            )
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("系统能力") },
                navigationIcon = {
                    IconButton(onClick = onBackAction) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.isBusy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Phase 7.4 · 日历 + 闹钟 + 笔记 + 用户文件",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "日历：Instances 读 + INSERT Intent 写。" +
                            "闹钟：setAlarmClock / Intent。" +
                            "笔记：Room 持久化 CRUD + SAF。" +
                            "用户文件：SAF 选取/导入 + imports 列表/分享。" +
                            "默认全关；写/删需 confirmed。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "隐私：笔记/imports 仅应用私有；SAF 由用户显式选文件；" +
                            "禁止系统分区与全盘扫描；不在服务器下模型。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "运行时权限",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "日历读取：${perms.calendarLabel}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = perms.calendarHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!perms.calendarReadGranted) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = viewModel::openCalendarPermissionSettings) {
                            Text("打开应用权限设置（日历）")
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "精确闹钟：${perms.exactAlarmLabel}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = perms.exactAlarmHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!perms.canScheduleExactAlarms) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(onClick = viewModel::openExactAlarmSettings) {
                            Text("打开「允许精确闹钟」")
                        }
                    }
                }
            }

            SwitchRow(
                title = "总开关",
                description = "关闭时所有系统能力工具拒绝执行",
                checked = config.masterEnabled,
                onCheckedChange = viewModel::setMaster
            )
            SwitchRow(
                title = "日历",
                description = "list（Instances）/ create（INSERT Intent，需确认）",
                checked = config.calendarEnabled,
                onCheckedChange = viewModel::setCalendar,
                enabled = config.masterEnabled
            )
            SwitchRow(
                title = "闹钟",
                description = "setAlarmClock 默认；mode=intent 真启动系统时钟 App",
                checked = config.alarmEnabled,
                onCheckedChange = viewModel::setAlarm,
                enabled = config.masterEnabled
            )
            SwitchRow(
                title = "笔记",
                description = "Room 私有库 CRUD + SAF 导出/导入；无厂商笔记深度集成",
                checked = config.notesEnabled,
                onCheckedChange = viewModel::setNotes,
                enabled = config.masterEnabled
            )
            SwitchRow(
                title = "用户文件",
                description = "SAF 选取导入 + 应用 imports 列表/读/写/分享（非系统分区）",
                checked = config.userFileEnabled,
                onCheckedChange = viewModel::setUserFile,
                enabled = config.masterEnabled
            )
            SwitchRow(
                title = "写操作需确认",
                description = "默认开；关则写工具可不带 confirmed（删仍需 EXPLICIT）",
                checked = config.requireConfirmOnWrite,
                onCheckedChange = viewModel::setRequireConfirm
            )

            // 笔记能力状态 + SAF
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "笔记能力状态",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = state.notesStatusLabel,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "工具：note_create / list / append / update / delete / export / import",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = {
                                val stamp = LocalDateTime.now()
                                    .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                                exportJsonLauncher.launch("lanxin_notes_$stamp.json")
                            },
                            enabled = state.notesEnabled || state.notesCount > 0,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("导出 JSON")
                        }
                        OutlinedButton(
                            onClick = {
                                val stamp = LocalDateTime.now()
                                    .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                                exportMdLauncher.launch("lanxin_notes_$stamp.md")
                            },
                            enabled = state.notesEnabled || state.notesCount > 0,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("导出 MD")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                importLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                            },
                            enabled = config.masterEnabled && config.notesEnabled,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("导入 JSON")
                        }
                        OutlinedButton(
                            onClick = { viewModel.shareNotes(NotesExportFormat.JSON) },
                            enabled = state.notesCount > 0,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("分享")
                        }
                    }
                }
            }

            // 用户文件能力状态 + SAF
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "用户文件能力状态",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = state.userFileStatusLabel,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "工具：file_pick / list / read_text / write / share / delete",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                userFilePickLauncher.launch(arrayOf("*/*"))
                            },
                            enabled = config.masterEnabled && config.userFileEnabled,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("导入文件")
                        }
                        OutlinedButton(
                            onClick = {
                                val stamp = LocalDateTime.now()
                                    .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                                userFileCreateLauncher.launch("lanxin_file_$stamp.txt")
                            },
                            enabled = config.masterEnabled && config.userFileEnabled,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("创建文本")
                        }
                    }
                    if (state.userFilePreview.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "最近文件",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        state.userFilePreview.forEach { entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = entry.name,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "${entry.source} · ${entry.sizeBytes ?: 0} B",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                OutlinedButton(
                                    onClick = { viewModel.shareUserFile(entry) }
                                ) {
                                    Text("分享")
                                }
                            }
                        }
                    }
                }
            }

            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "已注册工具（${state.stubToolNames.size}）",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    state.stubToolNames.forEach { name ->
                        Text(
                            text = "· $name",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        }
    }
}

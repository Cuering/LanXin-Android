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

package com.lanxin.android.builtin.scheduler.presentation

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lanxin.android.builtin.scheduler.domain.SchedulerTaskType
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditScreen(
    taskId: String?,
    onBackAction: () -> Unit,
    onSaved: () -> Unit,
    viewModel: TaskEditViewModel = hiltViewModel()
) {
    val isEdit = !taskId.isNullOrBlank()
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(SchedulerTaskType.ACTIVE_AGENT) }
    var useCron by remember { mutableStateOf(true) }
    var cron by remember { mutableStateOf("0 9 * * *") }
    var runAtMs by remember { mutableStateOf<Long?>(null) }
    var action by remember { mutableStateOf("log_event") }
    var notificationTitle by remember { mutableStateOf("") }
    var notificationContent by remember { mutableStateOf("") }
    var autoStart by remember { mutableStateOf(true) }
    var enabled by remember { mutableStateOf(true) }
    var loaded by remember { mutableStateOf(!isEdit) }
    var nextPreview by remember { mutableStateOf("") }

    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val timeFmt = remember {
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    }

    LaunchedEffect(taskId) {
        if (isEdit) {
            val task = viewModel.getTask(taskId!!)
            if (task != null) {
                name = task.name
                type = task.type
                useCron = !task.cronExpression.isNullOrBlank()
                cron = task.cronExpression ?: "0 9 * * *"
                runAtMs = task.runAt
                action = task.payload["action"] ?: "log_event"
                notificationTitle = task.payload["notificationTitle"] ?: ""
                notificationContent = task.payload["notificationContent"]
                    ?: task.payload["prompt"]
                    ?: ""
                autoStart = task.autoStartConversation
                enabled = task.enabled
            }
            loaded = true
        }
    }

    LaunchedEffect(cron, useCron) {
        nextPreview = if (useCron && cron.isNotBlank()) {
            viewModel.previewNext(cron)
        } else {
            runAtMs?.let {
                LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault())
                    .format(timeFmt)
            } ?: "未设置"
        }
    }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    LaunchedEffect(saved) {
        if (saved) onSaved()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEdit) "编辑任务" else "新建任务") },
                navigationIcon = {
                    IconButton(onClick = onBackAction) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        if (!loaded) return@Scaffold

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("名称") },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))
            Text("任务类型", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = type == SchedulerTaskType.ACTIVE_AGENT,
                    onClick = { type = SchedulerTaskType.ACTIVE_AGENT },
                    label = { Text("ACTIVE_AGENT") }
                )
                FilterChip(
                    selected = type == SchedulerTaskType.BASIC,
                    onClick = { type = SchedulerTaskType.BASIC },
                    label = { Text("BASIC") }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("使用 Cron 周期", modifier = Modifier.weight(1f))
                Switch(checked = useCron, onCheckedChange = { useCron = it })
            }

            if (useCron) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("预设", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    viewModel.presets.forEach { (label, expr) ->
                        FilterChip(
                            selected = cron == expr,
                            onClick = { cron = expr },
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = cron,
                    onValueChange = { cron = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Cron 表达式") },
                    supportingText = {
                        val err = viewModel.validateCron(cron)
                        Text(err ?: viewModel.humanReadable(cron))
                    },
                    isError = viewModel.validateCron(cron) != null,
                    singleLine = true
                )
                Text("下次执行：$nextPreview", style = MaterialTheme.typography.bodySmall)
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                val display = runAtMs?.let {
                    LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault())
                        .format(timeFmt)
                } ?: "未选择"
                Text("执行时间：$display")
                TextButton(
                    onClick = {
                        val cal = Calendar.getInstance()
                        runAtMs?.let { cal.timeInMillis = it }
                        DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                TimePickerDialog(
                                    context,
                                    { _, hour, minute ->
                                        val ldt = LocalDateTime.of(y, m + 1, d, hour, minute)
                                        runAtMs = ldt.atZone(ZoneId.systemDefault())
                                            .toInstant()
                                            .toEpochMilli()
                                    },
                                    cal.get(Calendar.HOUR_OF_DAY),
                                    cal.get(Calendar.MINUTE),
                                    true
                                ).show()
                            },
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    }
                ) {
                    Text("选择日期时间")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            when (type) {
                SchedulerTaskType.BASIC -> {
                    Text("Action", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        viewModel.actions.forEach { item ->
                            FilterChip(
                                selected = action == item,
                                onClick = { action = item },
                                label = { Text(item) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "BASIC 初始 action：http_request / app_broadcast / log_event / toast_notify。" +
                            "复杂参数请通过 MCP task_create 的 payload 传入。",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                SchedulerTaskType.ACTIVE_AGENT -> {
                    OutlinedTextField(
                        value = notificationTitle,
                        onValueChange = { notificationTitle = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("通知标题") },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = notificationContent,
                        onValueChange = { notificationContent = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        label = { Text("通知内容 / 对话 prompt") }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("点击通知自动发起对话", modifier = Modifier.weight(1f))
                        Switch(checked = autoStart, onCheckedChange = { autoStart = it })
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("启用", modifier = Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }

            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = {
                    viewModel.save(
                        taskId = taskId,
                        name = name,
                        type = type,
                        useCron = useCron,
                        cron = cron,
                        runAtMs = runAtMs,
                        action = action,
                        notificationTitle = notificationTitle,
                        notificationContent = notificationContent,
                        autoStart = autoStart,
                        enabled = enabled
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存")
            }
        }
    }
}

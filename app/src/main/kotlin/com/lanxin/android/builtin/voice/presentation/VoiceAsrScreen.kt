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

package com.lanxin.android.builtin.voice.presentation

import android.Manifest
import android.net.Uri
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lanxin.android.builtin.voice.domain.AsrEngineState
import com.lanxin.android.builtin.voice.domain.MicPermissionState
import com.lanxin.android.presentation.common.PathPickerField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceAsrScreen(
    onBackAction: () -> Unit,
    viewModel: VoiceAsrViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onPermissionResult(granted = granted, permanentlyDenied = !granted)
    }

    val asrTreePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.toString()?.let(viewModel::importModelFromTree)
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
                title = { Text("离线语音识别") },
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
            if (state.isBusy || state.pathImportBusy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "语音识别 · 设置引导",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    // 步骤 1
                    val asrPathDone = state.modelPath.isNotBlank()
                    val asrReady = state.engineState == AsrEngineState.READY
                    val micOk = state.micPermission == MicPermissionState.GRANTED
                    GuideLine(
                        done = asrPathDone,
                        index = 1,
                        title = "导入 ASR 模型",
                        detail = if (asrPathDone) "已选：${state.modelPath.takeLast(40)}"
                        else "优先在「桌宠 / 语音陪伴」一键下载；或本页选择含模型文件的文件夹。"
                    )
                    GuideLine(
                        done = asrReady,
                        index = 2,
                        title = "启用并加载",
                        detail = if (asrReady) "引擎就绪"
                        else "打开「启用离线语音识别」→「加载模型」。"
                    )
                    GuideLine(
                        done = micOk,
                        index = 3,
                        title = "授予麦克风权限",
                        detail = if (micOk) "已授权"
                        else "点下方「申请麦克风权限」；若永久拒绝请到系统设置开启。"
                    )
                    GuideLine(
                        done = asrReady && micOk,
                        index = 4,
                        title = "试转写 / 全屏陪伴说话",
                        detail = "本页「试转写」验证识别；全屏陪伴说话走完整 ASR→本地脑→TTS。"
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "报错时复制本页「状态」卡全文反馈。常见：模型缺文件、权限被拒、引擎未加载。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "状态",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = state.statusPreview.ifBlank { "刷新中…" },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "麦克风: ${micLabel(state.micPermission)} · 引擎: ${stateLabel(state.engineState)}" +
                            (state.lastError?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("启用离线语音识别", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "默认关；关闭时不 load so，引擎 DISABLED",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = state.enabled,
                    onCheckedChange = viewModel::setEnabled
                )
            }

            PathPickerField(
                label = "ASR 模型目录",
                path = state.modelPath,
                helperText = "优先在「桌宠 / 语音陪伴」一键下载；此处可自定义选择文件夹。" +
                    "大文件勿提交 git。",
                pickButtonText = "选择目录",
                onPick = { asrTreePicker.launch(null) },
                onClear = { viewModel.setModelPath("") },
                showManualEntry = false,
                enabled = !state.pathImportBusy && !state.isBusy
            )

            OutlinedTextField(
                value = state.language,
                onValueChange = viewModel::setLanguage,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("语言") },
                supportingText = { Text("如 zh / en") },
                singleLine = true
            )

            Text(
                text = "采样率: ${state.sampleRateHz} Hz（PCM 16-bit mono）",
                style = MaterialTheme.typography.bodyMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = viewModel::loadModel,
                    enabled = state.enabled && !state.isBusy
                ) {
                    Text("加载模型")
                }
                OutlinedButton(
                    onClick = viewModel::unloadModel,
                    enabled = !state.isBusy
                ) {
                    Text("卸载")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    enabled = !state.isBusy
                ) {
                    Text("申请麦克风权限")
                }
                Button(
                    onClick = viewModel::trialTranscribe,
                    enabled = state.enabled && !state.isBusy
                ) {
                    Text("试转写")
                }
            }

            if (state.lastTranscript.isNotBlank()) {
                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "最近试转写结果",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = state.lastTranscript,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Chat 接入",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "TODO：Chat 输入栏「按住说话」按钮。" +
                            "API 已预留 VoiceInputCoordinator.transcribePcm / preflightForRecording；" +
                            "识别文本交给现有发送消息链路，不走本地 LLM tool_call。",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

private fun stateLabel(state: AsrEngineState): String = when (state) {
    AsrEngineState.DISABLED -> "已禁用"
    AsrEngineState.IDLE -> "空闲"
    AsrEngineState.LOADING -> "加载中"
    AsrEngineState.READY -> "就绪"
    AsrEngineState.ERROR -> "错误"
}

private fun micLabel(state: MicPermissionState): String = when (state) {
    MicPermissionState.GRANTED -> "已授权"
    MicPermissionState.DENIED -> "未授权"
    MicPermissionState.PERMANENTLY_DENIED -> "已永久拒绝"
    MicPermissionState.UNKNOWN -> "未知"
}

@Composable
private fun GuideLine(
    done: Boolean,
    index: Int,
    title: String,
    detail: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = if (done) "✓" else "$index.",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (done) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(end = 8.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (done) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

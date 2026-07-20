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

package com.lanxin.android.builtin.localinference.presentation

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lanxin.android.builtin.localinference.domain.LocalEngineState
import com.lanxin.android.presentation.common.PathPickerField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalInferenceScreen(
    onBackAction: () -> Unit,
    viewModel: LocalInferenceViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val modelTreePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.toString()?.let(viewModel::importModelFromTree)
    }

    val modelFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.toString()?.let(viewModel::importModelFromDocument)
    }

    var modelDraft by remember { mutableStateOf(state.modelPath) }
    LaunchedEffect(state.modelPath) {
        modelDraft = state.modelPath
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
                title = { Text("本地推理") },
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
                        text = "Phase 6.2 · 离线兜底 + MNN 骨架（stub）",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "总开关默认关闭；关闭时不 load so / 不占模型内存。" +
                            "模型自备：轻量 0.5B/1.5B 或标准 7B Q4（16G 推荐）。" +
                            "本地无 tool_call，记忆/KB 由 App 注入。" +
                            "仅当开关已开且模型就绪时，无网才自动走本地；有网默认云端。",
                        style = MaterialTheme.typography.bodyMedium
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
                        text = "当前路由预览",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = state.routePreview.ifBlank { "刷新中…" },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "离线兜底：无网 + 本地就绪 → 自动本地；" +
                            "无网 + 本地未就绪 → 提示去设置加载模型；" +
                            "有网默认云端（可开「优先本地」）。",
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
                    Text("启用本地推理", style = MaterialTheme.typography.bodyLarge)
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("优先本地路由", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "有网时优先本地（需模型就绪）；完整 ChatRouter 见 6.3",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = state.preferLocal,
                    onCheckedChange = viewModel::setPreferLocal
                )
            }

            PathPickerField(
                label = "本地推理模型路径",
                path = state.modelPath,
                helperText = "请选择完整模型文件夹（config.json + *.mnn + tokenizer）。" +
                    "只选单个 llm.mnn 无法真正推理。高级可选手填路径 / stub://demo。",
                pickButtonText = "选择文件夹",
                onPick = { modelTreePicker.launch(null) },
                secondaryPickText = "选单文件",
                onSecondaryPick = {
                    modelFilePicker.launch(arrayOf("application/octet-stream", "*/*"))
                },
                onClear = { viewModel.setModelPath("") },
                manualDraft = modelDraft,
                onManualDraftChange = { modelDraft = it },
                onManualSave = viewModel::setModelPath,
                enabled = !state.pathImportBusy && !state.isBusy
            )

            OutlinedTextField(
                value = state.maxTokens.toString(),
                onValueChange = { raw ->
                    raw.toIntOrNull()?.let { viewModel.setMaxTokens(it) }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("maxTokens") },
                singleLine = true
            )

            Text(
                text = "引擎状态: ${stateLabel(state.engineState)}" +
                    (state.lastError?.let { " · $it" } ?: ""),
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
        }
    }
}

private fun stateLabel(state: LocalEngineState): String = when (state) {
    LocalEngineState.DISABLED -> "已禁用"
    LocalEngineState.IDLE -> "空闲"
    LocalEngineState.LOADING -> "加载中"
    LocalEngineState.READY -> "就绪"
    LocalEngineState.ERROR -> "错误"
}

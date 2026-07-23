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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lanxin.android.builtin.localinference.domain.LocalEngineState
import com.lanxin.android.builtin.localinference.domain.LocalInferenceConfig
import com.lanxin.android.presentation.common.PathPickerField
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalInferenceScreen(
    onBackAction: () -> Unit,
    onNavigateToOfflineAsr: () -> Unit = {},
    onNavigateToDesktopPet: () -> Unit = {},
    viewModel: LocalInferenceViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    val modelTreePicker = rememberLauncherForActivityResult(
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

    LaunchedEffect(state.needModelPicker) {
        if (state.needModelPicker) {
            modelTreePicker.launch(null)
            viewModel.clearNeedModelPicker()
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

            // ---- 分步引导（随状态实时刷新 ✅）----
            val guideSteps = listOf(
                SetupStep(
                    title = "准备模型包",
                    isDone = state.modelPath.isNotBlank(),
                    doneHint = "已选路径",
                    detail = "需要完整文件夹：config.json + llm.mnn + llm.mnn.weight + tokenizer（.mtok）。推荐 Qwen 0.5B / 1.5B MNN 导出包；内存不足勿上 7B。",
                    actionLabel = "选择文件夹",
                    actionIcon = StepIconModel,
                    actionEnabled = !state.pathImportBusy && !state.isBusy,
                    onAction = { modelTreePicker.launch(null) }
                ),
                SetupStep(
                    title = "启用并加载模型",
                    isDone = state.engineState == LocalEngineState.READY,
                    doneHint = "引擎就绪",
                    detail = "点「一键开启本地对话」自动开开关+选文件夹+加载；" +
                        "或手动开「启用本地推理」→「加载模型」。",
                    actionLabel = if (state.engineState == LocalEngineState.READY) "已就绪" else "一键开启",
                    actionIcon = StepIconPlay,
                    actionEnabled = !state.isBusy && !state.pathImportBusy,
                    onAction = viewModel::oneClickEnableLocalChat
                ),
                SetupStep(
                    title = "验证文字对话",
                    isDone = state.engineState == LocalEngineState.READY && state.enabled,
                    doneHint = "可对话",
                    detail = "新建会话勾选「本地模型」，或开「优先本地路由」后发「你好」。\n" +
                        "下方「当前路由预览」应显示本地生成；若仍走云端请检查开关。",
                    actionLabel = if (state.preferLocal) "已优先本地" else "开优先本地",
                    actionIcon = StepIconSettings,
                    actionEnabled = state.engineState == LocalEngineState.READY,
                    onAction = { viewModel.setPreferLocal(!state.preferLocal) }
                ),
                SetupStep(
                    title = "语音识别（ASR）",
                    isDone = false,
                    detail = "前往「离线语音识别」页：导入 ASR 模型、启用引擎、授予麦克风权限。\n" +
                        "也可在「桌宠 / 语音陪伴」页一键下载。",
                    actionLabel = "前往 ASR 设置",
                    actionIcon = StepIconMic,
                    actionEnabled = true,
                    onAction = onNavigateToOfflineAsr
                ),
                SetupStep(
                    title = "语音播报（TTS）",
                    isDone = false,
                    detail = "在「桌宠 / 语音陪伴」页配置 TTS 模型目录与参考音频。\n" +
                        "全屏陪伴里说话 → ASR 识别 → 本地脑生成 → TTS 读出。",
                    actionLabel = "前往桌宠设置",
                    actionIcon = StepIconTts,
                    actionEnabled = true,
                    onAction = onNavigateToDesktopPet
                )
            )
            SetupGuideCard(steps = guideSteps)

            // 报错一键复制反馈
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "遇到报错？一键复制反馈",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "会复制：引擎状态 / 路由预览 / 最后错误 / 模型路径摘要。\n" +
                            "常见失败：模型包缺文件、只选了单文件、内存不足、未授权麦克风。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            val report = buildString {
                                appendLine("=== 兰心本地脑反馈 ===")
                                appendLine("引擎: ${stateLabel(state.engineState)}")
                                appendLine("启用: ${state.enabled}")
                                appendLine("优先本地: ${state.preferLocal}")
                                appendLine("路由: ${state.routePreview}")
                                appendLine("模型路径: ${state.modelPath.ifBlank { "(空)" }}")
                                appendLine("最后错误: ${state.lastError ?: "(无)"}")
                                appendLine("网络: ${if (state.networkAvailable) "有" else "无"}")
                                appendLine("上下文窗口: ${state.contextWindowTokens}")
                                appendLine("maxTokens: ${state.maxTokens}")
                            }
                            clipboard.setText(AnnotatedString(report))
                            scope.launch {
                                snackbarHostState.showSnackbar("已复制到剪贴板，可粘贴反馈")
                            }
                        }
                    ) {
                        Text("复制诊断信息")
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "本地推理 · 能力说明",
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

            Button(
                onClick = viewModel::oneClickEnableLocalChat,
                enabled = !state.isBusy && !state.pathImportBusy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (state.engineState == LocalEngineState.READY) {
                        "本地对话已就绪"
                    } else {
                        "一键开启本地对话"
                    }
                )
            }
            if (state.engineState == LocalEngineState.ERROR ||
                (state.enabled && state.engineState != LocalEngineState.READY)
            ) {
                OutlinedButton(
                    onClick = viewModel::retryLoad,
                    enabled = !state.isBusy && !state.pathImportBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("重试加载")
                }
            }
            Text(
                text = "有完整模型包：开开关并加载；无模型：引导选择文件夹（config.json + *.mnn）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

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

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("显示思考过程", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "默认关：剥离 <think>，气泡只显示正文；开启后思考可折叠展示",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = state.showThinking,
                    onCheckedChange = viewModel::setShowThinking
                )
            }

            PathPickerField(
                label = "本地推理模型路径",
                path = state.modelPath,
                helperText = "请选择完整模型文件夹（config.json + *.mnn + tokenizer）。" +
                    "只选单个 llm.mnn 无法真正推理。",
                pickButtonText = "选择文件夹",
                onPick = { modelTreePicker.launch(null) },
                onClear = { viewModel.setModelPath("") },
                showManualEntry = false,
                enabled = !state.pathImportBusy && !state.isBusy
            )

            Text(
                text = "上下文窗口",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = "默认 8k。窗口越大越吃内存（KV cache）；12G 机建议不超过 12k。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LocalInferenceConfig.CONTEXT_WINDOW_PRESETS.forEach { preset ->
                    val selected = state.contextWindowTokens == preset
                    if (selected) {
                        Button(
                            onClick = { viewModel.setContextWindowTokens(preset) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(labelContextWindow(preset))
                        }
                    } else {
                        OutlinedButton(
                            onClick = { viewModel.setContextWindowTokens(preset) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(labelContextWindow(preset))
                        }
                    }
                }
            }

            OutlinedTextField(
                value = state.maxTokens.toString(),
                onValueChange = { raw ->
                    raw.toIntOrNull()?.let { viewModel.setMaxTokens(it) }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("生成 maxTokens（输出上限）") },
                supportingText = {
                    Text("与上下文窗口分离；默认 512，上限 ${LocalInferenceConfig.MAX_MAX_TOKENS}")
                },
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

private fun labelContextWindow(tokens: Int): String = when (tokens) {
    4096 -> "4k"
    8192 -> "8k"
    12288 -> "12k"
    else -> "${tokens / 1024}k"
}

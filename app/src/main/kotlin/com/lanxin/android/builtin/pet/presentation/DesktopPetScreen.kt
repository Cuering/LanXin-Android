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

package com.lanxin.android.builtin.pet.presentation

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lanxin.android.builtin.pet.domain.DebugAssetKind
import com.lanxin.android.builtin.pet.domain.DebugAssetMirror
import com.lanxin.android.builtin.pet.domain.Live2dModelCatalog
import com.lanxin.android.presentation.common.PathPickerField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesktopPetScreen(
    onBackAction: () -> Unit,
    onOpenCompanion: () -> Unit = {},
    viewModel: DesktopPetViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    var live2dDraft by remember { mutableStateOf("") }
    var asrDraft by remember { mutableStateOf("") }
    var ttsDirDraft by remember { mutableStateOf("") }
    var ttsRefDraft by remember { mutableStateOf("") }
    var localLlmDraft by remember { mutableStateOf("") }

    val live2dFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.toString()?.let(viewModel::importLive2dFromDocument)
    }
    val live2dTreePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.toString()?.let(viewModel::importLive2dFromTree)
    }
    val asrTreePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.toString()?.let(viewModel::importAsrFromTree)
    }
    val ttsTreePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.toString()?.let(viewModel::importTtsDirFromTree)
    }
    val ttsRefPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.toString()?.let(viewModel::importTtsReferenceFromDocument)
    }
    val localLlmPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.toString()?.let(viewModel::importLocalLlmFromDocument)
    }

    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    LaunchedEffect(state.live2dModelPathConfigured) {
        live2dDraft = state.live2dModelPathConfigured
    }
    LaunchedEffect(state.asrModelPathConfigured) {
        asrDraft = state.asrModelPathConfigured
    }
    LaunchedEffect(state.ttsModelDirConfigured) {
        ttsDirDraft = state.ttsModelDirConfigured
    }
    LaunchedEffect(state.ttsReferenceConfigured) {
        ttsRefDraft = state.ttsReferenceConfigured
    }
    LaunchedEffect(state.localLlmPathConfigured) {
        localLlmDraft = state.localLlmPathConfigured
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
                title = { Text("桌宠 / 语音陪伴") },
                navigationIcon = {
                    IconButton(onClick = onBackAction) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onOpenCompanion) {
                        Text("全屏陪伴")
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
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "M2b 打磨：Live2D 壳 + 会话表情/口型联动。默认关，不偷偷录音/截屏。" +
                    "语音资源可在本页一键下载到本机 LanXin/ 目录（不进 git）。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = onOpenCompanion,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("打开全屏陪伴（Live2D + 输入框）")
            }

            if (state.isBusy || state.pathImportBusy || state.downloadBusy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("总开关", fontWeight = FontWeight.SemiBold)
                            Text(
                                "关闭时不启动悬浮层、不跑会话",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(
                            checked = state.enabled,
                            onCheckedChange = viewModel::setEnabled
                        )
                    }
                }
            }

            // Live2D 模型切换
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Live2D 模型", fontWeight = FontWeight.SemiBold)
                    Text(
                        "当前：${state.live2dCurrentName} · ${state.live2dReadyLabel}" +
                            if (state.live2dReady) " ✓" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "路径：${
                            com.lanxin.android.util.PathImportHelper.shortSummary(
                                state.live2dModelPathResolved.ifBlank {
                                    state.live2dModelPathConfigured
                                }
                            )
                        }",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "点选即可切换；导入/下载落在同一目录，文件管理器可找到。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (state.live2dModels.isEmpty()) {
                        Text(
                            "暂无模型（应至少有内置 Mao）",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        state.live2dModels.forEach { entry ->
                            Live2dModelRow(
                                entry = entry,
                                enabled = !state.pathImportBusy,
                                onSelect = { viewModel.selectLive2dModel(entry) }
                            )
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = {
                                live2dFilePicker.launch(arrayOf("application/json", "*/*"))
                            },
                            enabled = !state.pathImportBusy,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("导入 model3")
                        }
                        OutlinedButton(
                            onClick = { live2dTreePicker.launch(null) },
                            enabled = !state.pathImportBusy,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("导入文件夹")
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(onClick = viewModel::showLive2dDirHint) {
                            Text("打开目录说明")
                        }
                        TextButton(onClick = viewModel::exportBuiltinLive2dToLanXin) {
                            Text("同步内置到目录")
                        }
                        TextButton(onClick = viewModel::refresh) {
                            Text("刷新列表")
                        }
                    }
                    if (state.live2dDirHint.isNotBlank()) {
                        Text(
                            "目录：${state.live2dDirHint}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 资源路径就绪
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("资源路径与就绪状态", fontWeight = FontWeight.SemiBold)
                    Text(
                        state.live2dSourceLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Live2D：${state.live2dReadyLabel}" +
                            if (state.live2dReady) " ✓" else "",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "显示：${state.live2dDisplayLabel}（${state.live2dDisplayMode}）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "表情：${state.expressionLabel}（${state.expressionName}）" +
                            if (state.mouthAnimating) " · 口型动画" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (state.resourceGuide.isNotBlank()) {
                        Text(
                            state.resourceGuide,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        "ASR：${state.asrSourceLabel} · ${state.asrReadyLabel}" +
                            if (state.asrReady) " ✓" else "",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "TTS：${state.ttsSourceLabel} · ${state.ttsReadyLabel}" +
                            if (state.ttsReady) " ✓" else "",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (state.resourceSummary.isNotBlank()) {
                        Text(
                            state.resourceSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // —— App 内一键下载 ——
                    Text("一键下载（本机 LanXin 目录）", fontWeight = FontWeight.Medium)
                    Text(
                        state.live2dBuiltinHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (state.downloadRootPath.isNotBlank()) {
                        Text(
                            buildString {
                                append("保存位置：")
                                append(state.downloadRootPath)
                                if (state.downloadRootFallback) {
                                    append("（公共目录不可写，已回退）")
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        state.live2dLicenseHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("镜像", style = MaterialTheme.typography.bodySmall)
                        FilterChip(
                            selected = state.preferredMirror == DebugAssetMirror.MIRROR_CDN,
                            onClick = {
                                viewModel.setPreferredMirror(DebugAssetMirror.MIRROR_CDN)
                            },
                            label = { Text("CDN（推荐）") }
                        )
                        FilterChip(
                            selected = state.preferredMirror == DebugAssetMirror.OFFICIAL,
                            onClick = {
                                viewModel.setPreferredMirror(DebugAssetMirror.OFFICIAL)
                            },
                            label = { Text("官方源") }
                        )
                    }
                    Text(
                        "源序：Live2D=jsDelivr→fastly→GitHub raw（内置优先）；" +
                            "ASR/TTS=hf-mirror→huggingface→GitHub release；" +
                            "本地脑=ModelScope→hf-mirror→HF。" +
                            "落盘 LanXin/…。大文件请连 Wi‑Fi。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    state.downloadItems.forEach { item ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "${item.displayName}（${item.sizeHint}）",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        buildString {
                                            append(item.statusText)
                                            if (item.ready && item.readyPath.isNotBlank()) {
                                                append(" · ")
                                                append(item.readyPath.takeLast(48))
                                            }
                                            item.lastError?.let {
                                                append(" · ")
                                                append(it)
                                            }
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (item.lastError != null) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                                if (item.downloading) {
                                    TextButton(onClick = viewModel::cancelDownload) {
                                        Text("取消")
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = { viewModel.startDownload(item.kind) },
                                        enabled = !state.downloadBusy
                                    ) {
                                        Text(
                                            when {
                                                item.ready && item.kind == DebugAssetKind.LIVE2D ->
                                                    "更新"
                                                item.ready -> "重新下载"
                                                else -> "下载"
                                            }
                                        )
                                    }
                                }
                            }
                            if (item.downloading && item.percent in 0..100) {
                                LinearProgressIndicator(
                                    progress = { item.percent / 100f },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else if (item.downloading) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                    if (state.downloadBusy) {
                        OutlinedButton(onClick = viewModel::cancelDownload) {
                            Text("取消当前下载")
                        }
                    }

                    val anyMissing = !state.live2dReady || !state.asrReady || !state.ttsReady
                    if (anyMissing) {
                        TextButton(onClick = viewModel::showFetchAssetsHint) {
                            Text("高级：脚本拉取说明（可选）")
                        }
                    } else {
                        Text(
                            "路径均已就绪（引擎 so 可后续接；本阶段不强制真推理）。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Text(
                        "自定义路径（高级）",
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "普通用户优先用上方「Live2D 模型」列表切换与导入。" +
                            "Live2D 导入落盘到 LanXin/live2d/；清除后回到内置解析。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    PathPickerField(
                        label = "Live2D 模型（高级手填）",
                        path = state.live2dModelPathConfigured,
                        readyLabel = "就绪：${state.live2dReadyLabel} · 生效：${
                            state.live2dModelPathResolved.ifBlank { "（空 → 占位/内置）" }
                        }",
                        ready = state.live2dReady,
                        helperText = "优先用上方列表；此处可手填绝对路径。导入请用上方「导入 model3/文件夹」。",
                        pickButtonText = "选 model3.json",
                        onPick = {
                            live2dFilePicker.launch(arrayOf("application/json", "*/*"))
                        },
                        secondaryPickText = "选文件夹",
                        onSecondaryPick = { live2dTreePicker.launch(null) },
                        onClear = { viewModel.setLive2dModelPath("") },
                        manualDraft = live2dDraft,
                        onManualDraftChange = { live2dDraft = it },
                        onManualSave = viewModel::setLive2dModelPath,
                        enabled = !state.pathImportBusy
                    )

                    PathPickerField(
                        label = "ASR 模型目录",
                        path = state.asrModelPathConfigured,
                        readyLabel = "就绪：${state.asrReadyLabel} · 生效：${
                            state.asrModelPathResolved.ifBlank { "（空）" }
                        }",
                        ready = state.asrReady,
                        helperText = "选择含模型文件的文件夹；也可在「离线语音识别」页配置。",
                        pickButtonText = "选择目录",
                        onPick = { asrTreePicker.launch(null) },
                        onClear = { viewModel.setAsrModelPath("") },
                        manualDraft = asrDraft,
                        onManualDraftChange = { asrDraft = it },
                        onManualSave = viewModel::setAsrModelPath,
                        enabled = !state.pathImportBusy
                    )

                    PathPickerField(
                        label = "TTS 模型目录",
                        path = state.ttsModelDirConfigured,
                        readyLabel = "就绪：${state.ttsReadyLabel} · 生效：${
                            state.ttsModelDirResolved.ifBlank { "（空）" }
                        }",
                        ready = state.ttsReady,
                        helperText = "选择 TTS 模型所在文件夹。",
                        pickButtonText = "选择目录",
                        onPick = { ttsTreePicker.launch(null) },
                        onClear = { viewModel.setTtsModelDir("") },
                        manualDraft = ttsDirDraft,
                        onManualDraftChange = { ttsDirDraft = it },
                        onManualSave = viewModel::setTtsModelDir,
                        enabled = !state.pathImportBusy
                    )

                    PathPickerField(
                        label = "TTS 参考音频",
                        path = state.ttsReferenceConfigured,
                        readyLabel = "生效：${
                            state.ttsReferenceResolved.ifBlank { "（空）" }
                        }",
                        helperText = "常见音频：wav / mp3 / m4a / ogg 等。",
                        pickButtonText = "选择音频",
                        onPick = {
                            ttsRefPicker.launch(
                                arrayOf(
                                    "audio/*",
                                    "audio/wav",
                                    "audio/x-wav",
                                    "audio/mpeg",
                                    "audio/mp4",
                                    "audio/ogg",
                                    "*/*"
                                )
                            )
                        },
                        onClear = { viewModel.setTtsReferenceAudio("") },
                        manualDraft = ttsRefDraft,
                        onManualDraftChange = { ttsRefDraft = it },
                        onManualSave = viewModel::setTtsReferenceAudio,
                        enabled = !state.pathImportBusy
                    )

                    Text(
                        "换 Live2D / TTS / ASR 只改设置项，不改 VoiceSession 状态机。" +
                            "仓内已内置 Mao；下载侧重 ASR/TTS 或备用 Live2D。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 本地脑预留
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("本地脑（预留）", fontWeight = FontWeight.SemiBold)
                    Text(
                        "键：local_inference_model_path · 状态：${state.localLlmReadyLabel}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        state.localLlmHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "也可在上方「一键下载」拉取 Qwen2.5-1.5B MNN（~880MB，优先魔搭）；" +
                            "或自备模型用选择器导入。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    PathPickerField(
                        label = "本地推理模型路径",
                        path = state.localLlmPathConfigured,
                        readyLabel = "状态：${state.localLlmReadyLabel}",
                        ready = state.localLlmPathConfigured.isNotBlank() &&
                            state.localLlmReadyLabel.contains("就绪"),
                        helperText = "选择模型文件（如 .gguf / .mnn / 目录打包文件）；大文件勿提交 git。",
                        pickButtonText = "选择文件",
                        onPick = {
                            localLlmPicker.launch(
                                arrayOf(
                                    "application/octet-stream",
                                    "*/*"
                                )
                            )
                        },
                        onClear = { viewModel.setLocalLlmModelPath("") },
                        manualDraft = localLlmDraft,
                        onManualDraftChange = { localLlmDraft = it },
                        onManualSave = viewModel::setLocalLlmModelPath,
                        enabled = !state.pathImportBusy
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("悬浮权限", fontWeight = FontWeight.SemiBold)
                    Text(
                        if (state.canDrawOverlays) "已授予 SYSTEM_ALERT_WINDOW"
                        else "未授予 — 需「显示在其他应用上层」",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedButton(
                        onClick = viewModel::requestOverlayPermission,
                        enabled = !state.canDrawOverlays
                    ) {
                        Text(if (state.canDrawOverlays) "权限已就绪" else "去系统设置授权")
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("桌宠控制", fontWeight = FontWeight.SemiBold)
                    Text(
                        "悬浮层：${if (state.overlayRunning) "运行中" else "未运行"}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = viewModel::startPet,
                            enabled = state.enabled && state.canDrawOverlays
                        ) { Text("启动桌宠") }
                        OutlinedButton(onClick = viewModel::stopPet) {
                            Text("停止桌宠")
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("语音会话", fontWeight = FontWeight.SemiBold)
                    Text(
                        "IDLE → LISTENING → THINKING → SPEAKING → IDLE",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("状态预览", fontWeight = FontWeight.Medium)
                    Text(
                        state.sessionPreview.ifBlank { "phase=IDLE" },
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (state.subtitle.isNotBlank()) {
                        Text("气泡：${state.subtitle}", style = MaterialTheme.typography.bodyMedium)
                    }
                    Button(
                        onClick = viewModel::runDemoRound,
                        enabled = state.enabled && !state.isBusy
                    ) {
                        Text("试运行 stub 一轮（听→想→说）")
                    }
                    Text(
                        "无需真 so / 真麦克风；气泡走桌宠会话，不塞 Chat 输入框。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Live2dModelRow(
    entry: Live2dModelCatalog.ModelEntry,
    enabled: Boolean,
    onSelect: () -> Unit
) {
    val sourceLabel = when (entry.source) {
        Live2dModelCatalog.Source.BUILTIN -> "内置"
        Live2dModelCatalog.Source.LANXIN -> "LanXin"
        Live2dModelCatalog.Source.CUSTOM -> "自定义"
    }
    val readyText = if (entry.ready) "就绪" else "未就绪"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onSelect)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = entry.selected,
            onClick = onSelect,
            enabled = enabled
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                entry.displayName,
                fontWeight = if (entry.selected) FontWeight.SemiBold else FontWeight.Normal
            )
            Text(
                "$sourceLabel · $readyText · ${entry.shortPath}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

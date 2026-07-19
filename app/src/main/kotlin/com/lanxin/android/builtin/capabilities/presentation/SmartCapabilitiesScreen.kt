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

package com.lanxin.android.builtin.capabilities.presentation

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lanxin.android.builtin.capabilities.domain.SmartCapabilityId

/**
 * 设置 → 智能能力 聚合页。
 *
 * 主开关 + 状态摘要 + 子能力平铺；高级折叠放路径/细项入口。
 * Claw / 桌宠悬浮不在此页默认 ON 列表。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartCapabilitiesScreen(
    onBackAction: () -> Unit,
    onNavigateToLocalInference: () -> Unit = {},
    onNavigateToVoice: () -> Unit = {},
    onNavigateToSystemTools: () -> Unit = {},
    onNavigateToWebSearch: () -> Unit = {},
    onNavigateToDeviceSensing: () -> Unit = {},
    onNavigateToSceneVision: () -> Unit = {},
    viewModel: SmartCapabilitiesViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

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
                title = { Text("智能能力") },
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
        if (state.loading && state.summary.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "智能能力",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "主开关关闭时，语音 / 系统工具 / 搜索 / 设备感知 / 位置 / 本地推理 / 场景视觉一律拒绝。" +
                            "本地推理与场景视觉默认关；桌宠悬浮与 Claw 不在本页。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            CapabilitySwitchRow(
                title = "主开关",
                description = "关则子能力一律拒",
                checked = state.masterEnabled,
                onCheckedChange = viewModel::setMaster
            )

            HorizontalDivider()

            CapabilitySwitchRow(
                title = "本地推理",
                description = "默认关；0.5B/1.5B 或 7B Q4；打开后须能真正 load",
                checked = state.localInferenceEnabled,
                enabled = state.masterEnabled,
                onCheckedChange = {
                    viewModel.setChild(SmartCapabilityId.LOCAL_INFERENCE, it)
                },
                onDetailClick = onNavigateToLocalInference
            )

            CapabilitySwitchRow(
                title = "语音能力",
                description = "ASR + TTS 会话；悬浮窗不绑死为 ON",
                checked = state.voiceEnabled,
                enabled = state.masterEnabled,
                onCheckedChange = {
                    viewModel.setChild(SmartCapabilityId.VOICE, it)
                },
                onDetailClick = onNavigateToVoice
            )

            CapabilitySwitchRow(
                title = "系统工具",
                description = "日历 / 闹钟 / 笔记 / 文件；写操作仍确认",
                checked = state.systemToolsEnabled,
                enabled = state.masterEnabled,
                onCheckedChange = {
                    viewModel.setChild(SmartCapabilityId.SYSTEM_TOOLS, it)
                },
                onDetailClick = onNavigateToSystemTools
            )

            CapabilitySwitchRow(
                title = "联网搜索",
                description = "web_search（DuckDuckGo）；开后 Agent 可见",
                checked = state.webSearchEnabled,
                enabled = state.masterEnabled,
                onCheckedChange = {
                    viewModel.setChild(SmartCapabilityId.WEB_SEARCH, it)
                },
                onDetailClick = onNavigateToWebSearch
            )

            CapabilitySwitchRow(
                title = "设备感知",
                description = "system_info（型号/网络/电量）",
                checked = state.deviceSensingEnabled,
                enabled = state.masterEnabled,
                onCheckedChange = {
                    viewModel.setChild(SmartCapabilityId.DEVICE_SENSING, it)
                },
                onDetailClick = onNavigateToDeviceSensing
            )

            CapabilitySwitchRow(
                title = "位置",
                description = "默认授权可调；tool 用时申请权限，不后台持续定位",
                checked = state.locationEnabled,
                enabled = state.masterEnabled,
                onCheckedChange = {
                    viewModel.setChild(SmartCapabilityId.LOCATION, it)
                }
            )

            CapabilitySwitchRow(
                title = "场景视觉",
                description = "默认关；摄像头快照→本地场景；需 consent",
                checked = state.sceneVisionEnabled,
                enabled = state.masterEnabled,
                onCheckedChange = {
                    viewModel.setChild(SmartCapabilityId.SCENE_VISION, it)
                },
                onDetailClick = onNavigateToSceneVision
            )

            HorizontalDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.toggleAdvanced() }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "高级",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (state.advancedExpanded) {
                        Icons.Filled.ExpandLess
                    } else {
                        Icons.Filled.ExpandMore
                    },
                    contentDescription = null
                )
            }

            AnimatedVisibility(visible = state.advancedExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AdvancedLink("本地推理 · 模型路径 / 参数", onNavigateToLocalInference)
                    AdvancedLink("离线语音 · ASR 模型 / 语言", onNavigateToVoice)
                    AdvancedLink("系统工具 · 分项与写确认", onNavigateToSystemTools)
                    AdvancedLink("联网搜索 · 条数 / 区域", onNavigateToWebSearch)
                    AdvancedLink("设备感知 · system_info", onNavigateToDeviceSensing)
                    AdvancedLink("场景视觉 · consent / 识别", onNavigateToSceneVision)
                    Text(
                        text = "键前缀 smart_capabilities_*；迁移标记 migrated_v1。" +
                            "Claw 与桌宠悬浮仍在各自设置页。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CapabilitySwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    onDetailClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .then(
                    if (onDetailClick != null) {
                        Modifier.clickable(onClick = onDetailClick)
                    } else {
                        Modifier
                    }
                )
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (onDetailClick != null) {
            IconButton(onClick = onDetailClick) {
                Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "详情")
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

@Composable
private fun AdvancedLink(title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null)
    }
}

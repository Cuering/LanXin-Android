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
import androidx.compose.ui.Modifier
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
 * 主开关 + 状态摘要 + 合并后 5 组；高级折叠放路径/细项入口。
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
                        text = "本页只管能力开关：主开关关闭时，本地模型 / 语音 / 助手工具 / 位置与周边 / 看世界一律拒绝。" +
                            "本地模型与看世界默认关。Live2D/背景/音乐与下载资源请去「桌宠 / 语音陪伴」。",
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
                title = "本地模型",
                description = "默认关；0.5B/1.5B 或 7B Q4；打开后须能真正 load",
                checked = state.localInferenceEnabled,
                enabled = state.masterEnabled,
                onCheckedChange = {
                    viewModel.setChild(SmartCapabilityId.LOCAL_INFERENCE, it)
                },
                onDetailClick = onNavigateToLocalInference
            )

            CapabilitySwitchRow(
                title = "语音",
                description = "ASR + TTS 会话；悬浮窗不绑死为 ON",
                checked = state.voiceEnabled,
                enabled = state.masterEnabled,
                onCheckedChange = {
                    viewModel.setChild(SmartCapabilityId.VOICE, it)
                },
                onDetailClick = onNavigateToVoice
            )

            CapabilitySwitchRow(
                title = "助手工具",
                description = "系统工具 + 联网搜索 + 设备感知；写操作仍确认",
                checked = state.assistantToolsEnabled,
                enabled = state.masterEnabled,
                onCheckedChange = {
                    viewModel.setChild(SmartCapabilityId.ASSISTANT_TOOLS, it)
                },
                onDetailClick = onNavigateToSystemTools
            )

            CapabilitySwitchRow(
                title = "位置与周边",
                description = "定位 / 附近；tool 用时申请权限，不后台持续定位",
                checked = state.locationAroundEnabled,
                enabled = state.masterEnabled,
                onCheckedChange = {
                    viewModel.setChild(SmartCapabilityId.LOCATION_AROUND, it)
                }
            )

            CapabilitySwitchRow(
                title = "导航",
                description = "默认关；附近 POI / 外链导航 / 酒店价（lanxin.navigate）",
                checked = state.navigateEnabled,
                enabled = state.masterEnabled,
                onCheckedChange = {
                    viewModel.setChild(SmartCapabilityId.NAVIGATE, it)
                }
            )

            CapabilitySwitchRow(
                title = "导游",
                description = "默认关；看世界讲解 / 位置增强（lanxin.guide）",
                checked = state.guideEnabled,
                enabled = state.masterEnabled,
                onCheckedChange = {
                    viewModel.setChild(SmartCapabilityId.GUIDE, it)
                }
            )

            CapabilitySwitchRow(
                title = "看世界",
                description = "默认关；摄像头快照→本地场景；需 consent（隐私敏感）",
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
                    AdvancedLink("本地模型 · 模型文件夹 / 参数（唯一配置源）", onNavigateToLocalInference)
                    AdvancedLink("离线语音 · ASR 模型 / 语言", onNavigateToVoice)
                    AdvancedLink("系统工具 · 分项与写确认", onNavigateToSystemTools)
                    AdvancedLink("联网搜索 · 条数 / 区域", onNavigateToWebSearch)
                    AdvancedLink("设备感知 · system_info", onNavigateToDeviceSensing)
                    AdvancedLink("看世界 · consent / 识别", onNavigateToSceneVision)
                    Text(
                        text = "键前缀 smart_capabilities_*；迁移标记 migrated_v1 / migrated_v2。" +
                            "助手工具开关联动系统工具/搜索/设备感知；位置与周边不含看世界。" +
                            "桌宠页只做体验资源与下载，本地脑路径不在此双份编辑。",
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

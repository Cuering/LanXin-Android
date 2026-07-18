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

package com.lanxin.android.builtin.platform.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SceneSensingScreen(
    onBackAction: () -> Unit,
    viewModel: SceneSensingViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val takePicture = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.onPreviewCaptured(bitmap, cameraGranted = granted)
    }

    val requestCamera = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            takePicture.launch(null)
        } else {
            viewModel.onPreviewCaptured(null, cameraGranted = false)
        }
    }

    fun startCapture() {
        // 关/未同意绝不拉起相机（隐私 Gate）
        if (!state.enabled || !state.consentGranted) {
            viewModel.onPreviewCaptured(null, cameraGranted = false)
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            takePicture.launch(null)
        } else {
            requestCamera.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    if (state.showConsentDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissConsentDialog,
            title = { Text("开启场景识别？") },
            text = {
                Text(
                    "将使用相机拍摄一张预览图，在本机用颜色启发式判断明亮/夜色/暖色等，" +
                        "并映射到现有陪伴背景与可选 mood 提示。\n\n" +
                        "· 默认关闭，仅在你点「识别当前场景」时拍照\n" +
                        "· 不上传原图、不后台偷拍、不持续预览\n" +
                        "· 可随时关闭 / 撤回同意 / 清除最近结果\n" +
                        "· 不发明不存在的 Live2D 资源"
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmConsentAndEnable) {
                    Text("同意并开启")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissConsentDialog) {
                    Text("取消")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("场景识别") },
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
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "摄像头 → 场景 · 本地优先",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "默认关闭。开启后可点「识别当前场景」：系统相机预览快照 → " +
                            "本地颜色启发式 → 映射现有陪伴背景（晴空/夜色/晚霞/薄荷绿/薰衣紫）" +
                            "与可选 mood 提示（smile/idle/joy/think）。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "隐私：不上传原图；可关可清；不后台偷拍。首次开启需明确确认。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "启用场景识别",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = when {
                                    state.enabled && state.consentGranted ->
                                        "已开 · 可手动识别"
                                    state.consentGranted ->
                                        "已同意但关闭 · 不拍照"
                                    else ->
                                        "已关 · 首次开需确认"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.enabled,
                            onCheckedChange = viewModel::onEnabledToggle
                        )
                    }
                }
            }

            if (state.consentGranted) {
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "操作",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Button(
                            onClick = { startCapture() },
                            enabled = state.enabled && !state.busy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (state.busy) "识别中…" else "识别当前场景")
                        }
                        OutlinedButton(
                            onClick = viewModel::clearLastScene,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("清除最近场景")
                        }
                        OutlinedButton(
                            onClick = viewModel::revokeConsent,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("撤回同意并关闭")
                        }
                    }
                }
            }

            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "最近结果（仅本机）",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (state.lastStatusText.isBlank()) {
                            "暂无 · 开启后点「识别当前场景」"
                        } else {
                            buildString {
                                append(state.lastStatusText)
                                if (state.lastSceneId.isNotBlank()) {
                                    append("\nscene=").append(state.lastSceneId)
                                }
                                if (state.lastBackgroundId.isNotBlank()) {
                                    append("\nbg=").append(state.lastBackgroundId)
                                }
                                if (state.lastMoodHint.isNotBlank()) {
                                    append("\nmood=").append(state.lastMoodHint)
                                }
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "映射表（仅现有资源）",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "· daylight → bg=sky · mood=smile\n" +
                            "· night → bg=night · mood=idle\n" +
                            "· sunset_warm → bg=sunset · mood=joy\n" +
                            "· green_nature → bg=mint · mood=smile\n" +
                            "· cool_indoor → bg=lavender · mood=think\n" +
                            "· unknown → 不改背景",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

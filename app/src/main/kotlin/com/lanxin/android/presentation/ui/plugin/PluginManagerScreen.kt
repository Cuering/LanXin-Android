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

package com.lanxin.android.presentation.ui.plugin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lanxin.android.plugin.dynamic.PluginLoadResult
import com.lanxin.android.plugin.dynamic.PluginRecord
import com.lanxin.android.plugin.dynamic.PluginSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginManagerScreen(
    onBackAction: () -> Unit,
    onNavigateToMarket: () -> Unit = {},
    viewModel: PluginManagerViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("插件管理") },
                navigationIcon = {
                    IconButton(onClick = onBackAction) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refresh(scanDynamic = false) },
                        enabled = !state.isLoading
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新列表")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (state.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    HeaderCard(
                        packagesPath = state.packagesPath,
                        recordCount = state.records.size,
                        onScan = { viewModel.refresh(scanDynamic = true) },
                        scanEnabled = !state.isLoading,
                        onOpenMarket = onNavigateToMarket
                    )
                }

                if (state.failures.isNotEmpty()) {
                    item {
                        Text(
                            text = "最近加载失败（${state.failures.size}）",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    items(
                        items = state.failures,
                        key = { f -> "${f.apkPath}|${f.pluginId}|${f.reason}" }
                    ) { failure ->
                        FailureCard(failure)
                    }
                }

                item {
                    Text(
                        text = "已注册插件",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                if (state.records.isEmpty()) {
                    item {
                        Text(
                            text = "暂无插件记录。可将 .apk 放入 plugin-packages 后点「重新扫描」。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(state.records, key = { it.id }) { record ->
                        PluginRecordCard(
                            record = record,
                            onEnabledChange = { enabled ->
                                viewModel.setEnabled(record.id, enabled)
                            },
                            onUnload = { viewModel.requestUnload(record.id) },
                            onDeleteApk = { viewModel.requestDeleteApk(record.id) }
                        )
                    }
                }

                item {
                    Text(
                        text = "说明：签名校验 MVP 为 AllowAll（5.6 将换实现）。" +
                            "卸载仅移除运行时注册；删除 APK 会从磁盘移除包文件。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }

    state.unloadConfirmId?.let { id ->
        AlertDialog(
            onDismissRequest = viewModel::cancelUnload,
            title = { Text("卸载插件") },
            text = {
                Text("确定卸载动态插件 $id？\nAPK 文件仍保留在 plugin-packages，可重新扫描加载。")
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmUnload) {
                    Text("卸载")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelUnload) {
                    Text("取消")
                }
            }
        )
    }

    state.deleteApkConfirmId?.let { id ->
        AlertDialog(
            onDismissRequest = viewModel::cancelDeleteApk,
            title = { Text("删除插件包") },
            text = {
                Text("确定卸载 $id 并删除 APK 文件？此操作不可撤销。")
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDeleteApk) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelDeleteApk) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun HeaderCard(
    packagesPath: String,
    recordCount: Int,
    onScan: () -> Unit,
    scanEnabled: Boolean,
    onOpenMarket: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "共 $recordCount 个插件",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (packagesPath.isBlank()) {
                    "动态包目录：filesDir/plugin-packages/"
                } else {
                    "动态包目录：$packagesPath"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = onScan, enabled = scanEnabled) {
                    Text("重新扫描动态插件")
                }
                TextButton(onClick = onOpenMarket, enabled = scanEnabled) {
                    Text("插件市场")
                }
            }
        }
    }
}

@Composable
private fun FailureCard(failure: PluginLoadResult.Failure) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = failure.pluginId ?: failure.apkPath ?: "未知包",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = failure.reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!failure.apkPath.isNullOrBlank() && failure.pluginId != null) {
                Text(
                    text = failure.apkPath,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PluginRecordCard(
    record: PluginRecord,
    onEnabledChange: (Boolean) -> Unit,
    onUnload: () -> Unit,
    onDeleteApk: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = record.name.ifBlank { record.id },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = record.id,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Switch(
                    checked = record.enabled,
                    onCheckedChange = onEnabledChange
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = false,
                    onClick = {},
                    enabled = false,
                    label = {
                        Text(
                            when (record.source) {
                                PluginSource.COMPILED -> "编译期"
                                PluginSource.DYNAMIC -> "动态"
                            }
                        )
                    }
                )
                Text(
                    text = "v${record.version}",
                    style = MaterialTheme.typography.labelMedium
                )
                if (record.author.isNotBlank()) {
                    Text(
                        text = record.author,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (record.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = record.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (record.source == PluginSource.DYNAMIC) {
                if (!record.apkPath.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = record.apkPath,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (record.removable) {
                        TextButton(onClick = onUnload) {
                            Text("卸载")
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        TextButton(onClick = onDeleteApk) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                            Text("删除 APK")
                        }
                    }
                }
            }
        }
    }
}

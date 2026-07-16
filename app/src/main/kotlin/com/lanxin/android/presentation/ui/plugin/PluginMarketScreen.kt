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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import com.lanxin.android.plugin.market.InstallPhase
import com.lanxin.android.plugin.market.MarketInstallStatus
import com.lanxin.android.plugin.market.MarketPluginEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginMarketScreen(
    onBackAction: () -> Unit,
    viewModel: PluginMarketViewModel = hiltViewModel()
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
                title = { Text("插件市场") },
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
                        onClick = viewModel::openUrlDialog,
                        enabled = state.isInstallingId == null
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = "索引 URL")
                    }
                    IconButton(
                        onClick = viewModel::refresh,
                        enabled = !state.isLoading && state.isInstallingId == null
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
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
            if (state.isLoading || state.isInstallingId != null) {
                LinearProgressIndicator(
                    progress = {
                        if (state.isInstallingId != null) {
                            state.installProgress.coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = viewModel::onQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("搜索插件") },
                        placeholder = { Text("id / 名称 / 作者") }
                    )
                }

                item {
                    Text(
                        text = state.sourceHint.ifBlank { "索引：${state.catalogUrl}" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (state.items.isEmpty() && !state.isLoading) {
                    item {
                        Text(
                            text = "暂无市场条目。可配置索引 URL 或使用内置 sample 回退。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(state.items, key = { it.entry.id }) { item ->
                        MarketPluginCard(
                            item = item,
                            isInstalling = state.isInstallingId == item.entry.id,
                            installPhase = if (state.isInstallingId == item.entry.id) {
                                state.installPhase
                            } else {
                                InstallPhase.IDLE
                            },
                            installProgress = if (state.isInstallingId == item.entry.id) {
                                state.installProgress
                            } else {
                                0f
                            },
                            installEnabled = state.isInstallingId == null && !state.isLoading,
                            onInstall = { viewModel.install(item.entry) }
                        )
                    }
                }

                item {
                    Text(
                        text = "说明：下载落入 filesDir/plugin-packages/，随后走 5.3 动态加载。" +
                            "签名校验 MVP 为 AllowAll（5.6）。默认索引见文档。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }

    if (state.showUrlDialog) {
        AlertDialog(
            onDismissRequest = viewModel::cancelUrlDialog,
            title = { Text("市场索引 URL") },
            text = {
                Column {
                    Text(
                        text = "留空则恢复默认 sample 索引。",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.draftCatalogUrl,
                        onValueChange = viewModel::onDraftUrlChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Catalog URL") }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::saveCatalogUrl) {
                    Text("保存并刷新")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelUrlDialog) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun MarketPluginCard(
    item: MarketListItem,
    isInstalling: Boolean,
    installPhase: InstallPhase,
    installProgress: Float,
    installEnabled: Boolean,
    onInstall: () -> Unit
) {
    val entry = item.entry
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
                        text = entry.name.ifBlank { entry.id },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = entry.id,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                FilterChip(
                    selected = false,
                    onClick = {},
                    enabled = false,
                    label = {
                        Text(
                            when (item.installStatus) {
                                MarketInstallStatus.NOT_INSTALLED -> "未安装"
                                MarketInstallStatus.INSTALLED -> "已安装"
                                MarketInstallStatus.UPDATE_AVAILABLE -> "可更新"
                            }
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "v${entry.version}",
                    style = MaterialTheme.typography.labelMedium
                )
                if (!item.localVersion.isNullOrBlank()) {
                    Text(
                        text = "本地 ${item.localVersion}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (entry.author.isNotBlank()) {
                    Text(
                        text = entry.author,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (entry.size > 0L) {
                    Text(
                        text = formatSize(entry.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (entry.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = entry.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isInstalling) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = phaseLabel(installPhase),
                    style = MaterialTheme.typography.labelMedium
                )
                LinearProgressIndicator(
                    progress = { installProgress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = onInstall,
                    enabled = installEnabled
                ) {
                    Text(
                        when (item.installStatus) {
                            MarketInstallStatus.NOT_INSTALLED -> "安装"
                            MarketInstallStatus.INSTALLED -> "重新安装"
                            MarketInstallStatus.UPDATE_AVAILABLE -> "更新"
                        }
                    )
                }
            }
        }
    }
}

private fun phaseLabel(phase: InstallPhase): String = when (phase) {
    InstallPhase.IDLE -> ""
    InstallPhase.DOWNLOADING -> "下载中…"
    InstallPhase.VERIFYING -> "校验中…"
    InstallPhase.INSTALLING -> "安装中…"
    InstallPhase.LOADING -> "加载中…"
    InstallPhase.DONE -> "完成"
    InstallPhase.FAILED -> "失败"
}

private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "${bytes}B"
    if (bytes < 1024 * 1024) return "${bytes / 1024}KB"
    return "${bytes / (1024 * 1024)}MB"
}

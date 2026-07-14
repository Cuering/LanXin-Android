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

package com.lanxin.android.builtin.statistics.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lanxin.android.builtin.statistics.domain.DailyStat
import com.lanxin.android.builtin.statistics.domain.ProviderTokenTotal
import com.lanxin.android.builtin.statistics.domain.StatisticsSummary
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StatisticsScreen(
    onBackAction: () -> Unit,
    viewModel: StatisticsViewModel = hiltViewModel()
) {
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val rangeDays by viewModel.rangeDays.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()
    val clearConfirm by viewModel.clearConfirm.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("数据统计") },
                navigationIcon = {
                    IconButton(onClick = onBackAction) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                    IconButton(onClick = viewModel::requestClear) {
                        Icon(Icons.Filled.Delete, contentDescription = "清空")
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
            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "回溯范围",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1, 3, 7, 30).forEach { days ->
                            FilterChip(
                                selected = rangeDays == days,
                                onClick = { viewModel.setRangeDays(days) },
                                label = { Text("${days}天") }
                            )
                        }
                    }
                }

                item {
                    OverviewCards(summary)
                }

                item {
                    Text(
                        text = "按提供商",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                if (summary.byProvider.isEmpty()) {
                    item {
                        Text(
                            text = "暂无提供商统计",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(summary.byProvider, key = { it.providerId }) { item ->
                        ProviderRow(item)
                    }
                }

                item {
                    Text(
                        text = "按日明细",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                if (summary.daily.isEmpty()) {
                    item {
                        Text(
                            text = "暂无按日数据",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(summary.daily, key = { it.day }) { day ->
                        DailyRow(day)
                    }
                }

                item {
                    Text(
                        text = "说明：token 为本地估算（中文约 1 字/token，英文约 4 字符/token），非 API 精确 usage。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (clearConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::cancelClear,
            title = { Text("清空统计") },
            text = { Text("确定清空全部本地统计数据？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmClear) {
                    Text("清空")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelClear) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun OverviewCards(summary: StatisticsSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(
                title = "总消息",
                value = summary.totalMessages.toString(),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "总调用",
                value = summary.totalCalls.toString(),
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(
                title = "总 Token",
                value = formatNumber(summary.totalTokens),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "成功率",
                value = String.format(Locale.US, "%.0f%%", summary.successRate * 100f),
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(
                title = "今日消息",
                value = summary.todayMessages.toString(),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "今日 Token",
                value = formatNumber(summary.todayTokens),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ProviderRow(item: ProviderTokenTotal) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.providerId, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${item.calls} 次调用",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                formatNumber(item.tokens),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun DailyRow(day: DailyStat) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(day.day, style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "消息 ${day.messageCount} · 调用 ${day.callCount} · Token ${formatNumber(day.tokenTotal)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatNumber(value: Int): String {
    return when {
        value >= 1_000_000 -> String.format(Locale.US, "%.1fM", value / 1_000_000f)
        value >= 1_000 -> String.format(Locale.US, "%.1fK", value / 1_000f)
        else -> value.toString()
    }
}

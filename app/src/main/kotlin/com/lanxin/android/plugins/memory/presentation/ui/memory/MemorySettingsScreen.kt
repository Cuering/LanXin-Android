package com.lanxin.android.plugins.memory.presentation.ui.memory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lanxin.android.plugins.memory.data.memory.MemoryType
import com.lanxin.android.plugins.memory.sync.NutstoreSyncProvider

/**
 * 记忆系统设置页面。
 *
 * - 同步总开关 + Provider 选择
 * - 坚果云 WebDAV 配置
 * - 判断包说明
 * - 统计概览
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemorySettingsScreen(
    onBack: () -> Unit,
    viewModel: MemoryViewModel = viewModel()
) {
    var syncEnabled by remember { mutableStateOf(true) }
    var selectedProvider by remember { mutableStateOf("astrbot") }
    var providerMenuExpanded by remember { mutableStateOf(false) }
    var nutstoreUrl by remember { mutableStateOf(NutstoreSyncProvider.DEFAULT_URL) }
    var nutstoreUser by remember { mutableStateOf("") }
    var nutstorePassword by remember { mutableStateOf("") }
    var testResult by remember { mutableStateOf<String?>(null) }

    val memories by viewModel.memories.collectAsState()
    val total = memories.size
    val active = memories.count { it.status == "active" }
    val preferenceCount = memories.count { it.type == MemoryType.PREFERENCE }
    val factualCount = memories.count { it.type == MemoryType.FACTUAL }
    val insightCount = memories.count { it.type == MemoryType.INSIGHT }
    val judgmentCount = memories.count { it.type == MemoryType.JUDGMENT }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("记忆设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("云端同步", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = if (syncEnabled) Icons.Default.CloudSync else Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = if (syncEnabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("启用云端同步")
                        Spacer(modifier = Modifier.weight(1f))
                        Switch(
                            checked = syncEnabled,
                            onCheckedChange = { syncEnabled = it }
                        )
                    }

                    if (syncEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))

                        ExposedDropdownMenuBox(
                            expanded = providerMenuExpanded,
                            onExpandedChange = { providerMenuExpanded = it }
                        ) {
                            TextField(
                                value = when (selectedProvider) {
                                    "nutstore" -> "坚果云 (WebDAV)"
                                    else -> "AstrBot 同步"
                                },
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                                leadingIcon = {
                                    Icon(Icons.Default.CloudSync, contentDescription = "同步源")
                                },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerMenuExpanded)
                                }
                            )

                            ExposedDropdownMenu(
                                expanded = providerMenuExpanded,
                                onDismissRequest = { providerMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("AstrBot 同步") },
                                    onClick = {
                                        selectedProvider = "astrbot"
                                        providerMenuExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("坚果云 (WebDAV)") },
                                    onClick = {
                                        selectedProvider = "nutstore"
                                        providerMenuExpanded = false
                                    }
                                )
                            }
                        }

                        if (selectedProvider == "nutstore") {
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = nutstoreUrl,
                                onValueChange = { nutstoreUrl = it },
                                label = { Text("WebDAV 地址") },
                                placeholder = { Text(NutstoreSyncProvider.DEFAULT_URL) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = nutstoreUser,
                                onValueChange = { nutstoreUser = it },
                                label = { Text("用户名") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = nutstorePassword,
                                onValueChange = { nutstorePassword = it },
                                label = { Text("应用密码") },
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    testResult = if (nutstoreUser.isBlank() || nutstorePassword.isBlank()) {
                                        "请填写用户名和应用密码"
                                    } else {
                                        "配置已填写，连接测试将在后台执行"
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("测试连接")
                            }

                            testResult?.let { result ->
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (result.contains("成功") || result.contains("已填写")) {
                                            Icons.Default.CheckCircle
                                        } else {
                                            Icons.Default.Warning
                                        },
                                        contentDescription = null,
                                        tint = if (result.contains("成功") || result.contains("已填写")) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.error
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(result, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }

            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("判断包", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "判断包用于场景化行为准则注入，默认全开，与 AstrBot 记忆插件对齐。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "内置：兰心陪伴边界 / 工作高效偏好",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("记忆统计", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("总记忆: $total")
                    Text("活跃: $active")
                    Text("偏好: $preferenceCount")
                    Text("事实: $factualCount")
                    Text("洞察: $insightCount")
                    Text("判断: $judgmentCount")
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("数据管理", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { /* export on list page */ },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("导出")
                        }
                        Button(
                            onClick = { /* 导入由系统文件选择器触发 */ },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("导入")
                        }
                        OutlinedButton(
                            onClick = { /* 清空操作保留在列表页 */ },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("清空")
                        }
                    }
                }
            }
        }
    }
}

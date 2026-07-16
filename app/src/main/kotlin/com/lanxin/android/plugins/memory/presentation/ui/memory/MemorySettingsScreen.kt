package com.lanxin.android.plugins.memory.presentation.ui.memory

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lanxin.android.plugins.memory.data.memory.MemoryType

/**
 * 记忆系统设置页面。
 *
 * 包含：
 * - 同步总开关 + Provider 选择
 * - 坚果云 WebDAV 配置
 * - 判断包管理入口
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
    var nutstoreUrl by remember { mutableStateOf("") }
    var nutstoreUser by remember { mutableStateOf("") }
    var nutstorePassword by remember { mutableStateOf("") }
    var testResult by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("记忆设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
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
            // === 同步设置 ===
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("云端同步", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            if (syncEnabled) Icons.Default.CloudSync else Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = if (syncEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
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

                        // Provider 选择
                        ExposedDropdownMenuBox(
                            expanded = false,
                            onExpandedChange = {}
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
                                    Icon(Icons.Default.CloudSync, "同步源")
                                },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = false)
                                }
                            )

                            ExposedDropdownMenu(
                                expanded = false,
                                onDismissRequest = {}
                            ) {
                                DropdownMenuItem(
                                    text = { Text("AstrBot 同步") },
                                    onClick = { selectedProvider = "astrbot" }
                                )
                                DropdownMenuItem(
                                    text = { Text("坚果云 (WebDAV)") },
                                    onClick = { selectedProvider = "nutstore" }
                                )
                            }
                        }

                        // 坚果云配置
                        if (selectedProvider == "nutstore") {
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = nutstoreUrl,
                                onValueChange = { nutstoreUrl = it },
                                label = { Text("WebDAV 地址") },
                                placeholder = { Text("https://dav.jianguoyun.com/dav/lanxin/") },
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
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    // 测试连接
                                    testResult = "测试中..."
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("测试连接")
                            }

                            testResult?.let {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (it.contains("成功")) Icons.Default.CheckCircle
                                        else Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = if (it.contains("成功"))
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(it, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }

            // === 判断包管理 ===
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("判断包管理", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("判断包用于场景化行为准则注入", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { /* TODO: 打开判断包管理页 */ },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("管理判断包")
                    }
                }
            }

            // === 统计概览 ===
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("记忆统计", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    viewModel.stats.collect { stats ->
                        stats?.let { s ->
                            Text("总记忆: ${s["total"] ?: 0}")
                            Text("活跃: ${s["active"] ?: 0}")
                            Text("偏好: ${s[MemoryType.PREFERENCE] ?: 0}")
                            Text("事实: ${s[MemoryType.FACTUAL] ?: 0}")
                            Text("洞察: ${s[MemoryType.INSIGHT] ?: 0}")
                            Text("判断: ${s[MemoryType.JUDGMENT] ?: 0}")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // === 数据管理 ===
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("数据管理", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { /* TODO: 导出 */ },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("导出")
                        }
                        Button(
                            onClick = { /* TODO: 导入 */ },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("导入")
                        }
                        OutlinedButton(
                            onClick = { /* TODO: 清空 */ },
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

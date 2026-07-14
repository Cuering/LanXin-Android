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

package com.lanxin.android.builtin.knowledge.presentation

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.lanxin.android.builtin.knowledge.domain.DocumentTypes
import com.lanxin.android.builtin.knowledge.domain.ImportPhase
import com.lanxin.android.builtin.knowledge.domain.ImportProgress
import com.lanxin.android.builtin.knowledge.domain.TextChunker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgeScreen(
    onBackAction: () -> Unit,
    viewModel: KnowledgeViewModel = hiltViewModel()
) {
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val vectorCount by viewModel.vectorCount.collectAsStateWithLifecycle()
    val embeddingReady by viewModel.embeddingReady.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()
    val clearConfirm by viewModel.clearConfirm.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        viewModel.importFromUri(uri)
    }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("知识库") },
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
                        onClick = viewModel::refreshStatus,
                        enabled = !progress.isRunning
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                    IconButton(
                        onClick = viewModel::requestClear,
                        enabled = !progress.isRunning && vectorCount > 0
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = "清空")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                StatusCard(
                    vectorCount = vectorCount,
                    embeddingReady = embeddingReady
                )
            }

            item {
                ImportCard(
                    progress = progress,
                    enabled = !progress.isRunning,
                    onImportClick = {
                        openDocumentLauncher.launch(DocumentTypes.MIME_TYPES)
                    },
                    onReset = viewModel::resetProgress
                )
            }

            item {
                HelpCard()
            }
        }
    }

    if (clearConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::cancelClear,
            title = { Text("清空知识库？") },
            text = {
                Text("将删除全部向量条目（含记忆侧索引），此操作不可撤销。")
            },
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
private fun StatusCard(
    vectorCount: Long,
    embeddingReady: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            Modifier.padding(16.dp)
        ) {
            Text(
                text = "管道状态",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("嵌入模型：${if (embeddingReady) "就绪" else "未就绪 / 预热中"}")
            Text("向量条目：$vectorCount")
            Text(
                text = "分段：窗口 ${TextChunker.DEFAULT_WINDOW} / 重叠 ${TextChunker.DEFAULT_OVERLAP}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ImportCard(
    progress: ImportProgress,
    enabled: Boolean,
    onImportClick: () -> Unit,
    onReset: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(16.dp)
        ) {
            Text(
                text = "导入文档",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "支持 .txt / .md / .pdf，自动分段并向量化入库",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onImportClick,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Filled.UploadFile,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(if (progress.isRunning) "导入中…" else "选择文件导入")
            }

            if (progress.phase != ImportPhase.IDLE) {
                Spacer(modifier = Modifier.height(16.dp))
                ProgressSection(progress)
                if (progress.isTerminal) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onReset,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("清除进度")
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressSection(progress: ImportProgress) {
    val phaseLabel = when (progress.phase) {
        ImportPhase.READING -> "读取"
        ImportPhase.PARSING -> "解析"
        ImportPhase.CHUNKING -> "分段"
        ImportPhase.EMBEDDING -> "向量化"
        ImportPhase.DONE -> "完成"
        ImportPhase.FAILED -> "失败"
        ImportPhase.IDLE -> ""
    }

    Text(
        text = "$phaseLabel · ${progress.message}",
        style = MaterialTheme.typography.bodyMedium
    )
    if (progress.fileName.isNotBlank()) {
        Text(
            text = progress.fileName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Spacer(modifier = Modifier.height(8.dp))

    // 与 UpdateDialogs / SetupPlatformWizard 保持一致的 progress lambda API
    val fraction = if (progress.phase == ImportPhase.FAILED) {
        0f
    } else {
        progress.fraction.coerceIn(0f, 1f)
    }
    LinearProgressIndicator(
        progress = { fraction },
        modifier = Modifier.fillMaxWidth()
    )

    if (progress.totalChunks > 0) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "分段 ${progress.doneChunks}/${progress.totalChunks}",
                style = MaterialTheme.typography.bodySmall
            )
            if (progress.charCount > 0) {
                Text(
                    text = "${progress.charCount} 字",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    if (progress.phase == ImportPhase.DONE) {
        Spacer(modifier = Modifier.height(8.dp))
        val failPart =
            if (progress.failedCount > 0) " · 失败 ${progress.failedCount}" else ""
        Text(
            text = "成功 ${progress.successCount} 段$failPart · 耗时 ${formatElapsed(progress.elapsedMs)}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun HelpCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            Modifier.padding(16.dp)
        ) {
            Text(
                text = "说明",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "• 文本按约 512 token 窗口、50 token 重叠切分\n" +
                    "• 同一文件再次导入会覆盖对应条目\n" +
                    "• PDF 依赖内置启发式提取，扫描件可能失败\n" +
                    "• 入库后可通过语义检索（kb_search）调用",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatElapsed(ms: Long): String {
    if (ms <= 0) return "—"
    return if (ms < 1000) "${ms}ms" else String.format("%.1fs", ms / 1000.0)
}

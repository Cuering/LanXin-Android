package com.lanxin.android.plugins.memory.presentation.ui.memory

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lanxin.android.plugins.memory.data.memory.MemoryType
import com.lanxin.android.plugins.memory.domain.memory.ImportStrategy
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(
    onBackAction: () -> Unit,
    viewModel: MemoryViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val memories by viewModel.memories.collectAsStateWithLifecycle()
    val selectedType by viewModel.selectedType.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val showAddDialog by viewModel.showAddDialog.collectAsStateWithLifecycle()
    val editingMemory by viewModel.editingMemory.collectAsStateWithLifecycle()
    val deleteConfirmId by viewModel.deleteConfirmId.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()
    val isExporting by viewModel.isExporting.collectAsStateWithLifecycle()
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()
    val showImportStrategyDialog by viewModel.showImportStrategyDialog.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // SAF: ACTION_CREATE_DOCUMENT — 用户选择导出保存位置
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.exportMemoriesToUri(context, uri)
        }
    }

    // SAF: ACTION_OPEN_DOCUMENT — 用户选择导入文件
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        viewModel.onImportFileSelected(uri)
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
                title = { Text("📚 记忆") },
                navigationIcon = {
                    IconButton(onClick = onBackAction) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    // 📤 导出
                    IconButton(
                        onClick = {
                            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                                .format(Date())
                            createDocumentLauncher.launch("lanxin_memories_$stamp.json")
                        },
                        enabled = !isExporting && !isImporting
                    ) {
                        if (isExporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Filled.FileUpload,
                                contentDescription = "导出记忆 📤"
                            )
                        }
                    }
                    // 📥 导入
                    IconButton(
                        onClick = {
                            openDocumentLauncher.launch(
                                arrayOf("application/json", "text/*", "*/*")
                            )
                        },
                        enabled = !isExporting && !isImporting
                    ) {
                        if (isImporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Filled.FileDownload,
                                contentDescription = "导入记忆 📥"
                            )
                        }
                    }
                    IconButton(
                        onClick = { viewModel.openAddDialog() },
                        enabled = !isExporting && !isImporting
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "添加记忆")
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
            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::search,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("搜索记忆…") },
                leadingIcon = {
                    Icon(Icons.Rounded.Search, contentDescription = null)
                },
                singleLine = true
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedType == null,
                    onClick = { viewModel.filterByType(null) },
                    label = { Text("全部") }
                )
                MemoryType.ALL.forEach { type ->
                    val color = memoryTypeColor(type)
                    FilterChip(
                        selected = selectedType == type,
                        onClick = {
                            viewModel.filterByType(
                                if (selectedType == type) null else type
                            )
                        },
                        label = { Text(MemoryType.displayName(type)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = color.copy(alpha = 0.25f),
                            selectedLabelColor = color
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (memories.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isNotEmpty()) {
                            "没有找到匹配的记忆"
                        } else {
                            "还没有记忆，点 + 添加"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(memories, key = { it.id }) { memory ->
                        MemoryCard(
                            memory = memory,
                            onEdit = { viewModel.openEditDialog(memory) },
                            onDelete = { viewModel.requestDelete(memory.id) }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddMemoryDialog(
            existing = editingMemory,
            onDismiss = { viewModel.closeAddDialog() },
            onConfirm = { content, type, importance ->
                val editing = editingMemory
                if (editing != null) {
                    viewModel.updateMemory(editing, content, type, importance)
                } else {
                    viewModel.addMemory(content, type, importance)
                }
            }
        )
    }

    deleteConfirmId?.let { id ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelDelete() },
            title = { Text("删除记忆") },
            text = { Text("确定要删除这条记忆吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteMemory(id) }) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDelete() }) {
                    Text("取消")
                }
            }
        )
    }

    if (showImportStrategyDialog) {
        ImportStrategyDialog(
            onDismiss = { viewModel.cancelImportStrategy() },
            onConfirm = { strategy ->
                viewModel.confirmImportStrategy(context, strategy)
            }
        )
    }
}

@Composable
private fun ImportStrategyDialog(
    onDismiss: () -> Unit,
    onConfirm: (ImportStrategy) -> Unit
) {
    var selected by remember { mutableStateOf(ImportStrategy.MERGE_DEDUP) }

    val options = listOf(
        ImportStrategy.REPLACE to ("替换全部" to "清空现有记忆后导入"),
        ImportStrategy.MERGE_BY_ID to ("按 ID 合并" to "跳过已存在的 ID，仅新增"),
        ImportStrategy.MERGE_DEDUP to ("去重合并" to "按内容+类型去重后新增")
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择导入策略") },
        text = {
            Column {
                options.forEach { (strategy, labels) ->
                    val (title, subtitle) = labels
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selected == strategy,
                                onClick = { selected = strategy },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected == strategy,
                            onClick = { selected = strategy }
                        )
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text(title, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }) {
                Text("导入")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

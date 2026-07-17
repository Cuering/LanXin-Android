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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lanxin.android.builtin.knowledge.data.AutoKnowledgeSettings
import com.lanxin.android.builtin.knowledge.domain.AutoKnowledgeService
import com.lanxin.android.builtin.knowledge.domain.EmbeddingService
import com.lanxin.android.builtin.knowledge.domain.ImportPhase
import com.lanxin.android.builtin.knowledge.domain.ImportProgress
import com.lanxin.android.builtin.knowledge.domain.KnowledgeImportService
import com.lanxin.android.builtin.knowledge.domain.VectorPipeline
import com.lanxin.android.builtin.knowledge.domain.VectorStore
import com.lanxin.android.plugins.memory.data.memory.MemoryEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class KnowledgeViewModel @Inject constructor(
    private val importService: KnowledgeImportService,
    private val vectorStore: VectorStore,
    private val embeddingService: EmbeddingService,
    private val pipeline: VectorPipeline,
    private val autoKnowledgeSettings: AutoKnowledgeSettings,
    private val autoKnowledgeService: AutoKnowledgeService
) : ViewModel() {

    private val _progress = MutableStateFlow(ImportProgress())
    val progress: StateFlow<ImportProgress> = _progress.asStateFlow()

    private val _vectorCount = MutableStateFlow(0L)
    val vectorCount: StateFlow<Long> = _vectorCount.asStateFlow()

    private val _embeddingReady = MutableStateFlow(false)
    val embeddingReady: StateFlow<Boolean> = _embeddingReady.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _clearConfirm = MutableStateFlow(false)
    val clearConfirm: StateFlow<Boolean> = _clearConfirm.asStateFlow()

    val autoKnowledgeEnabled: StateFlow<Boolean> = autoKnowledgeSettings.enabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val historyWindowSize: StateFlow<Int> = autoKnowledgeSettings.historyWindowSizeFlow
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            AutoKnowledgeSettings.DEFAULT_WINDOW
        )

    private val _autoKnowledgeItems = MutableStateFlow<List<MemoryEntity>>(emptyList())
    val autoKnowledgeItems: StateFlow<List<MemoryEntity>> = _autoKnowledgeItems.asStateFlow()

    private val _showAutoList = MutableStateFlow(false)
    val showAutoList: StateFlow<Boolean> = _showAutoList.asStateFlow()

    private val _clearAutoConfirm = MutableStateFlow(false)
    val clearAutoConfirm: StateFlow<Boolean> = _clearAutoConfirm.asStateFlow()

    private var importJob: Job? = null

    init {
        refreshStatus()
    }

    fun refreshStatus() {
        viewModelScope.launch {
            runCatching { pipeline.warmUp() }
            _embeddingReady.value = embeddingService.isReady
            _vectorCount.value = runCatching { vectorStore.count() }.getOrDefault(0L)
            if (_showAutoList.value) {
                refreshAutoList()
            }
        }
    }

    fun setAutoKnowledgeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            autoKnowledgeSettings.setEnabled(enabled)
        }
    }

    fun setHistoryWindowSize(size: Int) {
        viewModelScope.launch {
            autoKnowledgeSettings.setHistoryWindowSize(size)
        }
    }

    fun showAutoKnowledgeList() {
        _showAutoList.value = true
        viewModelScope.launch { refreshAutoList() }
    }

    fun hideAutoKnowledgeList() {
        _showAutoList.value = false
    }

    fun requestClearAuto() {
        _clearAutoConfirm.value = true
    }

    fun cancelClearAuto() {
        _clearAutoConfirm.value = false
    }

    fun confirmClearAuto() {
        viewModelScope.launch {
            _clearAutoConfirm.value = false
            runCatching {
                autoKnowledgeService.clearStored()
            }.onSuccess { n ->
                _autoKnowledgeItems.value = emptyList()
                _vectorCount.value = runCatching { vectorStore.count() }.getOrDefault(0L)
                _snackbarMessage.value = "已清空自动知识（$n 条）"
            }.onFailure {
                _snackbarMessage.value = "清空自动知识失败：${it.message ?: "未知错误"}"
            }
        }
    }

    private suspend fun refreshAutoList() {
        _autoKnowledgeItems.value = runCatching {
            autoKnowledgeService.listStored()
        }.getOrDefault(emptyList())
    }

    fun importFromUri(uri: Uri?) {
        if (uri == null) {
            _snackbarMessage.value = "未选择文件"
            return
        }
        startImport(importService.importDocument(uri), singleFileLabel = true)
    }

    /** SAF 选择知识库文件夹（OpenDocumentTree），批量导入支持的文档。 */
    fun importFromTree(uri: Uri?) {
        if (uri == null) {
            _snackbarMessage.value = "未选择文件夹"
            return
        }
        startImport(importService.importFolder(uri), singleFileLabel = false)
    }

    private fun startImport(
        flow: Flow<ImportProgress>,
        singleFileLabel: Boolean
    ) {
        if (_progress.value.isRunning) {
            _snackbarMessage.value = "正在导入中，请稍候"
            return
        }
        importJob?.cancel()
        importJob = viewModelScope.launch {
            flow.collect { p ->
                _progress.value = p
                if (p.phase == ImportPhase.DONE) {
                    _vectorCount.value =
                        runCatching { vectorStore.count() }.getOrDefault(p.storeCount)
                    _snackbarMessage.value = if (singleFileLabel && !p.batchMode) {
                        "导入完成：${p.successCount} 段，耗时 ${formatMs(p.elapsedMs)}"
                    } else {
                        "文件夹导入完成：${p.batchSuccess} 个文件 · ${p.successCount} 段 · " +
                            formatMs(p.elapsedMs)
                    }
                } else if (p.phase == ImportPhase.FAILED) {
                    _snackbarMessage.value = "导入失败：${p.message}"
                }
            }
            _embeddingReady.value = embeddingService.isReady
        }
    }

    fun resetProgress() {
        if (_progress.value.isRunning) return
        _progress.value = ImportProgress()
    }

    fun requestClear() {
        _clearConfirm.value = true
    }

    fun cancelClear() {
        _clearConfirm.value = false
    }

    fun confirmClear() {
        viewModelScope.launch {
            _clearConfirm.value = false
            runCatching {
                vectorStore.clear()
            }.onSuccess {
                _vectorCount.value = 0L
                _progress.value = ImportProgress()
                _snackbarMessage.value = "知识库向量已清空"
            }.onFailure {
                _snackbarMessage.value = "清空失败：${it.message ?: "未知错误"}"
            }
        }
    }

    fun clearSnackbar() {
        _snackbarMessage.update { null }
    }

    private fun formatMs(ms: Long): String {
        return if (ms < 1000) "${ms}ms" else String.format("%.1fs", ms / 1000.0)
    }
}

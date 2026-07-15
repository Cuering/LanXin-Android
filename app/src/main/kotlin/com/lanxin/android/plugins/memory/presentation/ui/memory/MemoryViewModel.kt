package com.lanxin.android.plugins.memory.presentation.ui.memory

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lanxin.android.plugins.memory.data.memory.MemoryEntity
import com.lanxin.android.plugins.memory.data.memory.MemoryExportFormat
import com.lanxin.android.plugins.memory.data.memory.MemoryImportResult
import com.lanxin.android.plugins.memory.data.memory.MemoryRepository
import com.lanxin.android.plugins.memory.domain.memory.ImportStrategy
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class MemoryViewModel @Inject constructor(
    private val memoryRepository: MemoryRepository
) : ViewModel() {

    private val _selectedType = MutableStateFlow<String?>(null)
    val selectedType: StateFlow<String?> = _selectedType.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _showAddDialog = MutableStateFlow(false)
    val showAddDialog: StateFlow<Boolean> = _showAddDialog.asStateFlow()

    private val _editingMemory = MutableStateFlow<MemoryEntity?>(null)
    val editingMemory: StateFlow<MemoryEntity?> = _editingMemory.asStateFlow()

    private val _deleteConfirmId = MutableStateFlow<Long?>(null)
    val deleteConfirmId: StateFlow<Long?> = _deleteConfirmId.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _importResult = MutableStateFlow<MemoryImportResult?>(null)
    val importResult: StateFlow<MemoryImportResult?> = _importResult.asStateFlow()

    private val _showImportStrategyDialog = MutableStateFlow(false)
    val showImportStrategyDialog: StateFlow<Boolean> = _showImportStrategyDialog.asStateFlow()

    private val _showExportFormatDialog = MutableStateFlow(false)
    val showExportFormatDialog: StateFlow<Boolean> = _showExportFormatDialog.asStateFlow()

    /** 待 SAF 写入的导出格式；null 表示尚未选择。 */
    private val _pendingExportFormat = MutableStateFlow<MemoryExportFormat?>(null)
    val pendingExportFormat: StateFlow<MemoryExportFormat?> = _pendingExportFormat.asStateFlow()

    private val _pendingImportUri = MutableStateFlow<Uri?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val baseMemories = _selectedType.flatMapLatest { type ->
        if (type == null) memoryRepository.getAllMemories()
        else memoryRepository.getMemoriesByType(type)
    }

    val memories: StateFlow<List<MemoryEntity>> = combine(
        baseMemories,
        _searchQuery
    ) { list, query ->
        if (query.isBlank()) list
        else list.filter { it.content.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun filterByType(type: String?) {
        _selectedType.update { type }
    }

    fun search(query: String) {
        _searchQuery.update { query }
    }

    fun openAddDialog() {
        _editingMemory.update { null }
        _showAddDialog.update { true }
    }

    fun openEditDialog(memory: MemoryEntity) {
        _editingMemory.update { memory }
        _showAddDialog.update { true }
    }

    fun closeAddDialog() {
        _showAddDialog.update { false }
        _editingMemory.update { null }
    }

    fun requestDelete(id: Long) {
        _deleteConfirmId.update { id }
    }

    fun cancelDelete() {
        _deleteConfirmId.update { null }
    }

    fun addMemory(content: String, type: String, importance: Float) {
        viewModelScope.launch {
            _isLoading.update { true }
            try {
                memoryRepository.addMemory(content, type, importance)
                _snackbarMessage.update { "记忆已添加" }
                closeAddDialog()
            } finally {
                _isLoading.update { false }
            }
        }
    }

    fun updateMemory(memory: MemoryEntity, content: String, type: String, importance: Float) {
        viewModelScope.launch {
            _isLoading.update { true }
            try {
                memoryRepository.updateMemory(
                    memory.copy(content = content, type = type, importance = importance)
                )
                _snackbarMessage.update { "记忆已更新" }
                closeAddDialog()
            } finally {
                _isLoading.update { false }
            }
        }
    }

    fun deleteMemory(id: Long) {
        viewModelScope.launch {
            memoryRepository.deleteMemory(id)
            _deleteConfirmId.update { null }
            _snackbarMessage.update { "记忆已删除" }
        }
    }

    fun clearSnackbar() {
        _snackbarMessage.update { null }
    }

    fun clearImportResult() {
        _importResult.update { null }
    }

    /**
     * P4：打开导出格式选择（JSON / Markdown）。
     */
    fun openExportFormatDialog() {
        if (_isExporting.value || _isImporting.value) return
        _showExportFormatDialog.update { true }
    }

    fun cancelExportFormatDialog() {
        _showExportFormatDialog.update { false }
    }

    /**
     * 用户选定格式后，UI 再调起 SAF CreateDocument。
     * @return 建议的文件名后缀（含点）与 MIME
     */
    fun confirmExportFormat(format: MemoryExportFormat): ExportFileSpec {
        _showExportFormatDialog.update { false }
        _pendingExportFormat.update { format }
        return when (format) {
            MemoryExportFormat.JSON -> ExportFileSpec(
                mimeType = "application/json",
                extension = "json"
            )
            MemoryExportFormat.MARKDOWN -> ExportFileSpec(
                mimeType = "text/markdown",
                extension = "md"
            )
        }
    }

    /**
     * 导出全部记忆并调起系统分享（备用路径，默认 JSON）。
     */
    fun exportMemories(context: Context) {
        exportMemories(context, MemoryExportFormat.JSON, typeFilter = null)
    }

    /**
     * P4：导出并分享，支持格式与过滤。
     * typeFilter 默认取当前列表筛选类型（null = 全部）。
     */
    fun exportMemories(
        context: Context,
        format: MemoryExportFormat,
        typeFilter: String? = _selectedType.value
    ) {
        if (_isExporting.value) return
        viewModelScope.launch {
            _isExporting.update { true }
            try {
                val file = memoryRepository.exportSuspend(context, format, typeFilter)
                withContext(Dispatchers.Main) {
                    shareExportFile(context, file, format)
                }
                _snackbarMessage.update { "已导出 ${file.name}" }
            } catch (e: Exception) {
                Log.e(TAG, "exportMemories failed", e)
                _snackbarMessage.update { "导出失败: ${e.message ?: "未知错误"}" }
            } finally {
                _isExporting.update { false }
            }
        }
    }

    /**
     * 将记忆导出到用户通过 SAF 选择的 Uri（默认 JSON，兼容旧调用）。
     */
    fun exportMemoriesToUri(context: Context, uri: Uri) {
        val format = _pendingExportFormat.value ?: MemoryExportFormat.JSON
        exportMemoriesToUri(context, uri, format, typeFilter = _selectedType.value)
    }

    /**
     * P4：导出到 SAF Uri，支持格式与 type 过滤。
     * 过滤默认使用当前列表选中的 type（null = 全部）。
     */
    fun exportMemoriesToUri(
        context: Context,
        uri: Uri,
        format: MemoryExportFormat,
        typeFilter: String? = _selectedType.value
    ) {
        if (_isExporting.value) return
        viewModelScope.launch {
            _isExporting.update { true }
            try {
                val file = memoryRepository.exportSuspend(context, format, typeFilter)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        file.inputStream().use { input -> input.copyTo(out) }
                    } ?: throw IllegalStateException("无法写入所选位置")
                }
                val label = when (format) {
                    MemoryExportFormat.JSON -> "JSON"
                    MemoryExportFormat.MARKDOWN -> "Markdown"
                }
                val filterHint = if (typeFilter.isNullOrBlank()) "全部" else typeFilter
                _snackbarMessage.update { "记忆已导出（$label · $filterHint）" }
            } catch (e: Exception) {
                Log.e(TAG, "exportMemoriesToUri failed", e)
                _snackbarMessage.update { "导出失败: ${e.message ?: "未知错误"}" }
            } finally {
                _pendingExportFormat.update { null }
                _isExporting.update { false }
            }
        }
    }

    /**
     * 用户通过 SAF 选中文件后，先弹出策略选择。
     */
    fun onImportFileSelected(uri: Uri?) {
        if (uri == null) return
        _pendingImportUri.update { uri }
        _showImportStrategyDialog.update { true }
    }

    fun cancelImportStrategy() {
        _showImportStrategyDialog.update { false }
        _pendingImportUri.update { null }
    }

    fun confirmImportStrategy(context: Context, strategy: ImportStrategy) {
        val uri = _pendingImportUri.value
        _showImportStrategyDialog.update { false }
        _pendingImportUri.update { null }
        if (uri == null) return
        importMemories(context, uri, strategy)
    }

    /**
     * 按指定策略从 Uri 导入记忆。
     */
    fun importMemories(context: Context, uri: Uri, strategy: ImportStrategy) {
        if (_isImporting.value) return
        viewModelScope.launch {
            _isImporting.update { true }
            try {
                val result = memoryRepository.importFromJson(context, uri, strategy)
                _importResult.update { result }
                _snackbarMessage.update { result.message }
            } catch (e: Exception) {
                Log.e(TAG, "importMemories failed", e)
                _snackbarMessage.update { "导入失败: ${e.message ?: "未知错误"}" }
            } finally {
                _isImporting.update { false }
            }
        }
    }

    suspend fun getMemoryById(id: Long): MemoryEntity? = memoryRepository.getMemoryById(id)

    private fun shareExportFile(
        context: Context,
        file: File,
        format: MemoryExportFormat
    ) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val mime = when (format) {
            MemoryExportFormat.JSON -> "application/json"
            MemoryExportFormat.MARKDOWN -> "text/markdown"
        }
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "导出记忆").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val resInfo = context.packageManager.queryIntentActivities(
            chooser,
            PackageManager.MATCH_DEFAULT_ONLY
        )
        resInfo.forEach { res ->
            context.grantUriPermission(
                res.activityInfo.packageName,
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        context.startActivity(chooser)
    }

    data class ExportFileSpec(
        val mimeType: String,
        val extension: String
    )

    companion object {
        private const val TAG = "MemoryViewModel"
    }
}

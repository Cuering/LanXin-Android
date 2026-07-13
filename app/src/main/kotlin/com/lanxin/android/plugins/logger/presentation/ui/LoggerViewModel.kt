package com.lanxin.android.plugins.logger.presentation.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lanxin.android.core.log.LogBroker
import com.lanxin.android.core.log.LogEntry
import com.lanxin.android.core.log.LogLevel
import com.lanxin.android.core.log.LogManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class LoggerViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val logManager: LogManager,
    private val logBroker: LogBroker
) : ViewModel() {

    data class UiState(
        val files: List<File> = emptyList(),
        val selectedFile: File? = null,
        val lines: List<String> = emptyList(),
        val filteredLines: List<String> = emptyList(),
        val liveEntries: List<LogEntry> = emptyList(),
        val levelFilter: LogLevel? = null,
        val query: String = "",
        val useLive: Boolean = false,
        val isLoading: Boolean = false,
        val error: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()

    private var liveJob: Job? = null

    init {
        refreshFiles()
        // 默认加载当前日志 + 缓存
        openCurrentOrLatest()
    }

    fun refreshFiles() {
        val files = logManager.listLogFiles()
        _uiState.update { it.copy(files = files) }
    }

    fun openCurrentOrLatest() {
        val current = logManager.getLogDir()?.let { File(it, LogManager.CURRENT_LOG_NAME) }
        val target = when {
            current != null && current.exists() -> current
            else -> logManager.listLogFiles().firstOrNull()
        }
        if (target != null) {
            openFile(target)
        } else {
            // 仅展示内存缓存
            showCache()
        }
    }

    fun openFile(file: File) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(isLoading = true, error = null, selectedFile = file, useLive = false)
            }
            try {
                val lines = withContext(Dispatchers.IO) {
                    if (!file.exists()) emptyList()
                    else file.readLines(Charsets.UTF_8).asReversed() // 最新在上
                }
                _uiState.update {
                    it.copy(lines = lines, isLoading = false).let { s -> applyFilter(s) }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "读取失败")
                }
            }
        }
    }

    fun showCache() {
        val entries = logBroker.snapshot().asReversed()
        _uiState.update {
            applyFilter(
                it.copy(
                    useLive = true,
                    liveEntries = entries,
                    selectedFile = null,
                    lines = emptyList(),
                    isLoading = false
                )
            )
        }
        startLive()
    }

    fun setLevelFilter(level: LogLevel?) {
        _uiState.update { applyFilter(it.copy(levelFilter = level)) }
    }

    fun setQuery(query: String) {
        _uiState.update { applyFilter(it.copy(query = query)) }
    }

    fun exportSelected(onReady: (File) -> Unit) {
        viewModelScope.launch {
            val state = _uiState.value
            val source = state.selectedFile
            val out = withContext(Dispatchers.IO) {
                val stamp = System.currentTimeMillis()
                val dest = File(context.cacheDir, "lanxin_log_export_$stamp.log")
                when {
                    source != null && source.exists() -> {
                        source.copyTo(dest, overwrite = true)
                        dest
                    }
                    else -> {
                        val text = if (state.useLive) {
                            state.liveEntries.joinToString("\n") { it.formatLine() }
                        } else {
                            state.filteredLines.joinToString("\n")
                        }
                        dest.writeText(text, Charsets.UTF_8)
                        dest
                    }
                }
            }
            onReady(out)
        }
    }

    fun shareFile(file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "导出日志").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun startLive() {
        liveJob?.cancel()
        liveJob = viewModelScope.launch {
            logBroker.events.collect { entry ->
                _uiState.update { state ->
                    if (!state.useLive) return@update state
                    val next = (listOf(entry) + state.liveEntries).take(LogBroker.CACHED_SIZE)
                    applyFilter(state.copy(liveEntries = next))
                }
            }
        }
    }

    private fun applyFilter(state: UiState): UiState {
        val query = state.query.trim()
        val level = state.levelFilter
        val filtered = if (state.useLive) {
            state.liveEntries
                .filter { level == null || it.level == level }
                .filter { query.isEmpty() || it.message.contains(query, true) || it.tag.contains(query, true) }
                .map { it.formatLine() }
        } else {
            state.lines.filter { line ->
                val levelOk = level == null || line.contains("[${level.shortName}]")
                val queryOk = query.isEmpty() || line.contains(query, ignoreCase = true)
                levelOk && queryOk
            }
        }
        return state.copy(filteredLines = filtered)
    }

    override fun onCleared() {
        liveJob?.cancel()
        super.onCleared()
    }
}

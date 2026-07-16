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

package com.lanxin.android.builtin.systemtools.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lanxin.android.builtin.systemtools.data.DeviceToolRegistry
import com.lanxin.android.builtin.systemtools.domain.DeviceToolIds
import com.lanxin.android.builtin.systemtools.domain.NotesCodec
import com.lanxin.android.builtin.systemtools.domain.NotesExportFormat
import com.lanxin.android.builtin.systemtools.domain.NotesImportStrategy
import com.lanxin.android.builtin.systemtools.domain.NotesIoResult
import com.lanxin.android.builtin.systemtools.domain.NotesSafGateway
import com.lanxin.android.builtin.systemtools.domain.NotesStore
import com.lanxin.android.builtin.systemtools.domain.SystemToolsConfig
import com.lanxin.android.builtin.systemtools.domain.SystemToolsPermissionChecker
import com.lanxin.android.builtin.systemtools.domain.SystemToolsPermissionStatus
import com.lanxin.android.builtin.systemtools.domain.SystemToolsSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SystemToolsUiState(
    val config: SystemToolsConfig = SystemToolsConfig(),
    val stubToolNames: List<String> = emptyList(),
    val permissions: SystemToolsPermissionStatus = SystemToolsPermissionStatus(),
    val notesCount: Int = 0,
    val notesEnabled: Boolean = false,
    val notesStatusLabel: String = "未启用",
    val snackbarMessage: String? = null,
    val isBusy: Boolean = false
)

@HiltViewModel
class SystemToolsViewModel @Inject constructor(
    private val settings: SystemToolsSettings,
    private val registry: DeviceToolRegistry,
    private val permissionChecker: SystemToolsPermissionChecker,
    private val notesStore: NotesStore,
    private val notesSaf: NotesSafGateway
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SystemToolsUiState(stubToolNames = registry.names().sorted())
    )
    val uiState: StateFlow<SystemToolsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val perms = runCatching { permissionChecker.check() }
                .getOrDefault(SystemToolsPermissionStatus())
            val config = settings.getConfig()
            val count = runCatching { notesStore.count() }.getOrDefault(0)
            _uiState.update {
                it.copy(
                    config = config,
                    stubToolNames = registry.names().sorted(),
                    permissions = perms,
                    notesCount = count,
                    notesEnabled = config.masterEnabled && config.notesEnabled,
                    notesStatusLabel = buildNotesStatus(config, count),
                    isBusy = false
                )
            }
        }
    }

    fun setMaster(enabled: Boolean) = update { settings.setMasterEnabled(enabled) }

    fun setCalendar(enabled: Boolean) = update { settings.setCalendarEnabled(enabled) }

    fun setAlarm(enabled: Boolean) = update { settings.setAlarmEnabled(enabled) }

    fun setNotes(enabled: Boolean) = update { settings.setNotesEnabled(enabled) }

    fun setUserFile(enabled: Boolean) = update { settings.setUserFileEnabled(enabled) }

    fun setRequireConfirm(require: Boolean) = update {
        settings.setRequireConfirmOnWrite(require)
    }

    fun openCalendarPermissionSettings() {
        runCatching { permissionChecker.openAppDetailsSettings() }
        _uiState.update { it.copy(snackbarMessage = "已打开应用权限设置") }
    }

    fun openExactAlarmSettings() {
        runCatching { permissionChecker.openExactAlarmSettings() }
        _uiState.update { it.copy(snackbarMessage = "已打开精确闹钟设置") }
    }

    /** 设置页 CreateDocument 选中 Uri 后调用：导出 JSON。 */
    fun exportNotesToUri(uriString: String, format: NotesExportFormat = NotesExportFormat.JSON) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            try {
                val notes = notesStore.list(500)
                val payload = when (format) {
                    NotesExportFormat.JSON -> NotesCodec.toJsonBundle(notes)
                    NotesExportFormat.MARKDOWN -> NotesCodec.toMarkdown(notes)
                }
                val mime = when (format) {
                    NotesExportFormat.JSON -> "application/json"
                    NotesExportFormat.MARKDOWN -> "text/markdown"
                }
                when (val r = notesSaf.writeText(uriString, payload, mime)) {
                    is NotesIoResult.Ok -> _uiState.update {
                        it.copy(
                            isBusy = false,
                            snackbarMessage = "已导出 ${notes.size} 条笔记（${r.bytes} 字节）"
                        )
                    }
                    is NotesIoResult.Error -> _uiState.update {
                        it.copy(isBusy = false, snackbarMessage = "导出失败：${r.message}")
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isBusy = false, snackbarMessage = "导出失败：${e.message}")
                }
            }
        }
    }

    /** 设置页 OpenDocument 选中 Uri 后调用：merge 导入。 */
    fun importNotesFromUri(
        uriString: String,
        strategy: NotesImportStrategy = NotesImportStrategy.MERGE
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            try {
                when (val r = notesSaf.readText(uriString)) {
                    is NotesIoResult.Error -> {
                        _uiState.update {
                            it.copy(isBusy = false, snackbarMessage = "导入失败：${r.message}")
                        }
                        return@launch
                    }
                    is NotesIoResult.Ok -> {
                        val parsed = NotesCodec.parseJsonBundle(r.message)
                        if (strategy == NotesImportStrategy.REPLACE) {
                            notesStore.clearAll()
                        }
                        val written = notesStore.upsertAll(parsed)
                        val count = notesStore.count()
                        val config = settings.getConfig()
                        _uiState.update {
                            it.copy(
                                isBusy = false,
                                notesCount = count,
                                notesStatusLabel = buildNotesStatus(config, count),
                                snackbarMessage = "已导入 $written 条，当前共 $count 条"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isBusy = false, snackbarMessage = "导入失败：${e.message}")
                }
            }
        }
    }

    fun shareNotes(format: NotesExportFormat = NotesExportFormat.JSON) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            try {
                val notes = notesStore.list(500)
                val payload = when (format) {
                    NotesExportFormat.JSON -> NotesCodec.toJsonBundle(notes)
                    NotesExportFormat.MARKDOWN -> NotesCodec.toMarkdown(notes)
                }
                val mime = when (format) {
                    NotesExportFormat.JSON -> "application/json"
                    NotesExportFormat.MARKDOWN -> "text/markdown"
                }
                when (val r = notesSaf.shareText(payload, mime)) {
                    is NotesIoResult.Ok -> _uiState.update {
                        it.copy(isBusy = false, snackbarMessage = r.message)
                    }
                    is NotesIoResult.Error -> _uiState.update {
                        it.copy(isBusy = false, snackbarMessage = "分享失败：${r.message}")
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isBusy = false, snackbarMessage = "分享失败：${e.message}")
                }
            }
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    private fun update(block: suspend () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            block()
            val perms = runCatching { permissionChecker.check() }
                .getOrDefault(SystemToolsPermissionStatus())
            val config = settings.getConfig()
            val count = runCatching { notesStore.count() }.getOrDefault(0)
            _uiState.update {
                it.copy(
                    config = config,
                    permissions = perms,
                    notesCount = count,
                    notesEnabled = config.masterEnabled && config.notesEnabled,
                    notesStatusLabel = buildNotesStatus(config, count),
                    isBusy = false,
                    snackbarMessage = "已保存"
                )
            }
        }
    }

    private fun buildNotesStatus(config: SystemToolsConfig, count: Int): String {
        return when {
            !config.masterEnabled -> "总开关关闭"
            !config.notesEnabled -> "笔记能力关闭"
            else -> "已启用 · Room 持久化 · $count 条"
        }
    }

    companion object {
        val DOC_TOOL_COUNT = DeviceToolIds.ALL.size
        val M1_COUNT = DeviceToolIds.M1_STUB_READY.size
        val NOTES_COUNT = DeviceToolIds.NOTES_READY.size
    }
}

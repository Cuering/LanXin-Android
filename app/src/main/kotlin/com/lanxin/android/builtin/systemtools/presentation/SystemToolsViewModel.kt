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
import com.lanxin.android.builtin.systemtools.domain.SystemToolsConfig
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
    val snackbarMessage: String? = null,
    val isBusy: Boolean = false
)

@HiltViewModel
class SystemToolsViewModel @Inject constructor(
    private val settings: SystemToolsSettings,
    private val registry: DeviceToolRegistry
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
            _uiState.update {
                it.copy(
                    config = settings.getConfig(),
                    stubToolNames = registry.names().sorted(),
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

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    private fun update(block: suspend () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            block()
            _uiState.update {
                it.copy(
                    config = settings.getConfig(),
                    isBusy = false,
                    snackbarMessage = "已保存"
                )
            }
        }
    }

    companion object {
        val DOC_TOOL_COUNT = DeviceToolIds.ALL.size
        val M1_COUNT = DeviceToolIds.M1_STUB_READY.size
    }
}

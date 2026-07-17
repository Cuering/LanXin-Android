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

package com.lanxin.android.builtin.platform.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lanxin.android.builtin.platform.domain.DeviceSensingSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DeviceSensingUiState(
    val enabled: Boolean = false,
    val snackbarMessage: String? = null
)

@HiltViewModel
class DeviceSensingViewModel @Inject constructor(
    private val settings: DeviceSensingSettings
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceSensingUiState())
    val uiState: StateFlow<DeviceSensingUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val config = settings.getConfig()
            _uiState.update {
                it.copy(enabled = config.enabled)
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settings.setEnabled(enabled)
            refresh()
            _uiState.update {
                it.copy(
                    snackbarMessage = if (enabled) {
                        "已开启设备感知（system_info 对 Agent 可见）"
                    } else {
                        "已关闭设备感知（默认安全）"
                    }
                )
            }
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}

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

package com.lanxin.android.builtin.capabilities.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lanxin.android.builtin.capabilities.domain.SmartCapabilitiesConfig
import com.lanxin.android.builtin.capabilities.domain.SmartCapabilitiesSettings
import com.lanxin.android.builtin.capabilities.domain.SmartCapabilityId
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SmartCapabilitiesUiState(
    val loading: Boolean = true,
    val masterEnabled: Boolean = true,
    val localInferenceEnabled: Boolean = false,
    val voiceEnabled: Boolean = true,
    val systemToolsEnabled: Boolean = true,
    val webSearchEnabled: Boolean = true,
    val deviceSensingEnabled: Boolean = true,
    val locationEnabled: Boolean = true,
    val sceneVisionEnabled: Boolean = false,
    val summary: String = "",
    val advancedExpanded: Boolean = false,
    val snackbarMessage: String? = null
)

@HiltViewModel
class SmartCapabilitiesViewModel @Inject constructor(
    private val settings: SmartCapabilitiesSettings
) : ViewModel() {

    private val _uiState = MutableStateFlow(SmartCapabilitiesUiState())
    val uiState: StateFlow<SmartCapabilitiesUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            runCatching {
                settings.ensureMigrated()
                settings.getConfig()
            }.onSuccess { cfg ->
                _uiState.update { cfg.toUi() }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        loading = false,
                        snackbarMessage = e.message ?: "加载失败"
                    )
                }
            }
        }
    }

    fun setMaster(enabled: Boolean) {
        viewModelScope.launch {
            settings.setMasterEnabled(enabled)
            refresh()
            _uiState.update {
                it.copy(
                    snackbarMessage = if (enabled) "智能能力主开关已开" else "主开关已关 · 子能力一律拒"
                )
            }
        }
    }

    fun setChild(id: SmartCapabilityId, enabled: Boolean) {
        viewModelScope.launch {
            settings.setChildEnabled(id, enabled)
            refresh()
        }
    }

    fun toggleAdvanced() {
        _uiState.update { it.copy(advancedExpanded = !it.advancedExpanded) }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    private fun SmartCapabilitiesConfig.toUi() = SmartCapabilitiesUiState(
        loading = false,
        masterEnabled = masterEnabled,
        localInferenceEnabled = localInferenceEnabled,
        voiceEnabled = voiceEnabled,
        systemToolsEnabled = systemToolsEnabled,
        webSearchEnabled = webSearchEnabled,
        deviceSensingEnabled = deviceSensingEnabled,
        locationEnabled = locationEnabled,
        sceneVisionEnabled = sceneVisionEnabled,
        summary = summaryLine(),
        advancedExpanded = _uiState.value.advancedExpanded
    )
}

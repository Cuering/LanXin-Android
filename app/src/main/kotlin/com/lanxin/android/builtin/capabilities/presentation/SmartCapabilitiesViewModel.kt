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
import com.lanxin.android.builtin.guide.domain.GuideConfig
import com.lanxin.android.builtin.navigate.domain.NavigateConfig
import com.lanxin.android.plugin.PluginManager
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
    val assistantToolsEnabled: Boolean = true,
    val locationAroundEnabled: Boolean = true,
    val navigateEnabled: Boolean = false,
    val guideEnabled: Boolean = false,
    val sceneVisionEnabled: Boolean = false,
    val summary: String = "",
    val advancedExpanded: Boolean = false,
    val snackbarMessage: String? = null
)

@HiltViewModel
class SmartCapabilitiesViewModel @Inject constructor(
    private val settings: SmartCapabilitiesSettings,
    private val pluginManager: PluginManager
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
                // 设置页镜像 → PluginManager（默认 OFF 插件开后才 onLoad 注册工具）
                runCatching {
                    pluginManager.setEnabled(NavigateConfig.PLUGIN_ID, cfg.navigateEnabled)
                }
                runCatching {
                    pluginManager.setEnabled(GuideConfig.PLUGIN_ID, cfg.guideEnabled)
                }
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
            when (id) {
                SmartCapabilityId.NAVIGATE ->
                    runCatching { pluginManager.setEnabled(NavigateConfig.PLUGIN_ID, enabled) }
                SmartCapabilityId.GUIDE ->
                    runCatching { pluginManager.setEnabled(GuideConfig.PLUGIN_ID, enabled) }
                else -> Unit
            }
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
        assistantToolsEnabled = assistantToolsEnabled,
        locationAroundEnabled = locationAroundEnabled,
        navigateEnabled = navigateEnabled,
        guideEnabled = guideEnabled,
        sceneVisionEnabled = sceneVisionEnabled,
        summary = summaryLine(),
        advancedExpanded = _uiState.value.advancedExpanded
    )
}

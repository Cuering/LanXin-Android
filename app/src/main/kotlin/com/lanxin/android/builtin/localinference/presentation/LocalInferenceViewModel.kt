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

package com.lanxin.android.builtin.localinference.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lanxin.android.builtin.localinference.domain.LocalEngineState
import com.lanxin.android.builtin.localinference.domain.LocalInferenceConfig
import com.lanxin.android.builtin.localinference.domain.LocalInferenceSettings
import com.lanxin.android.builtin.localinference.domain.LocalLlmEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LocalInferenceUiState(
    val enabled: Boolean = false,
    val modelPath: String = "",
    val maxTokens: Int = LocalInferenceConfig.DEFAULT_MAX_TOKENS,
    val temperature: Float = LocalInferenceConfig.DEFAULT_TEMPERATURE,
    val preferLocal: Boolean = false,
    val engineState: LocalEngineState = LocalEngineState.DISABLED,
    val lastError: String? = null,
    val isBusy: Boolean = false,
    val snackbarMessage: String? = null
)

@HiltViewModel
class LocalInferenceViewModel @Inject constructor(
    private val settings: LocalInferenceSettings,
    private val engine: LocalLlmEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(LocalInferenceUiState())
    val uiState: StateFlow<LocalInferenceUiState> = _uiState.asStateFlow()

    init {
        engine.state
            .stateIn(viewModelScope, SharingStarted.Eagerly, LocalEngineState.DISABLED)
        viewModelScope.launch {
            engine.state.collect { st ->
                _uiState.update {
                    it.copy(engineState = st, lastError = engine.lastError)
                }
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val config = settings.getConfig()
            val prefer = settings.isPreferLocal()
            _uiState.update {
                it.copy(
                    enabled = config.enabled,
                    modelPath = config.modelPath,
                    maxTokens = config.maxTokens,
                    temperature = config.temperature,
                    preferLocal = prefer,
                    engineState = engine.state.value,
                    lastError = engine.lastError
                )
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settings.setEnabled(enabled)
            _uiState.update { it.copy(enabled = enabled) }
            if (!enabled) {
                engine.unload()
            }
        }
    }

    fun setModelPath(path: String) {
        viewModelScope.launch {
            settings.setModelPath(path)
            _uiState.update { it.copy(modelPath = path.trim()) }
        }
    }

    fun setMaxTokens(value: Int) {
        viewModelScope.launch {
            settings.setMaxTokens(value)
            val config = settings.getConfig()
            _uiState.update { it.copy(maxTokens = config.maxTokens) }
        }
    }

    fun setPreferLocal(prefer: Boolean) {
        viewModelScope.launch {
            settings.setPreferLocal(prefer)
            _uiState.update { it.copy(preferLocal = prefer) }
        }
    }

    fun loadModel() {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            val config = settings.getConfig()
            val ok = engine.load(config)
            _uiState.update {
                it.copy(
                    isBusy = false,
                    engineState = engine.state.value,
                    lastError = engine.lastError,
                    snackbarMessage = if (ok) {
                        "模型已加载（stub）"
                    } else {
                        "加载失败: ${engine.lastError ?: "unknown"}"
                    }
                )
            }
        }
    }

    fun unloadModel() {
        viewModelScope.launch {
            engine.unload()
            _uiState.update {
                it.copy(
                    engineState = engine.state.value,
                    snackbarMessage = "已卸载"
                )
            }
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}

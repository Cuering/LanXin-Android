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
import com.lanxin.android.builtin.localinference.domain.InferenceRouteCoordinator
import com.lanxin.android.builtin.localinference.domain.LocalEngineState
import com.lanxin.android.builtin.localinference.domain.LocalInferenceConfig
import com.lanxin.android.builtin.localinference.domain.LocalInferenceSettings
import com.lanxin.android.builtin.localinference.domain.LocalLlmEngine
import com.lanxin.android.builtin.localinference.domain.NetworkStatusProvider
import com.lanxin.android.util.LocalPathImporter
import com.lanxin.android.util.PathImportHelper
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
    val pathImportBusy: Boolean = false,
    val snackbarMessage: String? = null,
    /** Phase 6.2 路由预览（云端/本地/不可用）。 */
    val routePreview: String = "",
    val networkAvailable: Boolean = true
)

@HiltViewModel
class LocalInferenceViewModel @Inject constructor(
    private val settings: LocalInferenceSettings,
    private val engine: LocalLlmEngine,
    private val routeCoordinator: InferenceRouteCoordinator,
    private val networkStatusProvider: NetworkStatusProvider,
    private val pathImporter: LocalPathImporter
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
            val preview = runCatching { routeCoordinator.previewLabel() }.getOrDefault("路由预览不可用")
            val networkOk = runCatching { networkStatusProvider.isNetworkAvailable() }.getOrDefault(true)
            _uiState.update {
                it.copy(
                    enabled = config.enabled,
                    modelPath = config.modelPath,
                    maxTokens = config.maxTokens,
                    temperature = config.temperature,
                    preferLocal = prefer,
                    engineState = engine.state.value,
                    lastError = engine.lastError,
                    routePreview = preview,
                    networkAvailable = networkOk
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
                refresh()
                return@launch
            }
            // 开关打开 → 自动 load；路径缺失时明确提示（与 ASR 同契约）
            val config = settings.getConfig()
            if (config.modelPath.isBlank()) {
                _uiState.update {
                    it.copy(
                        snackbarMessage =
                            "已启用本地脑，但模型路径为空。请到桌宠设置「一键下载本地脑」" +
                                "或导入 llm.mnn 所在目录后再试。"
                    )
                }
                refresh()
                return@launch
            }
            _uiState.update { it.copy(isBusy = true) }
            val ok = engine.load(config)
            _uiState.update {
                it.copy(
                    isBusy = false,
                    engineState = engine.state.value,
                    lastError = engine.lastError,
                    snackbarMessage = if (ok) {
                        "本地脑已启用并加载模型"
                    } else {
                        "本地脑已启用，但加载失败：${engine.lastError ?: "unknown"}。" +
                            "请检查模型路径是否存在（如 LanXin/models/local-llm/light/）。"
                    }
                )
            }
            refresh()
        }
    }

    fun setModelPath(path: String) {
        viewModelScope.launch {
            settings.setModelPath(path.ifBlank { null })
            _uiState.update {
                it.copy(
                    modelPath = path.trim(),
                    snackbarMessage = if (path.isBlank()) "已清除本地模型路径" else "本地模型路径已保存"
                )
            }
            refresh()
        }
    }

    /** SAF：选择模型文件并导入私有目录。 */
    fun importModelFromDocument(uriString: String) {
        if (_uiState.value.pathImportBusy) {
            _uiState.update { it.copy(snackbarMessage = "正在导入，请稍候") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(pathImportBusy = true, isBusy = true) }
            val result = pathImporter.importFile(uriString, PathImportHelper.Kind.LOCAL_LLM)
            result.fold(
                onSuccess = { r ->
                    settings.setModelPath(r.absolutePath)
                    _uiState.update {
                        it.copy(
                            pathImportBusy = false,
                            isBusy = false,
                            modelPath = r.absolutePath,
                            snackbarMessage = "本地模型已导入"
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            pathImportBusy = false,
                            isBusy = false,
                            snackbarMessage = "导入失败：${e.message ?: e}"
                        )
                    }
                }
            )
            refresh()
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
            refresh()
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
            refresh()
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
            refresh()
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}

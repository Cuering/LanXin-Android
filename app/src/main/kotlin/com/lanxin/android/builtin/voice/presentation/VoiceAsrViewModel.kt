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

package com.lanxin.android.builtin.voice.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lanxin.android.builtin.voice.data.PcmAudioRecorder
import com.lanxin.android.builtin.voice.domain.AsrConfig
import com.lanxin.android.builtin.voice.domain.AsrEngine
import com.lanxin.android.builtin.voice.domain.AsrEngineState
import com.lanxin.android.builtin.voice.domain.AsrSettings
import com.lanxin.android.builtin.voice.domain.MicPermissionChecker
import com.lanxin.android.builtin.voice.domain.MicPermissionGate
import com.lanxin.android.builtin.voice.domain.MicPermissionState
import com.lanxin.android.builtin.voice.domain.VoiceInputCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VoiceAsrUiState(
    val enabled: Boolean = false,
    val modelPath: String = "",
    val language: String = AsrConfig.DEFAULT_LANGUAGE,
    val sampleRateHz: Int = AsrConfig.DEFAULT_SAMPLE_RATE_HZ,
    val engineState: AsrEngineState = AsrEngineState.DISABLED,
    val lastError: String? = null,
    val micPermission: MicPermissionState = MicPermissionState.UNKNOWN,
    val statusPreview: String = "",
    val lastTranscript: String = "",
    val isBusy: Boolean = false,
    val snackbarMessage: String? = null
)

/**
 * 离线 ASR 设置页 ViewModel。
 */
@HiltViewModel
class VoiceAsrViewModel @Inject constructor(
    private val settings: AsrSettings,
    private val engine: AsrEngine,
    private val coordinator: VoiceInputCoordinator,
    private val permissionChecker: MicPermissionChecker,
    private val recorder: PcmAudioRecorder
) : ViewModel() {

    private val _uiState = MutableStateFlow(VoiceAsrUiState())
    val uiState: StateFlow<VoiceAsrUiState> = _uiState.asStateFlow()

    init {
        engine.state
            .stateIn(viewModelScope, SharingStarted.Eagerly, AsrEngineState.DISABLED)
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
            val preview = runCatching { coordinator.previewStatus() }.getOrDefault("状态不可用")
            val mic = runCatching { permissionChecker.check() }.getOrDefault(MicPermissionState.UNKNOWN)
            _uiState.update {
                it.copy(
                    enabled = config.enabled,
                    modelPath = config.modelPath,
                    language = config.language,
                    sampleRateHz = config.sampleRateHz,
                    engineState = engine.state.value,
                    lastError = engine.lastError,
                    micPermission = mic,
                    statusPreview = preview
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
            refresh()
        }
    }

    fun setModelPath(path: String) {
        viewModelScope.launch {
            settings.setModelPath(path)
            _uiState.update { it.copy(modelPath = path.trim()) }
        }
    }

    fun setLanguage(language: String) {
        viewModelScope.launch {
            settings.setLanguage(language)
            val config = settings.getConfig()
            _uiState.update { it.copy(language = config.language) }
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
                        "ASR 模型已加载（stub）"
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

    /**
     * 设置页「试转写」：用 stub PCM 走引擎，不强制真机麦克风。
     */
    fun trialTranscribe() {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            val config = settings.getConfig()
            if (!config.enabled || !engine.isReady) {
                val msg = MicPermissionGate.blockReason(
                    permission = MicPermissionState.GRANTED,
                    engineReady = engine.isReady,
                    enabled = config.enabled,
                    requireMic = false
                ) ?: "无法试转写"
                _uiState.update {
                    it.copy(isBusy = false, snackbarMessage = msg)
                }
                return@launch
            }
            val audio = recorder.recordStubPcm(
                durationMs = PcmAudioRecorder.DEFAULT_STUB_DURATION_MS,
                sampleRateHz = config.sampleRateHz
            )
            val result = coordinator.transcribePcm(
                pcm16leMono = audio.pcm16leMono,
                sampleRateHz = audio.sampleRateHz
            )
            _uiState.update {
                if (result.isSuccess) {
                    val text = result.getOrNull()?.text.orEmpty()
                    it.copy(
                        isBusy = false,
                        lastTranscript = text,
                        snackbarMessage = "试转写完成"
                    )
                } else {
                    it.copy(
                        isBusy = false,
                        snackbarMessage = result.exceptionOrNull()?.message ?: "试转写失败"
                    )
                }
            }
            refresh()
        }
    }

    /**
     * UI 层权限回调后刷新 mic 状态。
     *
     * @param granted 是否授予
     * @param permanentlyDenied 用户不再询问
     */
    fun onPermissionResult(granted: Boolean, permanentlyDenied: Boolean = false) {
        val state = when {
            granted -> MicPermissionState.GRANTED
            permanentlyDenied -> MicPermissionState.PERMANENTLY_DENIED
            else -> MicPermissionState.DENIED
        }
        _uiState.update {
            it.copy(
                micPermission = state,
                snackbarMessage = if (granted) {
                    "已获得麦克风权限"
                } else {
                    MicPermissionGate.deniedMessage(state)
                }
            )
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}

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

package com.lanxin.android.builtin.pet.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lanxin.android.builtin.pet.domain.FrameStats
import com.lanxin.android.builtin.pet.domain.SceneCaptureCoordinator
import com.lanxin.android.builtin.pet.domain.SceneRecognitionSession
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SceneRecognitionUiState(
    val enabled: Boolean = false,
    val consentGranted: Boolean = false,
    val feedbackLine: String? = null,
    val lastLabel: String? = null,
    val snackbarMessage: String? = null,
    val busy: Boolean = false
)

@HiltViewModel
class SceneRecognitionViewModel @Inject constructor(
    private val coordinator: SceneCaptureCoordinator,
    private val session: SceneRecognitionSession
) : ViewModel() {

    private val _uiState = MutableStateFlow(SceneRecognitionUiState())
    val uiState: StateFlow<SceneRecognitionUiState> = _uiState.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            session.lastResult.collect { result ->
                _uiState.update {
                    it.copy(
                        feedbackLine = session.feedbackLine(),
                        lastLabel = result?.label?.displayName
                    )
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val config = coordinator.currentConfig()
            _uiState.update {
                it.copy(
                    enabled = config.enabled,
                    consentGranted = config.consentGranted,
                    feedbackLine = session.feedbackLine(),
                    lastLabel = session.current()?.label?.displayName
                )
            }
        }
    }

    /** 用户已在 Dialog 确认隐私 → 写入 enabled+consent。 */
    fun confirmEnable() {
        viewModelScope.launch {
            val next = coordinator.enableWithConsent(userConfirmed = true)
            if (next == null) {
                snack("未确认，未开启")
                return@launch
            }
            refresh()
            snack("已开启场景识别（可随时关闭）")
        }
    }

    fun disable() {
        viewModelScope.launch {
            coordinator.disableAndClear(revokeConsent = true)
            refresh()
            snack("已关闭场景识别并清空会话缓存")
        }
    }

    fun onPermissionDenied() {
        snack("需要摄像头权限才能开启场景识别")
    }

    fun clearSession() {
        coordinator.clearSessionOnly()
        refresh()
        snack("已清空会话缓存")
    }

    /**
     * 演示采集：构造中等亮度暖色帧统计，验证门闸 + 识别 + 缓存链路。
     * 真 CameraX 可替换为真实 [FrameStats]。
     */
    fun runDemoCapture() {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true) }
            val demo = FrameStats(
                meanLuma = 0.55f,
                meanR = 0.62f,
                meanG = 0.50f,
                meanB = 0.40f,
                sampleCount = 256
            )
            when (val out = coordinator.captureWithStats(demo)) {
                is SceneCaptureCoordinator.CaptureOutcome.Success -> {
                    refresh()
                    snack("识别：${out.result.label.displayName} · ${out.result.feedbackText}")
                }
                is SceneCaptureCoordinator.CaptureOutcome.Denied -> {
                    snack("无法采集：${out.code}")
                }
            }
            _uiState.update { it.copy(busy = false) }
        }
    }

    fun snack(message: String) {
        _uiState.update { it.copy(snackbarMessage = message) }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}

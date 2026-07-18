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

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lanxin.android.builtin.pet.domain.PetSettings
import com.lanxin.android.builtin.platform.domain.SceneCaptureAnalyzer
import com.lanxin.android.builtin.platform.domain.SceneSensingGate
import com.lanxin.android.builtin.platform.domain.SceneSensingSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SceneSensingUiState(
    val enabled: Boolean = false,
    val consentGranted: Boolean = false,
    val lastSceneId: String = "",
    val lastStatusText: String = "",
    val lastMoodHint: String = "",
    val lastBackgroundId: String = "",
    val showConsentDialog: Boolean = false,
    val busy: Boolean = false,
    val snackbarMessage: String? = null
)

@HiltViewModel
class SceneSensingViewModel @Inject constructor(
    private val settings: SceneSensingSettings,
    private val petSettings: PetSettings
) : ViewModel() {

    private val _uiState = MutableStateFlow(SceneSensingUiState())
    val uiState: StateFlow<SceneSensingUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val config = settings.getConfig()
            _uiState.update {
                it.copy(
                    enabled = config.enabled,
                    consentGranted = config.consentGranted,
                    lastSceneId = config.lastSceneId,
                    lastStatusText = config.lastStatusText
                )
            }
        }
    }

    /**
     * 用户拨动开关。
     * - 关：直接关
     * - 开且未同意：弹确认 Gate，不立刻写 enabled
     * - 开且已同意：写 enabled
     */
    fun onEnabledToggle(wantOn: Boolean) {
        viewModelScope.launch {
            val config = settings.getConfig()
            if (SceneSensingGate.needsConsentDialog(config, wantOn)) {
                _uiState.update { it.copy(showConsentDialog = true) }
                return@launch
            }
            settings.setEnabled(wantOn)
            refresh()
            _uiState.update {
                it.copy(
                    snackbarMessage = if (wantOn) {
                        "已开启场景识别（仅点「识别」时用相机，默认不拍）"
                    } else {
                        "已关闭场景识别（默认安全）"
                    }
                )
            }
        }
    }

    /** 确认 Gate：同意隐私说明并开启。 */
    fun confirmConsentAndEnable() {
        viewModelScope.launch {
            settings.setConsentGranted(true)
            settings.setEnabled(true)
            _uiState.update {
                it.copy(
                    showConsentDialog = false,
                    snackbarMessage = "已同意并开启 · 不会后台偷拍"
                )
            }
            refresh()
        }
    }

    fun dismissConsentDialog() {
        _uiState.update { it.copy(showConsentDialog = false) }
    }

    /** 撤回同意并关闭。 */
    fun revokeConsent() {
        viewModelScope.launch {
            settings.setConsentGranted(false)
            refresh()
            _uiState.update {
                it.copy(snackbarMessage = "已撤回同意 · 场景缓存已清 · 功能关闭")
            }
        }
    }

    fun clearLastScene() {
        viewModelScope.launch {
            settings.clearLastScene()
            refresh()
            _uiState.update {
                it.copy(
                    lastMoodHint = "",
                    lastBackgroundId = "",
                    snackbarMessage = "已清除最近场景缓存"
                )
            }
        }
    }

    /**
     * 在已通过 Gate 的前提下处理系统相机快照。
     * @param cameraGranted 系统 CAMERA 权限
     * @param applyBackground 是否写入陪伴背景预设
     */
    fun onPreviewCaptured(
        bitmap: Bitmap?,
        cameraGranted: Boolean,
        applyBackground: Boolean = true
    ) {
        viewModelScope.launch {
            val config = settings.getConfig()
            val deny = SceneSensingGate.denyReason(config, cameraGranted)
            if (deny != null) {
                _uiState.update {
                    it.copy(snackbarMessage = SceneSensingGate.blockMessage(deny))
                }
                return@launch
            }
            if (bitmap == null) {
                _uiState.update { it.copy(snackbarMessage = "未获得预览图，请重试") }
                return@launch
            }
            _uiState.update { it.copy(busy = true) }
            try {
                val effect = withContext(Dispatchers.Default) {
                    try {
                        SceneCaptureAnalyzer.analyze(bitmap)
                    } finally {
                        if (!bitmap.isRecycled) {
                            bitmap.recycle()
                        }
                    }
                }
                settings.setLastScene(effect.scene.id, effect.statusText)
                if (applyBackground && effect.backgroundPresetId != null) {
                    petSettings.setCompanionBackground(
                        presetId = effect.backgroundPresetId,
                        customPath = null
                    )
                }
                _uiState.update {
                    it.copy(
                        busy = false,
                        lastSceneId = effect.scene.id,
                        lastStatusText = effect.statusText,
                        lastMoodHint = effect.moodHint.orEmpty(),
                        lastBackgroundId = effect.backgroundPresetId.orEmpty(),
                        snackbarMessage = effect.statusText +
                            (effect.moodHint?.let { m -> " · mood=$m" } ?: "")
                    )
                }
            } catch (e: Exception) {
                if (!bitmap.isRecycled) {
                    runCatching { bitmap.recycle() }
                }
                _uiState.update {
                    it.copy(
                        busy = false,
                        snackbarMessage = "识别失败：${e.message ?: "未知错误"}"
                    )
                }
            }
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}

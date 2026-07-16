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

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lanxin.android.BuildConfig
import com.lanxin.android.builtin.pet.data.FloatingPetService
import com.lanxin.android.builtin.pet.data.OverlayPermissionHelper
import com.lanxin.android.builtin.pet.domain.PetResourceResolver
import com.lanxin.android.builtin.pet.domain.PetSettings
import com.lanxin.android.builtin.pet.domain.VoiceSessionCoordinator
import com.lanxin.android.builtin.pet.domain.VoiceSessionPhase
import com.lanxin.android.builtin.voice.domain.AsrSettings
import com.lanxin.android.builtin.voice.domain.TtsEngine
import com.lanxin.android.builtin.voice.domain.TtsSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DesktopPetUiState(
    val enabled: Boolean = false,
    val overlayRunning: Boolean = false,
    val canDrawOverlays: Boolean = false,
    val phase: VoiceSessionPhase = VoiceSessionPhase.IDLE,
    val asrText: String = "",
    val replyText: String = "",
    val subtitle: String = "",
    val lastError: String? = null,
    val sessionPreview: String = "",
    val isBusy: Boolean = false,
    val snackbarMessage: String? = null,
    val ttsEnabled: Boolean = false,
    /** DataStore 原始 live2d 路径（用户配置）。 */
    val live2dModelPathConfigured: String = "",
    /** 解析后的运行时路径。 */
    val live2dModelPathResolved: String = "",
    val ttsModelDirResolved: String = "",
    val ttsReferenceResolved: String = "",
    val asrModelPathResolved: String = "",
    /** 设置页标注：当前：自定义 / Debug 妹居参考（仅本地）/ 占位。 */
    val live2dSourceLabel: String = "当前：占位 / 未配置",
    val ttsSourceLabel: String = "当前：占位 / 未配置",
    val asrSourceLabel: String = "当前：占位 / 未配置",
    val isDebugBuild: Boolean = false
)

@HiltViewModel
class DesktopPetViewModel @Inject constructor(
    application: Application,
    private val petSettings: PetSettings,
    private val sessionCoordinator: VoiceSessionCoordinator,
    private val ttsSettings: TtsSettings,
    private val ttsEngine: TtsEngine,
    private val asrSettings: AsrSettings
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(DesktopPetUiState(isDebugBuild = BuildConfig.DEBUG))
    val uiState: StateFlow<DesktopPetUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            sessionCoordinator.snapshot.collect { snap ->
                _uiState.update {
                    it.copy(
                        phase = snap.phase,
                        asrText = snap.asrText,
                        replyText = snap.replyText,
                        subtitle = snap.subtitle,
                        lastError = snap.lastError,
                        sessionPreview = formatPreview(
                            snap.phase.name,
                            snap.asrText,
                            snap.replyText,
                            snap.lastError
                        )
                    )
                }
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val config = petSettings.getConfig()
            val tts = ttsSettings.getConfig()
            val asr = asrSettings.getConfig()
            val resolved = PetResourceResolver.resolve(
                filesDir = app.filesDir,
                pet = config,
                tts = tts,
                asr = asr,
                isDebug = BuildConfig.DEBUG
            )
            val can = OverlayPermissionHelper.canDrawOverlays(app)
            val snap = sessionCoordinator.current()
            _uiState.update {
                it.copy(
                    enabled = config.enabled,
                    overlayRunning = config.overlayRunning,
                    canDrawOverlays = can,
                    ttsEnabled = tts.enabled,
                    live2dModelPathConfigured = config.live2dModelPath,
                    live2dModelPathResolved = resolved.live2dModelPath,
                    ttsModelDirResolved = resolved.ttsModelDir,
                    ttsReferenceResolved = resolved.ttsReferenceAudio,
                    asrModelPathResolved = resolved.asrModelPath,
                    live2dSourceLabel = resolved.live2dLabel,
                    ttsSourceLabel = resolved.ttsLabel,
                    asrSourceLabel = resolved.asrLabel,
                    isDebugBuild = BuildConfig.DEBUG,
                    phase = snap.phase,
                    asrText = snap.asrText,
                    replyText = snap.replyText,
                    subtitle = snap.subtitle,
                    lastError = snap.lastError,
                    sessionPreview = formatPreview(
                        snap.phase.name,
                        snap.asrText,
                        snap.replyText,
                        snap.lastError
                    )
                )
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            petSettings.setEnabled(enabled)
            if (!enabled) {
                FloatingPetService.stop(getApplication())
                petSettings.setOverlayRunning(false)
                sessionCoordinator.reset()
            }
            if (enabled && !ttsSettings.getConfig().enabled) {
                ttsSettings.setEnabled(true)
                ttsEngine.load(ttsSettings.getConfig())
            }
            _uiState.update { it.copy(enabled = enabled) }
            refresh()
        }
    }

    fun setLive2dModelPath(path: String) {
        viewModelScope.launch {
            petSettings.setLive2dModelPath(path.ifBlank { null })
            refresh()
        }
    }

    fun setTtsModelDir(path: String) {
        viewModelScope.launch {
            ttsSettings.setModelDir(path.ifBlank { null })
            refresh()
        }
    }

    fun setTtsReferenceAudio(path: String) {
        viewModelScope.launch {
            ttsSettings.setReferenceAudio(path.ifBlank { null })
            refresh()
        }
    }

    fun requestOverlayPermission() {
        val app = getApplication<Application>()
        app.startActivity(OverlayPermissionHelper.createManageOverlayIntent(app))
    }

    fun startPet() {
        viewModelScope.launch {
            val app = getApplication<Application>()
            if (!petSettings.getConfig().enabled) {
                _uiState.update { it.copy(snackbarMessage = "请先打开桌宠总开关") }
                return@launch
            }
            if (!OverlayPermissionHelper.canDrawOverlays(app)) {
                _uiState.update { it.copy(snackbarMessage = OverlayPermissionHelper.DENIED_HINT) }
                return@launch
            }
            FloatingPetService.start(app)
            petSettings.setOverlayRunning(true)
            refresh()
            _uiState.update { it.copy(snackbarMessage = "桌宠已启动") }
        }
    }

    fun stopPet() {
        viewModelScope.launch {
            FloatingPetService.stop(getApplication())
            petSettings.setOverlayRunning(false)
            refresh()
            _uiState.update { it.copy(snackbarMessage = "桌宠已停止") }
        }
    }

    /** stub 一轮听→想→说（无需真 so / 真麦）。 */
    fun runDemoRound() {
        viewModelScope.launch {
            if (!petSettings.getConfig().enabled) {
                _uiState.update { it.copy(snackbarMessage = "请先打开桌宠总开关") }
                return@launch
            }
            _uiState.update { it.copy(isBusy = true) }
            if (!ttsEngine.isReady) {
                ttsSettings.setEnabled(true)
                ttsEngine.load(ttsSettings.getConfig())
            }
            val result = sessionCoordinator.runDemoRound()
            _uiState.update {
                it.copy(
                    isBusy = false,
                    snackbarMessage = if (result.error != null) {
                        "演示失败：${result.error}"
                    } else {
                        "演示完成：${result.subtitle.ifBlank { result.replyText }}"
                    }
                )
            }
            refresh()
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    private fun formatPreview(
        phase: String,
        asr: String,
        reply: String,
        err: String?
    ): String = buildString {
        append("phase=").append(phase)
        if (asr.isNotBlank()) append(" · asr=").append(asr.take(32))
        if (reply.isNotBlank()) append(" · reply=").append(reply.take(32))
        if (!err.isNullOrBlank()) append(" · err=").append(err)
    }
}

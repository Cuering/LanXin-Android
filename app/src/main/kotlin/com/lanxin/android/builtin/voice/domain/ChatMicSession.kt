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

package com.lanxin.android.builtin.voice.domain

import com.lanxin.android.builtin.voice.data.PcmAudioRecorder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 主聊天对话框麦克风听写会话。
 *
 * 复用 [VoiceInputCoordinator] + [PcmAudioRecorder]；
 * 只产出文本回调，不直接碰 Chat 发送链路。
 *
 * 产品约束：
 * - ASR 未启用 / 模型未就绪 → snackbar，禁止静默
 * - 权限拒绝 → 温柔文案
 * - 关/松麦 → 停听、释放麦克风
 * - 转写结果填入输入框，由用户确认后再发
 */
@Singleton
class ChatMicSession @Inject constructor(
    private val coordinator: VoiceInputCoordinator,
    private val recorder: PcmAudioRecorder,
    private val settings: AsrSettings,
    private val engine: AsrEngine,
    private val permissionChecker: MicPermissionChecker
) {

    private val mutex = Mutex()
    private val _uiState = MutableStateFlow(ChatMicUiState())
    val uiState: StateFlow<ChatMicUiState> = _uiState.asStateFlow()

    /**
     * 点击麦克风（与陪伴底栏同一套「语音聊天模式」语义）：
     * - 关 + IDLE → 开模式并开始听写
     * - 开 + RECORDING → 停录并转写（模式保持开）
     * - 开 + IDLE → 关模式（不占麦）
     * - TRANSCRIBING → 忽略
     *
     * @param onTranscript 成功转写后回调（主线程外也可；UI 侧填入输入框）
     */
    suspend fun onMicClick(onTranscript: (String) -> Unit) {
        mutex.withLock {
            val st = _uiState.value
            when (st.phase) {
                ChatMicPhase.TRANSCRIBING -> return
                ChatMicPhase.RECORDING -> stopAndTranscribeLocked(onTranscript)
                ChatMicPhase.IDLE -> {
                    if (st.voiceChatEnabled) {
                        // 已开且空闲：再点关，释放麦意图
                        _uiState.update {
                            it.copy(
                                voiceChatEnabled = false,
                                snackbarMessage = "语音聊天已关"
                            )
                        }
                    } else {
                        _uiState.update { it.copy(voiceChatEnabled = true) }
                        startRecordingLocked()
                        // 未能进入录音（权限/引擎）则回关，避免假开占位
                        if (_uiState.value.phase != ChatMicPhase.RECORDING &&
                            !_uiState.value.needRequestPermission
                        ) {
                            _uiState.update { it.copy(voiceChatEnabled = false) }
                        }
                    }
                }
            }
        }
    }

    /**
     * 运行时权限结果。
     *
     * @param granted 是否授予
     * @param permanentlyDenied 用户勾选不再询问
     */
    suspend fun onPermissionResult(
        granted: Boolean,
        permanentlyDenied: Boolean = false,
        @Suppress("UNUSED_PARAMETER") onTranscript: (String) -> Unit = {}
    ) {
        mutex.withLock {
            _uiState.update { it.copy(needRequestPermission = false) }
            if (!granted) {
                val state = if (permanentlyDenied) {
                    MicPermissionState.PERMANENTLY_DENIED
                } else {
                    MicPermissionState.DENIED
                }
                _uiState.update {
                    it.copy(
                        phase = ChatMicPhase.IDLE,
                        voiceChatEnabled = false,
                        snackbarMessage = MicPermissionGate.deniedMessage(state)
                    )
                }
                return@withLock
            }
            // 授权后自动开录（用户刚点过麦）；保持语音模式开
            _uiState.update { it.copy(voiceChatEnabled = true) }
            startRecordingLocked(skipPermissionCheck = true)
            if (_uiState.value.phase != ChatMicPhase.RECORDING) {
                _uiState.update { it.copy(voiceChatEnabled = false) }
            }
        }
    }

    /** 离开 Chat 页 / 取消：停录并释放麦，不转写；语音模式也关。 */
    suspend fun cancel() {
        mutex.withLock {
            runCatching { recorder.cancelRecording() }
            _uiState.update {
                it.copy(
                    phase = ChatMicPhase.IDLE,
                    voiceChatEnabled = false,
                    needRequestPermission = false
                )
            }
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun consumePermissionRequest() {
        _uiState.update { it.copy(needRequestPermission = false) }
    }

    private suspend fun startRecordingLocked(skipPermissionCheck: Boolean = false) {
        val config = settings.getConfig()
        // 先确保引擎：关着 / 无路径 / 未就绪 → 明确 snackbar
        val ensureMsg = ensureEngineReady(config)
        if (ensureMsg != null) {
            _uiState.update {
                it.copy(phase = ChatMicPhase.IDLE, snackbarMessage = ensureMsg)
            }
            return
        }

        if (!skipPermissionCheck) {
            val perm = permissionChecker.check()
            if (!MicPermissionGate.canRecord(perm)) {
                // 可再申请：拉起系统弹窗；永久拒绝则直接文案
                if (perm == MicPermissionState.PERMANENTLY_DENIED) {
                    _uiState.update {
                        it.copy(
                            phase = ChatMicPhase.IDLE,
                            snackbarMessage = MicPermissionGate.deniedMessage(perm)
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            phase = ChatMicPhase.IDLE,
                            needRequestPermission = true,
                            snackbarMessage = null
                        )
                    }
                }
                return
            }
        }

        val start = recorder.startRecording(sampleRateHz = config.sampleRateHz)
        if (start.isFailure) {
            _uiState.update {
                it.copy(
                    phase = ChatMicPhase.IDLE,
                    snackbarMessage = start.exceptionOrNull()?.message
                        ?: "无法打开麦克风，请检查权限或设备。"
                )
            }
            return
        }
        _uiState.update {
            it.copy(phase = ChatMicPhase.RECORDING, snackbarMessage = null)
        }
    }

    private suspend fun stopAndTranscribeLocked(onTranscript: (String) -> Unit) {
        _uiState.update { it.copy(phase = ChatMicPhase.TRANSCRIBING) }
        val audioResult = recorder.stopRecording()
        if (audioResult.isFailure) {
            _uiState.update {
                it.copy(
                    phase = ChatMicPhase.IDLE,
                    snackbarMessage = audioResult.exceptionOrNull()?.message
                        ?: "录音失败"
                )
            }
            return
        }
        val audio = audioResult.getOrThrow()
        if (audio.pcm16leMono.isEmpty() || audio.durationMs < MIN_USEFUL_MS) {
            _uiState.update {
                it.copy(
                    phase = ChatMicPhase.IDLE,
                    snackbarMessage = "录音太短，请按住/点按麦克风再说一会儿。"
                )
            }
            return
        }

        val result = coordinator.transcribePcm(
            pcm16leMono = audio.pcm16leMono,
            sampleRateHz = audio.sampleRateHz
        )
        if (result.isFailure) {
            _uiState.update {
                it.copy(
                    phase = ChatMicPhase.IDLE,
                    snackbarMessage = result.exceptionOrNull()?.message
                        ?: "语音识别失败"
                )
            }
            return
        }
        val text = result.getOrNull()?.text?.trim().orEmpty()
        if (text.isEmpty()) {
            _uiState.update {
                it.copy(
                    phase = ChatMicPhase.IDLE,
                    snackbarMessage = "没有识别到内容，请再说一次。"
                )
            }
            return
        }
        onTranscript(text)
        _uiState.update {
            it.copy(
                phase = ChatMicPhase.IDLE,
                lastFilledText = text,
                snackbarMessage = null
            )
        }
    }

    /**
     * 确保 ASR 可用：必要时 auto-load。
     * @return null 可继续；否则用户可见阻断文案
     */
    private suspend fun ensureEngineReady(config: AsrConfig): String? {
        if (!config.enabled) {
            return MicPermissionGate.blockReason(
                permission = MicPermissionState.GRANTED,
                engineReady = false,
                enabled = false,
                requireMic = false
            )
        }
        if (engine.isReady) return null
        if (config.modelPath.isBlank()) {
            return "请先下载/开启离线语音。可到「设置 → 离线语音识别」或桌宠「一键下载 ASR」。"
        }
        val loaded = runCatching { engine.load(config) }.getOrDefault(false)
        if (loaded && engine.isReady) return null
        return engine.lastError
            ?: MicPermissionGate.blockReason(
                permission = MicPermissionState.GRANTED,
                engineReady = false,
                enabled = true,
                requireMic = false
            )
            ?: "请先下载/开启离线语音"
    }

    companion object {
        /** 过短录音（误触）阈值。 */
        const val MIN_USEFUL_MS = 200L
    }
}

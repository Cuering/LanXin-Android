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
                        // 只在真正开麦成功后亮「语音开」
                        startRecordingLocked()
                        if (_uiState.value.phase == ChatMicPhase.RECORDING) {
                            _uiState.update { it.copy(voiceChatEnabled = true) }
                        } else if (!_uiState.value.needRequestPermission) {
                            _uiState.update { it.copy(voiceChatEnabled = false) }
                        }
                    }
                }
            }
        }
    }

    /**
     * 按住听写：开麦录音，**不**点亮 voiceChatEnabled。
     * 与点按「语音对话模式」互斥，适合长按验证硬件麦。
     */
    suspend fun startHoldDictation() {
        mutex.withLock {
            val st = _uiState.value
            if (st.phase != ChatMicPhase.IDLE) return
            // 按住路径不改 voiceChatEnabled；失败只 snackbar
            startRecordingLocked()
        }
    }

    /**
     * 松手结束按住听写：停录转写；若尚未进入 RECORDING（心跳中）则取消。
     */
    suspend fun stopHoldDictation(onTranscript: (String) -> Unit) {
        mutex.withLock {
            when (_uiState.value.phase) {
                ChatMicPhase.RECORDING -> stopAndTranscribeLocked(onTranscript)
                ChatMicPhase.TRANSCRIBING -> return
                ChatMicPhase.IDLE -> {
                    // 可能还在 startRecording/heartbeat 中途失败后回 IDLE，或已 cancel
                    runCatching { recorder.cancelRecording() }
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
            startRecordingLocked(skipPermissionCheck = true)
            if (_uiState.value.phase == ChatMicPhase.RECORDING) {
                _uiState.update { it.copy(voiceChatEnabled = true) }
            } else {
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
        val heartbeat = recorder.awaitCaptureHeartbeat()
        if (heartbeat != null) {
            runCatching { recorder.cancelRecording() }
            _uiState.update {
                it.copy(
                    phase = ChatMicPhase.IDLE,
                    snackbarMessage = heartbeat
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
        if (looksLikeSilentCapture(audio.pcm16leMono)) {
            _uiState.update {
                it.copy(
                    phase = ChatMicPhase.IDLE,
                    snackbarMessage = "麦克风似乎没有收到声音。请检查系统麦克风权限，" +
                        "或到「设置 → 应用 → 兰心 → 权限」确认已允许录音。"
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

    private fun looksLikeSilentCapture(pcm: ByteArray): Boolean {
        if (pcm.size < 4) return true
        var peak = 0
        var i = 0
        val step = ((pcm.size / 2) / 4000).coerceAtLeast(1) * 2
        while (i + 1 < pcm.size) {
            val sample = ((pcm[i + 1].toInt() shl 8) or (pcm[i].toInt() and 0xFF)).toShort()
            val abs = kotlin.math.abs(sample.toInt())
            if (abs > peak) peak = abs
            if (peak >= SILENCE_PEAK_THRESHOLD) return false
            i += step
        }
        return peak < SILENCE_PEAK_THRESHOLD
    }

    companion object {
        /** 过短录音（误触）阈值。 */
        const val MIN_USEFUL_MS = 200L
        const val SILENCE_PEAK_THRESHOLD = 48
    }
}

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

import com.lanxin.android.builtin.localinference.domain.LocalReplySanitizer
import com.lanxin.android.builtin.voice.data.PcmAudioPlayer
import com.lanxin.android.builtin.voice.data.PcmAudioRecorder
import com.lanxin.android.builtin.voice.data.SherpaAsrEngine
import com.lanxin.android.builtin.voice.data.SherpaOnnxBridge
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 真语音对话会话：听 → 流式 ASR → 自动发送 → TTS 播放 →（可选）连续下一轮。
 *
 * 与 [ChatMicSession] 的区别：
 * - ChatMic：转写填入输入框，用户确认再发
 * - VoiceChat：转写后自动回调 [onAutoSend]，TTS 播完后可继续听
 *
 * 产品约束：
 * - 不在后台偷偷录音；仅用户显式开语音对话
 * - ASR/TTS 未就绪 → snackbar，禁止静默
 * - 流式 ASR 仅 OnlineTransducer 可用；否则整段转写 + 无 partial
 */
@Singleton
class VoiceChatSession @Inject constructor(
    private val recorder: PcmAudioRecorder,
    private val player: PcmAudioPlayer,
    private val settings: AsrSettings,
    private val engine: AsrEngine,
    private val nativeBridge: SherpaOnnxBridge,
    private val ttsEngine: TtsEngine,
    private val ttsSettings: TtsSettings,
    private val permissionChecker: MicPermissionChecker
) {

    private val mutex = Mutex()
    private val _uiState = MutableStateFlow(VoiceChatUiState())
    val uiState: StateFlow<VoiceChatUiState> = _uiState.asStateFlow()

    /** 会话级 scope：endpoint 轮询、异步收口 */
    private val sessionJob = SupervisorJob()
    private val sessionScope = CoroutineScope(sessionJob + Dispatchers.Main.immediate)

    @Volatile
    private var streamingActive: Boolean = false

    @Volatile
    private var continuous: Boolean = true

    /** 当前轮自动发送回调（endpoint / 手动停麦共用） */
    @Volatile
    private var autoSendHandler: (suspend (String) -> Unit)? = null

    /** endpoint 轮询协程；null 表示未在轮询 */
    private var endpointPollJob: Job? = null

    /**
     * 点麦切换：
     * - 关 → 开并开始听
     * - 听中 → 停录转写并 [onAutoSend]
     * - 其它忙阶段 → 忽略 / 取消
     *
     * @param onAutoSend 最终识别文本回调（调用方负责 sendQuestion）
     * @param continuousListen 播完 TTS 后是否自动再开下一轮
     */
    suspend fun onMicClick(
        continuousListen: Boolean = true,
        onAutoSend: suspend (String) -> Unit
    ) {
        mutex.withLock {
            continuous = continuousListen
            autoSendHandler = onAutoSend
            val st = _uiState.value
            when (st.phase) {
                VoiceChatPhase.LISTENING -> {
                    stopListenAndSendLocked(onAutoSend)
                }
                VoiceChatPhase.IDLE -> {
                    if (st.enabled) {
                        // 已开且空闲 → 关
                        disableLocked()
                    } else {
                        // 注意：enabled 只在真正开麦成功后才置 true，避免「图标亮了但没开麦」
                        val ok = startListeningLocked(onAutoSend = onAutoSend)
                        if (ok) {
                            _uiState.update { it.copy(enabled = true) }
                        } else if (!_uiState.value.needRequestPermission) {
                            _uiState.update { it.copy(enabled = false) }
                        }
                    }
                }
                VoiceChatPhase.SPEAKING -> {
                    // 打断 TTS，回到听
                    player.stop()
                    _uiState.update { it.copy(phase = VoiceChatPhase.IDLE) }
                    if (st.enabled) {
                        startListeningLocked(onAutoSend = onAutoSend)
                    }
                }
                VoiceChatPhase.TRANSCRIBING,
                VoiceChatPhase.WAITING_REPLY -> {
                    // 忙，忽略
                }
            }
        }
    }

    /**
     * 权限结果回调。
     */
    suspend fun onPermissionResult(
        granted: Boolean,
        permanentlyDenied: Boolean = false,
        onAutoSend: suspend (String) -> Unit = {}
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
                        phase = VoiceChatPhase.IDLE,
                        enabled = false,
                        snackbarMessage = MicPermissionGate.deniedMessage(state)
                    )
                }
                return@withLock
            }
            autoSendHandler = onAutoSend
            val ok = startListeningLocked(
                skipPermissionCheck = true,
                onAutoSend = onAutoSend
            )
            if (ok) {
                _uiState.update { it.copy(enabled = true) }
            } else {
                _uiState.update { it.copy(enabled = false) }
            }
        }
    }

    /**
     * LLM 回复就绪后调用：合成并播放 TTS。
     * 播完后若 [continuous] 且仍 enabled → 自动下一轮听。
     */
    suspend fun onReplyReady(replyText: String) {
        // TTS 统一走 forSpeech：剥 think/标签/emoji/装饰，避免念出内部协议
        val text = LocalReplySanitizer.forSpeech(replyText, showThinking = false).trim()
        if (text.isEmpty() || !_uiState.value.enabled) {
            _uiState.update {
                it.copy(
                    phase = VoiceChatPhase.IDLE,
                    partialText = ""
                )
            }
            return
        }
        _uiState.update {
            it.copy(phase = VoiceChatPhase.SPEAKING, snackbarMessage = null)
        }
        // 确保 TTS（主路径 Sherpa OfflineTts；系统 TTS 仅作可选兜底，默认不依赖）
        if (!ttsEngine.isReady) {
            runCatching {
                val cfg = ttsSettings.getConfig()
                ttsEngine.load(if (cfg.enabled) cfg else cfg.copy(enabled = true))
                if (!cfg.enabled) ttsSettings.setEnabled(true)
            }
        }
        val synth = runCatching {
            ttsEngine.synthesize(TtsSynthesizeRequest(text = text))
        }.getOrNull()
        if (synth == null || synth.pcm16leMono.isEmpty()) {
            _uiState.update {
                it.copy(
                    phase = VoiceChatPhase.IDLE,
                    snackbarMessage = if (synth?.isStub == true) {
                        null // stub 无声，不报错
                    } else {
                        "语音播放失败"
                    }
                )
            }
            maybeContinueListening()
            return
        }
        player.play(synth.pcm16leMono, synth.sampleRateHz)
        _uiState.update { it.copy(phase = VoiceChatPhase.IDLE) }
        maybeContinueListening()
    }

    /** 标记已送出，等待回复。 */
    fun markWaitingReply() {
        _uiState.update {
            it.copy(phase = VoiceChatPhase.WAITING_REPLY, partialText = "")
        }
    }

    /** 取消并关语音对话。 */
    suspend fun cancel() {
        mutex.withLock {
            disableLocked()
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun consumePermissionRequest() {
        _uiState.update { it.copy(needRequestPermission = false) }
    }

    private suspend fun maybeContinueListening() {
        if (!continuous || !_uiState.value.enabled) return
        val handler = autoSendHandler ?: return
        mutex.withLock {
            if (_uiState.value.enabled && _uiState.value.phase == VoiceChatPhase.IDLE) {
                startListeningLocked(skipPermissionCheck = true, onAutoSend = handler)
            }
        }
    }

    private suspend fun disableLocked() {
        cancelEndpointPolling()
        streamingActive = false
        autoSendHandler = null
        recorder.onPcmChunk = null
        runCatching { nativeBridge.cancelStreaming() }
        runCatching { recorder.cancelRecording() }
        player.stop()
        _uiState.update {
            VoiceChatUiState(
                phase = VoiceChatPhase.IDLE,
                enabled = false,
                snackbarMessage = "语音对话已关"
            )
        }
    }

    private suspend fun startListeningLocked(
        skipPermissionCheck: Boolean = false,
        onAutoSend: suspend (String) -> Unit
    ): Boolean {
        val config = settings.getConfig()
        val ensureMsg = ensureAsrReady(config)
        if (ensureMsg != null) {
            _uiState.update {
                it.copy(phase = VoiceChatPhase.IDLE, snackbarMessage = ensureMsg)
            }
            return false
        }
        if (!skipPermissionCheck) {
            val perm = permissionChecker.check()
            if (!MicPermissionGate.canRecord(perm)) {
                if (perm == MicPermissionState.PERMANENTLY_DENIED) {
                    _uiState.update {
                        it.copy(
                            phase = VoiceChatPhase.IDLE,
                            snackbarMessage = MicPermissionGate.deniedMessage(perm)
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            phase = VoiceChatPhase.IDLE,
                            needRequestPermission = true,
                            snackbarMessage = null
                        )
                    }
                }
                return false
            }
        }

        // 尝试流式
        streamingActive = nativeBridge.startStreaming(config.sampleRateHz)
        if (streamingActive) {
            recorder.onPcmChunk = { chunk ->
                val partial = nativeBridge.feedPcmChunk(chunk)
                if (partial != null) {
                    _uiState.update {
                        if (it.phase == VoiceChatPhase.LISTENING) {
                            it.copy(partialText = partial)
                        } else {
                            it
                        }
                    }
                }
                // endpoint → 自动停（异步标志；实际 stop 由 UI 轮询或下次 mutex）
                // 这里仅更新 partial；自动 endpoint 由调用方可选处理
            }
        } else {
            recorder.onPcmChunk = null
        }

        val start = recorder.startRecording(sampleRateHz = config.sampleRateHz)
        if (start.isFailure) {
            streamingActive = false
            recorder.onPcmChunk = null
            runCatching { nativeBridge.cancelStreaming() }
            _uiState.update {
                it.copy(
                    phase = VoiceChatPhase.IDLE,
                    snackbarMessage = start.exceptionOrNull()?.message
                        ?: "无法打开麦克风"
                )
            }
            return false
        }
        // 真机开麦后确认捕获线程有数据，避免「UI 在听但麦克风没流」
        val heartbeat = recorder.awaitCaptureHeartbeat()
        if (heartbeat != null) {
            streamingActive = false
            recorder.onPcmChunk = null
            runCatching { nativeBridge.cancelStreaming() }
            runCatching { recorder.cancelRecording() }
            _uiState.update {
                it.copy(
                    phase = VoiceChatPhase.IDLE,
                    snackbarMessage = heartbeat
                )
            }
            return false
        }
        _uiState.update {
            it.copy(
                phase = VoiceChatPhase.LISTENING,
                partialText = "",
                snackbarMessage = null
            )
        }
        startEndpointPollingLocked(onAutoSend)
        return true
    }

    /**
     * 流式 ASR 可用时轮询 [SherpaOnnxBridge.isEndpoint]，检测到端点后自动收口发送。
     * 非流式模式不做自动端点（整段录音，用户再点麦结束）。
     */
    private fun startEndpointPollingLocked(onAutoSend: suspend (String) -> Unit) {
        cancelEndpointPolling()
        if (!streamingActive) return
        endpointPollJob = sessionScope.launch {
            // 给用户一点开口时间，避免刚开麦就误触发
            delay(800)
            while (isActive) {
                delay(200)
                val hit = withContext(Dispatchers.Default) {
                    try {
                        streamingActive && nativeBridge.isEndpoint()
                    } catch (_: Throwable) {
                        false
                    }
                }
                if (!hit) continue
                // 端点命中：用 mutex 串行收口，避免与用户手动点麦竞态
                mutex.withLock {
                    if (_uiState.value.phase != VoiceChatPhase.LISTENING) return@withLock
                    stopListenAndSendLocked(onAutoSend)
                }
                return@launch
            }
        }
    }

    private fun cancelEndpointPolling() {
        endpointPollJob?.cancel()
        endpointPollJob = null
    }

    private suspend fun stopListenAndSendLocked(onAutoSend: suspend (String) -> Unit) {
        cancelEndpointPolling()
        _uiState.update { it.copy(phase = VoiceChatPhase.TRANSCRIBING) }
        recorder.onPcmChunk = null

        val text: String
        if (streamingActive) {
            streamingActive = false
            // 先停录（流已喂过 chunk）
            runCatching { recorder.stopRecording() }
            text = withContext(Dispatchers.IO) {
                nativeBridge.finishStreaming()?.trim().orEmpty()
            }
        } else {
            val audioResult = recorder.stopRecording()
            if (audioResult.isFailure) {
                _uiState.update {
                    it.copy(
                        phase = VoiceChatPhase.IDLE,
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
                        phase = VoiceChatPhase.IDLE,
                        snackbarMessage = "录音太短，请再说一会儿。"
                    )
                }
                return
            }
            // 有时长但几乎全静音 → 麦克风没真正采到声
            if (looksLikeSilentCapture(audio.pcm16leMono)) {
                _uiState.update {
                    it.copy(
                        phase = VoiceChatPhase.IDLE,
                        snackbarMessage = "麦克风似乎没有收到声音。请检查系统麦克风权限，" +
                            "或到「设置 → 应用 → 兰心 → 权限」确认已允许录音。"
                    )
                }
                return
            }
            val result = runCatching {
                engine.transcribe(
                    AsrTranscribeRequest(
                        pcm16leMono = audio.pcm16leMono,
                        sampleRateHz = audio.sampleRateHz
                    )
                )
            }
            if (result.isFailure) {
                _uiState.update {
                    it.copy(
                        phase = VoiceChatPhase.IDLE,
                        snackbarMessage = result.exceptionOrNull()?.message
                            ?: "语音识别失败"
                    )
                }
                return
            }
            text = result.getOrNull()?.text?.trim().orEmpty()
        }

        if (text.isEmpty() || text.startsWith("[asr-stub]")) {
            // stub 文本仍可发送（调试）；空则提示
            if (text.isEmpty()) {
                _uiState.update {
                    it.copy(
                        phase = VoiceChatPhase.IDLE,
                        snackbarMessage = "没有识别到内容，请再说一次。"
                    )
                }
                return
            }
        }

        _uiState.update {
            it.copy(
                phase = VoiceChatPhase.WAITING_REPLY,
                lastFinalText = text,
                partialText = text,
                snackbarMessage = null
            )
        }
        // 回调在锁外更安全，但我们已在 mutex 内；onAutoSend 应短
        runCatching { onAutoSend(text) }
            .onFailure { e ->
                _uiState.update {
                    it.copy(
                        phase = VoiceChatPhase.IDLE,
                        snackbarMessage = e.message ?: "发送失败"
                    )
                }
            }
    }

    private suspend fun ensureAsrReady(config: AsrConfig): String? {
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
            return "请先下载/开启离线语音。可到「设置 → 离线语音识别」。"
        }
        val loaded = runCatching { engine.load(config) }.getOrDefault(false)
        if (loaded && engine.isReady) return null
        return engine.lastError
            ?: "请先下载/开启离线语音"
    }

    private fun looksLikeSilentCapture(pcm: ByteArray): Boolean {
        if (pcm.size < 4) return true
        var peak = 0
        var i = 0
        // 抽样扫描，避免长音频拖慢主路径
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
        const val MIN_USEFUL_MS = 200L
        /** 峰值低于此阈值视为「麦克风没采到声」。 */
        const val SILENCE_PEAK_THRESHOLD = 48
    }
}

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

package com.lanxin.android.builtin.pet.domain

import com.lanxin.android.builtin.localinference.domain.LocalReplySanitizer
import com.lanxin.android.builtin.systemtools.domain.DeviceToolBridge
import com.lanxin.android.builtin.systemtools.domain.DeviceToolChannel
import com.lanxin.android.builtin.systemtools.domain.DeviceToolOutcome
import com.lanxin.android.builtin.systemtools.domain.DeviceToolTurn
import com.lanxin.android.builtin.voice.data.PcmAudioPlayer
import com.lanxin.android.builtin.voice.domain.TtsEngine
import com.lanxin.android.builtin.voice.domain.TtsSettings
import com.lanxin.android.builtin.voice.domain.TtsSynthesizeRequest
import com.lanxin.android.core.log.LogManager
import com.lanxin.android.plugins.unifiedinbox.data.CrossSessionEntity
import com.lanxin.android.plugins.unifiedinbox.data.CrossSessionPlatform
import com.lanxin.android.plugins.unifiedinbox.data.CrossSessionRepository
import com.lanxin.android.plugins.unifiedinbox.data.CrossSessionRole
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 桌宠语音会话协调器：听 → 想 → **办** → 说。
 *
 * - 输入：ASR 文本（可 stub）
 * - 思考：[PetChatResponder]（stub / 后续 ChatRouter）
 * - 办事：[DeviceToolBridge]（Registry + Gate，与 Chat/MCP 同一路径）
 * - 输出：[TtsEngine] 合成 → [PcmAudioPlayer] 真播放 + 字幕气泡（**不**塞 Chat 输入框）
 * - 落盘：每轮 user/assistant 写入跨会话历史 + 文件日志（不依赖 logcat）
 *
 * 默认不录音、不截屏；仅用户 / 设置页显式触发。
 * 系统工具默认关；写操作需确认策略由 Gate 决定。
 *
 * ## Phase 7.5 一体接入
 *
 * ```
 * 听 → 想 → DeviceToolBridge.resolveAndInvoke → 结果并入回复 → 说(合成+播放)
 * ```
 *
 * Chat / MCP / VoiceSession 共用同一套系统工具与确认门闸，见 `docs/system-tools.md` §7.5。
 */
@Singleton
class VoiceSessionCoordinator @Inject constructor(
    private val responder: PetChatResponder,
    private val ttsEngine: TtsEngine,
    private val ttsSettings: TtsSettings,
    private val petSettings: PetSettings,
    private val deviceToolBridge: DeviceToolBridge,
    private val pcmPlayer: PcmAudioPlayer,
    private val logManager: LogManager? = null,
    private val crossSessionRepository: CrossSessionRepository? = null
) {
    private val log = logManager?.getLogger(TAG)
    private val persistScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val mutex = Mutex()
    private val _snapshot = MutableStateFlow(VoiceSessionSnapshot())
    val snapshot: StateFlow<VoiceSessionSnapshot> = _snapshot.asStateFlow()

    fun current(): VoiceSessionSnapshot = _snapshot.value

    /**
     * 跑完整一轮会话（状态机驱动）。
     *
     * @param input ASR 文本；stub 演示可传固定句
     * @param toolConfirmed 若本轮命中写操作工具，是否视为用户已确认
     * @param skipTts true=仅文字（跳过 load/synthesize）；文字陪伴默认 true，避免 TTS 未就绪拖垮/误占麦
     */
    suspend fun runRound(
        input: VoiceSessionInput,
        toolConfirmed: Boolean = false,
        skipTts: Boolean = false
    ): VoiceSessionResult = mutex.withLock {
        val started = System.currentTimeMillis()
        var snap = _snapshot.value
        val config = petSettings.getConfig()
        if (!config.enabled) {
            snap = VoiceSessionStateMachine.fail(snap, "pet_disabled")
            _snapshot.value = snap
            log?.w("runRound blocked: pet_disabled source=${input.source}")
            return@withLock VoiceSessionResult(
                asrText = input.asrText,
                replyText = "",
                subtitle = "",
                phase = snap.phase,
                isStub = input.isStub,
                error = snap.lastError
            )
        }

        val text = input.asrText.trim()
        if (text.isEmpty()) {
            snap = VoiceSessionStateMachine.fail(snap, "empty_asr")
            _snapshot.value = snap
            log?.w("runRound blocked: empty_asr source=${input.source}")
            return@withLock VoiceSessionResult(
                asrText = "",
                replyText = "",
                subtitle = "",
                phase = snap.phase,
                isStub = input.isStub,
                error = snap.lastError
            )
        }

        log?.i("round start source=${input.source} stub=${input.isStub} skipTts=$skipTts text=${text.take(80)}")

        snap = VoiceSessionStateMachine.startListening(snap).copy(isStubRound = input.isStub)
        _snapshot.value = snap

        snap = VoiceSessionStateMachine.onAsrDone(snap, text)
        _snapshot.value = snap

        // 办：统一 DeviceToolBridge.voiceTurn（意图未命中 → 纯闲聊）；异常不拖垮会话
        val toolTurn = runCatching {
            deviceToolBridge.voiceTurn(text, confirmed = toolConfirmed)
        }.getOrElse { e ->
            log?.w("voiceTurn failed, continue chat-only: ${e.message}")
            DeviceToolTurn(
                channel = DeviceToolChannel.VOICE,
                plan = null,
                outcome = null,
                needsTools = false,
                summary = null
            )
        }

        val chatReply = runCatching { responder.respond(text) }
            .getOrElse { e ->
                snap = VoiceSessionStateMachine.fail(snap, e.message ?: "think_failed")
                _snapshot.value = snap
                log?.e("think_failed source=${input.source}: ${e.message}", e)
                // 用户输入仍归档，便于跨会话/排障
                persistRound(
                    userText = text,
                    assistantText = "",
                    source = input.source,
                    error = snap.lastError
                )
                return@withLock VoiceSessionResult(
                    asrText = text,
                    replyText = "",
                    subtitle = "",
                    phase = snap.phase,
                    isStub = input.isStub,
                    error = snap.lastError,
                    durationMs = System.currentTimeMillis() - started,
                    toolName = toolTurn.plan?.toolName,
                    toolOutcome = toolTurn.outcome
                )
            }

        // rawReply 保留 [[mood=…]] 供 SPEAKING 相位匹配；展示/TTS/历史只做轻量清洗（对齐 MNN，不硬截一句）
        val rawReply = composeReply(chatReply, toolTurn)
        val displayReply = LocalReplySanitizer.forDisplay(rawReply, showThinking = false)

        // SPEAKING：replyText=raw（匹配），subtitle=剥后（气泡立刻干净）
        snap = VoiceSessionStateMachine.onThinkDone(snap, rawReply).copy(
            subtitle = displayReply
        )
        _snapshot.value = snap

        // 仅文字轮次：跳过 TTS load/synthesize，直接收口到 IDLE（不因 TTS 失败标 error）
        if (skipTts) {
            snap = VoiceSessionStateMachine.onSpeakDone(snap)
            snap = snap.copy(
                replyText = LocalReplySanitizer.forDisplay(snap.replyText, showThinking = false),
                subtitle = LocalReplySanitizer.forDisplay(snap.subtitle, showThinking = false)
            )
            _snapshot.value = snap
            val result = VoiceSessionResult(
                asrText = text,
                replyText = displayReply,
                subtitle = displayReply,
                phase = snap.phase,
                isStub = input.isStub,
                durationMs = System.currentTimeMillis() - started,
                toolName = toolTurn.plan?.toolName,
                toolOutcome = toolTurn.outcome
            )
            persistRound(
                userText = text,
                assistantText = displayReply,
                source = input.source,
                error = null,
                durationMs = result.durationMs,
                toolName = result.toolName
            )
            return@withLock result
        }

        // TTS：未就绪时用 DataStore 配置 auto-load（含 modelDir）；绝不把标签念出来
        if (!ttsEngine.isReady) {
            log?.i("tts not ready, attempting auto-load...")
            runCatching {
                val stored = ttsSettings.getConfig()
                val toLoad = if (stored.enabled) {
                    stored
                } else {
                    // 会话需要发音时临时 enable；路径仍读 prefs / 下载结果
                    stored.copy(enabled = true)
                }
                log?.i("tts auto-load config: enabled=${toLoad.enabled} modelDir=${toLoad.modelDir} voiceId=${toLoad.voiceId}")
                val loaded = ttsEngine.load(toLoad)
                log?.i("tts auto-load result: $loaded, isReady=${ttsEngine.isReady}, lastError=${ttsEngine.lastError}")
                if (loaded && !stored.enabled) {
                    ttsSettings.setEnabled(true)
                }
            }.onFailure { e ->
                log?.w("tts auto-load exception: ${e.javaClass.simpleName}: ${e.message}")
            }
        }
        // 防闪退核心：如果 auto-load 后仍 not ready，绝对不调 synthesize，文字仍返回
        if (!ttsEngine.isReady) {
            log?.w("tts still not ready after auto-load (lastError=${ttsEngine.lastError}); skip synthesize, text-only reply")
            snap = VoiceSessionStateMachine.reset(snap).copy(
                replyText = displayReply,
                subtitle = displayReply,
                asrText = text
            )
            _snapshot.value = snap
            val err = "tts_skipped:not_ready:${ttsEngine.lastError ?: "unknown"}"
            persistRound(
                userText = text,
                assistantText = displayReply,
                source = input.source,
                error = err,
                durationMs = System.currentTimeMillis() - started,
                toolName = toolTurn.plan?.toolName
            )
            // TTS 未就绪，直接返回文字结果
            return@withLock VoiceSessionResult(
                asrText = text,
                replyText = displayReply,
                subtitle = displayReply,
                phase = snap.phase,
                isStub = input.isStub,
                error = err,
                durationMs = System.currentTimeMillis() - started,
                toolName = toolTurn.plan?.toolName,
                toolOutcome = toolTurn.outcome
            )
        }
        // TTS ready，继续正常合成
        log?.i("tts is ready, proceeding with synthesize")
        // TTS 走 forSpeech 而非 forDisplay：剥离 emoji / 颜文字 / 装饰，不念标签
        val speechText = LocalReplySanitizer.forSpeech(displayReply, showThinking = false)
        val tts = runCatching {
            ttsEngine.synthesize(TtsSynthesizeRequest(text = speechText))
        }.getOrElse { e ->
            // 合成失败：文字结果仍返回；error 仅作状态提示，调用方不应崩溃
            snap = VoiceSessionStateMachine.fail(snap, e.message ?: "tts_failed")
            _snapshot.value = snap
            snap = VoiceSessionStateMachine.reset(snap).copy(
                replyText = displayReply,
                subtitle = displayReply,
                asrText = text
            )
            _snapshot.value = snap
            val err = "tts_failed:${e.message}"
            log?.w("tts synthesize failed, keep text reply: ${e.message}")
            persistRound(
                userText = text,
                assistantText = displayReply,
                source = input.source,
                error = err,
                durationMs = System.currentTimeMillis() - started,
                toolName = toolTurn.plan?.toolName
            )
            return@withLock VoiceSessionResult(
                asrText = text,
                replyText = displayReply,
                subtitle = displayReply,
                phase = snap.phase,
                isStub = input.isStub,
                error = err,
                durationMs = System.currentTimeMillis() - started,
                toolName = toolTurn.plan?.toolName,
                toolOutcome = toolTurn.outcome
            )
        }

        val spokenSubtitle = LocalReplySanitizer.forSpeech(
            tts.subtitle.ifBlank { speechText },
            showThinking = false
        )
        // 匹配仍读 replyText(raw)；气泡优先 subtitle(已剥)
        snap = snap.copy(subtitle = spokenSubtitle)
        _snapshot.value = snap

        // 关键链路：synthesize 之后必须 play；空 PCM / stub 则 no-op，不崩
        if (tts.pcm16leMono.isNotEmpty()) {
            val playResult = runCatching {
                pcmPlayer.play(tts.pcm16leMono, tts.sampleRateHz)
            }.getOrElse { e ->
                log?.w("tts play failed: ${e.message}")
                Result.failure(e)
            }
            if (playResult.isFailure) {
                log?.w("tts play soft-fail: ${playResult.exceptionOrNull()?.message}")
            } else {
                log?.i("tts play ok bytes=${tts.pcm16leMono.size} rate=${tts.sampleRateHz} stub=${tts.isStub}")
            }
        } else {
            log?.i(
                "tts no pcm (stub=${tts.isStub} ready=${ttsEngine.isReady}); " +
                    "subtitle only, skip AudioTrack"
            )
        }

        snap = VoiceSessionStateMachine.onSpeakDone(snap)
        // 说完后历史快照统一剥离，避免标签进 UI / 预览
        snap = snap.copy(
            replyText = LocalReplySanitizer.forDisplay(snap.replyText, showThinking = false),
            subtitle = LocalReplySanitizer.forDisplay(snap.subtitle, showThinking = false)
        )
        _snapshot.value = snap

        val result = VoiceSessionResult(
            asrText = text,
            replyText = displayReply,
            subtitle = spokenSubtitle,
            phase = snap.phase,
            isStub = input.isStub || tts.isStub,
            durationMs = System.currentTimeMillis() - started,
            toolName = toolTurn.plan?.toolName,
            toolOutcome = toolTurn.outcome
        )
        persistRound(
            userText = text,
            assistantText = displayReply,
            source = input.source,
            error = null,
            durationMs = result.durationMs,
            toolName = result.toolName
        )
        result
    }

    /**
     * 设置页试运行：固定 stub 一轮「听→想→说」（不强制工具）。
     * TTS 未就绪时自动 skipTts，避免未装 so / 未下载模型时 native 路径踩雷。
     */
    suspend fun runDemoRound(): VoiceSessionResult {
        val skipTts = !ttsEngine.isReady
        return runRound(
            input = VoiceSessionInput(
                asrText = "兰心，你好呀",
                isStub = true,
                source = "demo"
            ),
            skipTts = skipTts
        )
    }

    suspend fun reset() = mutex.withLock {
        _snapshot.value = VoiceSessionStateMachine.reset(_snapshot.value)
    }

    private fun composeReply(
        chatReply: String,
        turn: DeviceToolTurn
    ): String {
        val outcome = turn.outcome ?: return chatReply
        val toolLine = turn.summary
            ?: deviceToolBridge.summarize(outcome, toolName = turn.plan?.toolName)
        return when (outcome) {
            is DeviceToolOutcome.Ok -> "$toolLine $chatReply".trim()
            is DeviceToolOutcome.NeedsConfirmation ->
                "$toolLine 你确认的话再说一遍并批准哦～"
            is DeviceToolOutcome.Denied,
            is DeviceToolOutcome.Error -> toolLine
        }
    }

    /**
     * 异步写入文件日志 + 跨会话历史。
     * 不阻塞会话主路径；失败仅记 warning，不抛回 UI。
     */
    private fun persistRound(
        userText: String,
        assistantText: String,
        source: String,
        error: String?,
        durationMs: Long = 0L,
        toolName: String? = null
    ) {
        val now = System.currentTimeMillis()
        val summary = buildString {
            append("round done source=").append(source)
            append(" durationMs=").append(durationMs)
            if (!toolName.isNullOrBlank()) append(" tool=").append(toolName)
            if (!error.isNullOrBlank()) append(" error=").append(error)
            append(" user=").append(userText.take(120))
            if (assistantText.isNotBlank()) {
                append(" reply=").append(assistantText.take(160))
            }
        }
        if (error.isNullOrBlank()) {
            log?.i(summary)
        } else {
            log?.w(summary)
        }

        val repo = crossSessionRepository ?: return
        persistScope.launch {
            runCatching {
                val entities = buildList {
                    if (userText.isNotBlank()) {
                        add(
                            CrossSessionEntity(
                                platform = CrossSessionPlatform.LOCAL,
                                sessionId = COMPANION_SESSION_ID,
                                sessionTitle = COMPANION_SESSION_TITLE,
                                time = now,
                                role = CrossSessionRole.USER,
                                content = userText
                            )
                        )
                    }
                    if (assistantText.isNotBlank()) {
                        add(
                            CrossSessionEntity(
                                platform = CrossSessionPlatform.LOCAL,
                                sessionId = COMPANION_SESSION_ID,
                                sessionTitle = COMPANION_SESSION_TITLE,
                                time = now + 1,
                                role = CrossSessionRole.ASSISTANT,
                                content = assistantText
                            )
                        )
                    }
                }
                if (entities.isNotEmpty()) {
                    repo.insertAll(entities)
                }
            }.onFailure { e ->
                log?.w("persistRound failed: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "VoiceSession"

        /** 跨会话固定 session：全屏陪伴 / 桌宠共用。 */
        const val COMPANION_SESSION_ID = "companion"
        const val COMPANION_SESSION_TITLE = "全屏陪伴"
    }
}

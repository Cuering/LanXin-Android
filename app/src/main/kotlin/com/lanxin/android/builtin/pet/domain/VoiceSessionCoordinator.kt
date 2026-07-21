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
import com.lanxin.android.builtin.systemtools.domain.DeviceToolInvocation
import com.lanxin.android.builtin.systemtools.domain.DeviceToolOutcome
import com.lanxin.android.builtin.systemtools.domain.DeviceToolTurn
import com.lanxin.android.builtin.voice.domain.TtsConfig
import com.lanxin.android.builtin.voice.domain.TtsEngine
import com.lanxin.android.builtin.voice.domain.TtsSettings
import com.lanxin.android.builtin.voice.domain.TtsSynthesizeRequest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 桌宠语音会话协调器：听 → 想 → **办** → 说。
 *
 * - 输入：ASR 文本（可 stub）
 * - 思考：[PetChatResponder]（stub / 后续 ChatRouter）
 * - 办事：[DeviceToolBridge]（Registry + Gate，与 Chat/MCP 同一路径）
 * - 输出：[TtsEngine] + 字幕气泡（**不**塞 Chat 输入框）
 *
 * 默认不录音、不截屏；仅用户 / 设置页显式触发。
 * 系统工具默认关；写操作需确认策略由 Gate 决定。
 *
 * ## Phase 7.5 一体接入
 *
 * ```
 * 听 → 想 → DeviceToolBridge.resolveAndInvoke → 结果并入回复 → 说
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
    private val deviceToolBridge: DeviceToolBridge
) {

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
            return@withLock VoiceSessionResult(
                asrText = "",
                replyText = "",
                subtitle = "",
                phase = snap.phase,
                isStub = input.isStub,
                error = snap.lastError
            )
        }

        snap = VoiceSessionStateMachine.startListening(snap).copy(isStubRound = input.isStub)
        _snapshot.value = snap

        snap = VoiceSessionStateMachine.onAsrDone(snap, text)
        _snapshot.value = snap

        // 办：统一 DeviceToolBridge.voiceTurn（意图未命中 → 纯闲聊）
        val toolTurn = deviceToolBridge.voiceTurn(text, confirmed = toolConfirmed)
        val toolInvocation: DeviceToolInvocation? = toolTurn.toInvocationOrNull()

        val chatReply = runCatching { responder.respond(text) }
            .getOrElse { e ->
                snap = VoiceSessionStateMachine.fail(snap, e.message ?: "think_failed")
                _snapshot.value = snap
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

        // rawReply 保留 [[mood=…]] 供 SPEAKING 相位匹配；展示/TTS/历史统一剥标签
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
            return@withLock VoiceSessionResult(
                asrText = text,
                replyText = displayReply,
                subtitle = displayReply,
                phase = snap.phase,
                isStub = input.isStub,
                durationMs = System.currentTimeMillis() - started,
                toolName = toolTurn.plan?.toolName,
                toolOutcome = toolTurn.outcome
            )
        }

        // TTS：未就绪时用 DataStore 配置 auto-load（含 modelDir）；绝不把标签念出来
        if (!ttsEngine.isReady) {
            runCatching {
                val stored = ttsSettings.getConfig()
                val toLoad = if (stored.enabled) {
                    stored
                } else {
                    // 会话需要发音时临时 enable；路径仍读 prefs / 下载结果
                    stored.copy(enabled = true)
                }
                ttsEngine.load(toLoad)
                if (!stored.enabled) {
                    ttsSettings.setEnabled(true)
                }
            }
        }
        val tts = runCatching {
            ttsEngine.synthesize(TtsSynthesizeRequest(text = displayReply))
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
            return@withLock VoiceSessionResult(
                asrText = text,
                replyText = displayReply,
                subtitle = displayReply,
                phase = snap.phase,
                isStub = input.isStub,
                error = "tts_failed:${e.message}",
                durationMs = System.currentTimeMillis() - started,
                toolName = toolTurn.plan?.toolName,
                toolOutcome = toolTurn.outcome
            )
        }

        val spokenSubtitle = LocalReplySanitizer.forDisplay(
            tts.subtitle.ifBlank { displayReply },
            showThinking = false
        )
        // 匹配仍读 replyText(raw)；气泡优先 subtitle(已剥)
        snap = snap.copy(subtitle = spokenSubtitle)
        _snapshot.value = snap

        snap = VoiceSessionStateMachine.onSpeakDone(snap)
        // 说完后历史快照统一剥离，避免标签进 UI / 预览
        snap = snap.copy(
            replyText = LocalReplySanitizer.forDisplay(snap.replyText, showThinking = false),
            subtitle = LocalReplySanitizer.forDisplay(snap.subtitle, showThinking = false)
        )
        _snapshot.value = snap

        VoiceSessionResult(
            asrText = text,
            replyText = displayReply,
            subtitle = spokenSubtitle,
            phase = snap.phase,
            isStub = input.isStub || tts.isStub,
            durationMs = System.currentTimeMillis() - started,
            toolName = toolTurn.plan?.toolName,
            toolOutcome = toolTurn.outcome
        )
    }

    /**
     * 设置页试运行：固定 stub 一轮「听→想→说」（不强制工具）。
     */
    suspend fun runDemoRound(): VoiceSessionResult {
        return runRound(
            VoiceSessionInput(
                asrText = "兰心，你好呀",
                isStub = true,
                source = "demo"
            )
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
}

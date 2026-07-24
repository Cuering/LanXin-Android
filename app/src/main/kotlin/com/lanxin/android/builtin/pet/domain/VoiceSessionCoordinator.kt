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
import com.lanxin.android.builtin.systemtools.domain.DeviceToolOutcome
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
 * 桌宠语音会话协调器（薄编排层）。
 *
 * 职责收敛：
 * 1. 状态机相位（IDLE→LISTENING→THINKING→SPEAKING→IDLE），供 UI/表情驱动
 * 2. 编排 [VoiceInputPipeline]（输入链）与 [VoiceOutputPipeline]（输出链）
 * 3. 落盘跨会话历史
 *
 * 两条流水线各自独立：
 * - [VoiceInputPipeline]：文字 → LLM → 回复文字（不含 TTS）
 * - [VoiceOutputPipeline]：文字 → 合成 → 播放（不含 LLM）
 * 调用方可根据场景只走输入链（文字显示）或两条链都走（语音对话）。
 *
 * ## 两链对接方式
 *
 * 语音全双工：
 * ```
 * ASR → inputPipeline.process(text) → replyText → outputPipeline.speak(replyText)
 * ```
 *
 * 仅文字（TTS 未就绪或用户关语音）：
 * ```
 * 键盘 → inputPipeline.process(text) → replyText（仅展示，不 speak）
 * ```
 *
 * @see VoiceInputPipeline
 * @see VoiceOutputPipeline
 */
@Singleton
class VoiceSessionCoordinator @Inject constructor(
    private val inputPipeline: VoiceInputPipeline,
    private val outputPipeline: VoiceOutputPipeline,
    private val petSettings: PetSettings,
    private val deviceToolBridge: DeviceToolBridge,
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
     * 单独输出链：把已有回复文字合成语音并播放。
     * 调用方可在 [runRound] 拿到 replyText 后选择性调此方法。
     */
    suspend fun speakReply(replyText: String) {
        outputPipeline.speak(replyText)
    }

    /**
     * 跑完整一轮文字（输入链）：文字 → LLM → 回复文字，不涉及 TTS。
     *
     * 这是最简路径：无论 ASR 还是键盘输入，调此方法拿回复文字。
     * 需要语音输出时再单独调 [speakReply]。
     *
     * @param input 输入（ASR 文本或键盘文字）
     * @param toolConfirmed 工具写操作是否默认确认
     * @return [VoiceSessionResult] 含回复文字、工具信息
     */
    suspend fun runRound(
        input: VoiceSessionInput,
        toolConfirmed: Boolean = false
    ): VoiceSessionResult = mutex.withLock {
        val started = System.currentTimeMillis()
        var snap = _snapshot.value
        val config = petSettings.getConfig()
        if (!config.enabled) {
            snap = VoiceSessionStateMachine.fail(snap, "pet_disabled")
            _snapshot.value = snap
            log?.w("runRound blocked: pet_disabled source=${input.source}")
            return@withLock emptyResult(input, snap.phase, snap.lastError)
        }

        val text = input.asrText.trim()
        if (text.isEmpty()) {
            snap = VoiceSessionStateMachine.fail(snap, "empty_asr")
            _snapshot.value = snap
            log?.w("runRound blocked: empty_asr source=${input.source}")
            return@withLock emptyResult(input, snap.phase, snap.lastError)
        }

        log?.i("round start source=${input.source} stub=${input.isStub} text=${text.take(80)}")
        snap = VoiceSessionStateMachine.startListening(snap).copy(isStubRound = input.isStub)
        _snapshot.value = snap

        snap = VoiceSessionStateMachine.onAsrDone(snap, text)
        _snapshot.value = snap

        // === 输入链：工具 + LLM ===
        val inputResult = inputPipeline.process(text, toolConfirmed = toolConfirmed)
        val rawReply = composeReply(inputResult.replyText, inputResult)
        val displayReply = LocalReplySanitizer.forDisplay(rawReply, showThinking = false)

        snap = VoiceSessionStateMachine.onThinkDone(snap, rawReply).copy(
            subtitle = displayReply
        )
        _snapshot.value = snap

        // 说完状态
        snap = VoiceSessionStateMachine.onSpeakDone(snap)
        snap = snap.copy(
            replyText = displayReply,
            subtitle = displayReply
        )
        _snapshot.value = snap

        val result = VoiceSessionResult(
            asrText = text,
            replyText = displayReply,
            subtitle = displayReply,
            phase = snap.phase,
            isStub = input.isStub,
            durationMs = System.currentTimeMillis() - started,
            toolName = inputResult.toolName,
            toolOutcome = inputResult.toolOutcome
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
     * 设置页试运行：模拟一轮输入，走完整输入链，文字返回。
     * 不强制 TTS（由调用方决定是否 [speakReply]）。
     */
    suspend fun runDemoRound(): VoiceSessionResult {
        return runRound(
            input = VoiceSessionInput(
                asrText = "兰心，你好呀",
                isStub = true,
                source = "demo"
            )
        )
    }

    suspend fun reset() = mutex.withLock {
        _snapshot.value = VoiceSessionStateMachine.reset(_snapshot.value)
    }

    private fun composeReply(chatReply: String, inputResult: VoiceInputResult): String {
        val outcome = inputResult.toolOutcome ?: return chatReply
        val toolLine = inputResult.toolName?.let { name ->
            deviceToolBridge.summarize(outcome, toolName = name)
        } ?: return chatReply
        return when (outcome) {
            is DeviceToolOutcome.Ok -> "$toolLine $chatReply".trim()
            is DeviceToolOutcome.NeedsConfirmation ->
                "$toolLine 你确认的话再说一遍并批准哦～"
            is DeviceToolOutcome.Denied,
            is DeviceToolOutcome.Error -> toolLine
        }
    }

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

    private fun emptyResult(input: VoiceSessionInput, phase: VoiceSessionPhase, error: String?): VoiceSessionResult {
        return VoiceSessionResult(
            asrText = input.asrText,
            replyText = "",
            subtitle = "",
            phase = phase,
            isStub = input.isStub,
            error = error
        )
    }

    companion object {
        private const val TAG = "VoiceSession"

        /** 跨会话固定 session：全屏陪伴 / 桌宠共用。 */
        const val COMPANION_SESSION_ID = "companion"
        const val COMPANION_SESSION_TITLE = "全屏陪伴"
    }
}

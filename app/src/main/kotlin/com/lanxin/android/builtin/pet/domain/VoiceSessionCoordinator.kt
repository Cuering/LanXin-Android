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

import com.lanxin.android.builtin.voice.domain.TtsEngine
import com.lanxin.android.builtin.voice.domain.TtsSynthesizeRequest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 桌宠语音会话协调器：听 → 想 → 说。
 *
 * - 输入：ASR 文本（可 stub）
 * - 思考：[PetChatResponder]（stub / 后续 ChatRouter）
 * - 输出：[TtsEngine] + 字幕气泡（**不**塞 Chat 输入框）
 *
 * 默认不录音、不截屏；仅用户 / 设置页显式触发。
 */
@Singleton
class VoiceSessionCoordinator @Inject constructor(
    private val responder: PetChatResponder,
    private val ttsEngine: TtsEngine,
    private val petSettings: PetSettings
) {

    private val mutex = Mutex()
    private val _snapshot = MutableStateFlow(VoiceSessionSnapshot())
    val snapshot: StateFlow<VoiceSessionSnapshot> = _snapshot.asStateFlow()

    fun current(): VoiceSessionSnapshot = _snapshot.value

    /**
     * 跑完整一轮会话（状态机驱动）。
     *
     * @param input ASR 文本；stub 演示可传固定句
     */
    suspend fun runRound(input: VoiceSessionInput): VoiceSessionResult = mutex.withLock {
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

        val reply = runCatching { responder.respond(text) }
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
                    durationMs = System.currentTimeMillis() - started
                )
            }

        snap = VoiceSessionStateMachine.onThinkDone(snap, reply)
        _snapshot.value = snap

        // TTS：未就绪时仍展示字幕气泡（M1 stub 可 auto-load）
        if (!ttsEngine.isReady) {
            runCatching {
                ttsEngine.load(
                    com.lanxin.android.builtin.voice.domain.TtsConfig(enabled = true)
                )
            }
        }
        val tts = runCatching {
            ttsEngine.synthesize(TtsSynthesizeRequest(text = reply))
        }.getOrElse { e ->
            // 合成失败仍回 IDLE，保留 reply 文本
            snap = VoiceSessionStateMachine.fail(snap, e.message ?: "tts_failed")
            _snapshot.value = snap
            snap = VoiceSessionStateMachine.reset(snap).copy(
                replyText = reply,
                subtitle = reply,
                asrText = text
            )
            _snapshot.value = snap
            return@withLock VoiceSessionResult(
                asrText = text,
                replyText = reply,
                subtitle = reply,
                phase = snap.phase,
                isStub = input.isStub,
                error = "tts_failed:${e.message}",
                durationMs = System.currentTimeMillis() - started
            )
        }

        snap = snap.copy(subtitle = tts.subtitle.ifBlank { reply })
        _snapshot.value = snap

        snap = VoiceSessionStateMachine.onSpeakDone(snap)
        _snapshot.value = snap

        VoiceSessionResult(
            asrText = text,
            replyText = reply,
            subtitle = tts.subtitle.ifBlank { reply },
            phase = snap.phase,
            isStub = input.isStub || tts.isStub,
            durationMs = System.currentTimeMillis() - started
        )
    }

    /**
     * 设置页试运行：固定 stub 一轮「听→想→说」。
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
}

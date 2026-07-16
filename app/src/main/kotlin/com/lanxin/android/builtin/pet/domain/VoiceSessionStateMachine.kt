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

/**
 * 纯逻辑状态机：IDLE → LISTENING → THINKING → SPEAKING → IDLE。
 *
 * 无 Android 依赖，便于 JVM 单测。
 */
object VoiceSessionStateMachine {

    /**
     * 是否允许从 [from] 转到 [to]。
     */
    fun canTransition(from: VoiceSessionPhase, to: VoiceSessionPhase): Boolean {
        if (from == to) return true
        return when (from) {
            VoiceSessionPhase.IDLE -> to == VoiceSessionPhase.LISTENING || to == VoiceSessionPhase.ERROR
            VoiceSessionPhase.LISTENING ->
                to == VoiceSessionPhase.THINKING ||
                    to == VoiceSessionPhase.IDLE ||
                    to == VoiceSessionPhase.ERROR
            VoiceSessionPhase.THINKING ->
                to == VoiceSessionPhase.SPEAKING ||
                    to == VoiceSessionPhase.IDLE ||
                    to == VoiceSessionPhase.ERROR
            VoiceSessionPhase.SPEAKING ->
                to == VoiceSessionPhase.IDLE ||
                    to == VoiceSessionPhase.ERROR
            VoiceSessionPhase.ERROR ->
                to == VoiceSessionPhase.IDLE ||
                    to == VoiceSessionPhase.LISTENING
        }
    }

    /**
     * 应用转移；非法则返回原状态 + 错误说明。
     */
    fun transition(
        current: VoiceSessionSnapshot,
        to: VoiceSessionPhase,
        asrText: String? = null,
        replyText: String? = null,
        subtitle: String? = null,
        error: String? = null,
        bumpRound: Boolean = false
    ): VoiceSessionSnapshot {
        if (!canTransition(current.phase, to)) {
            return current.copy(
                phase = VoiceSessionPhase.ERROR,
                lastError = "illegal_transition:${current.phase}->$to"
            )
        }
        return current.copy(
            phase = to,
            asrText = asrText ?: current.asrText,
            replyText = replyText ?: current.replyText,
            subtitle = subtitle ?: current.subtitle,
            lastError = if (to == VoiceSessionPhase.ERROR) {
                error ?: current.lastError ?: "error"
            } else {
                null
            },
            roundId = if (bumpRound) current.roundId + 1 else current.roundId
        )
    }

    /** 开始听。 */
    fun startListening(current: VoiceSessionSnapshot): VoiceSessionSnapshot =
        transition(current, VoiceSessionPhase.LISTENING, asrText = "", replyText = "", subtitle = "", bumpRound = true)

    /** ASR 完成 → 思考。 */
    fun onAsrDone(current: VoiceSessionSnapshot, text: String): VoiceSessionSnapshot =
        transition(current, VoiceSessionPhase.THINKING, asrText = text)

    /** 思考完成 → 说。 */
    fun onThinkDone(current: VoiceSessionSnapshot, reply: String): VoiceSessionSnapshot =
        transition(current, VoiceSessionPhase.SPEAKING, replyText = reply, subtitle = reply)

    /** 说完 → 闲。 */
    fun onSpeakDone(current: VoiceSessionSnapshot): VoiceSessionSnapshot =
        transition(current, VoiceSessionPhase.IDLE)

    /** 取消 / 复位。 */
    fun reset(current: VoiceSessionSnapshot): VoiceSessionSnapshot =
        transition(
            current.copy(phase = if (canTransition(current.phase, VoiceSessionPhase.IDLE)) current.phase else VoiceSessionPhase.ERROR),
            VoiceSessionPhase.IDLE,
            asrText = "",
            replyText = "",
            subtitle = ""
        ).let {
            // force idle even from ERROR
            if (it.phase != VoiceSessionPhase.IDLE) {
                current.copy(
                    phase = VoiceSessionPhase.IDLE,
                    asrText = "",
                    replyText = "",
                    subtitle = "",
                    lastError = null
                )
            } else {
                it.copy(lastError = null)
            }
        }

    fun fail(current: VoiceSessionSnapshot, message: String): VoiceSessionSnapshot =
        transition(current, VoiceSessionPhase.ERROR, error = message)
}

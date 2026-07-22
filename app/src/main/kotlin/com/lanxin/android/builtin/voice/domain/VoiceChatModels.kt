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

/**
 * 真语音对话阶段（录音 → 识别 → 自动发送 → TTS → 下一轮）。
 */
enum class VoiceChatPhase {
    /** 空闲。 */
    IDLE,

    /** 正在录音（可流式 partial）。 */
    LISTENING,

    /** 停麦后整段/收口转写中。 */
    TRANSCRIBING,

    /** 已送出用户话，等待 LLM。 */
    WAITING_REPLY,

    /** TTS 播放中。 */
    SPEAKING
}

/**
 * 真语音对话 UI 状态。
 *
 * @property phase 阶段
 * @property enabled 语音对话模式开关（开=自动发送+TTS；关=退回听写填框）
 * @property partialText 流式 ASR 实时 partial（边说边显示）
 * @property lastFinalText 最近一轮最终识别文本
 * @property snackbarMessage 一次性提示
 * @property needRequestPermission 需拉起 RECORD_AUDIO
 */
data class VoiceChatUiState(
    val phase: VoiceChatPhase = VoiceChatPhase.IDLE,
    val enabled: Boolean = false,
    val partialText: String = "",
    val lastFinalText: String? = null,
    val snackbarMessage: String? = null,
    val needRequestPermission: Boolean = false
) {
    val isListening: Boolean get() = phase == VoiceChatPhase.LISTENING
    val isBusy: Boolean
        get() = phase == VoiceChatPhase.TRANSCRIBING ||
            phase == VoiceChatPhase.WAITING_REPLY ||
            phase == VoiceChatPhase.SPEAKING
}
